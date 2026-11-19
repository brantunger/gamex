package io.github.brantunger.gamex.render;

import com.badlogic.gdx.Gdx;
import com.badlogic.gdx.graphics.Camera;
import com.badlogic.gdx.graphics.Color;
import com.badlogic.gdx.graphics.Mesh;
import com.badlogic.gdx.graphics.Pixmap;
import com.badlogic.gdx.graphics.Texture;
import com.badlogic.gdx.graphics.g3d.Environment;
import com.badlogic.gdx.graphics.g3d.Material;
import com.badlogic.gdx.graphics.g3d.ModelBatch;
import com.badlogic.gdx.graphics.g3d.Renderable;
import com.badlogic.gdx.graphics.g3d.attributes.ColorAttribute;
import com.badlogic.gdx.graphics.g3d.model.Animation;
import com.badlogic.gdx.graphics.g3d.utils.AnimationController;
import com.badlogic.gdx.math.MathUtils;
import com.badlogic.gdx.math.Vector3;
import com.badlogic.gdx.utils.Array;
import com.badlogic.gdx.utils.Disposable;
import com.badlogic.gdx.utils.GdxRuntimeException;
import com.badlogic.gdx.utils.Pool;
import com.badlogic.gdx.utils.TimeUtils;
import io.github.brantunger.gamex.model.GlbFile;
import io.github.brantunger.gamex.world.Enemy;
import io.github.brantunger.gamex.world.WorldModels;
import net.mgsx.gltf.loaders.glb.GLBLoader;
import net.mgsx.gltf.scene3d.attributes.FogAttribute;
import net.mgsx.gltf.scene3d.lights.DirectionalLightEx;
import net.mgsx.gltf.scene3d.scene.Scene;
import net.mgsx.gltf.scene3d.scene.SceneAsset;
import net.mgsx.gltf.scene3d.shaders.PBRShaderProvider;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.io.InputStream;
import java.util.IdentityHashMap;
import java.util.Iterator;
import java.util.List;
import java.util.Map;

/**
 * The Warden boss: a skinned, animated glTF model drawn with the gdx-gltf PBR shader. One model is shared by
 * every Warden; each Warden has its own skeleton, animation and lighting.
 */
final class WardenModel implements Disposable {

    private static final Logger log = LoggerFactory.getLogger(WardenModel.class);

    /** The scale that makes the model as tall as the Warden's collision cylinder. */
    static final float SCALE = Enemy.Kind.WARDEN.height / WorldModels.WARDEN_MODEL_HEIGHT;
    /** The model faces +Z and the enemies face -Z at a yaw of 0, so it is turned round. */
    private static final float FACING_TURN = 180f;
    /** How long one clip blends into the next, in seconds. */
    private static final float CROSSFADE = 0.15f;
    /**
     * How long a Warden must stand before it shows as standing, in seconds. A Warden that stops for a step or two
     * (turning to face a player already in reach, or held up) would start to stand and at once run or punch again;
     * the second change would cut the first blend short.
     */
    private static final float IDLE_DELAY = 0.12f;
    /** How far short of its end the combo's clip is held, in seconds. */
    private static final float COMBO_END_MARGIN = 1e-3f;
    /** A longer gap than this between two draws is a new or restarted game, not time to animate through. */
    private static final float MAX_STEP = 0.5f;
    /** Where the light comes from, as in the shader of the flat-shaded models. */
    private static final Vector3 TO_LIGHT = new Vector3(-0.3f, 0.9f, 0.4f).nor();
    private static final float KEY_LIGHT = 3.0f;
    private static final float AMBIENT_LIGHT = 0.8f;
    /**
     * How strongly the accent colour glows on the whole body, in linear light. It is small because the model has no
     * emissive texture: the glow tints every surface, and more than this hides the armour's own colours.
     */
    private static final float ACCENT_GLOW = 0.008f;
    /** How strongly a hit flashes the body red. */
    private static final float HIT_GLOW = 0.55f;

    /** One Warden's copy of the skeleton, with its animation and its own lighting. */
    private static final class Puppet {
        final Scene scene;
        final Environment environment = new Environment();
        final DirectionalLightEx keyLight = new DirectionalLightEx();
        final ColorAttribute ambient = new ColorAttribute(ColorAttribute.AmbientLight, 0f, 0f, 0f, 1f);
        final Array<ColorAttribute> emissives = new Array<>();
        WardenPose pose;
        float lastTime;
        float stillTime = IDLE_DELAY;
        boolean drawn;

        Puppet(Scene scene) {
            this.scene = scene;
        }
    }

    private final SceneAsset asset;
    private final ModelBatch batch;
    private final Color fogColour;
    private final float fogStart;
    private final float fogEnd;
    private final Map<Enemy, Puppet> puppets = new IdentityHashMap<>();
    private final Color glow = new Color();

    /**
     * Checks that the model file is there and is a model, without loading it. The model is only loaded when a
     * Warden is first drawn, which can be deep into a game; this is called when the game starts, so that a file
     * that is missing, or is the Git LFS pointer a clone without Git LFS leaves in its place, stops the game then.
     * @throws GdxRuntimeException if the file is missing, is a Git LFS pointer or is not a binary glTF file
     */
    static void checkFile() {
        String name = "The warden model " + WorldModels.WARDEN_PATH;
        byte[] header;
        try (InputStream in = Gdx.files.internal(WorldModels.WARDEN_PATH).read()) {
            header = GlbFile.readHeader(in);
        } catch (IOException | GdxRuntimeException e) {
            throw new GdxRuntimeException(name + " is missing or cannot be read: " + e.getMessage(), e);
        }
        try {
            GlbFile.checkHeader(header, name);
        } catch (IllegalArgumentException e) {
            throw new GdxRuntimeException(e.getMessage(), e);
        }
    }

    /**
     * @param enemies the enemies of the stage being shown
     * @return whether one of them is a Warden that is drawn, so that the model must be loaded
     */
    static boolean isNeeded(List<Enemy> enemies) {
        for (int i = 0; i < enemies.size(); i++) {
            Enemy enemy = enemies.get(i);
            if (enemy.getKind() == Enemy.Kind.WARDEN && !enemy.isDestroyed()) return true;
        }
        return false;
    }

    /**
     * Writes the glow of a Warden: its accent colour, pulsing in overdrive and brightening as a punch is about to
     * land, plus the red flash of a hit.
     * @param overdrive whether the Warden is in its second phase
     * @param windup how close the Warden's next punch is to landing, from 0 to 1
     * @param hitFlash how fresh the last hit is, from 0 to 1
     * @param time the game time in seconds, which the overdrive pulse follows
     * @param out receives the colour, in linear light
     * @return {@code out}
     */
    static Color glow(boolean overdrive, float windup, float hitFlash, float time, Color out) {
        float[] accent;
        float strength;
        if (overdrive) {
            accent = WorldModels.ACCENT_WARDEN_OVERDRIVE;
            float pulse = 0.5f + 0.5f * MathUtils.sin(time * 9f);
            strength = ACCENT_GLOW * (1f + 1.5f * pulse + windup);
        } else {
            accent = WorldModels.ACCENT_WARDEN_PHASE1;
            strength = ACCENT_GLOW * (1f + 2f * windup);
        }
        float hit = HIT_GLOW * hitFlash;
        return out.set(accent[0] * strength + hit, accent[1] * strength + 0.25f * hit,
            accent[2] * strength + 0.25f * hit, 1f);
    }

    /**
     * @param x where the Warden stands
     * @param z where the Warden stands
     * @param duration the length of the standing clip, in seconds
     * @return the moment of the clip the Warden starts to stand from: one that depends on where it is, so that it is
     *     the same in every run and differs between Wardens
     */
    static float idleStart(float x, float z, float duration) {
        float share = x * 0.37f + z * 0.61f;
        return (share - (float) Math.floor(share)) * duration;
    }

    /**
     * Loads the model and compiles its shader, which takes about a second: three 4096-pixel textures are decoded.
     * @param fogColour the colour distant things fade to
     * @param fogStart the distance at which the fog starts
     * @param fogEnd the distance at which the fog hides everything
     */
    WardenModel(Color fogColour, float fogStart, float fogEnd) {
        this.fogColour = fogColour;
        this.fogStart = fogStart;
        this.fogEnd = fogEnd;
        long started = TimeUtils.millis();
        try {
            asset = new GLBLoader().load(Gdx.files.internal(WorldModels.WARDEN_PATH));
        } catch (RuntimeException e) {
            throw new GdxRuntimeException("The warden model " + WorldModels.WARDEN_PATH + " could not be read: "
                + e.getMessage(), e);
        }
        // The loader keeps the decoded images, 48 MB each, until the model is disposed of; the textures made from
        // them never need them again.
        if (asset.pixmaps != null) {
            for (Pixmap pixmap : asset.pixmaps) pixmap.dispose();
            asset.pixmaps.clear();
        }
        for (WardenPose pose : WardenPose.values()) {
            if (asset.scene.model.getAnimation(pose.clip) == null) {
                asset.dispose();
                throw new GdxRuntimeException("The warden model " + WorldModels.WARDEN_PATH
                    + " has no animation named '" + pose.clip + "'");
            }
        }
        batch = new ModelBatch(PBRShaderProvider.createDefault(asset.maxBones));
        long read = TimeUtils.millis();
        compileShader();
        ModelMesh.modelLoaded(WorldModels.WARDEN_PATH, "warden");
        log.info("Loaded the warden model {} in {} ms", WorldModels.WARDEN_PATH, TimeUtils.millis() - started);
        if (log.isDebugEnabled()) {
            log.debug("The warden model was read in {} ms and its shader compiled in {} ms", read - started,
                TimeUtils.millis() - read);
            for (Mesh mesh : asset.meshes) {
                log.debug("The warden model has a mesh of {} vertices and {} indices, on up to {} bones",
                    mesh.getNumVertices(), mesh.getNumIndices(), asset.maxBones);
            }
            for (Texture texture : asset.textures) {
                log.debug("The warden model has a {}x{} texture", texture.getWidth(), texture.getHeight());
            }
            for (Animation animation : asset.animations) {
                log.debug("The warden model has the animation '{}' of {} s", animation.id, animation.duration);
            }
        }
    }

    /** Compiles the shader a Warden is drawn with now, so that the first Warden drawn does not wait for it. */
    private void compileShader() {
        Puppet puppet = newPuppet();
        Array<Renderable> renderables = new Array<>();
        Pool<Renderable> pool = new Pool<Renderable>() {
            @Override
            protected Renderable newObject() {
                return new Renderable();
            }
        };
        puppet.scene.modelInstance.getRenderables(renderables, pool);
        for (Renderable renderable : renderables) {
            renderable.environment = puppet.environment;
            batch.getShaderProvider().getShader(renderable);
        }
    }

    private Puppet newPuppet() {
        Puppet puppet = new Puppet(new Scene(asset.scene));
        puppet.keyLight.direction.set(TO_LIGHT).scl(-1f);
        puppet.environment.add(puppet.keyLight);
        puppet.environment.set(puppet.ambient);
        puppet.environment.set(new ColorAttribute(ColorAttribute.Fog, fogColour));
        puppet.environment.set(FogAttribute.createFog(fogStart, fogEnd, 1f));
        // Every attribute is set once, here: adding one later would compile another shader in the middle of a fight.
        for (Material material : puppet.scene.modelInstance.materials) {
            ColorAttribute emissive = new ColorAttribute(ColorAttribute.Emissive, 0f, 0f, 0f, 1f);
            material.set(emissive);
            puppet.emissives.add(emissive);
        }
        return puppet;
    }

    /**
     * Draws every live Warden, animated up to {@code time}. This changes the depth, culling and blending state, the
     * active texture unit and the bound shader; the caller sets them again afterwards.
     * @param camera the camera to draw from
     * @param enemies the enemies of the game; the ones that are not live Wardens are skipped
     * @param time the time to animate to in seconds: the game time, or the preview time of a menu backdrop
     * @param roomLight how bright the room is, where 1 is normal
     */
    void draw(Camera camera, List<Enemy> enemies, float time, float roomLight) {
        for (Puppet puppet : puppets.values()) puppet.drawn = false;
        batch.begin(camera);
        for (int i = 0; i < enemies.size(); i++) {
            Enemy enemy = enemies.get(i);
            if (enemy.getKind() != Enemy.Kind.WARDEN || enemy.isDestroyed()) continue;
            Puppet puppet = puppets.get(enemy);
            if (puppet == null) {
                puppet = newPuppet();
                puppets.put(enemy, puppet);
            }
            puppet.drawn = true;
            animate(puppet, enemy, time);
            light(puppet, enemy, time, roomLight);
            puppet.scene.modelInstance.transform.idt()
                .translate(enemy.getX(), 0f, enemy.getZ())
                .rotate(Vector3.Y, FACING_TURN - enemy.getYaw())
                .scale(SCALE, SCALE, SCALE);
            batch.render(puppet.scene.modelInstance, puppet.environment);
        }
        batch.end();
        // A Warden that was not drawn is destroyed or belongs to a game that is over.
        for (Iterator<Puppet> it = puppets.values().iterator(); it.hasNext(); ) {
            if (!it.next().drawn) it.remove();
        }
    }

    /**
     * Lets go of every Warden's skeleton. The renderer calls this while there is no Warden to draw, as after the
     * last one is destroyed, so that the skeletons do not keep that game's enemies alive.
     */
    void releasePuppets() {
        puppets.clear();
    }

    private void animate(Puppet puppet, Enemy enemy, float time) {
        boolean first = puppet.pose == null;
        float delta = first ? 0f : time - puppet.lastTime;
        if (delta < 0f || delta > MAX_STEP) delta = 0f;
        puppet.lastTime = time;
        AnimationController controller = puppet.scene.animationController;
        // While the game stands still, between two steps or on a paused frame, the pose stays as it is.
        if (first || delta > 0f) {
            float groundSpeed = enemy.getGroundSpeed();
            // An attack does not count as standing, so that the delay starts when the attack ends.
            boolean busy = groundSpeed > 0f
                || WardenPose.of(enemy.getState(), false, false) == WardenPose.ATTACK;
            puppet.stillTime = busy ? 0f : Math.min(IDLE_DELAY, puppet.stillTime + delta);
            // A Warden that has only just stopped keeps the pose it has until the delay is up.
            boolean waiting = !busy && puppet.stillTime < IDLE_DELAY && puppet.pose != null;
            WardenPose pose = waiting ? puppet.pose : WardenPose.of(enemy.getState(), busy, enemy.isChasing());
            if (pose != puppet.pose) {
                log.debug("A warden goes from {} to {} at {} s", puppet.pose, pose, time);
                // Every clip is set to loop, the combo too: a clip that has finished is replaced without a blend.
                controller.animate(pose.clip, -1, pose.playbackSpeed(groundSpeed), null, first ? 0f : CROSSFADE);
                puppet.pose = pose;
                // Each Warden stands from its own moment of the long standing clip, so that two do not move as one.
                if (pose == WardenPose.IDLE) {
                    controller.current.time = idleStart(enemy.getX(), enemy.getZ(), controller.current.duration);
                }
            }
            // The controller reuses its clip records, so the playing one is asked for here rather than kept.
            AnimationController.AnimationDesc playing = controller.current;
            if (playing != null) {
                if (pose == WardenPose.ATTACK) {
                    // Short of the end: at the end itself the controller counts the clip as played out.
                    playing.speed = 0f;
                    playing.time = Math.min(enemy.getComboTime(), playing.duration - COMBO_END_MARGIN);
                } else if (groundSpeed > 0f) {
                    playing.speed = pose.playbackSpeed(groundSpeed);
                }
            }
        }
        controller.update(delta);
    }

    private void light(Puppet puppet, Enemy enemy, float time, float roomLight) {
        float windup = enemy.getWindupProgress();
        float light = roomLight + 0.45f * windup;
        puppet.keyLight.intensity = KEY_LIGHT * light;
        puppet.keyLight.updateColor();
        puppet.ambient.color.set(AMBIENT_LIGHT * light, AMBIENT_LIGHT * light, AMBIENT_LIGHT * light, 1f);
        glow(enemy.isOverdrive(), windup, enemy.getHitFlash(), time, glow);
        for (ColorAttribute emissive : puppet.emissives) emissive.color.set(glow);
    }

    @Override
    public void dispose() {
        puppets.clear();
        batch.dispose();
        asset.dispose();
    }
}

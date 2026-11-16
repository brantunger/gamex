package io.github.brantunger.gamex.render;

import com.badlogic.gdx.Gdx;
import com.badlogic.gdx.graphics.Color;
import com.badlogic.gdx.graphics.GL20;
import com.badlogic.gdx.graphics.Mesh;
import com.badlogic.gdx.graphics.PerspectiveCamera;
import com.badlogic.gdx.graphics.Texture;
import com.badlogic.gdx.graphics.VertexAttribute;
import com.badlogic.gdx.graphics.VertexAttributes;
import com.badlogic.gdx.graphics.g2d.TextureRegion;
import com.badlogic.gdx.graphics.g3d.decals.CameraGroupStrategy;
import com.badlogic.gdx.graphics.g3d.decals.Decal;
import com.badlogic.gdx.graphics.g3d.decals.DecalBatch;
import com.badlogic.gdx.graphics.glutils.ShaderProgram;
import com.badlogic.gdx.math.MathUtils;
import com.badlogic.gdx.math.Matrix4;
import com.badlogic.gdx.math.Vector3;
import com.badlogic.gdx.utils.Array;
import com.badlogic.gdx.utils.Disposable;
import com.badlogic.gdx.utils.GdxRuntimeException;
import com.badlogic.gdx.utils.ScreenUtils;
import io.github.brantunger.gamex.GameSimulation;
import io.github.brantunger.gamex.player.Player;
import io.github.brantunger.gamex.weapon.BlastProjectile;
import io.github.brantunger.gamex.world.Effect;
import io.github.brantunger.gamex.world.GameMap;
import io.github.brantunger.gamex.world.MapGeometry;
import io.github.brantunger.gamex.world.Target;
import io.github.brantunger.gamex.world.WorldModels;

import java.util.Map;

/**
 * Draws the 3D view: the map as a few static meshes, one per texture, the targets and blast projectiles as 3D
 * models loaded from OBJ files, and the halos and effects as billboards.
 */
public final class WorldRenderer implements Disposable {

    private static final float FIELD_OF_VIEW = 70f;
    private static final float NEAR = 0.1f;
    private static final float FAR = 60f;
    private static final Color FOG_COLOUR = new Color(0.035f, 0.035f, 0.055f, 1f);
    private static final float FOG_START = 3.5f;
    private static final float FOG_END = 20f;
    /** How far the view rises and falls with each step, in world units. */
    private static final float BOB_HEIGHT = 0.012f;
    /** How long the spark of a pistol impact shows over its scorch mark, in seconds. */
    private static final float IMPACT_SPARK_TIME = 0.12f;
    /** The widest an explosion's fireball is drawn, so that it fits between the floor and the ceiling. */
    private static final float MAX_FIREBALL_SIZE = 0.8f;
    /** Explosions with at least this radius also light up the floor they cover. */
    private static final float MIN_FLOOR_GLOW_RADIUS = 1f;
    /** How far the floor glow sits above the floor. */
    private static final float FLOOR_GLOW_HEIGHT = 0.02f;

    private static final String VERTEX_SHADER =
        "attribute vec3 a_position;\n"
            + "attribute vec2 a_texCoord0;\n"
            + "attribute float a_shade;\n"
            + "uniform mat4 u_projViewTrans;\n"
            + "uniform vec3 u_cameraPosition;\n"
            + "uniform vec2 u_fogRange;\n"
            + "varying vec2 v_texCoord;\n"
            + "varying float v_shade;\n"
            + "varying float v_fog;\n"
            + "void main() {\n"
            + "    v_texCoord = a_texCoord0;\n"
            + "    v_shade = a_shade;\n"
            + "    float range = length(a_position - u_cameraPosition);\n"
            + "    v_fog = clamp((range - u_fogRange.x) / (u_fogRange.y - u_fogRange.x), 0.0, 1.0);\n"
            + "    gl_Position = u_projViewTrans * vec4(a_position, 1.0);\n"
            + "}\n";

    private static final String FRAGMENT_SHADER =
        "#ifdef GL_ES\n"
            + "precision mediump float;\n"
            + "#endif\n"
            + "uniform sampler2D u_texture;\n"
            + "uniform vec3 u_fogColour;\n"
            + "uniform float u_light;\n"
            + "varying vec2 v_texCoord;\n"
            + "varying float v_shade;\n"
            + "varying float v_fog;\n"
            + "void main() {\n"
            + "    vec3 colour = texture2D(u_texture, v_texCoord).rgb * v_shade * u_light;\n"
            + "    gl_FragColor = vec4(mix(colour, u_fogColour, v_fog), 1.0);\n"
            + "}\n";

    private static final String MODEL_VERTEX_SHADER =
        "attribute vec3 a_position;\n"
            + "attribute vec3 a_normal;\n"
            + "attribute vec3 a_colour;\n"
            + "attribute float a_gloss;\n"
            + "uniform mat4 u_projViewTrans;\n"
            + "uniform mat4 u_model;\n"
            + "uniform vec3 u_cameraPosition;\n"
            + "uniform vec2 u_fogRange;\n"
            + "uniform vec3 u_fogColour;\n"
            + "uniform float u_light;\n"
            + "uniform float u_hitFlash;\n"
            + "varying vec3 v_colour;\n"
            + "void main() {\n"
            + "    vec4 worldPos = u_model * vec4(a_position, 1.0);\n"
            + "    vec3 worldNormal = normalize((u_model * vec4(a_normal, 0.0)).xyz);\n"
            + "    vec3 weight = worldNormal * worldNormal;\n"
            + "    float shade = weight.z + weight.x * 0.72 + weight.y * (worldNormal.y > 0.0 ? 0.9 : 0.62);\n"
            + "    vec3 lightDir = normalize(vec3(-0.3, 0.9, 0.4));\n"
            + "    float key = 0.65 + 0.55 * max(dot(worldNormal, lightDir), 0.0);\n"
            + "    vec3 delta = u_cameraPosition - worldPos.xyz;\n"
            + "    float range = length(delta);\n"
            + "    vec3 toCamera = range > 1e-4 ? delta / range : vec3(0.0, 0.0, 1.0);\n"
            + "    vec3 halfVec = normalize(lightDir + toCamera);\n"
            + "    float sheen = a_gloss * pow(max(dot(worldNormal, halfVec), 0.0), 12.0) * 0.35;\n"
            + "    float rim = pow(1.0 - max(dot(worldNormal, toCamera), 0.0), 2.5) * 0.12;\n"
            + "    vec3 lit = (a_colour * shade * key + vec3(sheen + rim)) * u_light;\n"
            + "    lit = mix(lit, vec3(1.0, 0.25, 0.25), u_hitFlash * 0.65);\n"
            + "    float fog = clamp((range - u_fogRange.x) / (u_fogRange.y - u_fogRange.x), 0.0, 1.0);\n"
            + "    v_colour = mix(lit, u_fogColour, fog);\n"
            + "    gl_Position = u_projViewTrans * worldPos;\n"
            + "}\n";

    private static final String MODEL_FRAGMENT_SHADER =
        "#ifdef GL_ES\n"
            + "precision mediump float;\n"
            + "#endif\n"
            + "varying vec3 v_colour;\n"
            + "void main() {\n"
            + "    gl_FragColor = vec4(v_colour, 1.0);\n"
            + "}\n";

    /** A static mesh and the texture it is drawn with. */
    private static final class Surface {
        final Mesh mesh;
        final Texture texture;

        Surface(Mesh mesh, Texture texture) {
            this.mesh = mesh;
            this.texture = texture;
        }
    }

    private final PerspectiveCamera camera;
    private final ShaderProgram shader;
    private final ShaderProgram modelShader;
    private final Array<Surface> surfaces = new Array<>();
    private final Mesh canisterMesh;
    private final Mesh ringBoardMesh;
    private final Mesh blastOrbMesh;
    private final CameraGroupStrategy groupStrategy;
    private final DecalBatch decalBatch;
    private final Array<Decal> decals = new Array<>();
    private int decalsUsed;

    private final TextureRegion scorch;
    private final TextureRegion spark;
    private final TextureRegion orb;
    private final TextureRegion explosion;

    private final Matrix4 modelTransform = new Matrix4();
    private final Vector3 direction = new Vector3();
    private final Vector3 scratch = new Vector3();

    /**
     * @param map the map to build the static meshes from
     * @param textures the textures to draw with; the caller keeps ownership of them
     */
    public WorldRenderer(GameMap map, ProceduralTextures textures) {
        camera = new PerspectiveCamera(FIELD_OF_VIEW, Gdx.graphics.getWidth(), Gdx.graphics.getHeight());
        camera.near = NEAR;
        camera.far = FAR;

        shader = new ShaderProgram(VERTEX_SHADER, FRAGMENT_SHADER);
        if (!shader.isCompiled()) {
            throw new GdxRuntimeException("The world shader did not compile: " + shader.getLog());
        }
        modelShader = new ShaderProgram(MODEL_VERTEX_SHADER, MODEL_FRAGMENT_SHADER);
        if (!modelShader.isCompiled()) {
            throw new GdxRuntimeException("The world model shader did not compile: " + modelShader.getLog());
        }

        MapGeometry geometry = MapGeometry.build(map);
        for (Map.Entry<Character, float[]> entry : geometry.walls.entrySet()) {
            surfaces.add(new Surface(buildMesh(entry.getValue()), textures.wall(entry.getKey())));
        }
        surfaces.add(new Surface(buildMesh(geometry.floor), textures.floor));
        surfaces.add(new Surface(buildMesh(geometry.ceiling), textures.ceiling));

        canisterMesh = ModelMesh.loadMesh(WorldModels.CANISTER_PATH, "canister", WorldModels::vertices);
        ringBoardMesh = ModelMesh.loadMesh(WorldModels.RING_BOARD_PATH, "ring board", WorldModels::vertices);
        blastOrbMesh = ModelMesh.loadMesh(WorldModels.BLAST_ORB_PATH, "blast orb", WorldModels::vertices);

        groupStrategy = new CameraGroupStrategy(camera);
        decalBatch = new DecalBatch(groupStrategy);
        scorch = new TextureRegion(textures.scorch);
        spark = new TextureRegion(textures.spark);
        orb = new TextureRegion(textures.orb);
        explosion = new TextureRegion(textures.explosion);
    }

    /** Turns quads from {@link MapGeometry} into an indexed mesh of two triangles per quad. */
    private static Mesh buildMesh(float[] quads) {
        int quadCount = quads.length / MapGeometry.QUAD_SIZE;
        if (quadCount * 4 > 65535) {
            throw new GdxRuntimeException("Too many quads for one mesh: " + quadCount);
        }
        short[] indices = new short[quadCount * 6];
        for (int quad = 0; quad < quadCount; quad++) {
            int first = quad * 4;
            int at = quad * 6;
            indices[at] = (short) first;
            indices[at + 1] = (short) (first + 1);
            indices[at + 2] = (short) (first + 2);
            indices[at + 3] = (short) first;
            indices[at + 4] = (short) (first + 2);
            indices[at + 5] = (short) (first + 3);
        }
        Mesh mesh = new Mesh(true, quadCount * 4, indices.length,
            new VertexAttribute(VertexAttributes.Usage.Position, 3, ShaderProgram.POSITION_ATTRIBUTE),
            new VertexAttribute(VertexAttributes.Usage.TextureCoordinates, 2, ShaderProgram.TEXCOORD_ATTRIBUTE + "0"),
            new VertexAttribute(VertexAttributes.Usage.Generic, 1, "a_shade"));
        mesh.setVertices(quads);
        mesh.setIndices(indices);
        return mesh;
    }

    /**
     * Adapts the camera to a new window size.
     * @param width the new width in pixels
     * @param height the new height in pixels
     */
    public void resize(int width, int height) {
        camera.viewportWidth = width;
        camera.viewportHeight = height;
    }

    /**
     * Clears the screen and draws the world from the player's eyes.
     * @param simulation the game state to draw
     */
    public void render(GameSimulation simulation) {
        Player player = simulation.player;
        // The view is rebuilt from the yaw and pitch every frame; nothing is accumulated on the camera.
        player.eye(camera.position);
        camera.position.y += MathUtils.sin(player.getBobPhase()) * BOB_HEIGHT;
        camera.direction.set(player.direction(direction));
        camera.up.set(Vector3.Y);
        camera.update();

        // A batch may have left depth writes off, and the depth clear obeys that switch.
        Gdx.gl.glDepthMask(true);
        ScreenUtils.clear(FOG_COLOUR, true);
        Gdx.gl.glEnable(GL20.GL_DEPTH_TEST);
        Gdx.gl.glDepthFunc(GL20.GL_LEQUAL);
        Gdx.gl.glEnable(GL20.GL_CULL_FACE);
        Gdx.gl.glCullFace(GL20.GL_BACK);
        Gdx.gl.glDisable(GL20.GL_BLEND);

        float roomLight = simulation.pistol.isFlashVisible() ? 1.3f : 1f;
        shader.bind();
        shader.setUniformMatrix("u_projViewTrans", camera.combined);
        shader.setUniformf("u_cameraPosition", camera.position);
        shader.setUniformf("u_fogRange", FOG_START, FOG_END);
        shader.setUniformf("u_fogColour", FOG_COLOUR.r, FOG_COLOUR.g, FOG_COLOUR.b);
        // The muzzle flash lights the room for a moment.
        shader.setUniformf("u_light", roomLight);
        shader.setUniformi("u_texture", 0);
        for (Surface surface : surfaces) {
            surface.texture.bind(0);
            surface.mesh.render(shader, GL20.GL_TRIANGLES);
        }

        renderWorldModels(simulation, roomLight);

        // Billboards are seen from both sides.
        Gdx.gl.glDisable(GL20.GL_CULL_FACE);
        decalsUsed = 0;
        addProjectiles(simulation);
        addEffects(simulation);
        decalBatch.flush();

        // Leave a plain state for the 2D pass.
        Gdx.gl.glDepthMask(true);
        Gdx.gl.glDisable(GL20.GL_DEPTH_TEST);
        Gdx.gl.glDisable(GL20.GL_BLEND);
    }

    private void renderWorldModels(GameSimulation simulation, float roomLight) {
        modelShader.bind();
        modelShader.setUniformMatrix("u_projViewTrans", camera.combined);
        modelShader.setUniformf("u_cameraPosition", camera.position);
        modelShader.setUniformf("u_fogRange", FOG_START, FOG_END);
        modelShader.setUniformf("u_fogColour", FOG_COLOUR.r, FOG_COLOUR.g, FOG_COLOUR.b);

        modelShader.setUniformf("u_light", roomLight);
        for (Target target : simulation.targets) {
            if (target.isDestroyed()) continue;
            float dx = camera.position.x - target.getX();
            float dz = camera.position.z - target.getZ();
            if (dx * dx + dz * dz < 1e-6f) dz = 1f;
            float yaw = MathUtils.atan2(dx, dz) * MathUtils.radiansToDegrees + 180f;
            modelTransform.idt()
                .translate(target.getX(), 0f, target.getZ())
                .rotate(Vector3.Y, yaw);
            modelShader.setUniformMatrix("u_model", modelTransform);
            modelShader.setUniformf("u_hitFlash", target.getHitFlash());
            Mesh mesh = target.getKind() == Target.Kind.CANISTER ? canisterMesh : ringBoardMesh;
            mesh.render(modelShader, GL20.GL_TRIANGLES);
        }

        modelShader.setUniformf("u_light", 1.35f);
        modelShader.setUniformf("u_hitFlash", 0f);
        float spin = simulation.getTime() * 180f;
        for (BlastProjectile projectile : simulation.projectiles) {
            float pulse = 1f + 0.12f * MathUtils.sin(simulation.getTime() * 30f);
            float scale = (1f + 0.8f * projectile.getCharge()) * pulse;
            modelTransform.idt()
                .translate(projectile.position)
                .rotate(Vector3.Y, spin)
                .rotate(Vector3.X, spin)
                .scale(scale, scale, scale);
            modelShader.setUniformMatrix("u_model", modelTransform);
            blastOrbMesh.render(modelShader, GL20.GL_TRIANGLES);
        }
    }

    private void addProjectiles(GameSimulation simulation) {
        for (BlastProjectile projectile : simulation.projectiles) {
            float pulse = 1f + 0.12f * MathUtils.sin(simulation.getTime() * 30f);
            float size = (0.2f + 0.22f * projectile.getCharge()) * pulse;
            Decal decal = nextDecal(orb, size, size, true);
            decal.setPosition(projectile.position);
            decal.lookAt(camera.position, camera.up);
            decal.setColor(1f, 1f, 1f, 1f);
            decalBatch.add(decal);
        }
    }

    private void addEffects(GameSimulation simulation) {
        for (Effect effect : simulation.effects) {
            float progress = effect.getProgress();
            switch (effect.type) {
                case IMPACT: {
                    Decal mark = nextDecal(scorch, effect.size, effect.size, false);
                    mark.setPosition(effect.position);
                    // Lie flat on the surface; a floor or ceiling mark needs an up vector that is not its normal.
                    mark.setRotation(effect.normal, Math.abs(effect.normal.y) > 0.5f ? Vector3.Z : Vector3.Y);
                    float visible = 1f - fogAt(effect.position);
                    mark.setColor(visible, visible, visible, Math.min(1f, (1f - progress) * 5f));
                    decalBatch.add(mark);
                    if (effect.getAge() < IMPACT_SPARK_TIME) {
                        float fade = 1f - effect.getAge() / IMPACT_SPARK_TIME;
                        addGlow(spark, scratch.set(effect.normal).scl(0.03f).add(effect.position),
                            effect.size * (1.6f + 1.4f * fade), fade);
                    }
                    break;
                }
                case SPARK:
                    addGlow(spark, effect.position, effect.size * (1f + progress), 1f - progress);
                    break;
                case EXPLOSION:
                default: {
                    // Grows quickly to its full size, then fades.
                    float grown = 0.2f + 0.8f * (1f - (1f - progress) * (1f - progress));
                    float alpha = 1f - progress * progress;
                    // The fireball stays small enough to fit under the ceiling.
                    addGlow(explosion, effect.position, Math.min(effect.size * 2f, MAX_FIREBALL_SIZE) * grown, alpha);
                    // A glow spreads over the floor to the full radius, showing the area that took damage.
                    if (effect.size >= MIN_FLOOR_GLOW_RADIUS) {
                        float span = effect.size * 2f * grown;
                        Decal glow = nextDecal(explosion, span, span, true);
                        glow.setPosition(effect.position.x, FLOOR_GLOW_HEIGHT, effect.position.z);
                        glow.setRotation(Vector3.Y, Vector3.Z);
                        glow.setColor(1f, 1f, 1f, alpha * 0.7f);
                        decalBatch.add(glow);
                    }
                    break;
                }
            }
        }
    }

    /** Adds a camera-facing sprite that brightens what is behind it. */
    private void addGlow(TextureRegion region, Vector3 position, float size, float alpha) {
        Decal decal = nextDecal(region, size, size, true);
        decal.setPosition(position);
        decal.lookAt(camera.position, camera.up);
        decal.setColor(1f, 1f, 1f, alpha);
        decalBatch.add(decal);
    }

    /** Takes a decal from the pool, growing the pool when needed, and sets its sprite, size and blending. */
    private Decal nextDecal(TextureRegion region, float width, float height, boolean additive) {
        if (decalsUsed == decals.size) decals.add(Decal.newDecal(width, height, region, true));
        Decal decal = decals.get(decalsUsed++);
        decal.setTextureRegion(region);
        decal.setDimensions(width, height);
        decal.setBlending(GL20.GL_SRC_ALPHA, additive ? GL20.GL_ONE : GL20.GL_ONE_MINUS_SRC_ALPHA);
        return decal;
    }

    private float fogAt(Vector3 position) {
        return MathUtils.clamp((position.dst(camera.position) - FOG_START) / (FOG_END - FOG_START), 0f, 1f);
    }

    @Override
    public void dispose() {
        for (Surface surface : surfaces) surface.mesh.dispose();
        surfaces.clear();
        canisterMesh.dispose();
        ringBoardMesh.dispose();
        blastOrbMesh.dispose();
        shader.dispose();
        modelShader.dispose();
        decalBatch.dispose();
        groupStrategy.dispose();
    }
}

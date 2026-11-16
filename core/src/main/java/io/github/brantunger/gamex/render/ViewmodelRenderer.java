package io.github.brantunger.gamex.render;

import com.badlogic.gdx.Gdx;
import com.badlogic.gdx.graphics.GL20;
import com.badlogic.gdx.graphics.Mesh;
import com.badlogic.gdx.graphics.PerspectiveCamera;
import com.badlogic.gdx.graphics.g2d.TextureRegion;
import com.badlogic.gdx.graphics.g3d.decals.CameraGroupStrategy;
import com.badlogic.gdx.graphics.g3d.decals.Decal;
import com.badlogic.gdx.graphics.g3d.decals.DecalBatch;
import com.badlogic.gdx.graphics.glutils.ShaderProgram;
import com.badlogic.gdx.math.MathUtils;
import com.badlogic.gdx.math.Matrix4;
import com.badlogic.gdx.math.Vector3;
import com.badlogic.gdx.utils.Disposable;
import com.badlogic.gdx.utils.GdxRuntimeException;
import com.badlogic.gdx.utils.ScreenUtils;
import io.github.brantunger.gamex.GameSimulation;
import io.github.brantunger.gamex.settings.GameSettings;
import io.github.brantunger.gamex.weapon.BlastCharger;
import io.github.brantunger.gamex.weapon.HandModel;
import io.github.brantunger.gamex.weapon.PistolModel;
import io.github.brantunger.gamex.weapon.ViewmodelAnimation;

/**
 * Draws the first-person pistol and hand as a 3D model, in a pass of its own between the world and the HUD. The pass
 * has its own camera, fixed to the view and with a narrower field of view than the world's, and it clears the depth
 * buffer first, so the gun is never cut off by a wall the player stands against.
 *
 * <p>The model is lit like the world: each face takes the shade a wall, floor or ceiling facing the same way would
 * have, so the gun's shading shifts as the player turns, plus a fixed light from above the shoulder that keeps its
 * shape readable. There is no fog on it.
 */
public final class ViewmodelRenderer implements Disposable {

    private static final float FIELD_OF_VIEW = 52f;
    private static final float NEAR = 0.02f;
    private static final float FAR = 4f;

    /** Where the gun's origin rests in view space: right of and below the centre of the view. */
    private static final float REST_X = 0.140f;
    private static final float REST_Y = -0.066f;
    private static final float REST_Z = -0.550f;
    /** The point of the gun, in gun space, that it turns about: the middle of the grip. */
    private static final float PIVOT_Y = -0.060f;
    private static final float PIVOT_Z = 0.065f;
    /** The yaw, in degrees, that points the muzzle inwards, towards the centre of the view. */
    private static final float REST_YAW = 12f;
    /** The pitch, in degrees, that raises the muzzle a little at rest. */
    private static final float REST_PITCH = 3f;

    /** The roll, in degrees, that cants the top of the gun to the right, turning its left side to the eye. */
    private static final float REST_ROLL = -2f;

    /** The debug view of the model alone: how far away it is seen from, and the point it turns about. */
    private static final float ORBIT_DISTANCE = 0.62f;
    private static final float ORBIT_CENTRE_X = 0.03f;
    private static final float ORBIT_CENTRE_Y = -0.06f;
    private static final float ORBIT_CENTRE_Z = 0.07f;

    /** How far back and up a shot kicks the gun, in view units, and how far it tips the muzzle up, in degrees. */
    private static final float KICK_BACK = 0.018f;
    private static final float KICK_UP = 0.007f;
    private static final float KICK_PITCH = 6.5f;
    /** How far the slide travels back, in view units. */
    private static final float SLIDE_TRAVEL = 0.034f;

    private static final String VERTEX_SHADER =
        "attribute vec3 a_position;\n"
            + "attribute vec3 a_normal;\n"
            + "attribute vec3 a_colour;\n"
            + "attribute float a_gloss;\n"
            + "uniform mat4 u_projTrans;\n"
            + "uniform mat4 u_model;\n"
            + "uniform mat4 u_viewToWorld;\n"
            + "uniform float u_light;\n"
            + "uniform float u_charge;\n"
            + "varying vec3 v_colour;\n"
            + "void main() {\n"
            + "    vec3 viewNormal = normalize((u_model * vec4(a_normal, 0.0)).xyz);\n"
            + "    vec3 worldNormal = (u_viewToWorld * vec4(viewNormal, 0.0)).xyz;\n"
            // The same shades as the world's faces: north-south 1.0, east-west 0.72, floor 0.9, ceiling 0.62.
            + "    vec3 weight = worldNormal * worldNormal;\n"
            + "    float shade = weight.z + weight.x * 0.72 + weight.y * (worldNormal.y > 0.0 ? 0.9 : 0.62);\n"
            // A fixed light from above the left shoulder, so the form reads whichever way the player faces.
            + "    float key = 0.5 + 0.75 * max(dot(viewNormal, normalize(vec3(-0.3, 0.9, 0.3))), 0.0);\n"
            // The blast charge glows violet towards the muzzle, which is at negative z in gun space.
            + "    float front = clamp((-a_position.z - 0.01) / 0.1, 0.0, 1.0);\n"
            // Glossy faces that sit halfway between the light and the eye pick up a sheen.
            + "    float facing = max(dot(viewNormal, normalize(vec3(-0.3, 0.9, 1.3))), 0.0);\n"
            + "    float sheen = a_gloss * pow(facing, 10.0) * 0.3;\n"
            // Faces seen nearly edge-on catch a faint rim of light, which lifts dark shapes off a dark background.
            + "    float rim = pow(1.0 - abs(viewNormal.z), 2.0) * 0.09;\n"
            + "    v_colour = (a_colour * shade * key + vec3(sheen + rim)) * u_light\n"
            + "        + vec3(0.5, 0.22, 1.0) * u_charge * front * 0.55;\n"
            + "    gl_Position = u_projTrans * u_model * vec4(a_position, 1.0);\n"
            + "}\n";

    private static final String FRAGMENT_SHADER =
        "#ifdef GL_ES\n"
            + "precision mediump float;\n"
            + "#endif\n"
            + "varying vec3 v_colour;\n"
            + "void main() {\n"
            + "    gl_FragColor = vec4(v_colour, 1.0);\n"
            + "}\n";

    private final PerspectiveCamera camera;
    private final ShaderProgram shader;
    private final Mesh body;
    private final Mesh slide;
    /** The sculpted hand and forearm, which move with the body of the pistol. */
    private final Mesh hand;
    private final CameraGroupStrategy groupStrategy;
    private final DecalBatch decalBatch;
    private final Decal flash;
    private final Decal glow;

    private final Matrix4 gunTransform = new Matrix4();
    private final Matrix4 slideTransform = new Matrix4();
    private final Matrix4 viewToWorld = new Matrix4();
    private final Vector3 forward = new Vector3();
    private final Vector3 right = new Vector3();
    private final Vector3 up = new Vector3();
    private final Vector3 muzzle = new Vector3();
    /** The rest x offset for the current window shape. */
    private float restX = REST_X;

    /** @param textures the textures of the muzzle flash and the charge glow; the caller keeps ownership of them */
    public ViewmodelRenderer(ProceduralTextures textures) {
        // The camera never moves: it sits at the origin of view space, looking down negative z.
        camera = new PerspectiveCamera(FIELD_OF_VIEW, Gdx.graphics.getWidth(), Gdx.graphics.getHeight());
        camera.near = NEAR;
        camera.far = FAR;
        camera.position.setZero();
        camera.direction.set(0f, 0f, -1f);
        camera.up.set(Vector3.Y);
        resize(Gdx.graphics.getWidth(), Gdx.graphics.getHeight());

        shader = new ShaderProgram(VERTEX_SHADER, FRAGMENT_SHADER);
        if (!shader.isCompiled()) {
            throw new GdxRuntimeException("The viewmodel shader did not compile: " + shader.getLog());
        }
        PistolModel pistol = ModelMesh.load(PistolModel.ASSET_PATH, "pistol", PistolModel::from);
        body = ModelMesh.build(pistol.body);
        slide = ModelMesh.build(pistol.slide);
        // The hand is a sculpted model file in the pistol's gun space; its normals are smooth, so it shades smoothly.
        hand = ModelMesh.loadMesh(HandModel.ASSET_PATH, "hand", HandModel::vertices);

        groupStrategy = new CameraGroupStrategy(camera);
        decalBatch = new DecalBatch(groupStrategy);
        flash = Decal.newDecal(1f, 1f, new TextureRegion(textures.muzzleFlash), GL20.GL_SRC_ALPHA, GL20.GL_ONE);
        glow = Decal.newDecal(1f, 1f, new TextureRegion(textures.orb), GL20.GL_SRC_ALPHA, GL20.GL_ONE);
    }

    /**
     * Adapts the camera to a new window size.
     * @param width the new width in pixels
     * @param height the new height in pixels
     */
    public void resize(int width, int height) {
        camera.viewportWidth = width;
        camera.viewportHeight = height;
        camera.update();
        // The rest offset is tuned for a 16:9 window; a narrower window pulls the gun in so it stays in view.
        restX = REST_X * MathUtils.clamp((float) width / height / (16f / 9f), 0.6f, 1.2f);
    }

    /**
     * Draws the pistol over the finished world using default viewmodel settings.
     * @param simulation the game state to draw
     */
    public void render(GameSimulation simulation) {
        render(simulation, null);
    }

    /**
     * Draws the pistol over the finished world.
     * @param simulation the game state to draw
     * @param settings optional gameplay settings (view bob), or null
     */
    public void render(GameSimulation simulation, GameSettings settings) {
        if (simulation.player.isDead()) return;
        ViewmodelAnimation animation = simulation.viewmodel;
        float recoil = animation.getRecoil();
        boolean bobEnabled = settings == null || settings.isViewBob();
        float bobX = bobEnabled ? animation.getBobX() : 0f;
        float bobY = bobEnabled ? animation.getBobY() : 0f;

        // Place the gun: at rest lower right, moved by the bob and the kick, and turned about the grip.
        gunTransform.idt()
            .translate(restX + bobX, REST_Y + bobY + recoil * KICK_UP,
                REST_Z + recoil * KICK_BACK)
            .translate(0f, PIVOT_Y, PIVOT_Z)
            // A positive turn about y swings the muzzle left, so a lag to the left (negative sway) adds to it.
            .rotate(Vector3.Y, REST_YAW - animation.getSwayYaw())
            .rotate(Vector3.X, REST_PITCH + recoil * KICK_PITCH + animation.getSwayPitch())
            .rotate(Vector3.Z, REST_ROLL)
            .translate(0f, -PIVOT_Y, -PIVOT_Z);
        slideTransform.set(gunTransform).translate(0f, 0f, animation.getSlide() * SLIDE_TRAVEL);

        // The rotation from view space to world space, built from the player's view axes.
        simulation.player.direction(forward);
        right.set(forward).crs(Vector3.Y).nor();
        up.set(right).crs(forward).nor();
        viewToWorld.idt();
        viewToWorld.val[Matrix4.M00] = right.x;
        viewToWorld.val[Matrix4.M10] = right.y;
        viewToWorld.val[Matrix4.M20] = right.z;
        viewToWorld.val[Matrix4.M01] = up.x;
        viewToWorld.val[Matrix4.M11] = up.y;
        viewToWorld.val[Matrix4.M21] = up.z;
        viewToWorld.val[Matrix4.M02] = -forward.x;
        viewToWorld.val[Matrix4.M12] = -forward.y;
        viewToWorld.val[Matrix4.M22] = -forward.z;

        BlastCharger charger = simulation.charger;
        float charge = charger.isCharging() ? charger.getCharge() : 0f;
        boolean flashing = simulation.pistol.isFlashVisible();
        drawModel(flashing ? 1.5f : 1f, charge);

        // The flash and the charge glow sit just ahead of the muzzle, wherever the animation has put it.
        Gdx.gl.glDisable(GL20.GL_CULL_FACE);
        muzzle.set(PistolModel.MUZZLE_X, PistolModel.MUZZLE_Y, PistolModel.MUZZLE_Z - 0.012f)
            .mul(gunTransform);
        if (flashing) {
            float size = 0.085f;
            flash.setDimensions(size, size);
            flash.setPosition(muzzle);
            // The flash spins from frame to frame for the moment it shows.
            flash.setRotation(0f, 0f, simulation.getTime() * 997f % 360f);
            decalBatch.add(flash);
        }
        if (charge > 0f) {
            float size = (0.03f + 0.10f * charge) * (1f + 0.1f * MathUtils.sin(simulation.getTime() * 34f));
            glow.setDimensions(size, size);
            glow.setPosition(muzzle);
            glow.setRotation(0f, 0f, 0f);
            glow.setColor(1f, 1f, 1f, charger.isReadyToFire() ? 1f : 0.55f);
            decalBatch.add(glow);
        }
        decalBatch.flush();

        // Leave a plain state for the 2D pass.
        Gdx.gl.glDepthMask(true);
        Gdx.gl.glDisable(GL20.GL_DEPTH_TEST);
        Gdx.gl.glDisable(GL20.GL_BLEND);
    }

    /**
     * A debug view: clears the screen to a plain grey and draws the model alone, at rest, seen from outside. It is
     * for judging the model's shape without the world, the HUD or the first-person pose in the way.
     * @param yaw how far the model is turned about its vertical axis, in degrees; 0 shows it from behind, as the
     *     shooter sees it, and 90 shows its left side
     * @param pitch how far the model is tipped towards the eye, in degrees; 90 shows it from above
     */
    public void renderAlone(float yaw, float pitch) {
        ScreenUtils.clear(0.42f, 0.44f, 0.47f, 1f, true);
        gunTransform.idt()
            .translate(0f, 0f, -ORBIT_DISTANCE)
            .rotate(Vector3.X, pitch)
            .rotate(Vector3.Y, yaw)
            .translate(-ORBIT_CENTRE_X, -ORBIT_CENTRE_Y, -ORBIT_CENTRE_Z);
        slideTransform.set(gunTransform);
        viewToWorld.idt();
        drawModel(1f, 0f);
        Gdx.gl.glDisable(GL20.GL_CULL_FACE);
        Gdx.gl.glDisable(GL20.GL_DEPTH_TEST);
    }

    /** Draws both meshes with the transforms set up by the caller, on a fresh depth buffer. */
    private void drawModel(float light, float charge) {
        // A fresh depth buffer: the gun is drawn over the world whatever is close in front of the player.
        Gdx.gl.glDepthMask(true);
        Gdx.gl.glClear(GL20.GL_DEPTH_BUFFER_BIT);
        Gdx.gl.glEnable(GL20.GL_DEPTH_TEST);
        Gdx.gl.glDepthFunc(GL20.GL_LEQUAL);
        Gdx.gl.glEnable(GL20.GL_CULL_FACE);
        Gdx.gl.glCullFace(GL20.GL_BACK);
        Gdx.gl.glDisable(GL20.GL_BLEND);

        shader.bind();
        shader.setUniformMatrix("u_projTrans", camera.combined);
        shader.setUniformMatrix("u_viewToWorld", viewToWorld);
        shader.setUniformf("u_light", light);
        shader.setUniformf("u_charge", charge);
        shader.setUniformMatrix("u_model", gunTransform);
        body.render(shader, GL20.GL_TRIANGLES);
        hand.render(shader, GL20.GL_TRIANGLES);
        shader.setUniformMatrix("u_model", slideTransform);
        slide.render(shader, GL20.GL_TRIANGLES);
    }

    @Override
    public void dispose() {
        body.dispose();
        slide.dispose();
        hand.dispose();
        shader.dispose();
        decalBatch.dispose();
        groupStrategy.dispose();
    }
}

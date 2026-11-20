package io.github.brantunger.gamex.render;

import com.badlogic.gdx.Gdx;
import com.badlogic.gdx.Graphics;
import com.badlogic.gdx.graphics.GL20;
import com.badlogic.gdx.graphics.Mesh;
import com.badlogic.gdx.graphics.PerspectiveCamera;
import com.badlogic.gdx.graphics.g2d.TextureRegion;
import com.badlogic.gdx.graphics.g3d.decals.CameraGroupStrategy;
import com.badlogic.gdx.graphics.g3d.decals.Decal;
import com.badlogic.gdx.graphics.g3d.decals.DecalBatch;
import com.badlogic.gdx.graphics.glutils.ShaderProgram;
import com.badlogic.gdx.math.MathUtils;
import com.badlogic.gdx.math.Matrix3;
import com.badlogic.gdx.math.Matrix4;
import com.badlogic.gdx.math.Vector3;
import com.badlogic.gdx.utils.Array;
import com.badlogic.gdx.utils.Disposable;
import com.badlogic.gdx.utils.GdxRuntimeException;
import com.badlogic.gdx.utils.ScreenUtils;
import io.github.brantunger.gamex.GameSimulation;
import io.github.brantunger.gamex.model.ModelPalette;
import io.github.brantunger.gamex.model.ModelVertices;
import io.github.brantunger.gamex.model.ObjModel;
import io.github.brantunger.gamex.settings.GameSettings;
import io.github.brantunger.gamex.settings.WindowDefaults;
import io.github.brantunger.gamex.weapon.BlastCharger;
import io.github.brantunger.gamex.weapon.HandModel;
import io.github.brantunger.gamex.weapon.PistolModel;
import io.github.brantunger.gamex.weapon.RailLanceModel;
import io.github.brantunger.gamex.weapon.ScattergunModel;
import io.github.brantunger.gamex.weapon.ViewmodelAnimation;
import io.github.brantunger.gamex.weapon.WeaponSlot;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

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

    private static final Logger log = LoggerFactory.getLogger(ViewmodelRenderer.class);

    private static final float FIELD_OF_VIEW = 52f;
    private static final float NEAR = 0.02f;
    private static final float FAR = 4f;

    /** Where the gun's origin rests in view space: right of and below the centre of the view. */
    static final float REST_X = 0.140f;
    private static final float REST_Y = -0.066f;
    private static final float REST_Z = -0.550f;
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

    private PerspectiveCamera camera;
    private ShaderProgram shader;
    private Mesh body;
    private Mesh slide;
    private Mesh scatterBody;
    private Mesh scatterPump;
    private Mesh railBody;
    private Mesh railBolt;
    /** The sculpted hand and forearm, which move with the body of the active weapon. */
    private Mesh hand;
    private CameraGroupStrategy groupStrategy;
    private DecalBatch decalBatch;
    private Decal flash;
    private Decal glow;
    private final Array<Disposable> resourceHandles = new Array<>(10);

    private final Matrix4 gunTransform = new Matrix4();
    private final Matrix4 slideTransform = new Matrix4();
    private final Matrix4 viewToWorld = new Matrix4();
    private final Matrix3 normalMatrix = new Matrix3();
    private ObjModel.SocketSpec muzzleSocket;
    private ObjModel.SocketSpec scatterMuzzleSocket;
    private ObjModel.SocketSpec railMuzzleSocket;
    private final float[] railAccentReady;
    private final float[] railAccentRecharging;
    private final float[] railAccentEmpty;
    private final float[] railAccentScratch = new float[4];
    private final Vector3 forward = new Vector3();
    private final Vector3 right = new Vector3();
    private final Vector3 up = new Vector3();
    private final Vector3 muzzle = new Vector3();
    /** The rest x offset for the current window shape. */
    private float restX = REST_X;

    interface ResourceBackend {
        Disposable createShader(String vertexShader, String fragmentShader);

        boolean isShaderCompiled(Disposable shaderHandle);

        String getShaderLog(Disposable shaderHandle);

        ObjModel loadModel(String assetPath, String label);

        Disposable buildMesh(ModelVertices.IndexedMesh data, String assetPath, String label);

        Disposable createGroupStrategy(PerspectiveCamera camera);

        Disposable createDecalBatch(Disposable groupStrategyHandle);

        void initDecals(ProceduralTextures textures);
    }

    /**
     * @param textures the textures of the muzzle flash and the charge glow; the caller keeps ownership of them
     * @param palettes the colours of the weapons and the hand
     * @param window the size the camera takes until the window reports its own
     */
    public ViewmodelRenderer(ProceduralTextures textures, Palettes palettes, WindowDefaults window) {
        this(Gdx.graphics, textures, palettes, window, null, null);
    }

    /**
     * Builds the viewmodel renderer using pre-parsed and vertex-packed CPU models from {@link AssetPreparer}.
     * @param textures the textures of the muzzle flash and the charge glow; the caller keeps ownership of them
     * @param palettes the colours of the weapons and the hand
     * @param window the size the camera takes until the window reports its own
     * @param prepared optional pre-packed viewmodel meshes prepared off the GL thread
     */
    public ViewmodelRenderer(ProceduralTextures textures, Palettes palettes, WindowDefaults window,
                             AssetPreparer.PreparedViewmodels prepared) {
        this(Gdx.graphics, textures, palettes, window, null, prepared);
    }

    ViewmodelRenderer(Graphics graphics, ProceduralTextures textures, Palettes palettes, WindowDefaults window,
                      ResourceBackend backend) {
        this(graphics, textures, palettes, window, backend, null);
    }

    private ViewmodelRenderer(Graphics graphics, ProceduralTextures textures, Palettes palettes, WindowDefaults window,
                              ResourceBackend backend, AssetPreparer.PreparedViewmodels prepared) {
        railAccentReady = palettes.railLance.accent(RailLanceModel.Accent.READY.name);
        railAccentRecharging = palettes.railLance.accent(RailLanceModel.Accent.RECHARGING.name);
        railAccentEmpty = palettes.railLance.accent(RailLanceModel.Accent.EMPTY.name);
        ResourceBackend active = backend != null ? backend : createGlBackend();
        try {
            // The camera never moves: it sits at the origin of view space, looking down negative z.
            int initialWidth = initialViewportWidth(graphics, window);
            int initialHeight = initialViewportHeight(graphics, window);
            camera = createViewmodelCamera(graphics, window);
            resize(initialWidth, initialHeight);

            // The shader text is read from assets/shaders/; a missing file stops the start here, by name.
            Disposable shaderHandle = active.createShader(
                Shaders.load(Shaders.VIEWMODEL_VERTEX), Shaders.load(Shaders.VIEWMODEL_FRAGMENT));
            shader = trackResource(shaderHandle, ShaderProgram.class);
            if (!active.isShaderCompiled(shaderHandle)) {
                throw new GdxRuntimeException(
                    "The viewmodel shader did not compile: " + active.getShaderLog(shaderHandle)
                        + Shaders.describe(Shaders.VIEWMODEL_VERTEX, Shaders.VIEWMODEL_FRAGMENT));
            }
            int modelsBefore = ModelMesh.getLoadedCount();
            if (prepared != null) {
                muzzleSocket = prepared.pistolMuzzle;
                body = trackResource(active.buildMesh(prepared.pistol.bodyMesh, PistolModel.ASSET_PATH,
                    "viewmodel pistol body"), Mesh.class);
                slide = trackResource(active.buildMesh(prepared.pistol.slideMesh, PistolModel.ASSET_PATH,
                    "viewmodel pistol slide"), Mesh.class);
                scatterMuzzleSocket = prepared.scatterMuzzle;
                scatterBody = trackResource(active.buildMesh(prepared.scattergun.bodyMesh,
                    ScattergunModel.ASSET_PATH, "viewmodel scattergun body"), Mesh.class);
                scatterPump = trackResource(active.buildMesh(prepared.scattergun.pumpMesh,
                    ScattergunModel.ASSET_PATH, "viewmodel scattergun pump"), Mesh.class);
                railMuzzleSocket = prepared.railMuzzle;
                railBody = trackResource(active.buildMesh(prepared.railLance.bodyMesh, RailLanceModel.ASSET_PATH,
                    "viewmodel rail lance body"), Mesh.class);
                railBolt = trackResource(active.buildMesh(prepared.railLance.boltMesh, RailLanceModel.ASSET_PATH,
                    "viewmodel rail lance bolt"), Mesh.class);
                hand = trackResource(active.buildMesh(prepared.hand, HandModel.ASSET_PATH, "viewmodel hand"),
                    Mesh.class);
                log.info("Loaded {} viewmodel models", prepared.modelCount);
            } else {
                ObjModel pistolObj = active.loadModel(PistolModel.ASSET_PATH, "viewmodel pistol");
                PistolModel pistol = unpackPistol(pistolObj, palettes.pistol);
                muzzleSocket = pistolObj.getSocket("muzzle");
                body = trackResource(
                    active.buildMesh(pistol.bodyMesh, PistolModel.ASSET_PATH, "viewmodel pistol body"), Mesh.class);
                slide = trackResource(
                    active.buildMesh(pistol.slideMesh, PistolModel.ASSET_PATH, "viewmodel pistol slide"), Mesh.class);
                ObjModel scatterObj = active.loadModel(ScattergunModel.ASSET_PATH, "viewmodel scattergun");
                ScattergunModel scatter = unpackScattergun(scatterObj, palettes.scattergun);
                scatterMuzzleSocket = scatterObj.getSocket("muzzle");
                scatterBody = trackResource(
                    active.buildMesh(scatter.bodyMesh, ScattergunModel.ASSET_PATH, "viewmodel scattergun body"),
                    Mesh.class);
                scatterPump = trackResource(
                    active.buildMesh(scatter.pumpMesh, ScattergunModel.ASSET_PATH, "viewmodel scattergun pump"),
                    Mesh.class);
                ObjModel railObj = active.loadModel(RailLanceModel.ASSET_PATH, "viewmodel rail lance");
                RailLanceModel rail = unpackRailLance(railObj, palettes.railLance);
                railMuzzleSocket = railObj.getSocket("muzzle");
                railBody = trackResource(
                    active.buildMesh(rail.bodyMesh, RailLanceModel.ASSET_PATH, "viewmodel rail lance body"), Mesh.class);
                railBolt = trackResource(
                    active.buildMesh(rail.boltMesh, RailLanceModel.ASSET_PATH, "viewmodel rail lance bolt"), Mesh.class);
                // The hand is a sculpted model file in the pistol's gun space; its normals are smooth, so it shades smoothly.
                ObjModel handObj = active.loadModel(HandModel.ASSET_PATH, "viewmodel hand");
                hand = trackResource(
                    active.buildMesh(unpackHand(handObj, palettes.hand), HandModel.ASSET_PATH, "viewmodel hand"),
                    Mesh.class);
                log.info("Loaded {} viewmodel models", ModelMesh.getLoadedCount() - modelsBefore);
            }

            Disposable strategyHandle = active.createGroupStrategy(camera);
            trackResource(strategyHandle, CameraGroupStrategy.class);
            decalBatch = trackResource(active.createDecalBatch(strategyHandle), DecalBatch.class);
            active.initDecals(textures);
        } catch (Throwable t) {
            try {
                dispose();
            } catch (Throwable suppressed) {
                t.addSuppressed(suppressed);
            }
            throw t;
        }
    }

    private ResourceBackend createGlBackend() {
        return new ResourceBackend() {
            @Override
            public Disposable createShader(String vertexShader, String fragmentShader) {
                ShaderProgram compiled = new ShaderProgram(vertexShader, fragmentShader);
                shader = compiled;
                return compiled;
            }

            @Override
            public boolean isShaderCompiled(Disposable shaderHandle) {
                return shader != null && shader.isCompiled();
            }

            @Override
            public String getShaderLog(Disposable shaderHandle) {
                return shader != null ? shader.getLog() : "";
            }

            @Override
            public ObjModel loadModel(String assetPath, String label) {
                return ModelMesh.loadModel(assetPath, label);
            }

            @Override
            public Disposable buildMesh(ModelVertices.IndexedMesh data, String assetPath, String label) {
                return ModelMesh.buildIndexed(data, assetPath, label);
            }

            @Override
            public Disposable createGroupStrategy(PerspectiveCamera cam) {
                groupStrategy = new CameraGroupStrategy(cam);
                return null;
            }

            @Override
            public Disposable createDecalBatch(Disposable groupStrategyHandle) {
                DecalBatch batch = new DecalBatch(groupStrategy);
                decalBatch = batch;
                return batch;
            }

            @Override
            public void initDecals(ProceduralTextures textures) {
                if (textures == null) {
                    throw new IllegalArgumentException("textures must not be null");
                }
                flash = Decal.newDecal(
                    1f, 1f, new TextureRegion(textures.muzzleFlash), GL20.GL_SRC_ALPHA, GL20.GL_ONE);
                glow = Decal.newDecal(1f, 1f, new TextureRegion(textures.orb), GL20.GL_SRC_ALPHA, GL20.GL_ONE);
            }
        };
    }

    private <T> T trackResource(Disposable handle, Class<T> type) {
        if (type.isInstance(handle)) {
            return type.cast(handle);
        }
        if (handle != null) {
            resourceHandles.add(handle);
        }
        return null;
    }

    static PistolModel unpackPistol(ObjModel pistolObj, ModelPalette palette) {
        PistolModel pistol = ModelMesh.convert(
            pistolObj, PistolModel.ASSET_PATH, "viewmodel pistol", obj -> PistolModel.from(obj, palette));
        ModelMesh.requireMesh(pistol.bodyMesh, PistolModel.ASSET_PATH, "viewmodel pistol body");
        ModelMesh.requireMesh(pistol.slideMesh, PistolModel.ASSET_PATH, "viewmodel pistol slide");
        return pistol;
    }

    static ScattergunModel unpackScattergun(ObjModel scatterObj, ModelPalette palette) {
        ScattergunModel scatter = ModelMesh.convert(scatterObj, ScattergunModel.ASSET_PATH, "viewmodel scattergun",
            obj -> ScattergunModel.from(obj, palette));
        ModelMesh.requireMesh(scatter.bodyMesh, ScattergunModel.ASSET_PATH, "viewmodel scattergun body");
        ModelMesh.requireMesh(scatter.pumpMesh, ScattergunModel.ASSET_PATH, "viewmodel scattergun pump");
        return scatter;
    }

    static RailLanceModel unpackRailLance(ObjModel railObj, ModelPalette palette) {
        RailLanceModel rail = ModelMesh.convert(railObj, RailLanceModel.ASSET_PATH, "viewmodel rail lance",
            obj -> RailLanceModel.from(obj, palette));
        ModelMesh.requireMesh(rail.bodyMesh, RailLanceModel.ASSET_PATH, "viewmodel rail lance body");
        ModelMesh.requireMesh(rail.boltMesh, RailLanceModel.ASSET_PATH, "viewmodel rail lance bolt");
        return rail;
    }

    static ModelVertices.IndexedMesh unpackHand(ObjModel handObj, ModelPalette palette) {
        return ModelMesh.convertMesh(handObj, HandModel.ASSET_PATH, "viewmodel hand",
            obj -> HandModel.packIndexed(obj, palette));
    }

    static int initialViewportWidth(Graphics graphics, WindowDefaults window) {
        return graphics != null && graphics.getWidth() > 0 && graphics.getHeight() > 0
            ? graphics.getWidth()
            : window.width;
    }

    static int initialViewportHeight(Graphics graphics, WindowDefaults window) {
        return graphics != null && graphics.getWidth() > 0 && graphics.getHeight() > 0
            ? graphics.getHeight()
            : window.height;
    }

    static PerspectiveCamera createViewmodelCamera(Graphics graphics, WindowDefaults window) {
        PerspectiveCamera cam = new PerspectiveCamera();
        cam.fieldOfView = FIELD_OF_VIEW;
        cam.viewportWidth = initialViewportWidth(graphics, window);
        cam.viewportHeight = initialViewportHeight(graphics, window);
        cam.near = NEAR;
        cam.far = FAR;
        cam.position.setZero();
        cam.direction.set(0f, 0f, -1f);
        cam.up.set(Vector3.Y);
        cam.update(Gdx.app != null && Gdx.gl != null);
        return cam;
    }

    static boolean applyViewportResize(PerspectiveCamera camera, int width, int height) {
        if (width <= 0 || height <= 0) return false;
        camera.viewportWidth = width;
        camera.viewportHeight = height;
        camera.update(Gdx.app != null && Gdx.gl != null);
        return true;
    }

    static float computeRestX(int width, int height, float previousRestX) {
        if (width <= 0 || height <= 0) return previousRestX;
        return REST_X * MathUtils.clamp((float) width / height / (16f / 9f), 0.6f, 1.2f);
    }

    /**
     * Adapts the camera to a new window size.
     * @param width the new width in pixels
     * @param height the new height in pixels
     */
    public void resize(int width, int height) {
        if (width <= 0 || height <= 0) return;
        if (camera != null) {
            applyViewportResize(camera, width, height);
        }
        // The rest offset is tuned for a 16:9 window; a narrower window pulls the gun in so it stays in view.
        restX = computeRestX(width, height, restX);
    }

    /**
     * Draws the active weapon viewmodel and hand over the finished world.
     * @param simulation the game state to draw
     * @param settings the live gameplay settings (view bob)
     */
    public void render(GameSimulation simulation, GameSettings settings) {
        if (simulation.player.isDead()) return;
        ViewmodelAnimation animation = simulation.viewmodel;
        float recoil = animation.getRecoil();
        float swapDip = simulation.getSwapProgress();
        boolean bobEnabled = settings.isViewBob();
        float bobX = bobEnabled ? animation.getBobX() : 0f;
        float bobY = bobEnabled ? animation.getBobY() : 0f;

        WeaponSlot activeWeapon = simulation.getActiveWeapon();
        float zoom = activeWeapon == WeaponSlot.RAIL_LANCE ? simulation.getZoom() : 0f;
        float ads = zoom * zoom * (3f - 2f * zoom);
        float swayScale = 1f - 0.72f * ads;

        float vy = simulation.player.getVerticalVelocity();
        float landDip = simulation.player.getLandingDip();
        float airOffsetY = (MathUtils.clamp(-vy * 0.0045f, -0.012f, 0.012f) - landDip * 0.014f) * swayScale;
        float airPitch = (MathUtils.clamp(-vy * 1.1f, -3.0f, 3.0f) - landDip * 2.8f) * swayScale;

        float baseX = MathUtils.lerp(restX, 0f, ads) + bobX * swayScale;
        float baseY = MathUtils.lerp(REST_Y, -0.076f, ads) + bobY * swayScale + airOffsetY
            + recoil * KICK_UP * (1f - 0.25f * ads) - swapDip * 0.14f;
        float baseZ = MathUtils.lerp(REST_Z, -0.500f, ads) + recoil * KICK_BACK + swapDip * 0.045f;
        float baseYaw = MathUtils.lerp(REST_YAW, 0f, ads) - animation.getSwayYaw() * swayScale;
        float basePitch = MathUtils.lerp(REST_PITCH, -0.6f, ads) + airPitch
            + recoil * KICK_PITCH * (1f - 0.35f * ads) + animation.getSwayPitch() * swayScale - swapDip * 20f;
        float baseRoll = MathUtils.lerp(REST_ROLL, 0f, ads);

        // Place the active weapon: at rest lower right (or centered low-ADS when zoomed), shifted by bob, kick, swap and sway.
        gunTransform.idt()
            .translate(baseX, baseY, baseZ)
            .translate(0f, PistolModel.GRIP_PIVOT_Y, PistolModel.GRIP_PIVOT_Z)
            // A positive turn about y swings the muzzle left, so a lag to the left (negative sway) adds to it.
            .rotate(Vector3.Y, baseYaw)
            .rotate(Vector3.X, basePitch)
            .rotate(Vector3.Z, baseRoll)
            .translate(0f, -PistolModel.GRIP_PIVOT_Y, -PistolModel.GRIP_PIVOT_Z);

        Mesh weaponBody;
        Mesh weaponMoving;
        float movingZ;
        ObjModel.SocketSpec activeSocket;
        float defaultMx;
        float defaultMy;
        float defaultMz;
        float[] accent = null;
        if (activeWeapon == WeaponSlot.SCATTERGUN) {
            weaponBody = scatterBody;
            weaponMoving = scatterPump;
            movingZ = simulation.scattergun.getPump() * ScattergunModel.PUMP_TRAVEL;
            activeSocket = scatterMuzzleSocket;
            defaultMx = ScattergunModel.MUZZLE_X;
            defaultMy = ScattergunModel.MUZZLE_Y;
            defaultMz = ScattergunModel.MUZZLE_Z;
        } else if (activeWeapon == WeaponSlot.RAIL_LANCE) {
            weaponBody = railBody;
            weaponMoving = railBolt;
            movingZ = simulation.railLance.getBolt() * RailLanceModel.BOLT_TRAVEL;
            activeSocket = railMuzzleSocket;
            defaultMx = RailLanceModel.MUZZLE_X;
            defaultMy = RailLanceModel.MUZZLE_Y;
            defaultMz = RailLanceModel.MUZZLE_Z;
            accent = railAccent(simulation);
        } else {
            weaponBody = body;
            weaponMoving = slide;
            movingZ = animation.getSlide() * SLIDE_TRAVEL;
            activeSocket = muzzleSocket;
            defaultMx = PistolModel.MUZZLE_X;
            defaultMy = PistolModel.MUZZLE_Y;
            defaultMz = PistolModel.MUZZLE_Z;
        }
        slideTransform.set(gunTransform).translate(0f, 0f, movingZ);

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
        float charge = activeWeapon == WeaponSlot.PISTOL && charger.isCharging() ? charger.getCharge() : 0f;
        boolean flashing = simulation.isMuzzleFlashVisible();
        drawModel(weaponBody, weaponMoving, flashing ? 1.5f : 1f, charge, accent);

        // The flash and the charge glow sit just ahead of the muzzle, wherever the animation has put it.
        Gdx.gl.glDisable(GL20.GL_CULL_FACE);
        float mx = activeSocket != null ? activeSocket.x : defaultMx;
        float my = activeSocket != null ? activeSocket.y : defaultMy;
        float mz = activeSocket != null ? activeSocket.z : defaultMz;
        muzzle.set(mx, my, mz - 0.014f).mul(gunTransform);
        if (flashing) {
            float size;
            if (activeWeapon == WeaponSlot.SCATTERGUN) {
                size = simulation.scattergun.isLastShotDoubleBlast() ? 0.175f : 0.135f;
                flash.setColor(1f, 1f, 1f, 1f);
            } else if (activeWeapon == WeaponSlot.RAIL_LANCE) {
                size = 0.155f;
                flash.setColor(0.32f, 0.96f, 1f, 1f);
            } else {
                size = 0.085f;
                flash.setColor(1f, 1f, 1f, 1f);
            }
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
        Gdx.gl.glDepthMask(false);
        try {
            decalBatch.flush();
        } finally {
            Gdx.gl.glDepthMask(true);
        }

        // Leave a plain state for the 2D pass.
        Gdx.gl.glDisable(GL20.GL_DEPTH_TEST);
        Gdx.gl.glDisable(GL20.GL_BLEND);
    }

    /**
     * A debug view: clears the screen to a plain grey and draws the pistol model alone, at rest, seen from outside.
     */
    public void renderAlone(float yaw, float pitch) {
        renderAlone(WeaponSlot.PISTOL, yaw, pitch);
    }

    /**
     * A debug view: clears the screen to a plain grey and draws {@code weapon} and the hand alone, seen from outside.
     * @param weapon weapon viewmodel to preview
     * @param yaw orbit yaw in degrees
     * @param pitch orbit pitch in degrees
     */
    public void renderAlone(WeaponSlot weapon, float yaw, float pitch) {
        ScreenUtils.clear(0.42f, 0.44f, 0.47f, 1f, true);
        boolean scatter = weapon == WeaponSlot.SCATTERGUN;
        boolean rail = weapon == WeaponSlot.RAIL_LANCE;
        float dist = rail ? ORBIT_DISTANCE + 0.18f : (scatter ? ORBIT_DISTANCE + 0.08f : ORBIT_DISTANCE);
        float zShift = rail ? 0.02f : (scatter ? -0.01f : -ORBIT_CENTRE_Z);
        gunTransform.idt()
            .translate(0f, 0f, -dist)
            .rotate(Vector3.X, pitch)
            .rotate(Vector3.Y, yaw)
            .translate(-ORBIT_CENTRE_X, -ORBIT_CENTRE_Y, zShift);
        slideTransform.set(gunTransform);
        viewToWorld.idt();
        Mesh weaponBody = rail ? railBody : (scatter ? scatterBody : body);
        Mesh weaponMoving = rail ? railBolt : (scatter ? scatterPump : slide);
        drawModel(weaponBody, weaponMoving, 1f, 0f, rail ? railAccentReady : null);
        Gdx.gl.glDisable(GL20.GL_CULL_FACE);
        Gdx.gl.glDisable(GL20.GL_DEPTH_TEST);
    }

    private float[] railAccent(GameSimulation simulation) {
        if (simulation.getSlugs() <= 0) return railAccentEmpty;
        float charge = simulation.railLance.getChargeProgress();
        if (charge >= 0.999f) return railAccentReady;
        for (int i = 0; i < 4; i++) {
            railAccentScratch[i] = MathUtils.lerp(railAccentRecharging[i], railAccentReady[i], charge);
        }
        return railAccentScratch;
    }

    /** Draws weapon body, hand, and moving slide/pump/bolt with the transforms set up by the caller. */
    private void drawModel(Mesh weaponBody, Mesh movingPart, float light, float charge, float[] accent) {
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
        if (accent == null) shader.setUniformf("u_accentColour", 0f, 0f, 0f, 0f);
        else shader.setUniformf("u_accentColour", accent[0], accent[1], accent[2], accent[3]);
        shader.setUniformf("u_light", light);
        shader.setUniformf("u_charge", charge);
        shader.setUniformMatrix("u_model", gunTransform);
        normalMatrix.set(gunTransform).inv().transpose();
        shader.setUniformMatrix("u_normalMatrix", normalMatrix);
        weaponBody.render(shader, GL20.GL_TRIANGLES);
        hand.render(shader, GL20.GL_TRIANGLES);
        shader.setUniformMatrix("u_model", slideTransform);
        normalMatrix.set(slideTransform).inv().transpose();
        shader.setUniformMatrix("u_normalMatrix", normalMatrix);
        movingPart.render(shader, GL20.GL_TRIANGLES);
    }

    @Override
    public void dispose() {
        body = disposeQuietly(body);
        slide = disposeQuietly(slide);
        scatterBody = disposeQuietly(scatterBody);
        scatterPump = disposeQuietly(scatterPump);
        railBody = disposeQuietly(railBody);
        railBolt = disposeQuietly(railBolt);
        hand = disposeQuietly(hand);
        shader = disposeQuietly(shader);
        decalBatch = disposeQuietly(decalBatch);
        groupStrategy = disposeGroupStrategy(groupStrategy);
        for (int i = resourceHandles.size - 1; i >= 0; i--) {
            disposeQuietly(resourceHandles.get(i));
        }
        resourceHandles.clear();
    }

    static <D extends Disposable> D disposeQuietly(D resource) {
        if (resource != null) {
            try {
                resource.dispose();
            } catch (RuntimeException ignored) {
                // Isolate cleanup so one failing resource never skips disposing the rest.
            }
        }
        return null;
    }

    private static CameraGroupStrategy disposeGroupStrategy(CameraGroupStrategy strategy) {
        if (strategy != null) {
            try {
                strategy.dispose();
            } catch (RuntimeException ignored) {
                // Isolate cleanup so one failing resource never skips disposing the rest.
            }
        }
        return null;
    }
}

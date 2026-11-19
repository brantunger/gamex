package io.github.brantunger.gamex.render;

import com.badlogic.gdx.Gdx;
import com.badlogic.gdx.Graphics;
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
import com.badlogic.gdx.math.Matrix3;
import com.badlogic.gdx.math.Matrix4;
import com.badlogic.gdx.math.Vector3;
import com.badlogic.gdx.utils.Array;
import com.badlogic.gdx.utils.Disposable;
import com.badlogic.gdx.utils.GdxRuntimeException;
import com.badlogic.gdx.utils.ScreenUtils;
import io.github.brantunger.gamex.GameSimulation;
import io.github.brantunger.gamex.model.ModelPalette;
import io.github.brantunger.gamex.model.ModelRig;
import io.github.brantunger.gamex.model.ModelVertices;
import io.github.brantunger.gamex.model.ObjModel;
import io.github.brantunger.gamex.player.Player;
import io.github.brantunger.gamex.settings.GameSettings;
import io.github.brantunger.gamex.weapon.BlastProjectile;
import io.github.brantunger.gamex.weapon.EnemyProjectile;
import io.github.brantunger.gamex.world.Door;
import io.github.brantunger.gamex.world.Effect;
import io.github.brantunger.gamex.world.Enemy;
import io.github.brantunger.gamex.world.GameMap;
import io.github.brantunger.gamex.world.MapGeometry;
import io.github.brantunger.gamex.world.Pickup;
import io.github.brantunger.gamex.world.SecurityConsole;
import io.github.brantunger.gamex.world.Target;
import io.github.brantunger.gamex.world.WorldModels;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.function.Function;

/**
 * Draws the 3D view: the map as a few static meshes, one per texture, the targets, pickups and blast projectiles as
 * 3D models loaded from OBJ files, the Warden as an animated glTF model, and the halos, exit pad and effects as
 * billboards.
 */
public final class WorldRenderer implements Disposable {

    private static final Logger log = LoggerFactory.getLogger(WorldRenderer.class);

    static final int DEFAULT_VIEWPORT_WIDTH = 1280;
    static final int DEFAULT_VIEWPORT_HEIGHT = 720;
    private static final float FIELD_OF_VIEW = 70f;
    /** How far the view rises and falls with each step, in world units. */
    private static final float BOB_HEIGHT = 0.012f;
    /** How long the spark of a pistol impact shows over its scorch mark, in seconds. */
    private static final float IMPACT_SPARK_TIME = 0.12f;
    /** The widest an explosion's fireball is drawn, so that it fits between the floor and the ceiling. */
    private static final float MAX_FIREBALL_SIZE = 0.8f;
    /** Explosions with at least this radius also light up the floor they cover. */
    private static final float MIN_FLOOR_GLOW_RADIUS = 1f;
    /** How far horizontal floor-glow decals sit above the floor (above the 0.024f top of shock grates). */
    static final float FLOOR_GLOW_HEIGHT = 0.028f;

    /** A static mesh and the texture it is drawn with. */
    static final class Surface implements Disposable {
        final Mesh mesh;
        private Disposable handle;
        final Texture texture;

        Surface(Mesh mesh, Texture texture) {
            this.mesh = mesh;
            this.handle = mesh;
            this.texture = texture;
        }

        Surface(Disposable handle, Texture texture) {
            this.mesh = null;
            this.handle = handle;
            this.texture = texture;
        }

        @Override
        public void dispose() {
            handle = disposeQuietly(handle);
        }
    }

    @FunctionalInterface
    interface SurfaceMeshBuilder {
        Surface buildSurface(float[] quads, Texture texture);
    }

    interface ResourceBackend {
        void checkWardenModel();

        Disposable createWorldShader(String vertexShader, String fragmentShader);

        boolean isWorldShaderCompiled(Disposable shaderHandle);

        String getWorldShaderLog(Disposable shaderHandle);

        Disposable createModelShader(String vertexShader, String fragmentShader);

        boolean isModelShaderCompiled(Disposable shaderHandle);

        String getModelShaderLog(Disposable shaderHandle);

        Surface buildSurface(float[] quads, Texture texture);

        ObjModel loadModel(String assetPath, String label);

        Disposable loadIndexedMesh(String assetPath, String label,
                                   Function<ObjModel, ModelVertices.IndexedMesh> converter);

        Disposable buildIndexedMesh(ModelVertices.IndexedMesh data, String assetPath, String label);

        Disposable createGroupStrategy(PerspectiveCamera camera);

        Disposable createDecalBatch(Disposable groupStrategyHandle);

        void initTextureRegions(ProceduralTextures textures);
    }

    private PerspectiveCamera camera;
    private final ProceduralTextures textures;
    private final ModelPalette palette;
    private final RenderSettings renderSettings;
    private final Color fogColour;
    /** The accent colours of the world palette, by {@link WorldModels.Accent#ordinal()}. */
    private final float[][] accents;
    /** The colour of the halo under each kind of pickup, by {@link Pickup.Kind#ordinal()}. */
    private final float[][] pickupHalos;
    private final float[] consoleHaloActive;
    private final float[] consoleHaloLocked;
    private final SurfaceMeshBuilder surfaceMeshBuilder;
    private ShaderProgram shader;
    private ShaderProgram modelShader;
    private final Array<Surface> surfaces = new Array<>();
    private final Array<Disposable> resourceHandles = new Array<>(36);
    private GameMap currentMap;
    private Mesh canisterMesh;
    private Mesh ringBoardMesh;
    private Mesh blastOrbMesh;
    private Mesh medkitMesh;
    private Mesh armorMesh;
    private Mesh energyCellMesh;
    private Mesh scattergunPickupMesh;
    private Mesh shellsPickupMesh;
    private Mesh railLancePickupMesh;
    private Mesh railSlugsPickupMesh;
    private Mesh railBeamMesh;
    private Mesh turretBaseMesh;
    private Mesh turretHeadMesh;
    private Mesh turretBarrelsMesh;
    private Mesh droneMesh;
    private Mesh guardPelvisMesh;
    private Mesh guardLegLeftMesh;
    private Mesh guardLegRightMesh;
    private Mesh guardTorsoMesh;
    private Mesh guardArmLeftMesh;
    private Mesh guardArmRightMesh;
    private Mesh enemyBoltMesh;
    private Mesh doorMesh;
    private Mesh hazardGrateMesh;
    private Mesh keycardMesh;
    private Mesh consoleMesh;
    private ModelRig turretRig;
    private ModelRig droneRig;
    private ModelRig guardRig;
    /** The Warden's model, loaded the first time a stage with a live Warden is drawn and kept from then on. */
    private WardenModel wardenModel;
    private CameraGroupStrategy groupStrategy;
    private DecalBatch decalBatch;
    private final Array<Decal> decals = new Array<>();
    private int decalsUsed;

    private TextureRegion scorch;
    private TextureRegion spark;
    private TextureRegion orb;
    private TextureRegion explosion;
    private TextureRegion exitPad;

    private final Matrix4 modelTransform = new Matrix4();
    private final Matrix4 rootTransform = new Matrix4();
    private final Matrix3 normalMatrix = new Matrix3();
    private final Matrix4 rotYaw = new Matrix4();
    private final Matrix4 rotPitch = new Matrix4();
    private final Matrix4 rotLegL = new Matrix4();
    private final Matrix4 rotLegR = new Matrix4();
    private final Matrix4 rotArmL = new Matrix4();
    private final Matrix4 rotArmR = new Matrix4();
    private final Map<String, Matrix4> turretRotations = new HashMap<>();
    private final Map<String, Matrix4> guardRotations = new HashMap<>();
    private final Vector3 direction = new Vector3();
    private final Vector3 scratch = new Vector3();
    private final Vector3 billboardDir = new Vector3();
    private final Vector3 billboardUp = new Vector3();
    private final Color scratchColor = new Color();

    /**
     * @param map the initial map to build the static meshes from
     * @param textures the textures to draw with; the caller keeps ownership of them
     * @param palette the colours of the world models' materials and accents
     * @param settings the clip range, the fog, the halos and the accent of each kind of door and pickup
     */
    public WorldRenderer(GameMap map, ProceduralTextures textures, ModelPalette palette, RenderSettings settings) {
        this(map, Gdx.graphics, textures, palette, settings, null);
    }

    WorldRenderer(GameMap map, Graphics graphics, ProceduralTextures textures, ModelPalette palette,
                  RenderSettings settings, ResourceBackend backend) {
        this.textures = textures;
        this.palette = palette;
        this.renderSettings = settings;
        fogColour = new Color(settings.fogRed, settings.fogGreen, settings.fogBlue, 1f);
        // Looked up once, so that drawing a frame neither searches the palette nor copies a colour.
        WorldModels.Accent[] accentNames = WorldModels.Accent.values();
        accents = new float[accentNames.length][];
        for (WorldModels.Accent accent : accentNames) accents[accent.ordinal()] = palette.accent(accent.name);
        Pickup.Kind[] pickupKinds = Pickup.Kind.values();
        pickupHalos = new float[pickupKinds.length][];
        for (Pickup.Kind kind : pickupKinds) pickupHalos[kind.ordinal()] = settings.pickupHalo(kind);
        consoleHaloActive = settings.consoleHalo(true);
        consoleHaloLocked = settings.consoleHalo(false);
        ResourceBackend active = backend != null ? backend : createGlBackend();
        this.surfaceMeshBuilder = active::buildSurface;
        try {
            // The Warden's model is loaded later; whether it is there to load is found out now.
            active.checkWardenModel();
            int modelsBefore = ModelMesh.getLoadedCount();
            camera = createWorldCamera(graphics, settings);

            // The shader text is read from assets/shaders/; a missing file stops the start here, by name.
            Disposable worldShaderHandle = active.createWorldShader(
                Shaders.load(Shaders.WORLD_VERTEX), Shaders.load(Shaders.WORLD_FRAGMENT));
            shader = trackResource(worldShaderHandle, ShaderProgram.class);
            if (!active.isWorldShaderCompiled(worldShaderHandle)) {
                throw new GdxRuntimeException(
                    "The world shader did not compile: " + active.getWorldShaderLog(worldShaderHandle)
                        + Shaders.describe(Shaders.WORLD_VERTEX, Shaders.WORLD_FRAGMENT));
            }
            Disposable modelShaderHandle = active.createModelShader(
                Shaders.load(Shaders.MODEL_VERTEX), Shaders.load(Shaders.MODEL_FRAGMENT));
            modelShader = trackResource(modelShaderHandle, ShaderProgram.class);
            if (!active.isModelShaderCompiled(modelShaderHandle)) {
                throw new GdxRuntimeException(
                    "The world model shader did not compile: " + active.getModelShaderLog(modelShaderHandle)
                        + Shaders.describe(Shaders.MODEL_VERTEX, Shaders.MODEL_FRAGMENT));
            }

            if (backend == null && textures == null) {
                throw new IllegalArgumentException("textures must not be null");
            }
            setMap(map);

            canisterMesh = loadSingleWorldMesh(active, WorldModels.CANISTER_PATH, "canister");
            ringBoardMesh = loadSingleWorldMesh(active, WorldModels.RING_BOARD_PATH, "ring board");
            blastOrbMesh = loadSingleWorldMesh(active, WorldModels.BLAST_ORB_PATH, "blast orb");
            medkitMesh = loadSingleWorldMesh(active, WorldModels.MEDKIT_PATH, "medkit");
            armorMesh = loadSingleWorldMesh(active, WorldModels.ARMOR_PATH, "armor");
            energyCellMesh = loadSingleWorldMesh(active, WorldModels.ENERGY_CELL_PATH, "energy cell");
            scattergunPickupMesh = loadSingleWorldMesh(
                active, WorldModels.SCATTERGUN_PICKUP_PATH, "scattergun pickup");
            shellsPickupMesh = loadSingleWorldMesh(active, WorldModels.SHELLS_PICKUP_PATH, "shells pickup");
            railLancePickupMesh = loadSingleWorldMesh(
                active, WorldModels.RAIL_LANCE_PICKUP_PATH, "rail lance pickup");
            railSlugsPickupMesh = loadSingleWorldMesh(
                active, WorldModels.RAIL_SLUGS_PICKUP_PATH, "rail slugs pickup");
            railBeamMesh = loadSingleWorldMesh(active, WorldModels.RAIL_BEAM_PATH, "rail beam");

            ObjModel turretObj = active.loadModel(WorldModels.TURRET_PATH, "turret");
            turretRig = new ModelRig(turretObj);
            turretBaseMesh = buildWorldPartMesh(active, turretObj, WorldModels.TURRET_PATH, "turret", "base");
            turretHeadMesh = buildWorldPartMesh(active, turretObj, WorldModels.TURRET_PATH, "turret", "head");
            turretBarrelsMesh = buildWorldPartMesh(active, turretObj, WorldModels.TURRET_PATH, "turret", "barrels");

            ObjModel droneObj = active.loadModel(WorldModels.DRONE_PATH, "drone");
            droneRig = new ModelRig(droneObj);
            droneMesh = trackResource(active.buildIndexedMesh(
                unpackWorldModel(droneObj, WorldModels.DRONE_PATH, "drone", palette), WorldModels.DRONE_PATH,
                "drone"),
                Mesh.class);

            ObjModel guardObj = active.loadModel(WorldModels.GUARD_PATH, "guard");
            guardRig = new ModelRig(guardObj);
            guardPelvisMesh = buildWorldPartMesh(active, guardObj, WorldModels.GUARD_PATH, "guard", "pelvis");
            guardLegLeftMesh = buildWorldPartMesh(active, guardObj, WorldModels.GUARD_PATH, "guard", "leg_l");
            guardLegRightMesh = buildWorldPartMesh(active, guardObj, WorldModels.GUARD_PATH, "guard", "leg_r");
            guardTorsoMesh = buildWorldPartMesh(active, guardObj, WorldModels.GUARD_PATH, "guard", "torso");
            guardArmLeftMesh = buildWorldPartMesh(active, guardObj, WorldModels.GUARD_PATH, "guard", "arm_l");
            guardArmRightMesh = buildWorldPartMesh(active, guardObj, WorldModels.GUARD_PATH, "guard", "arm_r");

            enemyBoltMesh = loadSingleWorldMesh(active, WorldModels.ENEMY_BOLT_PATH, "enemy bolt");
            doorMesh = loadSingleWorldMesh(active, WorldModels.DOOR_BULKHEAD_PATH, "door bulkhead");
            hazardGrateMesh = loadSingleWorldMesh(active, WorldModels.HAZARD_GRATE_PATH, "hazard grate");
            keycardMesh = loadSingleWorldMesh(active, WorldModels.KEYCARD_PATH, "keycard");
            consoleMesh = loadSingleWorldMesh(active, WorldModels.SECURITY_CONSOLE_PATH, "security console");

            turretRotations.put("head", rotYaw);
            turretRotations.put("barrels", rotPitch);
            guardRotations.put("leg_l", rotLegL);
            guardRotations.put("leg_r", rotLegR);
            guardRotations.put("arm_l", rotArmL);
            guardRotations.put("arm_r", rotArmR);

            Disposable strategyHandle = active.createGroupStrategy(camera);
            trackResource(strategyHandle, CameraGroupStrategy.class);
            decalBatch = trackResource(active.createDecalBatch(strategyHandle), DecalBatch.class);
            active.initTextureRegions(textures);
            log.info("Loaded {} world models", ModelMesh.getLoadedCount() - modelsBefore);
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
            public void checkWardenModel() {
                WardenModel.checkFile();
            }

            @Override
            public Disposable createWorldShader(String vertexShader, String fragmentShader) {
                ShaderProgram compiled = new ShaderProgram(vertexShader, fragmentShader);
                shader = compiled;
                return compiled;
            }

            @Override
            public boolean isWorldShaderCompiled(Disposable shaderHandle) {
                return shader != null && shader.isCompiled();
            }

            @Override
            public String getWorldShaderLog(Disposable shaderHandle) {
                return shader != null ? shader.getLog() : "";
            }

            @Override
            public Disposable createModelShader(String vertexShader, String fragmentShader) {
                ShaderProgram compiled = new ShaderProgram(vertexShader, fragmentShader);
                modelShader = compiled;
                return compiled;
            }

            @Override
            public boolean isModelShaderCompiled(Disposable shaderHandle) {
                return modelShader != null && modelShader.isCompiled();
            }

            @Override
            public String getModelShaderLog(Disposable shaderHandle) {
                return modelShader != null ? modelShader.getLog() : "";
            }

            @Override
            public Surface buildSurface(float[] quads, Texture texture) {
                return new Surface(buildMesh(quads), texture);
            }

            @Override
            public ObjModel loadModel(String assetPath, String label) {
                return ModelMesh.loadModel(assetPath, label);
            }

            @Override
            public Disposable loadIndexedMesh(String assetPath, String label,
                                              Function<ObjModel, ModelVertices.IndexedMesh> converter) {
                return ModelMesh.loadIndexedMesh(assetPath, label, converter);
            }

            @Override
            public Disposable buildIndexedMesh(ModelVertices.IndexedMesh data, String assetPath, String label) {
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
            public void initTextureRegions(ProceduralTextures tex) {
                scorch = new TextureRegion(tex.scorch);
                spark = new TextureRegion(tex.spark);
                orb = new TextureRegion(tex.orb);
                explosion = new TextureRegion(tex.explosion);
                exitPad = new TextureRegion(tex.exitPad);
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

    private Mesh loadSingleWorldMesh(ResourceBackend active, String assetPath, String label) {
        return trackResource(
            active.loadIndexedMesh(assetPath, label, obj -> WorldModels.packIndexed(obj, palette)), Mesh.class);
    }

    private Mesh buildWorldPartMesh(ResourceBackend active, ObjModel model, String assetPath, String modelLabel,
                                    String partName) {
        String label = modelLabel + " " + partName;
        return trackResource(
            active.buildIndexedMesh(unpackWorldPart(model, assetPath, modelLabel, partName, palette), assetPath,
                label),
            Mesh.class);
    }

    /** Rebuilds the static map geometry when loading a different stage. */
    public void setMap(GameMap map) {
        if (map == null) {
            throw new IllegalArgumentException("map must not be null");
        }
        if (map == currentMap) return;
        MapGeometry geometry = MapGeometry.build(map);
        log.debug("Built the geometry of a {}x{} map: {} wall textures", map.getWidth(), map.getHeight(),
            geometry.walls.size());
        Array<Surface> nextSurfaces = new Array<>(geometry.walls.size() + 2);
        try {
            for (Map.Entry<Character, float[]> entry : geometry.walls.entrySet()) {
                Texture wallTexture = textures != null ? textures.wall(entry.getKey()) : null;
                nextSurfaces.add(surfaceMeshBuilder.buildSurface(entry.getValue(), wallTexture));
            }
            nextSurfaces.add(surfaceMeshBuilder.buildSurface(
                geometry.floor, textures != null ? textures.floor : null));
            nextSurfaces.add(surfaceMeshBuilder.buildSurface(
                geometry.ceiling, textures != null ? textures.ceiling : null));
        } catch (Throwable t) {
            disposeSurfaces(nextSurfaces);
            throw t;
        }
        disposeSurfaces(surfaces);
        surfaces.addAll(nextSurfaces);
        currentMap = map;
    }

    GameMap getCurrentMap() {
        return currentMap;
    }

    int getSurfaceCount() {
        return surfaces.size;
    }

    static ModelVertices.IndexedMesh unpackWorldPart(ObjModel model, String assetPath, String modelLabel,
                                                     String partName, ModelPalette palette) {
        String label = modelLabel + " " + partName;
        return ModelMesh.convertMesh(model, assetPath, label,
            obj -> WorldModels.packPartIndexed(obj, partName, palette));
    }

    static ModelVertices.IndexedMesh unpackWorldModel(ObjModel model, String assetPath, String modelLabel,
                                                      ModelPalette palette) {
        return ModelMesh.convertMesh(model, assetPath, modelLabel, obj -> WorldModels.packIndexed(obj, palette));
    }

    static short[] buildQuadIndices(float[] quads) {
        if (quads == null || quads.length % MapGeometry.QUAD_SIZE != 0) {
            throw new GdxRuntimeException("Invalid map quad buffer length: "
                + (quads == null ? "null" : quads.length));
        }
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
        return indices;
    }

    /** Turns quads from {@link MapGeometry} into an indexed mesh of two triangles per quad. */
    private static Mesh buildMesh(float[] quads) {
        short[] indices = buildQuadIndices(quads);
        int quadCount = quads.length / MapGeometry.QUAD_SIZE;
        Mesh mesh = new Mesh(true, quadCount * 4, indices.length,
            new VertexAttribute(VertexAttributes.Usage.Position, 3, ShaderProgram.POSITION_ATTRIBUTE),
            new VertexAttribute(VertexAttributes.Usage.TextureCoordinates, 2, ShaderProgram.TEXCOORD_ATTRIBUTE + "0"),
            new VertexAttribute(VertexAttributes.Usage.Generic, 1, "a_shade"));
        try {
            mesh.setVertices(quads);
            mesh.setIndices(indices);
            return mesh;
        } catch (Throwable t) {
            disposeQuietly(mesh);
            throw t;
        }
    }

    static int initialViewportWidth(Graphics graphics) {
        return graphics != null && graphics.getWidth() > 0 && graphics.getHeight() > 0
            ? graphics.getWidth()
            : DEFAULT_VIEWPORT_WIDTH;
    }

    static int initialViewportHeight(Graphics graphics) {
        return graphics != null && graphics.getWidth() > 0 && graphics.getHeight() > 0
            ? graphics.getHeight()
            : DEFAULT_VIEWPORT_HEIGHT;
    }

    static PerspectiveCamera createWorldCamera(Graphics graphics, RenderSettings settings) {
        PerspectiveCamera cam = new PerspectiveCamera();
        cam.fieldOfView = FIELD_OF_VIEW;
        cam.viewportWidth = initialViewportWidth(graphics);
        cam.viewportHeight = initialViewportHeight(graphics);
        cam.near = settings.clipNear;
        cam.far = settings.clipFar;
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

    /**
     * Adapts the camera to a new window size.
     * @param width the new width in pixels
     * @param height the new height in pixels
     */
    public void resize(int width, int height) {
        if (width <= 0 || height <= 0 || camera == null) return;
        applyViewportResize(camera, width, height);
    }

    /**
     * Clears the screen and draws the world from the player's eyes.
     * @param simulation the game state to draw
     * @param settings the live gameplay settings (FOV, view bob)
     */
    public void render(GameSimulation simulation, GameSettings settings) {
        setMap(simulation.map);
        Player player = simulation.player;
        float zoom = simulation.getZoom();
        float zoomScale = zoom > 0f ? 1f / (1f + (simulation.getZoomFactor() - 1f) * zoom) : 1f;
        camera.fieldOfView = settings.getFov() * zoomScale;
        player.eye(camera.position);
        if (settings.isViewBob() && !player.isDead()) {
            camera.position.y += MathUtils.sin(player.getBobPhase()) * BOB_HEIGHT;
        }
        camera.direction.set(player.direction(direction));
        camera.up.set(Vector3.Y);
        camera.update();

        float animTime = resolveAnimationTime(simulation, 0f, false);
        float roomLight = simulation.isMuzzleFlashVisible() ? 1.3f : 1f;
        beginWorldPass(roomLight);
        renderWorldModels(simulation, roomLight, animTime, false);

        // Billboards are seen from both sides.
        Gdx.gl.glDisable(GL20.GL_CULL_FACE);
        decalsUsed = 0;
        addExitPad(simulation.map, animTime);
        addPickupGlows(simulation);
        addConsoleGlows(simulation);
        addProjectiles(simulation, animTime);
        addEnemyTelegraphsAndBolts(simulation, animTime, false);
        addRailBeamDecals(simulation);
        addEffects(simulation);
        Gdx.gl.glDepthMask(false);
        try {
            decalBatch.flush();
        } finally {
            Gdx.gl.glDepthMask(true);
        }

        endWorldPass();
    }

    /**
     * Draws a gentle panning camera preview of {@code simulation} for the Title and Briefing backdrops.
     */
    public void renderBackdrop(GameSimulation simulation, GameSettings settings, float previewTime) {
        setMap(simulation.map);
        camera.fieldOfView = settings.getFov();
        camera.position.set(simulation.map.getSpawnX(), Player.EYE_HEIGHT + 0.05f, simulation.map.getSpawnZ());
        float yaw = simulation.map.getSpawnYaw() + MathUtils.sin(previewTime * 0.35f) * 26f;
        float pitch = MathUtils.sin(previewTime * 0.22f) * 4f - 2f;
        double yawRad = Math.toRadians(yaw);
        double pitchRad = Math.toRadians(pitch);
        double flat = Math.cos(pitchRad);
        camera.direction.set((float) (Math.sin(yawRad) * flat), (float) Math.sin(pitchRad),
            (float) (-Math.cos(yawRad) * flat));
        camera.up.set(Vector3.Y);
        camera.update();

        float animTime = resolveAnimationTime(simulation, previewTime, true);
        beginWorldPass(0.92f);
        renderWorldModels(simulation, 0.92f, animTime, true);
        Gdx.gl.glDisable(GL20.GL_CULL_FACE);
        decalsUsed = 0;
        addExitPad(simulation.map, animTime);
        addPickupGlows(simulation);
        addConsoleGlows(simulation);
        addEnemyTelegraphsAndBolts(simulation, animTime, true);
        Gdx.gl.glDepthMask(false);
        try {
            decalBatch.flush();
        } finally {
            Gdx.gl.glDepthMask(true);
        }
        endWorldPass();
    }

    private void beginWorldPass(float roomLight) {
        Gdx.gl.glDepthMask(true);
        ScreenUtils.clear(fogColour, true);
        Gdx.gl.glEnable(GL20.GL_DEPTH_TEST);
        Gdx.gl.glDepthFunc(GL20.GL_LEQUAL);
        Gdx.gl.glEnable(GL20.GL_CULL_FACE);
        Gdx.gl.glCullFace(GL20.GL_BACK);
        Gdx.gl.glDisable(GL20.GL_BLEND);

        shader.bind();
        shader.setUniformMatrix("u_projViewTrans", camera.combined);
        shader.setUniformf("u_cameraPosition", camera.position);
        shader.setUniformf("u_fogRange", renderSettings.fogStart, renderSettings.fogEnd);
        shader.setUniformf("u_fogColour", fogColour.r, fogColour.g, fogColour.b);
        shader.setUniformf("u_light", roomLight);
        shader.setUniformi("u_texture", 0);
        for (Surface surface : surfaces) {
            surface.texture.bind(0);
            surface.mesh.render(shader, GL20.GL_TRIANGLES);
        }
    }

    private void endWorldPass() {
        Gdx.gl.glDepthMask(true);
        Gdx.gl.glDisable(GL20.GL_DEPTH_TEST);
        Gdx.gl.glDisable(GL20.GL_BLEND);
    }

    static float resolveAnimationTime(GameSimulation simulation, float previewTime, boolean backdrop) {
        return backdrop ? previewTime : simulation.getTime();
    }

    static float hazardGratePulse(float animTime, int col, int row) {
        return 0.5f + 0.5f * MathUtils.sin(animTime * 7.5f + col * 1.1f + row * 0.7f);
    }

    static float pickupHoverY(float animTime, float x, float z) {
        return 0.08f + 0.035f * MathUtils.sin(animTime * 3.5f + x + z);
    }

    static float pickupSpinDegrees(float animTime, float x, float z) {
        return animTime * 95f + (x + z) * 45f;
    }

    static float droneHoverY(float animTime, float x) {
        return 0.03f * MathUtils.sin(animTime * 5.5f + x * 2f);
    }

    static List<BlastProjectile> visibleProjectiles(GameSimulation simulation, boolean backdrop) {
        return backdrop ? Collections.emptyList() : simulation.projectiles;
    }

    static List<EnemyProjectile> visibleEnemyProjectiles(GameSimulation simulation, boolean backdrop) {
        return backdrop ? Collections.emptyList() : simulation.enemyProjectiles;
    }

    static List<GameSimulation.RailBeam> visibleRailBeams(GameSimulation simulation, boolean backdrop) {
        return backdrop ? Collections.emptyList() : simulation.railBeams;
    }

    private void renderWorldModels(GameSimulation simulation, float roomLight, float animTime, boolean backdrop) {
        modelShader.bind();
        modelShader.setUniformMatrix("u_projViewTrans", camera.combined);
        modelShader.setUniformf("u_cameraPosition", camera.position);
        modelShader.setUniformf("u_fogRange", renderSettings.fogStart, renderSettings.fogEnd);
        modelShader.setUniformf("u_fogColour", fogColour.r, fogColour.g, fogColour.b);

        modelShader.setUniformf("u_light", roomLight);
        setAccent(null);
        List<GameMap.HazardCell> hazardCells = simulation.map.getHazardCells();
        if (!hazardCells.isEmpty()) {
            float[] hazardLive = accent(WorldModels.Accent.HAZARD_LIVE);
            modelShader.setUniformf("u_hitFlash", 0f);
            for (int i = 0; i < hazardCells.size(); i++) {
                GameMap.HazardCell cell = hazardCells.get(i);
                float pulse = hazardGratePulse(animTime, cell.col, cell.row);
                modelShader.setUniformf("u_light", Math.max(roomLight, 0.92f + 0.34f * pulse));
                modelShader.setUniformf("u_accentColour",
                    hazardLive[0] * (0.72f + 0.28f * pulse),
                    hazardLive[1] * (0.78f + 0.22f * pulse),
                    hazardLive[2],
                    hazardLive[3]);
                modelTransform.idt().translate(cell.x, 0f, cell.z);
                drawModelMesh(hazardGrateMesh, modelTransform);
            }
            modelShader.setUniformf("u_light", roomLight);
            setAccent(null);
        }

        for (Target target : simulation.targets) {
            if (target.isDestroyed()) continue;
            float dx = camera.position.x - target.getX();
            float dz = camera.position.z - target.getZ();
            if (dx * dx + dz * dz < 1e-6f) dz = 1f;
            float yaw = MathUtils.atan2(dx, dz) * MathUtils.radiansToDegrees + 180f;
            modelTransform.idt()
                .translate(target.getX(), 0f, target.getZ())
                .rotate(Vector3.Y, yaw);
            modelShader.setUniformf("u_hitFlash", target.getHitFlash());
            Mesh mesh = target.getKind() == Target.Kind.CANISTER ? canisterMesh : ringBoardMesh;
            drawModelMesh(mesh, modelTransform);
        }

        // Hostile 3D enemies with hierarchical local-pivot articulation and wind-up luminance.
        for (Enemy enemy : simulation.enemies) {
            if (enemy.isDestroyed()) continue;
            float windup = enemy.getWindupProgress();
            modelShader.setUniformf("u_light", roomLight + 0.45f * windup);
            modelShader.setUniformf("u_hitFlash", enemy.getHitFlash());
            switch (enemy.getKind()) {
                case TURRET:
                    setAccent(null);
                    prepareTurretPose(enemy);
                    drawModelMesh(turretBaseMesh,
                        turretRig.evaluatePartTransform("base", turretRotations, rootTransform, modelTransform));
                    drawModelMesh(turretHeadMesh,
                        turretRig.evaluatePartTransform("head", turretRotations, rootTransform, modelTransform));
                    drawModelMesh(turretBarrelsMesh,
                        turretRig.evaluatePartTransform("barrels", turretRotations, rootTransform, modelTransform));
                    break;
                case GUARD:
                    setAccent(null);
                    prepareGuardPose(enemy);
                    drawModelMesh(guardPelvisMesh,
                        guardRig.evaluatePartTransform("pelvis", guardRotations, rootTransform, modelTransform));
                    drawModelMesh(guardLegLeftMesh,
                        guardRig.evaluatePartTransform("leg_l", guardRotations, rootTransform, modelTransform));
                    drawModelMesh(guardLegRightMesh,
                        guardRig.evaluatePartTransform("leg_r", guardRotations, rootTransform, modelTransform));
                    drawModelMesh(guardTorsoMesh,
                        guardRig.evaluatePartTransform("torso", guardRotations, rootTransform, modelTransform));
                    drawModelMesh(guardArmLeftMesh,
                        guardRig.evaluatePartTransform("arm_l", guardRotations, rootTransform, modelTransform));
                    drawModelMesh(guardArmRightMesh,
                        guardRig.evaluatePartTransform("arm_r", guardRotations, rootTransform, modelTransform));
                    break;
                case WARDEN:
                    // Drawn by renderWardens, after the models of this shader.
                    break;
                case DRONE:
                default:
                    setAccent(null);
                    prepareDronePose(enemy, animTime);
                    drawModelMesh(droneMesh, rootTransform);
                    break;
            }
        }

        // Hovering and spinning 3D pickups.
        modelShader.setUniformf("u_light", 1.2f);
        modelShader.setUniformf("u_hitFlash", 0f);
        for (Pickup pickup : simulation.pickups) {
            if (pickup.isCollected()) continue;
            float hover = pickupHoverY(animTime, pickup.getX(), pickup.getZ());
            modelTransform.idt()
                .translate(pickup.getX(), hover, pickup.getZ())
                .rotate(Vector3.Y, pickupSpinDegrees(animTime, pickup.getX(), pickup.getZ()));
            setAccent(accent(renderSettings.pickupAccent(pickup.getKind())));
            drawModelMesh(pickupMesh(pickup.getKind()), modelTransform);
        }

        setAccent(null);
        List<BlastProjectile> projectiles = visibleProjectiles(simulation, backdrop);
        if (!projectiles.isEmpty()) {
            modelShader.setUniformf("u_light", 1.35f);
            modelShader.setUniformf("u_hitFlash", 0f);
            float spin = animTime * 180f;
            for (BlastProjectile projectile : projectiles) {
                float scale = 0.85f + 0.55f * projectile.getCharge();
                modelTransform.idt()
                    .translate(projectile.position)
                    .rotate(Vector3.Z, 26f)
                    .rotate(Vector3.Y, spin)
                    .scale(scale, scale, scale);
                drawModelMesh(blastOrbMesh, modelTransform);
            }
        }

        List<EnemyProjectile> enemyBolts = visibleEnemyProjectiles(simulation, backdrop);
        if (!enemyBolts.isEmpty()) {
            modelShader.setUniformf("u_light", 1.4f);
            modelShader.setUniformf("u_hitFlash", 0f);
            float boltSpin = animTime * 220f;
            for (EnemyProjectile bolt : enemyBolts) {
                modelTransform.idt()
                    .translate(bolt.position)
                    .rotate(Vector3.Z, 26f)
                    .rotate(Vector3.Y, boltSpin);
                drawModelMesh(enemyBoltMesh, modelTransform);
            }
        }

        // Sliding 3D bulkhead doors and security console pedestals (single shared mesh each with u_accentColour).
        modelShader.setUniformf("u_hitFlash", 0f);
        for (Door door : simulation.doors) {
            float yaw = door.getOrientation() == Door.Orientation.SPAN_Z ? 90f : 0f;
            modelTransform.idt()
                .translate(door.getX(), door.getBottomY(), door.getZ())
                .rotate(Vector3.Y, yaw);
            modelShader.setUniformf("u_light", door.isOpening() ? Math.max(roomLight, 1.08f) : roomLight);
            setAccent(doorAccent(door));
            drawModelMesh(doorMesh, modelTransform);
        }

        modelShader.setUniformf("u_light", Math.max(roomLight, 1.05f));
        for (SecurityConsole console : simulation.consoles) {
            modelTransform.idt().translate(console.getX(), 0f, console.getZ());
            setAccent(accent(console.isActivated()
                ? WorldModels.Accent.CONSOLE_ACTIVE
                : WorldModels.Accent.CONSOLE_LOCKED));
            drawModelMesh(consoleMesh, modelTransform);
        }

        List<GameSimulation.RailBeam> railBeams = visibleRailBeams(simulation, backdrop);
        if (!railBeams.isEmpty()) {
            modelShader.setUniformf("u_hitFlash", 0f);
            for (GameSimulation.RailBeam beam : railBeams) {
                scratch.set(beam.end).sub(beam.start);
                float length = scratch.len();
                if (length < 1e-4f) continue;
                scratch.scl(1f / length);
                float horiz = (float) Math.hypot(scratch.x, scratch.z);
                float pitchDeg = MathUtils.atan2(scratch.y, horiz) * MathUtils.radiansToDegrees;
                float yawDeg = horiz > 1e-5f
                    ? MathUtils.atan2(-scratch.x, -scratch.z) * MathUtils.radiansToDegrees
                    : 0f;
                float p = beam.getProgress();
                float fade = 1f - p;
                float thickness = Math.max(0.10f, 1.05f - p * 0.95f);
                modelTransform.idt()
                    .translate(beam.start)
                    .rotate(Vector3.Y, yawDeg)
                    .rotate(Vector3.X, pitchDeg)
                    .scale(thickness, thickness, length);
                modelShader.setUniformf("u_light", 1.12f + 0.28f * fade);
                modelShader.setUniformf("u_accentColour",
                    0.08f + 0.22f * fade, 0.78f + 0.18f * fade, 1.00f, 1.00f);
                drawModelMesh(railBeamMesh, modelTransform);
            }
            setAccent(null);
        }

        renderWardens(simulation, roomLight, animTime);
    }

    /**
     * Draws the Wardens with their own shader, into the same depth buffer as the rest of the world, and then puts
     * back the state the world pass runs with. A stage without a live Warden neither loads the model nor touches
     * the state.
     */
    private void renderWardens(GameSimulation simulation, float roomLight, float animTime) {
        if (!WardenModel.isNeeded(simulation.enemies)) {
            if (wardenModel != null) wardenModel.releasePuppets();
            return;
        }
        if (wardenModel == null) {
            wardenModel = new WardenModel(fogColour, renderSettings.fogStart, renderSettings.fogEnd,
                accent(WorldModels.Accent.WARDEN_PHASE1), accent(WorldModels.Accent.WARDEN_OVERDRIVE));
        }
        wardenModel.draw(camera, simulation.enemies, animTime, roomLight);

        Gdx.gl.glEnable(GL20.GL_DEPTH_TEST);
        Gdx.gl.glDepthFunc(GL20.GL_LEQUAL);
        Gdx.gl.glDepthMask(true);
        Gdx.gl.glEnable(GL20.GL_CULL_FACE);
        Gdx.gl.glCullFace(GL20.GL_BACK);
        Gdx.gl.glDisable(GL20.GL_BLEND);
        Gdx.gl.glActiveTexture(GL20.GL_TEXTURE0);
        modelShader.bind();
    }

    private void drawModelMesh(Mesh mesh, Matrix4 transform) {
        modelShader.setUniformMatrix("u_model", transform);
        normalMatrix.set(transform).inv().transpose();
        modelShader.setUniformMatrix("u_normalMatrix", normalMatrix);
        mesh.render(modelShader, GL20.GL_TRIANGLES);
    }

    private void setAccent(float[] rgbaGloss) {
        if (rgbaGloss == null) {
            modelShader.setUniformf("u_accentColour", 0f, 0f, 0f, 0f);
        } else {
            modelShader.setUniformf("u_accentColour", rgbaGloss[0], rgbaGloss[1], rgbaGloss[2], rgbaGloss[3]);
        }
    }

    private void prepareTurretPose(Enemy enemy) {
        rootTransform.idt().translate(enemy.getX(), 0f, enemy.getZ());
        rotYaw.setToRotation(Vector3.Y, -enemy.getYaw());
        rotPitch.setToRotation(Vector3.X, enemy.getAimPitch());
    }

    private void prepareGuardPose(Enemy enemy) {
        float swing = MathUtils.sin(enemy.getWalkPhase());
        float bobY = Math.abs(swing) * 0.018f;
        float strideDeg = swing * 26f;
        rootTransform.idt()
            .translate(enemy.getX(), bobY, enemy.getZ())
            .rotate(Vector3.Y, -enemy.getYaw());
        rotLegL.setToRotation(Vector3.X, strideDeg);
        rotLegR.setToRotation(Vector3.X, -strideDeg);
        rotArmL.setToRotation(Vector3.X, -strideDeg * 0.8f);
        rotArmR.setToRotation(Vector3.X, -strideDeg * 0.15f);
    }

    private void prepareDronePose(Enemy enemy, float time) {
        float hoverY = droneHoverY(time, enemy.getX());
        float tiltPitch = (enemy.getState() == Enemy.State.WINDUP
            || enemy.getState() == Enemy.State.LUNGING)
            ? -16f
            : (enemy.getState() == Enemy.State.PURSUING ? -7f : 0f);
        rootTransform.idt()
            .translate(enemy.getX(), hoverY, enemy.getZ())
            .rotate(Vector3.Y, -enemy.getYaw())
            .rotate(Vector3.X, tiltPitch);
    }

    /** @return the colour of {@code accent} in the world palette, or null for no accent */
    private float[] accent(WorldModels.Accent accent) {
        return accent == null ? null : accents[accent.ordinal()];
    }

    private float[] doorAccent(Door door) {
        return accent(door.isOpening() ? renderSettings.doorOpeningAccent
            : renderSettings.doorAccent(door.getLockKind()));
    }

    private Mesh pickupMesh(Pickup.Kind kind) {
        switch (kind) {
            case MEDKIT:
                return medkitMesh;
            case ARMOR:
                return armorMesh;
            case AMBER_KEYCARD:
            case CYAN_KEYCARD:
                return keycardMesh;
            case SCATTERGUN:
                return scattergunPickupMesh;
            case SHELLS:
                return shellsPickupMesh;
            case RAIL_LANCE:
                return railLancePickupMesh;
            case RAIL_SLUGS:
                return railSlugsPickupMesh;
            case ENERGY_CELL:
            default:
                return energyCellMesh;
        }
    }

    private void addExitPad(GameMap map, float animTime) {
        if (!map.hasExit()) return;
        float pulse = 0.82f + 0.14f * MathUtils.sin(animTime * 4.5f);
        Decal pad = nextDecal(exitPad, 0.86f, 0.86f, true);
        pad.setPosition(map.getExitX(), FLOOR_GLOW_HEIGHT, map.getExitZ());
        pad.setRotation(Vector3.Y, Vector3.Z);
        pad.setColor(0.55f, 1f, 0.95f, pulse);
        decalBatch.add(pad);

        Decal beacon = nextDecal(orb, 0.42f * pulse, 0.62f, true);
        beacon.setPosition(map.getExitX(), 0.36f, map.getExitZ());
        faceCamera(beacon);
        beacon.setColor(0.45f, 1f, 0.92f, 0.55f * pulse);
        decalBatch.add(beacon);
    }

    private void addPickupGlows(GameSimulation simulation) {
        for (Pickup pickup : simulation.pickups) {
            if (pickup.isCollected()) continue;
            Decal halo = nextDecal(orb, renderSettings.pickupHaloSize, renderSettings.pickupHaloSize, true);
            halo.setPosition(pickup.getX(), FLOOR_GLOW_HEIGHT, pickup.getZ());
            halo.setRotation(Vector3.Y, Vector3.Z);
            float[] colour = pickupHalos[pickup.getKind().ordinal()];
            halo.setColor(colour[0], colour[1], colour[2], colour[3]);
            decalBatch.add(halo);
        }
    }

    private void addConsoleGlows(GameSimulation simulation) {
        for (SecurityConsole console : simulation.consoles) {
            Decal halo = nextDecal(orb, renderSettings.consoleHaloSize, renderSettings.consoleHaloSize, true);
            halo.setPosition(console.getX(), FLOOR_GLOW_HEIGHT, console.getZ());
            halo.setRotation(Vector3.Y, Vector3.Z);
            float[] colour = console.isActivated() ? consoleHaloActive : consoleHaloLocked;
            halo.setColor(colour[0], colour[1], colour[2], colour[3]);
            decalBatch.add(halo);
        }
    }

    private void addProjectiles(GameSimulation simulation, float animTime) {
        for (BlastProjectile projectile : simulation.projectiles) {
            float meshScale = 0.85f + 0.55f * projectile.getCharge();
            float meshOuterRadius = BlastProjectile.RADIUS * meshScale;
            float baseSize = 0.26f + 0.20f * projectile.getCharge();
            scratch.set(camera.position).sub(projectile.position);
            float camDist = scratch.len();
            float pullForward = camDist > 1e-4f
                ? Math.min(meshOuterRadius + 0.02f, Math.max(0f, camDist - renderSettings.clipNear - 0.03f))
                : 0f;
            float decalDist = camDist - pullForward;
            float perspectiveScale = camDist > 1e-4f ? decalDist / camDist : 1f;
            float size = baseSize * perspectiveScale;
            if (camDist > 1e-4f) scratch.scl(pullForward / camDist).add(projectile.position);
            else scratch.set(projectile.position);
            Decal decal = nextDecal(orb, size, size, true);
            decal.setPosition(scratch);
            faceCamera(decal);
            float visible = 1f - fogAt(projectile.position);
            float shimmer = 0.92f + 0.08f * MathUtils.sin(animTime * 14f);
            decal.setColor(visible, visible, visible, visible * shimmer * 0.82f);
            decalBatch.add(decal);
        }
    }

    private void addEnemyTelegraphsAndBolts(GameSimulation simulation, float animTime, boolean backdrop) {
        for (Enemy enemy : simulation.enemies) {
            if (enemy.isDestroyed()) continue;
            float windup = enemy.getWindupProgress();
            if (windup <= 0f) continue;
            switch (enemy.getKind()) {
                case TURRET:
                    prepareTurretPose(enemy);
                    turretRig.evaluateSocketWorld("telegraph", turretRotations, rootTransform, scratch);
                    break;
                case GUARD:
                    prepareGuardPose(enemy);
                    guardRig.evaluateSocketWorld("telegraph", guardRotations, rootTransform, scratch);
                    break;
                case WARDEN:
                    rootTransform.idt()
                        .translate(enemy.getX(), 0f, enemy.getZ())
                        .rotate(Vector3.Y, -enemy.getYaw());
                    scratch.set(0f, WorldModels.WARDEN_TELEGRAPH_Y, WorldModels.WARDEN_TELEGRAPH_Z)
                        .mul(rootTransform);
                    break;
                case DRONE:
                default:
                    prepareDronePose(enemy, animTime);
                    droneRig.evaluateSocketWorld("telegraph", null, rootTransform, scratch);
                    break;
            }
            float size = (enemy.getKind() == Enemy.Kind.WARDEN ? 0.24f : 0.14f)
                + (enemy.getKind() == Enemy.Kind.WARDEN ? 0.24f : 0.18f) * windup;
            Decal flare = nextDecal(orb, size, size, true);
            flare.setPosition(scratch);
            faceCamera(flare);
            if (enemy.getKind() == Enemy.Kind.WARDEN && enemy.isOverdrive()) {
                flare.setColor(0.36f, 0.72f + 0.25f * windup, 1f, 0.55f + 0.40f * windup);
            } else {
                flare.setColor(1f, 0.35f + 0.25f * windup, 0.18f, 0.45f + 0.45f * windup);
            }
            decalBatch.add(flare);
        }
        for (EnemyProjectile bolt : visibleEnemyProjectiles(simulation, backdrop)) {
            Decal halo = nextDecal(orb, 0.24f, 0.24f, true);
            halo.setPosition(bolt.position);
            faceCamera(halo);
            halo.setColor(1f, 0.28f, 0.32f, 0.95f);
            decalBatch.add(halo);
        }
    }

    private void addRailBeamDecals(GameSimulation simulation) {
        for (GameSimulation.RailBeam beam : simulation.railBeams) {
            scratch.set(beam.end).sub(beam.start);
            float len = scratch.len();
            if (len < 0.1f) continue;
            scratch.scl(1f / len);
            float p = beam.getProgress();
            float fade = 1f - p;
            float step = Math.max(0.85f, len / 30f);
            float size = (0.16f + 0.08f * fade) * (1f + 0.25f * p);
            for (float s = 0.35f; s < len - 0.10f; s += step) {
                float px = beam.start.x + scratch.x * s;
                float py = beam.start.y + scratch.y * s;
                float pz = beam.start.z + scratch.z * s;
                float dist = (float) Math.hypot(px - camera.position.x,
                    Math.hypot(py - camera.position.y, pz - camera.position.z));
                float fog = renderSettings.fogFactor(dist);
                Decal ring = nextDecal(orb, size, size, true);
                ring.setPosition(px, py, pz);
                faceCamera(ring);
                ring.setColor(0.24f, 0.94f, 1.00f, (1f - fog) * fade * 0.78f);
                decalBatch.add(ring);
            }
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
                    scorchColor(effect.position, camera.position, progress, renderSettings, scratchColor);
                    mark.setColor(scratchColor.r, scratchColor.g, scratchColor.b, scratchColor.a);
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
                    float grown = 0.2f + 0.8f * (1f - (1f - progress) * (1f - progress));
                    float alpha = 1f - progress * progress;
                    addGlow(explosion, effect.position, Math.min(effect.size * 2f, MAX_FIREBALL_SIZE) * grown, alpha);
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

    private void addGlow(TextureRegion region, Vector3 position, float size, float alpha) {
        Decal decal = nextDecal(region, size, size, true);
        decal.setPosition(position);
        faceCamera(decal);
        decal.setColor(1f, 1f, 1f, alpha);
        decalBatch.add(decal);
    }

    private void faceCamera(Decal decal) {
        faceCamera(decal, camera.position, camera.direction, camera.up, billboardDir, billboardUp);
    }

    static void faceCamera(Decal decal, Vector3 camPos, Vector3 camDir, Vector3 camUp) {
        faceCamera(decal, camPos, camDir, camUp, new Vector3(), new Vector3());
    }

    private static void faceCamera(Decal decal, Vector3 camPos, Vector3 camDir, Vector3 camUp,
                                   Vector3 dirScratch, Vector3 upScratch) {
        Vector3 decalPos = decal.getPosition();
        dirScratch.set(camPos).sub(decalPos);
        if (dirScratch.len2() < 1e-8f) {
            if (camDir != null && camDir.len2() >= 1e-8f) {
                dirScratch.set(camDir).scl(-1f).nor();
            } else {
                dirScratch.set(Vector3.Z);
            }
        } else {
            dirScratch.nor();
        }

        boolean resolvedUp = false;
        float camUpDot = 1f;
        if (camUp != null && camUp.len2() >= 1e-8f) {
            upScratch.set(camUp).nor();
            camUpDot = dirScratch.dot(upScratch);
            if (Math.abs(camUpDot) <= 0.999f) {
                resolvedUp = true;
            }
        }
        if (!resolvedUp && camDir != null && camDir.len2() >= 1e-8f) {
            upScratch.set(camDir).nor();
            if (camUpDot < 0f) upScratch.scl(-1f);
            if (Math.abs(dirScratch.dot(upScratch)) <= 0.999f) {
                resolvedUp = true;
            }
        }
        if (!resolvedUp) {
            upScratch.set(Math.abs(dirScratch.y) > 0.9f ? Vector3.Z : Vector3.Y);
        }

        decal.setRotation(dirScratch, upScratch);
        decal.getRotation().nor();
    }

    private Decal nextDecal(TextureRegion region, float width, float height, boolean additive) {
        if (decalsUsed == decals.size) decals.add(Decal.newDecal(width, height, region, true));
        Decal decal = decals.get(decalsUsed++);
        decal.setTextureRegion(region);
        decal.setDimensions(width, height);
        decal.setBlending(GL20.GL_SRC_ALPHA, additive ? GL20.GL_ONE : GL20.GL_ONE_MINUS_SRC_ALPHA);
        return decal;
    }

    static Color scorchColor(Vector3 position, Vector3 cameraPosition, float progress, RenderSettings settings,
                             Color out) {
        float visible = 1f - settings.fogFactor(position.dst(cameraPosition));
        float alpha = visible * MathUtils.clamp((1f - progress) * 5f, 0f, 1f);
        return out.set(1f, 1f, 1f, alpha);
    }

    private float fogAt(Vector3 position) {
        return renderSettings.fogFactor(position.dst(camera.position));
    }

    @Override
    public void dispose() {
        disposeSurfaces(surfaces);
        currentMap = null;
        decals.clear();
        decalsUsed = 0;
        canisterMesh = disposeQuietly(canisterMesh);
        ringBoardMesh = disposeQuietly(ringBoardMesh);
        blastOrbMesh = disposeQuietly(blastOrbMesh);
        medkitMesh = disposeQuietly(medkitMesh);
        armorMesh = disposeQuietly(armorMesh);
        energyCellMesh = disposeQuietly(energyCellMesh);
        scattergunPickupMesh = disposeQuietly(scattergunPickupMesh);
        shellsPickupMesh = disposeQuietly(shellsPickupMesh);
        railLancePickupMesh = disposeQuietly(railLancePickupMesh);
        railSlugsPickupMesh = disposeQuietly(railSlugsPickupMesh);
        railBeamMesh = disposeQuietly(railBeamMesh);
        turretBaseMesh = disposeQuietly(turretBaseMesh);
        turretHeadMesh = disposeQuietly(turretHeadMesh);
        turretBarrelsMesh = disposeQuietly(turretBarrelsMesh);
        droneMesh = disposeQuietly(droneMesh);
        guardPelvisMesh = disposeQuietly(guardPelvisMesh);
        guardLegLeftMesh = disposeQuietly(guardLegLeftMesh);
        guardLegRightMesh = disposeQuietly(guardLegRightMesh);
        guardTorsoMesh = disposeQuietly(guardTorsoMesh);
        guardArmLeftMesh = disposeQuietly(guardArmLeftMesh);
        guardArmRightMesh = disposeQuietly(guardArmRightMesh);
        wardenModel = disposeQuietly(wardenModel);
        enemyBoltMesh = disposeQuietly(enemyBoltMesh);
        doorMesh = disposeQuietly(doorMesh);
        hazardGrateMesh = disposeQuietly(hazardGrateMesh);
        keycardMesh = disposeQuietly(keycardMesh);
        consoleMesh = disposeQuietly(consoleMesh);
        shader = disposeQuietly(shader);
        modelShader = disposeQuietly(modelShader);
        decalBatch = disposeQuietly(decalBatch);
        groupStrategy = disposeGroupStrategy(groupStrategy);
        for (int i = resourceHandles.size - 1; i >= 0; i--) {
            disposeQuietly(resourceHandles.get(i));
        }
        resourceHandles.clear();
    }

    private static void disposeSurfaces(Array<Surface> list) {
        if (list == null) return;
        for (int i = 0; i < list.size; i++) {
            Surface surface = list.get(i);
            if (surface != null) {
                surface.dispose();
            }
        }
        list.clear();
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

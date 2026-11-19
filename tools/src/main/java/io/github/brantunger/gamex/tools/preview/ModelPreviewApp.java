package io.github.brantunger.gamex.tools.preview;

import com.badlogic.gdx.ApplicationAdapter;
import com.badlogic.gdx.Gdx;
import com.badlogic.gdx.Graphics;
import com.badlogic.gdx.Input;
import com.badlogic.gdx.InputAdapter;
import com.badlogic.gdx.backends.lwjgl3.Lwjgl3Application;
import com.badlogic.gdx.backends.lwjgl3.Lwjgl3ApplicationConfiguration;
import com.badlogic.gdx.graphics.Color;
import com.badlogic.gdx.graphics.GL20;
import com.badlogic.gdx.graphics.Mesh;
import com.badlogic.gdx.graphics.PerspectiveCamera;
import com.badlogic.gdx.graphics.Pixmap;
import com.badlogic.gdx.graphics.PixmapIO;
import com.badlogic.gdx.graphics.Texture;
import com.badlogic.gdx.graphics.VertexAttribute;
import com.badlogic.gdx.graphics.VertexAttributes;
import com.badlogic.gdx.graphics.g2d.SpriteBatch;
import com.badlogic.gdx.graphics.glutils.ShaderProgram;
import com.badlogic.gdx.graphics.glutils.ShapeRenderer;
import com.badlogic.gdx.math.MathUtils;
import com.badlogic.gdx.math.Matrix3;
import com.badlogic.gdx.math.Matrix4;
import com.badlogic.gdx.math.Vector3;
import com.badlogic.gdx.utils.Disposable;
import com.badlogic.gdx.utils.GdxRuntimeException;
import com.badlogic.gdx.utils.ScreenUtils;
import io.github.brantunger.gamex.hud.PixelFont;
import io.github.brantunger.gamex.model.ModelRig;
import io.github.brantunger.gamex.model.ModelVertices;
import io.github.brantunger.gamex.model.ObjModel;
import io.github.brantunger.gamex.render.ModelMesh;
import io.github.brantunger.gamex.tools.GenerateAllModels;
import io.github.brantunger.gamex.weapon.BlastProjectile;
import io.github.brantunger.gamex.weapon.EnemyProjectile;
import io.github.brantunger.gamex.weapon.HandModel;
import io.github.brantunger.gamex.weapon.PistolModel;
import io.github.brantunger.gamex.weapon.RailLanceModel;
import io.github.brantunger.gamex.weapon.ScattergunModel;
import io.github.brantunger.gamex.world.Door;
import io.github.brantunger.gamex.world.Enemy;
import io.github.brantunger.gamex.world.GameMap;
import io.github.brantunger.gamex.world.Pickup;
import io.github.brantunger.gamex.world.SecurityConsole;
import io.github.brantunger.gamex.world.Target;
import io.github.brantunger.gamex.world.WorldModels;

import java.io.IOException;
import java.lang.reflect.Proxy;
import java.nio.ByteBuffer;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.function.Consumer;
import java.util.function.Function;
import java.util.zip.Deflater;

/**
 * Interactive LWJGL3 model previewer for {@code gamex}: orbit/zoom camera, live hierarchical joint/door animation,
 * collision bounds & socket gizmos, wireframe toggle, accent tint cycling, and instant {@code R} rebuild & reload.
 */
public final class ModelPreviewApp extends ApplicationAdapter {

    private static final String VERTEX_SHADER =
        "attribute vec3 a_position;\n"
            + "attribute vec3 a_normal;\n"
            + "attribute vec3 a_colour;\n"
            + "attribute float a_gloss;\n"
            + "attribute float a_accent;\n"
            + "uniform mat4 u_projViewTrans;\n"
            + "uniform mat4 u_model;\n"
            + "uniform mat3 u_normalMatrix;\n"
            + "uniform vec4 u_accentColour;\n"
            + "uniform vec3 u_cameraPosition;\n"
            + "varying vec3 v_colour;\n"
            + "void main() {\n"
            + "    vec4 worldPos = u_model * vec4(a_position, 1.0);\n"
            + "    vec3 n = normalize(u_normalMatrix * a_normal);\n"
            + "    vec3 weight = n * n;\n"
            + "    float shade = weight.z + weight.x * 0.72 + weight.y * (n.y > 0.0 ? 0.9 : 0.62);\n"
            + "    vec3 lightDir = normalize(vec3(0.35, 0.85, -0.45));\n"
            + "    float key = 0.65 + 0.55 * max(dot(n, lightDir), 0.0);\n"
            + "    vec3 toCam = normalize(u_cameraPosition - worldPos.xyz);\n"
            + "    vec3 halfVec = normalize(lightDir + toCam);\n"
            + "    vec3 col = mix(a_colour, u_accentColour.rgb, a_accent);\n"
            + "    float gloss = mix(a_gloss, u_accentColour.a, a_accent);\n"
            + "    float sheen = gloss * pow(max(dot(n, halfVec), 0.0), 12.0) * 0.35;\n"
            + "    float rim = pow(1.0 - max(dot(n, toCam), 0.0), 2.5) * 0.12;\n"
            + "    v_colour = col * shade * key + vec3(sheen + rim);\n"
            + "    gl_Position = u_projViewTrans * worldPos;\n"
            + "}\n";

    private static final String FRAGMENT_SHADER =
        "#ifdef GL_ES\n"
            + "precision mediump float;\n"
            + "#endif\n"
            + "varying vec3 v_colour;\n"
            + "void main() {\n"
            + "    gl_FragColor = vec4(v_colour, 1.0);\n"
            + "}\n";

    private static final float[][] DOOR_ACCENTS = {
        WorldModels.ACCENT_DOOR_UNLOCKED,
        WorldModels.ACCENT_DOOR_AMBER,
        WorldModels.ACCENT_DOOR_CYAN,
        WorldModels.ACCENT_DOOR_LOCKDOWN,
        WorldModels.ACCENT_DOOR_BOSS,
        WorldModels.ACCENT_DOOR_OPEN
    };
    private static final String[] DOOR_ACCENT_NAMES = {"UNLOCKED", "AMBER", "CYAN", "LOCKDOWN", "BOSS", "OPEN"};

    private final List<Entry> entries = new ArrayList<>();
    private PerspectiveCamera camera;
    private ShaderProgram shader;
    private ShapeRenderer shapes;
    private SpriteBatch batch;
    private Texture white;

    private int currentIndex;
    private int accentIndex;
    private boolean animate = true;
    private boolean showGizmos = true;
    private boolean wireframe;
    private float time;
    private float orbitYaw = 35f;
    private float orbitPitch = 18f;
    private float orbitDist = 1.15f;
    private int lastX;
    private int lastY;
    private boolean dragging;
    private String statusToast = "";
    private float statusTimer;

    private final Matrix4 rootMat = new Matrix4();
    private final Matrix4 partMat = new Matrix4();
    private final Matrix3 normalMat = new Matrix3();
    private final Vector3 targetCenter = new Vector3();
    private final Vector3 socketTmp = new Vector3();
    private final Map<String, Matrix4> animRotations = new HashMap<>();

    private final String requestedModel;
    private final String screenshotPath;
    private final Path explicitModelsDir;
    private final MeshFactory meshFactory;
    private int frames;
    private boolean screenshotCaptured;

    public ModelPreviewApp() {
        this(null, null);
    }

    public ModelPreviewApp(String requestedModel, String screenshotPath) {
        this(requestedModel, screenshotPath, null, GPU_MESH_FACTORY);
    }

    ModelPreviewApp(String requestedModel, String screenshotPath, Path explicitModelsDir, MeshFactory meshFactory) {
        this.requestedModel = requestedModel != null && !requestedModel.isEmpty()
            ? requestedModel
            : (explicitModelsDir != null ? null : System.getenv("GAMEX_PREVIEW_MODEL"));
        this.screenshotPath = screenshotPath != null && !screenshotPath.isEmpty()
            ? screenshotPath
            : (explicitModelsDir != null ? null : System.getenv("GAMEX_PREVIEW_SCREENSHOT"));
        this.explicitModelsDir = explicitModelsDir;
        this.meshFactory = meshFactory != null ? meshFactory : GPU_MESH_FACTORY;
    }

    public static void main(String[] args) {
        String modelArg = args.length >= 1 ? args[0] : null;
        String screenshotArg = args.length >= 2 ? args[1] : null;
        Lwjgl3ApplicationConfiguration config = new Lwjgl3ApplicationConfiguration();
        config.setTitle("gamex Model Previewer");
        config.setWindowedMode(1280, 800);
        config.setBackBufferConfig(8, 8, 8, 8, 16, 0, 2);
        config.useVsync(true);
        new Lwjgl3Application(new ModelPreviewApp(modelArg, screenshotArg), config);
    }

    static PerspectiveCamera createPreviewCamera(Graphics graphics) {
        boolean hasSize = graphics != null && graphics.getWidth() > 0 && graphics.getHeight() > 0;
        int w = hasSize ? graphics.getWidth() : 1280;
        int h = hasSize ? graphics.getHeight() : 800;
        // The no-arg constructor avoids an eager update(): Frustum.update calls native Matrix4.prj, which is only
        // available once the backend has loaded the gdx natives.
        PerspectiveCamera cam = new PerspectiveCamera();
        cam.fieldOfView = 55f;
        cam.viewportWidth = w;
        cam.viewportHeight = h;
        cam.near = 0.02f;
        cam.far = 30f;
        cam.update(nativesAvailable());
        return cam;
    }

    private static boolean nativesAvailable() {
        return Gdx.app != null && Gdx.gl != null
            && !Proxy.isProxyClass(Gdx.app.getClass())
            && !Proxy.isProxyClass(Gdx.gl.getClass());
    }

    @Override
    public void create() {
        camera = createPreviewCamera(Gdx.graphics);
        applyBackBufferViewport();

        shader = new ShaderProgram(VERTEX_SHADER, FRAGMENT_SHADER);
        if (!shader.isCompiled()) {
            throw new GdxRuntimeException("Preview shader failed: " + shader.getLog());
        }
        shapes = new ShapeRenderer();
        batch = new SpriteBatch();
        if (Gdx.graphics.getWidth() <= 0 || Gdx.graphics.getHeight() <= 0) {
            batch.getProjectionMatrix().setToOrtho2D(0f, 0f, 1280f, 800f);
        }
        Pixmap px = new Pixmap(1, 1, Pixmap.Format.RGBA8888);
        try {
            px.setColor(Color.WHITE);
            px.fill();
            white = new Texture(px);
        } finally {
            px.dispose();
        }

        loadAllEntries();

        if (requestedModel != null && !requestedModel.isEmpty()) {
            for (int i = 0; i < entries.size(); i++) {
                if (entries.get(i).label.equalsIgnoreCase(requestedModel)
                    || entries.get(i).label.contains(requestedModel.toLowerCase(Locale.ROOT))) {
                    currentIndex = i;
                    break;
                }
            }
        }
        resetCameraForEntry();

        Gdx.input.setInputProcessor(new InputAdapter() {
            @Override
            public boolean touchDown(int screenX, int screenY, int pointer, int button) {
                lastX = screenX;
                lastY = screenY;
                dragging = true;
                return true;
            }

            @Override
            public boolean touchUp(int screenX, int screenY, int pointer, int button) {
                dragging = false;
                return true;
            }

            @Override
            public boolean touchDragged(int screenX, int screenY, int pointer) {
                if (!dragging) return false;
                orbitYaw = (orbitYaw + (screenX - lastX) * 0.65f) % 360f;
                orbitPitch = MathUtils.clamp(orbitPitch + (screenY - lastY) * 0.55f, -85f, 85f);
                lastX = screenX;
                lastY = screenY;
                return true;
            }

            @Override
            public boolean scrolled(float amountX, float amountY) {
                orbitDist = MathUtils.clamp(orbitDist * (1f + amountY * 0.12f), 0.18f, 6.0f);
                return true;
            }

            @Override
            public boolean keyDown(int keycode) {
                if (keycode == Input.Keys.RIGHT || keycode == Input.Keys.RIGHT_BRACKET) {
                    stepEntry(1);
                } else if (keycode == Input.Keys.LEFT || keycode == Input.Keys.LEFT_BRACKET) {
                    stepEntry(-1);
                } else if (keycode == Input.Keys.SPACE) {
                    animate = !animate;
                } else if (keycode == Input.Keys.G) {
                    showGizmos = !showGizmos;
                } else if (keycode == Input.Keys.W) {
                    wireframe = !wireframe;
                } else if (keycode == Input.Keys.C) {
                    accentIndex++;
                } else if (keycode == Input.Keys.R) {
                    regenerateAndReload();
                } else if (keycode == Input.Keys.ESCAPE) {
                    Gdx.app.exit();
                }
                return true;
            }
        });
    }

    void stepEntry(int delta) {
        if (entries.isEmpty()) return;
        currentIndex = Math.floorMod(currentIndex + delta, entries.size());
        accentIndex = 0;
        resetCameraForEntry();
    }

    Path resolveModelsDir() {
        if (explicitModelsDir != null) return explicitModelsDir;
        String configured = System.getProperty("gamex.modelsDir");
        if (configured != null) return Paths.get(configured);
        Path direct = Paths.get("models");
        if (Files.isDirectory(direct)) return direct;
        return Paths.get("assets", "models");
    }

    @FunctionalInterface
    interface ThrowingRunnable {
        void run() throws IOException;
    }

    private void regenerateAndReload() {
        reloadModels(() -> GenerateAllModels.generateAll(resolveModelsDir()));
    }

    void reloadModels(ThrowingRunnable generator) {
        try {
            if (generator != null) {
                generator.run();
            }
            loadAllEntries();
            statusToast = "REGENERATED ALL MODELS";
            statusTimer = 2.0f;
        } catch (IOException | RuntimeException e) {
            statusToast = "RELOAD ERROR: " + e.getMessage();
            statusTimer = 4.0f;
        }
    }

    private void disposeEntries() {
        disposeEntryList(entries);
    }

    private static void disposeEntryList(List<Entry> list) {
        RuntimeException firstError = null;
        for (Entry e : list) {
            try {
                e.dispose();
            } catch (RuntimeException ex) {
                if (firstError == null) firstError = ex;
                else firstError.addSuppressed(ex);
            }
        }
        list.clear();
        if (firstError != null) throw firstError;
    }

    void loadAllEntries() {
        Path dir = resolveModelsDir();
        List<Entry> loaded = new ArrayList<>();
        try {
            // 0: Combined first-person viewmodel (pistol + hand).
            ObjModel pistolObj = readObj(dir.resolve("viewmodel-pistol.obj"), "pistol");
            ObjModel handObj = readObj(dir.resolve("viewmodel-hand.obj"), "hand");
            PistolModel pistol = PistolModel.from(pistolObj);
            ModelVertices.IndexedMesh handIndexed = HandModel.packIndexed(handObj);
            Entry vm = new Entry("viewmodel (pistol+hand)", pistolObj, 0f, -0.06f, 0.08f, 0.55f, 0f, 0f);
            loaded.add(vm);
            addPartMesh(vm, "body", pistol.bodyMesh);
            addPartMesh(vm, "slide", pistol.slideMesh);
            addPartMesh(vm, "hand", handIndexed);
            vm.rig = new ModelRig(pistolObj);
            vm.totalTriangles = pistol.bodyMesh.triangleCount() + pistol.slideMesh.triangleCount()
                + handIndexed.triangleCount();
            vm.uniqueVertices = pistol.bodyMesh.vertexCount() + pistol.slideMesh.vertexCount()
                + handIndexed.vertexCount();
            vm.rawVertices = vm.totalTriangles * 3;

            ObjModel scatterObj = readObj(dir.resolve("viewmodel-scattergun.obj"), "scattergun");
            ScattergunModel scatter = ScattergunModel.from(scatterObj);
            Entry vmScatter = new Entry("viewmodel-scattergun (scattergun+hand)", scatterObj, 0f, -0.05f, 0.01f, 0.62f,
                0f, 0f);
            loaded.add(vmScatter);
            addPartMesh(vmScatter, "body", scatter.bodyMesh);
            addPartMesh(vmScatter, "pump", scatter.pumpMesh);
            addPartMesh(vmScatter, "hand", handIndexed);
            vmScatter.rig = new ModelRig(scatterObj);
            vmScatter.totalTriangles = scatter.bodyMesh.triangleCount() + scatter.pumpMesh.triangleCount()
                + handIndexed.triangleCount();
            vmScatter.uniqueVertices = scatter.bodyMesh.vertexCount() + scatter.pumpMesh.vertexCount()
                + handIndexed.vertexCount();
            vmScatter.rawVertices = vmScatter.totalTriangles * 3;

            ObjModel railObj = readObj(dir.resolve("viewmodel-raillance.obj"), "raillance");
            RailLanceModel rail = RailLanceModel.from(railObj);
            Entry vmRail = new Entry("viewmodel-raillance (raillance+hand)", railObj, 0f, -0.05f, -0.03f, 0.76f,
                0f, 0f);
            loaded.add(vmRail);
            addPartMesh(vmRail, "body", rail.bodyMesh);
            addPartMesh(vmRail, "bolt", rail.boltMesh);
            addPartMesh(vmRail, "hand", handIndexed);
            vmRail.rig = new ModelRig(railObj);
            vmRail.totalTriangles = rail.bodyMesh.triangleCount() + rail.boltMesh.triangleCount()
                + handIndexed.triangleCount();
            vmRail.uniqueVertices = rail.bodyMesh.vertexCount() + rail.boltMesh.vertexCount()
                + handIndexed.vertexCount();
            vmRail.rawVertices = vmRail.totalTriangles * 3;

            addWorldEntry(loaded, dir, "enemy-guard.obj", 0f, Enemy.Kind.GUARD.height * 0.5f, 0f, 1.35f,
                Enemy.Kind.GUARD.radius, Enemy.Kind.GUARD.height,
                "pelvis", "leg_l", "leg_r", "torso", "arm_l", "arm_r");
            addWorldEntry(loaded, dir, "enemy-turret.obj", 0f, Enemy.Kind.TURRET.height * 0.5f, 0f, 1.25f,
                Enemy.Kind.TURRET.radius, Enemy.Kind.TURRET.height,
                "base", "head", "barrels");
            addWorldEntry(loaded, dir, "enemy-drone.obj", 0f, 0.35f, 0f, 1.05f,
                Enemy.Kind.DRONE.radius, Enemy.Kind.DRONE.height);
            addWorldEntry(loaded, dir, "door-bulkhead.obj", 0f, 0.50f, 0f, 1.70f, 0f, GameMap.WALL_HEIGHT);
            addWorldEntry(loaded, dir, "hazard-grate.obj", 0f, 0.012f, 0f, 1.45f, 0f, 0.024f);
            addWorldEntry(loaded, dir, "security-console.obj", 0f, 0.36f, 0f, 1.15f,
                SecurityConsole.RADIUS, SecurityConsole.HEIGHT);
            addWorldEntry(loaded, dir, "pickup-scattergun.obj", 0f, 0.15f, 0f, 0.72f, Pickup.RADIUS, 0.30f);
            addWorldEntry(loaded, dir, "pickup-shells.obj", 0f, 0.14f, 0f, 0.68f, Pickup.RADIUS, 0.27f);
            addWorldEntry(loaded, dir, "pickup-raillance.obj", 0f, 0.15f, 0f, 0.72f, Pickup.RADIUS, 0.31f);
            addWorldEntry(loaded, dir, "pickup-slugs.obj", 0f, 0.14f, 0f, 0.68f, Pickup.RADIUS, 0.28f);
            addWorldEntry(loaded, dir, "pickup-keycard.obj", 0f, 0.15f, 0f, 0.72f, Pickup.RADIUS, 0.30f);
            addWorldEntry(loaded, dir, "pickup-medkit.obj", 0f, 0.14f, 0f, 0.72f, Pickup.RADIUS, 0.28f);
            addWorldEntry(loaded, dir, "pickup-armor.obj", 0f, 0.17f, 0f, 0.72f, Pickup.RADIUS, 0.34f);
            addWorldEntry(loaded, dir, "pickup-energy.obj", 0f, 0.17f, 0f, 0.72f, Pickup.RADIUS, 0.34f);
            addWorldEntry(loaded, dir, "canister.obj", 0f, 0.28f, 0f, 1.05f,
                Target.Kind.CANISTER.radius, Target.Kind.CANISTER.height);
            addWorldEntry(loaded, dir, "ring-board.obj", 0f, 0.40f, 0f, 1.25f,
                Target.Kind.RING_BOARD.radius, Target.Kind.RING_BOARD.height);
            addWorldEntry(loaded, dir, "blast-orb.obj", 0f, 0f, 0f, 0.55f, BlastProjectile.RADIUS, 0f);
            addWorldEntry(loaded, dir, "rail-beam.obj", 0f, 0f, -0.5f, 1.25f, 0f, 0f);
            addWorldEntry(loaded, dir, "enemy-bolt.obj", 0f, 0f, 0f, 0.45f, EnemyProjectile.RADIUS, 0f);
        } catch (RuntimeException | Error e) {
            try {
                disposeEntryList(loaded);
            } catch (RuntimeException suppress) {
                e.addSuppressed(suppress);
            }
            throw e;
        }

        boolean hadEntries = !entries.isEmpty();
        disposeEntries();
        entries.addAll(loaded);
        if (entries.isEmpty()) {
            currentIndex = 0;
        } else if (!hadEntries) {
            currentIndex = MathUtils.clamp(currentIndex, 0, entries.size() - 1);
        } else if (currentIndex < 0 || currentIndex >= entries.size()) {
            currentIndex = MathUtils.clamp(currentIndex, 0, entries.size() - 1);
            resetCameraForEntry();
        }
    }

    private void addWorldEntry(List<Entry> target, Path dir, String fileName,
                               float cx, float cy, float cz, float defaultDist,
                               float cylRadius, float cylHeight, String... parts) {
        ObjModel model = readObj(dir.resolve(fileName), fileName);
        Entry e = new Entry(fileName, model, cx, cy, cz, defaultDist, cylRadius, cylHeight);
        e.rig = new ModelRig(model);
        target.add(e);
        if (parts.length == 0) {
            ModelVertices.IndexedMesh indexed = WorldModels.packIndexed(model);
            addPartMesh(e, ObjModel.ROOT_PART, indexed);
            e.totalTriangles = indexed.triangleCount();
            e.uniqueVertices = indexed.vertexCount();
            e.rawVertices = indexed.triangleCount() * 3;
        } else {
            for (String p : parts) {
                ModelVertices.IndexedMesh indexed = WorldModels.packPartIndexed(model, p);
                addPartMesh(e, p, indexed);
                e.totalTriangles += indexed.triangleCount();
                e.uniqueVertices += indexed.vertexCount();
                e.rawVertices += indexed.triangleCount() * 3;
            }
        }
    }

    private void addPartMesh(Entry entry, String partName, ModelVertices.IndexedMesh indexed) {
        if (indexed == null || indexed.vertices == null || indexed.vertices.length == 0
            || indexed.vertices.length % ModelVertices.VERTEX_SIZE != 0) {
            throw new IllegalArgumentException(
                "Model '" + entry.label + "' part '" + partName + "' has empty or invalid vertex data");
        }
        short[] wireIndices = buildWireframeIndices(indexed.indices, indexed.vertexCount());
        PreviewMesh solid = meshFactory.buildTriangles(indexed);
        entry.partMeshes.put(partName, solid);
        PreviewMesh wire = meshFactory.buildWireframe(indexed, wireIndices);
        entry.partWireMeshes.put(partName, wire);
    }

    static short[] buildWireframeIndices(short[] triangleIndices) {
        return buildWireframeIndices(triangleIndices, ModelVertices.MAX_INDEXED_VERTICES);
    }

    static short[] buildWireframeIndices(short[] triangleIndices, int vertexCount) {
        if (triangleIndices == null || triangleIndices.length == 0) {
            throw new IllegalArgumentException("Triangle index buffer must not be empty");
        }
        if (triangleIndices.length % 3 != 0) {
            throw new IllegalArgumentException(
                "Triangle index count must be a multiple of 3: " + triangleIndices.length);
        }
        if (vertexCount <= 0 || vertexCount > ModelVertices.MAX_INDEXED_VERTICES) {
            throw new IllegalArgumentException(
                "Wireframe vertex count out of 16-bit range: " + vertexCount);
        }
        int triangleCount = triangleIndices.length / 3;
        if (triangleCount > Integer.MAX_VALUE / 6) {
            throw new IllegalArgumentException("Triangle count exceeds index buffer capacity: " + triangleCount);
        }
        short[] out = new short[triangleCount * 6];
        Set<Long> seenEdges = new HashSet<>(triangleCount * 3);
        int at = 0;
        for (int i = 0; i < triangleIndices.length; i += 3) {
            int i0 = triangleIndices[i] & 0xFFFF;
            int i1 = triangleIndices[i + 1] & 0xFFFF;
            int i2 = triangleIndices[i + 2] & 0xFFFF;
            if (i0 >= vertexCount || i1 >= vertexCount || i2 >= vertexCount) {
                throw new IllegalArgumentException("Triangle index exceeds vertex count " + vertexCount);
            }
            at = appendWireEdge(i0, i1, seenEdges, out, at);
            at = appendWireEdge(i1, i2, seenEdges, out, at);
            at = appendWireEdge(i2, i0, seenEdges, out, at);
        }
        if (at == 0) {
            throw new IllegalArgumentException("Triangle index buffer contained only degenerate zero-length edges");
        }
        return at == out.length ? out : Arrays.copyOf(out, at);
    }

    private static int appendWireEdge(int a, int b, Set<Long> seenEdges, short[] out, int at) {
        if (a == b) return at;
        int lo = Math.min(a, b);
        int hi = Math.max(a, b);
        long key = ((long) lo << 32) | (hi & 0xFFFFFFFFL);
        if (seenEdges.add(key)) {
            out[at++] = (short) a;
            out[at++] = (short) b;
        }
        return at;
    }

    private static ObjModel readObj(Path file, String label) {
        try {
            String text = new String(Files.readAllBytes(file), StandardCharsets.UTF_8);
            return ObjModel.parse(text);
        } catch (IOException e) {
            throw new IllegalStateException("Failed to read " + label + " at " + file, e);
        }
    }

    void resetCameraForEntry() {
        if (entries.isEmpty()) return;
        currentIndex = MathUtils.clamp(currentIndex, 0, entries.size() - 1);
        Entry e = entries.get(currentIndex);
        targetCenter.set(e.centerX, e.centerY, e.centerZ);
        orbitDist = e.defaultDist;
        orbitYaw = e.label.startsWith("viewmodel") ? 65f : 148f;
        orbitPitch = e.label.equals("hazard-grate.obj") ? 38f
            : (e.label.startsWith("viewmodel") ? 14f : 18f);
    }

    @Override
    public void render() {
        frames++;
        applyBackBufferViewport();
        float dt = Gdx.graphics != null ? Math.min(0.05f, Gdx.graphics.getDeltaTime()) : 0f;
        if (animate && screenshotPath == null) time += dt;
        else if (screenshotPath != null) time = 0.65f;
        statusTimer = Math.max(0f, statusTimer - dt);

        if (Gdx.gl != null) {
            ScreenUtils.clear(0.16f, 0.18f, 0.23f, 1f, true);
        }
        if (entries.isEmpty()) {
            maybeCaptureAutomatedScreenshot();
            return;
        }
        currentIndex = MathUtils.clamp(currentIndex, 0, entries.size() - 1);
        Entry entry = entries.get(currentIndex);

        if (camera == null) {
            camera = createPreviewCamera(Gdx.graphics);
        }
        double yawRad = Math.toRadians(orbitYaw);
        double pitchRad = Math.toRadians(orbitPitch);
        float cosP = (float) Math.cos(pitchRad);
        camera.position.set(
            targetCenter.x + orbitDist * cosP * (float) Math.sin(yawRad),
            targetCenter.y + orbitDist * (float) Math.sin(pitchRad),
            targetCenter.z + orbitDist * cosP * (float) Math.cos(yawRad));
        camera.lookAt(targetCenter);
        camera.up.set(Vector3.Y);
        camera.update(nativesAvailable());

        rootMat.idt();
        animRotations.clear();
        float[] accent = null;
        String accentLabel = "NONE";
        if (entry.label.equals("door-bulkhead.obj")) {
            int i = Math.floorMod(accentIndex, DOOR_ACCENTS.length);
            accent = DOOR_ACCENTS[i];
            accentLabel = DOOR_ACCENT_NAMES[i];
            if (animate) {
                float phase = 0.5f - 0.5f * MathUtils.cos(time * 2.2f);
                rootMat.translate(0f, phase * 0.45f, 0f);
            }
        } else if (entry.label.equals("pickup-keycard.obj")) {
            boolean cyan = Math.floorMod(accentIndex, 2) == 1;
            accent = cyan ? WorldModels.ACCENT_KEYCARD_CYAN : WorldModels.ACCENT_KEYCARD_AMBER;
            accentLabel = cyan ? "CYAN" : "AMBER";
        } else if (entry.label.equals("security-console.obj")) {
            boolean active = Math.floorMod(accentIndex, 2) == 1;
            accent = active ? WorldModels.ACCENT_CONSOLE_ACTIVE : WorldModels.ACCENT_CONSOLE_LOCKED;
            accentLabel = active ? "ACTIVE" : "LOCKED";
        } else if (entry.label.equals("hazard-grate.obj")) {
            accent = WorldModels.ACCENT_HAZARD_LIVE;
            accentLabel = "LIVE";
        } else if (entry.label.equals("pickup-raillance.obj") || entry.label.equals("pickup-slugs.obj")) {
            accent = WorldModels.ACCENT_RAIL_READY;
            accentLabel = "READY";
        } else if (entry.label.equals("rail-beam.obj")) {
            accent = WorldModels.ACCENT_RAIL_BEAM;
            accentLabel = "BEAM";
        } else if (entry.label.startsWith("viewmodel-raillance")) {
            boolean recharging = Math.floorMod(accentIndex, 2) == 1;
            accent = recharging ? RailLanceModel.ACCENT_RECHARGING : RailLanceModel.ACCENT_READY;
            accentLabel = recharging ? "RECHARGING" : "READY";
            if (animate) {
                float boltKick = Math.max(0f, MathUtils.sin(time * 2.8f)) * RailLanceModel.BOLT_TRAVEL;
                animRotations.put("bolt", new Matrix4().setToTranslation(0f, 0f, boltKick));
            }
        } else if (entry.label.equals("enemy-guard.obj") && animate) {
            float swing = MathUtils.sin(time * 6f);
            rootMat.translate(0f, Math.abs(swing) * 0.018f, 0f);
            animRotations.put("leg_l", new Matrix4().setToRotation(Vector3.X, swing * 26f));
            animRotations.put("leg_r", new Matrix4().setToRotation(Vector3.X, -swing * 26f));
            animRotations.put("arm_l", new Matrix4().setToRotation(Vector3.X, -swing * 21f));
            animRotations.put("arm_r", new Matrix4().setToRotation(Vector3.X, -swing * 4f));
        } else if (entry.label.equals("enemy-turret.obj") && animate) {
            animRotations.put("head", new Matrix4().setToRotation(Vector3.Y, MathUtils.sin(time * 1.4f) * 35f));
            animRotations.put("barrels", new Matrix4().setToRotation(Vector3.X, MathUtils.sin(time * 2.3f) * 14f));
        } else if (entry.label.startsWith("viewmodel-scattergun") && animate) {
            float pumpKick = Math.max(0f, MathUtils.sin(time * 3.2f)) * ScattergunModel.PUMP_TRAVEL;
            animRotations.put("pump", new Matrix4().setToTranslation(0f, 0f, pumpKick));
        } else if (entry.label.startsWith("viewmodel") && animate) {
            float slideKick = Math.max(0f, MathUtils.sin(time * 4f)) * 0.032f;
            animRotations.put("slide", new Matrix4().setToTranslation(0f, 0f, slideKick));
        }

        if (Gdx.gl != null) {
            Gdx.gl.glEnable(GL20.GL_DEPTH_TEST);
            Gdx.gl.glDepthFunc(GL20.GL_LEQUAL);
            if (wireframe) {
                Gdx.gl.glDisable(GL20.GL_CULL_FACE);
            } else {
                Gdx.gl.glEnable(GL20.GL_CULL_FACE);
                Gdx.gl.glCullFace(GL20.GL_BACK);
            }
        }

        if (shader != null) {
            shader.bind();
            shader.setUniformMatrix("u_projViewTrans", camera.combined);
            shader.setUniformf("u_cameraPosition", camera.position);
            if (accent == null) shader.setUniformf("u_accentColour", 0f, 0f, 0f, 0f);
            else shader.setUniformf("u_accentColour", accent[0], accent[1], accent[2], accent[3]);
        }

        Map<String, PreviewMesh> activeMeshes = wireframe ? entry.partWireMeshes : entry.partMeshes;
        int primitiveType = wireframe ? GL20.GL_LINES : GL20.GL_TRIANGLES;
        for (Map.Entry<String, PreviewMesh> part : activeMeshes.entrySet()) {
            String partName = part.getKey();
            if ("hand".equals(partName)) {
                partMat.set(rootMat);
            } else {
                entry.rig.evaluatePartTransform(partName, animRotations, rootMat, partMat);
            }
            normalMat.set(partMat).inv().transpose();
            if (shader != null) {
                shader.setUniformMatrix("u_model", partMat);
                shader.setUniformMatrix("u_normalMatrix", normalMat);
                part.getValue().render(shader, primitiveType);
            }
        }

        if (showGizmos && shapes != null) {
            drawGizmos(entry);
        }

        if (Gdx.gl != null) {
            Gdx.gl.glDisable(GL20.GL_DEPTH_TEST);
            Gdx.gl.glDisable(GL20.GL_CULL_FACE);
            Gdx.gl.glEnable(GL20.GL_BLEND);
        }
        if (batch != null && white != null) {
            batch.begin();
            int hudH = Gdx.graphics != null ? Math.max(1, Gdx.graphics.getHeight()) : 800;
            float u = hudH / 800f;
            float topY = hudH - 28f * u;
            batch.setColor(0.55f, 0.95f, 1f, 1f);
            PixelFont.draw(batch, white, "MODEL: " + entry.label.toUpperCase(Locale.ROOT), 20f * u, topY, 3f * u);
            batch.setColor(0.88f, 0.92f, 0.96f, 0.95f);
            PixelFont.draw(batch, white,
                "TRIS " + entry.totalTriangles + "  VERTS " + entry.uniqueVertices + " / " + entry.rawVertices
                    + "  ACCENT: " + accentLabel,
                20f * u, topY - 26f * u, 2.2f * u);
            if (statusTimer > 0f) {
                batch.setColor(0.45f, 1f, 0.65f, 1f);
                PixelFont.draw(batch, white, statusToast, 20f * u, topY - 52f * u, 2.4f * u);
            }
            batch.setColor(0.70f, 0.76f, 0.85f, 0.9f);
            PixelFont.draw(batch, white,
                "DRAG: ORBIT  SCROLL: ZOOM  LEFT/RIGHT: MODEL  SPACE: ANIM  C: ACCENT  G: GIZMOS  W: WIRE  R: REBUILD",
                20f * u, 20f * u, 1.9f * u);
            batch.end();
        }

        maybeCaptureAutomatedScreenshot();
    }

    private void drawGizmos(Entry entry) {
        shapes.setProjectionMatrix(camera.combined);
        shapes.begin(ShapeRenderer.ShapeType.Line);
        // Floor reference grid centered at origin.
        shapes.setColor(0.28f, 0.31f, 0.36f, 1f);
        for (int i = -2; i <= 2; i++) {
            float g = i * 0.25f;
            shapes.line(g, 0f, -0.5f, g, 0f, 0.5f);
            shapes.line(-0.5f, 0f, g, 0.5f, 0f, g);
        }
        // Optional collision cylinder silhouette.
        if (entry.cylRadius > 0f && entry.cylHeight > 0f) {
            shapes.setColor(0.25f, 0.9f, 0.55f, 1f);
            int segs = 24;
            for (int i = 0; i < segs; i++) {
                float a0 = i * MathUtils.PI2 / segs;
                float a1 = (i + 1) * MathUtils.PI2 / segs;
                float x0 = entry.cylRadius * MathUtils.cos(a0);
                float z0 = entry.cylRadius * MathUtils.sin(a0);
                float x1 = entry.cylRadius * MathUtils.cos(a1);
                float z1 = entry.cylRadius * MathUtils.sin(a1);
                shapes.line(x0, 0f, z0, x1, 0f, z1);
                shapes.line(x0, entry.cylHeight, z0, x1, entry.cylHeight, z1);
            }
        }
        // Socket markers (# @socket).
        shapes.setColor(1f, 0.65f, 0.15f, 1f);
        for (ObjModel.SocketSpec socket : entry.model.getSocketSpecs().values()) {
            entry.rig.evaluateSocketWorld(socket.name, animRotations, rootMat, socketTmp);
            float r = 0.025f;
            shapes.line(socketTmp.x - r, socketTmp.y, socketTmp.z, socketTmp.x + r, socketTmp.y, socketTmp.z);
            shapes.line(socketTmp.x, socketTmp.y - r, socketTmp.z, socketTmp.x, socketTmp.y + r, socketTmp.z);
            shapes.line(socketTmp.x, socketTmp.y, socketTmp.z - r, socketTmp.x, socketTmp.y, socketTmp.z + r);
        }
        shapes.end();
    }

    @FunctionalInterface
    interface FrameCapture<P extends Disposable> {
        P capture(int width, int height);
    }

    private void maybeCaptureAutomatedScreenshot() {
        if (Gdx.graphics == null) return;
        maybeCaptureAutomatedScreenshot(
            Gdx.graphics.getBackBufferWidth(),
            Gdx.graphics.getBackBufferHeight(),
            (w, h) -> Pixmap.createFromFrameBuffer(0, 0, w, h),
            Pixmap::getPixels,
            pixmap -> PixmapIO.writePNG(
                Gdx.files.absolute(screenshotPath), pixmap, Deflater.DEFAULT_COMPRESSION, true),
            () -> {
                if (Gdx.app != null) Gdx.app.exit();
            });
    }

    <P extends Disposable> boolean maybeCaptureAutomatedScreenshot(int w, int h,
                                                                   FrameCapture<P> capture,
                                                                   Function<P, ByteBuffer> pixelsOf,
                                                                   Consumer<P> writer,
                                                                   Runnable onExit) {
        if (screenshotPath == null || screenshotCaptured || frames < 2) return false;
        if (!saveScreenshot(w, h, capture, pixelsOf, writer)) return false;
        screenshotCaptured = true;
        onExit.run();
        return true;
    }

    static boolean saveScreenshot(String path) {
        if (Gdx.graphics == null) return false;
        int w = Gdx.graphics.getBackBufferWidth();
        int h = Gdx.graphics.getBackBufferHeight();
        return saveScreenshot(
            w, h,
            (width, height) -> Pixmap.createFromFrameBuffer(0, 0, width, height),
            Pixmap::getPixels,
            pixmap -> PixmapIO.writePNG(Gdx.files.absolute(path), pixmap, Deflater.DEFAULT_COMPRESSION, true));
    }

    static <P extends Disposable> boolean saveScreenshot(int w, int h,
                                                         FrameCapture<P> capture,
                                                         Function<P, ByteBuffer> pixelsOf,
                                                         Consumer<P> writer) {
        if (w <= 0 || h <= 0) return false;
        P pixmap = capture.capture(w, h);
        try {
            ByteBuffer px = pixelsOf.apply(pixmap);
            int limit = Math.min(px.capacity(), w * h * 4);
            for (int i = 3; i < limit; i += 4) {
                px.put(i, (byte) 0xFF);
            }
            writer.accept(pixmap);
            return true;
        } finally {
            pixmap.dispose();
        }
    }

    void applyBackBufferViewport() {
        if (Gdx.graphics == null || Gdx.gl == null) return;
        int bw = Gdx.graphics.getBackBufferWidth();
        int bh = Gdx.graphics.getBackBufferHeight();
        if (bw > 0 && bh > 0) {
            Gdx.gl.glViewport(0, 0, bw, bh);
        }
    }

    @Override
    public void resize(int width, int height) {
        if (width <= 0 || height <= 0) return;
        applyBackBufferViewport();
        if (camera != null) {
            camera.viewportWidth = width;
            camera.viewportHeight = height;
            camera.update(nativesAvailable());
        }
        if (batch != null) {
            batch.getProjectionMatrix().setToOrtho2D(0f, 0f, width, height);
        }
    }

    @Override
    public void dispose() {
        disposeEntries();
        if (shader != null) shader.dispose();
        if (shapes != null) shapes.dispose();
        if (batch != null) batch.dispose();
        if (white != null) white.dispose();
    }

    int getEntryCount() {
        return entries.size();
    }

    int getCurrentIndex() {
        return currentIndex;
    }

    void setCurrentIndex(int index) {
        this.currentIndex = index;
    }

    String getEntryLabel(int index) {
        return entries.get(index).label;
    }

    String getStatusToast() {
        return statusToast;
    }

    float getStatusTimer() {
        return statusTimer;
    }

    PerspectiveCamera getCamera() {
        return camera;
    }

    void setCameraForTest(PerspectiveCamera camera) {
        this.camera = camera;
    }

    void advanceFrameForTest() {
        frames++;
    }

    interface PreviewMesh extends Disposable {
        void render(ShaderProgram shader, int primitiveType);
    }

    interface MeshFactory {
        PreviewMesh buildTriangles(ModelVertices.IndexedMesh data);

        PreviewMesh buildWireframe(ModelVertices.IndexedMesh data, short[] wireframeIndices);
    }

    private static final MeshFactory GPU_MESH_FACTORY = new MeshFactory() {
        @Override
        public PreviewMesh buildTriangles(ModelVertices.IndexedMesh data) {
            return new GpuPreviewMesh(ModelMesh.buildIndexed(data));
        }

        @Override
        public PreviewMesh buildWireframe(ModelVertices.IndexedMesh data, short[] wireframeIndices) {
            return new GpuPreviewMesh(buildWireframeGpuMesh(data, wireframeIndices));
        }
    };

    private static Mesh buildWireframeGpuMesh(ModelVertices.IndexedMesh data, short[] wireframeIndices) {
        Mesh mesh = new Mesh(true, data.vertexCount(), wireframeIndices.length,
            new VertexAttribute(VertexAttributes.Usage.Position, 3, ShaderProgram.POSITION_ATTRIBUTE),
            new VertexAttribute(VertexAttributes.Usage.Normal, 3, ShaderProgram.NORMAL_ATTRIBUTE),
            new VertexAttribute(VertexAttributes.Usage.Generic, 3, "a_colour"),
            new VertexAttribute(VertexAttributes.Usage.Generic, 1, "a_gloss"),
            new VertexAttribute(VertexAttributes.Usage.Generic, 1, "a_accent"));
        try {
            mesh.setVertices(data.vertices);
            mesh.setIndices(wireframeIndices);
            return mesh;
        } catch (RuntimeException | Error e) {
            mesh.dispose();
            throw e;
        }
    }

    private static final class GpuPreviewMesh implements PreviewMesh {
        private final Mesh mesh;

        GpuPreviewMesh(Mesh mesh) {
            this.mesh = mesh;
        }

        @Override
        public void render(ShaderProgram shader, int primitiveType) {
            mesh.render(shader, primitiveType);
        }

        @Override
        public void dispose() {
            mesh.dispose();
        }
    }

    private static final class Entry {
        final String label;
        final ObjModel model;
        final float centerX;
        final float centerY;
        final float centerZ;
        final float defaultDist;
        final float cylRadius;
        final float cylHeight;
        final Map<String, PreviewMesh> partMeshes = new LinkedHashMap<>();
        final Map<String, PreviewMesh> partWireMeshes = new LinkedHashMap<>();
        ModelRig rig;
        int totalTriangles;
        int uniqueVertices;
        int rawVertices;

        Entry(String label, ObjModel model, float centerX, float centerY, float centerZ, float defaultDist,
              float cylRadius, float cylHeight) {
            this.label = label;
            this.model = model;
            this.centerX = centerX;
            this.centerY = centerY;
            this.centerZ = centerZ;
            this.defaultDist = defaultDist;
            this.cylRadius = cylRadius;
            this.cylHeight = cylHeight;
        }

        void dispose() {
            RuntimeException firstError = null;
            for (PreviewMesh m : partMeshes.values()) {
                try {
                    m.dispose();
                } catch (RuntimeException ex) {
                    if (firstError == null) firstError = ex;
                    else firstError.addSuppressed(ex);
                }
            }
            partMeshes.clear();
            for (PreviewMesh m : partWireMeshes.values()) {
                try {
                    m.dispose();
                } catch (RuntimeException ex) {
                    if (firstError == null) firstError = ex;
                    else firstError.addSuppressed(ex);
                }
            }
            partWireMeshes.clear();
            if (firstError != null) throw firstError;
        }
    }
}

package io.github.brantunger.gamex.tools.preview;

import com.badlogic.gdx.ApplicationAdapter;
import com.badlogic.gdx.Gdx;
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
import com.badlogic.gdx.graphics.g2d.SpriteBatch;
import com.badlogic.gdx.graphics.glutils.ShaderProgram;
import com.badlogic.gdx.graphics.glutils.ShapeRenderer;
import com.badlogic.gdx.math.MathUtils;
import com.badlogic.gdx.math.Matrix3;
import com.badlogic.gdx.math.Matrix4;
import com.badlogic.gdx.math.Vector3;
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
import io.github.brantunger.gamex.weapon.ScattergunModel;
import io.github.brantunger.gamex.world.Door;
import io.github.brantunger.gamex.world.Enemy;
import io.github.brantunger.gamex.world.GameMap;
import io.github.brantunger.gamex.world.Pickup;
import io.github.brantunger.gamex.world.SecurityConsole;
import io.github.brantunger.gamex.world.Target;
import io.github.brantunger.gamex.world.WorldModels;

import java.io.IOException;
import java.nio.ByteBuffer;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
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
        WorldModels.ACCENT_DOOR_OPEN
    };
    private static final String[] DOOR_ACCENT_NAMES = {"UNLOCKED", "AMBER", "CYAN", "LOCKDOWN", "OPEN"};

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
    private int frames;

    public ModelPreviewApp() {
        this(null, null);
    }

    public ModelPreviewApp(String requestedModel, String screenshotPath) {
        this.requestedModel = requestedModel != null && !requestedModel.isEmpty()
            ? requestedModel
            : System.getenv("GAMEX_PREVIEW_MODEL");
        this.screenshotPath = screenshotPath != null && !screenshotPath.isEmpty()
            ? screenshotPath
            : System.getenv("GAMEX_PREVIEW_SCREENSHOT");
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

    @Override
    public void create() {
        camera = new PerspectiveCamera(55f, Gdx.graphics.getWidth(), Gdx.graphics.getHeight());
        camera.near = 0.02f;
        camera.far = 30f;

        shader = new ShaderProgram(VERTEX_SHADER, FRAGMENT_SHADER);
        if (!shader.isCompiled()) {
            throw new GdxRuntimeException("Preview shader failed: " + shader.getLog());
        }
        shapes = new ShapeRenderer();
        batch = new SpriteBatch();
        Pixmap px = new Pixmap(1, 1, Pixmap.Format.RGBA8888);
        px.setColor(Color.WHITE);
        px.fill();
        white = new Texture(px);
        px.dispose();

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
                    currentIndex = (currentIndex + 1) % entries.size();
                    accentIndex = 0;
                    resetCameraForEntry();
                } else if (keycode == Input.Keys.LEFT || keycode == Input.Keys.LEFT_BRACKET) {
                    currentIndex = (currentIndex - 1 + entries.size()) % entries.size();
                    accentIndex = 0;
                    resetCameraForEntry();
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

    private Path resolveModelsDir() {
        String configured = System.getProperty("gamex.modelsDir");
        if (configured != null) return Paths.get(configured);
        Path direct = Paths.get("models");
        if (Files.isDirectory(direct)) return direct;
        return Paths.get("assets", "models");
    }

    private void regenerateAndReload() {
        try {
            GenerateAllModels.generateAll(resolveModelsDir());
            loadAllEntries();
            statusToast = "REGENERATED ALL MODELS";
            statusTimer = 2.0f;
        } catch (IOException | RuntimeException e) {
            statusToast = "RELOAD ERROR: " + e.getMessage();
            statusTimer = 4.0f;
        }
    }

    private void disposeEntries() {
        for (Entry e : entries) e.dispose();
        entries.clear();
    }

    private void loadAllEntries() {
        disposeEntries();
        Path dir = resolveModelsDir();

        // 0: Combined first-person viewmodel (pistol + hand).
        ObjModel pistolObj = readObj(dir.resolve("viewmodel-pistol.obj"), "pistol");
        ObjModel handObj = readObj(dir.resolve("viewmodel-hand.obj"), "hand");
        PistolModel pistol = PistolModel.from(pistolObj);
        ModelVertices.IndexedMesh handIndexed = HandModel.packIndexed(handObj);
        Entry vm = new Entry("viewmodel (pistol+hand)", pistolObj, 0f, -0.06f, 0.08f, 0.55f, 0f, 0f);
        vm.partMeshes.put("body", ModelMesh.buildIndexed(pistol.bodyMesh));
        vm.partMeshes.put("slide", ModelMesh.buildIndexed(pistol.slideMesh));
        vm.partMeshes.put("hand", ModelMesh.buildIndexed(handIndexed));
        vm.rig = new ModelRig(pistolObj);
        vm.totalTriangles = pistol.bodyMesh.triangleCount() + pistol.slideMesh.triangleCount()
            + handIndexed.triangleCount();
        vm.uniqueVertices = pistol.bodyMesh.vertexCount() + pistol.slideMesh.vertexCount() + handIndexed.vertexCount();
        vm.rawVertices = vm.totalTriangles * 3;
        entries.add(vm);

        ObjModel scatterObj = readObj(dir.resolve("viewmodel-scattergun.obj"), "scattergun");
        ScattergunModel scatter = ScattergunModel.from(scatterObj);
        Entry vmScatter = new Entry("viewmodel-scattergun (scattergun+hand)", scatterObj, 0f, -0.05f, -0.02f, 0.72f,
            0f, 0f);
        vmScatter.partMeshes.put("body", ModelMesh.buildIndexed(scatter.bodyMesh));
        vmScatter.partMeshes.put("pump", ModelMesh.buildIndexed(scatter.pumpMesh));
        vmScatter.partMeshes.put("hand", ModelMesh.buildIndexed(handIndexed));
        vmScatter.rig = new ModelRig(scatterObj);
        vmScatter.totalTriangles = scatter.bodyMesh.triangleCount() + scatter.pumpMesh.triangleCount()
            + handIndexed.triangleCount();
        vmScatter.uniqueVertices = scatter.bodyMesh.vertexCount() + scatter.pumpMesh.vertexCount()
            + handIndexed.vertexCount();
        vmScatter.rawVertices = vmScatter.totalTriangles * 3;
        entries.add(vmScatter);

        addWorldEntry(dir, "enemy-guard.obj", 0f, Enemy.Kind.GUARD.height * 0.5f, 0f, 1.35f,
            Enemy.Kind.GUARD.radius, Enemy.Kind.GUARD.height,
            "pelvis", "leg_l", "leg_r", "torso", "arm_l", "arm_r");
        addWorldEntry(dir, "enemy-turret.obj", 0f, Enemy.Kind.TURRET.height * 0.5f, 0f, 1.25f,
            Enemy.Kind.TURRET.radius, Enemy.Kind.TURRET.height,
            "base", "head", "barrels");
        addWorldEntry(dir, "enemy-drone.obj", 0f, 0.35f, 0f, 1.05f, Enemy.Kind.DRONE.radius, Enemy.Kind.DRONE.height);
        addWorldEntry(dir, "door-bulkhead.obj", 0f, 0.50f, 0f, 1.70f, 0f, GameMap.WALL_HEIGHT);
        addWorldEntry(dir, "security-console.obj", 0f, 0.36f, 0f, 1.15f,
            SecurityConsole.RADIUS, SecurityConsole.HEIGHT);
        addWorldEntry(dir, "pickup-scattergun.obj", 0f, 0.15f, 0f, 0.72f, Pickup.RADIUS, 0.30f);
        addWorldEntry(dir, "pickup-shells.obj", 0f, 0.14f, 0f, 0.68f, Pickup.RADIUS, 0.27f);
        addWorldEntry(dir, "pickup-keycard.obj", 0f, 0.15f, 0f, 0.72f, Pickup.RADIUS, 0.30f);
        addWorldEntry(dir, "pickup-medkit.obj", 0f, 0.14f, 0f, 0.72f, Pickup.RADIUS, 0.28f);
        addWorldEntry(dir, "pickup-armor.obj", 0f, 0.17f, 0f, 0.72f, Pickup.RADIUS, 0.34f);
        addWorldEntry(dir, "pickup-energy.obj", 0f, 0.17f, 0f, 0.72f, Pickup.RADIUS, 0.34f);
        addWorldEntry(dir, "canister.obj", 0f, 0.28f, 0f, 1.05f, Target.Kind.CANISTER.radius, Target.Kind.CANISTER.height);
        addWorldEntry(dir, "ring-board.obj", 0f, 0.40f, 0f, 1.25f,
            Target.Kind.RING_BOARD.radius, Target.Kind.RING_BOARD.height);
        addWorldEntry(dir, "blast-orb.obj", 0f, 0f, 0f, 0.55f, BlastProjectile.RADIUS, 0f);
        addWorldEntry(dir, "enemy-bolt.obj", 0f, 0f, 0f, 0.45f, EnemyProjectile.RADIUS, 0f);
    }

    private void addWorldEntry(Path dir, String fileName, float cx, float cy, float cz, float defaultDist,
                               float cylRadius, float cylHeight, String... parts) {
        ObjModel model = readObj(dir.resolve(fileName), fileName);
        Entry e = new Entry(fileName, model, cx, cy, cz, defaultDist, cylRadius, cylHeight);
        e.rig = new ModelRig(model);
        if (parts.length == 0) {
            ModelVertices.IndexedMesh indexed = WorldModels.packIndexed(model);
            e.partMeshes.put(ObjModel.ROOT_PART, ModelMesh.buildIndexed(indexed));
            e.totalTriangles = indexed.triangleCount();
            e.uniqueVertices = indexed.vertexCount();
            e.rawVertices = indexed.triangleCount() * 3;
        } else {
            for (String p : parts) {
                ModelVertices.IndexedMesh indexed = WorldModels.packPartIndexed(model, p);
                e.partMeshes.put(p, ModelMesh.buildIndexed(indexed));
                e.totalTriangles += indexed.triangleCount();
                e.uniqueVertices += indexed.vertexCount();
                e.rawVertices += indexed.triangleCount() * 3;
            }
        }
        entries.add(e);
    }

    private static ObjModel readObj(Path file, String label) {
        try {
            String text = new String(Files.readAllBytes(file), StandardCharsets.UTF_8);
            return ObjModel.parse(text);
        } catch (IOException e) {
            throw new IllegalStateException("Failed to read " + label + " at " + file, e);
        }
    }

    private void resetCameraForEntry() {
        Entry e = entries.get(currentIndex);
        targetCenter.set(e.centerX, e.centerY, e.centerZ);
        orbitDist = e.defaultDist;
        orbitYaw = e.label.startsWith("viewmodel") ? 65f : 148f;
        orbitPitch = e.label.startsWith("viewmodel") ? 14f : 18f;
    }

    @Override
    public void render() {
        frames++;
        float dt = Math.min(0.05f, Gdx.graphics.getDeltaTime());
        if (animate && screenshotPath == null) time += dt;
        else if (screenshotPath != null) time = 0.65f;
        statusTimer = Math.max(0f, statusTimer - dt);

        Entry entry = entries.get(currentIndex);
        ScreenUtils.clear(0.16f, 0.18f, 0.23f, 1f, true);

        double yawRad = Math.toRadians(orbitYaw);
        double pitchRad = Math.toRadians(orbitPitch);
        float cosP = (float) Math.cos(pitchRad);
        camera.position.set(
            targetCenter.x + orbitDist * cosP * (float) Math.sin(yawRad),
            targetCenter.y + orbitDist * (float) Math.sin(pitchRad),
            targetCenter.z + orbitDist * cosP * (float) Math.cos(yawRad));
        camera.lookAt(targetCenter);
        camera.up.set(Vector3.Y);
        camera.update();

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

        Gdx.gl.glEnable(GL20.GL_DEPTH_TEST);
        Gdx.gl.glDepthFunc(GL20.GL_LEQUAL);
        Gdx.gl.glEnable(GL20.GL_CULL_FACE);
        Gdx.gl.glCullFace(GL20.GL_BACK);

        shader.bind();
        shader.setUniformMatrix("u_projViewTrans", camera.combined);
        shader.setUniformf("u_cameraPosition", camera.position);
        if (accent == null) shader.setUniformf("u_accentColour", 0f, 0f, 0f, 0f);
        else shader.setUniformf("u_accentColour", accent[0], accent[1], accent[2], accent[3]);

        for (Map.Entry<String, Mesh> part : entry.partMeshes.entrySet()) {
            String partName = part.getKey();
            if ("hand".equals(partName)) {
                partMat.set(rootMat);
            } else {
                entry.rig.evaluatePartTransform(partName, animRotations, rootMat, partMat);
            }
            shader.setUniformMatrix("u_model", partMat);
            normalMat.set(partMat).inv().transpose();
            shader.setUniformMatrix("u_normalMatrix", normalMat);
            part.getValue().render(shader, wireframe ? GL20.GL_LINES : GL20.GL_TRIANGLES);
        }

        if (showGizmos) {
            drawGizmos(entry);
        }

        Gdx.gl.glDisable(GL20.GL_DEPTH_TEST);
        Gdx.gl.glDisable(GL20.GL_CULL_FACE);
        Gdx.gl.glEnable(GL20.GL_BLEND);
        batch.begin();
        float u = Gdx.graphics.getHeight() / 800f;
        float topY = Gdx.graphics.getHeight() - 28f * u;
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

        if (screenshotPath != null && frames == 2) {
            saveScreenshot(screenshotPath);
            Gdx.app.exit();
        }
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

    private static void saveScreenshot(String path) {
        int w = Gdx.graphics.getBackBufferWidth();
        int h = Gdx.graphics.getBackBufferHeight();
        Pixmap pixmap = Pixmap.createFromFrameBuffer(0, 0, w, h);
        ByteBuffer px = pixmap.getPixels();
        for (int i = 3; i < w * h * 4; i += 4) px.put(i, (byte) 0xFF);
        PixmapIO.writePNG(Gdx.files.absolute(path), pixmap, Deflater.DEFAULT_COMPRESSION, true);
        pixmap.dispose();
    }

    @Override
    public void resize(int width, int height) {
        if (width <= 0 || height <= 0) return;
        camera.viewportWidth = width;
        camera.viewportHeight = height;
        camera.update();
        batch.getProjectionMatrix().setToOrtho2D(0f, 0f, width, height);
    }

    @Override
    public void dispose() {
        disposeEntries();
        shader.dispose();
        shapes.dispose();
        batch.dispose();
        white.dispose();
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
        final Map<String, Mesh> partMeshes = new LinkedHashMap<>();
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
            for (Mesh m : partMeshes.values()) m.dispose();
            partMeshes.clear();
        }
    }
}

package io.github.brantunger.gamex.render;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import ch.qos.logback.classic.Level;
import com.badlogic.gdx.Graphics;
import com.badlogic.gdx.graphics.Color;
import com.badlogic.gdx.graphics.PerspectiveCamera;
import com.badlogic.gdx.graphics.Texture;
import com.badlogic.gdx.graphics.g2d.TextureRegion;
import com.badlogic.gdx.graphics.g3d.decals.Decal;
import com.badlogic.gdx.math.Matrix4;
import com.badlogic.gdx.math.Quaternion;
import com.badlogic.gdx.math.Vector3;
import com.badlogic.gdx.utils.Array;
import com.badlogic.gdx.utils.Disposable;
import com.badlogic.gdx.utils.GdxRuntimeException;
import io.github.brantunger.gamex.GameSimulation;
import io.github.brantunger.gamex.LogCapture;
import io.github.brantunger.gamex.model.ModelVertices;
import io.github.brantunger.gamex.model.ObjModel;
import io.github.brantunger.gamex.player.Difficulty;
import io.github.brantunger.gamex.player.PlayerInput;
import io.github.brantunger.gamex.player.PlayerLoadout;
import io.github.brantunger.gamex.weapon.EnemyProjectile;
import io.github.brantunger.gamex.weapon.HandModel;
import io.github.brantunger.gamex.weapon.PistolModel;
import io.github.brantunger.gamex.weapon.RailLanceModel;
import io.github.brantunger.gamex.weapon.ScattergunModel;
import io.github.brantunger.gamex.weapon.WeaponSlot;
import io.github.brantunger.gamex.world.GameMap;
import io.github.brantunger.gamex.world.WorldModels;
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.lang.reflect.Proxy;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.function.Function;
import java.util.function.Supplier;

class RendererSupportTest {

    private static final float EPSILON = 1e-5f;
    private static final float FRAME = 1f / 60f;

    private static Graphics graphicsStub(int width, int height) {
        return (Graphics) Proxy.newProxyInstance(
            Graphics.class.getClassLoader(),
            new Class<?>[] {Graphics.class},
            (proxy, method, args) -> {
                switch (method.getName()) {
                    case "getWidth":
                        return width;
                    case "getHeight":
                        return height;
                    default:
                        return defaultValue(method.getReturnType());
                }
            });
    }

    private static Object defaultValue(Class<?> returnType) {
        if (!returnType.isPrimitive()) return null;
        if (returnType == boolean.class) return false;
        if (returnType == int.class) return 0;
        if (returnType == long.class) return 0L;
        if (returnType == float.class) return 0f;
        if (returnType == double.class) return 0d;
        if (returnType == short.class) return (short) 0;
        if (returnType == byte.class) return (byte) 0;
        if (returnType == char.class) return '\0';
        return null;
    }

    private static void assertAllFinite(float[] values, String label) {
        for (int i = 0; i < values.length; i++) {
            assertTrue(Float.isFinite(values[i]), label + "[" + i + "] was " + values[i]);
        }
    }

    private static void assertCameraMatricesFinite(PerspectiveCamera camera, String label) {
        assertAllFinite(camera.projection.val, label + ".projection");
        assertAllFinite(camera.view.val, label + ".view");
        assertAllFinite(camera.combined.val, label + ".combined");
        assertTrue(camera.projection.val[Matrix4.M00] > 0f, label + ".projection M00 must be positive");
        assertTrue(camera.projection.val[Matrix4.M11] > 0f, label + ".projection M11 must be positive");
    }

    private static Path resolveAssetObj(String assetPath) {
        String configuredAssets = System.getProperty("gamex.assets");
        if (configuredAssets != null && !configuredAssets.isEmpty()) {
            Path configured = Paths.get(configuredAssets, assetPath);
            if (Files.isRegularFile(configured)) return configured;
        }
        for (Path candidate : new Path[] {
            Paths.get("assets").resolve(assetPath),
            Paths.get("..", "assets").resolve(assetPath)
        }) {
            if (Files.isRegularFile(candidate)) return candidate;
        }
        throw new IllegalStateException("Could not locate assets/" + assetPath);
    }

    private static Path resolveHazardGrateObj() {
        return resolveAssetObj(WorldModels.HAZARD_GRATE_PATH);
    }

    private static String readAssetObj(String assetPath) throws IOException {
        return new String(Files.readAllBytes(resolveAssetObj(assetPath)), StandardCharsets.UTF_8);
    }

    private static void assertUnitBillboard(Decal decal, float expectedWidth, float expectedHeight,
                                            Vector3 expectedNormal, String label) {
        Quaternion q = decal.getRotation();
        assertTrue(Float.isFinite(q.x) && Float.isFinite(q.y) && Float.isFinite(q.z) && Float.isFinite(q.w),
            label + " quaternion must be finite: " + q);
        assertEquals(1f, q.len2(), 1e-4f, label + " quaternion |q|^2 must be unit length");

        Vector3 rotatedZ = q.transform(new Vector3(0f, 0f, 1f));
        assertEquals(expectedNormal.x, rotatedZ.x, 1e-4f, label + " normal.x");
        assertEquals(expectedNormal.y, rotatedZ.y, 1e-4f, label + " normal.y");
        assertEquals(expectedNormal.z, rotatedZ.z, 1e-4f, label + " normal.z");

        float[] v = decal.getVertices();
        for (int idx : new int[] {
            Decal.X1, Decal.Y1, Decal.Z1,
            Decal.X2, Decal.Y2, Decal.Z2,
            Decal.X3, Decal.Y3, Decal.Z3,
            Decal.X4, Decal.Y4, Decal.Z4
        }) {
            assertTrue(Float.isFinite(v[idx]), label + " vertex[" + idx + "] must be finite");
        }
        Vector3 topLeft = new Vector3(v[Decal.X1], v[Decal.Y1], v[Decal.Z1]);
        Vector3 topRight = new Vector3(v[Decal.X2], v[Decal.Y2], v[Decal.Z2]);
        Vector3 bottomLeft = new Vector3(v[Decal.X3], v[Decal.Y3], v[Decal.Z3]);
        Vector3 bottomRight = new Vector3(v[Decal.X4], v[Decal.Y4], v[Decal.Z4]);

        assertEquals(expectedWidth, topRight.dst(topLeft), 1e-4f, label + " top edge width");
        assertEquals(expectedWidth, bottomRight.dst(bottomLeft), 1e-4f, label + " bottom edge width");
        assertEquals(expectedHeight, bottomLeft.dst(topLeft), 1e-4f, label + " left edge height");
        assertEquals(expectedHeight, bottomRight.dst(topRight), 1e-4f, label + " right edge height");

        Vector3 edgeRight = new Vector3(topRight).sub(topLeft);
        Vector3 edgeUp = new Vector3(topLeft).sub(bottomLeft);
        assertEquals(0f, edgeRight.dot(edgeUp), 1e-4f, label + " edges must stay orthogonal");

        Vector3 quadNormal = new Vector3(edgeRight).crs(edgeUp).nor();
        assertEquals(expectedNormal.x, quadNormal.x, 1e-4f, label + " quadNormal.x");
        assertEquals(expectedNormal.y, quadNormal.y, 1e-4f, label + " quadNormal.y");
        assertEquals(expectedNormal.z, quadNormal.z, 1e-4f, label + " quadNormal.z");
    }

    @Test
    void initialViewportDimensionsFallBackTo1280x720OnNullZeroOrNegativeGraphics() {
        int[][] invalidSizes = {{0, 0}, {0, 720}, {1280, 0}, {-640, 480}, {800, -600}, {-1, -1}};
        assertEquals(1280, WorldRenderer.initialViewportWidth(null));
        assertEquals(720, WorldRenderer.initialViewportHeight(null));
        assertEquals(1280, ViewmodelRenderer.initialViewportWidth(null));
        assertEquals(720, ViewmodelRenderer.initialViewportHeight(null));

        for (int[] invalid : invalidSizes) {
            Graphics graphics = graphicsStub(invalid[0], invalid[1]);
            String label = invalid[0] + "x" + invalid[1];
            assertEquals(1280, WorldRenderer.initialViewportWidth(graphics), "World width " + label);
            assertEquals(720, WorldRenderer.initialViewportHeight(graphics), "World height " + label);
            assertEquals(1280, ViewmodelRenderer.initialViewportWidth(graphics), "Viewmodel width " + label);
            assertEquals(720, ViewmodelRenderer.initialViewportHeight(graphics), "Viewmodel height " + label);
        }

        Graphics fullHd = graphicsStub(1920, 1080);
        assertEquals(1920, WorldRenderer.initialViewportWidth(fullHd));
        assertEquals(1080, WorldRenderer.initialViewportHeight(fullHd));
        assertEquals(1920, ViewmodelRenderer.initialViewportWidth(fullHd));
        assertEquals(1080, ViewmodelRenderer.initialViewportHeight(fullHd));
    }

    @Test
    void worldAndViewmodelCamerasStayFiniteOnMinimizedStartupAndIgnoreZeroOrNegativeResizes() {
        PerspectiveCamera worldMinimized = WorldRenderer.createWorldCamera(graphicsStub(0, 0));
        PerspectiveCamera viewmodelMinimized = ViewmodelRenderer.createViewmodelCamera(graphicsStub(0, 0));
        assertEquals(1280f, worldMinimized.viewportWidth, EPSILON);
        assertEquals(720f, worldMinimized.viewportHeight, EPSILON);
        assertEquals(1280f, viewmodelMinimized.viewportWidth, EPSILON);
        assertEquals(720f, viewmodelMinimized.viewportHeight, EPSILON);
        assertCameraMatricesFinite(worldMinimized, "worldMinimized");
        assertCameraMatricesFinite(viewmodelMinimized, "viewmodelMinimized");

        assertCameraMatricesFinite(WorldRenderer.createWorldCamera(null), "worldNullGraphics");
        assertCameraMatricesFinite(ViewmodelRenderer.createViewmodelCamera(null), "viewmodelNullGraphics");

        assertTrue(WorldRenderer.applyViewportResize(worldMinimized, 1920, 1080));
        assertTrue(ViewmodelRenderer.applyViewportResize(viewmodelMinimized, 1920, 1080));
        assertEquals(1920f, worldMinimized.viewportWidth, EPSILON);
        assertEquals(1080f, worldMinimized.viewportHeight, EPSILON);
        assertEquals(1920f, viewmodelMinimized.viewportWidth, EPSILON);
        assertEquals(1080f, viewmodelMinimized.viewportHeight, EPSILON);
        assertCameraMatricesFinite(worldMinimized, "world1080p");
        assertCameraMatricesFinite(viewmodelMinimized, "viewmodel1080p");

        float[] worldCombinedBefore = Arrays.copyOf(worldMinimized.combined.val, 16);
        float[] viewmodelCombinedBefore = Arrays.copyOf(viewmodelMinimized.combined.val, 16);

        for (int[] invalid : new int[][] {{0, 0}, {0, 1080}, {1920, 0}, {-320, 240}, {320, -240}}) {
            assertFalse(WorldRenderer.applyViewportResize(worldMinimized, invalid[0], invalid[1]));
            assertFalse(ViewmodelRenderer.applyViewportResize(viewmodelMinimized, invalid[0], invalid[1]));
            assertEquals(1920f, worldMinimized.viewportWidth, EPSILON);
            assertEquals(1080f, worldMinimized.viewportHeight, EPSILON);
            assertEquals(1920f, viewmodelMinimized.viewportWidth, EPSILON);
            assertEquals(1080f, viewmodelMinimized.viewportHeight, EPSILON);
            assertArrayEquals(worldCombinedBefore, worldMinimized.combined.val, EPSILON);
            assertArrayEquals(viewmodelCombinedBefore, viewmodelMinimized.combined.val, EPSILON);
        }
    }

    @Test
    void viewmodelRestXScalesClampedByAspectRatioAndNeverTurnsNaNOnZeroOrNegativeSizes() {
        float rest16x9 = ViewmodelRenderer.computeRestX(1280, 720, 0f);
        assertEquals(ViewmodelRenderer.REST_X, rest16x9, EPSILON);

        float rest4x3 = ViewmodelRenderer.computeRestX(800, 600, rest16x9);
        assertEquals(ViewmodelRenderer.REST_X * 0.75f, rest4x3, EPSILON);

        float restPortrait = ViewmodelRenderer.computeRestX(400, 1000, rest16x9);
        assertEquals(ViewmodelRenderer.REST_X * 0.6f, restPortrait, EPSILON);

        float restSuperUltrawide = ViewmodelRenderer.computeRestX(3840, 1080, rest16x9);
        assertEquals(ViewmodelRenderer.REST_X * 1.2f, restSuperUltrawide, EPSILON);

        for (int[] invalid : new int[][] {{0, 0}, {0, 720}, {1280, 0}, {-800, 600}, {800, -600}}) {
            float kept = ViewmodelRenderer.computeRestX(invalid[0], invalid[1], rest4x3);
            assertTrue(Float.isFinite(kept), "restX must stay finite for " + invalid[0] + "x" + invalid[1]);
            assertEquals(rest4x3, kept, EPSILON);
        }
    }

    @Test
    void scorchColorKeepsWhiteRgbAndFadesAlphaIntoDistanceFogAndLifetime() {
        Vector3 cameraPos = new Vector3(1.5f, 0.56f, 1.5f);
        Color out = new Color();

        // Unfogged close impact (distance <= FOG_START): full white RGB and full alpha.
        WorldRenderer.scorchColor(new Vector3(1.5f, 0.56f, 1.5f + WorldRenderer.FOG_START), cameraPos, 0f, out);
        assertEquals(1f, out.r, EPSILON);
        assertEquals(1f, out.g, EPSILON);
        assertEquals(1f, out.b, EPSILON);
        assertEquals(1f, out.a, EPSILON);

        // Mid-fog impact: RGB remains (1, 1, 1) while alpha drops to 0.5 so scorch fades smoothly into wall fog.
        float midFogDist = 0.5f * (WorldRenderer.FOG_START + WorldRenderer.FOG_END);
        WorldRenderer.scorchColor(new Vector3(1.5f + midFogDist, 0.56f, 1.5f), cameraPos, 0.2f, out);
        assertEquals(0.5f, WorldRenderer.fogFactor(midFogDist), EPSILON);
        assertEquals(1f, out.r, EPSILON);
        assertEquals(1f, out.g, EPSILON);
        assertEquals(1f, out.b, EPSILON);
        assertEquals(0.5f, out.a, EPSILON);

        // At and beyond FOG_END (20u..60u weapon range): alpha reaches 0 instead of stamping opaque black over fog.
        for (float farDist : new float[] {WorldRenderer.FOG_END, 45f}) {
            WorldRenderer.scorchColor(new Vector3(1.5f + farDist, 0.56f, 1.5f), cameraPos, 0f, out);
            assertEquals(1f, WorldRenderer.fogFactor(farDist), EPSILON);
            assertEquals(1f, out.r, EPSILON);
            assertEquals(1f, out.g, EPSILON);
            assertEquals(1f, out.b, EPSILON);
            assertEquals(0f, out.a, EPSILON);
        }

        // Combined distance fog (visible = 0.5) and final-second lifetime fade ((1 - 0.9) * 5 = 0.5) -> alpha = 0.25.
        WorldRenderer.scorchColor(new Vector3(1.5f + midFogDist, 0.56f, 1.5f), cameraPos, 0.9f, out);
        assertEquals(1f, out.r, EPSILON);
        assertEquals(1f, out.g, EPSILON);
        assertEquals(1f, out.b, EPSILON);
        assertEquals(0.25f, out.a, EPSILON);

        WorldRenderer.scorchColor(new Vector3(1.5f, 0.56f, 2.5f), cameraPos, 1f, out);
        assertEquals(0f, out.a, EPSILON);
    }

    @Test
    void floorGlowHeightClearsShockGrateBarsAndCornerLugsWhileStayingBelowHoveringPickups() throws IOException {
        String objText = new String(Files.readAllBytes(resolveHazardGrateObj()), StandardCharsets.UTF_8);
        ObjModel grateModel = ObjModel.parse(objText);
        float[] positions = grateModel.getPositions();
        float maxGrateY = Float.NEGATIVE_INFINITY;
        for (int i = 1; i < positions.length; i += 3) {
            maxGrateY = Math.max(maxGrateY, positions[i]);
        }
        assertEquals(0.024f, maxGrateY, 1e-4f, "Shock grate corner lugs and coils top out at y = 0.024");
        assertTrue(WorldRenderer.FLOOR_GLOW_HEIGHT > maxGrateY + 0.002f,
            "FLOOR_GLOW_HEIGHT (" + WorldRenderer.FLOOR_GLOW_HEIGHT
                + ") must clear shock grate geometry (" + maxGrateY + ") to prevent Z-fighting");

        float minPickupHover = Float.POSITIVE_INFINITY;
        for (int step = 0; step < 200; step++) {
            minPickupHover = Math.min(minPickupHover, WorldRenderer.pickupHoverY(step * 0.05f, 2.5f, 3.5f));
        }
        assertTrue(WorldRenderer.FLOOR_GLOW_HEIGHT < minPickupHover,
            "FLOOR_GLOW_HEIGHT (" + WorldRenderer.FLOOR_GLOW_HEIGHT
                + ") must remain below hovering pickups (" + minPickupHover + ")");
    }

    @Test
    void backdropUsesPreviewTimeToAnimateGratesPickupsAndDronesWhileSimulationTimeStaysZero() {
        GameSimulation unstepped = new GameSimulation(GameMap.parse(
            "########",
            "#>~+D.X#",
            "########"));
        assertEquals(1, unstepped.map.getHazardCells().size());
        assertEquals(1, unstepped.pickups.size());
        assertEquals(1, unstepped.enemies.size());
        assertEquals(0f, unstepped.getTime(), EPSILON);
        assertEquals(0f, WorldRenderer.resolveAnimationTime(unstepped, 1.75f, false), EPSILON);
        assertEquals(1.75f, WorldRenderer.resolveAnimationTime(unstepped, 1.75f, true), EPSILON);

        float t0 = WorldRenderer.resolveAnimationTime(unstepped, 0f, true);
        float t1 = WorldRenderer.resolveAnimationTime(unstepped, 0.35f, true);
        assertNotEquals(WorldRenderer.hazardGratePulse(t0, 2, 1), WorldRenderer.hazardGratePulse(t1, 2, 1), 1e-3f);
        assertNotEquals(WorldRenderer.pickupHoverY(t0, 3.5f, 1.5f), WorldRenderer.pickupHoverY(t1, 3.5f, 1.5f), 1e-3f);
        assertNotEquals(WorldRenderer.pickupSpinDegrees(t0, 3.5f, 1.5f),
            WorldRenderer.pickupSpinDegrees(t1, 3.5f, 1.5f), 1e-3f);
        assertNotEquals(WorldRenderer.droneHoverY(t0, 4.5f), WorldRenderer.droneHoverY(t1, 4.5f), 1e-3f);
    }

    @Test
    void backdropSuppressesTransientPlayerProjectilesRailBeamsAndEnemyBolts() {
        GameMap corridor = GameMap.parse(
            "################",
            "#>.............#",
            "################");
        PlayerLoadout armed = new PlayerLoadout(
            100f, 50f, Difficulty.NORMAL, true, 8, true, 6, WeaponSlot.PISTOL);
        GameSimulation simulation = new GameSimulation(corridor, armed, false);

        // Charge and release a pistol energy blast so simulation.projectiles has a live orb in flight.
        PlayerInput input = new PlayerInput();
        input.charge = true;
        for (int i = 0; i < 40; i++) simulation.update(FRAME, input);
        input.charge = false;
        simulation.update(FRAME, input);
        assertFalse(simulation.projectiles.isEmpty(), "expected an active BlastProjectile");

        // Switch to the Rail Lance and fire so simulation.railBeams has a live beam wake while the orb is still flying.
        input.weaponSlot = 3;
        input.fire = true;
        for (int i = 0; i < 60 && simulation.railBeams.isEmpty(); i++) {
            simulation.update(FRAME, input);
            input.weaponSlot = 0;
        }
        assertFalse(simulation.projectiles.isEmpty(), "BlastProjectile still in flight along the corridor");
        assertFalse(simulation.railBeams.isEmpty(), "expected an active RailBeam");

        // Add an in-flight enemy bolt as well.
        simulation.enemyProjectiles.add(new EnemyProjectile(
            new Vector3(8f, 0.5f, 1.5f), new Vector3(-1f, 0f, 0f), 6f, 12f, null));
        assertFalse(simulation.enemyProjectiles.isEmpty(), "expected an active EnemyProjectile");

        // Gameplay mode renders all three transient shot collections.
        assertEquals(simulation.projectiles.size(), WorldRenderer.visibleProjectiles(simulation, false).size());
        assertEquals(simulation.railBeams.size(), WorldRenderer.visibleRailBeams(simulation, false).size());
        assertEquals(simulation.enemyProjectiles.size(),
            WorldRenderer.visibleEnemyProjectiles(simulation, false).size());

        // Backdrop mode suppresses all three so mid-flight meshes/bolts never leak into menu previews.
        assertTrue(WorldRenderer.visibleProjectiles(simulation, true).isEmpty());
        assertTrue(WorldRenderer.visibleRailBeams(simulation, true).isEmpty());
        assertTrue(WorldRenderer.visibleEnemyProjectiles(simulation, true).isEmpty());
    }

    @Test
    void faceCameraMaintainsUnitQuaternionAndFullDimensionsDirectlyAboveBelowAndNearCollinear() {
        float width = 0.42f;
        float height = 0.62f;
        Decal decal = Decal.newDecal(width, height, new TextureRegion());

        Vector3[] viewDirections = {
            new Vector3(0f, 0f, -1f),
            new Vector3(0.5f, -0.5f, -0.70710677f),
            new Vector3(-0.4f, 0.6f, -0.6928203f),
            new Vector3(0f, -1f, 0f),
            new Vector3(0f, 1f, 0f)
        };

        // Camera directly above decal (e.g. Shock Grate zap IMPACT spark at y = 0.09 beneath player eye at y = 0.56).
        decal.setPosition(2.5f, 0.09f, 2.5f);
        Vector3 camAbove = new Vector3(2.5f, 0.56f, 2.5f);
        Vector3 camUp = new Vector3(0f, 1f, 0f);
        for (int i = 0; i < viewDirections.length; i++) {
            Vector3 camDir = new Vector3(viewDirections[i]);
            WorldRenderer.faceCamera(decal, camAbove, camDir, camUp);
            assertUnitBillboard(decal, width, height, Vector3.Y, "cameraDirectlyAbove[" + i + "]");
            assertEquals(2.5f, camAbove.x, EPSILON);
            assertEquals(0.56f, camAbove.y, EPSILON);
            assertEquals(2.5f, camAbove.z, EPSILON);
            assertEquals(viewDirections[i].x, camDir.x, EPSILON);
            assertEquals(viewDirections[i].y, camDir.y, EPSILON);
            assertEquals(viewDirections[i].z, camDir.z, EPSILON);
            assertEquals(0f, camUp.x, EPSILON);
            assertEquals(1f, camUp.y, EPSILON);
            assertEquals(0f, camUp.z, EPSILON);
        }

        // Camera directly below decal (e.g. ceiling spark or overhead bolt/rail ring directly above eye).
        decal.setPosition(2.5f, 0.95f, 2.5f);
        Vector3 downNormal = new Vector3(0f, -1f, 0f);
        for (int i = 0; i < viewDirections.length; i++) {
            WorldRenderer.faceCamera(decal, camAbove, viewDirections[i], Vector3.Y);
            assertUnitBillboard(decal, width, height, downNormal, "cameraDirectlyBelow[" + i + "]");
        }

        // Sweep near-collinear vertical angles as well as normal diagonal/horizontal offsets.
        decal.setPosition(1.5f, 0.36f, -2.0f);
        float[] lateralOffsets = {0f, 1e-6f, 1e-4f, 1e-3f, 0.02f, 0.05f, 0.35f, 2.5f};
        for (float offset : lateralOffsets) {
            for (float dy : new float[] {0.5f, -0.5f, 0f}) {
                if (offset == 0f && dy == 0f) continue;
                Vector3 camPos = new Vector3(1.5f + offset, 0.36f + dy, -2.0f - 0.5f * offset);
                Vector3 toCamera = new Vector3(camPos).sub(decal.getPosition());
                // Sub-1e-4 offsets are coincident (len2 < 1e-8): the billboard faces back along -camDir = +Z.
                Vector3 expectedNormal = toCamera.len2() < 1e-8f ? new Vector3(Vector3.Z) : toCamera.nor();
                WorldRenderer.faceCamera(decal, camPos, new Vector3(0f, 0f, -1f), Vector3.Y);
                assertUnitBillboard(decal, width, height, expectedNormal,
                    "offset=" + offset + ",dy=" + dy);
            }
        }
    }

    @Test
    void faceCameraHandlesCoincidentCameraAndDegenerateVectorsWithoutZeroOrHalfScaleRotation() {
        float width = 0.26f;
        float height = 0.38f;
        Decal decal = Decal.newDecal(width, height, new TextureRegion());
        decal.setPosition(3.2f, 0.56f, -1.4f);

        Vector3[] coincidentPositions = {
            new Vector3(3.2f, 0.56f, -1.4f),
            new Vector3(3.2f + 1e-5f, 0.56f - 1e-5f, -1.4f)
        };
        Vector3[] camDirections = {
            new Vector3(0f, 0f, -1f),
            new Vector3(0.6f, -0.2f, -0.7745967f).nor(),
            new Vector3(0f, -1f, 0f),
            new Vector3(0f, 1f, 0f)
        };

        for (Vector3 camPos : coincidentPositions) {
            for (Vector3 camDir : camDirections) {
                Vector3 expectedNormal = new Vector3(camDir).scl(-1f).nor();
                WorldRenderer.faceCamera(decal, camPos, camDir, Vector3.Y);
                assertUnitBillboard(decal, width, height, expectedNormal,
                    "coincident pos=" + camPos + " dir=" + camDir);
            }
        }

        // Degenerate zero or null camDir/camUp fallback still yields a finite unit-norm quaternion along +Z.
        WorldRenderer.faceCamera(decal, new Vector3(3.2f, 0.56f, -1.4f), new Vector3(), new Vector3());
        assertUnitBillboard(decal, width, height, Vector3.Z, "coincidentZeroDirZeroUp");

        WorldRenderer.faceCamera(decal, new Vector3(3.2f, 0.56f, -1.4f), null, null);
        assertUnitBillboard(decal, width, height, Vector3.Z, "coincidentNullDirNullUp");
    }

    @Test
    void modelMeshRejectsNullAndInvalidBuffersWithGdxRuntimeExceptionBeforeTouchingOpenGl() {
        GdxRuntimeException nullData = assertThrows(GdxRuntimeException.class,
            () -> ModelMesh.buildIndexed(null));
        assertNotNull(nullData.getMessage());
        assertFalse(nullData.getMessage().isEmpty());

        GdxRuntimeException contextualNullBuild = assertThrows(GdxRuntimeException.class,
            () -> ModelMesh.buildIndexed(null, PistolModel.ASSET_PATH, "viewmodel pistol slide"));
        assertTrue(contextualNullBuild.getMessage().contains(PistolModel.ASSET_PATH), contextualNullBuild.getMessage());
        assertTrue(contextualNullBuild.getMessage().contains("viewmodel pistol slide"),
            contextualNullBuild.getMessage());

        GdxRuntimeException contextualRequire = assertThrows(GdxRuntimeException.class,
            () -> ModelMesh.requireMesh(null, ScattergunModel.ASSET_PATH, "viewmodel scattergun pump"));
        assertTrue(contextualRequire.getMessage().contains(ScattergunModel.ASSET_PATH), contextualRequire.getMessage());
        assertTrue(contextualRequire.getMessage().contains("viewmodel scattergun pump"),
            contextualRequire.getMessage());

        assertThrows(GdxRuntimeException.class, () -> ModelMesh.build(null));
        assertThrows(GdxRuntimeException.class, () -> ModelMesh.build(new float[0]));
        assertThrows(GdxRuntimeException.class, () -> ModelMesh.build(new float[5]));

        GdxRuntimeException badVertices = assertThrows(GdxRuntimeException.class,
            () -> ModelMesh.requireVertices(null, WorldModels.CANISTER_PATH, "canister"));
        assertTrue(badVertices.getMessage().contains(WorldModels.CANISTER_PATH), badVertices.getMessage());
        assertTrue(badVertices.getMessage().contains("canister"), badVertices.getMessage());

        GdxRuntimeException parseFailure = assertThrows(GdxRuntimeException.class,
            () -> ModelMesh.parseModel("models/broken-prop.obj", "broken prop", "v not_a_number 0 0"));
        assertTrue(parseFailure.getMessage().contains("models/broken-prop.obj"), parseFailure.getMessage());
        assertTrue(parseFailure.getMessage().contains("broken prop"), parseFailure.getMessage());
        assertInstanceOf(IllegalArgumentException.class, parseFailure.getCause());

        GdxRuntimeException nullTextFailure = assertThrows(GdxRuntimeException.class,
            () -> ModelMesh.parseModel("models/null-prop.obj", "null prop", null));
        assertTrue(nullTextFailure.getMessage().contains("models/null-prop.obj"), nullTextFailure.getMessage());
        assertTrue(nullTextFailure.getMessage().contains("null prop"), nullTextFailure.getMessage());

        GdxRuntimeException nullModelConvert = assertThrows(GdxRuntimeException.class,
            () -> ModelMesh.convert(null, PistolModel.ASSET_PATH, "viewmodel pistol", PistolModel::from));
        assertTrue(nullModelConvert.getMessage().contains(PistolModel.ASSET_PATH), nullModelConvert.getMessage());
        assertTrue(nullModelConvert.getMessage().contains("viewmodel pistol"), nullModelConvert.getMessage());
    }

    @Test
    void viewmodelUnpackHelpersWrapInvalidMaterialsUngroupedFacesAndMissingPartsWithContext() throws IOException {
        // Real generated viewmodel assets unpack cleanly with non-empty indexed meshes.
        PistolModel validPistol = ViewmodelRenderer.unpackPistol(
            ModelMesh.parseModel(PistolModel.ASSET_PATH, "viewmodel pistol", readAssetObj(PistolModel.ASSET_PATH)));
        assertTrue(validPistol.bodyMesh.triangleCount() > 0);
        assertTrue(validPistol.slideMesh.triangleCount() > 0);

        ScattergunModel validScatter = ViewmodelRenderer.unpackScattergun(ModelMesh.parseModel(
            ScattergunModel.ASSET_PATH, "viewmodel scattergun", readAssetObj(ScattergunModel.ASSET_PATH)));
        assertTrue(validScatter.bodyMesh.triangleCount() > 0);
        assertTrue(validScatter.pumpMesh.triangleCount() > 0);

        RailLanceModel validRail = ViewmodelRenderer.unpackRailLance(ModelMesh.parseModel(
            RailLanceModel.ASSET_PATH, "viewmodel rail lance", readAssetObj(RailLanceModel.ASSET_PATH)));
        assertTrue(validRail.bodyMesh.triangleCount() > 0);
        assertTrue(validRail.boltMesh.triangleCount() > 0);

        ModelVertices.IndexedMesh validHand = ViewmodelRenderer.unpackHand(
            ModelMesh.parseModel(HandModel.ASSET_PATH, "viewmodel hand", readAssetObj(HandModel.ASSET_PATH)));
        assertTrue(validHand.triangleCount() > 0);

        // Pistol missing slide (0 slide triangles -> slideMesh == null) names asset path + "viewmodel pistol slide".
        ObjModel bodyOnlyWeapon = ObjModel.parse(
            "g frame\n"
                + "usemtl gunmetal\n"
                + "v 0 0 0\n"
                + "v 1 0 0\n"
                + "v 0 1 0\n"
                + "f 1 2 3\n");
        GdxRuntimeException missingSlide = assertThrows(GdxRuntimeException.class,
            () -> ViewmodelRenderer.unpackPistol(bodyOnlyWeapon));
        assertTrue(missingSlide.getMessage().contains(PistolModel.ASSET_PATH), missingSlide.getMessage());
        assertTrue(missingSlide.getMessage().contains("viewmodel pistol slide"), missingSlide.getMessage());

        // Pistol missing body (0 body triangles -> bodyMesh == null) names asset path + "viewmodel pistol body".
        ObjModel slideOnlyPistol = ObjModel.parse(
            "g slide_top\n"
                + "usemtl gunmetal\n"
                + "v 0 0 0\n"
                + "v 1 0 0\n"
                + "v 0 1 0\n"
                + "f 1 2 3\n");
        GdxRuntimeException missingPistolBody = assertThrows(GdxRuntimeException.class,
            () -> ViewmodelRenderer.unpackPistol(slideOnlyPistol));
        assertTrue(missingPistolBody.getMessage().contains(PistolModel.ASSET_PATH), missingPistolBody.getMessage());
        assertTrue(missingPistolBody.getMessage().contains("viewmodel pistol body"), missingPistolBody.getMessage());

        // Pistol ungrouped face & unknown material wrap IllegalArgumentException with asset path + label.
        ObjModel ungroupedFace = ObjModel.parse(
            "usemtl gunmetal\n"
                + "v 0 0 0\n"
                + "v 1 0 0\n"
                + "v 0 1 0\n"
                + "f 1 2 3\n");
        GdxRuntimeException pistolUngrouped = assertThrows(GdxRuntimeException.class,
            () -> ViewmodelRenderer.unpackPistol(ungroupedFace));
        assertTrue(pistolUngrouped.getMessage().contains(PistolModel.ASSET_PATH), pistolUngrouped.getMessage());
        assertTrue(pistolUngrouped.getMessage().contains("viewmodel pistol"), pistolUngrouped.getMessage());
        assertInstanceOf(IllegalArgumentException.class, pistolUngrouped.getCause());

        ObjModel unknownMatWeapon = ObjModel.parse(
            "g frame\n"
                + "usemtl bogus_neon\n"
                + "v 0 0 0\n"
                + "v 1 0 0\n"
                + "v 0 1 0\n"
                + "f 1 2 3\n");
        GdxRuntimeException pistolBadMat = assertThrows(GdxRuntimeException.class,
            () -> ViewmodelRenderer.unpackPistol(unknownMatWeapon));
        assertTrue(pistolBadMat.getMessage().contains(PistolModel.ASSET_PATH), pistolBadMat.getMessage());
        assertTrue(pistolBadMat.getMessage().contains("viewmodel pistol"), pistolBadMat.getMessage());
        assertTrue(pistolBadMat.getMessage().contains("bogus_neon"), pistolBadMat.getMessage());

        // Scattergun missing pump, missing body, and unknown material.
        GdxRuntimeException missingPump = assertThrows(GdxRuntimeException.class,
            () -> ViewmodelRenderer.unpackScattergun(bodyOnlyWeapon));
        assertTrue(missingPump.getMessage().contains(ScattergunModel.ASSET_PATH), missingPump.getMessage());
        assertTrue(missingPump.getMessage().contains("viewmodel scattergun pump"), missingPump.getMessage());

        ObjModel pumpOnlyScatter = ObjModel.parse(
            "g pump_forend\n"
                + "usemtl gunmetal\n"
                + "v 0 0 0\n"
                + "v 1 0 0\n"
                + "v 0 1 0\n"
                + "f 1 2 3\n");
        GdxRuntimeException missingScatterBody = assertThrows(GdxRuntimeException.class,
            () -> ViewmodelRenderer.unpackScattergun(pumpOnlyScatter));
        assertTrue(missingScatterBody.getMessage().contains(ScattergunModel.ASSET_PATH),
            missingScatterBody.getMessage());
        assertTrue(missingScatterBody.getMessage().contains("viewmodel scattergun body"),
            missingScatterBody.getMessage());

        GdxRuntimeException scatterBadMat = assertThrows(GdxRuntimeException.class,
            () -> ViewmodelRenderer.unpackScattergun(unknownMatWeapon));
        assertTrue(scatterBadMat.getMessage().contains(ScattergunModel.ASSET_PATH), scatterBadMat.getMessage());
        assertTrue(scatterBadMat.getMessage().contains("viewmodel scattergun"), scatterBadMat.getMessage());
        assertInstanceOf(IllegalArgumentException.class, scatterBadMat.getCause());

        // Rail Lance missing bolt, missing body, and unknown material.
        GdxRuntimeException missingBolt = assertThrows(GdxRuntimeException.class,
            () -> ViewmodelRenderer.unpackRailLance(bodyOnlyWeapon));
        assertTrue(missingBolt.getMessage().contains(RailLanceModel.ASSET_PATH), missingBolt.getMessage());
        assertTrue(missingBolt.getMessage().contains("viewmodel rail lance bolt"), missingBolt.getMessage());

        ObjModel boltOnlyRail = ObjModel.parse(
            "g bolt_sled\n"
                + "usemtl gunmetal\n"
                + "v 0 0 0\n"
                + "v 1 0 0\n"
                + "v 0 1 0\n"
                + "f 1 2 3\n");
        GdxRuntimeException missingRailBody = assertThrows(GdxRuntimeException.class,
            () -> ViewmodelRenderer.unpackRailLance(boltOnlyRail));
        assertTrue(missingRailBody.getMessage().contains(RailLanceModel.ASSET_PATH), missingRailBody.getMessage());
        assertTrue(missingRailBody.getMessage().contains("viewmodel rail lance body"), missingRailBody.getMessage());

        GdxRuntimeException railBadMat = assertThrows(GdxRuntimeException.class,
            () -> ViewmodelRenderer.unpackRailLance(unknownMatWeapon));
        assertTrue(railBadMat.getMessage().contains(RailLanceModel.ASSET_PATH), railBadMat.getMessage());
        assertTrue(railBadMat.getMessage().contains("viewmodel rail lance"), railBadMat.getMessage());
        assertInstanceOf(IllegalArgumentException.class, railBadMat.getCause());

        // Hand unknown material and empty 0-triangle model.
        GdxRuntimeException handBadMat = assertThrows(GdxRuntimeException.class,
            () -> ViewmodelRenderer.unpackHand(unknownMatWeapon));
        assertTrue(handBadMat.getMessage().contains(HandModel.ASSET_PATH), handBadMat.getMessage());
        assertTrue(handBadMat.getMessage().contains("viewmodel hand"), handBadMat.getMessage());
        assertInstanceOf(IllegalArgumentException.class, handBadMat.getCause());

        GdxRuntimeException emptyHand = assertThrows(GdxRuntimeException.class,
            () -> ViewmodelRenderer.unpackHand(ObjModel.parse("")));
        assertTrue(emptyHand.getMessage().contains(HandModel.ASSET_PATH), emptyHand.getMessage());
        assertTrue(emptyHand.getMessage().contains("viewmodel hand"), emptyHand.getMessage());
    }

    @Test
    void worldModelUnpackHelpersWrapMissingArticulatedPartsAndUnknownMaterialsWithAssetAndPartContext()
        throws IOException {
        // Real enemy multi-part and single-part OBJ assets unpack cleanly.
        ObjModel turretObj = ModelMesh.parseModel(
            WorldModels.TURRET_PATH, "turret", readAssetObj(WorldModels.TURRET_PATH));
        for (String part : new String[] {"base", "head", "barrels"}) {
            assertTrue(WorldRenderer.unpackWorldPart(turretObj, WorldModels.TURRET_PATH, "turret", part)
                .triangleCount() > 0, "turret part " + part);
        }

        ObjModel droneObj = ModelMesh.parseModel(
            WorldModels.DRONE_PATH, "drone", readAssetObj(WorldModels.DRONE_PATH));
        assertTrue(WorldRenderer.unpackWorldModel(droneObj, WorldModels.DRONE_PATH, "drone").triangleCount() > 0);

        ObjModel guardObj = ModelMesh.parseModel(
            WorldModels.GUARD_PATH, "guard", readAssetObj(WorldModels.GUARD_PATH));
        for (String part : new String[] {"pelvis", "leg_l", "leg_r", "torso", "arm_l", "arm_r"}) {
            assertTrue(WorldRenderer.unpackWorldPart(guardObj, WorldModels.GUARD_PATH, "guard", part)
                .triangleCount() > 0, "guard part " + part);
        }

        // Missing articulated part names both the asset path and "<model> <part>" label.
        ObjModel baseOnlyTurret = ObjModel.parse(
            "# @part base parent=root pivot=0,0,0\n"
                + "# @part base\n"
                + "usemtl bot_armor\n"
                + "v 0 0 0\n"
                + "v 1 0 0\n"
                + "v 0 1 0\n"
                + "f 1 2 3\n");
        assertEquals(1,
            WorldRenderer.unpackWorldPart(baseOnlyTurret, WorldModels.TURRET_PATH, "turret", "base").triangleCount());
        GdxRuntimeException missingBarrels = assertThrows(GdxRuntimeException.class,
            () -> WorldRenderer.unpackWorldPart(baseOnlyTurret, WorldModels.TURRET_PATH, "turret", "barrels"));
        assertTrue(missingBarrels.getMessage().contains(WorldModels.TURRET_PATH), missingBarrels.getMessage());
        assertTrue(missingBarrels.getMessage().contains("turret barrels"), missingBarrels.getMessage());
        assertInstanceOf(IllegalArgumentException.class, missingBarrels.getCause());

        GdxRuntimeException missingGuardArm = assertThrows(GdxRuntimeException.class,
            () -> WorldRenderer.unpackWorldPart(baseOnlyTurret, WorldModels.GUARD_PATH, "guard", "arm_r"));
        assertTrue(missingGuardArm.getMessage().contains(WorldModels.GUARD_PATH), missingGuardArm.getMessage());
        assertTrue(missingGuardArm.getMessage().contains("guard arm_r"), missingGuardArm.getMessage());

        // Unknown material in articulated or single-part world model names asset path + label + material.
        ObjModel badGuardTorso = ObjModel.parse(
            "# @part torso parent=root pivot=0,0.4,0\n"
                + "# @part torso\n"
                + "usemtl mystery_chrome\n"
                + "v 0 0 0\n"
                + "v 1 0 0\n"
                + "v 0 1 0\n"
                + "f 1 2 3\n");
        GdxRuntimeException badGuardMat = assertThrows(GdxRuntimeException.class,
            () -> WorldRenderer.unpackWorldPart(badGuardTorso, WorldModels.GUARD_PATH, "guard", "torso"));
        assertTrue(badGuardMat.getMessage().contains(WorldModels.GUARD_PATH), badGuardMat.getMessage());
        assertTrue(badGuardMat.getMessage().contains("guard torso"), badGuardMat.getMessage());
        assertTrue(badGuardMat.getMessage().contains("mystery_chrome"), badGuardMat.getMessage());

        GdxRuntimeException badDroneMat = assertThrows(GdxRuntimeException.class,
            () -> WorldRenderer.unpackWorldModel(badGuardTorso, WorldModels.DRONE_PATH, "drone"));
        assertTrue(badDroneMat.getMessage().contains(WorldModels.DRONE_PATH), badDroneMat.getMessage());
        assertTrue(badDroneMat.getMessage().contains("drone"), badDroneMat.getMessage());
        assertTrue(badDroneMat.getMessage().contains("mystery_chrome"), badDroneMat.getMessage());
    }

    @Test
    void proceduralTexturesUploadPixmapAlwaysDisposesPixmapAndRollsBackBatchOnPartialFailure() {
        assertThrows(IllegalArgumentException.class,
            () -> ProceduralTextures.uploadPixmap(null, new Array<>(), p -> new RecordedDisposable("tex"), null));

        // 1) Successful upload disposes the temporary pixmap once and tracks the configured texture.
        Array<RecordedDisposable> tracked = new Array<>();
        RecordedDisposable okPixmap = new RecordedDisposable("okPixmap");
        AtomicInteger configured = new AtomicInteger();
        RecordedDisposable okTexture = ProceduralTextures.uploadPixmap(
            okPixmap, tracked, p -> new RecordedDisposable("okTex"), t -> configured.incrementAndGet());
        assertEquals(1, okPixmap.disposeCount, "pixmap must be disposed after upload");
        assertEquals(0, okTexture.disposeCount, "uploaded texture stays live");
        assertEquals(1, configured.get());
        assertEquals(1, tracked.size);
        assertSame(okTexture, tracked.get(0));

        // 2) Texture allocation failure still disposes pixmap in finally and keeps tracked untouched.
        RecordedDisposable failedAllocPixmap = new RecordedDisposable("failedAllocPixmap");
        GdxRuntimeException allocError = new GdxRuntimeException("GL texture upload failed");
        GdxRuntimeException thrownAlloc = assertThrows(GdxRuntimeException.class, () ->
            ProceduralTextures.uploadPixmap(failedAllocPixmap, tracked, p -> {
                throw allocError;
            }, null));
        assertSame(allocError, thrownAlloc);
        assertEquals(1, failedAllocPixmap.disposeCount, "pixmap must be disposed even when Texture(pixmap) throws");
        assertEquals(1, tracked.size, "failed upload must not add to tracked");

        // 3) Post-creation filter configuration failure disposes BOTH the newly created texture and the pixmap.
        RecordedDisposable filterFailPixmap = new RecordedDisposable("filterFailPixmap");
        List<RecordedDisposable> orphanCheck = new ArrayList<>();
        GdxRuntimeException filterError = new GdxRuntimeException("setFilter failed");
        GdxRuntimeException thrownFilter = assertThrows(GdxRuntimeException.class, () ->
            ProceduralTextures.uploadPixmap(filterFailPixmap, tracked, p -> {
                RecordedDisposable created = new RecordedDisposable("createdBeforeFilterThrows");
                orphanCheck.add(created);
                return created;
            }, t -> {
                throw filterError;
            }));
        assertSame(filterError, thrownFilter);
        assertEquals(1, filterFailPixmap.disposeCount);
        assertEquals(1, orphanCheck.size());
        assertEquals(1, orphanCheck.get(0).disposeCount, "partially configured texture must be disposed");
        assertEquals(1, tracked.size);

        // 4) Secondary pixmap.dispose() failure never masks the primary texture creation exception.
        RecordedDisposable throwingPixmap = new RecordedDisposable("throwingPixmap");
        throwingPixmap.throwOnDispose = true;
        GdxRuntimeException primaryError = new GdxRuntimeException("primary GL error");
        GdxRuntimeException preserved = assertThrows(GdxRuntimeException.class, () ->
            ProceduralTextures.uploadPixmap(throwingPixmap, tracked, p -> {
                throw primaryError;
            }, null));
        assertSame(primaryError, preserved);
        assertEquals(1, throwingPixmap.disposeCount);
        assertEquals(1, preserved.getSuppressed().length);

        // 5) Multi-texture constructor batch rollback: failing partway through 12 textures disposes all earlier ones.
        List<RecordedDisposable> batchPixmaps = new ArrayList<>();
        List<RecordedDisposable> batchTextures = new ArrayList<>();
        AtomicInteger paintedCount = new AtomicInteger();
        List<Supplier<RecordedDisposable>> painters = new ArrayList<>();
        for (int i = 0; i < 12; i++) {
            final int idx = i;
            painters.add(() -> {
                paintedCount.incrementAndGet();
                RecordedDisposable px = new RecordedDisposable("px-" + idx);
                batchPixmaps.add(px);
                return px;
            });
        }
        GdxRuntimeException midBatchError = new GdxRuntimeException("GPU OOM on texture #5");
        GdxRuntimeException batchThrown = assertThrows(GdxRuntimeException.class, () ->
            ProceduralTextures.uploadAll(painters, px -> {
                if (batchTextures.size() == 5) {
                    throw midBatchError;
                }
                RecordedDisposable tex = new RecordedDisposable("tex-" + batchTextures.size());
                if (tex.label.equals("tex-2")) {
                    tex.throwOnDispose = true;
                }
                batchTextures.add(tex);
                return tex;
            }, null));
        assertSame(midBatchError, batchThrown);
        assertEquals(6, paintedCount.get(), "painters after failure index #5 must not run");
        assertEquals(6, batchPixmaps.size());
        for (RecordedDisposable px : batchPixmaps) {
            assertEquals(1, px.disposeCount, px.label + " must be disposed");
        }
        assertEquals(5, batchTextures.size());
        for (RecordedDisposable tex : batchTextures) {
            assertEquals(1, tex.disposeCount, tex.label + " must be rolled back even when tex-2 throws on dispose");
        }

        // 6) Null-safe and exception-safe disposeAll.
        assertDoesNotThrow(() -> ProceduralTextures.disposeAll(null));
        Array<RecordedDisposable> mixed = new Array<>();
        RecordedDisposable d0 = new RecordedDisposable("d0");
        RecordedDisposable d1 = new RecordedDisposable("d1");
        d1.throwOnDispose = true;
        RecordedDisposable d2 = new RecordedDisposable("d2");
        mixed.add(d0);
        mixed.add(null);
        mixed.add(d1);
        mixed.add(d2);
        assertDoesNotThrow(() -> ProceduralTextures.disposeAll(mixed));
        assertEquals(0, mixed.size);
        assertEquals(1, d0.disposeCount);
        assertEquals(1, d1.disposeCount);
        assertEquals(1, d2.disposeCount);
    }

    /**
     * The renderers hand their backends the text of the shader files, and that text says what the strings in the
     * code said before they moved into files: the game must look the same.
     */
    @Test
    void renderersCompileTheShaderFilesWhoseTextIsWhatTheInlineStringsWere() {
        Map<String, String> objCache = new HashMap<>();
        GameMap room = GameMap.parse(
            "#####",
            "#>..#",
            "#####");

        HeadlessWorldBackend worldBackend = new HeadlessWorldBackend(objCache);
        new WorldRenderer(room, graphicsStub(1280, 720), null, worldBackend).dispose();
        assertEquals(ShaderFilesTest.BASE_WORLD_VERTEX,
            ShaderFilesTest.withoutComments(worldBackend.worldVertexSource));
        assertEquals(ShaderFilesTest.BASE_WORLD_FRAGMENT,
            ShaderFilesTest.withoutComments(worldBackend.worldFragmentSource));
        assertEquals(ShaderFilesTest.BASE_MODEL_VERTEX,
            ShaderFilesTest.withoutComments(worldBackend.modelVertexSource));
        assertEquals(ShaderFilesTest.BASE_MODEL_FRAGMENT,
            ShaderFilesTest.withoutComments(worldBackend.modelFragmentSource));

        HeadlessViewmodelBackend viewmodelBackend = new HeadlessViewmodelBackend(objCache);
        new ViewmodelRenderer(graphicsStub(1280, 720), null, viewmodelBackend).dispose();
        assertEquals(ShaderFilesTest.BASE_VIEWMODEL_VERTEX,
            ShaderFilesTest.withoutComments(viewmodelBackend.vertexSource));
        assertEquals(ShaderFilesTest.BASE_VIEWMODEL_FRAGMENT,
            ShaderFilesTest.withoutComments(viewmodelBackend.fragmentSource));
    }

    /** A shader that does not compile at launch must say which files it was read from. */
    @Test
    void shaderCompileErrorsNameTheShaderFiles() {
        Map<String, String> objCache = new HashMap<>();
        GameMap room = GameMap.parse(
            "#####",
            "#>..#",
            "#####");

        HeadlessWorldBackend failWorldShader = new HeadlessWorldBackend(objCache);
        failWorldShader.worldShaderCompiled = false;
        GdxRuntimeException worldError = assertThrows(GdxRuntimeException.class,
            () -> new WorldRenderer(room, graphicsStub(1280, 720), null, failWorldShader));
        assertTrue(worldError.getMessage().contains("shaders/world.vert.glsl"), worldError.getMessage());
        assertTrue(worldError.getMessage().contains("shaders/world.frag.glsl"), worldError.getMessage());

        HeadlessWorldBackend failModelShader = new HeadlessWorldBackend(objCache);
        failModelShader.modelShaderCompiled = false;
        GdxRuntimeException modelError = assertThrows(GdxRuntimeException.class,
            () -> new WorldRenderer(room, graphicsStub(1280, 720), null, failModelShader));
        assertTrue(modelError.getMessage().contains("shaders/model.vert.glsl"), modelError.getMessage());
        assertTrue(modelError.getMessage().contains("shaders/model.frag.glsl"), modelError.getMessage());

        HeadlessViewmodelBackend failViewmodelShader = new HeadlessViewmodelBackend(objCache);
        failViewmodelShader.shaderCompiled = false;
        GdxRuntimeException viewmodelError = assertThrows(GdxRuntimeException.class,
            () -> new ViewmodelRenderer(graphicsStub(1280, 720), null, failViewmodelShader));
        assertTrue(viewmodelError.getMessage().contains("shaders/viewmodel.vert.glsl"), viewmodelError.getMessage());
        assertTrue(viewmodelError.getMessage().contains("shaders/viewmodel.frag.glsl"), viewmodelError.getMessage());
    }

    @Test
    void viewmodelRendererRollsBackPartialConstructorResourcesAndDisposesNullAndExceptionSafely() {
        Map<String, String> objCache = new HashMap<>();

        // 1) Shader compile failure (!isCompiled()) disposes the uncompiled shader handle before rethrowing.
        HeadlessViewmodelBackend badShaderBackend = new HeadlessViewmodelBackend(objCache);
        badShaderBackend.shaderCompiled = false;
        badShaderBackend.shaderLog = "syntax error at line 7";
        GdxRuntimeException shaderError = assertThrows(GdxRuntimeException.class,
            () -> new ViewmodelRenderer(graphicsStub(1280, 720), null, badShaderBackend));
        assertTrue(shaderError.getMessage().contains("The viewmodel shader did not compile: syntax error at line 7"),
            shaderError.getMessage());
        assertEquals(1, badShaderBackend.createdShaders.size());
        assertEquals(1, badShaderBackend.createdShaders.get(0).disposeCount, "uncompiled shader must be disposed");
        assertTrue(badShaderBackend.createdMeshes.isEmpty());

        // 2) Mid-way OBJ asset load failure (rail lance) disposes shader + pistol + scattergun meshes.
        HeadlessViewmodelBackend failRailLoad = new HeadlessViewmodelBackend(objCache);
        failRailLoad.failModelAsset = RailLanceModel.ASSET_PATH;
        assertThrows(GdxRuntimeException.class,
            () -> new ViewmodelRenderer(graphicsStub(1280, 720), null, failRailLoad));
        assertEquals(1, failRailLoad.createdShaders.get(0).disposeCount);
        assertEquals(4, failRailLoad.createdMeshes.size(), "pistol(2) + scattergun(2) built before rail lance load");
        for (RecordedDisposable mesh : failRailLoad.createdMeshes) {
            assertEquals(1, mesh.disposeCount, mesh.label + " must be disposed on constructor rollback");
        }

        // 3) Mid-way mesh build failure (6th mesh: rail lance bolt) disposes shader + all 5 preceding weapon meshes.
        HeadlessViewmodelBackend failBoltBuild = new HeadlessViewmodelBackend(objCache);
        failBoltBuild.failOnMeshBuild = 6;
        assertThrows(GdxRuntimeException.class,
            () -> new ViewmodelRenderer(graphicsStub(1280, 720), null, failBoltBuild));
        assertEquals(1, failBoltBuild.createdShaders.get(0).disposeCount);
        assertEquals(5, failBoltBuild.createdMeshes.size());
        for (RecordedDisposable mesh : failBoltBuild.createdMeshes) {
            assertEquals(1, mesh.disposeCount, mesh.label + " must be disposed");
        }

        // 4) Late DecalBatch / decal-init failures dispose shader, all 7 weapon/hand meshes, and groupStrategy.
        HeadlessViewmodelBackend failBatch = new HeadlessViewmodelBackend(objCache);
        failBatch.failDecalBatch = true;
        assertThrows(GdxRuntimeException.class,
            () -> new ViewmodelRenderer(graphicsStub(1280, 720), null, failBatch));
        assertEquals(1, failBatch.createdShaders.get(0).disposeCount);
        assertEquals(7, failBatch.createdMeshes.size());
        for (RecordedDisposable mesh : failBatch.createdMeshes) assertEquals(1, mesh.disposeCount);
        assertEquals(1, failBatch.createdStrategies.get(0).disposeCount);

        HeadlessViewmodelBackend failDecals = new HeadlessViewmodelBackend(objCache);
        failDecals.failInitDecals = true;
        assertThrows(IllegalArgumentException.class,
            () -> new ViewmodelRenderer(graphicsStub(1280, 720), null, failDecals));
        assertEquals(1, failDecals.createdShaders.get(0).disposeCount);
        for (RecordedDisposable mesh : failDecals.createdMeshes) assertEquals(1, mesh.disposeCount);
        assertEquals(1, failDecals.createdStrategies.get(0).disposeCount);
        assertEquals(1, failDecals.createdBatches.get(0).disposeCount);

        // 5) Full construction preserves SLF4J "Loaded 4 viewmodel models" log, and dispose() isolates exceptions.
        HeadlessViewmodelBackend okBackend = new HeadlessViewmodelBackend(objCache);
        ViewmodelRenderer renderer;
        try (LogCapture capture = LogCapture.of(ViewmodelRenderer.class, Level.INFO)) {
            renderer = new ViewmodelRenderer(graphicsStub(1280, 720), null, okBackend);
            assertEquals(List.of("Loaded 4 viewmodel models"), capture.messages(Level.INFO));
        }
        assertEquals(7, okBackend.createdMeshes.size());
        okBackend.createdMeshes.get(2).throwOnDispose = true;
        okBackend.createdShaders.get(0).throwOnDispose = true;
        assertDoesNotThrow(renderer::dispose);
        assertEquals(1, okBackend.createdShaders.get(0).disposeCount);
        for (RecordedDisposable mesh : okBackend.createdMeshes) {
            assertEquals(1, mesh.disposeCount, mesh.label + " disposed despite throwing peer");
        }
        assertEquals(1, okBackend.createdBatches.get(0).disposeCount);
        assertEquals(1, okBackend.createdStrategies.get(0).disposeCount);

        // Second dispose() call is an idempotent no-op.
        assertDoesNotThrow(renderer::dispose);
        assertEquals(1, okBackend.createdShaders.get(0).disposeCount);
        for (RecordedDisposable mesh : okBackend.createdMeshes) assertEquals(1, mesh.disposeCount);
        assertNull(ViewmodelRenderer.disposeQuietly(null));
    }

    @Test
    void worldRendererRollsBackPartialConstructorResourcesAndDisposesNullAndExceptionSafely() {
        Map<String, String> objCache = new HashMap<>();
        GameMap room = GameMap.parse(
            "#####",
            "#>..#",
            "#####");

        // 0) Startup Warden GLB check failure disposes cleanly when all GL fields are still null.
        HeadlessWorldBackend failWardenCheck = new HeadlessWorldBackend(objCache);
        failWardenCheck.failWardenCheck = true;
        assertThrows(GdxRuntimeException.class,
            () -> new WorldRenderer(room, graphicsStub(1280, 720), null, failWardenCheck));
        assertTrue(failWardenCheck.worldShaders.isEmpty());

        // 1) World shader compile failure disposes world shader handle before rethrowing.
        HeadlessWorldBackend failWorldShader = new HeadlessWorldBackend(objCache);
        failWorldShader.worldShaderCompiled = false;
        GdxRuntimeException worldShaderErr = assertThrows(GdxRuntimeException.class,
            () -> new WorldRenderer(room, graphicsStub(1280, 720), null, failWorldShader));
        assertTrue(worldShaderErr.getMessage().contains("The world shader did not compile: world shader error"),
            worldShaderErr.getMessage());
        assertEquals(1, failWorldShader.worldShaders.get(0).disposeCount);
        assertTrue(failWorldShader.modelShaders.isEmpty());

        // 2) Model shader compile failure disposes BOTH worldShader and modelShader handles.
        HeadlessWorldBackend failModelShader = new HeadlessWorldBackend(objCache);
        failModelShader.modelShaderCompiled = false;
        GdxRuntimeException modelShaderErr = assertThrows(GdxRuntimeException.class,
            () -> new WorldRenderer(room, graphicsStub(1280, 720), null, failModelShader));
        assertTrue(modelShaderErr.getMessage().contains("The world model shader did not compile: model shader error"),
            modelShaderErr.getMessage());
        assertEquals(1, failModelShader.worldShaders.get(0).disposeCount);
        assertEquals(1, failModelShader.modelShaders.get(0).disposeCount);
        assertTrue(failModelShader.surfaceHandles.isEmpty());

        // 3) Null map or setMap failure in constructor disposes both shaders and any partial map surfaces.
        HeadlessWorldBackend failNullMap = new HeadlessWorldBackend(objCache);
        assertThrows(IllegalArgumentException.class,
            () -> new WorldRenderer(null, graphicsStub(1280, 720), null, failNullMap));
        assertEquals(1, failNullMap.worldShaders.get(0).disposeCount);
        assertEquals(1, failNullMap.modelShaders.get(0).disposeCount);

        HeadlessWorldBackend failInitialMapSurface = new HeadlessWorldBackend(objCache);
        failInitialMapSurface.failOnSurfaceBuild = 2;
        assertThrows(GdxRuntimeException.class,
            () -> new WorldRenderer(room, graphicsStub(1280, 720), null, failInitialMapSurface));
        assertEquals(1, failInitialMapSurface.worldShaders.get(0).disposeCount);
        assertEquals(1, failInitialMapSurface.modelShaders.get(0).disposeCount);
        assertEquals(1, failInitialMapSurface.surfaceHandles.size());
        assertEquals(1, failInitialMapSurface.surfaceHandles.get(0).disposeCount);

        // 4) Mid-way model failure (20th world mesh, inside articulated guard parts) rolls back shaders, map
        // surfaces, and all 19 previously built model meshes.
        HeadlessWorldBackend failGuardPart = new HeadlessWorldBackend(objCache);
        failGuardPart.failOnModelMeshBuild = 20;
        assertThrows(GdxRuntimeException.class,
            () -> new WorldRenderer(room, graphicsStub(1280, 720), null, failGuardPart));
        assertEquals(1, failGuardPart.worldShaders.get(0).disposeCount);
        assertEquals(1, failGuardPart.modelShaders.get(0).disposeCount);
        assertEquals(3, failGuardPart.surfaceHandles.size());
        for (RecordedDisposable s : failGuardPart.surfaceHandles) assertEquals(1, s.disposeCount);
        assertEquals(19, failGuardPart.modelMeshes.size());
        for (RecordedDisposable m : failGuardPart.modelMeshes) {
            assertEquals(1, m.disposeCount, m.label + " must be rolled back");
        }

        // 5) Late DecalBatch / TextureRegion failures roll back shaders, map surfaces, all 26 model meshes, and
        // groupStrategy / decalBatch.
        HeadlessWorldBackend failRegions = new HeadlessWorldBackend(objCache);
        failRegions.failInitRegions = true;
        assertThrows(IllegalArgumentException.class,
            () -> new WorldRenderer(room, graphicsStub(1280, 720), null, failRegions));
        assertEquals(1, failRegions.worldShaders.get(0).disposeCount);
        assertEquals(1, failRegions.modelShaders.get(0).disposeCount);
        for (RecordedDisposable s : failRegions.surfaceHandles) assertEquals(1, s.disposeCount);
        assertEquals(26, failRegions.modelMeshes.size());
        for (RecordedDisposable m : failRegions.modelMeshes) assertEquals(1, m.disposeCount);
        assertEquals(1, failRegions.strategies.get(0).disposeCount);
        assertEquals(1, failRegions.batches.get(0).disposeCount);

        // 6) Full construction preserves SLF4J debug/info logs, and dispose() isolates exceptions across all resources.
        HeadlessWorldBackend okBackend = new HeadlessWorldBackend(objCache);
        WorldRenderer renderer;
        try (LogCapture capture = LogCapture.of(WorldRenderer.class, Level.DEBUG)) {
            renderer = new WorldRenderer(room, graphicsStub(1280, 720), null, okBackend);
            assertEquals(List.of("Built the geometry of a 5x3 map: 1 wall textures"), capture.messages(Level.DEBUG));
            assertEquals(List.of("Loaded 19 world models"), capture.messages(Level.INFO));
        }
        assertSame(room, renderer.getCurrentMap());
        assertEquals(3, renderer.getSurfaceCount());
        assertEquals(26, okBackend.modelMeshes.size());

        okBackend.surfaceHandles.get(0).throwOnDispose = true;
        okBackend.modelMeshes.get(10).throwOnDispose = true;
        okBackend.modelShaders.get(0).throwOnDispose = true;
        assertDoesNotThrow(renderer::dispose);
        assertNull(renderer.getCurrentMap());
        assertEquals(0, renderer.getSurfaceCount());
        assertEquals(1, okBackend.worldShaders.get(0).disposeCount);
        assertEquals(1, okBackend.modelShaders.get(0).disposeCount);
        for (RecordedDisposable s : okBackend.surfaceHandles) assertEquals(1, s.disposeCount);
        for (RecordedDisposable m : okBackend.modelMeshes) assertEquals(1, m.disposeCount);
        assertEquals(1, okBackend.batches.get(0).disposeCount);
        assertEquals(1, okBackend.strategies.get(0).disposeCount);

        // Second dispose() is an idempotent no-op.
        assertDoesNotThrow(renderer::dispose);
        for (RecordedDisposable m : okBackend.modelMeshes) assertEquals(1, m.disposeCount);
        assertNull(WorldRenderer.disposeQuietly(null));
    }

    @Test
    void worldRendererSetMapBuildsAtomicallyPreservesCurrentMapOnFailureAndSwapsCleanlyOnSuccess() {
        Map<String, String> objCache = new HashMap<>();
        GameMap stage1 = GameMap.parse(
            "#####",
            "#>..#",
            "#####");
        GameMap multiWallStage = GameMap.parse(
            "##%%&&",
            "#>...&",
            "##%%&&");

        HeadlessWorldBackend backend = new HeadlessWorldBackend(objCache);
        WorldRenderer renderer = new WorldRenderer(stage1, graphicsStub(1280, 720), null, backend);
        assertSame(stage1, renderer.getCurrentMap());
        assertEquals(3, renderer.getSurfaceCount());
        List<RecordedDisposable> stage1Surfaces = new ArrayList<>(backend.surfaceHandles);
        assertEquals(3, stage1Surfaces.size());
        for (RecordedDisposable s : stage1Surfaces) assertEquals(0, s.disposeCount);

        // 1) Re-setting the identical map instance is a no-op.
        backend.clearSurfaceBatch();
        renderer.setMap(stage1);
        assertTrue(backend.surfaceHandles.isEmpty());
        for (RecordedDisposable s : stage1Surfaces) assertEquals(0, s.disposeCount);

        // 2) setMap(null) throws without disposing existing surfaces or overwriting currentMap.
        assertThrows(IllegalArgumentException.class, () -> renderer.setMap(null));
        assertSame(stage1, renderer.getCurrentMap());
        assertEquals(3, renderer.getSurfaceCount());
        for (RecordedDisposable s : stage1Surfaces) assertEquals(0, s.disposeCount);

        // 3) Simulated VBO allocation failure on surface #4 of 5 (3 walls built, floor fails):
        //    rolls back the 3 newly created wall surfaces while keeping stage1 surfaces and currentMap intact.
        backend.clearSurfaceBatch();
        backend.failOnSurfaceBuild = 4;
        assertThrows(GdxRuntimeException.class, () -> renderer.setMap(multiWallStage));
        assertSame(stage1, renderer.getCurrentMap());
        assertEquals(3, renderer.getSurfaceCount());
        for (RecordedDisposable s : stage1Surfaces) {
            assertEquals(0, s.disposeCount, "active stage1 surface must stay alive on failed setMap");
        }
        assertEquals(3, backend.surfaceHandles.size(), "3 wall surfaces built before floor failed");
        for (RecordedDisposable rolledBack : backend.surfaceHandles) {
            assertEquals(1, rolledBack.disposeCount, "temporary new surface must be disposed on setMap rollback");
        }

        // 4) Real >65,535-vertex overflow ("Too many quads for one mesh") on a 130x130 map (128x128 = 16,384 floor
        //    quads = 65,536 vertices, after perimeter '#' wall of 512 quads already succeeded):
        //    disposes the perimeter wall surface and leaves stage1 intact!
        String[] hugeRows = new String[130];
        char[] border = new char[130];
        Arrays.fill(border, '#');
        String borderRow = new String(border);
        char[] interior = new char[130];
        Arrays.fill(interior, '.');
        interior[0] = '#';
        interior[129] = '#';
        String interiorRow = new String(interior);
        interior[1] = '>';
        String spawnRow = new String(interior);
        hugeRows[0] = borderRow;
        hugeRows[1] = spawnRow;
        for (int r = 2; r < 129; r++) {
            hugeRows[r] = interiorRow;
        }
        hugeRows[129] = borderRow;
        GameMap oversizedMap = GameMap.parse(hugeRows);

        backend.clearSurfaceBatch();
        backend.failOnSurfaceBuild = -1;
        GdxRuntimeException overflow = assertThrows(GdxRuntimeException.class, () -> renderer.setMap(oversizedMap));
        assertEquals("Too many quads for one mesh: 16384", overflow.getMessage());
        assertSame(stage1, renderer.getCurrentMap());
        assertEquals(3, renderer.getSurfaceCount());
        assertEquals(1, backend.surfaceHandles.size(), "perimeter '#' wall surface built before floor overflowed");
        assertEquals(1, backend.surfaceHandles.get(0).disposeCount, "perimeter '#' wall surface must be rolled back");
        for (RecordedDisposable s : stage1Surfaces) {
            assertEquals(0, s.disposeCount, "stage1 surface must remain untouched after overflow");
        }

        // 5) Subsequent valid setMap(multiWallStage) disposes stage1 surfaces once and activates all 5 new surfaces.
        backend.clearSurfaceBatch();
        renderer.setMap(multiWallStage);
        assertSame(multiWallStage, renderer.getCurrentMap());
        assertEquals(5, renderer.getSurfaceCount());
        for (RecordedDisposable s : stage1Surfaces) {
            assertEquals(1, s.disposeCount, "old stage1 surface disposed after new map succeeded");
        }
        assertEquals(5, backend.surfaceHandles.size());
        for (RecordedDisposable s : backend.surfaceHandles) {
            assertEquals(0, s.disposeCount, "active multiWallStage surface must stay live");
        }

        renderer.dispose();
        for (RecordedDisposable s : backend.surfaceHandles) {
            assertEquals(1, s.disposeCount, "active multiWallStage surface disposed on renderer teardown");
        }
    }

    private static final class RecordedDisposable implements Disposable {
        final String label;
        int disposeCount;
        boolean throwOnDispose;

        RecordedDisposable(String label) {
            this.label = label;
        }

        @Override
        public void dispose() {
            disposeCount++;
            if (throwOnDispose) {
                throw new GdxRuntimeException("simulated dispose failure on " + label);
            }
        }
    }

    private static String cachedAssetObj(Map<String, String> cache, String assetPath) {
        return cache.computeIfAbsent(assetPath, path -> {
            try {
                return readAssetObj(path);
            } catch (IOException e) {
                throw new UncheckedIOException(e);
            }
        });
    }

    private static final class HeadlessViewmodelBackend implements ViewmodelRenderer.ResourceBackend {
        private final Map<String, String> objCache;
        final List<RecordedDisposable> createdShaders = new ArrayList<>();
        final List<RecordedDisposable> createdMeshes = new ArrayList<>();
        final List<RecordedDisposable> createdStrategies = new ArrayList<>();
        final List<RecordedDisposable> createdBatches = new ArrayList<>();
        String vertexSource;
        String fragmentSource;
        boolean shaderCompiled = true;
        String shaderLog = "";
        String failModelAsset;
        int failOnMeshBuild = -1;
        boolean failDecalBatch;
        boolean failInitDecals;
        private int meshBuildCalls;

        HeadlessViewmodelBackend(Map<String, String> objCache) {
            this.objCache = objCache;
        }

        @Override
        public Disposable createShader(String vertexShader, String fragmentShader) {
            vertexSource = vertexShader;
            fragmentSource = fragmentShader;
            RecordedDisposable d = new RecordedDisposable("viewmodelShader");
            createdShaders.add(d);
            return d;
        }

        @Override
        public boolean isShaderCompiled(Disposable shaderHandle) {
            return shaderCompiled;
        }

        @Override
        public String getShaderLog(Disposable shaderHandle) {
            return shaderLog;
        }

        @Override
        public ObjModel loadModel(String assetPath, String label) {
            if (assetPath.equals(failModelAsset)) {
                throw new GdxRuntimeException("simulated OBJ load failure for " + assetPath);
            }
            return ModelMesh.parseModel(assetPath, label, cachedAssetObj(objCache, assetPath));
        }

        @Override
        public Disposable buildMesh(ModelVertices.IndexedMesh data, String assetPath, String label) {
            ModelMesh.requireMesh(data, assetPath, label);
            meshBuildCalls++;
            if (meshBuildCalls == failOnMeshBuild) {
                throw new GdxRuntimeException("simulated VBO failure on " + label);
            }
            RecordedDisposable d = new RecordedDisposable(label);
            createdMeshes.add(d);
            return d;
        }

        @Override
        public Disposable createGroupStrategy(PerspectiveCamera camera) {
            RecordedDisposable d = new RecordedDisposable("viewmodelGroupStrategy");
            createdStrategies.add(d);
            return d;
        }

        @Override
        public Disposable createDecalBatch(Disposable groupStrategyHandle) {
            if (failDecalBatch) {
                throw new GdxRuntimeException("simulated DecalBatch allocation failure");
            }
            RecordedDisposable d = new RecordedDisposable("viewmodelDecalBatch");
            createdBatches.add(d);
            return d;
        }

        @Override
        public void initDecals(ProceduralTextures textures) {
            if (failInitDecals) {
                throw new IllegalArgumentException("simulated null ProceduralTextures");
            }
        }
    }

    private static final class HeadlessWorldBackend implements WorldRenderer.ResourceBackend {
        private final Map<String, String> objCache;
        final List<RecordedDisposable> worldShaders = new ArrayList<>();
        final List<RecordedDisposable> modelShaders = new ArrayList<>();
        final List<RecordedDisposable> surfaceHandles = new ArrayList<>();
        final List<RecordedDisposable> modelMeshes = new ArrayList<>();
        final List<RecordedDisposable> strategies = new ArrayList<>();
        final List<RecordedDisposable> batches = new ArrayList<>();
        String worldVertexSource;
        String worldFragmentSource;
        String modelVertexSource;
        String modelFragmentSource;
        boolean failWardenCheck;
        boolean worldShaderCompiled = true;
        boolean modelShaderCompiled = true;
        int failOnSurfaceBuild = -1;
        int failOnModelMeshBuild = -1;
        boolean failInitRegions;
        private int surfaceBuildCalls;
        private int modelMeshBuildCalls;

        HeadlessWorldBackend(Map<String, String> objCache) {
            this.objCache = objCache;
        }

        void clearSurfaceBatch() {
            surfaceHandles.clear();
            surfaceBuildCalls = 0;
        }

        @Override
        public void checkWardenModel() {
            if (failWardenCheck) {
                throw new GdxRuntimeException("The warden model " + WorldModels.WARDEN_PATH + " is missing");
            }
        }

        @Override
        public Disposable createWorldShader(String vertexShader, String fragmentShader) {
            worldVertexSource = vertexShader;
            worldFragmentSource = fragmentShader;
            RecordedDisposable d = new RecordedDisposable("worldShader");
            worldShaders.add(d);
            return d;
        }

        @Override
        public boolean isWorldShaderCompiled(Disposable shaderHandle) {
            return worldShaderCompiled;
        }

        @Override
        public String getWorldShaderLog(Disposable shaderHandle) {
            return "world shader error";
        }

        @Override
        public Disposable createModelShader(String vertexShader, String fragmentShader) {
            modelVertexSource = vertexShader;
            modelFragmentSource = fragmentShader;
            RecordedDisposable d = new RecordedDisposable("modelShader");
            modelShaders.add(d);
            return d;
        }

        @Override
        public boolean isModelShaderCompiled(Disposable shaderHandle) {
            return modelShaderCompiled;
        }

        @Override
        public String getModelShaderLog(Disposable shaderHandle) {
            return "model shader error";
        }

        @Override
        public WorldRenderer.Surface buildSurface(float[] quads, Texture texture) {
            WorldRenderer.buildQuadIndices(quads);
            surfaceBuildCalls++;
            if (surfaceBuildCalls == failOnSurfaceBuild) {
                throw new GdxRuntimeException("simulated surface VBO failure #" + surfaceBuildCalls);
            }
            RecordedDisposable handle = new RecordedDisposable("surface-" + surfaceBuildCalls);
            surfaceHandles.add(handle);
            return new WorldRenderer.Surface(handle, texture);
        }

        @Override
        public ObjModel loadModel(String assetPath, String label) {
            return ModelMesh.parseModel(assetPath, label, cachedAssetObj(objCache, assetPath));
        }

        @Override
        public Disposable loadIndexedMesh(String assetPath, String label,
                                          Function<ObjModel, ModelVertices.IndexedMesh> converter) {
            ObjModel model = loadModel(assetPath, label);
            ModelVertices.IndexedMesh data = ModelMesh.convertMesh(model, assetPath, label, converter);
            return buildIndexedMesh(data, assetPath, label);
        }

        @Override
        public Disposable buildIndexedMesh(ModelVertices.IndexedMesh data, String assetPath, String label) {
            ModelMesh.requireMesh(data, assetPath, label);
            modelMeshBuildCalls++;
            if (modelMeshBuildCalls == failOnModelMeshBuild) {
                throw new GdxRuntimeException("simulated model VBO failure on " + label);
            }
            RecordedDisposable handle = new RecordedDisposable(label);
            modelMeshes.add(handle);
            return handle;
        }

        @Override
        public Disposable createGroupStrategy(PerspectiveCamera camera) {
            RecordedDisposable d = new RecordedDisposable("worldGroupStrategy");
            strategies.add(d);
            return d;
        }

        @Override
        public Disposable createDecalBatch(Disposable groupStrategyHandle) {
            RecordedDisposable d = new RecordedDisposable("worldDecalBatch");
            batches.add(d);
            return d;
        }

        @Override
        public void initTextureRegions(ProceduralTextures textures) {
            if (failInitRegions) {
                throw new IllegalArgumentException("simulated null ProceduralTextures");
            }
        }
    }
}

package io.github.brantunger.gamex.render;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.badlogic.gdx.Graphics;
import com.badlogic.gdx.graphics.Color;
import com.badlogic.gdx.graphics.PerspectiveCamera;
import com.badlogic.gdx.graphics.g2d.TextureRegion;
import com.badlogic.gdx.graphics.g3d.decals.Decal;
import com.badlogic.gdx.math.Matrix4;
import com.badlogic.gdx.math.Quaternion;
import com.badlogic.gdx.math.Vector3;
import com.badlogic.gdx.utils.GdxRuntimeException;
import io.github.brantunger.gamex.GameSimulation;
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
import java.lang.reflect.Proxy;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.Arrays;

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
        ObjModel wardenObj = ModelMesh.parseModel(
            WorldModels.WARDEN_PATH, "warden", readAssetObj(WorldModels.WARDEN_PATH));
        for (String part : new String[] {"pelvis", "leg_l", "leg_r", "torso", "arm_l", "arm_r"}) {
            assertTrue(WorldRenderer.unpackWorldPart(guardObj, WorldModels.GUARD_PATH, "guard", part)
                .triangleCount() > 0, "guard part " + part);
            assertTrue(WorldRenderer.unpackWorldPart(wardenObj, WorldModels.WARDEN_PATH, "warden", part)
                .triangleCount() > 0, "warden part " + part);
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
        ObjModel badWardenTorso = ObjModel.parse(
            "# @part torso parent=root pivot=0,0.4,0\n"
                + "# @part torso\n"
                + "usemtl mystery_chrome\n"
                + "v 0 0 0\n"
                + "v 1 0 0\n"
                + "v 0 1 0\n"
                + "f 1 2 3\n");
        GdxRuntimeException badWardenMat = assertThrows(GdxRuntimeException.class,
            () -> WorldRenderer.unpackWorldPart(badWardenTorso, WorldModels.WARDEN_PATH, "warden", "torso"));
        assertTrue(badWardenMat.getMessage().contains(WorldModels.WARDEN_PATH), badWardenMat.getMessage());
        assertTrue(badWardenMat.getMessage().contains("warden torso"), badWardenMat.getMessage());
        assertTrue(badWardenMat.getMessage().contains("mystery_chrome"), badWardenMat.getMessage());

        GdxRuntimeException badDroneMat = assertThrows(GdxRuntimeException.class,
            () -> WorldRenderer.unpackWorldModel(badWardenTorso, WorldModels.DRONE_PATH, "drone"));
        assertTrue(badDroneMat.getMessage().contains(WorldModels.DRONE_PATH), badDroneMat.getMessage());
        assertTrue(badDroneMat.getMessage().contains("drone"), badDroneMat.getMessage());
        assertTrue(badDroneMat.getMessage().contains("mystery_chrome"), badDroneMat.getMessage());
    }
}

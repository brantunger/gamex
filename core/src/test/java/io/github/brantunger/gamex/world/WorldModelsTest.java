package io.github.brantunger.gamex.world;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.badlogic.gdx.math.Matrix4;
import com.badlogic.gdx.math.Vector3;
import io.github.brantunger.gamex.model.ModelRig;
import io.github.brantunger.gamex.model.ModelVertices;
import io.github.brantunger.gamex.model.ObjModel;
import io.github.brantunger.gamex.weapon.BlastProjectile;
import io.github.brantunger.gamex.weapon.EnemyProjectile;
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

/** Checks the shipped 3D world object models and how they are packed into vertices. */
class WorldModelsTest {

    private static final ObjModel CANISTER = load(WorldModels.CANISTER_PATH);
    private static final ObjModel RING_BOARD = load(WorldModels.RING_BOARD_PATH);
    private static final ObjModel BLAST_ORB = load(WorldModels.BLAST_ORB_PATH);
    private static final ObjModel MEDKIT = load(WorldModels.MEDKIT_PATH);
    private static final ObjModel ARMOR = load(WorldModels.ARMOR_PATH);
    private static final ObjModel ENERGY_CELL = load(WorldModels.ENERGY_CELL_PATH);
    private static final ObjModel TURRET = load(WorldModels.TURRET_PATH);
    private static final ObjModel DRONE = load(WorldModels.DRONE_PATH);
    private static final ObjModel GUARD = load(WorldModels.GUARD_PATH);
    private static final ObjModel WARDEN = load(WorldModels.WARDEN_PATH);
    private static final ObjModel ENEMY_BOLT = load(WorldModels.ENEMY_BOLT_PATH);
    private static final ObjModel DOOR_BULKHEAD = load(WorldModels.DOOR_BULKHEAD_PATH);
    private static final ObjModel HAZARD_GRATE = load(WorldModels.HAZARD_GRATE_PATH);
    private static final ObjModel KEYCARD = load(WorldModels.KEYCARD_PATH);
    private static final ObjModel SECURITY_CONSOLE = load(WorldModels.SECURITY_CONSOLE_PATH);
    private static final ObjModel SCATTERGUN_PICKUP = load(WorldModels.SCATTERGUN_PICKUP_PATH);
    private static final ObjModel SHELLS_PICKUP = load(WorldModels.SHELLS_PICKUP_PATH);
    private static final ObjModel[] ALL_MODELS = {
        CANISTER, RING_BOARD, BLAST_ORB, MEDKIT, ARMOR, ENERGY_CELL,
        TURRET, DRONE, GUARD, WARDEN, ENEMY_BOLT, DOOR_BULKHEAD, HAZARD_GRATE, KEYCARD, SECURITY_CONSOLE,
        SCATTERGUN_PICKUP, SHELLS_PICKUP
    };

    private static ObjModel load(String assetPath) {
        List<Path> candidates = new ArrayList<>();
        String assets = System.getProperty("gamex.assets");
        if (assets != null) candidates.add(Paths.get(assets, assetPath));
        candidates.add(Paths.get("..", "assets", assetPath));
        candidates.add(Paths.get("assets", assetPath));
        for (Path candidate : candidates) {
            if (!Files.isRegularFile(candidate)) continue;
            try {
                return ObjModel.parse(new String(Files.readAllBytes(candidate), StandardCharsets.UTF_8));
            } catch (IOException e) {
                throw new UncheckedIOException(e);
            }
        }
        List<Path> tried = new ArrayList<>();
        for (Path candidate : candidates) tried.add(candidate.toAbsolutePath().normalize());
        throw new IllegalStateException("The model " + assetPath + " was not found. Set the system property "
            + "gamex.assets to the assets folder, or run from the root project or the core project. Tried: " + tried);
    }

    private static Vector3 position(ObjModel model, int triangle, int corner) {
        int at = (triangle * 3 + corner) * 3;
        float[] positions = model.getPositions();
        return new Vector3(positions[at], positions[at + 1], positions[at + 2]);
    }

    private static String key(Vector3 corner) {
        return Math.round(corner.x * 1e6f) + "," + Math.round(corner.y * 1e6f) + "," + Math.round(corner.z * 1e6f);
    }

    @Test
    void everyGroupOfEveryWorldModelIsClosedWoundConsistentlyAndFacesOutwards() {
        for (ObjModel model : ALL_MODELS) {
            assertTrue(model.getTriangleCount() > 0);
            Set<String> groups = new LinkedHashSet<>();
            for (int triangle = 0; triangle < model.getTriangleCount(); triangle++) {
                String group = model.getGroup(triangle);
                assertFalse(group.isEmpty(), "every triangle belongs to a group");
                groups.add(group);
            }
            for (String group : groups) {
                Map<String, Integer> edges = new HashMap<>();
                double volume = 0.0;
                for (int triangle = 0; triangle < model.getTriangleCount(); triangle++) {
                    if (!model.getGroup(triangle).equals(group)) continue;
                    Vector3 a = position(model, triangle, 0);
                    Vector3 b = position(model, triangle, 1);
                    Vector3 c = position(model, triangle, 2);
                    volume += (double) a.dot(new Vector3(b).crs(c)) / 6.0;
                    for (int corner = 0; corner < 3; corner++) {
                        String from = key(position(model, triangle, corner));
                        String to = key(position(model, triangle, (corner + 1) % 3));
                        assertFalse(from.equals(to), group + " has a collapsed edge at " + from);
                        edges.merge(from + ">" + to, 1, Integer::sum);
                    }
                }
                for (Map.Entry<String, Integer> edge : edges.entrySet()) {
                    String[] ends = edge.getKey().split(">");
                    assertEquals(1, edge.getValue().intValue(), group + " repeats edge " + edge.getKey());
                    assertEquals(1, edges.getOrDefault(ends[1] + ">" + ends[0], 0).intValue(),
                        group + " has an open or mis-wound edge " + edge.getKey());
                }
                assertTrue(volume > 1e-8, group + " has volume " + volume);
            }
        }
    }

    @Test
    void canisterDimensionsMatchItsTargetCollisionCylinder() {
        float minY = Float.POSITIVE_INFINITY;
        float maxY = Float.NEGATIVE_INFINITY;
        float maxRadius = 0f;
        for (int triangle = 0; triangle < CANISTER.getTriangleCount(); triangle++) {
            for (int corner = 0; corner < 3; corner++) {
                Vector3 p = position(CANISTER, triangle, corner);
                minY = Math.min(minY, p.y);
                maxY = Math.max(maxY, p.y);
                maxRadius = Math.max(maxRadius, (float) Math.hypot(p.x, p.z));
            }
        }
        assertEquals(0f, minY, 1e-5f);
        assertEquals(Target.Kind.CANISTER.height, maxY, 1e-5f);
        assertEquals(Target.Kind.CANISTER.radius, maxRadius, 1e-5f);
    }

    @Test
    void ringBoardDimensionsMatchItsTargetCollisionCylinder() {
        float minX = Float.POSITIVE_INFINITY;
        float maxX = Float.NEGATIVE_INFINITY;
        float minY = Float.POSITIVE_INFINITY;
        float maxY = Float.NEGATIVE_INFINITY;
        float minZ = Float.POSITIVE_INFINITY;
        float maxZ = Float.NEGATIVE_INFINITY;
        for (int triangle = 0; triangle < RING_BOARD.getTriangleCount(); triangle++) {
            for (int corner = 0; corner < 3; corner++) {
                Vector3 p = position(RING_BOARD, triangle, corner);
                minX = Math.min(minX, p.x);
                maxX = Math.max(maxX, p.x);
                minY = Math.min(minY, p.y);
                maxY = Math.max(maxY, p.y);
                minZ = Math.min(minZ, p.z);
                maxZ = Math.max(maxZ, p.z);
            }
        }
        assertEquals(0f, minY, 1e-5f);
        assertEquals(Target.Kind.RING_BOARD.height, maxY, 1e-5f);
        assertEquals(-Target.Kind.RING_BOARD.radius, minX, 1e-5f);
        assertEquals(Target.Kind.RING_BOARD.radius, maxX, 1e-5f);
        assertEquals(0f, (minZ + maxZ) * 0.5f, 1e-5f);
    }

    @Test
    void blastOrbIsCentredAtTheOriginAndMatchesProjectileRadius() {
        float minX = Float.POSITIVE_INFINITY;
        float maxX = Float.NEGATIVE_INFINITY;
        float minY = Float.POSITIVE_INFINITY;
        float maxY = Float.NEGATIVE_INFINITY;
        float minZ = Float.POSITIVE_INFINITY;
        float maxZ = Float.NEGATIVE_INFINITY;
        for (int triangle = 0; triangle < BLAST_ORB.getTriangleCount(); triangle++) {
            for (int corner = 0; corner < 3; corner++) {
                Vector3 p = position(BLAST_ORB, triangle, corner);
                minX = Math.min(minX, p.x);
                maxX = Math.max(maxX, p.x);
                minY = Math.min(minY, p.y);
                maxY = Math.max(maxY, p.y);
                minZ = Math.min(minZ, p.z);
                maxZ = Math.max(maxZ, p.z);
            }
        }
        assertEquals(-BlastProjectile.RADIUS, minX, 1e-5f);
        assertEquals(BlastProjectile.RADIUS, maxX, 1e-5f);
        assertEquals(-BlastProjectile.RADIUS, minY, 1e-5f);
        assertEquals(BlastProjectile.RADIUS, maxY, 1e-5f);
        assertEquals(-BlastProjectile.RADIUS, minZ, 1e-5f);
        assertEquals(BlastProjectile.RADIUS, maxZ, 1e-5f);
    }

    @Test
    void pickupModelsStandOnTheFloorAndFitWithinPickupRadius() {
        ObjModel[] pickups = {MEDKIT, ARMOR, ENERGY_CELL, KEYCARD, SCATTERGUN_PICKUP, SHELLS_PICKUP};
        for (ObjModel pickup : pickups) {
            float minY = Float.POSITIVE_INFINITY;
            float maxY = Float.NEGATIVE_INFINITY;
            float maxRadius = 0f;
            for (int triangle = 0; triangle < pickup.getTriangleCount(); triangle++) {
                for (int corner = 0; corner < 3; corner++) {
                    Vector3 p = position(pickup, triangle, corner);
                    minY = Math.min(minY, p.y);
                    maxY = Math.max(maxY, p.y);
                    maxRadius = Math.max(maxRadius, (float) Math.hypot(p.x, p.z));
                }
            }
            assertEquals(0f, minY, 1e-5f);
            assertTrue(maxY > 0.2f && maxY <= 0.4f, "pickup height " + maxY);
            assertTrue(maxRadius <= Pickup.RADIUS + 1e-4f, "pickup radius " + maxRadius);
        }
    }

    @Test
    void verticesHaveUnitNormalsAndValidColoursAndUnknownMaterialsAreRejected() {
        for (ObjModel model : ALL_MODELS) {
            float[] vertices = WorldModels.vertices(model);
            assertEquals(model.getTriangleCount() * 3 * WorldModels.VERTEX_SIZE, vertices.length);
            for (int at = 0; at < vertices.length; at += WorldModels.VERTEX_SIZE) {
                float length = new Vector3(vertices[at + 3], vertices[at + 4], vertices[at + 5]).len();
                assertEquals(1f, length, 1e-3f, "normal length at float " + at);
                for (int channel = 6; channel < 10; channel++) {
                    assertTrue(vertices[at + channel] >= 0f && vertices[at + channel] <= 1f, "colour or gloss");
                }
            }
        }

        ObjModel stray = ObjModel.parse("v 0 0 0\nv 1 0 0\nv 0 1 0\nusemtl chrome\nf 1 2 3\n");
        IllegalArgumentException error = assertThrows(IllegalArgumentException.class,
            () -> WorldModels.vertices(stray));
        assertTrue(error.getMessage().contains("chrome"), error.getMessage());
    }

    @Test
    void enemyModelsMatchCollisionCylindersAndArticulatedGroupPrefixes() {
        assertBoundsFitCylinder(TURRET, 0f, Enemy.Kind.TURRET.height, Enemy.Kind.TURRET.radius, true);
        assertBoundsFitCylinder(DRONE, 0.1f, Enemy.Kind.DRONE.height, Enemy.Kind.DRONE.radius, false);
        assertBoundsFitCylinder(GUARD, 0f, Enemy.Kind.GUARD.height, Enemy.Kind.GUARD.radius, true);

        float[] turretBase = WorldModels.verticesForGroupPrefix(TURRET, "base_");
        float[] turretHead = WorldModels.verticesForGroupPrefix(TURRET, "head_");
        assertEquals(WorldModels.vertices(TURRET).length, turretBase.length + turretHead.length);

        float[] guardBase = WorldModels.verticesForGroupPrefix(GUARD, "base_");
        float[] guardHead = WorldModels.verticesForGroupPrefix(GUARD, "head_");
        assertEquals(WorldModels.vertices(GUARD).length, guardBase.length + guardHead.length);

        float[] wardenBase = WorldModels.verticesForGroupPrefix(WARDEN, "base_");
        float[] wardenHead = WorldModels.verticesForGroupPrefix(WARDEN, "head_");
        assertEquals(WorldModels.vertices(WARDEN).length, wardenBase.length + wardenHead.length);

        assertThrows(IllegalArgumentException.class, () -> WorldModels.verticesForGroupPrefix(DRONE, "missing_"));
    }

    @Test
    void enemyBoltIsCentredAtOriginAndMatchesProjectileRadius() {
        float minX = Float.POSITIVE_INFINITY;
        float maxX = Float.NEGATIVE_INFINITY;
        float minY = Float.POSITIVE_INFINITY;
        float maxY = Float.NEGATIVE_INFINITY;
        float minZ = Float.POSITIVE_INFINITY;
        float maxZ = Float.NEGATIVE_INFINITY;
        for (int triangle = 0; triangle < ENEMY_BOLT.getTriangleCount(); triangle++) {
            for (int corner = 0; corner < 3; corner++) {
                Vector3 p = position(ENEMY_BOLT, triangle, corner);
                minX = Math.min(minX, p.x);
                maxX = Math.max(maxX, p.x);
                minY = Math.min(minY, p.y);
                maxY = Math.max(maxY, p.y);
                minZ = Math.min(minZ, p.z);
                maxZ = Math.max(maxZ, p.z);
            }
        }
        assertEquals(-EnemyProjectile.RADIUS, minX, 1e-5f);
        assertEquals(EnemyProjectile.RADIUS, maxX, 1e-5f);
        assertEquals(-EnemyProjectile.RADIUS, minY, 1e-5f);
        assertEquals(EnemyProjectile.RADIUS, maxY, 1e-5f);
        assertEquals(-EnemyProjectile.RADIUS, minZ, 1e-5f);
        assertEquals(EnemyProjectile.RADIUS, maxZ, 1e-5f);
    }

    @Test
    void bulkheadDoorAndSecurityConsoleMatchCollisionDimensionsAndAccentMarking() {
        float minX = Float.POSITIVE_INFINITY;
        float maxX = Float.NEGATIVE_INFINITY;
        float minY = Float.POSITIVE_INFINITY;
        float maxY = Float.NEGATIVE_INFINITY;
        float minZ = Float.POSITIVE_INFINITY;
        float maxZ = Float.NEGATIVE_INFINITY;
        for (int triangle = 0; triangle < DOOR_BULKHEAD.getTriangleCount(); triangle++) {
            boolean allAtFloor = true;
            boolean allAtCeiling = true;
            boolean allAtLeftEdge = true;
            boolean allAtRightEdge = true;
            for (int corner = 0; corner < 3; corner++) {
                Vector3 p = position(DOOR_BULKHEAD, triangle, corner);
                minX = Math.min(minX, p.x);
                maxX = Math.max(maxX, p.x);
                minY = Math.min(minY, p.y);
                maxY = Math.max(maxY, p.y);
                minZ = Math.min(minZ, p.z);
                maxZ = Math.max(maxZ, p.z);
                if (Math.abs(p.y) > 1e-4f) allAtFloor = false;
                if (Math.abs(p.y - GameMap.WALL_HEIGHT) > 1e-4f) allAtCeiling = false;
                if (Math.abs(p.x + Door.HALF_SPAN) > 1e-4f) allAtLeftEdge = false;
                if (Math.abs(p.x - Door.HALF_SPAN) > 1e-4f) allAtRightEdge = false;
            }
            if ("door_armor".equals(DOOR_BULKHEAD.getMaterial(triangle))) {
                assertFalse(allAtFloor || allAtCeiling || allAtLeftEdge || allAtRightEdge,
                    "Inset door armor must not lie coplanar on outer trim planes");
            }
        }
        assertEquals(-Door.HALF_SPAN, minX, 1e-5f);
        assertEquals(Door.HALF_SPAN, maxX, 1e-5f);
        assertEquals(0f, minY, 1e-5f);
        assertEquals(GameMap.WALL_HEIGHT, maxY, 1e-5f);
        assertTrue(minZ >= -Door.HALF_THICKNESS - 1e-5f && maxZ <= Door.HALF_THICKNESS + 1e-5f);

        assertTrue(DOOR_BULKHEAD.isAccentMaterial("door_status"));
        assertFalse(DOOR_BULKHEAD.isAccentMaterial("door_armor"));
        assertTrue(HAZARD_GRATE.isAccentMaterial("hazard_coil"));
        assertFalse(HAZARD_GRATE.isAccentMaterial("hazard_frame"));
        assertTrue(WARDEN.isAccentMaterial("warden_core"));
        assertFalse(WARDEN.isAccentMaterial("warden_armor"));
        assertTrue(KEYCARD.isAccentMaterial("keycard_body"));
        assertTrue(SECURITY_CONSOLE.isAccentMaterial("console_screen"));

        float[] hazardVerts = WorldModels.vertices(HAZARD_GRATE);
        for (int t = 0; t < HAZARD_GRATE.getTriangleCount(); t++) {
            for (int c = 0; c < 3; c++) {
                Vector3 p = position(HAZARD_GRATE, t, c);
                assertTrue(p.x >= -0.49f && p.x <= 0.49f, "hazard grate X out of cell bounds: " + p.x);
                assertTrue(p.z >= -0.49f && p.z <= 0.49f, "hazard grate Z out of cell bounds: " + p.z);
                assertTrue(p.y >= 0.001f && p.y <= 0.03f, "hazard grate Y should stay slightly raised off floor: " + p.y);
            }
            float expectedAccent = HAZARD_GRATE.isAccentMaterial(HAZARD_GRATE.getMaterial(t)) ? 1f : 0f;
            for (int c = 0; c < 3; c++) {
                assertEquals(expectedAccent, hazardVerts[(t * 3 + c) * WorldModels.VERTEX_SIZE + 10], 0f);
            }
        }

        float[] doorVerts = WorldModels.vertices(DOOR_BULKHEAD);
        for (int t = 0; t < DOOR_BULKHEAD.getTriangleCount(); t++) {
            float expectedAccent = DOOR_BULKHEAD.isAccentMaterial(DOOR_BULKHEAD.getMaterial(t)) ? 1f : 0f;
            for (int c = 0; c < 3; c++) {
                assertEquals(expectedAccent, doorVerts[(t * 3 + c) * WorldModels.VERTEX_SIZE + 10], 0f);
            }
        }

        assertBoundsFitCylinder(SECURITY_CONSOLE, 0f, SecurityConsole.HEIGHT, SecurityConsole.RADIUS, true);
    }

    @Test
    void articulatedEnemyRigsAndIndexedMeshesMatchWorldConstants() {
        ModelVertices.IndexedMesh doorIndexed = WorldModels.packIndexed(DOOR_BULKHEAD);
        assertTrue(doorIndexed.vertexCount() > 0 && doorIndexed.vertexCount() < doorIndexed.indexCount());
        assertEquals(DOOR_BULKHEAD.getTriangleCount() * 3, doorIndexed.indexCount());

        for (String part : new String[] {"base", "head", "barrels"}) {
            assertTrue(TURRET.getPartSpec(part) != null, "Missing turret part " + part);
        }
        assertEquals("head", TURRET.getPartSpec("barrels").parent);
        assertEquals(WorldModels.TURRET_BARREL_PIVOT_Y, TURRET.getPartSpec("barrels").pivotY, 1e-5f);
        assertEquals(WorldModels.TURRET_BARREL_PIVOT_Z, TURRET.getPartSpec("barrels").pivotZ, 1e-5f);
        ModelRig turretRig = new ModelRig(TURRET);
        Matrix4 identity = new Matrix4();
        Vector3 turretMuzzle = turretRig.evaluateSocketWorld("muzzle_l", null, identity, new Vector3());
        assertEquals(-WorldModels.TURRET_BARREL_X, turretMuzzle.x, 1e-5f);
        assertEquals(WorldModels.TURRET_MUZZLE_Y, turretMuzzle.y, 1e-5f);
        assertEquals(WorldModels.TURRET_MUZZLE_Z, turretMuzzle.z, 1e-5f);
        assertTrue(TURRET.getSocket("muzzle_r") != null && TURRET.getSocket("telegraph") != null);

        for (String part : new String[] {"pelvis", "leg_l", "leg_r", "torso", "arm_l", "arm_r"}) {
            assertTrue(GUARD.getPartSpec(part) != null, "Missing guard part " + part);
        }
        ModelRig guardRig = new ModelRig(GUARD);
        Vector3 guardMuzzle = guardRig.evaluateSocketWorld("muzzle", null, identity, new Vector3());
        assertEquals(WorldModels.GUARD_MUZZLE_X, guardMuzzle.x, 1e-5f);
        assertEquals(WorldModels.GUARD_MUZZLE_Y, guardMuzzle.y, 1e-5f);
        assertEquals(WorldModels.GUARD_MUZZLE_Z, guardMuzzle.z, 1e-5f);
        assertTrue(GUARD.getSocket("telegraph") != null);

        ModelVertices.IndexedMesh guardLeftLeg = WorldModels.packPartIndexed(GUARD, "leg_l");
        assertTrue(guardLeftLeg.vertexCount() > 0);
        for (int i = 0; i < guardLeftLeg.vertices.length; i += WorldModels.VERTEX_SIZE) {
            assertTrue(guardLeftLeg.vertices[i + 1] <= 1e-4f, "leg_l vertices must be relative to hip pivot");
        }

        assertBoundsFitCylinder(WARDEN, 0f, Enemy.Kind.WARDEN.height, Enemy.Kind.WARDEN.radius, true);
        for (String part : new String[] {"pelvis", "leg_l", "leg_r", "torso", "arm_l", "arm_r"}) {
            assertTrue(WARDEN.getPartSpec(part) != null, "Missing warden part " + part);
        }
        ModelRig wardenRig = new ModelRig(WARDEN);
        Vector3 wardenLeftMuzzle = wardenRig.evaluateSocketWorld("muzzle_l", null, identity, new Vector3());
        assertEquals(-WorldModels.WARDEN_MUZZLE_X, wardenLeftMuzzle.x, 1e-5f);
        assertEquals(WorldModels.WARDEN_MUZZLE_Y, wardenLeftMuzzle.y, 1e-5f);
        assertEquals(WorldModels.WARDEN_MUZZLE_Z, wardenLeftMuzzle.z, 1e-5f);
        Vector3 wardenRightMuzzle = wardenRig.evaluateSocketWorld("muzzle_r", null, identity, new Vector3());
        assertEquals(WorldModels.WARDEN_MUZZLE_X, wardenRightMuzzle.x, 1e-5f);
        assertTrue(WARDEN.getSocket("telegraph") != null);

        ObjModel.SocketSpec droneTelegraph = DRONE.getSocket("telegraph");
        assertTrue(droneTelegraph != null);
        assertEquals(0f, droneTelegraph.x, 1e-5f);
        assertEquals(WorldModels.DRONE_FLARE_Y, droneTelegraph.y, 1e-5f);
        assertEquals(WorldModels.DRONE_FLARE_Z, droneTelegraph.z, 1e-5f);
    }

    private static void assertBoundsFitCylinder(ObjModel model, float minExpectedY, float expectedHeight,
                                                float maxAllowedRadius, boolean exactFloor) {
        float minY = Float.POSITIVE_INFINITY;
        float maxY = Float.NEGATIVE_INFINITY;
        float maxRadius = 0f;
        for (int triangle = 0; triangle < model.getTriangleCount(); triangle++) {
            for (int corner = 0; corner < 3; corner++) {
                Vector3 p = position(model, triangle, corner);
                minY = Math.min(minY, p.y);
                maxY = Math.max(maxY, p.y);
                maxRadius = Math.max(maxRadius, (float) Math.hypot(p.x, p.z));
            }
        }
        if (exactFloor) {
            assertEquals(minExpectedY, minY, 1e-5f);
            assertEquals(expectedHeight, maxY, 1e-5f);
        } else {
            assertTrue(minY >= 0f && maxY <= expectedHeight + 1e-5f, "height range " + minY + ".." + maxY);
        }
        assertTrue(maxRadius <= maxAllowedRadius + 1e-4f, "radius " + maxRadius + " > " + maxAllowedRadius);
    }
}

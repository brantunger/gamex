package io.github.brantunger.gamex.world;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.badlogic.gdx.math.Vector3;
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
    private static final ObjModel ENEMY_BOLT = load(WorldModels.ENEMY_BOLT_PATH);
    private static final ObjModel DOOR_BULKHEAD = load(WorldModels.DOOR_BULKHEAD_PATH);
    private static final ObjModel KEYCARD = load(WorldModels.KEYCARD_PATH);
    private static final ObjModel SECURITY_CONSOLE = load(WorldModels.SECURITY_CONSOLE_PATH);
    private static final ObjModel[] ALL_MODELS = {
        CANISTER, RING_BOARD, BLAST_ORB, MEDKIT, ARMOR, ENERGY_CELL,
        TURRET, DRONE, GUARD, ENEMY_BOLT, DOOR_BULKHEAD, KEYCARD, SECURITY_CONSOLE
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
        ObjModel[] pickups = {MEDKIT, ARMOR, ENERGY_CELL, KEYCARD};
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
    void bulkheadDoorAndSecurityConsoleMatchCollisionDimensionsAndMaterialSwap() {
        float minX = Float.POSITIVE_INFINITY;
        float maxX = Float.NEGATIVE_INFINITY;
        float minY = Float.POSITIVE_INFINITY;
        float maxY = Float.NEGATIVE_INFINITY;
        float minZ = Float.POSITIVE_INFINITY;
        float maxZ = Float.NEGATIVE_INFINITY;
        for (int triangle = 0; triangle < DOOR_BULKHEAD.getTriangleCount(); triangle++) {
            for (int corner = 0; corner < 3; corner++) {
                Vector3 p = position(DOOR_BULKHEAD, triangle, corner);
                minX = Math.min(minX, p.x);
                maxX = Math.max(maxX, p.x);
                minY = Math.min(minY, p.y);
                maxY = Math.max(maxY, p.y);
                minZ = Math.min(minZ, p.z);
                maxZ = Math.max(maxZ, p.z);
            }
        }
        assertEquals(-Door.HALF_SPAN, minX, 1e-5f);
        assertEquals(Door.HALF_SPAN, maxX, 1e-5f);
        assertEquals(0f, minY, 1e-5f);
        assertEquals(GameMap.WALL_HEIGHT, maxY, 1e-5f);
        assertTrue(minZ >= -Door.HALF_THICKNESS - 1e-5f && maxZ <= Door.HALF_THICKNESS + 1e-5f);

        float[] swapped = WorldModels.verticesReplacingMaterial(
            DOOR_BULKHEAD, "door_status", WorldModels.Material.DOOR_OPEN);
        assertEquals(WorldModels.vertices(DOOR_BULKHEAD).length, swapped.length);

        assertBoundsFitCylinder(SECURITY_CONSOLE, 0f, SecurityConsole.HEIGHT, SecurityConsole.RADIUS, true);
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

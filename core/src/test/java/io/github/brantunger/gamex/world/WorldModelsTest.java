package io.github.brantunger.gamex.world;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.badlogic.gdx.math.Vector3;
import io.github.brantunger.gamex.model.ObjModel;
import io.github.brantunger.gamex.weapon.BlastProjectile;
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
    private static final ObjModel[] ALL_MODELS = {CANISTER, RING_BOARD, BLAST_ORB, MEDKIT, ARMOR, ENERGY_CELL};

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
        ObjModel[] pickups = {MEDKIT, ARMOR, ENERGY_CELL};
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
}

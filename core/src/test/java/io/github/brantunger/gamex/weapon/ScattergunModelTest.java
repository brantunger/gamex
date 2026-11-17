package io.github.brantunger.gamex.weapon;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.badlogic.gdx.math.Vector3;
import io.github.brantunger.gamex.model.ModelVertices;
import io.github.brantunger.gamex.model.ObjModel;
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

/** Checks the shipped Combat Scattergun model, manifold integrity, rig sockets, and hand clearance. */
class ScattergunModelTest {

    private static final ObjModel RAW_MODEL = load(ScattergunModel.ASSET_PATH);
    private static final ScattergunModel MODEL = ScattergunModel.from(RAW_MODEL);
    private static final ObjModel HAND_OBJ = load(HandModel.ASSET_PATH);
    private static final String[] GLOVE_GROUPS = {
        "palm", "thumb", "index_finger", "middle_finger", "ring_finger", "little_finger",
        "knuckle_pad_0", "knuckle_pad_1", "knuckle_pad_2", "knuckle_pad_3"
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
        throw new IllegalStateException("Asset " + assetPath + " was not found. Tried: " + tried);
    }

    private static Vector3 position(float[] vertices, int vertex) {
        int at = vertex * ScattergunModel.VERTEX_SIZE;
        return new Vector3(vertices[at], vertices[at + 1], vertices[at + 2]);
    }

    private static Vector3 normal(float[] vertices, int vertex) {
        int at = vertex * ScattergunModel.VERTEX_SIZE;
        return new Vector3(vertices[at + 3], vertices[at + 4], vertices[at + 5]);
    }

    private static String key(Vector3 corner) {
        return Math.round(corner.x * 1e6f) + "," + Math.round(corner.y * 1e6f) + "," + Math.round(corner.z * 1e6f);
    }

    private static Vector3 centre(float[] vertices, ScattergunModel.Solid solid) {
        Vector3 sum = new Vector3();
        for (int i = 0; i < solid.vertexCount; i++) sum.add(position(vertices, solid.firstVertex + i));
        return sum.scl(1f / solid.vertexCount);
    }

    @Test
    void bothGroupsHoldWholeTrianglesAndIndexedBuffersWithMuzzleSocket() {
        for (ScattergunModel.Group group : ScattergunModel.Group.values()) {
            float[] vertices = MODEL.vertices(group);
            assertTrue(vertices.length > 0, group + " is empty");
            assertEquals(0, vertices.length % (3 * ScattergunModel.VERTEX_SIZE));
            int next = 0;
            for (ScattergunModel.Solid solid : MODEL.solids) {
                if (solid.group != group) continue;
                assertEquals(next, solid.firstVertex, solid.name);
                assertEquals(0, solid.vertexCount % 3, solid.name);
                next += solid.vertexCount;
            }
            assertEquals(vertices.length / ScattergunModel.VERTEX_SIZE, next, "solids of " + group);
        }
        assertNotNull(MODEL.bodyMesh);
        assertNotNull(MODEL.pumpMesh);
        assertTrue(MODEL.bodyMesh.vertexCount() > 0 && MODEL.pumpMesh.vertexCount() > 0);
        ObjModel.SocketSpec muzzle = RAW_MODEL.getSocket("muzzle");
        assertNotNull(muzzle);
        assertEquals(ScattergunModel.MUZZLE_X, muzzle.x, 1e-5f);
        assertEquals(ScattergunModel.MUZZLE_Y, muzzle.y, 1e-5f);
        assertEquals(ScattergunModel.MUZZLE_Z, muzzle.z, 1e-5f);
    }

    @Test
    void everySolidIsClosedWoundOutwardsWithUnitFaceNormalsAndValidColours() {
        Set<String> names = new HashSet<>();
        for (ScattergunModel.Solid solid : MODEL.solids) {
            names.add(solid.name);
            float[] vertices = MODEL.vertices(solid.group);
            Map<String, Integer> edges = new HashMap<>();
            Vector3 middle = centre(vertices, solid);
            for (int i = 0; i < solid.vertexCount; i += 3) {
                Vector3 a = position(vertices, solid.firstVertex + i);
                Vector3 b = position(vertices, solid.firstVertex + i + 1);
                Vector3 c = position(vertices, solid.firstVertex + i + 2);
                Vector3 wound = new Vector3(b).sub(a).crs(new Vector3(c).sub(a));
                assertTrue(wound.len() > 1e-9f, solid.name + " zero-area triangle");
                Vector3 outward = new Vector3(a).add(b).add(c).scl(1f / 3f).sub(middle);
                assertTrue(wound.dot(outward) > 0f, solid.name + " triangle faces inwards");
                Vector3 expected = new Vector3(wound).nor();
                for (int corner = 0; corner < 3; corner++) {
                    String from = key(position(vertices, solid.firstVertex + i + corner));
                    String to = key(position(vertices, solid.firstVertex + i + (corner + 1) % 3));
                    assertFalse(from.equals(to), solid.name + " degenerate edge");
                    edges.merge(from + ">" + to, 1, Integer::sum);

                    Vector3 stored = normal(vertices, solid.firstVertex + i + corner);
                    assertEquals(1f, stored.len(), 1e-4f, solid.name);
                    assertEquals(1f, stored.dot(expected), 1e-3f, solid.name);
                }
            }
            for (Map.Entry<String, Integer> edge : edges.entrySet()) {
                String[] ends = edge.getKey().split(">");
                assertEquals(1, edge.getValue().intValue(), solid.name + " repeats edge " + edge.getKey());
                assertEquals(1, edges.getOrDefault(ends[1] + ">" + ends[0], 0).intValue(),
                    solid.name + " open/mis-wound edge " + edge.getKey());
            }
        }
        assertTrue(names.contains("barrel_left"));
        assertTrue(names.contains("barrel_right"));
        assertTrue(names.contains("drum_receiver"));
        assertTrue(names.contains("pump_forend"));

        for (ScattergunModel.Group group : ScattergunModel.Group.values()) {
            float[] vertices = MODEL.vertices(group);
            for (int at = 0; at < vertices.length; at += ScattergunModel.VERTEX_SIZE) {
                for (int c = 6; c < 9; c++) {
                    assertTrue(vertices[at + c] >= 0f && vertices[at + c] <= 1f);
                }
            }
        }
    }

    @Test
    void noVertexOfTheGlovePenetratesTheScattergunAtRestOrFullPump() {
        Set<String> seen = new LinkedHashSet<>();
        for (String group : GLOVE_GROUPS) {
            for (int triangle = 0; triangle < HAND_OBJ.getTriangleCount(); triangle++) {
                if (!HAND_OBJ.getGroup(triangle).equals(group)) continue;
                for (int c = 0; c < 3; c++) {
                    int at = (triangle * 3 + c) * 3;
                    float[] pos = HAND_OBJ.getPositions();
                    Vector3 corner = new Vector3(pos[at], pos[at + 1], pos[at + 2]);
                    if (!seen.add(key(corner))) continue;
                    for (ScattergunModel.Solid solid : MODEL.solids) {
                        assertFalse(isInside(corner, solid, 0f),
                            group + " vertex " + corner + " inside scattergun solid " + solid.name);
                        if (solid.group == ScattergunModel.Group.PUMP) {
                            assertFalse(isInside(corner, solid, ScattergunModel.PUMP_TRAVEL),
                                group + " vertex " + corner + " clipped by full pump stroke in " + solid.name);
                        }
                    }
                }
            }
        }
        assertTrue(seen.size() > 500);
    }

    @Test
    void rejectsUnknownMaterialNameWithDiagnosticMessage() {
        IllegalArgumentException err = assertThrows(IllegalArgumentException.class,
            () -> ModelVertices.read(ScattergunModel.ASSET_PATH, "scattergun",
                "v 0 0 0\nv 1 0 0\nv 0 1 0\ng grip\nusemtl chrome\nf 1 2 3\n", ScattergunModel::from));
        assertTrue(err.getMessage().contains("chrome"));
    }

    private static boolean isInside(Vector3 point, ScattergunModel.Solid solid, float zShift) {
        float[] vertices = MODEL.vertices(solid.group);
        float maxOutside = -Float.MAX_VALUE;
        for (int i = 0; i < solid.vertexCount; i += 3) {
            Vector3 a = position(vertices, solid.firstVertex + i);
            Vector3 b = position(vertices, solid.firstVertex + i + 1);
            Vector3 c = position(vertices, solid.firstVertex + i + 2);
            a.z += zShift;
            b.z += zShift;
            c.z += zShift;
            Vector3 n = new Vector3(b).sub(a).crs(new Vector3(c).sub(a)).nor();
            maxOutside = Math.max(maxOutside, n.dot(new Vector3(point).sub(a)));
        }
        return maxOutside <= -2e-4f;
    }
}

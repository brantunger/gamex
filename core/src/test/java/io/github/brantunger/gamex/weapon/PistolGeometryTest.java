package io.github.brantunger.gamex.weapon;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.badlogic.gdx.math.Vector3;
import org.junit.jupiter.api.Test;

import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;

class PistolGeometryTest {

    private static final PistolGeometry GEOMETRY = PistolGeometry.build();

    private static Vector3 position(float[] vertices, int vertex) {
        int at = vertex * PistolGeometry.VERTEX_SIZE;
        return new Vector3(vertices[at], vertices[at + 1], vertices[at + 2]);
    }

    private static Vector3 normal(float[] vertices, int vertex) {
        int at = vertex * PistolGeometry.VERTEX_SIZE;
        return new Vector3(vertices[at + 3], vertices[at + 4], vertices[at + 5]);
    }

    /** A corner as a map key; corners shared between triangles are built from the same floats. */
    private static String key(Vector3 corner) {
        return Math.round(corner.x * 1e6f) + "," + Math.round(corner.y * 1e6f) + "," + Math.round(corner.z * 1e6f);
    }

    private static Vector3 centre(float[] vertices, PistolGeometry.Solid solid) {
        Vector3 sum = new Vector3();
        for (int i = 0; i < solid.vertexCount; i++) sum.add(position(vertices, solid.firstVertex + i));
        return sum.scl(1f / solid.vertexCount);
    }

    @Test
    void bothGroupsHoldWholeTrianglesAndTheSolidsCoverThemExactly() {
        for (PistolGeometry.Group group : PistolGeometry.Group.values()) {
            float[] vertices = GEOMETRY.vertices(group);
            assertTrue(vertices.length > 0, group + " is empty");
            assertEquals(0, vertices.length % (3 * PistolGeometry.VERTEX_SIZE), group + " holds whole triangles");
            int next = 0;
            for (PistolGeometry.Solid solid : GEOMETRY.solids) {
                if (solid.group != group) continue;
                assertEquals(next, solid.firstVertex, solid.name + " follows the solid before it");
                assertEquals(0, solid.vertexCount % 3, solid.name);
                next += solid.vertexCount;
            }
            assertEquals(vertices.length / PistolGeometry.VERTEX_SIZE, next, "the solids of " + group);
        }
    }

    @Test
    void everySolidIsClosedWithConsistentWinding() {
        for (PistolGeometry.Solid solid : GEOMETRY.solids) {
            float[] vertices = GEOMETRY.vertices(solid.group);
            // In a closed, consistently wound surface every directed edge a->b is matched by exactly one b->a.
            Map<String, Integer> edges = new HashMap<>();
            for (int i = 0; i < solid.vertexCount; i += 3) {
                for (int corner = 0; corner < 3; corner++) {
                    String from = key(position(vertices, solid.firstVertex + i + corner));
                    String to = key(position(vertices, solid.firstVertex + i + (corner + 1) % 3));
                    assertFalse(from.equals(to), solid.name + " has a degenerate edge");
                    edges.merge(from + ">" + to, 1, Integer::sum);
                }
            }
            for (Map.Entry<String, Integer> edge : edges.entrySet()) {
                String[] ends = edge.getKey().split(">");
                assertEquals(1, edge.getValue().intValue(), solid.name + " repeats edge " + edge.getKey());
                assertEquals(1, edges.getOrDefault(ends[1] + ">" + ends[0], 0).intValue(),
                    solid.name + " has an open or mis-wound edge " + edge.getKey());
            }
        }
    }

    @Test
    void everyTriangleIsWoundCounterClockwiseSeenFromOutside() {
        for (PistolGeometry.Solid solid : GEOMETRY.solids) {
            float[] vertices = GEOMETRY.vertices(solid.group);
            Vector3 middle = centre(vertices, solid);
            for (int i = 0; i < solid.vertexCount; i += 3) {
                Vector3 a = position(vertices, solid.firstVertex + i);
                Vector3 b = position(vertices, solid.firstVertex + i + 1);
                Vector3 c = position(vertices, solid.firstVertex + i + 2);
                Vector3 wound = new Vector3(b).sub(a).crs(new Vector3(c).sub(a));
                assertTrue(wound.len() > 1e-9f, solid.name + " has a triangle without area");
                // The solids are convex, so "outside" is away from the solid's centre.
                Vector3 outward = new Vector3(a).add(b).add(c).scl(1f / 3f).sub(middle);
                assertTrue(wound.dot(outward) > 0f, solid.name + " triangle " + i / 3 + " faces inwards");
            }
        }
    }

    @Test
    void everyVertexCarriesTheUnitNormalOfItsTriangle() {
        for (PistolGeometry.Solid solid : GEOMETRY.solids) {
            float[] vertices = GEOMETRY.vertices(solid.group);
            for (int i = 0; i < solid.vertexCount; i += 3) {
                Vector3 a = position(vertices, solid.firstVertex + i);
                Vector3 b = position(vertices, solid.firstVertex + i + 1);
                Vector3 c = position(vertices, solid.firstVertex + i + 2);
                Vector3 expected = new Vector3(b).sub(a).crs(new Vector3(c).sub(a)).nor();
                for (int corner = 0; corner < 3; corner++) {
                    Vector3 stored = normal(vertices, solid.firstVertex + i + corner);
                    assertEquals(1f, stored.len(), 1e-4f, solid.name + " normal length");
                    assertEquals(1f, stored.dot(expected), 1e-3f, solid.name + " normal direction");
                }
            }
        }
    }

    @Test
    void coloursAreWithinRange() {
        for (PistolGeometry.Group group : PistolGeometry.Group.values()) {
            float[] vertices = GEOMETRY.vertices(group);
            for (int at = 0; at < vertices.length; at += PistolGeometry.VERTEX_SIZE) {
                for (int channel = 6; channel < 9; channel++) {
                    assertTrue(vertices[at + channel] >= 0f && vertices[at + channel] <= 1f, "colour channel");
                }
            }
        }
    }

    @Test
    void modelHasTheRecognisablePartsOfAPistol() {
        Set<String> names = new HashSet<>();
        for (PistolGeometry.Solid solid : GEOMETRY.solids) names.add(solid.name);

        for (String part : new String[] {"slide", "slide top", "slide serration 0", "slide front sight",
            "slide rear sight left", "slide rear sight right", "frame", "barrel", "bore", "trigger guard 0",
            "trigger", "hammer", "beavertail", "grip", "magazine plate", "stipple left 0/0"}) {
            assertTrue(names.contains(part), "missing " + part);
        }
    }

    @Test
    void everySolidIsConvex() {
        for (PistolGeometry.Solid solid : GEOMETRY.solids) {
            float[] vertices = GEOMETRY.vertices(solid.group);
            for (int i = 0; i < solid.vertexCount; i += 3) {
                Vector3 onFace = position(vertices, solid.firstVertex + i);
                Vector3 faceNormal = normal(vertices, solid.firstVertex + i);
                // No corner of a convex solid lies in front of any of its faces.
                for (int other = 0; other < solid.vertexCount; other++) {
                    float ahead = position(vertices, solid.firstVertex + other).sub(onFace).dot(faceNormal);
                    assertTrue(ahead < 2e-5f, solid.name + " has a corner " + ahead + " in front of triangle " + i / 3);
                }
            }
        }
    }

    @Test
    void glossIsWithinRangeAndOnlyMetalShines() {
        for (PistolGeometry.Solid solid : GEOMETRY.solids) {
            float gloss = GEOMETRY.vertices(solid.group)[solid.firstVertex * PistolGeometry.VERTEX_SIZE + 9];
            assertTrue(gloss >= 0f && gloss <= 1f, solid.name + " gloss " + gloss);
            if (solid.name.equals("slide top") || solid.name.equals("barrel")) assertEquals(1f, gloss, 0f);
            if (solid.name.equals("grip") || solid.name.equals("frame")) {
                assertEquals(0f, gloss, 0f, solid.name + " is matt");
            }
        }
    }

    @Test
    void slideGroupHoldsExactlyTheSolidsNamedAsPartsOfTheSlide() {
        for (PistolGeometry.Solid solid : GEOMETRY.solids) {
            assertEquals(solid.name.startsWith("slide") ? PistolGeometry.Group.SLIDE : PistolGeometry.Group.BODY,
                solid.group, solid.name);
        }
    }

    @Test
    void muzzleIsTheForemostPointOfTheGunOnTheBarrelAxis() {
        float foremost = Float.POSITIVE_INFINITY;
        for (PistolGeometry.Group group : PistolGeometry.Group.values()) {
            float[] vertices = GEOMETRY.vertices(group);
            for (int at = 0; at < vertices.length; at += PistolGeometry.VERTEX_SIZE) {
                foremost = Math.min(foremost, vertices[at + 2]);
            }
        }

        // The barrel points along negative z.
        assertEquals(PistolGeometry.MUZZLE_Z, foremost, 1e-6f);
        PistolGeometry.Solid bore = null;
        for (PistolGeometry.Solid solid : GEOMETRY.solids) {
            if (solid.name.equals("bore")) bore = solid;
        }
        Vector3 low = new Vector3(Float.MAX_VALUE, Float.MAX_VALUE, 0f);
        Vector3 high = new Vector3(-Float.MAX_VALUE, -Float.MAX_VALUE, 0f);
        for (int i = 0; i < bore.vertexCount; i++) {
            Vector3 corner = position(GEOMETRY.body, bore.firstVertex + i);
            low.set(Math.min(low.x, corner.x), Math.min(low.y, corner.y), 0f);
            high.set(Math.max(high.x, corner.x), Math.max(high.y, corner.y), 0f);
        }
        assertEquals(PistolGeometry.MUZZLE_X, (low.x + high.x) * 0.5f, 1e-5f);
        assertEquals(PistolGeometry.MUZZLE_Y, (low.y + high.y) * 0.5f, 1e-5f);
    }

    @Test
    void gripIsRakedBackBelowTheSlide() {
        for (PistolGeometry.Solid solid : GEOMETRY.solids) {
            if (!solid.name.equals("grip")) continue;
            float topZ = 0f;
            float bottomZ = 0f;
            float topY = Float.NEGATIVE_INFINITY;
            float bottomY = Float.POSITIVE_INFINITY;
            for (int i = 0; i < solid.vertexCount; i++) {
                Vector3 corner = position(GEOMETRY.body, solid.firstVertex + i);
                if (corner.y > topY || (corner.y == topY && corner.z > topZ)) {
                    topY = corner.y;
                    topZ = corner.z;
                }
                if (corner.y < bottomY || (corner.y == bottomY && corner.z > bottomZ)) {
                    bottomY = corner.y;
                    bottomZ = corner.z;
                }
            }
            assertTrue(topY < 0f, "the grip hangs below the slide");
            assertTrue(bottomZ > topZ, "the heel of the grip is further back than its top");
        }
    }
}

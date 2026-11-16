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
    void modelHasTheRecognisablePartsOfAPistolAndAHand() {
        Set<String> names = new HashSet<>();
        for (PistolGeometry.Solid solid : GEOMETRY.solids) names.add(solid.name);

        for (String part : new String[] {"slide", "slide top", "slide serration 0", "slide front sight",
            "slide rear sight left", "slide rear sight right", "frame", "barrel", "bore", "trigger guard 0",
            "trigger", "hammer", "beavertail", "grip", "magazine plate", "stipple left 0/0"}) {
            assertTrue(names.contains(part), "missing " + part);
        }
    }

    @Test
    void handHasFourJointedFingersAThumbAPalmAndAnArm() {
        Set<String> names = new HashSet<>();
        for (PistolGeometry.Solid solid : GEOMETRY.solids) names.add(solid.name);

        for (String finger : new String[] {"index", "middle", "ring", "little"}) {
            for (String piece : new String[] {" knuckle", " finger base", " first joint", " finger middle",
                " second joint", " finger end", " fingertip"}) {
                assertTrue(names.contains(finger + piece), "missing " + finger + piece);
            }
        }
        for (String part : new String[] {"thumb base", "thumb knuckle", "thumb", "thumb joint", "thumb end",
            "thumb tip", "ball of thumb", "heel of hand", "back of hand", "wrist", "cuff", "sleeve"}) {
            assertTrue(names.contains(part), "missing " + part);
        }
    }

    @Test
    void fingertipsAndThumbLieOnTheLeftOfTheGripAndKnucklesOnTheRight() {
        for (PistolGeometry.Solid solid : GEOMETRY.solids) {
            float x = centre(GEOMETRY.body, solid).x;
            boolean wrappingTip = solid.name.endsWith("fingertip") && !solid.name.startsWith("index");
            if (wrappingTip || solid.name.equals("thumb tip")) {
                assertTrue(x < -0.013f, solid.name + " is on the left side, at x = " + x);
            }
            if (solid.name.endsWith(" knuckle") && !solid.name.startsWith("thumb")) {
                assertTrue(x > 0.013f, solid.name + " is on the right side, at x = " + x);
            }
        }
    }

    private static PistolGeometry.Solid solid(String name) {
        for (PistolGeometry.Solid solid : GEOMETRY.solids) {
            if (solid.name.equals(name)) return solid;
        }
        throw new AssertionError("no solid named " + name);
    }

    private static float[] colour(PistolGeometry.Solid solid) {
        float[] vertices = GEOMETRY.vertices(solid.group);
        int at = solid.firstVertex * PistolGeometry.VERTEX_SIZE;
        return new float[] {vertices[at + 6], vertices[at + 7], vertices[at + 8]};
    }

    private static boolean isGlove(String name) {
        return name.contains("finger") || name.contains("joint") || name.contains("thumb") || name.contains("hand")
            || name.equals("wrist");
    }

    @Test
    void gloveIsOneDarkColourWithOnlyTheKnucklePadsATouchLighter() {
        float[] glove = colour(solid("heel of hand"));
        assertTrue(glove[0] < 0.3f && glove[1] < 0.3f && glove[2] < 0.3f, "the glove is dark");
        int gloveSolids = 0;
        for (PistolGeometry.Solid solid : GEOMETRY.solids) {
            float[] colour = colour(solid);
            if (isGlove(solid.name)) {
                gloveSolids++;
                for (int channel = 0; channel < 3; channel++) {
                    assertEquals(glove[channel], colour[channel], 0f, solid.name + " is the colour of the glove");
                }
            } else if (solid.name.endsWith(" knuckle")) {
                for (int channel = 0; channel < 3; channel++) {
                    float lighter = colour[channel] - glove[channel];
                    assertTrue(lighter > 0f && lighter < 0.08f, solid.name + " is only a touch lighter: " + lighter);
                }
            }
        }
        assertTrue(gloveSolids > 30, "glove solids found: " + gloveSolids);
    }

    @Test
    void fourFingersTogetherAreAboutAsTallAsTheGripIsLong() {
        float fingersTop = Float.NEGATIVE_INFINITY;
        float fingersBottom = Float.POSITIVE_INFINITY;
        for (PistolGeometry.Solid solid : GEOMETRY.solids) {
            if (!solid.name.endsWith("finger base")) continue;
            for (int i = 0; i < solid.vertexCount; i++) {
                float y = position(GEOMETRY.body, solid.firstVertex + i).y;
                fingersTop = Math.max(fingersTop, y);
                fingersBottom = Math.min(fingersBottom, y);
            }
        }
        PistolGeometry.Solid grip = solid("grip");
        float gripTop = Float.NEGATIVE_INFINITY;
        float gripBottom = Float.POSITIVE_INFINITY;
        for (int i = 0; i < grip.vertexCount; i++) {
            float y = position(GEOMETRY.body, grip.firstVertex + i).y;
            gripTop = Math.max(gripTop, y);
            gripBottom = Math.min(gripBottom, y);
        }

        float ratio = (fingersTop - fingersBottom) / (gripTop - gripBottom);
        assertTrue(ratio > 0.8f && ratio < 1.1f, "hand width over grip length: " + ratio);
    }

    @Test
    void forearmRunsBackFromTheWristRoughlyInLineWithTheBarrel() {
        Vector3 wrist = centre(GEOMETRY.body, solid("wrist joint"));
        Vector3 cuff = centre(GEOMETRY.body, solid("cuff"));
        Vector3 sleeve = centre(GEOMETRY.body, solid("sleeve"));
        Vector3 heel = centre(GEOMETRY.body, solid("heel of hand"));

        // Hand, wrist, cuff and sleeve follow one another back towards the shooter.
        assertTrue(heel.z < wrist.z && wrist.z < cuff.z && cuff.z < sleeve.z, "order along the arm");
        Vector3 arm = new Vector3(sleeve).sub(wrist).nor();
        assertTrue(arm.z > (float) Math.cos(Math.toRadians(25.0)), "within 25 degrees of the barrel: " + arm);
        assertTrue(arm.x > 0f, "towards the shooter's right: " + arm);
        assertTrue(arm.y < 0f, "and downwards: " + arm);
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
            if (solid.name.equals("grip") || solid.name.equals("frame") || solid.name.equals("sleeve")) {
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

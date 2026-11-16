package io.github.brantunger.gamex.weapon;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.badlogic.gdx.math.Vector3;
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
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

/** Checks the shipped hand model file, and how it is turned into vertices. */
class HandModelTest {

    private static final ObjModel MODEL = load();
    private static final PistolGeometry PISTOL = PistolGeometry.build();
    /** The pistol's grip, the solid the hand is wrapped round. Every grip measurement below is read off it. */
    private static final PistolGeometry.Solid GRIP = pistolSolid("grip");
    /** The half-width of the grip with its stippling, which is the widest thing a finger lies against. */
    private static final float GRIP_HALF_WIDTH = gripHalfWidth();
    /** How far a glove vertex may be from the grip's own surface and still count as lying on it. */
    private static final float TOUCHING = 0.0015f;
    /**
     * The most the stippling may stand between the glove and the sides of the grip. It is a fixed allowance, not read
     * off the pistol, so that a grip that has become narrower than the hand was sculpted for is noticed.
     */
    private static final float STUDS = 0.002f;
    private static final String[] GLOVE_GROUPS = {"palm", "index_finger", "middle_finger", "ring_finger",
        "little_finger", "thumb"};
    private static final String[] WRAPPING_FINGERS = {"middle_finger", "ring_finger", "little_finger"};

    /**
     * Reads the model from the assets folder. The build passes the folder in as {@code gamex.assets}; a run from an
     * IDE has no such property, so the folder is then looked for from the working directory, which may be the
     * {@code core} project or the root project.
     */
    private static ObjModel load() {
        List<Path> candidates = new ArrayList<>();
        String assets = System.getProperty("gamex.assets");
        if (assets != null) candidates.add(Paths.get(assets, HandModel.ASSET_PATH));
        candidates.add(Paths.get("..", "assets", HandModel.ASSET_PATH));
        candidates.add(Paths.get("assets", HandModel.ASSET_PATH));
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
        throw new IllegalStateException("The hand model was not found. Set the system property gamex.assets to the "
            + "assets folder, or run from the root project or the core project. Tried: " + tried);
    }

    private static PistolGeometry.Solid pistolSolid(String name) {
        for (PistolGeometry.Solid solid : PISTOL.solids) {
            if (solid.name.equals(name)) return solid;
        }
        throw new IllegalStateException("The pistol has no solid named " + name);
    }

    /** A corner of one of the pistol's solids. */
    private static Vector3 pistolCorner(PistolGeometry.Solid solid, int corner) {
        int at = (solid.firstVertex + corner) * PistolGeometry.VERTEX_SIZE;
        float[] vertices = PISTOL.vertices(solid.group);
        return new Vector3(vertices[at], vertices[at + 1], vertices[at + 2]);
    }

    private static float gripHalfWidth() {
        float widest = 0f;
        for (PistolGeometry.Solid solid : PISTOL.solids) {
            if (solid != GRIP && !solid.name.startsWith("stipple")) continue;
            for (int i = 0; i < solid.vertexCount; i++) widest = Math.max(widest, Math.abs(pistolCorner(solid, i).x));
        }
        return widest;
    }

    /** @return the height of the top of the grip (index 0) and of its bottom (index 1). */
    private static float[] gripHeights() {
        float top = -Float.MAX_VALUE;
        float bottom = Float.MAX_VALUE;
        for (int i = 0; i < GRIP.vertexCount; i++) {
            float y = pistolCorner(GRIP, i).y;
            top = Math.max(top, y);
            bottom = Math.min(bottom, y);
        }
        return new float[] {top, bottom};
    }

    /**
     * @return how far a point is outside a convex solid of the pistol: its distance in front of the face it is
     *     furthest in front of. Negative inside. Beyond an edge or a corner this is a little less than the true
     *     distance.
     */
    private static float outside(Vector3 point, PistolGeometry.Solid solid) {
        float[] vertices = PISTOL.vertices(solid.group);
        float furthest = -Float.MAX_VALUE;
        for (int i = 0; i < solid.vertexCount; i += 3) {
            int at = (solid.firstVertex + i) * PistolGeometry.VERTEX_SIZE;
            float ahead = (point.x - vertices[at]) * vertices[at + 3] + (point.y - vertices[at + 1]) * vertices[at + 4]
                + (point.z - vertices[at + 2]) * vertices[at + 5];
            furthest = Math.max(furthest, ahead);
        }
        return furthest;
    }

    private static Vector3 position(int triangle, int corner) {
        int at = (triangle * 3 + corner) * 3;
        float[] positions = MODEL.getPositions();
        return new Vector3(positions[at], positions[at + 1], positions[at + 2]);
    }

    private static Vector3 normal(int triangle, int corner) {
        int at = (triangle * 3 + corner) * 3;
        float[] normals = MODEL.getNormals();
        return new Vector3(normals[at], normals[at + 1], normals[at + 2]);
    }

    /** Every corner position of a group, repeats included. */
    private static List<Vector3> corners(String group) {
        List<Vector3> corners = new ArrayList<>();
        for (int triangle = 0; triangle < MODEL.getTriangleCount(); triangle++) {
            if (!MODEL.getGroup(triangle).equals(group)) continue;
            for (int corner = 0; corner < 3; corner++) corners.add(position(triangle, corner));
        }
        assertFalse(corners.isEmpty(), "the model has no group " + group);
        return corners;
    }

    private static Vector3 centre(String group) {
        Vector3 low = new Vector3(Float.MAX_VALUE, Float.MAX_VALUE, Float.MAX_VALUE);
        Vector3 high = new Vector3(-Float.MAX_VALUE, -Float.MAX_VALUE, -Float.MAX_VALUE);
        for (Vector3 corner : corners(group)) {
            low.set(Math.min(low.x, corner.x), Math.min(low.y, corner.y), Math.min(low.z, corner.z));
            high.set(Math.max(high.x, corner.x), Math.max(high.y, corner.y), Math.max(high.z, corner.z));
        }
        return low.add(high).scl(0.5f);
    }

    private static String key(Vector3 corner) {
        return Math.round(corner.x * 1e6f) + "," + Math.round(corner.y * 1e6f) + "," + Math.round(corner.z * 1e6f);
    }

    /** The z of the front of the grip at a height, read off the real grip. */
    private static float frontStrap(float y) {
        float[] heights = gripHeights();
        // The front strap runs between the foremost corner of the grip's top and the foremost corner of its bottom.
        float topZ = Float.MAX_VALUE;
        float bottomZ = Float.MAX_VALUE;
        for (int i = 0; i < GRIP.vertexCount; i++) {
            Vector3 corner = pistolCorner(GRIP, i);
            if (corner.y == heights[0]) topZ = Math.min(topZ, corner.z);
            if (corner.y == heights[1]) bottomZ = Math.min(bottomZ, corner.z);
        }
        return topZ + (heights[0] - y) * (bottomZ - topZ) / (heights[0] - heights[1]);
    }

    @Test
    void shippedModelIsDetailedButNotHuge() {
        assertTrue(MODEL.getVertexCount() >= 1500 && MODEL.getVertexCount() <= 6000,
            "vertices: " + MODEL.getVertexCount());
        assertTrue(MODEL.getTriangleCount() >= 3000 && MODEL.getTriangleCount() <= 12000,
            "triangles: " + MODEL.getTriangleCount());
    }

    @Test
    void shippedModelHasEveryPartOfTheHandAndArmInTheRightMaterial() {
        Map<String, String> materialOfGroup = new HashMap<>();
        for (int triangle = 0; triangle < MODEL.getTriangleCount(); triangle++) {
            String before = materialOfGroup.put(MODEL.getGroup(triangle), MODEL.getMaterial(triangle));
            if (before != null) assertEquals(before, MODEL.getMaterial(triangle), MODEL.getGroup(triangle));
        }

        for (String glove : GLOVE_GROUPS) assertEquals("glove", materialOfGroup.get(glove), glove);
        for (int pad = 0; pad < 4; pad++) assertEquals("pads", materialOfGroup.get("knuckle_pad_" + pad));
        assertEquals("cuff", materialOfGroup.get("cuff"));
        assertEquals("strap", materialOfGroup.get("cuff_strap"));
        assertEquals("sleeve", materialOfGroup.get("sleeve"));
        assertEquals(13, materialOfGroup.size(), "groups: " + materialOfGroup.keySet());
    }

    @Test
    void verticesAreLaidOutLikeThePistolsWithUnitNormalsAndTheMaterialsColours() {
        float[] vertices = HandModel.vertices(MODEL);

        assertEquals(MODEL.getTriangleCount() * 3 * PistolGeometry.VERTEX_SIZE, vertices.length);
        Set<String> colours = new LinkedHashSet<>();
        for (int at = 0; at < vertices.length; at += PistolGeometry.VERTEX_SIZE) {
            float length = new Vector3(vertices[at + 3], vertices[at + 4], vertices[at + 5]).len();
            assertEquals(1f, length, 1e-3f, "normal length at float " + at);
            for (int channel = 6; channel < 10; channel++) {
                assertTrue(vertices[at + channel] >= 0f && vertices[at + channel] <= 1f, "colour or gloss");
            }
            colours.add(vertices[at + 6] + "/" + vertices[at + 7] + "/" + vertices[at + 8]);
        }
        assertEquals(HandModel.Material.values().length, colours.size(), "one colour per material: " + colours);
        // The first triangle is the palm's: its first corner comes through unchanged.
        Vector3 first = position(0, 0);
        assertEquals(first.x, vertices[0], 0f);
        assertEquals(first.y, vertices[1], 0f);
        assertEquals(first.z, vertices[2], 0f);
        Vector3 firstNormal = normal(0, 0);
        assertEquals(firstNormal.x, vertices[3], 0f);
        assertEquals(firstNormal.z, vertices[5], 0f);
    }

    @Test
    void modelWithAnUnknownMaterialIsRejected() {
        ObjModel stray = ObjModel.parse("v 0 0 0\nv 1 0 0\nv 0 1 0\nusemtl chrome\nf 1 2 3\n");

        IllegalArgumentException error = assertThrows(IllegalArgumentException.class,
            () -> HandModel.vertices(stray));
        assertTrue(error.getMessage().contains("chrome"), error.getMessage());
    }

    @Test
    void everyPartIsAClosedSurfaceWoundConsistently() {
        Set<String> groups = new LinkedHashSet<>();
        for (int triangle = 0; triangle < MODEL.getTriangleCount(); triangle++) groups.add(MODEL.getGroup(triangle));
        for (String group : groups) {
            // In a closed, consistently wound surface every directed edge a->b is matched by exactly one b->a.
            Map<String, Integer> edges = new HashMap<>();
            for (int triangle = 0; triangle < MODEL.getTriangleCount(); triangle++) {
                if (!MODEL.getGroup(triangle).equals(group)) continue;
                for (int corner = 0; corner < 3; corner++) {
                    String from = key(position(triangle, corner));
                    String to = key(position(triangle, (corner + 1) % 3));
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
        }
    }

    @Test
    void everyPartFacesOutwards() {
        Map<String, Double> volumes = new HashMap<>();
        for (int triangle = 0; triangle < MODEL.getTriangleCount(); triangle++) {
            Vector3 a = position(triangle, 0);
            Vector3 b = position(triangle, 1);
            Vector3 c = position(triangle, 2);
            // The signed volume of the tetrahedron from the origin; over a closed surface these add up to its volume,
            // positive when the triangles run counter-clockwise seen from outside.
            volumes.merge(MODEL.getGroup(triangle), (double) a.dot(b.crs(c)) / 6.0, Double::sum);
        }
        for (Map.Entry<String, Double> volume : volumes.entrySet()) {
            assertTrue(volume.getValue() > 1e-8, volume.getKey() + " has volume " + volume.getValue());
        }
    }

    @Test
    void smoothNormalsPointTheSameWayAsTheFacesTheyBelongTo() {
        int against = 0;
        for (int triangle = 0; triangle < MODEL.getTriangleCount(); triangle++) {
            Vector3 a = position(triangle, 0);
            Vector3 face = position(triangle, 1).sub(a).crs(position(triangle, 2).sub(a)).nor();
            for (int corner = 0; corner < 3; corner++) {
                if (normal(triangle, corner).dot(face) <= 0f) against++;
            }
        }
        // Where the glove is pressed flat against the pistol a few normals turn sharply; hardly any may turn round.
        assertTrue(against <= MODEL.getTriangleCount() * 3 / 100,
            "corners whose normal opposes their face: " + against);
    }

    @Test
    void noVertexOfTheGloveIsInsideThePistol() {
        Set<String> seen = new LinkedHashSet<>();
        for (String group : GLOVE_GROUPS) {
            for (Vector3 corner : corners(group)) {
                if (!seen.add(key(corner))) continue;
                for (PistolGeometry.Solid solid : PISTOL.solids) {
                    assertFalse(isInside(corner, solid), group + " has a vertex at " + corner + " inside the pistol's "
                        + solid.name);
                }
            }
        }
    }

    /** Whether a point lies more than a hair inside a convex solid: behind every one of its faces. */
    private static boolean isInside(Vector3 point, PistolGeometry.Solid solid) {
        return outside(point, solid) <= -2e-4f;
    }

    /** @return how near the nearest of some glove vertices comes to the surface of the real grip. */
    private static float nearestToGrip(List<Vector3> corners) {
        float nearest = Float.MAX_VALUE;
        for (Vector3 corner : corners) nearest = Math.min(nearest, outside(corner, GRIP));
        return nearest;
    }

    @Test
    void wrappingFingersLieOnTheRealGripOnBothSidesAndAcrossTheFrontStrap() {
        float gripSide = 0f;
        for (int i = 0; i < GRIP.vertexCount; i++) gripSide = Math.max(gripSide, pistolCorner(GRIP, i).x);

        for (String finger : WRAPPING_FINGERS) {
            List<Vector3> left = new ArrayList<>();
            List<Vector3> right = new ArrayList<>();
            List<Vector3> front = new ArrayList<>();
            for (Vector3 corner : corners(finger)) {
                boolean behindTheStrap = corner.z > frontStrap(corner.y);
                if (behindTheStrap && corner.x < -gripSide) left.add(corner);
                else if (behindTheStrap && corner.x > gripSide) right.add(corner);
                else if (!behindTheStrap && Math.abs(corner.x) < gripSide) front.add(corner);
            }
            assertTrue(nearestToGrip(left) < STUDS + TOUCHING,
                finger + " floats off the left of the grip by " + nearestToGrip(left));
            assertTrue(nearestToGrip(right) < STUDS + TOUCHING,
                finger + " floats off the right of the grip by " + nearestToGrip(right));
            assertTrue(nearestToGrip(front) < TOUCHING,
                finger + " floats off the front strap by " + nearestToGrip(front));
        }
    }

    @Test
    void palmLiesOnTheBackOfTheRealGripAndAlongItsRightSide() {
        float gripSide = 0f;
        for (int i = 0; i < GRIP.vertexCount; i++) gripSide = Math.max(gripSide, pistolCorner(GRIP, i).x);
        // The right side of the grip is judged only by the part of the palm that lies beside it, not behind it.
        float[] heights = gripHeights();
        float topBack = 0f;
        for (int i = 0; i < GRIP.vertexCount; i++) {
            Vector3 corner = pistolCorner(GRIP, i);
            if (corner.y == heights[0]) topBack = Math.max(topBack, corner.z);
        }
        List<Vector3> behind = new ArrayList<>();
        List<Vector3> right = new ArrayList<>();
        for (Vector3 corner : corners("palm")) {
            if (Math.abs(corner.x) < gripSide) behind.add(corner);
            else if (corner.x > gripSide && corner.z < topBack && corner.y < heights[0] && corner.y > heights[1]) {
                right.add(corner);
            }
        }

        assertTrue(nearestToGrip(behind) < TOUCHING, "the palm floats off the back strap by " + nearestToGrip(behind));
        assertTrue(nearestToGrip(right) < STUDS + TOUCHING,
            "the palm floats off the right of the grip by " + nearestToGrip(right));
    }

    @Test
    void threeFingersWrapTheFrontStrapAndEndPressedAgainstTheLeftOfTheGrip() {
        for (String finger : WRAPPING_FINGERS) {
            float rightmost = -Float.MAX_VALUE;
            float foremost = Float.MAX_VALUE;
            for (Vector3 corner : corners(finger)) {
                rightmost = Math.max(rightmost, corner.x);
                foremost = Math.min(foremost, corner.z - frontStrap(corner.y));
            }
            // The generator writes a finger from its knuckle to its tip, so the last triangle is on the fingertip.
            int last = -1;
            for (int triangle = 0; triangle < MODEL.getTriangleCount(); triangle++) {
                if (MODEL.getGroup(triangle).equals(finger)) last = triangle;
            }
            Vector3 tip = position(last, 0).add(position(last, 1)).add(position(last, 2)).scl(1f / 3f);

            assertTrue(rightmost > GRIP_HALF_WIDTH + 0.01f, finger + " starts on the right of the grip");
            assertTrue(foremost < -0.012f, finger + " passes in front of the front strap: " + foremost);
            assertTrue(tip.x < -GRIP_HALF_WIDTH, finger + " ends on the left of the grip, at " + tip);
            assertTrue(tip.z - frontStrap(tip.y) > 0.025f, finger + " lies back along the left side to " + tip);
            assertTrue(tip.x > -GRIP_HALF_WIDTH - 0.02f, finger + " stays against the grip: " + tip);
        }
    }

    @Test
    void fingersTogetherAreAboutAsTallAsTheGripIsLong() {
        float top = -Float.MAX_VALUE;
        float bottom = Float.MAX_VALUE;
        for (String finger : new String[] {"index_finger", "middle_finger", "ring_finger", "little_finger"}) {
            for (Vector3 corner : corners(finger)) {
                // Measured where the fingers leave the back of the hand, on the right of the grip.
                if (corner.x < 0.02f) continue;
                top = Math.max(top, corner.y);
                bottom = Math.min(bottom, corner.y);
            }
        }
        float[] gripHeights = gripHeights();
        float ratio = (top - bottom) / (gripHeights[0] - gripHeights[1]);
        assertTrue(ratio > 0.8f && ratio < 1.15f, "hand width over grip length: " + ratio);
    }

    @Test
    void indexFingerReachesTheTriggerAndTheThumbLiesAlongTheLeftOfTheFrame() {
        boolean onTrigger = false;
        for (Vector3 corner : corners("index_finger")) {
            // The trigger hangs below the frame on the centre line, between z = -0.013 and -0.002.
            if (Math.abs(corner.x) < 0.004f && corner.z < -0.012f && corner.z > -0.035f && corner.y < -0.018f) {
                onTrigger = true;
            }
        }
        assertTrue(onTrigger, "the index finger crosses in front of the trigger");

        float foremost = Float.MAX_VALUE;
        for (Vector3 corner : corners("thumb")) {
            foremost = Math.min(foremost, corner.z);
            if (corner.z < 0.06f) {
                assertTrue(corner.x < -0.012f, "the thumb's front half is left of the frame: " + corner);
            }
        }
        assertTrue(foremost < 0.01f, "the thumb points forward to z = " + foremost);
    }

    @Test
    void gloveRunsFromInsideTheCuffToTheKnucklesAsOneSurface() {
        float foremost = Float.MAX_VALUE;
        float rearmost = -Float.MAX_VALUE;
        for (Vector3 corner : corners("palm")) {
            foremost = Math.min(foremost, corner.z);
            rearmost = Math.max(rearmost, corner.z);
        }
        float cuffFront = Float.MAX_VALUE;
        for (Vector3 corner : corners("cuff")) cuffFront = Math.min(cuffFront, corner.z);

        assertTrue(foremost < 0.065f, "the palm reaches the knuckles: " + foremost);
        assertTrue(rearmost > cuffFront + 0.005f, "the palm ends inside the cuff: " + rearmost + " vs " + cuffFront);
    }

    @Test
    void forearmLeavesTowardsTheLowerRightBehindThePistol() {
        Vector3 wrist = centre("cuff");
        Vector3 sleeve = centre("sleeve");
        Vector3 arm = new Vector3(sleeve).sub(wrist).nor();

        // In gun space the view's lower right, as the shooter sees it, is +x and -y, and towards the shooter is +z.
        assertTrue(arm.z > (float) Math.cos(Math.toRadians(25.0)), "within 25 degrees of the barrel line: " + arm);
        assertTrue(arm.x > 0f, "towards the right: " + arm);
        assertTrue(arm.y < 0f, "and downwards: " + arm);
        assertTrue(centre("palm").z < wrist.z && wrist.z < sleeve.z, "palm, cuff and sleeve follow one another");
    }

    @Test
    void sleeveIsLooserThanTheCuffAndIsNotAPlainCylinder() {
        // The arm's axis as the model has it: from the middle of the cuff through the middle of the sleeve.
        Vector3 wristCentre = centre("cuff");
        Vector3 axis = centre("sleeve").sub(wristCentre).nor();
        float nearest = Float.MAX_VALUE;
        float furthest = 0f;
        for (Vector3 corner : corners("sleeve")) {
            Vector3 offset = new Vector3(corner).sub(wristCentre);
            float along = offset.dot(axis);
            // A slice of the sleeve well clear of both of its ends.
            if (along < 0.15f || along > 0.19f) continue;
            float radius = offset.mulAdd(axis, -along).len();
            nearest = Math.min(nearest, radius);
            furthest = Math.max(furthest, radius);
        }
        float cuffRadius = 0f;
        for (Vector3 corner : corners("cuff")) {
            Vector3 offset = new Vector3(corner).sub(wristCentre);
            cuffRadius = Math.max(cuffRadius, offset.mulAdd(axis, -offset.dot(axis)).len());
        }

        assertTrue(furthest > cuffRadius, "the sleeve is wider than the cuff: " + furthest + " vs " + cuffRadius);
        assertTrue(furthest - nearest > 0.012f, "the sleeve's cross-section is uneven: " + nearest + " to " + furthest);
    }
}

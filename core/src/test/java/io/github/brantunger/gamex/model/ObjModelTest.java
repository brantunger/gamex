package io.github.brantunger.gamex.model;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.Test;

import java.util.Arrays;

class ObjModelTest {

    private static final float EPSILON = 1e-6f;

    /** A unit square in the xy plane, facing +z, with one normal. */
    private static final String SQUARE =
        "v 0 0 0\n"
            + "v 1 0 0\n"
            + "v 1 1 0\n"
            + "v 0 1 0\n"
            + "vn 0 0 1\n";

    private static float[] corner(float[] nine, int triangle, int corner) {
        int at = (triangle * 3 + corner) * 3;
        return Arrays.copyOfRange(nine, at, at + 3);
    }

    private static IllegalArgumentException rejects(String text) {
        return assertThrows(IllegalArgumentException.class, () -> ObjModel.parse(text));
    }

    @Test
    void readsATriangleWithItsPositionsAndNormalsInFaceOrder() {
        ObjModel model = ObjModel.parse(SQUARE + "vn 0 1 0\nf 1//1 2//2 3//1\n");

        assertEquals(4, model.getVertexCount());
        assertEquals(1, model.getTriangleCount());
        assertArrayEquals(new float[] {0f, 0f, 0f, 1f, 0f, 0f, 1f, 1f, 0f}, model.getPositions(), EPSILON);
        assertArrayEquals(new float[] {0f, 0f, 1f, 0f, 1f, 0f, 0f, 0f, 1f}, model.getNormals(), EPSILON);
    }

    @Test
    void splitsAQuadIntoAFanOfTwoTrianglesThatKeepItsWinding() {
        ObjModel model = ObjModel.parse(SQUARE + "f 1//1 2//1 3//1 4//1\n");

        assertEquals(2, model.getTriangleCount());
        float[] positions = model.getPositions();
        assertArrayEquals(new float[] {0f, 0f, 0f}, corner(positions, 0, 0), EPSILON);
        assertArrayEquals(new float[] {1f, 0f, 0f}, corner(positions, 0, 1), EPSILON);
        assertArrayEquals(new float[] {1f, 1f, 0f}, corner(positions, 0, 2), EPSILON);
        assertArrayEquals(new float[] {0f, 0f, 0f}, corner(positions, 1, 0), EPSILON);
        assertArrayEquals(new float[] {1f, 1f, 0f}, corner(positions, 1, 1), EPSILON);
        assertArrayEquals(new float[] {0f, 1f, 0f}, corner(positions, 1, 2), EPSILON);
    }

    @Test
    void splitsAFaceOfFiveCornersIntoThreeTriangles() {
        ObjModel model = ObjModel.parse(SQUARE + "v 0.5 1.5 0\nf 1 2 3 5 4\n");

        assertEquals(3, model.getTriangleCount());
        assertArrayEquals(new float[] {0.5f, 1.5f, 0f}, corner(model.getPositions(), 1, 2), EPSILON);
        assertArrayEquals(new float[] {0f, 1f, 0f}, corner(model.getPositions(), 2, 2), EPSILON);
    }

    @Test
    void understandsEveryWayOfWritingAFaceCorner() {
        String texture = "vt 0 0\nvt 1 0\nvt 1 1\n";

        for (String face : new String[] {"f 1 2 3", "f 1/1 2/2 3/3", "f 1//1 2//1 3//1", "f 1/1/1 2/2/1 3/3/1"}) {
            ObjModel model = ObjModel.parse(SQUARE + texture + face + "\n");

            assertEquals(1, model.getTriangleCount(), face);
            assertArrayEquals(new float[] {1f, 1f, 0f}, corner(model.getPositions(), 0, 2), EPSILON, face);
            assertArrayEquals(new float[] {0f, 0f, 1f}, corner(model.getNormals(), 0, 2), EPSILON, face);
        }
    }

    @Test
    void countsNegativeIndicesBackFromTheLatestVertex() {
        ObjModel model = ObjModel.parse(SQUARE + "f -4//-1 -3//-1 -1//-1\n");

        assertArrayEquals(new float[] {0f, 0f, 0f}, corner(model.getPositions(), 0, 0), EPSILON);
        assertArrayEquals(new float[] {1f, 0f, 0f}, corner(model.getPositions(), 0, 1), EPSILON);
        assertArrayEquals(new float[] {0f, 1f, 0f}, corner(model.getPositions(), 0, 2), EPSILON);
    }

    @Test
    void faceWithoutNormalsGetsTheFlatNormalOfItsWinding() {
        ObjModel counterClockwise = ObjModel.parse(SQUARE + "f 1 2 3\n");
        ObjModel clockwise = ObjModel.parse(SQUARE + "f 3 2 1\n");

        for (int corner = 0; corner < 3; corner++) {
            assertArrayEquals(new float[] {0f, 0f, 1f}, corner(counterClockwise.getNormals(), 0, corner), EPSILON);
            assertArrayEquals(new float[] {0f, 0f, -1f}, corner(clockwise.getNormals(), 0, corner), EPSILON);
        }
    }

    @Test
    void makesNormalsUnitLength() {
        ObjModel model = ObjModel.parse("v 0 0 0\nv 1 0 0\nv 0 1 0\nvn 0 3 4\nf 1//1 2//1 3//1\n");

        assertArrayEquals(new float[] {0f, 0.6f, 0.8f}, corner(model.getNormals(), 0, 0), EPSILON);
    }

    @Test
    void givesEveryTriangleTheGroupAndMaterialInForceWhenItsFaceWasRead() {
        ObjModel model = ObjModel.parse(SQUARE
            + "f 1 2 3\n"
            + "g palm\n"
            + "usemtl glove\n"
            + "f 1 2 3 4\n"
            + "o left sleeve\n"
            + "usemtl sleeve\n"
            + "f 1 3 4\n");

        assertEquals(4, model.getTriangleCount());
        assertEquals(ObjModel.NO_GROUP, model.getGroup(0));
        assertEquals(ObjModel.NO_MATERIAL, model.getMaterial(0));
        assertEquals("palm", model.getGroup(1));
        assertEquals("glove", model.getMaterial(1));
        assertEquals("palm", model.getGroup(2));
        assertEquals("glove", model.getMaterial(2));
        assertEquals("left sleeve", model.getGroup(3));
        assertEquals("sleeve", model.getMaterial(3));
    }

    @Test
    void readsPastCommentsBlankLinesWindowsLineEndsAndStatementsItDoesNotNeed() {
        ObjModel model = ObjModel.parse("# a comment\r\n"
            + "mtllib hand.mtl\r\n"
            + "\r\n"
            + "v 0 0 0 # the origin\r\n"
            + "v 1 0 0\r\n"
            + "  v   0   1   0  \r\n"
            + "vt 0.5 0.5\r\n"
            + "s 1\r\n"
            + "f 1 2 3\r\n");

        assertEquals(3, model.getVertexCount());
        assertEquals(1, model.getTriangleCount());
    }

    @Test
    void emptyTextIsAnEmptyModel() {
        ObjModel model = ObjModel.parse("");

        assertEquals(0, model.getVertexCount());
        assertEquals(0, model.getTriangleCount());
        assertEquals(0, model.getPositions().length);
    }

    @Test
    void rejectsStatementsItDoesNotSupportAndNamesTheLine() {
        IllegalArgumentException error = rejects(SQUARE + "l 1 2\n");

        assertTrue(error.getMessage().contains("Line 6"), error.getMessage());
        assertTrue(error.getMessage().contains("'l'"), error.getMessage());
        rejects("curv 0 1 1 2\n");
        rejects("vp 0.5 0.5\n");
    }

    @Test
    void rejectsMalformedNumbers() {
        assertTrue(rejects("v 0 zero 0\n").getMessage().contains("Line 1"));
        rejects("v 0 0\n");
        rejects("v 0 0 NaN\n");
        rejects("vn 0 0 0\n");
        rejects(SQUARE + "f 1 two 3\n");
        rejects(SQUARE + "f 1/1/1/1 2 3\n");
    }

    @Test
    void rejectsFacesThatReferToVerticesOrNormalsThatDoNotExist() {
        assertTrue(rejects(SQUARE + "f 1 2 5\n").getMessage().contains("no vertex 5"));
        rejects(SQUARE + "f 0 1 2\n");
        rejects(SQUARE + "f 1 2 -5\n");
        assertTrue(rejects(SQUARE + "f 1//1 2//2 3//1\n").getMessage().contains("no normal 2"));
        // A face may only use what the file has defined above it.
        rejects("v 0 0 0\nv 1 0 0\nf 1 2 3\nv 0 1 0\n");
    }

    @Test
    void rejectsFacesWithFewerThanThreeCorners() {
        rejects(SQUARE + "f 1 2\n");
        rejects(SQUARE + "f\n");
    }
}

package io.github.brantunger.gamex.model;

import static org.junit.jupiter.api.Assertions.assertAll;
import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
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

    /** Three vertices in a line along the x axis: a triangle of them has no area. */
    private static final String IN_A_LINE =
        "v 0 0 0\n"
            + "v 1 0 0\n"
            + "v 2 0 0\n";

    private static float[] corner(float[] nine, int triangle, int corner) {
        int at = (triangle * 3 + corner) * 3;
        return Arrays.copyOfRange(nine, at, at + 3);
    }

    private static IllegalArgumentException rejects(String text) {
        return assertThrows(IllegalArgumentException.class, () -> ObjModel.parse(text));
    }

    private static void rejectsSaying(String text, String line, String wording) {
        String message = rejects(text).getMessage();
        assertTrue(message.contains(line), message);
        assertTrue(message.contains(wording), message);
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
    void faceWhoseFirstCornersAreInALineGetsTheFlatNormalOfItsFirstTriangleWithAnArea() {
        ObjModel model = ObjModel.parse(IN_A_LINE + "v 1 1 0\nf 1 2 3 4\n");

        // The triangle without an area is still there, so the triangles keep matching the corners of the face.
        assertEquals(2, model.getTriangleCount());
        assertEquals(18, model.getPositions().length);
        assertArrayEquals(new float[] {2f, 0f, 0f}, corner(model.getPositions(), 0, 2), EPSILON);
        assertArrayEquals(new float[] {1f, 1f, 0f}, corner(model.getPositions(), 1, 2), EPSILON);
        for (int triangle = 0; triangle < 2; triangle++) {
            for (int corner = 0; corner < 3; corner++) {
                assertArrayEquals(new float[] {0f, 0f, 1f}, corner(model.getNormals(), triangle, corner), EPSILON);
            }
        }
    }

    @Test
    void nonPlanarFaceWithoutNormalsGetsTheWholeFaceNormalIndependentOfStartCorner() {
        String vertices = IN_A_LINE + "v 1 1 0\nv 0 0 1\n";
        float s = (float) Math.sqrt(6.0);
        float[] expected = {1f / s, -1f / s, 2f / s};

        for (String face : new String[] {"f 1 2 3 4 5\n", "f 4 5 1 2 3\n"}) {
            ObjModel model = ObjModel.parse(vertices + face);

            assertEquals(3, model.getTriangleCount());
            for (int triangle = 0; triangle < 3; triangle++) {
                for (int corner = 0; corner < 3; corner++) {
                    assertArrayEquals(expected, corner(model.getNormals(), triangle, corner), EPSILON);
                }
            }
        }
    }

    @Test
    void concaveFaceWithoutNormalsGetsTheWholeFaceFlatNormalRegardlessOfStartCorner() {
        String dart = "v -2 -2 0\nv 0 -1 0\nv 2 -2 0\nv 0 2 0\n";
        for (String face : new String[] {"f 1 2 3 4\n", "f 2 3 4 1\n", "f 3 4 1 2\n", "f 4 1 2 3\n"}) {
            ObjModel model = ObjModel.parse(dart + face);

            assertEquals(2, model.getTriangleCount(), face);
            for (int triangle = 0; triangle < 2; triangle++) {
                for (int corner = 0; corner < 3; corner++) {
                    assertArrayEquals(new float[] {0f, 0f, 1f}, corner(model.getNormals(), triangle, corner), EPSILON);
                }
            }
        }

        // First three corners collinear, next corner inward (#20 case 2).
        String six = "v 0 0 0\nv 1 0 0\nv 2 0 0\nv 3 -1 0\nv 3 3 0\nv 0 3 0\nf 1 2 3 4 5 6\n";
        ObjModel sixModel = ObjModel.parse(six);
        assertEquals(4, sixModel.getTriangleCount());
        for (int triangle = 0; triangle < 4; triangle++) {
            for (int corner = 0; corner < 3; corner++) {
                assertArrayEquals(new float[] {0f, 0f, 1f}, corner(sixModel.getNormals(), triangle, corner), EPSILON);
            }
        }
    }

    @Test
    void concaveFaceEarClipsInsideItsOutlineForAnyStartCorner() {
        String dart = "v -2 -2 0\nv 0 -1 0\nv 2 -2 0\nv 0 2 0\n";
        for (String prefix : new String[] {"", "vn 0 0 1\n"}) {
            String suffix = prefix.isEmpty() ? "" : "//1";
            String[] faces = {
                "f 1" + suffix + " 2" + suffix + " 3" + suffix + " 4" + suffix + "\n",
                "f 2" + suffix + " 3" + suffix + " 4" + suffix + " 1" + suffix + "\n",
                "f 3" + suffix + " 4" + suffix + " 1" + suffix + " 2" + suffix + "\n",
                "f 4" + suffix + " 1" + suffix + " 2" + suffix + " 3" + suffix + "\n",
            };
            for (String face : faces) {
                ObjModel model = ObjModel.parse(dart + prefix + face);
                assertEquals(2, model.getTriangleCount(), face);
                float[] positions = model.getPositions();
                float totalTwiceArea = 0f;
                for (int t = 0; t < 2; t++) {
                    float[] a = corner(positions, t, 0);
                    float[] b = corner(positions, t, 1);
                    float[] c = corner(positions, t, 2);
                    float twiceSignedAreaZ = (b[0] - a[0]) * (c[1] - a[1]) - (b[1] - a[1]) * (c[0] - a[0]);
                    assertTrue(twiceSignedAreaZ > 0f, face + " triangle " + t + " wound wrong: " + twiceSignedAreaZ);
                    totalTwiceArea += twiceSignedAreaZ;
                }
                assertEquals(12f, totalTwiceArea, EPSILON, face);
            }
        }
    }

    @Test
    void rejectsSelfCrossingFacesAndKeepsCollinearZeroAreaFacesWithNormals() {
        String bowtie = "v 0 0 0\nv 1 1 0\nv 1 0 0\nv 0 1 0\n";
        assertAll(
            () -> rejectsSaying(bowtie + "f 1 2 3 4\n", "Line 5", "self-crossing"),
            () -> rejectsSaying(bowtie + "vn 0 0 1\nf 1//1 2//1 3//1 4//1\n", "Line 6", "self-crossing"),
            () -> rejectsSaying("v 0 0 0\nv 2 2 0\nv 2 0 0\nv 0 1 0\nf 1 2 3 4\n", "Line 5", "self-crossing"));

        ObjModel collinearWithNormals = ObjModel.parse(IN_A_LINE + "v 3 0 0\nvn 0 1 0\nf 1//1 2//1 3//1 4//1\n");
        assertEquals(2, collinearWithNormals.getTriangleCount());
        assertArrayEquals(new float[] {0f, 1f, 0f}, corner(collinearWithNormals.getNormals(), 0, 0), EPSILON);
    }

    @Test
    void faceWithNoAreaIsReadWhenEveryCornerHasANormal() {
        ObjModel model = ObjModel.parse(IN_A_LINE + "vn 0 1 0\nf 1//1 2//1 3//1\n");

        assertEquals(1, model.getTriangleCount());
        for (int corner = 0; corner < 3; corner++) {
            assertArrayEquals(new float[] {0f, 1f, 0f}, corner(model.getNormals(), 0, corner), EPSILON);
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
    void readsAFileWhoseLinesEndInALoneCarriageReturn() {
        ObjModel model = ObjModel.parse("v 0 0 0\rv 1 0 0\rv 0 1 0\rusemtl glove\rf 1 2 3\r");

        assertEquals(3, model.getVertexCount());
        assertEquals(1, model.getTriangleCount());
        assertEquals("glove", model.getMaterial(0));
        // Errors still name the right line.
        assertTrue(rejects("v 0 0 0\rv 1 0 0\rl 1 2\r").getMessage().contains("Line 3"));
    }

    @Test
    void readsPastAByteOrderMarkAtTheStartOfTheFile() {
        char byteOrderMark = 0xFEFF;
        ObjModel model = ObjModel.parse(byteOrderMark + "v 0 0 0\nv 1 0 0\nv 0 1 0\nf 1 2 3\n");

        assertEquals(3, model.getVertexCount());
        assertEquals(1, model.getTriangleCount());
        assertArrayEquals(new float[] {0f, 0f, 0f}, corner(model.getPositions(), 0, 0), EPSILON);
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
        rejectsSaying("v 0 0 NaN\n", "Line 1", "'NaN' is not a number");
        rejects("vn 0 0 0\n");
        rejects(SQUARE + "f 1 two 3\n");
        rejects(SQUARE + "f 1/1/1/1 2 3\n");
    }

    @Test
    void rejectsNumbersThatAreNotPlainDecimals() {
        assertAll(
            () -> rejectsSaying("v 1f 0 0\n", "Line 1", "'1f' is not a number"),
            () -> rejectsSaying("v 0 2d 0\n", "Line 1", "'2d' is not a number"),
            () -> rejectsSaying("v 0 0 0x1p1\n", "Line 1", "'0x1p1' is not a number"),
            () -> rejectsSaying("v 0 0 0\nvn 1f 0 0\n", "Line 2", "'1f' is not a number"),
            () -> rejectsSaying("v Infinity 0 0\n", "Line 1", "'Infinity' is not a number"),
            () -> rejectsSaying("v 0 -Infinity 0\n", "Line 1", "'-Infinity' is not a number"),
            () -> rejectsSaying("v 0 0 0\nvn 0 NaN 1\n", "Line 2", "'NaN' is not a number"));
    }

    @Test
    void rejectsFaceIndicesWrittenInDigitsThatAreNotAscii() {
        assertAll(
            () -> rejectsSaying(SQUARE + "f \u0661 2 3\n", "Line 6", "is not a vertex index"),
            () -> rejectsSaying(SQUARE + "f 1//\u0661 2//1 3//1\n", "Line 6", "is not a normal index"));
    }

    @Test
    void readsNumbersWithAPlusSignAnExponentOrNoDigitsOnOneSideOfThePoint() {
        ObjModel model = ObjModel.parse("v +1 .5 5.\nv 1e2 -2.5E-1 +.5e+1\nv 0 1 0\nvn 0 0 +1\nf 1//1 2//1 3//1\n");

        assertArrayEquals(new float[] {1f, 0.5f, 5f}, corner(model.getPositions(), 0, 0), EPSILON);
        assertArrayEquals(new float[] {100f, -0.25f, 5f}, corner(model.getPositions(), 0, 1), EPSILON);
        assertArrayEquals(new float[] {0f, 0f, 1f}, corner(model.getNormals(), 0, 0), EPSILON);
    }

    @Test
    void readsFaceIndicesWithAPlusSign() {
        ObjModel model = ObjModel.parse(SQUARE + "f +1 2 3\nf +1//+1 +2//1 +3//1\n");

        assertEquals(2, model.getTriangleCount());
        assertArrayEquals(new float[] {0f, 0f, 0f}, corner(model.getPositions(), 0, 0), EPSILON);
        assertArrayEquals(new float[] {1f, 1f, 0f}, corner(model.getPositions(), 1, 2), EPSILON);
    }

    @Test
    void rejectsNumbersThatAreTooLargeOrAreNotNumbersAtAll() {
        assertAll(
            () -> rejectsSaying("v 1e40 0 0\n", "Line 1", "'1e40' is not finite"),
            () -> rejects("v \u0661 0 0\n"),
            () -> rejects("v 1e 0 0\n"),
            () -> rejects("v . 0 0\n"));
    }

    @Test
    void rejectsANormalOfZeroLengthAndSaysSo() {
        rejectsSaying("v 0 0 0\nvn 0 0 0\n", "Line 2", "a normal of zero length");
    }

    @Test
    void rejectsAFaceIndexTooLargeToCountWithAndNamesTheLine() {
        rejectsSaying(SQUARE + "f 99999999999 2 3\n", "Line 6", "'99999999999' is not a vertex index");
    }

    @Test
    void rejectsAFaceWithNormalsOnOnlySomeOfItsCorners() {
        assertAll(
            () -> rejectsSaying(SQUARE + "f 1//1 2//1 3\n", "Line 6", "normals on only 2 of its 3 corners"),
            () -> rejectsSaying(SQUARE + "f 1//1 2//1 3//\n", "Line 6", "normals on only 2 of its 3 corners"),
            () -> rejectsSaying(SQUARE + "f 1 2//1 3//1 4//1\n", "Line 6", "normals on only 3 of its 4 corners"),
            () -> rejectsSaying(SQUARE + "vt 0 0\nf 1/1/1 2/1 3\n", "Line 7", "normals on only 1 of its 3 corners"));
    }

    @Test
    void namesANormalThatDoesNotExistBeforeTheCornersThatLackOne() {
        rejectsSaying(SQUARE + "f 1//1 2//2 3\n", "Line 6", "no normal 2");
    }

    @Test
    void rejectsAFaceWithoutNormalsThatHasNoAreaAndSaysSo() {
        IllegalArgumentException error = rejects(IN_A_LINE + "f 1 2 3\n");

        assertTrue(error.getMessage().contains("Line 4"), error.getMessage());
        assertTrue(error.getMessage().contains("a face with no area"), error.getMessage());
        assertFalse(error.getMessage().contains("normal of zero length"), error.getMessage());
        // Nor is there a triangle with an area anywhere in the fan of a longer face.
        rejectsSaying(IN_A_LINE + "v 3 0 0\nf 1 2 3 4\n", "Line 5", "a face with no area");
        rejectsSaying(IN_A_LINE + "f 1 1 1\n", "Line 4", "a face with no area");
    }

    @Test
    void rejectsAFaceWithoutNormalsWhoseAreaIsTooSmallToGiveItANormal() {
        // The same length decides that a normal on a vn line is of zero length.
        IllegalArgumentException error = rejects("v 0 0 0\nv 1e-7 0 0\nv 0 1e-7 0\nf 1 2 3\n");

        assertTrue(error.getMessage().contains("Line 4"), error.getMessage());
        assertTrue(error.getMessage().contains("a face with no area"), error.getMessage());
    }

    @Test
    void readsAFaceWithoutNormalsThatIsSmallButHasAnArea() {
        ObjModel model = ObjModel.parse("v 0 0 0\nv 1e-5 0 0\nv 0 1e-5 0\nf 1 2 3\n");

        assertEquals(1, model.getTriangleCount());
        for (int corner = 0; corner < 3; corner++) {
            assertArrayEquals(new float[] {0f, 0f, 1f}, corner(model.getNormals(), 0, corner), EPSILON);
        }
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

    @Test
    void checksTextureCoordinateIndicesAndRejectsTrailingSlashes() {
        assertAll(
            () -> rejectsSaying(SQUARE + "f 1/abc/1 2/abc/1 3/abc/1\n", "Line 6", "not a texture coordinate index"),
            () -> rejectsSaying(SQUARE + "f 1/abc 2/abc 3/abc\n", "Line 6", "not a texture coordinate index"),
            () -> rejectsSaying(SQUARE + "f 1/99 2/99 3/99\n", "Line 6", "no texture coordinate 99 (0 so far)"),
            () -> rejectsSaying(SQUARE + "f 1/0/1 2/0/1 3/0/1\n", "Line 6", "no texture coordinate 0"),
            () -> rejectsSaying(SQUARE + "f 1/ 2/ 3/\n", "Line 6", "is not a face corner"),
            () -> rejectsSaying(SQUARE + "f 1// 2// 3//\n", "Line 6", "is not a face corner"));

        ObjModel negativeAndUnreadVt = ObjModel.parse(SQUARE + "vt abc\nvt 0 0\nvt 1 1\nf 1/-3 2/-2 3/-1\n");
        assertEquals(1, negativeAndUnreadVt.getTriangleCount());
    }

    @Test
    void largeCoordinatesKeepTrueUnitNormalsWithoutOverflowOrFalseNoAreaError() {
        ObjModel longVn = ObjModel.parse("v 0 0 0\nv 1 0 0\nv 0 1 0\nvn 1e20 0 0\nf 1//1 2//1 3//1\n");
        assertArrayEquals(new float[] {1f, 0f, 0f}, corner(longVn.getNormals(), 0, 0), EPSILON);

        for (String scale : new String[] {"1e10", "1e20"}) {
            ObjModel largeFlat = ObjModel.parse("v 0 0 0\nv " + scale + " 0 0\nv 0 " + scale + " 0\nf 1 2 3\n");
            assertArrayEquals(new float[] {0f, 0f, 1f}, corner(largeFlat.getNormals(), 0, 0), EPSILON);
        }

        ObjModel extremeFlat = ObjModel.parse("v -3e38 0 0\nv 3e38 0 0\nv 0 3e38 0\nf 1 2 3\n");
        for (int c = 0; c < 3; c++) {
            assertArrayEquals(new float[] {0f, 0f, 1f}, corner(extremeFlat.getNormals(), 0, c), EPSILON);
        }
    }
}

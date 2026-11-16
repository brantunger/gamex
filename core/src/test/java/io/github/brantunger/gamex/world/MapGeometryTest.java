package io.github.brantunger.gamex.world;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.badlogic.gdx.math.Vector3;
import org.junit.jupiter.api.Test;

import java.util.Map;

class MapGeometryTest {

    private static final float EPSILON = 1e-5f;

    private static int quads(float[] vertices) {
        assertEquals(0, vertices.length % MapGeometry.QUAD_SIZE);
        return vertices.length / MapGeometry.QUAD_SIZE;
    }

    private static Vector3 corner(float[] vertices, int quad, int corner) {
        int at = quad * MapGeometry.QUAD_SIZE + corner * MapGeometry.VERTEX_SIZE;
        return new Vector3(vertices[at], vertices[at + 1], vertices[at + 2]);
    }

    /** The normal that counter-clockwise winding gives a quad, checked against both of its triangles. */
    private static Vector3 normal(float[] vertices, int quad) {
        Vector3 a = corner(vertices, quad, 0);
        Vector3 first = corner(vertices, quad, 1).sub(a).crs(corner(vertices, quad, 2).sub(a)).nor();
        Vector3 second = corner(vertices, quad, 2).sub(a).crs(corner(vertices, quad, 3).sub(a)).nor();
        assertEquals(1f, first.dot(second), EPSILON, "both triangles of a quad face the same way");
        return first;
    }

    private static Vector3 centre(float[] vertices, int quad) {
        Vector3 sum = new Vector3();
        for (int i = 0; i < 4; i++) sum.add(corner(vertices, quad, i));
        return sum.scl(0.25f);
    }

    @Test
    void closedCellGetsFourWallsAFloorAndACeiling() {
        MapGeometry geometry = MapGeometry.build(GameMap.parse(
            "###",
            "#>#",
            "###"));

        assertEquals(1, geometry.walls.size());
        assertEquals(4, quads(geometry.walls.get('#')));
        assertEquals(1, quads(geometry.floor));
        assertEquals(1, quads(geometry.ceiling));
    }

    @Test
    void noFaceIsEmittedBetweenTwoOpenCellsOrInsideSolidRock() {
        MapGeometry geometry = MapGeometry.build(GameMap.parse(
            "#####",
            "#>.##",
            "#####"));

        // Two open cells side by side: one face at each end and two along each side.
        assertEquals(6, quads(geometry.walls.get('#')));
        assertEquals(2, quads(geometry.floor));
    }

    @Test
    void wallFacesAreBatchedByTheWallTypeTheyBelongTo() {
        MapGeometry geometry = MapGeometry.build(GameMap.parse(
            "#%#",
            "&>&",
            "#%#"));

        assertEquals(2, quads(geometry.walls.get('%')));
        assertEquals(2, quads(geometry.walls.get('&')));
        assertFalse(geometry.walls.containsKey('#'), "the corner cells border no open floor");
    }

    @Test
    void everyWallFaceOfTheFirstLevelFacesTheOpenCellItBorders() {
        GameMap map = Levels.first();
        MapGeometry geometry = MapGeometry.build(map);

        for (Map.Entry<Character, float[]> entry : geometry.walls.entrySet()) {
            float[] vertices = entry.getValue();
            for (int quad = 0; quad < quads(vertices); quad++) {
                Vector3 normal = normal(vertices, quad);
                assertEquals(0f, normal.y, EPSILON, "walls are upright");
                // Half a cell along the normal lies the open cell; half a cell against it lies the wall itself.
                Vector3 front = centre(vertices, quad).mulAdd(normal, 0.5f);
                Vector3 back = centre(vertices, quad).mulAdd(normal, -0.5f);
                assertFalse(map.isSolid((int) Math.floor(front.x), (int) Math.floor(front.z)),
                    "front of quad " + quad + " of '" + entry.getKey() + "'");
                assertEquals(entry.getKey().charValue(),
                    map.wallAt((int) Math.floor(back.x), (int) Math.floor(back.z)), "the wall behind quad " + quad);
            }
        }
    }

    @Test
    void floorsFaceUpAndCeilingsFaceDown() {
        MapGeometry geometry = MapGeometry.build(Levels.first());

        for (int quad = 0; quad < quads(geometry.floor); quad++) {
            assertEquals(1f, normal(geometry.floor, quad).y, EPSILON);
            assertEquals(0f, centre(geometry.floor, quad).y, EPSILON);
        }
        for (int quad = 0; quad < quads(geometry.ceiling); quad++) {
            assertEquals(-1f, normal(geometry.ceiling, quad).y, EPSILON);
            assertEquals(GameMap.WALL_HEIGHT, centre(geometry.ceiling, quad).y, EPSILON);
        }
    }

    @Test
    void wallTextureIsUprightWithItsTopRowAtTheTopOfTheWall() {
        float[] vertices = MapGeometry.build(GameMap.parse(
            "###",
            "#>#",
            "###")).walls.get('#');

        for (int quad = 0; quad < quads(vertices); quad++) {
            for (int corner = 0; corner < 4; corner++) {
                int at = quad * MapGeometry.QUAD_SIZE + corner * MapGeometry.VERTEX_SIZE;
                float y = vertices[at + 1];
                float v = vertices[at + 4];
                // Image row 0 (v = 0) is the top of the texture.
                assertEquals(y > 0f ? 0f : 1f, v, EPSILON);
                assertTrue(vertices[at + 5] > 0f && vertices[at + 5] <= 1f, "shade");
            }
        }
    }
}

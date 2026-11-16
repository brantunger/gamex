package io.github.brantunger.gamex.world;

import java.util.Arrays;
import java.util.LinkedHashMap;
import java.util.Map;

/**
 * The quads that make up a map's walls, floor and ceiling, as plain float arrays with no graphics types, so the
 * winding can be checked without a GL context.
 *
 * <p>Every quad is four vertices of {@link #VERTEX_SIZE} floats: position x, y, z, texture u, v, and a shade factor.
 * The vertices run counter-clockwise when seen from the side the quad faces, so back-face culling keeps the side the
 * player sees. Only wall faces that border open floor are emitted.
 */
public final class MapGeometry {

    /** The number of floats per vertex. */
    public static final int VERTEX_SIZE = 6;
    /** The number of floats per quad. */
    public static final int QUAD_SIZE = 4 * VERTEX_SIZE;

    private static final float SHADE_NORTH_SOUTH = 1f;
    private static final float SHADE_EAST_WEST = 0.72f;
    private static final float SHADE_FLOOR = 0.9f;
    private static final float SHADE_CEILING = 0.62f;

    /** The wall quads, grouped by wall type. */
    public final Map<Character, float[]> walls;
    /** The floor quads. */
    public final float[] floor;
    /** The ceiling quads. */
    public final float[] ceiling;

    private MapGeometry(Map<Character, float[]> walls, float[] floor, float[] ceiling) {
        this.walls = walls;
        this.floor = floor;
        this.ceiling = ceiling;
    }

    /**
     * Builds the geometry of a map.
     * @param map the map
     * @return the quads, batched per surface
     */
    public static MapGeometry build(GameMap map) {
        Map<Character, FloatList> walls = new LinkedHashMap<>();
        FloatList floor = new FloatList();
        FloatList ceiling = new FloatList();
        float h = GameMap.WALL_HEIGHT;
        for (int row = 0; row < map.getHeight(); row++) {
            for (int col = 0; col < map.getWidth(); col++) {
                if (map.isSolid(col, row)) continue;
                float x0 = col;
                float x1 = col + 1f;
                float z0 = row;
                float z1 = row + 1f;
                floor.quad(SHADE_FLOOR, x0, 0f, z0, x0, 0f, z1, x1, 0f, z1, x1, 0f, z0);
                ceiling.quad(SHADE_CEILING, x0, h, z0, x1, h, z0, x1, h, z1, x0, h, z1);
                // Each wall face starts at its bottom-left corner as seen from inside this cell.
                if (map.isSolid(col, row - 1)) {
                    list(walls, map.wallAt(col, row - 1))
                        .quad(SHADE_NORTH_SOUTH, x0, 0f, z0, x1, 0f, z0, x1, h, z0, x0, h, z0);
                }
                if (map.isSolid(col, row + 1)) {
                    list(walls, map.wallAt(col, row + 1))
                        .quad(SHADE_NORTH_SOUTH, x1, 0f, z1, x0, 0f, z1, x0, h, z1, x1, h, z1);
                }
                if (map.isSolid(col - 1, row)) {
                    list(walls, map.wallAt(col - 1, row))
                        .quad(SHADE_EAST_WEST, x0, 0f, z1, x0, 0f, z0, x0, h, z0, x0, h, z1);
                }
                if (map.isSolid(col + 1, row)) {
                    list(walls, map.wallAt(col + 1, row))
                        .quad(SHADE_EAST_WEST, x1, 0f, z0, x1, 0f, z1, x1, h, z1, x1, h, z0);
                }
            }
        }
        Map<Character, float[]> wallArrays = new LinkedHashMap<>();
        for (Map.Entry<Character, FloatList> entry : walls.entrySet()) {
            wallArrays.put(entry.getKey(), entry.getValue().toArray());
        }
        return new MapGeometry(wallArrays, floor.toArray(), ceiling.toArray());
    }

    private static FloatList list(Map<Character, FloatList> walls, char type) {
        return walls.computeIfAbsent(type, key -> new FloatList());
    }

    /** A growing float array that knows how to append a textured quad. */
    private static final class FloatList {
        private static final float[] U = {0f, 1f, 1f, 0f};
        private static final float[] V = {1f, 1f, 0f, 0f};

        private float[] items = new float[QUAD_SIZE * 64];
        private int size;

        /** Appends a quad from four corners given as x, y, z triples, mapping the whole texture onto it. */
        void quad(float shade, float... corners) {
            if (size + QUAD_SIZE > items.length) items = Arrays.copyOf(items, items.length * 2);
            for (int i = 0; i < 4; i++) {
                items[size++] = corners[i * 3];
                items[size++] = corners[i * 3 + 1];
                items[size++] = corners[i * 3 + 2];
                items[size++] = U[i];
                items[size++] = V[i];
                items[size++] = shade;
            }
        }

        float[] toArray() {
            return Arrays.copyOf(items, size);
        }
    }
}

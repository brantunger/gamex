package io.github.brantunger.gamex.world;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

/**
 * The level grid. Each cell is one world unit square on the XZ plane: column {@code c} spans x from {@code c} to
 * {@code c + 1} and row {@code r} spans z from {@code r} to {@code r + 1}. Row 0 is the first string of the layout,
 * so the layout reads like a top-down view with north (negative z) at the top.
 *
 * <p>Layout characters: {@code .} is open floor, {@code #}, {@code %} and {@code &} are the three wall types,
 * {@code o} and {@code O} are open floor with a canister or a ring board on it, {@code +}, {@code a} and {@code e}
 * are medkit, armor and energy cell pickups, {@code X} is an optional level exit pad, and one of {@code ^ > v <} is
 * the player spawn, facing north, east, south or west.
 */
public final class GameMap {

    /** The height of every wall, and so the height of the ceiling above the floor. */
    public static final float WALL_HEIGHT = 1f;
    /** Trigger radius of an exit pad on the XZ plane. */
    public static final float EXIT_RADIUS = 0.35f;
    /** The cell value of open floor. */
    public static final char FLOOR = '.';
    /** The wall types a layout may use, in a fixed order. */
    public static final String WALL_TYPES = "#%&";

    /** Where a target starts. */
    public static final class TargetSpawn {
        /** The kind of target to create. */
        public final Target.Kind kind;
        /** The world x coordinate, at the centre of the cell. */
        public final float x;
        /** The world z coordinate, at the centre of the cell. */
        public final float z;

        TargetSpawn(Target.Kind kind, float x, float z) {
            this.kind = kind;
            this.x = x;
            this.z = z;
        }
    }

    /** Where a pickup item starts. */
    public static final class PickupSpawn {
        /** The kind of pickup to create. */
        public final Pickup.Kind kind;
        /** The world x coordinate, at the centre of the cell. */
        public final float x;
        /** The world z coordinate, at the centre of the cell. */
        public final float z;

        PickupSpawn(Pickup.Kind kind, float x, float z) {
            this.kind = kind;
            this.x = x;
            this.z = z;
        }
    }

    private final char[][] cells;
    private final int width;
    private final int height;
    private final float spawnX;
    private final float spawnZ;
    private final float spawnYaw;
    private final boolean hasExit;
    private final float exitX;
    private final float exitZ;
    private final List<TargetSpawn> targetSpawns;
    private final List<PickupSpawn> pickupSpawns;

    private GameMap(char[][] cells, float spawnX, float spawnZ, float spawnYaw, boolean hasExit, float exitX,
                    float exitZ, List<TargetSpawn> targetSpawns, List<PickupSpawn> pickupSpawns) {
        this.cells = cells;
        this.height = cells.length;
        this.width = cells[0].length;
        this.spawnX = spawnX;
        this.spawnZ = spawnZ;
        this.spawnYaw = spawnYaw;
        this.hasExit = hasExit;
        this.exitX = exitX;
        this.exitZ = exitZ;
        this.targetSpawns = Collections.unmodifiableList(targetSpawns);
        this.pickupSpawns = Collections.unmodifiableList(pickupSpawns);
    }

    /**
     * Parses a layout.
     * @param rows the rows from north to south, all of the same length
     * @return the parsed map
     * @throws IllegalArgumentException if the layout is empty or ragged, holds an unknown character, holds more than
     *     one exit, or does not hold exactly one player spawn
     */
    public static GameMap parse(String... rows) {
        if (rows == null || rows.length == 0 || rows[0].isEmpty()) {
            throw new IllegalArgumentException("The layout is empty");
        }
        int width = rows[0].length();
        char[][] cells = new char[rows.length][width];
        List<TargetSpawn> targets = new ArrayList<>();
        List<PickupSpawn> pickups = new ArrayList<>();
        boolean hasSpawn = false;
        float spawnX = 0f;
        float spawnZ = 0f;
        float spawnYaw = 0f;
        boolean hasExit = false;
        float exitX = 0f;
        float exitZ = 0f;
        for (int row = 0; row < rows.length; row++) {
            if (rows[row].length() != width) {
                throw new IllegalArgumentException("Row " + row + " is " + rows[row].length()
                    + " cells wide, expected " + width);
            }
            for (int col = 0; col < width; col++) {
                char c = rows[row].charAt(col);
                float centreX = col + 0.5f;
                float centreZ = row + 0.5f;
                if (WALL_TYPES.indexOf(c) >= 0) {
                    cells[row][col] = c;
                    continue;
                }
                cells[row][col] = FLOOR;
                int facing = "^>v<".indexOf(c);
                if (facing >= 0) {
                    if (hasSpawn) throw new IllegalArgumentException("The layout has more than one player spawn");
                    hasSpawn = true;
                    spawnX = centreX;
                    spawnZ = centreZ;
                    spawnYaw = facing * 90f;
                } else if (c == 'o') {
                    targets.add(new TargetSpawn(Target.Kind.CANISTER, centreX, centreZ));
                } else if (c == 'O') {
                    targets.add(new TargetSpawn(Target.Kind.RING_BOARD, centreX, centreZ));
                } else if (c == '+') {
                    pickups.add(new PickupSpawn(Pickup.Kind.MEDKIT, centreX, centreZ));
                } else if (c == 'a') {
                    pickups.add(new PickupSpawn(Pickup.Kind.ARMOR, centreX, centreZ));
                } else if (c == 'e') {
                    pickups.add(new PickupSpawn(Pickup.Kind.ENERGY_CELL, centreX, centreZ));
                } else if (c == 'X') {
                    if (hasExit) throw new IllegalArgumentException("The layout has more than one exit");
                    hasExit = true;
                    exitX = centreX;
                    exitZ = centreZ;
                } else if (c != FLOOR) {
                    throw new IllegalArgumentException("Unknown layout character '" + c + "' at column " + col
                        + ", row " + row);
                }
            }
        }
        if (!hasSpawn) throw new IllegalArgumentException("The layout has no player spawn");
        return new GameMap(cells, spawnX, spawnZ, spawnYaw, hasExit, exitX, exitZ, targets, pickups);
    }

    /** @return the number of columns. */
    public int getWidth() {
        return width;
    }

    /** @return the number of rows. */
    public int getHeight() {
        return height;
    }

    /**
     * @param col the column
     * @param row the row
     * @return whether the cell is a wall; every cell outside the grid counts as a wall
     */
    public boolean isSolid(int col, int row) {
        return col < 0 || row < 0 || col >= width || row >= height || cells[row][col] != FLOOR;
    }

    /**
     * @param col the column
     * @param row the row
     * @return the wall type of the cell, {@link #FLOOR} for open floor, or the first wall type outside the grid
     */
    public char wallAt(int col, int row) {
        if (col < 0 || row < 0 || col >= width || row >= height) return WALL_TYPES.charAt(0);
        return cells[row][col];
    }

    /** @return the world x coordinate of the player spawn. */
    public float getSpawnX() {
        return spawnX;
    }

    /** @return the world z coordinate of the player spawn. */
    public float getSpawnZ() {
        return spawnZ;
    }

    /** @return the yaw the player spawns with, in degrees: 0 faces north, 90 east, 180 south and 270 west. */
    public float getSpawnYaw() {
        return spawnYaw;
    }

    /** @return whether this map defines a level exit pad. */
    public boolean hasExit() {
        return hasExit;
    }

    /** @return world x coordinate of the exit pad, or 0 if {@link #hasExit()} is false. */
    public float getExitX() {
        return exitX;
    }

    /** @return world z coordinate of the exit pad, or 0 if {@link #hasExit()} is false. */
    public float getExitZ() {
        return exitZ;
    }

    /** @return the target spawns, in layout order. */
    public List<TargetSpawn> getTargetSpawns() {
        return targetSpawns;
    }

    /** @return the pickup spawns, in layout order. */
    public List<PickupSpawn> getPickupSpawns() {
        return pickupSpawns;
    }
}

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
 * {@code =}, {@code A}, {@code C}, {@code L} and {@code M} are unlocked, amber-key, cyan-key, lockdown and boss
 * bulkhead doors, {@code k} and {@code c} are amber and cyan keycard pickups, {@code W} and {@code s} are the Combat
 * Scattergun and shell box pickups, {@code R} and {@code r} are the Rail Lance and slug pack pickups, {@code S} is a
 * security console pedestal, {@code ~} is an always-live shock floor grate, {@code o} and {@code O} are open floor
 * with a canister or a ring board on it, {@code D}, {@code T}, {@code G} and {@code B} are rusher drone, sentry turret,
 * guard bot and Warden Mech spawns, {@code +}, {@code a} and {@code e} are medkit, armor and energy cell pickups,
 * {@code X} is an optional level exit pad, and one of {@code ^ > v <} is the player spawn, facing north, east, south or west.
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

    /** Where a hostile enemy starts. */
    public static final class EnemySpawn {
        /** The archetype of enemy to create. */
        public final Enemy.Kind kind;
        /** The world x coordinate, at the centre of the cell. */
        public final float x;
        /** The world z coordinate, at the centre of the cell. */
        public final float z;

        EnemySpawn(Enemy.Kind kind, float x, float z) {
            this.kind = kind;
            this.x = x;
            this.z = z;
        }
    }

    /** Where a sliding bulkhead door starts. */
    public static final class DoorSpawn {
        /** Locking mechanism of the door. */
        public final Door.LockKind lockKind;
        /** Panel orientation derived from flanking walls. */
        public final Door.Orientation orientation;
        /** Grid column of the doorway cell. */
        public final int col;
        /** Grid row of the doorway cell. */
        public final int row;

        DoorSpawn(Door.LockKind lockKind, Door.Orientation orientation, int col, int row) {
            this.lockKind = lockKind;
            this.orientation = orientation;
            this.col = col;
            this.row = row;
        }
    }

    /** Where a security console pedestal starts. */
    public static final class ConsoleSpawn {
        /** The world x coordinate, at the centre of the cell. */
        public final float x;
        /** The world z coordinate, at the centre of the cell. */
        public final float z;

        ConsoleSpawn(float x, float z) {
            this.x = x;
            this.z = z;
        }
    }

    /** World and grid coordinates of an always-live shock/thermal floor grate (`~`). */
    public static final class HazardCell {
        /** Grid column. */
        public final int col;
        /** Grid row. */
        public final int row;
        /** World X centre. */
        public final float x;
        /** World Z centre. */
        public final float z;

        HazardCell(int col, int row) {
            this.col = col;
            this.row = row;
            this.x = col + 0.5f;
            this.z = row + 0.5f;
        }
    }

    private final char[][] cells;
    private final boolean[][] hazards;
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
    private final List<EnemySpawn> enemySpawns;
    private final List<DoorSpawn> doorSpawns;
    private final List<ConsoleSpawn> consoleSpawns;
    private final List<HazardCell> hazardCells;

    private GameMap(char[][] cells, boolean[][] hazards, float spawnX, float spawnZ, float spawnYaw, boolean hasExit,
                    float exitX, float exitZ, List<TargetSpawn> targetSpawns, List<PickupSpawn> pickupSpawns,
                    List<EnemySpawn> enemySpawns, List<DoorSpawn> doorSpawns, List<ConsoleSpawn> consoleSpawns,
                    List<HazardCell> hazardCells) {
        this.cells = cells;
        this.hazards = hazards;
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
        this.enemySpawns = Collections.unmodifiableList(enemySpawns);
        this.doorSpawns = Collections.unmodifiableList(doorSpawns);
        this.consoleSpawns = Collections.unmodifiableList(consoleSpawns);
        this.hazardCells = Collections.unmodifiableList(hazardCells);
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
        boolean[][] hazards = new boolean[rows.length][width];
        List<TargetSpawn> targets = new ArrayList<>();
        List<PickupSpawn> pickups = new ArrayList<>();
        List<EnemySpawn> enemies = new ArrayList<>();
        List<DoorSpawn> doors = new ArrayList<>();
        List<ConsoleSpawn> consoles = new ArrayList<>();
        List<HazardCell> hazardCells = new ArrayList<>();
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
                } else if (c == 'k') {
                    pickups.add(new PickupSpawn(Pickup.Kind.AMBER_KEYCARD, centreX, centreZ));
                } else if (c == 'c') {
                    pickups.add(new PickupSpawn(Pickup.Kind.CYAN_KEYCARD, centreX, centreZ));
                } else if (c == 'W') {
                    pickups.add(new PickupSpawn(Pickup.Kind.SCATTERGUN, centreX, centreZ));
                } else if (c == 's') {
                    pickups.add(new PickupSpawn(Pickup.Kind.SHELLS, centreX, centreZ));
                } else if (c == 'R') {
                    pickups.add(new PickupSpawn(Pickup.Kind.RAIL_LANCE, centreX, centreZ));
                } else if (c == 'r') {
                    pickups.add(new PickupSpawn(Pickup.Kind.RAIL_SLUGS, centreX, centreZ));
                } else if (c == 'S') {
                    consoles.add(new ConsoleSpawn(centreX, centreZ));
                } else if (c == 'D') {
                    enemies.add(new EnemySpawn(Enemy.Kind.DRONE, centreX, centreZ));
                } else if (c == 'T') {
                    enemies.add(new EnemySpawn(Enemy.Kind.TURRET, centreX, centreZ));
                } else if (c == 'G') {
                    enemies.add(new EnemySpawn(Enemy.Kind.GUARD, centreX, centreZ));
                } else if (c == 'B') {
                    enemies.add(new EnemySpawn(Enemy.Kind.WARDEN, centreX, centreZ));
                } else if (c == '~') {
                    hazards[row][col] = true;
                    hazardCells.add(new HazardCell(col, row));
                } else if (c == 'X') {
                    if (hasExit) throw new IllegalArgumentException("The layout has more than one exit");
                    hasExit = true;
                    exitX = centreX;
                    exitZ = centreZ;
                } else if (c != FLOOR && doorLockKind(c) == null) {
                    throw new IllegalArgumentException("Unknown layout character '" + c + "' at column " + col
                        + ", row " + row);
                }
            }
        }
        for (int row = 0; row < rows.length; row++) {
            for (int col = 0; col < width; col++) {
                Door.LockKind lock = doorLockKind(rows[row].charAt(col));
                if (lock != null) {
                    doors.add(new DoorSpawn(lock, doorOrientation(cells, col, row), col, row));
                }
            }
        }
        if (!hasSpawn) throw new IllegalArgumentException("The layout has no player spawn");
        return new GameMap(cells, hazards, spawnX, spawnZ, spawnYaw, hasExit, exitX, exitZ,
            targets, pickups, enemies, doors, consoles, hazardCells);
    }

    private static Door.LockKind doorLockKind(char c) {
        switch (c) {
            case '=': return Door.LockKind.NONE;
            case 'A': return Door.LockKind.AMBER_KEY;
            case 'C': return Door.LockKind.CYAN_KEY;
            case 'L': return Door.LockKind.LOCKDOWN;
            case 'M': return Door.LockKind.BOSS;
            default: return null;
        }
    }

    private static Door.Orientation doorOrientation(char[][] cells, int col, int row) {
        int lr = (isSolidCell(cells, col - 1, row) ? 1 : 0) + (isSolidCell(cells, col + 1, row) ? 1 : 0);
        int ud = (isSolidCell(cells, col, row - 1) ? 1 : 0) + (isSolidCell(cells, col, row + 1) ? 1 : 0);
        return lr >= ud ? Door.Orientation.SPAN_X : Door.Orientation.SPAN_Z;
    }

    private static boolean isSolidCell(char[][] cells, int col, int row) {
        return col < 0 || row < 0 || row >= cells.length || col >= cells[0].length || cells[row][col] != FLOOR;
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
     * @param col the grid column
     * @param row the grid row
     * @return whether this cell holds an always-live shock grate hazard (`~`)
     */
    public boolean isHazard(int col, int row) {
        if (col < 0 || row < 0 || col >= width || row >= height) return false;
        return hazards[row][col];
    }

    /**
     * @param worldX world x coordinate
     * @param worldZ world z coordinate
     * @return whether the grid cell containing {@code (worldX, worldZ)} holds a shock grate hazard
     */
    public boolean isHazardAt(float worldX, float worldZ) {
        return isHazard((int) Math.floor(worldX), (int) Math.floor(worldZ));
    }

    /** @return immutable list of all shock grate cells in parse order. */
    public List<HazardCell> getHazardCells() {
        return hazardCells;
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

    /** @return the enemy spawns, in layout order. */
    public List<EnemySpawn> getEnemySpawns() {
        return enemySpawns;
    }

    /** @return the bulkhead door spawns, in layout order. */
    public List<DoorSpawn> getDoorSpawns() {
        return doorSpawns;
    }

    /** @return the security console spawns, in layout order. */
    public List<ConsoleSpawn> getConsoleSpawns() {
        return consoleSpawns;
    }
}

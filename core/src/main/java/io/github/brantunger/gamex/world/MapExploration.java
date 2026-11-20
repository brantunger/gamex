package io.github.brantunger.gamex.world;

import java.util.Arrays;
import java.util.List;

/**
 * Tracks permanent fog-of-war exploration across the cells of a {@link GameMap}. Cells within
 * {@link #DEFAULT_REVEAL_RADIUS} that have unobstructed 360-degree line of sight from the player (blocked by solid
 * walls and closed bulkhead doors) are permanently revealed along with their bordering walls.
 */
public final class MapExploration {

    /** Default 360-degree visibility radius from the player, in world units (map cells). */
    public static final float DEFAULT_REVEAL_RADIUS = 12f;
    /** Minimum player movement squared before recomputing line-of-sight when door states are unchanged. */
    private static final float MOVE_EPSILON_SQ = 0.0025f;
    private static final float[] SAMPLE_X = {0.5f, 0.2f, 0.8f, 0.2f, 0.8f};
    private static final float[] SAMPLE_Z = {0.5f, 0.2f, 0.2f, 0.8f, 0.8f};
    private static final float[] DOOR_FACE_SAMPLES = {0.5f, 0.25f, 0.75f};

    private final GameMap map;
    private final int width;
    private final int height;
    private final boolean[] explored;
    private final Door[] blockingDoors;
    private int exploredCount;

    private float lastX = Float.NaN;
    private float lastZ = Float.NaN;
    private float lastRadius = Float.NaN;
    private boolean[] lastDoorBlocking;

    /**
     * Creates an unexplored fog-of-war tracker sized to {@code map}.
     * @param map the stage layout
     * @throws IllegalArgumentException if {@code map} is {@code null}
     */
    public MapExploration(GameMap map) {
        if (map == null) {
            throw new IllegalArgumentException("Map must not be null");
        }
        this.map = map;
        this.width = map.getWidth();
        this.height = map.getHeight();
        int cellCount = width * height;
        this.explored = new boolean[cellCount];
        this.blockingDoors = new Door[cellCount];
    }

    /** @return map width in grid columns. */
    public int getWidth() {
        return width;
    }

    /** @return map height in grid rows. */
    public int getHeight() {
        return height;
    }

    /** @return number of permanently explored grid cells. */
    public int getExploredCount() {
        return exploredCount;
    }

    /**
     * @param col grid column
     * @param row grid row
     * @return whether cell {@code (col, row)} has been explored; out-of-bounds cells return {@code false}
     */
    public boolean isExplored(int col, int row) {
        if (!inBounds(col, row)) return false;
        return explored[row * width + col];
    }

    /**
     * @param worldX world x coordinate
     * @param worldZ world z coordinate
     * @return whether the cell containing {@code (worldX, worldZ)} has been explored
     */
    public boolean isExploredAt(float worldX, float worldZ) {
        if (!Float.isFinite(worldX) || !Float.isFinite(worldZ)) return false;
        return isExplored((int) Math.floor(worldX), (int) Math.floor(worldZ));
    }

    /**
     * Checks whether a dynamic entity at {@code (worldX, worldZ)} lies in explored territory. If the entity's centre
     * sits inside a closed bulkhead door cell, checks the explored status of the adjacent cell on the entity's own
     * side of the slab so enemies pressed against the far side of a closed door stay hidden.
     * @param worldX entity world x coordinate
     * @param worldZ entity world z coordinate
     * @param doors bulkhead doors in the level, or {@code null}
     * @return {@code true} if the entity should be visible on explored tiles
     */
    public boolean isEntityExplored(float worldX, float worldZ, List<Door> doors) {
        if (!Float.isFinite(worldX) || !Float.isFinite(worldZ)) return false;
        int col = (int) Math.floor(worldX);
        int row = (int) Math.floor(worldZ);
        if (!isExplored(col, row)) return false;
        if (doors != null) {
            for (int i = 0; i < doors.size(); i++) {
                Door door = doors.get(i);
                if (door == null || !door.isBlockingPassage()) continue;
                if (door.getCol() == col && door.getRow() == row) {
                    int sideCol = col;
                    int sideRow = row;
                    if (door.getOrientation() == Door.Orientation.SPAN_X) {
                        sideRow += worldZ < door.getZ() ? -1 : 1;
                    } else {
                        sideCol += worldX < door.getX() ? -1 : 1;
                    }
                    return isExplored(sideCol, sideRow);
                }
            }
        }
        return true;
    }

    /** Clears all explored cells and cached visibility state. */
    public void reset() {
        Arrays.fill(explored, false);
        Arrays.fill(blockingDoors, null);
        exploredCount = 0;
        lastX = Float.NaN;
        lastZ = Float.NaN;
        lastRadius = Float.NaN;
        lastDoorBlocking = null;
    }

    /**
     * Reveals all cells within {@link #DEFAULT_REVEAL_RADIUS} visible from {@code (playerX, playerZ)}.
     * @param playerX player world x
     * @param playerZ player world z
     * @param doors bulkhead doors in the level (closed ones block sight)
     */
    public void reveal(float playerX, float playerZ, List<Door> doors) {
        reveal(playerX, playerZ, doors, DEFAULT_REVEAL_RADIUS);
    }

    /**
     * Reveals all cells within {@code radius} that have unobstructed 360-degree line of sight from
     * {@code (playerX, playerZ)}.
     * @param playerX player world x
     * @param playerZ player world z
     * @param doors bulkhead doors in the level (closed ones block sight)
     * @param radius maximum sight radius in world units
     */
    public void reveal(float playerX, float playerZ, List<Door> doors, float radius) {
        if (!Float.isFinite(playerX) || !Float.isFinite(playerZ) || !Float.isFinite(radius) || radius <= 0f) {
            return;
        }
        int doorCount = doors == null ? 0 : doors.size();
        boolean doorsChanged = lastDoorBlocking == null || lastDoorBlocking.length != doorCount;
        if (!doorsChanged) {
            for (int i = 0; i < doorCount; i++) {
                Door door = doors.get(i);
                boolean blocking = door != null && door.isBlockingPassage();
                if (blocking != lastDoorBlocking[i]) {
                    doorsChanged = true;
                    break;
                }
            }
        }
        if (!Float.isNaN(lastX) && radius == lastRadius && !doorsChanged) {
            float dx = playerX - lastX;
            float dz = playerZ - lastZ;
            if (dx * dx + dz * dz < MOVE_EPSILON_SQ) {
                return;
            }
        }

        lastX = playerX;
        lastZ = playerZ;
        lastRadius = radius;
        if (lastDoorBlocking == null || lastDoorBlocking.length != doorCount) {
            lastDoorBlocking = new boolean[doorCount];
        }
        Arrays.fill(blockingDoors, null);
        for (int i = 0; i < doorCount; i++) {
            Door door = doors.get(i);
            boolean blocking = door != null && door.isBlockingPassage();
            lastDoorBlocking[i] = blocking;
            if (blocking && inBounds(door.getCol(), door.getRow())) {
                blockingDoors[door.getRow() * width + door.getCol()] = door;
            } else if (!blocking && door != null && isExplored(door.getCol(), door.getRow())) {
                // If an already-seen closed door just opened, reveal its remaining bordering corner walls too.
                markCellAndSolidNeighbors(door.getCol(), door.getRow());
            }
        }

        int playerCol = (int) Math.floor(playerX);
        int playerRow = (int) Math.floor(playerZ);
        if (inBounds(playerCol, playerRow) && !map.isSolid(playerCol, playerRow)) {
            Door playerDoor = blockingDoors[playerRow * width + playerCol];
            if (playerDoor == null) {
                markCellAndSolidNeighbors(playerCol, playerRow);
            } else {
                markDoorCellAndFlanks(playerCol, playerRow, playerDoor.getOrientation());
            }
        }

        float radiusSq = radius * radius;
        int minCol = Math.max(0, (int) Math.floor(playerX - radius) - 1);
        int maxCol = Math.min(width - 1, (int) Math.floor(playerX + radius) + 1);
        int minRow = Math.max(0, (int) Math.floor(playerZ - radius) - 1);
        int maxRow = Math.min(height - 1, (int) Math.floor(playerZ + radius) + 1);

        for (int row = minRow; row <= maxRow; row++) {
            for (int col = minCol; col <= maxCol; col++) {
                int idx = row * width + col;
                if (explored[idx] || map.isSolid(col, row)) continue;

                Door blockingDoor = blockingDoors[idx];
                if (blockingDoor != null) {
                    if (isDoorFaceVisible(playerX, playerZ, col, row, blockingDoor, doors, radiusSq)) {
                        markDoorCellAndFlanks(col, row, blockingDoor.getOrientation());
                    }
                    continue;
                }

                float nearX = Math.max(col, Math.min(playerX, col + 1f));
                float nearZ = Math.max(row, Math.min(playerZ, row + 1f));
                float ndx = playerX - nearX;
                float ndz = playerZ - nearZ;
                if (ndx * ndx + ndz * ndz > radiusSq) continue;

                if (isOpenCellVisible(playerX, playerZ, col, row, doors, radiusSq)) {
                    markCellAndSolidNeighbors(col, row);
                }
            }
        }
    }

    private boolean isOpenCellVisible(float playerX, float playerZ, int col, int row, List<Door> doors,
                                      float radiusSq) {
        for (int i = 0; i < SAMPLE_X.length; i++) {
            float sx = col + SAMPLE_X[i];
            float sz = row + SAMPLE_Z[i];
            float dx = sx - playerX;
            float dz = sz - playerZ;
            if (dx * dx + dz * dz <= radiusSq
                && Hitscan.hasLineOfSight(map, doors, playerX, playerZ, sx, sz)) {
                return true;
            }
        }
        return false;
    }

    private boolean isDoorFaceVisible(float playerX, float playerZ, int col, int row, Door door, List<Door> doors,
                                      float radiusSq) {
        if (door.getOrientation() == Door.Orientation.SPAN_X) {
            float zFace = playerZ <= door.getZ() ? row + 0.35f : row + 0.65f;
            for (int i = 0; i < DOOR_FACE_SAMPLES.length; i++) {
                float sx = col + DOOR_FACE_SAMPLES[i];
                float dx = sx - playerX;
                float dz = zFace - playerZ;
                if (dx * dx + dz * dz <= radiusSq
                    && Hitscan.hasLineOfSight(map, doors, playerX, playerZ, sx, zFace)) {
                    return true;
                }
            }
        } else {
            float xFace = playerX <= door.getX() ? col + 0.35f : col + 0.65f;
            for (int i = 0; i < DOOR_FACE_SAMPLES.length; i++) {
                float sz = row + DOOR_FACE_SAMPLES[i];
                float dx = xFace - playerX;
                float dz = sz - playerZ;
                if (dx * dx + dz * dz <= radiusSq
                    && Hitscan.hasLineOfSight(map, doors, playerX, playerZ, xFace, sz)) {
                    return true;
                }
            }
        }
        return false;
    }

    private void markCellAndSolidNeighbors(int col, int row) {
        markExplored(col, row);
        for (int dr = -1; dr <= 1; dr++) {
            for (int dc = -1; dc <= 1; dc++) {
                if (dc == 0 && dr == 0) continue;
                int nc = col + dc;
                int nr = row + dr;
                if (inBounds(nc, nr) && map.isSolid(nc, nr)) {
                    markExplored(nc, nr);
                }
            }
        }
    }

    private void markDoorCellAndFlanks(int col, int row, Door.Orientation orientation) {
        markExplored(col, row);
        if (orientation == Door.Orientation.SPAN_X) {
            markIfSolid(col - 1, row);
            markIfSolid(col + 1, row);
        } else {
            markIfSolid(col, row - 1);
            markIfSolid(col, row + 1);
        }
    }

    private void markIfSolid(int col, int row) {
        if (inBounds(col, row) && map.isSolid(col, row)) {
            markExplored(col, row);
        }
    }

    private void markExplored(int col, int row) {
        if (!inBounds(col, row)) return;
        int idx = row * width + col;
        if (!explored[idx]) {
            explored[idx] = true;
            exploredCount++;
        }
    }

    private boolean inBounds(int col, int row) {
        return col >= 0 && row >= 0 && col < width && row < height;
    }
}

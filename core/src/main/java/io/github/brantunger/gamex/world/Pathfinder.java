package io.github.brantunger.gamex.world;

import com.badlogic.gdx.math.Vector2;

import java.util.Arrays;
import java.util.Collections;
import java.util.List;

/**
 * Reusable zero-allocation grid BFS flow-field over {@link GameMap} for routing mobile enemies cleanly around walls,
 * pillars and 1-cell doorways.
 */
public final class Pathfinder {

    private static final int UNREACHABLE = Integer.MAX_VALUE;
    private static final int FLOOR_COST = 1;
    private static final int HAZARD_COST = 8;
    private static final int[] DIRS = {
        1, 0,
        -1, 0,
        0, 1,
        0, -1,
        1, 1,
        1, -1,
        -1, 1,
        -1, -1
    };

    private int[] distances = new int[0];
    private int[] queue = new int[0];
    private boolean[] inQueue = new boolean[0];
    private int width;
    private int height;
    private float goalX;
    private float goalZ;

    /**
     * Computes grid distances from {@code (goalX, goalZ)} across all reachable open cells in {@code map}.
     * @param map the level grid
     * @param goalX target world x coordinate
     * @param goalZ target world z coordinate
     */
    public void compute(GameMap map, float goalX, float goalZ) {
        compute(map, Collections.<Door>emptyList(), false, goalX, goalZ);
    }

    /**
     * Computes grid distances from {@code (goalX, goalZ)} across all reachable cells, treating closed bulkhead doors
     * as impassable until opened past their clearance threshold.
     * @param map the level grid
     * @param doors bulkhead doors in the level
     * @param goalX target world x coordinate
     * @param goalZ target world z coordinate
     */
    public void compute(GameMap map, List<Door> doors, float goalX, float goalZ) {
        compute(map, doors, false, goalX, goalZ);
    }

    /**
     * Computes shortest path costs from {@code (goalX, goalZ)} across all reachable cells, optionally weighting
     * {@code ~} shock grate hazard cells heavily so grounded units detour around them when a safe route exists.
     * @param map the level grid
     * @param doors bulkhead doors in the level
     * @param avoidHazards true to prefer routes free of shock grates when a safe detour exists
     * @param goalX target world x coordinate
     * @param goalZ target world z coordinate
     */
    public void compute(GameMap map, List<Door> doors, boolean avoidHazards, float goalX, float goalZ) {
        this.width = map.getWidth();
        this.height = map.getHeight();
        this.goalX = goalX;
        this.goalZ = goalZ;
        int total = width * height;
        if (distances.length < total) {
            distances = new int[total];
            queue = new int[total];
            inQueue = new boolean[total];
        }
        Arrays.fill(distances, 0, total, UNREACHABLE);
        Arrays.fill(inQueue, 0, total, false);

        if (!Float.isFinite(goalX) || !Float.isFinite(goalZ)) return;
        int goalCol = (int) Math.floor(goalX);
        int goalRow = (int) Math.floor(goalZ);
        if (map.isSolid(goalCol, goalRow)) return;
        Door goalDoor = blockingDoorAt(doors, goalCol, goalRow);
        if (goalDoor != null) {
            goalCol = stepOffDoorCol(goalDoor, goalX);
            goalRow = stepOffDoorRow(goalDoor, goalZ);
        }
        if (isBlocked(map, doors, goalCol, goalRow)) return;

        int startIdx = goalRow * width + goalCol;
        distances[startIdx] = 0;
        int head = 0;
        int tail = 0;
        int queuedCount = 0;
        queue[tail] = startIdx;
        tail = (tail + 1) % total;
        inQueue[startIdx] = true;
        queuedCount++;

        while (queuedCount > 0) {
            int idx = queue[head];
            head = (head + 1) % total;
            queuedCount--;
            inQueue[idx] = false;

            int col = idx % width;
            int row = idx / width;
            int currentDist = distances[idx];
            for (int d = 0; d < 8; d += 2) {
                int nc = col + DIRS[d];
                int nr = row + DIRS[d + 1];
                if (isBlocked(map, doors, nc, nr)) continue;
                int stepCost = (avoidHazards && map.isHazard(col, row)) ? HAZARD_COST : FLOOR_COST;
                int nextDist = currentDist + stepCost;
                int nIdx = nr * width + nc;
                if (distances[nIdx] <= nextDist) continue;
                distances[nIdx] = nextDist;
                if (!inQueue[nIdx]) {
                    queue[tail] = nIdx;
                    tail = (tail + 1) % total;
                    inQueue[nIdx] = true;
                    queuedCount++;
                }
            }
        }
    }

    /**
     * Finds the unit steering direction from {@code (fromX, fromZ)} toward the best adjacent cell closer to the goal.
     * Diagonal transitions are only allowed when both flanking cardinal cells are open so entities do not snag on
     * wall corners.
     * @param map the level grid
     * @param fromX starting world x
     * @param fromZ starting world z
     * @param outDirection receives the normalised direction when a step exists
     * @return true if a steering direction was found, false if unreachable or already inside the goal cell
     */
    public boolean nextDirection(GameMap map, float fromX, float fromZ, Vector2 outDirection) {
        return nextDirection(map, Collections.<Door>emptyList(), false, fromX, fromZ, outDirection);
    }

    /**
     * Finds the unit steering direction toward the goal while avoiding closed bulkhead doorway cells.
     * @param map the level grid
     * @param doors bulkhead doors in the level
     * @param fromX starting world x
     * @param fromZ starting world z
     * @param outDirection receives the normalised direction when a step exists
     * @return true if a steering direction was found, false if unreachable or already inside the goal cell
     */
    public boolean nextDirection(GameMap map, List<Door> doors, float fromX, float fromZ, Vector2 outDirection) {
        return nextDirection(map, doors, false, fromX, fromZ, outDirection);
    }

    /**
     * Finds the unit steering direction toward the goal while avoiding closed bulkheads and optionally preventing
     * diagonal corner-clips across {@code ~} shock grates.
     * @param map the level grid
     * @param doors bulkhead doors in the level
     * @param avoidHazards true to avoid diagonal steps that brush adjacent shock grates
     * @param fromX starting world x
     * @param fromZ starting world z
     * @param outDirection receives the normalised direction when a step exists
     * @return true if a steering direction was found, false if unreachable or already inside the goal cell
     */
    public boolean nextDirection(GameMap map, List<Door> doors, boolean avoidHazards, float fromX, float fromZ,
                                 Vector2 outDirection) {
        if (map == null || !Float.isFinite(fromX) || !Float.isFinite(fromZ)) return false;
        int col = (int) Math.floor(fromX);
        int row = (int) Math.floor(fromZ);
        if (col < 0 || row < 0 || col >= width || row >= height) return false;
        if (map.isSolid(col, row)) return false;

        Door originDoor = blockingDoorAt(doors, col, row);
        if (originDoor != null) {
            int sideCol = stepOffDoorCol(originDoor, fromX);
            int sideRow = stepOffDoorRow(originDoor, fromZ);
            if (sideCol < 0 || sideRow < 0 || sideCol >= width || sideRow >= height) return false;
            if (isBlocked(map, doors, sideCol, sideRow)) return false;
            int sideDist = distances[sideRow * width + sideCol];
            if (sideDist == UNREACHABLE) return false;
            if (sideDist == 0 && (int) Math.floor(goalX) == col && (int) Math.floor(goalZ) == row) {
                return false;
            }
            float dx = (sideCol + 0.5f) - fromX;
            float dz = (sideRow + 0.5f) - fromZ;
            float len = (float) Math.hypot(dx, dz);
            if (len < 1e-6f) return false;
            outDirection.set(dx / len, dz / len);
            return true;
        }

        int currentDist = distances[row * width + col];
        if (currentDist == UNREACHABLE || currentDist == 0) return false;

        int bestCol = -1;
        int bestRow = -1;
        int bestCost = UNREACHABLE;
        float bestGoalDist2 = Float.POSITIVE_INFINITY;

        for (int i = 0; i < DIRS.length; i += 2) {
            int dc = DIRS[i];
            int dr = DIRS[i + 1];
            int nc = col + dc;
            int nr = row + dr;
            if (nc < 0 || nr < 0 || nc >= width || nr >= height) continue;
            if (isBlocked(map, doors, nc, nr)) continue;
            if (dc != 0 && dr != 0
                && (isBlocked(map, doors, col + dc, row) || isBlocked(map, doors, col, row + dr))) {
                continue;
            }
            if (avoidHazards && dc != 0 && dr != 0
                && (map.isHazard(nc, nr) || map.isHazard(col + dc, row) || map.isHazard(col, row + dr))) {
                continue;
            }
            int d = distances[nr * width + nc];
            if (d == UNREACHABLE) continue;
            int stepCost = (avoidHazards && map.isHazard(nc, nr)) ? HAZARD_COST : FLOOR_COST;
            int candCost = d + stepCost;
            if (candCost > currentDist) continue;
            float cx = nc + 0.5f;
            float cz = nr + 0.5f;
            float goalDist2 = (cx - goalX) * (cx - goalX) + (cz - goalZ) * (cz - goalZ);
            if (candCost < bestCost || (candCost == bestCost && goalDist2 < bestGoalDist2)) {
                bestCost = candCost;
                bestGoalDist2 = goalDist2;
                bestCol = nc;
                bestRow = nr;
            }
        }

        if (bestCol < 0) return false;
        float dx = (bestCol + 0.5f) - fromX;
        float dz = (bestRow + 0.5f) - fromZ;
        float len = (float) Math.hypot(dx, dz);
        if (len < 1e-6f) return false;
        outDirection.set(dx / len, dz / len);
        return true;
    }

    private static boolean isBlocked(GameMap map, List<Door> doors, int col, int row) {
        return map.isSolid(col, row) || blockingDoorAt(doors, col, row) != null;
    }

    private static Door blockingDoorAt(List<Door> doors, int col, int row) {
        if (doors == null) return null;
        for (int i = 0; i < doors.size(); i++) {
            Door door = doors.get(i);
            if (door != null && door.isBlockingPassage() && door.getCol() == col && door.getRow() == row) {
                return door;
            }
        }
        return null;
    }

    private static int stepOffDoorCol(Door door, float worldX) {
        if (door.getOrientation() == Door.Orientation.SPAN_Z) {
            return door.getCol() + (worldX < door.getX() ? -1 : 1);
        }
        return door.getCol();
    }

    private static int stepOffDoorRow(Door door, float worldZ) {
        if (door.getOrientation() == Door.Orientation.SPAN_X) {
            return door.getRow() + (worldZ < door.getZ() ? -1 : 1);
        }
        return door.getRow();
    }
}

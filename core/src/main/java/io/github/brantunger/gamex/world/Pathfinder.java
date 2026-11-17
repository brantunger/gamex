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
        compute(map, Collections.<Door>emptyList(), goalX, goalZ);
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
        this.width = map.getWidth();
        this.height = map.getHeight();
        this.goalX = goalX;
        this.goalZ = goalZ;
        int total = width * height;
        if (distances.length < total) {
            distances = new int[total];
            queue = new int[total];
        }
        Arrays.fill(distances, 0, total, UNREACHABLE);

        int goalCol = (int) Math.floor(goalX);
        int goalRow = (int) Math.floor(goalZ);
        if (isBlocked(map, doors, goalCol, goalRow)) return;

        int startIdx = goalRow * width + goalCol;
        distances[startIdx] = 0;
        int head = 0;
        int tail = 0;
        queue[tail++] = startIdx;

        while (head < tail) {
            int idx = queue[head++];
            int col = idx % width;
            int row = idx / width;
            int nextDist = distances[idx] + 1;
            for (int d = 0; d < 8; d += 2) {
                int nc = col + DIRS[d];
                int nr = row + DIRS[d + 1];
                if (isBlocked(map, doors, nc, nr)) continue;
                int nIdx = nr * width + nc;
                if (distances[nIdx] <= nextDist) continue;
                distances[nIdx] = nextDist;
                queue[tail++] = nIdx;
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
        return nextDirection(map, Collections.<Door>emptyList(), fromX, fromZ, outDirection);
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
        int col = (int) Math.floor(fromX);
        int row = (int) Math.floor(fromZ);
        if (col < 0 || row < 0 || col >= width || row >= height) return false;
        int currentDist = distances[row * width + col];
        if (currentDist == UNREACHABLE || currentDist == 0) return false;

        int bestCol = -1;
        int bestRow = -1;
        int bestDist = currentDist;
        float bestGoalDist2 = Float.POSITIVE_INFINITY;

        for (int i = 0; i < DIRS.length; i += 2) {
            int dc = DIRS[i];
            int dr = DIRS[i + 1];
            int nc = col + dc;
            int nr = row + dr;
            if (isBlocked(map, doors, nc, nr)) continue;
            if (dc != 0 && dr != 0
                && (isBlocked(map, doors, col + dc, row) || isBlocked(map, doors, col, row + dr))) {
                continue;
            }
            int d = distances[nr * width + nc];
            if (d >= currentDist) continue;
            float cx = nc + 0.5f;
            float cz = nr + 0.5f;
            float goalDist2 = (cx - goalX) * (cx - goalX) + (cz - goalZ) * (cz - goalZ);
            if (d < bestDist || (d == bestDist && goalDist2 < bestGoalDist2)) {
                bestDist = d;
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
        if (map.isSolid(col, row)) return true;
        if (doors == null) return false;
        for (int i = 0; i < doors.size(); i++) {
            Door door = doors.get(i);
            if (door.isBlockingPassage() && door.getCol() == col && door.getRow() == row) return true;
        }
        return false;
    }
}

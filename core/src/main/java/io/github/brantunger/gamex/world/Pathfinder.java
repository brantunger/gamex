package io.github.brantunger.gamex.world;

import com.badlogic.gdx.math.Vector2;

import java.util.Arrays;

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
        if (map.isSolid(goalCol, goalRow)) return;

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
                if (map.isSolid(nc, nr)) continue;
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
            if (map.isSolid(nc, nr)) continue;
            if (dc != 0 && dr != 0 && (map.isSolid(col + dc, row) || map.isSolid(col, row + dr))) {
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
}

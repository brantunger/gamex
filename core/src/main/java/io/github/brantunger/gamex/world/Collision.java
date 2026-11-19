package io.github.brantunger.gamex.world;

import com.badlogic.gdx.math.Vector2;

import java.util.Collections;
import java.util.List;

/** Collision for things that walk on the floor: a circle against the grid's walls and against solid objects. */
public final class Collision {

    /** The number of halvings used to close the gap to a wall once an axis step is blocked. */
    private static final int REFINE_STEPS = 8;
    /** How deep a circle may sit in a solid object before it counts as overlapping; absorbs float rounding. */
    private static final float SKIN = 1e-3f;
    /** Maximum mid-step radial dip tolerated to absorb IEEE-754 float rounding on tangent escape steps. */
    private static final double TANGENT_EPS = 1e-5;

    private Collision() {}

    /**
     * @param map the map
     * @param x the circle centre's world x
     * @param z the circle centre's world z
     * @param radius the circle's radius
     * @return whether the circle overlaps any wall cell
     */
    public static boolean overlapsWall(GameMap map, float x, float z, float radius) {
        int minCol = (int) Math.floor(x - radius);
        int maxCol = (int) Math.floor(x + radius);
        int minRow = (int) Math.floor(z - radius);
        int maxRow = (int) Math.floor(z + radius);
        for (int row = minRow; row <= maxRow; row++) {
            for (int col = minCol; col <= maxCol; col++) {
                if (!map.isSolid(col, row)) continue;
                // The point of the cell's square nearest to the circle's centre.
                float nearestX = Math.max(col, Math.min(x, col + 1f));
                float nearestZ = Math.max(row, Math.min(z, row + 1f));
                float dx = x - nearestX;
                float dz = z - nearestZ;
                if (dx * dx + dz * dz < radius * radius) return true;
            }
        }
        return false;
    }

    /**
     * @param map the map
     * @param doors bulkhead doors in the level; closed ones act as solid barriers
     * @param x the circle centre's world x
     * @param z the circle centre's world z
     * @param radius the circle's radius
     * @return whether the circle overlaps any wall cell or blocking bulkhead door
     */
    public static boolean overlapsWall(GameMap map, List<Door> doors, float x, float z, float radius) {
        if (overlapsWall(map, x, z, radius)) return true;
        if (doors == null) return false;
        for (int i = 0; i < doors.size(); i++) {
            if (doors.get(i).overlapsCircle(x, z, radius)) return true;
        }
        return false;
    }

    /**
     * Moves a circle by a displacement, stopping at walls. Each axis is resolved on its own, so a circle pushed
     * diagonally into a wall keeps the part of its movement that runs along the wall. The displacement is walked in
     * sub-steps shorter than the radius, so no displacement, however large, carries the circle through a wall.
     * @param map the map
     * @param position the circle centre as (x, z), updated in place; it must not overlap a wall
     * @param dx the displacement along x
     * @param dz the displacement along z
     * @param radius the circle's radius
     */
    public static void move(GameMap map, Vector2 position, float dx, float dz, float radius) {
        move(map, Collections.<Damageable>emptyList(), position, dx, dz, radius);
    }

    /**
     * Moves a circle as {@link #move(GameMap, Vector2, float, float, float)} does, and also keeps it out of solid
     * objects. A circle that ends a sub-step inside a live object is pushed straight out of it, so it stops when it
     * walks at the object head-on and slides round it otherwise. Destroyed objects are not solid.
     * @param map the map
     * @param solids the objects that block the circle while they are not destroyed
     * @param position the circle centre as (x, z), updated in place; it must not overlap a wall
     * @param dx the displacement along x
     * @param dz the displacement along z
     * @param radius the circle's radius
     */
    public static void move(GameMap map, List<? extends Damageable> solids, Vector2 position, float dx, float dz,
                            float radius) {
        move(map, solids, Collections.<Door>emptyList(), position, dx, dz, radius);
    }

    /**
     * Moves a circle while stopping at walls, blocking bulkhead doors, and live {@link Damageable} solids.
     * @param map the map
     * @param solids the objects that block the circle while they are not destroyed
     * @param doors bulkhead doors in the level; closed ones block movement
     * @param position the circle centre as (x, z), updated in place
     * @param dx the displacement along x
     * @param dz the displacement along z
     * @param radius the circle's radius
     */
    public static void move(GameMap map, List<? extends Damageable> solids, List<Door> doors, Vector2 position,
                            float dx, float dz, float radius) {
        float longest = Math.max(Math.abs(dx), Math.abs(dz));
        int steps = Math.max(1, (int) Math.ceil(longest / (radius * 0.5f)));
        float stepX = dx / steps;
        float stepZ = dz / steps;
        for (int i = 0; i < steps; i++) {
            float beforeX = position.x;
            float beforeZ = position.y;
            float clampedX = beforeX + freeDistance(map, doors, beforeX, beforeZ, stepX, 0f, radius);
            float clampedZ = beforeZ + freeDistance(map, doors, clampedX, beforeZ, 0f, stepZ, radius);
            position.set(clampedX, clampedZ);
            if (!pushOut(solids, position, radius, stepX, stepZ)) continue;
            // The push must not end in a wall, door or another object; if it does, this sub-step is not taken
            // unless the circle already overlapped a solid and the unpushed step moves cleanly away from it.
            if (overlapsWall(map, doors, position.x, position.y, radius) || overlapsSolid(solids, position, radius)) {
                resolveBlockedPushOut(map, solids, doors, position, beforeX, beforeZ, clampedX, clampedZ,
                    stepX, stepZ, radius);
            }
        }
    }

    private static void resolveBlockedPushOut(GameMap map, List<? extends Damageable> solids, List<Door> doors,
                                              Vector2 position, float beforeX, float beforeZ,
                                              float clampedX, float clampedZ, float stepX, float stepZ, float radius) {
        if (!hasOverlappingSolid(solids, beforeX, beforeZ, radius)) {
            position.set(beforeX, beforeZ);
            return;
        }
        if (canEscapeStep(map, solids, doors, beforeX, beforeZ, clampedX, clampedZ, radius)) {
            position.set(clampedX, clampedZ);
            return;
        }
        if (stepX != 0f && stepZ != 0f) {
            float onlyZ = beforeZ + freeDistance(map, doors, beforeX, beforeZ, 0f, stepZ, radius);
            boolean preferX = Math.abs(clampedX - beforeX) >= Math.abs(onlyZ - beforeZ);
            float firstX = preferX ? clampedX : beforeX;
            float firstZ = preferX ? beforeZ : onlyZ;
            if (canEscapeStep(map, solids, doors, beforeX, beforeZ, firstX, firstZ, radius)) {
                position.set(firstX, firstZ);
                return;
            }
            float secondX = preferX ? beforeX : clampedX;
            float secondZ = preferX ? onlyZ : beforeZ;
            if (canEscapeStep(map, solids, doors, beforeX, beforeZ, secondX, secondZ, radius)) {
                position.set(secondX, secondZ);
                return;
            }
        }
        position.set(beforeX, beforeZ);
    }

    private static boolean hasOverlappingSolid(List<? extends Damageable> solids, float x, float z, float radius) {
        if (solids == null) return false;
        for (int i = 0; i < solids.size(); i++) {
            Damageable solid = solids.get(i);
            if (solid.isDestroyed()) continue;
            double reach = (double) solid.getRadius() + radius;
            double dx = (double) x - solid.getX();
            double dz = (double) z - solid.getZ();
            if (dx * dx + dz * dz < reach * reach) return true;
        }
        return false;
    }

    private static boolean canEscapeStep(GameMap map, List<? extends Damageable> solids, List<Door> doors,
                                         float beforeX, float beforeZ, float candX, float candZ, float radius) {
        double dxStep = (double) candX - beforeX;
        double dzStep = (double) candZ - beforeZ;
        double stepSq = dxStep * dxStep + dzStep * dzStep;
        if (stepSq == 0.0) return false;
        if (overlapsWall(map, doors, candX, candZ, radius)) return false;
        if (solids == null) return true;

        for (int i = 0; i < solids.size(); i++) {
            Damageable solid = solids.get(i);
            if (solid.isDestroyed()) continue;
            double reach = (double) solid.getRadius() + radius;
            double reachSq = reach * reach;
            double bx = (double) beforeX - solid.getX();
            double bz = (double) beforeZ - solid.getZ();
            double distSqBefore = bx * bx + bz * bz;
            double cx = (double) candX - solid.getX();
            double cz = (double) candZ - solid.getZ();
            double distSqCand = cx * cx + cz * cz;

            if (distSqBefore < reachSq) {
                if (distSqCand <= distSqBefore) return false;
                double dot = bx * dxStep + bz * dzStep;
                if (dot < 0.0) {
                    // Prevent a sub-step from chording deeper through an overlapped solid to its far side.
                    double minDistSq = distSqBefore - (dot * dot) / stepSq;
                    double minAllowed = Math.max(0.0, Math.sqrt(distSqBefore) - TANGENT_EPS);
                    if (minDistSq < minAllowed * minAllowed) return false;
                }
            } else {
                // Never introduce a new overlap with a solid that was clear before this sub-step.
                if (distSqCand < reachSq) return false;
            }
        }
        return true;
    }

    /**
     * @param solids the objects to test; destroyed ones are skipped
     * @param position the circle centre as (x, z)
     * @param radius the circle's radius
     * @return whether the circle overlaps any live object
     */
    public static boolean overlapsSolid(List<? extends Damageable> solids, Vector2 position, float radius) {
        if (solids == null) return false;
        for (int i = 0; i < solids.size(); i++) {
            Damageable solid = solids.get(i);
            if (solid.isDestroyed()) continue;
            float reach = solid.getRadius() + radius - SKIN;
            if (Vector2.dst2(position.x, position.y, solid.getX(), solid.getZ()) < reach * reach) return true;
        }
        return false;
    }

    /**
     * Pushes the circle out of every live object it overlaps, along the line from the object's centre.
     * @return whether the circle was moved
     */
    private static boolean pushOut(List<? extends Damageable> solids, Vector2 position, float radius, float stepX,
                                   float stepZ) {
        if (solids == null) return false;
        boolean pushed = false;
        for (int i = 0; i < solids.size(); i++) {
            Damageable solid = solids.get(i);
            if (solid.isDestroyed()) continue;
            float reach = solid.getRadius() + radius;
            float awayX = position.x - solid.getX();
            float awayZ = position.y - solid.getZ();
            float distance = (float) Math.sqrt(awayX * awayX + awayZ * awayZ);
            if (distance >= reach) continue;
            if (distance < 1e-5f) {
                // Dead centre: back out the way the circle came.
                awayX = -stepX;
                awayZ = -stepZ;
                distance = (float) Math.sqrt(awayX * awayX + awayZ * awayZ);
                if (distance < 1e-5f) continue;
            }
            position.x = solid.getX() + awayX / distance * reach;
            position.y = solid.getZ() + awayZ / distance * reach;
            pushed = true;
        }
        return pushed;
    }

    /**
     * Finds how much of a single-axis step the circle can take. Exactly one of {@code stepX} and {@code stepZ} is
     * expected to be non-zero.
     * @return the signed distance to move along that axis
     */
    private static float freeDistance(GameMap map, List<Door> doors, float x, float z, float stepX, float stepZ,
                                      float radius) {
        float step = stepX != 0f ? stepX : stepZ;
        if (step == 0f) return 0f;
        float unitX = stepX != 0f ? 1f : 0f;
        float unitZ = 1f - unitX;
        if (!overlapsWall(map, doors, x + unitX * step, z + unitZ * step, radius)) return step;
        // Blocked: bisect for the longest free part of the step, so the circle ends up against the barrier.
        float free = 0f;
        float blocked = step;
        for (int i = 0; i < REFINE_STEPS; i++) {
            float middle = (free + blocked) * 0.5f;
            if (overlapsWall(map, doors, x + unitX * middle, z + unitZ * middle, radius)) {
                blocked = middle;
            } else {
                free = middle;
            }
        }
        return free;
    }
}

package io.github.brantunger.gamex.world;

import com.badlogic.gdx.math.Vector3;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

/** Instant-hit ray casting against the map and the things standing in it. */
public final class Hitscan {

    /**
     * Ordered hits from a piercing ray cast through live targets/enemies up to the first solid surface
     * (wall, floor, ceiling, closed bulkhead door, or indestructible security console).
     */
    public static final class PiercingResult {
        /** Live non-console damageable hits sorted front-to-back by ascending distance. */
        public final List<RayHit> targetHits;
        /** Terminal solid surface hit (or {@link RayHit.Kind#NONE} if unobstructed to max range). */
        public final RayHit terminalHit;
        /** Distance from ray origin to the terminal stop point. */
        public final float stopDistance;
        /** World-space terminal point where the piercing ray stops. */
        public final Vector3 endPoint;

        PiercingResult(List<RayHit> targetHits, RayHit terminalHit, float stopDistance, Vector3 endPoint) {
            this.targetHits = Collections.unmodifiableList(targetHits);
            this.terminalHit = terminalHit;
            this.stopDistance = stopDistance;
            this.endPoint = endPoint;
        }
    }

    private Hitscan() {}

    /**
     * Casts a ray and returns the nearest thing it hits: a wall face, the floor, the ceiling, or a live
     * {@link Damageable}.
     * @param map the map
     * @param targets the objects that can be hit; destroyed ones are skipped
     * @param origin the ray's start, inside an open cell and between the floor and the ceiling
     * @param direction the ray's unit direction
     * @param maxDistance the ray's range
     * @return the hit, with {@link RayHit.Kind#NONE} if nothing lies within range
     */
    public static RayHit cast(GameMap map, List<? extends Damageable> targets, Vector3 origin, Vector3 direction,
                              float maxDistance) {
        return cast(map, targets, Collections.<Door>emptyList(), origin, direction, maxDistance);
    }

    /**
     * Casts a ray and returns the nearest thing it hits: a wall face, the floor, the ceiling, a {@link Door}, or a
     * live {@link Damageable}.
     * @param map the map
     * @param targets the objects that can be hit; destroyed ones are skipped
     * @param doors bulkhead doors in the level; their live retracted slab heights are tested
     * @param origin the ray's start, inside an open cell and between the floor and the ceiling
     * @param direction the ray's unit direction
     * @param maxDistance the ray's range
     * @return the hit, with {@link RayHit.Kind#NONE} if nothing lies within range
     */
    public static RayHit cast(GameMap map, List<? extends Damageable> targets, List<Door> doors, Vector3 origin,
                              Vector3 direction, float maxDistance) {
        RayHit hit = new RayHit();
        castGrid(map, origin, direction, maxDistance, hit);
        castFloorAndCeiling(origin, direction, maxDistance, hit);
        if (doors != null && !doors.isEmpty()) {
            float[] doorNormal = new float[3];
            for (int i = 0; i < doors.size(); i++) {
                Door door = doors.get(i);
                if (door != null) {
                    castDoor(door, origin, direction, maxDistance, hit, doorNormal);
                }
            }
        }
        if (targets != null) {
            for (int i = 0; i < targets.size(); i++) {
                Damageable target = targets.get(i);
                if (target != null) {
                    castTarget(target, origin, direction, maxDistance, hit);
                }
            }
        }
        if (hit.isHit()) hit.point.set(direction).scl(hit.distance).add(origin);
        return hit;
    }

    /**
     * Casts a piercing rail ray through every live target and enemy strictly closer than the first solid blocker
     * (wall, floor, ceiling, closed bulkhead door, or indestructible {@link SecurityConsole}).
     * @param map the map
     * @param targets damageable objects and consoles in the level
     * @param doors bulkhead doors in the level
     * @param origin ray start inside an open cell
     * @param direction unit ray direction
     * @param maxDistance maximum beam range
     * @return ordered target hits and terminal blocker info
     */
    public static PiercingResult castPiercing(GameMap map, List<? extends Damageable> targets, List<Door> doors,
                                              Vector3 origin, Vector3 direction, float maxDistance) {
        RayHit terminal = new RayHit();
        castGrid(map, origin, direction, maxDistance, terminal);
        castFloorAndCeiling(origin, direction, maxDistance, terminal);
        if (doors != null && !doors.isEmpty()) {
            float[] doorNormal = new float[3];
            for (int i = 0; i < doors.size(); i++) {
                Door door = doors.get(i);
                if (door != null) {
                    castDoor(door, origin, direction, maxDistance, terminal, doorNormal);
                }
            }
        }
        if (targets != null) {
            for (int i = 0; i < targets.size(); i++) {
                Damageable candidate = targets.get(i);
                if (candidate instanceof SecurityConsole) {
                    castTarget(candidate, origin, direction, maxDistance, terminal);
                }
            }
        }
        float stopDistance = terminal.isHit() ? terminal.distance : maxDistance;
        if (terminal.isHit()) {
            terminal.point.set(direction).scl(terminal.distance).add(origin);
        }
        Vector3 endPoint = new Vector3(direction).scl(stopDistance).add(origin);

        List<RayHit> hits = new ArrayList<>();
        if (targets != null) {
            for (int i = 0; i < targets.size(); i++) {
                Damageable target = targets.get(i);
                if (target == null || target.isDestroyed() || target instanceof SecurityConsole) continue;
                RayHit hit = new RayHit();
                castTarget(target, origin, direction, stopDistance, hit);
                if (hit.isHit() && hit.distance < stopDistance) {
                    hit.point.set(direction).scl(hit.distance).add(origin);
                    hits.add(hit);
                }
            }
        }
        hits.sort((a, b) -> Float.compare(a.distance, b.distance));
        return new PiercingResult(hits, terminal, stopDistance, endPoint);
    }

    /**
     * Checks whether two floor points have an unobstructed line of sight with no solid wall cells between them.
     * @param map the map
     * @param x0 the starting world x
     * @param z0 the starting world z
     * @param x1 the ending world x
     * @param z1 the ending world z
     * @return true if no solid wall cell blocks the straight segment from (x0, z0) to (x1, z1)
     */
    public static boolean hasLineOfSight(GameMap map, float x0, float z0, float x1, float z1) {
        if (map == null || !Float.isFinite(x0) || !Float.isFinite(z0)
            || !Float.isFinite(x1) || !Float.isFinite(z1)) {
            return false;
        }
        if (x0 < 0f || z0 < 0f || x0 > map.getWidth() || z0 > map.getHeight()
            || x1 < 0f || z1 < 0f || x1 > map.getWidth() || z1 > map.getHeight()) {
            return false;
        }
        if (x0 > x1 || (x0 == x1 && z0 > z1)) {
            float tx = x0;
            x0 = x1;
            x1 = tx;
            float tz = z0;
            z0 = z1;
            z1 = tz;
        }
        double dx = (double) x1 - (double) x0;
        double dz = (double) z1 - (double) z0;
        if (dx == 0.0 && dz == 0.0) {
            return isPointOpen(map, x0, z0);
        }
        if (dx == 0.0) {
            return isVerticalSegmentOpen(map, x0, Math.min(z0, z1), Math.max(z0, z1));
        }
        if (dz == 0.0) {
            return isHorizontalSegmentOpen(map, Math.min(x0, x1), Math.max(x0, x1), z0);
        }

        int stepCol = dx > 0.0 ? 1 : -1;
        int col = dx > 0.0 ? (int) Math.floor(x0) : (int) Math.ceil(x0) - 1;
        int endCol = dx > 0.0 ? (int) Math.ceil(x1) - 1 : (int) Math.floor(x1);
        int nextGridX = dx > 0.0 ? col + 1 : col;

        int stepRow = dz > 0.0 ? 1 : -1;
        int row = dz > 0.0 ? (int) Math.floor(z0) : (int) Math.ceil(z0) - 1;
        int endRow = dz > 0.0 ? (int) Math.ceil(z1) - 1 : (int) Math.floor(z1);
        int nextGridZ = dz > 0.0 ? row + 1 : row;

        if (map.isSolid(col, row)) return false;
        while (col != endCol || row != endRow) {
            double tNextX = col == endCol ? Double.POSITIVE_INFINITY : (nextGridX - (double) x0) / dx;
            double tNextZ = row == endRow ? Double.POSITIVE_INFINITY : (nextGridZ - (double) z0) / dz;
            if (Math.abs(tNextX - tNextZ) <= 1e-12) {
                if (map.isSolid(col + stepCol, row) || map.isSolid(col, row + stepRow)) {
                    return false;
                }
                col += stepCol;
                nextGridX += stepCol;
                row += stepRow;
                nextGridZ += stepRow;
            } else if (tNextX < tNextZ) {
                col += stepCol;
                nextGridX += stepCol;
            } else {
                row += stepRow;
                nextGridZ += stepRow;
            }
            if (map.isSolid(col, row)) return false;
        }
        return true;
    }

    /**
     * Checks whether two floor points have unobstructed line of sight across walls and blocking bulkhead doors.
     * @param map the map
     * @param doors bulkhead doors in the level; closed ones block sight
     * @param x0 starting world x
     * @param z0 starting world z
     * @param x1 ending world x
     * @param z1 ending world z
     * @return true if no wall or closed bulkhead door blocks the segment from (x0, z0) to (x1, z1)
     */
    public static boolean hasLineOfSight(GameMap map, List<Door> doors, float x0, float z0, float x1, float z1) {
        if (!hasLineOfSight(map, x0, z0, x1, z1)) return false;
        if (x0 > x1 || (x0 == x1 && z0 > z1)) {
            float tx = x0;
            x0 = x1;
            x1 = tx;
            float tz = z0;
            z0 = z1;
            z1 = tz;
        }
        if (doors != null) {
            for (int i = 0; i < doors.size(); i++) {
                Door door = doors.get(i);
                if (door != null && door.intersectsSegment2D(x0, z0, x1, z1)) return false;
            }
        }
        return true;
    }

    private static boolean isPointOpen(GameMap map, float x, float z) {
        int colMin = (int) Math.ceil(x) - 1;
        int colMax = (int) Math.floor(x);
        int rowMin = (int) Math.ceil(z) - 1;
        int rowMax = (int) Math.floor(z);
        for (int r = rowMin; r <= rowMax; r++) {
            for (int c = colMin; c <= colMax; c++) {
                if (!map.isSolid(c, r)) return true;
            }
        }
        return false;
    }

    private static boolean isVerticalSegmentOpen(GameMap map, float x, float zMin, float zMax) {
        int colMin = (int) Math.ceil(x) - 1;
        int colMax = (int) Math.floor(x);
        int rStart = (int) Math.floor(zMin);
        int rEnd = (int) Math.ceil(zMax) - 1;
        if (colMin == colMax) {
            for (int r = rStart; r <= rEnd; r++) {
                if (map.isSolid(colMin, r)) return false;
            }
            return true;
        }
        for (int r = rStart; r <= rEnd; r++) {
            if (map.isSolid(colMin, r) && map.isSolid(colMax, r)) return false;
            if (r < rEnd
                && (map.isSolid(colMin, r) || map.isSolid(colMin, r + 1))
                && (map.isSolid(colMax, r) || map.isSolid(colMax, r + 1))) {
                return false;
            }
        }
        return true;
    }

    private static boolean isHorizontalSegmentOpen(GameMap map, float xMin, float xMax, float z) {
        int rowMin = (int) Math.ceil(z) - 1;
        int rowMax = (int) Math.floor(z);
        int cStart = (int) Math.floor(xMin);
        int cEnd = (int) Math.ceil(xMax) - 1;
        if (rowMin == rowMax) {
            for (int c = cStart; c <= cEnd; c++) {
                if (map.isSolid(c, rowMin)) return false;
            }
            return true;
        }
        for (int c = cStart; c <= cEnd; c++) {
            if (map.isSolid(c, rowMin) && map.isSolid(c, rowMax)) return false;
            if (c < cEnd
                && (map.isSolid(c, rowMin) || map.isSolid(c + 1, rowMin))
                && (map.isSolid(c, rowMax) || map.isSolid(c + 1, rowMax))) {
                return false;
            }
        }
        return true;
    }

    private static float zeroClean(float v) {
        return v == 0f ? 0f : v;
    }

    private static void castDoor(Door door, Vector3 origin, Vector3 direction, float maxDistance,
                                 RayHit hit, float[] normalBuf) {
        float distance = door.intersectRay(origin.x, origin.y, origin.z,
            direction.x, direction.y, direction.z, maxDistance, normalBuf);
        if (distance < hit.distance) {
            hit.kind = RayHit.Kind.DOOR;
            hit.distance = distance;
            hit.door = door;
            hit.target = null;
            hit.normal.set(normalBuf[0], normalBuf[1], normalBuf[2]);
        }
    }

    /**
     * Walks the grid cell by cell along the ray (a DDA traversal) until it enters a wall cell. Because the direction
     * is a unit vector in 3D, the crossing parameters are distances along the ray itself.
     */
    private static void castGrid(GameMap map, Vector3 origin, Vector3 direction, float maxDistance, RayHit hit) {
        if (map == null || Float.isNaN(origin.x) || Float.isNaN(origin.z)
            || Float.isNaN(maxDistance) || maxDistance < 0f) {
            return;
        }
        int col = (int) Math.floor(origin.x);
        int row = (int) Math.floor(origin.z);
        if (map.isSolid(col, row)) {
            hit.kind = RayHit.Kind.WALL;
            hit.distance = 0f;
            hit.wallType = map.wallAt(col, row);
            float horizLen = (float) Math.hypot(direction.x, direction.z);
            if (horizLen > 1e-8f) {
                hit.normal.set(zeroClean(-direction.x / horizLen), 0f, zeroClean(-direction.z / horizLen));
            } else {
                hit.normal.set(0f, 0f, 1f);
            }
            return;
        }
        if (direction.x == 0f && direction.z == 0f) return;
        int stepCol = direction.x > 0f ? 1 : -1;
        int stepRow = direction.z > 0f ? 1 : -1;
        float deltaX = direction.x == 0f ? Float.POSITIVE_INFINITY : Math.abs(1f / direction.x);
        float deltaZ = direction.z == 0f ? Float.POSITIVE_INFINITY : Math.abs(1f / direction.z);
        float nextX = direction.x == 0f ? Float.POSITIVE_INFINITY
            : ((direction.x > 0f ? col + 1f : col) - origin.x) / direction.x;
        float nextZ = direction.z == 0f ? Float.POSITIVE_INFINITY
            : ((direction.z > 0f ? row + 1f : row) - origin.z) / direction.z;
        while (true) {
            float distance;
            boolean crossedX = nextX < nextZ;
            if (crossedX) {
                distance = nextX;
                nextX += deltaX;
                col += stepCol;
            } else {
                distance = nextZ;
                nextZ += deltaZ;
                row += stepRow;
            }
            if (!Float.isFinite(distance) || distance > maxDistance) return;
            if (distance < 0f) distance = 0f;
            if (map.isSolid(col, row)) {
                hit.kind = RayHit.Kind.WALL;
                hit.distance = distance == 0f ? 0f : distance;
                hit.wallType = map.wallAt(col, row);
                if (crossedX) hit.normal.set(-stepCol, 0f, 0f);
                else hit.normal.set(0f, 0f, -stepRow);
                return;
            }
        }
    }

    private static void castFloorAndCeiling(Vector3 origin, Vector3 direction, float maxDistance, RayHit hit) {
        if (direction.y == 0f) return;
        boolean down = direction.y < 0f;
        float distance = down ? -origin.y / direction.y : (GameMap.WALL_HEIGHT - origin.y) / direction.y;
        if (!Float.isFinite(distance) || distance < 0f || distance > maxDistance || distance >= hit.distance) return;
        hit.kind = down ? RayHit.Kind.FLOOR : RayHit.Kind.CEILING;
        hit.distance = distance == 0f ? 0f : distance;
        hit.door = null;
        hit.target = null;
        hit.normal.set(0f, down ? 1f : -1f, 0f);
    }

    /**
     * Intersects the ray with the target's upright cylinder: its side, or its flat top for a ray that comes down onto
     * it from above. A ray starting inside the cylinder hits it at distance 0, with the normal pointing back along the
     * ray.
     */
    private static void castTarget(Damageable target, Vector3 origin, Vector3 direction, float maxDistance,
                                   RayHit hit) {
        if (target == null || target.isDestroyed() || Float.isNaN(maxDistance) || maxDistance < 0f) return;
        float offsetX = origin.x - target.getX();
        float offsetZ = origin.z - target.getZ();
        float radius2 = target.getRadius() * target.getRadius();
        if (origin.y > target.getHeight() && direction.y < 0f) {
            // From above, the ray can only enter through the top: where it crosses the side it is still too high.
            float topDistance = (target.getHeight() - origin.y) / direction.y;
            float topX = offsetX + direction.x * topDistance;
            float topZ = offsetZ + direction.z * topDistance;
            if (topX * topX + topZ * topZ <= radius2) {
                if (!Float.isFinite(topDistance) || topDistance < 0f
                    || topDistance > maxDistance || topDistance >= hit.distance) {
                    return;
                }
                hit.kind = RayHit.Kind.TARGET;
                hit.distance = topDistance == 0f ? 0f : topDistance;
                hit.door = null;
                hit.target = target;
                hit.normal.set(0f, 1f, 0f);
                return;
            }
        }
        float c = offsetX * offsetX + offsetZ * offsetZ - radius2;
        float a = direction.x * direction.x + direction.z * direction.z;
        if (c <= 0f && origin.y >= 0f && origin.y <= target.getHeight()) {
            if (0f >= hit.distance) return;
            hit.kind = RayHit.Kind.TARGET;
            hit.distance = 0f;
            hit.door = null;
            hit.target = target;
            if (a >= 1e-8f) {
                float invHoriz = 1f / (float) Math.sqrt(a);
                hit.normal.set(zeroClean(-direction.x * invHoriz), 0f, zeroClean(-direction.z * invHoriz));
            } else {
                float len = direction.len();
                if (len > 1e-8f) {
                    hit.normal.set(zeroClean(-direction.x / len),
                        zeroClean(-direction.y / len),
                        zeroClean(-direction.z / len));
                } else {
                    hit.normal.set(0f, 1f, 0f);
                }
            }
            return;
        }
        if (a < 1e-8f) return;
        float b = offsetX * direction.x + offsetZ * direction.z;
        float discriminant = b * b - a * c;
        if (discriminant < 0f) return;
        // A ray that starts inside the cylinder hits it at once.
        float distance = c <= 0f ? 0f : (-b - (float) Math.sqrt(discriminant)) / a;
        if (!Float.isFinite(distance) || distance < 0f || distance > maxDistance || distance >= hit.distance) return;
        float y = origin.y + direction.y * distance;
        if (y < 0f || y > target.getHeight()) return;
        hit.kind = RayHit.Kind.TARGET;
        hit.distance = distance == 0f ? 0f : distance;
        hit.door = null;
        hit.target = target;
        if (distance > 0f) {
            hit.normal.set(offsetX + direction.x * distance, 0f, offsetZ + direction.z * distance).nor();
        } else {
            hit.normal.set(-direction.x, 0f, -direction.z).nor();
        }
    }
}

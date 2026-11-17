package io.github.brantunger.gamex.world;

import com.badlogic.gdx.math.Vector3;

import java.util.Collections;
import java.util.List;

/** Instant-hit ray casting against the map and the things standing in it. */
public final class Hitscan {

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
                castDoor(doors.get(i), origin, direction, maxDistance, hit, doorNormal);
            }
        }
        for (int i = 0; i < targets.size(); i++) {
            castTarget(targets.get(i), origin, direction, maxDistance, hit);
        }
        if (hit.isHit()) hit.point.set(direction).scl(hit.distance).add(origin);
        return hit;
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
        int startCol = (int) Math.floor(x0);
        int startRow = (int) Math.floor(z0);
        if (map.isSolid(startCol, startRow)) return false;
        float dx = x1 - x0;
        float dz = z1 - z0;
        float length = (float) Math.hypot(dx, dz);
        if (length < 1e-6f) return true;
        Vector3 origin = new Vector3(x0, 0.5f, z0);
        Vector3 dir = new Vector3(dx / length, 0f, dz / length);
        RayHit hit = new RayHit();
        castGrid(map, origin, dir, length - 1e-4f, hit);
        return hit.kind != RayHit.Kind.WALL;
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
        if (doors != null) {
            for (int i = 0; i < doors.size(); i++) {
                if (doors.get(i).intersectsSegment2D(x0, z0, x1, z1)) return false;
            }
        }
        return true;
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
        int col = (int) Math.floor(origin.x);
        int row = (int) Math.floor(origin.z);
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
            if (distance > maxDistance) return;
            if (map.isSolid(col, row)) {
                hit.kind = RayHit.Kind.WALL;
                hit.distance = distance;
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
        if (distance > maxDistance || distance >= hit.distance) return;
        hit.kind = down ? RayHit.Kind.FLOOR : RayHit.Kind.CEILING;
        hit.distance = distance;
        hit.normal.set(0f, down ? 1f : -1f, 0f);
    }

    /**
     * Intersects the ray with the side of the target's upright cylinder. A ray starting inside the cylinder hits it
     * at distance 0, with the normal pointing back along the ray.
     */
    private static void castTarget(Damageable target, Vector3 origin, Vector3 direction, float maxDistance,
                                   RayHit hit) {
        if (target.isDestroyed()) return;
        float offsetX = origin.x - target.getX();
        float offsetZ = origin.z - target.getZ();
        float a = direction.x * direction.x + direction.z * direction.z;
        if (a < 1e-8f) return;
        float b = offsetX * direction.x + offsetZ * direction.z;
        float c = offsetX * offsetX + offsetZ * offsetZ - target.getRadius() * target.getRadius();
        float discriminant = b * b - a * c;
        if (discriminant < 0f) return;
        // A ray that starts inside the cylinder hits it at once.
        float distance = c <= 0f ? 0f : (-b - (float) Math.sqrt(discriminant)) / a;
        if (distance < 0f || distance > maxDistance || distance >= hit.distance) return;
        float y = origin.y + direction.y * distance;
        if (y < 0f || y > target.getHeight()) return;
        hit.kind = RayHit.Kind.TARGET;
        hit.distance = distance;
        hit.door = null;
        hit.target = target;
        if (distance > 0f) {
            hit.normal.set(offsetX + direction.x * distance, 0f, offsetZ + direction.z * distance).nor();
        } else {
            hit.normal.set(-direction.x, 0f, -direction.z).nor();
        }
    }
}

package io.github.brantunger.gamex.world;

import com.badlogic.gdx.math.Vector3;

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
        RayHit hit = new RayHit();
        castGrid(map, origin, direction, maxDistance, hit);
        castFloorAndCeiling(origin, direction, maxDistance, hit);
        for (int i = 0; i < targets.size(); i++) {
            castTarget(targets.get(i), origin, direction, maxDistance, hit);
        }
        if (hit.isHit()) hit.point.set(direction).scl(hit.distance).add(origin);
        return hit;
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
        hit.target = target;
        if (distance > 0f) {
            hit.normal.set(offsetX + direction.x * distance, 0f, offsetZ + direction.z * distance).nor();
        } else {
            hit.normal.set(-direction.x, 0f, -direction.z).nor();
        }
    }
}

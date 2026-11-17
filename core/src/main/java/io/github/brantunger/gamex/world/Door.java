package io.github.brantunger.gamex.world;

/**
 * A vertical sliding bulkhead door occupying one doorway grid cell until opened.
 */
public final class Door {

    /** Half the slab width across a 1-unit doorway cell. */
    public static final float HALF_SPAN = 0.5f;
    /** Half the physical thickness of a bulkhead slab. */
    public static final float HALF_THICKNESS = 0.09f;
    /** Duration in seconds for an unlocked door to retract into the ceiling. */
    public static final float OPEN_DURATION = 0.45f;
    /** Maximum vertical lift applied to the bulkhead bottom once fully retracted. */
    public static final float OPEN_LIFT = 0.94f;
    /** Normalized open progress threshold above which characters and splash pass freely. */
    public static final float CLEAR_PROGRESS = 0.75f;
    /** Maximum center-to-player interaction reach in world units. */
    public static final float INTERACT_RADIUS = 1.65f;

    /** Locking mechanism guarding a bulkhead door. */
    public enum LockKind {
        /** Standard bulkhead operable directly with the interact key. */
        NONE,
        /** Requires the stage Amber Keycard. */
        AMBER_KEY,
        /** Requires the stage Cyan Keycard. */
        CYAN_KEY,
        /** Sealed remotely until an active Security Console overrides lockdown. */
        LOCKDOWN
    }

    /** Axis along which the door panel spans across its cell. */
    public enum Orientation {
        /** Panel spans X {@code [col, col + 1]} and blocks north-south travel across Z. */
        SPAN_X,
        /** Panel spans Z {@code [row, row + 1]} and blocks east-west travel across X. */
        SPAN_Z
    }

    private final LockKind lockKind;
    private final Orientation orientation;
    private final int col;
    private final int row;
    private final float x;
    private final float z;
    private boolean opening;
    private float openProgress;

    /**
     * Creates a closed bulkhead door centered in cell {@code (col, row)}.
     * @param lockKind lock governing whether the door opens directly, via keycard, or via console
     * @param orientation panel orientation across the doorway
     * @param col grid column index
     * @param row grid row index
     */
    public Door(LockKind lockKind, Orientation orientation, int col, int row) {
        this.lockKind = lockKind;
        this.orientation = orientation;
        this.col = col;
        this.row = row;
        this.x = col + 0.5f;
        this.z = row + 0.5f;
    }

    /** @return the lock type guarding this bulkhead. */
    public LockKind getLockKind() {
        return lockKind;
    }

    /** @return whether the bulkhead spans X or Z across its cell. */
    public Orientation getOrientation() {
        return orientation;
    }

    /** @return grid column index of the doorway cell. */
    public int getCol() {
        return col;
    }

    /** @return grid row index of the doorway cell. */
    public int getRow() {
        return row;
    }

    /** @return world X center of the bulkhead. */
    public float getX() {
        return x;
    }

    /** @return world Z center of the bulkhead. */
    public float getZ() {
        return z;
    }

    /** @return half the slab's extent along the X axis. */
    public float getHalfExtentX() {
        return orientation == Orientation.SPAN_X ? HALF_SPAN : HALF_THICKNESS;
    }

    /** @return half the slab's extent along the Z axis. */
    public float getHalfExtentZ() {
        return orientation == Orientation.SPAN_Z ? HALF_SPAN : HALF_THICKNESS;
    }

    /** @return whether this door has begun sliding open. */
    public boolean isOpening() {
        return opening;
    }

    /** @return normalized opening progress in {@code [0, 1]}. */
    public float getOpenProgress() {
        return openProgress;
    }

    /** @return whether the bulkhead has completely finished opening. */
    public boolean isOpen() {
        return openProgress >= 1f;
    }

    /** @return whether the slab currently blocks character movement, explosions, and pathfinding. */
    public boolean isBlockingPassage() {
        return openProgress < CLEAR_PROGRESS;
    }

    /** @return the live world Y height of the bottom of the bulkhead slab. */
    public float getBottomY() {
        float t = Math.max(0f, Math.min(1f, openProgress));
        return t * t * (3f - 2f * t) * OPEN_LIFT;
    }

    /**
     * Begins opening the bulkhead if it has not already started opening.
     * @return {@code true} if the door transitioned from closed to opening on this call
     */
    public boolean open() {
        if (opening) return false;
        opening = true;
        return true;
    }

    /**
     * Advances the upward retraction animation.
     * @param dt frame time in seconds
     */
    public void update(float dt) {
        if (!opening || openProgress >= 1f || dt <= 0f) return;
        openProgress = Math.min(1f, openProgress + dt / OPEN_DURATION);
    }

    /**
     * @param cx circle center X
     * @param cz circle center Z
     * @param radius circle radius
     * @return whether the XZ circle overlaps the bulkhead slab footprint while blocking passage
     */
    public boolean overlapsCircle(float cx, float cz, float radius) {
        if (!isBlockingPassage()) return false;
        float hx = getHalfExtentX();
        float hz = getHalfExtentZ();
        float nearX = Math.max(x - hx, Math.min(cx, x + hx));
        float nearZ = Math.max(z - hz, Math.min(cz, z + hz));
        float dx = cx - nearX;
        float dz = cz - nearZ;
        return dx * dx + dz * dz < radius * radius;
    }

    /**
     * @param cx sphere center X
     * @param cy sphere center Y
     * @param cz sphere center Z
     * @param radius sphere radius
     * @return whether the 3D sphere overlaps the live retracted slab volume
     */
    public boolean overlapsSphere(float cx, float cy, float cz, float radius) {
        if (isOpen()) return false;
        float hx = getHalfExtentX();
        float hz = getHalfExtentZ();
        float nearX = Math.max(x - hx, Math.min(cx, x + hx));
        float nearY = Math.max(getBottomY(), Math.min(cy, GameMap.WALL_HEIGHT));
        float nearZ = Math.max(z - hz, Math.min(cz, z + hz));
        float dx = cx - nearX;
        float dy = cy - nearY;
        float dz = cz - nearZ;
        return dx * dx + dy * dy + dz * dz < radius * radius;
    }

    /**
     * Tests whether the 2D XZ line segment from A to B intersects this closed door's slab footprint.
     * @param ax segment start X
     * @param az segment start Z
     * @param bx segment end X
     * @param bz segment end Z
     * @return {@code true} if blocked by this door
     */
    public boolean intersectsSegment2D(float ax, float az, float bx, float bz) {
        if (!isBlockingPassage()) return false;
        float hx = getHalfExtentX();
        float hz = getHalfExtentZ();
        float minX = x - hx;
        float maxX = x + hx;
        float minZ = z - hz;
        float maxZ = z + hz;
        float dx = bx - ax;
        float dz = bz - az;
        float tMin = 0f;
        float tMax = 1f;
        if (Math.abs(dx) < 1e-8f) {
            if (ax < minX || ax > maxX) return false;
        } else {
            float inv = 1f / dx;
            float t1 = (minX - ax) * inv;
            float t2 = (maxX - ax) * inv;
            if (t1 > t2) {
                float tmp = t1;
                t1 = t2;
                t2 = tmp;
            }
            tMin = Math.max(tMin, t1);
            tMax = Math.min(tMax, t2);
            if (tMin > tMax) return false;
        }
        if (Math.abs(dz) < 1e-8f) {
            if (az < minZ || az > maxZ) return false;
        } else {
            float inv = 1f / dz;
            float t1 = (minZ - az) * inv;
            float t2 = (maxZ - az) * inv;
            if (t1 > t2) {
                float tmp = t1;
                t1 = t2;
                t2 = tmp;
            }
            tMin = Math.max(tMin, t1);
            tMax = Math.min(tMax, t2);
            if (tMin > tMax) return false;
        }
        return true;
    }

    /**
     * Intersects a 3D unit ray against the live door slab volume and writes the hit distance and normal.
     * @param ox ray origin X
     * @param oy ray origin Y
     * @param oz ray origin Z
     * @param dx ray unit direction X
     * @param dy ray unit direction Y
     * @param dz ray unit direction Z
     * @param maxDistance maximum ray distance
     * @param normalOut optional 3-float array receiving the outward surface normal on hit
     * @return hit distance in {@code [0, maxDistance]}, or {@link Float#POSITIVE_INFINITY} if missed
     */
    public float intersectRay(float ox, float oy, float oz, float dx, float dy, float dz,
                              float maxDistance, float[] normalOut) {
        float minX = x - getHalfExtentX();
        float maxX = x + getHalfExtentX();
        float minY = getBottomY();
        float maxY = GameMap.WALL_HEIGHT;
        float minZ = z - getHalfExtentZ();
        float maxZ = z + getHalfExtentZ();

        float tMin = 0f;
        float tMax = maxDistance;
        float hitNx = 0f;
        float hitNy = 0f;
        float hitNz = 0f;

        // X slab
        if (Math.abs(dx) < 1e-8f) {
            if (ox < minX || ox > maxX) return Float.POSITIVE_INFINITY;
        } else {
            float inv = 1f / dx;
            float t1 = (minX - ox) * inv;
            float t2 = (maxX - ox) * inv;
            float nx = -Math.signum(dx);
            if (t1 > t2) {
                float tmp = t1;
                t1 = t2;
                t2 = tmp;
                nx = -nx;
            }
            if (t1 > tMin) {
                tMin = t1;
                hitNx = nx;
                hitNy = 0f;
                hitNz = 0f;
            }
            tMax = Math.min(tMax, t2);
            if (tMin > tMax) return Float.POSITIVE_INFINITY;
        }

        // Y slab
        if (Math.abs(dy) < 1e-8f) {
            if (oy < minY || oy > maxY) return Float.POSITIVE_INFINITY;
        } else {
            float inv = 1f / dy;
            float t1 = (minY - oy) * inv;
            float t2 = (maxY - oy) * inv;
            float ny = -Math.signum(dy);
            if (t1 > t2) {
                float tmp = t1;
                t1 = t2;
                t2 = tmp;
                ny = -ny;
            }
            if (t1 > tMin) {
                tMin = t1;
                hitNx = 0f;
                hitNy = ny;
                hitNz = 0f;
            }
            tMax = Math.min(tMax, t2);
            if (tMin > tMax) return Float.POSITIVE_INFINITY;
        }

        // Z slab
        if (Math.abs(dz) < 1e-8f) {
            if (oz < minZ || oz > maxZ) return Float.POSITIVE_INFINITY;
        } else {
            float inv = 1f / dz;
            float t1 = (minZ - oz) * inv;
            float t2 = (maxZ - oz) * inv;
            float nz = -Math.signum(dz);
            if (t1 > t2) {
                float tmp = t1;
                t1 = t2;
                t2 = tmp;
                nz = -nz;
            }
            if (t1 > tMin) {
                tMin = t1;
                hitNx = 0f;
                hitNy = 0f;
                hitNz = nz;
            }
            tMax = Math.min(tMax, t2);
            if (tMin > tMax) return Float.POSITIVE_INFINITY;
        }

        if (tMin <= 0f) return Float.POSITIVE_INFINITY;
        if (normalOut != null) {
            normalOut[0] = hitNx;
            normalOut[1] = hitNy;
            normalOut[2] = hitNz;
        }
        return tMin;
    }
}

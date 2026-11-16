package io.github.brantunger.gamex.world;

import com.badlogic.gdx.math.Vector3;

/** The result of a {@link Hitscan} ray. */
public final class RayHit {

    /** What a ray ended on. */
    public enum Kind {
        /** Nothing within the ray's range. */
        NONE,
        /** A wall face. */
        WALL,
        /** The floor. */
        FLOOR,
        /** The ceiling. */
        CEILING,
        /** A {@link Damageable}. */
        TARGET
    }

    /** What the ray ended on. */
    public Kind kind = Kind.NONE;
    /** The distance from the ray's origin to the hit point. */
    public float distance = Float.POSITIVE_INFINITY;
    /** The hit point in world coordinates. */
    public final Vector3 point = new Vector3();
    /** The unit normal of the surface that was hit, pointing back towards the open side. */
    public final Vector3 normal = new Vector3();
    /** The wall type that was hit, when {@link #kind} is {@link Kind#WALL}. */
    public char wallType;
    /** The object that was hit, when {@link #kind} is {@link Kind#TARGET}; otherwise null. */
    public Damageable target;

    /** @return whether the ray hit anything. */
    public boolean isHit() {
        return kind != Kind.NONE;
    }
}

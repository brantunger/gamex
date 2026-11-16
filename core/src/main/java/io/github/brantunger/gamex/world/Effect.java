package io.github.brantunger.gamex.world;

import com.badlogic.gdx.math.Vector3;

/** A short-lived visual left behind by a shot or a blast. It has no effect on the simulation. */
public final class Effect {

    /** The kinds of effect. */
    public enum Type {
        /** A scorch mark with a brief spark, lying flat on the surface a pistol shot hit. */
        IMPACT(5f),
        /** A brief spark where a pistol shot hit a target. */
        SPARK(0.14f),
        /** A fireball that grows to the blast radius and fades. */
        EXPLOSION(0.45f);

        /** How long, in seconds, an effect of this type lasts. */
        public final float lifetime;

        Type(float lifetime) {
            this.lifetime = lifetime;
        }
    }

    /** The kind of effect. */
    public final Type type;
    /** The world position. */
    public final Vector3 position = new Vector3();
    /** The unit normal of the surface an {@link Type#IMPACT} lies on. */
    public final Vector3 normal = new Vector3();
    /** The world size: the radius of an explosion, or the width of a mark or spark. */
    public final float size;
    private float age;

    /**
     * @param type the kind of effect
     * @param position the world position, copied
     * @param normal the surface normal, copied
     * @param size the world size
     */
    public Effect(Type type, Vector3 position, Vector3 normal, float size) {
        this.type = type;
        this.position.set(position);
        this.normal.set(normal);
        this.size = size;
    }

    /**
     * Ages the effect.
     * @param dt the time step in seconds
     */
    public void update(float dt) {
        age += dt;
    }

    /** @return the age in seconds. */
    public float getAge() {
        return age;
    }

    /** @return how far through its life the effect is, from 0 to 1. */
    public float getProgress() {
        return Math.min(1f, age / type.lifetime);
    }

    /** @return whether the effect has run its course and can be removed. */
    public boolean isFinished() {
        return age >= type.lifetime;
    }
}

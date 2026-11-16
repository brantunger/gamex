package io.github.brantunger.gamex.world;

/**
 * Something in the world that occupies an upright cylinder and can take damage.
 * Targets implement it now; enemies can implement it later and be hit by the same weapons.
 */
public interface Damageable {

    /** @return the world x coordinate of the cylinder's axis. */
    float getX();

    /** @return the world z coordinate of the cylinder's axis. */
    float getZ();

    /** @return the cylinder's radius on the XZ plane. */
    float getRadius();

    /** @return the cylinder's height above the floor. */
    float getHeight();

    /** @return the remaining health, never below zero. */
    float getHealth();

    /** @return whether the health has reached zero. */
    boolean isDestroyed();

    /**
     * Takes damage. A destroyed object ignores further damage.
     * @param amount the damage to apply; values at or below zero do nothing
     */
    void damage(float amount);
}

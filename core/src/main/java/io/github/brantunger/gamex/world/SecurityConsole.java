package io.github.brantunger.gamex.world;

/**
 * An indestructible security terminal pedestal that overrides lockdown bulkheads when activated.
 */
public final class SecurityConsole implements Damageable {

    /** Cylinder collision radius of the console pedestal on the XZ plane. */
    public static final float RADIUS = 0.22f;
    /** Vertical height of the console housing above the floor. */
    public static final float HEIGHT = 0.72f;
    /** Maximum center-to-player interaction reach in world units. */
    public static final float INTERACT_RADIUS = 1.65f;

    private final float x;
    private final float z;
    private boolean activated;

    /**
     * Creates an unactivated security console centered at {@code (x, z)}.
     * @param x world X coordinate
     * @param z world Z coordinate
     */
    public SecurityConsole(float x, float z) {
        this.x = x;
        this.z = z;
    }

    @Override
    public float getX() {
        return x;
    }

    @Override
    public float getZ() {
        return z;
    }

    @Override
    public float getRadius() {
        return RADIUS;
    }

    @Override
    public float getHeight() {
        return HEIGHT;
    }

    @Override
    public float getHealth() {
        return 100f;
    }

    @Override
    public boolean isDestroyed() {
        return false;
    }

    @Override
    public void damage(float amount) {
        // Security consoles act as permanent indestructible cover.
    }

    /** @return whether this console has already overridden lockdown. */
    public boolean isActivated() {
        return activated;
    }

    /**
     * Marks this console as activated.
     * @return {@code true} if the console switched from locked to activated on this call
     */
    public boolean activate() {
        if (activated) return false;
        activated = true;
        return true;
    }
}

package io.github.brantunger.gamex.world;

/** A destructible practice target standing on the floor, drawn as a billboard sprite. */
public final class Target implements Damageable {

    /** The kinds of target, each with its own size and starting health. */
    public enum Kind {
        /** A small coolant canister that a few pistol shots break. */
        CANISTER(50f, 0.2f, 0.55f),
        /** A sturdier ring board on a post. */
        RING_BOARD(120f, 0.24f, 0.8f);

        /** The health a new target of this kind starts with. */
        public final float maxHealth;
        /** The collision radius on the XZ plane. */
        public final float radius;
        /** The height above the floor. */
        public final float height;

        Kind(float maxHealth, float radius, float height) {
            this.maxHealth = maxHealth;
            this.radius = radius;
            this.height = height;
        }
    }

    /** How long, in seconds, a target shows its hit flash after taking damage. */
    public static final float HIT_FLASH_TIME = 0.15f;

    private final Kind kind;
    private final float x;
    private final float z;
    private float health;
    private float hitFlash;

    /**
     * @param kind the kind, which sets the size and starting health
     * @param x the world x coordinate
     * @param z the world z coordinate
     */
    public Target(Kind kind, float x, float z) {
        this.kind = kind;
        this.x = x;
        this.z = z;
        this.health = kind.maxHealth;
    }

    /** @return the kind of this target. */
    public Kind getKind() {
        return kind;
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
        return kind.radius;
    }

    @Override
    public float getHeight() {
        return kind.height;
    }

    @Override
    public float getHealth() {
        return health;
    }

    @Override
    public boolean isDestroyed() {
        return health <= 0f;
    }

    @Override
    public void damage(float amount) {
        if (amount <= 0f || isDestroyed()) return;
        health = Math.max(0f, health - amount);
        hitFlash = HIT_FLASH_TIME;
    }

    /** @return the hit flash strength, from 1 right after a hit down to 0. */
    public float getHitFlash() {
        return hitFlash / HIT_FLASH_TIME;
    }

    /**
     * Advances the hit flash timer.
     * @param dt the time step in seconds
     */
    public void update(float dt) {
        hitFlash = Math.max(0f, hitFlash - dt);
    }
}

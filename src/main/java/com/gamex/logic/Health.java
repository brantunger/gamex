package com.gamex.logic;

/** Hit points of an entity, kept between zero and a fixed maximum. Pure Java: no Godot types. */
public final class Health {

    private final int max;
    private int current;

    public Health(int max) {
        if (max <= 0) {
            throw new IllegalArgumentException("max must be positive, got " + max);
        }
        this.max = max;
        this.current = max;
    }

    public int current() {
        return current;
    }

    public int max() {
        return max;
    }

    public boolean isDead() {
        return current == 0;
    }

    /** Removes {@code amount} hit points, stopping at zero. */
    public void damage(int amount) {
        requireNonNegative(amount);
        current = Math.max(0, current - amount);
    }

    /** Restores {@code amount} hit points, stopping at the maximum. The dead stay dead. */
    public void heal(int amount) {
        requireNonNegative(amount);
        if (!isDead()) {
            current = Math.min(max, current + amount);
        }
    }

    private static void requireNonNegative(int amount) {
        if (amount < 0) {
            throw new IllegalArgumentException("amount must not be negative, got " + amount);
        }
    }
}

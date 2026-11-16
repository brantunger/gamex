package io.github.brantunger.gamex.weapon;

/** The energy that the charged blast draws from. It refills over time. */
public final class EnergyMeter {

    /** The capacity of the player's meter. */
    public static final float DEFAULT_CAPACITY = 100f;
    /** The refill rate of the player's meter, in energy per second. */
    public static final float DEFAULT_REGEN = 14f;

    private final float capacity;
    private final float regenPerSecond;
    private float energy;

    /** Creates a full meter with the default capacity and refill rate. */
    public EnergyMeter() {
        this(DEFAULT_CAPACITY, DEFAULT_REGEN);
    }

    /**
     * Creates a full meter.
     * @param capacity the most energy the meter holds
     * @param regenPerSecond the refill rate in energy per second
     */
    public EnergyMeter(float capacity, float regenPerSecond) {
        this.capacity = capacity;
        this.regenPerSecond = regenPerSecond;
        this.energy = capacity;
    }

    /** @return the energy available now. */
    public float getEnergy() {
        return energy;
    }

    /** @return the most energy the meter holds. */
    public float getCapacity() {
        return capacity;
    }

    /** @return how full the meter is, from 0 to 1. */
    public float getFraction() {
        return energy / capacity;
    }

    /**
     * @param amount an energy cost
     * @return whether the meter holds at least that much
     */
    public boolean canSpend(float amount) {
        return energy >= amount;
    }

    /**
     * Takes energy out of the meter, or leaves it untouched if it holds too little.
     * @param amount the energy to take
     * @return whether the energy was taken
     */
    public boolean spend(float amount) {
        if (!canSpend(amount)) return false;
        energy -= amount;
        return true;
    }

    /**
     * Refills the meter, up to its capacity.
     * @param dt the time step in seconds
     */
    public void regenerate(float dt) {
        energy = Math.min(capacity, energy + regenPerSecond * dt);
    }

    /**
     * Adds energy immediately, clamped to the capacity. Non-positive amounts do nothing.
     * @param amount the energy to add
     * @return the energy actually added
     */
    public float add(float amount) {
        if (amount <= 0f || energy >= capacity) return 0f;
        float before = energy;
        energy = Math.min(capacity, energy + amount);
        return energy - before;
    }

    /**
     * Sets the current energy directly, clamped between 0 and the capacity.
     * @param value the desired energy
     */
    public void setEnergy(float value) {
        energy = Math.max(0f, Math.min(capacity, value));
    }
}

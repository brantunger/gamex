package io.github.brantunger.gamex.weapon;

/**
 * The charge-and-release logic of the blast. Holding the button builds a charge from 0 to 1; releasing it fires if
 * the charge has reached {@link #MIN_CHARGE}, and otherwise the charge fizzles at no cost. A bigger charge costs more
 * energy, and the charge stops growing at what the meter can pay for. Charging cannot start while the meter holds
 * less than the cost of the smallest blast. The charger refills the meter while the button is up or charging is
 * refused, and never while a charge builds; when another weapon is out, the game refills the meter itself.
 */
public final class BlastCharger {

    /** The time, in seconds, that a full charge takes. */
    public static final float FULL_CHARGE_TIME = 1.2f;
    /** The smallest charge that fires. */
    public static final float MIN_CHARGE = 0.25f;
    /** The energy cost of a blast at {@link #MIN_CHARGE}. */
    public static final float MIN_COST = 20f;
    /** The energy cost of a blast at full charge. */
    public static final float MAX_COST = 60f;

    private final EnergyMeter energy;
    private boolean charging;
    private boolean refused;
    private boolean suppressUntilRelease;
    private float charge;

    /** @param energy the meter that pays for the blasts */
    public BlastCharger(EnergyMeter energy) {
        this.energy = energy;
    }

    /**
     * @param charge a charge level from {@link #MIN_CHARGE} to 1
     * @return the energy a blast of that charge costs, growing in a straight line with the charge
     */
    public static float costFor(float charge) {
        float t = Math.max(0f, Math.min(1f, (charge - MIN_CHARGE) / (1f - MIN_CHARGE)));
        return MIN_COST + (MAX_COST - MIN_COST) * t;
    }

    /** @return the biggest charge the meter can pay for now, or 0 when it cannot pay for the smallest blast. */
    public float affordableCharge() {
        if (!energy.canSpend(MIN_COST)) return 0f;
        float t = (energy.getEnergy() - MIN_COST) / (MAX_COST - MIN_COST);
        return MIN_CHARGE + (1f - MIN_CHARGE) * Math.max(0f, Math.min(1f, t));
    }

    /**
     * Advances the charge by one step.
     * @param dt the time step in seconds
     * @param held whether the charge button is held
     * @return the charge of the blast fired during this step, or 0 if none was fired
     */
    public float update(float dt, boolean held) {
        float stepDt = Float.isFinite(dt) && dt > 0f ? dt : 0f;
        refused = false;
        if (held) {
            if (charging && !energy.canSpend(MIN_COST)) {
                charging = false;
                charge = 0f;
                suppressUntilRelease = true;
            }
            if (suppressUntilRelease) {
                refused = !energy.canSpend(MIN_COST);
                energy.regenerate(stepDt);
                return 0f;
            }
            if (!charging) {
                if (!energy.canSpend(MIN_COST)) {
                    refused = true;
                    energy.regenerate(stepDt);
                    return 0f;
                }
                charging = true;
                charge = 0f;
            }
            charge = Math.min(charge + stepDt / FULL_CHARGE_TIME, affordableCharge());
            return 0f;
        }
        suppressUntilRelease = false;
        if (charging) {
            float released = Math.min(charge, affordableCharge());
            charging = false;
            charge = 0f;
            if (released < MIN_CHARGE || !energy.canSpend(MIN_COST)) return 0f;
            // The charge was capped at what the meter can pay; the min() only absorbs float rounding.
            energy.spend(Math.min(costFor(released), energy.getEnergy()));
            return released;
        }
        energy.regenerate(stepDt);
        return 0f;
    }

    /**
     * Drops a charge that is building, at no cost and without firing. Used when the game is paused, so that letting
     * go of the button during the pause does not fire on return.
     */
    public void cancel() {
        charging = false;
        refused = false;
        suppressUntilRelease = false;
        charge = 0f;
    }

    /** @return whether a charge is building. */
    public boolean isCharging() {
        return charging;
    }

    /** @return whether the button is held but the meter holds too little energy to start a charge. */
    public boolean isRefused() {
        return refused;
    }

    /** @return the charge built so far, from 0 to 1. */
    public float getCharge() {
        return charge;
    }

    /** @return whether the charge built so far would fire on release. */
    public boolean isReadyToFire() {
        return charging && charge >= MIN_CHARGE && energy.canSpend(MIN_COST);
    }
}

package io.github.brantunger.gamex.weapon;

/** The pistol's trigger timing. It has unlimited ammunition; its only limit is the fire rate. */
public final class Pistol {

    private final WeaponTuning tuning;
    private float cooldown;
    private float sinceShot = Float.POSITIVE_INFINITY;

    /** @param tuning the weapon balance; its pistol values set the fire rate and the flash */
    public Pistol(WeaponTuning tuning) {
        if (tuning == null) throw new IllegalArgumentException("Weapon tuning must not be null");
        this.tuning = tuning;
    }

    /**
     * Advances the timers and fires if the trigger is held and the pistol is ready. With the trigger held, the pistol
     * fires once per {@link WeaponTuning#pistolFireInterval} on average whatever the length of the steps, and at most
     * once per step.
     * @param dt the time step in seconds
     * @param trigger whether the trigger is held
     * @return whether a shot was fired during this step
     */
    public boolean update(float dt, boolean trigger) {
        if (!Float.isFinite(dt) || dt < 0f) return false;
        boolean wasReady = cooldown <= 0f;
        cooldown -= dt;
        sinceShot += dt;
        if (!trigger) {
            // An idle pistol is ready and no more: time spent idle does not bank extra shots.
            cooldown = Math.max(0f, cooldown);
            return false;
        }
        if (cooldown > 0f) return false;
        // Under held fire, the time by which this step overshot the interval counts towards the next shot, so the
        // rate does not depend on the frame rate. A pistol that was already ready starts a fresh interval.
        cooldown = wasReady ? tuning.pistolFireInterval : cooldown + tuning.pistolFireInterval;
        sinceShot = 0f;
        return true;
    }

    /** @return whether the muzzle flash is showing. */
    public boolean isFlashVisible() {
        return sinceShot < tuning.pistolFlashTime;
    }
}

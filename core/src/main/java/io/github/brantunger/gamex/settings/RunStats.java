package io.github.brantunger.gamex.settings;

/** What the player did in one finished stage run, as handed to the save data and shown on the tally. */
public final class RunStats {

    /** Clear time in seconds. */
    public final float clearTime;
    /** Hostiles destroyed, or targets on a stage that has no hostiles. */
    public final int destroyed;
    /** Hostiles on the stage, or targets on a stage that has no hostiles. */
    public final int totalTargets;
    /** Health and armor lost. */
    public final float damageTaken;
    /** Pickups collected. */
    public final int pickupsCollected;
    /** Pickups on the stage. */
    public final int pickupsTotal;
    /** Trigger pulls: every pistol shot, charged blast, Scattergun blast and Rail Lance beam counts once. */
    public final int shotsFired;
    /** Trigger pulls that damaged at least one hostile, target or canister. */
    public final int shotsHit;

    /**
     * @param clearTime clear time in seconds
     * @param destroyed hostiles (or targets) destroyed
     * @param totalTargets hostiles (or targets) on the stage
     * @param damageTaken health and armor lost
     * @param pickupsCollected pickups collected
     * @param pickupsTotal pickups on the stage
     * @param shotsFired trigger pulls
     * @param shotsHit trigger pulls that damaged something; kept between 0 and {@code shotsFired}
     */
    public RunStats(float clearTime, int destroyed, int totalTargets, float damageTaken, int pickupsCollected,
                    int pickupsTotal, int shotsFired, int shotsHit) {
        this.clearTime = clearTime;
        this.destroyed = destroyed;
        this.totalTargets = totalTargets;
        this.damageTaken = damageTaken;
        this.pickupsCollected = pickupsCollected;
        this.pickupsTotal = pickupsTotal;
        this.shotsFired = Math.max(0, shotsFired);
        this.shotsHit = Math.max(0, Math.min(this.shotsFired, shotsHit));
    }

    /**
     * @param shotsFired trigger pulls
     * @param shotsHit trigger pulls that damaged something
     * @return the share of shots that hit in whole percent, rounded down, or -1 when no shot was fired
     */
    public static int accuracyPercent(int shotsFired, int shotsHit) {
        if (shotsFired <= 0) return -1;
        return (int) (Math.max(0, Math.min(shotsFired, shotsHit)) * 100L / shotsFired);
    }
}

package io.github.brantunger.gamex.settings;

import java.util.Collections;
import java.util.EnumSet;
import java.util.Set;

/**
 * Feats a stage clear can earn besides its letter rank. Each is saved per stage and difficulty, and once earned it
 * is kept.
 */
public enum Medal {
    /** Cleared without losing any health or armor. */
    UNTOUCHED("UNTOUCHED"),
    /** Cleared with every hostile on the stage destroyed. */
    SWEEP("SWEEP"),
    /** Cleared in less than the stage's par time. */
    UNDER_PAR("UNDER PAR"),
    /** Cleared with at least {@link #SHARPSHOOTER_PERCENT} percent of the shots fired hitting. */
    SHARPSHOOTER("SHARPSHOOTER");

    /** Lowest accuracy, in percent of shots that hit, that earns {@link #SHARPSHOOTER}. */
    public static final int SHARPSHOOTER_PERCENT = 60;

    /** Display label for menus and the tally. */
    public final String label;

    Medal(String label) {
        this.label = label;
    }

    /**
     * @param stats the finished run
     * @param parSeconds the stage's par time in seconds
     * @return the medals that run earns
     */
    public static Set<Medal> earnedBy(RunStats stats, float parSeconds) {
        EnumSet<Medal> earned = EnumSet.noneOf(Medal.class);
        if (stats.damageTaken <= 0f) earned.add(UNTOUCHED);
        if (stats.totalTargets > 0 && stats.destroyed >= stats.totalTargets) earned.add(SWEEP);
        if (stats.clearTime < parSeconds) earned.add(UNDER_PAR);
        // Whole numbers, so a run just short of the mark is never rounded up to it.
        if (stats.shotsFired > 0 && stats.shotsHit * 100L >= stats.shotsFired * (long) SHARPSHOOTER_PERCENT) {
            earned.add(SHARPSHOOTER);
        }
        return Collections.unmodifiableSet(earned);
    }

    /** @return the medals as one bit each, for saving. */
    static int toBits(Set<Medal> medals) {
        int bits = 0;
        for (Medal medal : medals) bits |= 1 << medal.ordinal();
        return bits;
    }

    /** @return the medals whose bits are set; bits that belong to no medal are ignored. */
    static Set<Medal> fromBits(int bits) {
        EnumSet<Medal> medals = EnumSet.noneOf(Medal.class);
        for (Medal medal : values()) {
            if ((bits & (1 << medal.ordinal())) != 0) medals.add(medal);
        }
        return Collections.unmodifiableSet(medals);
    }
}

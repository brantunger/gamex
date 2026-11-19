package io.github.brantunger.gamex.player;

/**
 * Campaign difficulty tiers. What each one multiplies (incoming player damage, pickup amounts, enemy cadence) is in
 * {@link DifficultyTuning}, read from {@code assets/data/difficulty.txt}. The names stay here: saved games store
 * them.
 */
public enum Difficulty {
    EASY("EASY"),
    NORMAL("NORMAL"),
    HARD("HARD");

    /** Display label for menus and HUD. */
    public final String label;

    Difficulty(String label) {
        this.label = label;
    }
}

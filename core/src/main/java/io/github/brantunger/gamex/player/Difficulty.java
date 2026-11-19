package io.github.brantunger.gamex.player;

/**
 * Campaign difficulty tiers. What each one multiplies (incoming player damage, pickup amounts, enemy cadence) is in
 * {@link DifficultyTuning}, read from {@code assets/data/difficulty.txt}. The names stay here: saved games store
 * them. The label the menus and the HUD show for each is in {@code assets/data/strings.txt}.
 */
public enum Difficulty {
    EASY,
    NORMAL,
    HARD
}

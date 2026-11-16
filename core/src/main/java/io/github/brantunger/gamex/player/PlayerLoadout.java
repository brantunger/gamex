package io.github.brantunger.gamex.player;

/**
 * Immutable snapshot of the player's carried-over state on entering a level. Retrying after death restores this exact
 * state rather than resetting to a blank slate.
 */
public final class PlayerLoadout {

    /** Starting health on entering the level, within (0, {@link Player#MAX_HEALTH}]. */
    public final float health;
    /** Starting armor on entering the level, within [0, {@link Player#MAX_ARMOR}]. */
    public final float armor;
    /** Active campaign difficulty. */
    public final Difficulty difficulty;

    /**
     * @param health starting health
     * @param armor starting armor
     * @param difficulty difficulty tier
     */
    public PlayerLoadout(float health, float armor, Difficulty difficulty) {
        this.health = Math.max(1f, Math.min(Player.MAX_HEALTH, health));
        this.armor = Math.max(0f, Math.min(Player.MAX_ARMOR, armor));
        this.difficulty = difficulty == null ? Difficulty.NORMAL : difficulty;
    }

    /** @return a fresh baseline loadout (100 health, 0 armor) on {@link Difficulty#NORMAL}. */
    public static PlayerLoadout fresh() {
        return fresh(Difficulty.NORMAL);
    }

    /**
     * @param difficulty the difficulty tier
     * @return a fresh baseline loadout (100 health, 0 armor) for the start of a new run
     */
    public static PlayerLoadout fresh(Difficulty difficulty) {
        return new PlayerLoadout(Player.MAX_HEALTH, 0f, difficulty);
    }

    /**
     * Captures the surviving player's state at the end of a cleared level for carry-over into the next stage.
     * @param player the surviving player
     * @param difficulty the active difficulty
     * @return the snapshot for the next level
     */
    public static PlayerLoadout fromPlayer(Player player, Difficulty difficulty) {
        return new PlayerLoadout(player.getHealth(), player.getArmor(), difficulty);
    }
}

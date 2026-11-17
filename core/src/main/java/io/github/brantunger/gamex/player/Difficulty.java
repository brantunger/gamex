package io.github.brantunger.gamex.player;

/**
 * Campaign difficulty tiers, scaling incoming player damage and pickup restoration amounts.
 */
public enum Difficulty {
    EASY("EASY", 0.7f, 1.25f, 0.85f),
    NORMAL("NORMAL", 1.0f, 1.0f, 1.0f),
    HARD("HARD", 1.4f, 0.8f, 1.2f);

    /** Display label for menus and HUD. */
    public final String label;
    /** Multiplier applied to incoming damage on the player. */
    public final float damageTakenScale;
    /** Multiplier applied to health, armor and energy pickups. */
    public final float pickupScale;
    /** Multiplier applied to enemy attack wind-up cadence and projectile speeds. */
    public final float enemySpeedScale;

    Difficulty(String label, float damageTakenScale, float pickupScale, float enemySpeedScale) {
        this.label = label;
        this.damageTakenScale = damageTakenScale;
        this.pickupScale = pickupScale;
        this.enemySpeedScale = enemySpeedScale;
    }
}

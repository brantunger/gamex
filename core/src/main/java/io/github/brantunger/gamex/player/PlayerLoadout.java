package io.github.brantunger.gamex.player;

import io.github.brantunger.gamex.weapon.Scattergun;
import io.github.brantunger.gamex.weapon.WeaponSlot;

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
    /** Whether the player starts the stage holding the Combat Scattergun. */
    public final boolean hasScattergun;
    /** Starting shotgun shells carried in the shell pool, within [0, {@link Scattergun#MAX_SHELLS}]. */
    public final int shells;
    /** Weapon equipped when entering the stage. */
    public final WeaponSlot activeWeapon;

    /**
     * @param health starting health
     * @param armor starting armor
     * @param difficulty difficulty tier
     */
    public PlayerLoadout(float health, float armor, Difficulty difficulty) {
        this(health, armor, difficulty, false, 0, WeaponSlot.PISTOL);
    }

    /**
     * @param health starting health
     * @param armor starting armor
     * @param difficulty difficulty tier
     * @param hasScattergun whether the Scattergun has been acquired
     * @param shells carried shotgun shells
     * @param activeWeapon currently equipped weapon slot
     */
    public PlayerLoadout(float health, float armor, Difficulty difficulty,
                         boolean hasScattergun, int shells, WeaponSlot activeWeapon) {
        this.health = Math.max(1f, Math.min(Player.MAX_HEALTH, health));
        this.armor = Math.max(0f, Math.min(Player.MAX_ARMOR, armor));
        this.difficulty = difficulty == null ? Difficulty.NORMAL : difficulty;
        this.hasScattergun = hasScattergun;
        this.shells = Math.max(0, Math.min(Scattergun.MAX_SHELLS, shells));
        WeaponSlot slot = activeWeapon == null ? WeaponSlot.PISTOL : activeWeapon;
        if (slot == WeaponSlot.SCATTERGUN && (!this.hasScattergun || this.shells <= 0)) {
            slot = WeaponSlot.PISTOL;
        }
        this.activeWeapon = slot;
    }

    /** @return a fresh baseline loadout (100 health, 0 armor, pistol only) on {@link Difficulty#NORMAL}. */
    public static PlayerLoadout fresh() {
        return fresh(Difficulty.NORMAL);
    }

    /**
     * @param difficulty the difficulty tier
     * @return a fresh baseline loadout (100 health, 0 armor, pistol only) for the start of a new run
     */
    public static PlayerLoadout fresh(Difficulty difficulty) {
        return new PlayerLoadout(Player.MAX_HEALTH, 0f, difficulty, false, 0, WeaponSlot.PISTOL);
    }

    /**
     * Builds the default entry loadout when selecting a campaign stage directly from {@code SELECT STAGE}. Stage 01
     * begins with the Pistol only (unlocking the Scattergun on the stage); Stages 02+ start with the Scattergun and
     * difficulty-scaled starter shells.
     * @param stageIndex zero-based campaign stage index
     * @param difficulty difficulty tier
     * @return starter loadout for direct deployment to {@code stageIndex}
     */
    public static PlayerLoadout forStage(int stageIndex, Difficulty difficulty) {
        Difficulty safe = difficulty == null ? Difficulty.NORMAL : difficulty;
        if (stageIndex <= 0) return fresh(safe);
        int starterShells = Math.min(Scattergun.MAX_SHELLS,
            Math.max(1, Math.round(Scattergun.PICKUP_WEAPON_SHELLS * safe.pickupScale)));
        return new PlayerLoadout(Player.MAX_HEALTH, 0f, safe, true, starterShells, WeaponSlot.SCATTERGUN);
    }

    /**
     * Captures the surviving player's vitals at the end of a cleared level.
     * @param player the surviving player
     * @param difficulty the active difficulty
     * @return the snapshot for the next level
     */
    public static PlayerLoadout fromPlayer(Player player, Difficulty difficulty) {
        return fromPlayer(player, difficulty, false, 0, WeaponSlot.PISTOL);
    }

    /**
     * Captures the surviving player's full arsenal and vitals at the end of a cleared level for carry-over.
     * @param player the surviving player
     * @param difficulty the active difficulty
     * @param hasScattergun whether the player unlocked the Scattergun
     * @param shells remaining shotgun shells
     * @param activeWeapon active weapon slot at stage clear
     * @return the snapshot for the next level
     */
    public static PlayerLoadout fromPlayer(Player player, Difficulty difficulty,
                                           boolean hasScattergun, int shells, WeaponSlot activeWeapon) {
        return new PlayerLoadout(player.getHealth(), player.getArmor(), difficulty,
            hasScattergun, shells, activeWeapon);
    }
}

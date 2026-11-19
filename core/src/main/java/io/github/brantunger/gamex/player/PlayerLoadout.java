package io.github.brantunger.gamex.player;

import io.github.brantunger.gamex.weapon.RailLance;
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
    /** Whether the player starts the stage holding the Slot 3 Rail Lance. */
    public final boolean hasRailLance;
    /** Starting rail slugs carried in the slug pool, within [0, {@link RailLance#MAX_SLUGS}]. */
    public final int slugs;
    /** Weapon equipped when entering the stage. */
    public final WeaponSlot activeWeapon;

    /**
     * @param health starting health
     * @param armor starting armor
     * @param difficulty difficulty tier
     */
    public PlayerLoadout(float health, float armor, Difficulty difficulty) {
        this(health, armor, difficulty, false, 0, false, 0, WeaponSlot.PISTOL);
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
        this(health, armor, difficulty, hasScattergun, shells, false, 0, activeWeapon);
    }

    /**
     * @param health starting health
     * @param armor starting armor
     * @param difficulty difficulty tier
     * @param hasScattergun whether the Scattergun has been acquired
     * @param shells carried shotgun shells
     * @param hasRailLance whether the Rail Lance has been acquired
     * @param slugs carried rail slugs
     * @param activeWeapon currently equipped weapon slot
     */
    public PlayerLoadout(float health, float armor, Difficulty difficulty,
                         boolean hasScattergun, int shells, boolean hasRailLance, int slugs,
                         WeaponSlot activeWeapon) {
        // A NaN or infinity, as from a damaged save, would pass the clamps; it becomes full health and no armor.
        float finiteHealth = Float.isFinite(health) ? health : Player.MAX_HEALTH;
        float finiteArmor = Float.isFinite(armor) ? armor : 0f;
        this.health = Math.max(1f, Math.min(Player.MAX_HEALTH, finiteHealth));
        this.armor = Math.max(0f, Math.min(Player.MAX_ARMOR, finiteArmor));
        this.difficulty = difficulty == null ? Difficulty.NORMAL : difficulty;
        this.hasScattergun = hasScattergun;
        this.shells = Math.max(0, Math.min(Scattergun.MAX_SHELLS, shells));
        this.hasRailLance = hasRailLance;
        this.slugs = Math.max(0, Math.min(RailLance.MAX_SLUGS, slugs));
        WeaponSlot slot = activeWeapon == null ? WeaponSlot.PISTOL : activeWeapon;
        if (slot == WeaponSlot.SCATTERGUN && (!this.hasScattergun || this.shells <= 0)) {
            slot = (this.hasRailLance && this.slugs > 0) ? WeaponSlot.RAIL_LANCE : WeaponSlot.PISTOL;
        } else if (slot == WeaponSlot.RAIL_LANCE && (!this.hasRailLance || this.slugs <= 0)) {
            slot = (this.hasScattergun && this.shells > 0) ? WeaponSlot.SCATTERGUN : WeaponSlot.PISTOL;
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
        return new PlayerLoadout(Player.MAX_HEALTH, 0f, difficulty, false, 0, false, 0, WeaponSlot.PISTOL);
    }

    /**
     * Builds the default entry loadout when selecting a campaign stage directly from {@code SELECT STAGE}. Stage 01
     * begins with the Pistol only; Stages 02-03 start with the Scattergun; Stages 04+ unlock both the Scattergun and
     * the Rail Lance (with the Scattergun equipped in hand by default).
     * @param stageIndex zero-based campaign stage index
     * @param difficulty difficulty tier
     * @param tuning what each difficulty multiplies; its pickup scale sizes the starter ammunition
     * @return starter loadout for direct deployment to {@code stageIndex}
     */
    public static PlayerLoadout forStage(int stageIndex, Difficulty difficulty, DifficultyTuning tuning) {
        Difficulty safe = difficulty == null ? Difficulty.NORMAL : difficulty;
        float pickupScale = tuning.of(safe).pickupScale;
        if (stageIndex <= 0) return fresh(safe);
        int starterShells = Math.min(Scattergun.MAX_SHELLS,
            Math.max(1, Math.round(Scattergun.PICKUP_WEAPON_SHELLS * pickupScale)));
        if (stageIndex < 3) {
            return new PlayerLoadout(Player.MAX_HEALTH, 0f, safe, true, starterShells,
                false, 0, WeaponSlot.SCATTERGUN);
        }
        int starterSlugs = Math.min(RailLance.MAX_SLUGS,
            Math.max(1, Math.round(RailLance.PICKUP_WEAPON_SLUGS * pickupScale)));
        return new PlayerLoadout(Player.MAX_HEALTH, 0f, safe, true, starterShells,
            true, starterSlugs, WeaponSlot.SCATTERGUN);
    }

    /**
     * Captures the surviving player's vitals at the end of a cleared level.
     * @param player the surviving player
     * @param difficulty the active difficulty
     * @return the snapshot for the next level
     */
    public static PlayerLoadout fromPlayer(Player player, Difficulty difficulty) {
        return fromPlayer(player, difficulty, false, 0, false, 0, WeaponSlot.PISTOL);
    }

    /**
     * Captures the surviving player's arsenal and vitals at the end of a cleared level for carry-over.
     * @param player the surviving player
     * @param difficulty the active difficulty
     * @param hasScattergun whether the player unlocked the Scattergun
     * @param shells remaining shotgun shells
     * @param activeWeapon active weapon slot at stage clear
     * @return the snapshot for the next level
     */
    public static PlayerLoadout fromPlayer(Player player, Difficulty difficulty,
                                           boolean hasScattergun, int shells, WeaponSlot activeWeapon) {
        return fromPlayer(player, difficulty, hasScattergun, shells, false, 0, activeWeapon);
    }

    /**
     * Captures the surviving player's full 3-weapon arsenal and vitals at the end of a cleared level for carry-over.
     * @param player the surviving player
     * @param difficulty the active difficulty
     * @param hasScattergun whether the player unlocked the Scattergun
     * @param shells remaining shotgun shells
     * @param hasRailLance whether the player unlocked the Rail Lance
     * @param slugs remaining rail slugs
     * @param activeWeapon active weapon slot at stage clear
     * @return the snapshot for the next level
     */
    public static PlayerLoadout fromPlayer(Player player, Difficulty difficulty,
                                           boolean hasScattergun, int shells,
                                           boolean hasRailLance, int slugs, WeaponSlot activeWeapon) {
        return new PlayerLoadout(player.getHealth(), player.getArmor(), difficulty,
            hasScattergun, shells, hasRailLance, slugs, activeWeapon);
    }
}

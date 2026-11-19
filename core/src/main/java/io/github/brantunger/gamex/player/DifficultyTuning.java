package io.github.brantunger.gamex.player;

import io.github.brantunger.gamex.data.TuningFile;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

/**
 * What each {@link Difficulty} multiplies, read from {@code assets/data/difficulty.txt}. The game loads it once at
 * start-up and hands it to the classes that scale by difficulty. Instances are immutable.
 */
public final class DifficultyTuning {

    /** The file inside {@code assets/} that {@link #load()} reads. */
    public static final String PATH = "data/difficulty.txt";

    private static final String[] TIER_KEYS = {"damage_taken", "pickup", "enemy_speed"};

    /** The multipliers of one difficulty. */
    public static final class Scales {
        /** Multiplier applied to incoming damage on the player. */
        public final float damageTakenScale;
        /** Multiplier applied to health, armor, energy and ammunition pickups. */
        public final float pickupScale;
        /** Multiplier applied to enemy attack wind-up cadence and projectile speeds. */
        public final float enemySpeedScale;

        /**
         * @param damageTakenScale multiplier applied to incoming damage on the player, not negative
         * @param pickupScale multiplier applied to pickups, not negative
         * @param enemySpeedScale multiplier applied to enemy cadence and projectile speeds, above zero
         * @throws IllegalArgumentException if a multiplier is out of range or not a finite number
         */
        public Scales(float damageTakenScale, float pickupScale, float enemySpeedScale) {
            if (!(damageTakenScale >= 0f) || !Float.isFinite(damageTakenScale)) {
                throw new IllegalArgumentException("Damage taken scale must not be negative: " + damageTakenScale);
            }
            if (!(pickupScale >= 0f) || !Float.isFinite(pickupScale)) {
                throw new IllegalArgumentException("Pickup scale must not be negative: " + pickupScale);
            }
            if (!(enemySpeedScale > 0f) || !Float.isFinite(enemySpeedScale)) {
                throw new IllegalArgumentException("Enemy speed scale must be positive: " + enemySpeedScale);
            }
            this.damageTakenScale = damageTakenScale;
            this.pickupScale = pickupScale;
            this.enemySpeedScale = enemySpeedScale;
        }
    }

    private final Scales[] tiers;

    /**
     * @param easy the multipliers of {@link Difficulty#EASY}
     * @param normal the multipliers of {@link Difficulty#NORMAL}
     * @param hard the multipliers of {@link Difficulty#HARD}
     */
    public DifficultyTuning(Scales easy, Scales normal, Scales hard) {
        if (easy == null || normal == null || hard == null) {
            throw new IllegalArgumentException("Every difficulty needs its multipliers");
        }
        tiers = new Scales[Difficulty.values().length];
        tiers[Difficulty.EASY.ordinal()] = easy;
        tiers[Difficulty.NORMAL.ordinal()] = normal;
        tiers[Difficulty.HARD.ordinal()] = hard;
    }

    /**
     * Reads {@code data/difficulty.txt} from {@code assets/}.
     * @return the multipliers the game ships with, or those of the edited copy the working directory holds
     * @throws IllegalArgumentException if the file breaks the format, lacks a key, holds an unknown one or holds a
     *     value out of range; the message names the file, the key and, except for a key that is missing, the line
     */
    public static DifficultyTuning load() {
        return read(TuningFile.load(PATH, keys(), List.of()));
    }

    /**
     * Builds the multipliers from the text of a {@code difficulty.txt}, for tests.
     * @param label path label included in error messages
     * @param text the file's contents
     * @return the multipliers the text describes
     * @throws IllegalArgumentException as {@link #load()} does
     */
    public static DifficultyTuning parse(String label, String text) {
        return read(TuningFile.parseText(label, text, keys(), List.of()));
    }

    /**
     * @param difficulty a difficulty; {@code null} stands for {@link Difficulty#NORMAL}
     * @return its multipliers
     */
    public Scales of(Difficulty difficulty) {
        return tiers[(difficulty == null ? Difficulty.NORMAL : difficulty).ordinal()];
    }

    private static DifficultyTuning read(TuningFile file) {
        Scales[] read = new Scales[Difficulty.values().length];
        for (Difficulty difficulty : Difficulty.values()) {
            String prefix = prefix(difficulty);
            float damageTaken = file.getFloat(prefix + "damage_taken");
            if (damageTaken < 0f) throw file.invalid(prefix + "damage_taken", "must not be negative");
            float pickup = file.getFloat(prefix + "pickup");
            if (pickup < 0f) throw file.invalid(prefix + "pickup", "must not be negative");
            float enemySpeed = file.getFloat(prefix + "enemy_speed");
            if (enemySpeed <= 0f) throw file.invalid(prefix + "enemy_speed", "must be positive");
            read[difficulty.ordinal()] = new Scales(damageTaken, pickup, enemySpeed);
        }
        return new DifficultyTuning(read[Difficulty.EASY.ordinal()], read[Difficulty.NORMAL.ordinal()],
            read[Difficulty.HARD.ordinal()]);
    }

    /** @return the key prefix of a difficulty's block: its name in lower case and a dot. */
    private static String prefix(Difficulty difficulty) {
        return difficulty.name().toLowerCase(Locale.ROOT) + ".";
    }

    private static List<String> keys() {
        List<String> keys = new ArrayList<>();
        for (Difficulty difficulty : Difficulty.values()) {
            for (String key : TIER_KEYS) keys.add(prefix(difficulty) + key);
        }
        return keys;
    }
}

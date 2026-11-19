package io.github.brantunger.gamex;

import io.github.brantunger.gamex.data.AssetText;
import io.github.brantunger.gamex.player.Difficulty;
import io.github.brantunger.gamex.player.DifficultyTuning;
import io.github.brantunger.gamex.world.EnemyTuning;
import io.github.brantunger.gamex.world.WardenTuning;

/**
 * The tuning the game ships with, read from {@code assets/data/} once for the tests that build enemies and
 * simulations themselves, and copies of it with one value changed.
 */
public final class TestTuning {

    /** The shipped enemy balance. */
    public static final EnemyTuning ENEMIES = EnemyTuning.load();
    /** The shipped Warden fight. */
    public static final WardenTuning WARDEN = ENEMIES.warden;
    /** The shipped difficulty multipliers. */
    public static final DifficultyTuning DIFFICULTY = DifficultyTuning.load();
    public static final DifficultyTuning.Scales EASY = DIFFICULTY.of(Difficulty.EASY);
    public static final DifficultyTuning.Scales NORMAL = DIFFICULTY.of(Difficulty.NORMAL);
    public static final DifficultyTuning.Scales HARD = DIFFICULTY.of(Difficulty.HARD);

    private TestTuning() {}

    /**
     * @param key a key of {@code data/enemies.txt}
     * @param value the value to give it
     * @return the shipped enemy balance with that one value changed
     */
    public static EnemyTuning enemiesWith(String key, String value) {
        return EnemyTuning.parse(EnemyTuning.PATH, withValue(AssetText.read("data", EnemyTuning.PATH), key, value),
            WARDEN);
    }

    /**
     * @param key a key of {@code data/warden.txt}
     * @param value the value to give it
     * @return the shipped enemy balance with that one value of the Warden's fight changed
     */
    public static EnemyTuning wardenWith(String key, String value) {
        WardenTuning warden = WardenTuning.parse(WardenTuning.PATH,
            withValue(AssetText.read("data", WardenTuning.PATH), key, value));
        return EnemyTuning.parse(EnemyTuning.PATH, AssetText.read("data", EnemyTuning.PATH), warden);
    }

    /**
     * @param key a key of {@code data/difficulty.txt}
     * @param value the value to give it
     * @return the shipped difficulty multipliers with that one value changed
     */
    public static DifficultyTuning difficultyWith(String key, String value) {
        return DifficultyTuning.parse(DifficultyTuning.PATH,
            withValue(AssetText.read("data", DifficultyTuning.PATH), key, value));
    }

    /**
     * @param text the text of a tuning file
     * @param key one of its keys
     * @param value the value to give it; {@code null} removes the key's line
     * @return the text with that key's line changed
     * @throws IllegalArgumentException if the text does not hold the key on exactly one line
     */
    public static String withValue(String text, String key, String value) {
        StringBuilder out = new StringBuilder();
        int found = 0;
        for (String line : AssetText.splitLines(text)) {
            if (line.startsWith(key + ":")) {
                found++;
                if (value != null) out.append(key).append(": ").append(value).append('\n');
            } else {
                out.append(line).append('\n');
            }
        }
        if (found != 1) throw new IllegalArgumentException("Expected one line for " + key + ", found " + found);
        return out.toString();
    }
}

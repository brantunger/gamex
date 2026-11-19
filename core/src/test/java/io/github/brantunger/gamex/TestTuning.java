package io.github.brantunger.gamex;

import io.github.brantunger.gamex.data.AssetText;
import io.github.brantunger.gamex.hud.PixelFont;
import io.github.brantunger.gamex.model.ModelPalette;
import io.github.brantunger.gamex.player.Difficulty;
import io.github.brantunger.gamex.player.DifficultyTuning;
import io.github.brantunger.gamex.render.Palettes;
import io.github.brantunger.gamex.render.RenderSettings;
import io.github.brantunger.gamex.weapon.WeaponTuning;
import io.github.brantunger.gamex.world.EnemyTuning;
import io.github.brantunger.gamex.world.WardenTuning;
import io.github.brantunger.gamex.world.WorldModels;
import io.github.brantunger.gamex.world.WorldTuning;

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
    /** The shipped weapon balance. */
    public static final WeaponTuning WEAPONS = WeaponTuning.load();
    /** The shipped balance of pickups, canisters, shock grates and noise. */
    public static final WorldTuning WORLD = WorldTuning.load();
    /** The shipped HUD font. */
    public static final PixelFont FONT = PixelFont.load();
    /** The shipped colours of the world models, the weapons and the hand. */
    public static final Palettes PALETTES = Palettes.load();
    /** The shipped clip range, fog, halos and accent of each kind of door and pickup. */
    public static final RenderSettings RENDER = RenderSettings.load();

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
     * @param key a single-valued key of {@code data/weapons.txt}
     * @param value the value to give it
     * @return the shipped weapon balance with that one value changed
     */
    public static WeaponTuning weaponsWith(String key, String value) {
        return WeaponTuning.parse(WeaponTuning.PATH,
            withValue(AssetText.read("data", WeaponTuning.PATH), key, value));
    }

    /**
     * @param key a key of {@code data/world.txt}
     * @param value the value to give it
     * @return the shipped balance of pickups, canisters, shock grates and noise with that one value changed
     */
    public static WorldTuning worldWith(String key, String value) {
        return WorldTuning.parse(WorldTuning.PATH, withValue(AssetText.read("data", WorldTuning.PATH), key, value));
    }

    /**
     * @param key a key of {@code data/palette-world.txt}
     * @param value the value to give it
     * @return the shipped world palette with that one value changed
     */
    public static ModelPalette worldPaletteWith(String key, String value) {
        return WorldModels.parsePalette(WorldModels.PALETTE_PATH,
            withValue(AssetText.read("data", WorldModels.PALETTE_PATH), key, value));
    }

    /**
     * @param key a key of {@code data/render.txt}
     * @param value the value to give it
     * @return the shipped render settings with that one value changed
     */
    public static RenderSettings renderWith(String key, String value) {
        return RenderSettings.parse(RenderSettings.PATH,
            withValue(AssetText.read("data", RenderSettings.PATH), key, value));
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

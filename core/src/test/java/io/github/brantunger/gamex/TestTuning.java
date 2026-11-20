package io.github.brantunger.gamex;

import com.badlogic.gdx.Gdx;
import io.github.brantunger.gamex.audio.SoundVolumes;
import io.github.brantunger.gamex.data.AssetText;
import io.github.brantunger.gamex.hud.MenuState;
import io.github.brantunger.gamex.hud.PixelFont;
import io.github.brantunger.gamex.hud.TextValues;
import io.github.brantunger.gamex.hud.UiStrings;
import io.github.brantunger.gamex.input.InputController;
import io.github.brantunger.gamex.input.KeyBindings;
import io.github.brantunger.gamex.model.ModelPalette;
import io.github.brantunger.gamex.player.Difficulty;
import io.github.brantunger.gamex.player.DifficultyTuning;
import io.github.brantunger.gamex.player.PlayerLoadout;
import io.github.brantunger.gamex.render.Palettes;
import io.github.brantunger.gamex.render.RenderSettings;
import io.github.brantunger.gamex.settings.GameSettings;
import io.github.brantunger.gamex.settings.SaveData;
import io.github.brantunger.gamex.settings.SettingsDefaults;
import io.github.brantunger.gamex.settings.WindowDefaults;
import io.github.brantunger.gamex.weapon.HandModel;
import io.github.brantunger.gamex.weapon.PistolModel;
import io.github.brantunger.gamex.weapon.RailLanceModel;
import io.github.brantunger.gamex.weapon.ScattergunModel;
import io.github.brantunger.gamex.weapon.WeaponTuning;
import io.github.brantunger.gamex.world.EnemyTuning;
import io.github.brantunger.gamex.world.GameMap;
import io.github.brantunger.gamex.world.Levels;
import io.github.brantunger.gamex.world.WardenTuning;
import io.github.brantunger.gamex.world.WorldModels;
import io.github.brantunger.gamex.world.WorldTuning;

import java.io.IOException;
import java.io.InputStream;
import java.io.UncheckedIOException;
import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.function.Supplier;

/**
 * The tests' own tuning: a frozen copy of the data files, kept in {@code core/src/test/resources/frozen/data/}
 * (with the stage files a text test measures in {@code frozen/levels/}), and copies of it with one value changed.
 * The tests that build enemies, weapons, simulations, menus and HUD text themselves run on this copy, so the
 * numbers and the words they expect do not move when a value of {@code assets/data/} is tuned. The files the game
 * ships with are checked on their own: each loads. That each key
 * is read into its own value is checked on the {@code data-fixtures} files, where no two numbers are alike.
 * <p>
 * A key added to a file of {@code assets/data/} needs its line in the frozen copy too, and in the file the test of
 * its keys reads (its fixture, or the lines written out in its test).
 */
public final class TestTuning {

    /** Where the frozen copy lies on the test classpath; a file's path under it is its path under {@code assets/}. */
    public static final String FROZEN_DIR = "frozen";

    /** The frozen Warden fight. */
    public static final WardenTuning WARDEN =
        frozen(WardenTuning.PATH, () -> WardenTuning.parse(WardenTuning.PATH, text(WardenTuning.PATH)));
    /** The frozen enemy balance. */
    public static final EnemyTuning ENEMIES =
        frozen(EnemyTuning.PATH, () -> EnemyTuning.parse(EnemyTuning.PATH, text(EnemyTuning.PATH), WARDEN));
    /** The frozen difficulty multipliers. */
    public static final DifficultyTuning DIFFICULTY = frozen(DifficultyTuning.PATH,
        () -> DifficultyTuning.parse(DifficultyTuning.PATH, text(DifficultyTuning.PATH)));
    public static final DifficultyTuning.Scales EASY = DIFFICULTY.of(Difficulty.EASY);
    public static final DifficultyTuning.Scales NORMAL = DIFFICULTY.of(Difficulty.NORMAL);
    public static final DifficultyTuning.Scales HARD = DIFFICULTY.of(Difficulty.HARD);
    /** The frozen weapon balance. */
    public static final WeaponTuning WEAPONS =
        frozen(WeaponTuning.PATH, () -> WeaponTuning.parse(WeaponTuning.PATH, text(WeaponTuning.PATH)));
    /** The frozen balance of pickups, canisters, shock grates and noise. */
    public static final WorldTuning WORLD =
        frozen(WorldTuning.PATH, () -> WorldTuning.parse(WorldTuning.PATH, text(WorldTuning.PATH)));
    /** The frozen HUD font. */
    public static final PixelFont FONT =
        frozen(PixelFont.PATH, () -> PixelFont.parse(PixelFont.PATH, text(PixelFont.PATH)));
    /** The frozen colours of the world models, the weapons and the hand. */
    public static final Palettes PALETTES = new Palettes(
        frozen(WorldModels.PALETTE_PATH,
            () -> WorldModels.parsePalette(WorldModels.PALETTE_PATH, text(WorldModels.PALETTE_PATH))),
        frozen(PistolModel.PALETTE_PATH,
            () -> PistolModel.parsePalette(PistolModel.PALETTE_PATH, text(PistolModel.PALETTE_PATH))),
        frozen(ScattergunModel.PALETTE_PATH,
            () -> ScattergunModel.parsePalette(ScattergunModel.PALETTE_PATH, text(ScattergunModel.PALETTE_PATH))),
        frozen(RailLanceModel.PALETTE_PATH,
            () -> RailLanceModel.parsePalette(RailLanceModel.PALETTE_PATH, text(RailLanceModel.PALETTE_PATH))),
        frozen(HandModel.PALETTE_PATH,
            () -> HandModel.parsePalette(HandModel.PALETTE_PATH, text(HandModel.PALETTE_PATH))));
    /** The frozen clip range, fog, halos and accent of each kind of door and pickup. */
    public static final RenderSettings RENDER =
        frozen(RenderSettings.PATH, () -> RenderSettings.parse(RenderSettings.PATH, text(RenderSettings.PATH)));
    /** The tuning values a text may quote, from the frozen tuning. */
    public static final TextValues TEXT_VALUES = TextValues.of(WEAPONS, DIFFICULTY, WORLD);
    /** The frozen inputs of every action, which give the key names the text shows. */
    public static final KeyBindings BINDINGS =
        frozen(KeyBindings.PATH, () -> KeyBindings.parse(KeyBindings.PATH, text(KeyBindings.PATH), FONT));
    /** The frozen text of the menus and the HUD, with the key names of the frozen bindings. */
    public static final UiStrings STRINGS = frozen(UiStrings.PATH,
        () -> UiStrings.parse(UiStrings.PATH, text(UiStrings.PATH), FONT, BINDINGS::hint, TEXT_VALUES));
    /** The frozen defaults and ranges of the Settings screen. */
    public static final SettingsDefaults SETTINGS = frozen(SettingsDefaults.PATH,
        () -> SettingsDefaults.parse(SettingsDefaults.PATH, text(SettingsDefaults.PATH)));
    /** The frozen playback volume of each sound cue. */
    public static final SoundVolumes SOUNDS =
        frozen(SoundVolumes.PATH, () -> SoundVolumes.parse(SoundVolumes.PATH, text(SoundVolumes.PATH)));
    /**
     * The frozen campaign: the stage files of {@code frozen/levels/}, in the order of its {@code campaign.txt}.
     * A test that measures or quotes a stage's title, par or briefing takes its stages from here; the stages the
     * game plays are {@code Levels.campaign()}.
     */
    public static final List<Levels.Stage> CAMPAIGN = frozen(Levels.CAMPAIGN_PATH, () -> Levels.parseCampaign(
        Levels.CAMPAIGN_PATH, text(Levels.CAMPAIGN_PATH), path -> Levels.parseStage(path, text(path))));
    /** The frozen Training Range. */
    public static final Levels.Stage TRAINING_RANGE = frozen(Levels.TRAINING_RANGE_PATH,
        () -> Levels.parseStage(Levels.TRAINING_RANGE_PATH, text(Levels.TRAINING_RANGE_PATH)));
    /** The frozen size of the window. */
    public static final WindowDefaults WINDOW =
        frozen(WindowDefaults.PATH, () -> WindowDefaults.parse(WindowDefaults.PATH, text(WindowDefaults.PATH)));

    private TestTuning() {}

    /**
     * The balance a test of a design promise is played on. Such a test pairs a shipped stage, or the Warden's
     * fight, with the balance, and says something the game is meant to keep: an idle player is left alone at a
     * stage's start, the Warden runs down a player who only backpedals. It runs once on the tests' own copy, where
     * it tests the code, and once on the files the game ships with, where it tests the balance: a tuned value that
     * breaks the promise fails the test that names it.
     */
    public enum Balance {
        /** The tests' own copy of the data files. */
        FROZEN("the tests' own copy"),
        /** The files in {@code assets/data/}, loaded when the first test asks for them. */
        SHIPPED("the shipped files");

        private final String label;

        Balance(String label) {
            this.label = label;
        }

        /** @return the enemy balance, with the Warden's fight in {@code warden} */
        public EnemyTuning enemies() {
            return this == FROZEN ? ENEMIES : Shipped.ENEMIES;
        }

        /** @return the Warden's fight */
        public WardenTuning warden() {
            return enemies().warden;
        }

        /** @return the difficulty multipliers */
        public DifficultyTuning difficulty() {
            return this == FROZEN ? DIFFICULTY : Shipped.DIFFICULTY;
        }

        /** @return the weapon balance */
        public WeaponTuning weapons() {
            return this == FROZEN ? WEAPONS : Shipped.WEAPONS;
        }

        /** @return the balance of pickups, canisters, shock grates and noise */
        public WorldTuning world() {
            return this == FROZEN ? WORLD : Shipped.WORLD;
        }

        /**
         * @param map the level to play
         * @param entryLoadout starting player health, armor and difficulty snapshot
         * @param completeOnAllTargets whether clearing all targets ends the level
         * @return a simulation of that level with this balance
         */
        public GameSimulation simulation(GameMap map, PlayerLoadout entryLoadout, boolean completeOnAllTargets) {
            return new GameSimulation(map, entryLoadout, completeOnAllTargets, enemies(), difficulty(), weapons(),
                world());
        }

        @Override
        public String toString() {
            return label;
        }
    }

    /** The shipped balance, read without libGDX whatever a test before left in {@code Gdx.files}. */
    private static final class Shipped {
        static final EnemyTuning ENEMIES;
        static final DifficultyTuning DIFFICULTY;
        static final WeaponTuning WEAPONS;
        static final WorldTuning WORLD;

        static {
            com.badlogic.gdx.Files files = Gdx.files;
            Gdx.files = null;
            try {
                ENEMIES = EnemyTuning.load();
                DIFFICULTY = DifficultyTuning.load();
                WEAPONS = WeaponTuning.load();
                WORLD = WorldTuning.load();
            } finally {
                Gdx.files = files;
            }
        }
    }

    /**
     * Loads one file of the frozen copy for a field above. A copy the code no longer reads, most often because a key
     * was added to the file in {@code assets/} and not here, stops every test that uses this class; the error says
     * that it is this copy that is out of step, which the loader's own message, naming the path under
     * {@code assets/}, does not.
     * @param assetPath the path of the file under {@code assets/}
     * @param load reads the frozen copy of that file
     * @return what {@code load} read
     * @throws IllegalStateException if the frozen copy does not load
     */
    private static <T> T frozen(String assetPath, Supplier<T> load) {
        try {
            return load.get();
        } catch (IllegalArgumentException e) {
            throw new IllegalStateException("The tests' own copy of " + assetPath + ", core/src/test/resources/"
                + FROZEN_DIR + "/" + assetPath + ", does not load: " + e.getMessage()
                + ". A key added to or taken out of assets/" + assetPath + " needs the same change there.", e);
        }
    }

    /**
     * @param assetPath the path of a data file under {@code assets/}, for example {@code data/enemies.txt}
     * @return the text of the frozen copy of that file
     * @throws IllegalStateException if the frozen copy holds no such file
     */
    public static String text(String assetPath) {
        String resource = FROZEN_DIR + "/" + assetPath;
        try (InputStream in = TestTuning.class.getClassLoader().getResourceAsStream(resource)) {
            if (in == null) throw new IllegalStateException("No " + resource + " on the test classpath");
            return new String(in.readAllBytes(), StandardCharsets.UTF_8);
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
    }

    /**
     * @param map the level to play on Normal difficulty
     * @return a simulation of that level with the frozen tuning
     */
    public static GameSimulation simulation(GameMap map) {
        return simulation(map, PlayerLoadout.fresh(), false);
    }

    /**
     * @param map the level to play
     * @param entryLoadout starting player health, armor and difficulty snapshot
     * @param completeOnAllTargets whether clearing all targets ends the level
     * @return a simulation of that level with the frozen tuning
     */
    public static GameSimulation simulation(GameMap map, PlayerLoadout entryLoadout, boolean completeOnAllTargets) {
        return simulation(map, entryLoadout, completeOnAllTargets, ENEMIES, DIFFICULTY);
    }

    /**
     * @param map the level to play
     * @param entryLoadout starting player health, armor and difficulty snapshot
     * @param completeOnAllTargets whether clearing all targets ends the level
     * @param enemyTuning the enemy balance
     * @param difficultyTuning what each difficulty multiplies
     * @return a simulation of that level with that tuning and the frozen weapon and world tuning
     */
    public static GameSimulation simulation(GameMap map, PlayerLoadout entryLoadout, boolean completeOnAllTargets,
                                            EnemyTuning enemyTuning, DifficultyTuning difficultyTuning) {
        return new GameSimulation(map, entryLoadout, completeOnAllTargets, enemyTuning, difficultyTuning, WEAPONS,
            WORLD);
    }

    /**
     * @return the text the game ships with, loaded as the game loads it: with the shipped font, the key names of
     *     the shipped bindings and the values of the shipped tuning
     */
    public static UiStrings shippedStrings() {
        PixelFont font = PixelFont.load();
        return UiStrings.load(font, KeyBindings.load(font)::hint,
            TextValues.of(WeaponTuning.load(), DifficultyTuning.load(), WorldTuning.load()));
    }

    /**
     * @param weapons the weapons of the stage's {@code loadout} header, as the header lists them (for example
     *     {@code "scattergun, raillance"}), or an empty string for a stage without the header
     * @return a one-room stage of the tests' own that starts with those weapons, so that a test of the starter
     *     loadout does not go by the header of a shipped stage file
     */
    public static Levels.Stage stageStartingWith(String weapons) {
        return Levels.parseStage("levels/test-stage.map", "id: s1\ntitle: S1\npar: 30\ncomplete_on_all_targets: false\n"
            + (weapons.isEmpty() ? "" : "loadout: " + weapons + "\n") + "briefing: GO\n\n#####\n#.>.#\n#####\n");
    }

    /** @return settings that start at the frozen defaults and move within the frozen ranges */
    public static GameSettings settings() {
        return new GameSettings(SETTINGS);
    }

    /**
     * @param settings live settings model
     * @param saveData campaign and record persistence
     * @param startScreen initial screen
     * @return the menus with the frozen tuning and text
     */
    public static MenuState menu(GameSettings settings, SaveData saveData, MenuState.Screen startScreen) {
        return new MenuState(settings, saveData, startScreen, DIFFICULTY, WEAPONS, STRINGS);
    }

    /**
     * @param settings live settings model
     * @param saveData campaign and record persistence
     * @param startScreen initial screen
     * @param difficultyTuning what each difficulty multiplies
     * @param weaponTuning the weapon balance
     * @return the menus with that tuning and the frozen text, which quotes that tuning; null tuning is passed on
     *     for the menus to reject
     */
    public static MenuState menu(GameSettings settings, SaveData saveData, MenuState.Screen startScreen,
                                 DifficultyTuning difficultyTuning, WeaponTuning weaponTuning) {
        UiStrings strings = weaponTuning == null || difficultyTuning == null ? null : UiStrings.parse(UiStrings.PATH,
            text(UiStrings.PATH), FONT, BINDINGS::hint, TextValues.of(weaponTuning, difficultyTuning, WORLD));
        return new MenuState(settings, saveData, startScreen, difficultyTuning, weaponTuning, strings);
    }

    /**
     * @param menuState the menus the input drives
     * @param settings live settings model
     * @return an input controller with the frozen bindings
     */
    public static InputController input(MenuState menuState, GameSettings settings) {
        return new InputController(menuState, settings, BINDINGS);
    }

    /**
     * @param key a key of {@code data/enemies.txt}
     * @param value the value to give it
     * @return the frozen enemy balance with that one value changed
     */
    public static EnemyTuning enemiesWith(String key, String value) {
        return EnemyTuning.parse(EnemyTuning.PATH, withValue(text(EnemyTuning.PATH), key, value),
            WARDEN);
    }

    /**
     * @param key a key of {@code data/warden.txt}
     * @param value the value to give it
     * @return the frozen enemy balance with that one value of the Warden's fight changed
     */
    public static EnemyTuning wardenWith(String key, String value) {
        WardenTuning warden = WardenTuning.parse(WardenTuning.PATH,
            withValue(text(WardenTuning.PATH), key, value));
        return EnemyTuning.parse(EnemyTuning.PATH, text(EnemyTuning.PATH), warden);
    }

    /**
     * @param key a key of {@code data/difficulty.txt}
     * @param value the value to give it
     * @return the frozen difficulty multipliers with that one value changed
     */
    public static DifficultyTuning difficultyWith(String key, String value) {
        return DifficultyTuning.parse(DifficultyTuning.PATH,
            withValue(text(DifficultyTuning.PATH), key, value));
    }

    /**
     * @param key a single-valued key of {@code data/weapons.txt}
     * @param value the value to give it
     * @return the frozen weapon balance with that one value changed
     */
    public static WeaponTuning weaponsWith(String key, String value) {
        return WeaponTuning.parse(WeaponTuning.PATH,
            withValue(text(WeaponTuning.PATH), key, value));
    }

    /**
     * @param key a key of {@code data/world.txt}
     * @param value the value to give it
     * @return the frozen balance of pickups, canisters, shock grates and noise with that one value changed
     */
    public static WorldTuning worldWith(String key, String value) {
        return WorldTuning.parse(WorldTuning.PATH, withValue(text(WorldTuning.PATH), key, value));
    }

    /**
     * @param key a key of {@code data/palette-world.txt}
     * @param value the value to give it
     * @return the frozen world palette with that one value changed
     */
    public static ModelPalette worldPaletteWith(String key, String value) {
        return WorldModels.parsePalette(WorldModels.PALETTE_PATH,
            withValue(text(WorldModels.PALETTE_PATH), key, value));
    }

    /**
     * @param key a key of {@code data/render.txt}
     * @param value the value to give it
     * @return the frozen render settings with that one value changed
     */
    public static RenderSettings renderWith(String key, String value) {
        return RenderSettings.parse(RenderSettings.PATH,
            withValue(text(RenderSettings.PATH), key, value));
    }

    /**
     * @param key a key of {@code data/strings.txt}
     * @param value the value to give it
     * @return the frozen text with that one line changed
     */
    public static UiStrings stringsWith(String key, String value) {
        return UiStrings.parse(UiStrings.PATH, withValue(text(UiStrings.PATH), key, value), FONT,
            BINDINGS::hint, TEXT_VALUES);
    }

    /**
     * @param changes pairs of an action id of {@code data/bindings.txt} and the inputs to give it
     * @return the text of the frozen bindings file with those lines changed
     */
    public static String bindingsTextWith(String... changes) {
        String text = text(KeyBindings.PATH);
        for (int i = 0; i < changes.length; i += 2) text = withValue(text, changes[i], changes[i + 1]);
        return text;
    }

    /**
     * @param changes pairs of an action id of {@code data/bindings.txt} and the inputs to give it
     * @return the frozen bindings with those lines changed
     */
    public static KeyBindings bindingsWith(String... changes) {
        return KeyBindings.parse(KeyBindings.PATH, bindingsTextWith(changes), FONT);
    }

    /**
     * @param bindings the inputs of every action
     * @return the frozen text with the key names of those bindings
     */
    public static UiStrings stringsFor(KeyBindings bindings) {
        return UiStrings.parse(UiStrings.PATH, text(UiStrings.PATH), FONT, bindings::hint, TEXT_VALUES);
    }

    /**
     * @return the text of the frozen {@code data/strings.txt} with every line reworded: outside the placeholders,
     *     each letter is swapped for the one as far from the other end of the alphabet (A for Z, B for Y), so no
     *     word of the frozen text is left
     */
    public static String rewordedStringsText() {
        StringBuilder out = new StringBuilder();
        for (String line : AssetText.splitLines(text(UiStrings.PATH))) {
            int colon = line.indexOf(':');
            if (AssetText.isComment(line.trim()) || colon < 0) {
                out.append(line).append('\n');
                continue;
            }
            out.append(line, 0, colon + 1);
            boolean inPlaceholder = false;
            for (int i = colon + 1; i < line.length(); i++) {
                char c = line.charAt(i);
                if (c == '{') inPlaceholder = true;
                if (c == '}') inPlaceholder = false;
                out.append(!inPlaceholder && c >= 'A' && c <= 'Z' ? (char) ('A' + 'Z' - c) : c);
            }
            out.append('\n');
        }
        return out.toString();
    }

    /** @return the frozen text of the menus and the HUD with every line reworded by {@link #rewordedStringsText}. */
    public static UiStrings rewordedStrings() {
        return UiStrings.parse(UiStrings.PATH, rewordedStringsText(), FONT, BINDINGS::hint, TEXT_VALUES);
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

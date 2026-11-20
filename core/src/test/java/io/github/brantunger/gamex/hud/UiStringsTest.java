package io.github.brantunger.gamex.hud;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import io.github.brantunger.gamex.GameSimulation;
import io.github.brantunger.gamex.TestTuning;
import io.github.brantunger.gamex.data.AssetText;
import io.github.brantunger.gamex.hud.MenuState.OptionId;
import io.github.brantunger.gamex.input.Action;
import io.github.brantunger.gamex.player.Difficulty;
import io.github.brantunger.gamex.player.PlayerInput;
import io.github.brantunger.gamex.player.PlayerLoadout;
import io.github.brantunger.gamex.settings.GameSettings;
import io.github.brantunger.gamex.settings.MapKeyValueStore;
import io.github.brantunger.gamex.settings.Medal;
import io.github.brantunger.gamex.settings.SaveData;
import io.github.brantunger.gamex.weapon.WeaponSlot;
import io.github.brantunger.gamex.world.GameMap;
import io.github.brantunger.gamex.world.Levels;
import io.github.brantunger.gamex.world.Pickup;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Locale;

/**
 * Checks {@code assets/data/strings.txt}: that the shipped file shows the text the game held in code before the
 * text moved there, that an edited line changes what is shown and nothing else, and that a broken file stops the
 * load.
 */
class UiStringsTest {

    private static final UiStrings STRINGS = TestTuning.STRINGS;

    private static MenuState menu(UiStrings strings, SaveData save, MenuState.Screen screen) {
        return new MenuState(new GameSettings(), save, screen, TestTuning.DIFFICULTY, TestTuning.WEAPONS, strings);
    }

    private static MenuState menu(MenuState.Screen screen) {
        return menu(STRINGS, new SaveData(new MapKeyValueStore()), screen);
    }

    private static UiStrings parse(String text) {
        return UiStrings.parse(UiStrings.PATH, text, TestTuning.FONT, TestTuning.BINDINGS::hint, TestTuning.TEXT_VALUES);
    }

    private static String error(String key, String value) {
        String text = TestTuning.withValue(AssetText.read("data", UiStrings.PATH), key, value);
        return assertThrows(IllegalArgumentException.class, () -> parse(text)).getMessage();
    }

    /** @return the line of the shipped file that holds a key, from 1. */
    private static int lineOf(String key) {
        List<String> lines = AssetText.splitLines(AssetText.read("data", UiStrings.PATH));
        for (int i = 0; i < lines.size(); i++) {
            if (lines.get(i).startsWith(key + ":")) return i + 1;
        }
        throw new AssertionError("no line for " + key);
    }

    // ---- The shipped file shows what the code held ----

    @Test
    void shippedMenuOptionsAreTheLabelsTheCodeHeld() {
        SaveData saved = new SaveData(new MapKeyValueStore());
        saved.saveCampaignRun(0, PlayerLoadout.fresh());
        assertEquals(Arrays.asList("CONTINUE CAMPAIGN", "START CAMPAIGN", "SELECT STAGE", "TRAINING RANGE",
            "SETTINGS", "QUIT"), menu(STRINGS, saved, MenuState.Screen.TITLE).getOptions());
        assertEquals(Arrays.asList("START CAMPAIGN", "SELECT STAGE", "TRAINING RANGE", "SETTINGS", "QUIT"),
            menu(MenuState.Screen.TITLE).getOptions());
        assertEquals(Arrays.asList("EASY - 70% DAMAGE / +25% PICKUPS", "NORMAL - STANDARD CHALLENGE",
            "HARD - 140% DAMAGE / -20% PICKUPS", "BACK"), menu(MenuState.Screen.DIFFICULTY_SELECT).getOptions());
        assertEquals(Arrays.asList("DEPLOY", "RETURN TO TITLE"), menu(MenuState.Screen.BRIEFING).getOptions());
        assertEquals(Arrays.asList("RESUME", "RESTART STAGE", "SETTINGS", "RETURN TO TITLE", "QUIT"),
            menu(MenuState.Screen.PAUSED).getOptions());
        assertEquals(Arrays.asList("RETRY STAGE (R)", "RETURN TO TITLE"), menu(MenuState.Screen.DEAD).getOptions());

        MenuState tally = menu(MenuState.Screen.PLAYING);
        tally.onStageCleared(PlayerLoadout.fresh(), 40f, 8, 8, 0f);
        assertEquals(Arrays.asList("NEXT STAGE", "PLAY AGAIN", "RETURN TO TITLE"), tally.getOptions());

        MenuState stages = menu(MenuState.Screen.STAGE_SELECT);
        assertEquals(Arrays.asList("01: OUTER BREACH [READY]", "02: COOLANT CORE [LOCKED]"),
            stages.getOptions().subList(0, 2));
        assertEquals("BACK", stages.getOptions().get(stages.getOptions().size() - 1));
        assertEquals("PRESS Q AGAIN TO QUIT", STRINGS.get(UiText.MENU_QUIT_PROMPT));
    }

    @Test
    void shippedSettingsRowsAreTheLinesTheCodeBuilt() {
        GameSettings settings = new GameSettings();
        settings.setInvertY(true);
        MenuState menu = new MenuState(settings, new SaveData(new MapKeyValueStore()), MenuState.Screen.SETTINGS,
            TestTuning.DIFFICULTY, TestTuning.WEAPONS, STRINGS);
        // The expressions MenuState.getOptions() held before the text moved to the file.
        assertEquals(Arrays.asList(
            String.format(Locale.ROOT, "SENSITIVITY: < %.2f >", settings.getMouseSensitivity()),
            "INVERT LOOK Y: < " + (settings.isInvertY() ? "ON" : "OFF") + " >",
            "FIELD OF VIEW: < " + Math.round(settings.getFov()) + " >",
            "VIEW BOB: < " + (settings.isViewBob() ? "ON" : "OFF") + " >",
            "MASTER VOLUME: < " + Math.round(settings.getMasterVolume() * 100f) + "% >",
            "FULLSCREEN: < " + (settings.isFullscreen() ? "ON" : "OFF") + " >",
            "MINIMAP: < " + (settings.isMinimap() ? "ON" : "OFF") + " >",
            "BACK"), menu.getOptions());
        assertEquals("SENSITIVITY: < 0.12 >", menu.getOptions().get(0));
        assertEquals("INVERT LOOK Y: < ON >", menu.getOptions().get(1));
        assertEquals("FIELD OF VIEW: < 70 >", menu.getOptions().get(2));
        assertEquals("MASTER VOLUME: < 80% >", menu.getOptions().get(4));
        assertEquals("FULLSCREEN: < OFF >", menu.getOptions().get(5));
        assertEquals("MINIMAP: < ON >", menu.getOptions().get(6));
    }

    @Test
    void shippedHudCaptionsAreTheTextTheCodeHeld() {
        assertEquals("LOW ENERGY", STRINGS.get(UiText.HUD_LOW_ENERGY));
        assertEquals("CHARGE", STRINGS.get(UiText.HUD_CHARGE));
        assertEquals("ENERGY [SHIFT: DASH]", STRINGS.get(UiText.HUD_ENERGY));
        assertEquals("HP " + 100, STRINGS.format(UiText.HUD_HP, "100"));
        assertEquals("ARMOR " + 50, STRINGS.format(UiText.HUD_ARMOR, "50"));
        assertEquals("> 1: PISTOL [INF]", HudRenderer.weaponRow(true, STRINGS.get(UiText.HUD_WEAPON_PISTOL)));
        assertEquals("  1: PISTOL [INF]", HudRenderer.weaponRow(false, STRINGS.get(UiText.HUD_WEAPON_PISTOL)));
        assertEquals("> 2: SCATTERGUN [8/24]", HudRenderer.weaponRow(true,
            HudRenderer.ammoRow(STRINGS, UiText.HUD_WEAPON_SCATTERGUN, 8, 24)));
        assertEquals("  3: RAIL LANCE [5/15]", HudRenderer.weaponRow(false,
            HudRenderer.ammoRow(STRINGS, UiText.HUD_WEAPON_RAIL_LANCE, 5, 15)));
        assertEquals("  SHELLS [6/24]", HudRenderer.weaponRow(false,
            HudRenderer.ammoRow(STRINGS, UiText.HUD_AMMO_SHELLS, 6, 24)));
        assertEquals("  SLUGS [3/15]", HudRenderer.weaponRow(false,
            HudRenderer.ammoRow(STRINGS, UiText.HUD_AMMO_SLUGS, 3, 15)));
        assertEquals("[AMBER KEY]", STRINGS.get(UiText.HUD_KEY_AMBER));
        assertEquals("[CYAN KEY]", STRINGS.get(UiText.HUD_KEY_CYAN));
        assertEquals("HOSTILES 3/8", STRINGS.format(UiText.HUD_HOSTILES, "3", "8"));
        assertEquals("TARGETS 11/11", STRINGS.format(UiText.HUD_TARGETS, "11", "11"));
        assertEquals("TIME 01:42.5", STRINGS.format(UiText.HUD_TIME, TextFormat.stopwatch(102.5f)));
        assertEquals("WARDEN", STRINGS.get(UiText.HUD_BOSS));
        assertEquals("WARDEN // OVERDRIVE", STRINGS.get(UiText.HUD_BOSS_OVERDRIVE));
    }

    @Test
    void shippedScreenTextIsTheTextTheCodeHeld() {
        assertEquals("GAMEX", STRINGS.get(UiText.SCREEN_TITLE_NAME));
        assertEquals("SECTOR BREACH // TACTICAL FPS", STRINGS.get(UiText.SCREEN_TITLE_SUBTITLE));
        assertEquals("WASD: MOVE  SPACE: JUMP  SHIFT: DASH  E: USE  1-3/Q/SCROLL: WEAPON  LMB/RMB: FIRE/ALT  M: MAP",
            STRINGS.get(UiText.SCREEN_TITLE_FOOTER));
        assertEquals("SELECT STAGE", STRINGS.get(UiText.SCREEN_STAGE_SELECT_TITLE));
        assertEquals("CHOOSE ANY UNLOCKED STAGE TO DEPLOY", STRINGS.get(UiText.SCREEN_STAGE_SELECT_HINT));
        assertEquals("SELECT DIFFICULTY", STRINGS.get(UiText.SCREEN_DIFFICULTY_TITLE));
        assertEquals("DIFFICULTY: HARD   //   PAR TIME: 00:50.0",
            HudRenderer.briefingMetaLine(STRINGS, Difficulty.HARD, 50f));
        assertEquals("PAUSED", STRINGS.get(UiText.SCREEN_PAUSED_TITLE));
        assertEquals("SETTINGS", STRINGS.get(UiText.SCREEN_SETTINGS_TITLE));
        assertEquals("LEFT/RIGHT OR CLICK TO ADJUST - ESC TO SAVE", STRINGS.get(UiText.SCREEN_SETTINGS_HELP));
        assertEquals("SIGNAL LOST", STRINGS.get(UiText.SCREEN_DEAD_TITLE));
        assertEquals("SURVIVED 00:31.5   //   HOSTILES 2/8", HudRenderer.survivedLine(STRINGS, 31.5f, true, 2, 8));
        assertEquals("SURVIVED 00:31.5   //   TARGETS 4/11",
            HudRenderer.survivedLine(STRINGS, 31.5f, false, 4, 11));
        assertEquals("STAGE COMPLETE", STRINGS.get(UiText.SCREEN_COMPLETE_STAGE));
        assertEquals("CAMPAIGN COMPLETE", STRINGS.get(UiText.SCREEN_COMPLETE_CAMPAIGN));
        assertEquals("SECTOR CLEARED - ALL STAGES COMPLETE", STRINGS.get(UiText.SCREEN_COMPLETE_CAMPAIGN_NOTE));

        SaveData.CompletionSummary summary = new SaveData(new MapKeyValueStore()).recordCompletion(
            "01_outer_breach", Difficulty.NORMAL, 60f, 50f, 8, 8, 0f);
        assertEquals("RANK: [" + summary.rank + "]   BEST: [" + summary.bestRank + "]",
            HudRenderer.tallyRankLine(STRINGS, summary));
    }

    @Test
    void shippedLabelsObjectivesPromptsAndToastsAreTheTextTheCodeHeld() {
        assertEquals(Arrays.asList("EASY", "NORMAL", "HARD"), Arrays.asList(STRINGS.label(Difficulty.EASY),
            STRINGS.label(Difficulty.NORMAL), STRINGS.label(Difficulty.HARD)));
        assertEquals(Arrays.asList("UNTOUCHED", "SWEEP", "UNDER PAR", "SHARPSHOOTER"), Arrays.asList(
            STRINGS.label(Medal.UNTOUCHED), STRINGS.label(Medal.SWEEP), STRINGS.label(Medal.UNDER_PAR),
            STRINGS.label(Medal.SHARPSHOOTER)));
        String[] pickups = {"HEALTH", "ARMOR", "ENERGY", "AMBER KEYCARD", "CYAN KEYCARD", "SCATTERGUN", "SHELLS",
            "RAIL LANCE", "SLUGS"};
        for (Pickup.Kind kind : Pickup.Kind.values()) assertEquals(pickups[kind.ordinal()], STRINGS.label(kind));

        String[] objectives = {"FIND THE AMBER KEYCARD", "FIND THE CYAN KEYCARD", "OVERRIDE THE SECURITY CONSOLE",
            "DESTROY THE WARDEN", "REACH EXIT PAD", "DESTROY ALL TARGETS", "DESTROY ALL HOSTILES",
            "DESTROY TARGETS AND HOSTILES"};
        for (GameSimulation.Objective objective : GameSimulation.Objective.values()) {
            assertEquals(objectives[objective.ordinal()], STRINGS.objective(objective));
            assertEquals("OBJECTIVE: " + objectives[objective.ordinal()], STRINGS.objectiveLine(objective));
        }

        String[] prompts = {"[E] OVERRIDE LOCKDOWN", "[E] UNLOCK AMBER BULKHEAD", "[E] UNLOCK CYAN BULKHEAD",
            "[E] OPEN BULKHEAD", "NEEDS AMBER KEYCARD", "NEEDS CYAN KEYCARD", "SEALED BY SECURITY CONSOLE",
            "SEALED BY THE WARDEN"};
        for (GameSimulation.InteractionPrompt prompt : GameSimulation.InteractionPrompt.values()) {
            String text = STRINGS.prompt(prompt);
            assertEquals(prompts[prompt.ordinal()], text);
            // The colour the HUD picked by the text's first characters is the one the kind gives now.
            assertEquals(text.startsWith("[E]"), prompt.kind == GameSimulation.InteractionPrompt.Kind.USABLE, text);
        }

        String[] toasts = {"OUT OF SHELLS", "OUT OF SLUGS", "WARDEN OVERDRIVE", "LOCKDOWN OVERRIDDEN",
            "AMBER BULKHEAD UNLOCKED", "CYAN BULKHEAD UNLOCKED", "NEEDS AMBER KEYCARD", "NEEDS CYAN KEYCARD",
            "SEALED BY THE WARDEN", "SEALED BY SECURITY CONSOLE", "WARDEN DESTROYED - EXTRACTION UNSEALED"};
        for (int i = 0; i < toasts.length; i++) {
            GameSimulation.ToastId id = GameSimulation.ToastId.values()[i];
            assertEquals(toasts[i], STRINGS.toast(new GameSimulation.Toast(id, null, 0)), id.name());
        }
        assertEquals(toasts.length + 2, GameSimulation.ToastId.values().length, "the two pickup toasts come last");
        assertEquals("+25 HEALTH", STRINGS.toast(new GameSimulation.Toast(GameSimulation.ToastId.PICKUP_GAIN,
            Pickup.Kind.MEDKIT, 25)));
        assertEquals("+ AMBER KEYCARD", STRINGS.toast(new GameSimulation.Toast(GameSimulation.ToastId.PICKUP_GRANT,
            Pickup.Kind.AMBER_KEYCARD, 0)));
    }

    /** Walks the player over the pickup two cells ahead and returns the toast it raises, as the HUD words it. */
    private static String pickupToast(PlayerLoadout loadout, char pickup) {
        GameSimulation sim = new GameSimulation(GameMap.parse("#####", "#>" + pickup + ".#", "#####"), loadout,
            false);
        PlayerInput forward = new PlayerInput();
        forward.moveForward = 1f;
        while (sim.getToast() == null && sim.getTime() < 2f) sim.update(1f / 60f, forward);
        return STRINGS.toast(sim.getToast());
    }

    @Test
    void shippedPickupToastsAreTheTextThePickupBuilt() {
        PlayerLoadout hurt = new PlayerLoadout(60f, 0f, Difficulty.NORMAL);
        PlayerLoadout armed = new PlayerLoadout(100f, 0f, Difficulty.NORMAL, true, 4, true, 2, WeaponSlot.PISTOL);
        assertEquals("+25 HEALTH", pickupToast(hurt, '+'));
        assertEquals("+ AMBER KEYCARD", pickupToast(hurt, 'k'));
        assertEquals("+ CYAN KEYCARD", pickupToast(hurt, 'c'));
        assertEquals("+ SCATTERGUN", pickupToast(hurt, 'W'), "the first Scattergun is named, not counted");
        assertEquals("+ RAIL LANCE", pickupToast(hurt, 'R'));
        assertEquals("+8 SHELLS", pickupToast(armed, 'W'), "a second Scattergun gives its shells");
        assertEquals("+5 SLUGS", pickupToast(armed, 'R'));
        assertEquals("+6 SHELLS", pickupToast(armed, 's'));
        assertEquals("+3 SLUGS", pickupToast(armed, 'r'));
    }

    @Test
    void shippedBriefingsReadAsTheyDidWithTheirPlaceholdersFilledIn() {
        Levels.Stage hub = Levels.stage("03_security_hub");
        assertEquals("HOLD {key:alt_fire} FOR THE {rail.zoom_factor}X ZOOM OPTIC, THEN EXTRACT.",
            hub.briefing.get(2));
        MenuState menu = menu(MenuState.Screen.STAGE_SELECT);
        assertEquals(Arrays.asList(
            "AUTOMATED CONTAINMENT LOCKDOWN DETECTED IN SECTOR 01.",
            "GRAB THE COMBAT SCATTERGUN TO SHRED CLOSE-RANGE DRONES.",
            "USE COOLANT CANISTERS AND DASH TO REACH EXTRACTION."), menu.getActiveBriefing());
        assertEquals("HOLD RMB FOR THE 1.8X ZOOM OPTIC, THEN EXTRACT.",
            STRINGS.fill(hub.briefing.get(2), "the briefing"));
    }

    // ---- An edited file changes what is shown, and only that ----

    @Test
    void anEditedLineChangesTheTextTheHudAndTheMenusBuild() {
        UiStrings edited = TestTuning.stringsWith("hud.hostiles", "FOES {total} - {remaining} LEFT");
        assertEquals("FOES 8 - 3 LEFT", edited.format(UiText.HUD_HOSTILES, "3", "8"), "values go by name");
        assertEquals("HOSTILES 3/8", STRINGS.format(UiText.HUD_HOSTILES, "3", "8"));

        assertEquals("OBJECTIVE: LEAVE", TestTuning.stringsWith("objective.exit", "LEAVE")
            .objectiveLine(GameSimulation.Objective.EXIT));
        assertEquals("GOAL - REACH EXIT PAD", TestTuning.stringsWith("hud.objective", "GOAL - {text}")
            .objectiveLine(GameSimulation.Objective.EXIT));
        assertEquals("GOT 25 OF HEALTH", TestTuning.stringsWith("pickup.gain", "GOT {amount} OF {label}")
            .toast(new GameSimulation.Toast(GameSimulation.ToastId.PICKUP_GAIN, Pickup.Kind.MEDKIT, 25)));
        assertEquals("SURVIVED 00:31.5   //   2 OF 8 DOWN", HudRenderer.survivedLine(
            TestTuning.stringsWith("screen.dead.hostiles", "{destroyed} OF {total} DOWN"), 31.5f, true, 2, 8));

        UiStrings go = TestTuning.stringsWith("menu.deploy", "GO");
        MenuState briefing = menu(go, new SaveData(new MapKeyValueStore()), MenuState.Screen.BRIEFING);
        assertEquals(Arrays.asList("GO", "RETURN TO TITLE"), briefing.getOptions());
        assertEquals(OptionId.DEPLOY, briefing.getSelectedOption().id);
        assertEquals(MenuState.Command.LAUNCH_STAGE, briefing.confirm(), "the reworded option still deploys");
    }

    @Test
    void aPromptRewordedWithoutItsKeyKeepsItsKindAndABlockedOneWithAKeyStaysBlocked() {
        UiStrings edited = UiStrings.parse(UiStrings.PATH, TestTuning.withValue(TestTuning.withValue(
            AssetText.read("data", UiStrings.PATH), "prompt.open_bulkhead", "OPEN THE BULKHEAD"),
            "blocked.needs_amber", "[{key:interact}] WILL NOT OPEN THIS"), TestTuning.FONT, TestTuning.BINDINGS::hint,
            TestTuning.TEXT_VALUES);
        assertEquals("OPEN THE BULKHEAD", edited.prompt(GameSimulation.InteractionPrompt.OPEN_BULKHEAD));
        assertEquals("[E] WILL NOT OPEN THIS", edited.prompt(GameSimulation.InteractionPrompt.NEEDS_AMBER));
        assertEquals(GameSimulation.InteractionPrompt.Kind.USABLE, GameSimulation.InteractionPrompt.OPEN_BULKHEAD.kind);
        assertEquals(GameSimulation.InteractionPrompt.Kind.BLOCKED, GameSimulation.InteractionPrompt.NEEDS_AMBER.kind);
    }

    @Test
    void keyNamesComeFromTheKeyLabelsAndTuningValuesFromTheTuning() {
        KeyLabels other = action -> "interact".equals(action) ? "F" : "alt_fire".equals(action) ? "MMB" : "K";
        UiStrings rebound = UiStrings.parse(UiStrings.PATH, AssetText.read("data", UiStrings.PATH), TestTuning.FONT,
            other, TestTuning.TEXT_VALUES);
        assertEquals("[F] OPEN BULKHEAD", rebound.prompt(GameSimulation.InteractionPrompt.OPEN_BULKHEAD));
        assertEquals("ENERGY [K: DASH]", rebound.get(UiText.HUD_ENERGY));
        String briefing = Levels.stage("03_security_hub").briefing.get(2);
        assertEquals("HOLD MMB FOR THE 1.8X ZOOM OPTIC, THEN EXTRACT.", rebound.fill(briefing, "here"));

        UiStrings zoomed = UiStrings.parse(UiStrings.PATH, AssetText.read("data", UiStrings.PATH), TestTuning.FONT,
            TestTuning.BINDINGS::hint, TextValues.of(TestTuning.weaponsWith("rail.zoom_factor", "2.0"),
                TestTuning.DIFFICULTY, TestTuning.WORLD));
        assertEquals("HOLD RMB FOR THE 2X ZOOM OPTIC, THEN EXTRACT.", zoomed.fill(briefing, "here"));

        // A key name the font cannot draw stops the load, naming the line that uses it.
        KeyLabels undrawable = action -> "dash".equals(action) ? "\u00e9" : "K";
        String message = assertThrows(IllegalArgumentException.class, () -> UiStrings.parse(UiStrings.PATH,
            AssetText.read("data", UiStrings.PATH), TestTuning.FONT, undrawable, TestTuning.TEXT_VALUES))
            .getMessage();
        assertTrue(message.endsWith("in 'hud.energy' in data/strings.txt at line " + lineOf("hud.energy")), message);
    }

    @Test
    void theActionsAKeyPlaceholderMayNameAreTheActionsOfTheBindings() {
        assertEquals(new ArrayList<>(Action.ids()), new ArrayList<>(KeyLabels.ACTIONS));
        assertEquals(25, KeyLabels.ACTIONS.size());
        KeyLabels shipped = TestTuning.BINDINGS::hint;
        for (String action : KeyLabels.ACTIONS) {
            String name = shipped.label(action);
            assertFalse(name.isEmpty(), action);
            assertEquals(-1, GlyphCheck.firstUndrawable(TestTuning.FONT, name), action + " is shown as " + name);
        }
        assertThrows(IllegalArgumentException.class, () -> shipped.label("fly"));
        assertEquals("Q", shipped.label("quit"));
        assertEquals("Q", shipped.label("quick_swap"));
    }

    @Test
    void difficultyRowsAreMadeFromTheLoadedMultipliers() {
        assertEquals("EASY - 70% DAMAGE / +25% PICKUPS", menu(MenuState.Screen.TITLE).difficultyRow(Difficulty.EASY));

        MenuState gentler = new MenuState(new GameSettings(), new SaveData(new MapKeyValueStore()),
            MenuState.Screen.DIFFICULTY_SELECT, TestTuning.difficultyWith("easy.damage_taken", "0.5"),
            TestTuning.WEAPONS, STRINGS);
        assertEquals("EASY - 50% DAMAGE / +25% PICKUPS", gentler.getOptions().get(0));
        MenuState scarcer = new MenuState(new GameSettings(), new SaveData(new MapKeyValueStore()),
            MenuState.Screen.DIFFICULTY_SELECT, TestTuning.difficultyWith("hard.pickup", "0.5"),
            TestTuning.WEAPONS, STRINGS);
        assertEquals("HARD - 140% DAMAGE / -50% PICKUPS", scarcer.getOptions().get(2));
        // A difficulty that changes something is no longer the standard one, and the row says what it shows.
        MenuState harderNormal = new MenuState(new GameSettings(), new SaveData(new MapKeyValueStore()),
            MenuState.Screen.DIFFICULTY_SELECT, TestTuning.difficultyWith("normal.damage_taken", "1.1"),
            TestTuning.WEAPONS, STRINGS);
        assertEquals("NORMAL - 110% DAMAGE / +0% PICKUPS", harderNormal.getOptions().get(1));
        // Whatever the rows say, the difficulty picked is the row's own.
        assertTrue(harderNormal.selectId(OptionId.DIFFICULTY, Difficulty.NORMAL.ordinal()));
        harderNormal.confirm();
        assertEquals(Difficulty.NORMAL, harderNormal.getActiveLoadout().difficulty);
    }

    // ---- Width of the loaded text ----

    @Test
    void everyOptionRowOfTheLoadedTextFitsItsBoxWhenHighlighted() {
        GameSettings widest = new GameSettings();
        widest.setFov(widest.defaults().fov.max);
        widest.setMasterVolume(widest.defaults().volume.max);
        SaveData save = new SaveData(new MapKeyValueStore());
        save.saveCampaignRun(0, PlayerLoadout.fresh());
        List<String> rows = new ArrayList<>();
        for (MenuState.Screen screen : MenuState.Screen.values()) {
            rows.addAll(new MenuState(widest, save, screen, TestTuning.DIFFICULTY, TestTuning.WEAPONS, STRINGS)
                .getOptions());
        }
        assertTrue(rows.size() > 25, "rows of every screen: " + rows);
        for (String row : rows) {
            float width = PixelFont.width("> " + row + " <", HudRenderer.OPTION_PIXEL);
            assertTrue(width <= HudRenderer.OPTION_ROW_WIDTH, row + " is " + width + " wide when highlighted");
        }
        // The check sees a row that is too long.
        String tooLong = TestTuning.stringsWith("menu.deploy", "DEPLOY TO THE STAGE WITH EVERYTHING YOU CARRY")
            .get(UiText.MENU_DEPLOY);
        assertTrue(PixelFont.width("> " + tooLong + " <", HudRenderer.OPTION_PIXEL) > HudRenderer.OPTION_ROW_WIDTH);
    }

    // ---- A broken file stops the load ----

    @Test
    void aMissingUnknownOrEmptyLineFailsNamingTheFileAndTheKey() {
        assertEquals("Missing 'hud.hp' key in data/strings.txt", error("hud.hp", null));
        String unknown = assertThrows(IllegalArgumentException.class,
            () -> parse("hud.mana: MANA\n" + AssetText.read("data", UiStrings.PATH))).getMessage();
        assertEquals("Unknown key 'hud.mana' in data/strings.txt at line 1", unknown);
        assertEquals("Invalid 'menu.deploy' in data/strings.txt at line " + lineOf("menu.deploy")
            + ": must not be empty, got ''", error("menu.deploy", ""));
        String twice = assertThrows(IllegalArgumentException.class,
            () -> parse(AssetText.read("data", UiStrings.PATH) + "menu.deploy: GO\n")).getMessage();
        assertTrue(twice.startsWith("Duplicate 'menu.deploy' key in data/strings.txt at line "), twice);
    }

    @Test
    void aBadPlaceholderFailsNamingTheFileTheKeyAndTheLine() {
        String where = " in 'hud.hostiles' in data/strings.txt at line " + lineOf("hud.hostiles");
        assertEquals("Unknown placeholder {left}" + where + " (it may hold {remaining}, {total}, {key:action},"
            + " {rail.zoom_factor})", error("hud.hostiles", "HOSTILES {left}/{total}"));
        assertEquals("Missing placeholder {total}" + where, error("hud.hostiles", "HOSTILES {remaining}"));
        assertEquals("'{' without '}' after it" + where, error("hud.hostiles", "HOSTILES {remaining/{total}"));
        assertEquals("'}' without '{' before it" + where, error("hud.hostiles", "HOSTILES {remaining}/{total}}"));
        assertEquals("Empty placeholder {}" + where, error("hud.hostiles", "HOSTILES {}{remaining}/{total}"));
        assertTrue(error("hud.hostiles", "{key:fly} {remaining}/{total}")
            .startsWith("Unknown action in placeholder {key:fly}" + where), "an action no key is bound to");
        // A line without values takes none, so a stray placeholder in a plain label is caught too.
        assertTrue(error("menu.deploy", "DEPLOY {now}").startsWith("Unknown placeholder {now} in 'menu.deploy'"));
    }

    @Test
    void aCharacterTheFontLacksFailsNamingTheFileTheKeyAndTheLine() {
        assertEquals("Unsupported PixelFont character '@' (U+0040) in 'menu.deploy' in data/strings.txt at line "
            + lineOf("menu.deploy"), error("menu.deploy", "DEPLOY @ ONCE"));
        assertEquals("Unsupported PixelFont character '_' (U+005F) in 'hud.hp' in data/strings.txt at line "
            + lineOf("hud.hp"), error("hud.hp", "H_P {value}"));
        String briefing = assertThrows(IllegalArgumentException.class,
            () -> STRINGS.fill("PRESS {key:jump} @ ONCE", "briefing line 2 of stage s1")).getMessage();
        assertEquals("Unsupported PixelFont character '@' (U+0040) in briefing line 2 of stage s1", briefing);
        assertThrows(IllegalArgumentException.class, () -> STRINGS.requireDrawable("07: CAF\u00c9", "a title"));
        assertEquals("07: DEEP", STRINGS.requireDrawable("07: DEEP", "a title"));
    }

    @Test
    void everyKeyOfTheFileIsDeclaredOnceAndEveryLineIsRead() {
        List<String> keys = new ArrayList<>();
        for (UiText text : UiText.values()) {
            assertFalse(keys.contains(text.key), "two lines share the key " + text.key);
            keys.add(text.key);
            // A line with values needs them all; a line without takes none.
            if (text.placeholders.isEmpty()) {
                assertFalse(STRINGS.get(text).isEmpty(), text.key);
            } else {
                assertThrows(IllegalArgumentException.class, () -> STRINGS.get(text), text.key);
                assertThrows(IllegalArgumentException.class, () -> STRINGS.format(text), text.key);
            }
        }
        assertNotEquals(TestTuning.rewordedStrings().get(UiText.MENU_DEPLOY), STRINGS.get(UiText.MENU_DEPLOY));
    }
}

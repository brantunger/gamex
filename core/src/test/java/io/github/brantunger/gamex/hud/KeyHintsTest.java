package io.github.brantunger.gamex.hud;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import io.github.brantunger.gamex.GameSimulation;
import io.github.brantunger.gamex.TestTuning;
import io.github.brantunger.gamex.data.AssetText;
import io.github.brantunger.gamex.hud.MenuState.OptionId;
import io.github.brantunger.gamex.input.KeyBindings;
import io.github.brantunger.gamex.settings.MapKeyValueStore;
import io.github.brantunger.gamex.settings.SaveData;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Arrays;
import java.util.List;

/**
 * Checks that every key name the menus, the HUD and the briefings show comes from
 * {@code assets/data/bindings.txt}: with the tests' own file the text shows the key names of that file, and
 * a file with other keys changes every line that names one.
 */
class KeyHintsTest {

    /** Other inputs for every action the text names, but the wheel, whose two directions are both SCROLL. */
    private static final String[] OTHER_KEYS = {
        "pause", "P", "retry", "T", "move_forward", "I", "move_back", "K", "move_left", "J", "move_right", "L",
        "dash", "L-Ctrl", "jump", "N", "interact", "F", "weapon_1", "7", "weapon_2", "8", "weapon_3", "9",
        "quick_swap", "Tab", "fire", "Mouse-Middle", "alt_fire", "Mouse-Back", "minimap", "B", "menu_left", "Z",
        "menu_right", "C", "quit", "X"};
    private static final KeyBindings REBOUND = TestTuning.bindingsWith(OTHER_KEYS);
    private static final UiStrings FROZEN = TestTuning.STRINGS;
    private static final UiStrings STRINGS = TestTuning.stringsFor(REBOUND);

    private static MenuState menu(UiStrings strings, SaveData save, MenuState.Screen screen) {
        return new MenuState(TestTuning.settings(), save, screen, TestTuning.DIFFICULTY, TestTuning.WEAPONS, strings);
    }

    /** @return a line with a 1 for each value the game fills in */
    private static String shown(UiStrings strings, UiText text) {
        String[] values = new String[text.placeholders.size()];
        Arrays.fill(values, "1");
        return strings.format(text, values);
    }

    /** A briefing line of the test's own, as a stage file holds it: it names a key and quotes a tuning value. */
    private static final String BRIEFING_LINE =
        "HOLD {key:alt_fire} FOR THE {rail.zoom_factor}X ZOOM OPTIC AND BREACH TO EXTRACTION.";

    /** @return the briefing the menus show for the stage a new game opens on */
    private static List<String> shownBriefing(UiStrings strings) {
        return menu(strings, new SaveData(new MapKeyValueStore()), MenuState.Screen.BRIEFING).getActiveBriefing();
    }

    @Test
    void theBindingsGiveTheTextItsKeyNames() {
        assertEquals("WASD: MOVE  SPACE: JUMP  SHIFT: DASH  E: USE  1-3/Q/SCROLL: WEAPON  LMB/RMB: FIRE/ALT  M: MAP",
            FROZEN.get(UiText.SCREEN_TITLE_FOOTER));
        assertEquals("LEFT/RIGHT OR CLICK TO ADJUST - ESC TO SAVE", FROZEN.get(UiText.SCREEN_SETTINGS_HELP));
        assertEquals("ENERGY [SHIFT: DASH]", FROZEN.get(UiText.HUD_ENERGY));
        assertEquals("1: PISTOL [INF]", FROZEN.get(UiText.HUD_WEAPON_PISTOL));
        assertEquals("2: SCATTERGUN [4/16]", FROZEN.format(UiText.HUD_WEAPON_SCATTERGUN, "4", "16"));
        assertEquals("3: RAIL LANCE [2/8]", FROZEN.format(UiText.HUD_WEAPON_RAIL_LANCE, "2", "8"));
        assertEquals("PRESS Q AGAIN TO QUIT", FROZEN.get(UiText.MENU_QUIT_PROMPT));
        assertEquals("RETRY STAGE (R)", FROZEN.get(UiText.MENU_RETRY_STAGE));
        assertEquals("[E] OVERRIDE LOCKDOWN", FROZEN.prompt(GameSimulation.InteractionPrompt.OVERRIDE_CONSOLE));
        assertEquals("[E] UNLOCK AMBER BULKHEAD", FROZEN.prompt(GameSimulation.InteractionPrompt.UNLOCK_AMBER));
        assertEquals("[E] UNLOCK CYAN BULKHEAD", FROZEN.prompt(GameSimulation.InteractionPrompt.UNLOCK_CYAN));
        assertEquals("[E] OPEN BULKHEAD", FROZEN.prompt(GameSimulation.InteractionPrompt.OPEN_BULKHEAD));
        assertEquals("HOLD RMB FOR THE 1.8X ZOOM OPTIC AND BREACH TO EXTRACTION.",
            FROZEN.fill(BRIEFING_LINE, "a briefing"));
    }

    @Test
    void otherKeysInTheBindingsFileChangeEveryHintOfTheHudAndTheMenus() {
        assertEquals("IJKL: MOVE  N: JUMP  CTRL: DASH  F: USE  7-9/TAB/SCROLL: WEAPON  MMB/MB4: FIRE/ALT  B: MAP",
            STRINGS.get(UiText.SCREEN_TITLE_FOOTER));
        assertEquals("Z/C OR CLICK TO ADJUST - P TO SAVE", STRINGS.get(UiText.SCREEN_SETTINGS_HELP));
        assertEquals("ENERGY [CTRL: DASH]", STRINGS.get(UiText.HUD_ENERGY));
        assertEquals("7: PISTOL [INF]", STRINGS.get(UiText.HUD_WEAPON_PISTOL));
        assertEquals("8: SCATTERGUN [4/16]", STRINGS.format(UiText.HUD_WEAPON_SCATTERGUN, "4", "16"));
        assertEquals("9: RAIL LANCE [2/8]", STRINGS.format(UiText.HUD_WEAPON_RAIL_LANCE, "2", "8"));
        assertEquals("PRESS X AGAIN TO QUIT", STRINGS.get(UiText.MENU_QUIT_PROMPT));
        assertEquals("[F] OVERRIDE LOCKDOWN", STRINGS.prompt(GameSimulation.InteractionPrompt.OVERRIDE_CONSOLE));
        assertEquals("[F] UNLOCK AMBER BULKHEAD", STRINGS.prompt(GameSimulation.InteractionPrompt.UNLOCK_AMBER));
        assertEquals("[F] UNLOCK CYAN BULKHEAD", STRINGS.prompt(GameSimulation.InteractionPrompt.UNLOCK_CYAN));
        assertEquals("[F] OPEN BULKHEAD", STRINGS.prompt(GameSimulation.InteractionPrompt.OPEN_BULKHEAD));

        // The Death menu's retry row, as the menus list it.
        MenuState dead = menu(STRINGS, new SaveData(new MapKeyValueStore()), MenuState.Screen.PLAYING);
        dead.onPlayerDied();
        assertTrue(dead.selectId(OptionId.RETRY));
        assertEquals("RETRY STAGE (T)", dead.getSelectedOption().label);
    }

    @Test
    void anotherKeyInTheBindingsFileChangesTheBriefingThatNamesIt() {
        assertEquals("HOLD MB4 FOR THE 1.8X ZOOM OPTIC AND BREACH TO EXTRACTION.",
            STRINGS.fill(BRIEFING_LINE, "a briefing"));
        assertEquals("GRAB THE SCATTERGUN.", STRINGS.fill("GRAB THE SCATTERGUN.", "a line that names no key"));

        // Whatever the stage files say, the menus show each briefing line filled in with the bindings they are given.
        List<String> raw = menu(STRINGS, new SaveData(new MapKeyValueStore()), MenuState.Screen.BRIEFING)
            .getActiveStage().briefing;
        List<String> frozen = shownBriefing(FROZEN);
        List<String> rebound = shownBriefing(STRINGS);
        assertEquals(raw.size(), rebound.size());
        for (int i = 0; i < raw.size(); i++) {
            assertEquals(FROZEN.fill(raw.get(i), "a briefing"), frozen.get(i));
            assertEquals(STRINGS.fill(raw.get(i), "a briefing"), rebound.get(i));
        }
    }

    @Test
    void everyLineThatNamesAKeyFollowsTheBindingsAndNoOtherLineChanges() {
        List<String> lines = AssetText.splitLines(TestTuning.text(UiStrings.PATH));
        int naming = 0;
        for (UiText text : UiText.values()) {
            String raw = null;
            for (String line : lines) {
                if (line.startsWith(text.key + ":")) raw = line;
            }
            assertTrue(raw != null, text.key);
            if (raw.contains("{key:")) {
                naming++;
                assertNotEquals(shown(FROZEN, text), shown(STRINGS, text), text.key + " names a key");
            } else {
                assertEquals(shown(FROZEN, text), shown(STRINGS, text), text.key + " names no key");
            }
        }
        assertEquals(12, naming, "the lines of the tests' own file that name a key");
    }

    /**
     * The menus a test builds without handing them the text read the bindings file too, so an edited copy of it in
     * the assets folder reaches the screen with no key name left over from the code.
     */
    @Test
    void menusBuiltWithoutTheTextTakeTheirKeyNamesFromTheBindingsFile(@TempDir Path temp) throws IOException {
        Files.createDirectories(temp.resolve("data"));
        Files.write(temp.resolve(KeyBindings.PATH),
            TestTuning.bindingsTextWith("retry", "T").getBytes(StandardCharsets.UTF_8));
        Files.write(temp.resolve(UiStrings.PATH), TestTuning.text(UiStrings.PATH).getBytes(StandardCharsets.UTF_8));
        Files.write(temp.resolve(PixelFont.PATH), TestTuning.text(PixelFont.PATH).getBytes(StandardCharsets.UTF_8));
        String previous = System.getProperty("gamex.assets");
        MenuState dead;
        try {
            // The bindings, the text and the font are found here; every other file is read from the folders tried
            // next.
            System.setProperty("gamex.assets", temp.toString());
            dead = new MenuState(TestTuning.settings(), new SaveData(new MapKeyValueStore()),
                MenuState.Screen.PLAYING);
        } finally {
            if (previous == null) {
                System.clearProperty("gamex.assets");
            } else {
                System.setProperty("gamex.assets", previous);
            }
        }
        dead.onPlayerDied();
        assertTrue(dead.selectId(OptionId.RETRY));
        assertEquals("RETRY STAGE (T)", dead.getSelectedOption().label);
    }
}

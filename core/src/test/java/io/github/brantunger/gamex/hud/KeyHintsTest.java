package io.github.brantunger.gamex.hud;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import io.github.brantunger.gamex.GameSimulation;
import io.github.brantunger.gamex.TestTuning;
import io.github.brantunger.gamex.data.AssetText;
import io.github.brantunger.gamex.hud.MenuState.OptionId;
import io.github.brantunger.gamex.input.KeyBindings;
import io.github.brantunger.gamex.settings.GameSettings;
import io.github.brantunger.gamex.settings.MapKeyValueStore;
import io.github.brantunger.gamex.settings.SaveData;
import io.github.brantunger.gamex.world.Levels;
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
 * {@code assets/data/bindings.txt}: with the shipped file the text reads as it did while the names were fixed, and
 * a file with other keys changes every line that names one.
 */
class KeyHintsTest {

    /** Other inputs for every action the text names, but the wheel, whose two directions are both SCROLL. */
    private static final String[] OTHER_KEYS = {
        "pause", "P", "retry", "T", "move_forward", "I", "move_back", "K", "move_left", "J", "move_right", "L",
        "dash", "L-Ctrl", "jump", "N", "interact", "F", "weapon_1", "7", "weapon_2", "8", "weapon_3", "9",
        "quick_swap", "Tab", "fire", "Mouse-Middle", "alt_fire", "Mouse-Back", "menu_left", "Z", "menu_right", "C",
        "quit", "X"};
    private static final KeyBindings REBOUND = TestTuning.bindingsWith(OTHER_KEYS);
    private static final UiStrings SHIPPED = TestTuning.STRINGS;
    private static final UiStrings STRINGS = TestTuning.stringsFor(REBOUND);

    private static MenuState menu(UiStrings strings, SaveData save, MenuState.Screen screen) {
        return new MenuState(new GameSettings(), save, screen, TestTuning.DIFFICULTY, TestTuning.WEAPONS, strings);
    }

    /** @return a line with a 1 for each value the game fills in */
    private static String shown(UiStrings strings, UiText text) {
        String[] values = new String[text.placeholders.size()];
        Arrays.fill(values, "1");
        return strings.format(text, values);
    }

    /** @return the briefing the menus show for the Security Hub, whose last line names the alt fire key */
    private static List<String> hubBriefing(UiStrings strings) {
        SaveData save = new SaveData(new MapKeyValueStore());
        int hub = 0;
        while (!"03_security_hub".equals(Levels.campaign().get(hub).id)) hub++;
        save.unlockStage(hub);
        MenuState menu = menu(strings, save, MenuState.Screen.STAGE_SELECT);
        assertTrue(menu.selectId(OptionId.STAGE, hub));
        menu.confirm();
        menu.confirm();
        assertEquals(MenuState.Screen.BRIEFING, menu.getScreen(), "past Select Difficulty");
        assertEquals("03_security_hub", menu.getActiveStage().id);
        return menu.getActiveBriefing();
    }

    @Test
    void theShippedBindingsShowTheKeyNamesTheTextSpelledOut() {
        assertEquals("WASD: MOVE  SPACE: JUMP  SHIFT: DASH  E: USE  1-3/Q/SCROLL: WEAPON  LMB/RMB: FIRE/ALT",
            SHIPPED.get(UiText.SCREEN_TITLE_FOOTER));
        assertEquals("LEFT/RIGHT OR CLICK TO ADJUST - ESC TO SAVE", SHIPPED.get(UiText.SCREEN_SETTINGS_HELP));
        assertEquals("ENERGY [SHIFT: DASH]", SHIPPED.get(UiText.HUD_ENERGY));
        assertEquals("1: PISTOL [INF]", SHIPPED.get(UiText.HUD_WEAPON_PISTOL));
        assertEquals("2: SCATTERGUN [4/16]", SHIPPED.format(UiText.HUD_WEAPON_SCATTERGUN, "4", "16"));
        assertEquals("3: RAIL LANCE [2/8]", SHIPPED.format(UiText.HUD_WEAPON_RAIL_LANCE, "2", "8"));
        assertEquals("PRESS Q AGAIN TO QUIT", SHIPPED.get(UiText.MENU_QUIT_PROMPT));
        assertEquals("RETRY STAGE (R)", SHIPPED.get(UiText.MENU_RETRY_STAGE));
        assertEquals("[E] OVERRIDE LOCKDOWN", SHIPPED.prompt(GameSimulation.InteractionPrompt.OVERRIDE_CONSOLE));
        assertEquals("[E] UNLOCK AMBER BULKHEAD", SHIPPED.prompt(GameSimulation.InteractionPrompt.UNLOCK_AMBER));
        assertEquals("[E] UNLOCK CYAN BULKHEAD", SHIPPED.prompt(GameSimulation.InteractionPrompt.UNLOCK_CYAN));
        assertEquals("[E] OPEN BULKHEAD", SHIPPED.prompt(GameSimulation.InteractionPrompt.OPEN_BULKHEAD));
        assertEquals("HOLD RMB FOR THE 1.8X ZOOM OPTIC, THEN EXTRACT.", hubBriefing(SHIPPED).get(2));
    }

    @Test
    void otherKeysInTheBindingsFileChangeEveryHintOfTheHudAndTheMenus() {
        assertEquals("IJKL: MOVE  N: JUMP  CTRL: DASH  F: USE  7-9/TAB/SCROLL: WEAPON  MMB/MB4: FIRE/ALT",
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
        List<String> shipped = hubBriefing(SHIPPED);
        List<String> rebound = hubBriefing(STRINGS);
        assertEquals("HOLD MB4 FOR THE 1.8X ZOOM OPTIC, THEN EXTRACT.", rebound.get(2));
        assertEquals(shipped.subList(0, 2), rebound.subList(0, 2), "the lines that name no key");
    }

    @Test
    void everyLineThatNamesAKeyFollowsTheBindingsAndNoOtherLineChanges() {
        List<String> lines = AssetText.splitLines(AssetText.read("data", UiStrings.PATH));
        int naming = 0;
        for (UiText text : UiText.values()) {
            String raw = null;
            for (String line : lines) {
                if (line.startsWith(text.key + ":")) raw = line;
            }
            assertTrue(raw != null, text.key);
            if (raw.contains("{key:")) {
                naming++;
                assertNotEquals(shown(SHIPPED, text), shown(STRINGS, text), text.key + " names a key");
            } else {
                assertEquals(shown(SHIPPED, text), shown(STRINGS, text), text.key + " names no key");
            }
        }
        assertEquals(12, naming, "the lines of the shipped file that name a key");
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
        String previous = System.getProperty("gamex.assets");
        MenuState dead;
        try {
            // Only the bindings are found here; every other file is read from the folders tried next.
            System.setProperty("gamex.assets", temp.toString());
            dead = new MenuState(new GameSettings(), new SaveData(new MapKeyValueStore()), MenuState.Screen.PLAYING);
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

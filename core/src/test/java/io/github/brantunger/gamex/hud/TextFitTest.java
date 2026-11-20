package io.github.brantunger.gamex.hud;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import io.github.brantunger.gamex.TestTuning;
import io.github.brantunger.gamex.data.AssetText;
import io.github.brantunger.gamex.input.KeyBindings;
import io.github.brantunger.gamex.player.DifficultyTuning;
import io.github.brantunger.gamex.settings.SettingsDefaults;
import io.github.brantunger.gamex.world.Levels;
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Paths;
import java.util.List;

/**
 * Checks the measuring of the menu and HUD text at start-up ({@link TextFit}): the tests' own text, bindings and
 * stage files pass (the shipped ones are measured in {@link ShippedTextTest}), and a line made too long for its
 * box, in {@code strings.txt} or through a long key name of {@code bindings.txt}, stops the start with a message
 * that names it.
 */
class TextFitTest {

    private static final SettingsDefaults SETTINGS = TestTuning.SETTINGS;

    private static void require(UiStrings strings) {
        TextFit.require(strings, TestTuning.DIFFICULTY, SETTINGS, TestTuning.CAMPAIGN, TestTuning.TRAINING_RANGE);
    }

    private static String rejected(UiStrings strings) {
        return assertThrows(IllegalArgumentException.class, () -> require(strings)).getMessage();
    }

    /** @return a text of {@code length} characters the font can draw */
    private static String ofLength(int length) {
        StringBuilder text = new StringBuilder();
        while (text.length() < length) text.append('W');
        return text.toString();
    }

    /** @return the number of the line that holds a key in the tests' own {@code strings.txt} */
    private static int lineOf(String key) {
        List<String> lines = AssetText.splitLines(TestTuning.text(UiStrings.PATH));
        for (int i = 0; i < lines.size(); i++) {
            if (lines.get(i).startsWith(key + ":")) return i + 1;
        }
        throw new AssertionError("no line for " + key);
    }

    /** Asserts that a line of {@code fits} characters passes and that one character more stops the start. */
    private static void assertRoomFor(String key, int fits, String box) {
        assertDoesNotThrow(() -> require(TestTuning.stringsWith(key, ofLength(fits))), key);
        String message = rejected(TestTuning.stringsWith(key, ofLength(fits + 1)));
        assertTrue(message.startsWith("Invalid '" + key + "' in data/strings.txt at line " + lineOf(key)
            + ": too long for " + box + " by 1 character: \""), message);
    }

    @Test
    void theTestsOwnTextFitsWithItsKeyNames() {
        assertDoesNotThrow(() -> require(TestTuning.STRINGS));
    }

    @Test
    void theGameMeasuresTheTextOnceItHasLoadedIt() throws IOException {
        // MainGame.create() needs a window, so the call is checked in its source, as TunedSimulationTest does.
        String game = new String(Files.readAllBytes(
            Paths.get("src/main/java/io/github/brantunger/gamex/MainGame.java")), StandardCharsets.UTF_8);
        int create = game.indexOf("public void create()");
        int loaded = game.indexOf("UiStrings strings = UiStrings.load(", create);
        int measured = game.indexOf("TextFit.require(strings, difficultyTuning, settingsDefaults, "
            + "Levels.campaign(), Levels.trainingRange());", loaded);
        int menus = game.indexOf("new MenuState(", loaded);
        assertTrue(create > 0 && loaded > create, "MainGame.create() loads the text");
        assertTrue(measured > loaded, "MainGame.create() measures the text it loaded");
        assertTrue(measured < menus, "before the menus are built");
    }

    @Test
    void aLineTooLongForItsBoxStopsTheStartNamingTheFileTheKeyAndTheLine() {
        String message = rejected(TestTuning.stringsWith("toast.lockdown_overridden",
            "LOCKDOWN OVERRIDDEN - ALL BULKHEADS OPEN"));
        assertEquals("Invalid 'toast.lockdown_overridden' in data/strings.txt at line "
            + lineOf("toast.lockdown_overridden") + ": too long for a toast by 2 characters: "
            + "\"LOCKDOWN OVERRIDDEN - ALL BULKHEADS OPEN\" is 445.2 HUD units wide and 424 fit", message);
    }

    @Test
    void everyKindOfBoxHasTheRoomItsLayoutGivesIt() {
        // A character is 4 font pixels wide with its gap, less the gap after the last one.
        // Toast: 452 around the centre less 14 on each side, at 2.8.
        assertRoomFor("toast.out_of_shells", 38, "a toast");
        // Title footer: the 960 of a 4:3 window, at 2.0.
        assertRoomFor("screen.title.footer", 120, "a 4:3 window");
        // Interaction prompt: 720 less 10 on each side, at 2.4.
        assertRoomFor("prompt.open_bulkhead", 73, "an interaction prompt");
        // A blocked bulkhead's line is a toast as well as a prompt, and the toast is the narrower.
        assertRoomFor("blocked.needs_amber", 38, "a toast");
        // Highlighted menu row: 520 at 2.8, with "> " and " <" around the label.
        assertRoomFor("menu.resume", 42, "a highlighted menu row");
        // Quit prompt: the Pause panel's 560 less 20 on each side, at 2.8.
        assertRoomFor("menu.quit_prompt", 46, "the Pause panel");
        // Tally lines without values: the panel's 640 less 20 on each side, at 2.4.
        assertRoomFor("tally.medals_none", 62, "the Intermission Tally panel");
        // The titles and captions of the menu screens: their panel less 20 on each side, at their own sizes.
        assertRoomFor("screen.title.name", 11, "the Title panel");
        assertRoomFor("screen.title.subtitle", 46, "the Title panel");
        assertRoomFor("screen.stage_select.title", 26, "the Select Stage panel");
        assertRoomFor("screen.stage_select.hint", 66, "the Select Stage panel");
        assertRoomFor("screen.difficulty.title", 24, "the Select Difficulty panel");
        assertRoomFor("screen.paused.title", 20, "the Pause panel");
        assertRoomFor("screen.settings.title", 24, "the Settings panel");
        assertRoomFor("screen.dead.title", 20, "the Death panel");
        assertRoomFor("screen.complete.stage", 26, "the Intermission Tally panel");
        assertRoomFor("screen.complete.campaign", 27, "the Intermission Tally panel");
        assertRoomFor("screen.complete.campaign_note", 62, "the Intermission Tally panel");
        // The boss label: the bar's 380 and 12 on each side, at 2.3.
        assertRoomFor("hud.boss", 44, "the boss bar's panel");
        assertRoomFor("hud.boss_overdrive", 44, "the boss bar's panel");
    }

    @Test
    void aLineWithValuesIsMeasuredWithTheLongestItCanHold() {
        // The objective line is "OBJECTIVE: " and the objective's words: 382 from the status column's edge to the
        // limit, at 2.4, is 40 characters, 11 of them the caption of the tests' own text.
        assertDoesNotThrow(() -> require(TestTuning.stringsWith("objective.exit", ofLength(29))));
        String objective = rejected(TestTuning.stringsWith("objective.exit", ofLength(30)));
        assertTrue(objective.startsWith("Invalid 'objective.exit' in data/strings.txt at line "
            + lineOf("objective.exit") + ": too long for the objective line ('hud.objective' around it) by 1 "
            + "character: \"OBJECTIVE: WWW"), objective);

        // A pickup toast is measured with a three-digit amount and every label.
        String gain = rejected(TestTuning.stringsWith("pickup.label.rail_slugs", ofLength(34)));
        assertTrue(gain.startsWith("Invalid 'pickup.gain' in data/strings.txt at line " + lineOf("pickup.gain")
            + ": too long for a toast by 1 character: \"+999 WWW"), gain);

        // A keycard and a weapon come without an amount, so their labels have the room of "+ " and no less.
        assertDoesNotThrow(() -> require(TestTuning.stringsWith("pickup.label.amber_keycard", ofLength(36))));
        assertDoesNotThrow(() -> require(TestTuning.stringsWith("pickup.label.rail_lance", ofLength(36))));
        String grant = rejected(TestTuning.stringsWith("pickup.label.amber_keycard", ofLength(37)));
        assertTrue(grant.startsWith("Invalid 'pickup.grant' in data/strings.txt at line " + lineOf("pickup.grant")
            + ": too long for a toast by 1 character: \"+ WWW"), grant);

        // A Select Stage row is measured with every stage title, the longest time and every difficulty label.
        String stage = rejected(TestTuning.stringsWith("difficulty.label.hard", "HARDEST OF THEM ALL"));
        assertTrue(stage.startsWith("Invalid 'menu.stage.best_time' in data/strings.txt at line "
            + lineOf("menu.stage.best_time") + ": too long for a highlighted menu row by "), stage);
        assertTrue(stage.contains(" 99:59 (HARDEST OF THEM ALL) <\" is "), stage);

        // The Death screen's line is measured with the longest time and three-digit counts.
        String survived = rejected(TestTuning.stringsWith("screen.dead.survived",
            "SURVIVED THE BREACH FOR {time}    //    {tally}"));
        assertTrue(survived.startsWith("Invalid 'screen.dead.survived' in data/strings.txt at line "
            + lineOf("screen.dead.survived") + ": too long for the Death panel ('screen.dead.hostiles' in it) by "),
            survived);
        assertTrue(survived.contains("\"SURVIVED THE BREACH FOR 99:59.9    //    HOSTILES 999/999\""), survived);

        // The line under the briefing's title is measured with every difficulty label and the longest par time.
        String metaOnly = rejected(TestTuning.stringsWith("screen.briefing.meta",
            "DIFFICULTY OF THIS RUN: {difficulty}     //     PAR TIME TO BEAT: {par}"));
        assertTrue(metaOnly.startsWith("Invalid 'screen.briefing.meta' in data/strings.txt at line "
            + lineOf("screen.briefing.meta") + ": too long for the Briefing panel by "), metaOnly);
        assertTrue(metaOnly.contains("PAR TIME TO BEAT: 99:59.9\""), metaOnly);

        // A tally line is measured with three-digit counts, four-digit damage and 100%.
        String stats = rejected(TestTuning.stringsWith("tally.stats",
            "DAMAGE TAKEN: {damage}    PICKUPS COLLECTED: {collected}/{total}    ACCURACY: {accuracy}"));
        assertTrue(stats.startsWith("Invalid 'tally.stats' in data/strings.txt at line " + lineOf("tally.stats")
            + ": too long for the Intermission Tally panel by "), stats);
        assertTrue(stats.contains("\"DAMAGE TAKEN: 9999    PICKUPS COLLECTED: 999/999    ACCURACY: 100%\""), stats);

        // A Settings row is measured with the end of its range that is longer to write: 110 degrees in the tests'
        // own file, and two digits when the file's range ends at 90.
        UiStrings longRow = TestTuning.stringsWith("menu.settings.fov", ofLength(35) + " < {value} >");
        String row = rejected(longRow);
        assertTrue(row.startsWith("Invalid 'menu.settings.fov' in data/strings.txt at line "
            + lineOf("menu.settings.fov") + ": too long for a highlighted menu row by 1 character: "), row);
        assertTrue(row.contains(" < 110 > <\" is "), row);
        String narrower = TestTuning.withValue(TestTuning.text(SettingsDefaults.PATH), "fov.max", "90");
        assertDoesNotThrow(() -> TextFit.require(longRow, TestTuning.DIFFICULTY,
            SettingsDefaults.parse(SettingsDefaults.PATH, narrower), TestTuning.CAMPAIGN, TestTuning.TRAINING_RANGE));

        // The Select Difficulty rows are measured with the multipliers that were loaded.
        DifficultyTuning steep = TestTuning.difficultyWith("hard.damage_taken", "125000");
        UiStrings longDifficulty = TestTuning.stringsWith("menu.difficulty.row",
            "{label} - {damage} DAMAGE TAKEN / {pickups} PICKUPS");
        assertDoesNotThrow(() -> require(longDifficulty));
        String difficulty = assertThrows(IllegalArgumentException.class, () -> TextFit.require(longDifficulty, steep,
            SETTINGS, TestTuning.CAMPAIGN, TestTuning.TRAINING_RANGE)).getMessage();
        assertTrue(difficulty.startsWith("Invalid 'menu.difficulty.row' in data/strings.txt at line "
            + lineOf("menu.difficulty.row") + ": too long for a highlighted menu row by 2 characters: "
            + "\"> HARD - 12500000% DAMAGE TAKEN / -20% PICKUPS <\""), difficulty);
    }

    @Test
    void aStageWithMoreBriefingLinesThanTheBoxHoldsStopsTheStartNamingItsFile() {
        String stageText = TestTuning.text("levels/03-security-hub.map");
        String third = "briefing: HOLD {key:alt_fire} FOR THE {rail.zoom_factor}X ZOOM OPTIC, THEN EXTRACT.\n";
        assertTrue(stageText.contains(third), "the tests' own stage ends its briefing with the line the test splits");
        Levels.Stage fourLines = Levels.parseStage("levels/03-security-hub.map", stageText.replace(third,
            "briefing: HOLD {key:alt_fire} FOR THE {rail.zoom_factor}X ZOOM OPTIC.\nbriefing: THEN EXTRACT.\n"));
        assertEquals(4, fourLines.briefing.size());
        IllegalArgumentException error = assertThrows(IllegalArgumentException.class,
            () -> TextFit.require(TestTuning.STRINGS, TestTuning.DIFFICULTY, SETTINGS, List.of(fourLines),
                TestTuning.TRAINING_RANGE));
        assertEquals("Invalid briefing of stage 03_security_hub in levels/03-security-hub.map: it has 4 lines and "
            + "the briefing's box has room for 3", error.getMessage());
    }

    @Test
    void aParTimeOfAHundredMinutesIsMeasuredAsItIsWritten() {
        String stageText = TestTuning.text("levels/03-security-hub.map");
        assertTrue(stageText.contains("\npar: 80\n"), "the tests' own stage has the par the test replaces");
        Levels.Stage slow = Levels.parseStage("levels/03-security-hub.map",
            stageText.replace("\npar: 80\n", "\npar: 6000\n"));
        assertEquals("100:00.0", TextFormat.stopwatch(slow.parSeconds));

        // The tests' own lines have room for it.
        assertDoesNotThrow(() -> TextFit.require(TestTuning.STRINGS, TestTuning.DIFFICULTY, SETTINGS, List.of(slow),
            TestTuning.TRAINING_RANGE));

        // A line under the briefing's title that is full with 99:59.9 is one character short for 100:00.0.
        UiStrings fullMeta = TestTuning.stringsWith("screen.briefing.meta", ofLength(48) + "{difficulty} {par}");
        assertDoesNotThrow(() -> require(fullMeta));
        String meta = assertThrows(IllegalArgumentException.class, () -> TextFit.require(fullMeta,
            TestTuning.DIFFICULTY, SETTINGS, List.of(slow), TestTuning.TRAINING_RANGE)).getMessage();
        assertTrue(meta.startsWith("Invalid 'screen.briefing.meta' in data/strings.txt at line "
            + lineOf("screen.briefing.meta") + ": too long for the Briefing panel by 1 character: "), meta);
        assertTrue(meta.contains("NORMAL 100:00.0\" is "), meta);

        // So is the tally's clear time line.
        UiStrings fullTime = TestTuning.stringsWith("tally.clear_time", ofLength(42) + "{time} {par}");
        assertDoesNotThrow(() -> require(fullTime));
        String time = assertThrows(IllegalArgumentException.class, () -> TextFit.require(fullTime,
            TestTuning.DIFFICULTY, SETTINGS, List.of(slow), TestTuning.TRAINING_RANGE)).getMessage();
        assertTrue(time.startsWith("Invalid 'tally.clear_time' in data/strings.txt at line "
            + lineOf("tally.clear_time") + ": too long for the Intermission Tally panel by 1 character: "), time);
        assertTrue(time.contains("99:59.9 100:00.0\" is "), time);
    }

    @Test
    void aLongKeyNameStopsTheStartNamingTheLineAndTheBinding() {
        // One remapped action: the Security Hub's briefing names the alt-fire button. Its box has room for a key
        // name of 14 characters there, so "NUMPAD 1" loads, and so does "FORWARD DELETE", the longest name a key
        // has. With a zoom factor that takes two characters more to write, that name is two too many.
        KeyBindings longest = TestTuning.bindingsWith("alt_fire", "Forward Delete");
        assertDoesNotThrow(() -> require(TestTuning.stringsFor(TestTuning.bindingsWith("alt_fire", "Numpad 1"))));
        assertDoesNotThrow(() -> require(TestTuning.stringsFor(longest)));
        UiStrings remapped = UiStrings.parse(UiStrings.PATH, TestTuning.text(UiStrings.PATH), TestTuning.FONT,
            longest::hint, TextValues.of(
            TestTuning.weaponsWith("rail.zoom_factor", "1.875"), TestTuning.DIFFICULTY, TestTuning.WORLD));
        assertEquals("Invalid briefing line 3 of stage 03_security_hub in levels/03-security-hub.map: too long for "
            + "the briefing's box by 2 characters: \"> HOLD FORWARD DELETE FOR THE 1.875X ZOOM OPTIC, THEN EXTRACT.\" "
            + "is 592.8 HUD units wide and 580 fit; it shows the key name of 'alt_fire' (FORWARD DELETE) from "
            + "data/bindings.txt, and a shorter key name there shortens it", rejected(remapped));

        // The Title footer names twelve actions; numpad keys for seven of them push it past a 4:3 window.
        UiStrings numpad = TestTuning.stringsFor(TestTuning.bindingsWith("move_forward", "Numpad 8", "move_left",
            "Numpad 4", "move_back", "Numpad 5", "move_right", "Numpad 6", "weapon_1", "Numpad 1", "weapon_3",
            "Numpad 3", "quick_swap", "Numpad 0"));
        String footer = rejected(numpad);
        assertTrue(footer.startsWith("Invalid 'screen.title.footer' in data/strings.txt at line "
            + lineOf("screen.title.footer") + ": too long for a 4:3 window by 14 characters: "
            + "\"NUMPAD 8NUMPAD 4NUMPAD 5NUMPAD 6: MOVE  SPACE: JUMP"), footer);
        assertTrue(footer.contains("; it shows the key names of 'move_forward' (NUMPAD 8), 'move_left' (NUMPAD 4), "),
            footer);
        assertTrue(footer.endsWith("'alt_fire' (RMB) from data/bindings.txt, and a shorter key name there shortens "
            + "it"), footer);

        // A long name that every line has room for is no error.
        assertDoesNotThrow(() -> require(TestTuning.stringsFor(TestTuning.bindingsWith("dash", "Numpad 1"))));
    }
}

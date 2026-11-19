package io.github.brantunger.gamex;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import ch.qos.logback.classic.Level;
import io.github.brantunger.gamex.hud.MenuState;
import io.github.brantunger.gamex.player.Difficulty;
import io.github.brantunger.gamex.player.PlayerInput;
import io.github.brantunger.gamex.player.PlayerLoadout;
import io.github.brantunger.gamex.settings.GameSettings;
import io.github.brantunger.gamex.settings.MapKeyValueStore;
import io.github.brantunger.gamex.settings.RunStats;
import io.github.brantunger.gamex.settings.SaveData;
import io.github.brantunger.gamex.world.GameMap;
import io.github.brantunger.gamex.world.Levels;
import org.junit.jupiter.api.Test;

import java.util.List;

/** Checks what the game logs at its milestones, and that a frame with nothing new logs nothing. */
class GameLoggingTest {

    private static final float FRAME = 1f / 60f;

    private static GameSimulation tinyRange() {
        return new GameSimulation(GameMap.parse(
            "#######",
            "#>.o..#",
            "#######"), PlayerLoadout.fresh(), true);
    }

    @Test
    void clearingALevelLogsItOnceAtInfoAndTheKillAtDebug() {
        try (LogCapture capture = LogCapture.of(GameSimulation.class, Level.DEBUG)) {
            GameSimulation simulation = tinyRange();
            PlayerInput input = new PlayerInput();
            input.fire = true;
            for (int i = 0; i < 50; i++) simulation.update(FRAME, input);
            assertTrue(simulation.isLevelCleared());

            List<String> info = capture.messages(Level.INFO);
            assertEquals(1, info.size(), "one line for the cleared level: " + info);
            assertTrue(info.get(0).startsWith("Level cleared in "), info.get(0));
            assertTrue(info.get(0).contains("destroying everything"), info.get(0));

            List<String> debug = capture.messages(Level.DEBUG);
            assertEquals(1, debug.stream().filter(line -> line.startsWith("Destroyed target CANISTER")).count(),
                "one line for the canister: " + debug);
        }
    }

    @Test
    void framesWithNoEventLogNothing() {
        // A stage with an exit, hostiles, a keycard bulkhead and a console, so the objective is worked out.
        GameSimulation simulation = new GameSimulation(Levels.stage("04_lockdown_wing").map, PlayerLoadout.fresh(),
            false);
        PlayerInput input = new PlayerInput();
        assertNotNull(simulation.getObjectiveText(), "the stage has an objective");
        try (LogCapture capture = LogCapture.of(GameSimulation.class, Level.TRACE)) {
            for (int i = 0; i < 120; i++) {
                simulation.update(FRAME, input);
                simulation.getObjectiveText();
                simulation.pause();
            }
            assertFalse(simulation.player.isDead(), "nothing happened to the player");
            for (Level level : new Level[] {Level.TRACE, Level.DEBUG, Level.INFO, Level.WARN, Level.ERROR}) {
                assertEquals(List.of(), capture.messages(level), "idle frames log nothing at " + level);
            }
        }
    }

    @Test
    void theObjectiveIsLoggedWhenItChangesAndNotWhenItIsWorkedOutAgain() {
        // A plain bulkhead stands between the player and the exit; opening it does not change the objective.
        GameSimulation simulation = new GameSimulation(GameMap.parse(
            "#########",
            "#>.=...X#",
            "#########"));
        simulation.player.position.set(2.5f, 1.5f);
        PlayerInput input = new PlayerInput();
        try (LogCapture capture = LogCapture.of(GameSimulation.class, Level.DEBUG)) {
            String objective = simulation.getObjectiveText();
            int floods = simulation.getObjectiveFloodCount();
            assertEquals(List.of("Objective changed to '" + objective + "'"), capture.messages(Level.DEBUG));

            input.interact = true;
            simulation.update(FRAME, input);
            input.interact = false;
            assertTrue(simulation.doors.get(0).isOpening(), "the bulkhead opens");
            for (int i = 0; i < 30; i++) {
                simulation.update(FRAME, input);
                assertEquals(objective, simulation.getObjectiveText());
            }

            assertTrue(simulation.getObjectiveFloodCount() > floods, "the objective was worked out again");
            assertEquals(List.of("Objective changed to '" + objective + "'"), capture.messages(Level.DEBUG),
                "working out the same objective again logs nothing");
        }
    }

    @Test
    void aSettingAtTheEndOfItsRangeLogsNoChange() {
        GameSettings settings = new GameSettings();
        settings.setFov(GameSettings.MAX_FOV);
        settings.setMouseSensitivity(GameSettings.MIN_SENSITIVITY);
        settings.setMasterVolume(GameSettings.MAX_MASTER_VOLUME);
        MenuState menu = new MenuState(settings, new SaveData(new MapKeyValueStore()), MenuState.Screen.TITLE);
        menu.moveSelection(3);
        menu.confirm();
        assertEquals(MenuState.Screen.SETTINGS, menu.getScreen());
        try (LogCapture capture = LogCapture.of(MenuState.class, Level.DEBUG)) {
            menu.adjustHorizontal(-1);
            menu.setSelectedIndex(2);
            menu.adjustHorizontal(1);
            menu.setSelectedIndex(4);
            menu.adjustHorizontal(1);
            List<String> changes = capture.messages(Level.DEBUG).stream()
                .filter(line -> line.startsWith("Setting changed")).toList();
            assertEquals(List.of(), changes, "a value held at its limit did not change");

            menu.adjustHorizontal(-1);
            changes = capture.messages(Level.DEBUG).stream()
                .filter(line -> line.startsWith("Setting changed")).toList();
            assertEquals(List.of("Setting changed: master volume " + settings.getMasterVolume()), changes);
        }
    }

    @Test
    void menuLogsScreensAtInfoAndNavigationAndSettingsAtDebug() {
        try (LogCapture capture = LogCapture.of(MenuState.class, Level.DEBUG)) {
            GameSettings settings = new GameSettings();
            MenuState menu = new MenuState(settings, new SaveData(new MapKeyValueStore()), MenuState.Screen.TITLE);
            menu.moveSelection(1);
            // Reading the options, as the HUD does on every frame, is not an event.
            for (int i = 0; i < 10; i++) menu.getOptions();
            menu.moveSelection(1);
            menu.moveSelection(1);
            assertEquals("SETTINGS", menu.getOptions().get(menu.getSelectedIndex()));
            menu.confirm();
            menu.moveSelection(2);
            String fovRow = menu.getOptions().get(2);
            menu.adjustHorizontal(1);

            assertEquals(List.of("Screen TITLE shown", "Screen SETTINGS shown after TITLE"),
                capture.messages(Level.INFO));
            assertEquals(List.of(
                "Menu TITLE moved to 'SELECT STAGE'",
                "Menu TITLE moved to 'TRAINING RANGE'",
                "Menu TITLE moved to 'SETTINGS'",
                "Menu TITLE confirmed 'SETTINGS'",
                "Menu SETTINGS moved to '" + fovRow + "'",
                "Setting changed: field of view " + settings.getFov()), capture.messages(Level.DEBUG));
        }
    }

    @Test
    void recordingARunLogsTheBestTimeAndTheMedalsAtInfo() {
        try (LogCapture capture = LogCapture.of(SaveData.class, Level.INFO)) {
            SaveData save = new SaveData(new MapKeyValueStore());
            RunStats stats = new RunStats(30f, 4, 4, 0f, 2, 2, 10, 10);
            SaveData.CompletionSummary summary = save.recordCompletion("stage-x", Difficulty.NORMAL, 60f, stats, true);

            List<String> info = capture.messages(Level.INFO);
            assertTrue(info.contains("First best time on stage-x on NORMAL: 30.0 s"), info.toString());
            assertTrue(!summary.newMedals.isEmpty(), "the run earns medals");
            assertTrue(info.contains("Medals awarded on stage-x on NORMAL: " + summary.newMedals), info.toString());

            save.recordCompletion("stage-x", Difficulty.NORMAL, 60f, new RunStats(20f, 4, 4, 0f, 2, 2, 10, 10), true);
            assertTrue(capture.messages(Level.INFO).contains("New best time on stage-x on NORMAL: 20.0 s, was 30.0 s"),
                capture.messages(Level.INFO).toString());
        }
    }

    @Test
    void aSavedRunWithAnUnknownDifficultyWarnsAndReadsAsNormal() {
        MapKeyValueStore store = new MapKeyValueStore();
        SaveData save = new SaveData(store);
        save.saveCampaignRun(1, PlayerLoadout.fresh(Difficulty.HARD));
        store.putString("run.difficulty", "IMPOSSIBLE");
        try (LogCapture capture = LogCapture.of(SaveData.class, Level.WARN)) {
            assertEquals(Difficulty.NORMAL, save.getSavedLoadout().difficulty);
            assertEquals(List.of("The saved run has an unknown difficulty 'IMPOSSIBLE'; using NORMAL"),
                capture.messages(Level.WARN));
        }
    }
}

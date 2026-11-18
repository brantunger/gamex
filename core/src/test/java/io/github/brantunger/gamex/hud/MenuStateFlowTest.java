package io.github.brantunger.gamex.hud;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import io.github.brantunger.gamex.player.Difficulty;
import io.github.brantunger.gamex.player.PlayerLoadout;
import io.github.brantunger.gamex.settings.GameSettings;
import io.github.brantunger.gamex.settings.MapKeyValueStore;
import io.github.brantunger.gamex.settings.SaveData;
import io.github.brantunger.gamex.weapon.WeaponSlot;
import io.github.brantunger.gamex.world.Levels;
import org.junit.jupiter.api.Test;

import java.util.List;

/** Covers the menu flows around a run and every row of the saved-run (CONTINUE) table. */
class MenuStateFlowTest {

    /** A saved run that no default loadout matches, so an overwrite or a fallback is visible. */
    private static final PlayerLoadout OLD_RUN = new PlayerLoadout(37f, 55f, Difficulty.HARD, true, 20, true, 7,
        WeaponSlot.RAIL_LANCE);
    private static final int OLD_RUN_STAGE = 4;

    private static int indexOf(MenuState menu, String prefix) {
        List<String> options = menu.getOptions();
        for (int i = 0; i < options.size(); i++) {
            if (options.get(i).startsWith(prefix)) return i;
        }
        throw new AssertionError("no option " + prefix + " in " + options);
    }

    private static MenuState.Command choose(MenuState menu, String prefix) {
        menu.setSelectedIndex(indexOf(menu, prefix));
        return menu.confirm();
    }

    private static MenuState titleMenu(MapKeyValueStore store) {
        return new MenuState(new GameSettings(), new SaveData(store), MenuState.Screen.TITLE);
    }

    private static MapKeyValueStore storeWithOldRun() {
        MapKeyValueStore store = new MapKeyValueStore();
        new SaveData(store).saveCampaignRun(OLD_RUN_STAGE, OLD_RUN);
        return store;
    }

    private static void assertLoadout(PlayerLoadout expected, PlayerLoadout actual, String message) {
        assertEquals(expected.health, actual.health, 1e-4f, message + ": health");
        assertEquals(expected.armor, actual.armor, 1e-4f, message + ": armor");
        assertEquals(expected.difficulty, actual.difficulty, message + ": difficulty");
        assertEquals(expected.hasScattergun, actual.hasScattergun, message + ": scattergun");
        assertEquals(expected.shells, actual.shells, message + ": shells");
        assertEquals(expected.hasRailLance, actual.hasRailLance, message + ": rail lance");
        assertEquals(expected.slugs, actual.slugs, message + ": slugs");
        assertEquals(expected.activeWeapon, actual.activeWeapon, message + ": weapon");
    }

    private static void assertSavedRun(MapKeyValueStore store, int stageIndex, PlayerLoadout loadout,
                                       String message) {
        SaveData save = new SaveData(store);
        assertTrue(save.hasSavedRun(), message + ": a run is saved");
        assertEquals(stageIndex, save.getSavedStageIndex(), message + ": saved stage");
        assertLoadout(loadout, save.getSavedLoadout(), message);
    }

    // ---- Saved-run table ----

    @Test
    void difficultyPickFromStartCampaignLeavesTheSavedRunUnchangedWhenBackingOut() {
        MapKeyValueStore store = storeWithOldRun();
        MenuState menu = titleMenu(store);

        choose(menu, "START CAMPAIGN");
        choose(menu, "NORMAL");
        assertEquals(MenuState.Screen.BRIEFING, menu.getScreen());
        assertSavedRun(store, OLD_RUN_STAGE, OLD_RUN, "on the briefing, before DEPLOY");

        menu.escape();
        assertEquals(MenuState.Screen.TITLE, menu.getScreen());
        assertSavedRun(store, OLD_RUN_STAGE, OLD_RUN, "after Esc from the briefing");
    }

    @Test
    void difficultyPickFromStageSelectLeavesTheSavedRunUnchangedWhenBackingOut() {
        MapKeyValueStore store = storeWithOldRun();
        MenuState menu = titleMenu(store);

        choose(menu, "SELECT STAGE");
        menu.setSelectedIndex(0);
        menu.confirm();
        choose(menu, "EASY");
        assertEquals(MenuState.Screen.BRIEFING, menu.getScreen());
        assertSavedRun(store, OLD_RUN_STAGE, OLD_RUN, "on the briefing, before DEPLOY");

        choose(menu, "RETURN TO TITLE");
        assertEquals(MenuState.Screen.TITLE, menu.getScreen());
        assertSavedRun(store, OLD_RUN_STAGE, OLD_RUN, "after RETURN TO TITLE from the briefing");
    }

    @Test
    void difficultyPickAloneCreatesNoSavedRun() {
        MapKeyValueStore store = new MapKeyValueStore();
        MenuState menu = titleMenu(store);

        choose(menu, "START CAMPAIGN");
        choose(menu, "HARD");
        assertFalse(new SaveData(store).hasSavedRun(), "nothing is saved before DEPLOY");
        menu.escape();
        assertFalse(menu.getOptions().get(0).startsWith("CONTINUE"), "no CONTINUE row after backing out");
    }

    @Test
    void deployOnTheBriefingOfANewRunWritesTheStageAndItsEntryLoadout() {
        MapKeyValueStore store = storeWithOldRun();
        MenuState menu = titleMenu(store);

        choose(menu, "SELECT STAGE");
        menu.setSelectedIndex(1);
        menu.confirm();
        choose(menu, "EASY");
        assertEquals(MenuState.Command.LAUNCH_STAGE, choose(menu, "DEPLOY"));

        assertSavedRun(store, 1, PlayerLoadout.forStage(1, Difficulty.EASY), "after DEPLOY");
        assertEquals(OLD_RUN_STAGE, new SaveData(store).getUnlockedStageIndex(),
            "deploying a lower stage does not lower progress");
    }

    @Test
    void clearingANonFinalStageSavesTheNextStageWithTheCarryOverAtOnce() {
        PlayerLoadout carry = new PlayerLoadout(40f, 30f, Difficulty.NORMAL, true, 9, WeaponSlot.SCATTERGUN);
        // Each way of leaving the tally without NEXT STAGE; "close" leaves the window shut on the tally.
        for (String exit : new String[] {"close", "escape", "RETURN TO TITLE"}) {
            MapKeyValueStore store = new MapKeyValueStore();
            MenuState menu = titleMenu(store);
            choose(menu, "START CAMPAIGN");
            choose(menu, "NORMAL");
            choose(menu, "DEPLOY");
            menu.onStageCleared(carry, 40f, 9, 9, 60f);
            assertEquals(MenuState.Screen.INTERMISSION, menu.getScreen());
            assertSavedRun(store, 1, carry, "right after the clear");
            assertEquals(1, new SaveData(store).getUnlockedStageIndex(), "stage 02 unlocks on the clear");

            if ("escape".equals(exit)) menu.escape();
            else if (!"close".equals(exit)) choose(menu, exit);
            assertSavedRun(store, 1, carry, "after leaving the tally by " + exit);

            MenuState relaunched = titleMenu(store);
            assertEquals(MenuState.Command.LAUNCH_STAGE, choose(relaunched, "CONTINUE"));
            assertEquals("02_coolant_core", relaunched.getActiveStage().id, "CONTINUE after " + exit);
            assertLoadout(carry, relaunched.getActiveLoadout(), "CONTINUE after " + exit);
        }
    }

    @Test
    void nextStageBriefingAndDeployKeepTheSavedRunAtTheNextStageWithTheCarryOver() {
        MapKeyValueStore store = new MapKeyValueStore();
        MenuState menu = titleMenu(store);
        choose(menu, "START CAMPAIGN");
        choose(menu, "NORMAL");
        choose(menu, "DEPLOY");
        PlayerLoadout carry = new PlayerLoadout(64f, 12f, Difficulty.NORMAL, true, 5, WeaponSlot.SCATTERGUN);
        menu.onStageCleared(carry, 40f, 9, 9, 36f);

        assertEquals(MenuState.Command.NONE, choose(menu, "NEXT STAGE"));
        assertEquals(MenuState.Screen.BRIEFING, menu.getScreen());
        assertSavedRun(store, 1, carry, "on the next stage's briefing");

        assertEquals(MenuState.Command.LAUNCH_STAGE, choose(menu, "DEPLOY"));
        assertSavedRun(store, 1, carry, "after DEPLOY of the next stage");
        assertLoadout(carry, menu.getActiveLoadout(), "the next stage starts with the carry-over");
    }

    @Test
    void playAgainIsASideTripThatKeepsTheSavedRunAndRestoresTheEntryLoadout() {
        PlayerLoadout entry = new PlayerLoadout(62f, 18f, Difficulty.HARD, true, 11, WeaponSlot.SCATTERGUN);
        MapKeyValueStore store = new MapKeyValueStore();
        new SaveData(store).saveCampaignRun(1, entry);
        MenuState menu = titleMenu(store);
        choose(menu, "CONTINUE");
        assertEquals("02_coolant_core", menu.getActiveStage().id);

        PlayerLoadout firstCarry = new PlayerLoadout(25f, 5f, Difficulty.HARD, true, 3, WeaponSlot.SCATTERGUN);
        menu.onStageCleared(firstCarry, 50f, 8, 8, 75f);
        assertSavedRun(store, 2, firstCarry, "after the first clear");

        assertEquals(MenuState.Command.LAUNCH_STAGE, choose(menu, "PLAY AGAIN"));
        assertEquals(MenuState.Screen.PLAYING, menu.getScreen());
        assertEquals("02_coolant_core", menu.getActiveStage().id, "the replay is of the cleared stage");
        assertLoadout(entry, menu.getActiveLoadout(), "the replay starts from the stage's entry loadout");
        assertSavedRun(store, 2, firstCarry, "PLAY AGAIN leaves the saved run at the next stage");

        // Clearing the replay re-saves the next stage with the new carry-over.
        PlayerLoadout secondCarry = new PlayerLoadout(91f, 44f, Difficulty.HARD, true, 14, WeaponSlot.SCATTERGUN);
        menu.onStageCleared(secondCarry, 45f, 8, 8, 9f);
        assertSavedRun(store, 2, secondCarry, "after clearing the replay");

        // A second replay still starts from the original entry loadout, not from a carry-over.
        assertEquals(MenuState.Command.LAUNCH_STAGE, choose(menu, "PLAY AGAIN"));
        assertLoadout(entry, menu.getActiveLoadout(), "the second replay starts from the same entry loadout");
        assertSavedRun(store, 2, secondCarry, "the second PLAY AGAIN leaves the saved run alone");

        // NEXT STAGE after a replay moves on with the latest carry-over.
        menu.onStageCleared(secondCarry, 45f, 8, 8, 9f);
        choose(menu, "NEXT STAGE");
        assertEquals("03_security_hub", menu.getActiveStage().id);
        assertLoadout(secondCarry, menu.getActiveLoadout(), "the next stage after a replay");
    }

    @Test
    void playAgainAfterADifficultyPickReplaysFromThatPicksLoadout() {
        MapKeyValueStore store = new MapKeyValueStore();
        new SaveData(store).unlockStage(1);
        MenuState menu = titleMenu(store);
        choose(menu, "SELECT STAGE");
        menu.setSelectedIndex(1);
        menu.confirm();
        choose(menu, "HARD");
        choose(menu, "DEPLOY");

        PlayerLoadout carry = new PlayerLoadout(19f, 3f, Difficulty.HARD, true, 2, WeaponSlot.PISTOL);
        menu.onStageCleared(carry, 50f, 8, 8, 81f);
        assertEquals(MenuState.Command.LAUNCH_STAGE, choose(menu, "PLAY AGAIN"));

        assertEquals("02_coolant_core", menu.getActiveStage().id);
        assertLoadout(PlayerLoadout.forStage(1, Difficulty.HARD), menu.getActiveLoadout(), "the replay");
        assertSavedRun(store, 2, carry, "PLAY AGAIN leaves the saved run at the next stage");
    }

    @Test
    void playAgainAfterNextStageReplaysFromTheCarryOverThatStageWasEnteredWith() {
        MapKeyValueStore store = new MapKeyValueStore();
        MenuState menu = titleMenu(store);
        choose(menu, "START CAMPAIGN");
        choose(menu, "NORMAL");
        choose(menu, "DEPLOY");
        PlayerLoadout carryA = new PlayerLoadout(58f, 22f, Difficulty.NORMAL, true, 7, WeaponSlot.SCATTERGUN);
        menu.onStageCleared(carryA, 40f, 9, 9, 42f);
        choose(menu, "NEXT STAGE");
        choose(menu, "DEPLOY");
        PlayerLoadout carryB = new PlayerLoadout(14f, 0f, Difficulty.NORMAL, true, 1, WeaponSlot.PISTOL);
        menu.onStageCleared(carryB, 50f, 8, 8, 44f);

        assertEquals(MenuState.Command.LAUNCH_STAGE, choose(menu, "PLAY AGAIN"));

        assertEquals("02_coolant_core", menu.getActiveStage().id);
        assertLoadout(carryA, menu.getActiveLoadout(), "the replay starts as stage 02 was entered");
        assertSavedRun(store, 2, carryB, "the saved run is stage 03 with the first clear's carry-over");
    }

    @Test
    void playAgainInTheTrainingRangeAfterACampaignRunStartsFresh() {
        MapKeyValueStore store = storeWithOldRun();
        MenuState menu = titleMenu(store);
        choose(menu, "CONTINUE");
        menu.escape();
        choose(menu, "RETURN TO TITLE");

        choose(menu, "TRAINING RANGE");
        choose(menu, "DEPLOY");
        menu.onStageCleared(new PlayerLoadout(12f, 0f, Difficulty.NORMAL), 30f, 6, 6, 88f);
        assertEquals(MenuState.Command.LAUNCH_STAGE, choose(menu, "PLAY AGAIN"));

        assertEquals(Levels.trainingRange().id, menu.getActiveStage().id);
        assertLoadout(PlayerLoadout.fresh(Difficulty.NORMAL), menu.getActiveLoadout(),
            "the Training Range replay does not inherit the campaign run's loadout");
        assertSavedRun(store, OLD_RUN_STAGE, OLD_RUN, "the campaign run is untouched");
    }

    @Test
    void clearingTheFinalStageClearsTheSavedRunAndPlayAgainUsesTheEntryLoadout() {
        int last = Levels.campaign().size() - 1;
        PlayerLoadout entry = new PlayerLoadout(33f, 21f, Difficulty.EASY, true, 6, true, 2, WeaponSlot.RAIL_LANCE);
        MapKeyValueStore store = new MapKeyValueStore();
        new SaveData(store).saveCampaignRun(last, entry);
        MenuState menu = titleMenu(store);
        choose(menu, "CONTINUE");
        assertEquals("06_warden_core", menu.getActiveStage().id);

        PlayerLoadout carry = new PlayerLoadout(80f, 0f, Difficulty.EASY, true, 1, true, 1, WeaponSlot.SCATTERGUN);
        menu.onStageCleared(carry, 88f, 10, 10, 22f);
        assertTrue(menu.isCampaignCompleted());
        assertFalse(new SaveData(store).hasSavedRun(), "the final clear clears the saved run");
        assertFalse(menu.getOptions().contains("NEXT STAGE"));

        assertEquals(MenuState.Command.LAUNCH_STAGE, choose(menu, "PLAY AGAIN"));
        assertFalse(menu.isCampaignCompleted());
        assertLoadout(entry, menu.getActiveLoadout(), "the final stage replays from its entry loadout");
        assertFalse(new SaveData(store).hasSavedRun(), "PLAY AGAIN after the final stage saves nothing");
    }

    @Test
    void continueLaunchesTheSavedStageWithTheSavedLoadoutAndLeavesTheSaveAlone() {
        MapKeyValueStore store = storeWithOldRun();
        MenuState menu = titleMenu(store);
        assertEquals("CONTINUE CAMPAIGN", menu.getOptions().get(0));

        assertEquals(MenuState.Command.LAUNCH_STAGE, menu.confirm());
        assertEquals(MenuState.Screen.PLAYING, menu.getScreen());
        assertEquals(Levels.campaign().get(OLD_RUN_STAGE).id, menu.getActiveStage().id);
        assertLoadout(OLD_RUN, menu.getActiveLoadout(), "CONTINUE");
        assertSavedRun(store, OLD_RUN_STAGE, OLD_RUN, "after CONTINUE");
    }

    @Test
    void continueSurvivesAnOutOfRangeSavedStageIndex() {
        for (int stored : new int[] {999, -3}) {
            MapKeyValueStore store = storeWithOldRun();
            store.putInteger("run.stageIndex", stored);
            MenuState menu = titleMenu(store);

            assertEquals(MenuState.Command.LAUNCH_STAGE, choose(menu, "CONTINUE"));
            int expected = stored < 0 ? 0 : Levels.campaign().size() - 1;
            assertEquals(Levels.campaign().get(expected).id, menu.getActiveStage().id, "stored index " + stored);
        }
    }

    @Test
    void deathRestartsAndReturnToTitleLeaveTheSavedRunUnchanged() {
        MapKeyValueStore store = storeWithOldRun();
        MenuState menu = titleMenu(store);
        choose(menu, "CONTINUE");

        assertEquals(MenuState.Command.LAUNCH_STAGE, menu.quickRestart());
        assertSavedRun(store, OLD_RUN_STAGE, OLD_RUN, "after an R restart in play");

        menu.escape();
        assertEquals(MenuState.Command.LAUNCH_STAGE, choose(menu, "RESTART STAGE"));
        assertSavedRun(store, OLD_RUN_STAGE, OLD_RUN, "after pause RESTART");

        menu.onPlayerDied();
        assertEquals(MenuState.Screen.DEAD, menu.getScreen());
        assertSavedRun(store, OLD_RUN_STAGE, OLD_RUN, "after death");
        assertEquals(MenuState.Command.LAUNCH_STAGE, choose(menu, "RETRY"));
        assertLoadout(OLD_RUN, menu.getActiveLoadout(), "a retry starts from the entry loadout");
        assertSavedRun(store, OLD_RUN_STAGE, OLD_RUN, "after a retry");

        menu.escape();
        assertEquals(MenuState.Command.NONE, choose(menu, "RETURN TO TITLE"));
        assertEquals(MenuState.Screen.TITLE, menu.getScreen());
        assertSavedRun(store, OLD_RUN_STAGE, OLD_RUN, "after RETURN TO TITLE from pause");
    }

    @Test
    void trainingRangeNeverTouchesTheSavedRun() {
        MapKeyValueStore store = storeWithOldRun();
        MenuState menu = titleMenu(store);

        assertEquals(MenuState.Command.NONE, choose(menu, "TRAINING RANGE"));
        assertEquals(MenuState.Screen.BRIEFING, menu.getScreen());
        assertEquals(Levels.trainingRange().id, menu.getActiveStage().id);
        assertLoadout(PlayerLoadout.fresh(Difficulty.NORMAL), menu.getActiveLoadout(), "Training Range loadout");

        assertEquals(MenuState.Command.LAUNCH_STAGE, choose(menu, "DEPLOY"));
        assertSavedRun(store, OLD_RUN_STAGE, OLD_RUN, "after DEPLOY into the Training Range");

        menu.onStageCleared(new PlayerLoadout(12f, 0f, Difficulty.NORMAL), 30f, 6, 6, 88f);
        assertEquals(MenuState.Screen.INTERMISSION, menu.getScreen());
        assertNotNull(menu.getLastSummary());
        assertFalse(menu.isCampaignCompleted());
        assertFalse(menu.getOptions().contains("NEXT STAGE"), "the Training Range has no next stage");
        assertSavedRun(store, OLD_RUN_STAGE, OLD_RUN, "after clearing the Training Range");
        assertEquals(OLD_RUN_STAGE, new SaveData(store).getUnlockedStageIndex());

        assertEquals(MenuState.Command.LAUNCH_STAGE, choose(menu, "PLAY AGAIN"));
        assertLoadout(PlayerLoadout.fresh(Difficulty.NORMAL), menu.getActiveLoadout(), "Training Range replay");
        assertSavedRun(store, OLD_RUN_STAGE, OLD_RUN, "after PLAY AGAIN in the Training Range");
    }

    // ---- Menu flows ----

    @Test
    void pauseMenuResumesRestartsAndReturnsToTitle() {
        MenuState menu = new MenuState(new GameSettings(), new SaveData(new MapKeyValueStore()),
            MenuState.Screen.PLAYING);

        menu.escape();
        assertEquals(MenuState.Screen.PAUSED, menu.getScreen());
        assertEquals(MenuState.Command.NONE, choose(menu, "RESUME"));
        assertEquals(MenuState.Screen.PLAYING, menu.getScreen());

        menu.escape();
        assertEquals(MenuState.Command.LAUNCH_STAGE, choose(menu, "RESTART STAGE"));
        assertEquals(MenuState.Screen.PLAYING, menu.getScreen());

        menu.escape();
        assertEquals(MenuState.Command.NONE, choose(menu, "RETURN TO TITLE"));
        assertEquals(MenuState.Screen.TITLE, menu.getScreen());
        assertEquals(MenuState.Command.NONE, menu.escape(), "Esc does nothing on the Title");
        assertEquals(MenuState.Screen.TITLE, menu.getScreen());
    }

    @Test
    void deathScreenReturnsToTitleByItsRowAndByEscape() {
        for (boolean byEscape : new boolean[] {false, true}) {
            MenuState menu = new MenuState(new GameSettings(), new SaveData(new MapKeyValueStore()),
                MenuState.Screen.PLAYING);
            menu.onPlayerDied();
            assertEquals(MenuState.Screen.DEAD, menu.getScreen());
            menu.onPlayerDied();
            assertEquals(MenuState.Screen.DEAD, menu.getScreen(), "a second death report changes nothing");

            if (byEscape) assertEquals(MenuState.Command.NONE, menu.escape());
            else assertEquals(MenuState.Command.NONE, choose(menu, "RETURN TO TITLE"));
            assertEquals(MenuState.Screen.TITLE, menu.getScreen());
        }
    }

    @Test
    void quickRestartWorksInPlayOnPauseAndOnDeathOnly() {
        MenuState menu = new MenuState(new GameSettings(), new SaveData(new MapKeyValueStore()),
            MenuState.Screen.PLAYING);
        assertEquals(MenuState.Command.LAUNCH_STAGE, menu.quickRestart());

        menu.escape();
        assertEquals(MenuState.Screen.PAUSED, menu.getScreen());
        assertEquals(MenuState.Command.LAUNCH_STAGE, menu.quickRestart());
        assertEquals(MenuState.Screen.PLAYING, menu.getScreen());

        menu.onPlayerDied();
        assertEquals(MenuState.Command.LAUNCH_STAGE, menu.quickRestart());
        assertEquals(MenuState.Screen.PLAYING, menu.getScreen());

        menu.onStageCleared(PlayerLoadout.fresh(), 30f, 1, 1, 0f);
        assertEquals(MenuState.Command.NONE, menu.quickRestart(), "no quick restart from the tally");
        assertEquals(MenuState.Screen.INTERMISSION, menu.getScreen());

        MenuState title = titleMenu(new MapKeyValueStore());
        assertEquals(MenuState.Command.NONE, title.quickRestart(), "no quick restart from the Title");
        assertEquals(MenuState.Screen.TITLE, title.getScreen());
    }

    @Test
    void onWindowPausedPausesPlayAndLeavesMenusAlone() {
        MenuState menu = new MenuState(new GameSettings(), new SaveData(new MapKeyValueStore()),
            MenuState.Screen.PLAYING);
        menu.onWindowPaused();
        assertEquals(MenuState.Screen.PAUSED, menu.getScreen());
        assertTrue(menu.drainSoundEvents().isEmpty(), "losing the focus plays no menu sound");
        menu.onWindowPaused();
        assertEquals(MenuState.Screen.PAUSED, menu.getScreen());

        MenuState title = titleMenu(new MapKeyValueStore());
        title.onWindowPaused();
        assertEquals(MenuState.Screen.TITLE, title.getScreen());
    }

    @Test
    void fullscreenToggleReturnsApplyDisplayAndOtherSettingsDoNot() {
        GameSettings settings = new GameSettings();
        MenuState menu = new MenuState(settings, new SaveData(new MapKeyValueStore()), MenuState.Screen.TITLE);
        choose(menu, "SETTINGS");
        assertEquals(MenuState.Screen.SETTINGS, menu.getScreen());

        menu.setSelectedIndex(indexOf(menu, "FULLSCREEN"));
        assertEquals(MenuState.Command.APPLY_DISPLAY, menu.adjustHorizontal(1));
        assertTrue(settings.isFullscreen());
        assertEquals("FULLSCREEN: < ON >", menu.getOptions().get(5));
        assertEquals(MenuState.Command.APPLY_DISPLAY, menu.confirm(), "Enter on the row toggles it too");
        assertFalse(settings.isFullscreen());

        menu.setSelectedIndex(indexOf(menu, "SENSITIVITY"));
        assertEquals(MenuState.Command.NONE, menu.adjustHorizontal(1));
        assertEquals(0.13f, settings.getMouseSensitivity(), 1e-4f);
        menu.setSelectedIndex(indexOf(menu, "INVERT"));
        assertEquals(MenuState.Command.NONE, menu.adjustHorizontal(-1));
        assertTrue(settings.isInvertY());
        menu.setSelectedIndex(indexOf(menu, "VIEW BOB"));
        assertEquals(MenuState.Command.NONE, menu.adjustHorizontal(1));
        assertFalse(settings.isViewBob());
        assertEquals(MenuState.Command.NONE, menu.adjustHorizontal(0), "a zero step does nothing");
        assertFalse(settings.isViewBob());

        assertEquals(MenuState.Command.NONE, choose(menu, "BACK"));
        assertEquals(MenuState.Screen.TITLE, menu.getScreen());
        assertEquals(MenuState.Command.NONE, menu.adjustHorizontal(1), "no adjusting outside Settings");
    }

    @Test
    void quitRowQuitsAtOnceFromTitleAndPause() {
        MenuState title = titleMenu(new MapKeyValueStore());
        assertEquals(MenuState.Command.QUIT, choose(title, "QUIT"));

        MenuState paused = new MenuState(new GameSettings(), new SaveData(new MapKeyValueStore()),
            MenuState.Screen.PLAYING);
        paused.escape();
        assertEquals(MenuState.Command.QUIT, choose(paused, "QUIT"));
    }

    @Test
    void escapeFromStageSelectAndDifficultySelectGoesBackOneScreen() {
        MenuState menu = titleMenu(new MapKeyValueStore());

        choose(menu, "SELECT STAGE");
        assertEquals(MenuState.Screen.STAGE_SELECT, menu.getScreen());
        menu.escape();
        assertEquals(MenuState.Screen.TITLE, menu.getScreen());

        // Difficulty Select opened from Stage Select goes back to Stage Select, by Esc and by BACK.
        choose(menu, "SELECT STAGE");
        menu.setSelectedIndex(0);
        menu.confirm();
        assertEquals(MenuState.Screen.DIFFICULTY_SELECT, menu.getScreen());
        menu.escape();
        assertEquals(MenuState.Screen.STAGE_SELECT, menu.getScreen());
        menu.setSelectedIndex(0);
        menu.confirm();
        assertEquals(MenuState.Command.NONE, choose(menu, "BACK"));
        assertEquals(MenuState.Screen.STAGE_SELECT, menu.getScreen());
        assertEquals(MenuState.Command.NONE, choose(menu, "BACK"));
        assertEquals(MenuState.Screen.TITLE, menu.getScreen());

        // Difficulty Select opened from Start Campaign goes back to the Title.
        choose(menu, "START CAMPAIGN");
        assertEquals(MenuState.Screen.DIFFICULTY_SELECT, menu.getScreen());
        menu.escape();
        assertEquals(MenuState.Screen.TITLE, menu.getScreen());
        choose(menu, "START CAMPAIGN");
        choose(menu, "BACK");
        assertEquals(MenuState.Screen.TITLE, menu.getScreen());
    }

    @Test
    void quitKeyNeedsASecondPressWithinTheWindow() {
        MenuState menu = titleMenu(new MapKeyValueStore());
        assertFalse(menu.isQuitPromptVisible());

        assertEquals(MenuState.Command.NONE, menu.requestQuit());
        assertTrue(menu.isQuitPromptVisible(), "the first press shows the prompt");
        menu.update(MenuState.QUIT_CONFIRM_SECONDS - 0.1f);
        assertTrue(menu.isQuitPromptVisible());
        assertEquals(MenuState.Command.QUIT, menu.requestQuit());
        assertFalse(menu.isQuitPromptVisible());
    }

    @Test
    void quitKeyPromptExpiresAndIsCancelledByAScreenChange() {
        MenuState menu = titleMenu(new MapKeyValueStore());

        menu.requestQuit();
        menu.update(-5f);
        menu.update(Float.NaN);
        assertTrue(menu.isQuitPromptVisible(), "a negative or NaN frame time does not expire the prompt");
        menu.update(MenuState.QUIT_CONFIRM_SECONDS);
        assertFalse(menu.isQuitPromptVisible(), "the prompt expires after the window");
        assertEquals(MenuState.Command.NONE, menu.requestQuit(), "a press after the window starts over");

        choose(menu, "SELECT STAGE");
        assertFalse(menu.isQuitPromptVisible(), "leaving the Title cancels the pending quit");
        assertEquals(MenuState.Command.NONE, menu.requestQuit(), "the quit key does nothing on Stage Select");
        assertFalse(menu.isQuitPromptVisible());
        menu.escape();
        assertEquals(MenuState.Command.NONE, menu.requestQuit(), "back on the Title the first press only prompts");
    }

    @Test
    void quitKeyWorksOnPauseAndNotInPlayOrOnOtherScreens() {
        MenuState menu = new MenuState(new GameSettings(), new SaveData(new MapKeyValueStore()),
            MenuState.Screen.PLAYING);
        assertEquals(MenuState.Command.NONE, menu.requestQuit());
        assertEquals(MenuState.Command.NONE, menu.requestQuit(), "no quit key in play");

        menu.escape();
        menu.drainSoundEvents();
        assertEquals(MenuState.Command.NONE, menu.requestQuit());
        assertEquals(1, menu.drainSoundEvents().size(), "the first press makes one menu sound");
        assertEquals(MenuState.Command.QUIT, menu.requestQuit());

        menu.requestQuit();
        menu.escape();
        assertFalse(menu.isQuitPromptVisible(), "resuming cancels the pending quit");
        menu.onPlayerDied();
        assertEquals(MenuState.Command.NONE, menu.requestQuit());
        assertEquals(MenuState.Command.NONE, menu.requestQuit(), "no quit key on the Death screen");
    }

    @Test
    void settingsRemembersWhetherItWasOpenedFromTitleOrPause() {
        MenuState menu = titleMenu(new MapKeyValueStore());
        choose(menu, "SETTINGS");
        assertEquals(MenuState.Screen.TITLE, menu.getSettingsReturnScreen());
        menu.escape();
        assertEquals(MenuState.Screen.TITLE, menu.getScreen());

        choose(menu, "TRAINING RANGE");
        choose(menu, "DEPLOY");
        menu.escape();
        choose(menu, "SETTINGS");
        assertEquals(MenuState.Screen.PAUSED, menu.getSettingsReturnScreen());
        assertEquals(MenuState.Command.NONE, choose(menu, "BACK"));
        assertEquals(MenuState.Screen.PAUSED, menu.getScreen());
    }
}

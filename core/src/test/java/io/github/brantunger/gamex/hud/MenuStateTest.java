package io.github.brantunger.gamex.hud;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import io.github.brantunger.gamex.player.Difficulty;
import io.github.brantunger.gamex.player.Player;
import io.github.brantunger.gamex.settings.GameSettings;
import io.github.brantunger.gamex.settings.MapKeyValueStore;
import io.github.brantunger.gamex.settings.SaveData;
import org.junit.jupiter.api.Test;

class MenuStateTest {

    @Test
    void campaignFlowMovesThroughTitleDifficultyBriefingPlayPauseSettingsDeathAndTally() {
        GameSettings settings = new GameSettings();
        SaveData save = new SaveData(new MapKeyValueStore());
        MenuState menu = new MenuState(settings, save, MenuState.Screen.TITLE);

        assertEquals(MenuState.Screen.TITLE, menu.getScreen());
        assertFalse(menu.getOptions().get(0).startsWith("CONTINUE"), "no saved run yet");

        // Select START CAMPAIGN.
        assertEquals(MenuState.Command.NONE, menu.confirm());
        assertEquals(MenuState.Screen.DIFFICULTY_SELECT, menu.getScreen());
        assertEquals(1, menu.getSelectedIndex(), "defaults to Normal");

        // Move down to HARD and confirm -> enters BRIEFING and saves run.
        menu.moveSelection(1);
        assertEquals(MenuState.Command.NONE, menu.confirm());
        assertEquals(MenuState.Screen.BRIEFING, menu.getScreen());
        assertEquals(Difficulty.HARD, menu.getActiveLoadout().difficulty);
        assertTrue(save.hasSavedRun());

        // Confirm DEPLOY -> launches stage into PLAYING.
        assertEquals(MenuState.Command.LAUNCH_STAGE, menu.confirm());
        assertTrue(menu.isPlaying());

        // Esc pauses; open Settings from Pause and adjust FOV.
        menu.escape();
        assertEquals(MenuState.Screen.PAUSED, menu.getScreen());
        menu.setSelectedIndex(2); // SETTINGS
        menu.confirm();
        assertEquals(MenuState.Screen.SETTINGS, menu.getScreen());
        menu.setSelectedIndex(2); // FIELD OF VIEW
        menu.adjustHorizontal(1);
        assertEquals(75f, settings.getFov(), 1e-4f);
        menu.escape();
        assertEquals(MenuState.Screen.PAUSED, menu.getScreen());
        menu.escape();
        assertEquals(MenuState.Screen.PLAYING, menu.getScreen());

        // Player dies -> DEAD -> retry launches stage.
        menu.onPlayerDied();
        assertEquals(MenuState.Screen.DEAD, menu.getScreen());
        assertEquals(MenuState.Command.LAUNCH_STAGE, menu.confirm());
        assertEquals(MenuState.Screen.PLAYING, menu.getScreen());

        // Stage cleared -> INTERMISSION with recorded summary.
        Player survivor = new Player(1.5f, 1.5f, 0f, 75f, 25f);
        menu.onStageCleared(survivor, 35f, 10, 10, 20f);
        assertEquals(MenuState.Screen.INTERMISSION, menu.getScreen());
        assertNotNull(menu.getLastSummary());
        assertEquals('S', menu.getLastSummary().rank);
        assertFalse(menu.isCampaignCompleted(), "stage 01 of 3 does not complete campaign");
        assertTrue(menu.getOptions().get(0).startsWith("NEXT STAGE"));

        // Clear remaining stages 02 -> 03 -> Campaign Complete.
        assertEquals(MenuState.Command.NONE, menu.confirm()); // NEXT STAGE -> BRIEFING stage 02
        assertEquals("02_coolant_core", menu.getActiveStage().id);
        assertEquals(MenuState.Command.LAUNCH_STAGE, menu.confirm()); // DEPLOY
        menu.onStageCleared(survivor, 45f, 8, 8, 10f);
        assertFalse(menu.isCampaignCompleted());
        assertEquals(MenuState.Command.NONE, menu.confirm()); // NEXT STAGE -> BRIEFING stage 03
        assertEquals("03_security_hub", menu.getActiveStage().id);
        assertEquals(MenuState.Command.LAUNCH_STAGE, menu.confirm()); // DEPLOY
        menu.onStageCleared(survivor, 55f, 12, 12, 15f);
        assertTrue(menu.isCampaignCompleted());
        assertFalse(save.hasSavedRun(), "final clear clears active Continue snapshot");
        assertFalse(menu.getOptions().get(0).startsWith("NEXT STAGE"));
    }

    @Test
    void stageSelectShowsBestRanksLocksUnreachedStagesAndLaunchesUnlockedStage() {
        GameSettings settings = new GameSettings();
        SaveData save = new SaveData(new MapKeyValueStore());
        save.unlockStage(1); // Stage 01 & 02 unlocked, Stage 03 locked.
        save.recordCompletion("01_outer_breach", Difficulty.NORMAL, 40f, 50f, 8, 8, 0f);
        MenuState menu = new MenuState(settings, save, MenuState.Screen.TITLE);

        // Select SELECT STAGE (index 1 on fresh Title without Continue).
        menu.setSelectedIndex(1);
        assertEquals(MenuState.Command.NONE, menu.confirm());
        assertEquals(MenuState.Screen.STAGE_SELECT, menu.getScreen());
        assertTrue(menu.getOptions().get(0).contains("[BEST: S]"));
        assertTrue(menu.getOptions().get(1).contains("[READY]"));
        assertTrue(menu.getOptions().get(2).contains("[LOCKED]"));

        // Locked stage cannot be launched.
        menu.setSelectedIndex(2);
        assertEquals(MenuState.Command.NONE, menu.confirm());
        assertEquals(MenuState.Screen.STAGE_SELECT, menu.getScreen());

        // Unlocked stage 02 launches via DIFFICULTY_SELECT -> BRIEFING.
        menu.setSelectedIndex(1);
        menu.confirm();
        assertEquals(MenuState.Screen.DIFFICULTY_SELECT, menu.getScreen());
        assertEquals(MenuState.Command.NONE, menu.confirm()); // NORMAL -> BRIEFING
        assertEquals(MenuState.Screen.BRIEFING, menu.getScreen());
        assertEquals("02_coolant_core", menu.getActiveStage().id);
        assertEquals(100f, menu.getActiveLoadout().health, 1e-4f);
    }
}

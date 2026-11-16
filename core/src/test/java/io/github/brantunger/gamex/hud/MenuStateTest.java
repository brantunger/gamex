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
    }
}

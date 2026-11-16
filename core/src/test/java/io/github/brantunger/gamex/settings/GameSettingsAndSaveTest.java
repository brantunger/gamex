package io.github.brantunger.gamex.settings;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import io.github.brantunger.gamex.player.Difficulty;
import io.github.brantunger.gamex.player.PlayerLoadout;
import org.junit.jupiter.api.Test;

class GameSettingsAndSaveTest {

    @Test
    void settingsClampRangesAndRoundTripThroughStore() {
        MapKeyValueStore store = new MapKeyValueStore();
        GameSettings settings = new GameSettings();
        settings.setMouseSensitivity(9f);
        assertEquals(GameSettings.MAX_SENSITIVITY, settings.getMouseSensitivity(), 1e-4f);
        settings.setMouseSensitivity(0.18f);
        settings.setFov(15f);
        assertEquals(GameSettings.MIN_FOV, settings.getFov(), 1e-4f);
        settings.setFov(95f);
        settings.setInvertY(true);
        settings.setViewBob(false);
        settings.setFullscreen(true);
        settings.save(store);

        GameSettings loaded = GameSettings.load(store);
        assertEquals(0.18f, loaded.getMouseSensitivity(), 1e-4f);
        assertEquals(95f, loaded.getFov(), 1e-4f);
        assertTrue(loaded.isInvertY());
        assertFalse(loaded.isViewBob());
        assertTrue(loaded.isFullscreen());
    }

    @Test
    void saveDataPersistsCampaignSnapshotAndTracksBestTimesAndRanks() {
        MapKeyValueStore store = new MapKeyValueStore();
        SaveData save = new SaveData(store);

        assertFalse(save.hasSavedRun());
        save.saveCampaignRun(0, new PlayerLoadout(82f, 40f, Difficulty.HARD));
        assertTrue(save.hasSavedRun());
        assertEquals(82f, save.getSavedLoadout().health, 1e-4f);
        assertEquals(40f, save.getSavedLoadout().armor, 1e-4f);
        assertEquals(Difficulty.HARD, save.getSavedLoadout().difficulty);

        SaveData.CompletionSummary first = save.recordCompletion("01_outer_breach", Difficulty.HARD,
            52f, 40f, 8, 10, 25f);
        assertTrue(first.newBestTime);
        assertEquals('B', first.rank);
        assertEquals('B', first.bestRank);

        SaveData.CompletionSummary second = save.recordCompletion("01_outer_breach", Difficulty.HARD,
            34f, 40f, 10, 10, 0f);
        assertTrue(second.newBestTime);
        assertEquals('S', second.rank);
        assertEquals('S', second.bestRank);

        SaveData.CompletionSummary slower = save.recordCompletion("01_outer_breach", Difficulty.HARD,
            60f, 40f, 5, 10, 40f);
        assertFalse(slower.newBestTime);
        assertEquals(34f, slower.bestTime, 1e-4f);
        assertEquals('B', slower.rank);
        assertEquals('S', slower.bestRank, "best rank stays S");

        save.clearSavedRun();
        assertFalse(save.hasSavedRun());
    }
}

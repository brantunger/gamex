package io.github.brantunger.gamex.settings;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import io.github.brantunger.gamex.player.Difficulty;
import io.github.brantunger.gamex.player.PlayerLoadout;
import io.github.brantunger.gamex.weapon.WeaponSlot;
import org.junit.jupiter.api.Test;

class GameSettingsAndSaveTest {

    @Test
    void settingsClampRangesAndRoundTripThroughStore() {
        MapKeyValueStore store = new MapKeyValueStore();
        GameSettings settings = new GameSettings();
        assertEquals(GameSettings.DEFAULT_MASTER_VOLUME, settings.getMasterVolume(), 1e-4f);
        settings.setMouseSensitivity(9f);
        assertEquals(GameSettings.MAX_SENSITIVITY, settings.getMouseSensitivity(), 1e-4f);
        settings.setMouseSensitivity(0.18f);
        settings.setFov(15f);
        assertEquals(GameSettings.MIN_FOV, settings.getFov(), 1e-4f);
        settings.setFov(95f);
        settings.setMasterVolume(-0.5f);
        assertEquals(GameSettings.MIN_MASTER_VOLUME, settings.getMasterVolume(), 1e-4f);
        settings.setMasterVolume(2.0f);
        assertEquals(GameSettings.MAX_MASTER_VOLUME, settings.getMasterVolume(), 1e-4f);
        settings.setMasterVolume(0.80f);
        settings.adjustMasterVolume(-2);
        assertEquals(0.60f, settings.getMasterVolume(), 1e-4f);
        settings.setInvertY(true);
        settings.setViewBob(false);
        settings.setFullscreen(true);
        settings.save(store);

        GameSettings loaded = GameSettings.load(store);
        assertEquals(0.18f, loaded.getMouseSensitivity(), 1e-4f);
        assertEquals(95f, loaded.getFov(), 1e-4f);
        assertEquals(0.60f, loaded.getMasterVolume(), 1e-4f);
        assertTrue(loaded.isInvertY());
        assertFalse(loaded.isViewBob());
        assertTrue(loaded.isFullscreen());
    }

    @Test
    void saveDataPersistsCampaignSnapshotAndTracksBestTimesAndRanks() {
        MapKeyValueStore store = new MapKeyValueStore();
        SaveData save = new SaveData(store);

        assertFalse(save.hasSavedRun());
        save.saveCampaignRun(1, new PlayerLoadout(82f, 40f, Difficulty.HARD, true, 14, true, 9, WeaponSlot.RAIL_LANCE));
        assertTrue(save.hasSavedRun());
        assertEquals(82f, save.getSavedLoadout().health, 1e-4f);
        assertEquals(40f, save.getSavedLoadout().armor, 1e-4f);
        assertEquals(Difficulty.HARD, save.getSavedLoadout().difficulty);
        assertTrue(save.getSavedLoadout().hasScattergun);
        assertEquals(14, save.getSavedLoadout().shells);
        assertTrue(save.getSavedLoadout().hasRailLance);
        assertEquals(9, save.getSavedLoadout().slugs);
        assertEquals(WeaponSlot.RAIL_LANCE, save.getSavedLoadout().activeWeapon);

        PlayerLoadout s1 = PlayerLoadout.forStage(0, Difficulty.NORMAL);
        assertFalse(s1.hasScattergun);
        assertEquals(0, s1.shells);
        assertFalse(s1.hasRailLance);
        assertEquals(0, s1.slugs);
        assertEquals(WeaponSlot.PISTOL, s1.activeWeapon);

        PlayerLoadout s2 = PlayerLoadout.forStage(1, Difficulty.NORMAL);
        assertTrue(s2.hasScattergun);
        assertEquals(8, s2.shells);
        assertFalse(s2.hasRailLance);
        assertEquals(WeaponSlot.SCATTERGUN, s2.activeWeapon);

        PlayerLoadout s4 = PlayerLoadout.forStage(3, Difficulty.NORMAL);
        assertTrue(s4.hasScattergun);
        assertEquals(8, s4.shells);
        assertTrue(s4.hasRailLance);
        assertEquals(5, s4.slugs);
        assertEquals(WeaponSlot.SCATTERGUN, s4.activeWeapon,
            "Stages 04-06 start with Scattergun in hand and Rail Lance unlocked in Slot 3");

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

    @Test
    void rankForGradesEachTierFromClearTimeAndDestructionRate() {
        assertEquals('S', SaveData.rankFor(40f, 40f, 10, 10));
        assertEquals('A', SaveData.rankFor(40f, 40f, 9, 10), "on par but a target missed");
        assertEquals('A', SaveData.rankFor(50f, 40f, 8, 10), "within 125% of par with three quarters destroyed");
        assertEquals('B', SaveData.rankFor(50f, 40f, 7, 10), "within 125% of par but under three quarters");
        assertEquals('B', SaveData.rankFor(66f, 40f, 10, 10), "within 165% of par");
        assertEquals('C', SaveData.rankFor(66.5f, 40f, 10, 10), "beyond 165% of par");
        assertEquals('S', SaveData.rankFor(40f, 40f, 0, 0), "a stage with no targets counts as all destroyed");

        SaveData save = new SaveData(new MapKeyValueStore());
        SaveData.CompletionSummary slow = save.recordCompletion("02_coolant_core", Difficulty.NORMAL,
            90f, 40f, 10, 10, 5f);
        assertEquals('C', slow.rank);
        assertEquals('C', slow.bestRank);
        SaveData.CompletionSummary better = save.recordCompletion("02_coolant_core", Difficulty.NORMAL,
            48f, 40f, 8, 10, 5f);
        assertEquals('A', better.rank);
        assertEquals('A', better.bestRank);
        assertEquals('A', save.getBestRank("02_coolant_core", Difficulty.NORMAL));
        assertEquals(48f, save.getBestTime("02_coolant_core", Difficulty.NORMAL), 1e-4f);
    }

    @Test
    void bestOverallRankIsTheHighestAcrossDifficulties() {
        SaveData save = new SaveData(new MapKeyValueStore());
        assertEquals('-', save.getBestOverallRank("01_outer_breach"), "never cleared");
        assertEquals('-', save.getBestRank("01_outer_breach", Difficulty.EASY));
        assertEquals(Float.POSITIVE_INFINITY, save.getBestTime("01_outer_breach", Difficulty.EASY));

        save.recordCompletion("01_outer_breach", Difficulty.EASY, 90f, 40f, 10, 10, 0f);
        assertEquals('C', save.getBestOverallRank("01_outer_breach"));
        save.recordCompletion("01_outer_breach", Difficulty.HARD, 48f, 40f, 8, 10, 0f);
        assertEquals('A', save.getBestOverallRank("01_outer_breach"));
        save.recordCompletion("01_outer_breach", Difficulty.NORMAL, 60f, 40f, 10, 10, 0f);
        assertEquals('A', save.getBestOverallRank("01_outer_breach"), "a later B does not replace an A");
        assertEquals('B', save.getBestRank("01_outer_breach", Difficulty.NORMAL));
        assertEquals('-', save.getBestOverallRank("02_coolant_core"), "ranks are kept per stage");
    }

    @Test
    void unlockedStageNeverGoesDown() {
        SaveData save = new SaveData(new MapKeyValueStore());
        assertEquals(0, save.getUnlockedStageIndex());
        save.unlockStage(3);
        assertEquals(3, save.getUnlockedStageIndex());
        save.unlockStage(1);
        assertEquals(3, save.getUnlockedStageIndex(), "unlocking a lower stage keeps progress");
        save.saveCampaignRun(0, PlayerLoadout.fresh());
        assertEquals(3, save.getUnlockedStageIndex(), "saving a run on a lower stage keeps progress");
        save.saveCampaignRun(4, PlayerLoadout.fresh());
        assertEquals(4, save.getUnlockedStageIndex(), "saving a run on a higher stage unlocks it");
        save.clearSavedRun();
        assertEquals(4, save.getUnlockedStageIndex(), "clearing the run keeps progress");
    }
}

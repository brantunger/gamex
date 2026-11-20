package io.github.brantunger.gamex.hud;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import io.github.brantunger.gamex.TestTuning;
import io.github.brantunger.gamex.audio.SoundEvent;
import io.github.brantunger.gamex.player.Difficulty;
import io.github.brantunger.gamex.player.Player;
import io.github.brantunger.gamex.player.PlayerLoadout;
import io.github.brantunger.gamex.settings.GameSettings;
import io.github.brantunger.gamex.settings.MapKeyValueStore;
import io.github.brantunger.gamex.settings.SaveData;
import io.github.brantunger.gamex.weapon.Scattergun;
import io.github.brantunger.gamex.weapon.WeaponSlot;
import io.github.brantunger.gamex.world.Levels;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;

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

        // Move down to HARD and confirm -> enters BRIEFING; the run is not saved until DEPLOY.
        menu.moveSelection(1);
        assertEquals(MenuState.Command.NONE, menu.confirm());
        assertEquals(MenuState.Screen.BRIEFING, menu.getScreen());
        assertEquals(Difficulty.HARD, menu.getActiveLoadout().difficulty);
        assertFalse(save.hasSavedRun());

        // Confirm DEPLOY -> saves the run and launches stage into PLAYING.
        assertEquals(MenuState.Command.LAUNCH_STAGE, menu.confirm());
        assertTrue(menu.isPlaying());
        assertTrue(save.hasSavedRun());

        // Esc pauses; open Settings from Pause and adjust FOV.
        menu.escape();
        assertEquals(MenuState.Screen.PAUSED, menu.getScreen());
        assertTrue(menu.selectId(MenuState.OptionId.SETTINGS));
        menu.confirm();
        assertEquals(MenuState.Screen.SETTINGS, menu.getScreen());
        assertTrue(menu.selectId(MenuState.OptionId.SET_FOV));
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

        // Clear remaining stages 02 -> 03 -> 04 -> 05 -> 06 -> Campaign Complete.
        assertEquals(MenuState.Command.NONE, menu.confirm()); // NEXT STAGE -> BRIEFING stage 02
        assertEquals("02_coolant_core", menu.getActiveStage().id);
        assertEquals(MenuState.Command.LAUNCH_STAGE, menu.confirm()); // DEPLOY
        menu.onStageCleared(survivor, 45f, 8, 8, 10f);
        assertFalse(menu.isCampaignCompleted());
        assertEquals(MenuState.Command.NONE, menu.confirm()); // NEXT STAGE -> BRIEFING stage 03
        assertEquals("03_security_hub", menu.getActiveStage().id);
        assertEquals(MenuState.Command.LAUNCH_STAGE, menu.confirm()); // DEPLOY
        menu.onStageCleared(survivor, 55f, 12, 12, 15f);
        assertFalse(menu.isCampaignCompleted());
        assertEquals(MenuState.Command.NONE, menu.confirm()); // NEXT STAGE -> BRIEFING stage 04
        assertEquals("04_lockdown_wing", menu.getActiveStage().id);
        assertEquals(MenuState.Command.LAUNCH_STAGE, menu.confirm()); // DEPLOY
        menu.onStageCleared(survivor, 65f, 11, 11, 12f);
        assertFalse(menu.isCampaignCompleted());
        assertEquals(MenuState.Command.NONE, menu.confirm()); // NEXT STAGE -> BRIEFING stage 05
        assertEquals("05_command_vault", menu.getActiveStage().id);
        assertEquals(MenuState.Command.LAUNCH_STAGE, menu.confirm()); // DEPLOY
        menu.onStageCleared(survivor, 75f, 16, 16, 18f);
        assertFalse(menu.isCampaignCompleted());
        assertEquals(MenuState.Command.NONE, menu.confirm()); // NEXT STAGE -> BRIEFING stage 06
        assertEquals("06_warden_core", menu.getActiveStage().id);
        assertEquals(MenuState.Command.LAUNCH_STAGE, menu.confirm()); // DEPLOY
        menu.onStageCleared(survivor, 88f, 10, 10, 22f);
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

        assertTrue(menu.selectId(MenuState.OptionId.SELECT_STAGE));
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
        assertTrue(menu.getActiveLoadout().hasScattergun, "Stage 02+ direct select starts with Scattergun");
        assertEquals(TestTuning.WEAPONS.scattergunWeaponPickupShells, menu.getActiveLoadout().shells);
        assertEquals(WeaponSlot.SCATTERGUN, menu.getActiveLoadout().activeWeapon);
    }

    @Test
    void stageClearPreservesScattergunAndShellsIntoNextStageAndSaveData() {
        GameSettings settings = new GameSettings();
        SaveData save = new SaveData(new MapKeyValueStore());
        MenuState menu = new MenuState(settings, save, MenuState.Screen.TITLE);
        menu.confirm(); // START CAMPAIGN -> DIFFICULTY_SELECT
        menu.confirm(); // NORMAL -> BRIEFING 01
        menu.confirm(); // DEPLOY -> PLAYING 01
        assertFalse(menu.getActiveLoadout().hasScattergun);

        PlayerLoadout carry = new PlayerLoadout(88f, 15f, Difficulty.NORMAL, true, 12, WeaponSlot.SCATTERGUN);
        menu.onStageCleared(carry, 40f, 9, 9, 12f);
        assertEquals(MenuState.Screen.INTERMISSION, menu.getScreen());
        assertEquals(MenuState.Command.NONE, menu.confirm()); // NEXT STAGE -> BRIEFING 02
        assertTrue(menu.getActiveLoadout().hasScattergun);
        assertEquals(12, menu.getActiveLoadout().shells);
        assertEquals(WeaponSlot.SCATTERGUN, save.getSavedLoadout().activeWeapon);
    }

    @Test
    void settingsIncludesMasterVolumeAndMenuEmitsMoveConfirmAndBackSounds() {
        GameSettings settings = new GameSettings();
        SaveData save = new SaveData(new MapKeyValueStore());
        MenuState menu = new MenuState(settings, save, MenuState.Screen.TITLE);
        assertTrue(menu.drainSoundEvents().isEmpty(), "construction emits no sound");

        // Move to SETTINGS (index 3 without Continue).
        menu.moveSelection(3);
        assertEquals(MenuState.Command.NONE, menu.confirm());
        assertEquals(MenuState.Screen.SETTINGS, menu.getScreen());
        assertEquals("MASTER VOLUME: < 80% >", menu.getOptions().get(4));

        menu.setSelectedIndex(4);
        menu.adjustHorizontal(-2);
        assertEquals(0.60f, settings.getMasterVolume(), 1e-4f);
        assertEquals("MASTER VOLUME: < 60% >", menu.getOptions().get(4));
        menu.escape();
        assertEquals(MenuState.Screen.TITLE, menu.getScreen());

        List<SoundEvent> events = menu.drainSoundEvents();
        assertEquals(5, events.size());
        assertEquals(SoundEvent.Type.MENU_MOVE, events.get(0).type);
        assertEquals(SoundEvent.Type.MENU_CONFIRM, events.get(1).type);
        assertEquals(SoundEvent.Type.MENU_MOVE, events.get(2).type);
        assertEquals(SoundEvent.Type.MENU_CONFIRM, events.get(3).type);
        assertEquals(SoundEvent.Type.MENU_BACK, events.get(4).type);
        assertTrue(events.stream().noneMatch(e -> e.spatial));
        assertTrue(menu.drainSoundEvents().isEmpty());
    }

    @Test
    void intermissionReturnToTitleAfterFinalStageRestoresFirstCampaignStageOnTitle() {
        GameSettings settings = new GameSettings();
        MapKeyValueStore store = new MapKeyValueStore();
        SaveData save = new SaveData(store);
        save.saveCampaignRun(5,
            PlayerLoadout.forStage(Levels.stage("06_warden_core"), Difficulty.NORMAL, TestTuning.DIFFICULTY,
                TestTuning.WEAPONS));
        MenuState menu = new MenuState(settings, save, MenuState.Screen.TITLE);

        menu.confirm(); // CONTINUE CAMPAIGN -> Stage 06
        assertEquals("06_warden_core", menu.getActiveStage().id);
        menu.onStageCleared(new Player(1.5f, 1.5f, 0f, 80f, 20f), 70f, 10, 10, 15f);
        assertEquals(MenuState.Screen.INTERMISSION, menu.getScreen());

        menu.escape();
        assertEquals(MenuState.Screen.TITLE, menu.getScreen());
        assertEquals("01_outer_breach", menu.getActiveStage().id);
    }

    @Test
    void everyOptionCarriesTheIdOfWhatItDoesAndSettingsRowsAreToldApartById() {
        SaveData save = new SaveData(new MapKeyValueStore());
        save.saveCampaignRun(0, PlayerLoadout.fresh());
        MenuState title = new MenuState(new GameSettings(), save, MenuState.Screen.TITLE);
        List<MenuState.OptionId> ids = new ArrayList<>();
        for (MenuState.Option option : title.getMenuOptions()) {
            ids.add(option.id);
            assertEquals(MenuState.Option.NO_PAYLOAD, option.payload, option.label);
        }
        assertEquals(List.of(MenuState.OptionId.CONTINUE, MenuState.OptionId.START_CAMPAIGN,
            MenuState.OptionId.SELECT_STAGE, MenuState.OptionId.TRAINING_RANGE, MenuState.OptionId.SETTINGS,
            MenuState.OptionId.QUIT), ids);
        List<String> labels = new ArrayList<>();
        for (MenuState.Option option : title.getMenuOptions()) labels.add(option.label);
        assertEquals(labels, title.getOptions(), "the labels are those of the options, in their order");

        assertFalse(title.selectId(MenuState.OptionId.DEPLOY), "the Title has no such option");
        assertEquals(0, title.getSelectedIndex(), "and the highlight stays");
        assertTrue(title.selectId(MenuState.OptionId.SETTINGS));
        assertEquals(MenuState.OptionId.SETTINGS, title.getSelectedOption().id);
        title.confirm();

        List<MenuState.Option> rows = title.getMenuOptions();
        assertEquals(8, rows.size());
        for (int i = 0; i < rows.size(); i++) {
            assertEquals(i < rows.size() - 1, rows.get(i).id.isSetting(), rows.get(i).label);
        }
        assertEquals(MenuState.OptionId.BACK, rows.get(7).id);
        assertFalse(MenuState.OptionId.SETTINGS.isSetting(), "the option that opens Settings is not one of its rows");

        // Select Difficulty and Select Stage rows say which difficulty or stage they stand for.
        MenuState difficulty = new MenuState(new GameSettings(), save, MenuState.Screen.DIFFICULTY_SELECT);
        for (Difficulty each : Difficulty.values()) {
            MenuState.Option row = difficulty.getMenuOptions().get(each.ordinal());
            assertEquals(MenuState.OptionId.DIFFICULTY, row.id);
            assertEquals(each.ordinal(), row.payload);
        }
        assertTrue(difficulty.selectId(MenuState.OptionId.DIFFICULTY, Difficulty.HARD.ordinal()));
        assertEquals(2, difficulty.getSelectedIndex());
        assertFalse(difficulty.selectId(MenuState.OptionId.DIFFICULTY, 7));
        assertNull(new MenuState(new GameSettings(), save, MenuState.Screen.PLAYING).getSelectedOption());
    }

    @Test
    void toggleMinimapWorksOnlyDuringActivePlayAndFovReflectsSettings() {
        GameSettings settings = new GameSettings();
        settings.setFov(85f);
        MenuState menu = new MenuState(settings, new SaveData(new MapKeyValueStore()), MenuState.Screen.TITLE);
        assertEquals(85f, menu.getFov(), 0f);
        assertEquals(MenuState.Command.NONE, menu.toggleMinimap());
        assertTrue(settings.isMinimap());
        assertTrue(menu.drainSoundEvents().isEmpty());

        MenuState playing = new MenuState(settings, new SaveData(new MapKeyValueStore()), MenuState.Screen.PLAYING);
        assertEquals(MenuState.Command.SAVE_SETTINGS, playing.toggleMinimap());
        assertFalse(settings.isMinimap());
        assertFalse(playing.isMinimapEnabled());
        List<SoundEvent> sounds = playing.drainSoundEvents();
        assertEquals(1, sounds.size());
        assertEquals(SoundEvent.Type.MENU_MOVE, sounds.get(0).type);
        assertFalse(sounds.get(0).spatial);
    }
}

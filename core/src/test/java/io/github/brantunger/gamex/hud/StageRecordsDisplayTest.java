package io.github.brantunger.gamex.hud;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import io.github.brantunger.gamex.GameSimulation;
import io.github.brantunger.gamex.TestTuning;
import io.github.brantunger.gamex.player.Difficulty;
import io.github.brantunger.gamex.player.PlayerInput;
import io.github.brantunger.gamex.player.PlayerLoadout;
import io.github.brantunger.gamex.settings.KeyValueStore;
import io.github.brantunger.gamex.settings.MapKeyValueStore;
import io.github.brantunger.gamex.settings.Medal;
import io.github.brantunger.gamex.settings.RunStats;
import io.github.brantunger.gamex.settings.SaveData;
import io.github.brantunger.gamex.world.GameMap;
import io.github.brantunger.gamex.world.Levels;
import io.github.brantunger.gamex.world.Pickup;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;

/** Checks the text of best times, run statistics, medals and objectives, and that each fits where it is drawn. */
class StageRecordsDisplayTest {

    private static final String STAGE = "01_outer_breach";
    /** Room left free on each side of a line inside its panel, in HUD units. */
    private static final float PANEL_MARGIN = 20f;
    private static final UiStrings STRINGS = TestTuning.STRINGS;

    private static MenuState stageSelect(SaveData save) {
        MenuState menu = TestTuning.menu(TestTuning.settings(), save, MenuState.Screen.TITLE);
        assertTrue(menu.selectId(MenuState.OptionId.SELECT_STAGE));
        menu.confirm();
        assertEquals(MenuState.Screen.STAGE_SELECT, menu.getScreen());
        return menu;
    }

    /** A run on every difficulty of every campaign stage, with the longest times and every medal. */
    private static SaveData fullSave() {
        SaveData save = new SaveData(new MapKeyValueStore());
        List<Levels.Stage> campaign = Levels.campaign();
        save.unlockStage(campaign.size() - 1);
        for (Levels.Stage stage : campaign) {
            for (Difficulty difficulty : Difficulty.values()) {
                save.recordCompletion(stage.id, difficulty, 7000f, new RunStats(6000f, 8, 8, 0f, 9, 9, 10, 10),
                    true);
            }
            // Normal has the longest label, so make it the fastest.
            save.recordCompletion(stage.id, Difficulty.NORMAL, 7000f, new RunStats(5999f, 8, 8, 0f, 9, 9, 10, 10),
                true);
        }
        return save;
    }

    @Test
    void selectStageShowsTheFastestClearWithItsDifficultyNextToTheRank() {
        SaveData save = new SaveData(new MapKeyValueStore());
        save.unlockStage(1);
        save.recordCompletion(STAGE, Difficulty.NORMAL, 102.9f, 50f, 4, 8, 0f);
        MenuState menu = stageSelect(save);
        assertEquals("01: OUTER BREACH [BEST: C] 1:42 (NORMAL)", menu.getOptions().get(0));
        assertEquals("02: COOLANT CORE [READY]", menu.getOptions().get(1), "unlocked but never cleared: no time");
        assertEquals("03: SECURITY HUB [LOCKED]", menu.getOptions().get(2), "locked: no time");

        // A faster clear on Hard takes over the time and the tag; the rank stays the best of all difficulties.
        save.recordCompletion(STAGE, Difficulty.HARD, 61f, 50f, 8, 8, 0f);
        assertEquals("01: OUTER BREACH [BEST: A] 1:01 (HARD)", menu.getOptions().get(0));
        save.recordCompletion(STAGE, Difficulty.EASY, 75f, 50f, 8, 8, 0f);
        assertEquals("01: OUTER BREACH [BEST: A] 1:01 (HARD)", menu.getOptions().get(0), "a slower Easy clear");
    }

    @Test
    void selectStageRowOfADamagedSaveWithARankButNoTimeShowsOnlyTheRank() {
        MapKeyValueStore store = new MapKeyValueStore();
        store.putString("best." + STAGE + ".NORMAL.rank", "A");
        store.putFloat("best." + STAGE + ".NORMAL.time", Float.NaN);
        assertEquals("01: OUTER BREACH [BEST: A]", stageSelect(new SaveData(store)).getOptions().get(0));
    }

    @Test
    void shortTimeIsMinutesAndWholeSecondsRoundedDown() {
        assertEquals("0:00", TextFormat.clock(0f));
        assertEquals("0:59", TextFormat.clock(59.99f));
        assertEquals("1:42", TextFormat.clock(102.9f));
        assertEquals("12:05", TextFormat.clock(725f));
        assertEquals("99:59", TextFormat.clock(100000f), "the display stops at 99:59");
    }

    @Test
    void everySelectStageRowFitsItsBoxEvenWhenHighlighted() {
        MenuState menu = stageSelect(fullSave());
        List<String> rows = menu.getOptions();
        assertEquals("05: COMMAND VAULT [BEST: S] 99:59 (NORMAL)", rows.get(4));
        for (String row : rows) {
            String highlighted = "> " + row + " <";
            float width = PixelFont.width(highlighted, HudRenderer.OPTION_PIXEL);
            assertTrue(width <= HudRenderer.OPTION_ROW_WIDTH,
                highlighted + " is " + width + " wide, the row " + HudRenderer.OPTION_ROW_WIDTH);
        }
    }

    @Test
    void theMedalLineIsMadeWithoutBuildingTheStageRowsAgain() {
        // The HUD asks for the rows and then for the medal line on every frame; the rows read each stage's best
        // rank and time from the save, and the medal line must not read them a second time.
        MapKeyValueStore values = new MapKeyValueStore();
        List<String> read = new ArrayList<>();
        KeyValueStore recording = new KeyValueStore() {
            @Override
            public float getFloat(String key, float defaultValue) {
                read.add(key);
                return values.getFloat(key, defaultValue);
            }

            @Override
            public int getInteger(String key, int defaultValue) {
                read.add(key);
                return values.getInteger(key, defaultValue);
            }

            @Override
            public boolean getBoolean(String key, boolean defaultValue) {
                read.add(key);
                return values.getBoolean(key, defaultValue);
            }

            @Override
            public String getString(String key, String defaultValue) {
                read.add(key);
                return values.getString(key, defaultValue);
            }

            @Override
            public void putFloat(String key, float value) {
                values.putFloat(key, value);
            }

            @Override
            public void putInteger(String key, int value) {
                values.putInteger(key, value);
            }

            @Override
            public void putBoolean(String key, boolean value) {
                values.putBoolean(key, value);
            }

            @Override
            public void putString(String key, String value) {
                values.putString(key, value);
            }

            @Override
            public void flush() {
                values.flush();
            }
        };
        SaveData save = new SaveData(recording);
        save.unlockStage(5);
        save.recordCompletion(STAGE, Difficulty.HARD, 50f, new RunStats(40f, 8, 8, 0f, 0, 0, 10, 9), true);
        MenuState menu = stageSelect(save);

        read.clear();
        String firstRow = menu.getOptions().get(0);
        assertTrue(firstRow.contains("[BEST: "), firstRow);
        List<String> rowReads = new ArrayList<>(read);
        assertTrue(rowReads.stream().anyMatch(key -> key.endsWith(".time")), "the rows read the times: " + rowReads);

        read.clear();
        assertEquals("MEDALS: EASY 0/4  NORMAL 0/4  HARD 4/4", menu.getStageSelectDetail());
        for (String key : read) {
            assertTrue(key.endsWith(".medals") || !key.startsWith("best."),
                "the medal line read " + key + ", which only a stage row shows");
        }
        assertTrue(read.size() < rowReads.size(), read.size() + " reads for the medal line, " + rowReads.size()
            + " for the rows");
    }

    @Test
    void selectStageShowsTheHighlightedStagesMedalsPerDifficulty() {
        SaveData save = new SaveData(new MapKeyValueStore());
        save.unlockStage(1);
        MenuState menu = stageSelect(save);
        assertNull(menu.getStageSelectDetail(), "never cleared: no medal line");

        save.recordCompletion(STAGE, Difficulty.NORMAL, 50f, new RunStats(40f, 8, 8, 30f, 0, 0, 10, 1), true);
        save.recordCompletion(STAGE, Difficulty.HARD, 50f, new RunStats(40f, 8, 8, 0f, 0, 0, 10, 9), true);
        assertEquals("MEDALS: EASY 0/4  NORMAL 2/4  HARD 4/4", menu.getStageSelectDetail());

        assertTrue(menu.selectId(MenuState.OptionId.STAGE, 1));
        assertNull(menu.getStageSelectDetail(), "stage 02 has no medals");
        assertTrue(menu.selectId(MenuState.OptionId.STAGE, 2));
        assertNull(menu.getStageSelectDetail(), "a locked stage");
        assertTrue(menu.selectId(MenuState.OptionId.BACK));
        assertNull(menu.getStageSelectDetail(), "the BACK row");
        menu.escape();
        assertNull(menu.getStageSelectDetail(), "another screen");
    }

    @Test
    void oldSaveWithoutMedalKeysShowsItsBestTimeAndNoMedalLine() {
        MapKeyValueStore store = new MapKeyValueStore();
        store.putFloat("best." + STAGE + ".HARD.time", 44f);
        store.putString("best." + STAGE + ".HARD.rank", "S");
        store.putInteger("campaign.unlockedStage", 1);
        MenuState menu = stageSelect(new SaveData(store));

        assertEquals("01: OUTER BREACH [BEST: S] 0:44 (HARD)", menu.getOptions().get(0));
        assertNull(menu.getStageSelectDetail());
    }

    @Test
    void tallyShowsTheBestTimeOfTheDifficultyJustPlayed() {
        SaveData save = new SaveData(new MapKeyValueStore());
        SaveData.CompletionSummary first = save.recordCompletion(STAGE, Difficulty.NORMAL, 60f, 50f, 8, 8, 0f);
        assertEquals("CLEAR TIME: 01:00.0  (PAR 00:50.0)", HudRenderer.tallyTimeLine(STRINGS, first, 50f));
        assertEquals("BEST (NORMAL): 01:00.0  *NEW BEST!*",
            HudRenderer.tallyBestLine(STRINGS, first, Difficulty.NORMAL));

        SaveData.CompletionSummary slower = save.recordCompletion(STAGE, Difficulty.NORMAL, 75.5f, 50f, 8, 8, 0f);
        assertEquals("CLEAR TIME: 01:15.5  (PAR 00:50.0)", HudRenderer.tallyTimeLine(STRINGS, slower, 50f));
        assertEquals("BEST (NORMAL): 01:00.0", HudRenderer.tallyBestLine(STRINGS, slower, Difficulty.NORMAL));

        // A first clear on Hard is a new best there, although Normal holds a faster time.
        SaveData.CompletionSummary hard = save.recordCompletion(STAGE, Difficulty.HARD, 90f, 50f, 8, 8, 0f);
        assertEquals("BEST (HARD): 01:30.0  *NEW BEST!*", HudRenderer.tallyBestLine(STRINGS, hard, Difficulty.HARD));
    }

    @Test
    void tallyShowsDamagePickupsAndAccuracy() {
        SaveData save = new SaveData(new MapKeyValueStore());
        SaveData.CompletionSummary summary = save.recordCompletion(STAGE, Difficulty.NORMAL, 50f,
            new RunStats(60f, 7, 8, 35.6f, 5, 9, 13, 8), true);
        assertEquals("DAMAGE TAKEN: 36   PICKUPS: 5/9   ACCURACY: 61%", HudRenderer.tallyStatsLine(STRINGS, summary));
        assertEquals("HOSTILES CLEARED: 7/8 (88%)", HudRenderer.tallyClearedLine(STRINGS, summary, true));
        assertEquals("TARGETS CLEARED: 7/8 (88%)", HudRenderer.tallyClearedLine(STRINGS, summary, false));

        SaveData.CompletionSummary noShots = save.recordCompletion(STAGE, Difficulty.NORMAL, 50f,
            new RunStats(60f, 0, 8, 0f, 0, 9, 0, 0), true);
        assertEquals("DAMAGE TAKEN: 0   PICKUPS: 0/9   ACCURACY: --", HudRenderer.tallyStatsLine(STRINGS, noShots));
        assertFalse(noShots.medals.contains(Medal.SHARPSHOOTER));
    }

    @Test
    void tallyShowsAtLeastOneDamageSoARunDeniedUntouchedNeverShowsZero() {
        SaveData save = new SaveData(new MapKeyValueStore());
        SaveData.CompletionSummary grazed = save.recordCompletion(STAGE, Difficulty.NORMAL, 50f,
            new RunStats(60f, 8, 8, 0.3f, 0, 9, 10, 5), true);
        assertFalse(grazed.medals.contains(Medal.UNTOUCHED), "0.3 damage is not untouched");
        assertEquals("DAMAGE TAKEN: 1   PICKUPS: 0/9   ACCURACY: 50%", HudRenderer.tallyStatsLine(STRINGS, grazed));

        SaveData.CompletionSummary clean = save.recordCompletion(STAGE, Difficulty.NORMAL, 50f,
            new RunStats(60f, 8, 8, 0f, 0, 9, 10, 5), true);
        assertTrue(clean.medals.contains(Medal.UNTOUCHED));
        assertEquals("DAMAGE TAKEN: 0   PICKUPS: 0/9   ACCURACY: 50%", HudRenderer.tallyStatsLine(STRINGS, clean));
    }

    @Test
    void tallyDoesNotAddOneForFloatNoiseAboveAWholeDamage() {
        SaveData save = new SaveData(new MapKeyValueStore());
        // Five hits of 5.6 on Easy with armor sum to 28.000015, not 28.
        SaveData.CompletionSummary noisy = save.recordCompletion(STAGE, Difficulty.EASY, 50f,
            new RunStats(60f, 8, 8, 28.000015f, 0, 9, 10, 5), true);
        assertEquals("DAMAGE TAKEN: 28   PICKUPS: 0/9   ACCURACY: 50%", HudRenderer.tallyStatsLine(STRINGS, noisy));
    }

    @Test
    void tallyShowsTheDamageOfFiveShockGrateHitsOnEasyWithArmor() {
        GameSimulation sim = TestTuning.simulation(GameMap.parse(
            "#####",
            "#>.~#",
            "#####"), new PlayerLoadout(100f, 30f, Difficulty.EASY), false);
        PlayerInput input = new PlayerInput();
        input.moveForward = 1f;
        float fiveHits = 5 * TestTuning.WORLD.grateDamage * TestTuning.EASY.damageTakenScale;
        for (int i = 0; i < 600 && sim.getDamageTaken() < fiveHits - 0.5f; i++) sim.update(1f / 60f, input);
        assertEquals(fiveHits, sim.getDamageTaken(), 1e-3f, "five hits of 5.6");

        SaveData.CompletionSummary summary = new SaveData(new MapKeyValueStore()).recordCompletion(STAGE,
            Difficulty.EASY, 50f, new RunStats(60f, 8, 8, sim.getDamageTaken(), 0, 9, 10, 5), true);
        assertEquals("DAMAGE TAKEN: 28   PICKUPS: 0/9   ACCURACY: 50%", HudRenderer.tallyStatsLine(STRINGS, summary));
    }

    @Test
    void tallyListsThisRunsMedalsAndMarksTheNewOnes() {
        SaveData save = new SaveData(new MapKeyValueStore());
        SaveData.CompletionSummary none = save.recordCompletion(STAGE, Difficulty.NORMAL, 50f,
            new RunStats(80f, 7, 8, 30f, 0, 9, 10, 1), true);
        assertEquals("MEDALS: NONE", HudRenderer.tallyMedalLine(STRINGS, none));

        SaveData.CompletionSummary first = save.recordCompletion(STAGE, Difficulty.NORMAL, 50f,
            new RunStats(80f, 8, 8, 0f, 0, 9, 10, 1), true);
        assertEquals("MEDALS: *UNTOUCHED*  *SWEEP*", HudRenderer.tallyMedalLine(STRINGS, first));

        SaveData.CompletionSummary again = save.recordCompletion(STAGE, Difficulty.NORMAL, 50f,
            new RunStats(40f, 8, 8, 0f, 0, 9, 10, 9), true);
        assertEquals("MEDALS: UNTOUCHED  SWEEP  *UNDER PAR*  *SHARPSHOOTER*",
            HudRenderer.tallyMedalLine(STRINGS, again));

        SaveData.CompletionSummary kept = save.recordCompletion(STAGE, Difficulty.NORMAL, 50f,
            new RunStats(40f, 8, 8, 0f, 0, 9, 10, 9), true);
        assertEquals("MEDALS: UNTOUCHED  SWEEP  UNDER PAR  SHARPSHOOTER", HudRenderer.tallyMedalLine(STRINGS, kept));
    }

    @Test
    void trainingRangeClearRecordsNoMedalsAndTheTallySaysSo() {
        MapKeyValueStore store = new MapKeyValueStore();
        SaveData save = new SaveData(store);
        MenuState menu = TestTuning.menu(TestTuning.settings(), save, MenuState.Screen.TITLE);
        assertTrue(menu.selectId(MenuState.OptionId.TRAINING_RANGE));
        menu.confirm();
        assertEquals(MenuState.Command.LAUNCH_STAGE, menu.confirm());
        assertEquals("range", menu.getActiveStage().id);

        // Half the par the stage file gives, whatever that is: under par, so the rank is S.
        float clearTime = menu.getActiveStage().parSeconds * 0.5f;
        menu.onStageCleared(PlayerLoadout.fresh(), new RunStats(clearTime, 11, 11, 0f, 4, 12, 10, 10));

        SaveData.CompletionSummary summary = menu.getLastSummary();
        assertFalse(summary.medalsRecorded);
        assertEquals("TRAINING RUN - NO MEDALS", HudRenderer.tallyMedalLine(STRINGS, summary));
        assertTrue(save.getMedals("range", Difficulty.NORMAL).isEmpty());
        assertEquals("BEST (NORMAL): " + TextFormat.stopwatch(clearTime) + "  *NEW BEST!*",
            HudRenderer.tallyBestLine(STRINGS, summary, Difficulty.NORMAL));
        assertEquals("DAMAGE TAKEN: 0   PICKUPS: 4/12   ACCURACY: 100%", HudRenderer.tallyStatsLine(STRINGS, summary));
        assertEquals('S', summary.rank);
    }

    @Test
    void campaignClearThroughTheMenuRecordsMedalsForTheDifficultyPlayed() {
        SaveData save = new SaveData(new MapKeyValueStore());
        MenuState menu = TestTuning.menu(TestTuning.settings(), save, MenuState.Screen.PLAYING);
        assertEquals(STAGE, menu.getActiveStage().id);

        // Half the par the stage file gives, whatever that is, so the run is under par.
        menu.onStageCleared(PlayerLoadout.fresh(),
            new RunStats(menu.getActiveStage().parSeconds * 0.5f, 9, 9, 0f, 3, 9, 10, 6));

        SaveData.CompletionSummary summary = menu.getLastSummary();
        assertTrue(summary.medalsRecorded);
        assertEquals(4, summary.newMedals.size());
        assertEquals(4, save.getMedals(STAGE, Difficulty.NORMAL).size());
        assertTrue(save.getMedals(STAGE, Difficulty.HARD).isEmpty());
        assertEquals(60, summary.accuracyPercent());
    }

    @Test
    void everyTallyLineFitsThePanelAtItsLongest() {
        SaveData save = new SaveData(new MapKeyValueStore());
        // Four-digit damage, two-digit pickups, 100% accuracy, every medal new, times over ten minutes.
        SaveData.CompletionSummary summary = save.recordCompletion(STAGE, Difficulty.NORMAL, 5000f,
            new RunStats(4000f, 88, 88, 0f, 88, 88, 10, 10), true);
        SaveData.CompletionSummary hurt = save.recordCompletion("02_coolant_core", Difficulty.NORMAL, 5000f,
            new RunStats(4000f, 88, 88, 1234f, 88, 88, 10, 10), true);
        assertEquals("MEDALS: *UNTOUCHED*  *SWEEP*  *UNDER PAR*  *SHARPSHOOTER*",
            HudRenderer.tallyMedalLine(STRINGS, summary));

        float room = HudRenderer.TALLY_PANEL_WIDTH - 2f * PANEL_MARGIN;
        List<String> statLines = new ArrayList<>();
        statLines.add(HudRenderer.tallyTimeLine(STRINGS, summary, 5000f));
        statLines.add(HudRenderer.tallyBestLine(STRINGS, summary, Difficulty.NORMAL));
        statLines.add(HudRenderer.tallyClearedLine(STRINGS, summary, true));
        statLines.add(HudRenderer.tallyStatsLine(STRINGS, hurt));
        for (String line : statLines) {
            float width = PixelFont.width(line, HudRenderer.TALLY_STAT_PIXEL);
            assertTrue(width <= room, line + " is " + width + " wide, the panel leaves " + room);
        }
        for (String line : new String[] {HudRenderer.tallyMedalLine(STRINGS, summary),
            STRINGS.get(UiText.TALLY_MEDALS_TRAINING), STRINGS.get(UiText.TALLY_MEDALS_NONE)}) {
            float width = PixelFont.width(line, HudRenderer.MEDAL_PIXEL);
            assertTrue(width <= room, line + " is " + width + " wide, the panel leaves " + room);
        }
    }

    @Test
    void selectStageMedalLineFitsItsPanel() {
        MenuState menu = stageSelect(fullSave());
        String detail = menu.getStageSelectDetail();
        assertEquals("MEDALS: EASY 4/4  NORMAL 4/4  HARD 4/4", detail);
        float width = PixelFont.width(detail, HudRenderer.MEDAL_PIXEL);
        assertTrue(width <= HudRenderer.STAGE_SELECT_PANEL_WIDTH - 2f * PANEL_MARGIN, detail + " is " + width);
    }

    /**
     * @param strings the loaded text
     * @return the widest toast the text can make: every toast without a value, and the two pickup toasts with
     *     every label and a three-digit amount, which no pickup reaches
     */
    static String widestToast(UiStrings strings) {
        List<GameSimulation.Toast> toasts = new ArrayList<>();
        for (GameSimulation.ToastId id : GameSimulation.ToastId.values()) {
            if (id != GameSimulation.ToastId.PICKUP_GAIN && id != GameSimulation.ToastId.PICKUP_GRANT) {
                toasts.add(new GameSimulation.Toast(id, null, 0));
                continue;
            }
            for (Pickup.Kind kind : Pickup.Kind.values()) toasts.add(new GameSimulation.Toast(id, kind, 999));
        }
        String widest = "";
        for (GameSimulation.Toast toast : toasts) {
            String text = strings.toast(toast);
            if (text.length() > widest.length()) widest = text;
        }
        return widest;
    }

    @Test
    void theWidestToastBoxFitsTheRoomKeptAroundTheCentre() {
        String widest = widestToast(STRINGS);
        assertEquals("WARDEN DESTROYED - EXTRACTION UNSEALED", widest, "the widest toast of the tests' own text");
        float box = PixelFont.width(widest, HudRenderer.TOAST_PIXEL) + 2f * HudRenderer.TOAST_PAD_X;
        assertEquals(450.8f, box, 0.01f);
        assertTrue(box <= 2f * HudRenderer.CENTRE_HALF_WIDTH, widest + " makes a box " + box + " wide");

        // A longer toast in the file is caught by the same check.
        UiStrings longer = TestTuning.stringsWith("toast.lockdown_overridden",
            "LOCKDOWN OVERRIDDEN - ALL BULKHEADS OPEN");
        String widestNow = widestToast(longer);
        assertEquals("LOCKDOWN OVERRIDDEN - ALL BULKHEADS OPEN", widestNow);
        assertTrue(PixelFont.width(widestNow, HudRenderer.TOAST_PIXEL) + 2f * HudRenderer.TOAST_PAD_X
            > 2f * HudRenderer.CENTRE_HALF_WIDTH, "two characters more do not fit");
    }

    @Test
    void everyObjectiveLineStaysLeftOfTheCentredToastAndBossBar() {
        // On a 1280x720 window the screen is 1280 HUD units wide. The widest toast box of the tests' own text,
        // around "WARDEN DESTROYED - EXTRACTION UNSEALED", starts 414.6 units in; the boss bar's panel starts at 438.
        float widestToast = PixelFont.width(widestToast(STRINGS), HudRenderer.TOAST_PIXEL)
            + 2f * HudRenderer.TOAST_PAD_X;
        float toastLeft = 640f - widestToast * 0.5f;
        assertEquals(414.6f, toastLeft, 0.01f);
        float longest = 0f;
        for (GameSimulation.Objective objective : GameSimulation.Objective.values()) {
            String line = STRINGS.objectiveLine(objective);
            float right = HudRenderer.STATUS_LEFT + PixelFont.width(line, HudRenderer.OBJECTIVE_PIXEL);
            assertTrue(right <= toastLeft, line + " ends at " + right + ", the toast starts at " + toastLeft);
            longest = Math.max(longest, right);
        }
        assertEquals(409.6f, longest, 0.01f, "where the longest objective line of the tests' own text ends");
    }

    @Test
    void everyObjectiveLineClearsTheBossBarAndTheToastsAtEveryAspectRatio() {
        String widestToast = widestToast(STRINGS);
        // Window sizes at 4:3 (twice), 3:2, 16:10, 16:9 (twice) and 21:9, and whether the upper row has room.
        int[][] windows = {
            {800, 600, 0}, {1024, 768, 0}, {1080, 720, 0}, {1152, 720, 0}, {1280, 720, 1}, {1920, 1080, 1},
            {1680, 720, 1}
        };
        for (int[] window : windows) {
            float width = window[0];
            float height = window[1];
            float unit = height / 720f;
            String size = window[0] + "x" + window[1];

            // Left edges of the centred boxes, and the vertical span each one covers, as HudRenderer draws them.
            float bossLeft = (width - HudRenderer.BOSS_BAR_WIDTH * unit) * 0.5f - HudRenderer.BOSS_PANEL_PAD_X * unit;
            float bossBottom = height - (HudRenderer.BOSS_BAR_BOTTOM + HudRenderer.BOSS_PANEL_PAD_Y) * unit;
            float toastBoxWidth = PixelFont.width(widestToast,
                HudRenderer.TOAST_PIXEL * unit) + 2f * HudRenderer.TOAST_PAD_X * unit;
            float toastLeft = (width - toastBoxWidth) * 0.5f;
            float toastHeight = PixelFont.height(HudRenderer.TOAST_PIXEL * unit) + 2f * HudRenderer.TOAST_PAD_Y * unit;
            float toastBottom = height - HudRenderer.TOAST_BOTTOM * unit;
            float lowToastBottom = height - HudRenderer.TOAST_BOTTOM_UNDER_BOSS * unit;
            float reserved = width * 0.5f - HudRenderer.CENTRE_HALF_WIDTH * unit;
            assertTrue(reserved <= bossLeft && reserved <= toastLeft, size + " keeps room for both centred boxes");

            float bottom = HudRenderer.objectiveBottomY(width, height, unit);
            float top = bottom + PixelFont.height(HudRenderer.OBJECTIVE_PIXEL * unit);
            float statusBottom = height - HudRenderer.STATUS_TOP * unit
                - PixelFont.height(HudRenderer.STATUS_PIXEL * unit);
            boolean upperRow = bottom > lowToastBottom + toastHeight;
            assertTrue(top < statusBottom, size + " objective line is below the status row");

            for (GameSimulation.Objective objective : GameSimulation.Objective.values()) {
                String text = STRINGS.objectiveLine(objective);
                float right = HudRenderer.STATUS_LEFT * unit
                    + PixelFont.width(text, HudRenderer.OBJECTIVE_PIXEL * unit);
                String line = size + " " + text + " ends at " + right;
                assertTrue(right <= width - HudRenderer.STATUS_LEFT * unit, line + " in a window of " + width);
                assertTrue(right <= HudRenderer.OBJECTIVE_RIGHT_LIMIT * unit, line + ", past the room it is given");
                // Each box is either wholly above or below the line, or starts to the right of it.
                assertTrue(top <= bossBottom || right <= bossLeft, line + ", the boss bar starts at " + bossLeft);
                assertTrue(top <= toastBottom || bottom >= toastBottom + toastHeight || right <= toastLeft,
                    line + ", the toast starts at " + toastLeft);
                assertTrue(top <= lowToastBottom || bottom >= lowToastBottom + toastHeight || right <= toastLeft,
                    line + ", the toast under the boss bar starts at " + toastLeft);
            }
            assertEquals(window[2] == 1, upperRow, size + " objective row");
        }
    }
}

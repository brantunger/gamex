package io.github.brantunger.gamex.settings;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import io.github.brantunger.gamex.player.Difficulty;
import org.junit.jupiter.api.Test;

import java.util.EnumSet;
import java.util.Set;

/** Checks when each medal is awarded and that earned medals are kept per stage and difficulty. */
class MedalTest {

    private static final String STAGE = "01_outer_breach";
    private static final float PAR = 50f;

    /** A run that earns no medal: hurt, one hostile left, over par, 1 hit in 10 shots. */
    private static RunStats plainRun() {
        return new RunStats(80f, 7, 8, 30f, 2, 9, 10, 1);
    }

    private static Set<Medal> earned(float clearTime, int destroyed, int total, float damageTaken, int shotsFired,
                                     int shotsHit) {
        return Medal.earnedBy(new RunStats(clearTime, destroyed, total, damageTaken, 0, 0, shotsFired, shotsHit),
            PAR);
    }

    @Test
    void plainRunEarnsNothing() {
        assertTrue(Medal.earnedBy(plainRun(), PAR).isEmpty());
    }

    @Test
    void untouchedNeedsARunWithoutAnyDamage() {
        assertEquals(EnumSet.of(Medal.UNTOUCHED), earned(80f, 7, 8, 0f, 10, 1));
        assertTrue(earned(80f, 7, 8, 0.5f, 10, 1).isEmpty(), "half a point of damage is damage");
    }

    @Test
    void sweepNeedsEveryHostileDestroyed() {
        assertEquals(EnumSet.of(Medal.SWEEP), earned(80f, 8, 8, 30f, 10, 1));
        assertTrue(earned(80f, 7, 8, 30f, 10, 1).isEmpty(), "one hostile left");
        assertTrue(earned(80f, 0, 0, 30f, 10, 1).isEmpty(), "nothing to sweep");
    }

    @Test
    void underParNeedsAClearFasterThanPar() {
        assertEquals(EnumSet.of(Medal.UNDER_PAR), earned(49.9f, 7, 8, 30f, 10, 1));
        assertTrue(earned(PAR, 7, 8, 30f, 10, 1).isEmpty(), "exactly par is not under par");
    }

    @Test
    void sharpshooterNeedsSixtyPercentOfAtLeastOneShot() {
        assertEquals(EnumSet.of(Medal.SHARPSHOOTER), earned(80f, 7, 8, 30f, 5, 3), "3 of 5 is 60%");
        assertEquals(EnumSet.of(Medal.SHARPSHOOTER), earned(80f, 7, 8, 30f, 1, 1));
        assertTrue(earned(80f, 7, 8, 30f, 1000, 599).isEmpty(), "59.9% is not rounded up");
        assertTrue(earned(80f, 7, 8, 30f, 0, 0).isEmpty(), "no shot fired, no Sharpshooter");
    }

    @Test
    void accuracyIsRoundedDownAndUnknownWithoutAShot() {
        assertEquals(-1, RunStats.accuracyPercent(0, 0));
        assertEquals(59, RunStats.accuracyPercent(1000, 599));
        assertEquals(60, RunStats.accuracyPercent(5, 3));
        assertEquals(100, RunStats.accuracyPercent(4, 4));
        assertEquals(0, RunStats.accuracyPercent(4, 0));
    }

    @Test
    void perfectRunEarnsAllFourAndTheSummaryMarksThemNew() {
        SaveData save = new SaveData(new MapKeyValueStore());

        SaveData.CompletionSummary summary = save.recordCompletion(STAGE, Difficulty.NORMAL, PAR,
            new RunStats(40f, 8, 8, 0f, 9, 9, 10, 9), true);

        assertEquals(EnumSet.allOf(Medal.class), summary.medals);
        assertEquals(EnumSet.allOf(Medal.class), summary.newMedals);
        assertTrue(summary.medalsRecorded);
        assertEquals(90, summary.accuracyPercent());
        assertEquals(9, summary.pickupsCollected);
        assertEquals(9, summary.pickupsTotal);
        assertEquals('S', summary.rank, "the rank formula is untouched by the medals");
        assertEquals(EnumSet.allOf(Medal.class), save.getMedals(STAGE, Difficulty.NORMAL));
    }

    @Test
    void laterWorseRunKeepsEarnedMedalsAndALaterBetterRunAddsToThem() {
        MapKeyValueStore store = new MapKeyValueStore();
        SaveData save = new SaveData(store);
        // Unhurt and a full sweep, but slow and inaccurate.
        SaveData.CompletionSummary first = save.recordCompletion(STAGE, Difficulty.NORMAL, PAR,
            new RunStats(80f, 8, 8, 0f, 0, 9, 10, 1), true);
        assertEquals(EnumSet.of(Medal.UNTOUCHED, Medal.SWEEP), first.newMedals);

        SaveData.CompletionSummary worse = save.recordCompletion(STAGE, Difficulty.NORMAL, PAR, plainRun(), true);
        assertTrue(worse.medals.isEmpty(), "the worse run earns nothing itself");
        assertTrue(worse.newMedals.isEmpty());
        assertEquals(EnumSet.of(Medal.UNTOUCHED, Medal.SWEEP), save.getMedals(STAGE, Difficulty.NORMAL),
            "a worse run removes nothing");

        // Fast and unhurt, but one hostile left.
        SaveData.CompletionSummary faster = save.recordCompletion(STAGE, Difficulty.NORMAL, PAR,
            new RunStats(45f, 7, 8, 0f, 0, 9, 10, 1), true);
        assertEquals(EnumSet.of(Medal.UNTOUCHED, Medal.UNDER_PAR), faster.medals);
        assertEquals(EnumSet.of(Medal.UNDER_PAR), faster.newMedals, "Untouched was already held");
        assertEquals(EnumSet.of(Medal.UNTOUCHED, Medal.SWEEP, Medal.UNDER_PAR),
            new SaveData(store).getMedals(STAGE, Difficulty.NORMAL), "read back through a new SaveData");
    }

    @Test
    void medalsAreKeptPerDifficultyAndPerStage() {
        SaveData save = new SaveData(new MapKeyValueStore());
        save.recordCompletion(STAGE, Difficulty.EASY, PAR, new RunStats(40f, 7, 8, 30f, 0, 0, 10, 1), true);
        save.recordCompletion(STAGE, Difficulty.HARD, PAR, new RunStats(80f, 8, 8, 30f, 0, 0, 10, 9), true);

        assertEquals(EnumSet.of(Medal.UNDER_PAR), save.getMedals(STAGE, Difficulty.EASY));
        assertTrue(save.getMedals(STAGE, Difficulty.NORMAL).isEmpty(), "never played on Normal");
        assertEquals(EnumSet.of(Medal.SWEEP, Medal.SHARPSHOOTER), save.getMedals(STAGE, Difficulty.HARD));
        assertTrue(save.getMedals("02_coolant_core", Difficulty.HARD).isEmpty(), "another stage");
    }

    @Test
    void runThatRecordsNoMedalsStillRecordsTimeAndRank() {
        SaveData save = new SaveData(new MapKeyValueStore());

        SaveData.CompletionSummary summary = save.recordCompletion("range", Difficulty.NORMAL, 45f,
            new RunStats(30f, 11, 11, 0f, 3, 12, 10, 10), false);

        assertFalse(summary.medalsRecorded);
        assertTrue(summary.medals.isEmpty());
        assertTrue(summary.newMedals.isEmpty());
        assertTrue(save.getMedals("range", Difficulty.NORMAL).isEmpty());
        assertTrue(summary.newBestTime);
        assertEquals(30f, save.getBestTime("range", Difficulty.NORMAL), 1e-4f);
        assertEquals('S', save.getBestRank("range", Difficulty.NORMAL));
        assertEquals(100, summary.accuracyPercent(), "the tally still shows the run's accuracy");
    }

    @Test
    void oldSaveWithoutMedalKeysLoadsWithNoMedalsAndKeepsItsRecords() {
        MapKeyValueStore store = new MapKeyValueStore();
        store.putFloat("best." + STAGE + ".NORMAL.time", 62.5f);
        store.putString("best." + STAGE + ".NORMAL.rank", "A");
        store.putInteger("campaign.unlockedStage", 2);
        SaveData save = new SaveData(store);

        for (Difficulty difficulty : Difficulty.values()) {
            assertTrue(save.getMedals(STAGE, difficulty).isEmpty(), difficulty.name());
        }
        assertEquals(62.5f, save.getBestTime(STAGE, Difficulty.NORMAL), 1e-4f);
        assertEquals('A', save.getBestRank(STAGE, Difficulty.NORMAL));
        assertEquals(Difficulty.NORMAL, save.getFastestDifficulty(STAGE));

        // The next clear of an old save's stage adds medals without disturbing the better stored time.
        SaveData.CompletionSummary summary = save.recordCompletion(STAGE, Difficulty.NORMAL, PAR,
            new RunStats(70f, 8, 8, 12f, 0, 0, 4, 1), true);
        assertFalse(summary.newBestTime);
        assertEquals(62.5f, summary.bestTime, 1e-4f);
        assertEquals(EnumSet.of(Medal.SWEEP), save.getMedals(STAGE, Difficulty.NORMAL));
    }

    @Test
    void sevenArgumentRecordCanEarnEveryMedalButSharpshooter() {
        SaveData save = new SaveData(new MapKeyValueStore());
        SaveData.CompletionSummary summary = save.recordCompletion(STAGE, Difficulty.NORMAL, 40f, PAR, 8, 8, 0f);
        assertEquals(EnumSet.of(Medal.UNTOUCHED, Medal.SWEEP, Medal.UNDER_PAR), summary.medals);
        assertEquals(-1, summary.accuracyPercent());
    }

    @Test
    void unknownBitsInAStoredMedalValueAreIgnored() {
        MapKeyValueStore store = new MapKeyValueStore();
        store.putInteger("best." + STAGE + ".NORMAL.medals", 0x7FFFFFF0 | 2);
        assertEquals(EnumSet.of(Medal.SWEEP), new SaveData(store).getMedals(STAGE, Difficulty.NORMAL));
    }

    @Test
    void negativeStoredMedalValueHoldsNoMedals() {
        for (int stored : new int[] {-1, Integer.MIN_VALUE, Integer.MIN_VALUE | 2}) {
            MapKeyValueStore store = new MapKeyValueStore();
            store.putInteger("best." + STAGE + ".NORMAL.medals", stored);
            SaveData save = new SaveData(store);
            assertTrue(save.getMedals(STAGE, Difficulty.NORMAL).isEmpty(), "stored " + stored);

            // The next clear earns its medals as new ones and saves only their bits.
            SaveData.CompletionSummary summary = save.recordCompletion(STAGE, Difficulty.NORMAL, PAR,
                new RunStats(70f, 8, 8, 12f, 0, 0, 4, 1), true);
            assertEquals(EnumSet.of(Medal.SWEEP), summary.newMedals, "stored " + stored);
            assertEquals(2, store.getInteger("best." + STAGE + ".NORMAL.medals", 0), "stored " + stored);
        }
    }

    @Test
    void fastestDifficultyIsTheOneWithTheLowestStoredTime() {
        MapKeyValueStore store = new MapKeyValueStore();
        SaveData save = new SaveData(store);
        assertEquals(null, save.getFastestDifficulty(STAGE), "never cleared");

        save.recordCompletion(STAGE, Difficulty.EASY, 90f, PAR, 8, 8, 0f);
        save.recordCompletion(STAGE, Difficulty.HARD, 48f, PAR, 8, 8, 0f);
        save.recordCompletion(STAGE, Difficulty.NORMAL, 60f, PAR, 8, 8, 0f);
        assertEquals(Difficulty.HARD, save.getFastestDifficulty(STAGE));

        save.recordCompletion(STAGE, Difficulty.EASY, 48f, PAR, 8, 8, 0f);
        assertEquals(Difficulty.HARD, save.getFastestDifficulty(STAGE), "a tie goes to the harder difficulty");

        store.putFloat("best." + STAGE + ".HARD.time", Float.NaN);
        assertEquals(Difficulty.EASY, save.getFastestDifficulty(STAGE), "a damaged time is no time");
    }
}

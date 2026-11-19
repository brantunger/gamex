package io.github.brantunger.gamex.player;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import io.github.brantunger.gamex.TestTuning;
import io.github.brantunger.gamex.data.AssetText;
import io.github.brantunger.gamex.world.Levels;
import org.junit.jupiter.api.Test;

/** Checks the difficulty tuning file: what it ships with, what a changed value does, and its errors. */
class DifficultyTuningTest {

    private static void assertScales(Difficulty difficulty, float damageTaken, float pickup, float enemySpeed) {
        DifficultyTuning.Scales scales = TestTuning.DIFFICULTY.of(difficulty);
        assertEquals(damageTaken, scales.damageTakenScale, 0f, difficulty + " damage taken");
        assertEquals(pickup, scales.pickupScale, 0f, difficulty + " pickup");
        assertEquals(enemySpeed, scales.enemySpeedScale, 0f, difficulty + " enemy speed");
    }

    /** The values are the ones the {@code Difficulty} enum held before the file existed (commit 10cd4a4). */
    @Test
    void theShippedFileHoldsTheValuesTheEnumHadBeforeIt() {
        assertScales(Difficulty.EASY, 0.7f, 1.25f, 0.85f);
        assertScales(Difficulty.NORMAL, 1.0f, 1.0f, 1.0f);
        assertScales(Difficulty.HARD, 1.4f, 0.8f, 1.2f);
        assertSame(TestTuning.DIFFICULTY.of(Difficulty.NORMAL), TestTuning.DIFFICULTY.of(null));
    }

    /** Saved games store these names and the order is the menu's; neither may move with the numbers. */
    @Test
    void theDifficultiesThemselvesStayInCode() {
        assertArrayEquals(new Difficulty[] {Difficulty.EASY, Difficulty.NORMAL, Difficulty.HARD}, Difficulty.values());
        assertEquals("EASY", Difficulty.EASY.name());
        assertEquals("NORMAL", Difficulty.NORMAL.name());
        assertEquals("HARD", Difficulty.HARD.name());
        assertSame(Difficulty.HARD, Difficulty.valueOf("HARD"));
    }

    @Test
    void aChangedPickupScaleSizesTheStarterAmmunition() {
        Levels.Stage lockdownWing = Levels.stage("04_lockdown_wing");
        PlayerLoadout shipped = PlayerLoadout.forStage(lockdownWing, Difficulty.EASY, TestTuning.DIFFICULTY,
            TestTuning.WEAPONS);
        assertEquals(Math.round(TestTuning.WEAPONS.scattergunWeaponPickupShells * 1.25f), shipped.shells);
        assertEquals(Math.round(TestTuning.WEAPONS.railWeaponPickupSlugs * 1.25f), shipped.slugs);

        DifficultyTuning halved = TestTuning.difficultyWith("easy.pickup", "0.5");
        PlayerLoadout tuned = PlayerLoadout.forStage(lockdownWing, Difficulty.EASY, halved, TestTuning.WEAPONS);
        assertEquals(Math.round(TestTuning.WEAPONS.scattergunWeaponPickupShells * 0.5f), tuned.shells);
        assertEquals(Math.round(TestTuning.WEAPONS.railWeaponPickupSlugs * 0.5f), tuned.slugs);
        assertEquals(Difficulty.EASY, tuned.difficulty);
        // The other difficulties are untouched.
        assertEquals(
            PlayerLoadout.forStage(lockdownWing, Difficulty.HARD, TestTuning.DIFFICULTY, TestTuning.WEAPONS).shells,
            PlayerLoadout.forStage(lockdownWing, Difficulty.HARD, halved, TestTuning.WEAPONS).shells);
    }

    private static String error(String key, String value) {
        String text = TestTuning.withValue(AssetText.read("data", DifficultyTuning.PATH), key, value);
        return assertThrows(IllegalArgumentException.class,
            () -> DifficultyTuning.parse(DifficultyTuning.PATH, text)).getMessage();
    }

    @Test
    void aMissingUnknownOrInvalidValueFailsNamingTheFileAndTheKey() {
        assertEquals("Missing 'hard.pickup' key in data/difficulty.txt", error("hard.pickup", null));

        String unknown = assertThrows(IllegalArgumentException.class, () -> DifficultyTuning.parse(
            DifficultyTuning.PATH, "nightmare.pickup: 0.5\n" + AssetText.read("data", DifficultyTuning.PATH)))
            .getMessage();
        assertEquals("Unknown key 'nightmare.pickup' in data/difficulty.txt at line 1", unknown);

        for (String[] bad : new String[][] {
            {"easy.damage_taken", "-0.1"}, {"normal.pickup", "-1"}, {"hard.enemy_speed", "0"},
            {"hard.enemy_speed", "1.2f"}, {"easy.pickup", ""},
        }) {
            String message = error(bad[0], bad[1]);
            assertTrue(message.contains("data/difficulty.txt") && message.contains("'" + bad[0] + "'")
                && message.contains("at line "), message);
        }
    }

    @Test
    void multipliersBuiltInCodeAreCheckedTheSameWay() {
        DifficultyTuning.Scales ones = new DifficultyTuning.Scales(1f, 1f, 1f);
        DifficultyTuning.Scales twos = new DifficultyTuning.Scales(2f, 0f, 2f);
        DifficultyTuning tuning = new DifficultyTuning(ones, twos, ones);
        assertSame(twos, tuning.of(Difficulty.NORMAL));
        assertSame(ones, tuning.of(Difficulty.EASY));
        assertSame(ones, tuning.of(Difficulty.HARD));

        assertThrows(IllegalArgumentException.class, () -> new DifficultyTuning(ones, null, ones));
        assertThrows(IllegalArgumentException.class, () -> new DifficultyTuning(null, ones, ones));
        assertThrows(IllegalArgumentException.class, () -> new DifficultyTuning(ones, ones, null));
        assertThrows(IllegalArgumentException.class, () -> new DifficultyTuning.Scales(-1f, 1f, 1f));
        assertThrows(IllegalArgumentException.class, () -> new DifficultyTuning.Scales(1f, Float.NaN, 1f));
        assertThrows(IllegalArgumentException.class, () -> new DifficultyTuning.Scales(1f, 1f, 0f));
        assertThrows(IllegalArgumentException.class,
            () -> new DifficultyTuning.Scales(1f, 1f, Float.POSITIVE_INFINITY));
    }
}

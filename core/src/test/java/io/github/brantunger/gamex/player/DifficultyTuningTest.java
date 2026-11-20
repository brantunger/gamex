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

/**
 * Checks the difficulty tuning file: that each key sets its own value, that the shipped file loads, what a changed
 * value does, and its errors.
 */
class DifficultyTuningTest {

    /** The test's own file, in which every number differs from the shipped one and from every other. */
    private static final String FIXTURE = "data-fixtures/difficulty.txt";

    private static void assertScales(DifficultyTuning tuning, Difficulty difficulty, float damageTaken, float pickup,
                                     float enemySpeed) {
        DifficultyTuning.Scales scales = tuning.of(difficulty);
        assertEquals(damageTaken, scales.damageTakenScale, 0f, difficulty + " damage taken");
        assertEquals(pickup, scales.pickupScale, 0f, difficulty + " pickup");
        assertEquals(enemySpeed, scales.enemySpeedScale, 0f, difficulty + " enemy speed");
    }

    /**
     * The values on the right are those of the test's own file, where no two numbers are alike, so a key read into
     * another key's field, or a value still held in code, shows.
     */
    @Test
    void everyKeyOfADifficultyFileSetsItsOwnValue() {
        DifficultyTuning t = DifficultyTuning.parse(FIXTURE, AssetText.read("data", FIXTURE));
        assertScales(t, Difficulty.EASY, 0.714f, 1.2875f, 0.884f);
        assertScales(t, Difficulty.NORMAL, 1.05f, 1.06f, 1.07f);
        assertScales(t, Difficulty.HARD, 1.512f, 0.872f, 1.32f);
        assertSame(t.of(Difficulty.NORMAL), t.of(null));
    }

    /** Whatever values the shipped file holds, it loads and gives every difficulty its three multipliers. */
    @Test
    void theShippedFileLoads() {
        DifficultyTuning shipped = DifficultyTuning.load();
        for (Difficulty difficulty : Difficulty.values()) {
            DifficultyTuning.Scales scales = shipped.of(difficulty);
            assertTrue(scales.damageTakenScale >= 0f && scales.pickupScale > 0f && scales.enemySpeedScale > 0f,
                difficulty.toString());
        }
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
        Levels.Stage lockdownWing = TestTuning.stageStartingWith("scattergun, raillance");
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
        String text = TestTuning.withValue(TestTuning.text(DifficultyTuning.PATH), key, value);
        return assertThrows(IllegalArgumentException.class,
            () -> DifficultyTuning.parse(DifficultyTuning.PATH, text)).getMessage();
    }

    @Test
    void aMissingUnknownOrInvalidValueFailsNamingTheFileAndTheKey() {
        assertEquals("Missing 'hard.pickup' key in data/difficulty.txt", error("hard.pickup", null));

        String unknown = assertThrows(IllegalArgumentException.class, () -> DifficultyTuning.parse(
            DifficultyTuning.PATH, "nightmare.pickup: 0.5\n" + TestTuning.text(DifficultyTuning.PATH)))
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

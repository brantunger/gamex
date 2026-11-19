package io.github.brantunger.gamex.weapon;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.Test;

class PistolTest {

    @Test
    void firesAtOnceWhenTheTriggerIsPulled() {
        Pistol pistol = new Pistol();

        assertFalse(pistol.update(0.01f, false));
        assertTrue(pistol.update(0.01f, true));
    }

    @Test
    void heldTriggerFiresNoFasterThanTheFireInterval() {
        Pistol pistol = new Pistol();
        int shots = 0;

        // 0.95 seconds in steps of 0.01: shots at about 0, 0.3, 0.6 and 0.9 seconds.
        for (int i = 0; i < 95; i++) {
            if (pistol.update(0.01f, true)) shots++;
        }

        assertEquals(4, shots);
    }

    @Test
    void tappingFasterThanTheFireIntervalDoesNotFireFaster() {
        Pistol pistol = new Pistol();

        assertTrue(pistol.update(0.01f, true));
        assertFalse(pistol.update(0.01f, false));
        assertFalse(pistol.update(Pistol.FIRE_INTERVAL / 2f, true));
        assertTrue(pistol.update(Pistol.FIRE_INTERVAL / 2f, true));
    }

    private static int shotsWhileHeld(int framesPerSecond, int seconds) {
        Pistol pistol = new Pistol();
        int shots = 0;
        for (int i = 0; i < framesPerSecond * seconds; i++) {
            if (pistol.update(1f / framesPerSecond, true)) shots++;
        }
        return shots;
    }

    @Test
    void heldFireRateIsTheSameAtAnyFrameRate() {
        // The first shot leaves on the first frame, and then one every 0.3 seconds: 66 more within 20 seconds.
        assertEquals(67, shotsWhileHeld(30, 20));
        assertEquals(67, shotsWhileHeld(60, 20));
        assertEquals(67, shotsWhileHeld(144, 20));
    }

    @Test
    void timeSpentIdleDoesNotBankExtraShots() {
        Pistol pistol = new Pistol();
        for (int i = 0; i < 300; i++) pistol.update(1f / 60f, false);

        int shots = 0;
        // A quarter of a second of held fire, less than one fire interval.
        for (int i = 0; i < 15; i++) {
            if (pistol.update(1f / 60f, true)) shots++;
        }

        assertEquals(1, shots);
    }

    @Test
    void muzzleFlashShowsBrieflyAfterAShot() {
        Pistol pistol = new Pistol();
        assertFalse(pistol.isFlashVisible());

        pistol.update(0.01f, true);
        assertTrue(pistol.isFlashVisible());

        pistol.update(Pistol.FLASH_TIME + 0.01f, false);
        assertFalse(pistol.isFlashVisible());
    }

    @Test
    void nonFiniteAndNegativeDeltaTimesAreNoOpsWhileZeroDeltaAllowsReadyShot() {
        Pistol pistol = new Pistol();
        for (float badDt : new float[] {Float.NaN, Float.POSITIVE_INFINITY, Float.NEGATIVE_INFINITY, -0.25f}) {
            assertFalse(pistol.update(badDt, true), "bad dt must not fire: " + badDt);
            assertFalse(pistol.isFlashVisible());
        }

        // dt == 0f still allows an instant shot when already off cooldown.
        assertTrue(pistol.update(0f, true));
        assertTrue(pistol.isFlashVisible());
        assertFalse(pistol.update(0f, true), "immediately on cooldown after firing");

        // Advance slightly into flash window, then verify bad dt leaves flash and cooldown untouched.
        assertFalse(pistol.update(0.02f, false));
        assertTrue(pistol.isFlashVisible());
        for (float badDt : new float[] {Float.NaN, Float.POSITIVE_INFINITY, Float.NEGATIVE_INFINITY, -1f}) {
            assertFalse(pistol.update(badDt, true));
            assertFalse(pistol.update(badDt, false));
            assertTrue(pistol.isFlashVisible(), "bad dt must not clear muzzle flash: " + badDt);
        }

        assertFalse(pistol.update(Pistol.FIRE_INTERVAL - 0.03f, true), "cooldown still remaining");
        assertTrue(pistol.update(0.02f, true), "fires once remaining cooldown elapses");
    }
}

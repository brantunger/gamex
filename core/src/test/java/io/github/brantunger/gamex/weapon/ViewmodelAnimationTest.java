package io.github.brantunger.gamex.weapon;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.Test;

class ViewmodelAnimationTest {

    private static final float FRAME = 1f / 60f;

    private static void assertAtRest(ViewmodelAnimation animation) {
        assertEquals(0f, animation.getRecoil(), 0f);
        assertEquals(0f, animation.getSlide(), 0f);
        assertEquals(0f, animation.getBobX(), 0f);
        assertEquals(0f, animation.getBobY(), 0f);
        assertEquals(0f, animation.getSwayYaw(), 0f);
        assertEquals(0f, animation.getSwayPitch(), 0f);
    }

    @Test
    void newAnimationIsAtRestAndStaysThereWhileNothingHappens() {
        ViewmodelAnimation animation = new ViewmodelAnimation();
        assertAtRest(animation);

        for (int i = 0; i < 120; i++) animation.update(FRAME, false, 0f, 0f, 0f);

        assertAtRest(animation);
    }

    @Test
    void recoilPeaksJustAfterAShotAndIsBackAtRestWithinTheFireInterval() {
        ViewmodelAnimation animation = new ViewmodelAnimation();
        animation.update(FRAME, true, 0f, 0f, 0f);

        float peak = 0f;
        float peakTime = 0f;
        float time = 0f;
        float lastAfterPeak = Float.POSITIVE_INFINITY;
        while (time + FRAME < Pistol.FIRE_INTERVAL) {
            animation.update(FRAME, false, 0f, 0f, 0f);
            time += FRAME;
            float recoil = animation.getRecoil();
            assertTrue(recoil >= 0f && recoil <= 1f, "recoil " + recoil + " at " + time);
            if (recoil > peak) {
                peak = recoil;
                peakTime = time;
                lastAfterPeak = recoil;
            } else {
                assertTrue(recoil <= lastAfterPeak, "recoil only eases back after its peak");
                lastAfterPeak = recoil;
            }
        }

        assertTrue(peak > 0.8f, "the kick is felt: peak " + peak);
        assertTrue(peakTime < 0.07f, "the kick is sudden: peak at " + peakTime);
        assertEquals(0f, animation.getRecoil(), 0f, "back at rest before the next shot can leave");
        assertEquals(0f, animation.getSlide(), 0f);
    }

    @Test
    void slideSnapsBackAndReturnsFasterThanTheGunSettles() {
        ViewmodelAnimation animation = new ViewmodelAnimation();
        animation.update(FRAME, true, 0f, 0f, 0f);

        animation.update(0.02f, false, 0f, 0f, 0f);
        assertTrue(animation.getSlide() > 0.9f, "slide is back: " + animation.getSlide());

        animation.update(ViewmodelAnimation.SLIDE_TIME, false, 0f, 0f, 0f);
        assertEquals(0f, animation.getSlide(), 0f);
        assertTrue(animation.getRecoil() > 0f, "the gun is still settling");
    }

    @Test
    void everyShotRestartsTheRecoil() {
        ViewmodelAnimation animation = new ViewmodelAnimation();
        animation.update(FRAME, true, 0f, 0f, 0f);
        for (int i = 0; i < 60; i++) animation.update(FRAME, false, 0f, 0f, 0f);
        assertEquals(0f, animation.getRecoil(), 0f);

        animation.update(FRAME, true, 0f, 0f, 0f);
        animation.update(0.03f, false, 0f, 0f, 0f);

        assertTrue(animation.getRecoil() > 0.8f);
    }

    @Test
    void bobIsZeroWhenStillAndBoundedWhileWalking() {
        ViewmodelAnimation animation = new ViewmodelAnimation();
        float widest = 0f;
        float highest = 0f;

        for (int i = 0; i < 600; i++) {
            animation.update(FRAME, false, 1f, 0f, 0f);
            widest = Math.max(widest, Math.abs(animation.getBobX()));
            highest = Math.max(highest, Math.abs(animation.getBobY()));
        }

        assertTrue(widest <= ViewmodelAnimation.BOB_WIDTH, "sideways bob " + widest);
        assertTrue(highest <= ViewmodelAnimation.BOB_HEIGHT, "vertical bob " + highest);
        assertTrue(widest > ViewmodelAnimation.BOB_WIDTH * 0.8f, "the gun does bob: " + widest);
        assertTrue(highest > ViewmodelAnimation.BOB_HEIGHT * 0.8f, "the gun does bob: " + highest);
    }

    @Test
    void bobSettlesOnceThePlayerStops() {
        ViewmodelAnimation animation = new ViewmodelAnimation();
        // Stop a quarter of the way through a swing, where the sideways offset is at its widest.
        for (int i = 0; i < 115; i++) animation.update(FRAME, false, 1f, 0f, 0f);
        assertTrue(Math.abs(animation.getBobX()) > ViewmodelAnimation.BOB_WIDTH * 0.5f, "mid-swing");

        for (int i = 0; i < 120; i++) animation.update(FRAME, false, 0f, 0f, 0f);

        assertEquals(0f, animation.getBobX(), 1e-6f);
        assertEquals(0f, animation.getBobY(), 1e-6f);
    }

    @Test
    void bobScalesWithTheWalkingSpeed() {
        ViewmodelAnimation full = new ViewmodelAnimation();
        ViewmodelAnimation half = new ViewmodelAnimation();
        float widestFull = 0f;
        float widestHalf = 0f;

        for (int i = 0; i < 600; i++) {
            full.update(FRAME, false, 1f, 0f, 0f);
            half.update(FRAME, false, 0.5f, 0f, 0f);
            widestFull = Math.max(widestFull, Math.abs(full.getBobX()));
            widestHalf = Math.max(widestHalf, Math.abs(half.getBobX()));
        }

        assertEquals(widestFull * 0.5f, widestHalf, widestFull * 0.1f);
    }

    @Test
    void gunLagsBehindATurnWithinItsLimitAndCatchesUpAfterwards() {
        ViewmodelAnimation animation = new ViewmodelAnimation();

        // A very fast turn to the right and upwards: 20 degrees per frame.
        for (int i = 0; i < 60; i++) animation.update(FRAME, false, 0f, 20f, 20f);
        assertTrue(animation.getSwayYaw() < -1f, "lags to the left of a right turn: " + animation.getSwayYaw());
        assertTrue(animation.getSwayYaw() >= -ViewmodelAnimation.MAX_SWAY);
        assertTrue(animation.getSwayPitch() < -1f && animation.getSwayPitch() >= -ViewmodelAnimation.MAX_SWAY);

        for (int i = 0; i < 180; i++) animation.update(FRAME, false, 0f, 0f, 0f);
        assertEquals(0f, animation.getSwayYaw(), 1e-4f);
        assertEquals(0f, animation.getSwayPitch(), 1e-4f);
    }

    @Test
    void zeroLengthStepChangesNothingButStillTakesTheShot() {
        ViewmodelAnimation animation = new ViewmodelAnimation();

        animation.update(0f, false, 1f, 50f, 50f);
        assertAtRest(animation);

        animation.update(0f, true, 0f, 0f, 0f);
        animation.update(0.03f, false, 0f, 0f, 0f);
        assertTrue(animation.getRecoil() > 0.8f);
    }

    @Test
    void resetShotClearsHeavyRecoilAndSlideWithoutDisturbingWalkBobOrTurnSway() {
        ViewmodelAnimation animation = new ViewmodelAnimation();
        animation.update(FRAME, 2.6f, 1f, 12f, -8f);
        animation.update(0.035f, 0f, 1f, 12f, -8f);

        assertTrue(animation.getRecoil() > 2.4f, "heavy shot recoil active: " + animation.getRecoil());
        assertTrue(animation.getSlide() > 0.7f, "slide racked back: " + animation.getSlide());
        float bobXBefore = animation.getBobX();
        float bobYBefore = animation.getBobY();
        float swayYawBefore = animation.getSwayYaw();
        float swayPitchBefore = animation.getSwayPitch();
        assertTrue(Math.abs(bobXBefore) > 0f);
        assertTrue(Math.abs(swayYawBefore) > 0f);

        animation.resetShot();
        assertEquals(0f, animation.getRecoil(), 0f);
        assertEquals(0f, animation.getSlide(), 0f);
        assertEquals(bobXBefore, animation.getBobX(), 0f);
        assertEquals(bobYBefore, animation.getBobY(), 0f);
        assertEquals(swayYawBefore, animation.getSwayYaw(), 0f);
        assertEquals(swayPitchBefore, animation.getSwayPitch(), 0f);

        // Subsequent idle updates stay at 0 recoil/slide, and a 1x pistol shot uses reset recoilScale = 1.
        animation.update(FRAME, 0f, 0f, 0f, 0f);
        assertEquals(0f, animation.getRecoil(), 0f);
        assertEquals(0f, animation.getSlide(), 0f);
        animation.update(FRAME, true, 0f, 0f, 0f);
        animation.update(0.03f, false, 0f, 0f, 0f);
        assertTrue(animation.getRecoil() <= 1f, "recoilScale reset to 1f: " + animation.getRecoil());
    }

    @Test
    void nonFiniteAndNegativeInputsNeverCorruptRecoilSlideBobOrSway() {
        ViewmodelAnimation animation = new ViewmodelAnimation();
        animation.update(Float.NaN, Float.NaN, Float.NaN, Float.NaN, Float.NaN);
        animation.update(Float.POSITIVE_INFINITY, Float.POSITIVE_INFINITY, Float.POSITIVE_INFINITY,
            Float.POSITIVE_INFINITY, Float.POSITIVE_INFINITY);
        animation.update(-0.25f, 0f, 1f, 20f, -20f);
        assertAtRest(animation);

        // Start a valid shot + walk + sway, then bombard with negative and non-finite dt values.
        animation.update(FRAME, 1.5f, 0.8f, 8f, -6f);
        animation.update(0.03f, 0f, 0.8f, 8f, -6f);
        float recoilBefore = animation.getRecoil();
        float slideBefore = animation.getSlide();
        float bobXBefore = animation.getBobX();
        float bobYBefore = animation.getBobY();
        float swayYawBefore = animation.getSwayYaw();
        float swayPitchBefore = animation.getSwayPitch();
        assertTrue(recoilBefore > 1.2f);
        assertTrue(slideBefore > 0.7f);

        for (float badDt : new float[] {-0.5f, Float.NaN, Float.POSITIVE_INFINITY, Float.NEGATIVE_INFINITY}) {
            animation.update(badDt, 0f, 1f, 30f, -30f);
            animation.update(badDt, Float.NaN, 1f, 30f, -30f);
            animation.update(badDt, Float.POSITIVE_INFINITY, 1f, 30f, -30f);
            assertEquals(recoilBefore, animation.getRecoil(), 0f);
            assertEquals(slideBefore, animation.getSlide(), 0f);
            assertEquals(bobXBefore, animation.getBobX(), 0f);
            assertEquals(bobYBefore, animation.getBobY(), 0f);
            assertEquals(swayYawBefore, animation.getSwayYaw(), 0f);
            assertEquals(swayPitchBefore, animation.getSwayPitch(), 0f);
        }

        // Valid positive dt with non-finite shotKickScale, walkSpeed, yawDelta, pitchDelta sanitizes them cleanly.
        animation.update(FRAME, Float.POSITIVE_INFINITY, Float.NaN, Float.POSITIVE_INFINITY, Float.NEGATIVE_INFINITY);
        assertTrue(Float.isFinite(animation.getRecoil()) && animation.getRecoil() <= 1.5f);
        assertTrue(Float.isFinite(animation.getSlide()));
        assertTrue(Float.isFinite(animation.getBobX()));
        assertTrue(Float.isFinite(animation.getBobY()));
        assertTrue(Float.isFinite(animation.getSwayYaw())
            && Math.abs(animation.getSwayYaw()) <= ViewmodelAnimation.MAX_SWAY);
        assertTrue(Float.isFinite(animation.getSwayPitch())
            && Math.abs(animation.getSwayPitch()) <= ViewmodelAnimation.MAX_SWAY);
    }

    @Test
    void bobPhaseWrapsModuloTwoPiKeepingFigureEightSmoothAfterMultiHourElapsedTime() {
        ViewmodelAnimation animation = new ViewmodelAnimation();
        // Warm up bobAmount to 1 and advance bobPhase across ~9,000,000 radians (millions of cycles, where raw float
        // phase would have ULP >= 1.0 rad and completely freeze 60 fps updates).
        animation.update(1_000_000f, false, 1f, 0f, 0f);

        float prevX = animation.getBobX();
        float prevY = animation.getBobY();
        float minX = prevX;
        float maxX = prevX;
        float minY = prevY;
        float maxY = prevY;
        float maxStepX = ViewmodelAnimation.BOB_WIDTH * (FRAME * 9f + 0.02f);
        float maxStepY = ViewmodelAnimation.BOB_HEIGHT * (FRAME * 18f + 0.02f);

        for (int i = 0; i < 180; i++) {
            animation.update(FRAME, false, 1f, 0f, 0f);
            float x = animation.getBobX();
            float y = animation.getBobY();
            assertTrue(Float.isFinite(x) && Math.abs(x) <= ViewmodelAnimation.BOB_WIDTH + 1e-6f);
            assertTrue(Float.isFinite(y) && Math.abs(y) <= ViewmodelAnimation.BOB_HEIGHT + 1e-6f);
            assertTrue(Math.abs(x - prevX) <= maxStepX, "discontinuity in bobX across 2pi wrap: " + (x - prevX));
            assertTrue(Math.abs(y - prevY) <= maxStepY, "discontinuity in bobY across 2pi wrap: " + (y - prevY));
            minX = Math.min(minX, x);
            maxX = Math.max(maxX, x);
            minY = Math.min(minY, y);
            maxY = Math.max(maxY, y);
            prevX = x;
            prevY = y;
        }

        assertTrue(minX < -ViewmodelAnimation.BOB_WIDTH * 0.95f && maxX > ViewmodelAnimation.BOB_WIDTH * 0.95f,
            "sideways bob still sweeps full range after 1,000,000s: [" + minX + ", " + maxX + "]");
        assertTrue(minY < -ViewmodelAnimation.BOB_HEIGHT * 0.95f && maxY > ViewmodelAnimation.BOB_HEIGHT * 0.95f,
            "vertical bob still sweeps full range after 1,000,000s: [" + minY + ", " + maxY + "]");
    }
}

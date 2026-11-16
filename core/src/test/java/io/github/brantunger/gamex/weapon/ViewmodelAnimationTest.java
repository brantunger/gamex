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
}

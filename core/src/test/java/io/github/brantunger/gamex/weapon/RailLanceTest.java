package io.github.brantunger.gamex.weapon;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import io.github.brantunger.gamex.TestTuning;
import org.junit.jupiter.api.Test;

class RailLanceTest {

    /** The shipped weapon balance. */
    private static final WeaponTuning W = TestTuning.WEAPONS;

    private static final float DT = 1f / 60f;

    @Test
    void cooldownSlugsAndBoltRecoilCycleWorkCleanly() {
        RailLance rail = new RailLance(W);
        assertTrue(rail.isReady());
        assertFalse(rail.isFlashVisible());
        assertEquals(0f, rail.getBolt(), 1e-6f);
        assertEquals(1f, rail.getChargeProgress(), 1e-6f);

        // Zero slugs cannot fire.
        assertFalse(rail.update(DT, true, false, 0));
        assertTrue(rail.isReady());

        // Fires once with available slugs and enters 1.10s recharge cycle.
        assertTrue(rail.update(DT, true, false, 3));
        assertFalse(rail.isReady());
        assertTrue(rail.isFlashVisible());
        assertTrue(rail.getChargeProgress() < 0.05f);
        assertFalse(rail.update(DT, true, false, 2), "must not refire during 1.10s cycle");

        // Bolt snaps rearward then returns cleanly to 0 before cycle ends.
        float peakBolt = 0f;
        for (int i = 0; i < 66; i++) {
            rail.update(DT, false, false, 2);
            peakBolt = Math.max(peakBolt, rail.getBolt());
            assertTrue(rail.getBolt() >= 0f && rail.getBolt() <= 1f);
        }
        assertTrue(peakBolt > 0.85f, "expected full bolt recoil travel, got " + peakBolt);
        assertTrue(rail.isReady());
        assertEquals(1f, rail.getChargeProgress(), 1e-5f);
        assertEquals(0f, rail.getBolt(), 1e-5f);
    }

    @Test
    void tacticalZoomInterpolatesSmoothlyScalesFovAndLatchesOnCancelUntilRelease() {
        RailLance rail = new RailLance(W);
        assertEquals(0f, rail.getZoom(), 1e-6f);
        assertEquals(1f, rail.getZoomFovScale(), 1e-6f);

        // Engage RMB zoom -> ramps smoothly toward 1.0 and 1/1.8 FOV scale.
        rail.update(DT, false, true, 5);
        assertTrue(rail.consumeZoomInTriggered());
        assertFalse(rail.consumeZoomInTriggered());
        for (int i = 0; i < 12; i++) {
            rail.update(DT, false, true, 5);
        }
        assertEquals(1f, rail.getZoom(), 1e-5f);
        assertEquals(1f / W.railZoomFactor, rail.getZoomFovScale(), 1e-5f);

        // Cancelling zoom (dash/pause/swap) snaps zoom to 0 and keeps it suppressed while RMB stays held.
        rail.cancelZoom();
        assertEquals(0f, rail.getZoom(), 1e-6f);
        assertEquals(1f, rail.getZoomFovScale(), 1e-6f);
        for (int i = 0; i < 10; i++) {
            rail.update(DT, false, true, 5);
            assertEquals(0f, rail.getZoom(), 1e-6f, "zoom remains suppressed until RMB release");
        }

        // Releasing RMB clears suppression; re-pressing zooms back in cleanly.
        rail.update(DT, false, false, 5);
        rail.update(DT, false, true, 5);
        assertTrue(rail.consumeZoomInTriggered());
        assertTrue(rail.getZoom() > 0f);
    }

    @Test
    void cancelZoomWhenUnzoomedAllowsPreHeldZoomDuringSwapWhilePreservingDashSuppression() {
        RailLance rail = new RailLance(W);

        // Weapon-switch cancelZoom while RMB is up must not suppress a fresh RMB hold initiated during draw.
        rail.updateIdle(DT, false);
        rail.cancelZoom();
        for (int i = 0; i < 5; i++) {
            rail.updateIdle(DT, true);
        }
        rail.update(DT, false, true, 5);
        assertTrue(rail.consumeZoomInTriggered(), "fresh RMB press held during draw engages zoom on completion");
        assertTrue(rail.getZoom() > 0f);

        // Accepted dash cancels zoom and keeps suppression latched across zoomBlocked frames until RMB release.
        rail.cancelZoom();
        for (int i = 0; i < 9; i++) {
            rail.update(DT, false, true, true, 5);
            assertEquals(0f, rail.getZoom(), 1e-6f);
        }
        for (int i = 0; i < 6; i++) {
            rail.update(DT, false, true, false, 5);
            assertEquals(0f, rail.getZoom(), 1e-6f, "stays suppressed after dash ends while RMB remains held");
        }
        rail.updateIdle(DT, false);
        rail.update(DT, false, true, false, 5);
        assertTrue(rail.getZoom() > 0f);
    }

    @Test
    void isReadyAfterMatchesExactCapacitorCompletionStep() {
        RailLance rail = new RailLance(W);
        assertTrue(rail.update(DT, true, false, 1));
        rail.updateIdle(W.railFireInterval - 0.01f);

        assertFalse(rail.isReady());
        assertFalse(rail.isReadyAfter(0.005f));
        assertTrue(rail.isReadyAfter(DT));

        rail.updateIdle(DT);
        assertTrue(rail.isReady());
    }

    @Test
    void nonFiniteAndNegativeDeltaTimesDoNotMutateCooldownBoltOrZoomWhileZeroDeltaFiresWhenReady() {
        RailLance rail = new RailLance(W);
        for (float badDt : new float[] {Float.NaN, Float.POSITIVE_INFINITY, Float.NEGATIVE_INFINITY, -0.25f}) {
            assertFalse(rail.update(badDt, true, true, 5));
            rail.updateIdle(badDt);
            rail.updateIdle(badDt, true);
            assertTrue(rail.isReady());
            assertFalse(rail.isFlashVisible());
            assertEquals(0f, rail.getZoom(), 1e-6f);
            assertFalse(rail.consumeZoomInTriggered());
        }

        assertTrue(rail.update(0f, true, false, 3));
        assertFalse(rail.isReady());
        assertTrue(rail.isFlashVisible());
        assertFalse(rail.update(0f, true, false, 2));

        // Zoom in partway while bolt is mid-cycle, then verify bad dt leaves cooldown, bolt, and zoom untouched.
        assertFalse(rail.update(0.12f, false, true, 2));
        assertTrue(rail.consumeZoomInTriggered());
        float zoomBefore = rail.getZoom();
        float boltBefore = rail.getBolt();
        float chargeBefore = rail.getChargeProgress();
        assertTrue(zoomBefore > 0.5f);
        assertTrue(boltBefore > 0.4f);

        for (float badDt : new float[] {Float.NaN, Float.POSITIVE_INFINITY, Float.NEGATIVE_INFINITY, -0.5f}) {
            assertFalse(rail.update(badDt, true, false, 5));
            rail.updateIdle(badDt);
            rail.updateIdle(badDt, false);
            assertFalse(rail.isReady());
            assertFalse(rail.isReadyAfter(badDt));
            assertEquals(zoomBefore, rail.getZoom(), 1e-6f);
            assertEquals(boltBefore, rail.getBolt(), 1e-6f);
            assertEquals(chargeBefore, rail.getChargeProgress(), 1e-6f);
            assertFalse(rail.consumeZoomOutTriggered());
        }

        // Cancelled zoom suppression must also remain latched across invalid updateIdle(badDt, false) calls.
        rail.cancelZoom();
        rail.updateIdle(Float.NaN, false);
        rail.updateIdle(-0.1f, false);
        rail.update(DT, false, true, 2);
        assertEquals(0f, rail.getZoom(), 1e-6f, "invalid dt must not clear RMB zoom suppression");
    }
}

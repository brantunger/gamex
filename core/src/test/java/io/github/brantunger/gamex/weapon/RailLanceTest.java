package io.github.brantunger.gamex.weapon;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.Test;

class RailLanceTest {

    private static final float DT = 1f / 60f;

    @Test
    void cooldownSlugsAndBoltRecoilCycleWorkCleanly() {
        RailLance rail = new RailLance();
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
        RailLance rail = new RailLance();
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
        assertEquals(1f / RailLance.ZOOM_FACTOR, rail.getZoomFovScale(), 1e-5f);

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
}

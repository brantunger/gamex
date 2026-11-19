package io.github.brantunger.gamex.weapon;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.badlogic.gdx.math.Vector3;
import org.junit.jupiter.api.Test;

import java.util.List;

class ScattergunTest {

    @Test
    void primaryAndDoubleBlastGenerateDeterministicUnitPelletPatterns() {
        Vector3 forward = new Vector3(0.4f, -0.15f, -0.9f).nor();
        List<Vector3> primary = Scattergun.pelletDirections(forward, false);
        List<Vector3> alt = Scattergun.pelletDirections(forward, true);

        assertEquals(Scattergun.PRIMARY_PELLETS, primary.size());
        assertEquals(Scattergun.ALT_PELLETS, alt.size());
        assertEquals(1f, primary.get(0).dot(forward), 1e-6f);
        assertEquals(1f, alt.get(0).dot(forward), 1e-6f);

        float primaryRingCos = (float) Math.cos(Math.toRadians(Scattergun.PRIMARY_CONE_DEGREES));
        for (int i = 1; i < primary.size(); i++) {
            assertEquals(1f, primary.get(i).len(), 1e-5f);
            assertEquals(primaryRingCos, primary.get(i).dot(forward), 1e-4f);
        }

        float innerCos = (float) Math.cos(Math.toRadians(Scattergun.ALT_INNER_CONE_DEGREES));
        float outerCos = (float) Math.cos(Math.toRadians(Scattergun.ALT_CONE_DEGREES));
        for (int i = 1; i <= 5; i++) {
            assertEquals(innerCos, alt.get(i).dot(forward), 1e-4f);
        }
        for (int i = 6; i < alt.size(); i++) {
            assertEquals(outerCos, alt.get(i).dot(forward), 1e-4f);
        }

        // Degenerate vertical look falls back cleanly without NaNs.
        List<Vector3> upPellets = Scattergun.pelletDirections(Vector3.Y, false);
        assertEquals(Scattergun.PRIMARY_PELLETS, upPellets.size());
        for (Vector3 dir : upPellets) assertEquals(1f, dir.len(), 1e-5f);
    }

    @Test
    void linearDistanceFalloffClampsAtStartAndMaxRange() {
        assertEquals(Scattergun.PELLET_DAMAGE, Scattergun.damageAtDistance(0f), 1e-5f);
        assertEquals(Scattergun.PELLET_DAMAGE, Scattergun.damageAtDistance(Scattergun.FALLOFF_START), 1e-5f);
        float midDist = (Scattergun.FALLOFF_START + Scattergun.RANGE) * 0.5f;
        float expectedMid = Scattergun.PELLET_DAMAGE * (1f + Scattergun.MIN_FALLOFF_SCALE) * 0.5f;
        assertEquals(expectedMid, Scattergun.damageAtDistance(midDist), 1e-5f);
        assertEquals(Scattergun.PELLET_DAMAGE * Scattergun.MIN_FALLOFF_SCALE,
            Scattergun.damageAtDistance(Scattergun.RANGE), 1e-5f);
        assertEquals(Scattergun.PELLET_DAMAGE * Scattergun.MIN_FALLOFF_SCALE,
            Scattergun.damageAtDistance(100f), 1e-5f);
    }

    @Test
    void primaryDoubleBlastAndOneShellFallbackAndPumpCurveWorkCleanly() {
        Scattergun gun = new Scattergun();
        assertTrue(gun.isReady());
        assertFalse(gun.isFlashVisible());
        assertEquals(0f, gun.getPump(), 1e-6f);

        // 0 shells fires nothing.
        assertEquals(Scattergun.Shot.NONE, gun.update(1f / 60f, true, false, 0));

        // Double-blast spends 2 shells when >= 2 available.
        assertEquals(Scattergun.Shot.DOUBLE_BLAST, gun.update(1f / 60f, false, true, 4));
        assertTrue(gun.isFlashVisible());
        assertTrue(gun.isLastShotDoubleBlast());
        assertFalse(gun.isReady());
        assertEquals(0f, gun.getPump(), 1e-6f, "pump delays briefly during initial recoil kick");

        // Pump rises smoothly then returns to 0 over recovery.
        float peakPump = 0f;
        for (int i = 0; i < 65; i++) {
            assertEquals(Scattergun.Shot.NONE, gun.update(1f / 60f, true, false, 2));
            peakPump = Math.max(peakPump, gun.getPump());
            assertTrue(gun.getPump() >= 0f && gun.getPump() <= 1f);
        }
        assertTrue(peakPump > 0.85f, "pump forend reaches full rearward travel: " + peakPump);

        // Wait full alt cooldown -> ready again.
        for (int i = 0; i < 20; i++) gun.updateIdle(1f / 60f);
        assertTrue(gun.isReady());
        assertEquals(0f, gun.getPump(), 1e-6f);

        // Holding alt trigger with only 1 shell left seamlessly fires a 1-shell primary blast.
        assertEquals(Scattergun.Shot.PRIMARY, gun.update(1f / 60f, false, true, 1));
        assertFalse(gun.isLastShotDoubleBlast());
        assertEquals(1, Scattergun.Shot.PRIMARY.shellsSpent);
        assertEquals(2, Scattergun.Shot.DOUBLE_BLAST.shellsSpent);
    }

    @Test
    void isReadyAfterMatchesExactCooldownCompletionStep() {
        Scattergun gun = new Scattergun();
        assertEquals(Scattergun.Shot.PRIMARY, gun.update(1f / 60f, true, false, 1));
        gun.updateIdle(Scattergun.PRIMARY_INTERVAL - 0.01f);

        assertFalse(gun.isReady());
        assertFalse(gun.isReadyAfter(0.005f));
        assertTrue(gun.isReadyAfter(1f / 60f));

        gun.updateIdle(1f / 60f);
        assertTrue(gun.isReady());
    }

    @Test
    void nonFiniteAndNegativeDeltaTimesDoNotMutateCooldownFlashOrPumpWhileZeroDeltaFiresWhenReady() {
        Scattergun gun = new Scattergun();
        for (float badDt : new float[] {Float.NaN, Float.POSITIVE_INFINITY, Float.NEGATIVE_INFINITY, -0.2f}) {
            assertEquals(Scattergun.Shot.NONE, gun.update(badDt, true, true, 4));
            gun.updateIdle(badDt);
            assertTrue(gun.isReady());
            assertFalse(gun.isFlashVisible());
            assertEquals(0f, gun.getPump(), 1e-6f);
        }

        assertEquals(Scattergun.Shot.PRIMARY, gun.update(0f, true, false, 2));
        assertFalse(gun.isReady());
        assertTrue(gun.isFlashVisible());
        assertEquals(Scattergun.Shot.NONE, gun.update(0f, true, false, 2));

        gun.updateIdle(0.20f);
        float pumpBefore = gun.getPump();
        assertTrue(pumpBefore > 0.5f);
        for (float badDt : new float[] {Float.NaN, Float.POSITIVE_INFINITY, Float.NEGATIVE_INFINITY, -0.5f}) {
            assertEquals(Scattergun.Shot.NONE, gun.update(badDt, true, true, 4));
            gun.updateIdle(badDt);
            assertFalse(gun.isReady());
            assertFalse(gun.isReadyAfter(badDt));
            assertEquals(pumpBefore, gun.getPump(), 1e-6f);
        }

        assertEquals(Scattergun.PELLET_DAMAGE, Scattergun.damageAtDistance(Float.NaN), 1e-5f);
        List<Vector3> fallbackDirs = Scattergun.pelletDirections(new Vector3(Float.NaN, 0f, -1f), false);
        assertEquals(Scattergun.PRIMARY_PELLETS, fallbackDirs.size());
        for (Vector3 dir : fallbackDirs) assertEquals(1f, dir.len(), 1e-5f);
    }
}

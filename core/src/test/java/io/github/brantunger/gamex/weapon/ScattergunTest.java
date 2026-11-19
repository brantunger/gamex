package io.github.brantunger.gamex.weapon;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import io.github.brantunger.gamex.TestTuning;
import com.badlogic.gdx.math.Vector3;
import org.junit.jupiter.api.Test;

import java.util.List;

class ScattergunTest {

    /** The shipped weapon balance. */
    private static final WeaponTuning W = TestTuning.WEAPONS;
    /** A scattergun that is only asked for its pellet pattern and damage, never fired. */
    private static final Scattergun GUN = new Scattergun(W);

    @Test
    void primaryAndDoubleBlastGenerateDeterministicUnitPelletPatterns() {
        Vector3 forward = new Vector3(0.4f, -0.15f, -0.9f).nor();
        List<Vector3> primary = GUN.pelletDirections(forward, false);
        List<Vector3> alt = GUN.pelletDirections(forward, true);

        assertEquals(W.scattergunPrimaryPellets, primary.size());
        assertEquals(W.scattergunAltPellets, alt.size());
        assertEquals(1f, primary.get(0).dot(forward), 1e-6f);
        assertEquals(1f, alt.get(0).dot(forward), 1e-6f);

        float primaryRingCos = (float) Math.cos(Math.toRadians(W.scattergunPrimaryRings().get(0).coneDegrees));
        for (int i = 1; i < primary.size(); i++) {
            assertEquals(1f, primary.get(i).len(), 1e-5f);
            assertEquals(primaryRingCos, primary.get(i).dot(forward), 1e-4f);
        }

        float innerCos = (float) Math.cos(Math.toRadians(W.scattergunAltRings().get(0).coneDegrees));
        float outerCos = (float) Math.cos(Math.toRadians(W.scattergunAltRings().get(1).coneDegrees));
        for (int i = 1; i <= 5; i++) {
            assertEquals(innerCos, alt.get(i).dot(forward), 1e-4f);
        }
        for (int i = 6; i < alt.size(); i++) {
            assertEquals(outerCos, alt.get(i).dot(forward), 1e-4f);
        }

        // Degenerate vertical look falls back cleanly without NaNs.
        List<Vector3> upPellets = GUN.pelletDirections(Vector3.Y, false);
        assertEquals(W.scattergunPrimaryPellets, upPellets.size());
        for (Vector3 dir : upPellets) assertEquals(1f, dir.len(), 1e-5f);
    }

    @Test
    void linearDistanceFalloffClampsAtStartAndMaxRange() {
        assertEquals(W.scattergunPelletDamage, GUN.damageAtDistance(0f), 1e-5f);
        assertEquals(W.scattergunPelletDamage, GUN.damageAtDistance(W.scattergunFalloffStart), 1e-5f);
        float midDist = (W.scattergunFalloffStart + W.scattergunRange) * 0.5f;
        float expectedMid = W.scattergunPelletDamage * (1f + W.scattergunMinFalloffScale) * 0.5f;
        assertEquals(expectedMid, GUN.damageAtDistance(midDist), 1e-5f);
        assertEquals(W.scattergunPelletDamage * W.scattergunMinFalloffScale,
            GUN.damageAtDistance(W.scattergunRange), 1e-5f);
        assertEquals(W.scattergunPelletDamage * W.scattergunMinFalloffScale,
            GUN.damageAtDistance(100f), 1e-5f);
    }

    @Test
    void primaryDoubleBlastAndOneShellFallbackAndPumpCurveWorkCleanly() {
        Scattergun gun = new Scattergun(W);
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
        Scattergun gun = new Scattergun(W);
        assertEquals(Scattergun.Shot.PRIMARY, gun.update(1f / 60f, true, false, 1));
        gun.updateIdle(W.scattergunPrimaryInterval - 0.01f);

        assertFalse(gun.isReady());
        assertFalse(gun.isReadyAfter(0.005f));
        assertTrue(gun.isReadyAfter(1f / 60f));

        gun.updateIdle(1f / 60f);
        assertTrue(gun.isReady());
    }

    @Test
    void nonFiniteAndNegativeDeltaTimesDoNotMutateCooldownFlashOrPumpWhileZeroDeltaFiresWhenReady() {
        Scattergun gun = new Scattergun(W);
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

        assertEquals(W.scattergunPelletDamage, GUN.damageAtDistance(Float.NaN), 1e-5f);
        List<Vector3> fallbackDirs = GUN.pelletDirections(new Vector3(Float.NaN, 0f, -1f), false);
        assertEquals(W.scattergunPrimaryPellets, fallbackDirs.size());
        for (Vector3 dir : fallbackDirs) assertEquals(1f, dir.len(), 1e-5f);
    }
}

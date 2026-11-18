package io.github.brantunger.gamex.debug;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import io.github.brantunger.gamex.GameSimulation;
import io.github.brantunger.gamex.player.Difficulty;
import io.github.brantunger.gamex.player.PlayerInput;
import io.github.brantunger.gamex.player.PlayerLoadout;
import io.github.brantunger.gamex.weapon.WeaponSlot;
import io.github.brantunger.gamex.world.Levels;
import org.junit.jupiter.api.Test;

class DemoRunTest {

    @Test
    void orbitIsAYawWithAnOptionalPitch() {
        assertArrayEquals(new float[] {30f, 0f}, DemoRun.parseOrbit("30"), 1e-6f);
        assertArrayEquals(new float[] {30f, 15f}, DemoRun.parseOrbit("30,15"), 1e-6f);
        assertArrayEquals(new float[] {-90f, 22.5f}, DemoRun.parseOrbit(" -90 , 22.5 "), 1e-6f);
    }

    @Test
    void missingOrbitIsNoOrbit() {
        assertNull(DemoRun.parseOrbit(null));
        assertNull(DemoRun.parseOrbit(""));
    }

    @Test
    void malformedOrbitIsNoOrbit() {
        assertNull(DemoRun.parseOrbit("abc"));
        assertNull(DemoRun.parseOrbit("30,abc"));
        assertNull(DemoRun.parseOrbit(" "));
        assertNull(DemoRun.parseOrbit(",5"));
        assertNull(DemoRun.parseOrbit("30, "));
    }

    @Test
    void orbitMadeOnlyOfCommasIsNoOrbitRatherThanACrash() {
        // Splitting such text leaves no parts at all.
        assertNull(DemoRun.parseOrbit(","));
        assertNull(DemoRun.parseOrbit(",,"));
    }

    @Test
    void jumpScriptVaultsTheTrainingRangeShockGrateWithoutTakingDamage() {
        // The same setup MainGame gives the "jump" script.
        Levels.Stage range = Levels.trainingRange();
        GameSimulation simulation = new GameSimulation(range.map,
            new PlayerLoadout(100f, 0f, Difficulty.NORMAL, true, 16, WeaponSlot.SCATTERGUN),
            range.completeOnAllTargets);
        simulation.player.position.set(9.8f, 15.5f);
        simulation.player.look(0f, -14f);

        DemoRun demo = DemoRun.forScript("jump");
        PlayerInput input = new PlayerInput();
        boolean wasOverTheGrate = false;
        for (int frame = 0; frame < 240; frame++) {
            demo.nextFrame(input);
            simulation.update(DemoRun.FRAME_TIME, input);
            if (range.map.isHazardAt(simulation.player.position.x, simulation.player.position.y)) {
                wasOverTheGrate = true;
                assertTrue(simulation.player.getJumpHeight() > GameSimulation.HAZARD_CLEARANCE_HEIGHT,
                    "airborne over the grate on frame " + (frame + 1));
            }
        }

        assertTrue(wasOverTheGrate, "the run crosses the grate");
        assertTrue(simulation.player.position.x > 13f, "the run ends past the grate");
        assertEquals(0f, simulation.getDamageTaken(), 0f);
    }
}

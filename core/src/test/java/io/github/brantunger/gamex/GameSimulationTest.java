package io.github.brantunger.gamex;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import io.github.brantunger.gamex.player.Player;
import io.github.brantunger.gamex.player.PlayerInput;
import io.github.brantunger.gamex.weapon.BlastCharger;
import io.github.brantunger.gamex.weapon.Pistol;
import io.github.brantunger.gamex.world.Effect;
import io.github.brantunger.gamex.world.GameMap;
import io.github.brantunger.gamex.world.Levels;
import io.github.brantunger.gamex.world.Target;
import org.junit.jupiter.api.Test;

class GameSimulationTest {

    private static final float FRAME = 1f / 60f;

    /** The player faces east along a corridor with a canister two cells ahead. */
    private static GameSimulation corridorWithCanister() {
        return new GameSimulation(GameMap.parse(
            "##########",
            "#>.o.....#",
            "##########"));
    }

    private static GameSimulation emptyCorridor() {
        return new GameSimulation(GameMap.parse(
            "##########",
            "#>.......#",
            "##########"));
    }

    private static int count(GameSimulation simulation, Effect.Type type) {
        int count = 0;
        for (Effect effect : simulation.effects) {
            if (effect.type == type) count++;
        }
        return count;
    }

    @Test
    void startsWithThePlayerAtTheSpawnAndEveryTargetAlive() {
        GameSimulation simulation = new GameSimulation(Levels.first());

        assertEquals(simulation.map.getSpawnX(), simulation.player.position.x, 1e-6f);
        assertEquals(simulation.map.getSpawnZ(), simulation.player.position.y, 1e-6f);
        assertEquals(simulation.map.getSpawnYaw(), simulation.player.getYaw(), 1e-6f);
        assertEquals(simulation.map.getTargetSpawns().size(), simulation.targets.size());
        assertEquals(simulation.targets.size(), simulation.getTargetsRemaining());
    }

    @Test
    void longFrameIsClampedSoThePlayerOnlyMovesOneShortStep() {
        GameSimulation simulation = emptyCorridor();
        PlayerInput input = new PlayerInput();
        input.moveForward = 1f;

        simulation.update(30f, input);

        assertEquals(1.5f + Player.MOVE_SPEED * GameSimulation.MAX_DT, simulation.player.position.x, 1e-4f);
    }

    @Test
    void pistolShotDamagesTheTargetUnderTheCrosshairAndThreeShotsDestroyIt() {
        GameSimulation simulation = corridorWithCanister();
        Target canister = simulation.targets.get(0);
        PlayerInput input = new PlayerInput();
        input.fire = true;

        simulation.update(FRAME, input);
        assertEquals(50f - Pistol.DAMAGE, canister.getHealth(), 1e-4f);
        assertEquals(1, count(simulation, Effect.Type.SPARK));
        assertTrue(simulation.pistol.isFlashVisible());

        // The trigger stays held. The second shot leaves 0.3 seconds, 18 frames, after the first.
        for (int i = 0; i < 20; i++) simulation.update(FRAME, input);
        assertEquals(50f - 2f * Pistol.DAMAGE, canister.getHealth(), 1e-4f);
        assertFalse(canister.isDestroyed(), "two shots are not enough");
        assertEquals(1, simulation.getTargetsRemaining());

        for (int i = 0; i < 20; i++) simulation.update(FRAME, input);

        assertTrue(canister.isDestroyed());
        assertEquals(0, simulation.getTargetsRemaining());
        assertEquals(1, count(simulation, Effect.Type.EXPLOSION), "the canister bursts");
    }

    @Test
    void pistolShotAtAWallLeavesAMarkJustOffItsFace() {
        GameSimulation simulation = emptyCorridor();
        PlayerInput input = new PlayerInput();
        input.fire = true;

        simulation.update(FRAME, input);

        assertEquals(1, count(simulation, Effect.Type.IMPACT));
        Effect mark = simulation.effects.get(0);
        assertEquals(9f, mark.position.x, 0.05f);
        assertTrue(mark.position.x < 9f, "the mark sits on the open side of the wall face");
        assertEquals(Player.EYE_HEIGHT, mark.position.y, 1e-4f);
        assertEquals(-1f, mark.normal.x, 1e-5f);
    }

    @Test
    void chargedBlastFliesExplodesAndDestroysTheTarget() {
        GameSimulation simulation = corridorWithCanister();
        Target canister = simulation.targets.get(0);
        PlayerInput input = new PlayerInput();

        input.charge = true;
        for (int i = 0; i < 90; i++) simulation.update(FRAME, input);
        assertTrue(simulation.charger.isCharging());
        assertTrue(simulation.projectiles.isEmpty(), "nothing is fired until the button is released");

        input.charge = false;
        simulation.update(FRAME, input);
        assertEquals(1, simulation.projectiles.size());
        assertEquals(100f - BlastCharger.MAX_COST, simulation.energy.getEnergy(), 0.5f);
        assertFalse(canister.isDestroyed(), "the blast is slow: it has not arrived yet");

        for (int i = 0; i < 60; i++) simulation.update(FRAME, input);

        assertTrue(simulation.projectiles.isEmpty());
        assertTrue(canister.isDestroyed());
    }

    @Test
    void pausingDropsAChargeSoThatNothingFiresOnReturn() {
        GameSimulation simulation = emptyCorridor();
        PlayerInput input = new PlayerInput();
        input.charge = true;
        for (int i = 0; i < 60; i++) simulation.update(FRAME, input);
        assertTrue(simulation.charger.isReadyToFire());

        // The game is paused, and the button is let go while it is.
        simulation.pause();
        input.charge = false;
        simulation.update(FRAME, input);

        assertTrue(simulation.projectiles.isEmpty());
        assertEquals(100f, simulation.energy.getEnergy(), 1e-4f);
    }

    @Test
    void playerCannotWalkThroughALiveTargetButCanOnceItIsDestroyed() {
        GameSimulation simulation = corridorWithCanister();
        Target canister = simulation.targets.get(0);
        PlayerInput input = new PlayerInput();
        input.moveForward = 1f;

        for (int i = 0; i < 120; i++) simulation.update(FRAME, input);
        assertEquals(3.5f - canister.getRadius() - Player.RADIUS, simulation.player.position.x, 1e-3f);

        canister.damage(1000f);
        for (int i = 0; i < 60; i++) simulation.update(FRAME, input);
        assertTrue(simulation.player.position.x > 4.5f, "walked on to x = " + simulation.player.position.x);
    }

    @Test
    void simulatedTimeIsTheSumOfTheClampedSteps() {
        GameSimulation simulation = emptyCorridor();
        PlayerInput input = new PlayerInput();

        simulation.update(0.01f, input);
        simulation.update(5f, input);

        assertEquals(0.01f + GameSimulation.MAX_DT, simulation.getTime(), 1e-6f);
    }

    @Test
    void pistolViewmodelKicksOnAShotAndBobsOnlyWhileThePlayerActuallyMoves() {
        GameSimulation simulation = emptyCorridor();
        PlayerInput input = new PlayerInput();

        input.fire = true;
        simulation.update(FRAME, input);
        input.fire = false;
        simulation.update(FRAME, input);
        simulation.update(FRAME, input);
        assertTrue(simulation.viewmodel.getRecoil() > 0.8f, "recoil " + simulation.viewmodel.getRecoil());

        // Walk the length of the corridor and on into its end wall: the bob dies away once the wall stops the player.
        input.moveForward = 1f;
        float widest = 0f;
        for (int i = 0; i < 90; i++) {
            simulation.update(FRAME, input);
            widest = Math.max(widest, Math.abs(simulation.viewmodel.getBobX()));
        }
        assertTrue(widest > 0.003f, "bobbed while walking: " + widest);
        for (int i = 0; i < 240; i++) simulation.update(FRAME, input);
        assertEquals(0f, simulation.viewmodel.getBobX(), 1e-5f);
    }

    @Test
    void quickTapOfTheBlastButtonFiresNothing() {
        GameSimulation simulation = emptyCorridor();
        PlayerInput input = new PlayerInput();

        input.charge = true;
        for (int i = 0; i < 5; i++) simulation.update(FRAME, input);
        input.charge = false;
        simulation.update(FRAME, input);

        assertTrue(simulation.projectiles.isEmpty());
        assertEquals(100f, simulation.energy.getEnergy(), 1e-4f);
    }

    @Test
    void effectsAreRemovedOnceTheyHaveRunTheirCourse() {
        GameSimulation simulation = emptyCorridor();
        PlayerInput input = new PlayerInput();
        input.fire = true;
        simulation.update(FRAME, input);
        input.fire = false;
        assertEquals(1, simulation.effects.size());

        for (int i = 0; i < (int) (Effect.Type.IMPACT.lifetime / GameSimulation.MAX_DT) + 2; i++) {
            simulation.update(GameSimulation.MAX_DT, input);
        }

        assertTrue(simulation.effects.isEmpty());
    }
}

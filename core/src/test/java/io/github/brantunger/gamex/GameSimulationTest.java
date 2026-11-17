package io.github.brantunger.gamex;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import io.github.brantunger.gamex.player.Difficulty;
import io.github.brantunger.gamex.player.Player;
import io.github.brantunger.gamex.player.PlayerInput;
import io.github.brantunger.gamex.player.PlayerLoadout;
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
    void pistolViewmodelSwaysWithATurnAndSettlesOnceTheViewIsHeldAtThePitchLimit() {
        GameSimulation simulation = emptyCorridor();
        PlayerInput input = new PlayerInput();

        // Turn right and tilt up at 180 degrees per second: the gun lags behind, to the left and downwards.
        input.yawDelta = 3f;
        input.pitchDelta = 3f;
        for (int i = 0; i < 10; i++) simulation.update(FRAME, input);
        assertTrue(simulation.player.getPitch() < Player.MAX_PITCH, "still tilting up");
        assertTrue(simulation.viewmodel.getSwayYaw() < -0.5f, "yaw sway " + simulation.viewmodel.getSwayYaw());
        assertTrue(simulation.viewmodel.getSwayPitch() < -0.5f, "pitch sway " + simulation.viewmodel.getSwayPitch());

        // The mouse keeps pushing upwards against the limit: the view no longer moves, so the gun comes back.
        input.yawDelta = 0f;
        for (int i = 0; i < 240; i++) simulation.update(FRAME, input);
        assertEquals(Player.MAX_PITCH, simulation.player.getPitch(), 1e-6f);
        assertEquals(0f, simulation.viewmodel.getSwayPitch(), 1e-3f);
    }

    @Test
    void pistolViewmodelSwayIsSteadyWhileATurnCrossesNorth() {
        GameSimulation simulation = emptyCorridor();
        PlayerInput input = new PlayerInput();

        // The player faces east, at a yaw of 90, and turns left through north, where the yaw wraps from 0 to 360.
        input.yawDelta = -3f;
        for (int i = 0; i < 20; i++) simulation.update(FRAME, input);
        assertTrue(simulation.viewmodel.getSwayYaw() > 0.9f, "yaw sway " + simulation.viewmodel.getSwayYaw());
        for (int i = 0; i < 20; i++) {
            float before = simulation.viewmodel.getSwayYaw();
            simulation.update(FRAME, input);
            assertTrue(simulation.viewmodel.getSwayYaw() >= before - 1e-3f, "yaw sway fell from " + before + " to "
                + simulation.viewmodel.getSwayYaw() + " at a yaw of " + simulation.player.getYaw());
        }
        assertTrue(simulation.player.getYaw() > 180f, "crossed north to a yaw of " + simulation.player.getYaw());
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

    @Test
    void canisterExplosionChainsIntoAdjacentCanisterAndDamagesClosePlayer() {
        GameMap chainMap = GameMap.parse(
            "##########",
            "#>.oo....#",
            "##########");
        GameSimulation simulation = new GameSimulation(chainMap);
        PlayerInput input = new PlayerInput();
        input.fire = true;

        for (int i = 0; i < 45; i++) simulation.update(FRAME, input);

        assertTrue(simulation.targets.get(0).isDestroyed(), "first canister shot");
        assertTrue(simulation.targets.get(1).isDestroyed(), "second canister destroyed by chain blast");
        assertTrue(simulation.player.getHealth() < Player.MAX_HEALTH, "close blast splashed the player");
        assertTrue(simulation.getDamageTaken() > 0f);
    }

    @Test
    void dashCancelsChargingBlastAndCollectsPickupsAlongTheWay() {
        GameMap pickupHall = GameMap.parse(
            "##########",
            "#>+aeX...#",
            "##########");
        GameSimulation simulation = new GameSimulation(pickupHall, new PlayerLoadout(60f, 0f, Difficulty.EASY), false);
        simulation.energy.setEnergy(60f);
        PlayerInput input = new PlayerInput();

        // Start charging, then cancel with a dash.
        input.charge = true;
        for (int i = 0; i < 20; i++) simulation.update(FRAME, input);
        assertTrue(simulation.charger.isCharging());

        input.dash = true;
        input.moveForward = 1f;
        simulation.update(FRAME, input);
        input.dash = false;
        input.charge = false;
        assertFalse(simulation.charger.isCharging(), "dash cancelled blast charge");
        assertTrue(simulation.projectiles.isEmpty(), "cancelled charge fired nothing");

        for (int i = 0; i < 90; i++) simulation.update(FRAME, input);

        // Easy pickup scale is 1.25x: +31 health, +31 armor, and reaching X clears the stage.
        assertEquals(91f, simulation.player.getHealth(), 1f);
        assertEquals(31f, simulation.player.getArmor(), 1f);
        assertEquals(3, simulation.getPickupsCollected());
        assertTrue(simulation.isLevelCleared());
    }

    @Test
    void completeOnAllTargetsClearsStageOnceLastTargetBreaks() {
        GameMap tinyRange = GameMap.parse(
            "#######",
            "#>.o..#",
            "#######");
        GameSimulation simulation = new GameSimulation(tinyRange, PlayerLoadout.fresh(), true);
        PlayerInput input = new PlayerInput();
        input.fire = true;

        for (int i = 0; i < 50; i++) simulation.update(FRAME, input);

        assertTrue(simulation.isLevelCleared());
        float clearTime = simulation.getTime();
        simulation.update(FRAME, input);
        assertEquals(clearTime, simulation.getTime(), 1e-6f, "timer locks once cleared");
    }

    @Test
    void pistolKillsDroneAndEnemyBoltCanDetonateCanisterToDamageNearbyGuard() {
        GameMap combatHall = GameMap.parse(
            "##############",
            "#>..D........#",
            "#............#",
            "#.......oG...#",
            "##############");
        GameSimulation simulation = new GameSimulation(combatHall);
        assertEquals(2, simulation.enemies.size());
        assertEquals(2, simulation.getEnemiesRemaining());

        // Two pistol shots destroy the 40-HP Rusher Drone and alert the Guard via gunfire noise.
        PlayerInput input = new PlayerInput();
        input.fire = true;
        for (int i = 0; i < 25; i++) simulation.update(FRAME, input);
        assertTrue(simulation.enemies.get(0).isDestroyed());
        assertEquals(1, simulation.getEnemiesDestroyed());

        // Direct canister explosion kills or heavily damages an adjacent enemy.
        GameMap trapHall = GameMap.parse(
            "#########",
            "#>..oD..#",
            "#########");
        GameSimulation trapSim = new GameSimulation(trapHall);
        for (int i = 0; i < 45; i++) trapSim.update(FRAME, input);
        assertTrue(trapSim.targets.get(0).isDestroyed(), "canister destroyed by pistol");
        assertTrue(trapSim.enemies.get(0).isDestroyed(), "adjacent drone destroyed by canister blast");
    }

    @Test
    void gunshotsAlertNearbyEnemiesAndEnemyProjectilesHurtPlayer() {
        GameMap alertRoom = GameMap.parse(
            "############",
            "#>.....T...#",
            "############");
        GameSimulation simulation = new GameSimulation(alertRoom);
        PlayerInput input = new PlayerInput();
        input.fire = true;
        simulation.update(FRAME, input);
        input.fire = false;

        // Shot noise at 6u wakes the Turret immediately.
        assertFalse(simulation.enemies.get(0).getState() == io.github.brantunger.gamex.world.Enemy.State.DORMANT);

        // Stand still while turret aims and fires; player takes damage and damageTaken tracks it.
        for (int i = 0; i < 240; i++) simulation.update(FRAME, input);
        assertTrue(simulation.player.getHealth() < Player.MAX_HEALTH);
        assertTrue(simulation.getDamageTaken() > 0f);
    }

    @Test
    void blastSpawnsAheadOfEyeAtMuzzleTriggersRecoilAndConvergesOnCrosshairTarget() {
        GameMap range = GameMap.parse(
            "##############",
            "#>.......O...#",
            "##############");
        GameSimulation simulation = new GameSimulation(range);
        PlayerInput input = new PlayerInput();
        input.charge = true;
        for (int i = 0; i < 40; i++) simulation.update(FRAME, input);
        input.charge = false;
        simulation.update(FRAME, input);

        assertEquals(1, simulation.projectiles.size());
        com.badlogic.gdx.math.Vector3 p = simulation.projectiles.get(0).position;
        float aheadOfEye = p.x - simulation.player.position.x;
        float rightOfEye = p.z - simulation.player.position.y;
        assertTrue(aheadOfEye > 0.4f && aheadOfEye < 0.6f, "ahead of near clip plane: " + aheadOfEye);
        assertTrue(rightOfEye > 0.05f, "spawned from right-hand muzzle: " + rightOfEye);

        // Next frame: viewmodel kicks back and orb flies smoothly toward the centered ring board.
        simulation.update(FRAME, input);
        assertTrue(simulation.viewmodel.getRecoil() > 0f, "release kicks viewmodel");

        for (int i = 0; i < 120 && !simulation.projectiles.isEmpty(); i++) {
            simulation.update(FRAME, input);
        }
        assertTrue(simulation.projectiles.isEmpty());
        assertTrue(simulation.targets.get(0).getHealth() < Target.Kind.RING_BOARD.maxHealth);
    }
}

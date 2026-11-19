package io.github.brantunger.gamex;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.badlogic.gdx.math.Vector3;
import io.github.brantunger.gamex.audio.SoundEvent;
import io.github.brantunger.gamex.player.Difficulty;
import io.github.brantunger.gamex.player.Player;
import io.github.brantunger.gamex.player.PlayerInput;
import io.github.brantunger.gamex.player.PlayerLoadout;
import io.github.brantunger.gamex.weapon.BlastCharger;
import io.github.brantunger.gamex.weapon.BlastProjectile;
import io.github.brantunger.gamex.weapon.EnemyProjectile;
import io.github.brantunger.gamex.weapon.Pistol;
import io.github.brantunger.gamex.weapon.Scattergun;
import io.github.brantunger.gamex.weapon.WeaponSlot;
import io.github.brantunger.gamex.world.Door;
import io.github.brantunger.gamex.world.Effect;
import io.github.brantunger.gamex.world.Enemy;
import io.github.brantunger.gamex.world.EnemyTuning;
import io.github.brantunger.gamex.world.GameMap;
import io.github.brantunger.gamex.world.Levels;
import io.github.brantunger.gamex.world.Pickup;
import io.github.brantunger.gamex.world.Target;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;

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

    private static int countSound(List<SoundEvent> events, SoundEvent.Type type) {
        int count = 0;
        for (SoundEvent event : events) {
            if (event.type == type) count++;
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
        assertEquals(50f - TestTuning.WEAPONS.pistolDamage, canister.getHealth(), 1e-4f);
        assertEquals(1, count(simulation, Effect.Type.SPARK));
        assertTrue(simulation.pistol.isFlashVisible());

        // The trigger stays held. The second shot leaves 0.3 seconds, 18 frames, after the first.
        for (int i = 0; i < 20; i++) simulation.update(FRAME, input);
        assertEquals(50f - 2f * TestTuning.WEAPONS.pistolDamage, canister.getHealth(), 1e-4f);
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
        assertEquals(100f - TestTuning.WEAPONS.blastMaxCost, simulation.energy.getEnergy(), 0.5f);
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

    @Test
    void pistolShotWallImpactAndTargetDestroyEmitExpectedSoundEvents() {
        // Long range beyond the pistol's range hits nothing: only PISTOL_SHOT.
        String wallRow = "#".repeat(70);
        String openRow = "#>" + ".".repeat(67) + "#";
        GameSimulation longSim = new GameSimulation(GameMap.parse(wallRow, openRow, wallRow));
        PlayerInput input = new PlayerInput();
        input.fire = true;
        longSim.update(FRAME, input);
        List<SoundEvent> airShot = longSim.drainSoundEvents();
        assertEquals(1, airShot.size());
        assertEquals(SoundEvent.Type.PISTOL_SHOT, airShot.get(0).type);
        assertFalse(airShot.get(0).spatial);

        // Short corridor: PISTOL_SHOT + spatial WALL_IMPACT.
        GameSimulation wallSim = emptyCorridor();
        wallSim.update(FRAME, input);
        List<SoundEvent> wallShot = wallSim.drainSoundEvents();
        assertEquals(2, wallShot.size());
        assertEquals(SoundEvent.Type.PISTOL_SHOT, wallShot.get(0).type);
        assertEquals(SoundEvent.Type.WALL_IMPACT, wallShot.get(1).type);
        assertTrue(wallShot.get(1).spatial);
        assertEquals(9f, wallShot.get(1).x, 0.05f);

        // Canister: first hit emits TARGET_HIT, third hit emits spatial EXPLOSION.
        GameSimulation canisterSim = corridorWithCanister();
        canisterSim.update(FRAME, input);
        List<SoundEvent> firstHit = canisterSim.drainSoundEvents();
        assertEquals(1, countSound(firstHit, SoundEvent.Type.PISTOL_SHOT));
        assertEquals(1, countSound(firstHit, SoundEvent.Type.TARGET_HIT));
        for (int i = 0; i < 40; i++) canisterSim.update(FRAME, input);
        List<SoundEvent> lethalHits = canisterSim.drainSoundEvents();
        assertEquals(1, countSound(lethalHits, SoundEvent.Type.TARGET_HIT));
        assertEquals(1, countSound(lethalHits, SoundEvent.Type.EXPLOSION));

        // Ring board (120 HP = 6 shots): final shot emits TARGET_DESTROYED.
        GameSimulation boardSim = new GameSimulation(GameMap.parse("#########", "#>.O....#", "#########"));
        for (int i = 0; i < 100 && !boardSim.targets.get(0).isDestroyed(); i++) {
            boardSim.update(FRAME, input);
        }
        assertTrue(boardSim.targets.get(0).isDestroyed());
        List<SoundEvent> boardEvents = boardSim.drainSoundEvents();
        assertEquals(1, countSound(boardEvents, SoundEvent.Type.TARGET_DESTROYED));
    }

    @Test
    void blastChargeAudioStopsOnPauseDashOrShortReleaseAndEmitsOnReleaseAndExplosion() {
        GameSimulation simulation = emptyCorridor();
        PlayerInput input = new PlayerInput();

        // Charging starts active charge audio, and pause() cancels it cleanly.
        input.charge = true;
        for (int i = 0; i < 20; i++) simulation.update(FRAME, input);
        assertTrue(simulation.isBlastChargingAudioActive());
        assertTrue(simulation.getBlastChargeProgress() > 0f);
        simulation.pause();
        assertFalse(simulation.isBlastChargingAudioActive());
        assertEquals(0f, simulation.getBlastChargeProgress(), 1e-6f);
        input.charge = false;
        simulation.update(FRAME, input);
        assertEquals(0, countSound(simulation.drainSoundEvents(), SoundEvent.Type.BLAST_RELEASE));

        // Dashing cancels charge audio and emits PLAYER_DASH without firing.
        input.charge = true;
        for (int i = 0; i < 15; i++) simulation.update(FRAME, input);
        assertTrue(simulation.isBlastChargingAudioActive());
        input.dash = true;
        simulation.update(FRAME, input);
        input.dash = false;
        input.charge = false;
        assertFalse(simulation.isBlastChargingAudioActive());
        List<SoundEvent> dashEvents = simulation.drainSoundEvents();
        assertEquals(1, countSound(dashEvents, SoundEvent.Type.PLAYER_DASH));
        assertEquals(0, countSound(dashEvents, SoundEvent.Type.BLAST_RELEASE));

        // Sub-threshold tap cancels without BLAST_RELEASE.
        input.charge = true;
        for (int i = 0; i < 5; i++) simulation.update(FRAME, input);
        input.charge = false;
        simulation.update(FRAME, input);
        assertFalse(simulation.isBlastChargingAudioActive());
        assertEquals(0, countSound(simulation.drainSoundEvents(), SoundEvent.Type.BLAST_RELEASE));

        // Valid release emits BLAST_RELEASE, then impact emits spatial EXPLOSION.
        input.charge = true;
        for (int i = 0; i < 60; i++) simulation.update(FRAME, input);
        input.charge = false;
        simulation.update(FRAME, input);
        List<SoundEvent> releaseEvents = simulation.drainSoundEvents();
        assertEquals(1, countSound(releaseEvents, SoundEvent.Type.BLAST_RELEASE));
        for (int i = 0; i < 120 && !simulation.projectiles.isEmpty(); i++) {
            simulation.update(FRAME, input);
        }
        List<SoundEvent> impactEvents = simulation.drainSoundEvents();
        assertEquals(1, countSound(impactEvents, SoundEvent.Type.EXPLOSION));
        assertTrue(impactEvents.stream().anyMatch(e -> e.type == SoundEvent.Type.EXPLOSION && e.spatial));
    }

    @Test
    void lowEnergyRefusalIsEdgeTriggeredForChargeAndDash() {
        GameSimulation simulation = emptyCorridor();
        simulation.energy.setEnergy(10f);
        PlayerInput input = new PlayerInput();

        input.charge = true;
        for (int i = 0; i < 10; i++) simulation.update(FRAME, input);
        assertEquals(1, countSound(simulation.drainSoundEvents(), SoundEvent.Type.LOW_ENERGY));

        input.charge = false;
        simulation.update(FRAME, input);
        simulation.energy.setEnergy(10f);
        input.dash = true;
        simulation.update(FRAME, input);
        simulation.update(FRAME, input);
        assertEquals(1, countSound(simulation.drainSoundEvents(), SoundEvent.Type.LOW_ENERGY));
    }

    @Test
    void pickupsAndEnemyCombatEmitExpectedPlayerAndSpatialSounds() {
        GameSimulation pickupSim = new GameSimulation(
            GameMap.parse("#########", "#>+ae...#", "#########"),
            new PlayerLoadout(60f, 0f, Difficulty.NORMAL), false);
        pickupSim.energy.setEnergy(50f);
        PlayerInput input = new PlayerInput();
        input.moveForward = 1f;
        for (int i = 0; i < 60; i++) pickupSim.update(FRAME, input);
        List<SoundEvent> pickupEvents = pickupSim.drainSoundEvents();
        assertEquals(1, countSound(pickupEvents, SoundEvent.Type.PICKUP_HEALTH));
        assertEquals(1, countSound(pickupEvents, SoundEvent.Type.PICKUP_ARMOR));
        assertEquals(1, countSound(pickupEvents, SoundEvent.Type.PICKUP_ENERGY));

        // Guard (100 HP): pistol hit emits ENEMY_HIT, charged blast kill emits ENEMY_DESTROYED.
        GameSimulation guardSim = new GameSimulation(GameMap.parse("###########", "#>.G......#", "###########"));
        input.clear();
        input.fire = true;
        guardSim.update(FRAME, input);
        assertEquals(1, countSound(guardSim.drainSoundEvents(), SoundEvent.Type.ENEMY_HIT));
        input.fire = false;
        input.charge = true;
        for (int i = 0; i < 60; i++) guardSim.update(FRAME, input);
        input.charge = false;
        for (int i = 0; i < 120 && !guardSim.enemies.get(0).isDestroyed(); i++) {
            guardSim.update(FRAME, input);
        }
        assertTrue(guardSim.enemies.get(0).isDestroyed());
        assertEquals(1, countSound(guardSim.drainSoundEvents(), SoundEvent.Type.ENEMY_DESTROYED));

        // Drone lunge & Turret shoot/player hurt/death.
        GameSimulation droneSim = new GameSimulation(GameMap.parse("########", "#>D....#", "########"));
        input.clear();
        for (int i = 0; i < 60; i++) droneSim.update(FRAME, input);
        List<SoundEvent> droneEvents = droneSim.drainSoundEvents();
        assertTrue(countSound(droneEvents, SoundEvent.Type.DRONE_LUNGE) >= 1);
        assertTrue(countSound(droneEvents, SoundEvent.Type.PLAYER_HURT) >= 1);

        GameSimulation turretSim = new GameSimulation(
            GameMap.parse("#########", "#>..T...#", "#########"),
            new PlayerLoadout(5f, 0f, Difficulty.NORMAL), false);
        input.fire = true;
        turretSim.update(FRAME, input);
        input.fire = false;
        for (int i = 0; i < 240 && !turretSim.player.isDead(); i++) {
            turretSim.update(FRAME, input);
        }
        assertTrue(turretSim.player.isDead());
        List<SoundEvent> turretEvents = turretSim.drainSoundEvents();
        assertTrue(countSound(turretEvents, SoundEvent.Type.ENEMY_SHOOT) >= 1);
        assertEquals(1, countSound(turretEvents, SoundEvent.Type.PLAYER_DEATH));
    }

    @Test
    void unlockedBulkheadDoorOpensWithEStaysOpenAndUnblocksMovement() {
        GameSimulation sim = new GameSimulation(GameMap.parse(
            "#######",
            "#.#...#",
            "#.>=.X#",
            "#.#...#",
            "#######"));
        PlayerInput input = new PlayerInput();

        // Closed bulkhead shows interact prompt and blocks movement into the east hall.
        assertEquals("[E] OPEN BULKHEAD", sim.getInteractionPrompt());
        input.moveForward = 1f;
        for (int i = 0; i < 40; i++) sim.update(FRAME, input);
        assertTrue(sim.player.position.x < 3.5f, "blocked by closed bulkhead: " + sim.player.position.x);

        // Pressing E opens the bulkhead and emits spatial DOOR_OPEN.
        sim.drainSoundEvents();
        input.moveForward = 0f;
        input.interact = true;
        sim.update(FRAME, input);
        input.interact = false;
        assertTrue(sim.doors.get(0).isOpening());
        assertEquals(1, countSound(sim.drainSoundEvents(), SoundEvent.Type.DOOR_OPEN));
        assertNull(sim.getInteractionPrompt(), "opening doors clear the interact prompt");

        // After retracting smoothly into ceiling, it remains open permanently and lets player reach X.
        for (int i = 0; i < 35; i++) sim.update(FRAME, input);
        assertTrue(sim.doors.get(0).isOpen());
        input.moveForward = 1f;
        for (int i = 0; i < 60 && !sim.isLevelCleared(); i++) sim.update(FRAME, input);
        assertTrue(sim.doors.get(0).isOpen());
        assertTrue(sim.isLevelCleared(), "walked through permanently open bulkhead to exit");
    }

    @Test
    void keycardsUnlockMatchingAmberAndCyanBulkheadsWhileRefusingWithoutCard() {
        GameSimulation sim = new GameSimulation(GameMap.parse(
            "#########",
            "###...###",
            "#k>A.C.c#",
            "###...###",
            "#########"));
        PlayerInput input = new PlayerInput();

        // Attempt Amber bulkhead before picking up Amber keycard.
        assertEquals("NEEDS AMBER KEYCARD", sim.getInteractionPrompt());
        input.interact = true;
        sim.update(FRAME, input);
        input.interact = false;
        assertFalse(sim.doors.get(0).isOpening());
        assertEquals("NEEDS AMBER KEYCARD", sim.getToastMessage());
        assertEquals(1, countSound(sim.drainSoundEvents(), SoundEvent.Type.DOOR_LOCKED));

        // Walk west to pick up Amber keycard.
        input.moveForward = -1f;
        for (int i = 0; i < 35; i++) sim.update(FRAME, input);
        assertTrue(sim.hasKeycard(Pickup.Kind.AMBER_KEYCARD));
        assertFalse(sim.hasKeycard(Pickup.Kind.CYAN_KEYCARD));
        assertEquals(1, countSound(sim.drainSoundEvents(), SoundEvent.Type.PICKUP_KEYCARD));

        // Walk east up to Amber bulkhead and unlock it.
        input.moveForward = 1f;
        for (int i = 0; i < 45; i++) sim.update(FRAME, input);
        assertEquals("[E] UNLOCK AMBER BULKHEAD", sim.getInteractionPrompt());
        input.moveForward = 0f;
        input.interact = true;
        sim.update(FRAME, input);
        input.interact = false;
        assertTrue(sim.doors.get(0).isOpening());
        assertEquals("AMBER BULKHEAD UNLOCKED", sim.getToastMessage());
        assertEquals(1, countSound(sim.drainSoundEvents(), SoundEvent.Type.DOOR_OPEN));

        // Let Amber door open, cross through, confirm Cyan bulkhead still refuses.
        for (int i = 0; i < 35; i++) sim.update(FRAME, input);
        input.moveForward = 1f;
        for (int i = 0; i < 45; i++) sim.update(FRAME, input);
        assertEquals("NEEDS CYAN KEYCARD", sim.getInteractionPrompt());
    }

    @Test
    void securityConsoleIsIndestructibleCoverOverridingAllLockdownsAndAlertingEnemies() {
        GameSimulation sim = new GameSimulation(GameMap.parse(
            "#########",
            "#..#....#",
            "#>S...LD#",
            "#..#....#",
            "#..L....#",
            "#########"));
        PlayerInput input = new PlayerInput();

        // Shooting the Security Console acts as indestructible cover without fake hitmarkers.
        input.fire = true;
        sim.update(FRAME, input);
        input.fire = false;
        assertFalse(sim.consoles.get(0).isDestroyed());
        assertEquals(0f, sim.getHitMarker(), 1e-6f);
        assertEquals(1, countSound(sim.drainSoundEvents(), SoundEvent.Type.WALL_IMPACT));
        assertEquals(Enemy.State.DORMANT, sim.enemies.get(0).getState());

        // Activating the console overrides every L lockdown door simultaneously and wakes the ambush drone.
        assertEquals("[E] OVERRIDE LOCKDOWN", sim.getInteractionPrompt());
        input.interact = true;
        sim.update(FRAME, input);
        input.interact = false;
        assertTrue(sim.consoles.get(0).isActivated());
        assertEquals("LOCKDOWN OVERRIDDEN", sim.getToastMessage());
        assertTrue(sim.doors.get(0).isOpening());
        assertTrue(sim.doors.get(1).isOpening());
        assertTrue(sim.enemies.get(0).getState() != Enemy.State.DORMANT);
        List<SoundEvent> events = sim.drainSoundEvents();
        assertEquals(1, countSound(events, SoundEvent.Type.CONSOLE_ACTIVATE));
        assertEquals(2, countSound(events, SoundEvent.Type.DOOR_OPEN));
    }

    @Test
    void scattergunPickupAutoEquipsAndShellBoxesRespectMaxPoolCapacity() {
        GameSimulation sim = new GameSimulation(GameMap.parse(
            "###########",
            "#>Wsssss..#",
            "###########"));
        PlayerInput input = new PlayerInput();
        assertFalse(sim.hasScattergun());
        assertEquals(0, sim.getShells());
        assertEquals(WeaponSlot.PISTOL, sim.getActiveWeapon());

        // Attempting to swap to slot 2 before unlocking does nothing.
        input.weaponSlot = 2;
        sim.update(FRAME, input);
        input.weaponSlot = 0;
        assertEquals(WeaponSlot.PISTOL, sim.getTargetWeapon());

        // Step east onto W (Scattergun pickup -> +8 shells & auto-equip after swap transition).
        input.moveForward = 1f;
        for (int i = 0; i < 20; i++) sim.update(FRAME, input);
        input.moveForward = 0f;
        assertTrue(sim.hasScattergun());
        assertEquals(TestTuning.WEAPONS.scattergunWeaponPickupShells, sim.getShells());
        assertEquals(WeaponSlot.SCATTERGUN, sim.getActiveWeapon());
        assertTrue(sim.pickups.get(0).isCollected());
        assertEquals(1, countSound(sim.drainSoundEvents(), SoundEvent.Type.PICKUP_WEAPON));

        // Walk across five shell boxes (+6 each = 8 + 30 capped at 24). Last pickup stays uncollected once full.
        input.moveForward = 1f;
        for (int i = 0; i < 90; i++) sim.update(FRAME, input);
        assertEquals(TestTuning.WEAPONS.scattergunMaxShells, sim.getShells());
        assertFalse(sim.pickups.get(5).isCollected(), "shell box stays on floor at full 24/24 shells");
        assertTrue(countSound(sim.drainSoundEvents(), SoundEvent.Type.PICKUP_SHELLS) >= 2);
    }

    @Test
    void weaponSwapCancelsBlastChargeAtZeroCostAndReversesSmoothlyMidSwap() {
        GameSimulation sim = new GameSimulation(
            GameMap.parse("#####", "#>.o#", "#####"),
            new PlayerLoadout(100f, 0f, Difficulty.NORMAL, true, 6, WeaponSlot.PISTOL),
            false);
        PlayerInput input = new PlayerInput();
        input.charge = true;
        for (int i = 0; i < 20; i++) sim.update(FRAME, input);
        assertTrue(sim.charger.isCharging());
        float energyBeforeSwap = sim.energy.getEnergy();

        // Request swap to Scattergun via Q (-1): cancels BlastCharger at zero energy cost.
        input.charge = false;
        input.weaponSlot = -1;
        sim.update(FRAME, input);
        input.weaponSlot = 0;
        assertFalse(sim.charger.isCharging());
        assertTrue(sim.energy.getEnergy() >= energyBeforeSwap - 1e-4f);
        assertEquals(WeaponSlot.PISTOL, sim.getActiveWeapon(), "still dipping before midpoint");
        assertEquals(WeaponSlot.SCATTERGUN, sim.getTargetWeapon());
        assertEquals(1, countSound(sim.drainSoundEvents(), SoundEvent.Type.WEAPON_SWITCH));

        // Advance past midpoint (0.09s) -> active weapon switches while dipped, then reverse via 1!
        for (int i = 0; i < 7; i++) sim.update(FRAME, input);
        assertEquals(WeaponSlot.SCATTERGUN, sim.getActiveWeapon());
        input.weaponSlot = 1;
        sim.update(FRAME, input);
        input.weaponSlot = 0;
        assertEquals(WeaponSlot.PISTOL, sim.getTargetWeapon());
        assertTrue(sim.getSwapProgress() > 0f);
        for (int i = 0; i < 12; i++) sim.update(FRAME, input);
        assertEquals(WeaponSlot.PISTOL, sim.getActiveWeapon());
        assertEquals(0f, sim.getSwapProgress(), 1e-5f);
    }

    @Test
    void scattergunPrimaryAndDoubleBlastKillEnemiesAndEmptyTriggerAutoSwapsToPistol() {
        GameSimulation sim = new GameSimulation(
            GameMap.parse(
                "###########",
                "#.>..D....#",
                "###########"),
            new PlayerLoadout(100f, 0f, Difficulty.NORMAL, true, 3, WeaponSlot.SCATTERGUN),
            false);
        PlayerInput input = new PlayerInput();
        Enemy drone = sim.enemies.get(0);

        // Primary shot at close-range drone (40 HP): 8 pellets * 12 = 96 -> destroys it using 1 shell (3 -> 2).
        input.fire = true;
        sim.update(FRAME, input);
        input.fire = false;
        assertTrue(drone.isDestroyed());
        assertEquals(2, sim.getShells());
        assertTrue(sim.scattergun.isFlashVisible());
        assertEquals(1, countSound(sim.drainSoundEvents(), SoundEvent.Type.SCATTERGUN_FIRE));

        // Wait pump cooldown then trigger Double-Blast with 2 shells -> drops to 0.
        for (int i = 0; i < 50; i++) sim.update(FRAME, input);
        assertTrue(sim.scattergun.isReady());
        assertEquals(0f, sim.scattergun.getPump(), 1e-5f);
        input.charge = true;
        sim.update(FRAME, input);
        input.charge = false;
        assertEquals(0, sim.getShells());
        assertTrue(sim.scattergun.isLastShotDoubleBlast());
        assertEquals(1, countSound(sim.drainSoundEvents(), SoundEvent.Type.SCATTERGUN_ALT_FIRE));

        // Wait alt recovery then pull trigger at 0 shells -> dry-fire click, toast, and auto-swap to Pistol.
        for (int i = 0; i < 75; i++) sim.update(FRAME, input);
        assertTrue(sim.scattergun.isReady());
        input.fire = true;
        sim.update(FRAME, input);
        input.fire = false;
        assertEquals("OUT OF SHELLS", sim.getToastMessage());
        assertEquals(WeaponSlot.PISTOL, sim.getTargetWeapon());
        assertEquals(1, countSound(sim.drainSoundEvents(), SoundEvent.Type.WEAPON_DRY_FIRE));
        for (int i = 0; i < 15; i++) sim.update(FRAME, input);
        assertEquals(WeaponSlot.PISTOL, sim.getActiveWeapon());
        assertFalse(sim.captureCarryOverLoadout().shells > 0);
        assertTrue(sim.captureCarryOverLoadout().hasScattergun);
    }

    @Test
    void scattergunRightClickWithOneShellFallsBackCleanlyToSingleShellBlast() {
        GameSimulation sim = new GameSimulation(
            GameMap.parse("#########", "#>.o....#", "#########"),
            new PlayerLoadout(100f, 0f, Difficulty.NORMAL, true, 1, WeaponSlot.SCATTERGUN),
            false);
        PlayerInput input = new PlayerInput();
        input.charge = true;
        sim.update(FRAME, input);
        input.charge = false;
        assertEquals(0, sim.getShells());
        assertFalse(sim.scattergun.isLastShotDoubleBlast());
        assertEquals(1, countSound(sim.drainSoundEvents(), SoundEvent.Type.SCATTERGUN_FIRE));
    }

    @Test
    void shockGratesDamageWalkingUnitsWhilePlayerDashGrantsZeroDamageImmunity() {
        // Walking onto ~ deals HAZARD_DAMAGE + HAZARD_ZAP; Dashing across ~ clears the grate with 0 damage.
        GameMap dashCourse = GameMap.parse(
            "########",
            "#>~....#",
            "########");
        GameSimulation dashSim = new GameSimulation(dashCourse);
        PlayerInput input = new PlayerInput();
        input.dash = true;
        input.moveForward = 1f;
        dashSim.update(FRAME, input);
        input.dash = false;
        for (int i = 0; i < 12; i++) {
            dashSim.update(FRAME, input);
        }
        input.moveForward = 0f;
        assertTrue(dashSim.player.position.x > 3.1f, "expected dash to clear ~ grate, at " + dashSim.player.position.x);
        assertEquals(Player.MAX_HEALTH, dashSim.player.getHealth(), 1e-4f);
        assertEquals(0f, dashSim.getDamageTaken(), 1e-4f);
        assertEquals(0, countSound(dashSim.drainSoundEvents(), SoundEvent.Type.HAZARD_ZAP));

        // Walking slowly onto ~ deals hazard zap damage.
        GameMap walkMap = GameMap.parse(
            "########",
            "#>.~...#",
            "#......#",
            "########");
        GameSimulation walkSim = new GameSimulation(walkMap);
        input.moveForward = 1f;
        for (int i = 0; i < 35; i++) walkSim.update(FRAME, input);
        assertTrue(walkSim.player.getHealth() < Player.MAX_HEALTH, "walking onto ~ must deal shock damage");
        assertTrue(countSound(walkSim.drainSoundEvents(), SoundEvent.Type.HAZARD_ZAP) >= 1);

        // Choke-point ~ forces Guard, Drone, and Warden to cross it: only Guard takes shock grate damage.
        PlayerInput idle = new PlayerInput();
        GameSimulation guardSim = new GameSimulation(GameMap.parse(
            "######",
            "#>...#",
            "####~#",
            "#G...#",
            "######"));
        GameSimulation droneSim = new GameSimulation(GameMap.parse(
            "######",
            "#>...#",
            "####~#",
            "#D...#",
            "######"));
        GameSimulation wardenSim = new GameSimulation(GameMap.parse(
            "######",
            "#>...#",
            "####~#",
            "#B...#",
            "######"));
        guardSim.enemies.get(0).alert();
        droneSim.enemies.get(0).alert();
        wardenSim.enemies.get(0).alert();
        for (int i = 0; i < 180; i++) {
            guardSim.update(FRAME, idle);
            droneSim.update(FRAME, idle);
            wardenSim.update(FRAME, idle);
        }
        assertTrue(guardSim.enemies.get(0).getHealth() < TestTuning.ENEMIES.of(Enemy.Kind.GUARD).maxHealth,
            "Guard crossing forced ~ choke must take hazard zap damage");
        assertEquals(TestTuning.ENEMIES.of(Enemy.Kind.DRONE).maxHealth, droneSim.enemies.get(0).getHealth(), 1e-4f);
        assertEquals(TestTuning.ENEMIES.of(Enemy.Kind.WARDEN).maxHealth, wardenSim.enemies.get(0).getHealth(), 1e-4f);
    }

    @Test
    void wardenOverdriveAndDestructionUnsealsBossExtractionBulkhead() {
        GameSimulation sim = new GameSimulation(GameMap.parse(
            "#########",
            "#.>...B.#",
            "####M####",
            "#...X...#",
            "#########"));
        PlayerInput input = new PlayerInput();
        Door bossDoor = sim.doors.get(0);
        Enemy warden = sim.enemies.get(0);
        assertEquals(Door.LockKind.BOSS, bossDoor.getLockKind());
        assertFalse(bossDoor.isOpening());

        // Move close to sealed M bulkhead and verify prompt + locked toast.
        sim.player.position.set(4.5f, 1.3f);
        sim.player.look(90f, 0f); // face south toward M at (4.5, 2.5)
        assertEquals("SEALED BY THE WARDEN", sim.getInteractionPrompt());
        input.interact = true;
        sim.update(FRAME, input);
        input.interact = false;
        assertEquals("SEALED BY THE WARDEN", sim.getToastMessage());
        assertFalse(bossDoor.isOpening());

        // Turn back toward Warden and drop HP below 50%: wakes HUD boss bar & triggers Overdrive event.
        sim.player.look(-90f, 0f);
        sim.drainSoundEvents();
        warden.damage(470f);
        sim.update(FRAME, input);
        assertNotNull(sim.getActiveBoss());
        assertTrue(sim.getActiveBoss().isOverdrive());
        assertEquals("WARDEN OVERDRIVE", sim.getToastMessage());
        assertEquals(1, countSound(sim.drainSoundEvents(), SoundEvent.Type.BOSS_OVERDRIVE));

        // Destroying Warden emits BOSS_DESTROYED, clears active boss HUD, and opens all M extraction bulkheads.
        warden.damage(500f);
        sim.update(FRAME, input);
        assertTrue(warden.isDestroyed());
        assertNull(sim.getActiveBoss());
        assertTrue(bossDoor.isOpening());
        assertEquals("WARDEN DESTROYED - EXTRACTION UNSEALED", sim.getToastMessage());
        List<SoundEvent> events = sim.drainSoundEvents();
        assertEquals(1, countSound(events, SoundEvent.Type.BOSS_DESTROYED));
        assertEquals(1, countSound(events, SoundEvent.Type.DOOR_OPEN));
    }

    @Test
    void railLancePickupsThreeWeaponSwitchingWithDirectKeysScrollAndLastUsedQ() {
        GameSimulation sim = new GameSimulation(GameMap.parse(
            "############",
            "#>WRrrrrrrr#",
            "############"));
        PlayerInput input = new PlayerInput();
        assertFalse(sim.hasRailLance());
        assertEquals(0, sim.getSlugs());

        // Walk east over W (+Scattergun -> slot 2) then R (+Rail Lance -> slot 3) and r packs up to 15/15 cap.
        input.moveForward = 1f;
        for (int i = 0; i < 160; i++) sim.update(FRAME, input);
        input.moveForward = 0f;
        assertTrue(sim.hasScattergun());
        assertTrue(sim.hasRailLance());
        assertEquals(TestTuning.WEAPONS.railMaxSlugs, sim.getSlugs());
        assertEquals(WeaponSlot.RAIL_LANCE, sim.getTargetWeapon());
        for (int i = 0; i < 12; i++) sim.update(FRAME, input);
        assertEquals(WeaponSlot.RAIL_LANCE, sim.getActiveWeapon());
        assertFalse(sim.pickups.get(6).isCollected(), "slug pack stays on floor when at 15/15 cap");
        assertTrue(countSound(sim.drainSoundEvents(), SoundEvent.Type.PICKUP_SLUGS) >= 3);

        // Press Q -> swaps to previous weapon (Scattergun); press 1 -> Pistol; press Q -> back to Scattergun!
        input.weaponSlot = PlayerInput.WEAPON_QUICK_SWAP;
        sim.update(FRAME, input);
        input.weaponSlot = 0;
        for (int i = 0; i < 12; i++) sim.update(FRAME, input);
        assertEquals(WeaponSlot.SCATTERGUN, sim.getActiveWeapon());

        input.weaponSlot = 1;
        sim.update(FRAME, input);
        input.weaponSlot = 0;
        for (int i = 0; i < 12; i++) sim.update(FRAME, input);
        assertEquals(WeaponSlot.PISTOL, sim.getActiveWeapon());

        input.weaponSlot = PlayerInput.WEAPON_QUICK_SWAP;
        sim.update(FRAME, input);
        input.weaponSlot = 0;
        for (int i = 0; i < 12; i++) sim.update(FRAME, input);
        assertEquals(WeaponSlot.SCATTERGUN, sim.getActiveWeapon());

        // Directional scroll cycling: NEXT (2 -> 3 -> 1) and PREV (1 -> 3 -> 2).
        input.weaponSlot = PlayerInput.WEAPON_CYCLE_NEXT;
        sim.update(FRAME, input);
        input.weaponSlot = 0;
        assertEquals(WeaponSlot.RAIL_LANCE, sim.getTargetWeapon());
        for (int i = 0; i < 12; i++) sim.update(FRAME, input);
        assertEquals(WeaponSlot.RAIL_LANCE, sim.getActiveWeapon());

        input.weaponSlot = PlayerInput.WEAPON_CYCLE_NEXT;
        sim.update(FRAME, input);
        input.weaponSlot = 0;
        assertEquals(WeaponSlot.PISTOL, sim.getTargetWeapon());
        for (int i = 0; i < 12; i++) sim.update(FRAME, input);
        assertEquals(WeaponSlot.PISTOL, sim.getActiveWeapon());

        input.weaponSlot = PlayerInput.WEAPON_CYCLE_PREV;
        sim.update(FRAME, input);
        input.weaponSlot = 0;
        assertEquals(WeaponSlot.RAIL_LANCE, sim.getTargetWeapon());
        for (int i = 0; i < 12; i++) sim.update(FRAME, input);
        assertEquals(WeaponSlot.RAIL_LANCE, sim.getActiveWeapon());

        // Direct key 2 then 3.
        input.weaponSlot = 2;
        sim.update(FRAME, input);
        input.weaponSlot = 0;
        for (int i = 0; i < 12; i++) sim.update(FRAME, input);
        assertEquals(WeaponSlot.SCATTERGUN, sim.getActiveWeapon());

        input.weaponSlot = 3;
        sim.update(FRAME, input);
        input.weaponSlot = 0;
        for (int i = 0; i < 12; i++) sim.update(FRAME, input);
        assertEquals(WeaponSlot.RAIL_LANCE, sim.getActiveWeapon());
    }

    @Test
    void railLancePiercesTargetsSpawnsBeamAndDoesLessToTheWarden() {
        GameSimulation sim = new GameSimulation(
            GameMap.parse(
                "#############",
                "#.>.D.o.G...#",
                "#############"),
            new PlayerLoadout(100f, 0f, Difficulty.NORMAL, true, 6, true, 4, WeaponSlot.RAIL_LANCE),
            false);
        PlayerInput input = new PlayerInput();
        input.fire = true;
        sim.update(FRAME, input);
        input.fire = false;

        assertTrue(sim.enemies.get(0).isDestroyed(), "Drone pierced and destroyed");
        assertTrue(sim.targets.get(0).isDestroyed(), "Canister pierced and detonated");
        assertTrue(sim.enemies.get(1).isDestroyed(), "Guard behind canister pierced and destroyed");
        assertEquals(3, sim.getSlugs());
        assertEquals(1, sim.railBeams.size());
        assertEquals(1, countSound(sim.drainSoundEvents(), SoundEvent.Type.RAIL_FIRE));

        for (int i = 0; i < 24; i++) sim.update(FRAME, input);
        assertTrue(sim.railBeams.isEmpty(), "Rail beam trail decays and removes after DURATION");

        // The Warden's hide reduces 120 base Rail Lance damage to 90 (75%).
        EnemyTuning.KindStats wardenStats = TestTuning.ENEMIES.of(Enemy.Kind.WARDEN);
        assertEquals(90f, TestTuning.WEAPONS.railDamage * wardenStats.railDamageScale, 0f);
        GameSimulation bossSim = new GameSimulation(
            GameMap.parse(
                "###########",
                "#.>...B...#",
                "###########"),
            new PlayerLoadout(100f, 0f, Difficulty.NORMAL, false, 0, true, 2, WeaponSlot.RAIL_LANCE),
            false);
        bossSim.update(FRAME, new PlayerInput());
        PlayerInput fire = new PlayerInput();
        fire.fire = true;
        bossSim.update(FRAME, fire);
        assertEquals(wardenStats.maxHealth - TestTuning.WEAPONS.railDamage * wardenStats.railDamageScale,
            bossSim.enemies.get(0).getHealth(), 1e-3f);
    }

    @Test
    void railLanceZoomScalesLookSensitivityDropsOnDashAndEmptyTriggerSmartSwaps() {
        GameSimulation sim = new GameSimulation(
            GameMap.parse("#########", "#.>...o.#", "#########"),
            new PlayerLoadout(100f, 0f, Difficulty.NORMAL, true, 4, true, 1, WeaponSlot.SCATTERGUN),
            false);
        PlayerInput input = new PlayerInput();

        // Switch from Scattergun -> Rail Lance so previousWeapon is SCATTERGUN.
        input.weaponSlot = 3;
        sim.update(FRAME, input);
        input.weaponSlot = 0;
        for (int i = 0; i < 12; i++) sim.update(FRAME, input);
        assertEquals(WeaponSlot.RAIL_LANCE, sim.getActiveWeapon());

        // Hold RMB to reach full 1.8x zoom, verify zoom audio and look sensitivity scaling.
        input.charge = true;
        for (int i = 0; i < 12; i++) sim.update(FRAME, input);
        assertEquals(1f, sim.getZoom(), 1e-4f);
        assertEquals(1, countSound(sim.drainSoundEvents(), SoundEvent.Type.RAIL_ZOOM_IN));

        float yawBefore = sim.player.getYaw();
        input.yawDelta = 18f;
        sim.update(FRAME, input);
        input.yawDelta = 0f;
        float delta = (sim.player.getYaw() - yawBefore + 540f) % 360f - 180f;
        assertEquals(18f / TestTuning.WEAPONS.railZoomFactor, delta, 1e-3f);

        // Energy Dash immediately cancels optic zoom and suppresses re-zoom while RMB remains held.
        input.dash = true;
        sim.update(FRAME, input);
        input.dash = false;
        assertEquals(0f, sim.getZoom(), 1e-5f);
        for (int i = 0; i < 6; i++) sim.update(FRAME, input);
        assertEquals(0f, sim.getZoom(), 1e-5f);
        input.charge = false;
        sim.update(FRAME, input);

        // Spend final slug, wait recharge, then pull LMB at 0 slugs -> smart-swaps back to loaded Scattergun.
        input.fire = true;
        sim.update(FRAME, input);
        input.fire = false;
        assertEquals(0, sim.getSlugs());
        for (int i = 0; i < 68; i++) sim.update(FRAME, input);
        assertTrue(sim.railLance.isReady());

        input.fire = true;
        sim.update(FRAME, input);
        input.fire = false;
        assertEquals("OUT OF SLUGS", sim.getToastMessage());
        assertEquals(WeaponSlot.SCATTERGUN, sim.getTargetWeapon());
        assertEquals(1, countSound(sim.drainSoundEvents(), SoundEvent.Type.WEAPON_DRY_FIRE));
        for (int i = 0; i < 12; i++) sim.update(FRAME, input);
        assertEquals(WeaponSlot.SCATTERGUN, sim.getActiveWeapon());
    }

    @Test
    void jumpClearsFloorShockGrateWithoutSpendingEnergyAndDoesNotAutoHopWhenHeld() {
        GameSimulation sim = new GameSimulation(GameMap.parse(
            "###########",
            "#.>.~.....#",
            "###########"));
        sim.player.position.set(3.85f, 1.5f); // just west of the ~ shock grate at x = [4, 5]

        PlayerInput input = new PlayerInput();
        input.moveForward = 1f;
        input.jump = true;
        sim.update(FRAME, input);

        assertEquals(100f, sim.energy.getEnergy(), 1e-4f, "jump spends zero energy");
        assertEquals(1, countSound(sim.drainSoundEvents(), SoundEvent.Type.PLAYER_JUMP));

        int landSounds = 0;
        for (int i = 0; i < 35; i++) {
            sim.update(FRAME, input); // keep holding jump across touchdown
            landSounds += countSound(sim.drainSoundEvents(), SoundEvent.Type.PLAYER_LAND);
        }

        assertTrue(sim.player.position.x > 5.2f, "cleared across the 1u shock grate tile");
        assertEquals(Player.MAX_HEALTH, sim.player.getHealth(), 1e-4f, "took zero shock grate damage while airborne");
        assertEquals(1, landSounds, "emitted landing thud on touchdown");
        assertFalse(sim.player.isAirborne(), "holding Space across landing does not auto-hop");

        // Releasing and pressing Space again triggers a fresh jump.
        input.jump = false;
        sim.update(FRAME, input);
        input.jump = true;
        sim.update(FRAME, input);
        assertTrue(sim.player.isAirborne());
    }

    @Test
    void jumpPreservesBlastChargeAndRailLanceZoomUnlikeDash() {
        GameSimulation sim = new GameSimulation(
            GameMap.parse("#########", "#.>...o.#", "#########"),
            new PlayerLoadout(100f, 0f, Difficulty.NORMAL, false, 0, true, 4, WeaponSlot.PISTOL),
            false);
        PlayerInput input = new PlayerInput();
        input.charge = true;
        for (int i = 0; i < 12; i++) sim.update(FRAME, input);
        assertTrue(sim.charger.isCharging());

        input.jump = true;
        sim.update(FRAME, input);
        input.jump = false;
        assertTrue(sim.player.isAirborne());
        assertTrue(sim.charger.isCharging(), "Pistol Blast charge continues uninterrupted mid-jump");

        // Switch to Rail Lance, engage full 1.8x zoom, and jump -> zoom stays locked at 1.0.
        input.charge = false;
        for (int i = 0; i < 35; i++) sim.update(FRAME, input);
        input.weaponSlot = 3;
        sim.update(FRAME, input);
        input.weaponSlot = 0;
        for (int i = 0; i < 12; i++) sim.update(FRAME, input);

        input.charge = true;
        for (int i = 0; i < 12; i++) sim.update(FRAME, input);
        assertEquals(1f, sim.getZoom(), 1e-4f);

        input.jump = true;
        sim.update(FRAME, input);
        input.jump = false;
        assertTrue(sim.player.isAirborne());
        assertEquals(1f, sim.getZoom(), 1e-4f, "Rail Lance 1.8x zoom remains active mid-jump");
    }

    private static GameSimulation emptyCorridorHolding(WeaponSlot weapon) {
        return new GameSimulation(
            GameMap.parse("##########", "#>.......#", "##########"),
            new PlayerLoadout(100f, 0f, Difficulty.NORMAL, true, 8, true, 5, weapon),
            false);
    }

    @Test
    void energyRefillsWithTheScattergunOut() {
        assertEquals(24f, energyAfterOneIdleSecondFromTen(WeaponSlot.SCATTERGUN), 1e-3f);
    }

    @Test
    void energyRefillsWithTheRailLanceOut() {
        assertEquals(24f, energyAfterOneIdleSecondFromTen(WeaponSlot.RAIL_LANCE), 1e-3f);
    }

    private static float energyAfterOneIdleSecondFromTen(WeaponSlot weapon) {
        GameSimulation sim = emptyCorridorHolding(weapon);
        sim.energy.setEnergy(10f);
        PlayerInput input = new PlayerInput();
        for (int i = 0; i < 60; i++) sim.update(FRAME, input);
        return sim.energy.getEnergy();
    }

    @Test
    void energyRefillsWhileSwappingWeapons() {
        GameSimulation sim = emptyCorridorHolding(WeaponSlot.PISTOL);
        sim.energy.setEnergy(10f);
        PlayerInput input = new PlayerInput();
        input.weaponSlot = 2;
        sim.update(FRAME, input);
        input.weaponSlot = 0;
        for (int i = 0; i < 5; i++) sim.update(FRAME, input);
        assertTrue(sim.getSwapProgress() > 0f, "still swapping");
        assertEquals(10f + 14f * 6f / 60f, sim.energy.getEnergy(), 1e-3f);
    }

    @Test
    void pistolIdleEnergyRefillIsNotDoubledAndPausesWhileAChargeBuilds() {
        GameSimulation sim = emptyCorridorHolding(WeaponSlot.PISTOL);
        sim.energy.setEnergy(30f);
        PlayerInput input = new PlayerInput();
        for (int i = 0; i < 60; i++) sim.update(FRAME, input);
        assertEquals(44f, sim.energy.getEnergy(), 1e-3f, "one second of refill holding the pistol");

        input.charge = true;
        for (int i = 0; i < 30; i++) sim.update(FRAME, input);
        assertTrue(sim.charger.isCharging());
        assertEquals(44f, sim.energy.getEnergy(), 1e-3f, "no refill while the charge builds");
    }

    /**
     * Holds fire and asks for the Rail Lance, optionally one frame after asking for the Scattergun.
     * @return the frames from the Rail Lance's key press up to and including the one that fires it
     */
    private static int framesUntilTheRailLanceFires(boolean scattergunFirst) {
        GameSimulation sim = emptyCorridorHolding(WeaponSlot.PISTOL);
        PlayerInput input = new PlayerInput();
        if (scattergunFirst) {
            input.weaponSlot = 2;
            sim.update(FRAME, input);
        }
        input.fire = true;
        input.weaponSlot = 3;
        for (int frames = 1; frames < 100; frames++) {
            sim.update(FRAME, input);
            input.weaponSlot = 0;
            if (sim.getSlugs() < 5) return frames;
            if (frames < 5) {
                assertEquals(WeaponSlot.PISTOL, sim.getActiveWeapon(), "still dipping the pistol on frame " + frames);
            }
        }
        throw new AssertionError("the Rail Lance never fired");
    }

    @Test
    void askingForAThirdWeaponMidSwapRestartsTheDipInsteadOfSkippingIt() {
        assertEquals(framesUntilTheRailLanceFires(false), framesUntilTheRailLanceFires(true));
    }

    @Test
    void swapKeepsTrackOfTheWeaponItLeftAcrossAReversal() {
        // Pistol to Scattergun, back to the pistol four frames in, then on to the Rail Lance: a third weapon, so a
        // full dip of the pistol, as long as a direct switch.
        GameSimulation sim = emptyCorridorHolding(WeaponSlot.PISTOL);
        PlayerInput input = new PlayerInput();
        input.weaponSlot = 2;
        sim.update(FRAME, input);
        input.weaponSlot = 0;
        for (int i = 0; i < 3; i++) sim.update(FRAME, input);
        input.weaponSlot = 1;
        sim.update(FRAME, input);
        assertEquals(WeaponSlot.PISTOL, sim.getActiveWeapon());
        assertTrue(sim.getSwapProgress() > 0f, "still raising the pistol");
        input.fire = true;
        input.weaponSlot = 3;
        int frames = 0;
        while (sim.getSlugs() == 5 && frames < 100) {
            sim.update(FRAME, input);
            input.weaponSlot = 0;
            frames++;
            if (frames < 5) assertEquals(WeaponSlot.PISTOL, sim.getActiveWeapon(), "dipping on frame " + frames);
        }
        assertEquals(framesUntilTheRailLanceFires(false), frames);

        // Pistol to Scattergun, back to the pistol, then the Scattergun again: a second reversal, which carries on
        // from the depth the dip has reached instead of starting over.
        sim = emptyCorridorHolding(WeaponSlot.PISTOL);
        input = new PlayerInput();
        input.weaponSlot = 2;
        sim.update(FRAME, input);
        input.weaponSlot = 0;
        for (int i = 0; i < 3; i++) sim.update(FRAME, input);
        input.weaponSlot = 1;
        sim.update(FRAME, input);
        float progressBefore = sim.getSwapProgress();
        input.weaponSlot = 2;
        sim.update(FRAME, input);
        input.weaponSlot = 0;
        assertEquals(WeaponSlot.SCATTERGUN, sim.getTargetWeapon());
        assertTrue(sim.getSwapProgress() > progressBefore, "dips on from " + progressBefore);
        assertEquals(WeaponSlot.PISTOL, sim.getActiveWeapon());
        sim.update(FRAME, input);
        sim.update(FRAME, input);
        assertEquals(WeaponSlot.SCATTERGUN, sim.getActiveWeapon(), "reached the bottom of the dip");
    }

    @Test
    void reachingTheExitOnTheFrameThePlayerDiesDoesNotClearTheStage() {
        GameSimulation sim = new GameSimulation(
            GameMap.parse("######", "#>.~X#", "######"),
            new PlayerLoadout(5f, 0f, Difficulty.NORMAL),
            false);
        // On the grate and within reach of the exit pad: the first grate tick is lethal.
        sim.player.position.set(3.95f, 1.5f);
        PlayerInput input = new PlayerInput();
        sim.update(FRAME, input);
        assertTrue(sim.player.isDead());
        assertFalse(sim.isLevelCleared(), "a dead player does not clear the stage");
        for (int i = 0; i < 30; i++) sim.update(FRAME, input);
        assertFalse(sim.isLevelCleared());
    }

    @Test
    void exitReachedMidJumpSettlesThePlayerOnTheFloor() {
        GameSimulation sim = new GameSimulation(GameMap.parse("########", "#>....X#", "########"));
        sim.player.position.set(5.2f, 1.5f);
        PlayerInput input = new PlayerInput();
        input.moveForward = 1f;
        input.jump = true;
        float heightBefore = 0f;
        for (int i = 0; i < 20 && !sim.isLevelCleared(); i++) {
            heightBefore = sim.player.getJumpHeight();
            sim.update(FRAME, input);
        }
        assertTrue(sim.isLevelCleared());
        assertTrue(heightBefore > 0.1f, "was mid-jump the frame before, at " + heightBefore);
        assertEquals(0f, sim.player.getJumpHeight(), 0f);
        assertEquals(0f, sim.player.getVerticalVelocity(), 0f);
        assertFalse(sim.player.isAirborne());
        assertEquals(Player.EYE_HEIGHT, sim.player.eye(new Vector3()).y, 1e-5f);
    }

    @Test
    void lastTargetBrokenMidJumpSettlesThePlayerOnTheFloor() {
        GameSimulation sim = new GameSimulation(GameMap.parse("##########", "#>......o#", "##########"),
            PlayerLoadout.fresh(), true);
        PlayerInput input = new PlayerInput();
        input.jump = true;
        for (int i = 0; i < 8; i++) sim.update(FRAME, input);
        assertTrue(sim.player.getJumpHeight() > 0.1f);
        sim.targets.get(0).damage(9999f);
        sim.update(FRAME, input);
        assertTrue(sim.isLevelCleared());
        assertEquals(0f, sim.player.getJumpHeight(), 0f);
        assertFalse(sim.player.isAirborne());
    }

    @Test
    void plainJumpClearsTheTrainingRangeShockGrate() {
        Levels.Stage range = Levels.trainingRange();
        float west = Float.MAX_VALUE;
        for (GameMap.HazardCell cell : range.map.getHazardCells()) west = Math.min(west, cell.col);
        GameSimulation sim = new GameSimulation(range.map, PlayerLoadout.fresh(), false);
        sim.player.position.set(west - 0.7f, range.map.getHazardCells().get(0).row + 0.5f);
        sim.player.look(90f - sim.player.getYaw(), 0f);

        PlayerInput input = new PlayerInput();
        input.moveForward = 1f;
        boolean crossedTheGrate = false;
        for (int i = 0; i < 90; i++) {
            // One press, a quarter of a cell short of the grate; no dash.
            input.jump = sim.player.position.x >= west - 0.25f;
            sim.update(FRAME, input);
            crossedTheGrate |= range.map.isHazardAt(sim.player.position.x, sim.player.position.y);
        }
        assertTrue(crossedTheGrate);
        assertFalse(range.map.isHazardAt(sim.player.position.x, sim.player.position.y));
        assertTrue(sim.player.position.x > west + 1f, "walked on east to " + sim.player.position.x);
        assertEquals(0f, sim.getDamageTaken(), 0f);
        assertEquals(100f, sim.energy.getEnergy(), 0f, "no dash was needed");
    }

    /**
     * Walks east over a two-cell grate, as the campaign stages have, jumping a quarter of a cell short of it.
     * @return the damage taken on the way
     */
    private static float damageCrossingATwoCellGrate(boolean dashAtTheTop) {
        GameSimulation sim = new GameSimulation(GameMap.parse(
            "############",
            "#.>.~~.....#",
            "############"));
        sim.player.position.set(3.3f, 1.5f);
        PlayerInput input = new PlayerInput();
        input.moveForward = 1f;
        boolean jumped = false;
        for (int i = 0; i < 90; i++) {
            input.jump = sim.player.position.x >= 3.75f;
            jumped |= input.jump;
            input.dash = dashAtTheTop && jumped && sim.player.getVerticalVelocity() <= 0f;
            sim.update(FRAME, input);
        }
        assertTrue(sim.player.position.x > 6f, "walked on east to " + sim.player.position.x);
        return sim.getDamageTaken();
    }

    @Test
    void twoCellShockGrateNeedsAJumpWithAMidAirDash() {
        assertTrue(damageCrossingATwoCellGrate(false) > 0f, "a plain jump comes down on the grate");
        assertEquals(0f, damageCrossingATwoCellGrate(true), 0f);
    }

    @Test
    void idlePlayerAtSpawnIsLeftAloneOnEveryCampaignStage() {
        List<String> problems = new ArrayList<>();
        for (Levels.Stage stage : Levels.campaign()) {
            GameSimulation sim = new GameSimulation(stage.map, PlayerLoadout.fresh(Difficulty.NORMAL),
                stage.completeOnAllTargets);
            PlayerInput input = new PlayerInput();
            sim.update(FRAME, input);
            for (Enemy enemy : sim.enemies) {
                if (enemy.getState() == Enemy.State.DORMANT) continue;
                problems.add(stage.id + ": the " + enemy.getKind() + " at " + enemy.getX() + "," + enemy.getZ()
                    + " sees the spawn point");
            }
            for (int i = 0; i < 600; i++) sim.update(FRAME, input);
            if (sim.getDamageTaken() > 0f) {
                problems.add(stage.id + ": " + sim.getDamageTaken() + " damage in 10 s of standing at the spawn point");
            }
        }
        assertTrue(problems.isEmpty(), String.join("; ", problems));
    }

    @Test
    void steppingAfterLevelClearedStopsEnemiesAndEnemyBoltsFromHurtingPlayer() {
        GameSimulation sim = new GameSimulation(GameMap.parse(
            "##########",
            "#>...XD..#",
            "##########"));
        sim.enemies.get(0).alert();
        sim.player.position.set(5.3f, 1.5f);
        sim.enemyProjectiles.add(new EnemyProjectile(
            new Vector3(6.2f, 0.44f, 1.5f), new Vector3(-1f, 0f, 0f), 8f, 30f, sim.enemies.get(0)));

        PlayerInput idle = new PlayerInput();
        sim.update(FRAME, idle);
        assertTrue(sim.isLevelCleared());
        assertEquals(Player.MAX_HEALTH, sim.player.getHealth(), 1e-4f);

        for (int i = 0; i < 120; i++) {
            sim.update(FRAME, idle);
        }
        assertTrue(sim.isLevelCleared());
        assertFalse(sim.player.isDead(), "enemies and bolts must not kill player after stage clear");
        assertEquals(Player.MAX_HEALTH, sim.player.getHealth(), 1e-4f);
        assertEquals(0f, sim.getDamageTaken(), 1e-4f);
    }

    @Test
    void postMortemEnemyBoltsDoNotDamageTargetsOrMutateDestroyedTargetTally() {
        GameSimulation sim = new GameSimulation(GameMap.parse(
            "##########",
            "#>...o...#",
            "##########"));
        Target canister = sim.targets.get(0);
        canister.damage(40f);
        sim.enemyProjectiles.add(new EnemyProjectile(
            new Vector3(3.5f, 0.30f, 1.5f), new Vector3(1f, 0f, 0f), 8f, 25f, null));
        sim.player.damage(500f);
        assertTrue(sim.player.isDead());
        assertEquals(0, sim.getTargetsDestroyed());

        PlayerInput idle = new PlayerInput();
        for (int i = 0; i < 60; i++) {
            sim.update(FRAME, idle);
        }
        assertFalse(canister.isDestroyed(), "post-mortem bolts must not silently destroy targets");
        assertEquals(10f, canister.getHealth(), 1e-4f);
        assertEquals(0, sim.getTargetsDestroyed());
        assertTrue(sim.enemyProjectiles.isEmpty());
    }

    @Test
    void stageClearAndPlayerDeathImmediatelyCancelRailZoomAndBlastCharge() {
        // Stage clear via allDestroyed while at 1.8x Rail Lance zoom.
        GameSimulation clearZoomSim = new GameSimulation(
            GameMap.parse("#########", "#>....o.#", "#########"),
            new PlayerLoadout(100f, 0f, Difficulty.NORMAL, false, 0, true, 3, WeaponSlot.RAIL_LANCE),
            true);
        PlayerInput zoomInput = new PlayerInput();
        zoomInput.charge = true;
        for (int i = 0; i < 12; i++) clearZoomSim.update(FRAME, zoomInput);
        assertEquals(1f, clearZoomSim.getZoom(), 1e-4f);

        zoomInput.fire = true;
        clearZoomSim.update(FRAME, zoomInput);
        assertTrue(clearZoomSim.isLevelCleared());
        assertEquals(0f, clearZoomSim.getZoom(), 1e-6f, "zoom cancelled immediately on clear frame");
        assertEquals(0f, clearZoomSim.railLance.getZoom(), 1e-6f);

        // Stage clear via exit pad while holding Pistol Blast charge.
        GameSimulation clearChargeSim = new GameSimulation(GameMap.parse("#########", "#>....X.#", "#########"));
        PlayerInput chargeInput = new PlayerInput();
        chargeInput.charge = true;
        for (int i = 0; i < 20; i++) clearChargeSim.update(FRAME, chargeInput);
        assertTrue(clearChargeSim.getBlastChargeProgress() > 0f);

        clearChargeSim.player.position.set(6.2f, 1.5f);
        clearChargeSim.update(FRAME, chargeInput);
        assertTrue(clearChargeSim.isLevelCleared());
        assertEquals(0f, clearChargeSim.getBlastChargeProgress(), 1e-6f);
        assertFalse(clearChargeSim.isBlastChargingAudioActive());

        // Lethal damage mid-step while zoomed at 1.8x cancels zoom on death frame 1.
        GameSimulation deathZoomSim = new GameSimulation(
            GameMap.parse("########", "#>.~...#", "########"),
            new PlayerLoadout(5f, 0f, Difficulty.NORMAL, false, 0, true, 3, WeaponSlot.RAIL_LANCE),
            false);
        PlayerInput holdZoom = new PlayerInput();
        holdZoom.charge = true;
        for (int i = 0; i < 12; i++) deathZoomSim.update(FRAME, holdZoom);
        assertEquals(1f, deathZoomSim.getZoom(), 1e-4f);

        deathZoomSim.player.position.set(3.5f, 1.5f);
        deathZoomSim.update(FRAME, holdZoom);
        assertTrue(deathZoomSim.player.isDead());
        assertEquals(0f, deathZoomSim.getZoom(), 1e-6f, "zoom cancelled immediately on death frame");
        assertEquals(0f, deathZoomSim.railLance.getZoom(), 1e-6f);
    }

    @Test
    void reversingWeaponSwapBeforeMidpointAndAtExactMidpointBehaveCleanly() {
        // Reversing before SWAP_MIDPOINT preserves previousWeapon for Q quick-swap (#54).
        GameSimulation sim = emptyCorridorHolding(WeaponSlot.PISTOL);
        PlayerInput input = new PlayerInput();
        input.weaponSlot = 3;
        sim.update(FRAME, input);
        assertEquals(WeaponSlot.PISTOL, sim.getActiveWeapon());
        assertEquals(WeaponSlot.RAIL_LANCE, sim.getTargetWeapon());

        input.weaponSlot = 1;
        sim.update(FRAME, input);
        input.weaponSlot = 0;
        for (int i = 0; i < 10; i++) sim.update(FRAME, input);
        assertEquals(0f, sim.getSwapProgress(), 1e-6f);
        assertEquals(WeaponSlot.PISTOL, sim.getActiveWeapon());

        input.weaponSlot = PlayerInput.WEAPON_QUICK_SWAP;
        sim.update(FRAME, input);
        assertEquals(WeaponSlot.RAIL_LANCE, sim.getTargetWeapon(),
            "Q quick-swap remembers Rail Lance after reversing back to Pistol");

        // Reversing on exact SWAP_MIDPOINT updates activeWeapon immediately during raise (#55).
        GameSimulation midSim = emptyCorridorHolding(WeaponSlot.PISTOL);
        PlayerInput midInput = new PlayerInput();
        midInput.weaponSlot = 2;
        midSim.update(0.045f, midInput);
        midInput.weaponSlot = 0;
        midSim.update(0.045f, midInput);
        assertEquals(WeaponSlot.SCATTERGUN, midSim.getActiveWeapon());

        midInput.weaponSlot = 1;
        midSim.update(0.02f, midInput);
        assertTrue(midSim.getSwapProgress() > 0f);
        assertEquals(WeaponSlot.PISTOL, midSim.getActiveWeapon(),
            "reversing at exact midpoint immediately shows target weapon during raise");
    }

    @Test
    void initialPreviousWeaponAndDryFireSmartSwapPreferLoadedWeaponsForQuickSwap() {
        // initialPreviousWeapon skips 0-slug Rail Lance in favor of loaded Scattergun.
        GameSimulation initialSim = new GameSimulation(
            GameMap.parse("##########", "#>.......#", "##########"),
            new PlayerLoadout(100f, 0f, Difficulty.NORMAL, true, 8, true, 0, WeaponSlot.PISTOL),
            false);
        PlayerInput input = new PlayerInput();
        input.weaponSlot = PlayerInput.WEAPON_QUICK_SWAP;
        initialSim.update(FRAME, input);
        assertEquals(WeaponSlot.SCATTERGUN, initialSim.getTargetWeapon());

        // Dry-fire smart-swap from 0-shell Scattergun to Rail Lance sets previousWeapon to loaded Pistol.
        GameSimulation swapSim = new GameSimulation(
            GameMap.parse("##########", "#>.......#", "##########"),
            new PlayerLoadout(100f, 0f, Difficulty.NORMAL, true, 1, true, 5, WeaponSlot.RAIL_LANCE),
            false);
        input.weaponSlot = 2;
        swapSim.update(FRAME, input);
        input.weaponSlot = 0;
        for (int i = 0; i < 12; i++) swapSim.update(FRAME, input);
        assertEquals(WeaponSlot.SCATTERGUN, swapSim.getActiveWeapon());

        input.fire = true;
        swapSim.update(FRAME, input);
        input.fire = false;
        assertEquals(0, swapSim.getShells());
        for (int i = 0; i < 50; i++) swapSim.update(FRAME, input);

        input.fire = true;
        swapSim.update(FRAME, input);
        input.fire = false;
        assertEquals(WeaponSlot.RAIL_LANCE, swapSim.getTargetWeapon());
        for (int i = 0; i < 12; i++) swapSim.update(FRAME, input);
        assertEquals(WeaponSlot.RAIL_LANCE, swapSim.getActiveWeapon());

        input.weaponSlot = PlayerInput.WEAPON_QUICK_SWAP;
        swapSim.update(FRAME, input);
        assertEquals(WeaponSlot.PISTOL, swapSim.getTargetWeapon(),
            "Q quick-swap equips loaded Pistol instead of empty 0-shell Scattergun");
    }

    @Test
    void emptyScattergunAndRailLanceTriggerDryFireOnExactCooldownCompletionFrame() {
        GameSimulation scatterSim = new GameSimulation(
            GameMap.parse("##########", "#>.......#", "##########"),
            new PlayerLoadout(100f, 0f, Difficulty.NORMAL, true, 1, false, 0, WeaponSlot.SCATTERGUN),
            false);
        PlayerInput input = new PlayerInput();
        input.fire = true;
        scatterSim.update(FRAME, input);
        input.fire = false;
        for (int i = 0; i < 15; i++) scatterSim.update(0.049f, input);
        assertFalse(scatterSim.scattergun.isReady());

        scatterSim.drainSoundEvents();
        input.fire = true;
        scatterSim.update(FRAME, input);
        assertEquals("OUT OF SHELLS", scatterSim.getToastMessage());
        assertEquals(WeaponSlot.PISTOL, scatterSim.getTargetWeapon());
        assertEquals(1, countSound(scatterSim.drainSoundEvents(), SoundEvent.Type.WEAPON_DRY_FIRE));

        GameSimulation railSim = new GameSimulation(
            GameMap.parse("##########", "#>.......#", "##########"),
            new PlayerLoadout(100f, 0f, Difficulty.NORMAL, false, 0, true, 1, WeaponSlot.RAIL_LANCE),
            false);
        input.fire = true;
        railSim.update(FRAME, input);
        input.fire = false;
        for (int i = 0; i < 22; i++) railSim.update(0.0495f, input);
        assertFalse(railSim.railLance.isReady());

        railSim.drainSoundEvents();
        input.fire = true;
        railSim.update(FRAME, input);
        assertEquals("OUT OF SLUGS", railSim.getToastMessage());
        assertEquals(WeaponSlot.PISTOL, railSim.getTargetWeapon());
        assertEquals(1, countSound(railSim.drainSoundEvents(), SoundEvent.Type.WEAPON_DRY_FIRE));
    }

    @Test
    void weaponSwitchResetsViewmodelRecoilAndSlideAtSwapMidpoint() {
        GameSimulation sim = emptyCorridorHolding(WeaponSlot.RAIL_LANCE);
        PlayerInput input = new PlayerInput();
        input.fire = true;
        sim.update(FRAME, input);
        input.fire = false;
        sim.update(FRAME, input);
        assertTrue(sim.viewmodel.getRecoil() > 1.1f);
        assertTrue(sim.viewmodel.getSlide() > 0.8f);

        input.weaponSlot = 1;
        sim.update(FRAME, input);
        input.weaponSlot = 0;
        for (int i = 0; i < 5; i++) sim.update(FRAME, input);

        assertEquals(WeaponSlot.PISTOL, sim.getActiveWeapon());
        assertEquals(0f, sim.viewmodel.getSlide(), 1e-6f);
        assertEquals(0f, sim.viewmodel.getRecoil(), 1e-6f);
    }

    @Test
    void dashingAfterShockGrateHitDoesNotResetHazardCooldownEarly() {
        GameSimulation sim = new GameSimulation(GameMap.parse(
            "############",
            "#>~~~~.....#",
            "############"));
        sim.player.position.set(2.2f, 1.5f);
        PlayerInput input = new PlayerInput();

        sim.update(FRAME, input);
        assertEquals(Player.MAX_HEALTH - TestTuning.WORLD.grateDamage, sim.player.getHealth(), 1e-4f);

        sim.drainSoundEvents();
        input.dash = true;
        input.moveForward = 1f;
        sim.update(FRAME, input);
        input.dash = false;
        input.moveForward = 0f;
        for (int i = 0; i < 8; i++) sim.update(FRAME, input);
        assertFalse(sim.player.isDashing());
        assertTrue(sim.map.isHazardAt(sim.player.position.x, sim.player.position.y));

        // Still inside the 0.35s HAZARD_INTERVAL window (~0.17s elapsed): must not zap a second time yet.
        sim.update(FRAME, input);
        assertEquals(Player.MAX_HEALTH - TestTuning.WORLD.grateDamage, sim.player.getHealth(), 1e-4f);
        assertEquals(0, countSound(sim.drainSoundEvents(), SoundEvent.Type.HAZARD_ZAP));

        // Once the remaining cooldown elapses while still standing on ~, the next zap fires normally.
        for (int i = 0; i < 15; i++) sim.update(FRAME, input);
        assertEquals(Player.MAX_HEALTH - 2f * TestTuning.WORLD.grateDamage, sim.player.getHealth(), 1e-4f);
    }

    @Test
    void refusedDashPreservesBlastChargeAndRailZoomAndSyncsLowEnergyWarningDuringCooldown() {
        // Refused dash with 22 energy preserves active Pistol Blast charge (#126).
        GameSimulation blastSim = emptyCorridor();
        blastSim.energy.setEnergy(22f);
        PlayerInput blastInput = new PlayerInput();
        blastInput.charge = true;
        for (int i = 0; i < 18; i++) blastSim.update(FRAME, blastInput);
        assertTrue(blastSim.charger.isCharging());

        blastSim.drainSoundEvents();
        blastInput.dash = true;
        blastSim.update(FRAME, blastInput);
        blastInput.dash = false;
        assertFalse(blastSim.player.isDashing());
        assertTrue(blastSim.player.isDashRefused());
        assertEquals(1, countSound(blastSim.drainSoundEvents(), SoundEvent.Type.LOW_ENERGY));
        assertTrue(blastSim.charger.isCharging(), "refused dash must keep active Blast charge");

        // Refused dash preserves Rail Lance 1.8x zoom, while accepted dash keeps suppression across dash (#126/#127).
        GameSimulation railSim = emptyCorridorHolding(WeaponSlot.RAIL_LANCE);
        railSim.energy.setEnergy(10f);
        PlayerInput railInput = new PlayerInput();
        railInput.charge = true;
        for (int i = 0; i < 12; i++) railSim.update(FRAME, railInput);
        assertEquals(1f, railSim.getZoom(), 1e-4f);

        railInput.dash = true;
        railSim.update(FRAME, railInput);
        railInput.dash = false;
        assertFalse(railSim.player.isDashing());
        assertEquals(1f, railSim.getZoom(), 1e-4f, "refused dash must keep 1.8x optic zoom");

        railSim.energy.setEnergy(100f);
        railInput.dash = true;
        railSim.update(FRAME, railInput);
        railInput.dash = false;
        for (int i = 0; i < 15; i++) railSim.update(FRAME, railInput);
        assertFalse(railSim.player.isDashing());
        assertEquals(0f, railSim.getZoom(), 1e-5f, "accepted dash suppresses zoom while RMB stays held");

        // Post-dash cooldown with < 25 energy synchronizes LOW_ENERGY audio and HUD warning (#125).
        GameSimulation cdSim = emptyCorridor();
        cdSim.energy.setEnergy(35f);
        PlayerInput cdInput = new PlayerInput();
        cdInput.dash = true;
        cdSim.update(FRAME, cdInput);
        cdInput.dash = false;
        for (int i = 0; i < 9; i++) cdSim.update(FRAME, cdInput);
        assertFalse(cdSim.player.isDashing());

        cdSim.drainSoundEvents();
        cdInput.dash = true;
        cdSim.update(FRAME, cdInput);
        assertEquals(1, countSound(cdSim.drainSoundEvents(), SoundEvent.Type.LOW_ENERGY));
        assertTrue(cdSim.player.isDashRefused());
    }

    @Test
    void holdingRightMouseDuringSwapToRailLanceZoomsImmediatelyWhenDrawCompletes() {
        GameSimulation sim = emptyCorridorHolding(WeaponSlot.PISTOL);
        PlayerInput input = new PlayerInput();

        input.weaponSlot = 3;
        sim.update(FRAME, input);
        input.weaponSlot = 0;
        assertTrue(sim.getSwapProgress() > 0f);

        sim.drainSoundEvents();
        input.charge = true;
        for (int i = 0; i < 12; i++) sim.update(FRAME, input);
        assertEquals(0f, sim.getSwapProgress(), 1e-6f);
        assertEquals(WeaponSlot.RAIL_LANCE, sim.getActiveWeapon());
        assertTrue(sim.getZoom() > 0f, "holding RMB during draw zooms immediately once Rail Lance raises");
        assertEquals(1, countSound(sim.drainSoundEvents(), SoundEvent.Type.RAIL_ZOOM_IN));
    }

    @Test
    void muzzleOriginUses3DBoundsOverShortTargetsAndRetractingDoorsAndCanisterUses3DSplash() {
        // 1. Jumping beside a 0.55u Canister clears its top in 3D instead of snapping muzzle back (#136).
        GameSimulation canisterMuzzleSim = new GameSimulation(GameMap.parse(
            "############",
            "#..........#",
            "#>..o......#",
            "#..........#",
            "############"));
        PlayerInput chargeJump = new PlayerInput();
        chargeJump.charge = true;
        for (int i = 0; i < 30; i++) canisterMuzzleSim.update(FRAME, chargeJump);
        chargeJump.jump = true;
        canisterMuzzleSim.update(FRAME, chargeJump);
        chargeJump.jump = false;
        while (canisterMuzzleSim.player.getVerticalVelocity() > 0.25f) {
            canisterMuzzleSim.update(FRAME, chargeJump);
        }
        canisterMuzzleSim.player.position.set(3.98f, 2.39f);
        chargeJump.charge = false;
        canisterMuzzleSim.update(FRAME, chargeJump);
        assertEquals(1, canisterMuzzleSim.projectiles.size());
        Vector3 overCanisterOrigin = canisterMuzzleSim.projectiles.get(0).position;
        assertTrue(overCanisterOrigin.x - canisterMuzzleSim.player.position.x > 0.45f,
            "muzzle above 0.55u canister spawns forward at " + overCanisterOrigin);

        // 2. Jumping in front of a retracting bulkhead (openProgress = 0.81 >= 0.75) pulls muzzle out of 3D slab (#136).
        GameSimulation doorMuzzleSim = new GameSimulation(GameMap.parse(
            "#########",
            "#..#....#",
            "#.>=....#",
            "#..#....#",
            "#########"));
        Door door = doorMuzzleSim.doors.get(0);
        door.open();
        door.update(Door.OPEN_DURATION * 0.81f);
        PlayerInput doorInput = new PlayerInput();
        doorInput.charge = true;
        for (int i = 0; i < 25; i++) {
            doorMuzzleSim.player.updateTimers(0f);
            doorMuzzleSim.charger.update(FRAME, true);
        }
        doorMuzzleSim.player.position.set(2.98f, 2.50f);
        doorMuzzleSim.player.tryJump();
        while (doorMuzzleSim.player.getVerticalVelocity() > 0.25f) {
            doorMuzzleSim.player.move(doorMuzzleSim.map, FRAME * 0f, 0f, FRAME);
        }
        doorInput.charge = false;
        doorMuzzleSim.update(FRAME, doorInput);
        assertEquals(1, doorMuzzleSim.projectiles.size());
        Vector3 doorBlastOrigin = doorMuzzleSim.projectiles.get(0).position;
        assertTrue(doorBlastOrigin.x < 3.1f,
            "muzzle pulled back from 3D retracting bulkhead slab (openProgress >= 0.75): " + doorBlastOrigin);
        assertFalse(door.overlapsSphere(doorBlastOrigin.x, doorBlastOrigin.y, doorBlastOrigin.z,
            BlastProjectile.RADIUS), "blast must not spawn inside 3D retracting bulkhead slab: " + doorBlastOrigin);

        // 3. Canister detonation passes 3D blastY = height * 0.5f so jumping reduces splash damage (#140).
        GameSimulation groundedSim = corridorWithCanister();
        groundedSim.player.position.set(3.0f, 1.5f);
        groundedSim.targets.get(0).damage(999f);
        groundedSim.update(FRAME, new PlayerInput());

        GameSimulation jumpingSim = corridorWithCanister();
        jumpingSim.player.position.set(3.0f, 1.5f);
        jumpingSim.player.tryJump();
        while (jumpingSim.player.getVerticalVelocity() > 0.2f) {
            jumpingSim.player.move(jumpingSim.map, 0f, 0f, FRAME);
        }
        assertTrue(jumpingSim.player.getFeetY() > jumpingSim.targets.get(0).getHeight() * 0.5f);
        jumpingSim.targets.get(0).damage(999f);
        jumpingSim.update(FRAME, new PlayerInput());

        assertTrue(jumpingSim.getDamageTaken() > 0f);
        assertTrue(jumpingSim.getDamageTaken() < groundedSim.getDamageTaken(),
            "airborne player above canister midpoint takes less 3D splash (" + jumpingSim.getDamageTaken()
                + " vs " + groundedSim.getDamageTaken() + ")");
    }

    @Test
    void enemyBoltsAndShockGratesDoNotTriggerPlayerHitMarkersOnDestructionOrChainKills() {
        PlayerInput idle = new PlayerInput();

        // 1. Enemy plasma bolt breaking a Ring Board emits TARGET_DESTROYED and break VFX without a hit-marker (#53).
        GameSimulation boardSim = new GameSimulation(GameMap.parse(
            "##########",
            "#>...O...#",
            "##########"));
        boardSim.targets.get(0).damage(100f);
        boardSim.enemyProjectiles.add(new EnemyProjectile(
            new Vector3(4.8f, 0.5f, 1.5f), new Vector3(1f, 0f, 0f), 10f, 30f, null));
        for (int i = 0; i < 30 && !boardSim.enemyProjectiles.isEmpty(); i++) {
            boardSim.update(FRAME, idle);
            assertEquals(0f, boardSim.getHitMarker(), 1e-6f);
            assertFalse(boardSim.isHitMarkerKill());
        }
        assertTrue(boardSim.targets.get(0).isDestroyed());
        assertEquals(1, boardSim.getTargetsDestroyed());
        assertTrue(count(boardSim, Effect.Type.EXPLOSION) >= 1);
        assertEquals(1, countSound(boardSim.drainSoundEvents(), SoundEvent.Type.TARGET_DESTROYED));

        // 2. Enemy plasma bolt breaking a Canister that chains a second Canister and kills a Drone clears stage
        //    with normal explosions and sounds, but zero player hit-marker (#53).
        GameSimulation chainSim = new GameSimulation(
            GameMap.parse(
                "############",
                "#>....ooD..#",
                "############"),
            PlayerLoadout.fresh(),
            true);
        chainSim.enemyProjectiles.add(new EnemyProjectile(
            new Vector3(5.8f, 0.30f, 1.5f), new Vector3(1f, 0f, 0f), 10f, 60f, null));
        for (int i = 0; i < 30 && !chainSim.isLevelCleared(); i++) {
            chainSim.update(FRAME, idle);
            assertEquals(0f, chainSim.getHitMarker(), 1e-6f);
            assertFalse(chainSim.isHitMarkerKill());
        }
        assertTrue(chainSim.targets.get(0).isDestroyed());
        assertTrue(chainSim.targets.get(1).isDestroyed());
        assertTrue(chainSim.enemies.get(0).isDestroyed());
        assertEquals(2, chainSim.getTargetsDestroyed());
        assertEquals(1, chainSim.getEnemiesDestroyed());
        assertTrue(chainSim.isLevelCleared());
        List<SoundEvent> chainEvents = chainSim.drainSoundEvents();
        assertEquals(2, countSound(chainEvents, SoundEvent.Type.EXPLOSION));
        assertEquals(1, countSound(chainEvents, SoundEvent.Type.ENEMY_DESTROYED));

        // 3. Floor ~ Shock Grate killing a Guard Bot neither flashes a hit-marker nor upgrades an active non-lethal one.
        GameSimulation grateSim = new GameSimulation(GameMap.parse(
            "#######",
            "#>O...#",
            "#####~#",
            "#....G#",
            "#######"));
        Enemy guard = grateSim.enemies.get(0);
        guard.damage(95f);
        for (int i = 0; i < 180 && !guard.isDestroyed(); i++) {
            grateSim.update(FRAME, idle);
            assertEquals(0f, grateSim.getHitMarker(), 1e-6f);
            assertFalse(grateSim.isHitMarkerKill());
        }
        assertTrue(guard.isDestroyed());
        assertEquals(1, grateSim.getEnemiesDestroyed());
        List<SoundEvent> grateEvents = grateSim.drainSoundEvents();
        assertEquals(1, countSound(grateEvents, SoundEvent.Type.HAZARD_ZAP));
        assertEquals(1, countSound(grateEvents, SoundEvent.Type.ENEMY_DESTROYED));

        GameSimulation activeHitSim = new GameSimulation(GameMap.parse(
            "#######",
            "#>O...#",
            "#####~#",
            "#....G#",
            "#######"));
        Enemy dyingGuard = activeHitSim.enemies.get(0);
        dyingGuard.damage(95f);
        for (int i = 0; i < 180 && !dyingGuard.isDestroyed() && dyingGuard.getZ() > 3.04f; i++) {
            activeHitSim.update(FRAME, idle);
        }
        assertFalse(dyingGuard.isDestroyed());
        PlayerInput oneShot = new PlayerInput();
        oneShot.fire = true;
        activeHitSim.update(FRAME, oneShot);
        oneShot.fire = false;
        assertTrue(activeHitSim.getHitMarker() > 0.9f);
        assertFalse(activeHitSim.isHitMarkerKill());
        for (int i = 0; i < 8 && !dyingGuard.isDestroyed(); i++) {
            activeHitSim.update(FRAME, oneShot);
        }
        assertTrue(dyingGuard.isDestroyed());
        assertTrue(activeHitSim.getHitMarker() > 0f, "player non-lethal hit-marker still decaying");
        assertFalse(activeHitSim.isHitMarkerKill(), "grate kill must not upgrade non-lethal hit-marker to kill");
    }

    @Test
    void playerCanisterChainAndMidFlightBlastKillsStillTriggerKillHitMarker() {
        // 1. Player shooting a Canister that chains into a second Canister and kills a Drone triggers kill hit-marker.
        GameSimulation pistolChainSim = new GameSimulation(GameMap.parse(
            "############",
            "#>.ooD.....#",
            "############"));
        PlayerInput fireInput = new PlayerInput();
        fireInput.fire = true;
        for (int i = 0; i < 45 && !pistolChainSim.enemies.get(0).isDestroyed(); i++) {
            pistolChainSim.update(FRAME, fireInput);
        }
        assertTrue(pistolChainSim.targets.get(0).isDestroyed());
        assertTrue(pistolChainSim.targets.get(1).isDestroyed());
        assertTrue(pistolChainSim.enemies.get(0).isDestroyed());
        assertEquals(1f, pistolChainSim.getHitMarker(), 1e-5f);
        assertTrue(pistolChainSim.isHitMarkerKill());

        // 2. Player BlastProjectile detonating in flight on a Canister and chaining a Drone around a wall corner
        //    triggers kill hit-marker.
        GameSimulation blastChainSim = new GameSimulation(GameMap.parse(
            "#########",
            "#>.oo...#",
            "####D####",
            "#########"));
        PlayerInput chargeInput = new PlayerInput();
        chargeInput.charge = true;
        for (int i = 0; i < 40; i++) blastChainSim.update(FRAME, chargeInput);
        chargeInput.charge = false;
        blastChainSim.update(FRAME, chargeInput);
        assertEquals(1, blastChainSim.projectiles.size());
        assertEquals(0f, blastChainSim.getHitMarker(), 1e-6f);

        for (int i = 0; i < 60 && !blastChainSim.projectiles.isEmpty(); i++) {
            blastChainSim.update(FRAME, chargeInput);
        }
        assertTrue(blastChainSim.projectiles.isEmpty());
        assertTrue(blastChainSim.targets.get(0).isDestroyed());
        assertTrue(blastChainSim.targets.get(1).isDestroyed());
        assertTrue(blastChainSim.enemies.get(0).isDestroyed());
        assertEquals(1f, blastChainSim.getHitMarker(), 1e-5f);
        assertTrue(blastChainSim.isHitMarkerKill());
    }

    @Test
    void blastChargeFizzlesWhenEnergyDropsBelowMinimumMidHoldStoppingAudioAndEmittingLowEnergy() {
        GameSimulation sim = emptyCorridor();
        PlayerInput input = new PlayerInput();
        input.charge = true;
        for (int i = 0; i < 25; i++) sim.update(FRAME, input);
        assertTrue(sim.charger.isCharging());
        assertTrue(sim.charger.isReadyToFire());
        assertTrue(sim.isBlastChargingAudioActive());

        sim.energy.setEnergy(5f);
        sim.drainSoundEvents();
        for (int i = 0; i < 10; i++) sim.update(FRAME, input);
        assertFalse(sim.charger.isCharging());
        assertFalse(sim.isBlastChargingAudioActive());
        assertEquals(0f, sim.getBlastChargeProgress(), 1e-6f);
        assertTrue(sim.projectiles.isEmpty());
        assertEquals(1, countSound(sim.drainSoundEvents(), SoundEvent.Type.LOW_ENERGY));

        input.charge = false;
        sim.update(FRAME, input);
        assertTrue(sim.projectiles.isEmpty());
        assertEquals(0, countSound(sim.drainSoundEvents(), SoundEvent.Type.BLAST_RELEASE));
    }

    @Test
    void nonFiniteAndNegativeFrameTimesClampToZeroWithoutPoisoningSimulationState() {
        GameSimulation sim = new GameSimulation(
            GameMap.parse(
                "#############",
                "#>+.O.=...D.#",
                "#############"),
            new PlayerLoadout(70f, 20f, Difficulty.NORMAL, true, 8, true, 4, WeaponSlot.RAIL_LANCE),
            false);
        Door door = sim.doors.get(0);
        PlayerInput idle = new PlayerInput();

        // Collect the medkit (+) and decay the toast into its fade-out window so toastAlpha sits strictly in (0, 1).
        sim.player.position.set(2.4f, 1.5f);
        sim.update(0.05f, idle);
        assertNotNull(sim.getToastMessage());
        for (int i = 0; i < 34; i++) {
            sim.update(0.05f, idle);
        }

        // Open the door, fire a Rail Lance beam into the Ring Board (O), and advance partway into a weapon swap.
        door.open();
        PlayerInput stepInput = new PlayerInput();
        stepInput.fire = true;
        sim.update(FRAME, stepInput);
        stepInput.fire = false;
        stepInput.weaponSlot = 1;
        sim.update(FRAME, stepInput);
        stepInput.weaponSlot = 0;
        sim.update(FRAME * 0.5f, stepInput);

        float timeBefore = sim.getTime();
        float hitMarkerBefore = sim.getHitMarker();
        float toastAlphaBefore = sim.getToastAlpha();
        float swapProgressBefore = sim.getSwapProgress();
        float beamProgressBefore = sim.railBeams.get(0).getProgress();
        float doorProgressBefore = door.getOpenProgress();
        float playerXBefore = sim.player.position.x;
        float playerZBefore = sim.player.position.y;
        assertTrue(timeBefore > 0f);
        assertTrue(hitMarkerBefore > 0f && hitMarkerBefore < 1f);
        assertTrue(toastAlphaBefore > 0f && toastAlphaBefore < 1f);
        assertTrue(swapProgressBefore > 0f && swapProgressBefore < 1f);
        assertTrue(beamProgressBefore > 0f && beamProgressBefore < 1f);
        assertTrue(doorProgressBefore > 0f && doorProgressBefore < 1f);

        float[] badDeltas = {Float.NaN, Float.NEGATIVE_INFINITY, Float.POSITIVE_INFINITY, -0.25f};
        for (float badDt : badDeltas) {
            sim.update(badDt, idle);
            assertEquals(timeBefore, sim.getTime(), 1e-6f, "time after " + badDt);
            assertEquals(hitMarkerBefore, sim.getHitMarker(), 1e-6f, "hitMarker after " + badDt);
            assertEquals(toastAlphaBefore, sim.getToastAlpha(), 1e-6f, "toastAlpha after " + badDt);
            assertNotNull(sim.getToastMessage());
            assertEquals(swapProgressBefore, sim.getSwapProgress(), 1e-6f, "swapProgress after " + badDt);
            assertEquals(beamProgressBefore, sim.railBeams.get(0).getProgress(), 1e-6f, "railBeam after " + badDt);
            assertEquals(doorProgressBefore, door.getOpenProgress(), 1e-6f, "door after " + badDt);
            assertEquals(playerXBefore, sim.player.position.x, 1e-6f);
            assertEquals(playerZBefore, sim.player.position.y, 1e-6f);
            assertTrue(Float.isFinite(sim.getDamageTaken()));
            assertTrue(Float.isFinite(sim.energy.getEnergy()));
            assertTrue(Float.isFinite(sim.player.getYaw()));
            assertTrue(Float.isFinite(sim.player.getPitch()));
            assertTrue(Float.isFinite(sim.viewmodel.getBobX()));
            assertTrue(Float.isFinite(sim.viewmodel.getBobY()));
            assertTrue(Float.isFinite(sim.enemies.get(0).getX()));
            assertTrue(Float.isFinite(sim.enemies.get(0).getZ()));
        }

        // Direct RailBeam.update also ignores non-finite and negative deltas.
        sim.railBeams.get(0).update(Float.NaN);
        sim.railBeams.get(0).update(Float.NEGATIVE_INFINITY);
        sim.railBeams.get(0).update(-0.1f);
        assertEquals(beamProgressBefore, sim.railBeams.get(0).getProgress(), 1e-6f);

        // Normal frames immediately resume advancing time and decaying timers.
        sim.update(FRAME, idle);
        assertEquals(timeBefore + FRAME, sim.getTime(), 1e-5f);
        assertTrue(sim.getHitMarker() < hitMarkerBefore);
        assertTrue(sim.getToastAlpha() < toastAlphaBefore);

        // Inactive branch (dead player) also stays finite across non-finite/negative deltas.
        sim.player.damage(500f);
        assertTrue(sim.player.isDead());
        for (float badDt : badDeltas) {
            sim.update(badDt, idle);
            assertTrue(Float.isFinite(sim.getTime()));
            assertTrue(Float.isFinite(sim.getHitMarker()));
            assertTrue(Float.isFinite(sim.getToastAlpha()));
            assertTrue(Float.isFinite(sim.enemies.get(0).getX()));
        }
    }
}

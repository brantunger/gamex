package io.github.brantunger.gamex.world;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.junit.jupiter.api.Assertions.fail;

import com.badlogic.gdx.math.Vector2;
import io.github.brantunger.gamex.GameSimulation;
import io.github.brantunger.gamex.player.Difficulty;
import io.github.brantunger.gamex.player.Player;
import io.github.brantunger.gamex.player.PlayerInput;
import io.github.brantunger.gamex.player.PlayerLoadout;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;

/** Enemies driven through {@link GameSimulation} must not jam on wall corners, props or shock grates. */
class EnemyMovementTest {

    private static final float DT = 1f / 60f;

    /** A doorway whose corner the straight line from the alcove to the player grazes. */
    private static GameMap cornerMap(char enemy) {
        return GameMap.parse(
            "########################",
            "#..................#...#",
            "#.>................#." + enemy + ".#",
            "#..................#...#",
            "#......................#",
            "#..................#...#",
            "########################");
    }

    /** Builds a simulation in which only {@code subject} is alive, every door is open and the subject is alert. */
    private static GameSimulation soloSim(GameMap map, Enemy.Kind kind, float spawnX, float spawnZ) {
        GameSimulation sim = new GameSimulation(map, PlayerLoadout.fresh(Difficulty.NORMAL), false);
        Enemy subject = null;
        for (Enemy enemy : sim.enemies) {
            if (enemy.getKind() == kind && Math.abs(enemy.getX() - spawnX) < 0.01f
                && Math.abs(enemy.getZ() - spawnZ) < 0.01f) {
                subject = enemy;
            }
        }
        if (subject == null) fail("no " + kind + " spawns at (" + spawnX + ", " + spawnZ + ")");
        for (Enemy enemy : sim.enemies) {
            if (enemy != subject) enemy.damage(99999f);
        }
        for (Door door : sim.doors) door.open();
        subject.alert();
        return sim;
    }

    private static Enemy subject(GameSimulation sim) {
        for (Enemy enemy : sim.enemies) {
            if (!enemy.isDestroyed()) return enemy;
        }
        return fail("no live enemy");
    }

    /** The live objects that must keep {@code subject} out: props, consoles and other enemies. */
    private static List<Damageable> solidsExcept(GameSimulation sim, Enemy subject) {
        List<Damageable> solids = new ArrayList<>();
        solids.addAll(sim.targets);
        solids.addAll(sim.consoles);
        for (Enemy enemy : sim.enemies) {
            if (enemy != subject) solids.add(enemy);
        }
        return solids;
    }

    /**
     * Steps the simulation with an idle player, keeping {@code subject} alert, until the player is first damaged, the
     * player dies or {@code seconds} pass. Every frame the subject must stay out of walls and live solid objects.
     * @return the time of the first damage to the player, or -1 if there was none
     */
    private static float runUntilPlayerDamaged(GameSimulation sim, Enemy subject, float seconds) {
        return run(sim, subject, seconds, true);
    }

    /**
     * Steps the simulation as {@link #runUntilPlayerDamaged} does, but carries on after the first damage when
     * {@code stopAtFirstHit} is false.
     * @return the time of the first damage to the player, or -1 if there was none
     */
    private static float run(GameSimulation sim, Enemy subject, float seconds, boolean stopAtFirstHit) {
        PlayerInput input = new PlayerInput();
        float firstHit = -1f;
        List<Damageable> solids = solidsExcept(sim, subject);
        Vector2 at = new Vector2();
        int frames = Math.round(seconds / DT);
        for (int i = 0; i < frames && !sim.player.isDead(); i++) {
            subject.alert();
            sim.update(DT, input);
            at.set(subject.getX(), subject.getZ());
            assertFalse(Collision.overlapsWall(sim.map, sim.doors, at.x, at.y, subject.getRadius() - 1e-3f),
                "enemy inside a wall at " + at);
            assertFalse(Collision.overlapsSolid(solids, at, subject.getRadius()), "enemy inside a prop at " + at);
            if (firstHit < 0f && sim.getDamageTaken() > 0f) {
                firstHit = (i + 1) * DT;
                if (stopAtFirstHit) break;
            }
        }
        return firstHit;
    }

    private static String where(Enemy enemy) {
        return enemy.getKind() + " at (" + enemy.getX() + ", " + enemy.getZ() + ") state=" + enemy.getState()
            + " health=" + enemy.getHealth();
    }

    @Test
    void droneWithGrazingLineOfSightRoundsTheDoorwayCornerAndReachesThePlayer() {
        GameSimulation sim = soloSim(cornerMap('D'), Enemy.Kind.DRONE, 21.5f, 2.5f);
        Enemy drone = subject(sim);

        float firstHit = runUntilPlayerDamaged(sim, drone, 15f);

        assertTrue(firstHit > 0f, "drone never reached the player: " + where(drone));
    }

    @Test
    void wardenWithGrazingLineOfSightLeavesItsAlcoveThroughTheDoorway() {
        GameSimulation sim = soloSim(cornerMap('B'), Enemy.Kind.WARDEN, 21.5f, 2.5f);
        Enemy warden = subject(sim);
        PlayerInput input = new PlayerInput();
        List<Damageable> solids = solidsExcept(sim, warden);
        Vector2 at = new Vector2();

        // The test is about leaving the alcove, so progress is measured by position; the Warden can hurt the
        // player only once it reaches them.
        boolean leftAlcove = false;
        for (int i = 0; i < 60 * 15 && !sim.player.isDead() && !leftAlcove; i++) {
            warden.alert();
            sim.update(DT, input);
            at.set(warden.getX(), warden.getZ());
            assertFalse(Collision.overlapsWall(sim.map, sim.doors, at.x, at.y, warden.getRadius() - 1e-3f),
                "warden inside a wall at " + at);
            assertFalse(Collision.overlapsSolid(solids, at, warden.getRadius()), "warden inside a prop at " + at);
            leftAlcove = warden.getX() < 18.5f;
        }

        assertTrue(leftAlcove, "warden never left the alcove: " + where(warden));
    }

    @Test
    void guardWithGrazingLineOfSightStillStrafesThroughTheDoorway() {
        GameSimulation sim = soloSim(cornerMap('G'), Enemy.Kind.GUARD, 21.5f, 2.5f);
        Enemy guard = subject(sim);

        float firstHit = runUntilPlayerDamaged(sim, guard, 15f);

        assertTrue(firstHit > 0f, "guard never hit the player: " + where(guard));
    }

    @Test
    void stage04DroneBehindTheDoorwayCornerReachesThePlayerAtTheSpawn() {
        GameSimulation sim = soloSim(Levels.lockdownWing().map, Enemy.Kind.DRONE, 21.5f, 2.5f);
        Enemy drone = subject(sim);

        float firstHit = runUntilPlayerDamaged(sim, drone, 20f);

        assertTrue(firstHit > 0f, "drone never reached the player: " + where(drone));
    }

    @Test
    void droneWalkingStraightAtACanisterInAnOpenRoomGoesRoundIt() {
        GameSimulation sim = soloSim(GameMap.parse(
            "#########",
            "#.......#",
            "#D.o...>#",
            "#.......#",
            "#########"), Enemy.Kind.DRONE, 1.5f, 2.5f);
        Enemy drone = subject(sim);

        float firstHit = runUntilPlayerDamaged(sim, drone, 10f);

        assertTrue(firstHit > 0f, "drone never reached the player: " + where(drone));
        assertFalse(sim.targets.get(0).isDestroyed(), "the canister must still be standing");
    }

    @Test
    void droneGoesRoundACanisterThatStandsAgainstTheWallOnItsRight() {
        GameSimulation sim = soloSim(GameMap.parse(
            "#########",
            "#.......#",
            "#D.o...>#",
            "#########"), Enemy.Kind.DRONE, 1.5f, 2.5f);
        Enemy drone = subject(sim);

        float firstHit = runUntilPlayerDamaged(sim, drone, 10f);

        assertTrue(firstHit > 0f, "drone never reached the player: " + where(drone));
    }

    @Test
    void droneGoesRoundACanisterThatStandsAgainstTheWallOnItsLeft() {
        GameSimulation sim = soloSim(GameMap.parse(
            "#########",
            "#D.o...>#",
            "#.......#",
            "#########"), Enemy.Kind.DRONE, 1.5f, 1.5f);
        Enemy drone = subject(sim);

        float firstHit = runUntilPlayerDamaged(sim, drone, 10f);

        assertTrue(firstHit > 0f, "drone never reached the player: " + where(drone));
    }

    @Test
    void droneBehindACanisterThatPlugsACorridorComesToRest() {
        GameSimulation sim = soloSim(GameMap.parse(
            "#########",
            "#D.o...>#",
            "#########"), Enemy.Kind.DRONE, 1.5f, 1.5f);
        Enemy drone = subject(sim);

        runUntilPlayerDamaged(sim, drone, 5f);
        float restX = drone.getX();
        float restZ = drone.getZ();
        PlayerInput input = new PlayerInput();
        for (int i = 0; i < 60 * 5; i++) {
            drone.alert();
            sim.update(DT, input);
            assertEquals(0f, Vector2.dst(restX, restZ, drone.getX(), drone.getZ()), 1e-4f,
                "drone is shaking against the canister: " + where(drone));
        }

        assertTrue(drone.getX() < 3.5f, "drone passed a canister it cannot fit round: " + where(drone));
        assertEquals(0f, sim.getDamageTaken(), 0f);
    }

    @Test
    void droneGoesRoundATurretStandingBetweenItAndThePlayer() {
        GameSimulation sim = new GameSimulation(GameMap.parse(
            "#########",
            "#.......#",
            "#D.T...>#",
            "#.......#",
            "#########"), PlayerLoadout.fresh(Difficulty.NORMAL), false);
        Enemy drone = sim.enemies.get(0);
        Enemy turret = sim.enemies.get(1);
        assertEquals(Enemy.Kind.DRONE, drone.getKind());
        assertEquals(Enemy.Kind.TURRET, turret.getKind());
        PlayerInput input = new PlayerInput();
        Vector2 at = new Vector2();
        List<Damageable> solids = solidsExcept(sim, drone);

        // The turret shoots the player too, so progress is measured by position, not by damage.
        boolean passedTurret = false;
        for (int i = 0; i < 60 * 5 && !sim.player.isDead() && !passedTurret; i++) {
            drone.alert();
            sim.update(DT, input);
            at.set(drone.getX(), drone.getZ());
            assertFalse(Collision.overlapsSolid(solids, at, drone.getRadius()), "drone inside the turret at " + at);
            passedTurret = drone.getX() > turret.getX() + 1f;
        }

        assertTrue(passedTurret, "drone never got past the turret: " + where(drone));
    }

    @Test
    void droneCrossingARoomWithACanisterToADoorwayReachesThePlayer() {
        GameSimulation sim = soloSim(GameMap.parse(
            "###########",
            "#D........#",
            "#.........#",
            "#.o.......#",
            "#.........#",
            "####.######",
            "#.........#",
            "#>........#",
            "###########"), Enemy.Kind.DRONE, 1.5f, 1.5f);
        Enemy drone = subject(sim);

        float firstHit = runUntilPlayerDamaged(sim, drone, 15f);

        assertTrue(firstHit > 0f, "drone never reached the player: " + where(drone));
    }

    @Test
    void stage03GuardGoesRoundTheCanisterOnItsPathToThePlayer() {
        GameSimulation sim = soloSim(Levels.securityHub().map, Enemy.Kind.GUARD, 28.5f, 4.5f);
        Enemy guard = subject(sim);

        float firstHit = runUntilPlayerDamaged(sim, guard, 40f);

        assertTrue(firstHit > 0f, "guard never hit the player: " + where(guard));
    }

    @Test
    void stage05DroneGoesRoundTheCanisterDiagonallyOnItsPathToThePlayer() {
        GameSimulation sim = soloSim(Levels.commandVault().map, Enemy.Kind.DRONE, 7.5f, 20.5f);
        Enemy drone = subject(sim);

        float firstHit = runUntilPlayerDamaged(sim, drone, 40f);

        assertTrue(firstHit > 0f, "drone never reached the player: " + where(drone));
    }

    @Test
    void guardRoutingRoundAShockGrateIsNeverZapped() {
        GameSimulation sim = soloSim(GameMap.parse(
            "#########",
            "#G..#...#",
            "#.~.#...#",
            "#......>#",
            "#########"), Enemy.Kind.GUARD, 1.5f, 1.5f);
        Enemy guard = subject(sim);

        // The whole window counts, not only the time up to the first shot.
        float firstHit = run(sim, guard, 10f, false);

        // A guard that never moved would also be unharmed, so it must also have come round and shot the player.
        assertTrue(firstHit > 0f, "guard never hit the player: " + where(guard));
        assertEquals(Enemy.Kind.GUARD.maxHealth, guard.getHealth(), 0f, "guard was zapped: " + where(guard));
    }

    @Test
    void guardOutOfSightGoesRoundACanisterWithoutSidesteppingOntoTheGrateBesideIt() {
        // Dead ahead of the canister the first side tried is the south one, where the grate is.
        GameSimulation sim = soloSim(GameMap.parse(
            "#########",
            "#.......#",
            "#G...o..#",
            "#....~#.#",
            "#.....#>#",
            "#########"), Enemy.Kind.GUARD, 1.5f, 2.5f);
        Enemy guard = subject(sim);

        // The whole window counts, not only the time up to the first shot.
        float firstHit = run(sim, guard, 10f, false);

        // A guard that never moved would also be unharmed, so it must also have come round and shot the player.
        assertTrue(firstHit > 0f, "guard never hit the player: " + where(guard));
        assertEquals(Enemy.Kind.GUARD.maxHealth, guard.getHealth(), 0f, "guard was zapped: " + where(guard));
        assertFalse(sim.targets.get(0).isDestroyed(), "the canister must still be standing");
    }

    @Test
    void wardenSeparatedBySecurityConsoleWithinStopRangeGoesRoundItAndHitsPlayer() {
        GameSimulation sim = soloSim(GameMap.parse(
            "#########",
            "#.......#",
            "#..BS..>#",
            "#.......#",
            "#########"), Enemy.Kind.WARDEN, 3.5f, 2.5f);
        Enemy warden = subject(sim);
        // Place the player right across the indestructible console so Warden meets it at dist <= 1.35.
        sim.player.position.set(5.00f, 2.50f);

        float firstHit = runUntilPlayerDamaged(sim, warden, 8f);

        assertTrue(firstHit > 0f, "warden never damaged the player across the console: " + where(warden));
    }

    @Test
    void droneBlockedBehindCanisterWhileDoorIsClosedGoesRoundItOnceDoorOpens() {
        GameSimulation sim = new GameSimulation(GameMap.parse(
            "#######",
            "###D###",
            "###o=##",
            "###.>##",
            "#######"), PlayerLoadout.fresh(Difficulty.NORMAL), false);
        Enemy drone = subject(sim);
        Door door = sim.doors.get(0);
        PlayerInput input = new PlayerInput();

        for (int i = 0; i < 90; i++) {
            drone.alert();
            sim.update(DT, input);
        }
        float restX = drone.getX();
        float restZ = drone.getZ();
        for (int i = 0; i < 60; i++) {
            drone.alert();
            sim.update(DT, input);
            assertEquals(0f, Vector2.dst(restX, restZ, drone.getX(), drone.getZ()), 1e-4f,
                "drone jittered while bypass door was closed: " + where(drone));
        }
        assertEquals(0f, sim.getDamageTaken(), 0f);

        door.open();
        float firstHit = runUntilPlayerDamaged(sim, drone, 6f);

        assertTrue(firstHit > 0f, "drone stayed stuck behind canister after door opened: " + where(drone));
        assertTrue(drone.getZ() > 2.5f, "drone did not pass the canister: " + where(drone));
    }

    @Test
    void droneLungingInOneUnitWideCorridorNeverFinishesInsidePlayerOrWall() {
        GameSimulation sim = soloSim(GameMap.parse(
            "###",
            "#D#",
            "#.#",
            "#.#",
            "#>#",
            "#.#",
            "###"), Enemy.Kind.DRONE, 1.5f, 1.5f);
        Enemy drone = subject(sim);
        sim.player.position.set(1.26f, 4.50f);
        PlayerInput input = new PlayerInput();
        float minDist = Player.RADIUS + drone.getRadius();

        for (int i = 0; i < 180 && !sim.player.isDead(); i++) {
            drone.alert();
            sim.update(DT, input);
            float dist = Vector2.dst(drone.getX(), drone.getZ(), sim.player.position.x, sim.player.position.y);
            assertTrue(dist >= minDist - 1e-4f,
                "drone overlapped player in 1.0u corridor at dist=" + dist + ": " + where(drone));
            assertFalse(Collision.overlapsWall(sim.map, sim.doors, drone.getX(), drone.getZ(),
                drone.getRadius() - 1e-3f), "drone overlapped wall in 1.0u corridor: " + where(drone));
        }

        assertTrue(sim.getDamageTaken() > 0f, "drone never hit player in corridor: " + where(drone));
    }

    @Test
    void guardNeverClipsObliqueShockGrateCornersOrRoutesAcrossDoubleGrateTowardGratePlayer() {
        // 1. Stage-06-style sandwich of two ~~ shock grate strips with oblique sightline strafing: Guard must hit the
        // player and keep strafing for the full 10s window without ever clipping a grate corner or taking zap damage.
        GameSimulation strafeSim = soloSim(GameMap.parse(
            "############",
            "#.>........#",
            "#....~~....#",
            "#......G...#",
            "#....~~....#",
            "#..........#",
            "############"), Enemy.Kind.GUARD, 7.5f, 3.5f);
        Enemy strafeGuard = subject(strafeSim);
        PlayerInput idle = new PlayerInput();
        float strafeHit = -1f;
        for (int i = 0; i < 60 * 10 && !strafeSim.player.isDead(); i++) {
            strafeGuard.alert();
            strafeSim.update(DT, idle);
            assertFalse(strafeSim.map.isHazardAt(strafeGuard.getX(), strafeGuard.getZ()),
                "strafing Guard clipped onto ~ grate at " + where(strafeGuard));
            if (strafeHit < 0f && strafeSim.getDamageTaken() > 0f) {
                strafeHit = (i + 1) * DT;
            }
        }
        assertTrue(strafeHit > 0f, "Guard never damaged player: " + where(strafeGuard));
        assertEquals(Enemy.Kind.GUARD.maxHealth, strafeGuard.getHealth(), 0f,
            "strafing Guard took shock grate damage: " + where(strafeGuard));

        // 2. Out-of-sight Guard when player hops repeatedly in place over the west ~ cell of a ~~ strip: Guard detours
        // around the safe west corridor to (2..3, 1) instead of climbing onto (5, 1) ~, acquires LOS, and fires at the
        // player with zero shock damage to the Guard.
        GameMap grateGoalLoop = GameMap.parse(
            "#######",
            "#..>~~#",
            "#..##.#",
            "#...#G#",
            "#.....#",
            "#######");
        GameSimulation loopSim = new GameSimulation(
            grateGoalLoop, new PlayerLoadout(100f, 100f, Difficulty.EASY), false);
        Enemy loopGuard = subject(loopSim);
        loopGuard.alert();
        loopSim.player.position.set(4.5f, 1.5f);
        PlayerInput hopInput = new PlayerInput();
        boolean reachedSafeWestFlank = false;
        boolean guardFiredAtPlayer = false;
        for (int i = 0; i < 60 * 10 && !loopSim.player.isDead(); i++) {
            loopGuard.alert();
            hopInput.jump = !loopSim.player.isAirborne();
            loopSim.update(DT, hopInput);
            assertFalse(loopSim.map.isHazardAt(loopGuard.getX(), loopGuard.getZ()),
                "detouring Guard stepped onto ~ grate: " + where(loopGuard));
            if (loopGuard.getX() < 3.8f && loopGuard.getZ() < 2.0f) {
                reachedSafeWestFlank = true;
            }
            if (!loopSim.enemyProjectiles.isEmpty()) {
                guardFiredAtPlayer = true;
                if (reachedSafeWestFlank) break;
            }
        }
        assertTrue(reachedSafeWestFlank,
            "detouring Guard never rounded safe west corridor to row 1: " + where(loopGuard));
        assertTrue(guardFiredAtPlayer, "detouring Guard never fired after rounding safe detour: " + where(loopGuard));
        assertEquals(Enemy.Kind.GUARD.maxHealth, loopGuard.getHealth(), 0f,
            "detouring Guard was zapped: " + where(loopGuard));
    }
}

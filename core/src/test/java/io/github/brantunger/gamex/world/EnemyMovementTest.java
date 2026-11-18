package io.github.brantunger.gamex.world;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.junit.jupiter.api.Assertions.fail;

import com.badlogic.gdx.math.Vector2;
import io.github.brantunger.gamex.GameSimulation;
import io.github.brantunger.gamex.player.Difficulty;
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

        // Its fan volleys reach the player from the alcove, so progress is measured by position, not by damage.
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
}

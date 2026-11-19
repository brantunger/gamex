package io.github.brantunger.gamex.world;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.badlogic.gdx.math.Vector2;
import com.badlogic.gdx.math.Vector3;
import io.github.brantunger.gamex.player.Difficulty;
import io.github.brantunger.gamex.player.Player;
import io.github.brantunger.gamex.weapon.EnemyProjectile;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;

class EnemyTest {

    private static final float DT = 1f / 60f;

    @Test
    void sightConeWakesForwardAndCloseProximityWakesBehindButNotFarBehind() {
        GameMap arena = GameMap.parse(
            "######################",
            "#....................#",
            "#>...................#",
            "#....................#",
            "######################");
        // Facing north (yaw = 0): player behind at (+z, dist 6) is outside the 200-deg cone and outside 3.2u sense.
        Enemy farBehind = new Enemy(Enemy.Kind.GUARD, 10.5f, 2.5f, 0f);
        Player southPlayer = new Player(10.5f, 8.5f, 0f);
        GameMap tallArena = GameMap.parse(
            "###########",
            "#.........#",
            "#....>....#",
            "#.........#",
            "#.........#",
            "#.........#",
            "#.........#",
            "#.........#",
            "#.........#",
            "#.........#",
            "###########");
        Pathfinder pf = new Pathfinder();
        farBehind = new Enemy(Enemy.Kind.GUARD, 5.5f, 2.5f, 0f);
        southPlayer = new Player(5.5f, 8.5f, 0f); // 6u south (directly behind yaw=0)
        farBehind.update(DT, tallArena, pf, southPlayer, Difficulty.NORMAL, Collections.emptyList(), new ArrayList<>());
        assertEquals(Enemy.State.DORMANT, farBehind.getState());

        // Player within close proximity (2.5u behind) wakes it immediately.
        Player closeBehind = new Player(5.5f, 5.0f, 0f);
        farBehind.update(DT, tallArena, pf, closeBehind, Difficulty.NORMAL, Collections.emptyList(), new ArrayList<>());
        assertEquals(Enemy.State.PURSUING, farBehind.getState());
        assertFalse(arena.isSolid(1, 1));
    }

    @Test
    void aggroMemoryExpiresAfterSixSecondsWithoutLineOfSight() {
        GameMap walled = GameMap.parse(
            "#########",
            "#>..#...#",
            "#########");
        Enemy guard = new Enemy(Enemy.Kind.GUARD, 6.5f, 1.5f, 270f);
        Player player = new Player(1.5f, 1.5f, 90f);
        guard.alert();
        assertEquals(Enemy.State.PURSUING, guard.getState());

        Pathfinder pf = new Pathfinder();
        pf.compute(walled, player.position.x, player.position.y);
        for (int i = 0; i < 300; i++) {
            guard.update(DT, walled, pf, player, Difficulty.NORMAL, Collections.emptyList(), new ArrayList<>());
        }
        assertEquals(Enemy.State.PURSUING, guard.getState(), "still within 6s memory window at 5s");

        for (int i = 0; i < 70; i++) {
            guard.update(DT, walled, pf, player, Difficulty.NORMAL, Collections.emptyList(), new ArrayList<>());
        }
        assertEquals(Enemy.State.DORMANT, guard.getState(), "returns to dormant after 6s without LOS");
    }

    @Test
    void turretSwivelsAtCappedRateAndFiresThreeBoltBurst() {
        GameMap hall = GameMap.parse(
            "#############",
            "#>..........#",
            "#...........#",
            "#...........#",
            "#############");
        // Turret initially faces north (0 deg); player is 180 deg behind (south) -> takes >1s at 150 deg/s to turn.
        Enemy turret = new Enemy(Enemy.Kind.TURRET, 6.5f, 1.5f, 0f);
        Player player = new Player(6.5f, 3.0f, 0f); // 1.5u south (within close sense 3.2u)
        List<EnemyProjectile> bolts = new ArrayList<>();
        Pathfinder pf = new Pathfinder();

        // Advance 0.5s: turret should have turned ~75 deg (to 75 or 285 on a 180-deg tie), not yet fired.
        for (int i = 0; i < 30; i++) {
            turret.update(DT, hall, pf, player, Difficulty.NORMAL, Collections.emptyList(), bolts);
        }
        float turnedFromNorth = Math.min(turret.getYaw(), 360f - turret.getYaw());
        assertTrue(turnedFromNorth > 70f && turnedFromNorth < 85f, "turned " + turnedFromNorth + " (yaw " + turret.getYaw() + ")");
        assertTrue(bolts.isEmpty(), "turret shouldn't fire before turning toward player");

        // Advance another 1.5s: finishes swivel, winds up 0.35s, and fires full 3-bolt burst.
        for (int i = 0; i < 90; i++) {
            turret.update(DT, hall, pf, player, Difficulty.NORMAL, Collections.emptyList(), bolts);
        }
        assertEquals(Enemy.TURRET_BURST_COUNT, bolts.size());
    }

    @Test
    void droneLungesIntoStationaryPlayerButWhiffsWhenPlayerDashesClear() {
        GameMap hall = GameMap.parse(
            "###########",
            "#.........#",
            "#>........#",
            "#.........#",
            "#.........#",
            "###########");
        // Stationary player hit test.
        Enemy drone = new Enemy(Enemy.Kind.DRONE, 5.5f, 2.5f, 270f);
        Player stationary = new Player(4.7f, 2.5f, 90f); // 0.8u apart (<= 0.95 trigger)
        Pathfinder pf = new Pathfinder();
        for (int i = 0; i < 35; i++) {
            drone.update(DT, hall, pf, stationary, Difficulty.NORMAL, Collections.emptyList(), null);
        }
        assertEquals(Player.MAX_HEALTH - Enemy.DRONE_LUNGE_DAMAGE, stationary.getHealth(), 1e-4f);
        assertEquals(Enemy.State.RECOVERING, drone.getState());

        // Dodged lunge test: wake drone, let it enter WINDUP, then player sidesteps south during wind-up.
        Enemy dodgeDrone = new Enemy(Enemy.Kind.DRONE, 5.5f, 2.5f, 270f);
        Player dodger = new Player(4.7f, 2.5f, 90f);
        for (int i = 0; i < 20 && dodgeDrone.getState() != Enemy.State.WINDUP; i++) {
            dodgeDrone.update(DT, hall, pf, dodger, Difficulty.NORMAL, Collections.emptyList(), null);
        }
        assertEquals(Enemy.State.WINDUP, dodgeDrone.getState());
        dodger.position.set(4.7f, 4.0f); // dashed 1.5u sideways
        for (int i = 0; i < 35; i++) {
            dodgeDrone.update(DT, hall, pf, dodger, Difficulty.NORMAL, Collections.emptyList(), null);
        }
        assertEquals(Player.MAX_HEALTH, dodger.getHealth(), 1e-4f);
        assertEquals(Enemy.State.RECOVERING, dodgeDrone.getState());
    }

    @Test
    void difficultyScalesAttackCadenceWhileKeepingHitPointsConstant() {
        assertEquals(Enemy.Kind.DRONE.maxHealth, 40f, 1e-6f);
        assertEquals(Enemy.Kind.TURRET.maxHealth, 80f, 1e-6f);
        assertEquals(Enemy.Kind.GUARD.maxHealth, 100f, 1e-6f);

        GameMap room = GameMap.parse(
            "#############",
            "#>..........#",
            "#############");
        Enemy easyGuard = new Enemy(Enemy.Kind.GUARD, 8.5f, 1.5f, 270f);
        Enemy hardGuard = new Enemy(Enemy.Kind.GUARD, 8.5f, 1.5f, 270f);
        Player player = new Player(1.5f, 1.5f, 90f);
        List<EnemyProjectile> easyBolts = new ArrayList<>();
        List<EnemyProjectile> hardBolts = new ArrayList<>();
        Pathfinder pf = new Pathfinder();

        // Run for 3.0 seconds.
        for (int i = 0; i < 180; i++) {
            easyGuard.update(DT, room, pf, player, Difficulty.EASY, Collections.emptyList(), easyBolts);
            hardGuard.update(DT, room, pf, player, Difficulty.HARD, Collections.emptyList(), hardBolts);
        }
        assertTrue(hardBolts.size() > easyBolts.size(),
            "hard (" + hardBolts.size() + ") should fire more often than easy (" + easyBolts.size() + ")");
    }

    @Test
    void wardenTransitionsIntoOverdriveAtHalfHealthFiringFiveBoltFansAndChargeSlams() {
        assertEquals(600f, Enemy.Kind.WARDEN.maxHealth, 1e-6f);
        GameMap arena = GameMap.parse(
            "###############",
            "#>............#",
            "#.............#",
            "###############");
        Enemy warden = new Enemy(Enemy.Kind.WARDEN, 9.5f, 1.5f, 270f);
        Player player = new Player(1.5f, 1.5f, 90f);
        List<EnemyProjectile> bolts = new ArrayList<>();
        Pathfinder pf = new Pathfinder();

        // Phase 1 (> 50% HP): first volley spawns a 3-bolt fan.
        assertFalse(warden.isOverdrive());
        for (int i = 0; i < 60 && bolts.isEmpty(); i++) {
            warden.update(DT, arena, pf, player, Difficulty.NORMAL, Collections.emptyList(), bolts);
        }
        assertEquals(Enemy.WARDEN_FAN_PHASE1, bolts.size(), "Phase 1 volley must emit 3 bolts");
        assertFalse(warden.consumeOverdriveTriggered());

        // Dropping health to <= 50% (300 HP) triggers Overdrive once and upgrades next volley to a 5-bolt fan.
        warden.damage(310f);
        assertTrue(warden.isOverdrive());
        assertTrue(warden.consumeOverdriveTriggered());
        assertFalse(warden.consumeOverdriveTriggered());

        bolts.clear();
        for (int i = 0; i < 120 && bolts.isEmpty(); i++) {
            warden.update(DT, arena, pf, player, Difficulty.NORMAL, Collections.emptyList(), bolts);
        }
        assertEquals(Enemy.WARDEN_FAN_PHASE2, bolts.size(), "Phase 2 Overdrive volley must emit 5 bolts");

        // At close range (<= WARDEN_SLAM_TRIGGER_RANGE), Warden winds up and deals a hydraulic charge slam.
        Enemy slamWarden = new Enemy(Enemy.Kind.WARDEN, 4.0f, 1.5f, 270f);
        Player closeTarget = new Player(2.5f, 1.5f, 90f);
        for (int i = 0; i < 65; i++) {
            slamWarden.update(DT, arena, pf, closeTarget, Difficulty.NORMAL, Collections.emptyList(), null);
        }
        assertTrue(closeTarget.getHealth() < Player.MAX_HEALTH, "Charge slam should hit close stationary target");
    }

    @Test
    void droneAndWardenLungeInNarrowCorridorNeverStayClippedInsidePlayer() {
        GameMap corridor = GameMap.parse(
            "###",
            "#>#",
            "#.#",
            "#.#",
            "#.#",
            "#.#",
            "#.#",
            "###");
        Pathfinder pf = new Pathfinder();

        // Drone lunges south in a 1.0u-wide corridor while player hugs the west wall so direct east radial pushback
        // hits the east wall (x + 0.21 > 2.0).
        Enemy drone = new Enemy(Enemy.Kind.DRONE, 1.58f, 3.20f, 180f);
        Player player = new Player(1.58f, 4.00f, 0f);
        for (int i = 0; i < 30 && drone.getState() != Enemy.State.WINDUP; i++) {
            drone.update(DT, corridor, pf, player, Difficulty.NORMAL, Collections.emptyList(), null);
        }
        assertEquals(Enemy.State.WINDUP, drone.getState());
        player.position.set(1.40f, 3.60f);
        for (int i = 0; i < 35 && drone.getState() != Enemy.State.RECOVERING; i++) {
            drone.update(DT, corridor, pf, player, Difficulty.NORMAL, Collections.emptyList(), null);
        }
        assertEquals(Enemy.State.RECOVERING, drone.getState());
        assertTrue(player.getHealth() < Player.MAX_HEALTH);
        float droneDist = Vector2.dst(drone.getX(), drone.getZ(), player.position.x, player.position.y);
        assertTrue(droneDist >= Player.RADIUS + drone.getRadius() - 1e-4f,
            "drone stayed clipped inside player at dist=" + droneDist);
        assertFalse(Collision.overlapsWall(corridor, Collections.emptyList(),
            drone.getX(), drone.getZ(), drone.getRadius()), "drone pushed into corridor wall");

        // Warden charge slam in the same 1.0u-wide corridor when direct radial pushback hits the east wall.
        Enemy warden = new Enemy(Enemy.Kind.WARDEN, 1.55f, 2.50f, 180f);
        Player wPlayer = new Player(1.55f, 3.80f, 0f);
        for (int i = 0; i < 60 && warden.getState() != Enemy.State.LUNGING; i++) {
            warden.update(DT, corridor, pf, wPlayer, Difficulty.NORMAL, Collections.emptyList(), null);
        }
        assertEquals(Enemy.State.LUNGING, warden.getState());
        wPlayer.position.set(1.30f, warden.getZ() + 0.20f);
        for (int i = 0; i < 30 && warden.getState() != Enemy.State.RECOVERING; i++) {
            warden.update(DT, corridor, pf, wPlayer, Difficulty.NORMAL, Collections.emptyList(), null);
        }
        assertEquals(Enemy.State.RECOVERING, warden.getState());
        assertTrue(wPlayer.getHealth() < Player.MAX_HEALTH);
        float wardenDist = Vector2.dst(warden.getX(), warden.getZ(), wPlayer.position.x, wPlayer.position.y);
        assertTrue(wardenDist >= Player.RADIUS + warden.getRadius() - 1e-4f,
            "warden stayed clipped inside player at dist=" + wardenDist);
        assertFalse(Collision.overlapsWall(corridor, Collections.emptyList(),
            warden.getX(), warden.getZ(), warden.getRadius()), "warden pushed into corridor wall");
    }

    @Test
    void lungeIntoCornerBesideSolidPropsAndCoincidentCenterSeparatesCleanly() {
        GameMap room = GameMap.parse(
            "#####",
            "#>..#",
            "#...#",
            "#...#",
            "#####");
        Pathfinder pf = new Pathfinder();

        // Drone lunges west into NW corner onto coincident player center while props block both South and East retract.
        Enemy drone = new Enemy(Enemy.Kind.DRONE, 2.00f, 1.26f, 270f);
        Player player = new Player(1.26f, 1.26f, 90f);
        for (int i = 0; i < 40 && drone.getState() != Enemy.State.LUNGING; i++) {
            drone.update(DT, room, pf, player, Difficulty.NORMAL, Collections.emptyList(), null);
        }
        assertEquals(Enemy.State.LUNGING, drone.getState());
        player.position.set(1.58f, 1.26f);
        Target canister = new Target(Target.Kind.CANISTER, 1.26f, 1.80f);
        Enemy turret = new Enemy(Enemy.Kind.TURRET, 2.18f, 1.26f, 270f);
        List<Damageable> blockers = Arrays.asList(canister, turret);
        float exactCoincidentDt = (drone.getX() - player.position.x) / Enemy.DRONE_LUNGE_SPEED;
        drone.update(Math.max(1e-3f, exactCoincidentDt), room, pf, player, Difficulty.NORMAL, blockers, null);
        assertEquals(Enemy.State.RECOVERING, drone.getState());
        float droneDist = Vector2.dst(drone.getX(), drone.getZ(), player.position.x, player.position.y);
        assertTrue(droneDist >= Player.RADIUS + drone.getRadius() - 1e-4f,
            "coincident corner drone stayed inside player at dist=" + droneDist);
        assertFalse(Collision.overlapsWall(room, Collections.emptyList(),
            drone.getX(), drone.getZ(), drone.getRadius()));
        assertFalse(Collision.overlapsSolid(blockers, new Vector2(drone.getX(), drone.getZ()), drone.getRadius()));

        // Warden lunges diagonally into NW corner while a SecurityConsole blocks direct radial pushback.
        Enemy warden = new Enemy(Enemy.Kind.WARDEN, 1.95f, 1.95f, 315f);
        Player wPlayer = new Player(1.32f, 1.32f, 135f);
        SecurityConsole console = new SecurityConsole(2.05f, 1.32f);
        List<Damageable> wBlockers = Collections.singletonList(console);
        for (int i = 0; i < 60 && warden.getState() != Enemy.State.LUNGING; i++) {
            warden.update(DT, room, pf, wPlayer, Difficulty.NORMAL, wBlockers, null);
        }
        assertEquals(Enemy.State.LUNGING, warden.getState());
        wPlayer.position.set(1.45f, 1.56f);
        warden.update(0.06f, room, pf, wPlayer, Difficulty.NORMAL, wBlockers, null);
        assertEquals(Enemy.State.RECOVERING, warden.getState());
        float wardenDist = Vector2.dst(warden.getX(), warden.getZ(), wPlayer.position.x, wPlayer.position.y);
        assertTrue(wardenDist >= Player.RADIUS + warden.getRadius() - 1e-4f,
            "corner warden stayed inside player at dist=" + wardenDist);
        assertFalse(Collision.overlapsWall(room, Collections.emptyList(),
            warden.getX(), warden.getZ(), warden.getRadius()));
        assertFalse(Collision.overlapsSolid(wBlockers, new Vector2(warden.getX(), warden.getZ()), warden.getRadius()));
    }

    @Test
    void wardenAcrossBlockingPropWithinStopRangeSidestepsAndFiresVolleysInsteadOfBlockedSlams() {
        GameMap arena = GameMap.parse(
            "#########",
            "#.......#",
            "#>......#",
            "#.......#",
            "#########");
        assertWardenClearsBlockingProp(arena, new SecurityConsole(4.50f, 2.50f));
        assertWardenClearsBlockingProp(arena, new Target(Target.Kind.CANISTER, 4.50f, 2.50f));
        assertWardenClearsBlockingProp(arena, new Enemy(Enemy.Kind.TURRET, 4.50f, 2.50f, 90f));

        // Unobstructed close-range slam (even with a SecurityConsole behind the player) still triggers immediately.
        Enemy unobstructed = new Enemy(Enemy.Kind.WARDEN, 3.85f, 2.50f, 90f);
        Player openPlayer = new Player(5.00f, 2.50f, 270f);
        SecurityConsole behindPlayer = new SecurityConsole(6.00f, 2.50f);
        List<EnemyProjectile> openBolts = new ArrayList<>();
        Pathfinder pf = new Pathfinder();
        boolean slammed = false;
        for (int i = 0; i < 65; i++) {
            unobstructed.update(DT, arena, pf, openPlayer, Difficulty.NORMAL,
                Collections.singletonList(behindPlayer), openBolts);
            if (unobstructed.consumeSlamTriggered()) {
                slammed = true;
            }
        }
        assertTrue(slammed, "Unobstructed close-range Warden must still trigger charge slam");
        assertTrue(openPlayer.getHealth() < Player.MAX_HEALTH, "Unobstructed slam must damage player");
    }

    private static void assertWardenClearsBlockingProp(GameMap arena, Damageable prop) {
        // Warden (3.95, 2.50) and Player (5.00, 2.50) start at dist = 1.05 <= 1.35 across prop at (4.50, 2.50).
        Enemy warden = new Enemy(Enemy.Kind.WARDEN, 3.95f, 2.50f, 90f);
        Player player = new Player(5.00f, 2.50f, 270f);
        List<Damageable> blockers = Collections.singletonList(prop);
        List<EnemyProjectile> bolts = new ArrayList<>();
        Pathfinder pf = new Pathfinder();
        pf.compute(arena, player.position.x, player.position.y);

        boolean sidesteppedAroundProp = false;
        for (int i = 0; i < 240; i++) {
            warden.update(DT, arena, pf, player, Difficulty.NORMAL, blockers, bolts);
            if (warden.getX() < 4.15f && Math.abs(warden.getZ() - 2.50f) < 0.25f) {
                assertFalse(warden.consumeSlamTriggered(),
                    "Warden must not launch blocked 0-damage slam straight into " + prop.getClass().getSimpleName());
            } else {
                warden.consumeSlamTriggered();
            }
            if (warden.getX() > 4.30f && Math.abs(warden.getZ() - 2.50f) > 0.25f) {
                sidesteppedAroundProp = true;
            }
        }
        assertTrue(bolts.size() >= Enemy.WARDEN_FAN_PHASE1,
            "Warden should fire plasma fan volley when slam path is blocked by " + prop.getClass().getSimpleName());
        assertTrue(sidesteppedAroundProp,
            "Warden must sidestep around " + prop.getClass().getSimpleName() + " instead of freezing at dist <= 1.35");
        assertTrue(player.getHealth() < Player.MAX_HEALTH,
            "Warden must reach and damage player after rounding " + prop.getClass().getSimpleName());
    }

    @Test
    void stepRoundSolidsRecoversAfterTransientSquadmateOrDoorBlockage() {
        GameMap hall = GameMap.parse(
            "#########",
            "#.......#",
            "#>......#",
            "#########");
        Pathfinder pf = new Pathfinder();
        Player player = new Player(7.50f, 2.50f, 270f);
        pf.compute(hall, player.position.x, player.position.y);

        // 1. Transient squadmate blockage (> 2 frames): Canister against south wall, squadmate blocking north gap.
        Target canister = new Target(Target.Kind.CANISTER, 4.50f, 2.50f);
        Enemy drone = new Enemy(Enemy.Kind.DRONE, 4.05f, 2.50f, 90f);
        Enemy squadmate = new Enemy(Enemy.Kind.DRONE, 4.15f, 2.04f, 270f);
        List<Damageable> blockers = Arrays.asList(canister, drone, squadmate);
        drone.alert();

        for (int i = 0; i < 20; i++) {
            drone.update(DT, hall, pf, player, Difficulty.NORMAL, blockers, null);
        }
        float restX = drone.getX();
        float restZ = drone.getZ();
        // Hold transient blockage for 25 more frames (> 2 frames): must remain jitter-free while blocked.
        for (int i = 0; i < 25; i++) {
            drone.update(DT, hall, pf, player, Difficulty.NORMAL, blockers, null);
            assertEquals(0f, Vector2.dst(restX, restZ, drone.getX(), drone.getZ()), 1e-4f,
                "drone jittered while both sides were blocked");
        }
        assertTrue(drone.getX() < 4.40f);

        // Move squadmate away, then verify drone unlatches sidestepGaveUp and walks around canister to hit player.
        Player westLure = new Player(1.50f, 1.50f, 90f);
        squadmate.alert();
        for (int i = 0; i < 35; i++) {
            squadmate.update(DT, hall, null, westLure, Difficulty.NORMAL, blockers, null);
        }
        for (int i = 0; i < 120 && player.getHealth() == Player.MAX_HEALTH; i++) {
            drone.update(DT, hall, pf, player, Difficulty.NORMAL, blockers, null);
        }
        assertTrue(drone.getX() > 5.0f, "drone did not walk around canister after squadmate left");
        assertTrue(player.getHealth() < Player.MAX_HEALTH, "drone should reach and damage player");

        // 2. Transient closed bulkhead door blockage (> 2 frames), followed by door opening.
        GameMap doorHall = GameMap.parse(
            "#######",
            "###.###",
            "##=.###",
            "##..###",
            "###>###",
            "#######");
        Door door = new Door(Door.LockKind.NONE, Door.Orientation.SPAN_X, 2, 2);
        List<Door> doors = Collections.singletonList(door);
        Target doorCanister = new Target(Target.Kind.CANISTER, 3.50f, 2.75f);
        List<Damageable> doorBlockers = Collections.singletonList(doorCanister);
        Enemy doorDrone = new Enemy(Enemy.Kind.DRONE, 3.50f, 1.50f, 180f);
        Player southPlayer = new Player(3.50f, 4.50f, 0f);
        pf.compute(doorHall, doors, southPlayer.position.x, southPlayer.position.y);
        doorDrone.alert();

        for (int i = 0; i < 60; i++) {
            doorDrone.update(DT, doorHall, pf, southPlayer, Difficulty.NORMAL, doorBlockers, doors, null);
        }
        float doorRestX = doorDrone.getX();
        float doorRestZ = doorDrone.getZ();
        for (int i = 0; i < 30; i++) {
            doorDrone.update(DT, doorHall, pf, southPlayer, Difficulty.NORMAL, doorBlockers, doors, null);
            assertEquals(0f, Vector2.dst(doorRestX, doorRestZ, doorDrone.getX(), doorDrone.getZ()), 1e-4f,
                "drone jittered while door kept bypass closed");
        }
        assertTrue(doorDrone.getZ() < 2.75f);

        door.open();
        for (int i = 0; i < 150 && southPlayer.getHealth() == Player.MAX_HEALTH; i++) {
            door.update(DT);
            pf.compute(doorHall, doors, southPlayer.position.x, southPlayer.position.y);
            doorDrone.update(DT, doorHall, pf, southPlayer, Difficulty.NORMAL, doorBlockers, doors, null);
        }
        assertTrue(doorDrone.getZ() > 3.0f, "drone did not round canister after bulkhead opened");
        assertTrue(southPlayer.getHealth() < Player.MAX_HEALTH, "drone should reach and damage player once door opens");
    }

    @Test
    void guardObliqueStrafingAndObstacleSlideNeverStepCenterOntoShockGrate() {
        // 1. Oblique approach across a ~ grate corner (matching Stage 06 ~~ geometry) where a single 0.49u probe
        // overshoots into a safe cell while frame steps would clip the ~ corner.
        GameMap arena = GameMap.parse(
            "################",
            "#..............#",
            "#..............#",
            "#..........~~..#",
            "#>.............#",
            "#..........~~..#",
            "#..............#",
            "#..............#",
            "#..............#",
            "#..............#",
            "#..............#",
            "#..............#",
            "#..............#",
            "################");
        Enemy guard = new Enemy(Enemy.Kind.GUARD, 13.10f, 3.80f, 210f);
        Player player = new Player(11.61f, 11.66f, 0f);
        Pathfinder pf = new Pathfinder();
        pf.compute(arena, Collections.emptyList(), true, player.position.x, player.position.y);
        guard.alert();

        for (int i = 0; i < 240; i++) {
            guard.update(DT, arena, pf, player, Difficulty.NORMAL, Collections.emptyList(), new ArrayList<>());
            assertFalse(arena.isHazardAt(guard.getX(), guard.getZ()),
                "oblique Guard clipped shock grate corner on frame " + i + " at (" + guard.getX() + ", " + guard.getZ() + ")");
        }

        // 2. Out-of-sight Guard meeting an off-axis canister right beside a ~ shock grate must neither pushOut-slide
        // nor sidestep onto the ~ cell, rounding the safe north side instead.
        GameMap hall = GameMap.parse(
            "##########",
            "#........#",
            "#........#",
            "#...~#...#",
            "#....#..>#",
            "##########");
        Target canister = new Target(Target.Kind.CANISTER, 4.50f, 2.68f);
        List<Damageable> blockers = Collections.singletonList(canister);
        Enemy pathGuard = new Enemy(Enemy.Kind.GUARD, 2.50f, 2.82f, 90f);
        Player hiddenPlayer = new Player(8.50f, 4.50f, 270f);
        pf.compute(hall, Collections.emptyList(), true, hiddenPlayer.position.x, hiddenPlayer.position.y);
        pathGuard.alert();

        float maxX = pathGuard.getX();
        for (int i = 0; i < 240; i++) {
            pathGuard.update(DT, hall, pf, hiddenPlayer, Difficulty.NORMAL, blockers, new ArrayList<>());
            assertFalse(hall.isHazardAt(pathGuard.getX(), pathGuard.getZ()),
                "Guard slid or sidestepped onto ~ grate beside canister at (" + pathGuard.getX() + ", " + pathGuard.getZ() + ")");
            maxX = Math.max(maxX, pathGuard.getX());
        }
        assertTrue(maxX > 5.5f, "Guard should round the safe north side of the canister, maxX=" + maxX);
    }

    @Test
    void turretEnforcesBarrelYawAndPitchLimitsAndHoldsAimWhenLosBreaksMidBurst() {
        GameMap open = GameMap.parse(
            "###########",
            "#.........#",
            "#.........#",
            "#.........#",
            "#.........#",
            "#>........#",
            "#.........#",
            "#.........#",
            "#.........#",
            "###########");
        Pathfinder pf = new Pathfinder();

        // 1. Outflanking 180 deg behind Turret right before WINDUP completes: bolts fire along capped barrel yaw, not backwards.
        Enemy turret = new Enemy(Enemy.Kind.TURRET, 5.5f, 5.5f, 270f);
        Player flanker = new Player(4.0f, 5.5f, 90f);
        List<EnemyProjectile> bolts = new ArrayList<>();
        for (int i = 0; i < 60 && !(turret.getState() == Enemy.State.WINDUP && turret.getWindupProgress() > 0.85f); i++) {
            turret.update(DT, open, pf, flanker, Difficulty.NORMAL, Collections.emptyList(), bolts);
        }
        assertEquals(Enemy.State.WINDUP, turret.getState());
        assertTrue(bolts.isEmpty());

        flanker.position.set(7.0f, 5.5f); // dashed 180 deg behind turret (+X)
        List<Float> firingYaws = new ArrayList<>();
        for (int i = 0; i < 30 && bolts.size() < Enemy.TURRET_BURST_COUNT; i++) {
            int before = bolts.size();
            turret.update(DT, open, pf, flanker, Difficulty.NORMAL, Collections.emptyList(), bolts);
            for (int b = before; b < bolts.size(); b++) {
                firingYaws.add(turret.getYaw());
            }
        }
        assertEquals(Enemy.TURRET_BURST_COUNT, bolts.size());
        for (int b = 0; b < bolts.size(); b++) {
            Vector3 dir = measureBoltDirection(bolts.get(b), open);
            assertTrue(dir.x < -0.2f, "outflanked turret bolt shot backwards toward +X: " + dir);
            float boltYaw = ((float) Math.toDegrees(Math.atan2(dir.x, -dir.z)) + 360f) % 360f;
            float diff = ((boltYaw - firingYaws.get(b)) % 360f + 540f) % 360f - 180f;
            assertTrue(Math.abs(diff) <= 1.0f, "bolt yaw " + boltYaw + " diverged from barrel yaw " + firingYaws.get(b));
        }
        for (EnemyProjectile bolt : bolts) {
            for (int step = 0; step < 30 && !bolt.isFinished(); step++) {
                bolt.update(DT, open, flanker, 1f, Collections.emptyList(), Collections.emptyList(), null);
            }
        }
        assertEquals(Player.MAX_HEALTH, flanker.getHealth(), 1e-4f, "outflanked burst must miss player behind turret");

        // 2. Steep close-range jump (> 28 deg above muzzle): vertical bolt pitch stays clamped within [-28, +28] deg.
        Enemy pitchTurret = new Enemy(Enemy.Kind.TURRET, 5.5f, 5.5f, 270f);
        Player jumper = new Player(5.12f, 5.5f, 90f);
        assertTrue(jumper.tryJump());
        while (jumper.getVerticalVelocity() > 0f) {
            jumper.move(open, 0f, 0f, DT);
        }
        assertTrue(jumper.getJumpHeight() > 0.28f);
        List<EnemyProjectile> steepBolts = new ArrayList<>();
        for (int i = 0; i < 60 && steepBolts.size() < Enemy.TURRET_BURST_COUNT; i++) {
            pitchTurret.update(DT, open, pf, jumper, Difficulty.NORMAL, Collections.emptyList(), steepBolts);
        }
        assertEquals(Enemy.TURRET_BURST_COUNT, steepBolts.size());
        for (EnemyProjectile bolt : steepBolts) {
            Vector3 dir = measureBoltDirection(bolt, open);
            float pitchDeg = (float) Math.toDegrees(Math.asin(Math.max(-1f, Math.min(1f, dir.y))));
            assertTrue(pitchDeg <= 28.05f && pitchDeg >= -28.05f,
                "turret bolt exceeded 28-deg barrel elevation limit: " + pitchDeg);
        }

        // 3. Breaking LOS behind a pillar after burst shot 1: shots 2 & 3 do not track unseen player coordinates.
        GameMap pillared = GameMap.parse(
            "#########",
            "#.......#",
            "#.......#",
            "#>......#",
            "#...#...#",
            "#.......#",
            "#########");
        Enemy losTurret = new Enemy(Enemy.Kind.TURRET, 6.5f, 3.5f, 270f);
        Player peeker = new Player(3.5f, 3.5f, 90f);
        List<EnemyProjectile> burstBolts = new ArrayList<>();
        for (int i = 0; i < 60 && burstBolts.isEmpty(); i++) {
            losTurret.update(DT, pillared, pf, peeker, Difficulty.NORMAL, Collections.emptyList(), burstBolts);
        }
        assertEquals(1, burstBolts.size());
        float lockedYaw = losTurret.getYaw();
        float lockedPitch = losTurret.getAimPitch();

        // Duck south behind pillar (4, 4) so LOS is blocked, and jump.
        peeker.position.set(3.5f, 5.5f);
        assertFalse(Hitscan.hasLineOfSight(pillared, Collections.emptyList(),
            losTurret.getX(), losTurret.getZ(), peeker.position.x, peeker.position.y));
        assertTrue(peeker.tryJump());
        while (peeker.getVerticalVelocity() > 0f) {
            peeker.move(pillared, 0f, 0f, DT);
        }

        for (int i = 0; i < 25 && burstBolts.size() < Enemy.TURRET_BURST_COUNT; i++) {
            losTurret.update(DT, pillared, pf, peeker, Difficulty.NORMAL, Collections.emptyList(), burstBolts);
        }
        assertEquals(Enemy.TURRET_BURST_COUNT, burstBolts.size());
        assertEquals(lockedYaw, losTurret.getYaw(), 1e-4f, "turret tracked unseen player yaw behind cover");
        assertEquals(lockedPitch, losTurret.getAimPitch(), 1e-4f, "turret tracked unseen player pitch behind cover");
        for (int b = 1; b < burstBolts.size(); b++) {
            Vector3 dir = measureBoltDirection(burstBolts.get(b), pillared);
            assertTrue(dir.x < -0.99f && Math.abs(dir.z) < 0.02f && Math.abs(dir.y) < 0.02f,
                "burst bolt " + b + " tracked unseen player behind pillar: " + dir);
        }
    }

    @Test
    void wardenSlamLaunchUsesRateLimitedYawWhenPlayerDashesBehindDuringTelegraph() {
        GameMap arena = GameMap.parse(
            "###########",
            "#.........#",
            "#.........#",
            "#.........#",
            "#.........#",
            "#>........#",
            "#.........#",
            "#.........#",
            "#.........#",
            "###########");
        Pathfinder pf = new Pathfinder();
        Enemy warden = new Enemy(Enemy.Kind.WARDEN, 5.5f, 5.5f, 270f);
        Player player = new Player(4.1f, 5.5f, 90f);

        for (int i = 0; i < 60 && !(warden.getState() == Enemy.State.WINDUP
            && warden.isSlamAttack() && warden.getWindupProgress() >= 0.5f); i++) {
            warden.update(DT, arena, pf, player, Difficulty.NORMAL, Collections.emptyList(), null);
        }
        assertEquals(Enemy.State.WINDUP, warden.getState());
        assertTrue(warden.isSlamAttack());

        // Dash 180 deg behind the Warden halfway through the 0.42s telegraph.
        player.position.set(7.2f, 5.5f);
        float preLaunchX = warden.getX();
        for (int i = 0; i < 45 && warden.getState() != Enemy.State.RECOVERING; i++) {
            warden.update(DT, arena, pf, player, Difficulty.NORMAL, Collections.emptyList(), null);
        }
        assertEquals(Enemy.State.RECOVERING, warden.getState());
        assertTrue(warden.getX() < preLaunchX - 0.5f,
            "Warden snapped backwards on slam launch instead of launching along rate-limited yaw: x=" + warden.getX());
        assertEquals(Player.MAX_HEALTH, player.getHealth(), 1e-4f,
            "Player who dashed behind Warden during slam telegraph must dodge cleanly");
    }

    @Test
    void guardAndWardenRequireForwardArcBeforeWindupAndClampVolleysWhenFlanked() {
        GameMap arena = GameMap.parse(
            "#############",
            "#...........#",
            "#...........#",
            "#...........#",
            "#...........#",
            "#>..........#",
            "#...........#",
            "#...........#",
            "#...........#",
            "#...........#",
            "#############");
        Pathfinder pf = new Pathfinder();

        // 1. Woken from 180 deg behind: after WAKE_COOLDOWN (0.25s -> 18 frames = 0.30s), neither Guard nor Warden
        // has turned within the forward firing arc yet, so neither may initiate WINDUP backwards.
        Enemy rearGuard = new Enemy(Enemy.Kind.GUARD, 5.5f, 5.5f, 0f);
        Enemy rearWarden = new Enemy(Enemy.Kind.WARDEN, 5.5f, 5.5f, 0f);
        Player behindPlayer = new Player(5.5f, 8.3f, 0f);
        List<EnemyProjectile> rearGuardBolts = new ArrayList<>();
        List<EnemyProjectile> rearWardenBolts = new ArrayList<>();

        for (int i = 0; i < 18; i++) {
            rearGuard.update(DT, arena, pf, behindPlayer, Difficulty.NORMAL, Collections.emptyList(), rearGuardBolts);
            rearWarden.update(DT, arena, pf, behindPlayer, Difficulty.NORMAL, Collections.emptyList(), rearWardenBolts);
        }
        assertEquals(Enemy.State.PURSUING, rearGuard.getState(), "Guard started WINDUP while still facing away");
        assertEquals(Enemy.State.PURSUING, rearWarden.getState(), "Warden started WINDUP while still facing away");
        assertTrue(rearGuardBolts.isEmpty());
        assertTrue(rearWardenBolts.isEmpty());

        // Once they finish turning into their forward firing arc, both enter WINDUP and fire cleanly.
        for (int i = 0; i < 65; i++) {
            rearGuard.update(DT, arena, pf, behindPlayer, Difficulty.NORMAL, Collections.emptyList(), rearGuardBolts);
            rearWarden.update(DT, arena, pf, behindPlayer, Difficulty.NORMAL, Collections.emptyList(), rearWardenBolts);
        }
        assertFalse(rearGuardBolts.isEmpty(), "Guard should fire after turning to face player");
        assertEquals(Enemy.WARDEN_FAN_PHASE1, rearWardenBolts.size(), "Warden should fire volley after turning to face player");

        // 2. Flanking 180 deg behind right before ranged WINDUP completes: emitted bolts stay clamped to forward arc.
        Enemy flankedGuard = new Enemy(Enemy.Kind.GUARD, 5.5f, 5.5f, 90f);
        Enemy flankedWarden = new Enemy(Enemy.Kind.WARDEN, 5.5f, 5.5f, 90f);
        Player runner = new Player(9.0f, 5.5f, 270f);
        List<EnemyProjectile> guardVolley = new ArrayList<>();
        List<EnemyProjectile> wardenVolley = new ArrayList<>();

        for (int i = 0; i < 60 && !(flankedGuard.getState() == Enemy.State.WINDUP
            && flankedGuard.getWindupProgress() > 0.90f); i++) {
            flankedGuard.update(DT, arena, pf, runner, Difficulty.NORMAL, Collections.emptyList(), guardVolley);
        }
        for (int i = 0; i < 60 && !(flankedWarden.getState() == Enemy.State.WINDUP
            && flankedWarden.getWindupProgress() > 0.92f); i++) {
            flankedWarden.update(DT, arena, pf, runner, Difficulty.NORMAL, Collections.emptyList(), wardenVolley);
        }
        assertTrue(guardVolley.isEmpty());
        assertTrue(wardenVolley.isEmpty());

        runner.position.set(3.5f, 5.5f); // teleported 180 deg behind (-X) while both face +X (yaw ~ 90)
        for (int i = 0; i < 10 && guardVolley.isEmpty(); i++) {
            flankedGuard.update(DT, arena, pf, runner, Difficulty.NORMAL, Collections.emptyList(), guardVolley);
        }
        for (int i = 0; i < 10 && wardenVolley.isEmpty(); i++) {
            flankedWarden.update(DT, arena, pf, runner, Difficulty.NORMAL, Collections.emptyList(), wardenVolley);
        }
        assertEquals(1, guardVolley.size());
        assertEquals(Enemy.WARDEN_FAN_PHASE1, wardenVolley.size());

        Vector3 guardDir = measureBoltDirection(guardVolley.get(0), arena);
        assertTrue(guardDir.x > 0.5f, "flanked Guard fired backwards through torso: " + guardDir);
        for (EnemyProjectile wBolt : wardenVolley) {
            Vector3 wDir = measureBoltDirection(wBolt, arena);
            assertTrue(wDir.x > 0.4f, "flanked Warden fired fan bolt backwards through torso: " + wDir);
        }
    }

    @Test
    void wallAndDoorHuggingEnemiesPullProtrudingMuzzlesClearSoBoltsFlyCleanly() {
        GameMap alcove = GameMap.parse(
            "#######",
            "##...##",
            "##...##",
            "##...##",
            "##...##",
            "#.....#",
            "#.....#",
            "#.....#",
            "#.....#",
            "#.....#",
            "#.....#",
            "#.....#",
            "#.>...#",
            "#.....#",
            "#######");
        Pathfinder pf = new Pathfinder();

        // 1. Wall-hugging Guard whose right weapon arm protrudes past radius=0.24 into < 2.075 beside wall col 1 (x = 2.0).
        Enemy wallGuard = new Enemy(Enemy.Kind.GUARD, 2.24f, 3.95f, 183f);
        Player guardTarget = new Player(1.85f, 12.50f, 0f);
        List<EnemyProjectile> guardBolts = new ArrayList<>();
        for (int i = 0; i < 60 && guardBolts.isEmpty(); i++) {
            wallGuard.update(DT, alcove, pf, guardTarget, Difficulty.NORMAL, Collections.emptyList(), guardBolts);
        }
        assertEquals(1, guardBolts.size());
        EnemyProjectile gBolt = guardBolts.get(0);
        assertFalse(Collision.overlapsWall(alcove, Collections.emptyList(),
            gBolt.position.x, gBolt.position.z, EnemyProjectile.RADIUS),
            "wall-hugging Guard bolt spawned overlapping adjacent wall at " + gBolt.position);
        for (int i = 0; i < 90 && !gBolt.isFinished(); i++) {
            gBolt.update(DT, alcove, guardTarget, 1f, Collections.emptyList(), Collections.emptyList(), null);
        }
        assertTrue(guardTarget.getHealth() < Player.MAX_HEALTH,
            "wall-hugging Guard bolt detonated on shoulder wall instead of reaching player");

        // 2. Wall-hugging Phase 2 Overdrive Warden whose right cannon protrudes past radius=0.32 into < 2.075 beside wall x = 2.0.
        Enemy wallWarden = new Enemy(Enemy.Kind.WARDEN, 2.32f, 4.20f, 196f);
        wallWarden.damage(310f);
        assertTrue(wallWarden.isOverdrive());
        Player wardenTarget = new Player(1.35f, 8.50f, 0f);
        List<EnemyProjectile> wardenBolts = new ArrayList<>();
        for (int i = 0; i < 60 && wardenBolts.isEmpty(); i++) {
            wallWarden.update(DT, alcove, null, wardenTarget, Difficulty.NORMAL, Collections.emptyList(), wardenBolts);
        }
        assertEquals(Enemy.WARDEN_FAN_PHASE2, wardenBolts.size());
        for (EnemyProjectile wBolt : wardenBolts) {
            assertFalse(Collision.overlapsWall(alcove, Collections.emptyList(),
                wBolt.position.x, wBolt.position.z, EnemyProjectile.RADIUS),
                "wall-hugging Warden bolt spawned overlapping adjacent wall at " + wBolt.position);
            for (int i = 0; i < 60 && !wBolt.isFinished(); i++) {
                wBolt.update(DT, alcove, wardenTarget, 1f, Collections.emptyList(), Collections.emptyList(), null);
            }
        }
        assertTrue(wardenTarget.getHealth() < Player.MAX_HEALTH,
            "wall-hugging Warden fan volley failed to reach player");

        // 3. Guard hugging closed SPAN_Z bulkhead door slabs (east face x = 2.59 across z in [2.0, 4.0]).
        GameMap doorAlcove = GameMap.parse(
            "#######",
            "#.#...#",
            "#.L...#",
            "#.L...#",
            "#.....#",
            "#.....#",
            "#.....#",
            "#.....#",
            "#.....#",
            "#.....#",
            "#.....#",
            "#.>...#",
            "#.....#",
            "#######");
        Door northDoor = new Door(Door.LockKind.LOCKDOWN, Door.Orientation.SPAN_Z, 2, 2);
        Door southDoor = new Door(Door.LockKind.LOCKDOWN, Door.Orientation.SPAN_Z, 2, 3);
        List<Door> doors = Arrays.asList(northDoor, southDoor);
        Enemy doorGuard = new Enemy(Enemy.Kind.GUARD, 2.83f, 3.05f, 183f);
        Player doorTarget = new Player(2.45f, 11.60f, 0f);
        List<EnemyProjectile> doorBolts = new ArrayList<>();
        for (int i = 0; i < 60 && doorBolts.isEmpty(); i++) {
            doorGuard.update(DT, doorAlcove, pf, doorTarget, Difficulty.NORMAL,
                Collections.emptyList(), doors, doorBolts);
        }
        assertEquals(1, doorBolts.size());
        EnemyProjectile dBolt = doorBolts.get(0);
        assertFalse(Collision.overlapsWall(doorAlcove, doors,
            dBolt.position.x, dBolt.position.z, EnemyProjectile.RADIUS));
        assertFalse(southDoor.overlapsSphere(
            dBolt.position.x, dBolt.position.y, dBolt.position.z, EnemyProjectile.RADIUS),
            "door-hugging Guard bolt spawned overlapping closed Door slab at " + dBolt.position);
        for (int i = 0; i < 90 && !dBolt.isFinished(); i++) {
            dBolt.update(DT, doorAlcove, doorTarget, 1f, Collections.emptyList(), Collections.emptyList(),
                doors, Collections.emptyList(), null);
        }
        assertTrue(doorTarget.getHealth() < Player.MAX_HEALTH,
            "door-hugging Guard bolt should clear closed door slab and hit player");
    }

    private static Vector3 measureBoltDirection(EnemyProjectile bolt, GameMap map) {
        EnemyProjectile probe = bolt;
        float x0 = probe.position.x;
        float y0 = probe.position.y;
        float z0 = probe.position.z;
        probe.update(1e-3f, map, null, 1f, Collections.emptyList(), Collections.emptyList(), null);
        assertFalse(probe.isFinished(), "bolt unexpectedly hit wall on 1ms direction probe from ("
            + x0 + ", " + y0 + ", " + z0 + ")");
        return new Vector3(probe.position.x - x0, probe.position.y - y0, probe.position.z - z0).nor();
    }
}

package io.github.brantunger.gamex.world;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import io.github.brantunger.gamex.player.Difficulty;
import io.github.brantunger.gamex.player.Player;
import io.github.brantunger.gamex.weapon.EnemyProjectile;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
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
}

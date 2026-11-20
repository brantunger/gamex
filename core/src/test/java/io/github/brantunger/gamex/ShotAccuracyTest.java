package io.github.brantunger.gamex;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import io.github.brantunger.gamex.player.Difficulty;
import io.github.brantunger.gamex.player.PlayerInput;
import io.github.brantunger.gamex.player.PlayerLoadout;
import io.github.brantunger.gamex.settings.RunStats;
import io.github.brantunger.gamex.weapon.WeaponSlot;
import io.github.brantunger.gamex.world.GameMap;
import org.junit.jupiter.api.Test;

/** Checks that every trigger pull is counted once as a shot and at most once as a hit. */
class ShotAccuracyTest {

    private static final float FRAME = 1f / 60f;

    private static GameSimulation corridor(String row) {
        String wall = "#".repeat(row.length());
        return TestTuning.simulation(GameMap.parse(wall, row, wall));
    }

    private static GameSimulation corridor(String row, PlayerLoadout loadout) {
        String wall = "#".repeat(row.length());
        return TestTuning.simulation(GameMap.parse(wall, row, wall), loadout, false);
    }

    private static void assertShots(int fired, int hit, GameSimulation sim, String message) {
        assertEquals(fired, sim.getShotsFired(), message + ": shots fired");
        assertEquals(hit, sim.getShotsHit(), message + ": shots hit");
    }

    /** Holds the blast button for 90 frames and lets go, so one blast leaves. */
    private static void releaseBlast(GameSimulation sim) {
        PlayerInput input = new PlayerInput();
        input.charge = true;
        for (int i = 0; i < 90; i++) sim.update(FRAME, input);
        input.charge = false;
        sim.update(FRAME, input);
        assertEquals(1, sim.projectiles.size(), "one blast in flight");
    }

    @Test
    void nothingIsCountedBeforeTheFirstShot() {
        GameSimulation sim = corridor("#>.o.....#");
        for (int i = 0; i < 30; i++) sim.update(FRAME, new PlayerInput());
        assertShots(0, 0, sim, "idle");
    }

    @Test
    void pistolShotCountsOnceAndHitsOnlyWhenItDamagesSomething() {
        GameSimulation atCanister = corridor("#>.o.....#");
        PlayerInput input = new PlayerInput();
        input.fire = true;
        atCanister.update(FRAME, input);
        assertShots(1, 1, atCanister, "pistol at a canister");
        // The trigger stays held: the second shot leaves 18 frames after the first.
        for (int i = 0; i < 20; i++) atCanister.update(FRAME, input);
        assertShots(2, 2, atCanister, "second pistol shot");

        GameSimulation atWall = corridor("#>.......#");
        atWall.update(FRAME, input);
        assertShots(1, 0, atWall, "pistol at a wall");

        GameSimulation atConsole = corridor("#>.S.....#");
        atConsole.update(FRAME, input);
        assertShots(1, 0, atConsole, "pistol at a security console");
    }

    @Test
    void chargedBlastIsAShotOnReleaseAndAHitOnlyOnceItExplodesOnSomething() {
        GameSimulation sim = corridor("#>.o.....#");
        releaseBlast(sim);
        assertShots(1, 0, sim, "blast in flight");

        for (int i = 0; i < 60; i++) sim.update(FRAME, new PlayerInput());
        assertTrue(sim.projectiles.isEmpty());
        assertTrue(sim.targets.get(0).isDestroyed());
        assertShots(1, 1, sim, "blast exploded on the canister");

        for (int i = 0; i < 60; i++) sim.update(FRAME, new PlayerInput());
        assertShots(1, 1, sim, "the hit is counted once");
    }

    @Test
    void chargedBlastThatOnlyFindsAWallIsAMiss() {
        GameSimulation sim = corridor("#>.......#");
        releaseBlast(sim);
        for (int i = 0; i < 120; i++) sim.update(FRAME, new PlayerInput());
        assertTrue(sim.projectiles.isEmpty(), "the blast has exploded");
        assertShots(1, 0, sim, "blast on a wall");
    }

    @Test
    void chargedBlastStillFlyingWhenTheStageClearsStaysAMiss() {
        GameSimulation sim = corridor("#>X.........o#");
        releaseBlast(sim);
        PlayerInput input = new PlayerInput();
        input.moveForward = 1f;
        for (int i = 0; i < 30 && !sim.isLevelCleared(); i++) sim.update(FRAME, input);
        assertTrue(sim.isLevelCleared(), "walked onto the exit pad");
        assertEquals(1, sim.projectiles.size(), "the blast has not landed");

        for (int i = 0; i < 240; i++) sim.update(FRAME, input);
        assertFalse(sim.targets.get(0).isDestroyed(), "nothing moves once the stage is cleared");
        assertShots(1, 0, sim, "blast in flight at the clear");
    }

    @Test
    void scattergunBlastCountsOnceHoweverManyPelletsLand() {
        GameSimulation sim = corridor("#.>..D....#",
            new PlayerLoadout(100f, 0f, Difficulty.NORMAL, true, 3, WeaponSlot.SCATTERGUN));
        PlayerInput input = new PlayerInput();
        input.fire = true;
        sim.update(FRAME, input);
        input.fire = false;
        assertTrue(sim.enemies.get(0).isDestroyed(), "several pellets landed: 40 health gone in one blast");
        assertShots(1, 1, sim, "single-shell blast into a drone");

        // The drone is gone, so the two-shell blast only finds the wall.
        for (int i = 0; i < 50; i++) sim.update(FRAME, input);
        input.charge = true;
        sim.update(FRAME, input);
        input.charge = false;
        assertTrue(sim.scattergun.isLastShotDoubleBlast());
        assertEquals(0, sim.getShells());
        assertShots(2, 1, sim, "two-shell blast into a wall");
    }

    @Test
    void railLanceBeamCountsOnceHoweverManyItPierces() {
        GameSimulation sim = corridor("#.>.D.o.G...#",
            new PlayerLoadout(100f, 0f, Difficulty.NORMAL, true, 6, true, 4, WeaponSlot.RAIL_LANCE));
        PlayerInput input = new PlayerInput();
        input.fire = true;
        sim.update(FRAME, input);
        input.fire = false;
        assertTrue(sim.enemies.get(0).isDestroyed());
        assertTrue(sim.enemies.get(1).isDestroyed());
        assertShots(1, 1, sim, "beam through a drone, a canister and a guard");

        GameSimulation atWall = corridor("#.>.........#",
            new PlayerLoadout(100f, 0f, Difficulty.NORMAL, true, 6, true, 4, WeaponSlot.RAIL_LANCE));
        input.fire = true;
        atWall.update(FRAME, input);
        assertShots(1, 0, atWall, "beam into a wall");
    }

    @Test
    void clearedRunHandsItsCountsToTheTally() {
        GameSimulation sim = TestTuning.simulation(GameMap.parse("########", "#>+.o..#", "########"),
            PlayerLoadout.fresh(), true);
        PlayerInput input = new PlayerInput();
        input.fire = true;
        for (int i = 0; i < 50 && !sim.isLevelCleared(); i++) sim.update(FRAME, input);
        assertTrue(sim.isLevelCleared());

        RunStats stats = MainGame.runStatsOf(sim);

        assertEquals(3, stats.shotsFired, "three pistol shots break a canister");
        assertEquals(3, stats.shotsHit);
        assertEquals(1, stats.destroyed, "a stage without hostiles counts its targets");
        assertEquals(1, stats.totalTargets);
        assertEquals(0, stats.pickupsCollected, "the medkit is still on the floor");
        assertEquals(1, stats.pickupsTotal);
        assertEquals(sim.getTime(), stats.clearTime, 1e-6f);
        assertEquals(sim.getDamageTaken(), stats.damageTaken, 1e-6f);
    }
}

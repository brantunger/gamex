package io.github.brantunger.gamex;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertTrue;

import io.github.brantunger.gamex.player.PlayerInput;
import io.github.brantunger.gamex.player.PlayerLoadout;
import io.github.brantunger.gamex.world.Door;
import io.github.brantunger.gamex.world.Enemy;
import io.github.brantunger.gamex.world.GameMap;
import io.github.brantunger.gamex.world.Levels;
import io.github.brantunger.gamex.world.Pickup;
import io.github.brantunger.gamex.world.SecurityConsole;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.EnumSource;

import java.util.List;

/** Checks the HUD's live objective line against the real stages, step by step. */
class ObjectiveTextTest {

    private static final float FRAME = 1f / 60f;

    private static GameSimulation play(Levels.Stage stage) {
        return play(stage, TestTuning.Balance.FROZEN);
    }

    private static GameSimulation play(Levels.Stage stage, TestTuning.Balance balance) {
        return balance.simulation(stage.map, PlayerLoadout.fresh(), stage.completeOnAllTargets);
    }

    /** Puts the player on the stage's keycard of that kind and lets one frame pick it up. */
    private static void collectKeycard(GameSimulation sim, Pickup.Kind kind) {
        for (Pickup pickup : sim.pickups) {
            if (pickup.getKind() != kind) continue;
            sim.player.position.set(pickup.getX(), pickup.getZ());
            sim.update(FRAME, new PlayerInput());
            assertTrue(sim.hasKeycard(kind), kind + " collected");
            return;
        }
        throw new AssertionError("the stage has no " + kind);
    }

    /** Stands the player just west of the stage's console, facing it, and presses the interact key. */
    private static void overrideConsole(GameSimulation sim) {
        SecurityConsole console = sim.consoles.get(0);
        sim.player.position.set(console.getX() - 0.9f, console.getZ());
        sim.player.look(90f - sim.player.getYaw(), 0f);
        PlayerInput input = new PlayerInput();
        input.interact = true;
        sim.update(FRAME, input);
        assertTrue(console.isActivated(), "console overridden");
    }

    private static void destroyWarden(GameSimulation sim) {
        for (Enemy enemy : sim.enemies) {
            if (enemy.getKind() == Enemy.Kind.WARDEN) enemy.damage(100000f);
        }
        sim.update(FRAME, new PlayerInput());
    }

    /** Turns the player to {@code yaw} and holds the move key, forwards or backwards, for that many frames. */
    private static void walk(GameSimulation sim, float yaw, float forward, int frames) {
        sim.player.look(yaw - sim.player.getYaw(), 0f);
        PlayerInput input = new PlayerInput();
        input.moveForward = forward;
        for (int i = 0; i < frames; i++) {
            sim.update(FRAME, input);
        }
    }

    private static boolean anyDoorOpening(GameSimulation sim, Door.LockKind kind) {
        for (Door door : sim.doors) {
            if (door.getLockKind() == kind && door.isOpening()) return true;
        }
        return false;
    }

    @Test
    void stagesWithAnOpenRouteOnlyAskForTheExit() {
        List<Levels.Stage> campaign = Levels.campaign();
        for (int i = 0; i < 3; i++) {
            assertEquals(GameSimulation.Objective.EXIT, play(campaign.get(i)).getObjective(),
                campaign.get(i).title);
        }
    }

    @Test
    void lockdownWingAsksForTheAmberKeycardThenTheConsoleThenTheExit() {
        GameSimulation sim = play(Levels.campaign().get(3));
        assertEquals("04_lockdown_wing", Levels.campaign().get(3).id);
        assertEquals(GameSimulation.Objective.AMBER_KEYCARD, sim.getObjective());

        collectKeycard(sim, Pickup.Kind.AMBER_KEYCARD);
        assertFalse(anyDoorOpening(sim, Door.LockKind.AMBER_KEY), "the Amber bulkhead is not opened yet");
        assertEquals(GameSimulation.Objective.CONSOLE, sim.getObjective(), "holding the card is enough");

        overrideConsole(sim);
        assertEquals(GameSimulation.Objective.EXIT, sim.getObjective());
    }

    @Test
    void commandVaultAsksForAmberThenCyanThenTheConsoleThenTheExit() {
        GameSimulation sim = play(Levels.campaign().get(4));
        assertEquals("05_command_vault", Levels.campaign().get(4).id);
        assertEquals(GameSimulation.Objective.AMBER_KEYCARD, sim.getObjective(),
            "the Cyan keycard lies behind the Amber bulkhead, so Amber comes first");

        collectKeycard(sim, Pickup.Kind.AMBER_KEYCARD);
        assertEquals(GameSimulation.Objective.CYAN_KEYCARD, sim.getObjective(),
            "the console lies behind the Cyan bulkhead, so Cyan comes before it");

        collectKeycard(sim, Pickup.Kind.CYAN_KEYCARD);
        assertEquals(GameSimulation.Objective.CONSOLE, sim.getObjective());

        overrideConsole(sim);
        assertEquals(GameSimulation.Objective.EXIT, sim.getObjective());
    }

    @ParameterizedTest(name = "{displayName} with {0}")
    @EnumSource(TestTuning.Balance.class)
    void wardenCoreAsksForAmberThenTheConsoleThenTheWardenThenTheExit(TestTuning.Balance balance) {
        GameSimulation sim = play(Levels.campaign().get(5), balance);
        assertEquals("06_warden_core", Levels.campaign().get(5).id);
        assertEquals(GameSimulation.Objective.AMBER_KEYCARD, sim.getObjective(),
            "the Warden is alive, but sealed away behind the lockdown");

        collectKeycard(sim, Pickup.Kind.AMBER_KEYCARD);
        assertEquals(GameSimulation.Objective.CONSOLE, sim.getObjective());

        overrideConsole(sim);
        assertEquals(GameSimulation.Objective.WARDEN, sim.getObjective());

        destroyWarden(sim);
        assertTrue(anyDoorOpening(sim, Door.LockKind.BOSS), "extraction unsealed");
        assertEquals(GameSimulation.Objective.EXIT, sim.getObjective());
    }

    @Test
    void stepsFollowWhatThePlayerCanReachNotAFixedOrder() {
        // The Warden's bulkhead bars the way to the Amber keycard here, so the Warden comes first.
        GameSimulation sim = TestTuning.simulation(GameMap.parse(
            "###########",
            "#>.B.MkA.X#",
            "###########"));
        assertEquals(GameSimulation.Objective.WARDEN, sim.getObjective());

        destroyWarden(sim);
        assertEquals(GameSimulation.Objective.AMBER_KEYCARD, sim.getObjective());

        collectKeycard(sim, Pickup.Kind.AMBER_KEYCARD);
        assertEquals(GameSimulation.Objective.EXIT, sim.getObjective());
    }

    @Test
    void keycardForABulkheadThatIsNotOnTheWayIsNotAsked() {
        // The Amber bulkhead closes off a side room; the exit is in the open.
        GameSimulation sim = TestTuning.simulation(GameMap.parse(
            "#######",
            "#>.k.X#",
            "###A###",
            "#.....#",
            "#######"));
        assertEquals(GameSimulation.Objective.EXIT, sim.getObjective());
    }

    @Test
    void trainingRangeAsksForEveryTargetAndAStageWithNoGoalHasNoLine() {
        Levels.Stage range = Levels.trainingRange();
        assertFalse(range.map.hasExit());
        assertEquals(GameSimulation.Objective.ALL_TARGETS, play(range).getObjective());

        GameMap hostilesOnly = GameMap.parse("#######", "#>..D.#", "#######");
        assertEquals(GameSimulation.Objective.ALL_HOSTILES,
            TestTuning.simulation(hostilesOnly, PlayerLoadout.fresh(), true).getObjective());
        GameMap both = GameMap.parse("#######", "#>.oD.#", "#######");
        assertEquals(GameSimulation.Objective.ALL_TARGETS_AND_HOSTILES,
            TestTuning.simulation(both, PlayerLoadout.fresh(), true).getObjective());

        assertNull(TestTuning.simulation(Levels.first()).getObjective(), "no exit and no clear on all targets");
    }

    @Test
    void objectiveLineIsTheSameConstantFrameAfterFrame() {
        GameSimulation sim = play(Levels.campaign().get(4));
        GameSimulation.Objective first = sim.getObjective();
        PlayerInput input = new PlayerInput();
        for (int i = 0; i < 30; i++) {
            sim.update(FRAME, input);
            assertSame(first, sim.getObjective());
        }
        assertSame(GameSimulation.Objective.AMBER_KEYCARD, first);
    }

    @Test
    void leaningOnTheLockdownBulkheadOfLockdownWingStillAsksForTheConsole() {
        GameSimulation sim = play(Levels.campaign().get(3));
        collectKeycard(sim, Pickup.Kind.AMBER_KEYCARD);
        sim.player.position.set(19.5f, 15.5f);
        assertEquals(GameSimulation.Objective.CONSOLE, sim.getObjective());

        // East into the lockdown bulkhead at column 20, row 15, until the slab stops the player.
        walk(sim, 90f, 1f, 30);
        assertTrue(sim.player.position.x > 20f, "the player's centre is in the bulkhead's cell");
        assertFalse(anyDoorOpening(sim, Door.LockKind.LOCKDOWN));
        assertEquals(GameSimulation.Objective.CONSOLE, sim.getObjective(), "leaning on the bulkhead");

        walk(sim, 90f, -1f, 30);
        assertTrue(sim.player.position.x < 19.5f, "stepped back");
        assertEquals(GameSimulation.Objective.CONSOLE, sim.getObjective(), "after stepping back");
    }

    @Test
    void leaningOnTheLockdownBulkheadOfCommandVaultStillAsksForTheCyanKeycard() {
        GameSimulation sim = play(Levels.campaign().get(4));
        collectKeycard(sim, Pickup.Kind.AMBER_KEYCARD);
        sim.player.position.set(11.5f, 13.5f);
        assertEquals(GameSimulation.Objective.CYAN_KEYCARD, sim.getObjective());

        // South into the lockdown bulkhead at column 11, row 14.
        walk(sim, 180f, 1f, 30);
        assertTrue(sim.player.position.y > 14f, "the player's centre is in the bulkhead's cell");
        assertEquals(GameSimulation.Objective.CYAN_KEYCARD, sim.getObjective(), "leaning on the bulkhead");

        walk(sim, 180f, -1f, 30);
        assertTrue(sim.player.position.y < 13.5f, "stepped back");
        assertEquals(GameSimulation.Objective.CYAN_KEYCARD, sim.getObjective(), "after stepping back");
    }

    @ParameterizedTest(name = "{displayName} with {0}")
    @EnumSource(TestTuning.Balance.class)
    void leaningOnTheWardensBulkheadStillAsksForTheWarden(TestTuning.Balance balance) {
        GameSimulation sim = play(Levels.campaign().get(5), balance);
        collectKeycard(sim, Pickup.Kind.AMBER_KEYCARD);
        overrideConsole(sim);
        sim.player.position.set(23.5f, 14.5f);
        assertEquals(GameSimulation.Objective.WARDEN, sim.getObjective());

        // South into the extraction bulkhead at column 23, row 15.
        walk(sim, 180f, 1f, 30);
        assertTrue(sim.player.position.y > 15f, "the player's centre is in the bulkhead's cell");
        assertFalse(sim.enemies.stream().noneMatch(
            enemy -> enemy.getKind() == Enemy.Kind.WARDEN && !enemy.isDestroyed()), "the Warden still stands");
        assertFalse(anyDoorOpening(sim, Door.LockKind.BOSS));
        assertEquals(GameSimulation.Objective.WARDEN, sim.getObjective(), "leaning on the bulkhead");

        walk(sim, 180f, -1f, 30);
        assertEquals(GameSimulation.Objective.WARDEN, sim.getObjective(), "after stepping back");
    }

    @Test
    void leaningOnALockedBulkheadFromEitherSideKeepsTheFloodOnThatSide() {
        GameMap corridor = GameMap.parse(
            "#########",
            "#>.S.L.X#",
            "#########");
        GameSimulation sim = TestTuning.simulation(corridor);
        sim.player.position.set(4.5f, 1.5f);
        assertEquals(GameSimulation.Objective.CONSOLE, sim.getObjective());

        walk(sim, 90f, 1f, 30);
        assertTrue(sim.player.position.x > 5f, "the player's centre is in the bulkhead's cell");
        assertEquals(GameSimulation.Objective.CONSOLE, sim.getObjective(), "leaning on the west face");
        walk(sim, 90f, -1f, 30);
        assertEquals(GameSimulation.Objective.CONSOLE, sim.getObjective(), "after stepping back");

        // From the exit's side the same bulkhead is behind the player, so the exit is all that is left.
        GameSimulation beyond = TestTuning.simulation(corridor);
        beyond.player.position.set(6.5f, 1.5f);
        walk(beyond, 270f, 1f, 30);
        assertTrue(beyond.player.position.x < 6f, "the player's centre is in the bulkhead's cell");
        assertEquals(GameSimulation.Objective.EXIT, beyond.getObjective(), "leaning on the east face");
    }

    @Test
    void leaningOnALockedBulkheadDoesNotWorkTheObjectiveOutAgainEveryFrame() {
        GameSimulation sim = TestTuning.simulation(GameMap.parse(
            "#########",
            "#>.S.L.X#",
            "#########"));
        sim.player.position.set(4.5f, 1.5f);
        walk(sim, 90f, 1f, 30);
        assertTrue(sim.player.position.x > 5f, "the player's centre is in the bulkhead's cell");
        GameSimulation.Objective leaning = sim.getObjective();
        int floods = sim.getObjectiveFloodCount();

        PlayerInput input = new PlayerInput();
        input.moveForward = 1f;
        for (int i = 0; i < 30; i++) {
            sim.update(FRAME, input);
            assertSame(leaning, sim.getObjective());
        }
        assertEquals(floods, sim.getObjectiveFloodCount(), "no flood while the player keeps leaning");

        walk(sim, 90f, -1f, 30);
        assertSame(leaning, sim.getObjective());
        assertEquals(floods, sim.getObjectiveFloodCount(), "no flood on stepping back either");
    }
}

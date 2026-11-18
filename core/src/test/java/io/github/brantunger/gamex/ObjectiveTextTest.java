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

import java.util.List;

/** Checks the HUD's live objective line against the real stages, step by step. */
class ObjectiveTextTest {

    private static final float FRAME = 1f / 60f;

    private static GameSimulation play(Levels.Stage stage) {
        return new GameSimulation(stage.map, PlayerLoadout.fresh(), stage.completeOnAllTargets);
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
            assertEquals(GameSimulation.OBJECTIVE_EXIT, play(campaign.get(i)).getObjectiveText(),
                campaign.get(i).title);
        }
    }

    @Test
    void lockdownWingAsksForTheAmberKeycardThenTheConsoleThenTheExit() {
        GameSimulation sim = play(Levels.campaign().get(3));
        assertEquals("04_lockdown_wing", Levels.campaign().get(3).id);
        assertEquals(GameSimulation.OBJECTIVE_AMBER_KEYCARD, sim.getObjectiveText());

        collectKeycard(sim, Pickup.Kind.AMBER_KEYCARD);
        assertFalse(anyDoorOpening(sim, Door.LockKind.AMBER_KEY), "the Amber bulkhead is not opened yet");
        assertEquals(GameSimulation.OBJECTIVE_CONSOLE, sim.getObjectiveText(), "holding the card is enough");

        overrideConsole(sim);
        assertEquals(GameSimulation.OBJECTIVE_EXIT, sim.getObjectiveText());
    }

    @Test
    void commandVaultAsksForAmberThenCyanThenTheConsoleThenTheExit() {
        GameSimulation sim = play(Levels.campaign().get(4));
        assertEquals("05_command_vault", Levels.campaign().get(4).id);
        assertEquals(GameSimulation.OBJECTIVE_AMBER_KEYCARD, sim.getObjectiveText(),
            "the Cyan keycard lies behind the Amber bulkhead, so Amber comes first");

        collectKeycard(sim, Pickup.Kind.AMBER_KEYCARD);
        assertEquals(GameSimulation.OBJECTIVE_CYAN_KEYCARD, sim.getObjectiveText(),
            "the console lies behind the Cyan bulkhead, so Cyan comes before it");

        collectKeycard(sim, Pickup.Kind.CYAN_KEYCARD);
        assertEquals(GameSimulation.OBJECTIVE_CONSOLE, sim.getObjectiveText());

        overrideConsole(sim);
        assertEquals(GameSimulation.OBJECTIVE_EXIT, sim.getObjectiveText());
    }

    @Test
    void wardenCoreAsksForAmberThenTheConsoleThenTheWardenThenTheExit() {
        GameSimulation sim = play(Levels.campaign().get(5));
        assertEquals("06_warden_core", Levels.campaign().get(5).id);
        assertEquals(GameSimulation.OBJECTIVE_AMBER_KEYCARD, sim.getObjectiveText(),
            "the Warden is alive, but sealed away behind the lockdown");

        collectKeycard(sim, Pickup.Kind.AMBER_KEYCARD);
        assertEquals(GameSimulation.OBJECTIVE_CONSOLE, sim.getObjectiveText());

        overrideConsole(sim);
        assertEquals(GameSimulation.OBJECTIVE_WARDEN, sim.getObjectiveText());

        destroyWarden(sim);
        assertTrue(anyDoorOpening(sim, Door.LockKind.BOSS), "extraction unsealed");
        assertEquals(GameSimulation.OBJECTIVE_EXIT, sim.getObjectiveText());
    }

    @Test
    void stepsFollowWhatThePlayerCanReachNotAFixedOrder() {
        // The Warden's bulkhead bars the way to the Amber keycard here, so the Warden comes first.
        GameSimulation sim = new GameSimulation(GameMap.parse(
            "###########",
            "#>.B.MkA.X#",
            "###########"));
        assertEquals(GameSimulation.OBJECTIVE_WARDEN, sim.getObjectiveText());

        destroyWarden(sim);
        assertEquals(GameSimulation.OBJECTIVE_AMBER_KEYCARD, sim.getObjectiveText());

        collectKeycard(sim, Pickup.Kind.AMBER_KEYCARD);
        assertEquals(GameSimulation.OBJECTIVE_EXIT, sim.getObjectiveText());
    }

    @Test
    void keycardForABulkheadThatIsNotOnTheWayIsNotAsked() {
        // The Amber bulkhead closes off a side room; the exit is in the open.
        GameSimulation sim = new GameSimulation(GameMap.parse(
            "#######",
            "#>.k.X#",
            "###A###",
            "#.....#",
            "#######"));
        assertEquals(GameSimulation.OBJECTIVE_EXIT, sim.getObjectiveText());
    }

    @Test
    void trainingRangeAsksForEveryTargetAndAStageWithNoGoalHasNoLine() {
        Levels.Stage range = Levels.trainingRange();
        assertFalse(range.map.hasExit());
        assertEquals(GameSimulation.OBJECTIVE_ALL_TARGETS, play(range).getObjectiveText());

        GameMap hostilesOnly = GameMap.parse("#######", "#>..D.#", "#######");
        assertEquals(GameSimulation.OBJECTIVE_ALL_HOSTILES,
            new GameSimulation(hostilesOnly, PlayerLoadout.fresh(), true).getObjectiveText());
        GameMap both = GameMap.parse("#######", "#>.oD.#", "#######");
        assertEquals(GameSimulation.OBJECTIVE_ALL_TARGETS_AND_HOSTILES,
            new GameSimulation(both, PlayerLoadout.fresh(), true).getObjectiveText());

        assertNull(new GameSimulation(Levels.first()).getObjectiveText(), "no exit and no clear on all targets");
    }

    @Test
    void objectiveLineIsTheSameConstantFrameAfterFrame() {
        GameSimulation sim = play(Levels.campaign().get(4));
        String first = sim.getObjectiveText();
        PlayerInput input = new PlayerInput();
        for (int i = 0; i < 30; i++) {
            sim.update(FRAME, input);
            assertSame(first, sim.getObjectiveText());
        }
        assertSame(GameSimulation.OBJECTIVE_AMBER_KEYCARD, first);
    }
}

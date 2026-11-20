package io.github.brantunger.gamex.world;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import ch.qos.logback.classic.Level;
import io.github.brantunger.gamex.GameSimulation;
import io.github.brantunger.gamex.LogCapture;
import io.github.brantunger.gamex.TestTuning;
import io.github.brantunger.gamex.player.PlayerInput;
import org.junit.jupiter.api.Test;

import java.util.Collections;
import java.util.List;

class MapExplorationTest {

    private static final float FRAME = 1f / 60f;

    @Test
    void initialRevealUncoversLineOfSightFloorAndAdjacentWallsWhileHidingUnexploredRooms() {
        GameMap map = GameMap.parse(
            "#########",
            "#...#...#",
            "#.>.#...#",
            "#...#...#",
            "#########");
        MapExploration exploration = new MapExploration(map);
        assertEquals(0, exploration.getExploredCount());

        exploration.reveal(map.getSpawnX(), map.getSpawnZ(), Collections.emptyList(), 8f);

        // West room (cols 1..3, rows 1..3) and its enclosing walls (cols 0..4, rows 0..4) are revealed.
        for (int row = 0; row <= 4; row++) {
            for (int col = 0; col <= 4; col++) {
                assertTrue(exploration.isExplored(col, row), "west room cell (" + col + "," + row + ")");
            }
        }
        // East room behind the solid divider wall remains unexplored.
        for (int row = 0; row <= 4; row++) {
            for (int col = 5; col <= 8; col++) {
                assertFalse(exploration.isExplored(col, row), "unexplored east cell (" + col + "," + row + ")");
            }
        }
    }

    @Test
    void closedDoorIsRevealedWithFlanksOnNearSideWhileRoomBehindStaysHiddenUntilOpened() {
        GameMap map = GameMap.parse(
            "#########",
            "#...#...#",
            "#.>.=.X.#",
            "#...#...#",
            "#########");
        GameSimulation sim = TestTuning.simulation(map);

        assertEquals(12f, MapExploration.DEFAULT_REVEAL_RADIUS, 0f);
        // Door at (4, 2) and its north/south jamb walls (4, 1) and (4, 3) are visible from spawn (2.5, 2.5).
        assertTrue(sim.exploration.isExplored(4, 2), "closed door cell itself is visible");
        assertTrue(sim.exploration.isExplored(4, 1), "north flanking jamb wall");
        assertTrue(sim.exploration.isExplored(4, 3), "south flanking jamb wall");
        assertFalse(sim.exploration.isExplored(5, 2), "floor behind closed door stays hidden");
        assertFalse(sim.exploration.isExplored(6, 2), "exit pad behind closed door stays hidden");
        assertFalse(sim.exploration.isExplored(5, 1), "far-side wall stays hidden");

        // Even leaning directly against the closed door keeps the room behind it unexplored.
        sim.player.position.set(3.65f, 2.5f);
        sim.exploration.reveal(sim.player.position.x, sim.player.position.y, sim.doors);
        assertFalse(sim.exploration.isExplored(5, 2), "leaning against closed door must not leak sight");

        // Opening the door and advancing past CLEAR_PROGRESS reveals the east room.
        PlayerInput interact = new PlayerInput();
        interact.interact = true;
        sim.update(FRAME, interact);
        interact.interact = false;
        assertTrue(sim.doors.get(0).isOpening());

        for (int i = 0; i < 40; i++) {
            sim.update(FRAME, interact);
        }
        assertFalse(sim.doors.get(0).isBlockingPassage());
        assertTrue(sim.exploration.isExplored(5, 2), "floor beyond opened door is revealed");
        assertTrue(sim.exploration.isExploredAt(map.getExitX(), map.getExitZ()), "exit pad is revealed");
    }

    @Test
    void explorationIsPermanentOnceRevealed() {
        GameMap map = GameMap.parse(
            "#####################",
            "#>..................#",
            "#####################");
        MapExploration exploration = new MapExploration(map);

        exploration.reveal(1.5f, 1.5f, Collections.emptyList(), 3f);
        assertTrue(exploration.isExplored(1, 1));
        assertTrue(exploration.isExplored(3, 1));
        assertFalse(exploration.isExplored(18, 1));

        // Moving to the far east end reveals the east end without forgetting the west end.
        exploration.reveal(18.5f, 1.5f, Collections.emptyList(), 3f);
        assertTrue(exploration.isExplored(18, 1));
        assertTrue(exploration.isExplored(1, 1), "west spawn remains explored permanently");
        assertTrue(exploration.isExplored(0, 0), "west wall remains explored permanently");
    }

    @Test
    void defaultTwelveCellRadiusMapsDownLongCorridorsBeforeReachingFarDoor() {
        GameMap longHall = GameMap.parse(
            "##############",
            "#>..........=#",
            "##############");
        GameSimulation sim = TestTuning.simulation(longHall);
        assertTrue(sim.exploration.isExplored(12, 1), "far closed door 10.85 cells away is revealed at default 12f");
        assertTrue(sim.exploration.isExplored(12, 0), "door north flank wall is revealed");
        assertTrue(sim.exploration.isExplored(12, 2), "door south flank wall is revealed");
    }

    @Test
    void entityVisibilityIncludesEnemiesOutOfCurrentSightOnExploredTilesAndHidesFarSideClosedDoorEntities() {
        GameMap map = GameMap.parse(
            "###########",
            "#>..#.....#",
            "#.#.#..#..#",
            "#...=.D#..#",
            "###########");
        GameSimulation sim = TestTuning.simulation(map);
        List<Door> doors = sim.doors;
        assertEquals(1, doors.size());
        Door door = doors.get(0); // SPAN_Z at col 4, row 3

        // Move south-west to explore (1..3, 3) and the west side of the door, then walk back around the pillar.
        sim.exploration.reveal(2.5f, 3.5f, doors, 8f);
        assertTrue(sim.exploration.isExplored(1, 3), "south-west corner explored");
        assertTrue(sim.exploration.isExplored(4, 3), "door cell explored");
        assertFalse(sim.exploration.isExplored(5, 3), "east room behind closed door unexplored");

        // Now move player back to north-west behind the center pillar (1.5, 1.5), losing direct LOS to (3.5, 3.5).
        sim.player.position.set(1.5f, 1.5f);
        assertFalse(Hitscan.hasLineOfSight(map, doors, 1.5f, 1.5f, 3.5f, 3.5f));
        assertTrue(sim.exploration.isEntityExplored(3.5f, 3.5f, doors),
            "enemy on previously explored tile remains visible on minimap even out of current LOS");

        // Enemy on unexplored tile behind closed door is hidden.
        assertFalse(sim.exploration.isEntityExplored(6.5f, 3.5f, doors));

        // Enemy pressed against the far (east) side of the closed SPAN_Z door, with center inside the door cell,
        // must still be hidden because the east side (5, 3) is unexplored.
        assertFalse(sim.exploration.isEntityExplored(door.getX() + 0.25f, door.getZ(), doors),
            "enemy hugging far side of closed door must stay hidden");

        // An entity hugging the near (west) explored side inside the door cell is visible.
        assertTrue(sim.exploration.isEntityExplored(door.getX() - 0.25f, door.getZ(), doors));
    }

    @Test
    void invalidInputsAndResetAreHandledSafelyWithoutLoggingOnIdleFrames() {
        assertThrows(IllegalArgumentException.class, () -> new MapExploration(null));

        GameMap map = GameMap.parse(
            "#####",
            "#>..#",
            "#####");
        GameSimulation sim = TestTuning.simulation(map);
        assertTrue(sim.exploration.getExploredCount() > 0);

        assertFalse(sim.exploration.isExplored(-1, 0));
        assertFalse(sim.exploration.isExplored(0, -1));
        assertFalse(sim.exploration.isExplored(100, 0));
        assertFalse(sim.exploration.isExplored(0, 100));
        assertFalse(sim.exploration.isExploredAt(Float.NaN, 1f));
        assertFalse(sim.exploration.isExploredAt(1f, Float.POSITIVE_INFINITY));
        assertFalse(sim.exploration.isEntityExplored(Float.NaN, 1f, sim.doors));

        assertDoesNotThrow(() -> sim.exploration.reveal(Float.NaN, 1.5f, sim.doors, 8f));
        assertDoesNotThrow(() -> sim.exploration.reveal(1.5f, 1.5f, sim.doors, 0f));
        assertDoesNotThrow(() -> sim.exploration.reveal(1.5f, 1.5f, sim.doors, Float.NEGATIVE_INFINITY));

        sim.player.position.set(3.5f, 1.5f);
        sim.resetExploration();
        assertTrue(sim.exploration.isExploredAt(3.5f, 1.5f));

        // Idle frames must emit zero log messages from GameSimulation or MapExploration.
        PlayerInput idle = new PlayerInput();
        try (LogCapture simLog = LogCapture.of(GameSimulation.class, Level.TRACE);
             LogCapture expLog = LogCapture.of(MapExploration.class, Level.TRACE)) {
            for (int i = 0; i < 60; i++) {
                sim.update(FRAME, idle);
            }
            assertEquals(List.of(), simLog.messages(Level.TRACE));
            assertEquals(List.of(), expLog.messages(Level.TRACE));
        }
    }
}

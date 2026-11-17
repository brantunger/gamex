package io.github.brantunger.gamex.world;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.badlogic.gdx.math.Vector2;
import org.junit.jupiter.api.Test;

import java.util.Collections;
import java.util.List;

class PathfinderTest {

    @Test
    void routesAroundPillarsAndOneCellDoorwaysWithoutCuttingWallCorners() {
        GameMap map = GameMap.parse(
            "#######",
            "#>.#..#",
            "#..#..#",
            "#.....#",
            "#######");
        Pathfinder pathfinder = new Pathfinder();
        pathfinder.compute(map, 1.5f, 1.5f);

        Vector2 dir = new Vector2();
        // From (5.5, 1.5) around the vertical wall at col 3 (rows 1 & 2), it must head south toward row 3.
        assertTrue(pathfinder.nextDirection(map, 5.5f, 1.5f, dir));
        assertTrue(dir.y > 0.5f, "expected southward steering around wall, got " + dir);

        // Near an L-corner: goal at (2.5, 1.5), wall at (2, 2), unit at (1.5, 2.5) must not cut diagonally across (2, 2).
        GameMap cornerMap = GameMap.parse(
            "#####",
            "#..>#",
            "#.#.#",
            "#####");
        pathfinder.compute(cornerMap, 2.5f, 1.5f);
        assertTrue(pathfinder.nextDirection(cornerMap, 1.5f, 2.5f, dir));
        assertTrue(dir.y < -0.9f && Math.abs(dir.x) < 0.1f, "must go north past corner before turning east: " + dir);
    }

    @Test
    void returnsFalseWhenInsideGoalCellOrUnreachable() {
        GameMap splitMap = GameMap.parse(
            "#######",
            "#>..#.#",
            "#######");
        Pathfinder pathfinder = new Pathfinder();
        pathfinder.compute(splitMap, 1.5f, 1.5f);

        Vector2 dir = new Vector2();
        assertFalse(pathfinder.nextDirection(splitMap, 1.5f, 1.5f, dir));
        assertFalse(pathfinder.nextDirection(splitMap, 5.5f, 1.5f, dir));
        assertEquals(0f, dir.len(), 1e-6f);
    }

    @Test
    void closedDoorBlocksPathingAndOpeningItUnlocksTheRoute() {
        GameMap map = GameMap.parse(
            "#####",
            "#>..#",
            "###=#",
            "#...#",
            "#####");
        Pathfinder pathfinder = new Pathfinder();
        Door door = new Door(Door.LockKind.NONE, Door.Orientation.SPAN_X, 3, 2);
        List<Door> doors = Collections.singletonList(door);
        Vector2 dir = new Vector2();

        pathfinder.compute(map, doors, 1.5f, 1.5f);
        assertFalse(pathfinder.nextDirection(map, doors, 1.5f, 3.5f, dir));

        door.open();
        door.update(Door.OPEN_DURATION);
        pathfinder.compute(map, doors, 1.5f, 1.5f);
        assertTrue(pathfinder.nextDirection(map, doors, 1.5f, 3.5f, dir));
        assertTrue(dir.x > 0.5f, "expected eastward move toward open bulkhead, got " + dir);
    }
}

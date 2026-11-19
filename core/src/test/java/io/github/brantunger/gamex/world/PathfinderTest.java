package io.github.brantunger.gamex.world;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.badlogic.gdx.math.Vector2;
import io.github.brantunger.gamex.player.Player;
import org.junit.jupiter.api.Test;

import java.util.Arrays;
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

    @Test
    void guardHazardAvoidanceDetoursAroundShockGratesAndFallsBackWhenForced() {
        // Direct central path crosses a 2-cell shock grate strip (row 3), while north corridor (rows 1-2) is safe.
        GameMap map = GameMap.parse(
            "#########",
            "#.......#",
            "#.#####.#",
            "#>..~~..#",
            "#########");
        Pathfinder avoid = new Pathfinder();
        Pathfinder direct = new Pathfinder();
        avoid.compute(map, Collections.<Door>emptyList(), true, 1.5f, 3.5f);
        direct.compute(map, Collections.<Door>emptyList(), false, 1.5f, 3.5f);

        Vector2 avoidDir = new Vector2();
        Vector2 directDir = new Vector2();
        // Starting at (6.5, 3.5): hazard-avoiding path detours East -> North around the wall; direct goes West.
        assertTrue(avoid.nextDirection(map, Collections.<Door>emptyList(), true, 6.5f, 3.5f, avoidDir));
        assertTrue(avoidDir.x > 0.5f, "expected eastward detour around hazard corridor, got " + avoidDir);
        assertTrue(direct.nextDirection(map, Collections.<Door>emptyList(), false, 6.5f, 3.5f, directDir));
        assertTrue(directDir.x < -0.5f, "expected direct westward route over grate, got " + directDir);

        // When no safe detour exists, hazard-avoiding pathfinder still crosses the grate cleanly to reach the goal.
        GameMap bridgeOnly = GameMap.parse(
            "#######",
            "#>~~..#",
            "#######");
        avoid.compute(bridgeOnly, Collections.<Door>emptyList(), true, 1.5f, 1.5f);
        assertTrue(avoid.nextDirection(bridgeOnly, Collections.<Door>emptyList(), true, 4.5f, 1.5f, avoidDir));
        assertTrue(avoidDir.x < -0.5f, "expected fallback westward across grate when no safe detour exists");
    }

    @Test
    void hazardAvoidanceChargesEnteredCellCostOnSingleGratesDiagonalCornersAndHazardGoals() {
        // 1. Single-cell shock grate with an open north loop: entering (4, 3) from (5, 3) incurs HAZARD_COST so
        // (5.5, 3.5) detours east along the safe corridor instead of stepping onto the single ~ tile.
        GameMap singleGrateLoop = GameMap.parse(
            "########",
            "#......#",
            "#.####.#",
            "#>..~..#",
            "########");
        Pathfinder avoid = new Pathfinder();
        Pathfinder direct = new Pathfinder();
        Vector2 dir = new Vector2();

        avoid.compute(singleGrateLoop, Collections.<Door>emptyList(), true, 1.5f, 3.5f);
        assertTrue(avoid.nextDirection(singleGrateLoop, Collections.<Door>emptyList(), true, 5.5f, 3.5f, dir));
        assertTrue(dir.x > 0.8f, "expected eastward safe detour around single ~ grate, got " + dir);

        direct.compute(singleGrateLoop, Collections.<Door>emptyList(), false, 1.5f, 3.5f);
        assertTrue(direct.nextDirection(singleGrateLoop, Collections.<Door>emptyList(), false, 5.5f, 3.5f, dir));
        assertTrue(dir.x < -0.8f, "expected westward direct route when avoidHazards is false, got " + dir);

        // 2. Goal cell itself is a ~ shock grate (e.g. player airborne/dashing over west ~ of ~~): stepping onto the
        // goal incurs HAZARD_COST, and units on the east side detour around the safe north corridor to (1, 3) rather
        // than crossing the second ~ grate (3, 3), and never cut diagonally onto a ~ cell when a safe cardinal step exists.
        GameMap goalOnGrate = GameMap.parse(
            "######",
            "#....#",
            "#.##.#",
            "#>~~.#",
            "######");
        avoid.compute(goalOnGrate, Collections.<Door>emptyList(), true, 2.5f, 3.5f);
        assertFollowsHazardFreeDetourToAdjacentSafeCell(avoid, goalOnGrate, 4.5f, 3.5f, 1, 3);

        // 3. Open room where goal (2, 1) is ~ and origin (1, 2) is diagonally adjacent with both flanking cardinal
        // cells (1, 1) and (2, 2) safe: nextDirection steers cardinally into a safe flanking cell rather than
        // diagonally cutting onto the ~ goal cell.
        GameMap diagonalGoalGrate = GameMap.parse(
            "#####",
            "#.>~#",
            "#...#",
            "#####");
        avoid.compute(diagonalGoalGrate, Collections.<Door>emptyList(), true, 3.5f, 1.5f);
        assertTrue(avoid.nextDirection(diagonalGoalGrate, Collections.<Door>emptyList(), true, 2.5f, 2.5f, dir));
        assertFalse(dir.x > 0.4f && dir.y < -0.4f,
            "must steer cardinally into safe cell rather than diagonally onto ~ goal: " + dir);
    }

    private static void assertFollowsHazardFreeDetourToAdjacentSafeCell(
            Pathfinder pathfinder, GameMap map, float startX, float startZ, int expectedCol, int expectedRow) {
        Vector2 dir = new Vector2();
        float x = startX;
        float z = startZ;
        int steps = 0;
        while ((int) Math.floor(x) != expectedCol || (int) Math.floor(z) != expectedRow) {
            assertTrue(steps < 120, "exceeded step budget at (" + x + ", " + z + ")");
            assertTrue(pathfinder.nextDirection(map, Collections.<Door>emptyList(), true, x, z, dir),
                "expected steering direction at (" + x + ", " + z + ")");
            x += dir.x * 0.2f;
            z += dir.y * 0.2f;
            assertFalse(map.isHazardAt(x, z), "detour stepped onto shock grate at (" + x + ", " + z + ")");
            steps++;
        }
    }

    @Test
    void computeResolvesPlayerGoalAgainstSpanXBulkheadOntoGoalSideForClosedAndOpeningDoors() {
        GameMap sealedMap = GameMap.parse(
            "#######",
            "#>....#",
            "###L###",
            "#.....#",
            "#######");
        GameMap.DoorSpawn spawn = sealedMap.getDoorSpawns().get(0);
        assertEquals(Door.Orientation.SPAN_X, spawn.orientation);

        Door closedDoor = new Door(spawn.lockKind, spawn.orientation, spawn.col, spawn.row);
        Door openingDoor = new Door(Door.LockKind.NONE, spawn.orientation, spawn.col, spawn.row);
        openingDoor.open();
        openingDoor.update(Door.OPEN_DURATION * 0.5f);
        assertTrue(openingDoor.isBlockingPassage());

        Pathfinder pathfinder = new Pathfinder();
        Vector2 dir = new Vector2();

        for (Door door : Arrays.asList(closedDoor, openingDoor)) {
            List<Door> doors = Collections.singletonList(door);
            float northGoalZ = door.getZ() - Door.HALF_THICKNESS - Player.RADIUS;
            float southGoalZ = door.getZ() + Door.HALF_THICKNESS + Player.RADIUS;
            assertEquals(door.getRow(), (int) Math.floor(northGoalZ));
            assertEquals(door.getRow(), (int) Math.floor(southGoalZ));

            // Player pressed against north (-Z) face: north-side enemies pathfind east; sealed south side gets false.
            pathfinder.compute(sealedMap, doors, door.getX(), northGoalZ);
            assertTrue(pathfinder.nextDirection(sealedMap, doors, 1.5f, 1.5f, dir));
            assertTrue(dir.x > 0.9f && Math.abs(dir.y) < 0.1f, "expected eastward routing on north side, got " + dir);
            assertFalse(pathfinder.nextDirection(sealedMap, doors, 3.5f, 3.5f, dir));
            assertFalse(pathfinder.nextDirection(sealedMap, doors, 5.5f, 3.5f, dir));

            // Hazard-avoiding overload also resolves the north door margin cleanly.
            pathfinder.compute(sealedMap, doors, true, door.getX(), northGoalZ);
            assertTrue(pathfinder.nextDirection(sealedMap, doors, true, 1.5f, 1.5f, dir));
            assertTrue(dir.x > 0.9f, "expected hazard-avoiding route on north side, got " + dir);

            // Player pressed against south (+Z) face: south-side enemies pathfind west; sealed north side gets false.
            pathfinder.compute(sealedMap, doors, door.getX(), southGoalZ);
            assertTrue(pathfinder.nextDirection(sealedMap, doors, 5.5f, 3.5f, dir));
            assertTrue(dir.x < -0.9f && Math.abs(dir.y) < 0.1f, "expected westward routing on south side, got " + dir);
            assertFalse(pathfinder.nextDirection(sealedMap, doors, 3.5f, 1.5f, dir));
            assertFalse(pathfinder.nextDirection(sealedMap, doors, 1.5f, 1.5f, dir));
        }

        // When an open bypass corridor exists (col 1), opposite-side enemies route around the locked door to the player's side.
        GameMap loopMap = GameMap.parse(
            "#######",
            "#>....#",
            "#.###L#",
            "#.....#",
            "#######");
        Door loopDoor = new Door(Door.LockKind.LOCKDOWN, Door.Orientation.SPAN_X, 5, 2);
        List<Door> loopDoors = Collections.singletonList(loopDoor);

        pathfinder.compute(loopMap, loopDoors, loopDoor.getX(), loopDoor.getZ() + Door.HALF_THICKNESS + Player.RADIUS);
        assertTrue(pathfinder.nextDirection(loopMap, loopDoors, 5.5f, 1.5f, dir));
        assertTrue(dir.x < -0.9f, "expected detour west around locked SPAN_X door toward south-face player, got " + dir);

        pathfinder.compute(loopMap, loopDoors, loopDoor.getX(), loopDoor.getZ() - Door.HALF_THICKNESS - Player.RADIUS);
        assertTrue(pathfinder.nextDirection(loopMap, loopDoors, 5.5f, 3.5f, dir));
        assertTrue(dir.x < -0.9f, "expected detour west around locked SPAN_X door toward north-face player, got " + dir);
    }

    @Test
    void computeResolvesPlayerGoalAgainstSpanZBulkheadOntoGoalSideForClosedAndOpeningDoors() {
        GameMap sealedMap = GameMap.parse(
            "#########",
            "#>..#...#",
            "#...L...#",
            "#...#...#",
            "#########");
        GameMap.DoorSpawn spawn = sealedMap.getDoorSpawns().get(0);
        assertEquals(Door.Orientation.SPAN_Z, spawn.orientation);

        Door closedDoor = new Door(spawn.lockKind, spawn.orientation, spawn.col, spawn.row);
        Door openingDoor = new Door(Door.LockKind.NONE, spawn.orientation, spawn.col, spawn.row);
        openingDoor.open();
        openingDoor.update(Door.OPEN_DURATION * 0.70f);
        assertTrue(openingDoor.isBlockingPassage());

        Pathfinder pathfinder = new Pathfinder();
        Vector2 dir = new Vector2();

        for (Door door : Arrays.asList(closedDoor, openingDoor)) {
            List<Door> doors = Collections.singletonList(door);
            float westGoalX = door.getX() - Door.HALF_THICKNESS - Player.RADIUS;
            float eastGoalX = door.getX() + Door.HALF_THICKNESS + Player.RADIUS;
            assertEquals(door.getCol(), (int) Math.floor(westGoalX));
            assertEquals(door.getCol(), (int) Math.floor(eastGoalX));

            // Player pressed against west (-X) face: west-side enemies pathfind east; sealed east side gets false.
            pathfinder.compute(sealedMap, doors, westGoalX, door.getZ());
            assertTrue(pathfinder.nextDirection(sealedMap, doors, 1.5f, 2.5f, dir));
            assertTrue(dir.x > 0.9f && Math.abs(dir.y) < 0.1f, "expected eastward routing on west side, got " + dir);
            assertFalse(pathfinder.nextDirection(sealedMap, doors, 5.5f, 2.5f, dir));
            assertFalse(pathfinder.nextDirection(sealedMap, doors, 7.5f, 1.5f, dir));

            // Player pressed against east (+X) face: east-side enemies pathfind west; sealed west side gets false.
            pathfinder.compute(sealedMap, doors, eastGoalX, door.getZ());
            assertTrue(pathfinder.nextDirection(sealedMap, doors, 7.5f, 2.5f, dir));
            assertTrue(dir.x < -0.9f && Math.abs(dir.y) < 0.1f, "expected westward routing on east side, got " + dir);
            assertFalse(pathfinder.nextDirection(sealedMap, doors, 3.5f, 2.5f, dir));
            assertFalse(pathfinder.nextDirection(sealedMap, doors, 1.5f, 1.5f, dir));
        }

        // With an open south bypass (col 4, row 3), opposite-side enemies detour around the closed SPAN_Z door.
        GameMap loopMap = GameMap.parse(
            "#########",
            "#>..L...#",
            "#...#...#",
            "#.......#",
            "#########");
        Door loopDoor = new Door(Door.LockKind.LOCKDOWN, Door.Orientation.SPAN_Z, 4, 1);
        List<Door> loopDoors = Collections.singletonList(loopDoor);

        pathfinder.compute(loopMap, loopDoors, loopDoor.getX() + Door.HALF_THICKNESS + Player.RADIUS, loopDoor.getZ());
        assertTrue(pathfinder.nextDirection(loopMap, loopDoors, 3.5f, 1.5f, dir));
        assertTrue(dir.y > 0.9f, "expected southward detour around locked SPAN_Z door toward east-face player, got " + dir);

        pathfinder.compute(loopMap, loopDoors, loopDoor.getX() - Door.HALF_THICKNESS - Player.RADIUS, loopDoor.getZ());
        assertTrue(pathfinder.nextDirection(loopMap, loopDoors, 5.5f, 1.5f, dir));
        assertTrue(dir.y > 0.9f, "expected southward detour around locked SPAN_Z door toward west-face player, got " + dir);
    }

    @Test
    void enemiesInsideSpanXDoorMarginsSteerOutOntoOpenRoutesAndStopWhenSealed() {
        GameMap sealedMap = GameMap.parse(
            "#######",
            "#>....#",
            "###L###",
            "#.....#",
            "#######");
        Door closedDoor = new Door(Door.LockKind.LOCKDOWN, Door.Orientation.SPAN_X, 3, 2);
        Door openingDoor = new Door(Door.LockKind.NONE, Door.Orientation.SPAN_X, 3, 2);
        openingDoor.open();
        openingDoor.update(Door.OPEN_DURATION * 0.5f);
        assertTrue(openingDoor.isBlockingPassage());

        Pathfinder pathfinder = new Pathfinder();
        Vector2 dir = new Vector2();
        Enemy.Kind[] mobileKinds = {Enemy.Kind.DRONE, Enemy.Kind.GUARD, Enemy.Kind.WARDEN};

        for (Door door : Arrays.asList(closedDoor, openingDoor)) {
            List<Door> doors = Collections.singletonList(door);
            for (Enemy.Kind kind : mobileKinds) {
                float northMarginZ = door.getZ() - Door.HALF_THICKNESS - kind.radius;
                float southMarginZ = door.getZ() + Door.HALF_THICKNESS + kind.radius;
                assertEquals(door.getRow(), (int) Math.floor(northMarginZ));
                assertEquals(door.getRow(), (int) Math.floor(southMarginZ));

                // Same-side north goal: north margin steers north out of the doorway recess; sealed south margin returns false.
                pathfinder.compute(sealedMap, doors, 1.5f, 1.5f);
                assertTrue(pathfinder.nextDirection(sealedMap, doors, 3.35f, northMarginZ, dir),
                    kind + " in north SPAN_X margin should steer out toward north corridor");
                assertTrue(dir.y < -0.9f, "expected northward exit from doorway margin for " + kind + ", got " + dir);
                assertFalse(pathfinder.nextDirection(sealedMap, doors, 3.5f, southMarginZ, dir),
                    kind + " in sealed south SPAN_X margin must remain unreachable");

                // Hazard-avoiding Guard pathfinder also steers cleanly out of the doorway margin.
                pathfinder.compute(sealedMap, doors, true, 1.5f, 1.5f);
                assertTrue(pathfinder.nextDirection(sealedMap, doors, true, 3.5f, northMarginZ, dir));
                assertTrue(dir.y < -0.99f);

                // Adjacent cell goal (3, 1) directly in front of the north doorway margin still pulls enemy out of (3, 2).
                pathfinder.compute(sealedMap, doors, 3.5f, 1.5f);
                assertTrue(pathfinder.nextDirection(sealedMap, doors, 3.5f, northMarginZ, dir));
                assertTrue(dir.y < -0.99f && Math.abs(dir.x) < 1e-4f);

                // Same-side south goal: south margin steers south out of the doorway recess; sealed north margin returns false.
                pathfinder.compute(sealedMap, doors, 5.5f, 3.5f);
                assertTrue(pathfinder.nextDirection(sealedMap, doors, 3.65f, southMarginZ, dir),
                    kind + " in south SPAN_X margin should steer out toward south corridor");
                assertTrue(dir.y > 0.9f, "expected southward exit from doorway margin for " + kind + ", got " + dir);
                assertFalse(pathfinder.nextDirection(sealedMap, doors, 3.5f, northMarginZ, dir),
                    kind + " in sealed north SPAN_X margin must remain unreachable");
            }
        }

        // Alternate-loop route: player pressed against south (+Z) face of locked door (5, 2) while each enemy archetype
        // starts pressed against north (-Z) face of (5, 2) and routes all the way around (1, 2) without crossing the slab.
        GameMap loopMap = GameMap.parse(
            "#######",
            "#>....#",
            "#.###L#",
            "#.....#",
            "#######");
        Door loopDoor = new Door(Door.LockKind.LOCKDOWN, Door.Orientation.SPAN_X, 5, 2);
        List<Door> loopDoors = Collections.singletonList(loopDoor);
        pathfinder.compute(loopMap, loopDoors, loopDoor.getX(), loopDoor.getZ() + Door.HALF_THICKNESS + Player.RADIUS);

        for (Enemy.Kind kind : mobileKinds) {
            float startZ = loopDoor.getZ() - Door.HALF_THICKNESS - kind.radius;
            assertFollowsRouteToCellWithoutCrossingDoor(pathfinder, loopMap, loopDoors, loopDoor, 5.5f, startZ, 5, 3);
        }
    }

    @Test
    void enemiesInsideSpanZDoorMarginsSteerOutOntoOpenRoutesAndStopWhenSealed() {
        GameMap sealedMap = GameMap.parse(
            "#########",
            "#>..#...#",
            "#...L...#",
            "#...#...#",
            "#########");
        Door closedDoor = new Door(Door.LockKind.LOCKDOWN, Door.Orientation.SPAN_Z, 4, 2);
        Door openingDoor = new Door(Door.LockKind.NONE, Door.Orientation.SPAN_Z, 4, 2);
        openingDoor.open();
        openingDoor.update(Door.OPEN_DURATION * 0.6f);
        assertTrue(openingDoor.isBlockingPassage());

        Pathfinder pathfinder = new Pathfinder();
        Vector2 dir = new Vector2();
        Enemy.Kind[] mobileKinds = {Enemy.Kind.DRONE, Enemy.Kind.GUARD, Enemy.Kind.WARDEN};

        for (Door door : Arrays.asList(closedDoor, openingDoor)) {
            List<Door> doors = Collections.singletonList(door);
            for (Enemy.Kind kind : mobileKinds) {
                float westMarginX = door.getX() - Door.HALF_THICKNESS - kind.radius;
                float eastMarginX = door.getX() + Door.HALF_THICKNESS + kind.radius;
                assertEquals(door.getCol(), (int) Math.floor(westMarginX));
                assertEquals(door.getCol(), (int) Math.floor(eastMarginX));

                // Same-side west goal: west margin steers west out of the doorway recess; sealed east margin returns false.
                pathfinder.compute(sealedMap, doors, 1.5f, 1.5f);
                assertTrue(pathfinder.nextDirection(sealedMap, doors, westMarginX, 2.35f, dir),
                    kind + " in west SPAN_Z margin should steer out toward west room");
                assertTrue(dir.x < -0.9f, "expected westward exit from doorway margin for " + kind + ", got " + dir);
                assertFalse(pathfinder.nextDirection(sealedMap, doors, eastMarginX, 2.5f, dir),
                    kind + " in sealed east SPAN_Z margin must remain unreachable");

                // Adjacent cell goal (3, 2) directly outside the west doorway margin still pulls enemy out of (4, 2).
                pathfinder.compute(sealedMap, doors, 3.5f, 2.5f);
                assertTrue(pathfinder.nextDirection(sealedMap, doors, westMarginX, 2.5f, dir));
                assertTrue(dir.x < -0.99f && Math.abs(dir.y) < 1e-4f);

                // Same-side east goal: east margin steers east out of the doorway recess; sealed west margin returns false.
                pathfinder.compute(sealedMap, doors, 7.5f, 3.5f);
                assertTrue(pathfinder.nextDirection(sealedMap, doors, eastMarginX, 2.65f, dir),
                    kind + " in east SPAN_Z margin should steer out toward east room");
                assertTrue(dir.x > 0.9f, "expected eastward exit from doorway margin for " + kind + ", got " + dir);
                assertFalse(pathfinder.nextDirection(sealedMap, doors, westMarginX, 2.5f, dir),
                    kind + " in sealed west SPAN_Z margin must remain unreachable");
            }
        }

        // Alternate-loop route: player pressed against east (+X) face of locked SPAN_Z door (4, 1) while each enemy
        // archetype starts pressed against west (-X) face of (4, 1) and detours around (4, 3) without crossing the slab.
        GameMap loopMap = GameMap.parse(
            "#########",
            "#>..L...#",
            "#...#...#",
            "#.......#",
            "#########");
        Door loopDoor = new Door(Door.LockKind.LOCKDOWN, Door.Orientation.SPAN_Z, 4, 1);
        List<Door> loopDoors = Collections.singletonList(loopDoor);
        pathfinder.compute(loopMap, loopDoors, loopDoor.getX() + Door.HALF_THICKNESS + Player.RADIUS, loopDoor.getZ());

        for (Enemy.Kind kind : mobileKinds) {
            float startX = loopDoor.getX() - Door.HALF_THICKNESS - kind.radius;
            assertFollowsRouteToCellWithoutCrossingDoor(pathfinder, loopMap, loopDoors, loopDoor, startX, 1.5f, 5, 1);
        }
    }

    @Test
    void solidWallsOutOfBoundsAndBlockedAdjacentCellsReturnFalseSafely() {
        GameMap map = GameMap.parse(
            "#######",
            "#>..#.#",
            "###=#.#",
            "#...=.#",
            "#######");
        Door spanXDoor = new Door(Door.LockKind.NONE, Door.Orientation.SPAN_X, 3, 2);
        Door spanZDoor = new Door(Door.LockKind.NONE, Door.Orientation.SPAN_Z, 4, 3);
        Door doorOnSolidWall = new Door(Door.LockKind.NONE, Door.Orientation.SPAN_X, 4, 1);
        List<Door> doors = Arrays.asList(spanXDoor, spanZDoor, doorOnSolidWall);

        Pathfinder uncomputed = new Pathfinder();
        Vector2 dir = new Vector2();
        assertFalse(uncomputed.nextDirection(map, doors, 1.5f, 1.5f, dir));

        Pathfinder pathfinder = new Pathfinder();

        // Solid wall goals (border wall, interior wall, and a solid wall cell holding a synthetic Door) stay unreachable.
        float[][] invalidGoals = {
            {0.5f, 1.5f},
            {4.2f, 1.2f},
            {-0.5f, 1.5f},
            {1.5f, -0.5f},
            {99.5f, 1.5f},
            {1.5f, 99.5f},
            {Float.NaN, 1.5f},
            {1.5f, Float.POSITIVE_INFINITY}
        };
        for (float[] goal : invalidGoals) {
            pathfinder.compute(map, doors, goal[0], goal[1]);
            assertFalse(pathfinder.nextDirection(map, doors, 1.5f, 1.5f, dir));
            assertEquals(0f, dir.len(), 1e-6f);
        }

        // Valid open goal, tested against solid wall, out-of-bounds, NaN, and null-map origins.
        pathfinder.compute(map, doors, 1.5f, 1.5f);
        assertFalse(pathfinder.nextDirection(null, doors, 2.5f, 1.5f, dir));
        assertFalse(pathfinder.nextDirection(map, doors, 0.5f, 1.5f, dir));
        assertFalse(pathfinder.nextDirection(map, doors, 4.5f, 1.2f, dir));
        assertFalse(pathfinder.nextDirection(map, doors, -0.1f, 1.5f, dir));
        assertFalse(pathfinder.nextDirection(map, doors, 1.5f, -0.1f, dir));
        assertFalse(pathfinder.nextDirection(map, doors, 50.5f, 1.5f, dir));
        assertFalse(pathfinder.nextDirection(map, doors, Float.NaN, 1.5f, dir));
        assertEquals(0f, dir.len(), 1e-6f);

        // Origin at the exact goal inside a closed door margin returns false (already at goal).
        float northGoalZ = spanXDoor.getZ() - Door.HALF_THICKNESS - Player.RADIUS;
        pathfinder.compute(map, doors, spanXDoor.getX(), northGoalZ);
        assertFalse(pathfinder.nextDirection(map, doors, spanXDoor.getX(), northGoalZ, dir));

        // Door margin whose stepped side cell is itself blocked (by a wall or a second consecutive closed door).
        GameMap doubleDoorMap = GameMap.parse(
            "#####",
            "#>..#",
            "###=#",
            "###=#",
            "#####");
        Door northDoor = new Door(Door.LockKind.LOCKDOWN, Door.Orientation.SPAN_X, 3, 2);
        Door southDoor = new Door(Door.LockKind.LOCKDOWN, Door.Orientation.SPAN_X, 3, 3);
        List<Door> doubleDoors = Arrays.asList(northDoor, southDoor);

        // South margin of northDoor steps into southDoor (blocked); south margin of southDoor steps into row 4 wall (#).
        pathfinder.compute(doubleDoorMap, doubleDoors, 3.5f, 2.84f);
        assertFalse(pathfinder.nextDirection(doubleDoorMap, doubleDoors, 1.5f, 1.5f, dir));
        pathfinder.compute(doubleDoorMap, doubleDoors, 3.5f, 3.84f);
        assertFalse(pathfinder.nextDirection(doubleDoorMap, doubleDoors, 1.5f, 1.5f, dir));

        pathfinder.compute(doubleDoorMap, doubleDoors, 1.5f, 1.5f);
        assertFalse(pathfinder.nextDirection(doubleDoorMap, doubleDoors, 3.5f, 2.84f, dir));
        assertFalse(pathfinder.nextDirection(doubleDoorMap, doubleDoors, 3.5f, 3.84f, dir));
        assertTrue(pathfinder.nextDirection(doubleDoorMap, doubleDoors, 3.5f, 2.16f, dir));
        assertTrue(dir.y < -0.99f);
    }

    private static void assertFollowsRouteToCellWithoutCrossingDoor(
            Pathfinder pathfinder, GameMap map, List<Door> doors, Door closedDoor,
            float startX, float startZ, int targetCol, int targetRow) {
        Vector2 dir = new Vector2();
        float x = startX;
        float z = startZ;
        int steps = 0;
        while ((int) Math.floor(x) != targetCol || (int) Math.floor(z) != targetRow) {
            assertTrue(steps < 200, "exceeded step budget at (" + x + ", " + z + ")");
            assertTrue(pathfinder.nextDirection(map, doors, x, z, dir),
                "expected valid direction from (" + x + ", " + z + ") on step " + steps);
            float nextX = x + dir.x * 0.15f;
            float nextZ = z + dir.y * 0.15f;
            assertFalse(closedDoor.intersectsSegment2D(x, z, nextX, nextZ),
                "path step crossed closed door slab from (" + x + ", " + z + ") to (" + nextX + ", " + nextZ + ")");
            assertFalse(map.isSolid((int) Math.floor(nextX), (int) Math.floor(nextZ)),
                "path step entered solid wall at (" + nextX + ", " + nextZ + ")");
            x = nextX;
            z = nextZ;
            steps++;
        }
        assertTrue(steps > 0);
        assertFalse(pathfinder.nextDirection(map, doors, x, z, dir),
            "expected false once inside resolved goal cell (" + targetCol + ", " + targetRow + ")");
    }
}

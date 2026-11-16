package io.github.brantunger.gamex.world;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import io.github.brantunger.gamex.player.Player;
import org.junit.jupiter.api.Test;

import java.util.ArrayDeque;

class GameMapTest {

    private static final float EPSILON = 1e-6f;

    @Test
    void parsesSizeWallsAndFloor() {
        GameMap map = GameMap.parse(
            "#%&#",
            "#.>#",
            "####");

        assertEquals(4, map.getWidth());
        assertEquals(3, map.getHeight());
        assertTrue(map.isSolid(0, 0));
        assertEquals('%', map.wallAt(1, 0));
        assertEquals('&', map.wallAt(2, 0));
        assertFalse(map.isSolid(1, 1));
        assertEquals(GameMap.FLOOR, map.wallAt(1, 1));
    }

    @Test
    void spawnIsTheCentreOfItsCellAndIsOpenFloor() {
        GameMap map = GameMap.parse(
            "#####",
            "#...#",
            "#..>#",
            "#####");

        assertEquals(3.5f, map.getSpawnX(), EPSILON);
        assertEquals(2.5f, map.getSpawnZ(), EPSILON);
        assertFalse(map.isSolid(3, 2));
    }

    @Test
    void spawnCharacterSetsTheFacing() {
        assertEquals(0f, GameMap.parse("^").getSpawnYaw(), EPSILON);
        assertEquals(90f, GameMap.parse(">").getSpawnYaw(), EPSILON);
        assertEquals(180f, GameMap.parse("v").getSpawnYaw(), EPSILON);
        assertEquals(270f, GameMap.parse("<").getSpawnYaw(), EPSILON);
    }

    @Test
    void targetCharactersBecomeSpawnsOnOpenFloor() {
        GameMap map = GameMap.parse(
            "#####",
            "#>oO#",
            "#####");

        assertEquals(2, map.getTargetSpawns().size());
        GameMap.TargetSpawn canister = map.getTargetSpawns().get(0);
        assertEquals(Target.Kind.CANISTER, canister.kind);
        assertEquals(2.5f, canister.x, EPSILON);
        assertEquals(1.5f, canister.z, EPSILON);
        assertEquals(Target.Kind.RING_BOARD, map.getTargetSpawns().get(1).kind);
        assertFalse(map.isSolid(2, 1));
        assertFalse(map.isSolid(3, 1));
    }

    @Test
    void everythingOutsideTheGridIsSolid() {
        GameMap map = GameMap.parse(">");

        assertFalse(map.isSolid(0, 0));
        assertTrue(map.isSolid(-1, 0));
        assertTrue(map.isSolid(0, -1));
        assertTrue(map.isSolid(1, 0));
        assertTrue(map.isSolid(0, 1));
    }

    @Test
    void rejectsBrokenLayouts() {
        assertThrows(IllegalArgumentException.class, () -> GameMap.parse());
        assertThrows(IllegalArgumentException.class, () -> GameMap.parse("###", "#>"));
        assertThrows(IllegalArgumentException.class, () -> GameMap.parse("#.#"));
        assertThrows(IllegalArgumentException.class, () -> GameMap.parse("#><#"));
        assertThrows(IllegalArgumentException.class, () -> GameMap.parse("#>?#"));
    }

    @Test
    void firstLevelSpawnsThePlayerClearOfWalls() {
        GameMap map = Levels.first();

        assertFalse(map.isSolid((int) map.getSpawnX(), (int) map.getSpawnZ()));
        assertFalse(Collision.overlapsWall(map, map.getSpawnX(), map.getSpawnZ(), Player.RADIUS));
    }

    @Test
    void firstLevelHasTargetsThatCanAllBeReachedFromTheSpawn() {
        GameMap map = Levels.first();
        boolean[][] reached = new boolean[map.getHeight()][map.getWidth()];
        ArrayDeque<int[]> open = new ArrayDeque<>();
        open.add(new int[] {(int) map.getSpawnX(), (int) map.getSpawnZ()});
        while (!open.isEmpty()) {
            int[] cell = open.poll();
            int col = cell[0];
            int row = cell[1];
            if (map.isSolid(col, row) || reached[row][col]) continue;
            reached[row][col] = true;
            open.add(new int[] {col + 1, row});
            open.add(new int[] {col - 1, row});
            open.add(new int[] {col, row + 1});
            open.add(new int[] {col, row - 1});
        }

        assertTrue(map.getTargetSpawns().size() >= 5, "a handful of targets");
        for (GameMap.TargetSpawn spawn : map.getTargetSpawns()) {
            assertTrue(reached[(int) spawn.z][(int) spawn.x], "target at " + spawn.x + ", " + spawn.z);
        }
    }
}

package io.github.brantunger.gamex.player;

import static org.junit.jupiter.api.Assertions.assertEquals;

import com.badlogic.gdx.math.Vector3;
import io.github.brantunger.gamex.world.GameMap;
import org.junit.jupiter.api.Test;

class PlayerTest {

    private static final float EPSILON = 1e-5f;

    /** Open floor spans x from 1 to 6 and z from 1 to 6. */
    private static final GameMap ROOM = GameMap.parse(
        "#######",
        "#.....#",
        "#.....#",
        "#..^..#",
        "#.....#",
        "#.....#",
        "#######");

    private static void assertDirection(float x, float y, float z, Player player) {
        Vector3 direction = player.direction(new Vector3());
        assertEquals(x, direction.x, EPSILON, "x of " + direction);
        assertEquals(y, direction.y, EPSILON, "y of " + direction);
        assertEquals(z, direction.z, EPSILON, "z of " + direction);
    }

    @Test
    void yawZeroLooksNorthAndTurningRightGoesEastSouthWest() {
        Player player = new Player(3.5f, 3.5f, 0f);
        assertDirection(0f, 0f, -1f, player);

        player.look(90f, 0f);
        assertDirection(1f, 0f, 0f, player);

        player.look(90f, 0f);
        assertDirection(0f, 0f, 1f, player);

        player.look(90f, 0f);
        assertDirection(-1f, 0f, 0f, player);
    }

    @Test
    void pitchTiltsTheDirectionUpAndKeepsItAUnitVector() {
        Player player = new Player(3.5f, 3.5f, 90f);

        player.look(0f, 30f);

        assertDirection((float) Math.cos(Math.toRadians(30.0)), 0.5f, 0f, player);
        assertEquals(1f, player.direction(new Vector3()).len(), EPSILON);
    }

    @Test
    void pitchIsClampedBothWays() {
        Player player = new Player(3.5f, 3.5f, 0f);

        player.look(0f, 500f);
        assertEquals(Player.MAX_PITCH, player.getPitch(), EPSILON);

        player.look(0f, -1000f);
        assertEquals(-Player.MAX_PITCH, player.getPitch(), EPSILON);
    }

    @Test
    void yawWrapsIntoOneTurn() {
        Player player = new Player(3.5f, 3.5f, 350f);

        player.look(20f, 0f);
        assertEquals(10f, player.getYaw(), 1e-3f);

        player.look(-30f, 0f);
        assertEquals(340f, player.getYaw(), 1e-3f);
    }

    @Test
    void forwardFollowsTheYawAndStrafeIsToTheRight() {
        Player player = new Player(3.5f, 3.5f, 90f);

        // Facing east: forwards is +x, and the right-hand side is south, +z.
        player.move(ROOM, 1f, 0f, 0.25f);
        assertEquals(3.5f + Player.MOVE_SPEED * 0.25f, player.position.x, 1e-4f);
        assertEquals(3.5f, player.position.y, 1e-4f);

        player.move(ROOM, 0f, 1f, 0.25f);
        assertEquals(3.5f + Player.MOVE_SPEED * 0.25f, player.position.y, 1e-4f);
    }

    @Test
    void diagonalMovementIsNoFasterThanStraightMovement() {
        Player player = new Player(3.5f, 3.5f, 0f);

        player.move(ROOM, 1f, 1f, 0.25f);

        assertEquals(Player.MOVE_SPEED * 0.25f, player.position.dst(3.5f, 3.5f), 1e-4f);
    }

    @Test
    void walkingIntoAWallStopsAtTheWall() {
        Player player = new Player(3.5f, 3.5f, 0f);

        for (int i = 0; i < 200; i++) player.move(ROOM, 1f, 0f, 0.05f);

        assertEquals(1f + Player.RADIUS, player.position.y, 0.005f);
        assertEquals(3.5f, player.position.x, 1e-4f);
    }

    @Test
    void eyeIsAboveThePositionOnTheFloor() {
        Player player = new Player(2.25f, 4.75f, 0f);

        Vector3 eye = player.eye(new Vector3());

        assertEquals(2.25f, eye.x, EPSILON);
        assertEquals(Player.EYE_HEIGHT, eye.y, EPSILON);
        assertEquals(4.75f, eye.z, EPSILON);
    }
}

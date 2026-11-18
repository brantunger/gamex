package io.github.brantunger.gamex.player;

import static org.junit.jupiter.api.Assertions.assertAll;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.badlogic.gdx.math.Vector3;
import io.github.brantunger.gamex.weapon.EnergyMeter;
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

    @Test
    void armorAbsorbsHalfOfDamageAndOverflowSpillsIntoHealth() {
        Player player = new Player(3.5f, 3.5f, 0f, 100f, 15f);

        // 20 raw damage: 10 absorbed by armor (leaving 5 armor), 10 lost from health.
        player.damage(20f, 3.5f, 1.5f);
        assertEquals(90f, player.getHealth(), EPSILON);
        assertEquals(5f, player.getArmor(), EPSILON);
        assertTrue(player.getHurtFlash() > 0.9f);
        assertTrue(player.hasDirectionalHurt());
        assertEquals(0f, player.getHurtRelativeYaw(), 1e-3f, "source is straight ahead (north)");

        // 30 raw damage: only 5 armor left, so 25 goes to health.
        player.damage(30f, 5.5f, 3.5f);
        assertEquals(65f, player.getHealth(), EPSILON);
        assertEquals(0f, player.getArmor(), EPSILON);
        assertEquals(90f, player.getHurtRelativeYaw(), 1e-3f, "source is to the right (east)");
    }

    @Test
    void lethalDamageKillsPlayerDropsCameraAndBlocksHealing() {
        Player player = new Player(3.5f, 3.5f, 0f);
        player.damage(150f);

        assertTrue(player.isDead());
        assertEquals(0f, player.getHealth(), EPSILON);
        assertEquals(0f, player.heal(50f), EPSILON);
        assertEquals(0f, player.addArmor(50f), EPSILON);

        player.updateTimers(1f);
        assertEquals(Player.DEAD_EYE_HEIGHT, player.eye(new Vector3()).y, EPSILON);
    }

    @Test
    void dashSpendsEnergyCoversBurstDistanceAndRespectsCooldownAndLowEnergy() {
        GameMap longHall = GameMap.parse(
            "############",
            "#..........#",
            "#.>........#",
            "#..........#",
            "############");
        Player player = new Player(2.5f, 2.5f, 90f);
        EnergyMeter energy = new EnergyMeter(100f, 0f);

        assertTrue(player.tryDash(0f, 0f, energy), "stationary dash bursts straight forward");
        assertEquals(100f - Player.DASH_COST, energy.getEnergy(), EPSILON);
        assertFalse(player.tryDash(0f, 0f, energy), "cannot dash again mid-dash");

        player.move(longHall, 0f, 0f, Player.DASH_DURATION);
        assertEquals(2.5f + Player.DASH_SPEED * Player.DASH_DURATION, player.position.x, 1e-3f);

        player.updateTimers(0.1f);
        assertFalse(player.tryDash(1f, 0f, energy), "still on cooldown");

        player.updateTimers(Player.DASH_COOLDOWN);
        energy.setEnergy(10f);
        assertFalse(player.tryDash(1f, 0f, energy), "refused on low energy");
        assertTrue(player.isDashRefused());
    }

    @Test
    void jumpArcElevatesEyeAndHitboxBelowCeilingAndLandsWithDip() {
        Player player = new Player(3.5f, 3.5f, 0f);
        Vector3 eye = new Vector3();

        assertTrue(player.tryJump());
        assertTrue(player.consumeJumpTriggered());
        assertTrue(player.isAirborne());

        float peakFeet = 0f;
        float peakEye = 0f;
        for (int step = 0; step < 60 && player.isAirborne(); step++) {
            player.move(ROOM, 0f, 0f, 1f / 60f);
            peakFeet = Math.max(peakFeet, player.getJumpHeight());
            peakEye = Math.max(peakEye, player.eye(eye).y);
            assertTrue(eye.y < GameMap.WALL_HEIGHT - 0.12f, "camera clears ceiling: " + eye.y);
        }

        assertTrue(peakFeet > 0.28f && peakFeet <= Player.MAX_JUMP_HEIGHT, "peak feet height: " + peakFeet);
        assertTrue(peakEye > Player.EYE_HEIGHT + 0.28f, "peak eye height: " + peakEye);
        assertFalse(player.isAirborne(), "landed cleanly back on floor");
        assertEquals(0f, player.getFeetY(), EPSILON);
        assertTrue(player.consumeLandedTriggered());

        player.updateTimers(0.05f);
        assertTrue(player.getLandingDip() > 0.2f, "landing knee-flex dip active right after touchdown");
        player.updateTimers(0.2f);
        assertEquals(0f, player.getLandingDip(), EPSILON);
    }

    @Test
    void midAirJumpBuffersNearTouchdownWithoutDoubleJumping() {
        Player player = new Player(3.5f, 3.5f, 0f);

        // Early mid-air Space press: rejected for double-jump and expires well before touchdown.
        assertTrue(player.tryJump());
        player.consumeJumpTriggered();
        player.move(ROOM, 0f, 0f, 0.05f);
        assertFalse(player.tryJump(), "no double-jump in mid-air");
        for (int i = 0; i < 40 && player.isAirborne(); i++) {
            player.move(ROOM, 0f, 0f, 1f / 60f);
        }
        assertFalse(player.isAirborne(), "expired early press does not auto-hop on landing");

        // Late pre-landing Space press within JUMP_BUFFER_TIME launches immediately on touchdown.
        assertTrue(player.tryJump());
        player.consumeJumpTriggered();
        player.consumeLandedTriggered();
        while (player.getVerticalVelocity() > 0f || player.getJumpHeight() > 0.09f) {
            player.move(ROOM, 0f, 0f, 1f / 60f);
        }
        assertTrue(player.isAirborne());
        assertFalse(player.tryJump(), "queued in jump buffer just above floor");

        boolean relaunched = false;
        for (int i = 0; i < 10; i++) {
            player.move(ROOM, 0f, 0f, 1f / 60f);
            if (player.consumeLandedTriggered() && player.consumeJumpTriggered()) {
                relaunched = true;
                break;
            }
        }
        assertTrue(relaunched, "buffered jump fires automatically on touchdown");
        assertTrue(player.isAirborne());
    }

    @Test
    void midAirDashCombosWithJumpAndHoldsHorizontalThrusterAltitude() {
        GameMap longHall = GameMap.parse(
            "##############",
            "#............#",
            "#.>..........#",
            "#............#",
            "##############");
        Player player = new Player(2.5f, 2.5f, 90f);
        EnergyMeter energy = new EnergyMeter(100f, 0f);

        assertTrue(player.tryJump());
        while (player.getVerticalVelocity() > 0f) {
            player.move(longHall, 1f, 0f, 1f / 60f);
        }
        float apexHeight = player.getJumpHeight();
        float xAtDashStart = player.position.x;

        assertTrue(player.tryDash(1f, 0f, energy));
        assertEquals(100f - Player.DASH_COST, energy.getEnergy(), EPSILON);
        player.move(longHall, 1f, 0f, Player.DASH_DURATION);

        assertEquals(apexHeight, player.getJumpHeight(), 1e-4f, "downward fall pauses during horizontal air-dash");
        assertEquals(xAtDashStart + Player.DASH_SPEED * Player.DASH_DURATION, player.position.x, 1e-3f);

        for (int i = 0; i < 40 && player.isAirborne(); i++) {
            player.move(longHall, 1f, 0f, 1f / 60f);
        }
        assertFalse(player.isAirborne());
    }

    /**
     * Jumps on the spot in steps of {@code dt} and presses jump again at the start of the given step.
     * @return whether that second press launched a jump on touchdown
     */
    private static boolean bufferedPressFiresOnTouchdown(float dt, int pressStep) {
        Player player = new Player(3.5f, 3.5f, 0f);
        assertTrue(player.tryJump());
        player.consumeJumpTriggered();
        for (int step = 0; step < 200; step++) {
            if (step == pressStep) assertFalse(player.tryJump(), "still airborne at step " + step);
            player.move(ROOM, 0f, 0f, dt);
            if (player.consumeLandedTriggered()) return player.consumeJumpTriggered();
        }
        throw new AssertionError("never landed");
    }

    @Test
    void jumpBufferWindowIsTheSameAtLowFrameRates() {
        // The jump lasts 2 * 2.68 / 11.2 = 0.4786 s. A press 0.4 s in is 0.0786 s early, inside the 0.10 s window.
        assertAll(
            () -> assertTrue(bufferedPressFiresOnTouchdown(1f / 60f, 24), "0.0786 s early at 60 fps"),
            () -> assertTrue(bufferedPressFiresOnTouchdown(1f / 30f, 12), "0.0786 s early at 30 fps"),
            () -> assertTrue(bufferedPressFiresOnTouchdown(0.05f, 8), "0.0786 s early at 20 fps"),
            // A press 0.3833 s in is 0.0952 s early: six whole 60 fps steps pass before the one that lands.
            () -> assertTrue(bufferedPressFiresOnTouchdown(1f / 60f, 23), "0.0952 s early at 60 fps"));
    }

    @Test
    void jumpPressedMoreThanTheBufferTimeBeforeTouchdownIsDropped() {
        assertFalse(bufferedPressFiresOnTouchdown(1f / 60f, 21), "0.1286 s early at 60 fps");
        assertFalse(bufferedPressFiresOnTouchdown(1f / 30f, 11), "0.1119 s early at 30 fps");
        assertFalse(bufferedPressFiresOnTouchdown(0.05f, 7), "0.1286 s early at 20 fps");
        // In 0.03 s steps a press 0.36 s in is 0.1186 s early, though the timer still has 0.01 s left when the
        // step that lands begins.
        assertFalse(bufferedPressFiresOnTouchdown(0.03f, 12), "0.1186 s early at 33 fps");
    }
}

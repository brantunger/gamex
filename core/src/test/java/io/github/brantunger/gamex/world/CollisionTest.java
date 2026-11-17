package io.github.brantunger.gamex.world;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.badlogic.gdx.math.Vector2;
import org.junit.jupiter.api.Test;

import java.util.Collections;
import java.util.List;

class CollisionTest {

    private static final float RADIUS = 0.25f;
    /** How close to a wall a blocked circle has to end up. */
    private static final float CONTACT = 0.005f;

    /** A room whose open floor spans x from 1 to 6 and z from 1 to 4. */
    private static final GameMap ROOM = GameMap.parse(
        "#######",
        "#.....#",
        "#.>...#",
        "#.....#",
        "#######");

    /** The same room with a one-cell pillar spanning x from 4 to 5 and z from 2 to 3. */
    private static final GameMap PILLAR = GameMap.parse(
        "#######",
        "#.....#",
        "#.>.#.#",
        "#.....#",
        "#######");

    @Test
    void movesFreelyInOpenFloor() {
        Vector2 position = new Vector2(2.5f, 2.5f);

        Collision.move(ROOM, position, 1f, 0.5f, RADIUS);

        assertEquals(3.5f, position.x, 1e-4f);
        assertEquals(3f, position.y, 1e-4f);
    }

    @Test
    void slidesAlongAWallWhenPushedIntoItDiagonally() {
        Vector2 position = new Vector2(2.5f, 2.5f);

        // North is blocked by the wall whose face is at z = 1; the eastward part of the move must survive.
        Collision.move(ROOM, position, 1f, -3f, RADIUS);

        assertEquals(3.5f, position.x, 1e-4f);
        assertEquals(1f + RADIUS, position.y, CONTACT);
        assertTrue(position.y >= 1f + RADIUS, "must not sink into the wall");
    }

    @Test
    void stopsInACorner() {
        Vector2 position = new Vector2(2.5f, 2.5f);

        Collision.move(ROOM, position, -5f, -5f, RADIUS);

        assertEquals(1f + RADIUS, position.x, CONTACT);
        assertEquals(1f + RADIUS, position.y, CONTACT);
    }

    @Test
    void doesNotTunnelThroughTheOuterWallHoweverLargeTheStep() {
        Vector2 position = new Vector2(2.5f, 2.5f);

        Collision.move(ROOM, position, 1000f, 0f, RADIUS);

        assertEquals(6f - RADIUS, position.x, CONTACT);
        assertEquals(2.5f, position.y, 1e-4f);
    }

    @Test
    void doesNotTunnelThroughAOneCellPillar() {
        Vector2 position = new Vector2(2.5f, 2.5f);

        // A single step of 3 would land at x = 5.5, in the open cell behind the pillar.
        Collision.move(PILLAR, position, 3f, 0f, RADIUS);

        assertEquals(4f - RADIUS, position.x, CONTACT);
    }

    @Test
    void liveTargetStopsACircleWalkingStraightAtIt() {
        Target canister = new Target(Target.Kind.CANISTER, 4f, 2.5f);
        Vector2 position = new Vector2(2.5f, 2.5f);

        Collision.move(ROOM, Collections.singletonList(canister), position, 3f, 0f, RADIUS);

        assertEquals(4f - canister.getRadius() - RADIUS, position.x, 1e-3f);
        assertEquals(2.5f, position.y, 1e-3f);
    }

    @Test
    void circleSlidesRoundATargetItMeetsOffCentre() {
        Target canister = new Target(Target.Kind.CANISTER, 4f, 2.5f);
        List<Target> solids = Collections.singletonList(canister);
        Vector2 position = new Vector2(2.5f, 2.3f);

        Collision.move(ROOM, solids, position, 3f, 0f, RADIUS);

        assertTrue(position.x > 4f + canister.getRadius() + RADIUS, "got past the target, to x = " + position.x);
        assertTrue(position.y < 2.3f, "was pushed to the north side, to z = " + position.y);
        assertFalse(Collision.overlapsSolid(solids, position, RADIUS));
        assertFalse(Collision.overlapsWall(ROOM, position.x, position.y, RADIUS));
    }

    @Test
    void destroyedTargetIsNotSolid() {
        Target canister = new Target(Target.Kind.CANISTER, 4f, 2.5f);
        canister.damage(1000f);
        Vector2 position = new Vector2(2.5f, 2.5f);

        Collision.move(ROOM, Collections.singletonList(canister), position, 3f, 0f, RADIUS);

        assertEquals(5.5f, position.x, 1e-4f);
        assertEquals(2.5f, position.y, 1e-4f);
    }

    @Test
    void targetNeverPushesACircleIntoAWall() {
        // The gap between the canister and the north wall is 0.4 wide, too narrow for a circle 0.5 across.
        Target canister = new Target(Target.Kind.CANISTER, 3.5f, 1.6f);
        List<Target> solids = Collections.singletonList(canister);
        Vector2 position = new Vector2(2.5f, 1.3f);

        Collision.move(ROOM, solids, position, 3f, 0f, RADIUS);

        assertTrue(position.x < 3.5f, "stopped before the gap, at x = " + position.x);
        assertFalse(Collision.overlapsWall(ROOM, position.x, position.y, RADIUS));
        assertFalse(Collision.overlapsSolid(solids, position, RADIUS));
    }

    @Test
    void circleRoundsAPillarCornerInsteadOfTreatingItAsASquare() {
        // The pillar's north-west corner is at (4, 2). The circle's bounding square overlaps it in both cases.
        assertFalse(Collision.overlapsWall(PILLAR, 3.8f, 1.8f, RADIUS), "0.283 from the corner");
        assertTrue(Collision.overlapsWall(PILLAR, 3.85f, 1.85f, RADIUS), "0.212 from the corner");
    }

    @Test
    void overlapIsMeasuredToTheWallFace() {
        assertFalse(Collision.overlapsWall(ROOM, 1.26f, 2.5f, RADIUS));
        assertTrue(Collision.overlapsWall(ROOM, 1.24f, 2.5f, RADIUS));
    }

    @Test
    void closedDoorBlocksMovementUntilClearedPastThreshold() {
        Door door = new Door(Door.LockKind.NONE, Door.Orientation.SPAN_Z, 3, 2);
        List<Door> doors = Collections.singletonList(door);
        Vector2 position = new Vector2(2.5f, 2.5f);

        Collision.move(ROOM, Collections.<Damageable>emptyList(), doors, position, 2.5f, 0f, RADIUS);
        assertTrue(position.x < 3.5f - Door.HALF_THICKNESS, "stopped on west face of door: " + position.x);

        assertTrue(door.open());
        door.update(Door.OPEN_DURATION);
        Collision.move(ROOM, Collections.<Damageable>emptyList(), doors, position, 2.5f, 0f, RADIUS);
        assertTrue(position.x > 4.5f, "passes freely once door has opened: " + position.x);
    }
}

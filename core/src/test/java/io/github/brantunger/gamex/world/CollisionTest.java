package io.github.brantunger.gamex.world;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.badlogic.gdx.math.Vector2;
import io.github.brantunger.gamex.player.Player;
import io.github.brantunger.gamex.weapon.EnergyMeter;
import org.junit.jupiter.api.Test;

import java.util.Arrays;
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

    /** A 1.0u-wide north-south corridor whose open floor spans x from 1 to 2 and z from 1 to 6. */
    private static final GameMap CORRIDOR = GameMap.parse(
        "###",
        "#.#",
        "#.#",
        "#^#",
        "#.#",
        "#.#",
        "###");

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

    @Test
    void overlappingPlayerInNarrowCorridorWalksAwayAtHighRefreshRateUntilSeparated() {
        Enemy drone = new Enemy(Enemy.Kind.DRONE, 1.58f, 3.00f, 0f);
        List<Enemy> solids = Collections.singletonList(drone);
        Player player = new Player(1.40f, 3.00f, 180f);
        float reach = Player.RADIUS + drone.getRadius();

        double prevDistSq = distSq(player.position.x, player.position.y, drone.getX(), drone.getZ());
        assertTrue(prevDistSq < reach * reach, "starts overlapping inside reach");

        // Walk south (+z) in tiny 240 Hz steps; separation must strictly increase until >= reach.
        for (int i = 0; i < 60; i++) {
            player.move(CORRIDOR, solids, 1f, 0f, 1f / 240f);
            assertFalse(Collision.overlapsWall(CORRIDOR, player.position.x, player.position.y, Player.RADIUS));
            double nowDistSq = distSq(player.position.x, player.position.y, drone.getX(), drone.getZ());
            assertTrue(nowDistSq > prevDistSq, "distance must increase on step " + i);
            prevDistSq = nowDistSq;
        }

        assertTrue(player.position.dst(drone.getX(), drone.getZ()) >= reach,
            "completely separated from overlapping enemy: dst=" + player.position.dst(drone.getX(), drone.getZ()));
        assertFalse(Collision.overlapsSolid(solids, player.position, Player.RADIUS));
    }

    @Test
    void overlappingPlayerInNarrowCorridorDashesAwayStraightAndAngledUntilSeparated() {
        Enemy drone = new Enemy(Enemy.Kind.DRONE, 1.58f, 3.00f, 0f);
        List<Enemy> solids = Collections.singletonList(drone);
        float reach = Player.RADIUS + drone.getRadius();

        // Cardinal dash north (-z) away from the overlapping drone.
        Player straightPlayer = new Player(1.40f, 3.00f, 0f);
        EnergyMeter straightEnergy = new EnergyMeter(100f, 0f);
        assertTrue(straightPlayer.tryDash(1f, 0f, straightEnergy));
        for (int i = 0; i < 10; i++) {
            straightPlayer.move(CORRIDOR, solids, 1f, 0f, 1f / 60f);
            assertFalse(Collision.overlapsWall(CORRIDOR, straightPlayer.position.x, straightPlayer.position.y,
                Player.RADIUS));
        }
        assertTrue(straightPlayer.position.y < 2.0f, "dash covered full open corridor distance");
        assertTrue(straightPlayer.position.dst(drone.getX(), drone.getZ()) >= reach);
        assertFalse(Collision.overlapsSolid(solids, straightPlayer.position, Player.RADIUS));

        // Slightly angled dash (168 deg: south with a small +x component toward the drone) still slides down +z.
        Player angledPlayer = new Player(1.40f, 3.00f, 168f);
        EnergyMeter angledEnergy = new EnergyMeter(100f, 0f);
        assertTrue(angledPlayer.tryDash(1f, 0f, angledEnergy));
        for (int i = 0; i < 12; i++) {
            angledPlayer.move(CORRIDOR, solids, 1f, 0f, 1f / 60f);
            assertFalse(Collision.overlapsWall(CORRIDOR, angledPlayer.position.x, angledPlayer.position.y,
                Player.RADIUS));
        }
        assertTrue(angledPlayer.position.dst(drone.getX(), drone.getZ()) >= reach,
            "angled corridor dash still separates cleanly: dst=" + angledPlayer.position.dst(drone.getX(), drone.getZ()));
        assertFalse(Collision.overlapsSolid(solids, angledPlayer.position, Player.RADIUS));
    }

    @Test
    void overlappingPlayerEscapesFromRoomCornerAndBesideClosedBulkheadDoor() {
        // 1. Pinned in the north-west corner of ROOM while overlapping an enemy to the east.
        Enemy cornerDrone = new Enemy(Enemy.Kind.DRONE, 1.48f, 1.26f, 270f);
        List<Enemy> cornerSolids = Collections.singletonList(cornerDrone);
        float reach = Player.RADIUS + cornerDrone.getRadius();
        Player cornerPlayer = new Player(1.26f, 1.26f, 180f);
        for (int i = 0; i < 20; i++) {
            // Walk south while also strafing west into the corner wall.
            cornerPlayer.move(ROOM, cornerSolids, 1f, 1f, 1f / 60f);
            assertFalse(Collision.overlapsWall(ROOM, cornerPlayer.position.x, cornerPlayer.position.y, Player.RADIUS));
        }
        assertTrue(cornerPlayer.position.dst(cornerDrone.getX(), cornerDrone.getZ()) >= reach);
        assertFalse(Collision.overlapsSolid(cornerSolids, cornerPlayer.position, Player.RADIUS));

        // 2. Overlapping an enemy right beside a closed SPAN_X bulkhead door in CORRIDOR.
        Door lockedDoor = new Door(Door.LockKind.AMBER_KEY, Door.Orientation.SPAN_X, 1, 1);
        List<Door> doors = Collections.singletonList(lockedDoor);
        Enemy doorDrone = new Enemy(Enemy.Kind.DRONE, 1.58f, 1.85f, 180f);
        List<Enemy> doorSolids = Collections.singletonList(doorDrone);

        // Trying to dash north (-z) into the closed door stops at the slab face without clipping.
        Player intoDoorPlayer = new Player(1.40f, 1.85f, 0f);
        EnergyMeter energy = new EnergyMeter(100f, 0f);
        assertTrue(intoDoorPlayer.tryDash(1f, 0f, energy));
        intoDoorPlayer.move(CORRIDOR, doorSolids, doors, 1f, 0f, Player.DASH_DURATION);
        assertFalse(Collision.overlapsWall(CORRIDOR, doors, intoDoorPlayer.position.x, intoDoorPlayer.position.y,
            Player.RADIUS));
        assertTrue(intoDoorPlayer.position.y >= 1.5f + Door.HALF_THICKNESS + Player.RADIUS - 1e-4f);

        // Starting right against the closed door face while overlapping the enemy, walking south (+z) separates cleanly.
        Enemy flushDoorDrone = new Enemy(Enemy.Kind.DRONE, 1.58f, 1.84f, 180f);
        List<Enemy> flushDoorSolids = Collections.singletonList(flushDoorDrone);
        Player awayFromDoorPlayer = new Player(1.40f, 1.841f, 180f);
        assertFalse(Collision.overlapsWall(CORRIDOR, doors, awayFromDoorPlayer.position.x,
            awayFromDoorPlayer.position.y, Player.RADIUS));
        assertTrue(Collision.overlapsSolid(flushDoorSolids, awayFromDoorPlayer.position, Player.RADIUS));
        for (int i = 0; i < 20; i++) {
            awayFromDoorPlayer.move(CORRIDOR, flushDoorSolids, doors, 1f, 0f, 1f / 60f);
            assertFalse(Collision.overlapsWall(CORRIDOR, doors, awayFromDoorPlayer.position.x,
                awayFromDoorPlayer.position.y, Player.RADIUS));
        }
        assertTrue(awayFromDoorPlayer.position.dst(flushDoorDrone.getX(), flushDoorDrone.getZ()) >= reach);
        assertFalse(Collision.overlapsSolid(flushDoorSolids, awayFromDoorPlayer.position, Player.RADIUS));
    }

    @Test
    void movingDeeperOrChordingThroughOverlappingSolidDoesNotWorsenPenetration() {
        Enemy drone = new Enemy(Enemy.Kind.DRONE, 1.58f, 3.00f, 0f);
        List<Enemy> solids = Collections.singletonList(drone);
        float reach = RADIUS + drone.getRadius();

        // Pressing straight toward the overlapping solid's centre must never reduce separation.
        Vector2 intoCenter = new Vector2(1.40f, 3.00f);
        double initialDistSq = distSq(intoCenter.x, intoCenter.y, drone.getX(), drone.getZ());
        Collision.move(CORRIDOR, solids, intoCenter, 0.02f, 0f, RADIUS);
        assertTrue(distSq(intoCenter.x, intoCenter.y, drone.getX(), drone.getZ()) >= initialDistSq);
        Collision.move(CORRIDOR, solids, intoCenter, 0.50f, 0f, RADIUS);
        assertTrue(distSq(intoCenter.x, intoCenter.y, drone.getX(), drone.getZ()) >= initialDistSq);

        // Starting at z = z0 - 0.02 and trying to walk or dash past the drone along +z is blocked,
        // while moving away along -z separates cleanly.
        Vector2 slightlyNorth = new Vector2(1.40f, 2.98f);
        double northDistSq = distSq(slightlyNorth.x, slightlyNorth.y, drone.getX(), drone.getZ());
        Collision.move(CORRIDOR, solids, slightlyNorth, 0f, 0.05f, RADIUS);
        assertEquals(2.98f, slightlyNorth.y, 1e-6f, "walk into deeper overlap is blocked");
        Collision.move(CORRIDOR, solids, slightlyNorth, 0f, 1.60f, RADIUS);
        assertEquals(2.98f, slightlyNorth.y, 1e-6f, "dash cannot chord across z0 through deeper overlap");
        assertTrue(distSq(slightlyNorth.x, slightlyNorth.y, drone.getX(), drone.getZ()) >= northDistSq);

        Collision.move(CORRIDOR, solids, slightlyNorth, 0f, -1.60f, RADIUS);
        assertTrue(slightlyNorth.dst(drone.getX(), drone.getZ()) >= reach, "moving away along -z succeeds cleanly");
        assertFalse(Collision.overlapsWall(CORRIDOR, slightlyNorth.x, slightlyNorth.y, RADIUS));
    }

    @Test
    void escapingOverlapWithSolidACannotClipIntoAdjacentSolidBOrWall() {
        Enemy droneA = new Enemy(Enemy.Kind.DRONE, 1.58f, 3.00f, 0f);
        Target canisterB = new Target(Target.Kind.CANISTER, 1.50f, 3.60f);
        List<Damageable> solids = Arrays.asList(droneA, canisterB);

        // Moving south (+z) increases clearance from A until stopped cleanly by non-overlapping solid B.
        Vector2 southPos = new Vector2(1.40f, 3.00f);
        double initialDistSqA = distSq(southPos.x, southPos.y, droneA.getX(), droneA.getZ());
        Collision.move(CORRIDOR, solids, southPos, 0f, 2.0f, RADIUS);
        assertTrue(distSq(southPos.x, southPos.y, droneA.getX(), droneA.getZ()) > initialDistSqA,
            "progressed away from solid A before meeting solid B");
        assertTrue(southPos.dst(canisterB.getX(), canisterB.getZ()) >= RADIUS + canisterB.getRadius() - 1e-5f,
            "must not enter non-overlapping solid B");
        assertFalse(Collision.overlapsSolid(Collections.singletonList(canisterB), southPos, RADIUS));
        assertFalse(Collision.overlapsWall(CORRIDOR, southPos.x, southPos.y, RADIUS));

        // Escaping north (-z) from the same initial overlap where no second solid blocks separates completely.
        Vector2 northPos = new Vector2(1.40f, 3.00f);
        Collision.move(CORRIDOR, solids, northPos, 0f, -2.0f, RADIUS);
        assertTrue(northPos.dst(droneA.getX(), droneA.getZ()) >= RADIUS + droneA.getRadius());
        assertFalse(Collision.overlapsSolid(solids, northPos, RADIUS));
        assertFalse(Collision.overlapsWall(CORRIDOR, northPos.x, northPos.y, RADIUS));
    }

    @Test
    void nonOverlappingCircleRemainsBlockedBySolidInNarrowCorridorAndBesideClosedDoor() {
        Enemy drone = new Enemy(Enemy.Kind.DRONE, 1.58f, 3.00f, 0f);
        List<Enemy> corridorSolids = Collections.singletonList(drone);
        Vector2 position = new Vector2(1.40f, 2.00f);

        Collision.move(CORRIDOR, corridorSolids, position, 0f, 2.5f, RADIUS);
        assertTrue(position.y < 3.00f, "stopped on north side of corridor blocker: z=" + position.y);
        assertFalse(Collision.overlapsSolid(corridorSolids, position, RADIUS));
        assertFalse(Collision.overlapsWall(CORRIDOR, position.x, position.y, RADIUS));

        // Also verify a solid beside a closed SPAN_Z door blocks a non-overlapping circle until the door opens.
        Door door = new Door(Door.LockKind.NONE, Door.Orientation.SPAN_Z, 3, 1);
        List<Door> doors = Collections.singletonList(door);
        Target canister = new Target(Target.Kind.CANISTER, 2.80f, 1.50f);
        List<Target> roomSolids = Collections.singletonList(canister);
        Vector2 roomPos = new Vector2(3.10f, 2.30f);

        Collision.move(ROOM, roomSolids, doors, roomPos, 0f, -1.0f, RADIUS);
        assertTrue(roomPos.y > 1.50f, "stopped south of solid/closed-door pinch: z=" + roomPos.y);
        assertFalse(Collision.overlapsSolid(roomSolids, roomPos, RADIUS));
        assertFalse(Collision.overlapsWall(ROOM, doors, roomPos.x, roomPos.y, RADIUS));

        assertTrue(door.open());
        door.update(Door.OPEN_DURATION);
        Collision.move(ROOM, roomSolids, doors, roomPos, 0f, -1.0f, RADIUS);
        assertTrue(roomPos.y < 1.50f, "slides past canister once adjacent door opens: z=" + roomPos.y);
        assertFalse(Collision.overlapsSolid(roomSolids, roomPos, RADIUS));
        assertFalse(Collision.overlapsWall(ROOM, doors, roomPos.x, roomPos.y, RADIUS));
    }

    @Test
    void coincidentDeadCentreOverlapNearCornerEscapesAlongOpenAxis() {
        Enemy drone = new Enemy(Enemy.Kind.DRONE, 1.26f, 1.26f, 180f);
        List<Enemy> solids = Collections.singletonList(drone);
        Vector2 position = new Vector2(1.26f, 1.26f);

        Collision.move(ROOM, solids, position, -0.4f, 1.0f, RADIUS);

        assertTrue(position.dst(drone.getX(), drone.getZ()) >= RADIUS + drone.getRadius());
        assertFalse(Collision.overlapsSolid(solids, position, RADIUS));
        assertFalse(Collision.overlapsWall(ROOM, position.x, position.y, RADIUS));
    }

    private static double distSq(float ax, float az, float bx, float bz) {
        double dx = (double) ax - bx;
        double dz = (double) az - bz;
        return dx * dx + dz * dz;
    }
}

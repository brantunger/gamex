package io.github.brantunger.gamex.world;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertTrue;

import io.github.brantunger.gamex.TestTuning;
import com.badlogic.gdx.math.Vector3;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

class HitscanTest {

    private static final float EPSILON = 1e-4f;
    private static final float RANGE = 50f;

    /**
     * Open floor spans x from 1 to 6 and z from 1 to 4, with a panel pillar spanning x from 4 to 5 and z from 2 to
     * 3. Rays start at the centre of the spawn cell, (2.5, 2.5), at half the wall height.
     */
    private static final GameMap MAP = GameMap.parse(
        "#######",
        "#.....#",
        "#.>.%.#",
        "#.....#",
        "#######");
    private static final Vector3 ORIGIN = new Vector3(2.5f, 0.5f, 2.5f);
    private static final List<Target> NO_TARGETS = Collections.emptyList();

    private static RayHit cast(float x, float y, float z, List<Target> targets) {
        return Hitscan.cast(MAP, targets, ORIGIN, new Vector3(x, y, z).nor(), RANGE);
    }

    private static void assertVector(float x, float y, float z, Vector3 actual) {
        assertEquals(x, actual.x, EPSILON, "x of " + actual);
        assertEquals(y, actual.y, EPSILON, "y of " + actual);
        assertEquals(z, actual.z, EPSILON, "z of " + actual);
    }

    @Test
    void eastRayHitsThePillarsWestFace() {
        RayHit hit = cast(1f, 0f, 0f, NO_TARGETS);

        assertEquals(RayHit.Kind.WALL, hit.kind);
        assertEquals(1.5f, hit.distance, EPSILON);
        assertVector(4f, 0.5f, 2.5f, hit.point);
        assertVector(-1f, 0f, 0f, hit.normal);
        assertEquals('%', hit.wallType);
        assertNull(hit.target);
    }

    @Test
    void axisRaysHitTheOuterWallsWithNormalsFacingBack() {
        RayHit north = cast(0f, 0f, -1f, NO_TARGETS);
        assertEquals(1.5f, north.distance, EPSILON);
        assertVector(2.5f, 0.5f, 1f, north.point);
        assertVector(0f, 0f, 1f, north.normal);
        assertEquals('#', north.wallType);

        RayHit south = cast(0f, 0f, 1f, NO_TARGETS);
        assertEquals(1.5f, south.distance, EPSILON);
        assertVector(0f, 0f, -1f, south.normal);

        RayHit west = cast(-1f, 0f, 0f, NO_TARGETS);
        assertEquals(1.5f, west.distance, EPSILON);
        assertVector(1f, 0.5f, 2.5f, west.point);
        assertVector(1f, 0f, 0f, west.normal);
    }

    @Test
    void slantedRayPassesThePillarAndHitsTheNorthWall() {
        // Two cells east for every cell north: the ray crosses x = 4 at z = 1.75, just north of the pillar, and
        // reaches the north wall's face at z = 1 after going 3 east and 1.5 north.
        RayHit hit = cast(2f, 0f, -1f, NO_TARGETS);

        assertEquals(RayHit.Kind.WALL, hit.kind);
        assertEquals((float) Math.sqrt(3.0 * 3.0 + 1.5 * 1.5), hit.distance, EPSILON);
        assertVector(5.5f, 0.5f, 1f, hit.point);
        assertVector(0f, 0f, 1f, hit.normal);
        assertEquals('#', hit.wallType);
    }

    @Test
    void steepDownwardRayHitsTheFloorBeforeTheWall() {
        // 45 degrees down: the floor is 0.5 below, so the ray lands 0.5 further east.
        RayHit hit = cast(1f, -1f, 0f, NO_TARGETS);

        assertEquals(RayHit.Kind.FLOOR, hit.kind);
        assertEquals((float) Math.sqrt(0.5), hit.distance, EPSILON);
        assertVector(3f, 0f, 2.5f, hit.point);
        assertVector(0f, 1f, 0f, hit.normal);
    }

    @Test
    void steepUpwardRayHitsTheCeiling() {
        RayHit hit = cast(0f, 1f, -1f, NO_TARGETS);

        assertEquals(RayHit.Kind.CEILING, hit.kind);
        assertVector(2.5f, 1f, 2f, hit.point);
        assertVector(0f, -1f, 0f, hit.normal);
    }

    @Test
    void shallowDownwardRayStillHitsTheWall() {
        // Drops 0.15 over the 1.5 to the pillar, so it is still above the floor there.
        RayHit hit = cast(1f, -0.1f, 0f, NO_TARGETS);

        assertEquals(RayHit.Kind.WALL, hit.kind);
        assertVector(4f, 0.35f, 2.5f, hit.point);
    }

    @Test
    void nothingIsHitBeyondTheRange() {
        RayHit hit = Hitscan.cast(MAP, NO_TARGETS, ORIGIN, new Vector3(1f, 0f, 0f), 1f);

        assertEquals(RayHit.Kind.NONE, hit.kind);
        assertFalse(hit.isHit());
    }

    @Test
    void targetInFrontOfTheWallIsHitInsteadOfTheWall() {
        Target target = new Target(Target.Kind.CANISTER, 3.5f, 2.5f);

        RayHit hit = cast(1f, 0f, 0f, Collections.singletonList(target));

        assertEquals(RayHit.Kind.TARGET, hit.kind);
        assertSame(target, hit.target);
        assertEquals(1f - target.getRadius(), hit.distance, EPSILON);
        assertVector(3.5f - target.getRadius(), 0.5f, 2.5f, hit.point);
        assertVector(-1f, 0f, 0f, hit.normal);
    }

    @Test
    void wallInFrontOfTheTargetIsHitInsteadOfTheTarget() {
        Target behindPillar = new Target(Target.Kind.CANISTER, 5.5f, 2.5f);

        RayHit hit = cast(1f, 0f, 0f, Collections.singletonList(behindPillar));

        assertEquals(RayHit.Kind.WALL, hit.kind);
        assertEquals(1.5f, hit.distance, EPSILON);
        assertNull(hit.target);
    }

    @Test
    void nearerOfTwoTargetsIsHitWhateverTheirOrder() {
        Target near = new Target(Target.Kind.CANISTER, 3.2f, 2.5f);
        Target far = new Target(Target.Kind.CANISTER, 3.8f, 2.5f);
        List<Target> farFirst = new ArrayList<>();
        farFirst.add(far);
        farFirst.add(near);

        List<Target> nearFirst = new ArrayList<>();
        nearFirst.add(near);
        nearFirst.add(far);

        assertSame(near, cast(1f, 0f, 0f, farFirst).target);
        assertSame(near, cast(1f, 0f, 0f, nearFirst).target);
    }

    @Test
    void rayStartingInsideATargetHitsItAtDistanceZero() {
        // The canister's axis is 0.1 from the ray's origin, well inside its 0.2 radius; the ray points away from it.
        Target target = new Target(Target.Kind.CANISTER, 2.4f, 2.5f);

        RayHit hit = cast(1f, 0f, 0f, Collections.singletonList(target));

        assertEquals(RayHit.Kind.TARGET, hit.kind);
        assertSame(target, hit.target);
        assertEquals(0f, hit.distance, EPSILON);
        assertVector(2.5f, 0.5f, 2.5f, hit.point);
        assertVector(-1f, 0f, 0f, hit.normal);
    }

    @Test
    void rayStartingAboveATargetItStandsInsideOfDoesNotHitIt() {
        Target target = new Target(Target.Kind.CANISTER, 2.4f, 2.5f);
        Vector3 above = new Vector3(2.5f, 0.9f, 2.5f);

        RayHit hit = Hitscan.cast(MAP, Collections.singletonList(target), above, new Vector3(1f, 0f, 0f), RANGE);

        assertEquals(RayHit.Kind.WALL, hit.kind);
    }

    @Test
    void destroyedTargetNoLongerStopsTheRay() {
        Target target = new Target(Target.Kind.CANISTER, 3.5f, 2.5f);
        target.damage(target.getHealth());

        RayHit hit = cast(1f, 0f, 0f, Collections.singletonList(target));

        assertEquals(RayHit.Kind.WALL, hit.kind);
    }

    @Test
    void rayPassesOverOrBesideATarget() {
        Target target = new Target(Target.Kind.CANISTER, 3.5f, 2.5f);
        List<Target> targets = Collections.singletonList(target);

        // Rising 0.2 per unit: at the canister, 0.8 away, the ray is at 0.66, above its 0.55 height.
        assertEquals(RayHit.Kind.WALL, cast(1f, 0.2f, 0f, targets).kind);
        // Half a unit north per unit east: the ray passes 0.45 from the canister's axis.
        assertEquals(RayHit.Kind.WALL, cast(1f, 0f, -0.5f, targets).kind);
    }

    @Test
    void lineOfSightSeesOpenFloorAndStopsAtSolidPillar() {
        assertTrue(Hitscan.hasLineOfSight(MAP, 2.5f, 2.5f, 2.5f, 1.2f));
        assertTrue(Hitscan.hasLineOfSight(MAP, 2.5f, 1.5f, 5.5f, 1.5f));
        assertFalse(Hitscan.hasLineOfSight(MAP, 2.5f, 2.5f, 5.5f, 2.5f), "blocked by panel pillar at (4,2)");
    }

    @Test
    void closedDoorBlocksRaysAndSightUntilItLiftsClear() {
        Door door = new Door(Door.LockKind.NONE, Door.Orientation.SPAN_Z, 3, 1);
        List<Door> doors = Collections.singletonList(door);
        Vector3 corridorOrigin = new Vector3(1.5f, 0.5f, 1.5f);

        RayHit closedHit = Hitscan.cast(MAP, NO_TARGETS, doors, corridorOrigin, new Vector3(1f, 0f, 0f), RANGE);
        assertEquals(RayHit.Kind.DOOR, closedHit.kind);
        assertSame(door, closedHit.door);
        assertEquals(2f - Door.HALF_THICKNESS, closedHit.distance, EPSILON);
        assertFalse(Hitscan.hasLineOfSight(MAP, doors, 1.5f, 1.5f, 5.5f, 1.5f));

        door.open();
        door.update(Door.OPEN_DURATION * 0.8f);
        assertTrue(door.getBottomY() > 0.5f);
        RayHit openHit = Hitscan.cast(MAP, NO_TARGETS, doors, corridorOrigin, new Vector3(1f, 0f, 0f), RANGE);
        assertEquals(RayHit.Kind.WALL, openHit.kind);
        assertTrue(Hitscan.hasLineOfSight(MAP, doors, 1.5f, 1.5f, 5.5f, 1.5f));
    }

    @Test
    void castPiercingPenetratesLinedUpTargetsInOrderAndStopsAtWallsClosedDoorsAndConsoles() {
        GameMap lane = GameMap.parse(
            "##########",
            "#>.......#",
            "##########");
        Vector3 start = new Vector3(1.5f, 0.45f, 1.5f);
        Vector3 east = new Vector3(1f, 0f, 0f);

        Target tFar = new Target(Target.Kind.CANISTER, 7.5f, 1.5f);
        Target tNear = new Target(Target.Kind.CANISTER, 3.2f, 1.5f);
        Target tMid = new Target(Target.Kind.CANISTER, 5.1f, 1.5f);
        Target deadTarget = new Target(Target.Kind.CANISTER, 4.1f, 1.5f);
        deadTarget.damage(deadTarget.getHealth());

        List<Damageable> objects = new ArrayList<>();
        objects.add(tFar);
        objects.add(deadTarget);
        objects.add(tNear);
        objects.add(tMid);

        // All three live targets pierced front-to-back up to east wall at x = 9.0.
        Hitscan.PiercingResult allHit = Hitscan.castPiercing(lane, objects, Collections.emptyList(),
            start, east, 50f);
        assertEquals(3, allHit.targetHits.size());
        assertSame(tNear, allHit.targetHits.get(0).target);
        assertSame(tMid, allHit.targetHits.get(1).target);
        assertSame(tFar, allHit.targetHits.get(2).target);
        assertEquals(RayHit.Kind.WALL, allHit.terminalHit.kind);
        assertEquals(7.5f, allHit.stopDistance, EPSILON);
        assertVector(9f, 0.45f, 1.5f, allHit.endPoint);

        // Closed bulkhead door at col 6 stops the rail beam before reaching tFar at col 7.5.
        Door door = new Door(Door.LockKind.NONE, Door.Orientation.SPAN_Z, 6, 1);
        Hitscan.PiercingResult doorBlocked = Hitscan.castPiercing(lane, objects, Collections.singletonList(door),
            start, east, 50f);
        assertEquals(2, doorBlocked.targetHits.size());
        assertSame(tNear, doorBlocked.targetHits.get(0).target);
        assertSame(tMid, doorBlocked.targetHits.get(1).target);
        assertEquals(RayHit.Kind.DOOR, doorBlocked.terminalHit.kind);

        // Indestructible SecurityConsole at col 4.2 blocks piercing hits behind it (tMid, tFar).
        SecurityConsole console = new SecurityConsole(4.2f, 1.5f);
        objects.add(console);
        Hitscan.PiercingResult consoleBlocked = Hitscan.castPiercing(lane, objects, Collections.emptyList(),
            start, east, 50f);
        assertEquals(1, consoleBlocked.targetHits.size());
        assertSame(tNear, consoleBlocked.targetHits.get(0).target);
        assertSame(console, consoleBlocked.terminalHit.target);
    }

    @Test
    void rayFromAboveEntersAShortEnemyThroughItsTop() {
        Enemy drone = new Enemy(TestTuning.ENEMIES, Enemy.Kind.DRONE, 4.5f, 1.5f, 0f);
        List<Enemy> drones = Collections.singletonList(drone);
        // An eye at the top of a jump, 0.25 from the drone's side, looking 30 degrees down: the ray is still above
        // the drone where it crosses the side, and comes down through the top 0.044 short of the axis.
        Vector3 eye = new Vector3(4.5f - drone.getRadius() - 0.25f, 0.82f, 1.5f);
        double pitch = Math.toRadians(-30.0);
        Vector3 down = new Vector3((float) Math.cos(pitch), (float) Math.sin(pitch), 0f);

        RayHit hit = Hitscan.cast(MAP, drones, eye, down, RANGE);
        assertEquals(RayHit.Kind.TARGET, hit.kind);
        assertSame(drone, hit.target);
        assertEquals((0.82f - drone.getHeight()) / 0.5f, hit.distance, EPSILON);
        assertEquals(drone.getHeight(), hit.point.y, EPSILON);
        assertVector(0f, 1f, 0f, hit.normal);

        // The piercing cast finds the same entry.
        Hitscan.PiercingResult pierced = Hitscan.castPiercing(MAP, drones, Collections.<Door>emptyList(), eye, down,
            RANGE);
        assertEquals(1, pierced.targetHits.size());
        assertEquals(hit.distance, pierced.targetHits.get(0).distance, EPSILON);

        // An eye right over the drone hits its top too, straight down included; a ray that leaves the outline
        // before it has come down to the top does not.
        Vector3 above = new Vector3(4.5f, 0.9f, 1.5f);
        double steepPitch = Math.toRadians(-70.0);
        Vector3 steep = new Vector3((float) Math.cos(steepPitch), (float) Math.sin(steepPitch), 0f);
        assertEquals(RayHit.Kind.TARGET, Hitscan.cast(MAP, drones, above, steep, RANGE).kind);
        assertNotEquals(RayHit.Kind.TARGET, Hitscan.cast(MAP, drones, above, down, RANGE).kind);
        RayHit plumb = Hitscan.cast(MAP, drones, above, new Vector3(0f, -1f, 0f), RANGE);
        assertEquals(RayHit.Kind.TARGET, plumb.kind);
        assertEquals(0.9f - drone.getHeight(), plumb.distance, EPSILON);
    }

    @Test
    void rayThatLeavesAShortEnemysOutlineAboveItsTopStillMisses() {
        Enemy drone = new Enemy(TestTuning.ENEMIES, Enemy.Kind.DRONE, 4.5f, 1.5f, 0f);
        List<Enemy> drones = Collections.singletonList(drone);
        Vector3 eye = new Vector3(4.5f - drone.getRadius() - 0.25f, 0.82f, 1.5f);
        // Five degrees down, the ray is still 0.76 up where it leaves the outline, and lands on the floor beyond.
        double pitch = Math.toRadians(-5.0);
        Vector3 shallow = new Vector3((float) Math.cos(pitch), (float) Math.sin(pitch), 0f);
        assertEquals(RayHit.Kind.WALL, Hitscan.cast(MAP, drones, eye, shallow, RANGE).kind);
        // A level ray above the top misses as before.
        assertEquals(RayHit.Kind.WALL, Hitscan.cast(MAP, drones, eye, new Vector3(1f, 0f, 0f), RANGE).kind);
    }

    @Test
    void doorIntersectRayHandlesAllSixFacesFlushBoundariesAndInteriorOrigins() {
        Door spanZ = new Door(Door.LockKind.NONE, Door.Orientation.SPAN_Z, 3, 1);
        float minX = spanZ.getX() - spanZ.getHalfExtentX();
        float maxX = spanZ.getX() + spanZ.getHalfExtentX();
        float minZ = spanZ.getZ() - spanZ.getHalfExtentZ();
        float maxZ = spanZ.getZ() + spanZ.getHalfExtentZ();
        float[] normal = new float[3];

        // Exterior hits on west (-X) and east (+X) faces return outward normals opposing the ray.
        assertEquals(0.5f, spanZ.intersectRay(minX - 0.5f, 0.5f, 1.5f, 1f, 0f, 0f, RANGE, normal), EPSILON);
        assertVector(-1f, 0f, 0f, new Vector3(normal[0], normal[1], normal[2]));
        assertEquals(0.5f, spanZ.intersectRay(maxX + 0.5f, 0.5f, 1.5f, -1f, 0f, 0f, RANGE, normal), EPSILON);
        assertVector(1f, 0f, 0f, new Vector3(normal[0], normal[1], normal[2]));

        // Flush on entering faces (tEnter == 0, tExit > 0) hits at distance 0 with outward face normal.
        assertEquals(0f, spanZ.intersectRay(minX, 0.5f, 1.5f, 1f, 0f, 0f, RANGE, normal), EPSILON);
        assertVector(-1f, 0f, 0f, new Vector3(normal[0], normal[1], normal[2]));
        assertEquals(0f, spanZ.intersectRay(maxX, 0.5f, 1.5f, -1f, 0f, 0f, RANGE, normal), EPSILON);
        assertVector(1f, 0f, 0f, new Vector3(normal[0], normal[1], normal[2]));
        assertEquals(0f, spanZ.intersectRay(3.5f, 0.5f, minZ, 0f, 0f, 1f, RANGE, normal), EPSILON);
        assertVector(0f, 0f, -1f, new Vector3(normal[0], normal[1], normal[2]));
        assertEquals(0f, spanZ.intersectRay(3.5f, 0.5f, maxZ, 0f, 0f, -1f, RANGE, normal), EPSILON);
        assertVector(0f, 0f, 1f, new Vector3(normal[0], normal[1], normal[2]));

        // Flush on exiting faces pointing away from the slab (tExit == 0) misses cleanly.
        assertEquals(Float.POSITIVE_INFINITY, spanZ.intersectRay(maxX, 0.5f, 1.5f, 1f, 0f, 0f, RANGE, normal));
        assertEquals(Float.POSITIVE_INFINITY, spanZ.intersectRay(minX, 0.5f, 1.5f, -1f, 0f, 0f, RANGE, normal));
        assertEquals(Float.POSITIVE_INFINITY, spanZ.intersectRay(3.5f, 0.5f, maxZ, 0f, 0f, 1f, RANGE, normal));
        assertEquals(Float.POSITIVE_INFINITY, spanZ.intersectRay(3.5f, 0.5f, minZ, 0f, 0f, -1f, RANGE, normal));

        // Retracting bulkhead past CLEAR_PROGRESS (0.80): bottom face and interior origins still block 3D rays.
        spanZ.open();
        spanZ.update(Door.OPEN_DURATION * 0.8f);
        assertFalse(spanZ.isBlockingPassage());
        float bottomY = spanZ.getBottomY();
        assertTrue(bottomY > 0.78f && bottomY < 0.86f);

        // Flush on bottom face pointing up enters slab; pointing down away misses.
        assertEquals(0f, spanZ.intersectRay(3.5f, bottomY, 1.5f, 0f, 1f, 0f, RANGE, normal), EPSILON);
        assertVector(0f, -1f, 0f, new Vector3(normal[0], normal[1], normal[2]));
        assertEquals(Float.POSITIVE_INFINITY, spanZ.intersectRay(3.5f, bottomY, 1.5f, 0f, -1f, 0f, RANGE, normal));

        // Jumping eye strictly inside the retracting slab stops Hitscan.cast and Hitscan.castPiercing at distance 0.
        Vector3 insideEye = new Vector3(3.5f, (bottomY + GameMap.WALL_HEIGHT) * 0.5f, 1.5f);
        Vector3 slanted = new Vector3(1f, -0.25f, 0.2f).nor();
        Target behindDoor = new Target(Target.Kind.CANISTER, 5.0f, 1.5f);
        List<Door> doors = Collections.singletonList(spanZ);
        List<Target> targets = Collections.singletonList(behindDoor);

        RayHit castHit = Hitscan.cast(MAP, targets, doors, insideEye, slanted, RANGE);
        assertEquals(RayHit.Kind.DOOR, castHit.kind);
        assertSame(spanZ, castHit.door);
        assertEquals(0f, castHit.distance, EPSILON);
        assertVector(insideEye.x, insideEye.y, insideEye.z, castHit.point);
        assertVector(-slanted.x, -slanted.y, -slanted.z, castHit.normal);

        Hitscan.PiercingResult pierced = Hitscan.castPiercing(MAP, targets, doors, insideEye,
            new Vector3(1f, -0.1f, 0f).nor(), RANGE);
        assertEquals(RayHit.Kind.DOOR, pierced.terminalHit.kind);
        assertEquals(0f, pierced.stopDistance, EPSILON);
        assertTrue(pierced.targetHits.isEmpty(), "retracted steel slab blocks rail beam from inside");

        // Zero-length direction inside slab falls back to (0, 0, 1) unit normal.
        assertEquals(0f, spanZ.intersectRay(insideEye.x, insideEye.y, insideEye.z, 0f, 0f, 0f, RANGE, normal),
            EPSILON);
        assertVector(0f, 0f, 1f, new Vector3(normal[0], normal[1], normal[2]));
    }

    @Test
    void castGridStopsAtSolidOriginAndHandlesVerticalInfiniteRangeRaysWithoutNaN() {
        Vector3 insidePillar = new Vector3(4.5f, 0.5f, 2.5f);
        Vector3 diag = new Vector3(0.6f, 0f, -0.8f);
        Target beyondPillar = new Target(Target.Kind.CANISTER, 5.5f, 1.5f);

        RayHit wallHit = Hitscan.cast(MAP, Collections.singletonList(beyondPillar), insidePillar, diag, RANGE);
        assertEquals(RayHit.Kind.WALL, wallHit.kind);
        assertEquals(0f, wallHit.distance, EPSILON);
        assertEquals('%', wallHit.wallType);
        assertVector(4.5f, 0.5f, 2.5f, wallHit.point);
        assertVector(-0.6f, 0f, 0.8f, wallHit.normal);

        Hitscan.PiercingResult piercedFromWall = Hitscan.castPiercing(MAP, Collections.singletonList(beyondPillar),
            Collections.emptyList(), insidePillar, new Vector3(1f, 0f, 0f), RANGE);
        assertEquals(RayHit.Kind.WALL, piercedFromWall.terminalHit.kind);
        assertEquals(0f, piercedFromWall.stopDistance, EPSILON);
        assertTrue(piercedFromWall.targetHits.isEmpty());

        // Vertical ray inside solid wall still hits WALL at distance 0 with a finite unit fallback normal.
        RayHit verticalInWall = Hitscan.cast(MAP, NO_TARGETS, insidePillar, new Vector3(0f, -1f, 0f),
            Float.POSITIVE_INFINITY);
        assertEquals(RayHit.Kind.WALL, verticalInWall.kind);
        assertEquals(0f, verticalInWall.distance, EPSILON);
        assertEquals(1f, verticalInWall.normal.len(), EPSILON);

        // Vertical and zero-XZ infinite-range rays in open cells never march out-of-bounds to Infinity or NaN.
        RayHit downInf = Hitscan.cast(MAP, NO_TARGETS, ORIGIN, new Vector3(0f, -1f, 0f), Float.POSITIVE_INFINITY);
        assertEquals(RayHit.Kind.FLOOR, downInf.kind);
        assertEquals(0.5f, downInf.distance, EPSILON);
        assertVector(2.5f, 0f, 2.5f, downInf.point);

        RayHit upInf = Hitscan.cast(MAP, NO_TARGETS, ORIGIN, new Vector3(0f, 1f, 0f), Float.POSITIVE_INFINITY);
        assertEquals(RayHit.Kind.CEILING, upInf.kind);
        assertEquals(0.5f, upInf.distance, EPSILON);
        assertVector(2.5f, 1f, 2.5f, upInf.point);

        RayHit zeroDirInf = Hitscan.cast(MAP, NO_TARGETS, ORIGIN, new Vector3(0f, 0f, 0f), Float.POSITIVE_INFINITY);
        assertEquals(RayHit.Kind.NONE, zeroDirInf.kind);
        assertFalse(zeroDirInf.isHit());

        RayHit belowFloorDownInf = Hitscan.cast(MAP, NO_TARGETS, new Vector3(2.5f, -0.2f, 2.5f),
            new Vector3(0f, -1f, 0f), Float.POSITIVE_INFINITY);
        assertEquals(RayHit.Kind.NONE, belowFloorDownInf.kind);
        assertFalse(belowFloorDownInf.isHit());
    }

    @Test
    void lineOfSightIsSymmetricOnAllCellFacesAndRejectsPointsSlightlyInsideSolidWalls() {
        Door closedDoor = new Door(Door.LockKind.NONE, Door.Orientation.SPAN_Z, 3, 1);
        List<Door> doors = Collections.singletonList(closedDoor);

        // Outer corridor wall faces (west x=1, east x=6, north z=1, south z=4) looking into open floor.
        assertSymmetricLos(MAP, doors, 1.0f, 1.5f, 2.5f, 1.5f, true);
        assertSymmetricLos(MAP, doors, 6.0f, 3.5f, 2.5f, 3.5f, true);
        assertSymmetricLos(MAP, doors, 2.5f, 1.0f, 2.5f, 2.5f, true);
        assertSymmetricLos(MAP, doors, 2.5f, 4.0f, 2.5f, 2.5f, true);
        assertSymmetricLos(MAP, Collections.emptyList(), 1.0f, 1.5f, 6.0f, 1.5f, true);
        assertSymmetricLos(MAP, Collections.emptyList(), 2.5f, 1.0f, 2.5f, 4.0f, true);

        // Rays sliding flush along north, south, west, and east corridor wall faces.
        assertSymmetricLos(MAP, Collections.emptyList(), 1.5f, 1.0f, 5.5f, 1.0f, true);
        assertSymmetricLos(MAP, Collections.emptyList(), 1.5f, 4.0f, 5.5f, 4.0f, true);
        assertSymmetricLos(MAP, Collections.emptyList(), 1.0f, 1.5f, 1.0f, 3.5f, true);
        assertSymmetricLos(MAP, Collections.emptyList(), 6.0f, 1.5f, 6.0f, 3.5f, true);

        // Pillar [4..5, 2..3] faces: visible from adjacent open side, blocked looking through the pillar.
        assertSymmetricLos(MAP, doors, 4.0f, 2.5f, 2.5f, 2.5f, true);
        assertSymmetricLos(MAP, doors, 4.0f, 2.5f, 5.5f, 2.5f, false);
        assertSymmetricLos(MAP, doors, 5.0f, 2.5f, 5.5f, 2.5f, true);
        assertSymmetricLos(MAP, doors, 5.0f, 2.5f, 2.5f, 2.5f, false);
        assertSymmetricLos(MAP, doors, 4.5f, 2.0f, 4.5f, 1.5f, true);
        assertSymmetricLos(MAP, doors, 4.5f, 2.0f, 4.5f, 3.5f, false);
        assertSymmetricLos(MAP, doors, 4.5f, 3.0f, 4.5f, 3.5f, true);
        assertSymmetricLos(MAP, doors, 4.5f, 3.0f, 4.5f, 1.5f, false);

        // Points strictly inside solid walls (even 1e-5f, 5e-5f, or 1e-4f deep) are blocked in BOTH directions.
        assertSymmetricLos(MAP, doors, 2.5f, 2.5f, 4.00001f, 2.5f, false);
        assertSymmetricLos(MAP, doors, 2.5f, 2.5f, 4.00005f, 2.5f, false);
        assertSymmetricLos(MAP, doors, 2.5f, 2.5f, 4.0001f, 2.5f, false);
        assertSymmetricLos(MAP, doors, 5.5f, 2.5f, 4.99995f, 2.5f, false);
        assertSymmetricLos(MAP, doors, 2.5f, 1.5f, 2.5f, 0.99995f, false);
        assertSymmetricLos(MAP, doors, 2.5f, 3.5f, 2.5f, 4.00005f, false);
        assertSymmetricLos(MAP, doors, 2.5f, 2.5f, 6.00005f, 2.5f, false);

        // Degenerate/coincident and sub-1e-6f segments still check solid walls and closed doors symmetrically.
        assertSymmetricLos(MAP, doors, 2.5f, 2.5f, 2.5f, 2.5f, true);
        assertSymmetricLos(MAP, doors, 4.5f, 2.5f, 4.5f, 2.5f, false);
        assertSymmetricLos(MAP, doors, 4.00005f, 2.5f, 4.00005f, 2.5f, false);
        assertSymmetricLos(MAP, doors, Math.nextDown(4.0f), 2.5f, 4.0f, 2.5f, true);
        assertTrue(Math.nextUp(4.0f) - Math.nextDown(4.0f) < 1e-6f);
        assertSymmetricLos(MAP, doors, Math.nextDown(4.0f), 2.5f, Math.nextUp(4.0f), 2.5f, false);
        assertSymmetricLos(MAP, doors, 3.5f, 1.5f, 3.5f, 1.5f, false);

        // Exact 45-degree corner graze against pillar (4, 2) is symmetric in both directions.
        boolean cornerForward = Hitscan.hasLineOfSight(MAP, doors, 3.5f, 1.5f, 4.5f, 2.5f);
        assertFalse(cornerForward);
        assertSymmetricLos(MAP, doors, 3.5f, 1.5f, 4.5f, 2.5f, false);
        assertSymmetricLos(MAP, doors, 3.5f, 2.5f, 4.5f, 1.5f, false);
    }

    private static void assertSymmetricLos(GameMap map, List<Door> doors, float ax, float az, float bx, float bz,
                                           boolean expected) {
        assertEquals(expected, Hitscan.hasLineOfSight(map, doors, ax, az, bx, bz),
            "forward with doors (" + ax + "," + az + ") -> (" + bx + "," + bz + ")");
        assertEquals(expected, Hitscan.hasLineOfSight(map, doors, bx, bz, ax, az),
            "reverse with doors (" + bx + "," + bz + ") -> (" + ax + "," + az + ")");
        if (doors == null || doors.isEmpty()) {
            assertEquals(expected, Hitscan.hasLineOfSight(map, ax, az, bx, bz),
                "forward map-only (" + ax + "," + az + ") -> (" + bx + "," + bz + ")");
            assertEquals(expected, Hitscan.hasLineOfSight(map, bx, bz, ax, az),
                "reverse map-only (" + bx + "," + bz + ") -> (" + ax + "," + az + ")");
        } else {
            assertEquals(
                Hitscan.hasLineOfSight(map, ax, az, bx, bz),
                Hitscan.hasLineOfSight(map, bx, bz, ax, az),
                "map-only symmetry (" + ax + "," + az + ") <-> (" + bx + "," + bz + ")");
        }
    }

    @Test
    void floorAndCeilingRejectNegativePlaneDistancesWhenOriginIsOutsideRoomHeight() {
        Vector3 belowFloor = new Vector3(2.5f, -0.25f, 2.5f);
        Vector3 downEast = new Vector3(1f, -0.1f, 0f).nor();

        RayHit belowHit = Hitscan.cast(MAP, NO_TARGETS, belowFloor, downEast, RANGE);
        assertEquals(RayHit.Kind.WALL, belowHit.kind);
        assertTrue(belowHit.distance > 0f);

        Target forwardTarget = new Target(Target.Kind.CANISTER, 3.5f, 2.5f);
        Vector3 upIntoRoom = new Vector3(1f, -0.05f, 0f).nor();
        Hitscan.PiercingResult belowPiercing = Hitscan.castPiercing(MAP, Collections.singletonList(forwardTarget),
            Collections.emptyList(), new Vector3(2.5f, -0.01f, 2.5f), upIntoRoom, RANGE);
        assertEquals(RayHit.Kind.WALL, belowPiercing.terminalHit.kind);
        assertTrue(belowPiercing.stopDistance > 1.4f, "stopDistance must not be negative behind origin");

        Vector3 aboveCeiling = new Vector3(2.5f, 1.25f, 2.5f);
        Vector3 upEast = new Vector3(1f, 0.1f, 0f).nor();
        RayHit aboveHit = Hitscan.cast(MAP, NO_TARGETS, aboveCeiling, upEast, RANGE);
        assertEquals(RayHit.Kind.WALL, aboveHit.kind);
        assertTrue(aboveHit.distance > 0f);

        Hitscan.PiercingResult abovePiercing = Hitscan.castPiercing(MAP, NO_TARGETS, Collections.emptyList(),
            aboveCeiling, upEast, RANGE);
        assertEquals(RayHit.Kind.WALL, abovePiercing.terminalHit.kind);
        assertTrue(abovePiercing.stopDistance > 1.4f, "ceiling behind origin must not set negative stopDistance");

        // Below-floor ray slanted upward enters forward target cleanly when negative floor plane hit is ignored.
        Hitscan.PiercingResult risingFromBelow = Hitscan.castPiercing(MAP, Collections.singletonList(forwardTarget),
            Collections.emptyList(), new Vector3(2.5f, -0.05f, 2.5f), new Vector3(1f, 0.3f, 0f).nor(), RANGE);
        assertEquals(1, risingFromBelow.targetHits.size());
        assertSame(forwardTarget, risingFromBelow.targetHits.get(0).target);
    }

    @Test
    void verticalRaysInsideTargetCylinderHitAtDistanceZeroWithUnitNormals() {
        Target canister = new Target(Target.Kind.CANISTER, 2.5f, 2.5f);
        List<Target> targets = Collections.singletonList(canister);
        Vector3 inside = new Vector3(2.55f, 0.3f, 2.5f);

        RayHit downHit = Hitscan.cast(MAP, targets, inside, new Vector3(0f, -1f, 0f), RANGE);
        assertEquals(RayHit.Kind.TARGET, downHit.kind);
        assertSame(canister, downHit.target);
        assertEquals(0f, downHit.distance, EPSILON);
        assertVector(inside.x, inside.y, inside.z, downHit.point);
        assertVector(0f, 1f, 0f, downHit.normal);

        RayHit upHit = Hitscan.cast(MAP, targets, inside, new Vector3(0f, 1f, 0f), RANGE);
        assertEquals(RayHit.Kind.TARGET, upHit.kind);
        assertSame(canister, upHit.target);
        assertEquals(0f, upHit.distance, EPSILON);
        assertVector(inside.x, inside.y, inside.z, upHit.point);
        assertVector(0f, -1f, 0f, upHit.normal);

        Hitscan.PiercingResult downPierced = Hitscan.castPiercing(MAP, targets, Collections.emptyList(),
            inside, new Vector3(0f, -1f, 0f), RANGE);
        assertEquals(1, downPierced.targetHits.size());
        assertSame(canister, downPierced.targetHits.get(0).target);
        assertEquals(0f, downPierced.targetHits.get(0).distance, EPSILON);
        assertVector(0f, 1f, 0f, downPierced.targetHits.get(0).normal);
        assertEquals(RayHit.Kind.FLOOR, downPierced.terminalHit.kind);
        assertEquals(0.3f, downPierced.stopDistance, EPSILON);

        SecurityConsole console = new SecurityConsole(2.5f, 2.5f);
        Hitscan.PiercingResult consolePierced = Hitscan.castPiercing(MAP, Collections.singletonList(console),
            Collections.emptyList(), inside, new Vector3(0f, -1f, 0f), RANGE);
        assertEquals(RayHit.Kind.TARGET, consolePierced.terminalHit.kind);
        assertSame(console, consolePierced.terminalHit.target);
        assertEquals(0f, consolePierced.stopDistance, EPSILON);
        assertVector(0f, 1f, 0f, consolePierced.terminalHit.normal);

        // Degenerate zero-direction vector inside cylinder still produces a unit normal.
        RayHit zeroDirHit = Hitscan.cast(MAP, targets, inside, new Vector3(0f, 0f, 0f), RANGE);
        assertEquals(RayHit.Kind.TARGET, zeroDirHit.kind);
        assertEquals(0f, zeroDirHit.distance, EPSILON);
        assertVector(0f, 1f, 0f, zeroDirHit.normal);
    }

    @Test
    void castHandlesNullTargetsAndDoorsWithoutThrowing() {
        RayHit fiveArgNullTargets = Hitscan.cast(MAP, null, ORIGIN, new Vector3(1f, 0f, 0f), RANGE);
        assertEquals(RayHit.Kind.WALL, fiveArgNullTargets.kind);
        assertEquals(1.5f, fiveArgNullTargets.distance, EPSILON);

        RayHit sixArgNullBoth = Hitscan.cast(MAP, null, null, ORIGIN, new Vector3(1f, 0f, 0f), RANGE);
        assertEquals(RayHit.Kind.WALL, sixArgNullBoth.kind);
        assertEquals(1.5f, sixArgNullBoth.distance, EPSILON);

        Door door = new Door(Door.LockKind.NONE, Door.Orientation.SPAN_Z, 3, 1);
        RayHit doorOnly = Hitscan.cast(MAP, null, Collections.singletonList(door),
            new Vector3(1.5f, 0.5f, 1.5f), new Vector3(1f, 0f, 0f), RANGE);
        assertEquals(RayHit.Kind.DOOR, doorOnly.kind);
        assertSame(door, doorOnly.door);
    }
}

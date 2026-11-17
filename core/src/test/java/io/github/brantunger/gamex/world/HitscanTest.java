package io.github.brantunger.gamex.world;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertTrue;

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
}

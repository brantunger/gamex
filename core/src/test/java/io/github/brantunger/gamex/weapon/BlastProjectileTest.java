package io.github.brantunger.gamex.weapon;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.badlogic.gdx.math.Vector3;
import io.github.brantunger.gamex.TestTuning;
import io.github.brantunger.gamex.player.Player;
import io.github.brantunger.gamex.world.Door;
import io.github.brantunger.gamex.world.GameMap;
import io.github.brantunger.gamex.world.Target;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;

class BlastProjectileTest {

    /** The tests' own weapon balance. */
    private static final WeaponTuning W = TestTuning.WEAPONS;

    private static final float EPSILON = 1e-3f;

    /** A corridor one cell wide whose open floor spans x from 1 to 9 at z from 1 to 2. */
    private static final GameMap CORRIDOR = GameMap.parse(
        "##########",
        "#>.......#",
        "##########");
    private static final List<Target> NO_TARGETS = Collections.emptyList();

    private static BlastProjectile eastwards(float charge) {
        return new BlastProjectile(new Vector3(1.5f, 0.5f, 1.5f), new Vector3(1f, 0f, 0f), charge,
            W);
    }

    @Test
    void fliesInAStraightLineAtItsSpeed() {
        BlastProjectile projectile = eastwards(1f);

        assertFalse(projectile.update(0.5f, CORRIDOR, NO_TARGETS));

        assertEquals(1.5f + BlastProjectile.SPEED * 0.5f, projectile.position.x, EPSILON);
        assertEquals(0.5f, projectile.position.y, EPSILON);
        assertEquals(1.5f, projectile.position.z, EPSILON);
        assertFalse(projectile.isExploded());
    }

    @Test
    void explodesAtTheWallAndNeverPassesThroughIt() {
        BlastProjectile projectile = eastwards(1f);

        // One enormous step: without sub-stepping this would land far outside the map.
        assertTrue(projectile.update(100f, CORRIDOR, NO_TARGETS));

        assertTrue(projectile.isExploded());
        float contact = 9f - BlastProjectile.RADIUS;
        assertTrue(projectile.position.x <= contact, "stopped at " + projectile.position.x);
        assertTrue(projectile.position.x > contact - 0.1f, "stopped at " + projectile.position.x);
    }

    @Test
    void explodesOnTheFloorWhenFiredDownwards() {
        BlastProjectile projectile = new BlastProjectile(new Vector3(1.5f, 0.5f, 1.5f),
            new Vector3(1f, -1f, 0f).nor(), 1f, W);

        assertTrue(projectile.update(1f, CORRIDOR, NO_TARGETS));

        assertTrue(projectile.position.y >= BlastProjectile.RADIUS, "stopped at height " + projectile.position.y);
        assertTrue(projectile.position.x < 2.1f, "stopped at " + projectile.position.x);
    }

    @Test
    void explodesOnContactWithATargetAndDestroysIt() {
        Target canister = new Target(Target.Kind.CANISTER, 4.5f, 1.5f);
        List<Target> targets = Collections.singletonList(canister);
        BlastProjectile projectile = eastwards(1f);

        boolean exploded = false;
        for (int i = 0; i < 120 && !exploded; i++) exploded = projectile.update(1f / 60f, CORRIDOR, targets);

        assertTrue(exploded);
        float contact = 4.5f - canister.getRadius() - BlastProjectile.RADIUS;
        assertTrue(projectile.position.x <= contact && projectile.position.x > contact - 0.1f,
            "stopped at " + projectile.position.x);
        assertTrue(canister.isDestroyed());
    }

    @Test
    void fliesOnThroughADestroyedTarget() {
        Target canister = new Target(Target.Kind.CANISTER, 4.5f, 1.5f);
        canister.damage(1000f);
        BlastProjectile projectile = eastwards(1f);

        assertFalse(projectile.update(0.75f, CORRIDOR, Collections.singletonList(canister)));

        assertEquals(6f, projectile.position.x, EPSILON);
    }

    @Test
    void explodesOnlyOnce() {
        Target board = new Target(Target.Kind.RING_BOARD, 8.5f, 1.5f);
        List<Target> targets = Collections.singletonList(board);
        BlastProjectile projectile = eastwards(W.blastMinCharge);

        assertTrue(projectile.update(100f, CORRIDOR, targets));
        float healthAfterBlast = board.getHealth();
        assertTrue(healthAfterBlast < board.getKind().maxHealth);

        assertFalse(projectile.update(100f, CORRIDOR, targets));
        assertEquals(healthAfterBlast, board.getHealth(), EPSILON);
    }

    @Test
    void damageFallsOffInAStraightLineFromTheCentreToTheRadius() {
        assertEquals(100f, Explosion.damageAt(0f, 2f, 100f), EPSILON);
        assertEquals(75f, Explosion.damageAt(0.5f, 2f, 100f), EPSILON);
        assertEquals(50f, Explosion.damageAt(1f, 2f, 100f), EPSILON);
        assertEquals(0f, Explosion.damageAt(2f, 2f, 100f), EPSILON);
        assertEquals(0f, Explosion.damageAt(3f, 2f, 100f), EPSILON);
        assertEquals(100f, Explosion.damageAt(-0.1f, 2f, 100f), EPSILON, "touching counts as the centre");
    }

    @Test
    void areaDamageHitsEveryTargetInTheRadiusByItsDistance() {
        float edge = Target.Kind.RING_BOARD.radius;
        Target touching = new Target(Target.Kind.RING_BOARD, edge, 0f);
        Target near = new Target(Target.Kind.RING_BOARD, 0f, edge + 0.5f);
        Target far = new Target(Target.Kind.RING_BOARD, -(edge + 1.5f), 0f);
        Target outside = new Target(Target.Kind.RING_BOARD, 0f, -(edge + 2.5f));
        List<Target> targets = new ArrayList<>(Arrays.asList(touching, near, far, outside));

        int damaged = Explosion.apply(0f, 0f, 2f, 100f, targets);

        assertEquals(3, damaged);
        assertEquals(20f, touching.getHealth(), EPSILON);
        assertEquals(45f, near.getHealth(), EPSILON);
        assertEquals(95f, far.getHealth(), EPSILON);
        assertEquals(120f, outside.getHealth(), EPSILON);
    }

    @Test
    void areaDamageDestroysAWeakTargetAndOnlyWoundsASturdyOne() {
        Target canister = new Target(Target.Kind.CANISTER, 1f, 0f);
        Target board = new Target(Target.Kind.RING_BOARD, -1f, 0f);

        Explosion.apply(0f, 0f, 3f, 100f, Arrays.asList(canister, board));

        assertTrue(canister.isDestroyed());
        assertFalse(board.isDestroyed());
        assertTrue(board.getHealth() < board.getKind().maxHealth);
    }

    @Test
    void radiusAndDamageGrowWithTheCharge() {
        assertEquals(1.5f, W.blastRadius(0.25f), EPSILON);
        assertEquals(3f, W.blastRadius(1f), EPSILON);
        assertEquals(55f, W.blastDamage(0.25f), EPSILON);
        assertEquals(130f, W.blastDamage(1f), EPSILON);
    }

    @Test
    void biggerChargeReachesATargetThatASmallerChargeDoesNot() {
        // Both explode against the end wall at x = 9; the board stands 2.2 from there, edge 1.96 away.
        Target reachedByFull = new Target(Target.Kind.RING_BOARD, 6.7f, 1.5f);
        Target missedByMinimum = new Target(Target.Kind.RING_BOARD, 6.7f, 1.5f);
        BlastProjectile full = new BlastProjectile(new Vector3(7.5f, 0.5f, 1.5f), new Vector3(1f, 0f, 0f), 1f,
            W);
        BlastProjectile minimum = new BlastProjectile(new Vector3(7.5f, 0.5f, 1.5f), new Vector3(1f, 0f, 0f),
            W.blastMinCharge, W);

        assertTrue(full.update(1f, CORRIDOR, Collections.singletonList(reachedByFull)));
        assertTrue(minimum.update(1f, CORRIDOR, Collections.singletonList(missedByMinimum)));

        assertTrue(reachedByFull.getHealth() < 120f);
        assertEquals(120f, missedByMinimum.getHealth(), EPSILON);
    }

    @Test
    void solidWallShieldsTargetAndPlayerOnTheOtherSideOfABlast() {
        GameMap splitRooms = GameMap.parse(
            "#########",
            "#>..#...#",
            "#########");
        Target shielded = new Target(Target.Kind.CANISTER, 5.5f, 1.5f);
        Target exposed = new Target(Target.Kind.CANISTER, 3.2f, 1.5f);
        Player shieldedPlayer = new Player(5.5f, 1.5f, 0f);
        Player closePlayer = new Player(3.2f, 1.5f, 0f);

        // Explodes against the dividing wall at x = 3.9; radius 3 easily spans x = 5.5 geometrically,
        // but the wall at column 4 blocks line of sight.
        Explosion.apply(splitRooms, 3.85f, 1.5f, 3f, 100f, Arrays.asList(shielded, exposed), shieldedPlayer, 0.5f);
        assertEquals(50f, shielded.getHealth(), EPSILON, "wall blocks target damage");
        assertTrue(exposed.isDestroyed(), "target on same side of wall is destroyed");
        assertEquals(100f, shieldedPlayer.getHealth(), EPSILON, "wall blocks player splash damage");

        Explosion.apply(splitRooms, 3.85f, 1.5f, 3f, 100f, NO_TARGETS, closePlayer, 0.5f);
        assertTrue(closePlayer.getHealth() < 100f && closePlayer.getHealth() > 50f,
            "close player takes 50%-scaled splash damage: " + closePlayer.getHealth());
    }

    @Test
    void bisectsImpactToWithinThousandthsOfContactSurface() {
        BlastProjectile wallBlast = eastwards(1f);
        assertTrue(wallBlast.update(10f, CORRIDOR, NO_TARGETS));
        float wallContact = 9f - BlastProjectile.RADIUS;
        assertTrue(wallBlast.position.x <= wallContact, "outside wall");
        assertTrue(wallContact - wallBlast.position.x < 0.002f, "gap was " + (wallContact - wallBlast.position.x));

        Target canister = new Target(Target.Kind.CANISTER, 5.5f, 1.5f);
        BlastProjectile targetBlast = eastwards(1f);
        assertTrue(targetBlast.update(10f, CORRIDOR, Collections.singletonList(canister)));
        float targetContact = 5.5f - canister.getRadius() - BlastProjectile.RADIUS;
        assertTrue(targetBlast.position.x <= targetContact, "outside canister");
        assertTrue(targetContact - targetBlast.position.x < 0.002f,
            "gap was " + (targetContact - targetBlast.position.x));
    }

    @Test
    void constructorNormalizesDirectionAndFallsBackWhenDegenerate() {
        Door closedDoor = new Door(Door.LockKind.NONE, Door.Orientation.SPAN_Z, 3, 1);
        List<Door> doors = Collections.singletonList(closedDoor);

        // Unnormalized direction of length 5 would previously step 0.40u > 0.18u door slab thickness and tunnel.
        BlastProjectile scaled = new BlastProjectile(
            new Vector3(1.5f, 0.5f, 1.5f), new Vector3(5f, 0f, 0f), 1f, W);
        assertFalse(scaled.update(0.25f, CORRIDOR, NO_TARGETS, doors, null, 0f));
        assertEquals(1.5f + BlastProjectile.SPEED * 0.25f, scaled.position.x, EPSILON);

        assertTrue(scaled.update(1f, CORRIDOR, NO_TARGETS, doors, null, 0f));
        float doorFace = closedDoor.getX() - Door.HALF_THICKNESS - BlastProjectile.RADIUS;
        assertTrue(scaled.position.x <= doorFace + 1e-4f, "stopped by closed bulkhead: " + scaled.position.x);

        GameMap northHall = GameMap.parse(
            "###",
            "#.#",
            "#.#",
            "#>#",
            "###");
        for (Vector3 badDir : Arrays.asList(
            null,
            new Vector3(0f, 0f, 0f),
            new Vector3(1e-8f, 0f, 0f),
            new Vector3(Float.NaN, 0f, 0f),
            new Vector3(Float.POSITIVE_INFINITY, 0f, 0f))) {
            BlastProjectile fallback = new BlastProjectile(new Vector3(1.5f, 0.5f, 3.5f), badDir, 0.5f,
                W);
            assertFalse(fallback.update(0.25f, northHall, NO_TARGETS));
            assertEquals(1.5f, fallback.position.x, EPSILON);
            assertEquals(0.5f, fallback.position.y, EPSILON);
            assertEquals(3.5f - BlastProjectile.SPEED * 0.25f, fallback.position.z, EPSILON);
        }
    }

    @Test
    void ignoresNonPositiveAndNonFiniteTimeSteps() {
        Target adjacent = new Target(Target.Kind.CANISTER, 1.5f, 1.5f);
        BlastProjectile projectile = eastwards(1f);

        for (float badDt : new float[]{0f, -0.1f, Float.NaN, Float.POSITIVE_INFINITY, Float.NEGATIVE_INFINITY}) {
            assertFalse(projectile.update(badDt, CORRIDOR, Collections.singletonList(adjacent)));
            assertFalse(projectile.isExploded());
            assertEquals(1.5f, projectile.position.x, 1e-6f);
            assertEquals(Target.Kind.CANISTER.maxHealth, adjacent.getHealth(), 1e-6f);
        }
    }

    @Test
    void detonatesImmediatelyWhenSpawnedOverlappingRetractingDoorWallOrTarget() {
        Door retracting = new Door(Door.LockKind.NONE, Door.Orientation.SPAN_Z, 3, 1);
        retracting.open();
        retracting.update(0.82f * Door.OPEN_DURATION);
        assertFalse(retracting.isBlockingPassage(), "2D passage is already clear above 0.75 progress");
        assertFalse(retracting.isOpen(), "3D slab is still retracting near ceiling");

        // Spawn grazing the east face of the retracting upper slab so a 0.08u first step would exit the slab.
        float spawnX = retracting.getX() + Door.HALF_THICKNESS + 0.04f;
        float spawnY = Math.min(0.88f, retracting.getBottomY() + 0.02f);
        assertTrue(retracting.overlapsSphere(spawnX, spawnY, 1.5f, BlastProjectile.RADIUS));

        BlastProjectile doorSpawn = new BlastProjectile(
            new Vector3(spawnX, spawnY, 1.5f), new Vector3(1f, 0f, 0f), 1f, W);
        assertTrue(doorSpawn.update(1f / 60f, CORRIDOR, NO_TARGETS,
            Collections.singletonList(retracting), null, 0f));
        assertTrue(doorSpawn.isExploded());
        assertEquals(spawnX, doorSpawn.position.x, 1e-6f, "detonated at t=0 without tunneling out");

        Target canister = new Target(Target.Kind.CANISTER, 4.0f, 1.5f);
        float targetSpawnX = 4.0f + canister.getRadius() + 0.05f;
        BlastProjectile targetSpawn = new BlastProjectile(
            new Vector3(targetSpawnX, 0.4f, 1.5f), new Vector3(1f, 0f, 0f), 1f, W);
        assertTrue(targetSpawn.update(1f / 60f, CORRIDOR, Collections.singletonList(canister)));
        assertEquals(targetSpawnX, targetSpawn.position.x, 1e-6f);
        assertTrue(canister.isDestroyed());
    }

    @Test
    void ceilingAndFloorImpactsApplyThreeDimensionalVerticalSplashFalloff() {
        Player grounded = new Player(2.5f, 1.5f, 0f);
        BlastProjectile ceilingBlast = new BlastProjectile(
            new Vector3(2.5f, 0.80f, 1.5f), new Vector3(0f, 1f, 0f), W.blastMinCharge,
            W);

        assertTrue(ceilingBlast.update(1f, CORRIDOR, NO_TARGETS, grounded, BlastProjectile.SELF_DAMAGE_SCALE));
        assertEquals(GameMap.WALL_HEIGHT - BlastProjectile.RADIUS, ceilingBlast.position.y, 0.002f);

        float radius = W.blastRadius(W.blastMinCharge);
        float maxDamage = W.blastDamage(W.blastMinCharge);
        float vertGap = ceilingBlast.position.y - grounded.getTopY();
        assertTrue(vertGap > 0.2f);
        float expectedLoss = Explosion.damageAt(vertGap, radius, maxDamage) * BlastProjectile.SELF_DAMAGE_SCALE;
        assertEquals(Player.MAX_HEALTH - expectedLoss, grounded.getHealth(), 0.1f);
        assertTrue(grounded.getHealth() > Player.MAX_HEALTH - maxDamage * BlastProjectile.SELF_DAMAGE_SCALE + 2f,
            "vertical separation reduces ceiling self-damage below 100% center damage");
    }
}

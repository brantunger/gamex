package io.github.brantunger.gamex.weapon;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.badlogic.gdx.math.Vector3;
import io.github.brantunger.gamex.world.GameMap;
import io.github.brantunger.gamex.world.Target;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;

class BlastProjectileTest {

    private static final float EPSILON = 1e-3f;

    /** A corridor one cell wide whose open floor spans x from 1 to 9 at z from 1 to 2. */
    private static final GameMap CORRIDOR = GameMap.parse(
        "##########",
        "#>.......#",
        "##########");
    private static final List<Target> NO_TARGETS = Collections.emptyList();

    private static BlastProjectile eastwards(float charge) {
        return new BlastProjectile(new Vector3(1.5f, 0.5f, 1.5f), new Vector3(1f, 0f, 0f), charge);
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
            new Vector3(1f, -1f, 0f).nor(), 1f);

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
        BlastProjectile projectile = eastwards(BlastCharger.MIN_CHARGE);

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
        assertEquals(1.5f, BlastProjectile.blastRadius(0.25f), EPSILON);
        assertEquals(3f, BlastProjectile.blastRadius(1f), EPSILON);
        assertEquals(55f, BlastProjectile.blastDamage(0.25f), EPSILON);
        assertEquals(130f, BlastProjectile.blastDamage(1f), EPSILON);
    }

    @Test
    void biggerChargeReachesATargetThatASmallerChargeDoesNot() {
        // Both explode against the end wall at x = 9; the board stands 2.2 from there, edge 1.96 away.
        Target reachedByFull = new Target(Target.Kind.RING_BOARD, 6.7f, 1.5f);
        Target missedByMinimum = new Target(Target.Kind.RING_BOARD, 6.7f, 1.5f);
        BlastProjectile full = new BlastProjectile(new Vector3(7.5f, 0.5f, 1.5f), new Vector3(1f, 0f, 0f), 1f);
        BlastProjectile minimum = new BlastProjectile(new Vector3(7.5f, 0.5f, 1.5f), new Vector3(1f, 0f, 0f),
            BlastCharger.MIN_CHARGE);

        assertTrue(full.update(1f, CORRIDOR, Collections.singletonList(reachedByFull)));
        assertTrue(minimum.update(1f, CORRIDOR, Collections.singletonList(missedByMinimum)));

        assertTrue(reachedByFull.getHealth() < 120f);
        assertEquals(120f, missedByMinimum.getHealth(), EPSILON);
    }
}

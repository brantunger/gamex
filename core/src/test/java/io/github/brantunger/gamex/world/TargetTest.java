package io.github.brantunger.gamex.world;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.Test;

class TargetTest {

    private static final float EPSILON = 1e-5f;

    @Test
    void startsAtTheFullHealthAndSizeOfItsKind() {
        Target target = new Target(Target.Kind.RING_BOARD, 3f, 4f);

        assertEquals(Target.Kind.RING_BOARD.maxHealth, target.getHealth(), EPSILON);
        assertEquals(Target.Kind.RING_BOARD.radius, target.getRadius(), EPSILON);
        assertEquals(Target.Kind.RING_BOARD.height, target.getHeight(), EPSILON);
        assertEquals(3f, target.getX(), EPSILON);
        assertEquals(4f, target.getZ(), EPSILON);
        assertFalse(target.isDestroyed());
    }

    @Test
    void damageLowersHealth() {
        Damageable target = new Target(Target.Kind.CANISTER, 0f, 0f);

        target.damage(20f);

        assertEquals(30f, target.getHealth(), EPSILON);
        assertFalse(target.isDestroyed());
    }

    @Test
    void isDestroyedWhenHealthReachesZeroAndHealthNeverGoesNegative() {
        Damageable target = new Target(Target.Kind.CANISTER, 0f, 0f);

        target.damage(20f);
        target.damage(20f);
        assertFalse(target.isDestroyed());
        target.damage(500f);

        assertTrue(target.isDestroyed());
        assertEquals(0f, target.getHealth(), EPSILON);
    }

    @Test
    void ignoresZeroAndNegativeDamage() {
        Target target = new Target(Target.Kind.CANISTER, 0f, 0f);

        target.damage(0f);
        target.damage(-30f);

        assertEquals(50f, target.getHealth(), EPSILON);
        assertEquals(0f, target.getHitFlash(), EPSILON);
    }

    @Test
    void hitFlashStartsOnDamageAndFadesOut() {
        Target target = new Target(Target.Kind.CANISTER, 0f, 0f);
        assertEquals(0f, target.getHitFlash(), EPSILON);

        target.damage(1f);
        assertEquals(1f, target.getHitFlash(), EPSILON);

        target.update(Target.HIT_FLASH_TIME / 2f);
        assertEquals(0.5f, target.getHitFlash(), 1e-3f);

        target.update(1f);
        assertEquals(0f, target.getHitFlash(), EPSILON);
    }
}

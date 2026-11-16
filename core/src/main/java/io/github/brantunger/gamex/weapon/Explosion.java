package io.github.brantunger.gamex.weapon;

import io.github.brantunger.gamex.world.Damageable;

import java.util.List;

/** Area damage around a point, strongest at the centre and falling off in a straight line to the edge. */
public final class Explosion {

    private Explosion() {}

    /**
     * @param distance the distance from the centre of the explosion
     * @param radius the radius of the explosion
     * @param maxDamage the damage at the centre
     * @return the damage at that distance: {@code maxDamage} at the centre, half of it halfway out, and nothing at
     *     the radius or beyond
     */
    public static float damageAt(float distance, float radius, float maxDamage) {
        if (radius <= 0f || distance >= radius) return 0f;
        return maxDamage * (1f - Math.max(0f, distance) / radius);
    }

    /**
     * Damages everything within the radius. The distance to an object is measured on the XZ plane to the edge of its
     * cylinder, so an explosion touching an object deals its full damage. Walls do not shield from the blast.
     * @param x the world x of the centre
     * @param z the world z of the centre
     * @param radius the radius of the explosion
     * @param maxDamage the damage at the centre
     * @param targets the objects to damage; destroyed ones are skipped
     * @return the number of objects that took damage
     */
    public static int apply(float x, float z, float radius, float maxDamage, List<? extends Damageable> targets) {
        int damaged = 0;
        for (int i = 0; i < targets.size(); i++) {
            Damageable target = targets.get(i);
            if (target.isDestroyed()) continue;
            float dx = target.getX() - x;
            float dz = target.getZ() - z;
            float distance = (float) Math.sqrt(dx * dx + dz * dz) - target.getRadius();
            float damage = damageAt(distance, radius, maxDamage);
            if (damage <= 0f) continue;
            target.damage(damage);
            damaged++;
        }
        return damaged;
    }
}

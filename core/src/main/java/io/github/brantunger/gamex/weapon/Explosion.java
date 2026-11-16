package io.github.brantunger.gamex.weapon;

import io.github.brantunger.gamex.player.Player;
import io.github.brantunger.gamex.world.Damageable;
import io.github.brantunger.gamex.world.GameMap;
import io.github.brantunger.gamex.world.Hitscan;

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
     * Damages everything within the radius without checking map walls (pure geometric falloff).
     * @param x the world x of the centre
     * @param z the world z of the centre
     * @param radius the radius of the explosion
     * @param maxDamage the damage at the centre
     * @param targets the objects to damage; destroyed ones are skipped
     * @return the number of objects that took damage
     */
    public static int apply(float x, float z, float radius, float maxDamage, List<? extends Damageable> targets) {
        return apply(null, x, z, radius, maxDamage, targets, null, 0f);
    }

    /**
     * Damages every unobstructed object (and optional player) within the radius. When {@code map} is non-null, solid
     * wall cells shield anything behind them from the blast.
     * @param map the map for wall line-of-sight checks, or null to skip wall occlusion
     * @param x the world x of the centre
     * @param z the world z of the centre
     * @param radius the radius of the explosion
     * @param maxDamage the damage at the centre
     * @param targets the objects to damage; destroyed ones are skipped
     * @param player optional player to receive splash damage, or null
     * @param playerDamageScale multiplier applied to raw splash damage on {@code player}
     * @return the number of {@link Damageable} objects that took damage
     */
    public static int apply(GameMap map, float x, float z, float radius, float maxDamage,
                            List<? extends Damageable> targets, Player player, float playerDamageScale) {
        int damaged = 0;
        for (int i = 0; i < targets.size(); i++) {
            Damageable target = targets.get(i);
            if (target.isDestroyed()) continue;
            float dx = target.getX() - x;
            float dz = target.getZ() - z;
            float centreDist = (float) Math.sqrt(dx * dx + dz * dz);
            float distance = centreDist - target.getRadius();
            float damage = damageAt(distance, radius, maxDamage);
            if (damage <= 0f) continue;
            if (map != null && !canReachCylinder(map, x, z, target.getX(), target.getZ(), target.getRadius(),
                centreDist)) {
                continue;
            }
            target.damage(damage);
            damaged++;
        }
        if (player != null && !player.isDead() && playerDamageScale > 0f) {
            float dx = player.position.x - x;
            float dz = player.position.y - z;
            float centreDist = (float) Math.sqrt(dx * dx + dz * dz);
            float distance = centreDist - Player.RADIUS;
            float rawDamage = damageAt(distance, radius, maxDamage) * playerDamageScale;
            if (rawDamage > 0f && (map == null || canReachCylinder(map, x, z, player.position.x, player.position.y,
                Player.RADIUS, centreDist))) {
                player.damage(rawDamage, x, z);
            }
        }
        return damaged;
    }

    private static boolean canReachCylinder(GameMap map, float blastX, float blastZ, float cx, float cz, float radius,
                                            float centreDist) {
        if (Hitscan.hasLineOfSight(map, blastX, blastZ, cx, cz)) return true;
        if (centreDist <= radius + 1e-4f) return true;
        float inv = 1f / centreDist;
        float rimX = cx - (cx - blastX) * inv * radius;
        float rimZ = cz - (cz - blastZ) * inv * radius;
        return Hitscan.hasLineOfSight(map, blastX, blastZ, rimX, rimZ);
    }
}

package io.github.brantunger.gamex.weapon;

import io.github.brantunger.gamex.player.Player;
import io.github.brantunger.gamex.world.Damageable;
import io.github.brantunger.gamex.world.Door;
import io.github.brantunger.gamex.world.GameMap;
import io.github.brantunger.gamex.world.Hitscan;

import java.util.Collections;
import java.util.List;

/** Area damage around a point, strongest at the centre and falling off in a straight line to the edge. */
public final class Explosion {

    /** Inset factor for lateral cylinder-rim line-of-sight rays so tangent cylinders do not touch adjacent walls. */
    private static final float LATERAL_RIM_SCALE = 0.95f;

    private Explosion() {}

    /**
     * @param distance the distance from the centre of the explosion
     * @param radius the radius of the explosion
     * @param maxDamage the damage at the centre
     * @return the damage at that distance: {@code maxDamage} at the centre, half of it halfway out, and nothing at
     *     the radius or beyond
     */
    public static float damageAt(float distance, float radius, float maxDamage) {
        if (!Float.isFinite(distance) || !Float.isFinite(radius) || !Float.isFinite(maxDamage)
            || radius <= 0f || maxDamage <= 0f || distance >= radius) {
            return 0f;
        }
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
        return apply(map, Collections.<Door>emptyList(), x, z, radius, maxDamage, targets, player, playerDamageScale);
    }

    /**
     * Damages every unobstructed object (and optional player) within the radius on the 2D XZ plane. Solid walls and
     * closed bulkhead doors shield anything behind them from the blast.
     * @param map the map for wall line-of-sight checks, or null to skip wall occlusion
     * @param doors bulkhead doors in the level; closed ones shield targets behind them
     * @param blastX the world x of the centre
     * @param blastZ the world z of the centre
     * @param blastRadius the radius of the explosion
     * @param maxDamage the damage at the centre
     * @param targets the objects to damage; destroyed ones are skipped
     * @param player optional player to receive splash damage, or null
     * @param selfDamageScale multiplier applied to raw splash damage on {@code player}
     * @return the number of {@link Damageable} objects that took damage
     */
    public static int apply(GameMap map, List<Door> doors, float blastX, float blastZ, float blastRadius,
                            float maxDamage, List<? extends Damageable> targets, Player player,
                            float selfDamageScale) {
        return applyInternal(map, doors, blastX, 0f, blastZ, false, blastRadius, maxDamage, targets, player,
            selfDamageScale);
    }

    /**
     * Damages every unobstructed object (and optional player) within the 3D blast sphere, combining horizontal
     * cylinder gap and vertical interval gap via {@link Math#hypot(double, double)}. Solid walls and closed bulkhead
     * doors shield anything behind them from the blast.
     * @param map the map for wall line-of-sight checks, or null to skip wall occlusion
     * @param doors bulkhead doors in the level; closed ones shield targets behind them
     * @param blastX the world x of the centre
     * @param blastY the world y of the centre
     * @param blastZ the world z of the centre
     * @param blastRadius the radius of the explosion
     * @param maxDamage the damage at the centre
     * @param targets the objects to damage; destroyed ones are skipped
     * @param player optional player to receive splash damage, or null
     * @param selfDamageScale multiplier applied to raw splash damage on {@code player}
     * @return the number of {@link Damageable} objects that took damage
     */
    public static int apply(
        GameMap map,
        List<Door> doors,
        float blastX,
        float blastY,
        float blastZ,
        float blastRadius,
        float maxDamage,
        List<? extends Damageable> targets,
        Player player,
        float selfDamageScale
    ) {
        if (!Float.isFinite(blastY)) return 0;
        return applyInternal(map, doors, blastX, blastY, blastZ, true, blastRadius, maxDamage, targets, player,
            selfDamageScale);
    }

    private static int applyInternal(GameMap map, List<Door> doors, float blastX, float blastY, float blastZ,
                                     boolean checkVertical, float blastRadius, float maxDamage,
                                     List<? extends Damageable> targets, Player player, float selfDamageScale) {
        if (!Float.isFinite(blastX) || !Float.isFinite(blastZ)
            || !Float.isFinite(blastRadius) || !Float.isFinite(maxDamage)
            || blastRadius <= 0f || maxDamage <= 0f) {
            return 0;
        }
        int damaged = 0;
        if (targets != null) {
            for (int i = 0; i < targets.size(); i++) {
                Damageable target = targets.get(i);
                if (target == null || target.isDestroyed()) continue;
                float dx = target.getX() - blastX;
                float dz = target.getZ() - blastZ;
                float horizDist = (float) Math.hypot(dx, dz);
                float horizGap = Math.max(0f, horizDist - target.getRadius());
                float vertGap = checkVertical
                    ? verticalGap(blastY, 0f, Math.max(0f, target.getHeight()))
                    : 0f;
                float distance = vertGap == 0f ? horizGap : (float) Math.hypot(horizGap, vertGap);
                if (distance >= blastRadius) continue;
                float damage = damageAt(distance, blastRadius, maxDamage);
                if (damage <= 0f) continue;
                if (map != null && !canReachCylinder(map, doors, blastX, blastZ, target.getX(), target.getZ(),
                    target.getRadius(), horizDist)) {
                    continue;
                }
                float healthBefore = target.getHealth();
                target.damage(damage);
                if (target.getHealth() < healthBefore || target.isDestroyed()) {
                    damaged++;
                }
            }
        }
        if (player != null && !player.isDead() && Float.isFinite(selfDamageScale) && selfDamageScale > 0f) {
            float dx = player.position.x - blastX;
            float dz = player.position.y - blastZ;
            float horizDist = (float) Math.hypot(dx, dz);
            float horizGap = Math.max(0f, horizDist - Player.RADIUS);
            float feetY = player.getFeetY();
            float topY = Math.max(feetY, player.getTopY());
            float vertGap = checkVertical ? verticalGap(blastY, feetY, topY) : 0f;
            float distance = vertGap == 0f ? horizGap : (float) Math.hypot(horizGap, vertGap);
            if (distance < blastRadius) {
                float rawDamage = damageAt(distance, blastRadius, maxDamage) * selfDamageScale;
                if (rawDamage > 0f && (map == null || canReachCylinder(map, doors, blastX, blastZ, player.position.x,
                    player.position.y, Player.RADIUS, horizDist))) {
                    player.damage(rawDamage, blastX, blastZ);
                }
            }
        }
        return damaged;
    }

    private static float verticalGap(float y, float minY, float maxY) {
        if (y < minY) return minY - y;
        if (y > maxY) return y - maxY;
        return 0f;
    }

    private static boolean canReachCylinder(GameMap map, List<Door> doors, float blastX, float blastZ, float cx,
                                            float cz, float radius, float centreDist) {
        if (Hitscan.hasLineOfSight(map, doors, blastX, blastZ, cx, cz)) return true;
        if (radius <= 0f || map.isSolid((int) Math.floor(blastX), (int) Math.floor(blastZ))) return false;
        if (centreDist <= radius + 1e-4f) return true;
        float inv = 1f / centreDist;
        float ux = (cx - blastX) * inv;
        float uz = (cz - blastZ) * inv;
        float rimX = cx - ux * radius;
        float rimZ = cz - uz * radius;
        if (!map.isSolid((int) Math.floor(rimX), (int) Math.floor(rimZ))
            && Hitscan.hasLineOfSight(map, doors, blastX, blastZ, rimX, rimZ)) {
            return true;
        }
        float lateral = radius * LATERAL_RIM_SCALE;
        float perpX = -uz * lateral;
        float perpZ = ux * lateral;
        float leftX = cx + perpX;
        float leftZ = cz + perpZ;
        if (!map.isSolid((int) Math.floor(leftX), (int) Math.floor(leftZ))
            && Hitscan.hasLineOfSight(map, doors, blastX, blastZ, leftX, leftZ)) {
            return true;
        }
        float rightX = cx - perpX;
        float rightZ = cz - perpZ;
        return !map.isSolid((int) Math.floor(rightX), (int) Math.floor(rightZ))
            && Hitscan.hasLineOfSight(map, doors, blastX, blastZ, rightX, rightZ);
    }
}

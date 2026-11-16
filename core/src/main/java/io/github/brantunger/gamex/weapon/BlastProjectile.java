package io.github.brantunger.gamex.weapon;

import com.badlogic.gdx.math.Vector3;
import io.github.brantunger.gamex.player.Player;
import io.github.brantunger.gamex.world.Collision;
import io.github.brantunger.gamex.world.Damageable;
import io.github.brantunger.gamex.world.GameMap;

import java.util.List;

/**
 * The slow, glowing ball that a charged blast fires. It flies in a straight line and explodes on the first wall,
 * floor, ceiling or live {@link Damageable} it touches, damaging unobstructed targets (and optionally the player)
 * within its blast radius.
 */
public final class BlastProjectile {

    /** The flight speed in world units per second. */
    public static final float SPEED = 6f;
    /** The collision radius of the ball. */
    public static final float RADIUS = 0.1f;
    /** Share of blast damage dealt to the player who fired it. */
    public static final float SELF_DAMAGE_SCALE = 0.5f;
    /** The longest distance the ball moves between two collision checks. */
    private static final float MAX_STEP = 0.08f;

    /** The centre of the ball; once it has exploded, the centre of the explosion. */
    public final Vector3 position = new Vector3();
    private final Vector3 direction = new Vector3();
    private final float charge;
    private boolean exploded;

    /**
     * @param origin the starting position, copied
     * @param direction the unit flight direction, copied
     * @param charge the charge it was fired with, from {@link BlastCharger#MIN_CHARGE} to 1
     */
    public BlastProjectile(Vector3 origin, Vector3 direction, float charge) {
        this.position.set(origin);
        this.direction.set(direction);
        this.charge = charge;
    }

    /**
     * @param charge the charge a blast was fired with
     * @return the radius of its explosion in world units
     */
    public static float blastRadius(float charge) {
        return 1f + 2f * charge;
    }

    /**
     * @param charge the charge a blast was fired with
     * @return the damage at the centre of its explosion
     */
    public static float blastDamage(float charge) {
        return 30f + 100f * charge;
    }

    /** @return the charge this ball was fired with. */
    public float getCharge() {
        return charge;
    }

    /** @return whether the ball has exploded. */
    public boolean isExploded() {
        return exploded;
    }

    /**
     * Flies for one time step without a player target.
     * @param dt the time step in seconds
     * @param map the map to collide with
     * @param targets the objects the ball can hit and its explosion can damage
     * @return whether the ball exploded during this step
     */
    public boolean update(float dt, GameMap map, List<? extends Damageable> targets) {
        return update(dt, map, targets, null, 0f);
    }

    /**
     * Flies for one time step. The step is walked in pieces shorter than the ball, so it cannot pass through a wall
     * or a target however long the step is. On contact the ball stops at its last free position, explodes there and
     * applies its wall-occluded area damage.
     * @param dt the time step in seconds
     * @param map the map to collide with
     * @param targets the objects the ball can hit and its explosion can damage
     * @param player optional player to take scaled splash self-damage, or null
     * @param playerDamageScale multiplier applied to splash damage on {@code player}
     * @return whether the ball exploded during this step
     */
    public boolean update(float dt, GameMap map, List<? extends Damageable> targets, Player player,
                          float playerDamageScale) {
        if (exploded) return false;
        float distance = SPEED * dt;
        int steps = Math.max(1, (int) Math.ceil(distance / MAX_STEP));
        float step = distance / steps;
        for (int i = 0; i < steps; i++) {
            float x = position.x + direction.x * step;
            float y = position.y + direction.y * step;
            float z = position.z + direction.z * step;
            if (touches(x, y, z, map, targets)) {
                exploded = true;
                Explosion.apply(map, position.x, position.z, blastRadius(charge), blastDamage(charge), targets,
                    player, playerDamageScale);
                return true;
            }
            position.set(x, y, z);
        }
        return false;
    }

    private static boolean touches(float x, float y, float z, GameMap map, List<? extends Damageable> targets) {
        if (y - RADIUS <= 0f || y + RADIUS >= GameMap.WALL_HEIGHT) return true;
        if (Collision.overlapsWall(map, x, z, RADIUS)) return true;
        for (int i = 0; i < targets.size(); i++) {
            Damageable target = targets.get(i);
            if (target.isDestroyed() || y - RADIUS > target.getHeight()) continue;
            float dx = target.getX() - x;
            float dz = target.getZ() - z;
            float reach = target.getRadius() + RADIUS;
            if (dx * dx + dz * dz < reach * reach) return true;
        }
        return false;
    }
}

package io.github.brantunger.gamex.weapon;

import com.badlogic.gdx.math.Vector3;
import io.github.brantunger.gamex.player.Player;
import io.github.brantunger.gamex.world.Collision;
import io.github.brantunger.gamex.world.Damageable;
import io.github.brantunger.gamex.world.Door;
import io.github.brantunger.gamex.world.GameMap;

import java.util.Collections;
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
    /** Binary refinements used to land right against a contact surface on impact. */
    private static final int CONTACT_REFINE_STEPS = 8;
    /** Safety cap on distance walked in a single update call to avoid extreme-dt loop stalls. */
    private static final float MAX_STEP_DISTANCE = 10_000f;

    /** The centre of the ball; once it has exploded, the centre of the explosion. */
    public final Vector3 position = new Vector3();
    private final Vector3 direction = new Vector3();
    private final float charge;
    private final WeaponTuning tuning;
    private boolean exploded;

    /**
     * @param origin the starting position, copied
     * @param direction the flight direction, normalised and copied (defaulting to {@code (0, 0, -1)} if degenerate)
     * @param charge the charge it was fired with, from {@link WeaponTuning#blastMinCharge} to 1
     * @param tuning the weapon balance; its blast values size the explosion
     */
    public BlastProjectile(Vector3 origin, Vector3 direction, float charge, WeaponTuning tuning) {
        if (tuning == null) throw new IllegalArgumentException("Weapon tuning must not be null");
        if (origin != null) {
            this.position.set(origin);
        }
        setNormalizedDirection(direction, this.direction);
        this.charge = charge;
        this.tuning = tuning;
    }

    private static void setNormalizedDirection(Vector3 in, Vector3 out) {
        if (in != null && Float.isFinite(in.x) && Float.isFinite(in.y) && Float.isFinite(in.z)) {
            double len = Math.sqrt((double) in.x * in.x + (double) in.y * in.y + (double) in.z * in.z);
            if (len >= 1e-6) {
                out.set((float) (in.x / len), (float) (in.y / len), (float) (in.z / len));
                return;
            }
        }
        out.set(0f, 0f, -1f);
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
        return update(dt, map, targets, Collections.<Door>emptyList(), player, playerDamageScale);
    }

    /**
     * Flies for one time step against map walls, bulkhead door slabs, and live {@link Damageable} targets.
     * @param dt the time step in seconds
     * @param map the map to collide with
     * @param targets the objects the ball can hit and its explosion can damage
     * @param doors bulkhead doors that block both flight and blast splash while closed
     * @param player optional player to take scaled splash self-damage, or null
     * @param playerDamageScale multiplier applied to splash damage on {@code player}
     * @return whether the ball exploded during this step
     */
    public boolean update(float dt, GameMap map, List<? extends Damageable> targets, List<Door> doors,
                          Player player, float playerDamageScale) {
        if (exploded || !Float.isFinite(dt) || dt <= 0f) return false;
        if (!Float.isFinite(position.x) || !Float.isFinite(position.y) || !Float.isFinite(position.z)) {
            exploded = true;
            return false;
        }
        if (touches(position.x, position.y, position.z, map, targets, doors)) {
            return detonate(map, targets, doors, player, playerDamageScale);
        }
        float distance = (float) Math.min((double) SPEED * dt, MAX_STEP_DISTANCE);
        int steps = Math.max(1, (int) Math.ceil(distance / MAX_STEP));
        float step = distance / steps;
        for (int i = 0; i < steps; i++) {
            float x = position.x + direction.x * step;
            float y = position.y + direction.y * step;
            float z = position.z + direction.z * step;
            if (touches(x, y, z, map, targets, doors)) {
                float freeX = position.x;
                float freeY = position.y;
                float freeZ = position.z;
                float blockedX = x;
                float blockedY = y;
                float blockedZ = z;
                for (int r = 0; r < CONTACT_REFINE_STEPS; r++) {
                    float midX = (freeX + blockedX) * 0.5f;
                    float midY = (freeY + blockedY) * 0.5f;
                    float midZ = (freeZ + blockedZ) * 0.5f;
                    if (touches(midX, midY, midZ, map, targets, doors)) {
                        blockedX = midX;
                        blockedY = midY;
                        blockedZ = midZ;
                    } else {
                        freeX = midX;
                        freeY = midY;
                        freeZ = midZ;
                    }
                }
                position.set(freeX, freeY, freeZ);
                return detonate(map, targets, doors, player, playerDamageScale);
            }
            position.set(x, y, z);
        }
        return false;
    }

    private boolean detonate(GameMap map, List<? extends Damageable> targets, List<Door> doors,
                             Player player, float playerDamageScale) {
        exploded = true;
        Explosion.apply(map, doors, position.x, position.y, position.z, tuning.blastRadius(charge),
            tuning.blastDamage(charge), targets, player, playerDamageScale);
        return true;
    }

    private static boolean touches(float x, float y, float z, GameMap map, List<? extends Damageable> targets,
                                   List<Door> doors) {
        if (y - RADIUS <= 0f || y + RADIUS >= GameMap.WALL_HEIGHT) return true;
        if (map != null && Collision.overlapsWall(map, x, z, RADIUS)) return true;
        if (doors != null) {
            for (int i = 0; i < doors.size(); i++) {
                if (doors.get(i).overlapsSphere(x, y, z, RADIUS)) return true;
            }
        }
        if (targets != null) {
            for (int i = 0; i < targets.size(); i++) {
                Damageable target = targets.get(i);
                if (target == null || target.isDestroyed() || y - RADIUS > target.getHeight() || y + RADIUS < 0f) {
                    continue;
                }
                float dx = target.getX() - x;
                float dz = target.getZ() - z;
                float reach = target.getRadius() + RADIUS;
                if (dx * dx + dz * dz < reach * reach) return true;
            }
        }
        return false;
    }
}

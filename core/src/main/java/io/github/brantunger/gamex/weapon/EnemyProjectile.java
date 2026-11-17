package io.github.brantunger.gamex.weapon;

import com.badlogic.gdx.math.Vector3;
import io.github.brantunger.gamex.player.Player;
import io.github.brantunger.gamex.world.Collision;
import io.github.brantunger.gamex.world.Door;
import io.github.brantunger.gamex.world.Effect;
import io.github.brantunger.gamex.world.Enemy;
import io.github.brantunger.gamex.world.GameMap;
import io.github.brantunger.gamex.world.SecurityConsole;
import io.github.brantunger.gamex.world.Target;

import java.util.Collections;
import java.util.List;

/**
 * A dodgeable enemy plasma bolt. It travels in a straight line and explodes in a spark on the first wall, floor,
 * ceiling, player, target (such as a volatile coolant canister), or fellow enemy it strikes.
 */
public final class EnemyProjectile {

    /** The collision radius of the bolt. */
    public static final float RADIUS = 0.075f;
    /** The longest distance the bolt moves between collision checks. */
    private static final float MAX_STEP = 0.06f;

    /** Current world position of the bolt's centre. */
    public final Vector3 position = new Vector3();
    private final Vector3 direction = new Vector3();
    private final float speed;
    private final float damage;
    private final Enemy owner;
    private boolean finished;

    /**
     * @param origin starting world position, copied
     * @param direction flight direction, normalised and copied
     * @param speed travel speed in world units per second
     * @param damage base damage dealt to the player (before difficulty scaling) or a world target
     * @param owner firing enemy, excluded from self-blocking
     */
    public EnemyProjectile(Vector3 origin, Vector3 direction, float speed, float damage, Enemy owner) {
        this.position.set(origin);
        this.direction.set(direction);
        if (this.direction.len2() > 1e-8f) {
            this.direction.nor();
        } else {
            this.direction.set(0f, 0f, -1f);
        }
        this.speed = speed;
        this.damage = damage;
        this.owner = owner;
    }

    /** @return whether the bolt has struck something and should be removed. */
    public boolean isFinished() {
        return finished;
    }

    /** @return base damage carried by this bolt. */
    public float getDamage() {
        return damage;
    }

    /** @return the enemy that fired this bolt, or null in tests. */
    public Enemy getOwner() {
        return owner;
    }

    /**
     * Advances the bolt for one time step.
     * @return whether the bolt finished during this update
     */
    public boolean update(float dt, GameMap map, Player player, float playerDamageScale,
                          List<Target> targets, List<Enemy> enemies, List<Effect> effects) {
        return update(dt, map, player, playerDamageScale, targets, enemies,
            Collections.<Door>emptyList(), Collections.<SecurityConsole>emptyList(), effects);
    }

    /**
     * Advances the bolt for one time step against walls, doors, consoles, player, targets, and other enemies.
     * @return whether the bolt finished during this update
     */
    public boolean update(float dt, GameMap map, Player player, float playerDamageScale,
                          List<Target> targets, List<Enemy> enemies, List<Door> doors,
                          List<SecurityConsole> consoles, List<Effect> effects) {
        if (finished || dt <= 0f) return finished;
        float distance = speed * dt;
        int steps = Math.max(1, (int) Math.ceil(distance / MAX_STEP));
        float step = distance / steps;
        for (int i = 0; i < steps; i++) {
            float nx = position.x + direction.x * step;
            float ny = position.y + direction.y * step;
            float nz = position.z + direction.z * step;
            if (ny - RADIUS <= 0f || ny + RADIUS >= GameMap.WALL_HEIGHT
                || Collision.overlapsWall(map, nx, nz, RADIUS)
                || overlapsDoor3D(doors, nx, ny, nz)) {
                finish(effects);
                return true;
            }
            if (player != null && !player.isDead()
                && ny + RADIUS >= 0f && ny - RADIUS <= Player.EYE_HEIGHT + 0.18f) {
                float dx = player.position.x - nx;
                float dz = player.position.y - nz;
                float reach = Player.RADIUS + RADIUS;
                if (dx * dx + dz * dz < reach * reach) {
                    position.set(nx, ny, nz);
                    player.damage(damage * playerDamageScale, nx, nz);
                    finish(effects);
                    return true;
                }
            }
            if (consoles != null) {
                for (int c = 0; c < consoles.size(); c++) {
                    SecurityConsole console = consoles.get(c);
                    if (ny - RADIUS > console.getHeight() || ny + RADIUS < 0f) continue;
                    float dx = console.getX() - nx;
                    float dz = console.getZ() - nz;
                    float reach = console.getRadius() + RADIUS;
                    if (dx * dx + dz * dz < reach * reach) {
                        position.set(nx, ny, nz);
                        finish(effects);
                        return true;
                    }
                }
            }
            if (targets != null) {
                for (int t = 0; t < targets.size(); t++) {
                    Target target = targets.get(t);
                    if (target.isDestroyed() || ny - RADIUS > target.getHeight() || ny + RADIUS < 0f) continue;
                    float dx = target.getX() - nx;
                    float dz = target.getZ() - nz;
                    float reach = target.getRadius() + RADIUS;
                    if (dx * dx + dz * dz < reach * reach) {
                        position.set(nx, ny, nz);
                        target.damage(damage);
                        finish(effects);
                        return true;
                    }
                }
            }
            if (enemies != null) {
                for (int e = 0; e < enemies.size(); e++) {
                    Enemy enemy = enemies.get(e);
                    if (enemy == owner || enemy.isDestroyed()
                        || ny - RADIUS > enemy.getHeight() || ny + RADIUS < 0f) {
                        continue;
                    }
                    float dx = enemy.getX() - nx;
                    float dz = enemy.getZ() - nz;
                    float reach = enemy.getRadius() + RADIUS;
                    if (dx * dx + dz * dz < reach * reach) {
                        position.set(nx, ny, nz);
                        finish(effects);
                        return true;
                    }
                }
            }
            position.set(nx, ny, nz);
        }
        return false;
    }

    private static boolean overlapsDoor3D(List<Door> doors, float x, float y, float z) {
        if (doors == null) return false;
        for (int i = 0; i < doors.size(); i++) {
            if (doors.get(i).overlapsSphere(x, y, z, RADIUS)) return true;
        }
        return false;
    }

    private void finish(List<Effect> effects) {
        finished = true;
        if (effects != null) {
            effects.add(new Effect(Effect.Type.SPARK, position, Vector3.Y, 0.16f));
        }
    }
}

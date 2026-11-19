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
    /** Maximum lifetime in seconds before a stationary or unobstructed bolt expires. */
    public static final float MAX_LIFETIME = 10f;
    /** The longest distance the bolt moves between collision checks. */
    private static final float MAX_STEP = 0.06f;
    /** Binary refinements used to land right against a wall, door, floor, or ceiling surface on impact. */
    private static final int CONTACT_REFINE_STEPS = 8;
    /** Safety cap on distance walked in a single update call to avoid extreme-speed loop stalls. */
    private static final float MAX_STEP_DISTANCE = 10_000f;

    /** Current world position of the bolt's centre. */
    public final Vector3 position = new Vector3();
    private final Vector3 direction = new Vector3();
    private final float originX;
    private final float originZ;
    private final float speed;
    private final float damage;
    private final Enemy owner;
    private float age;
    private boolean finished;

    /**
     * @param origin starting world position, copied
     * @param direction flight direction, normalised and copied
     * @param speed travel speed in world units per second
     * @param damage base damage dealt to the player (before difficulty scaling) or a world target
     * @param owner firing enemy, excluded from self-blocking
     */
    public EnemyProjectile(Vector3 origin, Vector3 direction, float speed, float damage, Enemy owner) {
        if (origin != null) {
            this.position.set(origin);
            this.originX = origin.x;
            this.originZ = origin.z;
        } else {
            this.originX = 0f;
            this.originZ = 0f;
        }
        setNormalizedDirection(direction, this.direction);
        this.speed = Float.isFinite(speed) && speed > 0f ? speed : 0f;
        this.damage = Float.isFinite(damage) && damage > 0f ? damage : 0f;
        this.owner = owner;
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
        if (finished) return true;
        if (!Float.isFinite(dt) || dt <= 0f) return false;
        if (!Float.isFinite(position.x) || !Float.isFinite(position.y) || !Float.isFinite(position.z)) {
            finished = true;
            return true;
        }

        if (touchesSurface(position.x, position.y, position.z, map, doors)) {
            finish(effects);
            return true;
        }
        if (checkEntityImpact(position.x, position.y, position.z, player, playerDamageScale,
            consoles, targets, enemies, effects)) {
            return true;
        }

        if (!Float.isFinite(speed) || speed <= 0f) {
            age += dt;
            if (age >= MAX_LIFETIME) {
                finished = true;
                return true;
            }
            return false;
        }

        float distance = (float) Math.min((double) speed * dt, MAX_STEP_DISTANCE);
        int steps = Math.max(1, (int) Math.ceil(distance / MAX_STEP));
        float step = distance / steps;
        for (int i = 0; i < steps; i++) {
            float nx = position.x + direction.x * step;
            float ny = position.y + direction.y * step;
            float nz = position.z + direction.z * step;
            if (touchesSurface(nx, ny, nz, map, doors)) {
                refineSurfaceContact(nx, ny, nz, map, doors);
                finish(effects);
                return true;
            }
            if (checkEntityImpact(nx, ny, nz, player, playerDamageScale,
                consoles, targets, enemies, effects)) {
                return true;
            }
            position.set(nx, ny, nz);
        }

        age += dt;
        if (age >= MAX_LIFETIME) {
            finished = true;
            return true;
        }
        return false;
    }

    private void refineSurfaceContact(float blockedX, float blockedY, float blockedZ,
                                      GameMap map, List<Door> doors) {
        float freeX = position.x;
        float freeY = position.y;
        float freeZ = position.z;
        for (int r = 0; r < CONTACT_REFINE_STEPS; r++) {
            float midX = (freeX + blockedX) * 0.5f;
            float midY = (freeY + blockedY) * 0.5f;
            float midZ = (freeZ + blockedZ) * 0.5f;
            if (touchesSurface(midX, midY, midZ, map, doors)) {
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
    }

    private boolean checkEntityImpact(float x, float y, float z, Player player, float playerDamageScale,
                                      List<SecurityConsole> consoles, List<Target> targets,
                                      List<Enemy> enemies, List<Effect> effects) {
        if (player != null && !player.isDead()
            && y + RADIUS >= player.getFeetY() && y - RADIUS <= player.getTopY()) {
            float dx = player.position.x - x;
            float dz = player.position.y - z;
            float reach = Player.RADIUS + RADIUS;
            if (dx * dx + dz * dz < reach * reach) {
                position.set(x, y, z);
                damagePlayer(player, playerDamageScale);
                finish(effects);
                return true;
            }
        }
        if (consoles != null) {
            for (int c = 0; c < consoles.size(); c++) {
                SecurityConsole console = consoles.get(c);
                if (console == null || y - RADIUS > console.getHeight() || y + RADIUS < 0f) continue;
                float dx = console.getX() - x;
                float dz = console.getZ() - z;
                float reach = console.getRadius() + RADIUS;
                if (dx * dx + dz * dz < reach * reach) {
                    position.set(x, y, z);
                    finish(effects);
                    return true;
                }
            }
        }
        if (targets != null) {
            for (int t = 0; t < targets.size(); t++) {
                Target target = targets.get(t);
                if (target == null || target.isDestroyed()
                    || y - RADIUS > target.getHeight() || y + RADIUS < 0f) {
                    continue;
                }
                float dx = target.getX() - x;
                float dz = target.getZ() - z;
                float reach = target.getRadius() + RADIUS;
                if (dx * dx + dz * dz < reach * reach) {
                    position.set(x, y, z);
                    target.damage(damage);
                    finish(effects);
                    return true;
                }
            }
        }
        if (enemies != null) {
            for (int e = 0; e < enemies.size(); e++) {
                Enemy enemy = enemies.get(e);
                if (enemy == null || enemy == owner || enemy.isDestroyed()
                    || y - RADIUS > enemy.getHeight() || y + RADIUS < 0f) {
                    continue;
                }
                float dx = enemy.getX() - x;
                float dz = enemy.getZ() - z;
                float reach = enemy.getRadius() + RADIUS;
                if (dx * dx + dz * dz < reach * reach) {
                    position.set(x, y, z);
                    finish(effects);
                    return true;
                }
            }
        }
        return false;
    }

    private void damagePlayer(Player player, float playerDamageScale) {
        float scale = Float.isFinite(playerDamageScale) ? Math.max(0f, playerDamageScale) : 0f;
        float amount = damage * scale;
        if (amount <= 0f) return;
        double horizLen = Math.hypot(direction.x, direction.z);
        float sourceX;
        float sourceZ;
        if (horizLen > 1e-6) {
            sourceX = player.position.x - (float) (direction.x / horizLen);
            sourceZ = player.position.y - (float) (direction.z / horizLen);
        } else if (owner != null
            && Math.hypot(owner.getX() - player.position.x, owner.getZ() - player.position.y) > 1e-3) {
            sourceX = owner.getX();
            sourceZ = owner.getZ();
        } else {
            sourceX = originX;
            sourceZ = originZ;
        }
        player.damage(amount, sourceX, sourceZ);
    }

    private static boolean touchesSurface(float x, float y, float z, GameMap map, List<Door> doors) {
        return y - RADIUS <= 0f
            || y + RADIUS >= GameMap.WALL_HEIGHT
            || (map != null && Collision.overlapsWall(map, x, z, RADIUS))
            || overlapsDoor3D(doors, x, y, z);
    }

    private static boolean overlapsDoor3D(List<Door> doors, float x, float y, float z) {
        if (doors == null) return false;
        for (int i = 0; i < doors.size(); i++) {
            Door door = doors.get(i);
            if (door != null && door.overlapsSphere(x, y, z, RADIUS)) return true;
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

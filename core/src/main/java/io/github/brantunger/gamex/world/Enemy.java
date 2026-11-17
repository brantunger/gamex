package io.github.brantunger.gamex.world;

import com.badlogic.gdx.math.Vector2;
import com.badlogic.gdx.math.Vector3;
import io.github.brantunger.gamex.player.Difficulty;
import io.github.brantunger.gamex.player.Player;
import io.github.brantunger.gamex.weapon.EnemyProjectile;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

/**
 * A hostile robotic combatant occupying an upright floor cylinder and implementing {@link Damageable}. Supports three
 * archetypes ({@link Kind#DRONE}, {@link Kind#TURRET}, {@link Kind#GUARD}) with cone/proximity/noise wake-up, bounded
 * aggro memory, grid BFS navigation, telegraphed attacks and difficulty-scaled cadence.
 */
public final class Enemy implements Damageable {

    /** Enemy archetypes with constant hit-point breakpoints and distinct tactical roles. */
    public enum Kind {
        /** Fast close-range hover unit that rushes around cover and performs a telegraphed shock lunge. */
        DRONE(40f, 0.21f, 0.58f, 3.5f),
        /** Anchored dual-barrel turret with a capped swivel rate that fires telegraphed 3-bolt bursts. */
        TURRET(80f, 0.23f, 0.68f, 0f),
        /** Mobile armoured skirmisher that strafes at medium range and fires single aimed plasma bolts. */
        GUARD(100f, 0.24f, 0.82f, 2.15f),
        /** Two-phase heavy walker boss firing fan volleys and telegraphed hydraulic charge slams. */
        WARDEN(600f, 0.32f, 0.94f, 1.95f);

        public final float maxHealth;
        public final float radius;
        public final float height;
        public final float moveSpeed;

        Kind(float maxHealth, float radius, float height, float moveSpeed) {
            this.maxHealth = maxHealth;
            this.radius = radius;
            this.height = height;
            this.moveSpeed = moveSpeed;
        }
    }

    /** High-level combat AI states. */
    public enum State {
        DORMANT,
        PURSUING,
        WINDUP,
        LUNGING,
        RECOVERING
    }

    public static final float HIT_FLASH_TIME = 0.15f;
    public static final float SIGHT_RANGE = 15f;
    public static final float CLOSE_SENSE_RANGE = 3.2f;
    /** Cosine of half of the 200-degree forward detection cone. */
    public static final float FOV_COS = (float) Math.cos(Math.toRadians(100.0));
    public static final float AGGRO_MEMORY_TIME = 6.0f;

    public static final float TURRET_TURN_RATE = 150f;
    public static final float TURRET_WINDUP_TIME = 0.35f;
    public static final float TURRET_BURST_INTERVAL = 0.11f;
    public static final int TURRET_BURST_COUNT = 3;
    public static final float TURRET_COOLDOWN = 1.35f;
    public static final float TURRET_BOLT_SPEED = 8.5f;
    public static final float TURRET_BOLT_DAMAGE = 12f;

    public static final float GUARD_WINDUP_TIME = 0.28f;
    public static final float GUARD_COOLDOWN = 1.15f;
    public static final float GUARD_BOLT_SPEED = 7.2f;
    public static final float GUARD_BOLT_DAMAGE = 18f;
    public static final float GUARD_MIN_RANGE = 3.5f;
    public static final float GUARD_MAX_RANGE = 7.5f;
    public static final float GUARD_ATTACK_RANGE = 15.5f;

    public static final float DRONE_TRIGGER_RANGE = 0.95f;
    public static final float DRONE_WINDUP_TIME = 0.26f;
    public static final float DRONE_LUNGE_TIME = 0.18f;
    public static final float DRONE_LUNGE_SPEED = 8.5f;
    public static final float DRONE_LUNGE_DAMAGE = 22f;
    public static final float DRONE_RECOVER_TIME = 0.50f;

    public static final float WARDEN_OVERDRIVE_RATIO = 0.5f;
    public static final float WARDEN_OVERDRIVE_SCALE = 1.30f;
    public static final float WARDEN_VOLLEY_RANGE = 16.0f;
    public static final float WARDEN_WINDUP_TIME = 0.38f;
    public static final float WARDEN_COOLDOWN = 1.40f;
    public static final float WARDEN_BOLT_SPEED = 7.8f;
    public static final float WARDEN_BOLT_DAMAGE = 14f;
    public static final int WARDEN_FAN_PHASE1 = 3;
    public static final int WARDEN_FAN_PHASE2 = 5;
    public static final float WARDEN_FAN_SPREAD_DEG_PHASE1 = 18f;
    public static final float WARDEN_FAN_SPREAD_DEG_PHASE2 = 30f;
    public static final float WARDEN_SLAM_TRIGGER_RANGE = 2.65f;
    public static final float WARDEN_SLAM_WINDUP_TIME = 0.42f;
    public static final float WARDEN_SLAM_LUNGE_TIME = 0.24f;
    public static final float WARDEN_SLAM_LUNGE_SPEED = 9.2f;
    public static final float WARDEN_SLAM_DAMAGE = 26f;
    public static final float WARDEN_SLAM_RECOVER_TIME = 0.48f;

    private static final float WAKE_COOLDOWN = 0.25f;
    private static final float STRAFE_SWITCH_TIME = 1.8f;

    private final Kind kind;
    private final Vector2 position = new Vector2();
    private final Vector2 steerDir = new Vector2();
    private final List<Damageable> otherBlockers = new ArrayList<>();
    private float yaw;
    private float health;
    private float hitFlash;
    private State state = State.DORMANT;
    private float aggroTimer;
    private float cooldown;
    private float stateTimer;
    private float stateDuration;
    private float burstTimer;
    private int burstRemaining;
    private boolean alternateBarrel;
    private float lungeDirX;
    private float lungeDirZ;
    private boolean lungeHitDealt;
    private boolean windupIsSlam;
    private boolean preferSlamNext;
    private boolean overdriveTriggered;
    private boolean slamTriggered;
    private float shootRecoil;
    private float strafeSign = 1f;
    private float strafeTimer = STRAFE_SWITCH_TIME;
    private float walkPhase;
    private float aimPitch;

    /**
     * Spawns an enemy at {@code (x, z)} facing the longest unobstructed cardinal sightline.
     */
    public Enemy(Kind kind, GameMap map, float x, float z) {
        this(kind, x, z, initialYaw(map, x, z));
    }

    /**
     * Spawns an enemy at {@code (x, z)} with an explicit initial facing yaw in degrees.
     */
    public Enemy(Kind kind, float x, float z, float yaw) {
        this.kind = kind;
        this.position.set(x, z);
        this.yaw = normalizeYaw(yaw);
        this.health = kind.maxHealth;
    }

    /**
     * Picks the cardinal direction (0, 90, 180, 270) with the longest unobstructed floor line from {@code (x, z)}.
     */
    public static float initialYaw(GameMap map, float x, float z) {
        if (map == null) return 0f;
        int col = (int) Math.floor(x);
        int row = (int) Math.floor(z);
        int[] dx = {0, 1, 0, -1};
        int[] dz = {-1, 0, 1, 0};
        float bestYaw = 0f;
        int bestSteps = -1;
        for (int dir = 0; dir < 4; dir++) {
            int steps = 0;
            int c = col + dx[dir];
            int r = row + dz[dir];
            while (!map.isSolid(c, r)) {
                steps++;
                c += dx[dir];
                r += dz[dir];
            }
            if (steps > bestSteps) {
                bestSteps = steps;
                bestYaw = dir * 90f;
            }
        }
        return bestYaw;
    }

    public Kind getKind() {
        return kind;
    }

    @Override
    public float getX() {
        return position.x;
    }

    @Override
    public float getZ() {
        return position.y;
    }

    @Override
    public float getRadius() {
        return kind.radius;
    }

    @Override
    public float getHeight() {
        return kind.height;
    }

    @Override
    public float getHealth() {
        return health;
    }

    @Override
    public boolean isDestroyed() {
        return health <= 0f;
    }

    public float getYaw() {
        return yaw;
    }

    public State getState() {
        return state;
    }

    public float getHitFlash() {
        return hitFlash / HIT_FLASH_TIME;
    }

    public float getWalkPhase() {
        return walkPhase;
    }

    /** @return vertical barrel aim pitch in degrees (positive pitches slightly upward). */
    public float getAimPitch() {
        return aimPitch;
    }

    /**
     * @return wind-up intensity in [0, 1] during attack telegraphing or burst fire, otherwise 0.
     */
    public float getWindupProgress() {
        if (state != State.WINDUP) return 0f;
        if (burstRemaining > 0) return 1f;
        if (stateDuration <= 0f) return 0f;
        return Math.max(0f, Math.min(1f, 1f - stateTimer / stateDuration));
    }

    /** @return whether this enemy is a live Warden Mech operating in Phase 2 Overdrive (&le; 50% HP). */
    public boolean isOverdrive() {
        return kind == Kind.WARDEN && !isDestroyed() && health <= kind.maxHealth * WARDEN_OVERDRIVE_RATIO;
    }

    /** @return true once when a Warden Mech transitions across 50% HP into Phase 2 Overdrive. */
    public boolean consumeOverdriveTriggered() {
        boolean triggered = overdriveTriggered;
        overdriveTriggered = false;
        return triggered;
    }

    /** @return true once when a Warden Mech launches a hydraulic charge slam. */
    public boolean consumeSlamTriggered() {
        boolean triggered = slamTriggered;
        slamTriggered = false;
        return triggered;
    }

    /** @return true while a Warden Mech is winding up or executing a hydraulic charge slam. */
    public boolean isSlamAttack() {
        return kind == Kind.WARDEN && ((state == State.WINDUP && windupIsSlam) || state == State.LUNGING);
    }

    /** @return firing recoil impulse in [0, 1] for cannon arm animation. */
    public float getShootRecoil() {
        return shootRecoil;
    }

    /**
     * Wakes the enemy into {@link State#PURSUING} (or refreshes its aggro memory if already alert).
     */
    public void alert() {
        if (isDestroyed()) return;
        aggroTimer = AGGRO_MEMORY_TIME;
        if (state == State.DORMANT) {
            state = State.PURSUING;
            cooldown = Math.max(cooldown, WAKE_COOLDOWN);
        }
    }

    @Override
    public void damage(float amount) {
        if (amount <= 0f || isDestroyed()) return;
        boolean wasOverdrive = isOverdrive();
        health = Math.max(0f, health - amount);
        hitFlash = HIT_FLASH_TIME;
        if (!isDestroyed()) {
            if (!wasOverdrive && isOverdrive()) {
                overdriveTriggered = true;
            }
            alert();
        }
    }

    /**
     * Advances enemy perception, pathing, attack state machine and projectile firing for one step.
     */
    public void update(float dt, GameMap map, Pathfinder pathfinder, Player player, Difficulty difficulty,
                       List<? extends Damageable> worldBlockers, List<EnemyProjectile> outProjectiles) {
        update(dt, map, pathfinder, player, difficulty, worldBlockers,
            Collections.<Door>emptyList(), outProjectiles);
    }

    /**
     * Advances enemy perception, pathing, attack state machine and projectile firing for one step, respecting
     * bulkhead doors for sightlines, BFS routing, and movement collision.
     */
    public void update(float dt, GameMap map, Pathfinder pathfinder, Player player, Difficulty difficulty,
                       List<? extends Damageable> worldBlockers, List<Door> doors,
                       List<EnemyProjectile> outProjectiles) {
        if (dt <= 0f) return;
        hitFlash = Math.max(0f, hitFlash - dt);
        shootRecoil = Math.max(0f, shootRecoil - dt * 4.5f);
        if (isDestroyed() || player == null || player.isDead()) {
            if (state == State.WINDUP || state == State.LUNGING || state == State.RECOVERING) {
                state = State.DORMANT;
                stateTimer = 0f;
                burstRemaining = 0;
            }
            return;
        }

        cooldown = Math.max(0f, cooldown - dt);
        float dx = player.position.x - position.x;
        float dz = player.position.y - position.y;
        float dist = (float) Math.hypot(dx, dz);
        boolean hasLos = dist < 1e-4f || Hitscan.hasLineOfSight(map, doors, position.x, position.y,
            player.position.x, player.position.y);

        if (state == State.DORMANT) {
            if (canDetect(dx, dz, dist, hasLos)) {
                alert();
            } else {
                return;
            }
        } else {
            if (hasLos && dist <= SIGHT_RANGE * 1.25f) {
                aggroTimer = AGGRO_MEMORY_TIME;
            } else {
                aggroTimer = Math.max(0f, aggroTimer - dt);
                if (aggroTimer <= 0f && state == State.PURSUING) {
                    state = State.DORMANT;
                    return;
                }
            }
        }

        float speedScale = difficulty == null ? 1f : difficulty.enemySpeedScale;
        float damageScale = difficulty == null ? 1f : difficulty.damageTakenScale;
        switch (kind) {
            case TURRET:
                updateTurret(dt, dx, dz, dist, hasLos, speedScale, player, outProjectiles);
                break;
            case GUARD:
                updateGuard(dt, map, pathfinder, dx, dz, dist, hasLos, speedScale, player, worldBlockers, doors,
                    outProjectiles);
                break;
            case WARDEN:
                updateWarden(dt, map, pathfinder, dx, dz, dist, hasLos, speedScale, damageScale, player,
                    worldBlockers, doors, outProjectiles);
                break;
            case DRONE:
            default:
                updateDrone(dt, map, pathfinder, dx, dz, dist, hasLos, speedScale, damageScale, player,
                    worldBlockers, doors);
                break;
        }
    }

    private boolean canDetect(float dx, float dz, float dist, boolean hasLos) {
        if (!hasLos || dist > SIGHT_RANGE) return false;
        if (dist <= CLOSE_SENSE_RANGE || dist < 1e-4f) return true;
        double yawRad = Math.toRadians(yaw);
        float fx = (float) Math.sin(yawRad);
        float fz = (float) -Math.cos(yawRad);
        float dot = (dx * fx + dz * fz) / dist;
        return dot >= FOV_COS;
    }

    private void updateTurret(float dt, float dx, float dz, float dist, boolean hasLos, float speedScale,
                              Player player, List<EnemyProjectile> outProjectiles) {
        if (hasLos && dist > 1e-4f) {
            float targetYaw = yawToward(dx, dz);
            yaw = turnToward(yaw, targetYaw, TURRET_TURN_RATE * dt);
            float targetPitch = (float) Math.toDegrees(
                Math.atan2((Player.EYE_HEIGHT - 0.06f) - WorldModels.TURRET_MUZZLE_Y, Math.max(0.1f, dist)));
            targetPitch = Math.max(-28f, Math.min(28f, targetPitch));
            aimPitch = moveToward(aimPitch, targetPitch, 90f * dt);
        } else {
            aimPitch = moveToward(aimPitch, 0f, 60f * dt);
        }

        if (state == State.WINDUP) {
            if (burstRemaining == 0) {
                if (!hasLos) {
                    state = State.PURSUING;
                    stateTimer = 0f;
                    cooldown = 0.3f;
                    return;
                }
                stateTimer -= dt;
                if (stateTimer <= 0f) {
                    burstRemaining = TURRET_BURST_COUNT;
                    fireTurretBolt(speedScale, player, outProjectiles);
                    burstRemaining--;
                    burstTimer = TURRET_BURST_INTERVAL / speedScale;
                }
            } else {
                burstTimer -= dt;
                while (burstTimer <= 0f && burstRemaining > 0) {
                    fireTurretBolt(speedScale, player, outProjectiles);
                    burstRemaining--;
                    if (burstRemaining > 0) {
                        burstTimer += TURRET_BURST_INTERVAL / speedScale;
                    }
                }
                if (burstRemaining == 0) {
                    state = State.PURSUING;
                    cooldown = TURRET_COOLDOWN / speedScale;
                }
            }
            return;
        }

        if (hasLos && dist <= SIGHT_RANGE && cooldown <= 0f && Math.abs(angleDiff(yaw, yawToward(dx, dz))) <= 18f) {
            state = State.WINDUP;
            stateDuration = TURRET_WINDUP_TIME / speedScale;
            stateTimer = stateDuration;
            burstRemaining = 0;
        }
    }

    private void fireTurretBolt(float speedScale, Player player, List<EnemyProjectile> outProjectiles) {
        if (outProjectiles == null) return;
        double rad = Math.toRadians(yaw);
        float fx = (float) Math.sin(rad);
        float fz = (float) -Math.cos(rad);
        float rx = (float) Math.cos(rad);
        float rz = (float) Math.sin(rad);
        float side = alternateBarrel ? WorldModels.TURRET_BARREL_X : -WorldModels.TURRET_BARREL_X;
        alternateBarrel = !alternateBarrel;
        float fwd = -WorldModels.TURRET_MUZZLE_Z;
        Vector3 muzzle = new Vector3(position.x + fx * fwd + rx * side, WorldModels.TURRET_MUZZLE_Y,
            position.y + fz * fwd + rz * side);
        Vector3 aim = new Vector3(player.position.x - muzzle.x, (Player.EYE_HEIGHT - 0.06f) - muzzle.y,
            player.position.y - muzzle.z).nor();
        outProjectiles.add(new EnemyProjectile(muzzle, aim, TURRET_BOLT_SPEED * speedScale, TURRET_BOLT_DAMAGE, this));
    }

    private void updateGuard(float dt, GameMap map, Pathfinder pathfinder, float dx, float dz, float dist,
                             boolean hasLos, float speedScale, Player player,
                             List<? extends Damageable> worldBlockers, List<Door> doors,
                             List<EnemyProjectile> outProjectiles) {
        strafeTimer -= dt;
        if (strafeTimer <= 0f) {
            strafeSign = -strafeSign;
            strafeTimer = STRAFE_SWITCH_TIME;
        }

        float moveScale = state == State.WINDUP ? 0.45f : 1f;
        float moveX = 0f;
        float moveZ = 0f;
        if (hasLos && dist > 1e-4f) {
            float dirX = dx / dist;
            float dirZ = dz / dist;
            float rightX = -dirZ;
            float rightZ = dirX;
            if (dist > GUARD_MAX_RANGE) {
                moveX = dirX + rightX * strafeSign * 0.35f;
                moveZ = dirZ + rightZ * strafeSign * 0.35f;
            } else if (dist < GUARD_MIN_RANGE) {
                moveX = -dirX * 0.8f + rightX * strafeSign * 0.6f;
                moveZ = -dirZ * 0.8f + rightZ * strafeSign * 0.6f;
            } else {
                float rangeAdjust = (dist - 5.5f) * 0.2f;
                moveX = rightX * strafeSign + dirX * rangeAdjust;
                moveZ = rightZ * strafeSign + dirZ * rangeAdjust;
            }
            yaw = turnToward(yaw, yawToward(dx, dz), 420f * dt);
        } else if (pathfinder != null && pathfinder.nextDirection(map, doors, position.x, position.y, steerDir)) {
            moveX = steerDir.x;
            moveZ = steerDir.y;
            yaw = turnToward(yaw, yawToward(moveX, moveZ), 360f * dt);
        }

        float moveLen = (float) Math.hypot(moveX, moveZ);
        if (moveLen > 1e-4f) {
            float dirX = moveX / moveLen;
            float dirZ = moveZ / moveLen;
            float step = kind.moveSpeed * moveScale * dt;
            boolean currentlyOnHazard = map != null && map.isHazardAt(position.x, position.y);
            boolean nextOnHazard = map != null && map.isHazardAt(
                position.x + dirX * (kind.radius + 0.25f),
                position.y + dirZ * (kind.radius + 0.25f));
            if (hasLos && !currentlyOnHazard && nextOnHazard) {
                strafeSign = -strafeSign;
                strafeTimer = STRAFE_SWITCH_TIME;
                step = 0f;
            }
            if (step > 0f) {
                float beforeX = position.x;
                float beforeZ = position.y;
                moveSolid(map, worldBlockers, doors, player, dirX * step, dirZ * step, true);
                float moved = Vector2.dst(beforeX, beforeZ, position.x, position.y);
                walkPhase += moved * 7.5f;
                if (moved < step * 0.25f) {
                    strafeSign = -strafeSign;
                    strafeTimer = STRAFE_SWITCH_TIME;
                }
            }
        }

        if (state == State.WINDUP) {
            stateTimer -= dt;
            if (stateTimer <= 0f) {
                if (hasLos) {
                    fireGuardBolt(speedScale, player, outProjectiles);
                }
                state = State.PURSUING;
                cooldown = GUARD_COOLDOWN / speedScale;
            }
            return;
        }

        if (hasLos && dist <= GUARD_ATTACK_RANGE && cooldown <= 0f) {
            state = State.WINDUP;
            stateDuration = GUARD_WINDUP_TIME / speedScale;
            stateTimer = stateDuration;
        }
    }

    private void fireGuardBolt(float speedScale, Player player, List<EnemyProjectile> outProjectiles) {
        if (outProjectiles == null) return;
        double rad = Math.toRadians(yaw);
        float fx = (float) Math.sin(rad);
        float fz = (float) -Math.cos(rad);
        float rx = (float) Math.cos(rad);
        float rz = (float) Math.sin(rad);
        float fwd = -WorldModels.GUARD_MUZZLE_Z;
        Vector3 muzzle = new Vector3(position.x + fx * fwd + rx * WorldModels.GUARD_MUZZLE_X,
            WorldModels.GUARD_MUZZLE_Y, position.y + fz * fwd + rz * WorldModels.GUARD_MUZZLE_X);
        Vector3 aim = new Vector3(player.position.x - muzzle.x, (Player.EYE_HEIGHT - 0.06f) - muzzle.y,
            player.position.y - muzzle.z).nor();
        outProjectiles.add(new EnemyProjectile(muzzle, aim, GUARD_BOLT_SPEED * speedScale, GUARD_BOLT_DAMAGE, this));
    }

    private void updateWarden(float dt, GameMap map, Pathfinder pathfinder, float dx, float dz, float dist,
                              boolean hasLos, float speedScale, float damageScale, Player player,
                              List<? extends Damageable> worldBlockers, List<Door> doors,
                              List<EnemyProjectile> outProjectiles) {
        float phaseScale = isOverdrive() ? WARDEN_OVERDRIVE_SCALE : 1.0f;
        float effectiveScale = speedScale * phaseScale;

        if (state == State.WINDUP) {
            if (hasLos && dist > 1e-4f) {
                yaw = turnToward(yaw, yawToward(dx, dz), (windupIsSlam ? 260f : 340f) * phaseScale * dt);
            }
            stateTimer -= dt;
            if (stateTimer <= 0f) {
                if (windupIsSlam) {
                    if (hasLos && dist > 1e-4f) {
                        lungeDirX = dx / dist;
                        lungeDirZ = dz / dist;
                        yaw = yawToward(lungeDirX, lungeDirZ);
                    }
                    state = State.LUNGING;
                    stateDuration = WARDEN_SLAM_LUNGE_TIME;
                    stateTimer = stateDuration;
                    lungeHitDealt = false;
                    slamTriggered = true;
                } else {
                    if (hasLos) {
                        fireWardenFanVolley(effectiveScale, player, outProjectiles);
                    }
                    state = State.PURSUING;
                    cooldown = WARDEN_COOLDOWN / effectiveScale;
                    preferSlamNext = dist <= WARDEN_SLAM_TRIGGER_RANGE * 1.4f;
                }
            }
            return;
        }

        if (state == State.LUNGING) {
            stateTimer -= dt;
            float step = WARDEN_SLAM_LUNGE_SPEED * phaseScale * dt;
            float beforeX = position.x;
            float beforeZ = position.y;
            moveSolid(map, worldBlockers, doors, player, lungeDirX * step, lungeDirZ * step, false);
            walkPhase += Vector2.dst(beforeX, beforeZ, position.x, position.y) * 5.0f;
            float nowDist = Vector2.dst(position.x, position.y, player.position.x, player.position.y);
            float contactDist = Player.RADIUS + kind.radius + 0.24f;
            if (!lungeHitDealt && nowDist <= contactDist) {
                player.damage(WARDEN_SLAM_DAMAGE * damageScale, position.x, position.y);
                lungeHitDealt = true;
                keepOutsidePlayer(map, worldBlockers, doors, player);
                enterWardenRecovering(effectiveScale);
                return;
            }
            if (stateTimer <= 0f) {
                keepOutsidePlayer(map, worldBlockers, doors, player);
                enterWardenRecovering(effectiveScale);
            }
            return;
        }

        if (state == State.RECOVERING) {
            stateTimer -= dt;
            if (stateTimer <= 0f) {
                state = State.PURSUING;
                cooldown = (WARDEN_COOLDOWN * 0.65f) / effectiveScale;
            }
            return;
        }

        float moveX = 0f;
        float moveZ = 0f;
        if (hasLos && dist > 1e-4f) {
            yaw = turnToward(yaw, yawToward(dx, dz), 320f * phaseScale * dt);
            if (dist > 1.35f) {
                moveX = dx / dist;
                moveZ = dz / dist;
            }
        } else if (pathfinder != null && pathfinder.nextDirection(map, doors, position.x, position.y, steerDir)) {
            moveX = steerDir.x;
            moveZ = steerDir.y;
            yaw = turnToward(yaw, yawToward(moveX, moveZ), 280f * phaseScale * dt);
        }

        if (Math.hypot(moveX, moveZ) > 1e-4f) {
            float step = kind.moveSpeed * phaseScale * dt;
            float beforeX = position.x;
            float beforeZ = position.y;
            moveSolid(map, worldBlockers, doors, player, moveX * step, moveZ * step, true);
            walkPhase += Vector2.dst(beforeX, beforeZ, position.x, position.y) * 5.5f;
        }

        if (hasLos && cooldown <= 0f) {
            if (dist <= WARDEN_SLAM_TRIGGER_RANGE && (preferSlamNext || dist <= 1.75f)) {
                if (dist > 1e-4f) {
                    lungeDirX = dx / dist;
                    lungeDirZ = dz / dist;
                } else {
                    double rad = Math.toRadians(yaw);
                    lungeDirX = (float) Math.sin(rad);
                    lungeDirZ = (float) -Math.cos(rad);
                }
                yaw = yawToward(lungeDirX, lungeDirZ);
                windupIsSlam = true;
                state = State.WINDUP;
                stateDuration = WARDEN_SLAM_WINDUP_TIME / effectiveScale;
                stateTimer = stateDuration;
                return;
            }
            if (dist <= WARDEN_VOLLEY_RANGE) {
                windupIsSlam = false;
                state = State.WINDUP;
                stateDuration = WARDEN_WINDUP_TIME / effectiveScale;
                stateTimer = stateDuration;
                preferSlamNext = dist <= WARDEN_SLAM_TRIGGER_RANGE * 1.5f;
            }
        }
    }

    private void enterWardenRecovering(float effectiveScale) {
        state = State.RECOVERING;
        stateDuration = WARDEN_SLAM_RECOVER_TIME / effectiveScale;
        stateTimer = stateDuration;
        preferSlamNext = false;
    }

    private void fireWardenFanVolley(float effectiveScale, Player player, List<EnemyProjectile> outProjectiles) {
        shootRecoil = 1f;
        if (outProjectiles == null) return;
        boolean overdrive = isOverdrive();
        int count = overdrive ? WARDEN_FAN_PHASE2 : WARDEN_FAN_PHASE1;
        float spreadDeg = overdrive ? WARDEN_FAN_SPREAD_DEG_PHASE2 : WARDEN_FAN_SPREAD_DEG_PHASE1;

        double rad = Math.toRadians(yaw);
        float fx = (float) Math.sin(rad);
        float fz = (float) -Math.cos(rad);
        float rx = (float) Math.cos(rad);
        float rz = (float) Math.sin(rad);
        float fwd = -WorldModels.WARDEN_MUZZLE_Z;
        Vector3 leftMuzzle = new Vector3(
            position.x + fx * fwd - rx * WorldModels.WARDEN_MUZZLE_X,
            WorldModels.WARDEN_MUZZLE_Y,
            position.y + fz * fwd - rz * WorldModels.WARDEN_MUZZLE_X);
        Vector3 rightMuzzle = new Vector3(
            position.x + fx * fwd + rx * WorldModels.WARDEN_MUZZLE_X,
            WorldModels.WARDEN_MUZZLE_Y,
            position.y + fz * fwd + rz * WorldModels.WARDEN_MUZZLE_X);
        Vector3 baseAim = new Vector3(
            player.position.x - (position.x + fx * fwd),
            (Player.EYE_HEIGHT - 0.08f) - WorldModels.WARDEN_MUZZLE_Y,
            player.position.y - (position.y + fz * fwd)).nor();

        for (int i = 0; i < count; i++) {
            float offsetDeg = count <= 1 ? 0f : -spreadDeg * 0.5f + spreadDeg * i / (count - 1f);
            Vector3 muzzle = Math.abs(offsetDeg) < 0.5f
                ? (alternateBarrel ? rightMuzzle : leftMuzzle)
                : (offsetDeg < 0f ? leftMuzzle : rightMuzzle);
            double aRad = Math.toRadians(offsetDeg);
            float cos = (float) Math.cos(aRad);
            float sin = (float) Math.sin(aRad);
            Vector3 aim = new Vector3(
                baseAim.x * cos - baseAim.z * sin,
                baseAim.y,
                baseAim.x * sin + baseAim.z * cos).nor();
            outProjectiles.add(new EnemyProjectile(
                muzzle, aim, WARDEN_BOLT_SPEED * effectiveScale, WARDEN_BOLT_DAMAGE, this));
        }
        alternateBarrel = !alternateBarrel;
    }

    private void updateDrone(float dt, GameMap map, Pathfinder pathfinder, float dx, float dz, float dist,
                             boolean hasLos, float speedScale, float damageScale, Player player,
                             List<? extends Damageable> worldBlockers, List<Door> doors) {
        if (state == State.WINDUP) {
            stateTimer -= dt;
            if (stateTimer <= 0f) {
                state = State.LUNGING;
                stateDuration = DRONE_LUNGE_TIME;
                stateTimer = stateDuration;
                lungeHitDealt = false;
            }
            return;
        }

        if (state == State.LUNGING) {
            stateTimer -= dt;
            float step = DRONE_LUNGE_SPEED * dt;
            moveSolid(map, worldBlockers, doors, player, lungeDirX * step, lungeDirZ * step, false);
            float nowDist = Vector2.dst(position.x, position.y, player.position.x, player.position.y);
            float contactDist = Player.RADIUS + kind.radius + 0.12f;
            if (!lungeHitDealt && nowDist <= contactDist) {
                player.damage(DRONE_LUNGE_DAMAGE * damageScale, position.x, position.y);
                lungeHitDealt = true;
                keepOutsidePlayer(map, worldBlockers, doors, player);
                enterRecovering(speedScale);
                return;
            }
            if (stateTimer <= 0f) {
                keepOutsidePlayer(map, worldBlockers, doors, player);
                enterRecovering(speedScale);
            }
            return;
        }

        if (state == State.RECOVERING) {
            stateTimer -= dt;
            if (stateTimer <= 0f) {
                state = State.PURSUING;
                cooldown = 0.25f;
            }
            return;
        }

        if (hasLos && dist <= DRONE_TRIGGER_RANGE && cooldown <= 0f) {
            if (dist > 1e-4f) {
                lungeDirX = dx / dist;
                lungeDirZ = dz / dist;
            } else {
                double rad = Math.toRadians(yaw);
                lungeDirX = (float) Math.sin(rad);
                lungeDirZ = (float) -Math.cos(rad);
            }
            yaw = yawToward(lungeDirX, lungeDirZ);
            state = State.WINDUP;
            stateDuration = DRONE_WINDUP_TIME / speedScale;
            stateTimer = stateDuration;
            return;
        }

        float moveX = 0f;
        float moveZ = 0f;
        if (hasLos && dist > 1e-4f) {
            moveX = dx / dist;
            moveZ = dz / dist;
        } else if (pathfinder != null && pathfinder.nextDirection(map, doors, position.x, position.y, steerDir)) {
            moveX = steerDir.x;
            moveZ = steerDir.y;
        }
        if (Math.hypot(moveX, moveZ) > 1e-4f) {
            yaw = turnToward(yaw, yawToward(moveX, moveZ), 540f * dt);
            float step = kind.moveSpeed * dt;
            moveSolid(map, worldBlockers, doors, player, moveX * step, moveZ * step, true);
        }
    }

    private void enterRecovering(float speedScale) {
        state = State.RECOVERING;
        stateDuration = DRONE_RECOVER_TIME / speedScale;
        stateTimer = stateDuration;
    }

    private void moveSolid(GameMap map, List<? extends Damageable> worldBlockers, List<Door> doors, Player player,
                           float moveX, float moveZ, boolean separateFromPlayer) {
        float beforeX = position.x;
        float beforeZ = position.y;
        List<Damageable> blockers = blockersExcludingSelf(worldBlockers);
        Collision.move(map, blockers, doors, position, moveX, moveZ, kind.radius);
        if (separateFromPlayer && player != null && !player.isDead()) {
            keepOutsidePlayer(map, blockers, doors, player);
        }
        if (Collision.overlapsWall(map, doors, position.x, position.y, kind.radius)) {
            position.set(beforeX, beforeZ);
        }
    }

    private List<Damageable> blockersExcludingSelf(List<? extends Damageable> worldBlockers) {
        otherBlockers.clear();
        if (worldBlockers != null) {
            for (int i = 0; i < worldBlockers.size(); i++) {
                Damageable d = worldBlockers.get(i);
                if (d != this && !d.isDestroyed()) {
                    otherBlockers.add(d);
                }
            }
        }
        return otherBlockers;
    }

    private void keepOutsidePlayer(GameMap map, List<? extends Damageable> worldBlockers, List<Door> doors,
                                   Player player) {
        List<? extends Damageable> blockers = worldBlockers == otherBlockers
            ? worldBlockers
            : blockersExcludingSelf(worldBlockers);
        float minDist = Player.RADIUS + kind.radius;
        float dx = position.x - player.position.x;
        float dz = position.y - player.position.y;
        float dist = (float) Math.hypot(dx, dz);
        if (dist >= minDist) return;
        float beforeX = position.x;
        float beforeZ = position.y;
        if (dist < 1e-4f) {
            double rad = Math.toRadians(yaw);
            dx = (float) -Math.sin(rad);
            dz = (float) Math.cos(rad);
            dist = 1f;
        }
        float pushX = player.position.x + dx / dist * minDist;
        float pushZ = player.position.y + dz / dist * minDist;
        if (!Collision.overlapsWall(map, doors, pushX, pushZ, kind.radius)) {
            position.set(pushX, pushZ);
            if (Collision.overlapsSolid(blockers, position, kind.radius)) {
                position.set(beforeX, beforeZ);
            }
        }
    }

    private static float yawToward(float dx, float dz) {
        return normalizeYaw((float) Math.toDegrees(Math.atan2(dx, -dz)));
    }

    private static float turnToward(float current, float target, float maxStep) {
        float diff = angleDiff(current, target);
        if (Math.abs(diff) <= maxStep) return target;
        return normalizeYaw(current + Math.copySign(maxStep, diff));
    }

    private static float moveToward(float current, float target, float maxStep) {
        float diff = target - current;
        if (Math.abs(diff) <= maxStep) return target;
        return current + Math.copySign(maxStep, diff);
    }

    private static float angleDiff(float from, float to) {
        return ((to - from) % 360f + 540f) % 360f - 180f;
    }

    private static float normalizeYaw(float yaw) {
        return ((yaw % 360f) + 360f) % 360f;
    }
}

package io.github.brantunger.gamex.world;

import com.badlogic.gdx.math.Vector2;
import com.badlogic.gdx.math.Vector3;
import io.github.brantunger.gamex.player.DifficultyTuning;
import io.github.brantunger.gamex.player.Player;
import io.github.brantunger.gamex.weapon.EnemyProjectile;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

/**
 * A hostile combatant occupying an upright floor cylinder and implementing {@link Damageable}. Supports four
 * archetypes, three robots ({@link Kind#DRONE}, {@link Kind#TURRET}, {@link Kind#GUARD}) and a creature
 * ({@link Kind#WARDEN}), with cone/proximity/noise wake-up, bounded aggro memory, grid BFS navigation, telegraphed
 * attacks and difficulty-scaled cadence.
 */
public final class Enemy implements Damageable {

    /**
     * Enemy archetypes with distinct tactical roles. A kind's health, speed and the rest of its balance are in
     * {@link EnemyTuning.KindStats}, read from {@code assets/data/enemies.txt}. Its radius and height stay here:
     * they are its collision cylinder, which the {@code .obj} models are sculpted to, so changing one changes what
     * can be hit and not what is drawn. The Warden is the exception: its {@code .glb} model is drawn scaled to its
     * height ({@code WardenModel.SCALE}).
     */
    public enum Kind {
        /** Fast close-range hover unit that rushes around cover and performs a telegraphed shock lunge. */
        DRONE(0.21f, 0.58f),
        /** Anchored dual-barrel turret with a capped swivel rate that fires telegraphed bursts of bolts. */
        TURRET(0.23f, 0.68f),
        /** Mobile armoured skirmisher that strafes at medium range and fires single aimed plasma bolts. */
        GUARD(0.24f, 0.82f),
        /**
         * Two-phase melee boss. It walks while it cannot see the player, runs at them once it can, and throws a
         * combo of punches in reach.
         */
        WARDEN(0.32f, 0.94f);

        public final float radius;
        public final float height;

        Kind(float radius, float height) {
            this.radius = radius;
            this.height = height;
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

    /** The longest piece a lunge is moved in at once. */
    private static final float WARDEN_LUNGE_MAX_STEP = 0.05f;
    private static final float MUZZLE_WALL_MARGIN = 0.015f;
    private static final float PROJECTILE_FIRST_STEP = 0.06f;
    /** Extra clearance a straight chase needs on each side of the body, so a clear corridor is a walkable one. */
    private static final float CORRIDOR_MARGIN = 0.02f;
    /** How far beyond contact a solid object ahead still counts as having stopped a step. */
    private static final float SIDESTEP_PROBE = 0.05f;
    /** How far beyond contact the body must get from an object it could not pass before it tries again. */
    private static final float SIDESTEP_RETRY_DISTANCE = 1f;
    /** A step that covers less than this share of its length counts as stalled. */
    private static final float STALL_RATIO = 0.25f;

    private final Kind kind;
    private final EnemyTuning tuning;
    private final EnemyTuning.KindStats stats;
    private final WardenTuning warden;
    private final Vector2 position = new Vector2();
    private final Vector2 steerDir = new Vector2();
    private final Vector2 gaveUpDir = new Vector2();
    private final Vector2 probePos = new Vector2();
    private final Vector2 simPos = new Vector2();
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
    private boolean overdriveTriggered;
    /** How far into its combo the Warden is, in seconds of the clip. */
    private float comboTime;
    private int punchesThrown;
    private int punchesHeard;
    private int punchesLanded;
    private boolean chasing;
    private float unseenTime;
    private float groundSpeed;
    private float strafeSign = 1f;
    private float strafeTimer;
    private float sidestepSign = 1f;
    private Damageable obstacle;
    private float obstacleSide;
    private int sidestepFlips;
    private boolean sidestepping;
    private boolean sidestepGaveUp;
    private float walkPhase;
    private float aimPitch;

    /**
     * Spawns an enemy at {@code (x, z)} facing the longest unobstructed cardinal sightline.
     * @param tuning the enemy balance, as loaded at start-up
     */
    public Enemy(EnemyTuning tuning, Kind kind, GameMap map, float x, float z) {
        this(tuning, kind, x, z, initialYaw(map, x, z));
    }

    /**
     * Spawns an enemy at {@code (x, z)} with an explicit initial facing yaw in degrees.
     * @param tuning the enemy balance, as loaded at start-up
     */
    public Enemy(EnemyTuning tuning, Kind kind, float x, float z, float yaw) {
        if (tuning == null) throw new IllegalArgumentException("Enemy tuning must not be null");
        this.kind = kind;
        this.tuning = tuning;
        this.stats = tuning.of(kind);
        this.warden = tuning.warden;
        this.position.set(x, z);
        this.yaw = normalizeYaw(yaw);
        this.health = stats.maxHealth;
        this.unseenTime = warden.chaseMemory;
        this.strafeTimer = tuning.guardStrafeSwitchTime;
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

    /** @return the balance of this enemy's kind: its health, speed and the rules that apply to it. */
    public EnemyTuning.KindStats getStats() {
        return stats;
    }

    /** @return the health this enemy started with. */
    public float getMaxHealth() {
        return stats.maxHealth;
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
        return hitFlash / tuning.hitFlashTime;
    }

    public float getWalkPhase() {
        return walkPhase;
    }

    /** @return vertical barrel aim pitch in degrees (positive pitches slightly upward). */
    public float getAimPitch() {
        return aimPitch;
    }

    /**
     * @return wind-up intensity in [0, 1] during attack telegraphing or burst fire, otherwise 0. For the Warden it
     *     builds up over the {@link WardenTuning#telegraphTime} before each punch of its combo lands.
     */
    public float getWindupProgress() {
        if (state != State.WINDUP) return 0f;
        if (kind == Kind.WARDEN) {
            if (punchesThrown >= warden.punchCount()) return 0f;
            float left = warden.punchTime(punchesThrown) - comboTime;
            return Math.max(0f, Math.min(1f, 1f - left / warden.telegraphTime));
        }
        if (burstRemaining > 0) return 1f;
        if (stateDuration <= 0f) return 0f;
        return Math.max(0f, Math.min(1f, 1f - stateTimer / stateDuration));
    }

    /**
     * @return whether this enemy is a live Warden in overdrive, its second phase, at or below
     *     {@link WardenTuning#overdriveHealthRatio} of its health
     */
    public boolean isOverdrive() {
        return kind == Kind.WARDEN && !isDestroyed() && health <= stats.maxHealth * warden.overdriveHealthRatio;
    }

    /** @return true once when a Warden's health falls into overdrive. */
    public boolean consumeOverdriveTriggered() {
        boolean triggered = overdriveTriggered;
        overdriveTriggered = false;
        return triggered;
    }

    /** @return how many punches the Warden has thrown since this was last asked; each is asked about once. */
    public int consumePunchesThrown() {
        int thrown = punchesThrown - punchesHeard;
        punchesHeard = punchesThrown;
        return thrown;
    }

    /** @return how many punches of the Warden's current or last combo hit the player and hurt them. */
    public int getPunchesLanded() {
        return punchesLanded;
    }

    /** @return whether this is a Warden running at a player it can see, rather than walking after one it cannot. */
    public boolean isChasing() {
        return kind == Kind.WARDEN && state == State.PURSUING && chasing;
    }

    /**
     * @return the speed the Warden set out to move at in its last step, in units per second: its walk or its run
     *     while it had somewhere to go, and 0 while it stood, punched or recovered. Other enemies report 0.
     */
    public float getGroundSpeed() {
        return groundSpeed;
    }

    /**
     * @return how far into the {@code Punch_Combo} clip the Warden is, in seconds: 0 before its first combo, rising
     *     through a combo, and {@link WardenTuning#comboTime} from then on, through the recovery and the chase that
     *     follows, until its next combo starts
     */
    public float getComboTime() {
        return comboTime;
    }

    /**
     * Wakes the enemy into {@link State#PURSUING} (or refreshes its aggro memory if already alert).
     */
    public void alert() {
        if (isDestroyed()) return;
        aggroTimer = tuning.aggroMemoryTime;
        if (state == State.DORMANT) {
            state = State.PURSUING;
            cooldown = Math.max(cooldown, tuning.wakeCooldown);
        }
    }

    @Override
    public void damage(float amount) {
        if (!(amount > 0f) || Float.isNaN(amount) || isDestroyed()) return;
        boolean wasOverdrive = isOverdrive();
        health = Math.max(0f, health - amount);
        hitFlash = tuning.hitFlashTime;
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
    public void update(float dt, GameMap map, Pathfinder pathfinder, Player player,
                       DifficultyTuning.Scales difficulty, List<? extends Damageable> worldBlockers,
                       List<EnemyProjectile> outProjectiles) {
        update(dt, map, pathfinder, player, difficulty, worldBlockers,
            Collections.<Door>emptyList(), outProjectiles);
    }

    /**
     * Advances enemy perception, pathing, attack state machine and projectile firing for one step, respecting
     * bulkhead doors for sightlines, BFS routing, and movement collision.
     * @param difficulty the multipliers of the difficulty played; {@code null} scales nothing
     */
    public void update(float dt, GameMap map, Pathfinder pathfinder, Player player,
                       DifficultyTuning.Scales difficulty, List<? extends Damageable> worldBlockers,
                       List<Door> doors, List<EnemyProjectile> outProjectiles) {
        if (!Float.isFinite(dt) || dt <= 0f) return;
        hitFlash = Math.max(0f, hitFlash - dt);
        groundSpeed = 0f;
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
            if (hasLos && dist <= tuning.sightRange * tuning.aggroSightScale) {
                aggroTimer = tuning.aggroMemoryTime;
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
                updateTurret(dt, map, doors, dx, dz, dist, hasLos, speedScale, player, outProjectiles);
                break;
            case GUARD:
                updateGuard(dt, map, pathfinder, dx, dz, dist, hasLos, speedScale, player, worldBlockers, doors,
                    outProjectiles);
                break;
            case WARDEN:
                updateWarden(dt, map, pathfinder, dx, dz, dist, hasLos, speedScale, damageScale, player,
                    worldBlockers, doors);
                break;
            case DRONE:
            default:
                updateDrone(dt, map, pathfinder, dx, dz, dist, hasLos, speedScale, damageScale, player,
                    worldBlockers, doors);
                break;
        }
    }

    private boolean canDetect(float dx, float dz, float dist, boolean hasLos) {
        if (!hasLos || dist > tuning.sightRange) return false;
        if (dist <= tuning.closeSenseRange || dist < 1e-4f) return true;
        double yawRad = Math.toRadians(yaw);
        float fx = (float) Math.sin(yawRad);
        float fz = (float) -Math.cos(yawRad);
        float dot = (dx * fx + dz * fz) / dist;
        return dot >= tuning.fovCos;
    }

    private void updateTurret(float dt, GameMap map, List<Door> doors, float dx, float dz, float dist, boolean hasLos,
                              float speedScale, Player player, List<EnemyProjectile> outProjectiles) {
        if (hasLos && dist > 1e-4f) {
            float targetYaw = yawToward(dx, dz);
            yaw = turnToward(yaw, targetYaw, tuning.turretTurnRate * dt);
            float targetPitch = (float) Math.toDegrees(
                Math.atan2(player.getAimTargetY() - WorldModels.TURRET_MUZZLE_Y, Math.max(0.1f, dist)));
            targetPitch = Math.max(-tuning.turretMaxPitch, Math.min(tuning.turretMaxPitch, targetPitch));
            aimPitch = moveToward(aimPitch, targetPitch, tuning.turretPitchRate * dt);
        } else if (state != State.WINDUP || burstRemaining == 0) {
            aimPitch = moveToward(aimPitch, 0f, tuning.turretPitchReturnRate * dt);
        }

        if (state == State.WINDUP) {
            if (burstRemaining == 0) {
                if (!hasLos) {
                    state = State.PURSUING;
                    stateTimer = 0f;
                    cooldown = tuning.turretLostSightCooldown;
                    return;
                }
                stateTimer -= dt;
                if (stateTimer <= 0f) {
                    burstRemaining = tuning.turretBurstCount;
                    fireTurretBolt(map, doors, speedScale, outProjectiles);
                    burstRemaining--;
                    burstTimer = tuning.turretBurstInterval / speedScale;
                }
            } else {
                burstTimer -= dt;
                while (burstTimer <= 0f && burstRemaining > 0) {
                    fireTurretBolt(map, doors, speedScale, outProjectiles);
                    burstRemaining--;
                    if (burstRemaining > 0) {
                        burstTimer += tuning.turretBurstInterval / speedScale;
                    }
                }
                if (burstRemaining == 0) {
                    state = State.PURSUING;
                    cooldown = tuning.turretCooldown / speedScale;
                }
            }
            return;
        }

        boolean aligned = dist <= 1e-4f || Math.abs(angleDiff(yaw, yawToward(dx, dz))) <= tuning.turretFireArc;
        if (hasLos && dist <= tuning.sightRange && cooldown <= 0f && aligned) {
            state = State.WINDUP;
            stateDuration = tuning.turretWindupTime / speedScale;
            stateTimer = stateDuration;
            burstRemaining = 0;
        }
    }

    private void fireTurretBolt(GameMap map, List<Door> doors, float speedScale,
                                List<EnemyProjectile> outProjectiles) {
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
        pullMuzzleClearOfWalls(map, doors, muzzle);
        float clampedPitch = Math.max(-tuning.turretMaxPitch, Math.min(tuning.turretMaxPitch, aimPitch));
        double pitchRad = Math.toRadians(clampedPitch);
        float cosPitch = (float) Math.cos(pitchRad);
        Vector3 aim = new Vector3(fx * cosPitch, (float) Math.sin(pitchRad), fz * cosPitch);
        ensureFirstStepClearOfWalls(map, doors, muzzle, aim);
        outProjectiles.add(new EnemyProjectile(muzzle, aim, tuning.turretBoltSpeed * speedScale,
            tuning.turretBoltDamage, this));
    }

    private void updateGuard(float dt, GameMap map, Pathfinder pathfinder, float dx, float dz, float dist,
                             boolean hasLos, float speedScale, Player player,
                             List<? extends Damageable> worldBlockers, List<Door> doors,
                             List<EnemyProjectile> outProjectiles) {
        strafeTimer -= dt;
        if (strafeTimer <= 0f) {
            strafeSign = -strafeSign;
            strafeTimer = tuning.guardStrafeSwitchTime;
        }

        float moveScale = state == State.WINDUP ? tuning.guardWindupMoveScale : 1f;
        float moveX = 0f;
        float moveZ = 0f;
        if (hasLos && dist > 1e-4f) {
            float dirX = dx / dist;
            float dirZ = dz / dist;
            float rightX = -dirZ;
            float rightZ = dirX;
            if (dist > tuning.guardMaxRange) {
                moveX = dirX + rightX * strafeSign * tuning.guardAdvanceStrafe;
                moveZ = dirZ + rightZ * strafeSign * tuning.guardAdvanceStrafe;
            } else if (dist < tuning.guardMinRange) {
                moveX = -dirX * tuning.guardRetreatBack + rightX * strafeSign * tuning.guardRetreatStrafe;
                moveZ = -dirZ * tuning.guardRetreatBack + rightZ * strafeSign * tuning.guardRetreatStrafe;
            } else {
                float rangeAdjust = (dist - tuning.guardPreferredRange) * tuning.guardRangeAdjustGain;
                moveX = rightX * strafeSign + dirX * rangeAdjust;
                moveZ = rightZ * strafeSign + dirZ * rangeAdjust;
            }
            yaw = turnToward(yaw, yawToward(dx, dz), tuning.guardTurnRate * dt);
        } else if (pathfinder != null
            && pathfinder.nextDirection(map, doors, stats.avoidsHazards, position.x, position.y, steerDir)) {
            moveX = steerDir.x;
            moveZ = steerDir.y;
            yaw = turnToward(yaw, yawToward(moveX, moveZ), tuning.guardPathTurnRate * dt);
        }

        float moveLen = (float) Math.hypot(moveX, moveZ);
        if (moveLen > 1e-4f) {
            float dirX = moveX / moveLen;
            float dirZ = moveZ / moveLen;
            float step = stats.moveSpeed * moveScale * dt;
            boolean currentlyOnHazard = map != null && map.isHazardAt(position.x, position.y);
            boolean nextOnHazard = stats.avoidsHazards && !currentlyOnHazard
                && probesHazardAhead(map, position.x, position.y, dirX, dirZ, step);
            if (hasLos && nextOnHazard) {
                strafeSign = -strafeSign;
                strafeTimer = tuning.guardStrafeSwitchTime;
                step = 0f;
            }
            if (step > 0f) {
                float beforeX = position.x;
                float beforeZ = position.y;
                boolean revertedHazard = false;
                if (hasLos) {
                    moveSolid(map, worldBlockers, doors, player, dirX * step, dirZ * step, true);
                    if (stats.avoidsHazards && !currentlyOnHazard && map != null
                        && map.isHazardAt(position.x, position.y)) {
                        position.set(beforeX, beforeZ);
                        revertedHazard = true;
                    }
                } else {
                    stepRoundSolids(map, worldBlockers, doors, player, dirX, dirZ, step);
                }
                float moved = Vector2.dst(beforeX, beforeZ, position.x, position.y);
                walkPhase += moved * tuning.guardWalkPhaseRate;
                if (revertedHazard || moved < step * STALL_RATIO) {
                    strafeSign = -strafeSign;
                    strafeTimer = tuning.guardStrafeSwitchTime;
                }
            }
        }

        if (state == State.WINDUP) {
            stateTimer -= dt;
            if (stateTimer <= 0f) {
                if (hasLos) {
                    fireGuardBolt(map, doors, speedScale, player, outProjectiles);
                }
                state = State.PURSUING;
                cooldown = tuning.guardCooldown / speedScale;
            }
            return;
        }

        float nowDx = player.position.x - position.x;
        float nowDz = player.position.y - position.y;
        float nowDist = (float) Math.hypot(nowDx, nowDz);
        boolean facingPlayer = nowDist <= 1e-4f
            || Math.abs(angleDiff(yaw, yawToward(nowDx, nowDz))) <= tuning.guardFireArc;
        if (hasLos && nowDist <= tuning.guardAttackRange && cooldown <= 0f && facingPlayer) {
            state = State.WINDUP;
            stateDuration = tuning.guardWindupTime / speedScale;
            stateTimer = stateDuration;
        }
    }

    private void fireGuardBolt(GameMap map, List<Door> doors, float speedScale, Player player,
                               List<EnemyProjectile> outProjectiles) {
        if (outProjectiles == null) return;
        double rad = Math.toRadians(yaw);
        float fx = (float) Math.sin(rad);
        float fz = (float) -Math.cos(rad);
        float rx = (float) Math.cos(rad);
        float rz = (float) Math.sin(rad);
        float fwd = -WorldModels.GUARD_MUZZLE_Z;
        Vector3 muzzle = new Vector3(position.x + fx * fwd + rx * WorldModels.GUARD_MUZZLE_X,
            WorldModels.GUARD_MUZZLE_Y, position.y + fz * fwd + rz * WorldModels.GUARD_MUZZLE_X);
        pullMuzzleClearOfWalls(map, doors, muzzle);
        Vector3 aim = clampedAimVector(muzzle.x, muzzle.y, muzzle.z,
            player.position.x, player.getAimTargetY(), player.position.y,
            tuning.guardFireArc, tuning.guardBoltMaxPitch);
        if (ensureFirstStepClearOfWalls(map, doors, muzzle, aim)) {
            aim = clampedAimVector(muzzle.x, muzzle.y, muzzle.z,
                player.position.x, player.getAimTargetY(), player.position.y,
                tuning.guardFireArc, tuning.guardBoltMaxPitch);
        }
        outProjectiles.add(new EnemyProjectile(muzzle, aim, tuning.guardBoltSpeed * speedScale,
            tuning.guardBoltDamage, this));
    }

    private void updateWarden(float dt, GameMap map, Pathfinder pathfinder, float dx, float dz, float dist,
                              boolean hasLos, float speedScale, float damageScale, Player player,
                              List<? extends Damageable> worldBlockers, List<Door> doors) {
        boolean overdrive = isOverdrive();
        float turnScale = overdrive ? warden.overdriveTurnScale : 1f;

        if (state == State.WINDUP) {
            float rate = speedScale * (overdrive ? warden.overdriveComboRate : 1f);
            float damage = damageScale * (overdrive ? warden.overdriveDamageScale : 1f);
            stepWardenCombo(dt * rate, dt, turnScale, damage, speedScale, map, player, worldBlockers, doors);
            return;
        }

        if (state == State.RECOVERING) {
            stateTimer -= dt;
            if (stateTimer > 0f) return;
            // It chases again in this same step, so that with the player still in reach the next combo follows
            // the recovery at once, with no step of chasing in between.
            state = State.PURSUING;
        }

        // It runs at a player it can see, or saw a moment ago, and walks along the way to one it cannot.
        unseenTime = hasLos ? 0f : Math.min(warden.chaseMemory, unseenTime + dt);
        chasing = unseenTime < warden.chaseMemory;
        float speed = chasing ? warden.runSpeed * (overdrive ? warden.overdriveSpeedScale : 1f) : stats.moveSpeed;
        float moveX = 0f;
        float moveZ = 0f;
        if (hasLos && dist > 1e-4f) {
            yaw = turnToward(yaw, yawToward(dx, dz), warden.turnRate * turnScale * dt);
            if (dist > warden.attackRange) {
                steerTowardVisiblePlayer(map, pathfinder, doors, dx, dz, dist);
                moveX = steerDir.x;
                moveZ = steerDir.y;
            }
        } else if (pathfinder != null && pathfinder.nextDirection(map, doors, position.x, position.y, steerDir)) {
            moveX = steerDir.x;
            moveZ = steerDir.y;
            yaw = turnToward(yaw, yawToward(moveX, moveZ), warden.pathTurnRate * turnScale * dt);
        }

        if (Math.hypot(moveX, moveZ) > 1e-4f) {
            stepRoundSolids(map, worldBlockers, doors, player, moveX, moveZ, speed * dt);
            groundSpeed = speed;
        }

        if (hasLos && cooldown <= 0f && dist <= warden.attackRange
            && (dist < 1e-4f || Math.abs(angleDiff(yaw, yawToward(dx, dz))) <= warden.attackFacing)) {
            state = State.WINDUP;
            comboTime = 0f;
            punchesThrown = 0;
            punchesHeard = 0;
            punchesLanded = 0;
        }
    }

    /**
     * Advances the Warden's combo by {@code clipDt} seconds of its clip. It turns slowly toward the player, lunges
     * along its facing into each punch, and at the moment a punch lands hurts a player who is then in reach and in
     * front of it. The combo always runs to the end of the clip, hit or miss, and the Warden then recovers.
     */
    private void stepWardenCombo(float clipDt, float dt, float turnScale, float damage, float speedScale,
                                 GameMap map, Player player, List<? extends Damageable> worldBlockers,
                                 List<Door> doors) {
        float dx = player.position.x - position.x;
        float dz = player.position.y - position.y;
        if (Math.hypot(dx, dz) > 1e-4f) {
            yaw = turnToward(yaw, yawToward(dx, dz), warden.comboTurnRate * turnScale * dt);
        }

        float from = comboTime;
        comboTime = Math.min(warden.comboTime, comboTime + clipDt);
        float lunge = 0f;
        for (int i = 0; i < warden.punchCount(); i++) {
            lunge += warden.lungeDistance(i) * lungeShare(i, from, comboTime);
        }
        if (lunge > 0f) {
            double rad = Math.toRadians(yaw);
            float dirX = (float) Math.sin(rad);
            float dirZ = (float) -Math.cos(rad);
            // In short pieces, and no further than the player: a lunge that slid round them would carry the
            // Warden past and leave them behind its back for the punch.
            float contact = Player.RADIUS + kind.radius;
            for (float left = lunge; left > 0f; left -= WARDEN_LUNGE_MAX_STEP) {
                float piece = Math.min(left, WARDEN_LUNGE_MAX_STEP);
                float fromX = position.x;
                float fromZ = position.y;
                moveSolid(map, worldBlockers, doors, player, dirX * piece, dirZ * piece, false);
                if (Vector2.dst(position.x, position.y, player.position.x, player.position.y) < contact) {
                    position.set(fromX, fromZ);
                    break;
                }
            }
        }

        // A long step can pass more than one punch; each still lands.
        while (punchesThrown < warden.punchCount() && comboTime >= warden.punchTime(punchesThrown)) {
            if (isInPunchReach(map, doors, player)
                && player.damage(warden.punchDamage(punchesThrown) * damage, position.x, position.y) > 0f) {
                punchesLanded++;
            }
            punchesThrown++;
        }

        if (comboTime >= warden.comboTime) {
            state = State.RECOVERING;
            float recover = warden.recoverTime * (isOverdrive() ? warden.overdriveRecoverScale : 1f);
            stateDuration = recover / speedScale;
            stateTimer = stateDuration;
        }
    }

    /** @return the share of punch {@code i}'s lunge that falls between two times of the clip */
    private float lungeShare(int i, float from, float to) {
        float end = warden.punchTime(i);
        float start = end - warden.lungeTime(i);
        float overlap = Math.min(to, end) - Math.max(from, start);
        return overlap <= 0f ? 0f : overlap / warden.lungeTime(i);
    }

    /** @return whether a punch landing now hits the player: in sight, within reach and within the frontal arc */
    private boolean isInPunchReach(GameMap map, List<Door> doors, Player player) {
        float dx = player.position.x - position.x;
        float dz = player.position.y - position.y;
        float dist = (float) Math.hypot(dx, dz);
        if (dist > warden.punchReach) return false;
        if (dist < 1e-4f) return true;
        if (Math.abs(angleDiff(yaw, yawToward(dx, dz))) > warden.punchArc) return false;
        return Hitscan.hasLineOfSight(map, doors, position.x, position.y, player.position.x, player.position.y);
    }

    private void updateDrone(float dt, GameMap map, Pathfinder pathfinder, float dx, float dz, float dist,
                             boolean hasLos, float speedScale, float damageScale, Player player,
                             List<? extends Damageable> worldBlockers, List<Door> doors) {
        if (state == State.WINDUP) {
            stateTimer -= dt;
            if (stateTimer <= 0f) {
                state = State.LUNGING;
                stateDuration = tuning.droneLungeTime;
                stateTimer = stateDuration;
                lungeHitDealt = false;
            }
            return;
        }

        if (state == State.LUNGING) {
            stateTimer -= dt;
            float step = tuning.droneLungeSpeed * dt;
            moveSolid(map, worldBlockers, doors, player, lungeDirX * step, lungeDirZ * step, false);
            float nowDist = Vector2.dst(position.x, position.y, player.position.x, player.position.y);
            float contactDist = Player.RADIUS + kind.radius + tuning.droneContactMargin;
            if (!lungeHitDealt && nowDist <= contactDist) {
                player.damage(tuning.droneLungeDamage * damageScale, position.x, position.y);
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
                cooldown = tuning.droneCooldown;
            }
            return;
        }

        if (hasLos && dist <= tuning.droneTriggerRange && cooldown <= 0f) {
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
            stateDuration = tuning.droneWindupTime / speedScale;
            stateTimer = stateDuration;
            return;
        }

        float moveX = 0f;
        float moveZ = 0f;
        if (hasLos && dist > 1e-4f) {
            steerTowardVisiblePlayer(map, pathfinder, doors, dx, dz, dist);
            moveX = steerDir.x;
            moveZ = steerDir.y;
        } else if (pathfinder != null && pathfinder.nextDirection(map, doors, position.x, position.y, steerDir)) {
            moveX = steerDir.x;
            moveZ = steerDir.y;
        }
        if (Math.hypot(moveX, moveZ) > 1e-4f) {
            yaw = turnToward(yaw, yawToward(moveX, moveZ), tuning.droneTurnRate * dt);
            float step = stats.moveSpeed * dt;
            stepRoundSolids(map, worldBlockers, doors, player, moveX, moveZ, step);
        }
    }

    private void enterRecovering(float speedScale) {
        state = State.RECOVERING;
        stateDuration = tuning.droneRecoverTime / speedScale;
        stateTimer = stateDuration;
    }

    /**
     * Sets {@code steerDir} for closing on a player in line of sight: straight at them while the whole body has a
     * clear run, otherwise along the flow field, so a sightline that grazes a wall corner does not pin the body
     * against that corner. Without a flow-field step (no pathfinder, or already in the player's cell) it is straight.
     */
    private void steerTowardVisiblePlayer(GameMap map, Pathfinder pathfinder, List<Door> doors, float dx, float dz,
                                          float dist) {
        float dirX = dx / dist;
        float dirZ = dz / dist;
        if (pathfinder != null && !hasClearRun(map, doors, dirX, dirZ, dist)
            && pathfinder.nextDirection(map, doors, position.x, position.y, steerDir)) {
            return;
        }
        steerDir.set(dirX, dirZ);
    }

    /**
     * @return whether the body can travel along the unit direction until it touches a player {@code dist} away
     *     without meeting a wall or a blocking bulkhead door
     */
    private boolean hasClearRun(GameMap map, List<Door> doors, float dirX, float dirZ, float dist) {
        float reach = dist - (Player.RADIUS + kind.radius);
        float clearance = kind.radius + CORRIDOR_MARGIN;
        // Circles one radius apart cover the swept body closely enough that no wall cell fits between them.
        for (float along = kind.radius; along < reach; along += kind.radius) {
            if (Collision.overlapsWall(map, doors, position.x + dirX * along, position.y + dirZ * along, clearance)) {
                return false;
            }
        }
        return reach <= 0f
            || !Collision.overlapsWall(map, doors, position.x + dirX * reach, position.y + dirZ * reach, clearance);
    }

    /**
     * Takes one pursuit step along the unit direction, walking round a solid object, such as a canister standing on
     * the path, that stops it. A head-on push never slides round an object, so once a step stalls against one the
     * body steps sideways instead until the object is no longer in its way. The side is kept until a sideways step
     * is itself blocked, so the body does not jitter, and with both sides blocked it rests against the object.
     */
    private void stepRoundSolids(GameMap map, List<? extends Damageable> worldBlockers, List<Door> doors,
                                 Player player, float dirX, float dirZ, float step) {
        if (sidestepping) {
            if (isInTheWay(obstacle, dirX, dirZ, SIDESTEP_RETRY_DISTANCE)) {
                sidestep(map, worldBlockers, doors, player, dirX, dirZ, step);
                return;
            }
            sidestepping = false;
        }
        float beforeX = position.x;
        float beforeZ = position.y;
        boolean avoidSlideOntoHazard = stats.avoidsHazards && map != null
            && !map.isHazardAt(beforeX, beforeZ)
            && !probesHazardAhead(map, beforeX, beforeZ, dirX, dirZ, step);
        moveSolid(map, worldBlockers, doors, player, dirX * step, dirZ * step, true);
        if (avoidSlideOntoHazard && map.isHazardAt(position.x, position.y)) {
            position.set(beforeX, beforeZ);
        }
        if (Vector2.dst(beforeX, beforeZ, position.x, position.y) >= step * STALL_RATIO) return;
        Damageable stopper = solidInTheWay(worldBlockers, dirX, dirZ);
        if (stopper == null) return;
        if (sidestepGaveUp && stopper == obstacle && dirX * gaveUpDir.x + dirZ * gaveUpDir.y > 0.7f
            && isWithin(obstacle, SIDESTEP_RETRY_DISTANCE)) {
            float clearSign = findClearSidestepSign(map, worldBlockers, doors, player, stopper, dirX, dirZ, step);
            if (clearSign == 0f) {
                return;
            }
            obstacle = stopper;
            sidestepping = true;
            sidestepGaveUp = false;
            sidestepFlips = 0;
            sidestepSign = clearSign;
            sidestep(map, worldBlockers, doors, player, dirX, dirZ, step);
            return;
        }
        obstacle = stopper;
        sidestepping = true;
        sidestepGaveUp = false;
        sidestepFlips = 0;
        // Leave by the side the body is already on; dead on the axis either side will do.
        if (Math.abs(obstacleSide) > 1e-3f) {
            sidestepSign = -Math.signum(obstacleSide);
        }
        sidestep(map, worldBlockers, doors, player, dirX, dirZ, step);
    }

    /** Moves one step square to the unit direction, turning to the other side when that step is blocked. */
    private void sidestep(GameMap map, List<? extends Damageable> worldBlockers, List<Door> doors, Player player,
                          float dirX, float dirZ, float step) {
        float sideX = -dirZ * sidestepSign;
        float sideZ = dirX * sidestepSign;
        float fromX = position.x;
        float fromZ = position.y;
        boolean currentlyOnHazard = stats.avoidsHazards && map != null && map.isHazardAt(fromX, fromZ);
        // A grounded guard does not sidestep onto a shock grate.
        boolean ontoHazard = stats.avoidsHazards && map != null && !currentlyOnHazard
            && probesHazardAhead(map, fromX, fromZ, sideX, sideZ, step);
        if (!ontoHazard) {
            moveSolid(map, worldBlockers, doors, player, sideX * step, sideZ * step, true);
            if (stats.avoidsHazards && map != null && !currentlyOnHazard && map.isHazardAt(position.x, position.y)) {
                position.set(fromX, fromZ);
            }
        }
        if (Vector2.dst(fromX, fromZ, position.x, position.y) >= step * STALL_RATIO) return;
        sidestepSign = -sidestepSign;
        sidestepFlips++;
        if (sidestepFlips >= 2) {
            sidestepping = false;
            sidestepGaveUp = true;
            gaveUpDir.set(dirX, dirZ);
        }
    }

    /**
     * Checks non-mutatingly whether either perpendicular sidestep direction around {@code stopper} has opened up and
     * can clear the obstacle without stalling, returning {@code +1f}, {@code -1f}, or {@code 0f} when still plugged.
     */
    private float findClearSidestepSign(GameMap map, List<? extends Damageable> worldBlockers, List<Door> doors,
                                        Player player, Damageable stopper, float dirX, float dirZ, float step) {
        List<Damageable> blockers = worldBlockers == otherBlockers
            ? otherBlockers
            : blockersExcludingSelf(worldBlockers);
        float firstSign = Math.abs(obstacleSide) > 1e-3f
            ? -Math.signum(obstacleSide)
            : (sidestepSign >= 0f ? 1f : -1f);
        if (canSidestepPastObstacle(map, blockers, doors, player, stopper, dirX, dirZ, step, firstSign)) {
            return firstSign;
        }
        if (canSidestepPastObstacle(map, blockers, doors, player, stopper, dirX, dirZ, step, -firstSign)) {
            return -firstSign;
        }
        return 0f;
    }

    private boolean canSidestepPastObstacle(GameMap map, List<Damageable> blockers, List<Door> doors, Player player,
                                            Damageable stopper, float dirX, float dirZ, float step, float sign) {
        if (step <= 0f) return false;
        simPos.set(position.x, position.y);
        boolean trackPlayer = false;
        if (player != null && !player.isDead()) {
            float pdx = player.position.x - position.x;
            float pdz = player.position.y - position.y;
            float pdist = (float) Math.hypot(pdx, pdz);
            trackPlayer = pdist > 1e-4f && (pdx * dirX + pdz * dirZ) / pdist > 0.98f;
        }
        float curDirX = dirX;
        float curDirZ = dirZ;
        float simStep = Math.max(step, kind.radius * 0.5f);
        for (int k = 0; k < 32; k++) {
            if (k > 0 && trackPlayer) {
                float pdx = player.position.x - simPos.x;
                float pdz = player.position.y - simPos.y;
                float pdist = (float) Math.hypot(pdx, pdz);
                if (pdist <= Player.RADIUS + kind.radius + 0.05f) {
                    return true;
                }
                curDirX = pdx / pdist;
                curDirZ = pdz / pdist;
            }
            if (k > 0 && !isInTheWayAt(simPos.x, simPos.y, stopper, curDirX, curDirZ, SIDESTEP_RETRY_DISTANCE)) {
                return canAdvancePastStopper(map, blockers, doors, player, stopper, curDirX, curDirZ,
                    trackPlayer, simStep);
            }
            float stepLen = k == 0 ? step : simStep;
            float sideX = -curDirZ * sign;
            float sideZ = curDirX * sign;
            float fromX = simPos.x;
            float fromZ = simPos.y;
            boolean fromOnHazard = stats.avoidsHazards && map != null && map.isHazardAt(fromX, fromZ);
            if (stats.avoidsHazards && map != null && !fromOnHazard
                && probesHazardAhead(map, fromX, fromZ, sideX, sideZ, stepLen)) {
                return false;
            }
            if (map != null) {
                Collision.move(map, blockers, doors, simPos, sideX * stepLen, sideZ * stepLen, kind.radius);
            } else {
                simPos.add(sideX * stepLen, sideZ * stepLen);
                if (Collision.overlapsSolid(blockers, simPos, kind.radius)) {
                    simPos.set(fromX, fromZ);
                }
            }
            if (player != null && !player.isDead()) {
                float minDist = Player.RADIUS + kind.radius;
                if (Vector2.dst(simPos.x, simPos.y, player.position.x, player.position.y) < minDist) {
                    if (k > 0) return true;
                    resolveOutsidePlayer(map, blockers, doors, player, simPos, false);
                }
            }
            if (stats.avoidsHazards && map != null && !fromOnHazard && map.isHazardAt(simPos.x, simPos.y)) {
                return false;
            }
            if (map != null && Collision.overlapsWall(map, doors, simPos.x, simPos.y, kind.radius)) {
                return false;
            }
            float moved = Vector2.dst(fromX, fromZ, simPos.x, simPos.y);
            if (k == 0 && moved < stepLen * STALL_RATIO) {
                return false;
            }
            if (!isInTheWayAt(simPos.x, simPos.y, stopper, curDirX, curDirZ, SIDESTEP_RETRY_DISTANCE)) {
                return canAdvancePastStopper(map, blockers, doors, player, stopper, curDirX, curDirZ,
                    trackPlayer, simStep);
            }
            if (moved < stepLen * STALL_RATIO) {
                return false;
            }
        }
        return false;
    }

    private boolean canAdvancePastStopper(GameMap map, List<Damageable> blockers, List<Door> doors, Player player,
                                          Damageable stopper, float curDirX, float curDirZ, boolean trackPlayer,
                                          float simStep) {
        for (int f = 0; f < 20; f++) {
            if (trackPlayer && player != null && !player.isDead()) {
                float pdx = player.position.x - simPos.x;
                float pdz = player.position.y - simPos.y;
                float pdist = (float) Math.hypot(pdx, pdz);
                if (pdist <= Player.RADIUS + kind.radius + 0.05f) {
                    return true;
                }
                curDirX = pdx / pdist;
                curDirZ = pdz / pdist;
            }
            if ((stopper.getX() - simPos.x) * curDirX + (stopper.getZ() - simPos.y) * curDirZ <= 0f) {
                return true;
            }
            float fromX = simPos.x;
            float fromZ = simPos.y;
            boolean fromOnHazard = stats.avoidsHazards && map != null && map.isHazardAt(fromX, fromZ);
            if (stats.avoidsHazards && map != null && !fromOnHazard
                && probesHazardAhead(map, fromX, fromZ, curDirX, curDirZ, simStep)) {
                return false;
            }
            if (map != null) {
                Collision.move(map, blockers, doors, simPos, curDirX * simStep, curDirZ * simStep, kind.radius);
            } else {
                simPos.add(curDirX * simStep, curDirZ * simStep);
                if (Collision.overlapsSolid(blockers, simPos, kind.radius)) {
                    simPos.set(fromX, fromZ);
                }
            }
            if (stats.avoidsHazards && map != null && !fromOnHazard && map.isHazardAt(simPos.x, simPos.y)) {
                return false;
            }
            if (player != null && !player.isDead()
                && Vector2.dst(simPos.x, simPos.y, player.position.x, player.position.y) < Player.RADIUS + kind.radius) {
                return true;
            }
            if (map != null && Collision.overlapsWall(map, doors, simPos.x, simPos.y, kind.radius)) {
                return false;
            }
            if ((stopper.getX() - simPos.x) * curDirX + (stopper.getZ() - simPos.y) * curDirZ <= 0f) {
                return true;
            }
            if (Vector2.dst(fromX, fromZ, simPos.x, simPos.y) < simStep * STALL_RATIO) {
                return false;
            }
        }
        return false;
    }

    /** @return the live solid object the body touches, or all but touches, that lies across its way, or null */
    private Damageable solidInTheWay(List<? extends Damageable> worldBlockers, float dirX, float dirZ) {
        List<Damageable> blockers = blockersExcludingSelf(worldBlockers);
        for (int i = 0; i < blockers.size(); i++) {
            Damageable solid = blockers.get(i);
            if (isInTheWay(solid, dirX, dirZ, SIDESTEP_PROBE)) return solid;
        }
        return null;
    }

    /**
     * Records in {@code obstacleSide} how far to the right of the unit direction the object's centre is.
     * @return whether a live object within {@code slack} of contact lies ahead, across the body's way
     */
    private boolean isInTheWay(Damageable solid, float dirX, float dirZ, float slack) {
        if (solid.isDestroyed() || !isWithin(solid, slack)) return false;
        float relX = solid.getX() - position.x;
        float relZ = solid.getZ() - position.y;
        if (relX * dirX + relZ * dirZ <= 0f) return false;
        obstacleSide = relZ * dirX - relX * dirZ;
        return Math.abs(obstacleSide) < solid.getRadius() + kind.radius;
    }

    private boolean isInTheWayAt(float fromX, float fromZ, Damageable solid, float dirX, float dirZ, float slack) {
        if (solid == null || solid.isDestroyed()) return false;
        float near = solid.getRadius() + kind.radius + slack;
        if (Vector2.dst2(fromX, fromZ, solid.getX(), solid.getZ()) >= near * near) return false;
        float relX = solid.getX() - fromX;
        float relZ = solid.getZ() - fromZ;
        if (relX * dirX + relZ * dirZ <= 0f) return false;
        float side = relZ * dirX - relX * dirZ;
        return Math.abs(side) < solid.getRadius() + kind.radius;
    }

    /** @return whether the body is within {@code slack} of touching the object */
    private boolean isWithin(Damageable solid, float slack) {
        float near = solid.getRadius() + kind.radius + slack;
        return Vector2.dst2(position.x, position.y, solid.getX(), solid.getZ()) < near * near;
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
        if (player == null) return;
        List<? extends Damageable> blockers = worldBlockers == otherBlockers
            ? worldBlockers
            : blockersExcludingSelf(worldBlockers);
        resolveOutsidePlayer(map, blockers, doors, player, position, state == State.LUNGING);
    }

    private void resolveOutsidePlayer(GameMap map, List<? extends Damageable> blockers, List<Door> doors,
                                      Player player, Vector2 pos, boolean useLungeRetract) {
        float minDist = Player.RADIUS + kind.radius;
        float dx = pos.x - player.position.x;
        float dz = pos.y - player.position.y;
        float dist = (float) Math.hypot(dx, dz);
        if (dist >= minDist) return;

        float beforeX = pos.x;
        float beforeZ = pos.y;
        float lungeLen = useLungeRetract ? (float) Math.hypot(lungeDirX, lungeDirZ) : 0f;
        boolean hasLungeDir = lungeLen > 1e-5f;
        float retractX = hasLungeDir ? -lungeDirX / lungeLen : 0f;
        float retractZ = hasLungeDir ? -lungeDirZ / lungeLen : 0f;

        float radX;
        float radZ;
        if (dist >= 1e-4f) {
            radX = dx / dist;
            radZ = dz / dist;
        } else if (hasLungeDir) {
            radX = retractX;
            radZ = retractZ;
        } else {
            double rad = Math.toRadians(yaw);
            radX = (float) -Math.sin(rad);
            radZ = (float) Math.cos(rad);
        }

        boolean avoidHazard = stats.avoidsHazards && map != null && !map.isHazardAt(beforeX, beforeZ);
        float pushX = player.position.x + radX * minDist;
        float pushZ = player.position.y + radZ * minDist;
        if (isClearSeparationSpot(map, blockers, doors, player, pushX, pushZ, avoidHazard)) {
            pos.set(pushX, pushZ);
            return;
        }

        float targetDist = minDist + 1e-4f;
        if (hasLungeDir) {
            float relX = beforeX - player.position.x;
            float relZ = beforeZ - player.position.y;
            float b = relX * retractX + relZ * retractZ;
            float c = relX * relX + relZ * relZ - targetDist * targetDist;
            float disc = b * b - c;
            if (disc >= 0f) {
                float tExit = -b + (float) Math.sqrt(disc);
                if (tExit >= 0f) {
                    float candX = beforeX + retractX * tExit;
                    float candZ = beforeZ + retractZ * tExit;
                    if (isClearSeparationSpot(map, blockers, doors, player, candX, candZ, avoidHazard)) {
                        pos.set(candX, candZ);
                        return;
                    }
                }
            }
        }

        float baseX = hasLungeDir ? retractX : radX;
        float baseZ = hasLungeDir ? retractZ : radZ;
        float baseAngle = (float) Math.atan2(baseZ, baseX);
        float cross = baseX * radZ - baseZ * radX;
        float preferredSign = cross >= 0f ? 1f : -1f;

        for (int pass = 0; pass < (avoidHazard ? 2 : 1); pass++) {
            boolean checkHazard = avoidHazard && pass == 0;
            for (int ring = 0; ring < 5; ring++) {
                float ringDist = targetDist + ring * 0.15f;
                if (hasLungeDir || ring > 0) {
                    float candX = player.position.x + baseX * ringDist;
                    float candZ = player.position.y + baseZ * ringDist;
                    if (isClearSeparationSpot(map, blockers, doors, player, candX, candZ, checkHazard)) {
                        pos.set(candX, candZ);
                        return;
                    }
                }
                for (int deg = 5; deg <= 180; deg += 5) {
                    double deltaRad = Math.toRadians(deg);
                    for (int s = 0; s < 2; s++) {
                        if (deg == 180 && s == 1) break;
                        double angle = baseAngle + (s == 0 ? preferredSign : -preferredSign) * deltaRad;
                        float candX = player.position.x + (float) Math.cos(angle) * ringDist;
                        float candZ = player.position.y + (float) Math.sin(angle) * ringDist;
                        if (isClearSeparationSpot(map, blockers, doors, player, candX, candZ, checkHazard)) {
                            pos.set(candX, candZ);
                            return;
                        }
                    }
                }
            }
        }

        pos.set(beforeX, beforeZ);
    }

    private boolean isClearSeparationSpot(GameMap map, List<? extends Damageable> blockers, List<Door> doors,
                                          Player player, float x, float z, boolean avoidHazard) {
        if (map != null) {
            if (avoidHazard && map.isHazardAt(x, z)) return false;
            if (Collision.overlapsWall(map, doors, x, z, kind.radius)) return false;
            if (!Hitscan.hasLineOfSight(map, doors, player.position.x, player.position.y, x, z)) return false;
        }
        probePos.set(x, z);
        return !Collision.overlapsSolid(blockers, probePos, kind.radius);
    }

    private boolean probesHazardAhead(GameMap map, float fromX, float fromZ, float dirX, float dirZ, float step) {
        if (map == null) return false;
        float ahead = kind.radius + 0.25f;
        return map.isHazardAt(fromX + dirX * (0.5f * step), fromZ + dirZ * (0.5f * step))
            || map.isHazardAt(fromX + dirX * step, fromZ + dirZ * step)
            || map.isHazardAt(fromX + dirX * (0.25f * ahead), fromZ + dirZ * (0.25f * ahead))
            || map.isHazardAt(fromX + dirX * (0.5f * ahead), fromZ + dirZ * (0.5f * ahead))
            || map.isHazardAt(fromX + dirX * (0.75f * ahead), fromZ + dirZ * (0.75f * ahead))
            || map.isHazardAt(fromX + dirX * ahead, fromZ + dirZ * ahead);
    }

    private Vector3 clampedAimVector(float originX, float originY, float originZ,
                                     float targetX, float targetY, float targetZ,
                                     float maxYawOffsetDeg, float maxPitchDeg) {
        float rawDx = targetX - originX;
        float rawDy = targetY - originY;
        float rawDz = targetZ - originZ;
        float horizDist = (float) Math.hypot(rawDx, rawDz);
        float aimYawDeg;
        if (horizDist > 1e-4f) {
            float diff = angleDiff(yaw, yawToward(rawDx, rawDz));
            float clampedDiff = Math.max(-maxYawOffsetDeg, Math.min(maxYawOffsetDeg, diff));
            aimYawDeg = yaw + clampedDiff;
        } else {
            aimYawDeg = yaw;
        }
        float pitchDeg = (float) Math.toDegrees(Math.atan2(rawDy, Math.max(1e-4f, horizDist)));
        pitchDeg = Math.max(-maxPitchDeg, Math.min(maxPitchDeg, pitchDeg));
        double yawRad = Math.toRadians(aimYawDeg);
        double pitchRad = Math.toRadians(pitchDeg);
        float cosPitch = (float) Math.cos(pitchRad);
        return new Vector3(
            (float) Math.sin(yawRad) * cosPitch,
            (float) Math.sin(pitchRad),
            (float) -Math.cos(yawRad) * cosPitch);
    }

    private void pullMuzzleClearOfWalls(GameMap map, List<Door> doors, Vector3 muzzle) {
        float clearance = EnemyProjectile.RADIUS + MUZZLE_WALL_MARGIN;
        if (!isMuzzleBlocked(map, doors, muzzle.x, muzzle.y, muzzle.z, clearance)) {
            return;
        }
        float startX = muzzle.x;
        float startZ = muzzle.z;
        for (int s = 1; s <= 8; s++) {
            float t = s / 8f;
            float candX = startX + (position.x - startX) * t;
            float candZ = startZ + (position.y - startZ) * t;
            if (!isMuzzleBlocked(map, doors, candX, muzzle.y, candZ, clearance)
                && (map == null || Hitscan.hasLineOfSight(map, doors, position.x, position.y, candX, candZ))) {
                muzzle.x = candX;
                muzzle.z = candZ;
                return;
            }
        }
        muzzle.x = position.x;
        muzzle.z = position.y;
    }

    private boolean ensureFirstStepClearOfWalls(GameMap map, List<Door> doors, Vector3 muzzle, Vector3 aim) {
        if (!isMuzzleBlocked(map, doors,
            muzzle.x + aim.x * PROJECTILE_FIRST_STEP,
            muzzle.y + aim.y * PROJECTILE_FIRST_STEP,
            muzzle.z + aim.z * PROJECTILE_FIRST_STEP,
            EnemyProjectile.RADIUS)) {
            return false;
        }
        if (!isMuzzleBlocked(map, doors,
            position.x + aim.x * PROJECTILE_FIRST_STEP,
            muzzle.y + aim.y * PROJECTILE_FIRST_STEP,
            position.y + aim.z * PROJECTILE_FIRST_STEP,
            EnemyProjectile.RADIUS)) {
            muzzle.x = position.x;
            muzzle.z = position.y;
            return true;
        }
        return false;
    }

    private static boolean isMuzzleBlocked(GameMap map, List<Door> doors, float x, float y, float z, float radius) {
        if (map != null && Collision.overlapsWall(map, doors, x, z, radius)) {
            return true;
        }
        if (doors != null) {
            for (int i = 0; i < doors.size(); i++) {
                Door door = doors.get(i);
                if (door != null && door.overlapsSphere(x, y, z, radius)) {
                    return true;
                }
            }
        }
        return false;
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
        if (!Float.isFinite(yaw)) return 0f;
        return ((yaw % 360f) + 360f) % 360f;
    }
}

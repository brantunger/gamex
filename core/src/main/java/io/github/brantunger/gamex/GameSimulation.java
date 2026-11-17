package io.github.brantunger.gamex;

import com.badlogic.gdx.math.Vector2;
import com.badlogic.gdx.math.Vector3;
import io.github.brantunger.gamex.player.Player;
import io.github.brantunger.gamex.player.PlayerInput;
import io.github.brantunger.gamex.player.PlayerLoadout;
import io.github.brantunger.gamex.weapon.BlastCharger;
import io.github.brantunger.gamex.weapon.BlastProjectile;
import io.github.brantunger.gamex.weapon.EnemyProjectile;
import io.github.brantunger.gamex.weapon.EnergyMeter;
import io.github.brantunger.gamex.weapon.Explosion;
import io.github.brantunger.gamex.weapon.Pistol;
import io.github.brantunger.gamex.weapon.ViewmodelAnimation;
import io.github.brantunger.gamex.world.Collision;
import io.github.brantunger.gamex.world.Damageable;
import io.github.brantunger.gamex.world.Effect;
import io.github.brantunger.gamex.world.Enemy;
import io.github.brantunger.gamex.world.GameMap;
import io.github.brantunger.gamex.world.Hitscan;
import io.github.brantunger.gamex.world.Pathfinder;
import io.github.brantunger.gamex.world.Pickup;
import io.github.brantunger.gamex.world.RayHit;
import io.github.brantunger.gamex.world.Target;

import java.util.ArrayList;
import java.util.List;

/**
 * The whole game state and the rules that advance it. It uses no graphics types, so it runs without a window: the
 * renderers only read it, and each frame's {@link PlayerInput} is its only input.
 */
public final class GameSimulation {

    /** The longest time step simulated at once, in seconds; a longer frame is cut down to this. */
    public static final float MAX_DT = 0.05f;
    /** Blast radius of a detonating coolant canister, in world units. */
    public static final float CANISTER_BLAST_RADIUS = 2.2f;
    /** Centre blast damage of a detonating coolant canister. */
    public static final float CANISTER_BLAST_DAMAGE = 80f;
    /** Share of canister blast damage applied to the player before difficulty scaling. */
    public static final float CANISTER_SELF_DAMAGE_SCALE = 0.5f;
    /** How long crosshair hit-markers remain visible, in seconds. */
    public static final float HIT_MARKER_TIME = 0.16f;
    /** How long a pickup toast message remains visible, in seconds. */
    public static final float TOAST_TIME = 2.0f;

    /** How far a mark sits off the surface it lies on, so it does not fight with the surface for depth. */
    private static final float MARK_OFFSET = 0.012f;
    /** World-space muzzle launch offset from eye so the blast starts in view and past the near clip plane. */
    private static final float BLAST_SPAWN_FORWARD = 0.52f;
    private static final float BLAST_SPAWN_RIGHT = 0.11f;
    private static final float BLAST_SPAWN_DOWN = 0.05f;

    /** The level. */
    public final GameMap map;
    /** Loadout and difficulty snapshot from when the player entered this level. */
    public final PlayerLoadout entryLoadout;
    /** Whether destroying every target completes the stage automatically. */
    public final boolean completeOnAllTargets;
    /** The player. */
    public final Player player;
    /** Every target of the level, destroyed ones included. */
    public final List<Target> targets = new ArrayList<>();
    /** Every hostile enemy of the level, destroyed ones included. */
    public final List<Enemy> enemies = new ArrayList<>();
    /** Every pickup item of the level, collected ones included. */
    public final List<Pickup> pickups = new ArrayList<>();
    /** The pistol. */
    public final Pistol pistol = new Pistol();
    /** The energy that pays for blasts and dashes. */
    public final EnergyMeter energy = new EnergyMeter();
    /** The blast's charge state. */
    public final BlastCharger charger = new BlastCharger(energy);
    /** The motion of the first-person pistol: recoil, bob and sway. */
    public final ViewmodelAnimation viewmodel = new ViewmodelAnimation();
    /** The blasts in flight. */
    public final List<BlastProjectile> projectiles = new ArrayList<>();
    /** Enemy plasma bolts in flight. */
    public final List<EnemyProjectile> enemyProjectiles = new ArrayList<>();
    /** The visual effects that are still running. */
    public final List<Effect> effects = new ArrayList<>();

    private final List<Damageable> allDamageables = new ArrayList<>();
    private final Pathfinder pathfinder = new Pathfinder();
    private final Vector3 eye = new Vector3();
    private final Vector3 direction = new Vector3();
    private final Vector3 blastOrigin = new Vector3();
    private final Vector3 blastDir = new Vector3();
    private final Vector3 viewRight = new Vector3();
    private final Vector3 viewUp = new Vector3();
    private final Vector3 aimPoint = new Vector3();
    private final Vector2 spawnCheckPos = new Vector2();
    private final boolean[] breakHandled;
    private final boolean[] enemyBreakHandled;
    private float time;
    private float damageTaken;
    private boolean levelCleared;
    private float hitMarkerTimer;
    private boolean hitMarkerKill;
    private String toastMessage;
    private float toastTimer;

    /** @param map the level to play on Normal difficulty */
    public GameSimulation(GameMap map) {
        this(map, PlayerLoadout.fresh(), false);
    }

    /**
     * @param map the level to play
     * @param entryLoadout starting player health, armor and difficulty snapshot
     * @param completeOnAllTargets whether clearing all targets ends the level
     */
    public GameSimulation(GameMap map, PlayerLoadout entryLoadout, boolean completeOnAllTargets) {
        this.map = map;
        this.entryLoadout = entryLoadout == null ? PlayerLoadout.fresh() : entryLoadout;
        this.completeOnAllTargets = completeOnAllTargets;
        this.player = new Player(map.getSpawnX(), map.getSpawnZ(), map.getSpawnYaw(),
            this.entryLoadout.health, this.entryLoadout.armor);
        for (GameMap.TargetSpawn spawn : map.getTargetSpawns()) {
            Target target = new Target(spawn.kind, spawn.x, spawn.z);
            targets.add(target);
            allDamageables.add(target);
        }
        for (GameMap.EnemySpawn spawn : map.getEnemySpawns()) {
            Enemy enemy = new Enemy(spawn.kind, map, spawn.x, spawn.z);
            enemies.add(enemy);
            allDamageables.add(enemy);
        }
        for (GameMap.PickupSpawn spawn : map.getPickupSpawns()) {
            pickups.add(new Pickup(spawn.kind, spawn.x, spawn.z));
        }
        breakHandled = new boolean[targets.size()];
        enemyBreakHandled = new boolean[enemies.size()];
    }

    /**
     * Advances the game by one frame.
     * @param frameTime the time since the last frame in seconds; it is clamped to between 0 and {@link #MAX_DT}
     * @param input what the player asks for this frame
     */
    public void update(float frameTime, PlayerInput input) {
        float dt = Math.max(0f, Math.min(MAX_DT, frameTime));
        player.updateTimers(dt);
        hitMarkerTimer = Math.max(0f, hitMarkerTimer - dt);
        toastTimer = Math.max(0f, toastTimer - dt);

        if (levelCleared || player.isDead()) {
            charger.cancel();
            viewmodel.update(dt, false, 0f, 0f, 0f);
            updateEnemiesWhenInactive(dt);
            updateTargetsAndEffects(dt);
            return;
        }

        time += dt;
        float beforeYaw = player.getYaw();
        float beforePitch = player.getPitch();
        player.look(input.yawDelta, input.pitchDelta);

        if (input.dash) {
            charger.cancel();
            player.tryDash(input.moveForward, input.moveStrafe, energy);
        }

        float beforeX = player.position.x;
        float beforeZ = player.position.y;
        player.move(map, allDamageables, input.moveForward, input.moveStrafe, dt);
        collectPickups();
        checkExitReached();

        player.eye(eye);
        player.direction(direction);

        float vitalsBefore = player.getHealth() + player.getArmor();
        boolean fired = !levelCleared && pistol.update(dt, input.fire);
        if (fired) firePistol();

        float firedCharge = charger.update(dt, !levelCleared && !player.isDashing() && input.charge);

        // The gun bobs with the distance actually covered, so it settles when the player is held up by a wall.
        float walked = player.position.dst(beforeX, beforeZ);
        float walkSpeed = dt > 0f ? walked / (Player.MOVE_SPEED * dt) : 0f;
        // It sways with the turn actually made, so it settles when the view is held at the pitch limit.
        float turned = (player.getYaw() - beforeYaw + 540f) % 360f - 180f;
        viewmodel.update(dt, fired || firedCharge > 0f, walkSpeed, turned, player.getPitch() - beforePitch);

        float selfScale = BlastProjectile.SELF_DAMAGE_SCALE * entryLoadout.difficulty.damageTakenScale;
        for (int i = projectiles.size() - 1; i >= 0; i--) {
            BlastProjectile projectile = projectiles.get(i);
            int aliveBefore = getTargetsRemaining() + getEnemiesRemaining();
            float healthBefore = totalDamageableHealth();
            if (!projectile.update(dt, map, allDamageables, player, selfScale)) continue;
            projectiles.remove(i);
            float radius = BlastProjectile.blastRadius(projectile.getCharge());
            effects.add(new Effect(Effect.Type.EXPLOSION, projectile.position, Vector3.Y, radius));
            alertNoise(projectile.position.x, projectile.position.z, 11.0f, 5.5f);
            if (totalDamageableHealth() < healthBefore) {
                triggerHitMarker(getTargetsRemaining() + getEnemiesRemaining() < aliveBefore);
            }
        }

        if (firedCharge > 0f) {
            spawnBlast(firedCharge);
        }

        if (!enemies.isEmpty()) {
            pathfinder.compute(map, player.position.x, player.position.y);
            for (int i = 0; i < enemies.size(); i++) {
                enemies.get(i).update(dt, map, pathfinder, player, entryLoadout.difficulty,
                    allDamageables, enemyProjectiles);
            }
        }
        for (int i = enemyProjectiles.size() - 1; i >= 0; i--) {
            if (enemyProjectiles.get(i).update(dt, map, player, entryLoadout.difficulty.damageTakenScale,
                targets, enemies, effects)) {
                enemyProjectiles.remove(i);
            }
        }

        resolveDestroyedObjects();

        float vitalsLost = Math.max(0f, vitalsBefore - (player.getHealth() + player.getArmor()));
        damageTaken += vitalsLost;

        if (!player.isDead() && completeOnAllTargets
            && (!targets.isEmpty() || !enemies.isEmpty())
            && getTargetsRemaining() == 0 && getEnemiesRemaining() == 0) {
            levelCleared = true;
        }

        updateTargetsAndEffects(dt);
    }

    /** @return the simulated active play time in seconds: the sum of the clamped time steps while playing. */
    public float getTime() {
        return time;
    }

    /** @return total health and armor lost by the player during this stage. */
    public float getDamageTaken() {
        return damageTaken;
    }

    /** @return whether the stage objective or exit has been reached. */
    public boolean isLevelCleared() {
        return levelCleared;
    }

    /** @return hit-marker alpha from 0 (hidden) to 1 (just hit). */
    public float getHitMarker() {
        return hitMarkerTimer / HIT_MARKER_TIME;
    }

    /** @return whether the active hit-marker represents a target destruction. */
    public boolean isHitMarkerKill() {
        return hitMarkerKill;
    }

    /** @return current pickup notification text, or null when none is active. */
    public String getToastMessage() {
        return toastTimer > 0f ? toastMessage : null;
    }

    /** @return pickup notification alpha from 0 to 1. */
    public float getToastAlpha() {
        return Math.min(1f, toastTimer / 0.4f);
    }

    /**
     * Call when the game is paused. A blast that is charging is dropped at no cost, so that letting go of the button
     * during the pause does not fire it on return.
     */
    public void pause() {
        charger.cancel();
    }

    /** @return the number of targets that are not destroyed. */
    public int getTargetsRemaining() {
        int remaining = 0;
        for (int i = 0; i < targets.size(); i++) {
            if (!targets.get(i).isDestroyed()) remaining++;
        }
        return remaining;
    }

    /** @return the number of targets destroyed so far. */
    public int getTargetsDestroyed() {
        return targets.size() - getTargetsRemaining();
    }

    /** @return the number of hostile enemies still alive. */
    public int getEnemiesRemaining() {
        int remaining = 0;
        for (int i = 0; i < enemies.size(); i++) {
            if (!enemies.get(i).isDestroyed()) remaining++;
        }
        return remaining;
    }

    /** @return the number of hostile enemies destroyed so far. */
    public int getEnemiesDestroyed() {
        return enemies.size() - getEnemiesRemaining();
    }

    /** @return the number of pickups collected so far. */
    public int getPickupsCollected() {
        int collected = 0;
        for (int i = 0; i < pickups.size(); i++) {
            if (pickups.get(i).isCollected()) collected++;
        }
        return collected;
    }

    private void collectPickups() {
        for (int i = 0; i < pickups.size(); i++) {
            String message = pickups.get(i).tryCollect(player, energy, entryLoadout.difficulty.pickupScale);
            if (message != null) {
                toastMessage = message;
                toastTimer = TOAST_TIME;
            }
        }
    }

    private void checkExitReached() {
        if (!map.hasExit() || player.isDead()) return;
        float reach = Player.RADIUS + GameMap.EXIT_RADIUS;
        if (Vector2.dst2(player.position.x, player.position.y, map.getExitX(), map.getExitZ()) < reach * reach) {
            levelCleared = true;
        }
    }

    private void spawnBlast(float charge) {
        RayHit aim = Hitscan.cast(map, allDamageables, eye, direction, Pistol.RANGE);
        if (aim.isHit()) {
            aimPoint.set(aim.point);
        } else {
            aimPoint.set(eye).mulAdd(direction, Pistol.RANGE);
        }

        viewRight.set(direction).crs(Vector3.Y);
        if (viewRight.len2() < 1e-6f) viewRight.set(1f, 0f, 0f);
        else viewRight.nor();
        viewUp.set(viewRight).crs(direction).nor();

        float clearance = aim.isHit() ? Math.max(0f, (aim.distance - 0.20f) / 0.55f) : 1f;
        float t = Math.min(1f, clearance);
        blastOrigin.set(eye)
            .mulAdd(direction, BLAST_SPAWN_FORWARD * t)
            .mulAdd(viewRight, BLAST_SPAWN_RIGHT * t)
            .mulAdd(viewUp, -BLAST_SPAWN_DOWN * t);
        float minY = BlastProjectile.RADIUS + 0.01f;
        float maxY = GameMap.WALL_HEIGHT - BlastProjectile.RADIUS - 0.01f;
        blastOrigin.y = Math.max(minY, Math.min(maxY, blastOrigin.y));

        spawnCheckPos.set(blastOrigin.x, blastOrigin.z);
        if (Collision.overlapsWall(map, blastOrigin.x, blastOrigin.z, BlastProjectile.RADIUS + 0.01f)
            || Collision.overlapsSolid(allDamageables, spawnCheckPos, BlastProjectile.RADIUS + 0.01f)) {
            blastOrigin.set(eye.x, Math.max(minY, eye.y - 0.05f), eye.z);
        }

        blastDir.set(aimPoint).sub(blastOrigin);
        if (blastDir.len2() < 1e-6f) blastDir.set(direction);
        else blastDir.nor();
        projectiles.add(new BlastProjectile(blastOrigin, blastDir, charge));
    }

    private void firePistol() {
        alertNoise(player.position.x, player.position.y, 9.0f, 4.5f);
        RayHit hit = Hitscan.cast(map, allDamageables, eye, direction, Pistol.RANGE);
        if (!hit.isHit()) return;
        // Lift the effect off the surface towards the open side.
        hit.point.mulAdd(hit.normal, MARK_OFFSET);
        if (hit.kind != RayHit.Kind.TARGET) {
            effects.add(new Effect(Effect.Type.IMPACT, hit.point, hit.normal, 0.11f));
            return;
        }
        hit.target.damage(Pistol.DAMAGE);
        if (hit.target instanceof Enemy) {
            alertNoise(hit.target.getX(), hit.target.getZ(), 5.0f, 3.0f);
        }
        effects.add(new Effect(Effect.Type.SPARK, hit.point, hit.normal, 0.2f));
        triggerHitMarker(hit.target.isDestroyed());
    }

    private void resolveDestroyedObjects() {
        float canisterPlayerScale = CANISTER_SELF_DAMAGE_SCALE * entryLoadout.difficulty.damageTakenScale;
        boolean progressed = true;
        while (progressed) {
            progressed = false;
            for (int i = 0; i < targets.size(); i++) {
                if (breakHandled[i] || !targets.get(i).isDestroyed()) continue;
                breakHandled[i] = true;
                progressed = true;
                triggerHitMarker(true);
                Target target = targets.get(i);
                if (target.getKind() == Target.Kind.CANISTER) {
                    Vector3 centre = new Vector3(target.getX(), target.getHeight() * 0.5f, target.getZ());
                    effects.add(new Effect(Effect.Type.EXPLOSION, centre, Vector3.Y, CANISTER_BLAST_RADIUS));
                    Explosion.apply(map, target.getX(), target.getZ(), CANISTER_BLAST_RADIUS, CANISTER_BLAST_DAMAGE,
                        allDamageables, player, canisterPlayerScale);
                    alertNoise(target.getX(), target.getZ(), 11.0f, 5.5f);
                } else {
                    addBreakEffect(target);
                }
            }
            for (int i = 0; i < enemies.size(); i++) {
                if (enemyBreakHandled[i] || !enemies.get(i).isDestroyed()) continue;
                enemyBreakHandled[i] = true;
                progressed = true;
                triggerHitMarker(true);
                Enemy enemy = enemies.get(i);
                Vector3 centre = new Vector3(enemy.getX(), enemy.getHeight() * 0.5f, enemy.getZ());
                effects.add(new Effect(Effect.Type.EXPLOSION, centre, Vector3.Y, enemy.getHeight() * 0.8f));
                alertNoise(enemy.getX(), enemy.getZ(), 5.0f, 3.0f);
            }
        }
    }

    private void alertNoise(float x, float z, float losRange, float muffledRange) {
        float losRange2 = losRange * losRange;
        float muffledRange2 = muffledRange * muffledRange;
        for (int i = 0; i < enemies.size(); i++) {
            Enemy enemy = enemies.get(i);
            if (enemy.isDestroyed()) continue;
            float dx = enemy.getX() - x;
            float dz = enemy.getZ() - z;
            float dist2 = dx * dx + dz * dz;
            if (dist2 <= muffledRange2) {
                enemy.alert();
            } else if (dist2 <= losRange2 && Hitscan.hasLineOfSight(map, x, z, enemy.getX(), enemy.getZ())) {
                enemy.alert();
            }
        }
    }

    private void addBreakEffect(Damageable target) {
        Vector3 centre = new Vector3(target.getX(), target.getHeight() * 0.5f, target.getZ());
        effects.add(new Effect(Effect.Type.EXPLOSION, centre, Vector3.Y, target.getHeight() * 0.7f));
    }

    private void triggerHitMarker(boolean kill) {
        if (hitMarkerTimer <= 0f) {
            hitMarkerKill = kill;
        } else {
            hitMarkerKill = hitMarkerKill || kill;
        }
        hitMarkerTimer = HIT_MARKER_TIME;
    }

    private float totalDamageableHealth() {
        float sum = 0f;
        for (int i = 0; i < allDamageables.size(); i++) sum += allDamageables.get(i).getHealth();
        return sum;
    }

    private void updateEnemiesWhenInactive(float dt) {
        for (int i = 0; i < enemies.size(); i++) {
            enemies.get(i).update(dt, map, null, player, entryLoadout.difficulty, allDamageables, null);
        }
        for (int i = enemyProjectiles.size() - 1; i >= 0; i--) {
            if (enemyProjectiles.get(i).update(dt, map, player, entryLoadout.difficulty.damageTakenScale,
                targets, enemies, effects)) {
                enemyProjectiles.remove(i);
            }
        }
    }

    private void updateTargetsAndEffects(float dt) {
        for (int i = 0; i < targets.size(); i++) targets.get(i).update(dt);
        for (int i = effects.size() - 1; i >= 0; i--) {
            Effect effect = effects.get(i);
            effect.update(dt);
            if (effect.isFinished()) effects.remove(i);
        }
    }
}

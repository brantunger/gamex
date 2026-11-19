package io.github.brantunger.gamex;

import com.badlogic.gdx.math.Vector2;
import com.badlogic.gdx.math.Vector3;
import io.github.brantunger.gamex.audio.SoundEvent;
import io.github.brantunger.gamex.player.Player;
import io.github.brantunger.gamex.player.PlayerInput;
import io.github.brantunger.gamex.player.PlayerLoadout;
import io.github.brantunger.gamex.weapon.BlastCharger;
import io.github.brantunger.gamex.weapon.BlastProjectile;
import io.github.brantunger.gamex.weapon.EnemyProjectile;
import io.github.brantunger.gamex.weapon.EnergyMeter;
import io.github.brantunger.gamex.weapon.Explosion;
import io.github.brantunger.gamex.weapon.Pistol;
import io.github.brantunger.gamex.weapon.RailLance;
import io.github.brantunger.gamex.weapon.Scattergun;
import io.github.brantunger.gamex.weapon.ViewmodelAnimation;
import io.github.brantunger.gamex.weapon.WeaponSlot;
import io.github.brantunger.gamex.world.Collision;
import io.github.brantunger.gamex.world.Damageable;
import io.github.brantunger.gamex.world.Door;
import io.github.brantunger.gamex.world.Effect;
import io.github.brantunger.gamex.world.Enemy;
import io.github.brantunger.gamex.world.GameMap;
import io.github.brantunger.gamex.world.Hitscan;
import io.github.brantunger.gamex.world.Pathfinder;
import io.github.brantunger.gamex.world.Pickup;
import io.github.brantunger.gamex.world.RayHit;
import io.github.brantunger.gamex.world.SecurityConsole;
import io.github.brantunger.gamex.world.Target;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.EnumSet;
import java.util.List;
import java.util.Objects;
import java.util.Set;

/**
 * The whole game state and the rules that advance it. It uses neither graphics nor audio types, so it runs without a
 * window: renderers and sound managers only read its state and events, and each frame's {@link PlayerInput} is its only
 * input.
 */
public final class GameSimulation {

    private static final Logger log = LoggerFactory.getLogger(GameSimulation.class);

    /** A short-lived 3D rail beam wake rendered from weapon muzzle to terminal impact. */
    public static final class RailBeam {
        /** Total fade-out duration of a rail beam trail in seconds. */
        public static final float DURATION = 0.35f;

        public final Vector3 start = new Vector3();
        public final Vector3 end = new Vector3();
        private float age;

        RailBeam(Vector3 start, Vector3 end) {
            this.start.set(start);
            this.end.set(end);
        }

        void update(float dt) {
            age += dt;
        }

        /** @return normalized decay progress from 0 (just fired) to 1 (finished). */
        public float getProgress() {
            return Math.min(1f, age / DURATION);
        }

        /** @return whether this beam has faded and can be removed. */
        public boolean isFinished() {
            return age >= DURATION;
        }
    }

    /** Longest time step simulated at once, in seconds; a longer frame is cut down to this. */
    public static final float MAX_DT = 0.05f;
    /** Total duration of a weapon switch dip-and-raise transition, in seconds. */
    public static final float SWAP_DURATION = ViewmodelAnimation.SWAP_TIME;
    private static final float SWAP_MIDPOINT = SWAP_DURATION * 0.5f;
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
    /** Base interval between consecutive shock grate damage ticks while standing on {@code ~}, in seconds. */
    public static final float HAZARD_INTERVAL = 0.35f;
    /** Base damage dealt per shock grate tick while grounded and not dashing. */
    public static final float HAZARD_DAMAGE = 8f;
    /** Minimum feet height above the floor that clears live {@code ~} shock grates while jumping. */
    public static final float HAZARD_CLEARANCE_HEIGHT = 0.06f;

    /** How far a mark sits off the surface it lies on, so it does not fight with the surface for depth. */
    private static final float MARK_OFFSET = 0.012f;
    /** World-space muzzle launch offset from eye so the blast starts in view and past the near clip plane. */
    private static final float BLAST_SPAWN_FORWARD = 0.52f;
    private static final float BLAST_SPAWN_RIGHT = 0.11f;
    private static final float BLAST_SPAWN_DOWN = 0.05f;
    private static final int MAX_QUEUED_SOUNDS = 256;

    /** Objective while an Amber bulkhead bars the way and its keycard is not held. */
    public static final String OBJECTIVE_AMBER_KEYCARD = "FIND THE AMBER KEYCARD";
    /** Objective while a Cyan bulkhead bars the way and its keycard is not held. */
    public static final String OBJECTIVE_CYAN_KEYCARD = "FIND THE CYAN KEYCARD";
    /** Objective while a lockdown bulkhead bars the way and no security console is overridden. */
    public static final String OBJECTIVE_CONSOLE = "OVERRIDE THE SECURITY CONSOLE";
    /** Objective while a living Warden seals the way. */
    public static final String OBJECTIVE_WARDEN = "DESTROY THE WARDEN";
    /** Objective once nothing bars the way to the exit pad. */
    public static final String OBJECTIVE_EXIT = "REACH EXIT PAD";
    /** Objective of a stage without an exit that clears once every target is destroyed. */
    public static final String OBJECTIVE_ALL_TARGETS = "DESTROY ALL TARGETS";
    /** Objective of a stage without an exit that clears once every hostile is destroyed. */
    public static final String OBJECTIVE_ALL_HOSTILES = "DESTROY ALL HOSTILES";
    /** Objective of a stage without an exit that clears once every target and hostile is destroyed. */
    public static final String OBJECTIVE_ALL_TARGETS_AND_HOSTILES = "DESTROY TARGETS AND HOSTILES";

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
    /** Every sliding bulkhead door in the level. */
    public final List<Door> doors = new ArrayList<>();
    /** Every security console pedestal in the level. */
    public final List<SecurityConsole> consoles = new ArrayList<>();
    /** The pistol. */
    public final Pistol pistol = new Pistol();
    /** The combat scattergun. */
    public final Scattergun scattergun = new Scattergun();
    /** The Slot 3 Rail Lance. */
    public final RailLance railLance = new RailLance();
    /** The energy that pays for blasts and dashes. */
    public final EnergyMeter energy = new EnergyMeter();
    /** The blast's charge state. */
    public final BlastCharger charger = new BlastCharger(energy);
    /** The motion of the first-person weapon viewmodel: recoil, bob and sway. */
    public final ViewmodelAnimation viewmodel = new ViewmodelAnimation();
    /** The blasts in flight. */
    public final List<BlastProjectile> projectiles = new ArrayList<>();
    /** Enemy plasma bolts in flight. */
    public final List<EnemyProjectile> enemyProjectiles = new ArrayList<>();
    /** Active fading 3D rail beam wakes. */
    public final List<RailBeam> railBeams = new ArrayList<>();
    /** The visual effects that are still running. */
    public final List<Effect> effects = new ArrayList<>();

    private final List<Damageable> allDamageables = new ArrayList<>();
    private final Pathfinder pathfinder = new Pathfinder();
    private final Pathfinder guardPathfinder = new Pathfinder();
    private final Vector3 eye = new Vector3();
    private final Vector3 direction = new Vector3();
    private final Vector3 blastOrigin = new Vector3();
    private final Vector3 blastDir = new Vector3();
    private final Vector3 viewRight = new Vector3();
    private final Vector3 viewUp = new Vector3();
    private final Vector3 aimPoint = new Vector3();
    private final boolean[] breakHandled;
    private final boolean[] enemyBreakHandled;
    private final float[] enemyHazardTimers;
    private final float[] targetHealthBefore;
    private final float[] enemyHealthBefore;
    private final List<SoundEvent> soundEvents = new ArrayList<>();
    private final Set<Pickup.Kind> heldKeycards = EnumSet.noneOf(Pickup.Kind.class);
    /** The bulkhead standing on each cell, row by row, or null where there is none. */
    private final Door[] doorAtCell;
    /**
     * The cells the player can walk to without a keycard, console or kill they do not have yet, and the cell of a
     * bulkhead they lean on but cannot pass.
     */
    private final boolean[] reachable;
    private final int[] reachQueue;
    /** The state {@link #reachable} and {@link #objectiveText} were worked out for; -1 before the first time. */
    private int objectiveState = -1;
    private String objectiveText;
    private int objectiveFloodCount;
    private int shotsFired;
    private int shotsHit;
    private float time;
    private float damageTaken;
    private float playerHazardTimer;
    private boolean levelCleared;
    private float hitMarkerTimer;
    private boolean hitMarkerKill;
    private String toastMessage;
    private float toastTimer;
    private boolean prevJumpInput;
    private boolean prevDashInput;
    private boolean prevChargeRefused;
    private boolean prevInteractInput;
    private boolean hasScattergun;
    private int shells;
    private boolean hasRailLance;
    private int slugs;
    private WeaponSlot activeWeapon;
    private WeaponSlot pendingWeapon;
    private WeaponSlot previousWeapon;
    private boolean swapping;
    /** The weapon the swap in progress is moving away from; asking for it again reverses the swap. */
    private WeaponSlot swapOrigin;
    private float swapElapsed;

    private final Pickup.Arsenal pickupArsenal = new Pickup.Arsenal() {
        @Override
        public boolean hasScattergun() {
            return hasScattergun;
        }

        @Override
        public int getShells() {
            return shells;
        }

        @Override
        public void grantScattergun(int addedShells) {
            boolean firstUnlock = !hasScattergun;
            hasScattergun = true;
            shells = Math.min(Scattergun.MAX_SHELLS, shells + Math.max(0, addedShells));
            if (firstUnlock) {
                requestWeaponSwitch(WeaponSlot.SCATTERGUN, false);
            }
        }

        @Override
        public void addShells(int addedShells) {
            shells = Math.min(Scattergun.MAX_SHELLS, shells + Math.max(0, addedShells));
        }

        @Override
        public boolean hasRailLance() {
            return hasRailLance;
        }

        @Override
        public int getSlugs() {
            return slugs;
        }

        @Override
        public void grantRailLance(int addedSlugs) {
            boolean firstUnlock = !hasRailLance;
            hasRailLance = true;
            slugs = Math.min(RailLance.MAX_SLUGS, slugs + Math.max(0, addedSlugs));
            if (firstUnlock) {
                requestWeaponSwitch(WeaponSlot.RAIL_LANCE, false);
            }
        }

        @Override
        public void addSlugs(int addedSlugs) {
            slugs = Math.min(RailLance.MAX_SLUGS, slugs + Math.max(0, addedSlugs));
        }
    };

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
        this.hasScattergun = this.entryLoadout.hasScattergun;
        this.shells = this.entryLoadout.shells;
        this.hasRailLance = this.entryLoadout.hasRailLance;
        this.slugs = this.entryLoadout.slugs;
        this.activeWeapon = this.entryLoadout.activeWeapon;
        this.pendingWeapon = this.activeWeapon;
        this.previousWeapon = initialPreviousWeapon(this.activeWeapon, this.hasScattergun, this.shells,
            this.hasRailLance, this.slugs);
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
        for (GameMap.DoorSpawn spawn : map.getDoorSpawns()) {
            doors.add(new Door(spawn.lockKind, spawn.orientation, spawn.col, spawn.row));
        }
        for (GameMap.ConsoleSpawn spawn : map.getConsoleSpawns()) {
            SecurityConsole console = new SecurityConsole(spawn.x, spawn.z);
            consoles.add(console);
            allDamageables.add(console);
        }
        breakHandled = new boolean[targets.size()];
        enemyBreakHandled = new boolean[enemies.size()];
        enemyHazardTimers = new float[enemies.size()];
        targetHealthBefore = new float[targets.size()];
        enemyHealthBefore = new float[enemies.size()];
        int cellCount = map.getWidth() * map.getHeight();
        doorAtCell = new Door[cellCount];
        for (int i = 0; i < doors.size(); i++) {
            Door door = doors.get(i);
            doorAtCell[door.getRow() * map.getWidth() + door.getCol()] = door;
        }
        reachable = new boolean[cellCount];
        reachQueue = new int[cellCount];
        if (log.isDebugEnabled()) {
            for (int i = 0; i < enemies.size(); i++) {
                Enemy enemy = enemies.get(i);
                log.debug("Spawned {} at ({}, {})", enemy.getKind(), enemy.getX(), enemy.getZ());
            }
            log.debug("Simulation ready on {}: {}x{} map, {} targets, {} hostiles, {} pickups, {} doors, {} consoles",
                this.entryLoadout.difficulty, map.getWidth(), map.getHeight(), targets.size(), enemies.size(),
                pickups.size(), doors.size(), consoles.size());
        }
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
            railLance.cancelZoom();
            pistol.update(dt, false);
            scattergun.updateIdle(dt);
            railLance.updateIdle(dt);
            player.clearJumpBuffer();
            prevJumpInput = false;
            prevDashInput = false;
            prevChargeRefused = false;
            prevInteractInput = false;
            viewmodel.update(dt, false, 0f, 0f, 0f);
            updateEnemiesWhenInactive(dt);
            updateTargetsAndEffects(dt);
            return;
        }

        time += dt;
        float beforeYaw = player.getYaw();
        float beforePitch = player.getPitch();
        float lookScale = activeWeapon == WeaponSlot.RAIL_LANCE ? railLance.getZoomFovScale() : 1f;
        player.look(input.yawDelta * lookScale, input.pitchDelta * lookScale);

        handleWeaponSwitchInput(input.weaponSlot);
        advanceWeaponSwap(dt);

        boolean jumpPressedEdge = input.jump && !prevJumpInput;
        if (jumpPressedEdge) {
            player.tryJump();
        }
        prevJumpInput = input.jump;

        boolean dashPressedEdge = input.dash && !prevDashInput;
        if (input.dash) {
            boolean canAffordDash = energy.canSpend(Player.DASH_COST);
            if (player.tryDash(input.moveForward, input.moveStrafe, energy)) {
                charger.cancel();
                railLance.cancelZoom();
                emitPlayerSound(SoundEvent.Type.PLAYER_DASH, 1f);
            } else if (dashPressedEdge && !canAffordDash && !player.isDead() && !player.isDashing()) {
                emitPlayerSound(SoundEvent.Type.LOW_ENERGY, 1f);
            }
        }
        prevDashInput = input.dash;

        float beforeX = player.position.x;
        float beforeZ = player.position.y;
        player.move(map, allDamageables, doors, input.moveForward, input.moveStrafe, dt);
        if (player.consumeLandedTriggered()) {
            emitPlayerSound(SoundEvent.Type.PLAYER_LAND, 0.85f);
        }
        if (player.consumeJumpTriggered()) {
            emitPlayerSound(SoundEvent.Type.PLAYER_JUMP, 0.90f);
        }
        collectPickups();
        boolean interactPressedEdge = input.interact && !prevInteractInput;
        prevInteractInput = input.interact;
        if (interactPressedEdge) {
            handleInteract();
        }
        boolean exitReached = isExitReached();

        player.eye(eye);
        player.direction(direction);

        float vitalsBefore = player.getHealth() + player.getArmor();
        snapshotDamageableHealth();
        float kickScale = 0f;
        float firedCharge = 0f;
        if (exitReached || swapping) {
            pistol.update(dt, false);
            scattergun.updateIdle(dt);
            railLance.updateIdle(dt, !exitReached && input.charge);
            charger.cancel();
            prevChargeRefused = false;
            energy.regenerate(dt);
        } else if (activeWeapon == WeaponSlot.PISTOL) {
            scattergun.updateIdle(dt);
            railLance.updateIdle(dt, input.charge);
            boolean fired = pistol.update(dt, input.fire);
            if (fired) {
                firePistol();
                kickScale = 1f;
            }
            // The charger refills the meter itself, except while a charge is building.
            firedCharge = charger.update(dt, !player.isDashing() && input.charge);
            boolean refusedNow = charger.isRefused();
            if (refusedNow && !prevChargeRefused) {
                emitPlayerSound(SoundEvent.Type.LOW_ENERGY, 1f);
            }
            prevChargeRefused = refusedNow;
            if (firedCharge > 0f) kickScale = 1f;
        } else if (activeWeapon == WeaponSlot.SCATTERGUN) {
            pistol.update(dt, false);
            railLance.updateIdle(dt, input.charge);
            charger.cancel();
            prevChargeRefused = false;
            energy.regenerate(dt);
            boolean wantPrimary = input.fire;
            boolean wantAlt = input.charge;
            if (shells <= 0) {
                scattergun.updateIdle(dt);
                if (scattergun.isReady() && (wantPrimary || wantAlt)) {
                    emitPlayerSound(SoundEvent.Type.WEAPON_DRY_FIRE, 1f);
                    showToast("OUT OF SHELLS");
                    smartSwapFromEmptyWeapon(WeaponSlot.SCATTERGUN);
                }
            } else {
                Scattergun.Shot shot = scattergun.update(dt, wantPrimary, wantAlt, shells);
                if (shot != Scattergun.Shot.NONE) {
                    fireScattergun(shot);
                    kickScale = shot == Scattergun.Shot.DOUBLE_BLAST ? 2.4f : 1.6f;
                }
            }
        } else {
            pistol.update(dt, false);
            scattergun.updateIdle(dt);
            charger.cancel();
            prevChargeRefused = false;
            energy.regenerate(dt);
            if (slugs <= 0 && input.fire && railLance.isReadyAfter(dt)) {
                railLance.updateIdle(dt, input.charge);
                emitPlayerSound(SoundEvent.Type.WEAPON_DRY_FIRE, 1f);
                showToast("OUT OF SLUGS");
                smartSwapFromEmptyWeapon(WeaponSlot.RAIL_LANCE);
            } else {
                boolean firedRail = railLance.update(dt, input.fire, input.charge, player.isDashing(), slugs);
                if (railLance.consumeZoomInTriggered()) {
                    emitPlayerSound(SoundEvent.Type.RAIL_ZOOM_IN, 0.85f);
                }
                if (railLance.consumeZoomOutTriggered()) {
                    emitPlayerSound(SoundEvent.Type.RAIL_ZOOM_OUT, 0.75f);
                }
                if (firedRail) {
                    fireRailLance();
                    kickScale = 2.6f;
                }
            }
        }

        // The gun bobs with the distance actually walked on the floor, settling against walls or while airborne.
        float walked = player.isAirborne() ? 0f : player.position.dst(beforeX, beforeZ);
        float walkSpeed = dt > 0f ? walked / (Player.MOVE_SPEED * dt) : 0f;
        // It sways with the turn actually made, so it settles when the view is held at the pitch limit.
        float turned = (player.getYaw() - beforeYaw + 540f) % 360f - 180f;
        viewmodel.update(dt, kickScale, walkSpeed, turned, player.getPitch() - beforePitch);

        float selfScale = BlastProjectile.SELF_DAMAGE_SCALE * entryLoadout.difficulty.damageTakenScale;
        for (int i = projectiles.size() - 1; i >= 0; i--) {
            BlastProjectile projectile = projectiles.get(i);
            int aliveBefore = getTargetsRemaining() + getEnemiesRemaining();
            float healthBefore = totalDamageableHealth();
            if (!projectile.update(dt, map, allDamageables, doors, player, selfScale)) continue;
            projectiles.remove(i);
            float radius = BlastProjectile.blastRadius(projectile.getCharge());
            effects.add(new Effect(Effect.Type.EXPLOSION, projectile.position, Vector3.Y, radius));
            emitWorldSound(SoundEvent.Type.EXPLOSION, projectile.position.x, projectile.position.y,
                projectile.position.z, 0.75f + 0.35f * projectile.getCharge());
            alertNoise(projectile.position.x, projectile.position.z, 11.0f, 5.5f);
            if (totalDamageableHealth() < healthBefore) {
                // The blast was counted as a shot on release; whether it hit is only known here.
                shotsHit++;
                triggerHitMarker(getTargetsRemaining() + getEnemiesRemaining() < aliveBefore);
            }
        }
        resolveDestroyedObjects(true);

        if (firedCharge > 0f) {
            spawnBlast(firedCharge);
        }

        if (!enemies.isEmpty()) {
            pathfinder.compute(map, doors, player.position.x, player.position.y);
            boolean hasHazards = !map.getHazardCells().isEmpty();
            if (hasHazards && hasAliveGuard()) {
                guardPathfinder.compute(map, doors, true, player.position.x, player.position.y);
            }
            for (int i = 0; i < enemies.size(); i++) {
                Enemy enemy = enemies.get(i);
                Enemy.State stateBefore = enemy.getState();
                int boltsBefore = enemyProjectiles.size();
                Pathfinder enemyPath = (hasHazards && enemy.getKind() == Enemy.Kind.GUARD)
                    ? guardPathfinder
                    : pathfinder;
                enemy.update(dt, map, enemyPath, player, entryLoadout.difficulty,
                    allDamageables, doors, enemyProjectiles);
                if (enemy.consumeOverdriveTriggered()) {
                    log.info("Boss phase 2: {} entered overdrive at {} health after {} s", enemy.getKind(),
                        enemy.getHealth(), roundedTime());
                    emitWorldSound(SoundEvent.Type.BOSS_OVERDRIVE,
                        enemy.getX(), enemy.getHeight() * 0.55f, enemy.getZ(), 1.25f);
                    effects.add(new Effect(Effect.Type.EXPLOSION,
                        new Vector3(enemy.getX(), enemy.getHeight() * 0.5f, enemy.getZ()), Vector3.Y, 1.6f));
                    showToast("WARDEN OVERDRIVE");
                }
                // Each punch of the Warden's combo is heard, whether it lands or not.
                for (int punch = enemy.consumePunchesThrown(); punch > 0; punch--) {
                    log.debug("{} threw a punch at {} s; {} of this combo have landed", enemy.getKind(),
                        roundedTime(), enemy.getPunchesLanded());
                    emitWorldSound(SoundEvent.Type.BOSS_SLAM, enemy.getX(), enemy.getHeight() * 0.6f, enemy.getZ(),
                        1.15f);
                }
                for (int b = boltsBefore; b < enemyProjectiles.size(); b++) {
                    EnemyProjectile bolt = enemyProjectiles.get(b);
                    emitWorldSound(SoundEvent.Type.ENEMY_SHOOT,
                        bolt.position.x, bolt.position.y, bolt.position.z, 1f);
                }
                if (enemy.getKind() == Enemy.Kind.DRONE && stateBefore != Enemy.State.LUNGING
                    && enemy.getState() == Enemy.State.LUNGING) {
                    emitWorldSound(SoundEvent.Type.DRONE_LUNGE, enemy.getX(), enemy.getHeight() * 0.5f, enemy.getZ(),
                        1f);
                }
            }
        }
        for (int i = enemyProjectiles.size() - 1; i >= 0; i--) {
            if (enemyProjectiles.get(i).update(dt, map, player, entryLoadout.difficulty.damageTakenScale,
                targets, enemies, doors, consoles, effects)) {
                enemyProjectiles.remove(i);
            }
        }

        updateHazards(dt);
        resolveDestroyedObjects(false);
        emitNonLethalHitSounds();

        float vitalsLost = Math.max(0f, vitalsBefore - (player.getHealth() + player.getArmor()));
        damageTaken += vitalsLost;
        if (vitalsLost > 0f) {
            if (player.isDead()) {
                log.info("Player died after {} s at ({}, {}); {} damage taken", roundedTime(), player.position.x,
                    player.position.y, damageTaken);
                emitPlayerSound(SoundEvent.Type.PLAYER_DEATH, 1f);
            } else {
                emitPlayerSound(SoundEvent.Type.PLAYER_HURT, Math.min(1f, 0.6f + vitalsLost / 50f));
            }
        }
        if (player.isDead()) {
            charger.cancel();
            railLance.cancelZoom();
        }

        boolean allDestroyed = completeOnAllTargets
            && (!targets.isEmpty() || !enemies.isEmpty())
            && getTargetsRemaining() == 0 && getEnemiesRemaining() == 0;
        // Decided once the step's damage is dealt: a player killed on the frame that meets the objective has not
        // cleared the stage.
        if ((exitReached || allDestroyed) && !player.isDead()) {
            levelCleared = true;
            log.info("Level cleared in {} s by {}: {} damage taken, {} of {} shots hit", roundedTime(),
                exitReached ? "reaching the exit" : "destroying everything", damageTaken, shotsHit, shotsFired);
            charger.cancel();
            railLance.cancelZoom();
            // Nothing moves the player once the stage is cleared, so a jump in progress ends here.
            player.settleOnFloor();
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

    /** Appends pending simulation sound events to {@code out} and clears the internal queue. */
    public void drainSoundEvents(List<SoundEvent> out) {
        if (soundEvents.isEmpty()) return;
        if (out != null) out.addAll(soundEvents);
        soundEvents.clear();
    }

    /** @return a copy of pending simulation sound events, clearing the internal queue. */
    public List<SoundEvent> drainSoundEvents() {
        List<SoundEvent> copy = new ArrayList<>(soundEvents);
        soundEvents.clear();
        return copy;
    }

    /** @return whether the continuous blast-charge loop audio should currently be active. */
    public boolean isBlastChargingAudioActive() {
        return !levelCleared && !player.isDead() && charger.isCharging();
    }

    /** @return normalized blast charge progress [0, 1] while charging, otherwise 0. */
    public float getBlastChargeProgress() {
        return charger.isCharging() ? charger.getCharge() : 0f;
    }

    /** @return whether the player has unlocked the Combat Scattergun. */
    public boolean hasScattergun() {
        return hasScattergun;
    }

    /** @return shells remaining in the finite shotgun shell pool. */
    public int getShells() {
        return shells;
    }

    /** @return whether the player has unlocked the Slot 3 Rail Lance. */
    public boolean hasRailLance() {
        return hasRailLance;
    }

    /** @return slugs remaining in the finite Rail Lance slug pool. */
    public int getSlugs() {
        return slugs;
    }

    /** @return normalized Rail Lance tactical optic zoom in [0, 1] while active and not swapping. */
    public float getZoom() {
        return activeWeapon == WeaponSlot.RAIL_LANCE && !swapping ? railLance.getZoom() : 0f;
    }

    /** @return weapon currently visible and firing (changes at the midpoint of a swap dip). */
    public WeaponSlot getActiveWeapon() {
        return activeWeapon;
    }

    /** @return weapon being swapped towards, or {@link #getActiveWeapon()} at rest. */
    public WeaponSlot getTargetWeapon() {
        return swapping ? pendingWeapon : activeWeapon;
    }

    /** @return normalized viewmodel swap dip progress in [0, 1] peaking at the midpoint of the switch. */
    public float getSwapProgress() {
        if (!swapping || SWAP_DURATION <= 0f) return 0f;
        float u = Math.min(1f, Math.max(0f, swapElapsed / SWAP_DURATION));
        return (float) Math.sin(Math.PI * u);
    }

    /** @return whether the active weapon currently has a visible muzzle flash for dynamic world lighting. */
    public boolean isMuzzleFlashVisible() {
        switch (activeWeapon) {
            case SCATTERGUN:
                return scattergun.isFlashVisible();
            case RAIL_LANCE:
                return railLance.isFlashVisible();
            case PISTOL:
            default:
                return pistol.isFlashVisible();
        }
    }

    /** @return carry-over loadout snapshot at stage exit (preserving unlocked weapons and remaining ammo). */
    public PlayerLoadout captureCarryOverLoadout() {
        return PlayerLoadout.fromPlayer(player, entryLoadout.difficulty,
            hasScattergun, shells, hasRailLance, slugs, getTargetWeapon());
    }

    /**
     * Call when the game is paused. Drops any charging blast or tactical optic zoom cleanly so releasing mouse
     * buttons during pause does not trigger on resume.
     */
    public void pause() {
        charger.cancel();
        railLance.cancelZoom();
        player.clearJumpBuffer();
        prevJumpInput = false;
        prevDashInput = false;
        prevChargeRefused = false;
        prevInteractInput = false;
    }

    /**
     * @param kind keycard kind to check
     * @return whether the player currently holds that stage-local keycard
     */
    public boolean hasKeycard(Pickup.Kind kind) {
        return kind != null && heldKeycards.contains(kind);
    }

    /** @return unmodifiable view of keycards collected in this stage. */
    public Set<Pickup.Kind> getHeldKeycards() {
        return Collections.unmodifiableSet(heldKeycards);
    }

    /**
     * @return contextual HUD interaction prompt when near an operable bulkhead or security console, or {@code null}
     */
    public String getInteractionPrompt() {
        if (levelCleared || player.isDead()) return null;
        Object target = findInteractTarget();
        if (target instanceof SecurityConsole) return "[E] OVERRIDE LOCKDOWN";
        if (!(target instanceof Door)) return null;
        Door door = (Door) target;
        switch (door.getLockKind()) {
            case AMBER_KEY:
                return hasKeycard(Pickup.Kind.AMBER_KEYCARD)
                    ? "[E] UNLOCK AMBER BULKHEAD"
                    : "NEEDS AMBER KEYCARD";
            case CYAN_KEY:
                return hasKeycard(Pickup.Kind.CYAN_KEYCARD)
                    ? "[E] UNLOCK CYAN BULKHEAD"
                    : "NEEDS CYAN KEYCARD";
            case LOCKDOWN:
                return "SEALED BY SECURITY CONSOLE";
            case BOSS:
                return "SEALED BY THE WARDEN";
            case NONE:
            default:
                return "[E] OPEN BULKHEAD";
        }
    }

    /** @return the active alerted live Warden for HUD display, or {@code null} when dormant or dead. */
    public Enemy getActiveBoss() {
        for (int i = 0; i < enemies.size(); i++) {
            Enemy enemy = enemies.get(i);
            if (enemy.getKind() == Enemy.Kind.WARDEN && !enemy.isDestroyed()
                && enemy.getState() != Enemy.State.DORMANT) {
                return enemy;
            }
        }
        return null;
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

    /**
     * @return the trigger pulls so far: every pistol shot, charged blast, Scattergun blast (one shell or two) and
     *     Rail Lance beam counts once
     */
    public int getShotsFired() {
        return shotsFired;
    }

    /**
     * @return the trigger pulls that damaged at least one hostile, target or canister. A charged blast counts once
     *     it explodes, so one still in flight has not hit yet.
     */
    public int getShotsHit() {
        return shotsHit;
    }

    /**
     * Names the next step towards clearing the stage, for the HUD. On a stage with an exit pad it is whatever the
     * player still lacks to walk there: a keycard for a locked bulkhead, a console override for a lockdown, the
     * Warden's destruction for a sealed one. Among those, the one the player can reach now comes first.
     * @return one of the {@code OBJECTIVE_} constants, or null on a stage with neither an exit nor a clear on all
     *     targets
     */
    public String getObjectiveText() {
        if (!map.hasExit()) {
            if (!completeOnAllTargets) return null;
            if (enemies.isEmpty()) return OBJECTIVE_ALL_TARGETS;
            return targets.isEmpty() ? OBJECTIVE_ALL_HOSTILES : OBJECTIVE_ALL_TARGETS_AND_HOSTILES;
        }
        int state = objectiveStateNow();
        int playerCell = cellIndexAt(player.position.x, player.position.y);
        // Worked out again only when a keycard, console, Warden or bulkhead changed, or the player is somewhere
        // the last flood did not reach. The cell of a bulkhead the player leans on counts as reached.
        if (state != objectiveState || playerCell < 0 || !reachable[playerCell]) {
            objectiveState = state;
            String previous = objectiveText;
            objectiveText = computeObjectiveText(playerCell);
            // The text is worked out again more often than it changes, so only a new one is logged.
            if (!Objects.equals(previous, objectiveText)) log.debug("Objective changed to '{}'", objectiveText);
        }
        return objectiveText;
    }

    /** @return how many times the objective has been worked out afresh; a test reads it to pin the caching. */
    int getObjectiveFloodCount() {
        return objectiveFloodCount;
    }

    private int objectiveStateNow() {
        int state = 0;
        if (hasKeycard(Pickup.Kind.AMBER_KEYCARD)) state |= 1;
        if (hasKeycard(Pickup.Kind.CYAN_KEYCARD)) state |= 2;
        if (isAnyConsoleActivated()) state |= 4;
        if (hasAliveWarden()) state |= 8;
        int opened = 0;
        for (int i = 0; i < doors.size(); i++) {
            if (doors.get(i).isOpening()) opened++;
        }
        return state | (opened << 4);
    }

    private boolean isAnyConsoleActivated() {
        for (int i = 0; i < consoles.size(); i++) {
            if (consoles.get(i).isActivated()) return true;
        }
        return false;
    }

    private int cellIndexAt(float worldX, float worldZ) {
        int col = (int) Math.floor(worldX);
        int row = (int) Math.floor(worldZ);
        if (col < 0 || row < 0 || col >= map.getWidth() || row >= map.getHeight()) return -1;
        return row * map.getWidth() + col;
    }

    private boolean isReachableAt(float worldX, float worldZ) {
        int cell = cellIndexAt(worldX, worldZ);
        return cell >= 0 && reachable[cell];
    }

    /** @return whether the player can pass the door now or by pressing the interact key. */
    private boolean canPass(Door door) {
        if (door.isOpening()) return true;
        switch (door.getLockKind()) {
            case NONE:
                return true;
            case AMBER_KEY:
                return hasKeycard(Pickup.Kind.AMBER_KEYCARD);
            case CYAN_KEY:
                return hasKeycard(Pickup.Kind.CYAN_KEYCARD);
            default:
                return false;
        }
    }

    /** Marks in {@link #reachable} every cell the player can walk to from {@code startCell}. */
    private void floodReachable(int startCell) {
        Arrays.fill(reachable, false);
        if (startCell < 0) return;
        int width = map.getWidth();
        int head = 0;
        int tail = 0;
        reachable[startCell] = true;
        reachQueue[tail++] = startCell;
        while (head < tail) {
            int cell = reachQueue[head++];
            int col = cell % width;
            int row = cell / width;
            for (int side = 0; side < 4; side++) {
                int nextCol = col + (side == 0 ? 1 : side == 1 ? -1 : 0);
                int nextRow = row + (side == 2 ? 1 : side == 3 ? -1 : 0);
                if (map.isSolid(nextCol, nextRow)) continue;
                int next = nextRow * width + nextCol;
                if (reachable[next]) continue;
                Door door = doorAtCell[next];
                if (door != null && !canPass(door)) continue;
                reachable[next] = true;
                reachQueue[tail++] = next;
            }
        }
    }

    /**
     * Picks the cell to flood from. A player pressed against a closed bulkhead has their centre inside its cell,
     * because {@link Player#RADIUS} and {@link Door#HALF_THICKNESS} together are less than half a cell. Flooding
     * from there would run out on both sides of a bulkhead the player cannot pass, so the flood starts from the
     * cell on the player's side of the slab instead.
     * @return the cell to start from, or -1 if there is none
     */
    private int floodStartCell(int playerCell) {
        if (playerCell < 0) return -1;
        Door door = doorAtCell[playerCell];
        if (door == null || canPass(door)) return playerCell;
        int col = door.getCol();
        int row = door.getRow();
        if (door.getOrientation() == Door.Orientation.SPAN_X) {
            row += player.position.y < door.getZ() ? -1 : 1;
        } else {
            col += player.position.x < door.getX() ? -1 : 1;
        }
        if (map.isSolid(col, row)) return -1;
        int start = row * map.getWidth() + col;
        Door beside = doorAtCell[start];
        return beside != null && !canPass(beside) ? -1 : start;
    }

    private String computeObjectiveText(int playerCell) {
        objectiveFloodCount++;
        int startCell = floodStartCell(playerCell);
        floodReachable(startCell);
        // Leaning on a bulkhead must not flood again every frame, so its cell counts as reached too.
        if (startCell != playerCell) reachable[playerCell] = true;
        if (isReachableAt(map.getExitX(), map.getExitZ())) return OBJECTIVE_EXIT;
        // First the steps the player can act on from where they can walk, then any step still open.
        for (int pass = 0; pass < 2; pass++) {
            boolean mustReach = pass == 0;
            if (hasClosedDoor(Door.LockKind.AMBER_KEY) && isKeycardWaiting(Pickup.Kind.AMBER_KEYCARD, mustReach)) {
                return OBJECTIVE_AMBER_KEYCARD;
            }
            if (hasClosedDoor(Door.LockKind.CYAN_KEY) && isKeycardWaiting(Pickup.Kind.CYAN_KEYCARD, mustReach)) {
                return OBJECTIVE_CYAN_KEYCARD;
            }
            if (hasClosedDoor(Door.LockKind.LOCKDOWN) && isConsoleWaiting(mustReach)) return OBJECTIVE_CONSOLE;
            if (hasClosedDoor(Door.LockKind.BOSS) && isWardenWaiting(mustReach)) return OBJECTIVE_WARDEN;
        }
        return OBJECTIVE_EXIT;
    }

    private boolean hasClosedDoor(Door.LockKind kind) {
        for (int i = 0; i < doors.size(); i++) {
            Door door = doors.get(i);
            if (door.getLockKind() == kind && !door.isOpening()) return true;
        }
        return false;
    }

    private boolean isKeycardWaiting(Pickup.Kind kind, boolean mustReach) {
        if (hasKeycard(kind)) return false;
        for (int i = 0; i < pickups.size(); i++) {
            Pickup pickup = pickups.get(i);
            if (pickup.getKind() != kind || pickup.isCollected()) continue;
            if (!mustReach || isReachableAt(pickup.getX(), pickup.getZ())) return true;
        }
        return false;
    }

    private boolean isConsoleWaiting(boolean mustReach) {
        if (isAnyConsoleActivated()) return false;
        for (int i = 0; i < consoles.size(); i++) {
            SecurityConsole console = consoles.get(i);
            if (!mustReach || isReachableAt(console.getX(), console.getZ())) return true;
        }
        return false;
    }

    private boolean isWardenWaiting(boolean mustReach) {
        for (int i = 0; i < enemies.size(); i++) {
            Enemy enemy = enemies.get(i);
            if (enemy.isDestroyed() || enemy.getKind() != Enemy.Kind.WARDEN) continue;
            if (!mustReach || isReachableAt(enemy.getX(), enemy.getZ())) return true;
        }
        return false;
    }

    private void collectPickups() {
        for (int i = 0; i < pickups.size(); i++) {
            Pickup pickup = pickups.get(i);
            String message = pickup.tryCollect(player, energy, entryLoadout.difficulty.pickupScale,
                heldKeycards, pickupArsenal);
            if (message != null) {
                log.debug("Picked up {}: {}", pickup.getKind(), message);
                showToast(message);
                emitPlayerSound(pickupSoundFor(pickup.getKind()), 1f);
            }
        }
    }

    private void handleWeaponSwitchInput(int requestedSlot) {
        if (requestedSlot == PlayerInput.WEAPON_NONE) return;
        WeaponSlot current = getTargetWeapon();
        WeaponSlot target;
        if (requestedSlot == PlayerInput.WEAPON_QUICK_SWAP) {
            if (previousWeapon != null && previousWeapon != current
                && previousWeapon.isOwned(hasScattergun, hasRailLance)) {
                target = previousWeapon;
            } else {
                target = current.cycle(1, hasScattergun, hasRailLance);
            }
        } else if (requestedSlot == PlayerInput.WEAPON_CYCLE_NEXT) {
            target = current.cycle(1, hasScattergun, hasRailLance);
        } else if (requestedSlot == PlayerInput.WEAPON_CYCLE_PREV) {
            target = current.cycle(-1, hasScattergun, hasRailLance);
        } else if (requestedSlot == WeaponSlot.PISTOL.slotNumber) {
            target = WeaponSlot.PISTOL;
        } else if (requestedSlot == WeaponSlot.SCATTERGUN.slotNumber) {
            target = WeaponSlot.SCATTERGUN;
        } else if (requestedSlot == WeaponSlot.RAIL_LANCE.slotNumber) {
            target = WeaponSlot.RAIL_LANCE;
        } else {
            return;
        }
        requestWeaponSwitch(target, true);
    }

    private void requestWeaponSwitch(WeaponSlot target, boolean playSound) {
        if (target == null || !target.isOwned(hasScattergun, hasRailLance)) return;
        WeaponSlot currentTarget = getTargetWeapon();
        if (target == currentTarget) return;
        previousWeapon = currentTarget;
        charger.cancel();
        railLance.cancelZoom();
        prevChargeRefused = false;
        pendingWeapon = target;
        if (!swapping) {
            swapping = true;
            swapOrigin = activeWeapon;
            swapElapsed = 0f;
        } else if (target == swapOrigin) {
            // Reversing: the timer is mirrored, so the viewmodel turns back from the depth it has reached.
            swapOrigin = currentTarget;
            swapElapsed = Math.max(0f, SWAP_DURATION - swapElapsed);
        } else {
            // A third weapon: the dip starts over, so it is never raised sooner than by a direct switch.
            swapOrigin = activeWeapon;
            swapElapsed = 0f;
        }
        if (playSound) {
            emitPlayerSound(SoundEvent.Type.WEAPON_SWITCH, 1f);
        }
    }

    private void smartSwapFromEmptyWeapon(WeaponSlot emptySlot) {
        WeaponSlot fallback = bestLoadedFallbackWeapon(emptySlot);
        requestWeaponSwitch(fallback, false);
        previousWeapon = loadedFallbackOtherThan(fallback);
    }

    private WeaponSlot loadedFallbackOtherThan(WeaponSlot current) {
        if (current != WeaponSlot.PISTOL) return WeaponSlot.PISTOL;
        if (hasRailLance && slugs > 0) return WeaponSlot.RAIL_LANCE;
        if (hasScattergun && shells > 0) return WeaponSlot.SCATTERGUN;
        return WeaponSlot.PISTOL;
    }

    private WeaponSlot bestLoadedFallbackWeapon(WeaponSlot emptySlot) {
        if (previousWeapon != null && previousWeapon != emptySlot && isWeaponLoaded(previousWeapon)) {
            return previousWeapon;
        }
        if (emptySlot != WeaponSlot.SCATTERGUN && isWeaponLoaded(WeaponSlot.SCATTERGUN)) {
            return WeaponSlot.SCATTERGUN;
        }
        if (emptySlot != WeaponSlot.RAIL_LANCE && isWeaponLoaded(WeaponSlot.RAIL_LANCE)) {
            return WeaponSlot.RAIL_LANCE;
        }
        return WeaponSlot.PISTOL;
    }

    private boolean isWeaponLoaded(WeaponSlot slot) {
        if (slot == null || !slot.isOwned(hasScattergun, hasRailLance)) return false;
        if (slot == WeaponSlot.SCATTERGUN) return shells > 0;
        if (slot == WeaponSlot.RAIL_LANCE) return slugs > 0;
        return true;
    }

    private static WeaponSlot initialPreviousWeapon(WeaponSlot active, boolean hasScattergun, int shells,
                                                    boolean hasRailLance, int slugs) {
        if (active != WeaponSlot.PISTOL) return WeaponSlot.PISTOL;
        if (hasRailLance && slugs > 0) return WeaponSlot.RAIL_LANCE;
        if (hasScattergun && shells > 0) return WeaponSlot.SCATTERGUN;
        if (hasRailLance) return WeaponSlot.RAIL_LANCE;
        if (hasScattergun) return WeaponSlot.SCATTERGUN;
        return WeaponSlot.PISTOL;
    }

    private void advanceWeaponSwap(float dt) {
        if (!swapping) return;
        swapElapsed += dt;
        if (swapElapsed >= SWAP_MIDPOINT) {
            if (activeWeapon != pendingWeapon) {
                activeWeapon = pendingWeapon;
                viewmodel.resetShot();
            }
        }
        if (swapElapsed >= SWAP_DURATION) {
            swapping = false;
            swapElapsed = 0f;
        }
    }

    private void showToast(String message) {
        toastMessage = message;
        toastTimer = TOAST_TIME;
    }

    private Object findInteractTarget() {
        Door door = findInteractDoor(Door.INTERACT_RADIUS);
        SecurityConsole console = findInteractConsole(SecurityConsole.INTERACT_RADIUS);
        if (door == null) return console;
        if (console == null) return door;
        float consoleDist2 = Vector2.dst2(player.position.x, player.position.y, console.getX(), console.getZ());
        float doorDist2 = Vector2.dst2(player.position.x, player.position.y, door.getX(), door.getZ());
        return consoleDist2 <= doorDist2 ? console : door;
    }

    private void handleInteract() {
        Object target = findInteractTarget();
        if (target instanceof SecurityConsole) {
            SecurityConsole console = (SecurityConsole) target;
            if (!console.activate()) return;
            log.debug("Security console activated at ({}, {}); lockdown bulkheads open", console.getX(),
                console.getZ());
            for (int i = 0; i < doors.size(); i++) {
                Door candidate = doors.get(i);
                if (candidate.getLockKind() == Door.LockKind.LOCKDOWN && candidate.open()) {
                    emitWorldSound(SoundEvent.Type.DOOR_OPEN, candidate.getX(), 0.5f, candidate.getZ(), 1f);
                }
            }
            showToast("LOCKDOWN OVERRIDDEN");
            emitWorldSound(SoundEvent.Type.CONSOLE_ACTIVATE, console.getX(), SecurityConsole.HEIGHT * 0.8f,
                console.getZ(), 1f);
            alertNoise(console.getX(), console.getZ(), 12.0f, 7.0f);
            return;
        }
        if (!(target instanceof Door)) return;
        Door door = (Door) target;
        switch (door.getLockKind()) {
            case NONE:
                if (door.open()) {
                    emitWorldSound(SoundEvent.Type.DOOR_OPEN, door.getX(), 0.5f, door.getZ(), 1f);
                }
                break;
            case AMBER_KEY:
                if (hasKeycard(Pickup.Kind.AMBER_KEYCARD)) {
                    if (door.open()) {
                        showToast("AMBER BULKHEAD UNLOCKED");
                        emitWorldSound(SoundEvent.Type.DOOR_OPEN, door.getX(), 0.5f, door.getZ(), 1f);
                    }
                } else {
                    showToast("NEEDS AMBER KEYCARD");
                    emitWorldSound(SoundEvent.Type.DOOR_LOCKED, door.getX(), 0.5f, door.getZ(), 1f);
                }
                break;
            case CYAN_KEY:
                if (hasKeycard(Pickup.Kind.CYAN_KEYCARD)) {
                    if (door.open()) {
                        showToast("CYAN BULKHEAD UNLOCKED");
                        emitWorldSound(SoundEvent.Type.DOOR_OPEN, door.getX(), 0.5f, door.getZ(), 1f);
                    }
                } else {
                    showToast("NEEDS CYAN KEYCARD");
                    emitWorldSound(SoundEvent.Type.DOOR_LOCKED, door.getX(), 0.5f, door.getZ(), 1f);
                }
                break;
            case BOSS:
                showToast("SEALED BY THE WARDEN");
                emitWorldSound(SoundEvent.Type.DOOR_LOCKED, door.getX(), 0.5f, door.getZ(), 1f);
                break;
            case LOCKDOWN:
            default:
                showToast("SEALED BY SECURITY CONSOLE");
                emitWorldSound(SoundEvent.Type.DOOR_LOCKED, door.getX(), 0.5f, door.getZ(), 1f);
                break;
        }
    }

    private Door findInteractDoor(float reach) {
        double yawRad = Math.toRadians(player.getYaw());
        float fx = (float) Math.sin(yawRad);
        float fz = (float) -Math.cos(yawRad);
        float maxDist2 = reach * reach;
        Door best = null;
        float bestDist2 = maxDist2;
        for (int i = 0; i < doors.size(); i++) {
            Door door = doors.get(i);
            if (door.isOpening()) continue;
            float dx = door.getX() - player.position.x;
            float dz = door.getZ() - player.position.y;
            float dist2 = dx * dx + dz * dz;
            if (dist2 > bestDist2 || dist2 < 1e-6f) continue;
            float dist = (float) Math.sqrt(dist2);
            if ((fx * dx + fz * dz) / dist < 0.2f) continue;
            if (!Hitscan.hasLineOfSight(map, player.position.x, player.position.y, door.getX(), door.getZ())) {
                continue;
            }
            boolean blockedByOtherDoor = false;
            for (int j = 0; j < doors.size(); j++) {
                Door other = doors.get(j);
                if (other != door && other.intersectsSegment2D(player.position.x, player.position.y,
                    door.getX(), door.getZ())) {
                    blockedByOtherDoor = true;
                    break;
                }
            }
            if (!blockedByOtherDoor) {
                bestDist2 = dist2;
                best = door;
            }
        }
        return best;
    }

    private SecurityConsole findInteractConsole(float reach) {
        double yawRad = Math.toRadians(player.getYaw());
        float fx = (float) Math.sin(yawRad);
        float fz = (float) -Math.cos(yawRad);
        float maxDist2 = reach * reach;
        SecurityConsole best = null;
        float bestDist2 = maxDist2;
        for (int i = 0; i < consoles.size(); i++) {
            SecurityConsole console = consoles.get(i);
            if (console.isActivated()) continue;
            float dx = console.getX() - player.position.x;
            float dz = console.getZ() - player.position.y;
            float dist2 = dx * dx + dz * dz;
            if (dist2 > bestDist2 || dist2 < 1e-6f) continue;
            float dist = (float) Math.sqrt(dist2);
            if ((fx * dx + fz * dz) / dist < 0.2f) continue;
            if (Hitscan.hasLineOfSight(map, doors, player.position.x, player.position.y,
                console.getX(), console.getZ())) {
                bestDist2 = dist2;
                best = console;
            }
        }
        return best;
    }

    private static SoundEvent.Type pickupSoundFor(Pickup.Kind kind) {
        switch (kind) {
            case MEDKIT:
                return SoundEvent.Type.PICKUP_HEALTH;
            case ARMOR:
                return SoundEvent.Type.PICKUP_ARMOR;
            case AMBER_KEYCARD:
            case CYAN_KEYCARD:
                return SoundEvent.Type.PICKUP_KEYCARD;
            case SCATTERGUN:
            case RAIL_LANCE:
                return SoundEvent.Type.PICKUP_WEAPON;
            case SHELLS:
                return SoundEvent.Type.PICKUP_SHELLS;
            case RAIL_SLUGS:
                return SoundEvent.Type.PICKUP_SLUGS;
            case ENERGY_CELL:
            default:
                return SoundEvent.Type.PICKUP_ENERGY;
        }
    }

    private boolean isExitReached() {
        if (!map.hasExit() || player.isDead()) return false;
        float reach = Player.RADIUS + GameMap.EXIT_RADIUS;
        return Vector2.dst2(player.position.x, player.position.y, map.getExitX(), map.getExitZ()) < reach * reach;
    }

    private void computeMuzzleOrigin(float hitDistance, float rightScale, float clearanceRadius, Vector3 out) {
        viewRight.set(direction).crs(Vector3.Y);
        if (viewRight.len2() < 1e-6f) viewRight.set(1f, 0f, 0f);
        else viewRight.nor();
        viewUp.set(viewRight).crs(direction).nor();

        float clearance = Float.isFinite(hitDistance) ? Math.max(0f, (hitDistance - 0.20f) / 0.55f) : 1f;
        float t = Math.min(1f, clearance);
        out.set(eye)
            .mulAdd(direction, BLAST_SPAWN_FORWARD * t)
            .mulAdd(viewRight, BLAST_SPAWN_RIGHT * rightScale * t)
            .mulAdd(viewUp, -BLAST_SPAWN_DOWN * t);
        float minY = clearanceRadius + 0.01f;
        float maxY = GameMap.WALL_HEIGHT - clearanceRadius - 0.01f;
        out.y = Math.max(minY, Math.min(maxY, out.y));

        if (muzzleOverlaps3D(out.x, out.y, out.z, clearanceRadius)) {
            out.set(eye.x, Math.max(minY, Math.min(maxY, eye.y - 0.05f)), eye.z);
        }
    }

    private boolean muzzleOverlaps3D(float x, float y, float z, float radius) {
        if (y - radius <= 0f || y + radius >= GameMap.WALL_HEIGHT) return true;
        if (Collision.overlapsWall(map, x, z, radius)) return true;
        for (int i = 0; i < doors.size(); i++) {
            if (doors.get(i).overlapsSphere(x, y, z, radius)) return true;
        }
        for (int i = 0; i < allDamageables.size(); i++) {
            Damageable solid = allDamageables.get(i);
            if (solid.isDestroyed()) continue;
            if (y + radius < 0f || y - radius > solid.getHeight()) continue;
            float dx = solid.getX() - x;
            float dz = solid.getZ() - z;
            float reach = solid.getRadius() + radius;
            if (dx * dx + dz * dz < reach * reach) return true;
        }
        return false;
    }

    private void spawnBlast(float charge) {
        RayHit aim = Hitscan.cast(map, allDamageables, doors, eye, direction, Pistol.RANGE);
        if (aim.isHit()) {
            aimPoint.set(aim.point);
        } else {
            aimPoint.set(eye).mulAdd(direction, Pistol.RANGE);
        }

        computeMuzzleOrigin(aim.isHit() ? aim.distance : Float.POSITIVE_INFINITY, 1f,
            BlastProjectile.RADIUS + 0.01f, blastOrigin);

        blastDir.set(aimPoint).sub(blastOrigin);
        if (blastDir.len2() < 1e-6f) blastDir.set(direction);
        else blastDir.nor();
        projectiles.add(new BlastProjectile(blastOrigin, blastDir, charge));
        shotsFired++;
        emitPlayerSound(SoundEvent.Type.BLAST_RELEASE, 0.7f + 0.3f * charge);
    }

    private void firePistol() {
        shotsFired++;
        emitPlayerSound(SoundEvent.Type.PISTOL_SHOT, 1f);
        alertNoise(player.position.x, player.position.y, 9.0f, 4.5f);
        RayHit hit = Hitscan.cast(map, allDamageables, doors, eye, direction, Pistol.RANGE);
        if (!hit.isHit()) return;
        hit.point.mulAdd(hit.normal, MARK_OFFSET);
        if (applyNonDamageableImpact(hit, true, true)) return;
        hit.target.damage(Pistol.DAMAGE);
        shotsHit++;
        if (hit.target instanceof Enemy) {
            alertNoise(hit.target.getX(), hit.target.getZ(), 5.0f, 3.0f);
        }
        effects.add(new Effect(Effect.Type.SPARK, hit.point, hit.normal, 0.2f));
        triggerHitMarker(hit.target.isDestroyed());
    }

    private void fireRailLance() {
        shotsFired++;
        slugs = Math.max(0, slugs - 1);
        emitPlayerSound(SoundEvent.Type.RAIL_FIRE, 1f);
        alertNoise(player.position.x, player.position.y, 15.0f, 7.5f);

        Hitscan.PiercingResult result = Hitscan.castPiercing(map, allDamageables, doors, eye, direction,
            RailLance.RANGE);
        boolean anyHit = false;
        boolean anyKilled = false;
        for (int i = 0; i < result.targetHits.size(); i++) {
            RayHit hit = result.targetHits.get(i);
            float dmg = (hit.target instanceof Enemy && ((Enemy) hit.target).getKind() == Enemy.Kind.WARDEN)
                ? RailLance.WARDEN_DAMAGE
                : RailLance.DAMAGE;
            hit.target.damage(dmg);
            anyHit = true;
            if (hit.target.isDestroyed()) anyKilled = true;
            if (hit.target instanceof Enemy) {
                alertNoise(hit.target.getX(), hit.target.getZ(), 7.0f, 4.0f);
            }
            Vector3 sparkPos = new Vector3(hit.point).mulAdd(hit.normal, MARK_OFFSET);
            effects.add(new Effect(Effect.Type.SPARK, sparkPos, hit.normal, 0.24f));
        }
        if (anyHit) {
            // One beam is one hit, however many it pierces.
            shotsHit++;
            triggerHitMarker(anyKilled);
        }

        if (result.terminalHit.isHit()) {
            result.terminalHit.point.mulAdd(result.terminalHit.normal, MARK_OFFSET);
            applyNonDamageableImpact(result.terminalHit, true, true);
        }

        computeMuzzleOrigin(result.stopDistance, 1f - railLance.getZoom(), 0.03f, blastOrigin);
        railBeams.add(new RailBeam(blastOrigin, result.endPoint));
    }

    private void fireScattergun(Scattergun.Shot shot) {
        boolean doubleBlast = shot == Scattergun.Shot.DOUBLE_BLAST;
        shotsFired++;
        shells = Math.max(0, shells - shot.shellsSpent);
        emitPlayerSound(doubleBlast ? SoundEvent.Type.SCATTERGUN_ALT_FIRE : SoundEvent.Type.SCATTERGUN_FIRE, 1f);
        alertNoise(player.position.x, player.position.y, doubleBlast ? 13.0f : 11.0f, doubleBlast ? 6.5f : 5.5f);

        List<Vector3> rays = Scattergun.pelletDirections(direction, doubleBlast);
        boolean anyHit = false;
        boolean anyKilled = false;
        boolean wallSoundPlayed = false;
        int wallEffects = 0;
        for (int i = 0; i < rays.size(); i++) {
            RayHit hit = Hitscan.cast(map, allDamageables, doors, eye, rays.get(i), Scattergun.RANGE);
            if (!hit.isHit()) continue;
            hit.point.mulAdd(hit.normal, MARK_OFFSET);
            boolean nonDamageable = hit.kind != RayHit.Kind.TARGET || hit.target instanceof SecurityConsole;
            if (nonDamageable) {
                boolean emitWallAudio = !wallSoundPlayed;
                boolean spawnDecal = wallEffects < 8;
                applyNonDamageableImpact(hit, spawnDecal, emitWallAudio);
                if (spawnDecal) wallEffects++;
                wallSoundPlayed = true;
                continue;
            }
            hit.target.damage(Scattergun.damageAtDistance(hit.distance));
            anyHit = true;
            if (hit.target.isDestroyed()) anyKilled = true;
            if (hit.target instanceof Enemy) {
                alertNoise(hit.target.getX(), hit.target.getZ(), 5.0f, 3.0f);
            }
            effects.add(new Effect(Effect.Type.SPARK, hit.point, hit.normal, 0.2f));
        }
        if (anyHit) {
            // One blast is one hit, however many pellets land.
            shotsHit++;
            triggerHitMarker(anyKilled);
        }
    }

    private boolean applyNonDamageableImpact(RayHit hit, boolean spawnEffect, boolean emitSound) {
        if (hit.kind == RayHit.Kind.DOOR || hit.target instanceof SecurityConsole) {
            if (spawnEffect) effects.add(new Effect(Effect.Type.SPARK, hit.point, hit.normal, 0.16f));
            if (emitSound) emitWorldSound(SoundEvent.Type.WALL_IMPACT, hit.point.x, hit.point.y, hit.point.z, 1f);
            return true;
        }
        if (hit.kind != RayHit.Kind.TARGET) {
            if (spawnEffect) effects.add(new Effect(Effect.Type.IMPACT, hit.point, hit.normal, 0.11f));
            if (emitSound) emitWorldSound(SoundEvent.Type.WALL_IMPACT, hit.point.x, hit.point.y, hit.point.z, 1f);
            return true;
        }
        return false;
    }

    private void resolveDestroyedObjects(boolean allowPlayerHitMarker) {
        float canisterPlayerScale = CANISTER_SELF_DAMAGE_SCALE * entryLoadout.difficulty.damageTakenScale;
        boolean progressed = true;
        while (progressed) {
            progressed = false;
            for (int i = 0; i < targets.size(); i++) {
                if (breakHandled[i] || !targets.get(i).isDestroyed()) continue;
                breakHandled[i] = true;
                progressed = true;
                if (allowPlayerHitMarker) {
                    triggerHitMarker(true);
                }
                Target target = targets.get(i);
                log.debug("Destroyed target {} at ({}, {}); {} left", target.getKind(), target.getX(), target.getZ(),
                    getTargetsRemaining());
                if (target.getKind() == Target.Kind.CANISTER) {
                    Vector3 centre = new Vector3(target.getX(), target.getHeight() * 0.5f, target.getZ());
                    effects.add(new Effect(Effect.Type.EXPLOSION, centre, Vector3.Y, CANISTER_BLAST_RADIUS));
                    Explosion.apply(map, doors, target.getX(), target.getHeight() * 0.5f, target.getZ(),
                        CANISTER_BLAST_RADIUS, CANISTER_BLAST_DAMAGE, allDamageables, player, canisterPlayerScale);
                    emitWorldSound(SoundEvent.Type.EXPLOSION, centre.x, centre.y, centre.z, 1.0f);
                    alertNoise(target.getX(), target.getZ(), 11.0f, 5.5f);
                } else {
                    addBreakEffect(target);
                    emitWorldSound(SoundEvent.Type.TARGET_DESTROYED, target.getX(), target.getHeight() * 0.5f,
                        target.getZ(), 1.0f);
                }
            }
            for (int i = 0; i < enemies.size(); i++) {
                if (enemyBreakHandled[i] || !enemies.get(i).isDestroyed()) continue;
                enemyBreakHandled[i] = true;
                progressed = true;
                if (allowPlayerHitMarker) {
                    triggerHitMarker(true);
                }
                Enemy enemy = enemies.get(i);
                Vector3 centre = new Vector3(enemy.getX(), enemy.getHeight() * 0.5f, enemy.getZ());
                boolean isBoss = enemy.getKind() == Enemy.Kind.WARDEN;
                if (isBoss) {
                    log.info("Boss destroyed: {} at ({}, {}) after {} s", enemy.getKind(), enemy.getX(), enemy.getZ(),
                        roundedTime());
                } else {
                    log.debug("Killed {} at ({}, {}); {} hostiles left", enemy.getKind(), enemy.getX(), enemy.getZ(),
                        getEnemiesRemaining());
                }
                effects.add(new Effect(Effect.Type.EXPLOSION, centre, Vector3.Y,
                    isBoss ? 1.8f : enemy.getHeight() * 0.8f));
                emitWorldSound(isBoss ? SoundEvent.Type.BOSS_DESTROYED : SoundEvent.Type.ENEMY_DESTROYED,
                    centre.x, centre.y, centre.z, isBoss ? 1.25f : 1.0f);
                alertNoise(enemy.getX(), enemy.getZ(), isBoss ? 10.0f : 5.0f, isBoss ? 6.0f : 3.0f);
                if (isBoss && !hasAliveWarden()) {
                    unsealBossDoors();
                }
            }
        }
    }

    private void updateHazards(float dt) {
        playerHazardTimer = Math.max(0f, playerHazardTimer - dt);
        if (!player.isDead() && !player.isDashing() && player.getJumpHeight() <= HAZARD_CLEARANCE_HEIGHT
            && map.isHazardAt(player.position.x, player.position.y) && playerHazardTimer <= 0f) {
            float hx = (float) Math.floor(player.position.x) + 0.5f;
            float hz = (float) Math.floor(player.position.y) + 0.5f;
            player.damage(HAZARD_DAMAGE * entryLoadout.difficulty.damageTakenScale, hx, hz);
            playerHazardTimer = HAZARD_INTERVAL;
            emitPlayerSound(SoundEvent.Type.HAZARD_ZAP, 0.9f);
            effects.add(new Effect(Effect.Type.IMPACT,
                new Vector3(player.position.x, 0.06f, player.position.y), Vector3.Y, 0.24f));
        }

        if (map.getHazardCells().isEmpty()) return;
        for (int i = 0; i < enemies.size(); i++) {
            Enemy enemy = enemies.get(i);
            if (enemy.isDestroyed() || enemy.getKind() != Enemy.Kind.GUARD) continue;
            enemyHazardTimers[i] = Math.max(0f, enemyHazardTimers[i] - dt);
            if (map.isHazardAt(enemy.getX(), enemy.getZ()) && enemyHazardTimers[i] <= 0f) {
                enemy.damage(HAZARD_DAMAGE);
                enemyHazardTimers[i] = HAZARD_INTERVAL;
                emitWorldSound(SoundEvent.Type.HAZARD_ZAP, enemy.getX(), 0.14f, enemy.getZ(), 0.75f);
                effects.add(new Effect(Effect.Type.IMPACT,
                    new Vector3(enemy.getX(), 0.05f, enemy.getZ()), Vector3.Y, 0.20f));
            }
        }
    }

    private boolean hasAliveGuard() {
        for (int i = 0; i < enemies.size(); i++) {
            Enemy e = enemies.get(i);
            if (!e.isDestroyed() && e.getKind() == Enemy.Kind.GUARD) return true;
        }
        return false;
    }

    private boolean hasAliveWarden() {
        for (int i = 0; i < enemies.size(); i++) {
            Enemy e = enemies.get(i);
            if (!e.isDestroyed() && e.getKind() == Enemy.Kind.WARDEN) return true;
        }
        return false;
    }

    private void unsealBossDoors() {
        for (int i = 0; i < doors.size(); i++) {
            Door candidate = doors.get(i);
            if (candidate.getLockKind() == Door.LockKind.BOSS && candidate.open()) {
                emitWorldSound(SoundEvent.Type.DOOR_OPEN, candidate.getX(), 0.5f, candidate.getZ(), 1f);
            }
        }
        showToast("WARDEN DESTROYED - EXTRACTION UNSEALED");
    }

    private void snapshotDamageableHealth() {
        for (int i = 0; i < targets.size(); i++) targetHealthBefore[i] = targets.get(i).getHealth();
        for (int i = 0; i < enemies.size(); i++) enemyHealthBefore[i] = enemies.get(i).getHealth();
    }

    private void emitNonLethalHitSounds() {
        for (int i = 0; i < targets.size(); i++) {
            Target target = targets.get(i);
            if (!target.isDestroyed() && target.getHealth() < targetHealthBefore[i]) {
                emitWorldSound(SoundEvent.Type.TARGET_HIT, target.getX(), target.getHeight() * 0.5f, target.getZ(), 1f);
            }
        }
        for (int i = 0; i < enemies.size(); i++) {
            Enemy enemy = enemies.get(i);
            if (!enemy.isDestroyed() && enemy.getHealth() < enemyHealthBefore[i]) {
                emitWorldSound(SoundEvent.Type.ENEMY_HIT, enemy.getX(), enemy.getHeight() * 0.5f, enemy.getZ(), 1f);
            }
        }
    }

    /** @return the play time in seconds to two decimal places, for the log. */
    private float roundedTime() {
        return Math.round(time * 100f) / 100f;
    }

    private void emitPlayerSound(SoundEvent.Type type, float intensity) {
        addSoundEvent(SoundEvent.player(type, intensity));
    }

    private void emitWorldSound(SoundEvent.Type type, float x, float y, float z, float intensity) {
        addSoundEvent(SoundEvent.world(type, x, y, z, intensity));
    }

    private void addSoundEvent(SoundEvent event) {
        if (soundEvents.size() < MAX_QUEUED_SOUNDS) {
            soundEvents.add(event);
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
            } else if (dist2 <= losRange2 && Hitscan.hasLineOfSight(map, doors, x, z, enemy.getX(), enemy.getZ())) {
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
            enemies.get(i).update(dt, map, null, null, entryLoadout.difficulty, allDamageables, doors, null);
        }
        for (int i = enemyProjectiles.size() - 1; i >= 0; i--) {
            if (enemyProjectiles.get(i).update(dt, map, null, 0f,
                Collections.emptyList(), enemies, doors, consoles, effects)) {
                enemyProjectiles.remove(i);
            }
        }
    }

    private void updateTargetsAndEffects(float dt) {
        for (int i = 0; i < doors.size(); i++) doors.get(i).update(dt);
        for (int i = 0; i < targets.size(); i++) targets.get(i).update(dt);
        for (int i = effects.size() - 1; i >= 0; i--) {
            Effect effect = effects.get(i);
            effect.update(dt);
            if (effect.isFinished()) effects.remove(i);
        }
        for (int i = railBeams.size() - 1; i >= 0; i--) {
            RailBeam beam = railBeams.get(i);
            beam.update(dt);
            if (beam.isFinished()) railBeams.remove(i);
        }
    }
}

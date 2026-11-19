package io.github.brantunger.gamex.world;

import io.github.brantunger.gamex.data.TuningFile;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

/**
 * The enemy balance read from {@code assets/data/enemies.txt}, with the Warden's fight from
 * {@code assets/data/warden.txt}: a block of values for each {@link Enemy.Kind}, the perception every kind shares
 * and the attack of the turret, the guard and the drone. The game loads it once at start-up and hands it to every
 * {@link Enemy}. Distances are in map cells, times in seconds, angles in degrees and turn rates in degrees per
 * second. Instances are immutable.
 */
public final class EnemyTuning {

    /** The file inside {@code assets/} that {@link #load()} reads. */
    public static final String PATH = "data/enemies.txt";

    private static final String[] KIND_KEYS = {
        "health", "move_speed", "rail_damage_scale", "hazard_damage", "avoids_hazards", "boss", "death_burst_size",
        "death_sound_gain", "death_noise_range", "death_noise_muffled_range",
    };

    private static final String[] SHARED_KEYS = {
        "perception.hit_flash_time", "perception.sight_range", "perception.close_sense_range",
        "perception.fov_degrees", "perception.aggro_memory_time", "perception.aggro_sight_scale",
        "perception.wake_cooldown",
        "turret.turn_rate", "turret.windup_time", "turret.burst_count", "turret.burst_interval", "turret.cooldown",
        "turret.bolt_speed", "turret.bolt_damage", "turret.max_pitch", "turret.pitch_rate",
        "turret.pitch_return_rate", "turret.lost_sight_cooldown", "turret.fire_arc",
        "guard.windup_time", "guard.cooldown", "guard.bolt_speed", "guard.bolt_damage", "guard.min_range",
        "guard.max_range", "guard.preferred_range", "guard.range_adjust_gain", "guard.attack_range",
        "guard.advance_strafe", "guard.retreat_back", "guard.retreat_strafe", "guard.strafe_switch_time",
        "guard.windup_move_scale", "guard.turn_rate", "guard.path_turn_rate", "guard.walk_phase_rate",
        "guard.fire_arc", "guard.bolt_max_pitch",
        "drone.trigger_range", "drone.windup_time", "drone.lunge_time", "drone.lunge_speed", "drone.lunge_damage",
        "drone.contact_margin", "drone.recover_time", "drone.cooldown", "drone.turn_rate",
    };

    /** The values every kind has, one set per {@link Enemy.Kind}. */
    public static final class KindStats {
        /** Hit points. */
        public final float maxHealth;
        /** Cells per second; for the Warden its walk (see {@link WardenTuning#runSpeed}). */
        public final float moveSpeed;
        /** The share of the Rail Lance's damage a slug does to this kind. */
        public final float railDamageScale;
        /** Whether a shock grate under this kind hurts it. */
        public final boolean hazardDamage;
        /**
         * Whether this kind routes and steps round shock grates. Only the guard's movement does so, so the file
         * may say true for the guard alone.
         */
        public final boolean avoidsHazards;
        /** Whether this kind has the boss health bar and death sound, and holds the boss doors sealed. */
        public final boolean boss;
        /** The size of the explosion this kind leaves. */
        public final float deathBurstSize;
        /** The volume of this kind's death sound. */
        public final float deathSoundGain;
        /** This kind's death wakes enemies this near that have a line of sight to it. */
        public final float deathNoiseRange;
        /** This kind's death wakes enemies this near through walls. */
        public final float deathNoiseMuffledRange;

        private KindStats(TuningFile file, String prefix) {
            maxHealth = positive(file, prefix + "health");
            moveSpeed = nonNegative(file, prefix + "move_speed");
            railDamageScale = nonNegative(file, prefix + "rail_damage_scale");
            hazardDamage = file.getBoolean(prefix + "hazard_damage");
            avoidsHazards = file.getBoolean(prefix + "avoids_hazards");
            boss = file.getBoolean(prefix + "boss");
            deathBurstSize = nonNegative(file, prefix + "death_burst_size");
            deathSoundGain = nonNegative(file, prefix + "death_sound_gain");
            deathNoiseRange = nonNegative(file, prefix + "death_noise_range");
            deathNoiseMuffledRange = nonNegative(file, prefix + "death_noise_muffled_range");
        }
    }

    private final KindStats[] kinds;

    /** How long a hit keeps an enemy flashing. */
    public final float hitFlashTime;
    /** A dormant enemy notices a player in its line of sight and its forward cone within this distance. */
    public final float sightRange;
    /** A dormant enemy notices a player in its line of sight within this distance, whichever way it faces. */
    public final float closeSenseRange;
    /** The whole angle of the forward detection cone. */
    public final float fovDegrees;
    /** The cosine of half of {@link #fovDegrees}, worked out from it. */
    public final float fovCos;
    /** An alerted enemy that has not seen the player for this long goes dormant again. */
    public final float aggroMemoryTime;
    /** An alerted enemy still counts the player as seen out to {@link #sightRange} times this. */
    public final float aggroSightScale;
    /** The shortest wait between waking up and the first attack. */
    public final float wakeCooldown;

    public final float turretTurnRate;
    public final float turretWindupTime;
    public final int turretBurstCount;
    public final float turretBurstInterval;
    public final float turretCooldown;
    public final float turretBoltSpeed;
    public final float turretBoltDamage;
    /** The turret's barrels pitch up or down by at most this. */
    public final float turretMaxPitch;
    /** How fast the turret's barrels pitch toward a player in sight. */
    public final float turretPitchRate;
    /** How fast the turret's barrels return to level without a player in sight. */
    public final float turretPitchReturnRate;
    /** The wait before a turret winds up again after losing sight of the player during a wind-up. */
    public final float turretLostSightCooldown;
    /** A turret starts to wind up once it faces the player within this angle. */
    public final float turretFireArc;

    public final float guardWindupTime;
    public final float guardCooldown;
    public final float guardBoltSpeed;
    public final float guardBoltDamage;
    /** A guard backs off from a player nearer than this. */
    public final float guardMinRange;
    /** A guard closes in on a player further than this. */
    public final float guardMaxRange;
    /** The distance a circling guard drifts toward. */
    public final float guardPreferredRange;
    /** How strongly a circling guard drifts toward {@link #guardPreferredRange}, per cell it is off. */
    public final float guardRangeAdjustGain;
    public final float guardAttackRange;
    /** How much a guard strafes for each unit it advances. */
    public final float guardAdvanceStrafe;
    /** How much a guard that is too close backs off. */
    public final float guardRetreatBack;
    /** How much a guard that is too close strafes. */
    public final float guardRetreatStrafe;
    /** How often a guard changes the side it strafes to. */
    public final float guardStrafeSwitchTime;
    /** The share of its speed a guard keeps while it winds up a shot. */
    public final float guardWindupMoveScale;
    /** How fast a guard turns to a player in sight. */
    public final float guardTurnRate;
    /** How fast a guard turns along its route to a player out of sight. */
    public final float guardPathTurnRate;
    /** How far a guard's walk animation advances per cell walked. */
    public final float guardWalkPhaseRate;
    /** A guard fires once it faces the player within this angle, and its bolt leaves within it. */
    public final float guardFireArc;
    /** A guard's bolt leaves within this angle of level. */
    public final float guardBoltMaxPitch;

    public final float droneTriggerRange;
    public final float droneWindupTime;
    public final float droneLungeTime;
    public final float droneLungeSpeed;
    public final float droneLungeDamage;
    /** A drone's lunge hits a player within this distance beyond touching. */
    public final float droneContactMargin;
    public final float droneRecoverTime;
    /** The wait from the end of a drone's recovery to its next lunge. */
    public final float droneCooldown;
    public final float droneTurnRate;

    /** The Warden's fight. */
    public final WardenTuning warden;

    private EnemyTuning(TuningFile file, WardenTuning warden) {
        if (warden == null) throw new IllegalArgumentException("Warden tuning must not be null");
        this.warden = warden;
        Enemy.Kind[] all = Enemy.Kind.values();
        kinds = new KindStats[all.length];
        for (Enemy.Kind kind : all) {
            kinds[kind.ordinal()] = new KindStats(file, prefix(kind));
            if (kind != Enemy.Kind.GUARD && kinds[kind.ordinal()].avoidsHazards) {
                throw file.invalid(prefix(kind) + "avoids_hazards",
                    "must be false: only the guard steps round shock grates");
            }
        }

        hitFlashTime = positive(file, "perception.hit_flash_time");
        sightRange = nonNegative(file, "perception.sight_range");
        closeSenseRange = nonNegative(file, "perception.close_sense_range");
        fovDegrees = file.getFloat("perception.fov_degrees");
        if (fovDegrees <= 0f || fovDegrees > 360f) {
            throw file.invalid("perception.fov_degrees", "must be above 0 and at most 360");
        }
        fovCos = (float) Math.cos(Math.toRadians(fovDegrees * 0.5));
        aggroMemoryTime = nonNegative(file, "perception.aggro_memory_time");
        aggroSightScale = nonNegative(file, "perception.aggro_sight_scale");
        wakeCooldown = nonNegative(file, "perception.wake_cooldown");

        turretTurnRate = nonNegative(file, "turret.turn_rate");
        turretWindupTime = nonNegative(file, "turret.windup_time");
        turretBurstCount = file.getInt("turret.burst_count");
        if (turretBurstCount < 1) throw file.invalid("turret.burst_count", "must be at least 1");
        turretBurstInterval = positive(file, "turret.burst_interval");
        turretCooldown = nonNegative(file, "turret.cooldown");
        turretBoltSpeed = positive(file, "turret.bolt_speed");
        turretBoltDamage = nonNegative(file, "turret.bolt_damage");
        turretMaxPitch = nonNegative(file, "turret.max_pitch");
        turretPitchRate = nonNegative(file, "turret.pitch_rate");
        turretPitchReturnRate = nonNegative(file, "turret.pitch_return_rate");
        turretLostSightCooldown = nonNegative(file, "turret.lost_sight_cooldown");
        turretFireArc = nonNegative(file, "turret.fire_arc");

        guardWindupTime = nonNegative(file, "guard.windup_time");
        guardCooldown = nonNegative(file, "guard.cooldown");
        guardBoltSpeed = positive(file, "guard.bolt_speed");
        guardBoltDamage = nonNegative(file, "guard.bolt_damage");
        guardMinRange = nonNegative(file, "guard.min_range");
        guardMaxRange = nonNegative(file, "guard.max_range");
        if (guardMaxRange < guardMinRange) throw file.invalid("guard.max_range", "must be at least guard.min_range");
        guardPreferredRange = nonNegative(file, "guard.preferred_range");
        guardRangeAdjustGain = nonNegative(file, "guard.range_adjust_gain");
        guardAttackRange = nonNegative(file, "guard.attack_range");
        guardAdvanceStrafe = nonNegative(file, "guard.advance_strafe");
        guardRetreatBack = nonNegative(file, "guard.retreat_back");
        guardRetreatStrafe = nonNegative(file, "guard.retreat_strafe");
        guardStrafeSwitchTime = positive(file, "guard.strafe_switch_time");
        guardWindupMoveScale = nonNegative(file, "guard.windup_move_scale");
        guardTurnRate = nonNegative(file, "guard.turn_rate");
        guardPathTurnRate = nonNegative(file, "guard.path_turn_rate");
        guardWalkPhaseRate = nonNegative(file, "guard.walk_phase_rate");
        guardFireArc = nonNegative(file, "guard.fire_arc");
        guardBoltMaxPitch = nonNegative(file, "guard.bolt_max_pitch");

        droneTriggerRange = nonNegative(file, "drone.trigger_range");
        droneWindupTime = nonNegative(file, "drone.windup_time");
        droneLungeTime = nonNegative(file, "drone.lunge_time");
        droneLungeSpeed = nonNegative(file, "drone.lunge_speed");
        droneLungeDamage = nonNegative(file, "drone.lunge_damage");
        droneContactMargin = nonNegative(file, "drone.contact_margin");
        droneRecoverTime = nonNegative(file, "drone.recover_time");
        droneCooldown = nonNegative(file, "drone.cooldown");
        droneTurnRate = nonNegative(file, "drone.turn_rate");
    }

    /**
     * Reads {@code data/enemies.txt} and {@code data/warden.txt} from {@code assets/}.
     * @return the enemy balance the game ships with, or the edited copy the working directory holds
     * @throws IllegalArgumentException if a file breaks the format, lacks a key, holds an unknown one or holds a
     *     value out of range; the message names the file, the key and, except for a key that is missing, the line
     */
    public static EnemyTuning load() {
        return new EnemyTuning(TuningFile.load(PATH, keys(), List.of()), WardenTuning.load());
    }

    /**
     * Builds the enemy balance from the text of an {@code enemies.txt}, for tests.
     * @param label path label included in error messages
     * @param text the file's contents
     * @param warden the Warden's fight
     * @return the enemy balance the text describes
     * @throws IllegalArgumentException as {@link #load()} does
     */
    public static EnemyTuning parse(String label, String text, WardenTuning warden) {
        return new EnemyTuning(TuningFile.parseText(label, text, keys(), List.of()), warden);
    }

    /**
     * @param kind an enemy kind
     * @return the values of that kind
     */
    public KindStats of(Enemy.Kind kind) {
        return kinds[kind.ordinal()];
    }

    /** @return the key prefix of a kind's block: its name in lower case and a dot. */
    private static String prefix(Enemy.Kind kind) {
        return kind.name().toLowerCase(Locale.ROOT) + ".";
    }

    private static List<String> keys() {
        List<String> keys = new ArrayList<>();
        for (Enemy.Kind kind : Enemy.Kind.values()) {
            for (String key : KIND_KEYS) keys.add(prefix(kind) + key);
        }
        for (String key : SHARED_KEYS) keys.add(key);
        return keys;
    }

    /** Reads a number that must be above zero. */
    static float positive(TuningFile file, String key) {
        float value = file.getFloat(key);
        if (value <= 0f) throw file.invalid(key, "must be positive");
        return value;
    }

    /** Reads a number that must not be below zero. */
    static float nonNegative(TuningFile file, String key) {
        float value = file.getFloat(key);
        if (value < 0f) throw file.invalid(key, "must not be negative");
        return value;
    }
}

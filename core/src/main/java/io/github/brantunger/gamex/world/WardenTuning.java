package io.github.brantunger.gamex.world;

import io.github.brantunger.gamex.data.TuningFile;

/**
 * The Warden's fight read from {@code assets/data/warden.txt}: its chase, its combo of punches and its overdrive.
 * Its health, walk speed and the other values every kind has are in {@link EnemyTuning.KindStats}. Distances are in
 * map cells, times in seconds, angles in degrees and turn rates in degrees per second. Instances are immutable.
 *
 * <p>{@link #comboTime} and the punch times are measured from the {@code Punch_Combo} clip of
 * {@code assets/models/enemy-warden.glb}, not chosen: changing them without changing the clip takes the hits out of
 * step with the animation.
 */
public final class WardenTuning {

    /** The file inside {@code assets/} that {@link #load()} reads. */
    public static final String PATH = "data/warden.txt";

    private static final String[] KEYS = {
        "overdrive_health_ratio", "run_speed", "chase_memory", "turn_rate", "path_turn_rate", "attack_range",
        "attack_facing", "combo_time", "punch_times", "punch_damage", "lunge_distance", "lunge_time", "punch_reach",
        "punch_arc", "combo_turn_rate", "telegraph_time", "recover_time", "punch_sound_gain",
        "overdrive_speed_scale", "overdrive_turn_scale", "overdrive_damage_scale", "overdrive_combo_rate",
        "overdrive_recover_scale", "overdrive_sound_gain", "overdrive_burst_size",
    };

    /** The Warden is in overdrive, its second phase, at or below this share of its health. */
    public final float overdriveHealthRatio;
    /** How fast the Warden runs at a player it can see. The player moves at 3.2, so walking away does not work. */
    public final float runSpeed;
    /**
     * How long the Warden keeps running after it loses sight of the player. A sightline that grazes a pillar comes
     * and goes from step to step, and without this the Warden would stutter between walk and run.
     */
    public final float chaseMemory;
    /** How fast the Warden turns while it chases a player in sight. */
    public final float turnRate;
    /** How fast the Warden turns along its route to a player out of sight. */
    public final float pathTurnRate;
    /** The Warden starts its combo when the player's centre is this close, in sight and in front of it. */
    public final float attackRange;
    /** The player must be within this angle of the Warden's facing for the combo to start. */
    public final float attackFacing;
    /** The length of the {@code Punch_Combo} clip of the Warden's model; the combo lasts as long. */
    public final float comboTime;
    /** A punch hits a player whose centre is within this distance of the Warden's when it lands. */
    public final float punchReach;
    /** A punch hits a player within this angle of the Warden's facing, to either side. */
    public final float punchArc;
    /** How fast the Warden turns during its combo: slowly, so a sidestep gets round it. */
    public final float comboTurnRate;
    /** How long before a punch lands its telegraph builds up, in seconds of the clip. */
    public final float telegraphTime;
    /** How long the Warden stands open after its combo before it chases again. */
    public final float recoverTime;
    /** The volume of the sound of each punch. */
    public final float punchSoundGain;
    /** In overdrive the Warden runs this much faster. */
    public final float overdriveSpeedScale;
    /** In overdrive the Warden turns this much faster, in the chase and in the combo. */
    public final float overdriveTurnScale;
    /** In overdrive every punch does this much more damage. */
    public final float overdriveDamageScale;
    /** In overdrive the combo plays this much faster, its punches and lunges with it. */
    public final float overdriveComboRate;
    /** In overdrive the recovery after the combo lasts this share of its time. */
    public final float overdriveRecoverScale;
    /** The volume of the sound of the Warden entering overdrive. */
    public final float overdriveSoundGain;
    /** The size of the burst the Warden gives off entering overdrive. */
    public final float overdriveBurstSize;

    private final float[] punchTimes;
    private final float[] punchDamage;
    private final float[] lungeDistance;
    private final float[] lungeTime;

    private WardenTuning(TuningFile file) {
        overdriveHealthRatio = file.getFloat("overdrive_health_ratio");
        if (overdriveHealthRatio < 0f || overdriveHealthRatio > 1f) {
            throw file.invalid("overdrive_health_ratio", "must be from 0 to 1");
        }
        runSpeed = EnemyTuning.nonNegative(file, "run_speed");
        chaseMemory = EnemyTuning.nonNegative(file, "chase_memory");
        turnRate = EnemyTuning.nonNegative(file, "turn_rate");
        pathTurnRate = EnemyTuning.nonNegative(file, "path_turn_rate");
        attackRange = EnemyTuning.nonNegative(file, "attack_range");
        attackFacing = EnemyTuning.nonNegative(file, "attack_facing");
        comboTime = EnemyTuning.positive(file, "combo_time");

        punchTimes = file.getFloats("punch_times");
        int punches = punchTimes.length;
        punchDamage = file.getFloats("punch_damage", punches);
        lungeDistance = file.getFloats("lunge_distance", punches);
        lungeTime = file.getFloats("lunge_time", punches);
        for (int i = 0; i < punches; i++) {
            float earliest = i == 0 ? 0f : punchTimes[i - 1];
            if (punchTimes[i] < earliest || punchTimes[i] > comboTime) {
                throw file.invalid("punch_times", "must rise from 0 to combo_time");
            }
            if (punchDamage[i] < 0f) throw file.invalid("punch_damage", "must not be negative");
            if (lungeDistance[i] < 0f) throw file.invalid("lunge_distance", "must not be negative");
            if (lungeTime[i] <= 0f) throw file.invalid("lunge_time", "must be positive");
        }

        punchReach = EnemyTuning.nonNegative(file, "punch_reach");
        punchArc = EnemyTuning.nonNegative(file, "punch_arc");
        comboTurnRate = EnemyTuning.nonNegative(file, "combo_turn_rate");
        telegraphTime = EnemyTuning.positive(file, "telegraph_time");
        recoverTime = EnemyTuning.nonNegative(file, "recover_time");
        punchSoundGain = EnemyTuning.nonNegative(file, "punch_sound_gain");
        overdriveSpeedScale = EnemyTuning.nonNegative(file, "overdrive_speed_scale");
        overdriveTurnScale = EnemyTuning.nonNegative(file, "overdrive_turn_scale");
        overdriveDamageScale = EnemyTuning.nonNegative(file, "overdrive_damage_scale");
        overdriveComboRate = EnemyTuning.positive(file, "overdrive_combo_rate");
        overdriveRecoverScale = EnemyTuning.nonNegative(file, "overdrive_recover_scale");
        overdriveSoundGain = EnemyTuning.nonNegative(file, "overdrive_sound_gain");
        overdriveBurstSize = EnemyTuning.nonNegative(file, "overdrive_burst_size");
    }

    /**
     * Reads {@code data/warden.txt} from {@code assets/}.
     * @return the Warden's fight as the game ships it, or as the edited copy the working directory holds has it
     * @throws IllegalArgumentException if the file breaks the format, lacks a key, holds an unknown one or holds a
     *     value out of range; the message names the file, the key and, except for a key that is missing, the line
     */
    public static WardenTuning load() {
        return new WardenTuning(TuningFile.load(PATH, KEYS));
    }

    /**
     * Builds the Warden's fight from the text of a {@code warden.txt}, for tests.
     * @param label path label included in error messages
     * @param text the file's contents
     * @return the fight the text describes
     * @throws IllegalArgumentException as {@link #load()} does
     */
    public static WardenTuning parse(String label, String text) {
        return new WardenTuning(TuningFile.parseText(label, text, KEYS));
    }

    /** @return how many punches the combo has. */
    public int punchCount() {
        return punchTimes.length;
    }

    /**
     * @param punch a punch of the combo, from 0
     * @return when it lands, in seconds of the clip
     */
    public float punchTime(int punch) {
        return punchTimes[punch];
    }

    /**
     * @param punch a punch of the combo, from 0
     * @return the damage it does
     */
    public float punchDamage(int punch) {
        return punchDamage[punch];
    }

    /**
     * @param punch a punch of the combo, from 0
     * @return how far the Warden lunges along its facing into it, at most: it stops at the player
     */
    public float lungeDistance(int punch) {
        return lungeDistance[punch];
    }

    /**
     * @param punch a punch of the combo, from 0
     * @return how long before it lands its lunge starts, in seconds of the clip
     */
    public float lungeTime(int punch) {
        return lungeTime[punch];
    }
}

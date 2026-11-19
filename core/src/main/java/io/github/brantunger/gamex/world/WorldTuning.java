package io.github.brantunger.gamex.world;

import io.github.brantunger.gamex.data.TuningFile;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

/**
 * The balance of the level's own rules, read from {@code assets/data/world.txt}: what the health, armor and energy
 * pickups give, the coolant canister's explosion, the shock grates, how long the hit marker and the pickup toast
 * show, and how far each noise the player makes wakes enemies. The game loads it once at start-up and hands it to
 * the simulation. Distances are in world units and times in seconds. Instances are immutable.
 */
public final class WorldTuning {

    /** The file inside {@code assets/} that {@link #load()} reads. */
    public static final String PATH = "data/world.txt";

    private static final String[] KEYS = {
        "pickup.medkit", "pickup.armor", "pickup.energy_cell",
        "canister.blast_radius", "canister.blast_damage", "canister.self_damage_scale",
        "grate.damage", "grate.interval", "grate.clearance_height",
        "feedback.hit_marker_time", "feedback.toast_time",
    };

    /** The noises that wake enemies, each with a {@code noise.} key named after it in lower case. */
    public enum NoiseEvent {
        /** A blast going off, at the explosion. */
        BLAST_DETONATION,
        /** A security console being overridden, at the console. */
        CONSOLE_OVERRIDE,
        /** A pistol shot, at the player. */
        PISTOL_SHOT,
        /** A pistol shot hitting an enemy, at the enemy. */
        PISTOL_HIT,
        /** A rail shot, at the player. */
        RAIL_SHOT,
        /** A rail beam hitting an enemy, at the enemy. */
        RAIL_HIT,
        /** A primary scattergun shot, at the player. */
        SCATTERGUN_SHOT,
        /** A scattergun double-blast, at the player. */
        SCATTERGUN_DOUBLE_SHOT,
        /** A scattergun pellet hitting an enemy, at the enemy. */
        SCATTERGUN_HIT,
        /** A coolant canister going off, at the canister. */
        CANISTER_EXPLOSION;

        /** @return the key of this noise's line in the file. */
        String key() {
            return "noise." + name().toLowerCase(Locale.ROOT);
        }
    }

    /** How far one noise carries. */
    public static final class Noise {
        /** The noise wakes enemies this near that have a line of sight to it. */
        public final float losRange;
        /** The noise wakes enemies this near through walls. */
        public final float muffledRange;

        private Noise(float losRange, float muffledRange) {
            this.losRange = losRange;
            this.muffledRange = muffledRange;
        }
    }

    /** The health a medkit restores on Normal difficulty; at least 1. */
    public final float pickupMedkit;
    /** The armor an armor pickup adds on Normal difficulty; at least 1. */
    public final float pickupArmor;
    /** The energy an energy cell refills on Normal difficulty; at least 1. */
    public final float pickupEnergyCell;

    /** The blast radius of a detonating coolant canister. */
    public final float canisterBlastRadius;
    /** The damage at the centre of a detonating coolant canister. */
    public final float canisterBlastDamage;
    /** The share of a canister's blast damage the player takes, before the difficulty's multiplier. */
    public final float canisterSelfDamageScale;

    /** The damage of one shock grate tick, to the player before the difficulty's multiplier. */
    public final float grateDamage;
    /** The time between two shock grate ticks on whoever stands on a grate. */
    public final float grateInterval;
    /** A jumping player whose feet are higher than this above the floor clears a shock grate. */
    public final float grateClearanceHeight;

    /** How long the crosshair's hit marker shows. */
    public final float hitMarkerTime;
    /** How long a pickup toast shows. */
    public final float toastTime;

    private final Noise[] noises;

    private WorldTuning(TuningFile file) {
        pickupMedkit = atLeastOne(file, "pickup.medkit");
        pickupArmor = atLeastOne(file, "pickup.armor");
        pickupEnergyCell = atLeastOne(file, "pickup.energy_cell");

        canisterBlastRadius = positive(file, "canister.blast_radius");
        canisterBlastDamage = nonNegative(file, "canister.blast_damage");
        canisterSelfDamageScale = nonNegative(file, "canister.self_damage_scale");

        grateDamage = nonNegative(file, "grate.damage");
        grateInterval = positive(file, "grate.interval");
        grateClearanceHeight = nonNegative(file, "grate.clearance_height");

        hitMarkerTime = positive(file, "feedback.hit_marker_time");
        toastTime = nonNegative(file, "feedback.toast_time");

        NoiseEvent[] events = NoiseEvent.values();
        noises = new Noise[events.length];
        for (NoiseEvent event : events) {
            float[] ranges = file.getFloats(event.key(), 2);
            if (ranges[0] < 0f || ranges[1] < 0f) throw file.invalid(event.key(), "must not be negative");
            noises[event.ordinal()] = new Noise(ranges[0], ranges[1]);
        }
    }

    /**
     * Reads {@code data/world.txt} from {@code assets/}.
     * @return the balance the game ships with, or the edited copy the working directory holds
     * @throws IllegalArgumentException if the file breaks the format, lacks a key, holds an unknown one or holds a
     *     value out of range; the message names the file, the key and the line
     */
    public static WorldTuning load() {
        return new WorldTuning(TuningFile.load(PATH, keys(), List.of()));
    }

    /**
     * Builds the balance from the text of a {@code world.txt}, for tests.
     * @param label path label included in error messages
     * @param text the file's contents
     * @return the balance the text describes
     * @throws IllegalArgumentException as {@link #load()} does
     */
    public static WorldTuning parse(String label, String text) {
        return new WorldTuning(TuningFile.parseText(label, text, keys(), List.of()));
    }

    /**
     * @param event a noise
     * @return how far it carries
     */
    public Noise noise(NoiseEvent event) {
        return noises[event.ordinal()];
    }

    private static List<String> keys() {
        List<String> keys = new ArrayList<>();
        for (String key : KEYS) keys.add(key);
        for (NoiseEvent event : NoiseEvent.values()) keys.add(event.key());
        return keys;
    }

    /** Reads a number that must be above zero. */
    private static float positive(TuningFile file, String key) {
        float value = file.getFloat(key);
        if (value <= 0f) throw file.invalid(key, "must be positive");
        return value;
    }

    /** Reads a pickup's amount, which must be at least 1: a pickup never gives less, whatever the difficulty. */
    private static float atLeastOne(TuningFile file, String key) {
        float value = file.getFloat(key);
        if (value < 1f) throw file.invalid(key, "must be at least 1");
        return value;
    }

    /** Reads a number that must not be below zero. */
    private static float nonNegative(TuningFile file, String key) {
        float value = file.getFloat(key);
        if (value < 0f) throw file.invalid(key, "must not be negative");
        return value;
    }
}

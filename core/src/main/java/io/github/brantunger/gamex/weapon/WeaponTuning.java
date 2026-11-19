package io.github.brantunger.gamex.weapon;

import io.github.brantunger.gamex.data.TuningFile;
import io.github.brantunger.gamex.player.Player;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;

/**
 * The weapon balance read from {@code assets/data/weapons.txt}: the pistol, the scattergun and its pellet rings, the
 * Rail Lance, the charged blast and the energy meter that pays for it. The game loads it once at start-up and hands
 * it to the weapons and the simulation. Distances are in world units, times in seconds and angles in degrees.
 * Instances are immutable.
 */
public final class WeaponTuning {

    /** The file inside {@code assets/} that {@link #load()} reads. */
    public static final String PATH = "data/weapons.txt";

    private static final String PRIMARY_RING = "scattergun.primary.ring";
    private static final String ALT_RING = "scattergun.alt.ring";

    private static final String[] KEYS = {
        "pistol.fire_interval", "pistol.damage", "pistol.range", "pistol.flash_time", "pistol.recoil_kick",
        "scattergun.max_shells", "scattergun.weapon_pickup_shells", "scattergun.box_pickup_shells",
        "scattergun.primary.interval", "scattergun.primary.recoil_kick", "scattergun.primary.pump_delay",
        "scattergun.primary.pump_time",
        "scattergun.alt.interval", "scattergun.alt.recoil_kick", "scattergun.alt.pump_delay",
        "scattergun.alt.pump_time",
        "scattergun.pellet_damage", "scattergun.falloff_start", "scattergun.range",
        "scattergun.min_falloff_scale", "scattergun.flash_time", "scattergun.pump_pull_share",
        "rail.max_slugs", "rail.weapon_pickup_slugs", "rail.pack_pickup_slugs", "rail.damage", "rail.range",
        "rail.fire_interval", "rail.flash_time", "rail.bolt_delay", "rail.bolt_time", "rail.bolt_pull_share",
        "rail.zoom_factor", "rail.zoom_speed", "rail.recoil_kick",
        "blast.full_charge_time", "blast.min_charge", "blast.min_cost", "blast.max_cost", "blast.min_radius",
        "blast.max_radius", "blast.min_damage", "blast.max_damage", "blast.recoil_kick",
        "energy.capacity", "energy.regen",
    };

    /** One ring of scattergun pellets, spread evenly round the aim line. */
    public static final class Ring {
        /** How many pellets the ring holds. */
        public final int count;
        /** The angle between the aim line and each pellet of the ring. */
        public final float coneDegrees;
        /** How far the ring is turned round the aim line from having its first pellet straight to the right. */
        public final float twistDegrees;

        private Ring(int count, float coneDegrees, float twistDegrees) {
            this.count = count;
            this.coneDegrees = coneDegrees;
            this.twistDegrees = twistDegrees;
        }
    }

    /** The shortest time between two pistol shots. */
    public final float pistolFireInterval;
    /** The damage of one pistol shot. */
    public final float pistolDamage;
    /** The range of a pistol shot, and the distance a blast is aimed at when nothing is in the way. */
    public final float pistolRange;
    /** How long the pistol's muzzle flash shows after a shot. */
    public final float pistolFlashTime;
    /**
     * How hard a pistol shot kicks the viewmodel, as a multiple of the viewmodel's base kick; each weapon's kick is
     * set on its own.
     */
    public final float pistolRecoilKick;

    /** The most shells the player carries. */
    public final int scattergunMaxShells;
    /** The shells that come with the Scattergun itself on Normal difficulty; at least 1. */
    public final int scattergunWeaponPickupShells;
    /** The shells in a Shell Box on Normal difficulty; at least 1. */
    public final int scattergunBoxPickupShells;
    /** The pellets of a 1-shell primary blast: the centre pellet and those of its rings. */
    public final int scattergunPrimaryPellets;
    /** The shortest time between two primary shots. */
    public final float scattergunPrimaryInterval;
    /** How hard a primary shot kicks the viewmodel. */
    public final float scattergunPrimaryRecoilKick;
    /** The wait from a primary shot to the start of the pump rack. */
    public final float scattergunPrimaryPumpDelay;
    /** How long the pump rack of a primary shot takes. */
    public final float scattergunPrimaryPumpTime;
    /** The pellets of a 2-shell double-blast: the centre pellet and those of its rings. */
    public final int scattergunAltPellets;
    /** The recovery time after a double-blast. */
    public final float scattergunAltInterval;
    /** How hard a double-blast kicks the viewmodel. */
    public final float scattergunAltRecoilKick;
    /** The wait from a double-blast to the start of the pump rack. */
    public final float scattergunAltPumpDelay;
    /** How long the pump rack of a double-blast takes. */
    public final float scattergunAltPumpTime;
    /** The damage of one pellet at or inside {@link #scattergunFalloffStart}. */
    public final float scattergunPelletDamage;
    /** The distance at which pellet damage starts to taper. */
    public final float scattergunFalloffStart;
    /** The range of a pellet. */
    public final float scattergunRange;
    /** The share of {@link #scattergunPelletDamage} a pellet still does at {@link #scattergunRange}. */
    public final float scattergunMinFalloffScale;
    /** How long the scattergun's muzzle flash shows after a shot. */
    public final float scattergunFlashTime;
    /** The share of a pump rack spent pulling the forend back; the rest is the return. */
    public final float scattergunPumpPullShare;

    /** The most slugs the player carries. */
    public final int railMaxSlugs;
    /** The slugs that come with the Rail Lance itself on Normal difficulty; at least 1. */
    public final int railWeaponPickupSlugs;
    /** The slugs in a Slug Pack on Normal difficulty; at least 1. */
    public final int railPackPickupSlugs;
    /** The damage of the beam to a target, and to an enemy before the {@code rail_damage_scale} of its kind. */
    public final float railDamage;
    /** The range of the beam. */
    public final float railRange;
    /** The capacitor's recharge time between two shots. */
    public final float railFireInterval;
    /** How long the Rail Lance's muzzle flash shows after a shot. */
    public final float railFlashTime;
    /** The wait from a shot to the start of the breech bolt's cycle. */
    public final float railBoltDelay;
    /** How long the breech bolt's cycle takes. */
    public final float railBoltTime;
    /** The share of the bolt's cycle spent recoiling; the rest is the return. */
    public final float railBoltPullShare;
    /** The magnification of the optic at full zoom; at least 1. */
    public final float railZoomFactor;
    /** How fast the optic zooms in and out, in full zooms per second. */
    public final float railZoomSpeed;
    /** How hard a rail shot kicks the viewmodel. */
    public final float railRecoilKick;

    /** The time a full charge takes. */
    public final float blastFullChargeTime;
    /** The smallest charge that fires, from 0 to 1. */
    public final float blastMinCharge;
    /** The energy cost of a blast at {@link #blastMinCharge}. */
    public final float blastMinCost;
    /** The energy cost of a blast at full charge. */
    public final float blastMaxCost;
    /** The explosion radius a charge of 0 would give; see {@link #blastRadius}. */
    public final float blastMinRadius;
    /** The explosion radius of a full charge. */
    public final float blastMaxRadius;
    /** The damage at the centre of the explosion a charge of 0 would give; see {@link #blastDamage}. */
    public final float blastMinDamage;
    /** The damage at the centre of the explosion of a full charge. */
    public final float blastMaxDamage;
    /** How hard a released blast kicks the viewmodel. */
    public final float blastRecoilKick;

    /**
     * The capacity of the player's energy meter; at least {@link Player#DASH_COST} and {@link #blastMinCost}, so
     * that a full meter pays for a dash and for the smallest blast.
     */
    public final float energyCapacity;
    /** The refill rate of the player's energy meter, in energy per second. */
    public final float energyRegen;

    private final List<Ring> scattergunPrimaryRings;
    private final List<Ring> scattergunAltRings;

    private WeaponTuning(TuningFile file) {
        pistolFireInterval = positive(file, "pistol.fire_interval");
        pistolDamage = nonNegative(file, "pistol.damage");
        pistolRange = positive(file, "pistol.range");
        pistolFlashTime = nonNegative(file, "pistol.flash_time");
        pistolRecoilKick = positive(file, "pistol.recoil_kick");

        scattergunMaxShells = atLeastOne(file, "scattergun.max_shells");
        scattergunWeaponPickupShells = atLeastOne(file, "scattergun.weapon_pickup_shells");
        scattergunBoxPickupShells = atLeastOne(file, "scattergun.box_pickup_shells");
        scattergunPrimaryRings = rings(file, PRIMARY_RING);
        scattergunPrimaryPellets = pellets(scattergunPrimaryRings);
        scattergunPrimaryInterval = positive(file, "scattergun.primary.interval");
        scattergunPrimaryRecoilKick = positive(file, "scattergun.primary.recoil_kick");
        scattergunPrimaryPumpDelay = nonNegative(file, "scattergun.primary.pump_delay");
        scattergunPrimaryPumpTime = positive(file, "scattergun.primary.pump_time");
        scattergunAltRings = rings(file, ALT_RING);
        scattergunAltPellets = pellets(scattergunAltRings);
        scattergunAltInterval = positive(file, "scattergun.alt.interval");
        scattergunAltRecoilKick = positive(file, "scattergun.alt.recoil_kick");
        scattergunAltPumpDelay = nonNegative(file, "scattergun.alt.pump_delay");
        scattergunAltPumpTime = positive(file, "scattergun.alt.pump_time");
        scattergunPelletDamage = nonNegative(file, "scattergun.pellet_damage");
        scattergunFalloffStart = nonNegative(file, "scattergun.falloff_start");
        scattergunRange = file.getFloat("scattergun.range");
        if (scattergunRange <= scattergunFalloffStart) {
            throw file.invalid("scattergun.range", "must be above scattergun.falloff_start");
        }
        scattergunMinFalloffScale = file.getFloat("scattergun.min_falloff_scale");
        if (scattergunMinFalloffScale < 0f || scattergunMinFalloffScale > 1f) {
            throw file.invalid("scattergun.min_falloff_scale", "must be from 0 to 1");
        }
        scattergunFlashTime = nonNegative(file, "scattergun.flash_time");
        scattergunPumpPullShare = share(file, "scattergun.pump_pull_share");

        railMaxSlugs = atLeastOne(file, "rail.max_slugs");
        railWeaponPickupSlugs = atLeastOne(file, "rail.weapon_pickup_slugs");
        railPackPickupSlugs = atLeastOne(file, "rail.pack_pickup_slugs");
        railDamage = nonNegative(file, "rail.damage");
        railRange = positive(file, "rail.range");
        railFireInterval = positive(file, "rail.fire_interval");
        railFlashTime = nonNegative(file, "rail.flash_time");
        railBoltDelay = nonNegative(file, "rail.bolt_delay");
        railBoltTime = positive(file, "rail.bolt_time");
        railBoltPullShare = share(file, "rail.bolt_pull_share");
        railZoomFactor = file.getFloat("rail.zoom_factor");
        if (railZoomFactor < 1f) throw file.invalid("rail.zoom_factor", "must be at least 1");
        railZoomSpeed = nonNegative(file, "rail.zoom_speed");
        railRecoilKick = positive(file, "rail.recoil_kick");

        blastFullChargeTime = positive(file, "blast.full_charge_time");
        blastMinCharge = share(file, "blast.min_charge");
        blastMinCost = nonNegative(file, "blast.min_cost");
        blastMaxCost = file.getFloat("blast.max_cost");
        if (blastMaxCost <= blastMinCost) throw file.invalid("blast.max_cost", "must be above blast.min_cost");
        blastMinRadius = nonNegative(file, "blast.min_radius");
        blastMaxRadius = file.getFloat("blast.max_radius");
        if (blastMaxRadius < blastMinRadius) {
            throw file.invalid("blast.max_radius", "must be at least blast.min_radius");
        }
        blastMinDamage = nonNegative(file, "blast.min_damage");
        blastMaxDamage = file.getFloat("blast.max_damage");
        if (blastMaxDamage < blastMinDamage) {
            throw file.invalid("blast.max_damage", "must be at least blast.min_damage");
        }
        blastRecoilKick = positive(file, "blast.recoil_kick");

        energyCapacity = positive(file, "energy.capacity");
        // A meter smaller than a cost could never pay it: the dash or every blast would be refused for ever.
        if (energyCapacity < Player.DASH_COST) {
            throw file.invalid("energy.capacity", "must be at least " + Player.DASH_COST + ", the cost of a dash");
        }
        if (energyCapacity < blastMinCost) {
            throw file.invalid("energy.capacity", "must be at least blast.min_cost");
        }
        energyRegen = nonNegative(file, "energy.regen");
    }

    /**
     * Reads {@code data/weapons.txt} from {@code assets/}.
     * @return the weapon balance the game ships with, or the edited copy the working directory holds
     * @throws IllegalArgumentException if the file breaks the format, lacks a key, holds an unknown one or holds a
     *     value out of range; the message names the file, the key and the line
     */
    public static WeaponTuning load() {
        return new WeaponTuning(TuningFile.load(PATH, Arrays.asList(KEYS), List.of(PRIMARY_RING, ALT_RING)));
    }

    /**
     * Builds the weapon balance from the text of a {@code weapons.txt}, for tests.
     * @param label path label included in error messages
     * @param text the file's contents
     * @return the weapon balance the text describes
     * @throws IllegalArgumentException as {@link #load()} does
     */
    public static WeaponTuning parse(String label, String text) {
        return new WeaponTuning(
            TuningFile.parseText(label, text, Arrays.asList(KEYS), List.of(PRIMARY_RING, ALT_RING)));
    }

    /** @return the rings of a primary blast round its centre pellet, in file order; the list cannot be changed. */
    public List<Ring> scattergunPrimaryRings() {
        return scattergunPrimaryRings;
    }

    /** @return the rings of a double-blast round its centre pellet, in file order; the list cannot be changed. */
    public List<Ring> scattergunAltRings() {
        return scattergunAltRings;
    }

    /**
     * @param charge a charge level from {@link #blastMinCharge} to 1
     * @return the energy a blast of that charge costs, growing in a straight line with the charge
     */
    public float blastCost(float charge) {
        float t = Math.max(0f, Math.min(1f, (charge - blastMinCharge) / (1f - blastMinCharge)));
        return blastMinCost + (blastMaxCost - blastMinCost) * t;
    }

    /**
     * @param charge the charge a blast was fired with
     * @return the radius of its explosion, growing in a straight line from {@link #blastMinRadius} at a charge of 0
     *     to {@link #blastMaxRadius} at a charge of 1
     */
    public float blastRadius(float charge) {
        return blastMinRadius + (blastMaxRadius - blastMinRadius) * charge;
    }

    /**
     * @param charge the charge a blast was fired with
     * @return the damage at the centre of its explosion, growing in a straight line from {@link #blastMinDamage} at
     *     a charge of 0 to {@link #blastMaxDamage} at a charge of 1
     */
    public float blastDamage(float charge) {
        return blastMinDamage + (blastMaxDamage - blastMinDamage) * charge;
    }

    /** Reads the rows of a ring key: the pellet count, the cone angle and the twist of each ring. */
    private static List<Ring> rings(TuningFile file, String key) {
        List<Ring> rings = new ArrayList<>();
        for (TuningFile.Entry row : file.entries(key)) {
            float[] values = file.getFloats(row, 3);
            int count = (int) values[0];
            if (count != values[0] || count < 1) {
                throw file.invalid(row, "the pellet count must be a whole number of at least 1");
            }
            if (values[1] < 0f || values[1] >= 90f) {
                throw file.invalid(row, "the cone angle must be from 0 to below 90 degrees");
            }
            rings.add(new Ring(count, values[1], values[2]));
        }
        return Collections.unmodifiableList(rings);
    }

    /** @return the pellets of a shot with these rings: one down the aim line and those of every ring. */
    private static int pellets(List<Ring> rings) {
        int total = 1;
        for (Ring ring : rings) total += ring.count;
        return total;
    }

    /** Reads a number that must be above zero. */
    private static float positive(TuningFile file, String key) {
        float value = file.getFloat(key);
        if (value <= 0f) throw file.invalid(key, "must be positive");
        return value;
    }

    /** Reads a number that must not be below zero. */
    private static float nonNegative(TuningFile file, String key) {
        float value = file.getFloat(key);
        if (value < 0f) throw file.invalid(key, "must not be negative");
        return value;
    }

    /** Reads a share of a whole that is divided by, and so must be above 0 and below 1. */
    private static float share(TuningFile file, String key) {
        float value = file.getFloat(key);
        if (value <= 0f || value >= 1f) throw file.invalid(key, "must be above 0 and below 1");
        return value;
    }

    /** Reads a whole number that must be at least 1. */
    private static int atLeastOne(TuningFile file, String key) {
        int value = file.getInt(key);
        if (value < 1) throw file.invalid(key, "must be at least 1");
        return value;
    }
}

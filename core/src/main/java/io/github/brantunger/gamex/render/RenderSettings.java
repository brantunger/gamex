package io.github.brantunger.gamex.render;

import com.badlogic.gdx.math.MathUtils;
import io.github.brantunger.gamex.data.TuningFile;
import io.github.brantunger.gamex.world.Door;
import io.github.brantunger.gamex.world.Pickup;
import io.github.brantunger.gamex.world.WorldModels;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

/**
 * How the 3D view is drawn, where that is a number, a colour or a choice per kind rather than code: the clip range,
 * the fog, the halo under each pickup and security console, and which accent of the world palette each kind of door
 * and pickup is drawn with. Loaded once at start-up from {@code assets/data/render.txt} and passed to
 * {@link WorldRenderer}; tests read the shipped file or hand their own text to {@link #parse}.
 *
 * <p>The file holds the keys below and nothing else. A kind's key ends in the lower-case name of its enum constant
 * ({@code pickup.halo.medkit} for {@link Pickup.Kind#MEDKIT}).
 * <ul>
 *   <li>{@code clip.near}, {@code clip.far}: the nearest and farthest distance the world camera draws.</li>
 *   <li>{@code fog.colour}: red, green, blue of the fog, which is also the colour the frame is cleared to;
 *       {@code fog.start}, {@code fog.end}: the distances at which the fog begins and at which it hides
 *       everything.</li>
 *   <li>{@code pickup.halo.size}: the width of the halo on the floor under a pickup; {@code pickup.halo.<kind>}:
 *       its red, green, blue and alpha for each {@link Pickup.Kind}.</li>
 *   <li>{@code pickup.accent.<kind>}: the accent of {@code data/palette-world.txt} drawn on the {@code # @accent}
 *       surfaces of each {@link Pickup.Kind}'s model, or {@value #NO_ACCENT}.</li>
 *   <li>{@code console.halo.size}, {@code console.halo.active}, {@code console.halo.locked}: the same for the halo
 *       under a security console, by whether it has been switched on.</li>
 *   <li>{@code door.accent.<lock>}: the accent of the status strips of a closed door of each
 *       {@link Door.LockKind}; {@code door.accent.opening}: the accent of any door while it opens.</li>
 * </ul>
 * There is no default: a missing key, a key that is not listed here, a distance that is out of order, a colour
 * outside 0 to 1 and an accent the world palette does not have each fail the load with a message naming the file
 * and the key.
 *
 * <p>Instances are immutable and safe to share.
 */
public final class RenderSettings {

    /** Internal asset path of the settings file. */
    public static final String PATH = "data/render.txt";
    /** The value of a {@code pickup.accent.<kind>} line for a pickup that is drawn without an accent. */
    public static final String NO_ACCENT = "none";

    private static final String CLIP_NEAR = "clip.near";
    private static final String CLIP_FAR = "clip.far";
    private static final String FOG_COLOUR = "fog.colour";
    private static final String FOG_START = "fog.start";
    private static final String FOG_END = "fog.end";
    private static final String PICKUP_HALO_SIZE = "pickup.halo.size";
    private static final String PICKUP_HALO = "pickup.halo.";
    private static final String PICKUP_ACCENT = "pickup.accent.";
    private static final String CONSOLE_HALO_SIZE = "console.halo.size";
    private static final String CONSOLE_HALO_ACTIVE = "console.halo.active";
    private static final String CONSOLE_HALO_LOCKED = "console.halo.locked";
    private static final String DOOR_ACCENT = "door.accent.";
    private static final String DOOR_ACCENT_OPENING = "door.accent.opening";

    /** The nearest distance the world camera draws. */
    public final float clipNear;
    /** The farthest distance the world camera draws. */
    public final float clipFar;
    /** The red of the fog, and of the cleared frame. */
    public final float fogRed;
    /** The green of the fog, and of the cleared frame. */
    public final float fogGreen;
    /** The blue of the fog, and of the cleared frame. */
    public final float fogBlue;
    /** The distance at which the fog begins. */
    public final float fogStart;
    /** The distance at which the fog hides everything. */
    public final float fogEnd;
    /** The width of the halo on the floor under a pickup. */
    public final float pickupHaloSize;
    /** The width of the halo on the floor under a security console. */
    public final float consoleHaloSize;
    /** The accent of any door while it opens. */
    public final WorldModels.Accent doorOpeningAccent;

    /** Red, green, blue and alpha by {@link Pickup.Kind#ordinal()}. */
    private final float[][] pickupHalos;
    /** By {@link Pickup.Kind#ordinal()}; null for a pickup drawn without an accent. */
    private final WorldModels.Accent[] pickupAccents;
    private final float[] consoleHaloActive;
    private final float[] consoleHaloLocked;
    /** By {@link Door.LockKind#ordinal()}. */
    private final WorldModels.Accent[] doorAccents;

    private RenderSettings(TuningFile file) {
        clipNear = file.getFloat(CLIP_NEAR);
        if (clipNear <= 0f) throw file.invalid(CLIP_NEAR, "must be positive");
        clipFar = file.getFloat(CLIP_FAR);
        if (clipFar <= clipNear) throw file.invalid(CLIP_FAR, "must be more than " + CLIP_NEAR);

        float[] fog = colour(file, FOG_COLOUR, 3);
        fogRed = fog[0];
        fogGreen = fog[1];
        fogBlue = fog[2];
        fogStart = file.getFloat(FOG_START);
        if (fogStart < 0f) throw file.invalid(FOG_START, "must not be negative");
        fogEnd = file.getFloat(FOG_END);
        if (fogEnd <= fogStart) throw file.invalid(FOG_END, "must be more than " + FOG_START);

        pickupHaloSize = size(file, PICKUP_HALO_SIZE);
        Pickup.Kind[] kinds = Pickup.Kind.values();
        pickupHalos = new float[kinds.length][];
        pickupAccents = new WorldModels.Accent[kinds.length];
        for (Pickup.Kind kind : kinds) {
            pickupHalos[kind.ordinal()] = colour(file, PICKUP_HALO + suffix(kind), 4);
            String key = PICKUP_ACCENT + suffix(kind);
            pickupAccents[kind.ordinal()] = NO_ACCENT.equals(file.getString(key)) ? null : accent(file, key);
        }

        consoleHaloSize = size(file, CONSOLE_HALO_SIZE);
        consoleHaloActive = colour(file, CONSOLE_HALO_ACTIVE, 4);
        consoleHaloLocked = colour(file, CONSOLE_HALO_LOCKED, 4);

        Door.LockKind[] locks = Door.LockKind.values();
        doorAccents = new WorldModels.Accent[locks.length];
        for (Door.LockKind lock : locks) {
            doorAccents[lock.ordinal()] = accent(file, DOOR_ACCENT + suffix(lock));
        }
        doorOpeningAccent = accent(file, DOOR_ACCENT_OPENING);
    }

    /**
     * @param distance a distance from the camera
     * @return how much of the fog covers something that far away, from 0 (none) to 1 (hidden)
     */
    public float fogFactor(float distance) {
        return MathUtils.clamp((distance - fogStart) / (fogEnd - fogStart), 0f, 1f);
    }

    /**
     * @param kind a kind of pickup
     * @return a copy of the red, green, blue and alpha of the halo under it
     */
    public float[] pickupHalo(Pickup.Kind kind) {
        return pickupHalos[kind.ordinal()].clone();
    }

    /**
     * @param kind a kind of pickup
     * @return the accent its model is drawn with, or null if it is drawn without one
     */
    public WorldModels.Accent pickupAccent(Pickup.Kind kind) {
        return pickupAccents[kind.ordinal()];
    }

    /**
     * @param activated whether the console has been switched on
     * @return a copy of the red, green, blue and alpha of the halo under a security console
     */
    public float[] consoleHalo(boolean activated) {
        return (activated ? consoleHaloActive : consoleHaloLocked).clone();
    }

    /**
     * @param lock how a door is locked
     * @return the accent a closed door with that lock is drawn with
     */
    public WorldModels.Accent doorAccent(Door.LockKind lock) {
        return doorAccents[lock.ordinal()];
    }

    /**
     * Reads the settings from {@link #PATH}.
     * @return the loaded settings
     * @throws IllegalArgumentException if the file breaks the format, lacks a key, holds a key this class does not
     *     list, or holds a value that is out of range or names an accent the world palette does not have
     */
    public static RenderSettings load() {
        return new RenderSettings(TuningFile.load(PATH, keys(), new ArrayList<>()));
    }

    /**
     * Parses the text of a settings file.
     * @param assetPath path label included in error messages
     * @param text raw file contents
     * @return the parsed settings
     * @throws IllegalArgumentException as {@link #load} does
     */
    public static RenderSettings parse(String assetPath, String text) {
        return new RenderSettings(TuningFile.parseText(assetPath, text, keys(), new ArrayList<>()));
    }

    private static List<String> keys() {
        List<String> keys = new ArrayList<>();
        keys.add(CLIP_NEAR);
        keys.add(CLIP_FAR);
        keys.add(FOG_COLOUR);
        keys.add(FOG_START);
        keys.add(FOG_END);
        keys.add(PICKUP_HALO_SIZE);
        for (Pickup.Kind kind : Pickup.Kind.values()) keys.add(PICKUP_HALO + suffix(kind));
        for (Pickup.Kind kind : Pickup.Kind.values()) keys.add(PICKUP_ACCENT + suffix(kind));
        keys.add(CONSOLE_HALO_SIZE);
        keys.add(CONSOLE_HALO_ACTIVE);
        keys.add(CONSOLE_HALO_LOCKED);
        for (Door.LockKind lock : Door.LockKind.values()) keys.add(DOOR_ACCENT + suffix(lock));
        keys.add(DOOR_ACCENT_OPENING);
        return keys;
    }

    private static String suffix(Enum<?> kind) {
        return kind.name().toLowerCase(Locale.ROOT);
    }

    private static float size(TuningFile file, String key) {
        float size = file.getFloat(key);
        if (size <= 0f) throw file.invalid(key, "must be positive");
        return size;
    }

    private static float[] colour(TuningFile file, String key, int components) {
        float[] colour = file.getFloats(key, components);
        for (float component : colour) {
            if (component < 0f || component > 1f) {
                throw file.invalid(key, "must hold " + components + " numbers from 0 to 1");
            }
        }
        return colour;
    }

    private static WorldModels.Accent accent(TuningFile file, String key) {
        String name = file.getString(key);
        for (WorldModels.Accent accent : WorldModels.Accent.values()) {
            if (accent.name.equals(name)) return accent;
        }
        throw file.invalid(key, "must name an accent of " + WorldModels.PALETTE_PATH);
    }
}

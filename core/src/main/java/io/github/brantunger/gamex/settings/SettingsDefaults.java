package io.github.brantunger.gamex.settings;

import io.github.brantunger.gamex.data.AssetText;
import io.github.brantunger.gamex.data.TuningFile;

import java.nio.file.Path;

/**
 * What each setting is while nothing is saved, and how far and in what steps the Settings screen moves it.
 * Loaded once at start-up from {@code assets/data/settings.txt} and passed to {@link GameSettings}; tests read the
 * shipped file or hand their own text to {@link #parse}. The player's own choices are not here: they are kept in the
 * saved preferences, which {@link GameSettings#load(KeyValueStore, SettingsDefaults)} reads. The game saves every
 * setting together, changed or not, so a default counts until the settings are first saved and not after.
 *
 * <p>The file holds the keys below and nothing else.
 * <ul>
 *   <li>{@code sensitivity.default}, {@code sensitivity.min}, {@code sensitivity.max}, {@code sensitivity.step}:
 *       the mouse sensitivity, which is kept to two decimal places.</li>
 *   <li>{@code fov.default}, {@code fov.min}, {@code fov.max}, {@code fov.step}: the field of view, which is kept
 *       in whole degrees.</li>
 *   <li>{@code volume.default}, {@code volume.min}, {@code volume.max}, {@code volume.step}: the master volume,
 *       which is kept to two decimal places.</li>
 *   <li>{@code invert_y.default}, {@code view_bob.default}, {@code fullscreen.default}, {@code minimap.default}:
 *       {@code true} or {@code false}.</li>
 * </ul>
 * There is no default behind the file: a missing key, a key this class does not list, a least value above the most,
 * a default outside its range, a range the setting cannot hold (a negative sensitivity, a field of view outside 1 to
 * 179 degrees, a volume outside 0 to 1), a least value, a most value or a default finer than the precision the
 * setting is kept to (a field of view of 72.5, a sensitivity of 0.405) and a step smaller than that precision each
 * fail the load with a message naming the file and the key, and the line where the key has one.
 *
 * <p>Instances are immutable and safe to share.
 */
public final class SettingsDefaults {

    /** Internal asset path of the settings defaults. */
    public static final String PATH = "data/settings.txt";

    /** The smallest step of the mouse sensitivity and of the master volume: both are kept to two decimal places. */
    static final float HUNDREDTH = 0.01f;
    /** The smallest step of the field of view, which is kept in whole degrees. */
    static final float WHOLE = 1f;

    private static final String SENSITIVITY = "sensitivity";
    private static final String FOV = "fov";
    private static final String VOLUME = "volume";
    private static final String DEFAULT = ".default";
    private static final String MIN = ".min";
    private static final String MAX = ".max";
    private static final String STEP = ".step";
    private static final String INVERT_Y = "invert_y" + DEFAULT;
    private static final String VIEW_BOB = "view_bob" + DEFAULT;
    private static final String FULLSCREEN = "fullscreen" + DEFAULT;
    private static final String MINIMAP = "minimap" + DEFAULT;

    /** The default, the range and the step of one numeric setting. */
    public static final class Range {
        /**
         * The value while nothing is saved, within {@link #min} to {@link #max}. Like them it is a value the
         * setting can hold: a whole number or a number of hundredths.
         */
        public final float initial;
        /** The least value the setting takes. */
        public final float min;
        /** The most value the setting takes. */
        public final float max;
        /** How far one press on the Settings row moves the value; at least the precision the setting is kept to. */
        public final float step;

        private Range(TuningFile file, String name, float precision, float lowest, float highest) {
            min = file.getFloat(name + MIN);
            if (min < lowest) throw file.invalid(name + MIN, "must be at least " + number(lowest));
            requireKept(file, name + MIN, min, precision);
            max = file.getFloat(name + MAX);
            if (max < min) throw file.invalid(name + MAX, "must not be less than " + name + MIN);
            if (max > highest) throw file.invalid(name + MAX, "must be at most " + number(highest));
            requireKept(file, name + MAX, max, precision);
            initial = file.getFloat(name + DEFAULT);
            if (initial < min || initial > max) {
                throw file.invalid(name + DEFAULT, "must be from " + name + MIN + " to " + name + MAX);
            }
            requireKept(file, name + DEFAULT, initial, precision);
            step = file.getFloat(name + STEP);
            if (step < precision) {
                throw file.invalid(name + STEP, "must be at least " + number(precision)
                    + ", the precision the setting is kept to");
            }
        }

        /**
         * @param value a value of the setting
         * @return the value brought into {@link #min} to {@link #max}; {@link #initial} for a NaN or an infinity
         */
        public float clamp(float value) {
            float finite = Float.isFinite(value) ? value : initial;
            return Math.max(min, Math.min(max, finite));
        }

        /** Rejects a value the setting cannot hold as it is: {@link GameSettings} would round it to another one. */
        private static void requireKept(TuningFile file, String key, float value, float precision) {
            boolean whole = precision == WHOLE;
            float kept = whole ? Math.round(value) : Math.round(value * 100f) / 100f;
            if (kept != value) {
                throw file.invalid(key, whole ? "must be a whole number, which is how the setting is kept"
                    : "must have no more than two decimal places, which is how the setting is kept");
            }
        }

        private static String number(float value) {
            return value == Math.rint(value) ? Integer.toString((int) value) : Float.toString(value);
        }
    }

    /** The mouse sensitivity. */
    public final Range sensitivity;
    /** The field of view, in degrees. */
    public final Range fov;
    /** The master volume, from 0 to 1. */
    public final Range volume;
    /** Whether looking up and down is inverted while nothing is saved. */
    public final boolean invertY;
    /** Whether the view bobs with the player's steps while nothing is saved. */
    public final boolean viewBob;
    /** Whether the game is fullscreen while nothing is saved. */
    public final boolean fullscreen;
    /** Whether the HUD minimap is shown while nothing is saved. */
    public final boolean minimap;

    private SettingsDefaults(TuningFile file) {
        sensitivity = new Range(file, SENSITIVITY, HUNDREDTH, 0f, Float.MAX_VALUE);
        fov = new Range(file, FOV, WHOLE, 1f, 179f);
        volume = new Range(file, VOLUME, HUNDREDTH, 0f, 1f);
        invertY = file.getBoolean(INVERT_Y);
        viewBob = file.getBoolean(VIEW_BOB);
        fullscreen = file.getBoolean(FULLSCREEN);
        minimap = file.getBoolean(MINIMAP);
    }

    /**
     * Reads the defaults from {@link #PATH}.
     * @return the loaded defaults
     * @throws IllegalArgumentException if the file breaks the format, lacks a key, holds a key this class does not
     *     list, or holds a range, a default or a step that the class comment rules out
     */
    public static SettingsDefaults load() {
        return new SettingsDefaults(TuningFile.load(PATH, keys()));
    }

    /**
     * Reads the defaults from {@link #PATH} without libGDX, from the places libGDX will read the game's files from
     * once it is up (see {@link AssetText#readAsInternal}): under a working directory if the file is there, and
     * from the classpath otherwise. This is how the desktop launcher reads them.
     * @param workingDir the directory that stands for the working directory
     * @return the loaded defaults
     * @throws IllegalArgumentException as {@link #load()} does
     * @throws IllegalStateException if the file is in neither place
     */
    public static SettingsDefaults load(Path workingDir) {
        return parse(PATH, AssetText.readAsInternal("data", PATH, workingDir));
    }

    /**
     * Parses the text of a settings defaults file.
     * @param assetPath path label included in error messages
     * @param text raw file contents
     * @return the parsed defaults
     * @throws IllegalArgumentException as {@link #load()} does
     */
    public static SettingsDefaults parse(String assetPath, String text) {
        return new SettingsDefaults(TuningFile.parseText(assetPath, text, keys()));
    }

    private static String[] keys() {
        return new String[] {
            SENSITIVITY + DEFAULT, SENSITIVITY + MIN, SENSITIVITY + MAX, SENSITIVITY + STEP,
            FOV + DEFAULT, FOV + MIN, FOV + MAX, FOV + STEP,
            VOLUME + DEFAULT, VOLUME + MIN, VOLUME + MAX, VOLUME + STEP,
            INVERT_Y, VIEW_BOB, FULLSCREEN, MINIMAP
        };
    }
}

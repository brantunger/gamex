package io.github.brantunger.gamex.audio;

import io.github.brantunger.gamex.data.TuningFile;

import java.util.ArrayList;
import java.util.EnumMap;
import java.util.List;
import java.util.Map;

/**
 * The playback volume of every sound cue, before {@link SoundManager} applies the master volume, the event's
 * intensity and distance attenuation. Loaded once at start-up from {@code assets/data/sound-volumes.txt} and passed
 * to {@link SoundManager}; tests build their own with the constructor or {@link #parse}.
 *
 * <p>The file holds one {@code key: value} line per {@link SoundEvent.Type} and nothing else. A cue's key is its file
 * name in {@code assets/sounds/} without {@code .wav} ({@code PLAYER_JUMP} is {@code player-jump}, see {@link #key}).
 * There is no default: a cue without a line, a key that names no cue and a negative gain each fail the load with a
 * message naming the file and the key.
 *
 * <p>Instances are immutable and safe to share.
 */
public final class SoundVolumes {

    /** Internal asset path of the volume table. */
    public static final String PATH = "data/sound-volumes.txt";

    private static final String WAV_SUFFIX = ".wav";

    /** Gains indexed by {@link SoundEvent.Type#ordinal()}. */
    private final float[] gains;

    /**
     * @param gains the gain of every {@link SoundEvent.Type}, each finite and zero or more
     * @throws IllegalArgumentException if a type has no gain, or a gain is negative or not finite
     */
    public SoundVolumes(Map<SoundEvent.Type, Float> gains) {
        SoundEvent.Type[] types = SoundEvent.Type.values();
        this.gains = new float[types.length];
        for (SoundEvent.Type type : types) {
            Float gain = gains.get(type);
            if (gain == null) {
                throw new IllegalArgumentException("Missing sound volume for " + type);
            }
            if (!Float.isFinite(gain) || gain < 0f) {
                throw new IllegalArgumentException("Sound volume for " + type + " must be finite and zero or more, "
                    + "got " + gain);
            }
            this.gains[type.ordinal()] = gain;
        }
    }

    /** @return the gain of {@code type}, zero or more */
    public float gain(SoundEvent.Type type) {
        return gains[type.ordinal()];
    }

    /** @return the key of {@code type} in the volume table: its file name in {@code assets/sounds/} without .wav */
    public static String key(SoundEvent.Type type) {
        String fileName = SoundManager.fileName(type);
        return fileName.substring(0, fileName.length() - WAV_SUFFIX.length());
    }

    /**
     * Reads the volume table from {@link #PATH}.
     * @return the loaded volumes
     * @throws IllegalArgumentException if the file breaks the format, lacks a cue, holds a key that names no cue,
     *     or holds a gain that is not a number or is negative
     */
    public static SoundVolumes load() {
        return from(TuningFile.load(PATH, keys(), new ArrayList<>()));
    }

    /**
     * Parses the text of a volume table.
     * @param assetPath path label included in error messages
     * @param text raw file contents
     * @return the parsed volumes
     * @throws IllegalArgumentException as {@link #load} does
     */
    public static SoundVolumes parse(String assetPath, String text) {
        return from(TuningFile.parseText(assetPath, text, keys(), new ArrayList<>()));
    }

    private static List<String> keys() {
        List<String> keys = new ArrayList<>();
        for (SoundEvent.Type type : SoundEvent.Type.values()) {
            keys.add(key(type));
        }
        return keys;
    }

    private static SoundVolumes from(TuningFile file) {
        Map<SoundEvent.Type, Float> gains = new EnumMap<>(SoundEvent.Type.class);
        for (SoundEvent.Type type : SoundEvent.Type.values()) {
            String key = key(type);
            float gain = file.getFloat(key);
            if (gain < 0f) throw file.invalid(key, "must be zero or more");
            gains.put(type, gain);
        }
        return new SoundVolumes(gains);
    }
}

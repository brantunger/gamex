package io.github.brantunger.gamex.model;

import io.github.brantunger.gamex.data.TuningFile;

import java.util.ArrayList;
import java.util.Collection;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * The colours of one model family: a colour and gloss for each material its model files may use, and the accent
 * colours a renderer puts on its {@code # @accent} surfaces. Each family has its own palette file in
 * {@code assets/data/} ({@code palette-world.txt}, {@code palette-pistol.txt}, ...), because the families reuse
 * material names with different colours. The class that owns the family ({@code WorldModels}, {@code PistolModel},
 * ...) names the file and the materials and accents it must hold; the game loads each palette once at start-up and
 * passes it to the code that packs and draws the models, and tests build their own with the constructor or
 * {@link #parse}.
 *
 * <p>The file holds one {@code material.<name>: red, green, blue, gloss} line per material, where the name is the
 * one the model files use in their {@code usemtl} lines, and one {@code accent.<name>: red, green, blue, gloss} line
 * per accent. Every number is from 0 to 1. There is no default: a material or accent without a line, a line whose
 * name the family does not know and a value that is not four numbers from 0 to 1 each fail the load with a message
 * naming the file and the key.
 *
 * <p>Instances are immutable and safe to share.
 */
public final class ModelPalette {

    /** How many numbers a colour holds: red, green, blue and gloss. */
    public static final int COMPONENTS = 4;

    /** The start of the key of a material's line. */
    public static final String MATERIAL_PREFIX = "material.";
    /** The start of the key of an accent's line. */
    public static final String ACCENT_PREFIX = "accent.";

    private final String label;
    private final Map<String, float[]> materials;
    private final Map<String, float[]> accents;

    /**
     * @param label what the palette colours, for error messages (for example {@code "world"})
     * @param materials the red, green, blue and gloss of each material, by the name the model files use
     * @param accents the red, green, blue and gloss of each accent, by name
     * @throws IllegalArgumentException if a colour is not four finite numbers from 0 to 1
     */
    public ModelPalette(String label, Map<String, float[]> materials, Map<String, float[]> accents) {
        this.label = label;
        this.materials = copy(label, "material", materials);
        this.accents = copy(label, "accent", accents);
    }

    private static Map<String, float[]> copy(String label, String noun, Map<String, float[]> colours) {
        Map<String, float[]> out = new LinkedHashMap<>();
        for (Map.Entry<String, float[]> entry : colours.entrySet()) {
            float[] colour = entry.getValue();
            if (colour == null || colour.length != COMPONENTS || !inRange(colour)) {
                throw new IllegalArgumentException("The " + label + " palette's " + noun + " '" + entry.getKey()
                    + "' must be red, green, blue and gloss, each from 0 to 1");
            }
            out.put(entry.getKey(), colour.clone());
        }
        return out;
    }

    private static boolean inRange(float[] colour) {
        for (float component : colour) {
            // Written so that NaN fails too.
            if (!(component >= 0f && component <= 1f)) return false;
        }
        return true;
    }

    /**
     * @param material a material name from a model file
     * @return a copy of the material's red, green, blue and gloss
     * @throws IllegalArgumentException if the palette has no such material
     */
    public float[] colour(String material) {
        float[] colour = materials.get(material);
        if (colour == null) {
            throw new IllegalArgumentException("The " + label + " palette has no colour for the material '"
                + material + "'");
        }
        return colour.clone();
    }

    /**
     * @param accent an accent name
     * @return a copy of the accent's red, green, blue and gloss
     * @throws IllegalArgumentException if the palette has no such accent
     */
    public float[] accent(String accent) {
        float[] colour = accents.get(accent);
        if (colour == null) {
            throw new IllegalArgumentException("The " + label + " palette has no accent '" + accent + "'");
        }
        return colour.clone();
    }

    /**
     * Reads a palette file.
     * @param label what the palette colours, for error messages
     * @param assetPath internal path of the file inside {@code assets/}
     * @param materialNames the materials the file must hold, and no others
     * @param accentNames the accents the file must hold, and no others
     * @return the loaded palette
     * @throws IllegalArgumentException if the file breaks the format, lacks a material or accent, holds a name
     *     that is in neither list, or holds a value that is not four numbers from 0 to 1
     */
    public static ModelPalette load(String label, String assetPath, Collection<String> materialNames,
                                    Collection<String> accentNames) {
        return from(label, TuningFile.load(assetPath, keys(materialNames, accentNames), new ArrayList<>()),
            materialNames, accentNames);
    }

    /**
     * Parses the text of a palette file.
     * @param label what the palette colours, for error messages
     * @param assetPath path label included in error messages
     * @param text raw file contents
     * @param materialNames the materials the text must hold, and no others
     * @param accentNames the accents the text must hold, and no others
     * @return the parsed palette
     * @throws IllegalArgumentException as {@link #load} does
     */
    public static ModelPalette parse(String label, String assetPath, String text, Collection<String> materialNames,
                                     Collection<String> accentNames) {
        return from(label,
            TuningFile.parseText(assetPath, text, keys(materialNames, accentNames), new ArrayList<>()),
            materialNames, accentNames);
    }

    private static List<String> keys(Collection<String> materialNames, Collection<String> accentNames) {
        List<String> keys = new ArrayList<>();
        for (String name : materialNames) keys.add(MATERIAL_PREFIX + name);
        for (String name : accentNames) keys.add(ACCENT_PREFIX + name);
        return keys;
    }

    private static ModelPalette from(String label, TuningFile file, Collection<String> materialNames,
                                     Collection<String> accentNames) {
        return new ModelPalette(label, read(file, MATERIAL_PREFIX, materialNames),
            read(file, ACCENT_PREFIX, accentNames));
    }

    private static Map<String, float[]> read(TuningFile file, String prefix, Collection<String> names) {
        Map<String, float[]> colours = new LinkedHashMap<>();
        for (String name : names) {
            String key = prefix + name;
            float[] colour = file.getFloats(key, COMPONENTS);
            if (!inRange(colour)) throw file.invalid(key, "must hold four numbers from 0 to 1");
            colours.put(name, colour);
        }
        return colours;
    }
}

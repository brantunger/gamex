package io.github.brantunger.gamex.weapon;

import io.github.brantunger.gamex.model.ModelPalette;
import io.github.brantunger.gamex.model.ModelVertices;
import io.github.brantunger.gamex.model.ObjModel;

import java.util.ArrayList;
import java.util.List;

/**
 * The sculpted first-person hand, wrist, cuff and sleeve. The shape lives in a model file, in the same gun space as
 * {@link PistolModel}; this class knows where that file is and which materials it may use, and turns the model
 * into the vertex layout the pistol uses, with the colours of a {@link ModelPalette}. It has no graphics types.
 */
public final class HandModel {

    /** The path of the model file inside the assets folder. */
    public static final String ASSET_PATH = "models/viewmodel-hand.obj";
    /** The path of the file inside the assets folder that gives each {@link Material} its colour and gloss. */
    public static final String PALETTE_PATH = "data/palette-hand.txt";

    private static final String PALETTE_LABEL = "hand";

    /** The materials the model file may use. {@link #PALETTE_PATH} holds the colour and gloss of each. */
    public enum Material {
        /** The dark fabric of the glove. */
        GLOVE("glove"),
        /** The padding over the knuckles, a touch lighter than the glove. */
        PADS("pads"),
        /** The stiffer band that closes the glove at the wrist. */
        CUFF("cuff"),
        /** The dark strap round the cuff. */
        STRAP("strap"),
        /** The olive fabric of the sleeve. */
        SLEEVE("sleeve");

        /** The name the model file uses for this material in its {@code usemtl} lines. */
        public final String name;

        Material(String name) {
            this.name = name;
        }

        /**
         * @param name a material name from a model file
         * @return the material of that name
         * @throws IllegalArgumentException if there is none
         */
        public static Material named(String name) {
            for (Material material : values()) {
                if (material.name.equals(name)) return material;
            }
            throw new IllegalArgumentException("The hand model uses a material this game has no colour for: '"
                + name + "'");
        }
    }

    private HandModel() {}

    /**
     * Reads the colours of the hand from {@link #PALETTE_PATH}.
     * @return the loaded palette
     * @throws IllegalArgumentException if the file breaks the format, lacks a material, holds a name
     *     this class does not list, or holds a value that is not four numbers from 0 to 1
     */
    public static ModelPalette loadPalette() {
        return ModelPalette.load(PALETTE_LABEL, PALETTE_PATH, materialNames(), accentNames());
    }

    /**
     * Parses the text of a palette file of the hand.
     * @param assetPath path label included in error messages
     * @param text raw file contents
     * @return the parsed palette
     * @throws IllegalArgumentException as {@link #loadPalette} does
     */
    public static ModelPalette parsePalette(String assetPath, String text) {
        return ModelPalette.parse(PALETTE_LABEL, assetPath, text, materialNames(), accentNames());
    }

    private static List<String> materialNames() {
        List<String> names = new ArrayList<>();
        for (Material material : Material.values()) names.add(material.name);
        return names;
    }

    private static List<String> accentNames() {
        return new ArrayList<>();
    }

    private static float[] colour(ModelPalette palette, String material) {
        return palette.colour(Material.named(material).name);
    }

    /**
     * Turns the hand model into triangles in the layout of {@link PistolModel}: {@link PistolModel#VERTEX_SIZE}
     * floats per vertex, being position, normal, colour and gloss. The normals are the model's own, so its smooth
     * surfaces stay smooth.
     * @param model the parsed model file
     * @param palette the colours of the hand's materials
     * @return three vertices per triangle
     * @throws IllegalArgumentException if a face of the model has a material that {@link Material} does not list
     */
    public static float[] vertices(ObjModel model, ModelPalette palette) {
        return ModelVertices.pack(model, name -> colour(palette, name));
    }

    /**
     * Turns the hand model into deduplicated indexed vertices and 16-bit triangle indices.
     * @param model the parsed model file
     * @param palette the colours of the hand's materials
     * @return indexed vertex and index buffers
     */
    public static ModelVertices.IndexedMesh packIndexed(ObjModel model, ModelPalette palette) {
        return ModelVertices.packIndexed(model, name -> colour(palette, name));
    }
}

package io.github.brantunger.gamex.weapon;

import io.github.brantunger.gamex.model.ModelVertices;
import io.github.brantunger.gamex.model.ObjModel;

/**
 * The sculpted first-person hand, wrist, cuff and sleeve. The shape lives in a model file, in the same gun space as
 * {@link PistolModel}; this class knows where that file is and gives each of its materials a colour, turning the
 * model into the vertex layout the pistol uses. It has no graphics types.
 */
public final class HandModel {

    /** The path of the model file inside the assets folder. */
    public static final String ASSET_PATH = "models/viewmodel-hand.obj";

    /** The materials the model file may use. */
    public enum Material {
        /** The dark fabric of the glove. */
        GLOVE("glove", 0.200f, 0.202f, 0.220f, 0f),
        /** The padding over the knuckles, a touch lighter than the glove. */
        PADS("pads", 0.250f, 0.252f, 0.272f, 0f),
        /** The stiffer band that closes the glove at the wrist. */
        CUFF("cuff", 0.270f, 0.270f, 0.295f, 0f),
        /** The dark strap round the cuff. */
        STRAP("strap", 0.100f, 0.100f, 0.115f, 0f),
        /** The olive fabric of the sleeve. */
        SLEEVE("sleeve", 0.330f, 0.370f, 0.220f, 0f);

        /** The name the model file uses for this material in its {@code usemtl} lines. */
        public final String name;
        /** The red, green and blue of the material, and its gloss from 0 (matt) to 1 (polished). */
        final float[] colour;

        Material(String name, float red, float green, float blue, float gloss) {
            this.name = name;
            this.colour = new float[] {red, green, blue, gloss};
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
     * Turns the hand model into triangles in the layout of {@link PistolModel}: {@link PistolModel#VERTEX_SIZE}
     * floats per vertex, being position, normal, colour and gloss. The normals are the model's own, so its smooth
     * surfaces stay smooth.
     * @param model the parsed model file
     * @return three vertices per triangle
     * @throws IllegalArgumentException if a face of the model has a material that {@link Material} does not list
     */
    public static float[] vertices(ObjModel model) {
        return ModelVertices.pack(model, name -> Material.named(name).colour);
    }
}

package io.github.brantunger.gamex.model;

import java.util.function.Function;

/**
 * Packs parsed {@link ObjModel} triangles into flat float arrays of position, normal, colour and gloss. It uses no
 * graphics types, so vertex packing can be tested without a GL context.
 */
public final class ModelVertices {

    /** The number of floats per vertex: position (3), normal (3), colour (3) and gloss (1). */
    public static final int VERTEX_SIZE = 10;

    private ModelVertices() {}

    /**
     * Parses OBJ text and converts it with {@code converter}, naming the file and model role if either step fails.
     * @param assetPath the path of the model file, to name in an error message
     * @param label what the model is (for example {@code "hand"} or {@code "pistol"})
     * @param text the Wavefront OBJ contents
     * @param converter turns the parsed {@link ObjModel} into the caller's representation
     * @param <T> the converted representation
     * @return the converted model
     * @throws IllegalArgumentException if the OBJ text or one of its materials or groups is invalid; the message names
     *     {@code label}, {@code assetPath}, and the parser or converter failure
     */
    public static <T> T read(String assetPath, String label, String text, Function<ObjModel, T> converter) {
        try {
            return converter.apply(ObjModel.parse(text));
        } catch (IllegalArgumentException e) {
            throw new IllegalArgumentException("The " + label + " model " + assetPath + " could not be read: "
                + e.getMessage(), e);
        }
    }

    /**
     * Converts every triangle of a model into packed vertices.
     * @param model the parsed model
     * @param materialColour maps each triangle's material name to four floats: red, green, blue and gloss
     * @return three vertices per triangle
     */
    public static float[] pack(ObjModel model, Function<String, float[]> materialColour) {
        float[] out = new float[model.getTriangleCount() * 3 * VERTEX_SIZE];
        int at = 0;
        for (int triangle = 0; triangle < model.getTriangleCount(); triangle++) {
            writeTriangle(model, triangle, materialColour.apply(model.getMaterial(triangle)), out, at);
            at += 3 * VERTEX_SIZE;
        }
        return out;
    }

    /**
     * Writes one triangle's three vertices into {@code out} starting at {@code floatOffset}.
     * @param model the parsed model
     * @param triangle the triangle index
     * @param colour four floats: red, green, blue and gloss
     * @param out the destination array
     * @param floatOffset the index in {@code out} at which to write the first vertex
     */
    public static void writeTriangle(ObjModel model, int triangle, float[] colour, float[] out, int floatOffset) {
        float[] positions = model.getPositions();
        float[] normals = model.getNormals();
        int at = floatOffset;
        for (int corner = 0; corner < 3; corner++) {
            int from = (triangle * 3 + corner) * 3;
            out[at++] = positions[from];
            out[at++] = positions[from + 1];
            out[at++] = positions[from + 2];
            out[at++] = normals[from];
            out[at++] = normals[from + 1];
            out[at++] = normals[from + 2];
            out[at++] = colour[0];
            out[at++] = colour[1];
            out[at++] = colour[2];
            out[at++] = colour[3];
        }
    }
}

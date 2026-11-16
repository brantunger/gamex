package io.github.brantunger.gamex.model;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

/**
 * A triangle mesh read from Wavefront OBJ text. It has no graphics types, so it can be parsed and checked without a
 * GL context.
 *
 * <p>The parser understands the part of the format that a sculpted model needs: {@code v} positions, {@code vn}
 * normals, {@code f} faces of three or more corners written as {@code v}, {@code v/vt}, {@code v//vn} or
 * {@code v/vt/vn} with indices counted from 1 or, when negative, back from the latest, {@code g} and {@code o} group
 * names and {@code usemtl} material names. Faces with more than three corners are split into a fan of triangles. A
 * face without normals gets the flat normal of its first triangle. {@code vt}, {@code mtllib} and {@code s} lines
 * and comments are read past. Anything else is an error, so a file that uses a feature this parser lacks is not
 * silently drawn wrong.
 */
public final class ObjModel {

    /** The material name of faces that come before any {@code usemtl} line. */
    public static final String NO_MATERIAL = "";
    /** The group name of faces that come before any {@code g} or {@code o} line. */
    public static final String NO_GROUP = "";

    private final int vertexCount;
    private final float[] positions;
    private final float[] normals;
    private final String[] materials;
    private final String[] groups;

    private ObjModel(int vertexCount, float[] positions, float[] normals, String[] materials, String[] groups) {
        this.vertexCount = vertexCount;
        this.positions = positions;
        this.normals = normals;
        this.materials = materials;
        this.groups = groups;
    }

    /** @return the number of {@code v} lines in the file: the distinct vertices of the model. */
    public int getVertexCount() {
        return vertexCount;
    }

    /** @return the number of triangles, after faces with more corners have been split. */
    public int getTriangleCount() {
        return materials.length;
    }

    /** @return the corner positions: nine floats per triangle, x, y, z for each of its three corners in order. */
    public float[] getPositions() {
        return positions;
    }

    /** @return the corner normals, laid out like {@link #getPositions()}; each is a unit vector. */
    public float[] getNormals() {
        return normals;
    }

    /**
     * @param triangle the index of a triangle
     * @return the name of its material, or {@link #NO_MATERIAL}
     */
    public String getMaterial(int triangle) {
        return materials[triangle];
    }

    /**
     * @param triangle the index of a triangle
     * @return the name of its group, or {@link #NO_GROUP}
     */
    public String getGroup(int triangle) {
        return groups[triangle];
    }

    /**
     * Parses OBJ text.
     * @param text the contents of an OBJ file
     * @return the model
     * @throws IllegalArgumentException if a line is malformed, refers to a vertex or normal that does not exist, or
     *     uses a part of the format this parser does not support; the message names the line
     */
    public static ObjModel parse(String text) {
        List<float[]> vertices = new ArrayList<>();
        List<float[]> vertexNormals = new ArrayList<>();
        FloatList positions = new FloatList();
        FloatList normals = new FloatList();
        List<String> materials = new ArrayList<>();
        List<String> groups = new ArrayList<>();
        String material = NO_MATERIAL;
        String group = NO_GROUP;

        String[] lines = text.split("\n", -1);
        for (int number = 1; number <= lines.length; number++) {
            String line = lines[number - 1];
            int comment = line.indexOf('#');
            if (comment >= 0) line = line.substring(0, comment);
            line = line.trim();
            if (line.isEmpty()) continue;
            String[] words = line.split("\\s+");
            switch (words[0]) {
                case "v":
                    vertices.add(vector(words, number));
                    break;
                case "vn":
                    vertexNormals.add(unit(vector(words, number), number));
                    break;
                case "f":
                    face(words, number, vertices, vertexNormals, positions, normals);
                    for (int i = 0; i < words.length - 3; i++) {
                        materials.add(material);
                        groups.add(group);
                    }
                    break;
                case "g":
                case "o":
                    group = words.length > 1 ? line.substring(words[0].length()).trim() : NO_GROUP;
                    break;
                case "usemtl":
                    material = words.length > 1 ? words[1] : NO_MATERIAL;
                    break;
                case "vt":
                case "mtllib":
                case "s":
                    break;
                default:
                    throw new IllegalArgumentException("Line " + number + ": unsupported OBJ statement '" + words[0]
                        + "'");
            }
        }
        return new ObjModel(vertices.size(), positions.toArray(), normals.toArray(),
            materials.toArray(new String[0]), groups.toArray(new String[0]));
    }

    private static float[] vector(String[] words, int number) {
        if (words.length < 4) {
            throw new IllegalArgumentException("Line " + number + ": '" + words[0] + "' needs three numbers");
        }
        float[] vector = new float[3];
        for (int i = 0; i < 3; i++) {
            try {
                vector[i] = Float.parseFloat(words[i + 1]);
            } catch (NumberFormatException e) {
                throw new IllegalArgumentException("Line " + number + ": '" + words[i + 1] + "' is not a number", e);
            }
            if (Float.isNaN(vector[i]) || Float.isInfinite(vector[i])) {
                throw new IllegalArgumentException("Line " + number + ": '" + words[i + 1] + "' is not finite");
            }
        }
        return vector;
    }

    private static float[] unit(float[] vector, int number) {
        float length = (float) Math.sqrt(vector[0] * vector[0] + vector[1] * vector[1] + vector[2] * vector[2]);
        if (length < 1e-12f) throw new IllegalArgumentException("Line " + number + ": a normal of zero length");
        return new float[] {vector[0] / length, vector[1] / length, vector[2] / length};
    }

    /** Reads one face and appends its fan of triangles. */
    private static void face(String[] words, int number, List<float[]> vertices, List<float[]> vertexNormals,
                             FloatList positions, FloatList normals) {
        int corners = words.length - 1;
        if (corners < 3) throw new IllegalArgumentException("Line " + number + ": a face needs at least three corners");
        float[][] cornerPositions = new float[corners][];
        float[][] cornerNormals = new float[corners][];
        boolean allHaveNormals = true;
        for (int i = 0; i < corners; i++) {
            String[] parts = words[i + 1].split("/", -1);
            if (parts.length > 3) {
                throw new IllegalArgumentException("Line " + number + ": '" + words[i + 1] + "' is not a face corner");
            }
            cornerPositions[i] = vertices.get(index(parts[0], vertices.size(), number, "vertex"));
            if (parts.length == 3 && !parts[2].isEmpty()) {
                cornerNormals[i] = vertexNormals.get(index(parts[2], vertexNormals.size(), number, "normal"));
            } else {
                allHaveNormals = false;
            }
        }
        float[] flat = null;
        if (!allHaveNormals) {
            flat = unit(cross(cornerPositions[0], cornerPositions[1], cornerPositions[2]), number);
        }
        for (int i = 1; i < corners - 1; i++) {
            for (int corner : new int[] {0, i, i + 1}) {
                positions.add(cornerPositions[corner]);
                normals.add(flat != null ? flat : cornerNormals[corner]);
            }
        }
    }

    /** Turns an OBJ index, counted from 1 or negative from the end, into an index counted from 0. */
    private static int index(String word, int count, int number, String what) {
        int value;
        try {
            value = Integer.parseInt(word);
        } catch (NumberFormatException e) {
            throw new IllegalArgumentException("Line " + number + ": '" + word + "' is not a " + what + " index", e);
        }
        int index = value < 0 ? count + value : value - 1;
        if (value == 0 || index < 0 || index >= count) {
            throw new IllegalArgumentException("Line " + number + ": there is no " + what + " " + value
                + " (" + count + " so far)");
        }
        return index;
    }

    private static float[] cross(float[] a, float[] b, float[] c) {
        float ux = b[0] - a[0];
        float uy = b[1] - a[1];
        float uz = b[2] - a[2];
        float vx = c[0] - a[0];
        float vy = c[1] - a[1];
        float vz = c[2] - a[2];
        return new float[] {uy * vz - uz * vy, uz * vx - ux * vz, ux * vy - uy * vx};
    }

    /** A growing float array. */
    private static final class FloatList {
        private float[] items = new float[4096];
        private int size;

        void add(float[] three) {
            if (size + 3 > items.length) items = Arrays.copyOf(items, items.length * 2);
            items[size++] = three[0];
            items[size++] = three[1];
            items[size++] = three[2];
        }

        float[] toArray() {
            return Arrays.copyOf(items, size);
        }
    }
}

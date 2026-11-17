package io.github.brantunger.gamex.model;

import java.util.Arrays;
import java.util.HashMap;
import java.util.Map;
import java.util.function.Function;
import java.util.function.IntPredicate;

/**
 * Packs parsed {@link ObjModel} triangles into flat or indexed float arrays of position, normal, colour, gloss and
 * accent mask. It uses no graphics types, so vertex packing can be tested without a GL context.
 */
public final class ModelVertices {

    /** The number of floats per vertex: position (3), normal (3), colour (3), gloss (1) and accent mask (1). */
    public static final int VERTEX_SIZE = 11;
    /** Maximum unique vertices supported by a 16-bit {@code GL_UNSIGNED_SHORT} GPU index buffer. */
    public static final int MAX_INDEXED_VERTICES = 65_535;

    /** Deduplicated GPU vertex data and 16-bit triangle index buffer. */
    public static final class IndexedMesh {
        /** Interleaved vertex attributes of {@link #VERTEX_SIZE} floats per vertex. */
        public final float[] vertices;
        /** Triangle indices referencing {@link #vertices}. */
        public final short[] indices;

        IndexedMesh(float[] vertices, short[] indices) {
            this.vertices = vertices;
            this.indices = indices;
        }

        /** @return number of unique vertices in {@link #vertices}. */
        public int vertexCount() {
            return vertices.length / VERTEX_SIZE;
        }

        /** @return number of triangle corner indices in {@link #indices}. */
        public int indexCount() {
            return indices.length;
        }

        /** @return number of indexed triangles. */
        public int triangleCount() {
            return indices.length / 3;
        }
    }

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
        float accent = model.isAccentMaterial(model.getMaterial(triangle)) ? 1f : 0f;
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
            out[at++] = accent;
        }
    }

    /**
     * Packs every triangle in {@code model} into deduplicated vertices and 16-bit triangle indices around origin.
     * @param model parsed model
     * @param materialColour maps each triangle's material name to four floats: red, green, blue and gloss
     * @return indexed vertex and index arrays
     */
    public static IndexedMesh packIndexed(ObjModel model, Function<String, float[]> materialColour) {
        return packIndexed(model, t -> true, 0f, 0f, 0f, materialColour);
    }

    /**
     * Packs selected triangles into deduplicated vertices translated by {@code (-originX, -originY, -originZ)} and
     * 16-bit triangle indices.
     * @param model parsed model
     * @param triangleFilter selects which triangles belong to this mesh
     * @param originX local pivot X subtracted from every vertex position
     * @param originY local pivot Y subtracted from every vertex position
     * @param originZ local pivot Z subtracted from every vertex position
     * @param materialColour maps each selected triangle's material name to four floats: red, green, blue and gloss
     * @return indexed vertex and 16-bit index buffer
     * @throws IllegalArgumentException if zero triangles match or unique vertices exceed {@value #MAX_INDEXED_VERTICES}
     */
    public static IndexedMesh packIndexed(ObjModel model, IntPredicate triangleFilter,
                                          float originX, float originY, float originZ,
                                          Function<String, float[]> materialColour) {
        float[] positions = model.getPositions();
        float[] normals = model.getNormals();
        int totalTriangles = model.getTriangleCount();
        Map<VertexKey, Integer> uniqueMap = new HashMap<>();
        float[] vertices = new float[256 * VERTEX_SIZE];
        short[] indices = new short[256 * 3];
        int uniqueCount = 0;
        int indexCount = 0;

        for (int t = 0; t < totalTriangles; t++) {
            if (!triangleFilter.test(t)) continue;
            String mat = model.getMaterial(t);
            float[] colour = materialColour.apply(mat);
            float accent = model.isAccentMaterial(mat) ? 1f : 0f;
            for (int corner = 0; corner < 3; corner++) {
                int from = (t * 3 + corner) * 3;
                float px = positions[from] - originX;
                float py = positions[from + 1] - originY;
                float pz = positions[from + 2] - originZ;
                float nx = normals[from];
                float ny = normals[from + 1];
                float nz = normals[from + 2];
                VertexKey key = new VertexKey(px, py, pz, nx, ny, nz,
                    colour[0], colour[1], colour[2], colour[3], accent);
                Integer existing = uniqueMap.get(key);
                int idx;
                if (existing != null) {
                    idx = existing;
                } else {
                    if (uniqueCount >= MAX_INDEXED_VERTICES) {
                        throw new IllegalArgumentException(
                            "Indexed mesh exceeds 16-bit vertex limit (" + MAX_INDEXED_VERTICES + ")");
                    }
                    idx = uniqueCount++;
                    uniqueMap.put(key, idx);
                    int dst = idx * VERTEX_SIZE;
                    if (dst + VERTEX_SIZE > vertices.length) {
                        vertices = Arrays.copyOf(vertices, vertices.length * 2);
                    }
                    vertices[dst] = px;
                    vertices[dst + 1] = py;
                    vertices[dst + 2] = pz;
                    vertices[dst + 3] = nx;
                    vertices[dst + 4] = ny;
                    vertices[dst + 5] = nz;
                    vertices[dst + 6] = colour[0];
                    vertices[dst + 7] = colour[1];
                    vertices[dst + 8] = colour[2];
                    vertices[dst + 9] = colour[3];
                    vertices[dst + 10] = accent;
                }
                if (indexCount >= indices.length) {
                    indices = Arrays.copyOf(indices, indices.length * 2);
                }
                indices[indexCount++] = (short) idx;
            }
        }
        if (indexCount == 0) {
            throw new IllegalArgumentException("No triangles matched indexed mesh filter");
        }
        return new IndexedMesh(Arrays.copyOf(vertices, uniqueCount * VERTEX_SIZE),
            Arrays.copyOf(indices, indexCount));
    }

    private static final class VertexKey {
        private final int[] bits;
        private final int hash;

        VertexKey(float a, float b, float c, float d, float e, float f,
                  float g, float h, float i, float j, float k) {
            bits = new int[] {
                bit(a), bit(b), bit(c), bit(d), bit(e), bit(f), bit(g), bit(h), bit(i), bit(j), bit(k)
            };
            this.hash = Arrays.hashCode(bits);
        }

        private static int bit(float value) {
            return value == 0f ? 0 : Float.floatToIntBits(value);
        }

        @Override
        public int hashCode() {
            return hash;
        }

        @Override
        public boolean equals(Object obj) {
            return obj instanceof VertexKey && Arrays.equals(bits, ((VertexKey) obj).bits);
        }
    }
}

package io.github.brantunger.gamex.weapon;

import io.github.brantunger.gamex.model.ModelVertices;
import io.github.brantunger.gamex.model.ObjModel;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

/**
 * The first-person combat scattergun, loaded from a Wavefront OBJ file and packed as plain float arrays without
 * graphics types. Shares {@link PistolModel}'s rear grip envelope so {@link HandModel} fits cleanly without clipping,
 * while splitting the sliding forend pump into {@link Group#PUMP}.
 */
public final class ScattergunModel {

    /** Path of the model file inside the assets folder. */
    public static final String ASSET_PATH = "models/viewmodel-scattergun.obj";
    /** Number of floats per packed vertex. */
    public static final int VERTEX_SIZE = ModelVertices.VERTEX_SIZE;
    /** Muzzle socket coordinates in gun space. */
    public static final float MUZZLE_X = 0f;
    public static final float MUZZLE_Y = 0.020f;
    public static final float MUZZLE_Z = -0.277f;
    /** Distance the ribbed pump forend travels back along +Z when racking a shell, in view units. */
    public static final float PUMP_TRAVEL = 0.052f;

    /** Logical articulated groups of the scattergun viewmodel. */
    public enum Group {
        /** Receiver, barrel, tube, side-saddle shells and rear grip. */
        BODY,
        /** Ribbed forend pump that slides back along +Z between shots. */
        PUMP
    }

    /** Materials supported by {@code viewmodel-scattergun.obj}. */
    public enum Material {
        GUNMETAL("gunmetal", 0.22f, 0.25f, 0.30f, 0.6f),
        GUNMETAL_TOP("gunmetal_top", 0.28f, 0.30f, 0.36f, 1f),
        DARK_METAL("dark_metal", 0.08f, 0.09f, 0.11f, 0.4f),
        STEEL("steel", 0.60f, 0.62f, 0.68f, 1f),
        BORE("bore", 0.02f, 0.02f, 0.03f, 0f),
        BRASS("brass", 0.95f, 0.68f, 0.22f, 0.7f),
        SIGHT_DOT("sight_dot", 0.88f, 0.90f, 0.86f, 0f),
        SIGHT_AMBER("sight_amber", 1.00f, 0.66f, 0.14f, 0.3f),
        POLYMER("polymer", 0.24f, 0.26f, 0.30f, 0.15f),
        POLYMER_DARK("polymer_dark", 0.14f, 0.15f, 0.18f, 0.10f),
        STIPPLE("stipple", 0.30f, 0.32f, 0.36f, 0.10f),
        SHELL_RED("shell_red", 0.84f, 0.16f, 0.20f, 0.45f),
        HEATSHIELD("heatshield", 0.16f, 0.18f, 0.22f, 0.60f);

        /** Name referenced by {@code usemtl} lines in the OBJ file. */
        public final String name;
        final float[] colour;

        Material(String name, float red, float green, float blue, float gloss) {
            this.name = name;
            this.colour = new float[] {red, green, blue, gloss};
        }

        /**
         * Resolves a material colour entry by name.
         * @param name material name in the OBJ file
         * @return matching {@link Material}
         * @throws IllegalArgumentException if unknown
         */
        public static Material named(String name) {
            for (Material material : values()) {
                if (material.name.equals(name)) return material;
            }
            throw new IllegalArgumentException("The scattergun model uses an unknown material: '" + name + "'");
        }
    }

    /** One contiguous solid run in either {@link Group#BODY} or {@link Group#PUMP}. */
    public static final class Solid {
        public final String name;
        public final Group group;
        public final int firstVertex;
        public final int vertexCount;

        Solid(String name, Group group, int firstVertex, int vertexCount) {
            this.name = name;
            this.group = group;
            this.firstVertex = firstVertex;
            this.vertexCount = vertexCount;
        }
    }

    /** Unindexed triangle vertex data for {@link Group#BODY}. */
    public final float[] body;
    /** Unindexed triangle vertex data for {@link Group#PUMP}. */
    public final float[] pump;
    /** Deduplicated indexed GPU buffer for {@link Group#BODY}. */
    public final ModelVertices.IndexedMesh bodyMesh;
    /** Deduplicated indexed GPU buffer for {@link Group#PUMP}. */
    public final ModelVertices.IndexedMesh pumpMesh;
    /** Ordered solids in file order. */
    public final List<Solid> solids;

    private ScattergunModel(float[] body, float[] pump, ModelVertices.IndexedMesh bodyMesh,
                            ModelVertices.IndexedMesh pumpMesh, List<Solid> solids) {
        this.body = body;
        this.pump = pump;
        this.bodyMesh = bodyMesh;
        this.pumpMesh = pumpMesh;
        this.solids = Collections.unmodifiableList(solids);
    }

    /**
     * Parses a scattergun OBJ model into separated body and pump meshes and solid metadata.
     * @param model parsed OBJ model
     * @return scattergun vertex arrays and indexed buffers
     */
    public static ScattergunModel from(ObjModel model) {
        int triangleCount = model.getTriangleCount();
        int bodyTriangles = 0;
        int pumpTriangles = 0;
        for (int triangle = 0; triangle < triangleCount; triangle++) {
            String groupName = model.getGroup(triangle);
            if (groupName.isEmpty()) {
                throw new IllegalArgumentException("The scattergun model has a face with no group");
            }
            Material.named(model.getMaterial(triangle));
            if (isPump(model, triangle, groupName)) pumpTriangles++;
            else bodyTriangles++;
        }

        float[] body = new float[bodyTriangles * 3 * VERTEX_SIZE];
        float[] pump = new float[pumpTriangles * 3 * VERTEX_SIZE];
        int bodyVertex = 0;
        int pumpVertex = 0;

        List<Solid> solids = new ArrayList<>();
        String currentName = null;
        Group currentGroup = null;
        int runStart = 0;
        int runCount = 0;

        for (int triangle = 0; triangle < triangleCount; triangle++) {
            String groupName = model.getGroup(triangle);
            Group group = isPump(model, triangle, groupName) ? Group.PUMP : Group.BODY;
            float[] colour = Material.named(model.getMaterial(triangle)).colour;

            if (!groupName.equals(currentName)) {
                if (currentName != null) {
                    solids.add(new Solid(currentName, currentGroup, runStart, runCount));
                }
                currentName = groupName;
                currentGroup = group;
                runStart = group == Group.PUMP ? pumpVertex : bodyVertex;
                runCount = 0;
            }

            if (group == Group.PUMP) {
                ModelVertices.writeTriangle(model, triangle, colour, pump, pumpVertex * VERTEX_SIZE);
                pumpVertex += 3;
            } else {
                ModelVertices.writeTriangle(model, triangle, colour, body, bodyVertex * VERTEX_SIZE);
                bodyVertex += 3;
            }
            runCount += 3;
        }
        if (currentName != null) {
            solids.add(new Solid(currentName, currentGroup, runStart, runCount));
        }

        ModelVertices.IndexedMesh bodyIndexed = bodyTriangles > 0
            ? ModelVertices.packIndexed(model, t -> !isPump(model, t, model.getGroup(t)),
                0f, 0f, 0f, name -> Material.named(name).colour)
            : null;
        ModelVertices.IndexedMesh pumpIndexed = pumpTriangles > 0
            ? ModelVertices.packIndexed(model, t -> isPump(model, t, model.getGroup(t)),
                0f, 0f, 0f, name -> Material.named(name).colour)
            : null;
        return new ScattergunModel(body, pump, bodyIndexed, pumpIndexed, solids);
    }

    private static boolean isPump(ObjModel model, int triangle, String groupName) {
        return "pump".equals(model.getPart(triangle)) || groupName.startsWith("pump");
    }

    /** @return unindexed triangle vertex array for {@code group}. */
    public float[] vertices(Group group) {
        return group == Group.PUMP ? pump : body;
    }
}

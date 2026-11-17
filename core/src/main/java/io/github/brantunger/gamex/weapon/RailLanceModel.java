package io.github.brantunger.gamex.weapon;

import io.github.brantunger.gamex.model.ModelVertices;
import io.github.brantunger.gamex.model.ObjModel;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

/**
 * The first-person Rail Lance viewmodel, loaded from a Wavefront OBJ file without graphics types. Shares
 * {@link PistolModel}'s rear grip envelope so {@link HandModel} fits cleanly without glove penetration, while splitting
 * the magnetic breech sled into {@link Group#BOLT} and exposing {@code # @accent rail_core} capacitor ribs.
 */
public final class RailLanceModel {

    /** Path of the model file inside the assets folder. */
    public static final String ASSET_PATH = "models/viewmodel-raillance.obj";
    /** Number of floats per packed vertex. */
    public static final int VERTEX_SIZE = ModelVertices.VERTEX_SIZE;
    /** Muzzle socket coordinates in gun space. */
    public static final float MUZZLE_X = 0f;
    /** Muzzle socket Y coordinate in gun space. */
    public static final float MUZZLE_Y = 0.018f;
    /** Muzzle socket Z coordinate in gun space. */
    public static final float MUZZLE_Z = -0.295f;
    /** Distance the magnetic breech bolt travels back along +Z during shot cycling, in view units. */
    public static final float BOLT_TRAVEL = 0.042f;

    /** Accent colours ({r, g, b, gloss}) driven via {@code u_accentColour} on {@code # @accent rail_core}. */
    public static final float[] ACCENT_READY = {0.18f, 0.94f, 1.00f, 0.98f};
    public static final float[] ACCENT_RECHARGING = {1.00f, 0.44f, 0.14f, 0.70f};
    public static final float[] ACCENT_EMPTY = {0.42f, 0.14f, 0.16f, 0.35f};

    /** Logical articulated groups of the Rail Lance viewmodel. */
    public enum Group {
        /** Twin electromagnetic rails, capacitor chassis, optic housing and rear grip. */
        BODY,
        /** Articulated magnetic breech sled that cycles rearward along +Z after each shot. */
        BOLT
    }

    /** Materials supported by {@code viewmodel-raillance.obj}. */
    public enum Material {
        GUNMETAL("gunmetal", 0.22f, 0.25f, 0.31f, 0.65f),
        GUNMETAL_TOP("gunmetal_top", 0.30f, 0.33f, 0.40f, 1f),
        DARK_METAL("dark_metal", 0.08f, 0.09f, 0.12f, 0.45f),
        STEEL("steel", 0.62f, 0.66f, 0.72f, 1f),
        BORE("bore", 0.02f, 0.02f, 0.03f, 0f),
        POLYMER("polymer", 0.22f, 0.24f, 0.28f, 0.15f),
        POLYMER_DARK("polymer_dark", 0.13f, 0.14f, 0.17f, 0.10f),
        STIPPLE("stipple", 0.28f, 0.30f, 0.35f, 0.10f),
        SIGHT_DOT("sight_dot", 0.32f, 0.96f, 1.00f, 0.60f),
        OPTIC_GLASS("optic_glass", 0.14f, 0.68f, 0.92f, 0.95f),
        RAIL_COPPER("rail_copper", 0.88f, 0.54f, 0.22f, 0.80f),
        RAIL_CORE("rail_core", 0.18f, 0.94f, 1.00f, 0.98f);

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
            throw new IllegalArgumentException("The rail lance model uses an unknown material: '" + name + "'");
        }
    }

    /** One contiguous solid run in either {@link Group#BODY} or {@link Group#BOLT}. */
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
    /** Unindexed triangle vertex data for {@link Group#BOLT}. */
    public final float[] bolt;
    /** Deduplicated indexed GPU buffer for {@link Group#BODY}. */
    public final ModelVertices.IndexedMesh bodyMesh;
    /** Deduplicated indexed GPU buffer for {@link Group#BOLT}. */
    public final ModelVertices.IndexedMesh boltMesh;
    /** Ordered solids in file order. */
    public final List<Solid> solids;

    private RailLanceModel(float[] body, float[] bolt, ModelVertices.IndexedMesh bodyMesh,
                           ModelVertices.IndexedMesh boltMesh, List<Solid> solids) {
        this.body = body;
        this.bolt = bolt;
        this.bodyMesh = bodyMesh;
        this.boltMesh = boltMesh;
        this.solids = Collections.unmodifiableList(solids);
    }

    /**
     * Parses a Rail Lance OBJ model into separated body and bolt meshes and solid metadata.
     * @param model parsed OBJ model
     * @return Rail Lance vertex arrays and indexed buffers
     */
    public static RailLanceModel from(ObjModel model) {
        int triangleCount = model.getTriangleCount();
        int bodyTriangles = 0;
        int boltTriangles = 0;
        for (int triangle = 0; triangle < triangleCount; triangle++) {
            String groupName = model.getGroup(triangle);
            if (groupName.isEmpty()) {
                throw new IllegalArgumentException("The rail lance model has a face with no group");
            }
            Material.named(model.getMaterial(triangle));
            if (isBolt(model, triangle, groupName)) boltTriangles++;
            else bodyTriangles++;
        }

        float[] body = new float[bodyTriangles * 3 * VERTEX_SIZE];
        float[] bolt = new float[boltTriangles * 3 * VERTEX_SIZE];
        int bodyVertex = 0;
        int boltVertex = 0;

        List<Solid> solids = new ArrayList<>();
        String currentName = null;
        Group currentGroup = null;
        int runStart = 0;
        int runCount = 0;

        for (int triangle = 0; triangle < triangleCount; triangle++) {
            String groupName = model.getGroup(triangle);
            Group group = isBolt(model, triangle, groupName) ? Group.BOLT : Group.BODY;
            float[] colour = Material.named(model.getMaterial(triangle)).colour;

            if (!groupName.equals(currentName)) {
                if (currentName != null) {
                    solids.add(new Solid(currentName, currentGroup, runStart, runCount));
                }
                currentName = groupName;
                currentGroup = group;
                runStart = group == Group.BOLT ? boltVertex : bodyVertex;
                runCount = 0;
            }

            if (group == Group.BOLT) {
                ModelVertices.writeTriangle(model, triangle, colour, bolt, boltVertex * VERTEX_SIZE);
                boltVertex += 3;
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
            ? ModelVertices.packIndexed(model, t -> !isBolt(model, t, model.getGroup(t)),
                0f, 0f, 0f, name -> Material.named(name).colour)
            : null;
        ModelVertices.IndexedMesh boltIndexed = boltTriangles > 0
            ? ModelVertices.packIndexed(model, t -> isBolt(model, t, model.getGroup(t)),
                0f, 0f, 0f, name -> Material.named(name).colour)
            : null;
        return new RailLanceModel(body, bolt, bodyIndexed, boltIndexed, solids);
    }

    private static boolean isBolt(ObjModel model, int triangle, String groupName) {
        return "bolt".equals(model.getPart(triangle)) || groupName.startsWith("bolt");
    }

    /** @return unindexed triangle vertex array for {@code group}. */
    public float[] vertices(Group group) {
        return group == Group.BOLT ? bolt : body;
    }
}

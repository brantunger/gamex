package io.github.brantunger.gamex.weapon;

import io.github.brantunger.gamex.model.ModelVertices;
import io.github.brantunger.gamex.model.ObjModel;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

/**
 * The first-person pistol, loaded from a Wavefront OBJ file and packed as plain float arrays with no graphics types, so
 * its winding, normals and solids can be checked without a GL context. The hand that holds it is a sculpted model: see
 * {@link HandModel}.
 *
 * <p>Gun space: the barrel points along negative z, y is up and x is to the shooter's right; the origin is the middle
 * of the underside of the slide. Lengths are in view units, roughly metres. A vertex is {@link #VERTEX_SIZE} floats:
 * position x, y, z, normal x, y, z, colour r, g, b, and a gloss from 0 (matt) to 1 (polished metal).
 *
 * <p>The model is split into two groups, because the slide moves on its own when the pistol fires. Every solid whose
 * OBJ group name starts with {@code slide} goes into {@link Group#SLIDE}; the rest go into {@link Group#BODY}.
 */
public final class PistolModel {

    /** The path of the model file inside the assets folder. */
    public static final String ASSET_PATH = "models/viewmodel-pistol.obj";
    /** The number of floats per vertex. */
    public static final int VERTEX_SIZE = ModelVertices.VERTEX_SIZE;
    /** The x of the muzzle's centre in gun space. */
    public static final float MUZZLE_X = 0f;
    /** The y of the muzzle's centre in gun space. */
    public static final float MUZZLE_Y = 0.0125f;
    /** The z of the muzzle's face in gun space. */
    public static final float MUZZLE_Z = -0.111f;

    /** The groups the model is split into. */
    public enum Group {
        /** The frame, barrel and grip, which move together. */
        BODY,
        /** The slide with its sights, which also travels back along z on each shot. */
        SLIDE
    }

    /** The materials the model file may use. */
    public enum Material {
        GUNMETAL("gunmetal", 0.23f, 0.25f, 0.30f, 0.6f),
        GUNMETAL_TOP("gunmetal_top", 0.28f, 0.30f, 0.36f, 1f),
        DARK_METAL("dark_metal", 0.08f, 0.09f, 0.11f, 0.4f),
        STEEL("steel", 0.60f, 0.62f, 0.68f, 1f),
        BORE("bore", 0.02f, 0.02f, 0.03f, 0f),
        BRASS("brass", 0.95f, 0.66f, 0.20f, 0.6f),
        SIGHT_DOT("sight_dot", 0.88f, 0.90f, 0.86f, 0f),
        POLYMER("polymer", 0.34f, 0.29f, 0.21f, 0f),
        POLYMER_DARK("polymer_dark", 0.24f, 0.205f, 0.15f, 0f),
        STIPPLE("stipple", 0.41f, 0.355f, 0.26f, 0f);

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
            throw new IllegalArgumentException("The pistol model uses a material this game has no colour for: '"
                + name + "'");
        }
    }

    /** One solid of the model: a contiguous run of triangles in its group's vertex array. */
    public static final class Solid {
        /** What the solid is, from its OBJ group name. */
        public final String name;
        /** The group whose array holds the solid. */
        public final Group group;
        /** The index of the solid's first vertex in that array. */
        public final int firstVertex;
        /** The number of vertices, three per triangle. */
        public final int vertexCount;

        Solid(String name, Group group, int firstVertex, int vertexCount) {
            this.name = name;
            this.group = group;
            this.firstVertex = firstVertex;
            this.vertexCount = vertexCount;
        }
    }

    /** The triangles of the {@link Group#BODY} group. */
    public final float[] body;
    /** The triangles of the {@link Group#SLIDE} group. */
    public final float[] slide;
    /** Every solid of the model, in file order. */
    public final List<Solid> solids;

    private PistolModel(float[] body, float[] slide, List<Solid> solids) {
        this.body = body;
        this.slide = slide;
        this.solids = Collections.unmodifiableList(solids);
    }

    /**
     * Turns a parsed pistol model into separated {@link Group#BODY} and {@link Group#SLIDE} vertex arrays.
     * @param model the parsed OBJ model
     * @return the pistol model
     * @throws IllegalArgumentException if a face has no group or uses a material that {@link Material} does not list
     */
    public static PistolModel from(ObjModel model) {
        int triangleCount = model.getTriangleCount();
        int bodyTriangles = 0;
        int slideTriangles = 0;
        for (int triangle = 0; triangle < triangleCount; triangle++) {
            String groupName = model.getGroup(triangle);
            if (groupName.isEmpty()) {
                throw new IllegalArgumentException("The pistol model has a face with no group");
            }
            Material.named(model.getMaterial(triangle));
            if (groupName.startsWith("slide")) slideTriangles++;
            else bodyTriangles++;
        }

        float[] body = new float[bodyTriangles * 3 * VERTEX_SIZE];
        float[] slide = new float[slideTriangles * 3 * VERTEX_SIZE];
        int bodyVertex = 0;
        int slideVertex = 0;

        List<Solid> solids = new ArrayList<>();
        String currentName = null;
        Group currentGroup = null;
        int runStart = 0;
        int runCount = 0;

        for (int triangle = 0; triangle < triangleCount; triangle++) {
            String groupName = model.getGroup(triangle);
            Group group = groupName.startsWith("slide") ? Group.SLIDE : Group.BODY;
            float[] colour = Material.named(model.getMaterial(triangle)).colour;

            if (!groupName.equals(currentName)) {
                if (currentName != null) {
                    solids.add(new Solid(currentName, currentGroup, runStart, runCount));
                }
                currentName = groupName;
                currentGroup = group;
                runStart = group == Group.SLIDE ? slideVertex : bodyVertex;
                runCount = 0;
            }

            if (group == Group.SLIDE) {
                ModelVertices.writeTriangle(model, triangle, colour, slide, slideVertex * VERTEX_SIZE);
                slideVertex += 3;
            } else {
                ModelVertices.writeTriangle(model, triangle, colour, body, bodyVertex * VERTEX_SIZE);
                bodyVertex += 3;
            }
            runCount += 3;
        }
        if (currentName != null) {
            solids.add(new Solid(currentName, currentGroup, runStart, runCount));
        }
        return new PistolModel(body, slide, solids);
    }

    /**
     * @param group a group
     * @return that group's triangles
     */
    public float[] vertices(Group group) {
        return group == Group.SLIDE ? slide : body;
    }
}

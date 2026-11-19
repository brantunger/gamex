package io.github.brantunger.gamex.weapon;

import io.github.brantunger.gamex.model.ModelPalette;
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
    /** The path of the file inside the assets folder that gives each {@link Material} its colour and gloss. */
    public static final String PALETTE_PATH = "data/palette-pistol.txt";
    /** The number of floats per vertex. */
    public static final int VERTEX_SIZE = ModelVertices.VERTEX_SIZE;
    /** The x of the muzzle's centre in gun space. */
    public static final float MUZZLE_X = 0f;
    /** The y of the muzzle's centre in gun space. */
    public static final float MUZZLE_Y = 0.0125f;
    /** The z of the muzzle's face in gun space. */
    public static final float MUZZLE_Z = -0.111f;
    /** Point in gun space that the first-person viewmodel rotates around: the middle of the grip. */
    public static final float GRIP_PIVOT_Y = -0.060f;
    public static final float GRIP_PIVOT_Z = 0.065f;

    /** Canonical pistol side profiles shared by pistol generation, hand fitting, and validation tests. */
    public static final float[] GRIP_OUTLINE = {
        -0.018f, 0.022f, -0.018f, 0.082f, -0.045f, 0.093f, -0.075f, 0.104f, -0.105f, 0.110f, -0.105f, 0.050f
    };
    public static final float GRIP_BODY_HALF_WIDTH = 0.013f;
    public static final float GRIP_HALF_WIDTH = 0.0146f;
    public static final float[] FRAME_OUTLINE = {0f, -0.092f, 0f, 0.082f, -0.018f, 0.082f, -0.018f, -0.092f};
    public static final float FRAME_HALF_WIDTH = 0.0152f;
    public static final float[] SLIDE_OUTLINE = {0.0275f, -0.105f, 0.0275f, 0.082f, 0f, 0.082f, 0f, -0.105f};
    public static final float SLIDE_HALF_WIDTH = 0.0147f;
    public static final float[] BEAVERTAIL_OUTLINE = {-0.004f, 0.080f, -0.004f, 0.102f, -0.018f, 0.102f, -0.018f, 0.080f};
    public static final float BEAVERTAIL_HALF_WIDTH = 0.011f;
    public static final float[] PLATE_OUTLINE = {-0.105f, 0.046f, -0.105f, 0.115f, -0.112f, 0.115f, -0.112f, 0.046f};
    public static final float PLATE_HALF_WIDTH = 0.0145f;

    /** @return the Z coordinate of the front strap of the raked grip at height {@code y}. */
    public static float frontStrap(float y) {
        return 0.022f + (-0.018f - y) * (0.050f - 0.022f) / (0.105f - 0.018f);
    }

    /** The groups the model is split into. */
    public enum Group {
        /** The frame, barrel and grip, which move together. */
        BODY,
        /** The slide with its sights, which also travels back along z on each shot. */
        SLIDE
    }

    /** The materials the model file may use. {@link #PALETTE_PATH} holds the colour and gloss of each. */
    public enum Material {
        GUNMETAL("gunmetal"),
        GUNMETAL_TOP("gunmetal_top"),
        DARK_METAL("dark_metal"),
        STEEL("steel"),
        BORE("bore"),
        BRASS("brass"),
        SIGHT_DOT("sight_dot"),
        POLYMER("polymer"),
        POLYMER_DARK("polymer_dark"),
        STIPPLE("stipple");

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
    /** Deduplicated indexed GPU buffer for {@link Group#BODY}. */
    public final ModelVertices.IndexedMesh bodyMesh;
    /** Deduplicated indexed GPU buffer for {@link Group#SLIDE}. */
    public final ModelVertices.IndexedMesh slideMesh;
    /** Every solid of the model, in file order. */
    public final List<Solid> solids;

    private static final String PALETTE_LABEL = "pistol";

    private PistolModel(float[] body, float[] slide, ModelVertices.IndexedMesh bodyMesh,
                        ModelVertices.IndexedMesh slideMesh, List<Solid> solids) {
        this.body = body;
        this.slide = slide;
        this.bodyMesh = bodyMesh;
        this.slideMesh = slideMesh;
        this.solids = Collections.unmodifiableList(solids);
    }

    /**
     * Turns a parsed pistol model into separated {@link Group#BODY} and {@link Group#SLIDE} vertex and indexed arrays.
     * @param model the parsed OBJ model
     * @param palette the colours of the pistol's materials
     * @return the pistol model
     * @throws IllegalArgumentException if a face has no group or uses a material that {@link Material} does not list
     */
    public static PistolModel from(ObjModel model, ModelPalette palette) {
        int triangleCount = model.getTriangleCount();
        int bodyTriangles = 0;
        int slideTriangles = 0;
        for (int triangle = 0; triangle < triangleCount; triangle++) {
            String groupName = model.getGroup(triangle);
            if (groupName.isEmpty()) {
                throw new IllegalArgumentException("The pistol model has a face with no group");
            }
            Material.named(model.getMaterial(triangle));
            if (isSlide(model, triangle, groupName)) slideTriangles++;
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
            Group group = isSlide(model, triangle, groupName) ? Group.SLIDE : Group.BODY;
            float[] colour = colour(palette, model.getMaterial(triangle));

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
        ModelVertices.IndexedMesh bodyIndexed = bodyTriangles > 0
            ? ModelVertices.packIndexed(model, t -> !isSlide(model, t, model.getGroup(t)),
                0f, 0f, 0f, name -> colour(palette, name))
            : null;
        ModelVertices.IndexedMesh slideIndexed = slideTriangles > 0
            ? ModelVertices.packIndexed(model, t -> isSlide(model, t, model.getGroup(t)),
                0f, 0f, 0f, name -> colour(palette, name))
            : null;
        return new PistolModel(body, slide, bodyIndexed, slideIndexed, solids);
    }

    /**
     * Reads the colours of the pistol from {@link #PALETTE_PATH}.
     * @return the loaded palette
     * @throws IllegalArgumentException if the file breaks the format, lacks a material, holds a name
     *     this class does not list, or holds a value that is not four numbers from 0 to 1
     */
    public static ModelPalette loadPalette() {
        return ModelPalette.load(PALETTE_LABEL, PALETTE_PATH, materialNames(), accentNames());
    }

    /**
     * Parses the text of a palette file of the pistol.
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

    private static boolean isSlide(ObjModel model, int triangle, String groupName) {
        return "slide".equals(model.getPart(triangle)) || groupName.startsWith("slide");
    }

    /**
     * @param group a group
     * @return that group's triangles
     */
    public float[] vertices(Group group) {
        return group == Group.SLIDE ? slide : body;
    }
}

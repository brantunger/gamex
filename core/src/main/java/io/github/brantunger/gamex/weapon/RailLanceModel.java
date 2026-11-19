package io.github.brantunger.gamex.weapon;

import io.github.brantunger.gamex.model.ModelPalette;
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
    /** Path of the file inside the assets folder that gives each {@link Material} and {@link Accent} its colour. */
    public static final String PALETTE_PATH = "data/palette-rail-lance.txt";
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

    /**
     * Accent colours ({r, g, b, gloss}) driven via {@code u_accentColour} on {@code # @accent rail_core};
     * {@link #PALETTE_PATH} holds their colours.
     */
    public enum Accent {
        /** Fully charged with a slug to fire. */
        READY("ready"),
        /** Just fired; blends towards {@link #READY} as the charge returns. */
        RECHARGING("recharging"),
        /** No slugs left. */
        EMPTY("empty");

        /** Name of the accent in the palette file, after {@code accent.}. */
        public final String name;

        Accent(String name) {
            this.name = name;
        }
    }

    /** Logical articulated groups of the Rail Lance viewmodel. */
    public enum Group {
        /** Twin electromagnetic rails, capacitor chassis, optic housing and rear grip. */
        BODY,
        /** Articulated magnetic breech sled that cycles rearward along +Z after each shot. */
        BOLT
    }

    /** Materials supported by {@code viewmodel-raillance.obj}; {@link #PALETTE_PATH} holds their colours. */
    public enum Material {
        GUNMETAL("gunmetal"),
        GUNMETAL_TOP("gunmetal_top"),
        DARK_METAL("dark_metal"),
        STEEL("steel"),
        BORE("bore"),
        POLYMER("polymer"),
        POLYMER_DARK("polymer_dark"),
        STIPPLE("stipple"),
        SIGHT_DOT("sight_dot"),
        OPTIC_GLASS("optic_glass"),
        RAIL_COPPER("rail_copper"),
        RAIL_CORE("rail_core");

        /** Name referenced by {@code usemtl} lines in the OBJ file. */
        public final String name;

        Material(String name) {
            this.name = name;
        }

        /**
         * Resolves a material by name.
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

    private static final String PALETTE_LABEL = "rail lance";

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
     * @param palette colours of the Rail Lance's materials
     * @return Rail Lance vertex arrays and indexed buffers
     */
    public static RailLanceModel from(ObjModel model, ModelPalette palette) {
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
            float[] colour = colour(palette, model.getMaterial(triangle));

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
                0f, 0f, 0f, name -> colour(palette, name))
            : null;
        ModelVertices.IndexedMesh boltIndexed = boltTriangles > 0
            ? ModelVertices.packIndexed(model, t -> isBolt(model, t, model.getGroup(t)),
                0f, 0f, 0f, name -> colour(palette, name))
            : null;
        return new RailLanceModel(body, bolt, bodyIndexed, boltIndexed, solids);
    }

    /**
     * Reads the colours of the Rail Lance from {@link #PALETTE_PATH}.
     * @return the loaded palette
     * @throws IllegalArgumentException if the file breaks the format, lacks a material or accent, holds a name
     *     this class does not list, or holds a value that is not four numbers from 0 to 1
     */
    public static ModelPalette loadPalette() {
        return ModelPalette.load(PALETTE_LABEL, PALETTE_PATH, materialNames(), accentNames());
    }

    /**
     * Parses the text of a palette file of the Rail Lance.
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
        List<String> names = new ArrayList<>();
        for (Accent accent : Accent.values()) names.add(accent.name);
        return names;
    }

    private static float[] colour(ModelPalette palette, String material) {
        return palette.colour(Material.named(material).name);
    }

    private static boolean isBolt(ObjModel model, int triangle, String groupName) {
        return "bolt".equals(model.getPart(triangle)) || groupName.startsWith("bolt");
    }

    /** @return unindexed triangle vertex array for {@code group}. */
    public float[] vertices(Group group) {
        return group == Group.BOLT ? bolt : body;
    }
}

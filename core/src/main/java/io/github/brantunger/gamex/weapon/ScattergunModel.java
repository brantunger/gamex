package io.github.brantunger.gamex.weapon;

import io.github.brantunger.gamex.model.ModelPalette;
import io.github.brantunger.gamex.model.ModelVertices;
import io.github.brantunger.gamex.model.ObjModel;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

/**
 * The first-person shortened double-barrel drum combat scattergun, loaded from a Wavefront OBJ file and packed as plain
 * float arrays without graphics types. Shares {@link PistolModel}'s rear grip envelope so {@link HandModel} fits cleanly
 * without clipping, while splitting the compact walnut forend &amp; lever-action loop into {@link Group#PUMP}.
 */
public final class ScattergunModel {

    /** Path of the model file inside the assets folder. */
    public static final String ASSET_PATH = "models/viewmodel-scattergun.obj";
    /** Path of the file inside the assets folder that gives each {@link Material} its colour and gloss. */
    public static final String PALETTE_PATH = "data/palette-scattergun.txt";
    /** Number of floats per packed vertex. */
    public static final int VERTEX_SIZE = ModelVertices.VERTEX_SIZE;
    /** Muzzle socket coordinates in gun space. */
    public static final float MUZZLE_X = 0f;
    /** Muzzle socket Y coordinate centered on the twin barrels. */
    public static final float MUZZLE_Y = 0.019f;
    /** Muzzle socket Z coordinate for the shortened sawed-off twin barrels. */
    public static final float MUZZLE_Z = -0.196f;
    /** Distance the compact forend &amp; lever loop travel back along +Z when cocking a shell, in view units. */
    public static final float PUMP_TRAVEL = 0.036f;

    /** Logical articulated groups of the scattergun viewmodel. */
    public enum Group {
        /** Transverse drum receiver, twin sawed-off barrels and rear wood grip. */
        BODY,
        /** Compact walnut forend &amp; lever loop that cycle back along +Z between shots. */
        PUMP
    }

    /** Materials supported by {@code viewmodel-scattergun.obj}; {@link #PALETTE_PATH} holds their colours. */
    public enum Material {
        GUNMETAL("gunmetal"),
        GUNMETAL_TOP("gunmetal_top"),
        DARK_METAL("dark_metal"),
        STEEL("steel"),
        BORE("bore"),
        BRASS("brass"),
        SIGHT_DOT("sight_dot"),
        SIGHT_AMBER("sight_amber"),
        POLYMER("polymer"),
        POLYMER_DARK("polymer_dark"),
        STIPPLE("stipple"),
        SHELL_RED("shell_red"),
        HEATSHIELD("heatshield"),
        WOOD("wood"),
        WOOD_DARK("wood_dark");

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

    private static final String PALETTE_LABEL = "scattergun";

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
     * @param palette colours of the scattergun's materials
     * @return scattergun vertex arrays and indexed buffers
     */
    public static ScattergunModel from(ObjModel model, ModelPalette palette) {
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
            float[] colour = colour(palette, model.getMaterial(triangle));

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
                0f, 0f, 0f, name -> colour(palette, name))
            : null;
        ModelVertices.IndexedMesh pumpIndexed = pumpTriangles > 0
            ? ModelVertices.packIndexed(model, t -> isPump(model, t, model.getGroup(t)),
                0f, 0f, 0f, name -> colour(palette, name))
            : null;
        return new ScattergunModel(body, pump, bodyIndexed, pumpIndexed, solids);
    }

    /**
     * Reads the colours of the scattergun from {@link #PALETTE_PATH}.
     * @return the loaded palette
     * @throws IllegalArgumentException if the file breaks the format, lacks a material, holds a name
     *     this class does not list, or holds a value that is not four numbers from 0 to 1
     */
    public static ModelPalette loadPalette() {
        return ModelPalette.load(PALETTE_LABEL, PALETTE_PATH, materialNames(), accentNames());
    }

    /**
     * Parses the text of a palette file of the scattergun.
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

    private static boolean isPump(ObjModel model, int triangle, String groupName) {
        return "pump".equals(model.getPart(triangle)) || groupName.startsWith("pump");
    }

    /** @return unindexed triangle vertex array for {@code group}. */
    public float[] vertices(Group group) {
        return group == Group.PUMP ? pump : body;
    }
}

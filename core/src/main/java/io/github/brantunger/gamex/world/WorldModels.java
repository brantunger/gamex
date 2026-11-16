package io.github.brantunger.gamex.world;

import io.github.brantunger.gamex.model.ModelVertices;
import io.github.brantunger.gamex.model.ObjModel;

/**
 * The 3D world objects (targets and blast projectiles), loaded from Wavefront OBJ files. This class names their asset
 * paths and gives each material in those files a colour and gloss, turning parsed models into the game's vertex
 * layout without graphics types.
 */
public final class WorldModels {

    /** The path of the canister model file inside the assets folder. */
    public static final String CANISTER_PATH = "models/canister.obj";
    /** The path of the ring board model file inside the assets folder. */
    public static final String RING_BOARD_PATH = "models/ring-board.obj";
    /** The path of the blast projectile model file inside the assets folder. */
    public static final String BLAST_ORB_PATH = "models/blast-orb.obj";
    /** The number of floats per vertex. */
    public static final int VERTEX_SIZE = ModelVertices.VERTEX_SIZE;

    /** The materials the world object model files may use. */
    public enum Material {
        CANISTER_BODY("canister_body", 0.22f, 0.82f, 0.71f, 0.45f),
        CANISTER_BAND("canister_band", 0.16f, 0.27f, 0.30f, 0.3f),
        CANISTER_CAP("canister_cap", 0.50f, 0.53f, 0.55f, 0.8f),
        CANISTER_EMBLEM("canister_emblem", 0.91f, 0.96f, 0.81f, 0.2f),
        BOARD_FOOT("board_foot", 0.36f, 0.35f, 0.33f, 0.2f),
        BOARD_POST("board_post", 0.47f, 0.45f, 0.42f, 0.3f),
        BOARD_RIM("board_rim", 0.12f, 0.10f, 0.18f, 0.2f),
        BOARD_WHITE("board_white", 0.91f, 0.90f, 0.85f, 0.1f),
        BOARD_VIOLET("board_violet", 0.41f, 0.27f, 0.77f, 0.2f),
        BOARD_BULLSEYE("board_bullseye", 0.89f, 0.24f, 0.56f, 0.3f),
        ORB_CORE("orb_core", 0.95f, 0.85f, 1.00f, 1.0f),
        ORB_SHELL("orb_shell", 0.62f, 0.32f, 1.00f, 0.8f);

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
            throw new IllegalArgumentException("A world model uses a material this game has no colour for: '"
                + name + "'");
        }
    }

    private WorldModels() {}

    /**
     * Turns a parsed world model into packed triangles of position, normal, colour and gloss.
     * @param model the parsed model file
     * @return three vertices per triangle
     * @throws IllegalArgumentException if a face uses a material that {@link Material} does not list
     */
    public static float[] vertices(ObjModel model) {
        return ModelVertices.pack(model, name -> Material.named(name).colour);
    }
}

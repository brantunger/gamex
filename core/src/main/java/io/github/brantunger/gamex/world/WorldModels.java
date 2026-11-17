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
    /** The path of the medkit pickup model file inside the assets folder. */
    public static final String MEDKIT_PATH = "models/pickup-medkit.obj";
    /** The path of the armor pickup model file inside the assets folder. */
    public static final String ARMOR_PATH = "models/pickup-armor.obj";
    /** The path of the energy cell pickup model file inside the assets folder. */
    public static final String ENERGY_CELL_PATH = "models/pickup-energy.obj";
    /** The path of the sentry turret enemy model file inside the assets folder. */
    public static final String TURRET_PATH = "models/enemy-turret.obj";
    /** The path of the rusher drone enemy model file inside the assets folder. */
    public static final String DRONE_PATH = "models/enemy-drone.obj";
    /** The path of the guard bot enemy model file inside the assets folder. */
    public static final String GUARD_PATH = "models/enemy-guard.obj";
    /** The path of the enemy plasma bolt projectile model file inside the assets folder. */
    public static final String ENEMY_BOLT_PATH = "models/enemy-bolt.obj";
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
        ORB_SHELL("orb_shell", 0.62f, 0.32f, 1.00f, 0.8f),
        MEDKIT_CASE("medkit_case", 0.90f, 0.92f, 0.90f, 0.35f),
        MEDKIT_TRIM("medkit_trim", 0.30f, 0.35f, 0.38f, 0.5f),
        MEDKIT_CROSS("medkit_cross", 0.92f, 0.20f, 0.26f, 0.4f),
        ARMOR_PLATE("armor_plate", 0.18f, 0.52f, 0.86f, 0.65f),
        ARMOR_TRIM("armor_trim", 0.42f, 0.48f, 0.56f, 0.75f),
        ARMOR_CORE("armor_core", 0.45f, 0.92f, 1.00f, 0.9f),
        CELL_CAP("cell_cap", 0.32f, 0.28f, 0.42f, 0.6f),
        CELL_CORE("cell_core", 0.76f, 0.42f, 1.00f, 0.95f),
        BOT_ARMOR("bot_armor", 0.34f, 0.37f, 0.43f, 0.65f),
        BOT_DARK("bot_dark", 0.15f, 0.17f, 0.21f, 0.40f),
        BOT_TRIM("bot_trim", 0.52f, 0.56f, 0.63f, 0.80f),
        BOT_VISOR("bot_visor", 1.00f, 0.22f, 0.28f, 0.95f),
        BOT_CORE("bot_core", 1.00f, 0.48f, 0.18f, 0.90f),
        BOLT_CORE("bolt_core", 1.00f, 0.82f, 0.78f, 1.00f),
        BOLT_SHELL("bolt_shell", 1.00f, 0.22f, 0.26f, 0.85f);

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

    /**
     * Turns only the triangles whose OBJ group starts with {@code groupPrefix} into packed vertices.
     * @param model the parsed model file
     * @param groupPrefix prefix matched against {@link ObjModel#getGroup(int)}
     * @return three vertices per matching triangle
     * @throws IllegalArgumentException if no triangle matches {@code groupPrefix} or a material is unknown
     */
    public static float[] verticesForGroupPrefix(ObjModel model, String groupPrefix) {
        int count = 0;
        for (int t = 0; t < model.getTriangleCount(); t++) {
            if (model.getGroup(t).startsWith(groupPrefix)) count++;
        }
        if (count == 0) {
            throw new IllegalArgumentException("No triangles matched group prefix '" + groupPrefix + "'");
        }
        float[] out = new float[count * 3 * VERTEX_SIZE];
        int at = 0;
        for (int t = 0; t < model.getTriangleCount(); t++) {
            if (!model.getGroup(t).startsWith(groupPrefix)) continue;
            ModelVertices.writeTriangle(model, t, Material.named(model.getMaterial(t)).colour, out, at);
            at += 3 * VERTEX_SIZE;
        }
        return out;
    }
}

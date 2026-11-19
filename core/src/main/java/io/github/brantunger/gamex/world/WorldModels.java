package io.github.brantunger.gamex.world;

import io.github.brantunger.gamex.model.ModelPalette;
import io.github.brantunger.gamex.model.ModelVertices;
import io.github.brantunger.gamex.model.ObjModel;

import java.util.ArrayList;
import java.util.List;

/**
 * The 3D world objects (targets and blast projectiles), loaded from Wavefront OBJ files. This class names their asset
 * paths, the materials those files may use and the accents drawn on them, and turns parsed models into the game's
 * vertex layout, with the colours of a {@link ModelPalette}, without graphics types. The Warden is the exception:
 * it is a glTF model with its own materials, and only its path, height, flare position and the names of its glow
 * colours are here.
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
    /** The path of the Combat Scattergun pickup model file inside the assets folder. */
    public static final String SCATTERGUN_PICKUP_PATH = "models/pickup-scattergun.obj";
    /** The path of the shotgun shell box pickup model file inside the assets folder. */
    public static final String SHELLS_PICKUP_PATH = "models/pickup-shells.obj";
    /** The path of the Slot 3 Rail Lance pickup model file inside the assets folder. */
    public static final String RAIL_LANCE_PICKUP_PATH = "models/pickup-raillance.obj";
    /** The path of the Rail Lance slug pack pickup model file inside the assets folder. */
    public static final String RAIL_SLUGS_PICKUP_PATH = "models/pickup-slugs.obj";
    /** The path of the 3D rail beam trail prism model file inside the assets folder. */
    public static final String RAIL_BEAM_PATH = "models/rail-beam.obj";
    /** The path of the sentry turret enemy model file inside the assets folder. */
    public static final String TURRET_PATH = "models/enemy-turret.obj";
    /** The path of the rusher drone enemy model file inside the assets folder. */
    public static final String DRONE_PATH = "models/enemy-drone.obj";
    /** The path of the guard bot enemy model file inside the assets folder. */
    public static final String GUARD_PATH = "models/enemy-guard.obj";
    /** The path of the Warden boss model file, a binary glTF, inside the assets folder. */
    public static final String WARDEN_PATH = "models/enemy-warden.glb";
    /** The path of the shock/thermal floor grate hazard model file inside the assets folder. */
    public static final String HAZARD_GRATE_PATH = "models/hazard-grate.obj";
    /** The path of the enemy plasma bolt projectile model file inside the assets folder. */
    public static final String ENEMY_BOLT_PATH = "models/enemy-bolt.obj";
    /** The path of the sliding bulkhead door model file inside the assets folder. */
    public static final String DOOR_BULKHEAD_PATH = "models/door-bulkhead.obj";
    /** The path of the rotating security keycard pickup model file inside the assets folder. */
    public static final String KEYCARD_PATH = "models/pickup-keycard.obj";
    /** The path of the security console pedestal model file inside the assets folder. */
    public static final String SECURITY_CONSOLE_PATH = "models/security-console.obj";
    /** The number of floats per vertex. */
    public static final int VERTEX_SIZE = ModelVertices.VERTEX_SIZE;

    /** Shared canonical enemy joint pivots and muzzle/telegraph sockets across generators, AI, and rendering. */
    public static final float TURRET_HEAD_PIVOT_Y = 0.34f;
    public static final float TURRET_BARREL_PIVOT_Y = 0.44f;
    public static final float TURRET_BARREL_PIVOT_Z = -0.04f;
    public static final float TURRET_BARREL_X = 0.070f;
    public static final float TURRET_MUZZLE_Y = 0.44f;
    public static final float TURRET_MUZZLE_Z = -0.205f;
    public static final float TURRET_FLARE_Z = -0.215f;

    public static final float GUARD_PELVIS_PIVOT_Y = 0.34f;
    public static final float GUARD_HIP_X = 0.075f;
    public static final float GUARD_HIP_Y = 0.30f;
    public static final float GUARD_TORSO_PIVOT_Y = 0.38f;
    public static final float GUARD_SHOULDER_X = 0.162f;
    public static final float GUARD_SHOULDER_Y = 0.54f;
    public static final float GUARD_MUZZLE_X = 0.162f;
    public static final float GUARD_MUZZLE_Y = 0.46f;
    public static final float GUARD_MUZZLE_Z = -0.148f;
    public static final float GUARD_FLARE_Z = -0.160f;

    public static final float DRONE_FLARE_Y = 0.34f;
    public static final float DRONE_FLARE_Z = -0.180f;

    /** How tall the Warden model stands in its file, feet at 0; the renderer scales it to the Warden's height. */
    public static final float WARDEN_MODEL_HEIGHT = 1.70f;
    /** Where the flare that warns of each of the Warden's punches shows: in front of its chest. */
    public static final float WARDEN_TELEGRAPH_Y = 0.68f;
    public static final float WARDEN_TELEGRAPH_Z = -0.26f;

    /** The path of the file inside the assets folder that gives each {@link Material} and {@link Accent} a colour. */
    public static final String PALETTE_PATH = "data/palette-world.txt";

    private static final String PALETTE_LABEL = "world";

    /**
     * Per-draw accent shader colours ({r, g, b, gloss}) substituting {@code # @accent} surfaces without VBO clones.
     * {@link #PALETTE_PATH} holds the colour of each.
     */
    public enum Accent {
        DOOR_UNLOCKED("door_unlocked"),
        DOOR_AMBER("door_amber"),
        DOOR_CYAN("door_cyan"),
        DOOR_LOCKDOWN("door_lockdown"),
        DOOR_BOSS("door_boss"),
        DOOR_OPEN("door_open"),
        KEYCARD_AMBER("keycard_amber"),
        KEYCARD_CYAN("keycard_cyan"),
        CONSOLE_LOCKED("console_locked"),
        CONSOLE_ACTIVE("console_active"),
        WARDEN_PHASE1("warden_phase1"),
        WARDEN_OVERDRIVE("warden_overdrive"),
        HAZARD_LIVE("hazard_live"),
        RAIL_READY("rail_ready"),
        RAIL_BEAM("rail_beam");

        /** The name of the accent in the palette file, after {@code accent.}. */
        public final String name;

        Accent(String name) {
            this.name = name;
        }
    }

    /** The materials the world object model files may use. {@link #PALETTE_PATH} holds the colour of each. */
    public enum Material {
        CANISTER_BODY("canister_body"),
        CANISTER_BAND("canister_band"),
        CANISTER_CAP("canister_cap"),
        CANISTER_EMBLEM("canister_emblem"),
        BOARD_FOOT("board_foot"),
        BOARD_POST("board_post"),
        BOARD_RIM("board_rim"),
        BOARD_WHITE("board_white"),
        BOARD_VIOLET("board_violet"),
        BOARD_BULLSEYE("board_bullseye"),
        ORB_CORE("orb_core"),
        ORB_SHELL("orb_shell"),
        MEDKIT_CASE("medkit_case"),
        MEDKIT_TRIM("medkit_trim"),
        MEDKIT_CROSS("medkit_cross"),
        ARMOR_PLATE("armor_plate"),
        ARMOR_TRIM("armor_trim"),
        ARMOR_CORE("armor_core"),
        CELL_CAP("cell_cap"),
        CELL_CORE("cell_core"),
        BOT_ARMOR("bot_armor"),
        BOT_DARK("bot_dark"),
        BOT_TRIM("bot_trim"),
        BOT_VISOR("bot_visor"),
        BOT_CORE("bot_core"),
        BOLT_CORE("bolt_core"),
        BOLT_SHELL("bolt_shell"),
        DOOR_ARMOR("door_armor"),
        DOOR_TRIM("door_trim"),
        DOOR_DARK("door_dark"),
        DOOR_STATUS("door_status"),
        KEYCARD_BODY("keycard_body"),
        KEYCARD_TRIM("keycard_trim"),
        KEYCARD_STRIPE("keycard_stripe"),
        KEYCARD_CHIP("keycard_chip"),
        CONSOLE_DARK("console_dark"),
        CONSOLE_BODY("console_body"),
        CONSOLE_TRIM("console_trim"),
        CONSOLE_KEYPAD("console_keypad"),
        CONSOLE_SCREEN("console_screen"),
        SHOTGUN_METAL("shotgun_metal"),
        SHOTGUN_DARK("shotgun_dark"),
        SHOTGUN_POLYMER("shotgun_polymer"),
        SHOTGUN_WOOD("shotgun_wood"),
        SHELL_CRIMSON("shell_crimson"),
        SHELL_BRASS("shell_brass"),
        SHELL_CRATE("shell_crate"),
        HAZARD_FRAME("hazard_frame"),
        HAZARD_DARK("hazard_dark"),
        HAZARD_COIL("hazard_coil"),
        RAIL_METAL("rail_metal"),
        RAIL_DARK("rail_dark"),
        RAIL_COPPER("rail_copper"),
        RAIL_CORE("rail_core"),
        RAIL_BEAM("rail_beam"),
        RAIL_BEAM_CORE("rail_beam_core");

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
            throw new IllegalArgumentException("A world model uses a material this game has no colour for: '"
                + name + "'");
        }
    }

    private WorldModels() {}

    /**
     * Reads the colours of the world models from {@link #PALETTE_PATH}.
     * @return the loaded palette
     * @throws IllegalArgumentException if the file breaks the format, lacks a material or accent, holds a name
     *     this class does not list, or holds a value that is not four numbers from 0 to 1
     */
    public static ModelPalette loadPalette() {
        return ModelPalette.load(PALETTE_LABEL, PALETTE_PATH, materialNames(), accentNames());
    }

    /**
     * Parses the text of a palette file of the world models.
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

    /**
     * Turns a parsed world model into packed triangles of position, normal, colour and gloss.
     * @param model the parsed model file
     * @param palette the colours of the world materials
     * @return three vertices per triangle
     * @throws IllegalArgumentException if a face uses a material that {@link Material} does not list
     */
    public static float[] vertices(ObjModel model, ModelPalette palette) {
        return ModelVertices.pack(model, name -> colour(palette, name));
    }

    /**
     * Packs an entire world model into deduplicated vertices and 16-bit triangle indices around origin.
     * @param model parsed world model
     * @param palette colours of the world materials
     * @return indexed vertex and triangle index arrays
     */
    public static ModelVertices.IndexedMesh packIndexed(ObjModel model, ModelPalette palette) {
        return ModelVertices.packIndexed(model, name -> colour(palette, name));
    }

    /**
     * Packs one hierarchical part declared via {@code # @part} into an indexed mesh with vertices centered on that
     * part's local pivot.
     * @param model parsed world model
     * @param partName part name to extract
     * @param palette colours of the world materials
     * @return pivot-relative indexed vertex and triangle index arrays
     * @throws IllegalArgumentException if no triangles belong to {@code partName} or a material is unknown
     */
    public static ModelVertices.IndexedMesh packPartIndexed(ObjModel model, String partName,
                                                            ModelPalette palette) {
        ObjModel.PartSpec spec = model.getPartSpec(partName);
        float px = spec == null ? 0f : spec.pivotX;
        float py = spec == null ? 0f : spec.pivotY;
        float pz = spec == null ? 0f : spec.pivotZ;
        return ModelVertices.packIndexed(model, t -> partName.equals(model.getPart(t)),
            px, py, pz, name -> colour(palette, name));
    }

    /**
     * Turns only the triangles whose OBJ group starts with {@code groupPrefix} into packed vertices.
     * @param model the parsed model file
     * @param groupPrefix prefix matched against {@link ObjModel#getGroup(int)}
     * @param palette the colours of the world materials
     * @return three vertices per matching triangle
     * @throws IllegalArgumentException if no triangle matches {@code groupPrefix} or a material is unknown
     */
    public static float[] verticesForGroupPrefix(ObjModel model, String groupPrefix, ModelPalette palette) {
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
            ModelVertices.writeTriangle(model, t, colour(palette, model.getMaterial(t)), out, at);
            at += 3 * VERTEX_SIZE;
        }
        return out;
    }
}

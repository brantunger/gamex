package io.github.brantunger.gamex.render;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.badlogic.gdx.graphics.Color;
import com.badlogic.gdx.graphics.PerspectiveCamera;
import com.badlogic.gdx.math.Vector3;
import io.github.brantunger.gamex.TestTuning;
import io.github.brantunger.gamex.data.AssetText;
import io.github.brantunger.gamex.model.ModelPalette;
import io.github.brantunger.gamex.model.ModelVertices;
import io.github.brantunger.gamex.model.ObjModel;
import io.github.brantunger.gamex.settings.SettingsDefaults;
import io.github.brantunger.gamex.settings.WindowDefaults;
import io.github.brantunger.gamex.weapon.HandModel;
import io.github.brantunger.gamex.weapon.PistolModel;
import io.github.brantunger.gamex.weapon.RailLanceModel;
import io.github.brantunger.gamex.weapon.ScattergunModel;
import io.github.brantunger.gamex.world.Door;
import io.github.brantunger.gamex.world.Pickup;
import io.github.brantunger.gamex.world.WorldModels;
import org.junit.jupiter.api.Test;

import java.util.Arrays;
import java.util.LinkedHashMap;
import java.util.Map;

/**
 * Verifies the palette files and {@code data/render.txt}: the shipped files hold the colours and settings the game
 * had in code, an edited value reaches the packed vertices and the renderer's arithmetic, and every mistake in a
 * file is reported with the file and the key.
 */
class PalettesTest {

    /** Offset of the red component in a packed vertex: after the position and the normal. */
    private static final int COLOUR = 6;

    private static final String WORLD_FILE = "data/test-palette-world.txt";
    private static final String RENDER_FILE = "data/test-render.txt";

    private static final Palettes PALETTES = TestTuning.PALETTES;
    private static final RenderSettings RENDER = TestTuning.RENDER;

    private static float[] rgbg(float red, float green, float blue, float gloss) {
        return new float[] {red, green, blue, gloss};
    }

    // The five tables below are the Material enums and ACCENT_ arrays of WorldModels, PistolModel, ScattergunModel,
    // RailLanceModel and HandModel as they were at 10cd4a4, before the colours moved to assets/data/.

    private static Map<String, float[]> worldBeforeTheMove() {
        Map<String, float[]> expected = new LinkedHashMap<>();
        expected.put("material.canister_body", rgbg(0.22f, 0.82f, 0.71f, 0.45f));
        expected.put("material.canister_band", rgbg(0.16f, 0.27f, 0.30f, 0.3f));
        expected.put("material.canister_cap", rgbg(0.50f, 0.53f, 0.55f, 0.8f));
        expected.put("material.canister_emblem", rgbg(0.91f, 0.96f, 0.81f, 0.2f));
        expected.put("material.board_foot", rgbg(0.36f, 0.35f, 0.33f, 0.2f));
        expected.put("material.board_post", rgbg(0.47f, 0.45f, 0.42f, 0.3f));
        expected.put("material.board_rim", rgbg(0.12f, 0.10f, 0.18f, 0.2f));
        expected.put("material.board_white", rgbg(0.91f, 0.90f, 0.85f, 0.1f));
        expected.put("material.board_violet", rgbg(0.41f, 0.27f, 0.77f, 0.2f));
        expected.put("material.board_bullseye", rgbg(0.89f, 0.24f, 0.56f, 0.3f));
        expected.put("material.orb_core", rgbg(0.95f, 0.85f, 1.00f, 1.0f));
        expected.put("material.orb_shell", rgbg(0.62f, 0.32f, 1.00f, 0.8f));
        expected.put("material.medkit_case", rgbg(0.90f, 0.92f, 0.90f, 0.35f));
        expected.put("material.medkit_trim", rgbg(0.30f, 0.35f, 0.38f, 0.5f));
        expected.put("material.medkit_cross", rgbg(0.92f, 0.20f, 0.26f, 0.4f));
        expected.put("material.armor_plate", rgbg(0.18f, 0.52f, 0.86f, 0.65f));
        expected.put("material.armor_trim", rgbg(0.42f, 0.48f, 0.56f, 0.75f));
        expected.put("material.armor_core", rgbg(0.45f, 0.92f, 1.00f, 0.9f));
        expected.put("material.cell_cap", rgbg(0.32f, 0.28f, 0.42f, 0.6f));
        expected.put("material.cell_core", rgbg(0.76f, 0.42f, 1.00f, 0.95f));
        expected.put("material.bot_armor", rgbg(0.34f, 0.37f, 0.43f, 0.65f));
        expected.put("material.bot_dark", rgbg(0.15f, 0.17f, 0.21f, 0.40f));
        expected.put("material.bot_trim", rgbg(0.52f, 0.56f, 0.63f, 0.80f));
        expected.put("material.bot_visor", rgbg(1.00f, 0.22f, 0.28f, 0.95f));
        expected.put("material.bot_core", rgbg(1.00f, 0.48f, 0.18f, 0.90f));
        expected.put("material.bolt_core", rgbg(1.00f, 0.82f, 0.78f, 1.00f));
        expected.put("material.bolt_shell", rgbg(1.00f, 0.22f, 0.26f, 0.85f));
        expected.put("material.door_armor", rgbg(0.36f, 0.40f, 0.46f, 0.55f));
        expected.put("material.door_trim", rgbg(0.22f, 0.25f, 0.30f, 0.65f));
        expected.put("material.door_dark", rgbg(0.13f, 0.15f, 0.18f, 0.35f));
        expected.put("material.door_status", rgbg(0.62f, 0.78f, 0.92f, 0.85f));
        expected.put("material.keycard_body", rgbg(1.00f, 0.72f, 0.16f, 0.85f));
        expected.put("material.keycard_trim", rgbg(0.22f, 0.25f, 0.30f, 0.70f));
        expected.put("material.keycard_stripe", rgbg(0.12f, 0.14f, 0.18f, 0.50f));
        expected.put("material.keycard_chip", rgbg(0.98f, 0.94f, 0.68f, 0.95f));
        expected.put("material.console_dark", rgbg(0.16f, 0.19f, 0.23f, 0.45f));
        expected.put("material.console_body", rgbg(0.30f, 0.34f, 0.40f, 0.60f));
        expected.put("material.console_trim", rgbg(0.48f, 0.52f, 0.58f, 0.75f));
        expected.put("material.console_keypad", rgbg(0.20f, 0.24f, 0.28f, 0.40f));
        expected.put("material.console_screen", rgbg(0.98f, 0.28f, 0.22f, 0.95f));
        expected.put("material.shotgun_metal", rgbg(0.25f, 0.28f, 0.33f, 0.70f));
        expected.put("material.shotgun_dark", rgbg(0.11f, 0.13f, 0.16f, 0.45f));
        expected.put("material.shotgun_polymer", rgbg(0.26f, 0.28f, 0.32f, 0.18f));
        expected.put("material.shotgun_wood", rgbg(0.46f, 0.27f, 0.14f, 0.22f));
        expected.put("material.shell_crimson", rgbg(0.88f, 0.17f, 0.20f, 0.45f));
        expected.put("material.shell_brass", rgbg(0.96f, 0.72f, 0.22f, 0.80f));
        expected.put("material.shell_crate", rgbg(0.20f, 0.26f, 0.22f, 0.30f));
        expected.put("material.hazard_frame", rgbg(0.22f, 0.25f, 0.30f, 0.65f));
        expected.put("material.hazard_dark", rgbg(0.08f, 0.10f, 0.12f, 0.30f));
        expected.put("material.hazard_coil", rgbg(0.18f, 0.86f, 1.00f, 0.95f));
        expected.put("material.rail_metal", rgbg(0.24f, 0.28f, 0.35f, 0.72f));
        expected.put("material.rail_dark", rgbg(0.10f, 0.12f, 0.16f, 0.45f));
        expected.put("material.rail_copper", rgbg(0.88f, 0.54f, 0.22f, 0.80f));
        expected.put("material.rail_core", rgbg(0.18f, 0.94f, 1.00f, 0.98f));
        expected.put("material.rail_beam", rgbg(0.36f, 0.96f, 1.00f, 1.00f));
        expected.put("material.rail_beam_core", rgbg(0.88f, 0.98f, 1.00f, 1.00f));
        expected.put("accent.door_unlocked", rgbg(0.62f, 0.78f, 0.92f, 0.85f));
        expected.put("accent.door_amber", rgbg(1.00f, 0.72f, 0.16f, 0.95f));
        expected.put("accent.door_cyan", rgbg(0.18f, 0.92f, 0.98f, 0.95f));
        expected.put("accent.door_lockdown", rgbg(0.98f, 0.22f, 0.24f, 0.95f));
        expected.put("accent.door_boss", rgbg(0.84f, 0.24f, 0.96f, 0.95f));
        expected.put("accent.door_open", rgbg(0.24f, 0.96f, 0.48f, 0.95f));
        expected.put("accent.keycard_amber", rgbg(1.00f, 0.72f, 0.16f, 0.85f));
        expected.put("accent.keycard_cyan", rgbg(0.18f, 0.92f, 0.98f, 0.85f));
        expected.put("accent.console_locked", rgbg(0.98f, 0.28f, 0.22f, 0.95f));
        expected.put("accent.console_active", rgbg(0.24f, 0.96f, 0.48f, 0.95f));
        expected.put("accent.warden_phase1", rgbg(1.00f, 0.30f, 0.14f, 0.96f));
        expected.put("accent.warden_overdrive", rgbg(0.42f, 0.54f, 1.00f, 0.98f));
        expected.put("accent.hazard_live", rgbg(0.18f, 0.86f, 1.00f, 0.95f));
        expected.put("accent.rail_ready", rgbg(0.18f, 0.94f, 1.00f, 0.98f));
        expected.put("accent.rail_beam", rgbg(0.36f, 0.96f, 1.00f, 1.00f));
        return expected;
    }

    private static Map<String, float[]> pistolBeforeTheMove() {
        Map<String, float[]> expected = new LinkedHashMap<>();
        expected.put("material.gunmetal", rgbg(0.23f, 0.25f, 0.30f, 0.6f));
        expected.put("material.gunmetal_top", rgbg(0.28f, 0.30f, 0.36f, 1f));
        expected.put("material.dark_metal", rgbg(0.08f, 0.09f, 0.11f, 0.4f));
        expected.put("material.steel", rgbg(0.60f, 0.62f, 0.68f, 1f));
        expected.put("material.bore", rgbg(0.02f, 0.02f, 0.03f, 0f));
        expected.put("material.brass", rgbg(0.95f, 0.66f, 0.20f, 0.6f));
        expected.put("material.sight_dot", rgbg(0.88f, 0.90f, 0.86f, 0f));
        expected.put("material.polymer", rgbg(0.34f, 0.29f, 0.21f, 0f));
        expected.put("material.polymer_dark", rgbg(0.24f, 0.205f, 0.15f, 0f));
        expected.put("material.stipple", rgbg(0.41f, 0.355f, 0.26f, 0f));
        return expected;
    }

    private static Map<String, float[]> scattergunBeforeTheMove() {
        Map<String, float[]> expected = new LinkedHashMap<>();
        expected.put("material.gunmetal", rgbg(0.22f, 0.25f, 0.30f, 0.6f));
        expected.put("material.gunmetal_top", rgbg(0.28f, 0.30f, 0.36f, 1f));
        expected.put("material.dark_metal", rgbg(0.08f, 0.09f, 0.11f, 0.4f));
        expected.put("material.steel", rgbg(0.60f, 0.62f, 0.68f, 1f));
        expected.put("material.bore", rgbg(0.02f, 0.02f, 0.03f, 0f));
        expected.put("material.brass", rgbg(0.95f, 0.68f, 0.22f, 0.7f));
        expected.put("material.sight_dot", rgbg(0.88f, 0.90f, 0.86f, 0f));
        expected.put("material.sight_amber", rgbg(1.00f, 0.66f, 0.14f, 0.3f));
        expected.put("material.polymer", rgbg(0.24f, 0.26f, 0.30f, 0.15f));
        expected.put("material.polymer_dark", rgbg(0.14f, 0.15f, 0.18f, 0.10f));
        expected.put("material.stipple", rgbg(0.30f, 0.32f, 0.36f, 0.10f));
        expected.put("material.shell_red", rgbg(0.84f, 0.16f, 0.20f, 0.45f));
        expected.put("material.heatshield", rgbg(0.16f, 0.18f, 0.22f, 0.60f));
        expected.put("material.wood", rgbg(0.46f, 0.27f, 0.14f, 0.22f));
        expected.put("material.wood_dark", rgbg(0.31f, 0.17f, 0.09f, 0.15f));
        return expected;
    }

    private static Map<String, float[]> railLanceBeforeTheMove() {
        Map<String, float[]> expected = new LinkedHashMap<>();
        expected.put("material.gunmetal", rgbg(0.22f, 0.25f, 0.31f, 0.65f));
        expected.put("material.gunmetal_top", rgbg(0.30f, 0.33f, 0.40f, 1f));
        expected.put("material.dark_metal", rgbg(0.08f, 0.09f, 0.12f, 0.45f));
        expected.put("material.steel", rgbg(0.62f, 0.66f, 0.72f, 1f));
        expected.put("material.bore", rgbg(0.02f, 0.02f, 0.03f, 0f));
        expected.put("material.polymer", rgbg(0.22f, 0.24f, 0.28f, 0.15f));
        expected.put("material.polymer_dark", rgbg(0.13f, 0.14f, 0.17f, 0.10f));
        expected.put("material.stipple", rgbg(0.28f, 0.30f, 0.35f, 0.10f));
        expected.put("material.sight_dot", rgbg(0.32f, 0.96f, 1.00f, 0.60f));
        expected.put("material.optic_glass", rgbg(0.14f, 0.68f, 0.92f, 0.95f));
        expected.put("material.rail_copper", rgbg(0.88f, 0.54f, 0.22f, 0.80f));
        expected.put("material.rail_core", rgbg(0.18f, 0.94f, 1.00f, 0.98f));
        expected.put("accent.ready", rgbg(0.18f, 0.94f, 1.00f, 0.98f));
        expected.put("accent.recharging", rgbg(1.00f, 0.44f, 0.14f, 0.70f));
        expected.put("accent.empty", rgbg(0.42f, 0.14f, 0.16f, 0.35f));
        return expected;
    }

    private static Map<String, float[]> handBeforeTheMove() {
        Map<String, float[]> expected = new LinkedHashMap<>();
        expected.put("material.glove", rgbg(0.200f, 0.202f, 0.220f, 0f));
        expected.put("material.pads", rgbg(0.250f, 0.252f, 0.272f, 0f));
        expected.put("material.cuff", rgbg(0.270f, 0.270f, 0.295f, 0f));
        expected.put("material.strap", rgbg(0.100f, 0.100f, 0.115f, 0f));
        expected.put("material.sleeve", rgbg(0.330f, 0.370f, 0.220f, 0f));
        return expected;
    }

    /** Compares every colour of a palette with the table, bit for bit, in both directions. */
    private static void assertPalette(Map<String, float[]> expected, ModelPalette palette, int materials,
                                      int accents) {
        int foundMaterials = 0;
        int foundAccents = 0;
        for (Map.Entry<String, float[]> entry : expected.entrySet()) {
            String key = entry.getKey();
            float[] loaded;
            if (key.startsWith(ModelPalette.MATERIAL_PREFIX)) {
                loaded = palette.colour(key.substring(ModelPalette.MATERIAL_PREFIX.length()));
                foundMaterials++;
            } else {
                assertTrue(key.startsWith(ModelPalette.ACCENT_PREFIX), key);
                loaded = palette.accent(key.substring(ModelPalette.ACCENT_PREFIX.length()));
                foundAccents++;
            }
            assertEquals(ModelPalette.COMPONENTS, loaded.length, key);
            for (int i = 0; i < loaded.length; i++) {
                assertEquals(Float.floatToIntBits(entry.getValue()[i]), Float.floatToIntBits(loaded[i]),
                    key + " component " + i + ": expected " + entry.getValue()[i] + ", loaded " + loaded[i]);
            }
        }
        assertEquals(materials, foundMaterials, "materials in the table");
        assertEquals(accents, foundAccents, "accents in the table");
    }

    @Test
    void shippedPalettesHoldEveryColourTheModelsHadInCode() {
        // The enums fix which names a file must hold, so a table as long as its enum covers the whole file.
        assertPalette(worldBeforeTheMove(), PALETTES.world, WorldModels.Material.values().length,
            WorldModels.Accent.values().length);
        assertPalette(pistolBeforeTheMove(), PALETTES.pistol, PistolModel.Material.values().length, 0);
        assertPalette(scattergunBeforeTheMove(), PALETTES.scattergun, ScattergunModel.Material.values().length, 0);
        assertPalette(railLanceBeforeTheMove(), PALETTES.railLance, RailLanceModel.Material.values().length,
            RailLanceModel.Accent.values().length);
        assertPalette(handBeforeTheMove(), PALETTES.hand, HandModel.Material.values().length, 0);

        assertEquals(56, WorldModels.Material.values().length);
        assertEquals(15, WorldModels.Accent.values().length);
        assertEquals(10, PistolModel.Material.values().length);
        assertEquals(15, ScattergunModel.Material.values().length);
        assertEquals(12, RailLanceModel.Material.values().length);
        assertEquals(3, RailLanceModel.Accent.values().length);
        assertEquals(5, HandModel.Material.values().length);
    }

    @Test
    void paletteFilesNameTheirMaterialsAndAccentsAsTheEnumsDo() {
        for (WorldModels.Material material : WorldModels.Material.values()) {
            assertEquals(ModelPalette.COMPONENTS, PALETTES.world.colour(material.name).length, material.name);
        }
        for (WorldModels.Accent accent : WorldModels.Accent.values()) {
            assertEquals(ModelPalette.COMPONENTS, PALETTES.world.accent(accent.name).length, accent.name);
        }
        for (PistolModel.Material material : PistolModel.Material.values()) {
            assertEquals(ModelPalette.COMPONENTS, PALETTES.pistol.colour(material.name).length, material.name);
        }
        for (ScattergunModel.Material material : ScattergunModel.Material.values()) {
            assertEquals(ModelPalette.COMPONENTS, PALETTES.scattergun.colour(material.name).length, material.name);
        }
        for (RailLanceModel.Material material : RailLanceModel.Material.values()) {
            assertEquals(ModelPalette.COMPONENTS, PALETTES.railLance.colour(material.name).length, material.name);
        }
        for (RailLanceModel.Accent accent : RailLanceModel.Accent.values()) {
            assertEquals(ModelPalette.COMPONENTS, PALETTES.railLance.accent(accent.name).length, accent.name);
        }
        for (HandModel.Material material : HandModel.Material.values()) {
            assertEquals(ModelPalette.COMPONENTS, PALETTES.hand.colour(material.name).length, material.name);
        }
    }

    @Test
    void weaponsKeepTheirOwnColourForAMaterialNameTheyShare() {
        assertArrayEquals(rgbg(0.23f, 0.25f, 0.30f, 0.6f), PALETTES.pistol.colour("gunmetal"), 0f);
        assertArrayEquals(rgbg(0.22f, 0.25f, 0.30f, 0.6f), PALETTES.scattergun.colour("gunmetal"), 0f);
        assertArrayEquals(rgbg(0.22f, 0.25f, 0.31f, 0.65f), PALETTES.railLance.colour("gunmetal"), 0f);
        for (String shared : new String[] {"brass", "polymer", "polymer_dark", "stipple"}) {
            assertFalse(Arrays.equals(PALETTES.pistol.colour(shared), PALETTES.scattergun.colour(shared)), shared);
        }
        assertFalse(Arrays.equals(PALETTES.pistol.colour("sight_dot"), PALETTES.railLance.colour("sight_dot")));
    }

    @Test
    void shippedRenderSettingsHoldTheValuesTheRendererHadInCode() {
        assertEquals(0.1f, RENDER.clipNear, 0f);
        assertEquals(60f, RENDER.clipFar, 0f);
        assertEquals(0.035f, RENDER.fogRed, 0f);
        assertEquals(0.035f, RENDER.fogGreen, 0f);
        assertEquals(0.055f, RENDER.fogBlue, 0f);
        assertEquals(3.5f, RENDER.fogStart, 0f);
        assertEquals(20f, RENDER.fogEnd, 0f);

        // WorldRenderer.addPickupGlows at 10cd4a4: one case per kind, ENERGY_CELL sharing the default.
        assertEquals(0.42f, RENDER.pickupHaloSize, 0f);
        Map<Pickup.Kind, float[]> halos = new LinkedHashMap<>();
        halos.put(Pickup.Kind.MEDKIT, rgbg(1f, 0.35f, 0.4f, 0.4f));
        halos.put(Pickup.Kind.ARMOR, rgbg(0.3f, 0.78f, 1f, 0.45f));
        halos.put(Pickup.Kind.ENERGY_CELL, rgbg(0.75f, 0.42f, 1f, 0.48f));
        halos.put(Pickup.Kind.AMBER_KEYCARD, rgbg(1f, 0.74f, 0.18f, 0.52f));
        halos.put(Pickup.Kind.CYAN_KEYCARD, rgbg(0.2f, 0.92f, 1f, 0.52f));
        halos.put(Pickup.Kind.SCATTERGUN, rgbg(1f, 0.56f, 0.20f, 0.54f));
        halos.put(Pickup.Kind.SHELLS, rgbg(0.95f, 0.34f, 0.24f, 0.48f));
        halos.put(Pickup.Kind.RAIL_LANCE, rgbg(0.22f, 0.94f, 1.00f, 0.56f));
        halos.put(Pickup.Kind.RAIL_SLUGS, rgbg(0.18f, 0.80f, 1.00f, 0.48f));
        assertEquals(Pickup.Kind.values().length, halos.size(), "a halo for every kind of pickup");
        for (Map.Entry<Pickup.Kind, float[]> halo : halos.entrySet()) {
            assertArrayEquals(halo.getValue(), RENDER.pickupHalo(halo.getKey()), 0f, halo.getKey().name());
        }

        // WorldRenderer.pickupAccent at 10cd4a4: the keycards and the rail pickups, and no accent for the rest.
        for (Pickup.Kind kind : Pickup.Kind.values()) {
            WorldModels.Accent expected;
            switch (kind) {
                case AMBER_KEYCARD:
                    expected = WorldModels.Accent.KEYCARD_AMBER;
                    break;
                case CYAN_KEYCARD:
                    expected = WorldModels.Accent.KEYCARD_CYAN;
                    break;
                case RAIL_LANCE:
                case RAIL_SLUGS:
                    expected = WorldModels.Accent.RAIL_READY;
                    break;
                default:
                    expected = null;
                    break;
            }
            assertSame(expected, RENDER.pickupAccent(kind), kind.name());
        }

        // WorldRenderer.addConsoleGlows at 10cd4a4.
        assertEquals(0.46f, RENDER.consoleHaloSize, 0f);
        assertArrayEquals(rgbg(0.25f, 0.96f, 0.52f, 0.42f), RENDER.consoleHalo(true), 0f);
        assertArrayEquals(rgbg(1f, 0.28f, 0.22f, 0.38f), RENDER.consoleHalo(false), 0f);

        // WorldRenderer.doorAccent at 10cd4a4.
        assertSame(WorldModels.Accent.DOOR_OPEN, RENDER.doorOpeningAccent);
        assertSame(WorldModels.Accent.DOOR_UNLOCKED, RENDER.doorAccent(Door.LockKind.NONE));
        assertSame(WorldModels.Accent.DOOR_AMBER, RENDER.doorAccent(Door.LockKind.AMBER_KEY));
        assertSame(WorldModels.Accent.DOOR_CYAN, RENDER.doorAccent(Door.LockKind.CYAN_KEY));
        assertSame(WorldModels.Accent.DOOR_LOCKDOWN, RENDER.doorAccent(Door.LockKind.LOCKDOWN));
        assertSame(WorldModels.Accent.DOOR_BOSS, RENDER.doorAccent(Door.LockKind.BOSS));
        assertEquals(5, Door.LockKind.values().length, "an accent for every kind of lock");
    }

    private static final String CANISTER_FACE = "v 0 0 0\nv 1 0 0\nv 0 1 0\nusemtl canister_body\nf 1 2 3\n";

    @Test
    void anEditedWorldColourReachesThePackedVertices() {
        ObjModel model = ObjModel.parse(CANISTER_FACE);
        float[] shipped = WorldModels.vertices(model, PALETTES.world);
        assertArrayEquals(rgbg(0.22f, 0.82f, 0.71f, 0.45f), Arrays.copyOfRange(shipped, COLOUR, COLOUR + 4), 0f);

        ModelPalette edited = TestTuning.worldPaletteWith("material.canister_body", "0.9, 0.1, 0.2, 0.3");
        float[] expected = rgbg(0.9f, 0.1f, 0.2f, 0.3f);
        float[] packed = WorldModels.vertices(model, edited);
        for (int vertex = 0; vertex < 3; vertex++) {
            int at = vertex * ModelVertices.VERTEX_SIZE + COLOUR;
            assertArrayEquals(expected, Arrays.copyOfRange(packed, at, at + 4), 0f, "vertex " + vertex);
        }
        // The meshes the renderer builds take the colour from the same palette.
        ModelVertices.IndexedMesh whole = WorldRenderer.unpackWorldModel(model, "models/test.obj", "test", edited);
        assertArrayEquals(expected, Arrays.copyOfRange(whole.vertices, COLOUR, COLOUR + 4), 0f);
        ModelVertices.IndexedMesh part =
            WorldRenderer.unpackWorldPart(model, "models/test.obj", "test", ObjModel.ROOT_PART, edited);
        assertArrayEquals(expected, Arrays.copyOfRange(part.vertices, COLOUR, COLOUR + 4), 0f);
        assertArrayEquals(expected,
            Arrays.copyOfRange(WorldModels.verticesForGroupPrefix(model, "", edited), COLOUR, COLOUR + 4), 0f);
        // The other materials keep their colours.
        assertArrayEquals(PALETTES.world.colour("canister_band"), edited.colour("canister_band"), 0f);
    }

    @Test
    void anEditedWeaponOrHandColourReachesThePackedVertices() {
        String face = "v 0 0 0\nv 1 0 0\nv 0 1 0\ng body\nusemtl gunmetal\nf 1 2 3\n"
            + "g slide\nusemtl steel\nf 1 2 3\n";
        ObjModel weapon = ObjModel.parse(face);
        float[] expected = rgbg(0.5f, 0.25f, 0.125f, 1f);

        ModelPalette pistol = PistolModel.parsePalette(PistolModel.PALETTE_PATH, TestTuning.withValue(
            AssetText.read("data", PistolModel.PALETTE_PATH), "material.gunmetal", "0.5, 0.25, 0.125, 1"));
        assertArrayEquals(expected,
            Arrays.copyOfRange(ViewmodelRenderer.unpackPistol(weapon, pistol).body, COLOUR, COLOUR + 4), 0f);
        assertArrayEquals(expected,
            Arrays.copyOfRange(ViewmodelRenderer.unpackPistol(weapon, pistol).bodyMesh.vertices, COLOUR, COLOUR + 4),
            0f);
        assertArrayEquals(rgbg(0.23f, 0.25f, 0.30f, 0.6f),
            Arrays.copyOfRange(PistolModel.from(weapon, PALETTES.pistol).body, COLOUR, COLOUR + 4), 0f);

        String pumped = face.replace("g slide", "g pump");
        ModelPalette scattergun = ScattergunModel.parsePalette(ScattergunModel.PALETTE_PATH, TestTuning.withValue(
            AssetText.read("data", ScattergunModel.PALETTE_PATH), "material.gunmetal", "0.5, 0.25, 0.125, 1"));
        assertArrayEquals(expected, Arrays.copyOfRange(
            ViewmodelRenderer.unpackScattergun(ObjModel.parse(pumped), scattergun).body, COLOUR, COLOUR + 4), 0f);

        String bolted = face.replace("g slide", "g bolt");
        ModelPalette railLance = RailLanceModel.parsePalette(RailLanceModel.PALETTE_PATH, TestTuning.withValue(
            AssetText.read("data", RailLanceModel.PALETTE_PATH), "material.gunmetal", "0.5, 0.25, 0.125, 1"));
        assertArrayEquals(expected, Arrays.copyOfRange(
            ViewmodelRenderer.unpackRailLance(ObjModel.parse(bolted), railLance).body, COLOUR, COLOUR + 4), 0f);

        ObjModel sleeve = ObjModel.parse("v 0 0 0\nv 1 0 0\nv 0 1 0\nusemtl sleeve\nf 1 2 3\n");
        ModelPalette hand = HandModel.parsePalette(HandModel.PALETTE_PATH, TestTuning.withValue(
            AssetText.read("data", HandModel.PALETTE_PATH), "material.sleeve", "0.5, 0.25, 0.125, 1"));
        assertArrayEquals(expected, Arrays.copyOfRange(HandModel.vertices(sleeve, hand), COLOUR, COLOUR + 4), 0f);
        assertArrayEquals(expected,
            Arrays.copyOfRange(ViewmodelRenderer.unpackHand(sleeve, hand).vertices, COLOUR, COLOUR + 4), 0f);
    }

    @Test
    void anEditedAccentIsTheOneThePaletteGivesAndTheWardenGlowsWith() {
        ModelPalette edited = TestTuning.worldPaletteWith("accent.warden_phase1", "0, 1, 0, 0.5");
        float[] accent = edited.accent(WorldModels.Accent.WARDEN_PHASE1.name);
        assertArrayEquals(rgbg(0f, 1f, 0f, 0.5f), accent, 0f);

        Color shippedGlow = WardenModel.glow(PALETTES.world.accent(WorldModels.Accent.WARDEN_PHASE1.name), false, 0f,
            0f, 0f, new Color());
        Color editedGlow = WardenModel.glow(accent, false, 0f, 0f, 0f, new Color());
        assertTrue(shippedGlow.r > shippedGlow.g, "the shipped first phase glows red");
        assertEquals(0f, editedGlow.r, 0f);
        assertTrue(editedGlow.g > 0f, "the edited first phase glows green");

        ModelPalette rail = RailLanceModel.parsePalette(RailLanceModel.PALETTE_PATH, TestTuning.withValue(
            AssetText.read("data", RailLanceModel.PALETTE_PATH), "accent.empty", "0.5, 0.5, 0.5, 0"));
        assertArrayEquals(rgbg(0.5f, 0.5f, 0.5f, 0f), rail.accent(RailLanceModel.Accent.EMPTY.name), 0f);
    }

    @Test
    void editedRenderSettingsChangeTheFogTheCameraAndTheTables() {
        // Shipped: nothing fogged up to 3.5, half at 11.75, everything from 20.
        assertEquals(0f, RENDER.fogFactor(3.5f), 0f);
        assertEquals(0.5f, RENDER.fogFactor(11.75f), 1e-6f);
        assertEquals(1f, RENDER.fogFactor(20f), 0f);
        assertEquals(1f, RENDER.fogFactor(60f), 0f);

        RenderSettings nearFog = TestTuning.renderWith("fog.end", "7.5");
        assertEquals(7.5f, nearFog.fogEnd, 0f);
        assertEquals(0.5f, nearFog.fogFactor(5.5f), 1e-6f);
        assertEquals(1f, nearFog.fogFactor(11.75f), 0f);
        Color scorch = new Color();
        Vector3 camera = new Vector3(0f, 0.5f, 0f);
        Vector3 mark = new Vector3(0f, 0.5f, 11.75f);
        assertEquals(0.5f, WorldRenderer.scorchColor(mark, camera, 0f, RENDER, scorch).a, 1e-6f);
        assertEquals(0f, WorldRenderer.scorchColor(mark, camera, 0f, nearFog, scorch).a, 0f);

        assertEquals(1.5f, TestTuning.renderWith("fog.start", "1.5").fogStart, 0f);
        RenderSettings green = TestTuning.renderWith("fog.colour", "0, 0.5, 0.25");
        assertEquals(0f, green.fogRed, 0f);
        assertEquals(0.5f, green.fogGreen, 0f);
        assertEquals(0.25f, green.fogBlue, 0f);

        assertEquals(0.1f, worldCamera(RENDER).near, 0f);
        assertEquals(60f, worldCamera(RENDER).far, 0f);
        assertEquals(0.25f, worldCamera(TestTuning.renderWith("clip.near", "0.25")).near, 0f);
        assertEquals(35f, worldCamera(TestTuning.renderWith("clip.far", "35")).far, 0f);

        assertArrayEquals(rgbg(0f, 1f, 0f, 1f),
            TestTuning.renderWith("pickup.halo.medkit", "0, 1, 0, 1").pickupHalo(Pickup.Kind.MEDKIT), 0f);
        assertEquals(0.9f, TestTuning.renderWith("pickup.halo.size", "0.9").pickupHaloSize, 0f);
        assertArrayEquals(rgbg(0f, 0f, 1f, 1f),
            TestTuning.renderWith("console.halo.locked", "0, 0, 1, 1").consoleHalo(false), 0f);
        assertEquals(0.2f, TestTuning.renderWith("console.halo.size", "0.2").consoleHaloSize, 0f);
        assertSame(WorldModels.Accent.DOOR_BOSS,
            TestTuning.renderWith("door.accent.none", "door_boss").doorAccent(Door.LockKind.NONE));
        assertSame(WorldModels.Accent.RAIL_BEAM,
            TestTuning.renderWith("door.accent.opening", "rail_beam").doorOpeningAccent);
        assertSame(WorldModels.Accent.HAZARD_LIVE,
            TestTuning.renderWith("pickup.accent.medkit", "hazard_live").pickupAccent(Pickup.Kind.MEDKIT));
        assertNull(TestTuning.renderWith("pickup.accent.rail_lance", "none").pickupAccent(Pickup.Kind.RAIL_LANCE));
    }

    @Test
    void paletteAndSettingsHandOutCopiesSoThatNoCallerCanChangeThem() {
        PALETTES.world.colour("canister_body")[0] = 9f;
        PALETTES.world.accent("door_open")[0] = 9f;
        RENDER.pickupHalo(Pickup.Kind.MEDKIT)[0] = 9f;
        RENDER.consoleHalo(true)[0] = 9f;
        assertEquals(0.22f, PALETTES.world.colour("canister_body")[0], 0f);
        assertEquals(0.24f, PALETTES.world.accent("door_open")[0], 0f);
        assertEquals(1f, RENDER.pickupHalo(Pickup.Kind.MEDKIT)[0], 0f);
        assertEquals(0.25f, RENDER.consoleHalo(true)[0], 0f);

        Map<String, float[]> materials = new LinkedHashMap<>();
        float[] grey = rgbg(0.5f, 0.5f, 0.5f, 0f);
        materials.put("grey", grey);
        ModelPalette own = new ModelPalette("test", materials, new LinkedHashMap<>());
        grey[0] = 1f;
        assertEquals(0.5f, own.colour("grey")[0], 0f);
    }

    private static PerspectiveCamera worldCamera(RenderSettings settings) {
        return WorldRenderer.createWorldCamera(null, settings, WindowDefaults.load(),
            SettingsDefaults.load().fov.initial);
    }

    private static String worldText() {
        return AssetText.read("data", WorldModels.PALETTE_PATH);
    }

    private static String renderText() {
        return AssetText.read("data", RenderSettings.PATH);
    }

    private static IllegalArgumentException paletteError(String text) {
        return assertThrows(IllegalArgumentException.class, () -> WorldModels.parsePalette(WORLD_FILE, text));
    }

    private static IllegalArgumentException renderError(String key, String value) {
        String text = TestTuning.withValue(renderText(), key, value);
        return assertThrows(IllegalArgumentException.class, () -> RenderSettings.parse(RENDER_FILE, text));
    }

    private static void assertNames(IllegalArgumentException error, String file, String key) {
        assertTrue(error.getMessage().contains(file), error.getMessage());
        assertTrue(error.getMessage().contains("'" + key + "'"), error.getMessage());
    }

    @Test
    void aPaletteRowWithAnUnknownNameAndAMissingRowFailNamingTheFileAndTheKey() {
        IllegalArgumentException unknownMaterial = paletteError(worldText() + "material.chrome: 1, 1, 1, 1\n");
        assertNames(unknownMaterial, WORLD_FILE, "material.chrome");
        assertTrue(unknownMaterial.getMessage().contains("Unknown"), unknownMaterial.getMessage());
        assertTrue(unknownMaterial.getMessage().contains("at line"), unknownMaterial.getMessage());

        IllegalArgumentException unknownAccent = paletteError(worldText() + "accent.door_pink: 1, 0.5, 0.5, 1\n");
        assertNames(unknownAccent, WORLD_FILE, "accent.door_pink");

        // A material of another model family is unknown here: the files are keyed per model.
        IllegalArgumentException otherFamily = paletteError(worldText() + "material.gunmetal: 0.2, 0.2, 0.3, 0.6\n");
        assertNames(otherFamily, WORLD_FILE, "material.gunmetal");

        IllegalArgumentException missingMaterial =
            paletteError(TestTuning.withValue(worldText(), "material.rail_core", null));
        assertNames(missingMaterial, WORLD_FILE, "material.rail_core");
        assertTrue(missingMaterial.getMessage().contains("Missing"), missingMaterial.getMessage());

        IllegalArgumentException missingAccent =
            paletteError(TestTuning.withValue(worldText(), "accent.hazard_live", null));
        assertNames(missingAccent, WORLD_FILE, "accent.hazard_live");

        IllegalArgumentException twice = paletteError(worldText() + "material.rail_core: 0, 0, 0, 0\n");
        assertNames(twice, WORLD_FILE, "material.rail_core");
        assertTrue(twice.getMessage().contains("Duplicate"), twice.getMessage());

        // Every family checks its own names the same way.
        String pistol = AssetText.read("data", PistolModel.PALETTE_PATH);
        assertNames(assertThrows(IllegalArgumentException.class,
            () -> PistolModel.parsePalette("data/p.txt", pistol + "material.wood: 0.4, 0.3, 0.1, 0.2\n")),
            "data/p.txt", "material.wood");
        assertNames(assertThrows(IllegalArgumentException.class,
            () -> PistolModel.parsePalette("data/p.txt", TestTuning.withValue(pistol, "material.brass", null))),
            "data/p.txt", "material.brass");
        String scattergun = AssetText.read("data", ScattergunModel.PALETTE_PATH);
        assertNames(assertThrows(IllegalArgumentException.class, () -> ScattergunModel.parsePalette("data/s.txt",
            TestTuning.withValue(scattergun, "material.wood", null))), "data/s.txt", "material.wood");
        String rail = AssetText.read("data", RailLanceModel.PALETTE_PATH);
        assertNames(assertThrows(IllegalArgumentException.class, () -> RailLanceModel.parsePalette("data/r.txt",
            TestTuning.withValue(rail, "accent.recharging", null))), "data/r.txt", "accent.recharging");
        String hand = AssetText.read("data", HandModel.PALETTE_PATH);
        assertNames(assertThrows(IllegalArgumentException.class, () -> HandModel.parsePalette("data/h.txt",
            TestTuning.withValue(hand, "material.glove", null))), "data/h.txt", "material.glove");
    }

    @Test
    void aPaletteValueThatIsNotFourNumbersFromZeroToOneFailsNamingTheFileAndTheKey() {
        for (String bad : new String[] {"0.5, 0.5, 0.5", "0.5, 0.5, 0.5, 0.5, 0.5", "0.5, 0.5, 0.5, 1.5",
            "-0.1, 0.5, 0.5, 0.5", "0.5f, 0.5, 0.5, 0.5", "red", ""}) {
            IllegalArgumentException error =
                paletteError(TestTuning.withValue(worldText(), "material.orb_core", bad));
            assertNames(error, WORLD_FILE, "material.orb_core");
            IllegalArgumentException accentError =
                paletteError(TestTuning.withValue(worldText(), "accent.door_cyan", bad));
            assertNames(accentError, WORLD_FILE, "accent.door_cyan");
        }

        Map<String, float[]> shortColour = new LinkedHashMap<>();
        shortColour.put("grey", new float[] {0.5f, 0.5f, 0.5f});
        assertThrows(IllegalArgumentException.class,
            () -> new ModelPalette("test", shortColour, new LinkedHashMap<>()));
        Map<String, float[]> notANumber = new LinkedHashMap<>();
        notANumber.put("glow", rgbg(0.5f, Float.NaN, 0.5f, 1f));
        assertThrows(IllegalArgumentException.class,
            () -> new ModelPalette("test", new LinkedHashMap<>(), notANumber));
    }

    @Test
    void aModelMaterialOrAccentThePaletteLacksFailsByName() {
        ModelPalette empty = new ModelPalette("test", new LinkedHashMap<>(), new LinkedHashMap<>());
        IllegalArgumentException noColour = assertThrows(IllegalArgumentException.class,
            () -> WorldModels.vertices(ObjModel.parse(CANISTER_FACE), empty));
        assertTrue(noColour.getMessage().contains("'canister_body'"), noColour.getMessage());
        IllegalArgumentException noAccent =
            assertThrows(IllegalArgumentException.class, () -> empty.accent("door_open"));
        assertTrue(noAccent.getMessage().contains("'door_open'"), noAccent.getMessage());

        // A material no enum lists still fails as it did before the colours moved to files.
        ObjModel chrome = ObjModel.parse(CANISTER_FACE.replace("canister_body", "chrome"));
        IllegalArgumentException unknown = assertThrows(IllegalArgumentException.class,
            () -> WorldModels.vertices(chrome, PALETTES.world));
        assertEquals("A world model uses a material this game has no colour for: 'chrome'", unknown.getMessage());
    }

    @Test
    void aRenderSettingThatIsMissingUnknownOrOutOfRangeFailsNamingTheFileAndTheKey() {
        for (String key : new String[] {"clip.near", "clip.far", "fog.colour", "fog.start", "fog.end",
            "pickup.halo.size", "pickup.halo.shells", "pickup.accent.shells", "console.halo.size",
            "console.halo.active", "console.halo.locked", "door.accent.lockdown", "door.accent.opening"}) {
            IllegalArgumentException missing = renderError(key, null);
            assertNames(missing, RENDER_FILE, key);
            assertTrue(missing.getMessage().contains("Missing"), missing.getMessage());
        }
        IllegalArgumentException unknown = assertThrows(IllegalArgumentException.class,
            () -> RenderSettings.parse(RENDER_FILE, renderText() + "fog.density: 0.5\n"));
        assertNames(unknown, RENDER_FILE, "fog.density");
        // The field of view is a setting of the player's, not of this file.
        assertNames(assertThrows(IllegalArgumentException.class,
            () -> RenderSettings.parse(RENDER_FILE, renderText() + "fov: 70\n")), RENDER_FILE, "fov");

        assertNames(renderError("clip.near", "0"), RENDER_FILE, "clip.near");
        assertNames(renderError("clip.near", "-1"), RENDER_FILE, "clip.near");
        assertNames(renderError("clip.far", "0.1"), RENDER_FILE, "clip.far");
        assertNames(renderError("fog.start", "-0.5"), RENDER_FILE, "fog.start");
        assertNames(renderError("fog.end", "3.5"), RENDER_FILE, "fog.end");
        assertNames(renderError("fog.end", "2"), RENDER_FILE, "fog.end");
        assertNames(renderError("fog.colour", "0.1, 0.1"), RENDER_FILE, "fog.colour");
        assertNames(renderError("fog.colour", "0.1, 0.1, 0.1, 1"), RENDER_FILE, "fog.colour");
        assertNames(renderError("fog.colour", "0.1, 0.1, 1.1"), RENDER_FILE, "fog.colour");
        assertNames(renderError("pickup.halo.size", "0"), RENDER_FILE, "pickup.halo.size");
        assertNames(renderError("console.halo.size", "-0.46"), RENDER_FILE, "console.halo.size");
        assertNames(renderError("pickup.halo.armor", "0.3, 0.78, 1"), RENDER_FILE, "pickup.halo.armor");
        assertNames(renderError("pickup.halo.armor", "0.3, 0.78, 1, -0.1"), RENDER_FILE, "pickup.halo.armor");
        assertNames(renderError("console.halo.active", "2, 0, 0, 1"), RENDER_FILE, "console.halo.active");
        assertNames(renderError("console.halo.locked", "red"), RENDER_FILE, "console.halo.locked");
    }

    @Test
    void aRenderSettingNamingAnAccentTheWorldPaletteLacksFailsNamingTheFileAndTheKey() {
        IllegalArgumentException door = renderError("door.accent.boss", "door_pink");
        assertNames(door, RENDER_FILE, "door.accent.boss");
        assertTrue(door.getMessage().contains("door_pink"), door.getMessage());
        assertTrue(door.getMessage().contains(WorldModels.PALETTE_PATH), door.getMessage());

        assertNames(renderError("pickup.accent.cyan_keycard", "keycard_teal"), RENDER_FILE,
            "pickup.accent.cyan_keycard");
        // A door always has an accent: "none" is for pickups only. The Rail Lance's own accents are not the world's.
        assertNames(renderError("door.accent.none", RenderSettings.NO_ACCENT), RENDER_FILE, "door.accent.none");
        assertNames(renderError("door.accent.opening", RenderSettings.NO_ACCENT), RENDER_FILE,
            "door.accent.opening");
        assertNames(renderError("pickup.accent.rail_lance", "recharging"), RENDER_FILE, "pickup.accent.rail_lance");
        assertNames(renderError("pickup.accent.medkit", "DOOR_OPEN"), RENDER_FILE, "pickup.accent.medkit");
    }
}

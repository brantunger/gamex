package io.github.brantunger.gamex.render;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
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
 * Verifies the palette files and {@code data/render.txt}: every key sets its own value, the shipped files load, an
 * edited value reaches the packed vertices and the renderer's arithmetic, and every mistake in a file is reported
 * with the file and the key.
 */
class PalettesTest {

    /** Offset of the red component in a packed vertex: after the position and the normal. */
    private static final int COLOUR = 6;

    private static final String WORLD_FILE = "data/test-palette-world.txt";
    private static final String RENDER_FILE = "data/test-render.txt";
    /** The test's own render settings, in which every value differs from the shipped one and from every other. */
    private static final String RENDER_FIXTURE = "data-fixtures/render.txt";

    private static final Palettes PALETTES = TestTuning.PALETTES;
    private static final RenderSettings RENDER = TestTuning.RENDER;

    private static float[] rgbg(float red, float green, float blue, float gloss) {
        return new float[] {red, green, blue, gloss};
    }

    /**
     * Gives every row of a palette file four numbers no other row has, so a material that took another's row, or a
     * colour still held in code, shows.
     * @param text the text of a palette file
     * @param expected filled with the colour given to each key
     * @return the text with every colour replaced
     */
    private static String withAColourOfItsOwnPerRow(String text, Map<String, float[]> expected) {
        StringBuilder out = new StringBuilder();
        for (String line : AssetText.splitLines(text)) {
            int colon = line.indexOf(':');
            if (AssetText.isComment(line.trim()) || colon < 0) {
                out.append(line).append('\n');
                continue;
            }
            int row = expected.size();
            float[] colour = rgbg((4 * row + 1) / 1024f, (4 * row + 2) / 1024f, (4 * row + 3) / 1024f,
                (4 * row + 4) / 1024f);
            expected.put(line.substring(0, colon), colour);
            out.append(line, 0, colon).append(": ").append(colour[0]).append(", ").append(colour[1]).append(", ")
                .append(colour[2]).append(", ").append(colour[3]).append('\n');
        }
        return out.toString();
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
    void everyRowOfAPaletteFileSetsItsOwnColour() {
        // The enums fix which names a file must hold, so a table as long as its enum covers the whole file.
        Map<String, float[]> world = new LinkedHashMap<>();
        assertPalette(world, WorldModels.parsePalette(WorldModels.PALETTE_PATH,
            withAColourOfItsOwnPerRow(TestTuning.text(WorldModels.PALETTE_PATH), world)),
            WorldModels.Material.values().length, WorldModels.Accent.values().length);
        Map<String, float[]> pistol = new LinkedHashMap<>();
        assertPalette(pistol, PistolModel.parsePalette(PistolModel.PALETTE_PATH,
            withAColourOfItsOwnPerRow(TestTuning.text(PistolModel.PALETTE_PATH), pistol)),
            PistolModel.Material.values().length, 0);
        Map<String, float[]> scattergun = new LinkedHashMap<>();
        assertPalette(scattergun, ScattergunModel.parsePalette(ScattergunModel.PALETTE_PATH,
            withAColourOfItsOwnPerRow(TestTuning.text(ScattergunModel.PALETTE_PATH), scattergun)),
            ScattergunModel.Material.values().length, 0);
        Map<String, float[]> railLance = new LinkedHashMap<>();
        assertPalette(railLance, RailLanceModel.parsePalette(RailLanceModel.PALETTE_PATH,
            withAColourOfItsOwnPerRow(TestTuning.text(RailLanceModel.PALETTE_PATH), railLance)),
            RailLanceModel.Material.values().length, RailLanceModel.Accent.values().length);
        Map<String, float[]> hand = new LinkedHashMap<>();
        assertPalette(hand, HandModel.parsePalette(HandModel.PALETTE_PATH,
            withAColourOfItsOwnPerRow(TestTuning.text(HandModel.PALETTE_PATH), hand)),
            HandModel.Material.values().length, 0);

        assertEquals(56, WorldModels.Material.values().length);
        assertEquals(15, WorldModels.Accent.values().length);
        assertEquals(10, PistolModel.Material.values().length);
        assertEquals(15, ScattergunModel.Material.values().length);
        assertEquals(12, RailLanceModel.Material.values().length);
        assertEquals(3, RailLanceModel.Accent.values().length);
        assertEquals(5, HandModel.Material.values().length);
    }

    /** Whatever colours and settings the shipped files hold, they load and name what the enums name. */
    @Test
    void theShippedPalettesAndRenderSettingsLoad() {
        assertNamesTheMaterialsAndAccentsOfTheEnums(Palettes.load());

        RenderSettings shipped = RenderSettings.load();
        assertTrue(shipped.clipNear > 0f && shipped.clipNear < shipped.clipFar);
        assertTrue(shipped.fogStart < shipped.fogEnd);
        for (Pickup.Kind kind : Pickup.Kind.values()) {
            assertEquals(ModelPalette.COMPONENTS, shipped.pickupHalo(kind).length, kind.name());
        }
        for (Door.LockKind lock : Door.LockKind.values()) assertNotNull(shipped.doorAccent(lock), lock.name());
        assertNotNull(shipped.doorOpeningAccent);
    }

    @Test
    void paletteFilesNameTheirMaterialsAndAccentsAsTheEnumsDo() {
        assertNamesTheMaterialsAndAccentsOfTheEnums(PALETTES);
    }

    private static void assertNamesTheMaterialsAndAccentsOfTheEnums(Palettes palettes) {
        for (WorldModels.Material material : WorldModels.Material.values()) {
            assertEquals(ModelPalette.COMPONENTS, palettes.world.colour(material.name).length, material.name);
        }
        for (WorldModels.Accent accent : WorldModels.Accent.values()) {
            assertEquals(ModelPalette.COMPONENTS, palettes.world.accent(accent.name).length, accent.name);
        }
        for (PistolModel.Material material : PistolModel.Material.values()) {
            assertEquals(ModelPalette.COMPONENTS, palettes.pistol.colour(material.name).length, material.name);
        }
        for (ScattergunModel.Material material : ScattergunModel.Material.values()) {
            assertEquals(ModelPalette.COMPONENTS, palettes.scattergun.colour(material.name).length, material.name);
        }
        for (RailLanceModel.Material material : RailLanceModel.Material.values()) {
            assertEquals(ModelPalette.COMPONENTS, palettes.railLance.colour(material.name).length, material.name);
        }
        for (RailLanceModel.Accent accent : RailLanceModel.Accent.values()) {
            assertEquals(ModelPalette.COMPONENTS, palettes.railLance.accent(accent.name).length, accent.name);
        }
        for (HandModel.Material material : HandModel.Material.values()) {
            assertEquals(ModelPalette.COMPONENTS, palettes.hand.colour(material.name).length, material.name);
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

    /**
     * The values on the right are those of the test's own file, where no two numbers are alike and every accent
     * differs from the shipped one, so a key read into another key's field, or a value still held in code, shows.
     */
    @Test
    void everyKeyOfARenderFileSetsItsOwnValue() {
        RenderSettings t = RenderSettings.parse(RENDER_FIXTURE, AssetText.read("data", RENDER_FIXTURE));
        assertEquals(0.13f, t.clipNear, 0f);
        assertEquals(61f, t.clipFar, 0f);
        assertEquals(0.118f, t.fogRed, 0f);
        assertEquals(0.135f, t.fogGreen, 0f);
        assertEquals(0.152f, t.fogBlue, 0f);
        assertEquals(3.7f, t.fogStart, 0f);
        assertEquals(21f, t.fogEnd, 0f);

        assertEquals(0.169f, t.pickupHaloSize, 0f);
        Map<Pickup.Kind, float[]> halos = new LinkedHashMap<>();
        halos.put(Pickup.Kind.MEDKIT, rgbg(0.186f, 0.203f, 0.220f, 0.237f));
        halos.put(Pickup.Kind.ARMOR, rgbg(0.254f, 0.271f, 0.288f, 0.305f));
        halos.put(Pickup.Kind.ENERGY_CELL, rgbg(0.322f, 0.339f, 0.356f, 0.373f));
        halos.put(Pickup.Kind.AMBER_KEYCARD, rgbg(0.390f, 0.407f, 0.424f, 0.441f));
        halos.put(Pickup.Kind.CYAN_KEYCARD, rgbg(0.458f, 0.475f, 0.492f, 0.509f));
        halos.put(Pickup.Kind.SCATTERGUN, rgbg(0.526f, 0.543f, 0.560f, 0.577f));
        halos.put(Pickup.Kind.SHELLS, rgbg(0.594f, 0.611f, 0.628f, 0.645f));
        halos.put(Pickup.Kind.RAIL_LANCE, rgbg(0.662f, 0.679f, 0.696f, 0.713f));
        halos.put(Pickup.Kind.RAIL_SLUGS, rgbg(0.730f, 0.747f, 0.764f, 0.781f));
        assertEquals(Pickup.Kind.values().length, halos.size(), "a halo for every kind of pickup");
        for (Map.Entry<Pickup.Kind, float[]> halo : halos.entrySet()) {
            assertArrayEquals(halo.getValue(), t.pickupHalo(halo.getKey()), 0f, halo.getKey().name());
        }

        assertSame(WorldModels.Accent.DOOR_OPEN, t.pickupAccent(Pickup.Kind.MEDKIT));
        assertSame(WorldModels.Accent.DOOR_BOSS, t.pickupAccent(Pickup.Kind.ARMOR));
        assertSame(WorldModels.Accent.DOOR_CYAN, t.pickupAccent(Pickup.Kind.ENERGY_CELL));
        assertSame(WorldModels.Accent.KEYCARD_CYAN, t.pickupAccent(Pickup.Kind.AMBER_KEYCARD));
        assertSame(WorldModels.Accent.KEYCARD_AMBER, t.pickupAccent(Pickup.Kind.CYAN_KEYCARD));
        assertSame(WorldModels.Accent.RAIL_BEAM, t.pickupAccent(Pickup.Kind.SCATTERGUN));
        assertSame(WorldModels.Accent.HAZARD_LIVE, t.pickupAccent(Pickup.Kind.SHELLS));
        assertNull(t.pickupAccent(Pickup.Kind.RAIL_LANCE));
        assertSame(WorldModels.Accent.CONSOLE_ACTIVE, t.pickupAccent(Pickup.Kind.RAIL_SLUGS));

        assertEquals(0.798f, t.consoleHaloSize, 0f);
        assertArrayEquals(rgbg(0.815f, 0.832f, 0.849f, 0.866f), t.consoleHalo(true), 0f);
        assertArrayEquals(rgbg(0.883f, 0.900f, 0.917f, 0.934f), t.consoleHalo(false), 0f);

        assertSame(WorldModels.Accent.WARDEN_PHASE1, t.doorOpeningAccent);
        assertSame(WorldModels.Accent.DOOR_AMBER, t.doorAccent(Door.LockKind.NONE));
        assertSame(WorldModels.Accent.DOOR_CYAN, t.doorAccent(Door.LockKind.AMBER_KEY));
        assertSame(WorldModels.Accent.DOOR_LOCKDOWN, t.doorAccent(Door.LockKind.CYAN_KEY));
        assertSame(WorldModels.Accent.DOOR_BOSS, t.doorAccent(Door.LockKind.LOCKDOWN));
        assertSame(WorldModels.Accent.DOOR_UNLOCKED, t.doorAccent(Door.LockKind.BOSS));
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
            TestTuning.text(PistolModel.PALETTE_PATH), "material.gunmetal", "0.5, 0.25, 0.125, 1"));
        assertArrayEquals(expected,
            Arrays.copyOfRange(ViewmodelRenderer.unpackPistol(weapon, pistol).body, COLOUR, COLOUR + 4), 0f);
        assertArrayEquals(expected,
            Arrays.copyOfRange(ViewmodelRenderer.unpackPistol(weapon, pistol).bodyMesh.vertices, COLOUR, COLOUR + 4),
            0f);
        assertArrayEquals(rgbg(0.23f, 0.25f, 0.30f, 0.6f),
            Arrays.copyOfRange(PistolModel.from(weapon, PALETTES.pistol).body, COLOUR, COLOUR + 4), 0f);

        String pumped = face.replace("g slide", "g pump");
        ModelPalette scattergun = ScattergunModel.parsePalette(ScattergunModel.PALETTE_PATH, TestTuning.withValue(
            TestTuning.text(ScattergunModel.PALETTE_PATH), "material.gunmetal", "0.5, 0.25, 0.125, 1"));
        assertArrayEquals(expected, Arrays.copyOfRange(
            ViewmodelRenderer.unpackScattergun(ObjModel.parse(pumped), scattergun).body, COLOUR, COLOUR + 4), 0f);

        String bolted = face.replace("g slide", "g bolt");
        ModelPalette railLance = RailLanceModel.parsePalette(RailLanceModel.PALETTE_PATH, TestTuning.withValue(
            TestTuning.text(RailLanceModel.PALETTE_PATH), "material.gunmetal", "0.5, 0.25, 0.125, 1"));
        assertArrayEquals(expected, Arrays.copyOfRange(
            ViewmodelRenderer.unpackRailLance(ObjModel.parse(bolted), railLance).body, COLOUR, COLOUR + 4), 0f);

        ObjModel sleeve = ObjModel.parse("v 0 0 0\nv 1 0 0\nv 0 1 0\nusemtl sleeve\nf 1 2 3\n");
        ModelPalette hand = HandModel.parsePalette(HandModel.PALETTE_PATH, TestTuning.withValue(
            TestTuning.text(HandModel.PALETTE_PATH), "material.sleeve", "0.5, 0.25, 0.125, 1"));
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
        assertTrue(shippedGlow.r > shippedGlow.g, "the first phase of the tests' own palette glows red");
        assertEquals(0f, editedGlow.r, 0f);
        assertTrue(editedGlow.g > 0f, "the edited first phase glows green");

        ModelPalette rail = RailLanceModel.parsePalette(RailLanceModel.PALETTE_PATH, TestTuning.withValue(
            TestTuning.text(RailLanceModel.PALETTE_PATH), "accent.empty", "0.5, 0.5, 0.5, 0"));
        assertArrayEquals(rgbg(0.5f, 0.5f, 0.5f, 0f), rail.accent(RailLanceModel.Accent.EMPTY.name), 0f);
    }

    @Test
    void editedRenderSettingsChangeTheFogTheCameraAndTheTables() {
        // The tests' own file: nothing fogged up to 3.5, half at 11.75, everything from 20.
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
        return WorldRenderer.createWorldCamera(null, settings, TestTuning.WINDOW,
            TestTuning.SETTINGS.fov.initial);
    }

    private static String worldText() {
        return TestTuning.text(WorldModels.PALETTE_PATH);
    }

    private static String renderText() {
        return TestTuning.text(RenderSettings.PATH);
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
        String pistol = TestTuning.text(PistolModel.PALETTE_PATH);
        assertNames(assertThrows(IllegalArgumentException.class,
            () -> PistolModel.parsePalette("data/p.txt", pistol + "material.wood: 0.4, 0.3, 0.1, 0.2\n")),
            "data/p.txt", "material.wood");
        assertNames(assertThrows(IllegalArgumentException.class,
            () -> PistolModel.parsePalette("data/p.txt", TestTuning.withValue(pistol, "material.brass", null))),
            "data/p.txt", "material.brass");
        String scattergun = TestTuning.text(ScattergunModel.PALETTE_PATH);
        assertNames(assertThrows(IllegalArgumentException.class, () -> ScattergunModel.parsePalette("data/s.txt",
            TestTuning.withValue(scattergun, "material.wood", null))), "data/s.txt", "material.wood");
        String rail = TestTuning.text(RailLanceModel.PALETTE_PATH);
        assertNames(assertThrows(IllegalArgumentException.class, () -> RailLanceModel.parsePalette("data/r.txt",
            TestTuning.withValue(rail, "accent.recharging", null))), "data/r.txt", "accent.recharging");
        String hand = TestTuning.text(HandModel.PALETTE_PATH);
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

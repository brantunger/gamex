package io.github.brantunger.gamex.settings;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import io.github.brantunger.gamex.TestTuning;
import io.github.brantunger.gamex.data.AssetText;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

/**
 * Checks the settings file: that each key sets its own default, range or step, that the shipped file loads and new
 * settings start at its defaults, that {@link GameSettings} follows the file it is given, and that a file the
 * settings cannot work with stops the load.
 */
class SettingsDefaultsTest {

    private static final String FILE = "settings.txt";

    private static String shippedText() {
        return TestTuning.text(SettingsDefaults.PATH);
    }

    /** @return the tests' own file with the value of one key replaced */
    private static String with(String key, String value) {
        List<String> lines = new ArrayList<>();
        boolean replaced = false;
        for (String line : AssetText.splitLines(shippedText())) {
            if (line.startsWith(key + ":")) {
                lines.add(key + ": " + value);
                replaced = true;
            } else {
                lines.add(line);
            }
        }
        assertTrue(replaced, "the tests' own file holds " + key);
        return String.join("\n", lines) + "\n";
    }

    private static SettingsDefaults defaultsWith(String key, String value) {
        return SettingsDefaults.parse(FILE, with(key, value));
    }

    private static void assertRejected(String text, String key) {
        IllegalArgumentException error =
            assertThrows(IllegalArgumentException.class, () -> SettingsDefaults.parse(FILE, text), key);
        assertTrue(error.getMessage().contains(FILE), error.getMessage());
        assertTrue(error.getMessage().contains("'" + key + "'"), error.getMessage());
    }

    private static void assertRange(SettingsDefaults.Range range, float initial, float min, float max, float step) {
        assertEquals(initial, range.initial, 0f);
        assertEquals(min, range.min, 0f);
        assertEquals(max, range.max, 0f);
        assertEquals(step, range.step, 0f);
    }

    /** A file in which no two numbers are alike, so a key read into another key's field, or held in code, shows. */
    @Test
    void everyKeyOfASettingsFileSetsItsOwnValue() {
        SettingsDefaults defaults = SettingsDefaults.parse(FILE, String.join("\n",
            "sensitivity.default: 0.21", "sensitivity.min: 0.06", "sensitivity.max: 0.36", "sensitivity.step: 0.03",
            "fov.default: 90", "fov.min: 80", "fov.max: 100", "fov.step: 2",
            "volume.default: 0.50", "volume.min: 0.2", "volume.max: 0.9", "volume.step: 0.10",
            "invert_y.default: true", "view_bob.default: false", "fullscreen.default: true",
            "minimap.default: false"));
        assertRange(defaults.sensitivity, 0.21f, 0.06f, 0.36f, 0.03f);
        assertRange(defaults.fov, 90f, 80f, 100f, 2f);
        assertRange(defaults.volume, 0.50f, 0.2f, 0.9f, 0.10f);
        assertTrue(defaults.invertY);
        assertFalse(defaults.viewBob);
        assertTrue(defaults.fullscreen);
        assertFalse(defaults.minimap);

        SettingsDefaults others = SettingsDefaults.parse(FILE, with("invert_y.default", "false"));
        assertFalse(others.invertY);
        assertFalse(SettingsDefaults.parse(FILE, with("fullscreen.default", "false")).fullscreen);
        assertTrue(SettingsDefaults.parse(FILE, with("view_bob.default", "true")).viewBob);
        assertTrue(SettingsDefaults.parse(FILE, with("minimap.default", "true")).minimap);
    }

    /** Whatever the shipped file holds, it loads, and settings made with no defaults of their own start there. */
    @Test
    void newSettingsStartAtTheShippedDefaults() {
        SettingsDefaults shipped = SettingsDefaults.load();
        for (SettingsDefaults.Range range : new SettingsDefaults.Range[] {
            shipped.sensitivity, shipped.fov, shipped.volume}) {
            assertTrue(range.min <= range.initial && range.initial <= range.max && range.step > 0f);
        }
        for (GameSettings settings : new GameSettings[] {
            new GameSettings(), new GameSettings(shipped), GameSettings.load(null, shipped),
            GameSettings.load(new MapKeyValueStore(), shipped)}) {
            assertEquals(shipped.sensitivity.initial, settings.getMouseSensitivity(), 0f);
            assertEquals(shipped.fov.initial, settings.getFov(), 0f);
            assertEquals(shipped.volume.initial, settings.getMasterVolume(), 0f);
            assertEquals(shipped.invertY, settings.isInvertY());
            assertEquals(shipped.viewBob, settings.isViewBob());
            assertEquals(shipped.fullscreen, settings.isFullscreen());
            assertEquals(shipped.minimap, settings.isMinimap());
        }
    }

    @Test
    void settingsStartAtTheDefaultsOfTheFileTheyAreGiven() {
        SettingsDefaults defaults = SettingsDefaults.parse(FILE, String.join("\n",
            "sensitivity.default: 0.25", "sensitivity.min: 0.05", "sensitivity.max: 0.30", "sensitivity.step: 0.05",
            "fov.default: 90", "fov.min: 80", "fov.max: 100", "fov.step: 2",
            "volume.default: 0.50", "volume.min: 0.2", "volume.max: 0.9", "volume.step: 0.25",
            "invert_y.default: true", "view_bob.default: false", "fullscreen.default: true",
            "minimap.default: false"));

        GameSettings settings = new GameSettings(defaults);
        assertSame(defaults, settings.defaults());
        assertEquals(0.25f, settings.getMouseSensitivity(), 0f);
        assertEquals(90f, settings.getFov(), 0f);
        assertEquals(0.50f, settings.getMasterVolume(), 0f);
        assertTrue(settings.isInvertY());
        assertFalse(settings.isViewBob());
        assertTrue(settings.isFullscreen());
        assertFalse(settings.isMinimap());

        // A store that holds nothing leaves every default; none is written a second time in load().
        GameSettings loaded = GameSettings.load(new MapKeyValueStore(), defaults);
        assertEquals(0.25f, loaded.getMouseSensitivity(), 0f);
        assertEquals(90f, loaded.getFov(), 0f);
        assertEquals(0.50f, loaded.getMasterVolume(), 0f);
        assertTrue(loaded.isInvertY());
        assertFalse(loaded.isViewBob());
        assertTrue(loaded.isFullscreen());
        assertFalse(loaded.isMinimap());
    }

    @Test
    void theSettingsRowsStepAndStopByTheFileTheyAreGiven() {
        GameSettings settings = new GameSettings(defaultsWith("fov.step", "2"));
        settings.adjustFov(1);
        assertEquals(72f, settings.getFov(), 0f);
        settings.adjustFov(-3);
        assertEquals(66f, settings.getFov(), 0f);

        settings = new GameSettings(defaultsWith("fov.max", "80"));
        settings.adjustFov(4);
        assertEquals(80f, settings.getFov(), 0f);
        settings.setFov(Float.NaN);
        assertEquals(70f, settings.getFov(), 0f);

        settings = new GameSettings(defaultsWith("sensitivity.step", "0.05"));
        settings.adjustSensitivity(2);
        assertEquals(0.22f, settings.getMouseSensitivity(), 1e-6f);
        settings = new GameSettings(defaultsWith("sensitivity.min", "0.10"));
        settings.adjustSensitivity(-5);
        assertEquals(0.10f, settings.getMouseSensitivity(), 1e-6f);

        settings = new GameSettings(defaultsWith("volume.step", "0.25"));
        settings.adjustMasterVolume(-1);
        assertEquals(0.55f, settings.getMasterVolume(), 1e-6f);
        settings = new GameSettings(defaultsWith("volume.max", "0.9"));
        settings.adjustMasterVolume(3);
        assertEquals(0.9f, settings.getMasterVolume(), 1e-6f);
    }

    @Test
    void aStoredValueOutsideANarrowedRangeIsClampedOnLoad() {
        MapKeyValueStore store = new MapKeyValueStore();
        GameSettings wide = TestTuning.settings();
        wide.setFov(110f);
        wide.setMouseSensitivity(0.03f);
        wide.setMasterVolume(1f);
        wide.setFullscreen(true);
        wide.setMinimap(false);
        wide.save(store);

        SettingsDefaults narrow = SettingsDefaults.parse(FILE, with("fov.max", "90")
            .replace("sensitivity.min: 0.03", "sensitivity.min: 0.06")
            .replace("volume.max: 1.0", "volume.max: 0.9"));
        GameSettings loaded = GameSettings.load(store, narrow);
        assertEquals(90f, loaded.getFov(), 0f);
        assertEquals(0.06f, loaded.getMouseSensitivity(), 1e-6f);
        assertEquals(0.9f, loaded.getMasterVolume(), 1e-6f);
        assertTrue(loaded.isFullscreen(), "a stored choice wins over the default");
        assertFalse(loaded.isMinimap(), "a stored choice wins over the default");
    }

    @Test
    void aRangeTheSettingCannotWorkWithStopsTheLoad() {
        assertRejected(with("fov.min", "120"), "fov.max");
        assertRejected(with("sensitivity.max", "0.01"), "sensitivity.max");
        assertRejected(with("volume.min", "1.5"), "volume.max");
        assertRejected(with("fov.step", "0"), "fov.step");
        assertRejected(with("fov.step", "0.5"), "fov.step");
        assertRejected(with("sensitivity.step", "0"), "sensitivity.step");
        assertRejected(with("sensitivity.step", "0.001"), "sensitivity.step");
        assertRejected(with("volume.step", "-0.1"), "volume.step");
        assertRejected(with("fov.default", "59"), "fov.default");
        assertRejected(with("fov.default", "111"), "fov.default");
        assertRejected(with("sensitivity.default", "0.5"), "sensitivity.default");
        assertRejected(with("volume.default", "1.2"), "volume.default");
        assertRejected(with("sensitivity.min", "-0.1"), "sensitivity.min");
        assertRejected(with("fov.min", "0"), "fov.min");
        assertRejected(with("fov.max", "180"), "fov.max");
        assertRejected(with("volume.min", "-0.5"), "volume.min");
        assertRejected(with("volume.max", "1.5"), "volume.max");
        // A range of one value is a range.
        assertEquals(70f, SettingsDefaults.parse(FILE, with("fov.min", "70").replace("fov.max: 110", "fov.max: 70"))
            .fov.max, 0f);
    }

    private static void assertRejectedAtItsLine(String key, String value) {
        String text = with(key, value);
        int line = 1 + AssetText.splitLines(text).indexOf(key + ": " + value);
        IllegalArgumentException error =
            assertThrows(IllegalArgumentException.class, () -> SettingsDefaults.parse(FILE, text), key);
        assertTrue(error.getMessage().startsWith("Invalid '" + key + "' in " + FILE + " at line " + line + ": "),
            error.getMessage());
        assertTrue(error.getMessage().endsWith(", got '" + value + "'"), error.getMessage());
    }

    @Test
    void aLeastMostOrDefaultValueFinerThanTheSettingIsKeptStopsTheLoad() {
        // GameSettings keeps the field of view in whole degrees and the other two in hundredths. A range end or a
        // default between two such values could not be reached, or would be rounded past the end of its range.
        assertRejectedAtItsLine("fov.max", "109.5");
        assertRejectedAtItsLine("fov.default", "72.5");
        assertRejectedAtItsLine("fov.min", "60.5");
        assertRejectedAtItsLine("sensitivity.max", "0.405");
        assertRejectedAtItsLine("sensitivity.default", "0.125");
        assertRejectedAtItsLine("sensitivity.min", "0.035");
        assertRejectedAtItsLine("volume.max", "0.999");
        assertRejectedAtItsLine("volume.default", "0.805");
        assertRejectedAtItsLine("volume.min", "0.001");

        // Trailing zeros are not a finer value.
        assertEquals(110f, defaultsWith("fov.max", "110.0").fov.max, 0f);
        assertEquals(0.40f, defaultsWith("sensitivity.max", "0.400").sensitivity.max, 0f);
    }

    @Test
    void everyValueASettingCanHoldIsADefaultBothWaysOfMakingSettingsStartAt() {
        for (int hundredths = 0; hundredths <= 100; hundredths++) {
            String text = String.format(Locale.ROOT, "%.2f", hundredths / 100f);
            SettingsDefaults defaults = SettingsDefaults.parse(FILE, with("volume.default", text)
                .replace("sensitivity.min: 0.03", "sensitivity.min: 0.00")
                .replace("sensitivity.max: 0.40", "sensitivity.max: 1.00")
                .replace("sensitivity.default: 0.12", "sensitivity.default: " + text));
            float expected = Float.parseFloat(text);
            for (GameSettings settings : new GameSettings[] {
                new GameSettings(defaults), GameSettings.load(null, defaults),
                GameSettings.load(new MapKeyValueStore(), defaults)}) {
                assertEquals(expected, settings.getMasterVolume(), 0f, text);
                assertEquals(expected, settings.getMouseSensitivity(), 0f, text);
            }
        }
        for (int degrees = 1; degrees <= 179; degrees++) {
            SettingsDefaults defaults = SettingsDefaults.parse(FILE, with("fov.default", Integer.toString(degrees))
                .replace("fov.min: 60", "fov.min: 1").replace("fov.max: 110", "fov.max: 179"));
            assertEquals(degrees, new GameSettings(defaults).getFov(), 0f);
            assertEquals(degrees, GameSettings.load(new MapKeyValueStore(), defaults).getFov(), 0f);
        }
    }

    @Test
    void aMissingUnknownOrUnreadableKeyStopsTheLoad() {
        for (String key : new String[] {"sensitivity.default", "fov.step", "volume.max", "invert_y.default",
            "view_bob.default", "fullscreen.default", "minimap.default"}) {
            StringBuilder without = new StringBuilder();
            for (String line : AssetText.splitLines(shippedText())) {
                if (!line.startsWith(key + ":")) without.append(line).append('\n');
            }
            assertRejected(without.toString(), key);
        }
        assertRejected(shippedText() + "fov.defualt: 75\n", "fov.defualt");
        assertRejected(shippedText() + "fov.default: 75\n", "fov.default");
        assertRejected(with("fov.default", "70f"), "fov.default");
        assertRejected(with("view_bob.default", "on"), "view_bob.default");
        assertRejected(with("fullscreen.default", ""), "fullscreen.default");
        assertRejected(with("minimap.default", "yes"), "minimap.default");
    }
}

package io.github.brantunger.gamex.settings;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.Test;

/**
 * Checks the window file: that the shipped file loads, that the size is the one the file holds, and that a file
 * without a usable size stops the load instead of falling back to one.
 */
class WindowDefaultsTest {

    private static final String FILE = "window.txt";

    private static String text(String width, String height) {
        return "window.width: " + width + "\nwindow.height: " + height + "\n";
    }

    private static void assertRejected(String text, String key) {
        IllegalArgumentException error =
            assertThrows(IllegalArgumentException.class, () -> WindowDefaults.parse(FILE, text), text);
        assertTrue(error.getMessage().contains(FILE), error.getMessage());
        assertTrue(error.getMessage().contains("'" + key + "'"), error.getMessage());
    }

    /** Whatever size the shipped file holds, it loads and is one a window can have. */
    @Test
    void theShippedFileLoads() {
        WindowDefaults window = WindowDefaults.load();
        assertTrue(window.width >= WindowDefaults.MIN_WIDTH && window.width <= WindowDefaults.MAX_WIDTH);
        assertTrue(window.height >= WindowDefaults.MIN_HEIGHT && window.height <= WindowDefaults.MAX_HEIGHT);
    }

    @Test
    void theSizeIsTheOneTheFileHolds() {
        WindowDefaults window = WindowDefaults.parse(FILE, "// A comment.\n" + text("1024", "768"));
        assertEquals(1024, window.width);
        assertEquals(768, window.height);

        WindowDefaults smallest = WindowDefaults.parse(FILE, text("160", "120"));
        assertEquals(WindowDefaults.MIN_WIDTH, smallest.width);
        assertEquals(WindowDefaults.MIN_HEIGHT, smallest.height);
        WindowDefaults largest = WindowDefaults.parse(FILE, text("7680", "4320"));
        assertEquals(WindowDefaults.MAX_WIDTH, largest.width);
        assertEquals(WindowDefaults.MAX_HEIGHT, largest.height);
    }

    @Test
    void aFileWithoutAUsableSizeStopsTheLoadNamingTheFileAndTheKey() {
        assertRejected("", "window.width");
        assertRejected("window.width: 1280\n", "window.height");
        assertRejected("window.height: 720\n", "window.width");
        assertRejected(text("1280", "720") + "window.depth: 24\n", "window.depth");
        assertRejected(text("1280", "720") + "window.width: 800\n", "window.width");
        assertRejected(text("wide", "720"), "window.width");
        assertRejected(text("1280.5", "720"), "window.width");
        assertRejected(text("1280", ""), "window.height");
        assertRejected(text("159", "720"), "window.width");
        assertRejected(text("7681", "720"), "window.width");
        assertRejected(text("1280", "119"), "window.height");
        assertRejected(text("1280", "4321"), "window.height");
        assertRejected(text("-1280", "720"), "window.width");
    }
}

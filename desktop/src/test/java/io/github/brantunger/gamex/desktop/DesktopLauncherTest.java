package io.github.brantunger.gamex.desktop;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import ch.qos.logback.classic.Level;
import ch.qos.logback.classic.Logger;
import ch.qos.logback.classic.spi.ILoggingEvent;
import ch.qos.logback.core.read.ListAppender;
import io.github.brantunger.gamex.settings.GameSettings;
import io.github.brantunger.gamex.settings.MapKeyValueStore;
import org.junit.jupiter.api.Test;
import org.slf4j.LoggerFactory;

import javax.imageio.ImageIO;
import java.awt.image.BufferedImage;
import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

class DesktopLauncherTest {

    private static final int[] DEFAULT_SIZE = {1280, 720};

    private static Path resolveDesktopFile(String relativePath) {
        Path fromDesktop = Paths.get(relativePath);
        if (Files.isRegularFile(fromDesktop)) return fromDesktop;
        Path fromRoot = Paths.get("desktop", relativePath);
        if (Files.isRegularFile(fromRoot)) return fromRoot;
        throw new IllegalStateException("Desktop file not found from working directory: " + relativePath);
    }

    /** @return the warnings the launcher logs while {@code action} runs, as formatted messages in order */
    private static List<String> captureWarnings(Runnable action) {
        Logger logger = (Logger) LoggerFactory.getLogger(DesktopLauncher.class);
        ListAppender<ILoggingEvent> appender = new ListAppender<>();
        boolean additive = logger.isAdditive();
        appender.start();
        logger.addAppender(appender);
        // The lines go to the list alone, not on to the console as well.
        logger.setAdditive(false);
        try {
            action.run();
        } finally {
            logger.setAdditive(additive);
            logger.detachAppender(appender);
            appender.stop();
        }
        List<String> warnings = new ArrayList<>();
        for (ILoggingEvent event : appender.list) {
            assertEquals(Level.WARN, event.getLevel(), event.getFormattedMessage());
            warnings.add(event.getFormattedMessage());
        }
        return warnings;
    }

    @Test
    void windowSizeUsesDefaultSilentlyWhenMissingOrBlank() {
        for (String input : new String[] {null, "", "   "}) {
            List<String> warnings = captureWarnings(
                () -> assertArrayEquals(DEFAULT_SIZE, DesktopLauncher.windowSize(input)));
            assertEquals(List.of(), warnings, "expected no warning for blank input: '" + input + "'");
        }
    }

    @Test
    void windowSizeParsesValidDimensionsWithinBoundsSilently() {
        List<String> warnings = captureWarnings(() -> {
            assertArrayEquals(new int[] {800, 600}, DesktopLauncher.windowSize("800x600"));
            assertArrayEquals(new int[] {1920, 1080}, DesktopLauncher.windowSize(" 1920X1080 "));
            assertArrayEquals(new int[] {DesktopLauncher.MIN_WINDOW_WIDTH, DesktopLauncher.MIN_WINDOW_HEIGHT},
                DesktopLauncher.windowSize("160x120"));
            assertArrayEquals(new int[] {DesktopLauncher.MAX_WINDOW_WIDTH, DesktopLauncher.MAX_WINDOW_HEIGHT},
                DesktopLauncher.windowSize("7680x4320"));
        });
        assertEquals(List.of(), warnings);
    }

    @Test
    void windowSizeRejectsOutOfBoundsAndMalformedValuesWithWarning() {
        String[] invalid = {
            "159x120", "160x119", "0x0", "-800x600",
            "7681x4320", "7680x4321", "99999x99999",
            "fullscreen", "800", "800x", "x600", "800x600x2"
        };
        for (String input : invalid) {
            List<String> warnings = captureWarnings(
                () -> assertArrayEquals(DEFAULT_SIZE, DesktopLauncher.windowSize(input)));
            assertEquals(List.of("GAMEX_WINDOW is not a size between 160x120 and 7680x4320 like 800x600: '" + input
                + "'; using 1280x720"), warnings);
        }
    }

    @Test
    void versionIsTheProjectVersionTheBuildWroteIntoTheResource() {
        String expected = System.getProperty("gamex.version");
        assertTrue(expected != null && !expected.isEmpty(), "the build passes gamex.version to the tests");
        assertEquals(expected, DesktopLauncher.version());
    }

    @Test
    void versionIsUnknownWhenMissingBlankOrAnUnfilledPlaceholder() {
        for (String input : new String[] {null, "", "  ", "${gamexVersion}", " ${version} "}) {
            assertEquals("unknown", DesktopLauncher.version(input), "'" + input + "'");
        }
        assertEquals("1.2.3", DesktopLauncher.version(" 1.2.3 "));
    }

    @Test
    void logLevelIsReadSilentlyWhenMissingBlankOrValid() {
        String[][] cases = {{null, "INFO"}, {"", "INFO"}, {"  ", "INFO"}, {"debug", "DEBUG"}, {" Warn ", "WARN"}};
        for (String[] pair : cases) {
            List<String> warnings = captureWarnings(() -> assertEquals(pair[1], DesktopLauncher.logLevel(pair[0])));
            assertEquals(List.of(), warnings, "expected no warning for '" + pair[0] + "'");
        }
    }

    @Test
    void logLevelThatIsNotALevelIsInfoWithAWarning() {
        for (String input : new String[] {"garbage", "verbose", "warning"}) {
            List<String> warnings = captureWarnings(() -> assertEquals("INFO", DesktopLauncher.logLevel(input)));
            assertEquals(List.of("GAMEX_LOG_LEVEL is not trace, debug, info, warn or error: '" + input
                + "'; using INFO"), warnings);
        }
    }

    @Test
    void shouldStartFullscreenHonorsSavedSettingUnlessOverriddenByWindowOrScreenshotEnv() {
        assertFalse(DesktopLauncher.shouldStartFullscreen(null, null, null));

        MapKeyValueStore store = new MapKeyValueStore();
        GameSettings settings = new GameSettings();
        settings.setFullscreen(false);
        settings.save(store);
        assertFalse(DesktopLauncher.shouldStartFullscreen(null, null, store));

        settings.setFullscreen(true);
        settings.save(store);
        assertTrue(DesktopLauncher.shouldStartFullscreen(null, null, store));
        assertTrue(DesktopLauncher.shouldStartFullscreen("   ", "", store));

        assertFalse(DesktopLauncher.shouldStartFullscreen(null, "out.png", store));
        assertFalse(DesktopLauncher.shouldStartFullscreen("1600x900", null, store));
    }

    @Test
    void windowIsForcedExactlyWhenGamexWindowIsSet() {
        assertFalse(DesktopLauncher.isWindowForced(null));
        assertFalse(DesktopLauncher.isWindowForced(""));
        assertFalse(DesktopLauncher.isWindowForced("   "));
        assertTrue(DesktopLauncher.isWindowForced("1600x900"));
        assertTrue(DesktopLauncher.isWindowForced("fullscreen"),
            "a malformed size still starts windowed, at the 1280x720 fallback");

        MapKeyValueStore store = new MapKeyValueStore();
        GameSettings settings = new GameSettings();
        settings.setFullscreen(true);
        settings.save(store);
        for (String windowEnv : new String[] {null, "", "   ", "1600x900", "fullscreen"}) {
            assertEquals(!DesktopLauncher.isWindowForced(windowEnv),
                DesktopLauncher.shouldStartFullscreen(windowEnv, null, store),
                "the launcher and the game agree for GAMEX_WINDOW='" + windowEnv + "'");
        }
    }

    @Test
    void packagingIconsMatchTheWindowIconArt() throws IOException {
        BufferedImage gamex128 = ImageIO.read(resolveDesktopFile("src/main/resources/gamex128.png").toFile());
        BufferedImage logoPng = ImageIO.read(resolveDesktopFile("icons/logo.png").toFile());

        assertEquals(128, gamex128.getWidth());
        assertEquals(128, gamex128.getHeight());
        assertEquals(256, logoPng.getWidth());
        assertEquals(256, logoPng.getHeight());
        for (int y = 0; y < 256; y++) {
            for (int x = 0; x < 256; x++) {
                assertEquals(gamex128.getRGB(x / 2, y / 2), logoPng.getRGB(x, y),
                    "pixel mismatch at (" + x + ", " + y + ")");
            }
        }

        byte[] icnsBytes = Files.readAllBytes(resolveDesktopFile("icons/logo.icns"));
        ByteBuffer buffer = ByteBuffer.wrap(icnsBytes).order(ByteOrder.BIG_ENDIAN);
        byte[] magic = new byte[4];
        buffer.get(magic);
        assertEquals("icns", new String(magic, StandardCharsets.US_ASCII));
        assertEquals(icnsBytes.length, buffer.getInt());

        Map<String, Integer> expectedSizes = new LinkedHashMap<>();
        expectedSizes.put("icp4", 16);
        expectedSizes.put("icp5", 32);
        expectedSizes.put("icp6", 64);
        expectedSizes.put("ic07", 128);
        expectedSizes.put("ic08", 256);
        expectedSizes.put("ic09", 512);

        Map<String, BufferedImage> decodedChunks = new LinkedHashMap<>();
        while (buffer.hasRemaining()) {
            byte[] tagBytes = new byte[4];
            buffer.get(tagBytes);
            String tag = new String(tagBytes, StandardCharsets.US_ASCII);
            int chunkLength = buffer.getInt();
            assertTrue(chunkLength > 8 && chunkLength - 8 <= buffer.remaining(),
                "invalid chunk length for " + tag + ": " + chunkLength);
            byte[] png = new byte[chunkLength - 8];
            buffer.get(png);
            BufferedImage image = ImageIO.read(new ByteArrayInputStream(png));
            decodedChunks.put(tag, image);
        }

        assertEquals(expectedSizes.keySet(), decodedChunks.keySet());
        for (Map.Entry<String, Integer> expected : expectedSizes.entrySet()) {
            BufferedImage image = decodedChunks.get(expected.getKey());
            assertEquals(expected.getValue().intValue(), image.getWidth(), expected.getKey() + " width");
            assertEquals(expected.getValue().intValue(), image.getHeight(), expected.getKey() + " height");
        }

        BufferedImage ic07 = decodedChunks.get("ic07");
        for (int y = 0; y < 128; y++) {
            for (int x = 0; x < 128; x++) {
                assertEquals(gamex128.getRGB(x, y), ic07.getRGB(x, y),
                    "ic07 pixel mismatch at (" + x + ", " + y + ")");
            }
        }
    }
}

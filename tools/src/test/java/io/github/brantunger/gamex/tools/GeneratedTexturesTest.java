package io.github.brantunger.gamex.tools;

import com.badlogic.gdx.files.FileHandle;
import com.badlogic.gdx.graphics.Pixmap;
import com.badlogic.gdx.utils.GdxNativesLoader;
import io.github.brantunger.gamex.render.ProceduralTextures;
import io.github.brantunger.gamex.render.WallTextures;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.opentest4j.AssertionFailedError;

import javax.imageio.ImageIO;
import java.awt.image.BufferedImage;
import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.ByteBuffer;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.TreeSet;
import java.util.function.Supplier;
import java.util.stream.Stream;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class GeneratedTexturesTest {

    @Test
    void shippedTexturesMatchTheGeneratorPixelForPixel(@TempDir Path generatedDir) throws IOException {
        Path texturesDir = resolveTexturesDir();
        GenerateAllTextures.generateAll(generatedDir);

        assertMatchesGenerators(texturesDir, "Shipped textures must match generator registry");
        assertMatchesGenerators(generatedDir, "Generated output must match generator registry");
    }

    @Test
    void aPngWithOneChangedPixelIsReportedAsOutOfDate(@TempDir Path alteredDir) throws IOException {
        GenerateAllTextures.generateAll(alteredDir);
        TexturePainter.Image stone = TexturePainter.paintStone();
        stone.rgba[5 * stone.width + 7] ^= 0x00000100;
        Files.write(alteredDir.resolve("stone.png"), GenerateAllTextures.encodePng(stone));

        AssertionFailedError error = assertThrows(AssertionFailedError.class,
            () -> assertMatchesGenerators(alteredDir, "names"));

        assertTrue(error.getMessage().startsWith("stone.png is out of date; re-run ./gradlew tools:generateTextures"),
            error.getMessage());
    }

    @Test
    void anUnknownOrMissingPngIsReported(@TempDir Path dir) throws IOException {
        GenerateAllTextures.generateAll(dir);
        Files.copy(dir.resolve("stone.png"), dir.resolve("marble.png"));
        assertThrows(AssertionFailedError.class, () -> assertMatchesGenerators(dir, "names"));

        Files.delete(dir.resolve("marble.png"));
        assertMatchesGenerators(dir, "names");
        Files.delete(dir.resolve("orb.png"));
        assertThrows(AssertionFailedError.class, () -> assertMatchesGenerators(dir, "names"));
    }

    /** Whatever a painter draws, it fills a square whose side is a power of two, as the tiling of walls needs. */
    @Test
    void everyPainterFillsASquareWithAPowerOfTwoSide() {
        for (Map.Entry<String, Supplier<TexturePainter.Image>> entry : GenerateAllTextures.generators().entrySet()) {
            TexturePainter.Image image = entry.getValue().get();
            assertEquals(image.width, image.height, entry.getKey());
            assertTrue(image.width >= 2 && Integer.bitCount(image.width) == 1, entry.getKey() + ": " + image.width);
            assertEquals(image.width * image.height, image.rgba.length, entry.getKey());
            boolean painted = false;
            for (int pixel : image.rgba) painted |= pixel != image.rgba[0];
            assertTrue(painted, entry.getKey() + " is one flat colour");
        }
    }

    @Test
    void paintingTwiceGivesTheSamePixels() {
        for (Map.Entry<String, Supplier<TexturePainter.Image>> entry : GenerateAllTextures.generators().entrySet()) {
            assertArrayEquals(entry.getValue().get().rgba, entry.getValue().get().rgba, entry.getKey());
        }
    }

    /** The game decodes the files with the PNG reader of libGDX, not with ImageIO. No GL context is needed. */
    @Test
    void libgdxDecodesEachShippedTextureToThePaintedRgba8888Pixels() {
        GdxNativesLoader.load();
        Path texturesDir = resolveTexturesDir();
        for (Map.Entry<String, Supplier<TexturePainter.Image>> entry : GenerateAllTextures.generators().entrySet()) {
            TexturePainter.Image painted = entry.getValue().get();
            Pixmap pixmap = new Pixmap(new FileHandle(texturesDir.resolve(entry.getKey()).toFile()));
            try {
                assertEquals(Pixmap.Format.RGBA8888, pixmap.getFormat(), entry.getKey());
                assertEquals(painted.width, pixmap.getWidth(), entry.getKey());
                assertEquals(painted.height, pixmap.getHeight(), entry.getKey());
                ByteBuffer pixels = pixmap.getPixels().duplicate();
                byte[] decoded = new byte[pixels.remaining()];
                pixels.get(decoded);
                assertArrayEquals(rgbaBytes(painted.rgba), decoded,
                    entry.getKey() + " is out of date; re-run ./gradlew tools:generateTextures");
            } finally {
                pixmap.dispose();
            }
        }
    }

    /** A gamma, sRGB, chromaticity or profile chunk would let a decoder change the colours. */
    @Test
    void shippedPngsAreEightBitRgbaWithNoColourChunks() throws IOException {
        Path texturesDir = resolveTexturesDir();
        for (String name : GenerateAllTextures.generators().keySet()) {
            byte[] png = Files.readAllBytes(texturesDir.resolve(name));
            Set<String> chunks = new TreeSet<>();
            ByteBuffer buffer = ByteBuffer.wrap(png);
            buffer.position(8);
            while (buffer.remaining() >= 12) {
                int length = buffer.getInt();
                byte[] type = new byte[4];
                buffer.get(type);
                chunks.add(new String(type, StandardCharsets.US_ASCII));
                buffer.position(buffer.position() + length + 4);
            }
            assertEquals(new TreeSet<>(Arrays.asList("IDAT", "IEND", "IHDR")), chunks, name);
            assertEquals(8, png[24], name + " bit depth");
            assertEquals(6, png[25], name + " colour type (6 is RGBA)");
            assertEquals(0, png[28], name + " interlace");
        }
    }

    @Test
    void theGameLoadsExactlyTheGeneratedTextures() {
        List<String> loaded = new ArrayList<>(WallTextures.load().files());
        loaded.addAll(Arrays.asList(ProceduralTextures.FLOOR_FILE, ProceduralTextures.CEILING_FILE,
            ProceduralTextures.MUZZLE_FLASH_FILE, ProceduralTextures.SCORCH_FILE, ProceduralTextures.SPARK_FILE,
            ProceduralTextures.ORB_FILE, ProceduralTextures.EXPLOSION_FILE, ProceduralTextures.EXIT_PAD_FILE));

        assertEquals(new TreeSet<>(GenerateAllTextures.generators().keySet()), new TreeSet<>(loaded));
    }

    private static void assertMatchesGenerators(Path dir, String namesMessage) throws IOException {
        Set<String> expectedNames = new TreeSet<>(GenerateAllTextures.generators().keySet());
        assertEquals(expectedNames, listPngNames(dir), namesMessage);
        for (Map.Entry<String, Supplier<TexturePainter.Image>> entry : GenerateAllTextures.generators().entrySet()) {
            String name = entry.getKey();
            TexturePainter.Image painted = entry.getValue().get();
            BufferedImage decoded = ImageIO.read(new ByteArrayInputStream(Files.readAllBytes(dir.resolve(name))));
            assertNotNull(decoded, name + " is not a PNG image");
            String outOfDate = name + " is out of date; re-run ./gradlew tools:generateTextures";
            assertEquals(painted.width + "x" + painted.height, decoded.getWidth() + "x" + decoded.getHeight(),
                outOfDate);
            int[] rgba = new int[painted.rgba.length];
            for (int y = 0; y < painted.height; y++) {
                for (int x = 0; x < painted.width; x++) {
                    int argb = decoded.getRGB(x, y);
                    rgba[y * painted.width + x] = (argb << 8) | (argb >>> 24);
                }
            }
            assertArrayEquals(painted.rgba, rgba, outOfDate);
        }
    }

    private static byte[] rgbaBytes(int[] rgba) {
        ByteBuffer buffer = ByteBuffer.allocate(rgba.length * 4);
        for (int pixel : rgba) {
            buffer.putInt(pixel);
        }
        return buffer.array();
    }

    private static Set<String> listPngNames(Path dir) throws IOException {
        Set<String> names = new TreeSet<>();
        try (Stream<Path> entries = Files.list(dir)) {
            entries.filter(Files::isRegularFile)
                .map(path -> path.getFileName().toString())
                .filter(name -> name.endsWith(".png"))
                .forEach(names::add);
        }
        return names;
    }

    private static Path resolveTexturesDir() {
        String configured = System.getProperty("gamex.assets");
        if (configured != null && !configured.trim().isEmpty()) {
            Path candidate = Paths.get(configured).toAbsolutePath().normalize()
                .resolve(ProceduralTextures.TEXTURES_DIR);
            if (Files.isDirectory(candidate)) {
                return candidate;
            }
        }
        Path current = Paths.get("").toAbsolutePath().normalize();
        while (current != null) {
            Path candidate = current.resolve("assets").resolve(ProceduralTextures.TEXTURES_DIR);
            if (Files.isDirectory(candidate)) {
                return candidate;
            }
            current = current.getParent();
        }
        try {
            throw new IOException("Could not find assets/" + ProceduralTextures.TEXTURES_DIR + " directory");
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
    }
}

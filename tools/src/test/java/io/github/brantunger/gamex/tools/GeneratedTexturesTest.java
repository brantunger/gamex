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
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.LinkedHashMap;
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

    private static final String SHA_BRICK = "142d64fa39ffe341f22927b59ef2caf5609562a61f442f88dd369a190e75ba7b";
    private static final String SHA_PANEL = "5c278f3938c17426b6b5d650d5355b7cb73a0df7bd15270214faa7220b2d600b";
    private static final String SHA_STONE = "db883688e3db9c659261f9bf85a8c92eed5f2f95c7c9f71e4d10cb13eb2eda0e";
    private static final String SHA_FLOOR = "d742e49149f27f3ec6511be6523fbf010551e581e7002e973a12d0b406d1d6f2";
    private static final String SHA_CEILING = "e172cabeea083ac2e2a510dc07339100cf2dcc19172b0d1ecc20adec75fe81bd";
    private static final String SHA_MUZZLE_FLASH = "cb076455ec1acefabe9f49413c60c00a67aab430d39df1ae8d884a524f31dbbb";
    private static final String SHA_SCORCH = "3d63bfd2d8a7e75c2b472f4e237216f66d0ce174c0775f8d978ef4bc3aacd76a";
    private static final String SHA_SPARK = "91f8057e7b90eb738327e6e67401daedd3cfdf5eb0931003086d7a24137d70e1";
    private static final String SHA_ORB = "96e76c0a4cd82b4d0ab562f048a0e5e7d5392ea12e3efc98cf6c6e57355e8ede";
    private static final String SHA_EXPLOSION = "a4baea0475335fc8b146db2eab1a06c3e3996fa2cb86b169ce24b1ac83104f0a";
    private static final String SHA_EXIT_PAD = "f8a42f15a13c008071c409feff4a9a925e1c2b6cac7d1d3bb74da59947e59afa";

    /**
     * The size of each texture and the SHA-256 of its RGBA8888 pixel bytes (rows from the top, four bytes a pixel)
     * as {@code ProceduralTextures} painted it in code at commit 10cd4a4, before the painting moved to this module.
     */
    private static final Map<String, String> PIXELS_PAINTED_IN_CODE = new LinkedHashMap<>();

    static {
        PIXELS_PAINTED_IN_CODE.put("brick.png", "32x32 " + SHA_BRICK);
        PIXELS_PAINTED_IN_CODE.put("panel.png", "32x32 " + SHA_PANEL);
        PIXELS_PAINTED_IN_CODE.put("stone.png", "32x32 " + SHA_STONE);
        PIXELS_PAINTED_IN_CODE.put("floor.png", "32x32 " + SHA_FLOOR);
        PIXELS_PAINTED_IN_CODE.put("ceiling.png", "32x32 " + SHA_CEILING);
        PIXELS_PAINTED_IN_CODE.put("muzzle-flash.png", "32x32 " + SHA_MUZZLE_FLASH);
        PIXELS_PAINTED_IN_CODE.put("scorch.png", "16x16 " + SHA_SCORCH);
        PIXELS_PAINTED_IN_CODE.put("spark.png", "16x16 " + SHA_SPARK);
        PIXELS_PAINTED_IN_CODE.put("orb.png", "64x64 " + SHA_ORB);
        PIXELS_PAINTED_IN_CODE.put("explosion.png", "64x64 " + SHA_EXPLOSION);
        PIXELS_PAINTED_IN_CODE.put("exit-pad.png", "32x32 " + SHA_EXIT_PAD);
    }

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

    @Test
    void thePaintedPixelsAreTheOnesTheGamePaintedInCodeBeforeTheMove() {
        assertEquals(PIXELS_PAINTED_IN_CODE.keySet(), GenerateAllTextures.generators().keySet());
        for (Map.Entry<String, Supplier<TexturePainter.Image>> entry : GenerateAllTextures.generators().entrySet()) {
            TexturePainter.Image image = entry.getValue().get();
            assertEquals(PIXELS_PAINTED_IN_CODE.get(entry.getKey()),
                image.width + "x" + image.height + " " + sha256(rgbaBytes(image.rgba)),
                entry.getKey() + " no longer has the pixels ProceduralTextures painted at 10cd4a4");
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

    private static String sha256(byte[] bytes) {
        try {
            StringBuilder hex = new StringBuilder();
            for (byte b : MessageDigest.getInstance("SHA-256").digest(bytes)) {
                hex.append(String.format("%02x", b));
            }
            return hex.toString();
        } catch (NoSuchAlgorithmException e) {
            throw new IllegalStateException(e);
        }
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

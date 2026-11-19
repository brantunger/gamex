package io.github.brantunger.gamex.tools;

import io.github.brantunger.gamex.render.ProceduralTextures;

import javax.imageio.ImageIO;
import java.awt.image.BufferedImage;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.function.Supplier;

/**
 * Generates every shipped {@code .png} texture in {@code assets/textures/} deterministically via
 * {@link TexturePainter}.
 */
public final class GenerateAllTextures {

    private static final Map<String, Supplier<TexturePainter.Image>> GENERATORS;

    static {
        Map<String, Supplier<TexturePainter.Image>> map = new LinkedHashMap<>();
        // The wall textures; assets/data/walls.txt gives each wall character one of them.
        map.put("brick.png", TexturePainter::paintBrick);
        map.put("panel.png", TexturePainter::paintPanel);
        map.put("stone.png", TexturePainter::paintStone);
        map.put(ProceduralTextures.FLOOR_FILE, TexturePainter::paintFloor);
        map.put(ProceduralTextures.CEILING_FILE, TexturePainter::paintCeiling);
        map.put(ProceduralTextures.MUZZLE_FLASH_FILE, TexturePainter::paintMuzzleFlash);
        map.put(ProceduralTextures.SCORCH_FILE, TexturePainter::paintScorch);
        map.put(ProceduralTextures.SPARK_FILE, TexturePainter::paintSpark);
        map.put(ProceduralTextures.ORB_FILE, TexturePainter::paintOrb);
        map.put(ProceduralTextures.EXPLOSION_FILE, TexturePainter::paintExplosion);
        map.put(ProceduralTextures.EXIT_PAD_FILE, TexturePainter::paintExitPad);
        GENERATORS = Collections.unmodifiableMap(map);
    }

    private GenerateAllTextures() {}

    /** @return ordered map from {@code .png} file name to its painter. */
    static Map<String, Supplier<TexturePainter.Image>> generators() {
        return GENERATORS;
    }

    /** Regenerates every {@code .png} file into {@code outputDir}. */
    public static void generateAll(Path outputDir) throws IOException {
        Files.createDirectories(outputDir);
        for (Map.Entry<String, Supplier<TexturePainter.Image>> entry : GENERATORS.entrySet()) {
            TexturePainter.Image image = entry.getValue().get();
            byte[] png = encodePng(image);
            Path target = outputDir.resolve(entry.getKey());
            Files.write(target, png);
            System.out.println("Wrote " + target + ": " + png.length + " bytes, " + image.width + "x" + image.height
                + " pixels");
        }
    }

    /**
     * Encodes an image as an 8-bit RGBA {@code .png} with no colour profile or gamma chunk, so a decoder returns
     * the painted pixels unchanged.
     */
    static byte[] encodePng(TexturePainter.Image image) throws IOException {
        BufferedImage buffered = new BufferedImage(image.width, image.height, BufferedImage.TYPE_INT_ARGB);
        for (int y = 0; y < image.height; y++) {
            for (int x = 0; x < image.width; x++) {
                int rgba = image.rgba[y * image.width + x];
                buffered.setRGB(x, y, (rgba >>> 8) | (rgba << 24));
            }
        }
        ByteArrayOutputStream out = new ByteArrayOutputStream();
        if (!ImageIO.write(buffered, "png", out)) {
            throw new IOException("This Java runtime has no PNG writer");
        }
        return out.toByteArray();
    }

    public static void main(String[] args) throws IOException {
        if (args.length != 1) {
            System.err.println("usage: ./gradlew tools:generateTextures  (or pass <output-dir>)");
            System.exit(2);
        }
        generateAll(Paths.get(args[0]));
    }
}

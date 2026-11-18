package io.github.brantunger.gamex.tools;

import io.github.brantunger.gamex.audio.SoundEvent;
import io.github.brantunger.gamex.audio.SoundManager;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.function.Supplier;

/**
 * Generates every shipped {@code .wav} sound effect in {@code assets/sounds/} deterministically via
 * {@link SoundSynth}.
 */
public final class GenerateAllSounds {

    private static final Map<String, Supplier<byte[]>> GENERATORS;

    static {
        Map<String, Supplier<byte[]>> map = new LinkedHashMap<>();
        for (SoundEvent.Type type : SoundEvent.Type.values()) {
            map.put(SoundManager.fileName(type), () -> SoundSynth.synthesize(type));
        }
        map.put(SoundManager.CHARGE_HUM_FILE, SoundSynth::synthesizeChargeHum);
        GENERATORS = Collections.unmodifiableMap(map);
    }

    private GenerateAllSounds() {}

    /** @return ordered map from {@code .wav} file name to its generator. */
    public static Map<String, Supplier<byte[]>> generators() {
        return GENERATORS;
    }

    /** Regenerates every {@code .wav} file into {@code outputDir}. */
    public static void generateAll(Path outputDir) throws IOException {
        Files.createDirectories(outputDir);
        for (Map.Entry<String, Supplier<byte[]>> entry : GENERATORS.entrySet()) {
            byte[] wav = entry.getValue().get();
            Path target = outputDir.resolve(entry.getKey());
            Files.write(target, wav);
            int sampleCount = (wav.length - 44) / 2;
            System.out.println("Wrote " + target + ": " + wav.length + " bytes, " + sampleCount + " samples");
        }
    }

    public static void main(String[] args) throws IOException {
        if (args.length != 1) {
            System.err.println("usage: ./gradlew tools:generateSounds  (or pass <output-dir>)");
            System.exit(2);
        }
        generateAll(Paths.get(args[0]));
    }
}

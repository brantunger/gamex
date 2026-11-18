package io.github.brantunger.gamex.tools;

import io.github.brantunger.gamex.audio.SoundManager;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.Set;
import java.util.TreeSet;
import java.util.stream.Stream;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;

class GeneratedSoundsTest {

    @Test
    void generatedSoundsMatchShippedAssets(@TempDir Path generatedDir) throws IOException {
        Path soundsDir = resolveSoundsDir();
        GenerateAllSounds.generateAll(generatedDir);

        Set<String> expectedNames = new TreeSet<>(GenerateAllSounds.generators().keySet());
        assertEquals(expectedNames, listWavNames(soundsDir), "Shipped sounds must match generator registry");
        assertEquals(expectedNames, listWavNames(generatedDir), "Generated output must match generator registry");

        for (String name : expectedNames) {
            Path shipped = soundsDir.resolve(name);
            Path fresh = generatedDir.resolve(name);
            assertArrayEquals(Files.readAllBytes(fresh), Files.readAllBytes(shipped),
                name + " is out of date; re-run ./gradlew tools:generateSounds");
        }
    }

    private static Set<String> listWavNames(Path dir) throws IOException {
        Set<String> names = new TreeSet<>();
        try (Stream<Path> entries = Files.list(dir)) {
            entries.filter(Files::isRegularFile)
                .map(path -> path.getFileName().toString())
                .filter(name -> name.endsWith(".wav"))
                .forEach(names::add);
        }
        return names;
    }

    private static Path resolveSoundsDir() {
        String configured = System.getProperty("gamex.assets");
        if (configured != null && !configured.trim().isEmpty()) {
            Path candidate = Paths.get(configured).toAbsolutePath().normalize().resolve(SoundManager.SOUNDS_DIR);
            if (Files.isDirectory(candidate)) {
                return candidate;
            }
        }
        Path current = Paths.get("").toAbsolutePath().normalize();
        while (current != null) {
            Path candidate = current.resolve("assets").resolve(SoundManager.SOUNDS_DIR);
            if (Files.isDirectory(candidate)) {
                return candidate;
            }
            current = current.getParent();
        }
        try {
            throw new IOException("Could not find assets/" + SoundManager.SOUNDS_DIR + " directory");
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
    }
}

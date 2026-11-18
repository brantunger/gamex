package io.github.brantunger.gamex.audio;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import io.github.brantunger.gamex.GameSimulation;
import io.github.brantunger.gamex.hud.MenuState;
import io.github.brantunger.gamex.player.PlayerInput;
import io.github.brantunger.gamex.settings.GameSettings;
import io.github.brantunger.gamex.settings.MapKeyValueStore;
import io.github.brantunger.gamex.settings.SaveData;
import io.github.brantunger.gamex.world.Levels;
import org.junit.jupiter.api.Test;

import javax.sound.sampled.AudioFileFormat;
import javax.sound.sampled.AudioFormat;
import javax.sound.sampled.AudioSystem;
import javax.sound.sampled.UnsupportedAudioFileException;
import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.List;
import java.util.Set;
import java.util.TreeSet;
import java.util.stream.Stream;

/** Checks shipped {@code assets/sounds/*.wav} files and {@link SoundManager} spatial and muted behavior. */
class SoundManagerTest {

    private static final int EXPECTED_SAMPLE_RATE = 44_100;

    private static Path resolveAssetsDir() {
        List<Path> candidates = new ArrayList<>();
        String assets = System.getProperty("gamex.assets");
        if (assets != null) candidates.add(Paths.get(assets));
        candidates.add(Paths.get("..", "assets"));
        candidates.add(Paths.get("assets"));
        for (Path candidate : candidates) {
            if (Files.isDirectory(candidate.resolve(SoundManager.SOUNDS_DIR))) {
                return candidate;
            }
        }
        List<Path> tried = new ArrayList<>();
        for (Path candidate : candidates) tried.add(candidate.toAbsolutePath().normalize());
        throw new IllegalStateException("The sounds folder was not found. Set the system property gamex.assets to "
            + "the assets folder, or run from the root project or the core project. Tried: " + tried);
    }

    private static byte[] loadWav(Path assetsDir, String assetPath) {
        Path file = assetsDir.resolve(assetPath);
        assertTrue(Files.isRegularFile(file), "Missing sound asset: " + file);
        try {
            AudioFileFormat format = AudioSystem.getAudioFileFormat(file.toFile());
            assertEquals(AudioFileFormat.Type.WAVE, format.getType(), assetPath + " audio file type");
            AudioFormat audioFormat = format.getFormat();
            assertEquals(AudioFormat.Encoding.PCM_SIGNED, audioFormat.getEncoding(), assetPath + " encoding");
            assertEquals(1, audioFormat.getChannels(), assetPath + " mono channel");
            assertEquals(16, audioFormat.getSampleSizeInBits(), assetPath + " sample size");
            assertFalse(audioFormat.isBigEndian(), assetPath + " little-endian PCM");
            return Files.readAllBytes(file);
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        } catch (UnsupportedAudioFileException e) {
            throw new AssertionError("Unsupported WAV file " + assetPath + ": " + e.getMessage(), e);
        }
    }

    private static int readIntLE(byte[] data, int at) {
        return (data[at] & 0xFF)
            | ((data[at + 1] & 0xFF) << 8)
            | ((data[at + 2] & 0xFF) << 16)
            | ((data[at + 3] & 0xFF) << 24);
    }

    private static int readShortLE(byte[] data, int at) {
        return (data[at] & 0xFF) | ((data[at + 1] & 0xFF) << 8);
    }

    private static short[] decodePcmWav(byte[] wav, String label) {
        assertTrue(wav.length > 44, label + " too short");
        assertEquals("RIFF", new String(wav, 0, 4, StandardCharsets.US_ASCII), label);
        assertEquals(wav.length - 8, readIntLE(wav, 4), label + " RIFF size");
        assertEquals("WAVEfmt ", new String(wav, 8, 8, StandardCharsets.US_ASCII), label);
        assertEquals(16, readIntLE(wav, 16), label + " fmt chunk size");
        assertEquals(1, readShortLE(wav, 20), label + " PCM format");
        assertEquals(1, readShortLE(wav, 22), label + " mono channels");
        assertEquals(EXPECTED_SAMPLE_RATE, readIntLE(wav, 24), label + " sample rate");
        assertEquals(EXPECTED_SAMPLE_RATE * 2, readIntLE(wav, 28), label + " byte rate");
        assertEquals(2, readShortLE(wav, 32), label + " block align");
        assertEquals(16, readShortLE(wav, 34), label + " bits per sample");
        assertEquals("data", new String(wav, 36, 4, StandardCharsets.US_ASCII), label);

        int dataBytes = readIntLE(wav, 40);
        assertEquals(wav.length - 44, dataBytes, label + " data chunk size");
        assertTrue(dataBytes > 0 && dataBytes % 2 == 0, label + " non-empty even PCM");

        short[] samples = new short[dataBytes / 2];
        int peak = 0;
        for (int i = 0; i < samples.length; i++) {
            int lo = wav[44 + i * 2] & 0xFF;
            int hi = wav[44 + i * 2 + 1];
            samples[i] = (short) ((hi << 8) | lo);
            peak = Math.max(peak, Math.abs(samples[i]));
        }
        assertTrue(peak > 1500, label + " should be non-silent, peak=" + peak);
        return samples;
    }

    @Test
    void everyEventTypeAndChargeHumHasValidNonSilentShippedMonoPcmWavAsset() throws IOException {
        Path assetsDir = resolveAssetsDir();
        Set<String> expectedFiles = new TreeSet<>();
        for (SoundEvent.Type type : SoundEvent.Type.values()) {
            String path = SoundManager.assetPath(type);
            assertTrue(path.startsWith(SoundManager.SOUNDS_DIR + "/") && path.endsWith(".wav"), path);
            decodePcmWav(loadWav(assetsDir, path), path);
            expectedFiles.add(SoundManager.fileName(type));
        }
        decodePcmWav(loadWav(assetsDir, SoundManager.CHARGE_HUM_PATH), SoundManager.CHARGE_HUM_PATH);
        expectedFiles.add(SoundManager.CHARGE_HUM_FILE);
        assertEquals(SoundEvent.Type.values().length + 1, expectedFiles.size(), "distinct sound file per cue");

        Set<String> actualFiles = new TreeSet<>();
        try (Stream<Path> entries = Files.list(assetsDir.resolve(SoundManager.SOUNDS_DIR))) {
            entries.filter(Files::isRegularFile)
                .map(path -> path.getFileName().toString())
                .forEach(actualFiles::add);
        }
        assertEquals(expectedFiles, actualFiles, "assets/sounds contents must match SoundManager cues");
    }

    @Test
    void chargeHumAssetLoopsContinuouslyBetweenEndAndStartSample() {
        Path assetsDir = resolveAssetsDir();
        short[] samples = decodePcmWav(loadWav(assetsDir, SoundManager.CHARGE_HUM_PATH), SoundManager.CHARGE_HUM_PATH);
        assertEquals(EXPECTED_SAMPLE_RATE / 2, samples.length);
        int wrapDelta = Math.abs(samples[0] - samples[samples.length - 1]);
        assertTrue(wrapDelta < 1500, "loop seam delta should be smooth without click: " + wrapDelta);
    }

    @Test
    void mutedSoundManagerDrainsMenuAndSimulationEventsWithoutAudioBackend() {
        GameSettings settings = new GameSettings();
        MenuState menu = new MenuState(settings, new SaveData(new MapKeyValueStore()), MenuState.Screen.TITLE);
        menu.moveSelection(1);
        GameSimulation sim = new GameSimulation(Levels.first());
        PlayerInput input = new PlayerInput();
        input.fire = true;
        sim.update(1f / 60f, input);

        SoundManager manager = new SoundManager(true);
        manager.update(sim, menu, settings);
        assertTrue(sim.drainSoundEvents().isEmpty(), "simulation events drained");
        List<SoundEvent> remainingMenu = new ArrayList<>();
        menu.drainSoundEvents(remainingMenu);
        assertTrue(remainingMenu.isEmpty(), "menu events drained");
        manager.stopLoops();
        manager.dispose();
    }

    @Test
    void spatialAttenuationAndPanFollowListenerOrientationAndDistance() {
        // Facing north (yaw = 0): right is +x, ahead is -z.
        float[] ahead = SoundManager.computeSpatial(0f, 0.5f, 0f, 0f, 0f, 0.5f, -5f);
        assertEquals(0f, ahead[1], 1e-4f);
        assertTrue(ahead[0] > 0f && ahead[0] < 1f);

        float[] right = SoundManager.computeSpatial(0f, 0.5f, 0f, 0f, 5f, 0.5f, 0f);
        assertTrue(right[1] > 0.7f, "pan right: " + right[1]);

        float[] left = SoundManager.computeSpatial(0f, 0.5f, 0f, 0f, -5f, 0.5f, 0f);
        assertTrue(left[1] < -0.7f, "pan left: " + left[1]);

        // Facing east (yaw = 90): south (+z) is right, north (-z) is left.
        float[] southRight = SoundManager.computeSpatial(0f, 0.5f, 0f, 90f, 0f, 0.5f, 5f);
        assertTrue(southRight[1] > 0.7f, "facing east, south pans right: " + southRight[1]);
        float[] northLeft = SoundManager.computeSpatial(0f, 0.5f, 0f, 90f, 0f, 0.5f, -5f);
        assertTrue(northLeft[1] < -0.7f, "facing east, north pans left: " + northLeft[1]);

        // Distance rolloff decreases monotonically from close range out to silence at >= 28u.
        float close = SoundManager.computeSpatial(0f, 0.5f, 0f, 0f, 0f, 0.5f, -1f)[0];
        float mid = SoundManager.computeSpatial(0f, 0.5f, 0f, 0f, 0f, 0.5f, -6f)[0];
        float far = SoundManager.computeSpatial(0f, 0.5f, 0f, 0f, 0f, 0.5f, -15f)[0];
        float silent = SoundManager.computeSpatial(0f, 0.5f, 0f, 0f, 0f, 0.5f, -30f)[0];
        assertEquals(1.0f, close, 1e-5f);
        assertTrue(close > mid && mid > far && far > silent);
        assertEquals(0f, silent, 1e-5f);
    }
}

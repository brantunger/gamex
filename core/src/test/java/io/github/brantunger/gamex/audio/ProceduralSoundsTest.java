package io.github.brantunger.gamex.audio;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.Test;

import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;

class ProceduralSoundsTest {

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
        assertEquals(ProceduralSounds.SAMPLE_RATE, readIntLE(wav, 24), label + " sample rate");
        assertEquals(ProceduralSounds.SAMPLE_RATE * 2, readIntLE(wav, 28), label + " byte rate");
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
    void everyEventTypeAndChargeHumSynthesizesValidNonSilentMonoPcmWav() {
        List<String> checked = new ArrayList<>();
        for (SoundEvent.Type type : SoundEvent.Type.values()) {
            decodePcmWav(ProceduralSounds.synthesize(type), type.name());
            checked.add(type.name());
        }
        assertEquals(SoundEvent.Type.values().length, checked.size());
        decodePcmWav(ProceduralSounds.synthesizeChargeHum(), "CHARGE_HUM");
    }

    @Test
    void chargeHumLoopWrapsContinuouslyBetweenEndAndStartSample() {
        short[] samples = decodePcmWav(ProceduralSounds.synthesizeChargeHum(), "CHARGE_HUM");
        assertEquals(ProceduralSounds.SAMPLE_RATE / 2, samples.length);
        int wrapDelta = Math.abs(samples[0] - samples[samples.length - 1]);
        assertTrue(wrapDelta < 1500, "loop seam delta should be smooth without click: " + wrapDelta);
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

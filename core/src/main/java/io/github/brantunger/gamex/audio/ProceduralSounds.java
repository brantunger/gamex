package io.github.brantunger.gamex.audio;

/**
 * Pure-Java procedural 16-bit mono PCM synthesizer producing industrial sci-fi WAV byte buffers in memory so the
 * game needs no external audio files.
 */
public final class ProceduralSounds {

    /** Output PCM sample rate in Hz. */
    public static final int SAMPLE_RATE = 44_100;

    private static final double TWO_PI = 2.0 * Math.PI;
    private static final float CHARGE_HUM_SECONDS = 0.50f;

    private ProceduralSounds() {}

    /**
     * Synthesizes a complete RIFF/WAVE 16-bit mono buffer for the requested one-shot sound type.
     * @param type sound cue to synthesize
     * @return encoded 16-bit mono WAV bytes
     */
    public static byte[] synthesize(SoundEvent.Type type) {
        double[] samples;
        double peakScale;
        switch (type) {
            case PISTOL_SHOT:
                samples = renderPistolShot();
                peakScale = 26_000.0;
                break;
            case WALL_IMPACT:
                samples = renderWallImpact();
                peakScale = 18_000.0;
                break;
            case BLAST_RELEASE:
                samples = renderBlastRelease();
                peakScale = 25_000.0;
                break;
            case EXPLOSION:
                samples = renderExplosion();
                peakScale = 29_000.0;
                break;
            case TARGET_HIT:
                samples = renderTargetHit();
                peakScale = 20_000.0;
                break;
            case TARGET_DESTROYED:
                samples = renderTargetDestroyed();
                peakScale = 25_000.0;
                break;
            case ENEMY_HIT:
                samples = renderEnemyHit();
                peakScale = 21_000.0;
                break;
            case ENEMY_DESTROYED:
                samples = renderEnemyDestroyed();
                peakScale = 26_000.0;
                break;
            case ENEMY_SHOOT:
                samples = renderEnemyShoot();
                peakScale = 22_000.0;
                break;
            case DRONE_LUNGE:
                samples = renderDroneLunge();
                peakScale = 23_000.0;
                break;
            case PLAYER_DASH:
                samples = renderPlayerDash();
                peakScale = 22_000.0;
                break;
            case PLAYER_HURT:
                samples = renderPlayerHurt();
                peakScale = 25_000.0;
                break;
            case PLAYER_DEATH:
                samples = renderPlayerDeath();
                peakScale = 26_000.0;
                break;
            case PICKUP_HEALTH:
                samples = renderToneSteps(0.18, new double[] {0.0, 0.065}, new double[] {0.085, 0.18},
                    new double[] {523.25, 783.99}, 0.25);
                peakScale = 18_000.0;
                break;
            case PICKUP_ARMOR:
                samples = renderToneSteps(0.18, new double[] {0.0, 0.065}, new double[] {0.085, 0.18},
                    new double[] {329.63, 493.88}, 0.45);
                peakScale = 19_000.0;
                break;
            case PICKUP_ENERGY:
                samples = renderToneSteps(0.20, new double[] {0.0, 0.055, 0.11}, new double[] {0.065, 0.13, 0.20},
                    new double[] {440.0, 660.0, 880.0}, 0.30);
                peakScale = 18_000.0;
                break;
            case LOW_ENERGY:
                samples = renderLowEnergy();
                peakScale = 17_000.0;
                break;
            case MENU_MOVE:
                samples = renderMenuMove();
                peakScale = 11_000.0;
                break;
            case MENU_CONFIRM:
                samples = renderToneSteps(0.075, new double[] {0.0, 0.030}, new double[] {0.035, 0.075},
                    new double[] {660.0, 990.0}, 0.18);
                peakScale = 14_000.0;
                break;
            case MENU_BACK:
            default:
                samples = renderToneSteps(0.075, new double[] {0.0, 0.030}, new double[] {0.035, 0.075},
                    new double[] {740.0, 495.0}, 0.18);
                peakScale = 14_000.0;
                break;
        }
        applyEdgeRamps(samples);
        return encodeWav(quantize(samples, peakScale), SAMPLE_RATE);
    }

    /**
     * Synthesizes a seamless 0.50 s looping energy-charge hum where every harmonic and modulation wave completes an
     * exact integer number of cycles so loop wrap points join continuously without clicking.
     * @return encoded 16-bit mono WAV bytes
     */
    public static byte[] synthesizeChargeHum() {
        int count = Math.round(CHARGE_HUM_SECONDS * SAMPLE_RATE);
        double[] samples = new double[count];
        for (int i = 0; i < count; i++) {
            double t = (double) i / SAMPLE_RATE;
            double am = 0.85 + 0.15 * Math.sin(TWO_PI * 4.0 * t);
            double fm = 2.0 * Math.sin(TWO_PI * 8.0 * t);
            double wave = 0.55 * Math.sin(TWO_PI * 110.0 * t + 0.25 * fm)
                + 0.28 * Math.sin(TWO_PI * 220.0 * t + 0.5 * fm)
                + 0.12 * Math.sin(TWO_PI * 330.0 * t)
                + 0.07 * Math.sin(TWO_PI * 440.0 * t);
            samples[i] = Math.tanh(wave * am * 1.15);
        }
        return encodeWav(quantize(samples, 18_000.0), SAMPLE_RATE);
    }

    static byte[] encodeWav(short[] samples, int sampleRate) {
        int dataBytes = samples.length * 2;
        byte[] out = new byte[44 + dataBytes];
        writeAscii(out, 0, "RIFF");
        writeIntLE(out, 4, 36 + dataBytes);
        writeAscii(out, 8, "WAVE");
        writeAscii(out, 12, "fmt ");
        writeIntLE(out, 16, 16);
        writeShortLE(out, 20, 1);
        writeShortLE(out, 22, 1);
        writeIntLE(out, 24, sampleRate);
        writeIntLE(out, 28, sampleRate * 2);
        writeShortLE(out, 32, 2);
        writeShortLE(out, 34, 16);
        writeAscii(out, 36, "data");
        writeIntLE(out, 40, dataBytes);
        for (int i = 0; i < samples.length; i++) {
            writeShortLE(out, 44 + i * 2, samples[i]);
        }
        return out;
    }

    private static double[] renderPistolShot() {
        double[] buf = alloc(0.16);
        int rng = 0x13572468;
        double snapPhase = 0.0;
        double bodyPhase = 0.0;
        double lp = 0.0;
        double hpPrev = 0.0;
        for (int i = 0; i < buf.length; i++) {
            double t = (double) i / SAMPLE_RATE;
            double snapFreq = 240.0 + 1560.0 * Math.exp(-t / 0.011);
            double bodyFreq = 52.0 + 103.0 * Math.exp(-t / 0.032);
            snapPhase += TWO_PI * snapFreq / SAMPLE_RATE;
            bodyPhase += TWO_PI * bodyFreq / SAMPLE_RATE;

            rng = nextRng(rng);
            double white = rngFloat(rng);
            lp += 0.34 * (white - lp);
            double hp = white - hpPrev * 0.65;
            hpPrev = white;

            double crack = hp * Math.exp(-t / 0.020) * 0.70 + lp * Math.exp(-t / 0.048) * 0.55;
            double punch = Math.sin(snapPhase) * Math.exp(-t / 0.014) * 0.65
                + Math.sin(bodyPhase) * Math.exp(-t / 0.055) * 0.75;
            buf[i] = Math.tanh((crack + punch) * 1.4);
        }
        return buf;
    }

    private static double[] renderWallImpact() {
        double[] buf = alloc(0.08);
        int rng = 0x24681357;
        double phase = 0.0;
        double lp = 0.0;
        for (int i = 0; i < buf.length; i++) {
            double t = (double) i / SAMPLE_RATE;
            double freq = 300.0 + 680.0 * Math.exp(-t / 0.014);
            phase += TWO_PI * freq / SAMPLE_RATE;
            rng = nextRng(rng);
            lp += 0.28 * (rngFloat(rng) - lp);
            buf[i] = Math.sin(phase) * Math.exp(-t / 0.018) * 0.65 + lp * Math.exp(-t / 0.016) * 0.55;
        }
        return buf;
    }

    private static double[] renderBlastRelease() {
        double[] buf = alloc(0.28);
        int rng = 0x51A7F00D;
        double carPhase = 0.0;
        double modPhase = 0.0;
        double subPhase = 0.0;
        double lp = 0.0;
        for (int i = 0; i < buf.length; i++) {
            double t = (double) i / SAMPLE_RATE;
            double carFreq = 95.0 + 405.0 * Math.exp(-t / 0.095);
            double modFreq = 55.0 + 135.0 * Math.exp(-t / 0.075);
            double subFreq = 40.0 + 70.0 * Math.exp(-t / 0.090);
            carPhase += TWO_PI * carFreq / SAMPLE_RATE;
            modPhase += TWO_PI * modFreq / SAMPLE_RATE;
            subPhase += TWO_PI * subFreq / SAMPLE_RATE;

            rng = nextRng(rng);
            lp += 0.22 * (rngFloat(rng) - lp);
            double fm = Math.sin(carPhase + 1.8 * Math.exp(-t / 0.11) * Math.sin(modPhase));
            double env = Math.exp(-t / 0.105);
            buf[i] = Math.tanh((fm * 0.72 + Math.sin(subPhase) * 0.65 + lp * 0.35) * env * 1.25);
        }
        return buf;
    }

    private static double[] renderExplosion() {
        double[] buf = alloc(0.52);
        int rng = 0x7E57C0DE;
        double subPhase = 0.0;
        double lp = 0.0;
        double midLp = 0.0;
        for (int i = 0; i < buf.length; i++) {
            double t = (double) i / SAMPLE_RATE;
            double subFreq = 26.0 + 66.0 * Math.exp(-t / 0.14);
            subPhase += TWO_PI * subFreq / SAMPLE_RATE;
            rng = nextRng(rng);
            double white = rngFloat(rng);
            lp += 0.055 * (white - lp);
            midLp += 0.22 * (white - midLp);
            double crackle = (midLp - lp) * Math.exp(-t / 0.11) * 1.25;
            double rumble = lp * Math.exp(-t / 0.22) * 1.8;
            double boom = Math.sin(subPhase) * Math.exp(-t / 0.19) * 0.85;
            buf[i] = Math.tanh((boom + rumble + crackle) * 1.35);
        }
        return buf;
    }

    private static double[] renderTargetHit() {
        double[] buf = alloc(0.11);
        int rng = 0x3C4D5E6F;
        for (int i = 0; i < buf.length; i++) {
            double t = (double) i / SAMPLE_RATE;
            rng = nextRng(rng);
            double clang = 0.50 * Math.sin(TWO_PI * 620.0 * t) * Math.exp(-t / 0.042)
                + 0.35 * Math.sin(TWO_PI * 985.0 * t) * Math.exp(-t / 0.030)
                + 0.25 * Math.sin(TWO_PI * 1490.0 * t) * Math.exp(-t / 0.020);
            double strike = rngFloat(rng) * Math.exp(-t / 0.006) * 0.35;
            buf[i] = clang + strike;
        }
        return buf;
    }

    private static double[] renderTargetDestroyed() {
        double[] buf = alloc(0.24);
        int rng = 0x9E3779B9;
        double p1 = 0.0;
        double p2 = 0.0;
        double subPhase = 0.0;
        double lp = 0.0;
        for (int i = 0; i < buf.length; i++) {
            double t = (double) i / SAMPLE_RATE;
            p1 += TWO_PI * (260.0 + 660.0 * Math.exp(-t / 0.08)) / SAMPLE_RATE;
            p2 += TWO_PI * (410.0 + 1010.0 * Math.exp(-t / 0.06)) / SAMPLE_RATE;
            subPhase += TWO_PI * (45.0 + 90.0 * Math.exp(-t / 0.065)) / SAMPLE_RATE;
            rng = nextRng(rng);
            double white = rngFloat(rng);
            lp += 0.26 * (white - lp);
            double shatter = lp * (Math.exp(-t / 0.045) + 0.65 * Math.exp(-Math.max(0.0, t - 0.045) / 0.055));
            double ring = (Math.sin(p1) * 0.45 + Math.sin(p2) * 0.30) * Math.exp(-t / 0.095);
            double thud = Math.sin(subPhase) * Math.exp(-t / 0.075) * 0.55;
            buf[i] = Math.tanh((shatter * 0.75 + ring + thud) * 1.25);
        }
        return buf;
    }

    private static double[] renderEnemyHit() {
        double[] buf = alloc(0.10);
        int rng = 0xA1B2C3D4;
        for (int i = 0; i < buf.length; i++) {
            double t = (double) i / SAMPLE_RATE;
            rng = nextRng(rng);
            double clang = 0.55 * Math.sin(TWO_PI * 430.0 * t + 1.1 * Math.sin(TWO_PI * 215.0 * t))
                + 0.35 * Math.sin(TWO_PI * 1280.0 * t);
            double spark = rngFloat(rng) * Math.exp(-t / 0.025) * 0.45;
            buf[i] = clang * Math.exp(-t / 0.036) + spark;
        }
        return buf;
    }

    private static double[] renderEnemyDestroyed() {
        double[] buf = alloc(0.34);
        int rng = 0xCAFEBABE;
        double turbinePhase = 0.0;
        double popPhase = 0.0;
        double lp = 0.0;
        for (int i = 0; i < buf.length; i++) {
            double t = (double) i / SAMPLE_RATE;
            double spinFreq = 82.0 + 598.0 * Math.exp(-t / 0.14);
            turbinePhase += TWO_PI * spinFreq / SAMPLE_RATE;
            popPhase += TWO_PI * (42.0 + 140.0 * Math.exp(-t / 0.055)) / SAMPLE_RATE;
            rng = nextRng(rng);
            lp += 0.18 * (rngFloat(rng) - lp);
            double wine = (0.55 * Math.sin(turbinePhase) + 0.25 * Math.sin(2.0 * turbinePhase))
                * Math.exp(-t / 0.16);
            double crunch = lp * Math.exp(-t / 0.12) * 0.90;
            double pop = Math.sin(popPhase) * Math.exp(-t / 0.08) * 0.75;
            buf[i] = Math.tanh((wine + crunch + pop) * 1.3);
        }
        return buf;
    }

    private static double[] renderEnemyShoot() {
        double[] buf = alloc(0.14);
        int rng = 0x42424242;
        double carPhase = 0.0;
        double modPhase = 0.0;
        for (int i = 0; i < buf.length; i++) {
            double t = (double) i / SAMPLE_RATE;
            double freq = 270.0 + 1110.0 * Math.exp(-t / 0.038);
            carPhase += TWO_PI * freq / SAMPLE_RATE;
            modPhase += TWO_PI * (freq * 0.5) / SAMPLE_RATE;
            rng = nextRng(rng);
            double zap = Math.sin(carPhase + 1.4 * Math.sin(modPhase)) * Math.exp(-t / 0.052);
            double fizz = rngFloat(rng) * Math.exp(-t / 0.018) * 0.22;
            buf[i] = Math.tanh((zap + fizz) * 1.2);
        }
        return buf;
    }

    private static double[] renderDroneLunge() {
        double[] buf = alloc(0.20);
        int rng = 0x55AA55AA;
        double phase = 0.0;
        for (int i = 0; i < buf.length; i++) {
            double t = (double) i / SAMPLE_RATE;
            double u = t / 0.20;
            double arch = Math.sin(Math.PI * u);
            double freq = 320.0 + 580.0 * arch + 40.0 * Math.sin(TWO_PI * 65.0 * t);
            phase += TWO_PI * freq / SAMPLE_RATE;
            rng = nextRng(rng);
            double screech = Math.sin(phase) + 0.40 * Math.sin(2.0 * phase);
            double env = Math.sin(Math.PI * Math.min(1.0, u)) * Math.exp(-t / 0.15);
            buf[i] = Math.tanh((screech * 0.65 + rngFloat(rng) * 0.20) * env * 1.35);
        }
        return buf;
    }

    private static double[] renderPlayerDash() {
        double[] buf = alloc(0.15);
        int rng = 0x8080F00D;
        double zipPhase = 0.0;
        double lp = 0.0;
        for (int i = 0; i < buf.length; i++) {
            double t = (double) i / SAMPLE_RATE;
            double freq = 180.0 + 360.0 * (1.0 - Math.exp(-t / 0.055));
            zipPhase += TWO_PI * freq / SAMPLE_RATE;
            rng = nextRng(rng);
            lp += 0.28 * (rngFloat(rng) - lp);
            double air = (rngFloat(rng) - lp * 0.6) * (1.0 - Math.exp(-t / 0.012)) * Math.exp(-t / 0.055);
            double tone = Math.sin(zipPhase) * Math.exp(-t / 0.065) * 0.55;
            buf[i] = Math.tanh((air * 0.75 + tone) * 1.25);
        }
        return buf;
    }

    private static double[] renderPlayerHurt() {
        double[] buf = alloc(0.16);
        int rng = 0xBADC0FFE;
        double phase = 0.0;
        double lp = 0.0;
        for (int i = 0; i < buf.length; i++) {
            double t = (double) i / SAMPLE_RATE;
            double freq = 46.0 + 94.0 * Math.exp(-t / 0.055);
            phase += TWO_PI * freq / SAMPLE_RATE;
            rng = nextRng(rng);
            lp += 0.14 * (rngFloat(rng) - lp);
            double thud = Math.sin(phase) * Math.exp(-t / 0.068) * 0.85;
            double bite = lp * Math.exp(-t / 0.045) * 0.75;
            buf[i] = Math.tanh((thud + bite) * 1.35);
        }
        return buf;
    }

    private static double[] renderPlayerDeath() {
        double[] buf = alloc(0.46);
        int rng = 0xDEADFA11;
        double phase = 0.0;
        double subPhase = 0.0;
        double lp = 0.0;
        for (int i = 0; i < buf.length; i++) {
            double t = (double) i / SAMPLE_RATE;
            double freq = 62.0 + 358.0 * Math.exp(-t / 0.19);
            double subFreq = 30.0 + 65.0 * Math.exp(-t / 0.12);
            phase += TWO_PI * freq / SAMPLE_RATE;
            subPhase += TWO_PI * subFreq / SAMPLE_RATE;
            rng = nextRng(rng);
            lp += 0.10 * (rngFloat(rng) - lp);
            double powerDown = (0.55 * Math.sin(phase) + 0.45 * Math.sin(subPhase)) * Math.exp(-t / 0.24);
            double haze = lp * Math.exp(-t / 0.26) * 0.55;
            buf[i] = Math.tanh((powerDown + haze) * 1.3);
        }
        return buf;
    }

    private static double[] renderLowEnergy() {
        double[] buf = alloc(0.14);
        for (int i = 0; i < buf.length; i++) {
            double t = (double) i / SAMPLE_RATE;
            double pulse1 = pulseWindow(t, 0.0, 0.055, 0.005);
            double pulse2 = pulseWindow(t, 0.075, 0.135, 0.005);
            double s1 = (0.65 * Math.sin(TWO_PI * 210.0 * t) + 0.35 * Math.sin(TWO_PI * 420.0 * t)) * pulse1;
            double s2 = (0.65 * Math.sin(TWO_PI * 165.0 * t) + 0.35 * Math.sin(TWO_PI * 330.0 * t)) * pulse2;
            buf[i] = Math.tanh((s1 + s2) * 1.25);
        }
        return buf;
    }

    private static double[] renderMenuMove() {
        double[] buf = alloc(0.035);
        for (int i = 0; i < buf.length; i++) {
            double t = (double) i / SAMPLE_RATE;
            double wave = 0.78 * Math.sin(TWO_PI * 880.0 * t) + 0.22 * Math.sin(TWO_PI * 1760.0 * t);
            buf[i] = wave * Math.exp(-t / 0.008);
        }
        return buf;
    }

    private static double[] renderToneSteps(double totalSec, double[] starts, double[] ends, double[] freqs,
                                            double secondHarmonicMix) {
        double[] buf = alloc(totalSec);
        for (int i = 0; i < buf.length; i++) {
            double t = (double) i / SAMPLE_RATE;
            double sum = 0.0;
            for (int n = 0; n < freqs.length; n++) {
                double w = pulseWindow(t, starts[n], ends[n], 0.006);
                if (w <= 0.0) continue;
                double decay = Math.exp(-Math.max(0.0, t - starts[n]) / Math.max(0.015, (ends[n] - starts[n]) * 0.65));
                double tone = Math.sin(TWO_PI * freqs[n] * t)
                    + secondHarmonicMix * Math.sin(TWO_PI * (freqs[n] * 2.0) * t);
                sum += tone * w * decay;
            }
            buf[i] = sum;
        }
        return buf;
    }

    private static double pulseWindow(double t, double start, double end, double edge) {
        if (t <= start || t >= end) return 0.0;
        double in = Math.min(1.0, (t - start) / edge);
        double out = Math.min(1.0, (end - t) / edge);
        return Math.min(in, out);
    }

    private static void applyEdgeRamps(double[] buf) {
        int inSamples = Math.min(buf.length / 4, (int) (0.0025 * SAMPLE_RATE));
        for (int i = 0; i < inSamples; i++) {
            buf[i] *= i / (double) Math.max(1, inSamples);
        }
        int outSamples = Math.min(buf.length / 3, (int) (0.004 * SAMPLE_RATE));
        for (int i = 0; i < outSamples; i++) {
            int idx = buf.length - 1 - i;
            buf[idx] *= i / (double) Math.max(1, outSamples);
        }
    }

    private static short[] quantize(double[] samples, double targetPeak) {
        double maxAbs = 0.0;
        for (double s : samples) maxAbs = Math.max(maxAbs, Math.abs(s));
        double scale = maxAbs > 1e-9 ? targetPeak / maxAbs : 0.0;
        short[] out = new short[samples.length];
        for (int i = 0; i < samples.length; i++) {
            long rounded = Math.round(samples[i] * scale);
            out[i] = (short) Math.max(Short.MIN_VALUE, Math.min(Short.MAX_VALUE, rounded));
        }
        return out;
    }

    private static double[] alloc(double seconds) {
        return new double[Math.max(1, (int) Math.round(seconds * SAMPLE_RATE))];
    }

    private static int nextRng(int state) {
        state ^= state << 13;
        state ^= state >>> 17;
        state ^= state << 5;
        return state == 0 ? 1 : state;
    }

    private static double rngFloat(int state) {
        return (state / (double) Integer.MAX_VALUE);
    }

    private static void writeAscii(byte[] dst, int at, String text) {
        for (int i = 0; i < text.length(); i++) dst[at + i] = (byte) text.charAt(i);
    }

    private static void writeShortLE(byte[] dst, int at, int value) {
        dst[at] = (byte) (value & 0xFF);
        dst[at + 1] = (byte) ((value >>> 8) & 0xFF);
    }

    private static void writeIntLE(byte[] dst, int at, int value) {
        dst[at] = (byte) (value & 0xFF);
        dst[at + 1] = (byte) ((value >>> 8) & 0xFF);
        dst[at + 2] = (byte) ((value >>> 16) & 0xFF);
        dst[at + 3] = (byte) ((value >>> 24) & 0xFF);
    }
}

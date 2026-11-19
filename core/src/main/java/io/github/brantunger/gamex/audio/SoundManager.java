package io.github.brantunger.gamex.audio;

import com.badlogic.gdx.Gdx;
import com.badlogic.gdx.audio.Sound;
import com.badlogic.gdx.math.Vector3;
import com.badlogic.gdx.utils.Disposable;
import io.github.brantunger.gamex.GameSimulation;
import io.github.brantunger.gamex.hud.MenuState;
import io.github.brantunger.gamex.settings.GameSettings;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.ArrayList;
import java.util.EnumMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;

/**
 * Loads {@link Sound} assets from {@code assets/sounds/} via libGDX internal file handles and drives 3D spatialized
 * combat audio, centered player/menu audio, and the pitched blast-charge loop. Degrades cleanly to silent mode when
 * muted or when no audio device exists; a sound file that fails to load silences only its own cue. Each cue's
 * playback volume comes from the {@link SoundVolumes} table it is given.
 */
public final class SoundManager implements Disposable {

    private static final Logger log = LoggerFactory.getLogger(SoundManager.class);

    /** Directory inside {@code assets/} holding shipped {@code .wav} sound effects. */
    public static final String SOUNDS_DIR = "sounds";
    /** File name of the seamless blast-charge loop inside {@link #SOUNDS_DIR}. */
    public static final String CHARGE_HUM_FILE = "charge-hum.wav";
    /** Internal asset path of the seamless blast-charge loop. */
    public static final String CHARGE_HUM_PATH = SOUNDS_DIR + "/" + CHARGE_HUM_FILE;

    private final boolean muted;
    private final SoundVolumes volumes;
    private final Map<SoundEvent.Type, Sound> sounds = new EnumMap<>(SoundEvent.Type.class);
    private final List<SoundEvent> frameEvents = new ArrayList<>();
    private final Vector3 listenerEye = new Vector3();
    private final Sound chargeHum;
    private boolean available;
    private long chargeLoopId = -1L;

    /**
     * @param muted when true (e.g. {@code GAMEX_SCREENSHOT} runs), skips audio creation and suppresses playback
     * @param volumes the playback volume of each cue, from {@link SoundVolumes#load()} in the game
     */
    public SoundManager(boolean muted, SoundVolumes volumes) {
        this.muted = muted;
        this.volumes = Objects.requireNonNull(volumes, "volumes");
        Sound loadedCharge = null;
        if (!muted && Gdx.audio != null && Gdx.files != null) {
            for (SoundEvent.Type type : SoundEvent.Type.values()) {
                Sound sound = loadSound(assetPath(type));
                if (sound != null) sounds.put(type, sound);
            }
            loadedCharge = loadSound(CHARGE_HUM_PATH);
            available = !sounds.isEmpty() || loadedCharge != null;
            int expected = SoundEvent.Type.values().length + 1;
            int loaded = sounds.size() + (loadedCharge == null ? 0 : 1);
            if (loaded == expected) {
                log.info("Loaded {} sounds from {}/", loaded, SOUNDS_DIR);
            } else {
                log.warn("Loaded {} of {} sounds from {}/; the rest stay silent", loaded, expected, SOUNDS_DIR);
            }
        } else if (muted) {
            log.info("Sound is muted; no sounds loaded");
        } else {
            // The desktop backend always provides an audio object, a silent one when no device opens.
            log.debug("No libGDX audio or files backend is set, as in a headless test; no sounds loaded");
        }
        this.chargeHum = loadedCharge;
    }

    /** @return the sound at internal asset {@code path}, or null if it is missing or cannot be decoded. */
    private static Sound loadSound(String path) {
        try {
            Sound sound = Gdx.audio.newSound(Gdx.files.internal(path));
            log.debug("Loaded sound {}", path);
            return sound;
        } catch (RuntimeException e) {
            log.warn("Sound {} failed to load; continuing without it: {}", path, e.getMessage());
            return null;
        }
    }

    /** @return the {@code .wav} file name inside {@link #SOUNDS_DIR} for {@code type}. */
    public static String fileName(SoundEvent.Type type) {
        return type.name().toLowerCase(Locale.ROOT).replace('_', '-') + ".wav";
    }

    /** @return the internal asset path ({@code sounds/<cue>.wav}) for {@code type}. */
    public static String assetPath(SoundEvent.Type type) {
        return SOUNDS_DIR + "/" + fileName(type);
    }

    /**
     * Drains pending menu and simulation sound events, triggers spatial or centered one-shots scaled by master volume,
     * and updates or stops the blast-charge loop.
     */
    public void update(GameSimulation simulation, MenuState menuState, GameSettings settings) {
        frameEvents.clear();
        if (menuState != null) menuState.drainSoundEvents(frameEvents);
        if (simulation != null) simulation.drainSoundEvents(frameEvents);

        float master = settings == null ? GameSettings.DEFAULT_MASTER_VOLUME : settings.getMasterVolume();
        if (muted || !available) {
            stopLoops();
            return;
        }

        boolean canPlay = master > 0.001f;
        if (canPlay && !frameEvents.isEmpty()) {
            float eyeX = 0f;
            float eyeY = 0f;
            float eyeZ = 0f;
            float yawDeg = 0f;
            if (simulation != null) {
                simulation.player.eye(listenerEye);
                eyeX = listenerEye.x;
                eyeY = listenerEye.y;
                eyeZ = listenerEye.z;
                yawDeg = simulation.player.getYaw();
            }
            for (int i = 0; i < frameEvents.size(); i++) {
                playEvent(frameEvents.get(i), master, eyeX, eyeY, eyeZ, yawDeg, simulation != null);
            }
        }

        boolean shouldLoop = canPlay && chargeHum != null && menuState != null && menuState.isPlaying()
            && simulation != null && simulation.isBlastChargingAudioActive();
        if (!shouldLoop) {
            stopLoops();
            return;
        }

        float progress = simulation.getBlastChargeProgress();
        float loopVol = clamp((0.30f + 0.35f * progress) * master, 0f, 1f);
        float loopPitch = 0.78f + 0.82f * progress;
        if (chargeLoopId == -1L) {
            chargeLoopId = chargeHum.loop(loopVol, loopPitch, 0f);
        } else {
            chargeHum.setVolume(chargeLoopId, loopVol);
            chargeHum.setPitch(chargeLoopId, loopPitch);
        }
    }

    /** Immediately stops any continuous looping sound (e.g. when pausing or muting). */
    public void stopLoops() {
        if (chargeHum != null) {
            if (chargeLoopId != -1L) chargeHum.stop(chargeLoopId);
            chargeHum.stop();
        }
        chargeLoopId = -1L;
    }

    @Override
    public void dispose() {
        log.debug("Disposing {} sounds", sounds.size() + (chargeHum == null ? 0 : 1));
        stopLoops();
        available = false;
        disposeHandles(chargeHum);
    }

    /**
     * Computes distance attenuation {@code [0]} and stereo pan {@code [1]} for a 3D world event relative to listener
     * eye position and yaw (0 faces negative z, 90 faces positive x).
     */
    static float[] computeSpatial(float eyeX, float eyeY, float eyeZ, float yawDeg,
                                  float worldX, float worldY, float worldZ) {
        float dx = worldX - eyeX;
        float dy = worldY - eyeY;
        float dz = worldZ - eyeZ;
        float dist = (float) Math.sqrt(dx * dx + dy * dy + dz * dz);
        float horiz = (float) Math.hypot(dx, dz);
        double yawRad = Math.toRadians(yawDeg);
        float rightX = (float) Math.cos(yawRad);
        float rightZ = (float) Math.sin(yawRad);
        float pan = horiz < 0.05f ? 0f : clamp((dx * rightX + dz * rightZ) / horiz * 0.85f, -0.85f, 0.85f);
        float over = Math.max(0f, dist - 1.5f);
        float attenuation = clamp((1f / (1f + 0.16f * over)) * (1f - over / 26.5f), 0f, 1f);
        return new float[] {attenuation, pan};
    }

    private void playEvent(SoundEvent event, float master, float eyeX, float eyeY, float eyeZ, float yawDeg,
                           boolean hasListener) {
        Sound sound = sounds.get(event.type);
        if (sound == null) return;
        float attenuation = 1f;
        float pan = 0f;
        if (event.spatial && hasListener) {
            float[] spatial = computeSpatial(eyeX, eyeY, eyeZ, yawDeg, event.x, event.y, event.z);
            attenuation = spatial[0];
            pan = spatial[1];
        }
        float volume = clamp(volumes.gain(event.type) * master * event.intensity * attenuation, 0f, 1f);
        if (volume < 0.005f) return;
        sound.play(volume, pitchFor(event), pan);
    }

    private static float pitchFor(SoundEvent event) {
        if (event.type == SoundEvent.Type.BLAST_RELEASE) {
            return 1.12f - 0.24f * clamp(event.intensity, 0.70f, 1.0f);
        }
        if (event.type == SoundEvent.Type.EXPLOSION) {
            return 1.08f - 0.22f * clamp(event.intensity, 0.75f, 1.1f);
        }
        return 1.0f;
    }

    private void disposeHandles(Sound loopHandle) {
        for (Sound sound : sounds.values()) {
            if (sound != null) sound.dispose();
        }
        sounds.clear();
        if (loopHandle != null) loopHandle.dispose();
    }

    private static float clamp(float value, float min, float max) {
        return Math.max(min, Math.min(max, value));
    }
}

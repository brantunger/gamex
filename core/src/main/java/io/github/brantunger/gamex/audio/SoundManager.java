package io.github.brantunger.gamex.audio;

import com.badlogic.gdx.Gdx;
import com.badlogic.gdx.audio.Sound;
import com.badlogic.gdx.math.Vector3;
import com.badlogic.gdx.utils.Disposable;
import io.github.brantunger.gamex.GameSimulation;
import io.github.brantunger.gamex.hud.MenuState;
import io.github.brantunger.gamex.settings.GameSettings;

import java.util.ArrayList;
import java.util.EnumMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

/**
 * Loads {@link Sound} assets from {@code assets/sounds/} via libGDX internal file handles and drives 3D spatialized
 * combat audio, centered player/menu audio, and the pitched blast-charge loop. Degrades cleanly to silent mode when
 * muted or when no audio device exists; a sound file that fails to load silences only its own cue.
 */
public final class SoundManager implements Disposable {

    /** Directory inside {@code assets/} holding shipped {@code .wav} sound effects. */
    public static final String SOUNDS_DIR = "sounds";
    /** File name of the seamless blast-charge loop inside {@link #SOUNDS_DIR}. */
    public static final String CHARGE_HUM_FILE = "charge-hum.wav";
    /** Internal asset path of the seamless blast-charge loop. */
    public static final String CHARGE_HUM_PATH = SOUNDS_DIR + "/" + CHARGE_HUM_FILE;

    private final boolean muted;
    private final Map<SoundEvent.Type, Sound> sounds = new EnumMap<>(SoundEvent.Type.class);
    private final List<SoundEvent> frameEvents = new ArrayList<>();
    private final Vector3 listenerEye = new Vector3();
    private final Sound chargeHum;
    private boolean available;
    private long chargeLoopId = -1L;

    /**
     * @param muted when true (e.g. {@code GAMEX_SCREENSHOT} runs), skips audio creation and suppresses playback
     */
    public SoundManager(boolean muted) {
        this.muted = muted;
        Sound loadedCharge = null;
        if (!muted && Gdx.audio != null && Gdx.files != null) {
            for (SoundEvent.Type type : SoundEvent.Type.values()) {
                Sound sound = loadSound(assetPath(type));
                if (sound != null) sounds.put(type, sound);
            }
            loadedCharge = loadSound(CHARGE_HUM_PATH);
            available = !sounds.isEmpty() || loadedCharge != null;
        }
        this.chargeHum = loadedCharge;
    }

    /** @return the sound at internal asset {@code path}, or null if it is missing or cannot be decoded. */
    private static Sound loadSound(String path) {
        try {
            return Gdx.audio.newSound(Gdx.files.internal(path));
        } catch (RuntimeException e) {
            System.err.println("Sound " + path + " failed to load; continuing without it: " + e.getMessage());
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
        float volume = clamp(baseGain(event.type) * master * event.intensity * attenuation, 0f, 1f);
        if (volume < 0.005f) return;
        sound.play(volume, pitchFor(event), pan);
    }

    private static float baseGain(SoundEvent.Type type) {
        switch (type) {
            case MENU_MOVE:
                return 0.35f;
            case MENU_CONFIRM:
                return 0.50f;
            case MENU_BACK:
                return 0.48f;
            case WALL_IMPACT:
                return 0.42f;
            case TARGET_HIT:
            case ENEMY_HIT:
                return 0.62f;
            case PISTOL_SHOT:
                return 0.68f;
            case SCATTERGUN_FIRE:
                return 0.78f;
            case SCATTERGUN_ALT_FIRE:
                return 0.88f;
            case RAIL_FIRE:
                return 0.86f;
            case RAIL_ZOOM_IN:
            case RAIL_ZOOM_OUT:
                return 0.36f;
            case WEAPON_SWITCH:
            case WEAPON_DRY_FIRE:
            case PLAYER_JUMP:
                return 0.56f;
            case PLAYER_LAND:
                return 0.52f;
            case BLAST_RELEASE:
            case ENEMY_SHOOT:
            case DRONE_LUNGE:
            case HAZARD_ZAP:
            case PLAYER_DASH:
                return 0.72f;
            case BOSS_SHOOT:
                return 0.80f;
            case BOSS_SLAM:
                return 0.88f;
            case BOSS_OVERDRIVE:
                return 0.90f;
            case BOSS_DESTROYED:
                return 0.95f;
            case PICKUP_HEALTH:
            case PICKUP_ARMOR:
            case PICKUP_ENERGY:
            case PICKUP_SHELLS:
            case PICKUP_SLUGS:
                return 0.65f;
            case PICKUP_KEYCARD:
                return 0.68f;
            case PICKUP_WEAPON:
                return 0.74f;
            case DOOR_LOCKED:
                return 0.62f;
            case DOOR_OPEN:
                return 0.72f;
            case CONSOLE_ACTIVATE:
                return 0.75f;
            case LOW_ENERGY:
                return 0.58f;
            case TARGET_DESTROYED:
            case ENEMY_DESTROYED:
            case PLAYER_HURT:
            case PLAYER_DEATH:
                return 0.80f;
            case EXPLOSION:
                return 0.90f;
            default:
                return 0.70f;
        }
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

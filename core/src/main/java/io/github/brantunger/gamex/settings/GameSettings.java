package io.github.brantunger.gamex.settings;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * User-configurable gameplay and display settings, clamped to safe ranges and persisted via {@link KeyValueStore}.
 */
public final class GameSettings {

    private static final Logger log = LoggerFactory.getLogger(GameSettings.class);

    public static final String PREFERENCES_NAME = "io.github.brantunger.gamex.settings";

    public static final float DEFAULT_SENSITIVITY = 0.12f;
    public static final float MIN_SENSITIVITY = 0.03f;
    public static final float MAX_SENSITIVITY = 0.40f;
    public static final float SENSITIVITY_STEP = 0.01f;

    public static final float DEFAULT_FOV = 70f;
    public static final float MIN_FOV = 60f;
    public static final float MAX_FOV = 110f;
    public static final float FOV_STEP = 5f;

    public static final float DEFAULT_MASTER_VOLUME = 0.80f;
    public static final float MIN_MASTER_VOLUME = 0.0f;
    public static final float MAX_MASTER_VOLUME = 1.0f;
    public static final float MASTER_VOLUME_STEP = 0.10f;

    private static final String KEY_SENSITIVITY = "mouseSensitivity";
    private static final String KEY_INVERT_Y = "invertY";
    private static final String KEY_FOV = "fov";
    private static final String KEY_VIEW_BOB = "viewBob";
    private static final String KEY_MASTER_VOLUME = "masterVolume";
    private static final String KEY_FULLSCREEN = "fullscreen";

    private float mouseSensitivity = DEFAULT_SENSITIVITY;
    private boolean invertY;
    private float fov = DEFAULT_FOV;
    private boolean viewBob = true;
    private float masterVolume = DEFAULT_MASTER_VOLUME;
    private boolean fullscreen;

    /**
     * Loads settings from {@code store}, clamping any out-of-range values; a stored NaN or infinity becomes the
     * default.
     */
    public static GameSettings load(KeyValueStore store) {
        GameSettings settings = new GameSettings();
        if (store == null) return settings;
        settings.setMouseSensitivity(store.getFloat(KEY_SENSITIVITY, DEFAULT_SENSITIVITY));
        settings.setInvertY(store.getBoolean(KEY_INVERT_Y, false));
        settings.setFov(store.getFloat(KEY_FOV, DEFAULT_FOV));
        settings.setViewBob(store.getBoolean(KEY_VIEW_BOB, true));
        settings.setMasterVolume(store.getFloat(KEY_MASTER_VOLUME, DEFAULT_MASTER_VOLUME));
        settings.setFullscreen(store.getBoolean(KEY_FULLSCREEN, false));
        return settings;
    }

    /** Writes all settings into {@code store} and flushes. */
    public void save(KeyValueStore store) {
        if (store == null) return;
        store.putFloat(KEY_SENSITIVITY, mouseSensitivity);
        store.putBoolean(KEY_INVERT_Y, invertY);
        store.putFloat(KEY_FOV, fov);
        store.putBoolean(KEY_VIEW_BOB, viewBob);
        store.putFloat(KEY_MASTER_VOLUME, masterVolume);
        store.putBoolean(KEY_FULLSCREEN, fullscreen);
        store.flush();
        log.debug("Saved settings: sensitivity {}, invert Y {}, field of view {}, view bob {}, volume {},"
            + " fullscreen {}", mouseSensitivity, invertY, fov, viewBob, masterVolume, fullscreen);
    }

    public float getMouseSensitivity() {
        return mouseSensitivity;
    }

    public void setMouseSensitivity(float value) {
        float finite = Float.isFinite(value) ? value : DEFAULT_SENSITIVITY;
        float clamped = Math.max(MIN_SENSITIVITY, Math.min(MAX_SENSITIVITY, finite));
        mouseSensitivity = Math.round(clamped * 100f) / 100f;
    }

    public void adjustSensitivity(int steps) {
        setMouseSensitivity(mouseSensitivity + steps * SENSITIVITY_STEP);
    }

    public boolean isInvertY() {
        return invertY;
    }

    public void setInvertY(boolean invertY) {
        this.invertY = invertY;
    }

    public float getFov() {
        return fov;
    }

    public void setFov(float value) {
        float finite = Float.isFinite(value) ? value : DEFAULT_FOV;
        float clamped = Math.max(MIN_FOV, Math.min(MAX_FOV, finite));
        fov = Math.round(clamped);
    }

    public void adjustFov(int steps) {
        setFov(fov + steps * FOV_STEP);
    }

    public boolean isViewBob() {
        return viewBob;
    }

    public void setViewBob(boolean viewBob) {
        this.viewBob = viewBob;
    }

    public float getMasterVolume() {
        return masterVolume;
    }

    public void setMasterVolume(float value) {
        float finite = Float.isFinite(value) ? value : DEFAULT_MASTER_VOLUME;
        float clamped = Math.max(MIN_MASTER_VOLUME, Math.min(MAX_MASTER_VOLUME, finite));
        masterVolume = Math.round(clamped * 100f) / 100f;
    }

    public void adjustMasterVolume(int steps) {
        setMasterVolume(masterVolume + steps * MASTER_VOLUME_STEP);
    }

    public boolean isFullscreen() {
        return fullscreen;
    }

    public void setFullscreen(boolean fullscreen) {
        this.fullscreen = fullscreen;
    }
}

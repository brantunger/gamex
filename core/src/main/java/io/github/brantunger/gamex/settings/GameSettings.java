package io.github.brantunger.gamex.settings;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * User-configurable gameplay and display settings, clamped to safe ranges and persisted via {@link KeyValueStore}.
 * The defaults, the ranges and the steps come from {@link SettingsDefaults}; the preference keys are the save format
 * and stay here.
 */
public final class GameSettings {

    private static final Logger log = LoggerFactory.getLogger(GameSettings.class);

    public static final String PREFERENCES_NAME = "io.github.brantunger.gamex.settings";

    private static final String KEY_SENSITIVITY = "mouseSensitivity";
    private static final String KEY_INVERT_Y = "invertY";
    private static final String KEY_FOV = "fov";
    private static final String KEY_VIEW_BOB = "viewBob";
    private static final String KEY_MASTER_VOLUME = "masterVolume";
    private static final String KEY_FULLSCREEN = "fullscreen";

    private final SettingsDefaults defaults;
    private float mouseSensitivity;
    private boolean invertY;
    private float fov;
    private boolean viewBob;
    private float masterVolume;
    private boolean fullscreen;

    /**
     * Builds the default settings with the defaults file read here and now. This is for tests only: the game loads
     * the defaults once at start-up and uses {@link #GameSettings(SettingsDefaults)} or
     * {@link #load(KeyValueStore, SettingsDefaults)}.
     */
    public GameSettings() {
        this(SettingsDefaults.load());
    }

    /**
     * @param defaults what each setting starts at, and its range and step, as loaded at start-up
     */
    public GameSettings(SettingsDefaults defaults) {
        if (defaults == null) throw new IllegalArgumentException("Settings defaults must not be null");
        this.defaults = defaults;
        // Through the setters, as load() sets a stored value, so that both start a setting at the same number.
        setMouseSensitivity(defaults.sensitivity.initial);
        invertY = defaults.invertY;
        setFov(defaults.fov.initial);
        viewBob = defaults.viewBob;
        setMasterVolume(defaults.volume.initial);
        fullscreen = defaults.fullscreen;
    }

    /**
     * Loads settings from {@code store}, clamping any out-of-range values; a setting the store does not hold, and a
     * stored NaN or infinity, becomes the default.
     * @param store the saved preferences, or null for the defaults alone
     * @param defaults what each setting starts at, and its range and step, as loaded at start-up
     */
    public static GameSettings load(KeyValueStore store, SettingsDefaults defaults) {
        GameSettings settings = new GameSettings(defaults);
        if (store == null) return settings;
        settings.setMouseSensitivity(store.getFloat(KEY_SENSITIVITY, settings.mouseSensitivity));
        settings.setInvertY(store.getBoolean(KEY_INVERT_Y, settings.invertY));
        settings.setFov(store.getFloat(KEY_FOV, settings.fov));
        settings.setViewBob(store.getBoolean(KEY_VIEW_BOB, settings.viewBob));
        settings.setMasterVolume(store.getFloat(KEY_MASTER_VOLUME, settings.masterVolume));
        settings.setFullscreen(store.getBoolean(KEY_FULLSCREEN, settings.fullscreen));
        return settings;
    }

    /** @return what each setting starts at, and its range and step */
    public SettingsDefaults defaults() {
        return defaults;
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
        mouseSensitivity = Math.round(defaults.sensitivity.clamp(value) * 100f) / 100f;
    }

    public void adjustSensitivity(int steps) {
        setMouseSensitivity(mouseSensitivity + steps * defaults.sensitivity.step);
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
        fov = Math.round(defaults.fov.clamp(value));
    }

    public void adjustFov(int steps) {
        setFov(fov + steps * defaults.fov.step);
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
        masterVolume = Math.round(defaults.volume.clamp(value) * 100f) / 100f;
    }

    public void adjustMasterVolume(int steps) {
        setMasterVolume(masterVolume + steps * defaults.volume.step);
    }

    public boolean isFullscreen() {
        return fullscreen;
    }

    public void setFullscreen(boolean fullscreen) {
        this.fullscreen = fullscreen;
    }
}

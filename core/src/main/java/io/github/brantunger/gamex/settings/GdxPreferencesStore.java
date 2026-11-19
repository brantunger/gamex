package io.github.brantunger.gamex.settings;

import com.badlogic.gdx.Preferences;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.HashSet;
import java.util.Set;

/**
 * Adapts libGDX {@link Preferences} to {@link KeyValueStore}. A stored number that does not parse, as in a
 * hand-edited or damaged file, reads as the default.
 */
public final class GdxPreferencesStore implements KeyValueStore {

    private static final Logger log = LoggerFactory.getLogger(GdxPreferencesStore.class);
    private static final String MISSING = "\u0000gamex.missing\u0000";

    private final Preferences prefs;
    /** Keys already warned about; the menus read some keys on every frame. */
    private final Set<String> warnedKeys = new HashSet<>();

    /** @param prefs the backing libGDX preferences instance */
    public GdxPreferencesStore(Preferences prefs) {
        this.prefs = prefs;
    }

    @Override
    public float getFloat(String key, float defaultValue) {
        String raw = prefs.getString(key, MISSING);
        try {
            if (raw == null || MISSING.equals(raw)) return prefs.getFloat(key, defaultValue);
            return Float.parseFloat(raw.trim());
        } catch (NumberFormatException e) {
            warnUnreadable(key, defaultValue);
            return defaultValue;
        }
    }

    @Override
    public int getInteger(String key, int defaultValue) {
        String raw = prefs.getString(key, MISSING);
        try {
            if (raw == null || MISSING.equals(raw)) return prefs.getInteger(key, defaultValue);
            return Integer.parseInt(raw.trim());
        } catch (NumberFormatException e) {
            warnUnreadable(key, defaultValue);
            return defaultValue;
        }
    }

    /** Warns, once for each key, that a stored number did not parse. */
    private void warnUnreadable(String key, Object defaultValue) {
        if (warnedKeys.add(key)) log.warn("The stored value of '{}' is not a number; using {}", key, defaultValue);
    }

    @Override
    public boolean getBoolean(String key, boolean defaultValue) {
        String raw = prefs.getString(key, MISSING);
        if (raw == null || MISSING.equals(raw)) return prefs.getBoolean(key, defaultValue);
        String trimmed = raw.trim();
        if ("true".equalsIgnoreCase(trimmed)) return true;
        if ("false".equalsIgnoreCase(trimmed)) return false;
        return defaultValue;
    }

    @Override
    public String getString(String key, String defaultValue) {
        return prefs.getString(key, defaultValue);
    }

    @Override
    public void putFloat(String key, float value) {
        prefs.putFloat(key, value);
    }

    @Override
    public void putInteger(String key, int value) {
        prefs.putInteger(key, value);
    }

    @Override
    public void putBoolean(String key, boolean value) {
        prefs.putBoolean(key, value);
    }

    @Override
    public void putString(String key, String value) {
        prefs.putString(key, value);
    }

    @Override
    public void flush() {
        try {
            prefs.flush();
        } catch (RuntimeException e) {
            log.error("Failed to save preferences: {}", e.getMessage(), e);
        }
    }
}

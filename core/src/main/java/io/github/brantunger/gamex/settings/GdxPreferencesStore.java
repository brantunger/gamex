package io.github.brantunger.gamex.settings;

import com.badlogic.gdx.Preferences;

/**
 * Adapts libGDX {@link Preferences} to {@link KeyValueStore}. A stored number that does not parse, as in a
 * hand-edited or damaged file, reads as the default.
 */
public final class GdxPreferencesStore implements KeyValueStore {

    private final Preferences prefs;

    /** @param prefs the backing libGDX preferences instance */
    public GdxPreferencesStore(Preferences prefs) {
        this.prefs = prefs;
    }

    @Override
    public float getFloat(String key, float defaultValue) {
        try {
            return prefs.getFloat(key, defaultValue);
        } catch (NumberFormatException e) {
            return defaultValue;
        }
    }

    @Override
    public int getInteger(String key, int defaultValue) {
        try {
            return prefs.getInteger(key, defaultValue);
        } catch (NumberFormatException e) {
            return defaultValue;
        }
    }

    @Override
    public boolean getBoolean(String key, boolean defaultValue) {
        return prefs.getBoolean(key, defaultValue);
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
        prefs.flush();
    }
}

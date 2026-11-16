package io.github.brantunger.gamex.settings;

import com.badlogic.gdx.Preferences;

/** Adapts libGDX {@link Preferences} to {@link KeyValueStore}. */
public final class GdxPreferencesStore implements KeyValueStore {

    private final Preferences prefs;

    /** @param prefs the backing libGDX preferences instance */
    public GdxPreferencesStore(Preferences prefs) {
        this.prefs = prefs;
    }

    @Override
    public float getFloat(String key, float defaultValue) {
        return prefs.getFloat(key, defaultValue);
    }

    @Override
    public int getInteger(String key, int defaultValue) {
        return prefs.getInteger(key, defaultValue);
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
    public void remove(String key) {
        prefs.remove(key);
    }

    @Override
    public void flush() {
        prefs.flush();
    }
}

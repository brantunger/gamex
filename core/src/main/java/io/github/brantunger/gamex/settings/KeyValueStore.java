package io.github.brantunger.gamex.settings;

/**
 * Minimal key-value persistence contract so settings and save data can be tested without a graphics/application
 * context and backed by libGDX {@code Preferences} at runtime.
 */
public interface KeyValueStore {

    float getFloat(String key, float defaultValue);

    int getInteger(String key, int defaultValue);

    boolean getBoolean(String key, boolean defaultValue);

    String getString(String key, String defaultValue);

    void putFloat(String key, float value);

    void putInteger(String key, int value);

    void putBoolean(String key, boolean value);

    void putString(String key, String value);

    void flush();
}

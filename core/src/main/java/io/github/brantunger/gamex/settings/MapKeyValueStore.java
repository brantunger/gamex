package io.github.brantunger.gamex.settings;

import java.util.HashMap;
import java.util.Map;

/** In-memory {@link KeyValueStore} used for headless tests and unattended demo runs. */
public final class MapKeyValueStore implements KeyValueStore {

    private final Map<String, Object> values = new HashMap<>();

    @Override
    public float getFloat(String key, float defaultValue) {
        Object v = values.get(key);
        return v instanceof Number ? ((Number) v).floatValue() : defaultValue;
    }

    @Override
    public int getInteger(String key, int defaultValue) {
        Object v = values.get(key);
        return v instanceof Number ? ((Number) v).intValue() : defaultValue;
    }

    @Override
    public boolean getBoolean(String key, boolean defaultValue) {
        Object v = values.get(key);
        return v instanceof Boolean ? (Boolean) v : defaultValue;
    }

    @Override
    public String getString(String key, String defaultValue) {
        Object v = values.get(key);
        return v instanceof String ? (String) v : defaultValue;
    }

    @Override
    public void putFloat(String key, float value) {
        values.put(key, value);
    }

    @Override
    public void putInteger(String key, int value) {
        values.put(key, value);
    }

    @Override
    public void putBoolean(String key, boolean value) {
        values.put(key, value);
    }

    @Override
    public void putString(String key, String value) {
        values.put(key, value);
    }

    @Override
    public void flush() {
        // Nothing to write: the values live only in memory.
    }
}

package io.github.brantunger.gamex.settings;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import ch.qos.logback.classic.Level;
import com.badlogic.gdx.Preferences;
import com.badlogic.gdx.utils.GdxRuntimeException;
import io.github.brantunger.gamex.LogCapture;
import org.junit.jupiter.api.Test;

import java.lang.reflect.Proxy;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

class GdxPreferencesStoreTest {

    private static final class PreferencesStub {
        final Map<String, String> raw = new HashMap<>();
        RuntimeException flushFailure;
        int flushCalls;
        final Preferences prefs;

        PreferencesStub() {
            prefs = (Preferences) Proxy.newProxyInstance(
                Preferences.class.getClassLoader(),
                new Class<?>[] {Preferences.class},
                (proxy, method, args) -> {
                    switch (method.getName()) {
                        case "putFloat":
                        case "putInteger":
                        case "putBoolean":
                            raw.put((String) args[0], String.valueOf(args[1]));
                            return proxy;
                        case "putString":
                            raw.put((String) args[0], (String) args[1]);
                            return proxy;
                        case "getFloat": {
                            String v = raw.get((String) args[0]);
                            return v == null ? (Float) args[1] : Float.parseFloat(v);
                        }
                        case "getInteger": {
                            String v = raw.get((String) args[0]);
                            return v == null ? (Integer) args[1] : Integer.parseInt(v);
                        }
                        case "getBoolean": {
                            String v = raw.get((String) args[0]);
                            return v == null ? (Boolean) args[1] : Boolean.parseBoolean(v);
                        }
                        case "getString": {
                            String v = raw.get((String) args[0]);
                            return v == null ? (String) args[1] : v;
                        }
                        case "flush":
                            flushCalls++;
                            if (flushFailure != null) throw flushFailure;
                            return null;
                        default:
                            return defaultValue(method.getReturnType());
                    }
                });
        }
    }

    private static Object defaultValue(Class<?> returnType) {
        if (!returnType.isPrimitive()) return null;
        if (returnType == boolean.class) return false;
        if (returnType == int.class) return 0;
        if (returnType == long.class) return 0L;
        if (returnType == float.class) return 0f;
        if (returnType == double.class) return 0d;
        if (returnType == short.class) return (short) 0;
        if (returnType == byte.class) return (byte) 0;
        if (returnType == char.class) return '\0';
        return null;
    }

    @Test
    void readsAndWritesTypedValuesAndFlushesUnderlyingPreferences() {
        PreferencesStub stub = new PreferencesStub();
        GdxPreferencesStore store = new GdxPreferencesStore(stub.prefs);

        assertEquals(0.25f, store.getFloat("missingFloat", 0.25f), 1e-6f);
        assertEquals(7, store.getInteger("missingInt", 7));
        assertTrue(store.getBoolean("missingBool", true));
        assertEquals("fallback", store.getString("missingStr", "fallback"));

        store.putFloat("fov", 95f);
        store.putInteger("stage", 4);
        store.putBoolean("fullscreen", true);
        store.putString("weapon", "RAIL_LANCE");
        store.flush();

        assertEquals(95f, store.getFloat("fov", 70f), 1e-6f);
        assertEquals(4, store.getInteger("stage", 0));
        assertTrue(store.getBoolean("fullscreen", false));
        assertEquals("RAIL_LANCE", store.getString("weapon", "PISTOL"));
        assertEquals(1, stub.flushCalls);
    }

    @Test
    void corruptedNumericPreferencesFallBackToDefaultsAndWarnOncePerKey() {
        PreferencesStub stub = new PreferencesStub();
        stub.raw.put("fov", "not-a-float");
        stub.raw.put("stage", "not-an-int");
        GdxPreferencesStore store = new GdxPreferencesStore(stub.prefs);

        try (LogCapture capture = LogCapture.of(GdxPreferencesStore.class, Level.WARN)) {
            assertEquals(70f, store.getFloat("fov", 70f), 1e-6f);
            assertEquals(70f, store.getFloat("fov", 70f), 1e-6f);
            assertEquals(2, store.getInteger("stage", 2));
            assertEquals(2, store.getInteger("stage", 2));
            assertEquals(List.of(
                "The stored value of 'fov' is not a number; using 70.0",
                "The stored value of 'stage' is not a number; using 2"),
                capture.messages(Level.WARN));
        }
    }

    @Test
    void flushSwallowsGdxRuntimeExceptionLogsErrorAndKeepsSessionValuesReadable() {
        PreferencesStub stub = new PreferencesStub();
        stub.flushFailure = new GdxRuntimeException("Error writing preferences: read-only .prefs");
        GdxPreferencesStore store = new GdxPreferencesStore(stub.prefs);

        GameSettings settings = new GameSettings();
        settings.setFov(100f);
        settings.setMasterVolume(0.4f);
        settings.setFullscreen(true);

        try (LogCapture capture = LogCapture.of(GdxPreferencesStore.class, Level.WARN)) {
            assertDoesNotThrow(() -> settings.save(store));
            assertEquals(1, stub.flushCalls);

            GameSettings reloadedInSession = GameSettings.load(store);
            assertEquals(100f, reloadedInSession.getFov(), 1e-6f);
            assertEquals(0.4f, reloadedInSession.getMasterVolume(), 1e-6f);
            assertTrue(reloadedInSession.isFullscreen());

            SaveData saveData = new SaveData(store);
            assertDoesNotThrow(() -> saveData.unlockStage(3));
            assertEquals(2, stub.flushCalls);
            assertEquals(3, saveData.getUnlockedStageIndex());
            assertFalse(saveData.hasSavedRun());

            assertEquals(List.of(
                "Failed to save preferences: Error writing preferences: read-only .prefs",
                "Failed to save preferences: Error writing preferences: read-only .prefs"),
                capture.messages(Level.ERROR));
        }
    }
}

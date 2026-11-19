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

    @Test
    void trimsSurroundingWhitespaceInHandEditedXmlPreferencesForFloatsIntegersAndBooleans() {
        PreferencesStub stub = new PreferencesStub();
        stub.raw.put("fov", "  95.5 \n");
        stub.raw.put("stage", "\t 3 \r\n");
        stub.raw.put("fullscreen", " true ");
        stub.raw.put("invertY", "\nTRUE\t");
        stub.raw.put("viewBob", " FALSE ");
        stub.raw.put("active", " false\n");
        stub.raw.put("blankFloat", "   ");
        stub.raw.put("blankInt", " \t ");
        stub.raw.put("blankBoolDefaultTrue", "   ");
        stub.raw.put("badBoolDefaultTrue", "yes");
        stub.raw.put("badBoolDefaultFalse", "1");
        GdxPreferencesStore store = new GdxPreferencesStore(stub.prefs);

        try (LogCapture capture = LogCapture.of(GdxPreferencesStore.class, Level.WARN)) {
            assertEquals(95.5f, store.getFloat("fov", 70f), 1e-6f);
            assertEquals(3, store.getInteger("stage", 0));
            assertTrue(store.getBoolean("fullscreen", false));
            assertTrue(store.getBoolean("invertY", false));
            assertFalse(store.getBoolean("viewBob", true));
            assertFalse(store.getBoolean("active", true));
            assertTrue(store.getBoolean("blankBoolDefaultTrue", true));
            assertTrue(store.getBoolean("badBoolDefaultTrue", true));
            assertFalse(store.getBoolean("badBoolDefaultFalse", false));
            assertEquals(List.of(), capture.messages(Level.WARN), "padded valid entries must not log warnings");

            assertEquals(70f, store.getFloat("blankFloat", 70f), 1e-6f);
            assertEquals(70f, store.getFloat("blankFloat", 70f), 1e-6f);
            assertEquals(5, store.getInteger("blankInt", 5));
            assertEquals(5, store.getInteger("blankInt", 5));
            assertEquals(List.of(
                "The stored value of 'blankFloat' is not a number; using 70.0",
                "The stored value of 'blankInt' is not a number; using 5"),
                capture.messages(Level.WARN));
        }
    }

    @Test
    void typedOnlyPreferencesStubWithoutGetStringStillDelegatesToTypedGetters() {
        Preferences typedOnly = (Preferences) Proxy.newProxyInstance(
            Preferences.class.getClassLoader(),
            new Class<?>[] {Preferences.class},
            (proxy, method, args) -> {
                String key = args != null && args.length > 0 && args[0] instanceof String ? (String) args[0] : "";
                switch (method.getName()) {
                    case "getFloat":
                        if ("badFloat".equals(key)) throw new NumberFormatException("bad float");
                        return "fov".equals(key) ? 88f : (Float) args[1];
                    case "getInteger":
                        if ("badInt".equals(key)) throw new NumberFormatException("bad int");
                        return "stage".equals(key) ? 4 : (Integer) args[1];
                    case "getBoolean":
                        return "fullscreen".equals(key) ? Boolean.TRUE : (Boolean) args[1];
                    default:
                        return defaultValue(method.getReturnType());
                }
            });
        GdxPreferencesStore store = new GdxPreferencesStore(typedOnly);

        try (LogCapture capture = LogCapture.of(GdxPreferencesStore.class, Level.WARN)) {
            assertEquals(88f, store.getFloat("fov", 70f), 1e-6f);
            assertEquals(4, store.getInteger("stage", 0));
            assertTrue(store.getBoolean("fullscreen", false));
            assertEquals(70f, store.getFloat("badFloat", 70f), 1e-6f);
            assertEquals(70f, store.getFloat("badFloat", 70f), 1e-6f);
            assertEquals(2, store.getInteger("badInt", 2));
            assertEquals(List.of(
                "The stored value of 'badFloat' is not a number; using 70.0",
                "The stored value of 'badInt' is not a number; using 2"),
                capture.messages(Level.WARN));
        }
    }
}

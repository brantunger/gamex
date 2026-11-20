package io.github.brantunger.gamex.settings;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import io.github.brantunger.gamex.TestTuning;
import io.github.brantunger.gamex.player.Difficulty;
import io.github.brantunger.gamex.player.PlayerLoadout;
import io.github.brantunger.gamex.weapon.WeaponSlot;
import io.github.brantunger.gamex.world.Levels;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicReference;

class AsyncKeyValueStoreTest {

    @Test
    void putAndGetStayNonBlockingWhileFlushRunsOnBackgroundThread() throws Exception {
        MapKeyValueStore delegate = new MapKeyValueStore();
        CountDownLatch flushStarted = new CountDownLatch(1);
        CountDownLatch allowFlushFinish = new CountDownLatch(1);
        AtomicReference<String> flushThreadName = new AtomicReference<>("");

        KeyValueStore blockingDelegate = new KeyValueStore() {
            @Override
            public float getFloat(String key, float defaultValue) {
                return delegate.getFloat(key, defaultValue);
            }

            @Override
            public int getInteger(String key, int defaultValue) {
                return delegate.getInteger(key, defaultValue);
            }

            @Override
            public boolean getBoolean(String key, boolean defaultValue) {
                return delegate.getBoolean(key, defaultValue);
            }

            @Override
            public String getString(String key, String defaultValue) {
                return delegate.getString(key, defaultValue);
            }

            @Override
            public void putFloat(String key, float value) {
                delegate.putFloat(key, value);
            }

            @Override
            public void putInteger(String key, int value) {
                delegate.putInteger(key, value);
            }

            @Override
            public void putBoolean(String key, boolean value) {
                delegate.putBoolean(key, value);
            }

            @Override
            public void putString(String key, String value) {
                delegate.putString(key, value);
            }

            @Override
            public void flush() {
                flushThreadName.compareAndSet("", Thread.currentThread().getName());
                flushStarted.countDown();
                try {
                    assertTrue(allowFlushFinish.await(5, TimeUnit.SECONDS), "flush gate timed out");
                } catch (InterruptedException e) {
                    Thread.currentThread().interrupt();
                }
                delegate.flush();
            }
        };

        AsyncKeyValueStore store = new AsyncKeyValueStore(blockingDelegate);
        try {
            store.putFloat("fov", 92f);
            store.putInteger("stage", 3);
            store.putBoolean("minimap", true);
            store.putString("weapon", "rail");
            store.flush();

            assertTrue(flushStarted.await(5, TimeUnit.SECONDS), "background flush did not start");
            assertTrue(flushThreadName.get().startsWith("gamex-io"),
                "expected gamex-io daemon thread, got: " + flushThreadName.get());

            // Cache hits and new writes on the main thread must proceed immediately even while the background
            // delegate flush is blocked holding delegateLock.
            assertEquals(92f, store.getFloat("fov", 0f), 1e-4f);
            assertEquals(3, store.getInteger("stage", 0));
            assertTrue(store.getBoolean("minimap", false));
            assertEquals("rail", store.getString("weapon", ""));

            store.putFloat("fov", 104f);
            store.putString("weapon", "scattergun");
            assertEquals(104f, store.getFloat("fov", 0f), 1e-4f);
            assertEquals("scattergun", store.getString("weapon", ""));
        } finally {
            allowFlushFinish.countDown();
            store.dispose();
        }

        assertEquals(104f, delegate.getFloat("fov", 0f), 1e-4f);
        assertEquals(3, delegate.getInteger("stage", 0));
        assertTrue(delegate.getBoolean("minimap", false));
        assertEquals("scattergun", delegate.getString("weapon", ""));
    }

    @Test
    void sequentialFlushesPreserveWriteOrderAndDrainCleanlyOnDispose() {
        List<String> writeLog = Collections.synchronizedList(new ArrayList<>());
        MapKeyValueStore backing = new MapKeyValueStore();
        KeyValueStore loggingDelegate = new KeyValueStore() {
            @Override
            public float getFloat(String key, float defaultValue) {
                return backing.getFloat(key, defaultValue);
            }

            @Override
            public int getInteger(String key, int defaultValue) {
                return backing.getInteger(key, defaultValue);
            }

            @Override
            public boolean getBoolean(String key, boolean defaultValue) {
                return backing.getBoolean(key, defaultValue);
            }

            @Override
            public String getString(String key, String defaultValue) {
                return backing.getString(key, defaultValue);
            }

            @Override
            public void putFloat(String key, float value) {
                writeLog.add(key + "=" + value);
                backing.putFloat(key, value);
            }

            @Override
            public void putInteger(String key, int value) {
                writeLog.add(key + "=" + value);
                backing.putInteger(key, value);
            }

            @Override
            public void putBoolean(String key, boolean value) {
                writeLog.add(key + "=" + value);
                backing.putBoolean(key, value);
            }

            @Override
            public void putString(String key, String value) {
                writeLog.add(key + "=" + value);
                backing.putString(key, value);
            }

            @Override
            public void flush() {
                writeLog.add("flush");
                backing.flush();
            }
        };

        ExecutorService sharedExecutor = AsyncKeyValueStore.newIoExecutor();
        try {
            AsyncKeyValueStore store = new AsyncKeyValueStore(loggingDelegate, sharedExecutor, false);
            for (int i = 1; i <= 5; i++) {
                store.putInteger("counter", i);
                store.flush();
            }
            // Buffer a final unflushed update right before dispose() to ensure remaining dirty keys drain too.
            store.putInteger("counter", 99);
            store.putString("status", "complete");
            store.dispose();

            assertFalse(sharedExecutor.isShutdown(), "ownsExecutor=false must leave caller's executor running");
            assertEquals(99, backing.getInteger("counter", -1));
            assertEquals("complete", backing.getString("status", ""));
            assertEquals("flush", writeLog.get(writeLog.size() - 1));
        } finally {
            sharedExecutor.shutdownNow();
        }
    }

    @Test
    void writesAfterDisposeFallBackSynchronouslyWithoutThrowing() {
        MapKeyValueStore delegate = new MapKeyValueStore();
        AsyncKeyValueStore store = new AsyncKeyValueStore(delegate);
        store.putInteger("initial", 1);
        store.dispose();

        assertEquals(1, delegate.getInteger("initial", 0));

        store.putFloat("lateFloat", 2.5f);
        store.putInteger("lateInt", 42);
        store.putBoolean("lateBool", true);
        store.putString("lateString", "saved-synchronously");
        store.flush();

        assertEquals(2.5f, delegate.getFloat("lateFloat", 0f), 1e-4f);
        assertEquals(42, delegate.getInteger("lateInt", 0));
        assertTrue(delegate.getBoolean("lateBool", false));
        assertEquals("saved-synchronously", delegate.getString("lateString", ""));
    }

    @Test
    void failedBackgroundFlushIsIsolatedSoLaterFlushesSucceed() {
        MapKeyValueStore delegate = new MapKeyValueStore();
        AtomicBoolean failNextFlush = new AtomicBoolean(true);
        AtomicInteger flushCalls = new AtomicInteger();

        KeyValueStore flakyDelegate = new KeyValueStore() {
            @Override
            public float getFloat(String key, float defaultValue) {
                return delegate.getFloat(key, defaultValue);
            }

            @Override
            public int getInteger(String key, int defaultValue) {
                return delegate.getInteger(key, defaultValue);
            }

            @Override
            public boolean getBoolean(String key, boolean defaultValue) {
                return delegate.getBoolean(key, defaultValue);
            }

            @Override
            public String getString(String key, String defaultValue) {
                return delegate.getString(key, defaultValue);
            }

            @Override
            public void putFloat(String key, float value) {
                delegate.putFloat(key, value);
            }

            @Override
            public void putInteger(String key, int value) {
                delegate.putInteger(key, value);
            }

            @Override
            public void putBoolean(String key, boolean value) {
                delegate.putBoolean(key, value);
            }

            @Override
            public void putString(String key, String value) {
                delegate.putString(key, value);
            }

            @Override
            public void flush() {
                flushCalls.incrementAndGet();
                if (failNextFlush.getAndSet(false)) {
                    throw new IllegalStateException("Simulated disk flush failure");
                }
                delegate.flush();
            }
        };

        AsyncKeyValueStore store = new AsyncKeyValueStore(flakyDelegate);
        try {
            store.putInteger("first", 10);
            store.flush();
            store.awaitPending();

            store.putInteger("second", 20);
            store.flush();
            store.awaitPending();
        } finally {
            store.dispose();
        }

        assertEquals(2, flushCalls.get());
        assertEquals(10, delegate.getInteger("first", 0));
        assertEquals(20, delegate.getInteger("second", 0));
    }

    @Test
    void worksEndToEndWithGameSettingsAndSaveData() {
        MapKeyValueStore backingSettings = new MapKeyValueStore();
        MapKeyValueStore backingSave = new MapKeyValueStore();
        SettingsDefaults defaults = TestTuning.SETTINGS;

        ExecutorService ioExecutor = AsyncKeyValueStore.newIoExecutor();
        try {
            AsyncKeyValueStore settingsStore = new AsyncKeyValueStore(backingSettings, ioExecutor, false);
            AsyncKeyValueStore saveStore = new AsyncKeyValueStore(backingSave, ioExecutor, false);

            GameSettings settings = GameSettings.load(settingsStore, defaults);
            settings.setMouseSensitivity(0.24f);
            settings.setFov(100f);
            settings.setMasterVolume(0.65f);
            settings.setInvertY(true);
            settings.setViewBob(false);
            settings.setFullscreen(true);
            settings.setMinimap(false);
            settings.save(settingsStore);

            SaveData save = new SaveData(saveStore);
            save.saveCampaignRun(2, new PlayerLoadout(88f, 45f, Difficulty.HARD, true, 12, true, 6,
                WeaponSlot.RAIL_LANCE));
            save.recordCompletion("01_outer_breach", Difficulty.HARD, 44.5f, 40f, 9, 10, 20f);

            settingsStore.dispose();
            saveStore.dispose();
        } finally {
            ioExecutor.shutdownNow();
        }

        GameSettings reloadedSettings = GameSettings.load(backingSettings, defaults);
        assertEquals(0.24f, reloadedSettings.getMouseSensitivity(), 1e-4f);
        assertEquals(100f, reloadedSettings.getFov(), 1e-4f);
        assertEquals(0.65f, reloadedSettings.getMasterVolume(), 1e-4f);
        assertTrue(reloadedSettings.isInvertY());
        assertFalse(reloadedSettings.isViewBob());
        assertTrue(reloadedSettings.isFullscreen());
        assertFalse(reloadedSettings.isMinimap());

        SaveData reloadedSave = new SaveData(backingSave);
        assertTrue(reloadedSave.hasSavedRun());
        assertEquals(88f, reloadedSave.getSavedLoadout().health, 1e-4f);
        assertEquals(WeaponSlot.RAIL_LANCE, reloadedSave.getSavedLoadout().activeWeapon);
        assertEquals(44.5f, reloadedSave.getBestTime(Levels.stage("01_outer_breach").id, Difficulty.HARD), 1e-4f);
    }
}

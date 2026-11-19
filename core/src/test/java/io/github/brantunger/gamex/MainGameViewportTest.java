package io.github.brantunger.gamex;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotSame;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertTrue;

import ch.qos.logback.classic.Level;
import com.badlogic.gdx.Application;
import com.badlogic.gdx.Gdx;
import com.badlogic.gdx.Graphics;
import com.badlogic.gdx.graphics.GL20;
import com.badlogic.gdx.utils.Disposable;
import com.badlogic.gdx.utils.GdxRuntimeException;
import io.github.brantunger.gamex.audio.SoundManager;
import io.github.brantunger.gamex.hud.MenuState;
import io.github.brantunger.gamex.player.PlayerInput;
import io.github.brantunger.gamex.settings.GameSettings;
import io.github.brantunger.gamex.settings.KeyValueStore;
import io.github.brantunger.gamex.settings.MapKeyValueStore;
import io.github.brantunger.gamex.settings.SaveData;
import io.github.brantunger.gamex.world.Levels;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.lang.reflect.Proxy;
import java.util.ArrayList;
import java.util.List;

class MainGameViewportTest {

    private Application previousApp;
    private Graphics previousGraphics;
    private GL20 previousGl;
    private GL20 previousGl20;

    @BeforeEach
    void saveGdxState() {
        previousApp = Gdx.app;
        previousGraphics = Gdx.graphics;
        previousGl = Gdx.gl;
        previousGl20 = Gdx.gl20;
    }

    @AfterEach
    void restoreGdxState() {
        Gdx.app = previousApp;
        Gdx.graphics = previousGraphics;
        Gdx.gl = previousGl;
        Gdx.gl20 = previousGl20;
    }

    private static Graphics graphicsStub(int logicalWidth, int logicalHeight, int backBufferWidth,
        int backBufferHeight, boolean fullscreen) {
        return (Graphics) Proxy.newProxyInstance(
            Graphics.class.getClassLoader(),
            new Class<?>[] {Graphics.class},
            (proxy, method, args) -> {
                switch (method.getName()) {
                    case "getWidth":
                        return logicalWidth;
                    case "getHeight":
                        return logicalHeight;
                    case "getBackBufferWidth":
                        return backBufferWidth;
                    case "getBackBufferHeight":
                        return backBufferHeight;
                    case "isFullscreen":
                        return fullscreen;
                    default:
                        return defaultValue(method.getReturnType());
                }
            });
    }

    /** A stub window that records each switch to fullscreen or windowed mode and then reports the new mode. */
    private static final class DisplayRecorder {
        final List<String> switches = new ArrayList<>();
        boolean fullscreen;
        final Graphics graphics;

        DisplayRecorder(boolean startsFullscreen) {
            fullscreen = startsFullscreen;
            Graphics.DisplayMode desktopMode = new Graphics.DisplayMode(1920, 1080, 60, 32) {};
            graphics = (Graphics) Proxy.newProxyInstance(
                Graphics.class.getClassLoader(),
                new Class<?>[] {Graphics.class},
                (proxy, method, args) -> {
                    switch (method.getName()) {
                        case "getWidth":
                        case "getBackBufferWidth":
                            return fullscreen ? 1920 : 800;
                        case "getHeight":
                        case "getBackBufferHeight":
                            return fullscreen ? 1080 : 600;
                        case "isFullscreen":
                            return fullscreen;
                        case "getDisplayMode":
                            return desktopMode;
                        case "setFullscreenMode":
                            switches.add("fullscreen");
                            fullscreen = true;
                            return true;
                        case "setWindowedMode":
                            switches.add("windowed " + args[0] + "x" + args[1]);
                            fullscreen = false;
                            return true;
                        default:
                            return defaultValue(method.getReturnType());
                    }
                });
        }
    }

    private static GameSettings savedSettings(boolean fullscreen) {
        MapKeyValueStore store = new MapKeyValueStore();
        GameSettings settings = new GameSettings();
        settings.setFullscreen(fullscreen);
        settings.save(store);
        return GameSettings.load(store);
    }

    private static final class ViewportRecorder {
        int calls;
        int[] lastArgs;
        final GL20 gl;

        ViewportRecorder() {
            gl = (GL20) Proxy.newProxyInstance(
                GL20.class.getClassLoader(),
                new Class<?>[] {GL20.class},
                (proxy, method, args) -> {
                    if ("glViewport".equals(method.getName()) && args != null && args.length == 4) {
                        calls++;
                        lastArgs = new int[] {(int) args[0], (int) args[1], (int) args[2], (int) args[3]};
                        return null;
                    }
                    return defaultValue(method.getReturnType());
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
    void resizeUpdatesGlViewportToFullBackBufferOnLargeFullscreenResolution() {
        ViewportRecorder recorder = new ViewportRecorder();
        Gdx.graphics = graphicsStub(3840, 2160, 3840, 2160, true);
        Gdx.gl = recorder.gl;
        Gdx.gl20 = recorder.gl;

        MainGame game = new MainGame(1280, 720);
        game.resize(3840, 2160);

        assertEquals(1, recorder.calls);
        assertArrayEquals(new int[] {0, 0, 3840, 2160}, recorder.lastArgs);
        assertEquals(1280, game.getWindowedWidth());
        assertEquals(720, game.getWindowedHeight());
    }

    @Test
    void resizeUsesPhysicalBackBufferPixelsOnHiDpiDisplay() {
        ViewportRecorder recorder = new ViewportRecorder();
        Gdx.graphics = graphicsStub(1920, 1080, 3840, 2160, true);
        Gdx.gl = recorder.gl;
        Gdx.gl20 = recorder.gl;

        MainGame game = new MainGame(1280, 720);
        game.syncDisplayViewport();

        assertEquals(1, recorder.calls);
        assertArrayEquals(new int[] {0, 0, 3840, 2160}, recorder.lastArgs);
    }

    @Test
    void windowedResizeUpdatesWindowedFallbackAndIgnoresMinimizedZeroSize() {
        ViewportRecorder recorder = new ViewportRecorder();
        Gdx.graphics = graphicsStub(1600, 900, 1600, 900, false);
        Gdx.gl = recorder.gl;
        Gdx.gl20 = recorder.gl;

        MainGame game = new MainGame();
        game.resize(1600, 900);

        assertEquals(1, recorder.calls);
        assertArrayEquals(new int[] {0, 0, 1600, 900}, recorder.lastArgs);
        assertEquals(1600, game.getWindowedWidth());
        assertEquals(900, game.getWindowedHeight());

        game.resize(0, 0);
        assertEquals(1, recorder.calls, "minimized zero size must not clobber glViewport");
        assertEquals(1600, game.getWindowedWidth());
        assertEquals(900, game.getWindowedHeight());
    }

    @Test
    void disposeBeforeCreateFinishedDoesNotThrow() {
        MainGame game = new MainGame();

        assertDoesNotThrow(game::dispose, "a failed create() must not be masked by a NullPointerException");
    }

    @Test
    void forcedWindowedStartIsNotUndoneByASavedFullscreenSetting() {
        DisplayRecorder display = new DisplayRecorder(false);
        Gdx.graphics = display.graphics;
        GameSettings settings = savedSettings(true);

        MainGame game = new MainGame(800, 600, true);
        game.applyStartupDisplayMode(settings);

        assertEquals(List.of(), display.switches, "GAMEX_WINDOW keeps the window it asked for");
        assertFalse(display.fullscreen);
        assertTrue(settings.isFullscreen(), "the saved fullscreen setting is not changed");
        assertEquals(800, game.getWindowedWidth());
        assertEquals(600, game.getWindowedHeight());
    }

    @Test
    void unforcedStartStillSwitchesToTheSavedDisplayMode() {
        DisplayRecorder windowed = new DisplayRecorder(false);
        Gdx.graphics = windowed.graphics;
        MainGame game = new MainGame(1280, 720);
        game.applyStartupDisplayMode(savedSettings(true));
        assertEquals(List.of("fullscreen"), windowed.switches, "a saved fullscreen setting goes fullscreen");
        assertEquals(800, game.getWindowedWidth(), "the windowed size to go back to is the starting window's");
        assertEquals(600, game.getWindowedHeight());

        DisplayRecorder fullscreen = new DisplayRecorder(true);
        Gdx.graphics = fullscreen.graphics;
        new MainGame(1280, 720).applyStartupDisplayMode(savedSettings(false));
        assertEquals(List.of("windowed 1280x720"), fullscreen.switches, "a saved windowed setting leaves fullscreen");

        DisplayRecorder matching = new DisplayRecorder(false);
        Gdx.graphics = matching.graphics;
        new MainGame(1280, 720).applyStartupDisplayMode(savedSettings(false));
        assertEquals(List.of(), matching.switches, "no switch when the window already matches the setting");
    }

    @Test
    void settingsOpenedFromTheTitleKeepsTheTitleBackdrop() {
        MenuState menu = new MenuState(new GameSettings(), new SaveData(new MapKeyValueStore()),
            MenuState.Screen.TITLE);
        assertTrue(MainGame.showsBackdrop(menu), "Title");
        menu.setSelectedIndex(menu.getOptions().indexOf("SETTINGS"));
        menu.confirm();
        assertEquals(MenuState.Screen.SETTINGS, menu.getScreen());

        assertTrue(MainGame.showsBackdrop(menu), "Settings from the Title");
    }

    @Test
    void settingsOpenedFromPauseKeepsTheFirstPersonView() {
        MenuState menu = new MenuState(new GameSettings(), new SaveData(new MapKeyValueStore()),
            MenuState.Screen.PLAYING);
        assertFalse(MainGame.showsBackdrop(menu), "in play");
        menu.escape();
        assertFalse(MainGame.showsBackdrop(menu), "Pause");
        menu.setSelectedIndex(menu.getOptions().indexOf("SETTINGS"));
        menu.confirm();
        assertEquals(MenuState.Screen.SETTINGS, menu.getScreen());

        assertFalse(MainGame.showsBackdrop(menu), "Settings from Pause");
    }

    @Test
    void backdropShowsOnTheMenuScreensBeforeARunOnly() {
        for (MenuState.Screen screen : MenuState.Screen.values()) {
            if (screen == MenuState.Screen.SETTINGS) continue;
            boolean expected = screen == MenuState.Screen.TITLE || screen == MenuState.Screen.STAGE_SELECT
                || screen == MenuState.Screen.DIFFICULTY_SELECT || screen == MenuState.Screen.BRIEFING;
            MenuState menu = new MenuState(new GameSettings(), new SaveData(new MapKeyValueStore()), screen);
            assertEquals(expected, MainGame.showsBackdrop(menu), screen.name());
        }
    }

    @Test
    void minimizedZeroStartupPreservesWindowedFallbackDimensions() {
        Gdx.graphics = graphicsStub(0, 0, 0, 0, false);

        MainGame defaultGame = new MainGame();
        defaultGame.applyStartupDisplayMode(savedSettings(false));
        assertEquals(1280, defaultGame.getWindowedWidth(), "0x0 minimized startup must not clobber width to 160");
        assertEquals(720, defaultGame.getWindowedHeight(), "0x0 minimized startup must not clobber height to 120");

        MainGame customGame = new MainGame(960, 540, true);
        customGame.applyStartupDisplayMode(savedSettings(true));
        assertEquals(960, customGame.getWindowedWidth());
        assertEquals(540, customGame.getWindowedHeight());
    }

    @Test
    void tinyWindowedResizeAndStartupEnforceMinimum160x120Floor() {
        ViewportRecorder recorder = new ViewportRecorder();
        Gdx.graphics = graphicsStub(80, 60, 80, 60, false);
        Gdx.gl = recorder.gl;
        Gdx.gl20 = recorder.gl;

        MainGame game = new MainGame(1280, 720);
        game.applyStartupDisplayMode(savedSettings(false));
        assertEquals(160, game.getWindowedWidth());
        assertEquals(120, game.getWindowedHeight());

        game.resize(1600, 900);
        assertEquals(1600, game.getWindowedWidth());
        assertEquals(900, game.getWindowedHeight());

        game.resize(100, 75);
        assertEquals(160, game.getWindowedWidth(), "tiny windowed width clamps to 160");
        assertEquals(120, game.getWindowedHeight(), "tiny windowed height clamps to 120");

        game.resize(-20, 400);
        game.resize(400, 0);
        assertEquals(160, game.getWindowedWidth());
        assertEquals(120, game.getWindowedHeight());
    }

    @Test
    void backdropSimulationReplacesPlayedOrMutatedGameplaySimulationOnSameStage() {
        MenuState menu = new MenuState(new GameSettings(), new SaveData(new MapKeyValueStore()),
            MenuState.Screen.TITLE);
        Levels.Stage outerBreach = menu.getActiveStage();
        assertEquals("01_outer_breach", outerBreach.id);

        GameSimulation cleanPreview = new GameSimulation(outerBreach.map);
        assertSame(cleanPreview, MainGame.backdropSimulation(cleanPreview, menu, false),
            "clean unplayed preview of active stage is reused without reallocating");

        // Even if map matches, a simulation flagged as played replaces the instance.
        GameSimulation afterFlaggedPlay = MainGame.backdropSimulation(cleanPreview, menu, true);
        assertNotSame(cleanPreview, afterFlaggedPlay);
        assertEquals(0f, afterFlaggedPlay.getTime(), 1e-6f);

        // Stepped simulation on the same map (time > 0, fired shots, etc.) is replaced with a clean preview.
        GameSimulation mutated = new GameSimulation(outerBreach.map);
        PlayerInput fireInput = new PlayerInput();
        fireInput.fire = true;
        mutated.update(1f / 60f, fireInput);
        assertTrue(mutated.getTime() > 0f);
        assertTrue(mutated.getShotsFired() > 0);

        GameSimulation rebuilt = MainGame.backdropSimulation(mutated, menu, false);
        assertNotSame(mutated, rebuilt, "mutated gameplay simulation must not leak into menu backdrop");
        assertSame(outerBreach.map, rebuilt.map);
        assertEquals(0f, rebuilt.getTime(), 1e-6f);
        assertEquals(0, rebuilt.getShotsFired());
    }

    private static final class FailingKeyValueStore implements KeyValueStore {
        private final MapKeyValueStore delegate = new MapKeyValueStore();
        private final RuntimeException flushFailure;
        int flushCalls;

        FailingKeyValueStore(RuntimeException flushFailure) {
            this.flushFailure = flushFailure;
        }

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
            flushCalls++;
            if (flushFailure != null) throw flushFailure;
        }
    }

    private static Disposable recordingDisposable(String name, List<String> log, RuntimeException failure) {
        return () -> {
            log.add(name);
            if (failure != null) throw failure;
        };
    }

    @Test
    void disposeContinuesFreeingAllResourcesWhenSettingsFlushOrEarlierDisposablesThrow() {
        Gdx.app = null;
        FailingKeyValueStore failingStore = new FailingKeyValueStore(
            new GdxRuntimeException("Read-only preferences file"));
        GameSettings settings = new GameSettings();
        settings.setFov(95f);

        List<String> disposed = new ArrayList<>();
        Disposable sound = recordingDisposable("soundManager", disposed,
            new GdxRuntimeException("OpenAL context lost"));
        Disposable world = recordingDisposable("worldRenderer", disposed, null);
        Disposable viewmodel = recordingDisposable("viewmodelRenderer", disposed, null);
        Disposable hud = recordingDisposable("hudRenderer", disposed,
            new IllegalStateException("Shader already freed"));
        Disposable textures = recordingDisposable("textures", disposed, null);

        try (LogCapture capture = LogCapture.of(MainGame.class, Level.INFO)) {
            assertDoesNotThrow(() -> MainGame.disposeResources(
                settings, failingStore, false, sound, world, viewmodel, hud, textures));

            assertEquals(1, failingStore.flushCalls);
            assertEquals(95f, failingStore.getFloat("fov", 70f), 1e-6f);
            assertEquals(
                List.of("soundManager", "worldRenderer", "viewmodelRenderer", "hudRenderer", "textures"),
                disposed,
                "every OpenAL and OpenGL subsystem must still be disposed in order");
            assertEquals(List.of(
                "Failed to save settings: Read-only preferences file",
                "Failed to dispose resource: OpenAL context lost",
                "Failed to dispose resource: Shader already freed"),
                capture.messages(Level.ERROR));

            // Partial create() failure with null entries still disposes every non-null resource.
            disposed.clear();
            assertDoesNotThrow(() -> MainGame.disposeResources(
                settings, failingStore, false, sound, null, viewmodel, null, textures));
            assertEquals(List.of("soundManager", "viewmodelRenderer", "textures"), disposed);
        }
    }

    @Test
    void gameDisposeCatchesSettingsSaveFailureAndSkipsSaveOnUnattendedDemoRun() {
        Gdx.app = null;
        FailingKeyValueStore failingStore = new FailingKeyValueStore(new GdxRuntimeException("Disk full"));
        MainGame game = new MainGame();
        game.settings = new GameSettings();
        game.settingsStore = failingStore;
        game.soundManager = new SoundManager(true);

        try (LogCapture capture = LogCapture.of(MainGame.class, Level.INFO)) {
            assertDoesNotThrow(game::dispose);
            assertEquals(1, failingStore.flushCalls);
            assertEquals(List.of("Shutting down"), capture.messages(Level.INFO));
            assertEquals(List.of("Failed to save settings: Disk full"), capture.messages(Level.ERROR));

            List<String> demoDisposed = new ArrayList<>();
            Disposable demoSound = recordingDisposable("soundManager", demoDisposed, null);
            assertDoesNotThrow(() -> MainGame.disposeResources(game.settings, failingStore, true, demoSound));
            assertEquals(1, failingStore.flushCalls, "unattended demo run skips saving settings");
            assertEquals(List.of("soundManager"), demoDisposed);
        }
    }
}

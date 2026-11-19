package io.github.brantunger.gamex.audio;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import ch.qos.logback.classic.Level;
import com.badlogic.gdx.Audio;
import com.badlogic.gdx.Files;
import com.badlogic.gdx.Gdx;
import com.badlogic.gdx.audio.Sound;
import com.badlogic.gdx.files.FileHandle;
import com.badlogic.gdx.utils.GdxRuntimeException;
import io.github.brantunger.gamex.GameSimulation;
import io.github.brantunger.gamex.LogCapture;
import io.github.brantunger.gamex.hud.MenuState;
import io.github.brantunger.gamex.player.PlayerInput;
import io.github.brantunger.gamex.settings.GameSettings;
import io.github.brantunger.gamex.settings.MapKeyValueStore;
import io.github.brantunger.gamex.settings.SaveData;
import io.github.brantunger.gamex.world.Levels;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.lang.reflect.Proxy;
import java.nio.charset.StandardCharsets;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

/** Checks that {@link SoundManager} keeps every cue that loads when one sound file is missing or corrupt. */
class SoundManagerPartialLoadTest {

    private Audio previousAudio;
    private Files previousFiles;
    /** Asset paths whose load fails. */
    private final Set<String> failing = new HashSet<>();
    /** Calls per asset path, as {@code path#method}. */
    private final Map<String, Integer> calls = new HashMap<>();

    @BeforeEach
    void installStubs() {
        previousAudio = Gdx.audio;
        previousFiles = Gdx.files;
        Gdx.files = (Files) Proxy.newProxyInstance(
            Files.class.getClassLoader(),
            new Class<?>[] {Files.class},
            (proxy, method, args) -> {
                if ("internal".equals(method.getName())) return assetHandle((String) args[0]);
                throw new UnsupportedOperationException(method.getName());
            });
        Gdx.audio = (Audio) Proxy.newProxyInstance(
            Audio.class.getClassLoader(),
            new Class<?>[] {Audio.class},
            (proxy, method, args) -> {
                if (!"newSound".equals(method.getName())) throw new UnsupportedOperationException(method.getName());
                String path = ((FileHandle) args[0]).path();
                if (failing.contains(path)) throw new GdxRuntimeException("Error reading WAV file: " + path);
                return soundStub(path);
            });
    }

    @AfterEach
    void restoreGdxState() {
        Gdx.audio = previousAudio;
        Gdx.files = previousFiles;
    }

    private static Path resolveAssetsDir() {
        List<Path> candidates = new ArrayList<>();
        String assets = System.getProperty("gamex.assets");
        if (assets != null) candidates.add(Paths.get(assets));
        candidates.add(Paths.get("..", "assets"));
        candidates.add(Paths.get("assets"));
        for (Path candidate : candidates) {
            if (java.nio.file.Files.isDirectory(candidate.resolve(Levels.LEVELS_DIR))) {
                return candidate;
            }
        }
        List<Path> tried = new ArrayList<>();
        for (Path candidate : candidates) tried.add(candidate.toAbsolutePath().normalize());
        throw new IllegalStateException("The levels folder was not found. Set the system property gamex.assets to "
            + "the assets folder, or run from the root project or the core project. Tried: " + tried);
    }

    /** @return a handle that keeps the asset path the sound stubs key on and reads its text from the assets folder */
    private static FileHandle assetHandle(String path) {
        return new FileHandle(path) {
            @Override
            public String readString(String charset) {
                try {
                    return java.nio.file.Files.readString(resolveAssetsDir().resolve(path), StandardCharsets.UTF_8);
                } catch (IOException e) {
                    throw new UncheckedIOException(e);
                }
            }
        };
    }

    private Sound soundStub(String path) {
        return (Sound) Proxy.newProxyInstance(
            Sound.class.getClassLoader(),
            new Class<?>[] {Sound.class},
            (proxy, method, args) -> {
                calls.merge(path + "#" + method.getName(), 1, Integer::sum);
                return method.getReturnType() == long.class ? (Object) 7L : null;
            });
    }

    private int calls(String path, String method) {
        return calls.getOrDefault(path + "#" + method, 0);
    }

    private int totalCalls(String method) {
        int total = 0;
        for (Map.Entry<String, Integer> entry : calls.entrySet()) {
            if (entry.getKey().endsWith("#" + method)) total += entry.getValue();
        }
        return total;
    }

    /** @return a Title menu holding one MENU_MOVE and one MENU_CONFIRM event */
    private static MenuState menuWithMoveAndConfirm(GameSettings settings) {
        MenuState menu = new MenuState(settings, new SaveData(new MapKeyValueStore()), MenuState.Screen.TITLE);
        menu.moveSelection(1);
        menu.confirm();
        return menu;
    }

    /** @return a simulation in which the blast is charging, so the charge loop should sound */
    private static GameSimulation chargingSimulation() {
        GameSimulation simulation = new GameSimulation(Levels.first());
        PlayerInput input = new PlayerInput();
        input.charge = true;
        for (int i = 0; i < 6; i++) simulation.update(1f / 60f, input);
        assertTrue(simulation.isBlastChargingAudioActive(), "the blast is charging");
        return simulation;
    }

    @Test
    void oneFailedCueIsSkippedAndTheOthersStillPlay() {
        String moveCue = SoundManager.assetPath(SoundEvent.Type.MENU_MOVE);
        String confirmCue = SoundManager.assetPath(SoundEvent.Type.MENU_CONFIRM);
        failing.add(moveCue);
        GameSettings settings = new GameSettings();

        SoundManager manager = new SoundManager(false);
        assertEquals(0, totalCalls("dispose"), "a failed cue disposes none of the loaded sounds");
        manager.update(null, menuWithMoveAndConfirm(settings), settings);

        assertEquals(1, calls(confirmCue, "play"), "the cue that loaded plays");
        assertEquals(1, totalCalls("play"), "the failed cue plays nothing and breaks nothing");
        manager.dispose();
    }

    @Test
    void aFailedCueLogsOneWarningNamingItAndTheLoadSummaryCountsIt() {
        String moveCue = SoundManager.assetPath(SoundEvent.Type.MENU_MOVE);
        failing.add(moveCue);
        int all = SoundEvent.Type.values().length + 1;

        try (LogCapture capture = LogCapture.of(SoundManager.class, Level.DEBUG)) {
            new SoundManager(false).dispose();

            assertEquals(List.of(
                "Sound " + moveCue + " failed to load; continuing without it: Error reading WAV file: " + moveCue,
                "Loaded " + (all - 1) + " of " + all + " sounds from sounds/; the rest stay silent"),
                capture.messages(Level.WARN));
            assertEquals(List.of(), capture.messages(Level.INFO), "a partial load is a warning, not an info line");
            long perFile = capture.messages(Level.DEBUG).stream()
                .filter(line -> line.startsWith("Loaded sound ")).count();
            assertEquals(all - 1, perFile, "each sound that loaded has its own line at DEBUG");
        }
    }

    @Test
    void aFullLoadLogsOneSummaryAtInfoAndNoWarning() {
        int all = SoundEvent.Type.values().length + 1;
        try (LogCapture capture = LogCapture.of(SoundManager.class, Level.INFO)) {
            new SoundManager(false).dispose();

            assertEquals(List.of("Loaded " + all + " sounds from sounds/"), capture.messages(Level.INFO));
            assertEquals(List.of(), capture.messages(Level.WARN));
        }
    }

    @Test
    void aFailedChargeHumKeepsTheCuesAndAChargingUpdateDoesNotFail() {
        failing.add(SoundManager.CHARGE_HUM_PATH);
        GameSettings settings = new GameSettings();
        MenuState playing = new MenuState(settings, new SaveData(new MapKeyValueStore()), MenuState.Screen.PLAYING);
        GameSimulation simulation = chargingSimulation();

        SoundManager manager = new SoundManager(false);
        manager.update(null, menuWithMoveAndConfirm(settings), settings);
        assertEquals(2, totalCalls("play"), "both menu cues play without the charge hum");

        assertDoesNotThrow(() -> manager.update(simulation, playing, settings));
        assertEquals(0, totalCalls("loop"));
        assertDoesNotThrow(manager::stopLoops);
        assertDoesNotThrow(manager::dispose);
    }

    @Test
    void everyCueLoadsPlaysLoopsAndIsDisposedWhenNothingFails() {
        GameSettings settings = new GameSettings();
        MenuState playing = new MenuState(settings, new SaveData(new MapKeyValueStore()), MenuState.Screen.PLAYING);
        GameSimulation simulation = chargingSimulation();

        SoundManager manager = new SoundManager(false);
        manager.update(null, menuWithMoveAndConfirm(settings), settings);
        assertEquals(1, calls(SoundManager.assetPath(SoundEvent.Type.MENU_MOVE), "play"));
        assertEquals(1, calls(SoundManager.assetPath(SoundEvent.Type.MENU_CONFIRM), "play"));

        manager.update(simulation, playing, settings);
        assertEquals(1, calls(SoundManager.CHARGE_HUM_PATH, "loop"), "the charge loop starts");
        manager.update(simulation, playing, settings);
        assertEquals(1, calls(SoundManager.CHARGE_HUM_PATH, "loop"), "a running loop is retuned, not restarted");
        assertEquals(1, calls(SoundManager.CHARGE_HUM_PATH, "setPitch"));

        manager.dispose();
        assertEquals(SoundEvent.Type.values().length + 1, totalCalls("dispose"), "every sound is disposed once");
    }

    @Test
    void everyCueFailingLeavesASilentManagerThatStillDrainsEvents() {
        for (SoundEvent.Type type : SoundEvent.Type.values()) failing.add(SoundManager.assetPath(type));
        failing.add(SoundManager.CHARGE_HUM_PATH);
        GameSettings settings = new GameSettings();
        MenuState menu = menuWithMoveAndConfirm(settings);

        SoundManager manager = new SoundManager(false);
        manager.update(null, menu, settings);

        assertTrue(menu.drainSoundEvents().isEmpty(), "menu events are drained");
        assertEquals(0, totalCalls("play"));
        manager.dispose();
    }
}

package io.github.brantunger.gamex;

import com.badlogic.gdx.ApplicationAdapter;
import com.badlogic.gdx.Gdx;
import com.badlogic.gdx.Graphics;
import com.badlogic.gdx.utils.Disposable;
import io.github.brantunger.gamex.audio.SoundManager;
import io.github.brantunger.gamex.audio.SoundVolumes;
import io.github.brantunger.gamex.debug.DemoRun;
import io.github.brantunger.gamex.debug.DemoScene;
import io.github.brantunger.gamex.hud.HudRenderer;
import io.github.brantunger.gamex.hud.MenuState;
import io.github.brantunger.gamex.hud.PixelFont;
import io.github.brantunger.gamex.hud.TextFit;
import io.github.brantunger.gamex.hud.TextValues;
import io.github.brantunger.gamex.hud.UiStrings;
import io.github.brantunger.gamex.input.InputController;
import io.github.brantunger.gamex.input.KeyBindings;
import io.github.brantunger.gamex.player.DifficultyTuning;
import io.github.brantunger.gamex.player.PlayerInput;
import io.github.brantunger.gamex.player.PlayerLoadout;
import io.github.brantunger.gamex.weapon.WeaponTuning;
import io.github.brantunger.gamex.render.AssetPreparer;
import io.github.brantunger.gamex.render.Palettes;
import io.github.brantunger.gamex.render.ProceduralTextures;
import io.github.brantunger.gamex.render.RenderSettings;
import io.github.brantunger.gamex.render.ViewmodelRenderer;
import io.github.brantunger.gamex.render.WallTextures;
import io.github.brantunger.gamex.render.WorldRenderer;
import io.github.brantunger.gamex.settings.AsyncKeyValueStore;
import io.github.brantunger.gamex.settings.GameSettings;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.TimeUnit;
import io.github.brantunger.gamex.settings.GdxPreferencesStore;
import io.github.brantunger.gamex.settings.KeyValueStore;
import io.github.brantunger.gamex.settings.MapKeyValueStore;
import io.github.brantunger.gamex.settings.RunStats;
import io.github.brantunger.gamex.settings.SaveData;
import io.github.brantunger.gamex.settings.SettingsDefaults;
import io.github.brantunger.gamex.settings.WindowDefaults;
import io.github.brantunger.gamex.world.EnemyTuning;
import io.github.brantunger.gamex.world.Levels;
import io.github.brantunger.gamex.world.WorldTuning;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/** {@link com.badlogic.gdx.ApplicationListener} implementation shared by all platforms. */
public class MainGame extends ApplicationAdapter {

    private static final Logger log = LoggerFactory.getLogger(MainGame.class);

    /** Short delay after player death before the Death menu overlays the lowered view, in seconds. */
    static final float DEATH_MENU_DELAY = 0.45f;
    static final float MAX_BACKDROP_DT = 0.1f;
    static final int MIN_WINDOW_WIDTH = WindowDefaults.MIN_WIDTH;
    static final int MIN_WINDOW_HEIGHT = WindowDefaults.MIN_HEIGHT;

    private final PlayerInput input = new PlayerInput();
    /** The window size of {@code assets/data/window.txt}, as the launcher loaded it; not read again here. */
    private final WindowDefaults windowDefaults;
    /** The settings defaults of {@code assets/data/settings.txt}, as the launcher loaded them; not read again. */
    private final SettingsDefaults settingsDefaults;
    KeyValueStore settingsStore;
    GameSettings settings;
    private SaveData saveData;
    private ExecutorService workerPool;
    private ExecutorService ioExecutor;
    private AsyncKeyValueStore asyncSettingsStore;
    private AsyncKeyValueStore asyncSaveStore;
    private MenuState menuState;
    private GameSimulation simulation;
    /** The enemy balance, read from {@code assets/data/} once in {@link #create()}. */
    private EnemyTuning enemyTuning;
    /** What each difficulty multiplies, read from {@code assets/data/} once in {@link #create()}. */
    private DifficultyTuning difficultyTuning;
    /** The weapon balance, read from {@code assets/data/} once in {@link #create()}. */
    private WeaponTuning weaponTuning;
    /** The balance of pickups, canisters, shock grates and noise, read once in {@link #create()}. */
    private WorldTuning worldTuning;
    /** The HUD font, read from {@code assets/data/} once in {@link #create()}. */
    private PixelFont font;
    private ProceduralTextures textures;
    private WorldRenderer worldRenderer;
    private ViewmodelRenderer viewmodelRenderer;
    private HudRenderer hudRenderer;
    SoundManager soundManager;
    private InputController controller;
    /** The unattended screenshot run, or null in a normal game. */
    DemoRun demoRun;
    private float backdropTime;
    private float deathTimer;
    private float smoothedDt = 1f / 60f;
    private int windowedWidth;
    private int windowedHeight;
    private int lastResizeWidth;
    private int lastResizeHeight;
    private boolean simulationPlayed;
    /** Whether the launcher forced a windowed start, which the saved fullscreen setting must not undo. */
    private final boolean forceWindowed;

    /**
     * Creates the game with the window and settings defaults files read here and now, and the size the window file
     * holds as the windowed fallback size. This is for tests only: the launcher loads the two files once and uses
     * {@link #MainGame(int, int, boolean, WindowDefaults, SettingsDefaults)}.
     */
    public MainGame() {
        this(WindowDefaults.load(), SettingsDefaults.load());
    }

    /**
     * Creates the game with the size of {@code windowDefaults} as the windowed fallback size. This is for tests
     * only, as {@link #MainGame()} is.
     * @param windowDefaults the window size to fall back to
     * @param settingsDefaults what each setting starts at, and its range and step
     */
    MainGame(WindowDefaults windowDefaults, SettingsDefaults settingsDefaults) {
        this(windowDefaults.width, windowDefaults.height, false, windowDefaults, settingsDefaults);
    }

    /**
     * Creates the game with an initial windowed fallback size used if launched in fullscreen and later switched to
     * windowed mode, and the window and settings defaults files read here and now. This is for tests only, as
     * {@link #MainGame()} is.
     */
    public MainGame(int initialWindowWidth, int initialWindowHeight) {
        this(initialWindowWidth, initialWindowHeight, false);
    }

    /**
     * Creates the game with an initial windowed fallback size, says whether the launcher forced a windowed start,
     * and reads the window and settings defaults files here and now. This is for tests only, as {@link #MainGame()}
     * is.
     * @param initialWindowWidth the windowed fallback width in pixels
     * @param initialWindowHeight the windowed fallback height in pixels
     * @param forceWindowed true when the launcher forced a windowed start
     */
    public MainGame(int initialWindowWidth, int initialWindowHeight, boolean forceWindowed) {
        this(initialWindowWidth, initialWindowHeight, forceWindowed, WindowDefaults.load(), SettingsDefaults.load());
    }

    /**
     * Creates the game with an initial windowed fallback size and says whether the launcher forced a windowed start.
     * The two defaults objects are the ones the launcher opened the window with, so that the launcher and the game
     * cannot read different copies of the files; the game does not read the files again.
     * @param initialWindowWidth the windowed fallback width in pixels
     * @param initialWindowHeight the windowed fallback height in pixels
     * @param forceWindowed true when the launcher forced a windowed start (for example {@code GAMEX_WINDOW}), so
     *     that startup does not switch to fullscreen for a saved fullscreen setting; the setting itself is kept
     * @param windowDefaults the window size of {@code assets/data/window.txt}, which the renderers size their
     *     cameras and the overlay with until the window reports its own size
     * @param settingsDefaults what each setting starts at, and its range and step, from
     *     {@code assets/data/settings.txt}
     */
    public MainGame(int initialWindowWidth, int initialWindowHeight, boolean forceWindowed,
                    WindowDefaults windowDefaults, SettingsDefaults settingsDefaults) {
        if (windowDefaults == null) throw new IllegalArgumentException("Window defaults must not be null");
        if (settingsDefaults == null) throw new IllegalArgumentException("Settings defaults must not be null");
        this.windowedWidth = Math.max(MIN_WINDOW_WIDTH, initialWindowWidth);
        this.windowedHeight = Math.max(MIN_WINDOW_HEIGHT, initialWindowHeight);
        this.forceWindowed = forceWindowed;
        this.windowDefaults = windowDefaults;
        this.settingsDefaults = settingsDefaults;
    }

    int getWindowedWidth() {
        return windowedWidth;
    }

    int getWindowedHeight() {
        return windowedHeight;
    }

    @Override
    public void create() {
        enemyTuning = EnemyTuning.load();
        difficultyTuning = DifficultyTuning.load();
        SoundVolumes soundVolumes = SoundVolumes.load();
        WallTextures wallTextures = WallTextures.load();
        weaponTuning = WeaponTuning.load();
        worldTuning = WorldTuning.load();
        font = PixelFont.load();
        Palettes palettes = Palettes.load();
        RenderSettings renderSettings = RenderSettings.load();
        KeyBindings bindings = KeyBindings.load(font);
        UiStrings strings = UiStrings.load(font, bindings::hint,
            TextValues.of(weaponTuning, difficultyTuning, worldTuning));
        // A line too long for its box, in the text or through a long key name, stops the start here.
        TextFit.require(strings, difficultyTuning, settingsDefaults, Levels.campaign(), Levels.trainingRange());
        demoRun = DemoRun.fromEnvironment();
        if (demoRun != null) {
            if (demoRun.getScript().isEmpty()) {
                log.info("Debug screenshot run with no demo script: settings and progress are neither read nor"
                    + " written");
            } else {
                log.info("Debug screenshot run with demo script '{}': settings and progress are neither read nor"
                    + " written", demoRun.getScript());
            }
            settingsStore = new MapKeyValueStore();
            settings = new GameSettings(settingsDefaults);
            saveData = new SaveData(new MapKeyValueStore());
            // The screen, the level, the loadout and where the player stands are the header block of the script's
            // file in assets/demos/.
            DemoScene scene = demoRun.getScene();
            if (scene.unlockAllStages) {
                saveData.unlockStage(Levels.campaign().size() - 1);
            }
            menuState = new MenuState(settings, saveData, scene.startScreen, difficultyTuning, weaponTuning,
                strings);
            simulation = scene.createSimulation(enemyTuning, difficultyTuning, weaponTuning, worldTuning);
        } else {
            ioExecutor = AsyncKeyValueStore.newIoExecutor();
            asyncSettingsStore = new AsyncKeyValueStore(
                new GdxPreferencesStore(Gdx.app.getPreferences(GameSettings.PREFERENCES_NAME)), ioExecutor, false);
            settingsStore = asyncSettingsStore;
            settings = GameSettings.load(settingsStore, settingsDefaults);
            asyncSaveStore = new AsyncKeyValueStore(
                new GdxPreferencesStore(Gdx.app.getPreferences(SaveData.PREFERENCES_NAME)), ioExecutor, false);
            saveData = new SaveData(asyncSaveStore);
            menuState = new MenuState(settings, saveData, MenuState.Screen.TITLE, difficultyTuning, weaponTuning,
                strings);
            Levels.Stage initialStage = menuState.getActiveStage();
            simulation = new GameSimulation(initialStage.map, menuState.getActiveLoadout(),
                initialStage.completeOnAllTargets, enemyTuning, difficultyTuning, weaponTuning, worldTuning);
            simulationPlayed = false;
            log.info("Loaded settings: sensitivity {}, invert Y {}, field of view {}, view bob {}, volume {},"
                + " fullscreen {}, minimap {}", settings.getMouseSensitivity(), settings.isInvertY(),
                settings.getFov(), settings.isViewBob(), settings.getMasterVolume(), settings.isFullscreen(),
                settings.isMinimap());
            log.info("Loaded progress: saved run {}, unlocked stage index {}", saveData.hasSavedRun(),
                saveData.getUnlockedStageIndex());
            applyStartupDisplayMode(settings);
        }

        workerPool = AssetPreparer.newWorkerPool();
        AssetPreparer.StartupBatch startupBatch = AssetPreparer.prepareStartup(wallTextures, palettes, workerPool);
        try {
            soundManager = new SoundManager(demoRun != null, soundVolumes);
            textures = new ProceduralTextures(wallTextures, startupBatch.awaitTextures());
            worldRenderer = new WorldRenderer(simulation.map, textures, palettes.world, renderSettings, windowDefaults,
                settingsDefaults, startupBatch.awaitWorldModels(), workerPool);
            viewmodelRenderer = new ViewmodelRenderer(textures, palettes, windowDefaults,
                startupBatch.awaitViewmodels());
        } finally {
            startupBatch.dispose();
        }
        hudRenderer = new HudRenderer(textures, font, strings, windowDefaults);
        if (demoRun == null) controller = new InputController(menuState, settings, bindings);
        syncDisplayViewport();
        log.info("Game created at {}x{}, fullscreen {}", Gdx.graphics.getWidth(), Gdx.graphics.getHeight(),
            Gdx.graphics.isFullscreen());
    }

    @Override
    public void resize(int width, int height) {
        if (width <= 0 || height <= 0) return;
        if (width != lastResizeWidth || height != lastResizeHeight) log.debug("Resized to {}x{}", width, height);
        if (Gdx.graphics != null && !Gdx.graphics.isFullscreen()) {
            windowedWidth = Math.max(MIN_WINDOW_WIDTH, width);
            windowedHeight = Math.max(MIN_WINDOW_HEIGHT, height);
        }
        applyBackBufferViewport();
        lastResizeWidth = width;
        lastResizeHeight = height;
        if (worldRenderer != null) worldRenderer.resize(width, height);
        if (viewmodelRenderer != null) viewmodelRenderer.resize(width, height);
        if (hudRenderer != null) hudRenderer.resize(width, height);
    }

    @Override
    public void pause() {
        log.debug("Paused: the window lost the focus, was minimised or is closing");
        if (soundManager != null) soundManager.stopLoops();
        if (controller != null) {
            controller.pause();
            simulation.pause();
        }
    }

    @Override
    public void render() {
        syncDisplayViewport();
        if (demoRun != null) {
            demoRun.nextFrame(input);
            backdropTime += DemoRun.FRAME_TIME;
            if (!menuState.isPlaying()) {
                worldRenderer.renderBackdrop(simulation, settings, backdropTime);
                hudRenderer.render(simulation, menuState);
                soundManager.update(simulation, menuState, settings);
                demoRun.frameRendered();
                return;
            }
            simulation.update(DemoRun.FRAME_TIME, input);
            soundManager.update(simulation, menuState, settings);
            float[] orbit = demoRun.getViewmodelOrbit();
            if (orbit != null) {
                viewmodelRenderer.renderAlone(simulation.getActiveWeapon(), orbit[0], orbit[1]);
            } else {
                worldRenderer.render(simulation, settings);
                viewmodelRenderer.render(simulation, settings);
                hudRenderer.render(simulation, menuState);
            }
            demoRun.frameRendered();
            return;
        }

        float rawDt = Gdx.graphics.getDeltaTime();
        float stepDt = smoothStepDt(rawDt);
        advanceBackdropTime(rawDt);

        MenuState.Screen beforeScreen = menuState.getScreen();
        MenuState.Command command = controller.poll(input);
        if (beforeScreen == MenuState.Screen.SETTINGS && menuState.getScreen() != MenuState.Screen.SETTINGS) {
            saveSettingsQuietly(settings, settingsStore);
        }
        executeCommand(command);

        MenuState.Screen screen = menuState.getScreen();
        if (screen == MenuState.Screen.PLAYING) {
            simulationPlayed = true;
            simulation.update(stepDt, input);
            if (simulation.isLevelCleared()) {
                menuState.onStageCleared(simulation.captureCarryOverLoadout(), runStatsOf(simulation));
                controller.syncCursorCapture();
            } else if (simulation.player.isDead()) {
                if (advanceDeathTimer(rawDt)) {
                    menuState.onPlayerDied();
                    controller.syncCursorCapture();
                }
            }
        } else if (screen == MenuState.Screen.PAUSED) {
            simulation.pause();
        } else if (screen == MenuState.Screen.DEAD) {
            // Keep settling the lowered camera and lingering blast effects behind the death overlay.
            simulationPlayed = true;
            input.clear();
            simulation.update(stepDt, input);
        }
        soundManager.update(simulation, menuState, settings);

        if (showsBackdrop(menuState)) {
            GameSimulation next = backdropSimulation(simulation, menuState, simulationPlayed, enemyTuning,
                difficultyTuning, weaponTuning, worldTuning);
            if (next != simulation) {
                log.debug("Backdrop shows stage {}", menuState.getActiveStage().id);
                simulation = next;
                simulationPlayed = false;
            }
            worldRenderer.renderBackdrop(simulation, settings, backdropTime);
        } else {
            worldRenderer.render(simulation, settings);
            viewmodelRenderer.render(simulation, settings);
        }
        hudRenderer.render(simulation, menuState);
    }

    /**
     * @param simulation a simulation whose stage was just cleared
     * @return the run's statistics for the save data and the tally. Hostiles are counted on a stage that has any,
     *     its targets otherwise.
     */
    static RunStats runStatsOf(GameSimulation simulation) {
        boolean countTargets = simulation.enemies.isEmpty();
        int cleared = countTargets ? simulation.getTargetsDestroyed() : simulation.getEnemiesDestroyed();
        int total = countTargets ? simulation.targets.size() : simulation.enemies.size();
        return new RunStats(simulation.getTime(), cleared, total, simulation.getDamageTaken(),
            simulation.getPickupsCollected(), simulation.pickups.size(), simulation.getShotsFired(),
            simulation.getShotsHit());
    }

    /**
     * @param menuState the menu state
     * @return whether the slow-panning stage backdrop is drawn instead of the first-person view: on the Title, Stage
     *     Select, Difficulty Select and Briefing screens, and on Settings when it was opened from the Title
     */
    static boolean showsBackdrop(MenuState menuState) {
        switch (menuState.getScreen()) {
            case TITLE:
            case STAGE_SELECT:
            case DIFFICULTY_SELECT:
            case BRIEFING:
                return true;
            case SETTINGS:
                return menuState.getSettingsReturnScreen() == MenuState.Screen.TITLE;
            default:
                return false;
        }
    }

    /**
     * Returns {@code current} when it is already an unplayed preview of {@code menuState}'s active stage, or a fresh
     * {@link GameSimulation} for that stage when the map changed or {@code current} was stepped or mutated in play.
     * A fresh one is built with the tuning the game loaded at start-up.
     */
    static GameSimulation backdropSimulation(GameSimulation current, MenuState menuState, boolean simulationPlayed,
                                             EnemyTuning enemyTuning, DifficultyTuning difficultyTuning,
                                             WeaponTuning weaponTuning, WorldTuning worldTuning) {
        Levels.Stage stage = menuState.getActiveStage();
        if (current == null || current.map != stage.map || simulationPlayed || isSimulationMutated(current)) {
            return new GameSimulation(stage.map, PlayerLoadout.fresh(), false, enemyTuning, difficultyTuning,
                weaponTuning, worldTuning);
        }
        return current;
    }

    private static boolean isSimulationMutated(GameSimulation sim) {
        return sim.getTime() > 0f
            || sim.isLevelCleared()
            || sim.player.isDead()
            || sim.getShotsFired() > 0
            || sim.getDamageTaken() > 0f
            || sim.getPickupsCollected() > 0
            || !sim.projectiles.isEmpty()
            || !sim.enemyProjectiles.isEmpty()
            || !sim.railBeams.isEmpty()
            || !sim.effects.isEmpty();
    }

    private void executeCommand(MenuState.Command command) {
        switch (command) {
            case LAUNCH_STAGE: {
                Levels.Stage stage = menuState.getActiveStage();
                PlayerLoadout loadout = menuState.getActiveLoadout();
                log.info("Level started: {} ({}) on {}, health {}, armor {}, weapon {}", stage.id, stage.title,
                    loadout.difficulty, loadout.health, loadout.armor, loadout.activeWeapon);
                simulation = new GameSimulation(stage.map, loadout, stage.completeOnAllTargets, enemyTuning,
                    difficultyTuning, weaponTuning, worldTuning);
                simulationPlayed = true;
                worldRenderer.preloadWardenIfNeeded(simulation.enemies);
                worldRenderer.setMap(simulation.map);
                deathTimer = 0f;
                controller.resetCaptureForStageLaunch();
                break;
            }
            case APPLY_DISPLAY:
                saveSettingsQuietly(settings, settingsStore);
                applyDisplayMode(settings.isFullscreen());
                break;
            case SAVE_SETTINGS:
                saveSettingsQuietly(settings, settingsStore);
                break;
            case QUIT:
                log.info("Quit chosen from the menu");
                saveSettingsQuietly(settings, settingsStore);
                Gdx.app.exit();
                break;
            case NONE:
            default:
                break;
        }
    }

    static float clampDelta(float rawDt, float maxDt) {
        if (!Float.isFinite(rawDt) || rawDt <= 0f || !Float.isFinite(maxDt) || maxDt <= 0f) return 0f;
        return Math.min(maxDt, rawDt);
    }

    float smoothStepDt(float rawDt) {
        if (!Float.isFinite(rawDt) || rawDt <= 0f) {
            return 0f;
        }
        if (!Float.isFinite(smoothedDt) || smoothedDt <= 0f) {
            smoothedDt = Math.min(GameSimulation.MAX_DT, rawDt);
            return smoothedDt;
        }
        if (rawDt <= GameSimulation.MAX_DT && Math.abs(rawDt - smoothedDt) <= smoothedDt * 0.25f) {
            smoothedDt += (rawDt - smoothedDt) * 0.25f;
        } else {
            smoothedDt = Math.min(GameSimulation.MAX_DT, rawDt);
        }
        return smoothedDt;
    }

    float advanceBackdropTime(float rawDt) {
        if (!Float.isFinite(backdropTime) || backdropTime < 0f) {
            backdropTime = 0f;
        }
        backdropTime += clampDelta(rawDt, MAX_BACKDROP_DT);
        return backdropTime;
    }

    boolean advanceDeathTimer(float rawDt) {
        if (!Float.isFinite(deathTimer) || deathTimer < 0f) {
            deathTimer = 0f;
        }
        deathTimer += clampDelta(rawDt, GameSimulation.MAX_DT);
        return deathTimer >= DEATH_MENU_DELAY;
    }

    float getBackdropTime() {
        return backdropTime;
    }

    float getDeathTimer() {
        return deathTimer;
    }

    void applyBackBufferViewport() {
        if (Gdx.graphics == null || Gdx.gl == null) return;
        int backBufferWidth = Gdx.graphics.getBackBufferWidth();
        int backBufferHeight = Gdx.graphics.getBackBufferHeight();
        if (backBufferWidth > 0 && backBufferHeight > 0) {
            Gdx.gl.glViewport(0, 0, backBufferWidth, backBufferHeight);
        }
    }

    void syncDisplayViewport() {
        if (Gdx.graphics == null) return;
        int width = Gdx.graphics.getWidth();
        int height = Gdx.graphics.getHeight();
        if (width > 0 && height > 0 && (width != lastResizeWidth || height != lastResizeHeight)) {
            resize(width, height);
        } else {
            applyBackBufferViewport();
        }
    }

    /**
     * Matches the window to the loaded fullscreen setting at startup, after noting the windowed size to go back to.
     * Does not switch when the launcher forced a windowed start; the saved setting is left as it is.
     * @param loaded the settings read from the store
     */
    void applyStartupDisplayMode(GameSettings loaded) {
        if (!Gdx.graphics.isFullscreen()) {
            int currentWidth = Gdx.graphics.getWidth();
            int currentHeight = Gdx.graphics.getHeight();
            if (currentWidth > 0 && currentHeight > 0) {
                windowedWidth = Math.max(MIN_WINDOW_WIDTH, currentWidth);
                windowedHeight = Math.max(MIN_WINDOW_HEIGHT, currentHeight);
            }
        }
        if (forceWindowed) return;
        if (loaded.isFullscreen() != Gdx.graphics.isFullscreen()) {
            applyDisplayMode(loaded.isFullscreen());
        }
    }

    private void applyDisplayMode(boolean fullscreen) {
        if (fullscreen) {
            Graphics.DisplayMode mode = Gdx.graphics.getDisplayMode();
            if (mode != null) {
                log.info("Switching to fullscreen {}x{}", mode.width, mode.height);
                Gdx.graphics.setFullscreenMode(mode);
            } else {
                log.warn("No display mode to go fullscreen with; staying in a window");
            }
        } else {
            log.info("Switching to a {}x{} window", windowedWidth, windowedHeight);
            Gdx.graphics.setWindowedMode(windowedWidth, windowedHeight);
        }
        if (worldRenderer != null) {
            syncDisplayViewport();
        } else {
            applyBackBufferViewport();
        }
    }

    @Override
    public void dispose() {
        log.info("Shutting down");
        // create() may have failed part-way, so any of these can still be null.
        disposeResources(settings, settingsStore, demoRun != null,
            soundManager, worldRenderer, viewmodelRenderer, hudRenderer, textures,
            asyncSettingsStore, asyncSaveStore,
            () -> shutdownExecutor(ioExecutor),
            () -> shutdownExecutor(workerPool));
    }

    static void disposeResources(GameSettings settings, KeyValueStore settingsStore, boolean skipSettingsSave,
                                 Disposable... resources) {
        if (!skipSettingsSave) {
            saveSettingsQuietly(settings, settingsStore);
        }
        if (resources == null) return;
        for (Disposable resource : resources) {
            disposeQuietly(resource);
        }
    }

    static void shutdownExecutor(ExecutorService pool) {
        if (pool == null) return;
        pool.shutdown();
        try {
            if (!pool.awaitTermination(5, TimeUnit.SECONDS)) {
                log.warn("Timed out waiting for background worker pool shutdown");
                pool.shutdownNow();
            }
        } catch (InterruptedException e) {
            pool.shutdownNow();
            Thread.currentThread().interrupt();
        }
    }

    static void saveSettingsQuietly(GameSettings settings, KeyValueStore settingsStore) {
        if (settings == null || settingsStore == null) return;
        try {
            settings.save(settingsStore);
        } catch (RuntimeException e) {
            logError("Failed to save settings", e);
        }
    }

    static void disposeQuietly(Disposable resource) {
        if (resource == null) return;
        try {
            resource.dispose();
        } catch (RuntimeException e) {
            logError("Failed to dispose resource", e);
        }
    }

    private static void logError(String context, RuntimeException error) {
        log.error("{}: {}", context, error.getMessage(), error);
    }
}

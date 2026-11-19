package io.github.brantunger.gamex;

import com.badlogic.gdx.ApplicationAdapter;
import com.badlogic.gdx.Gdx;
import com.badlogic.gdx.Graphics;
import com.badlogic.gdx.utils.Disposable;
import io.github.brantunger.gamex.audio.SoundManager;
import io.github.brantunger.gamex.debug.DemoRun;
import io.github.brantunger.gamex.hud.HudRenderer;
import io.github.brantunger.gamex.hud.MenuState;
import io.github.brantunger.gamex.input.InputController;
import io.github.brantunger.gamex.player.Difficulty;
import io.github.brantunger.gamex.player.PlayerInput;
import io.github.brantunger.gamex.player.PlayerLoadout;
import io.github.brantunger.gamex.weapon.WeaponSlot;
import io.github.brantunger.gamex.render.ProceduralTextures;
import io.github.brantunger.gamex.render.ViewmodelRenderer;
import io.github.brantunger.gamex.render.WorldRenderer;
import io.github.brantunger.gamex.settings.GameSettings;
import io.github.brantunger.gamex.settings.GdxPreferencesStore;
import io.github.brantunger.gamex.settings.KeyValueStore;
import io.github.brantunger.gamex.settings.MapKeyValueStore;
import io.github.brantunger.gamex.settings.RunStats;
import io.github.brantunger.gamex.settings.SaveData;
import io.github.brantunger.gamex.world.Enemy;
import io.github.brantunger.gamex.world.Levels;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/** {@link com.badlogic.gdx.ApplicationListener} implementation shared by all platforms. */
public class MainGame extends ApplicationAdapter {

    private static final Logger log = LoggerFactory.getLogger(MainGame.class);

    /** Short delay after player death before the Death menu overlays the lowered view, in seconds. */
    private static final float DEATH_MENU_DELAY = 0.45f;
    private static final int DEFAULT_WINDOW_WIDTH = 1280;
    private static final int DEFAULT_WINDOW_HEIGHT = 720;
    static final int MIN_WINDOW_WIDTH = 160;
    static final int MIN_WINDOW_HEIGHT = 120;

    private final PlayerInput input = new PlayerInput();
    KeyValueStore settingsStore;
    GameSettings settings;
    private SaveData saveData;
    private MenuState menuState;
    private GameSimulation simulation;
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

    /** Creates the game using the default windowed fallback size of 1280x720. */
    public MainGame() {
        this(DEFAULT_WINDOW_WIDTH, DEFAULT_WINDOW_HEIGHT);
    }

    /**
     * Creates the game with an initial windowed fallback size used if launched in fullscreen and later switched to
     * windowed mode.
     */
    public MainGame(int initialWindowWidth, int initialWindowHeight) {
        this(initialWindowWidth, initialWindowHeight, false);
    }

    /**
     * Creates the game with an initial windowed fallback size and says whether the launcher forced a windowed start.
     * @param initialWindowWidth the windowed fallback width in pixels
     * @param initialWindowHeight the windowed fallback height in pixels
     * @param forceWindowed true when the launcher forced a windowed start (for example {@code GAMEX_WINDOW}), so
     *     that startup does not switch to fullscreen for a saved fullscreen setting; the setting itself is kept
     */
    public MainGame(int initialWindowWidth, int initialWindowHeight, boolean forceWindowed) {
        this.windowedWidth = Math.max(MIN_WINDOW_WIDTH, initialWindowWidth);
        this.windowedHeight = Math.max(MIN_WINDOW_HEIGHT, initialWindowHeight);
        this.forceWindowed = forceWindowed;
    }

    int getWindowedWidth() {
        return windowedWidth;
    }

    int getWindowedHeight() {
        return windowedHeight;
    }

    @Override
    public void create() {
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
            settings = new GameSettings();
            saveData = new SaveData(new MapKeyValueStore());
            String script = demoRun.getScript();
            boolean showTitle = "title".equals(script);
            boolean showStages = "stages".equals(script);
            if (showStages) {
                saveData.unlockStage(Levels.campaign().size() - 1);
            }
            MenuState.Screen startScreen = showTitle
                ? MenuState.Screen.TITLE
                : (showStages ? MenuState.Screen.STAGE_SELECT : MenuState.Screen.PLAYING);
            menuState = new MenuState(settings, saveData, startScreen);
            if ("lockdown".equals(script) || showStages) {
                Levels.Stage lockdown = Levels.lockdownWing();
                simulation = new GameSimulation(lockdown.map, menuState.getActiveLoadout(), lockdown.completeOnAllTargets);
            } else if ("scattergun".equals(script)) {
                Levels.Stage range = Levels.trainingRange();
                simulation = new GameSimulation(range.map,
                    new PlayerLoadout(100f, 0f, Difficulty.NORMAL, true, 16, WeaponSlot.SCATTERGUN),
                    range.completeOnAllTargets);
            } else if ("raillance".equals(script)) {
                Levels.Stage hub = Levels.securityHub();
                simulation = new GameSimulation(hub.map,
                    new PlayerLoadout(100f, 50f, Difficulty.NORMAL, true, 8, true, 6, WeaponSlot.RAIL_LANCE),
                    hub.completeOnAllTargets);
                simulation.player.position.set(4.5f, 4.5f);
                simulation.player.look(0f, -1f);
            } else if ("warden".equals(script)) {
                Levels.Stage warden = Levels.wardenCore();
                simulation = new GameSimulation(warden.map,
                    new PlayerLoadout(100f, 50f, Difficulty.NORMAL, true, 16, WeaponSlot.SCATTERGUN),
                    warden.completeOnAllTargets);
                simulation.player.position.set(23.5f, 13.6f);
                simulation.player.look(-90f, -4f);
            } else if ("wardenidle".equals(script)) {
                // Behind the Warden's shoulder, as it faces its door, and out of its hearing, so it stands as it does
                // before a fight. The turret and the drone that would open fire on the player there, and the rest
                // of the stage's hostiles with them, are taken out of the picture.
                Levels.Stage warden = Levels.wardenCore();
                simulation = new GameSimulation(warden.map,
                    new PlayerLoadout(100f, 50f, Difficulty.NORMAL, true, 16, WeaponSlot.SCATTERGUN),
                    warden.completeOnAllTargets);
                for (Enemy enemy : simulation.enemies) {
                    if (enemy.getKind() != Enemy.Kind.WARDEN) enemy.damage(Float.MAX_VALUE);
                }
                simulation.player.position.set(26.8f, 5.2f);
                simulation.player.look(135f, -4f);
            } else if ("jump".equals(script)) {
                Levels.Stage range = Levels.trainingRange();
                simulation = new GameSimulation(range.map,
                    new PlayerLoadout(100f, 0f, Difficulty.NORMAL, true, 16, WeaponSlot.SCATTERGUN),
                    range.completeOnAllTargets);
                simulation.player.position.set(9.8f, 15.5f);
                simulation.player.look(0f, -14f);
            } else if ("breach".equals(script) || showTitle) {
                Levels.Stage breach = Levels.outerBreach();
                simulation = new GameSimulation(breach.map, menuState.getActiveLoadout(), breach.completeOnAllTargets);
            } else {
                simulation = new GameSimulation(Levels.first());
            }
        } else {
            settingsStore = new GdxPreferencesStore(Gdx.app.getPreferences(GameSettings.PREFERENCES_NAME));
            settings = GameSettings.load(settingsStore);
            saveData = new SaveData(new GdxPreferencesStore(Gdx.app.getPreferences(SaveData.PREFERENCES_NAME)));
            menuState = new MenuState(settings, saveData, MenuState.Screen.TITLE);
            Levels.Stage initialStage = menuState.getActiveStage();
            simulation = new GameSimulation(initialStage.map, menuState.getActiveLoadout(),
                initialStage.completeOnAllTargets);
            simulationPlayed = false;
            log.info("Loaded settings: sensitivity {}, invert Y {}, field of view {}, view bob {}, volume {},"
                + " fullscreen {}", settings.getMouseSensitivity(), settings.isInvertY(), settings.getFov(),
                settings.isViewBob(), settings.getMasterVolume(), settings.isFullscreen());
            log.info("Loaded progress: saved run {}, unlocked stage index {}", saveData.hasSavedRun(),
                saveData.getUnlockedStageIndex());
            applyStartupDisplayMode(settings);
        }

        textures = new ProceduralTextures();
        worldRenderer = new WorldRenderer(simulation.map, textures);
        viewmodelRenderer = new ViewmodelRenderer(textures);
        hudRenderer = new HudRenderer(textures);
        soundManager = new SoundManager(demoRun != null);
        if (demoRun == null) controller = new InputController(menuState, settings);
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
        backdropTime += Math.min(0.1f, Math.max(0f, rawDt));

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
                deathTimer += Math.min(GameSimulation.MAX_DT, Math.max(0f, rawDt));
                if (deathTimer >= DEATH_MENU_DELAY) {
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
            GameSimulation next = backdropSimulation(simulation, menuState, simulationPlayed);
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
     */
    static GameSimulation backdropSimulation(GameSimulation current, MenuState menuState, boolean simulationPlayed) {
        Levels.Stage stage = menuState.getActiveStage();
        if (current == null || current.map != stage.map || simulationPlayed || isSimulationMutated(current)) {
            return new GameSimulation(stage.map);
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
                simulation = new GameSimulation(stage.map, loadout, stage.completeOnAllTargets);
                simulationPlayed = true;
                worldRenderer.setMap(simulation.map);
                deathTimer = 0f;
                controller.resetCaptureForStageLaunch();
                break;
            }
            case APPLY_DISPLAY:
                saveSettingsQuietly(settings, settingsStore);
                applyDisplayMode(settings.isFullscreen());
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

    private float smoothStepDt(float rawDt) {
        if (rawDt > 0f && rawDt <= GameSimulation.MAX_DT && Math.abs(rawDt - smoothedDt) <= smoothedDt * 0.25f) {
            smoothedDt += (rawDt - smoothedDt) * 0.25f;
        } else {
            smoothedDt = Math.min(GameSimulation.MAX_DT, Math.max(0f, rawDt));
        }
        return smoothedDt;
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
            soundManager, worldRenderer, viewmodelRenderer, hudRenderer, textures);
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

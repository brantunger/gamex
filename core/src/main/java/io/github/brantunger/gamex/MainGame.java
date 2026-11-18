package io.github.brantunger.gamex;

import com.badlogic.gdx.ApplicationAdapter;
import com.badlogic.gdx.Gdx;
import com.badlogic.gdx.Graphics;
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
import io.github.brantunger.gamex.settings.SaveData;
import io.github.brantunger.gamex.world.Levels;

/** {@link com.badlogic.gdx.ApplicationListener} implementation shared by all platforms. */
public class MainGame extends ApplicationAdapter {

    /** Short delay after player death before the Death menu overlays the lowered view, in seconds. */
    private static final float DEATH_MENU_DELAY = 0.45f;
    private static final int DEFAULT_WINDOW_WIDTH = 1280;
    private static final int DEFAULT_WINDOW_HEIGHT = 720;

    private final PlayerInput input = new PlayerInput();
    private KeyValueStore settingsStore;
    private GameSettings settings;
    private SaveData saveData;
    private MenuState menuState;
    private GameSimulation simulation;
    private ProceduralTextures textures;
    private WorldRenderer worldRenderer;
    private ViewmodelRenderer viewmodelRenderer;
    private HudRenderer hudRenderer;
    private SoundManager soundManager;
    private InputController controller;
    /** The unattended screenshot run, or null in a normal game. */
    private DemoRun demoRun;
    private float backdropTime;
    private float deathTimer;
    private float smoothedDt = 1f / 60f;
    private int windowedWidth;
    private int windowedHeight;
    private int lastResizeWidth;
    private int lastResizeHeight;
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
        this.windowedWidth = Math.max(160, initialWindowWidth);
        this.windowedHeight = Math.max(120, initialWindowHeight);
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
            applyStartupDisplayMode(settings);
        }

        textures = new ProceduralTextures();
        worldRenderer = new WorldRenderer(simulation.map, textures);
        viewmodelRenderer = new ViewmodelRenderer(textures);
        hudRenderer = new HudRenderer(textures);
        soundManager = new SoundManager(demoRun != null);
        if (demoRun == null) controller = new InputController(menuState, settings);
        syncDisplayViewport();
    }

    @Override
    public void resize(int width, int height) {
        if (width <= 0 || height <= 0) return;
        if (Gdx.graphics != null && !Gdx.graphics.isFullscreen()) {
            windowedWidth = width;
            windowedHeight = height;
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
            settings.save(settingsStore);
        }
        executeCommand(command);

        MenuState.Screen screen = menuState.getScreen();
        if (screen == MenuState.Screen.PLAYING) {
            simulation.update(stepDt, input);
            if (simulation.isLevelCleared()) {
                int cleared = simulation.enemies.isEmpty()
                    ? simulation.getTargetsDestroyed()
                    : simulation.getEnemiesDestroyed();
                int total = simulation.enemies.isEmpty()
                    ? simulation.targets.size()
                    : simulation.enemies.size();
                menuState.onStageCleared(simulation.captureCarryOverLoadout(), simulation.getTime(), cleared, total,
                    simulation.getDamageTaken());
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
            input.clear();
            simulation.update(stepDt, input);
        }
        soundManager.update(simulation, menuState, settings);

        if (showsBackdrop(menuState)) {
            if (simulation.map != menuState.getActiveStage().map) {
                Levels.Stage stage = menuState.getActiveStage();
                simulation = new GameSimulation(stage.map, menuState.getActiveLoadout(), stage.completeOnAllTargets);
            }
            worldRenderer.renderBackdrop(simulation, settings, backdropTime);
        } else {
            worldRenderer.render(simulation, settings);
            viewmodelRenderer.render(simulation, settings);
        }
        hudRenderer.render(simulation, menuState);
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

    private void executeCommand(MenuState.Command command) {
        switch (command) {
            case LAUNCH_STAGE: {
                Levels.Stage stage = menuState.getActiveStage();
                simulation = new GameSimulation(stage.map, menuState.getActiveLoadout(), stage.completeOnAllTargets);
                worldRenderer.setMap(simulation.map);
                deathTimer = 0f;
                controller.syncCursorCapture();
                break;
            }
            case APPLY_DISPLAY:
                settings.save(settingsStore);
                applyDisplayMode(settings.isFullscreen());
                break;
            case QUIT:
                settings.save(settingsStore);
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
            windowedWidth = Math.max(160, Gdx.graphics.getWidth());
            windowedHeight = Math.max(120, Gdx.graphics.getHeight());
        }
        if (forceWindowed) return;
        if (loaded.isFullscreen() != Gdx.graphics.isFullscreen()) {
            applyDisplayMode(loaded.isFullscreen());
        }
    }

    private void applyDisplayMode(boolean fullscreen) {
        if (fullscreen) {
            Graphics.DisplayMode mode = Gdx.graphics.getDisplayMode();
            if (mode != null) Gdx.graphics.setFullscreenMode(mode);
        } else {
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
        if (settings != null && settingsStore != null && demoRun == null) {
            settings.save(settingsStore);
        }
        // create() may have failed part-way, so any of these can still be null.
        if (soundManager != null) soundManager.dispose();
        if (worldRenderer != null) worldRenderer.dispose();
        if (viewmodelRenderer != null) viewmodelRenderer.dispose();
        if (hudRenderer != null) hudRenderer.dispose();
        if (textures != null) textures.dispose();
    }
}

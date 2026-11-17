package io.github.brantunger.gamex;

import com.badlogic.gdx.ApplicationAdapter;
import com.badlogic.gdx.Gdx;
import com.badlogic.gdx.Graphics;
import io.github.brantunger.gamex.audio.SoundManager;
import io.github.brantunger.gamex.debug.DemoRun;
import io.github.brantunger.gamex.hud.HudRenderer;
import io.github.brantunger.gamex.hud.MenuState;
import io.github.brantunger.gamex.input.InputController;
import io.github.brantunger.gamex.player.PlayerInput;
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
    private int windowedWidth = DEFAULT_WINDOW_WIDTH;
    private int windowedHeight = DEFAULT_WINDOW_HEIGHT;

    @Override
    public void create() {
        demoRun = DemoRun.fromEnvironment();
        if (demoRun != null) {
            settingsStore = new MapKeyValueStore();
            settings = new GameSettings();
            saveData = new SaveData(new MapKeyValueStore());
            boolean showTitle = "title".equals(demoRun.getScript());
            menuState = new MenuState(settings, saveData,
                showTitle ? MenuState.Screen.TITLE : MenuState.Screen.PLAYING);
            if ("breach".equals(demoRun.getScript()) || showTitle) {
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
            windowedWidth = Math.max(160, Gdx.graphics.getWidth());
            windowedHeight = Math.max(120, Gdx.graphics.getHeight());
            if (settings.isFullscreen()) applyDisplayMode();
        }

        textures = new ProceduralTextures();
        worldRenderer = new WorldRenderer(simulation.map, textures);
        viewmodelRenderer = new ViewmodelRenderer(textures);
        hudRenderer = new HudRenderer(textures);
        soundManager = new SoundManager(demoRun != null);
        if (demoRun == null) controller = new InputController(menuState, settings);
    }

    @Override
    public void resize(int width, int height) {
        if (width <= 0 || height <= 0) return;
        if (!Gdx.graphics.isFullscreen()) {
            windowedWidth = width;
            windowedHeight = height;
        }
        worldRenderer.resize(width, height);
        viewmodelRenderer.resize(width, height);
        hudRenderer.resize(width, height);
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
        if (demoRun != null) {
            demoRun.nextFrame(input);
            backdropTime += DemoRun.FRAME_TIME;
            if (menuState.getScreen() == MenuState.Screen.TITLE) {
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
                viewmodelRenderer.renderAlone(orbit[0], orbit[1]);
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
                menuState.onStageCleared(simulation.player, simulation.getTime(), cleared, total,
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

        MenuState.Screen renderScreen = menuState.getScreen();
        if (renderScreen == MenuState.Screen.TITLE
            || renderScreen == MenuState.Screen.STAGE_SELECT
            || renderScreen == MenuState.Screen.DIFFICULTY_SELECT
            || renderScreen == MenuState.Screen.BRIEFING) {
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
                applyDisplayMode();
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

    private void applyDisplayMode() {
        if (settings.isFullscreen()) {
            Graphics.DisplayMode mode = Gdx.graphics.getDisplayMode();
            Gdx.graphics.setFullscreenMode(mode);
        } else {
            Gdx.graphics.setWindowedMode(windowedWidth, windowedHeight);
        }
    }

    @Override
    public void dispose() {
        if (settings != null && settingsStore != null && demoRun == null) {
            settings.save(settingsStore);
        }
        if (soundManager != null) soundManager.dispose();
        worldRenderer.dispose();
        viewmodelRenderer.dispose();
        hudRenderer.dispose();
        textures.dispose();
    }
}

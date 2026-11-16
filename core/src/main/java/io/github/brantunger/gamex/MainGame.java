package io.github.brantunger.gamex;

import com.badlogic.gdx.ApplicationAdapter;
import com.badlogic.gdx.Gdx;
import io.github.brantunger.gamex.debug.DemoRun;
import io.github.brantunger.gamex.hud.HudRenderer;
import io.github.brantunger.gamex.input.InputController;
import io.github.brantunger.gamex.player.PlayerInput;
import io.github.brantunger.gamex.render.ProceduralTextures;
import io.github.brantunger.gamex.render.ViewmodelRenderer;
import io.github.brantunger.gamex.render.WorldRenderer;
import io.github.brantunger.gamex.world.Levels;

/** {@link com.badlogic.gdx.ApplicationListener} implementation shared by all platforms. */
public class MainGame extends ApplicationAdapter {
    private final PlayerInput input = new PlayerInput();
    private GameSimulation simulation;
    private ProceduralTextures textures;
    private WorldRenderer worldRenderer;
    private ViewmodelRenderer viewmodelRenderer;
    private HudRenderer hudRenderer;
    private InputController controller;
    /** The unattended screenshot run, or null in a normal game. */
    private DemoRun demoRun;

    @Override
    public void create() {
        simulation = new GameSimulation(Levels.first());
        textures = new ProceduralTextures();
        worldRenderer = new WorldRenderer(simulation.map, textures);
        viewmodelRenderer = new ViewmodelRenderer(textures);
        hudRenderer = new HudRenderer(textures);
        demoRun = DemoRun.fromEnvironment();
        if (demoRun == null) controller = new InputController();
    }

    @Override
    public void resize(int width, int height) {
        // A minimised window reports a size of zero.
        if (width <= 0 || height <= 0) return;
        worldRenderer.resize(width, height);
        viewmodelRenderer.resize(width, height);
        hudRenderer.resize(width, height);
    }

    /**
     * Called when the window is minimised or loses the focus: the game pauses and frees the cursor, as Esc does. It
     * stays paused when the window comes back, until the player clicks or presses Esc.
     */
    @Override
    public void pause() {
        if (controller != null) controller.pause();
    }

    @Override
    public void render() {
        boolean paused = false;
        if (demoRun != null) {
            demoRun.nextFrame(input);
            simulation.update(DemoRun.FRAME_TIME, input);
        } else {
            controller.poll(input);
            paused = controller.isPaused();
            if (paused) simulation.pause();
            else simulation.update(Gdx.graphics.getDeltaTime(), input);
        }
        float[] orbit = demoRun != null ? demoRun.getViewmodelOrbit() : null;
        if (orbit != null) {
            viewmodelRenderer.renderAlone(orbit[0], orbit[1]);
        } else {
            worldRenderer.render(simulation);
            viewmodelRenderer.render(simulation);
            hudRenderer.render(simulation, paused);
        }
        if (demoRun != null) demoRun.frameRendered();
    }

    @Override
    public void dispose() {
        worldRenderer.dispose();
        viewmodelRenderer.dispose();
        hudRenderer.dispose();
        textures.dispose();
    }
}

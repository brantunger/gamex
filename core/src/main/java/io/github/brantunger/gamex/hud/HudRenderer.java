package io.github.brantunger.gamex.hud;

import com.badlogic.gdx.Gdx;
import com.badlogic.gdx.graphics.GL20;
import com.badlogic.gdx.graphics.g2d.SpriteBatch;
import com.badlogic.gdx.math.MathUtils;
import com.badlogic.gdx.math.Matrix4;
import com.badlogic.gdx.utils.Disposable;
import io.github.brantunger.gamex.GameSimulation;
import io.github.brantunger.gamex.player.Difficulty;
import io.github.brantunger.gamex.player.Player;
import io.github.brantunger.gamex.render.ProceduralTextures;
import io.github.brantunger.gamex.settings.Medal;
import io.github.brantunger.gamex.settings.SaveData;
import io.github.brantunger.gamex.settings.WindowDefaults;
import io.github.brantunger.gamex.weapon.BlastCharger;
import io.github.brantunger.gamex.weapon.EnergyMeter;
import io.github.brantunger.gamex.weapon.WeaponSlot;
import io.github.brantunger.gamex.world.Enemy;
import io.github.brantunger.gamex.world.Levels;
import io.github.brantunger.gamex.world.Pickup;

import java.util.List;

/**
 * Draws the 2D overlay on top of the finished 3D view: combat HUD (crosshair, hit-markers, directional hurt vignette,
 * health/armor/energy bars, pickup toasts, objective/timer) and interactive menu screens (Title, Difficulty, Briefing,
 * Pause, Settings, Death and Intermission Tally).
 *
 * <p>Sizes are in units of 1/720 of the shorter window dimension, so the overlay scales cleanly without horizontal
 * clipping at any aspect ratio.
 */
public final class HudRenderer implements Disposable {

    static final float HUD_REFERENCE_SPAN = 720f;
    private static final int ENERGY_SEGMENTS = 10;
    private static final float MENU_BOX_WIDTH = 520f;
    private static final float MENU_ROW_HEIGHT = 34f;
    private static final float MENU_ROW_STEP = 42f;
    /** Left edge of the status column, in HUD units. */
    static final float STATUS_LEFT = 28f;
    /** Font pixel size of the objective line, in HUD units. */
    static final float OBJECTIVE_PIXEL = 2.4f;
    /** Gap between the top of the window and the top of the status row, in HUD units. */
    static final float STATUS_TOP = 28f;
    /** Font pixel size of the status row, in HUD units. */
    static final float STATUS_PIXEL = 3f;
    /** How far the objective line's upper row sits below the bottom of the status row, in HUD units. */
    static final float OBJECTIVE_ROW_DROP = 24f;
    /** Gap between the top of the window and the top of the objective line's lower row, in HUD units. */
    static final float OBJECTIVE_LOW_TOP = 110f;
    /**
     * Right edge the objective line is given room up to, in HUD units; the longest line of the shipped text ends at
     * 409.6.
     */
    static final float OBJECTIVE_RIGHT_LIMIT = 410f;
    /**
     * Half the width kept free around the centre of the window for the boss bar's panel and the toast boxes, in HUD
     * units; the widest toast box of the shipped text is 450.8 wide.
     */
    static final float CENTRE_HALF_WIDTH = 226f;
    /** Width of the boss health bar, in HUD units. */
    static final float BOSS_BAR_WIDTH = 380f;
    /** Gap between the top of the window and the bottom of the boss health bar, in HUD units. */
    static final float BOSS_BAR_BOTTOM = 56f;
    /** Room the boss bar's panel leaves on each side of the bar, in HUD units. */
    static final float BOSS_PANEL_PAD_X = 12f;
    /** Room the boss bar's panel leaves below the bar, in HUD units. */
    static final float BOSS_PANEL_PAD_Y = 8f;
    /** Font pixel size of a toast, in HUD units. */
    static final float TOAST_PIXEL = 2.8f;
    /** Room a toast box leaves on each side of its text, in HUD units. */
    static final float TOAST_PAD_X = 14f;
    /** Room a toast box leaves above and below its text, in HUD units. */
    static final float TOAST_PAD_Y = 8f;
    /** Gap between the top of the window and the bottom of a toast box, in HUD units. */
    static final float TOAST_BOTTOM = 64f;
    /** Gap between the top of the window and the bottom of a toast box while the boss bar is shown, in HUD units. */
    static final float TOAST_BOTTOM_UNDER_BOSS = 104f;
    /** Width of the Intermission Tally panel, in HUD units. */
    static final float TALLY_PANEL_WIDTH = 640f;
    /** Font pixel size of the tally's statistic lines, in HUD units. */
    static final float TALLY_STAT_PIXEL = 2.6f;
    /** Font pixel size of the medal lines on the tally and on Select Stage, in HUD units. */
    static final float MEDAL_PIXEL = 2.4f;
    /** Width of the Select Stage panel, in HUD units. */
    static final float STAGE_SELECT_PANEL_WIDTH = 620f;
    /** Font pixel size of the tally's rank line, in HUD units. */
    static final float TALLY_RANK_PIXEL = 4f;
    /** Font pixel size of the tally's title after a stage, in HUD units. */
    static final float TALLY_TITLE_PIXEL = 5.8f;
    /** Font pixel size of the tally's title after the last stage, in HUD units. */
    static final float TALLY_CAMPAIGN_TITLE_PIXEL = 5.6f;
    /** Font pixel size of the stage's title on the tally, in HUD units. */
    static final float TALLY_STAGE_PIXEL = 3.2f;
    /** Font pixel size of the note under the stage's title after the last stage, in HUD units. */
    static final float TALLY_NOTE_PIXEL = 2.4f;
    /** Width of the Title panel, in HUD units. */
    static final float TITLE_PANEL_WIDTH = 600f;
    /** Font pixel size of the game's name on the Title screen, in HUD units. */
    static final float TITLE_NAME_PIXEL = 12f;
    /** Font pixel size of the line under the game's name, in HUD units. */
    static final float TITLE_SUBTITLE_PIXEL = 3f;
    /** Font pixel size of the Select Stage title, in HUD units. */
    static final float STAGE_SELECT_TITLE_PIXEL = 5.5f;
    /** Font pixel size of the line under the Select Stage title, in HUD units. */
    static final float STAGE_SELECT_HINT_PIXEL = 2.2f;
    /** Width of the Select Difficulty panel, in HUD units. */
    static final float DIFFICULTY_PANEL_WIDTH = 580f;
    /** Font pixel size of the Select Difficulty title, in HUD units. */
    static final float DIFFICULTY_TITLE_PIXEL = 5.5f;
    /** Width of the Briefing panel, in HUD units. */
    static final float BRIEFING_PANEL_WIDTH = 660f;
    /** Font pixel size of the stage's title on the Briefing screen, in HUD units. */
    static final float BRIEFING_TITLE_PIXEL = 5.2f;
    /** Font pixel size of the line under the briefing's title, in HUD units. */
    static final float BRIEFING_META_PIXEL = 2.5f;
    /** Font pixel size of the Pause title, in HUD units. */
    static final float PAUSE_TITLE_PIXEL = 6.5f;
    /** Font pixel size of the Settings title, in HUD units. */
    static final float SETTINGS_TITLE_PIXEL = 5.8f;
    /** Width of the Death panel, in HUD units. */
    static final float DEATH_PANEL_WIDTH = 560f;
    /** Font pixel size of the Death title, in HUD units. */
    static final float DEATH_TITLE_PIXEL = 6.5f;
    /** Font pixel size of the line under the Death title, in HUD units. */
    static final float DEATH_SURVIVED_PIXEL = 2.5f;
    /** Font pixel size of the label over the boss health bar, in HUD units. */
    static final float BOSS_LABEL_PIXEL = 2.3f;
    /** Room a line of text is given clear of each side of the panel it is drawn in, in HUD units. */
    static final float PANEL_TEXT_MARGIN = 20f;
    /** Width of the Pause panel, in HUD units; the narrower of the two panels the quit prompt is drawn in. */
    static final float PAUSE_PANEL_WIDTH = 560f;
    /** Font pixel size of the quit prompt on the Title and Pause screens, in HUD units. */
    static final float QUIT_PROMPT_PIXEL = 2.8f;
    /** Width of the Settings panel, in HUD units. */
    static final float SETTINGS_PANEL_WIDTH = 600f;
    /** Font pixel size of the help line on the Settings screen, in HUD units. */
    static final float SETTINGS_HELP_PIXEL = 2.2f;
    /** Width of the box the briefing's lines are drawn in, in HUD units. */
    static final float BRIEFING_LOG_WIDTH = 580f;
    /** How many lines the box of the briefing has room for, one under the other. */
    static final int BRIEFING_LINES = 3;
    /** Font pixel size of a briefing line, in HUD units. */
    static final float BRIEFING_LINE_PIXEL = 2.4f;
    /** Width of a menu option row, in HUD units. */
    static final float OPTION_ROW_WIDTH = MENU_BOX_WIDTH;
    /** Font pixel size of a menu option row, in HUD units. */
    static final float OPTION_PIXEL = 2.8f;
    /** Font pixel size of the line of key hints at the foot of the Title screen, in HUD units. */
    static final float TITLE_FOOTER_PIXEL = 1.9f;
    /** Font pixel size of the centre-screen interaction prompt, in HUD units. */
    static final float INTERACTION_PROMPT_PIXEL = 2.4f;
    /** Horizontal padding inside the interaction prompt box, in HUD units. */
    static final float INTERACTION_PROMPT_PAD_X = 10f;
    /** Vertical padding inside the interaction prompt box, in HUD units. */
    static final float INTERACTION_PROMPT_PAD_Y = 5f;
    /** Minimum Rail Lance zoom fraction above which tactical rangefinder brackets are drawn. */
    static final float RAIL_ZOOM_OPTIC_THRESHOLD = 0.05f;
    /** Red, green, blue and alpha of the prompt in front of something the player can use now. */
    private static final float[] PROMPT_USABLE_COLOUR = {0.62f, 1f, 0.86f, 0.95f};
    /** Red, green, blue and alpha of the prompt in front of a bulkhead that stays shut. */
    private static final float[] PROMPT_BLOCKED_COLOUR = {1f, 0.72f, 0.30f, 0.95f};

    private final SpriteBatch batch;
    private final Matrix4 fallbackProjection = new Matrix4();
    private final ProceduralTextures textures;
    private final PixelFont font;
    private final UiStrings strings;
    private final MinimapRenderer minimap;
    /** The toast whose words {@link #toastText} holds, so they are put together once and not on every frame. */
    private GameSimulation.Toast shownToast;
    private String toastText;
    private float width;
    private float height;
    /** One HUD unit in pixels. */
    private float unit;

    /**
     * @param textures the textures to draw with; the caller keeps ownership of them
     * @param font the loaded font all text is drawn with
     * @param strings the text of the HUD and the menu screens, as loaded at start-up
     * @param window the size the overlay takes until the window reports its own
     */
    public HudRenderer(ProceduralTextures textures, PixelFont font, UiStrings strings, WindowDefaults window) {
        this(textures, font, strings, window, new SpriteBatch());
    }

    HudRenderer(ProceduralTextures textures, PixelFont font, UiStrings strings, WindowDefaults window,
                SpriteBatch batch) {
        if (strings == null) throw new IllegalArgumentException("Strings must not be null");
        if (window == null) throw new IllegalArgumentException("Window defaults must not be null");
        this.textures = textures;
        this.font = font;
        this.strings = strings;
        this.minimap = new MinimapRenderer(textures, font, strings);
        this.batch = batch;
        int initialWidth = Gdx.graphics != null ? Gdx.graphics.getWidth() : 0;
        int initialHeight = Gdx.graphics != null ? Gdx.graphics.getHeight() : 0;
        if (initialWidth <= 0 || initialHeight <= 0) {
            resize(window.width, window.height);
        } else {
            resize(initialWidth, initialHeight);
        }
    }

    float getWidth() {
        return width;
    }

    float getHeight() {
        return height;
    }

    float getUnit() {
        return unit;
    }

    Matrix4 getProjectionMatrix() {
        return batch != null ? batch.getProjectionMatrix() : fallbackProjection;
    }

    /**
     * @param windowWidth the window width in pixels
     * @param windowHeight the window height in pixels
     * @return one HUD unit in pixels, scaled from the shorter window dimension so at least 720 units fit on both axes
     */
    static float hudUnit(float windowWidth, float windowHeight) {
        return Math.min(windowWidth / HUD_REFERENCE_SPAN, windowHeight / HUD_REFERENCE_SPAN);
    }

    /**
     * Adapts the overlay to a new window size; non-positive dimensions (such as a minimised window) are ignored.
     * @param newWidth the new width in pixels
     * @param newHeight the new height in pixels
     */
    public void resize(int newWidth, int newHeight) {
        if (newWidth <= 0 || newHeight <= 0) return;
        width = newWidth;
        height = newHeight;
        unit = hudUnit(newWidth, newHeight);
        getProjectionMatrix().setToOrtho2D(0f, 0f, newWidth, newHeight);
    }

    /**
     * Draws the active combat HUD and/or menu screen driven by {@code menuState}.
     */
    public void render(GameSimulation simulation, MenuState menuState) {
        Gdx.gl.glDisable(GL20.GL_DEPTH_TEST);
        Gdx.gl.glDisable(GL20.GL_CULL_FACE);
        Gdx.gl.glEnable(GL20.GL_BLEND);
        Gdx.gl.glBlendFunc(GL20.GL_SRC_ALPHA, GL20.GL_ONE_MINUS_SRC_ALPHA);
        batch.begin();

        MenuState.Screen screen = menuState.getScreen();
        if (showsCombatHud(menuState, simulation)) {
            drawCombatHud(simulation, menuState.isMinimapEnabled(), menuState.getFov());
        }

        switch (screen) {
            case TITLE:
                drawTitleScreen(menuState);
                break;
            case STAGE_SELECT:
                drawStageSelectScreen(menuState);
                break;
            case DIFFICULTY_SELECT:
                drawDifficultyScreen(menuState);
                break;
            case BRIEFING:
                drawBriefingScreen(menuState);
                break;
            case PAUSED:
                drawPauseMenu(menuState);
                break;
            case SETTINGS:
                drawSettingsScreen(menuState);
                break;
            case DEAD:
                drawDeathScreen(simulation, menuState);
                break;
            case INTERMISSION:
                drawIntermissionScreen(menuState);
                break;
            case PLAYING:
            default:
                break;
        }

        batch.setColor(1f, 1f, 1f, 1f);
        batch.end();
    }

    /**
     * @param menuState the menu state
     * @param simulation the simulation behind the overlay, or null
     * @return whether the combat HUD is drawn under the active screen: in play, on the Pause and Death screens, and
     *     on Settings when it was opened from Pause over a stage that is not cleared. Settings opened from the Title
     *     has no run behind it, so it shows no combat HUD.
     */
    static boolean showsCombatHud(MenuState menuState, GameSimulation simulation) {
        if (simulation == null) return false;
        switch (menuState.getScreen()) {
            case PLAYING:
            case PAUSED:
            case DEAD:
                return true;
            case SETTINGS:
                return menuState.getSettingsReturnScreen() == MenuState.Screen.PAUSED && !simulation.isLevelCleared();
            default:
                return false;
        }
    }

    /**
     * @param menuState the menu state
     * @param simulation the simulation behind the overlay, or null
     * @return whether the HUD minimap is drawn over the combat HUD
     */
    static boolean showsMinimap(MenuState menuState, GameSimulation simulation) {
        return showsCombatHud(menuState, simulation) && menuState.isMinimapEnabled();
    }

    /**
     * Hit-tests which menu option row lies under OS window coordinates {@code (screenX, screenY)}, or -1 if none.
     */
    public static int optionAt(int windowWidth, int windowHeight, int screenX, int screenY, MenuState menuState) {
        if (windowWidth <= 0 || windowHeight <= 0 || menuState == null) return -1;
        List<String> options = menuState.getOptions();
        if (options.isEmpty()) return -1;
        float u = hudUnit(windowWidth, windowHeight);
        float uiX = screenX;
        float uiY = windowHeight - screenY;
        float boxHalf = MENU_BOX_WIDTH * 0.5f * u;
        float centreX = windowWidth * 0.5f;
        if (uiX < centreX - boxHalf || uiX > centreX + boxHalf) return -1;

        float firstBottomY = firstOptionBottomY(windowHeight, u, menuState.getScreen());
        float rowHeight = MENU_ROW_HEIGHT * u;
        float rowStep = MENU_ROW_STEP * u;
        for (int i = 0; i < options.size(); i++) {
            float bottom = firstBottomY - i * rowStep;
            if (uiY >= bottom && uiY <= bottom + rowHeight) return i;
        }
        return -1;
    }

    /**
     * On the Settings screen, clicking the left half of a setting row steps down (-1) and the right half steps up (+1).
     */
    public static int horizontalStepForClick(int windowWidth, int screenX) {
        return screenX < windowWidth * 0.5f ? -1 : 1;
    }

    private static float firstOptionBottomY(float h, float u, MenuState.Screen screen) {
        float cy = h * 0.5f;
        switch (screen) {
            case TITLE:
                return cy + 22f * u;
            case STAGE_SELECT:
                return cy + 78f * u;
            case DIFFICULTY_SELECT:
                return cy + 28f * u;
            case BRIEFING:
                return cy - 120f * u;
            case PAUSED:
                return cy + 44f * u;
            case SETTINGS:
                return cy + 94f * u;
            case DEAD:
                return cy - 48f * u;
            case INTERMISSION:
                return cy - 146f * u;
            default:
                return cy;
        }
    }

    // ---- Combat HUD ----

    private void drawCombatHud(GameSimulation simulation, boolean showMinimap, float verticalFovDegrees) {
        drawHurtOverlay(simulation.player);
        if (!simulation.player.isDead()) {
            drawCrosshair(simulation);
            drawInteractionPrompt(simulation);
            drawCharge(simulation);
        }
        drawVitalsAndEnergy(simulation);
        drawStatus(simulation);
        if (showMinimap) minimap.draw(batch, simulation, width, height, unit, verticalFovDegrees);
        drawBossBar(simulation);
        drawToast(simulation);
    }

    /**
     * @param height the window height in pixels
     * @param unit one HUD unit in pixels
     * @param weapon the active weapon slot, or null
     * @param zoom the normalized Rail Lance optic zoom
     * @return the bottom edge of the interaction prompt box in pixels, shifted below the Rail Lance zoom optic
     *     rangefinder brackets whenever those brackets are active
     */
    static float interactionPromptBottomY(float height, float unit, WeaponSlot weapon, float zoom) {
        float clampedZoom = Float.isFinite(zoom) ? MathUtils.clamp(zoom, 0f, 1f) : 0f;
        float offset = weapon == WeaponSlot.RAIL_LANCE && clampedZoom > RAIL_ZOOM_OPTIC_THRESHOLD ? 68f : 44f;
        return height * 0.5f - offset * unit;
    }

    /** @return the full height of the interaction prompt box in pixels for {@code unit}. */
    static float interactionPromptHeight(float unit) {
        return PixelFont.height(INTERACTION_PROMPT_PIXEL * unit) + INTERACTION_PROMPT_PAD_Y * 2f * unit;
    }

    /**
     * @param unit one HUD unit in pixels
     * @param zoom the normalized Rail Lance optic zoom
     * @return the half-span from screen centre to the outer edge of the Rail Lance rangefinder brackets in pixels
     */
    static float railZoomBracketSpan(float unit, float zoom) {
        float clampedZoom = Float.isFinite(zoom) ? MathUtils.clamp(zoom, 0f, 1f) : 0f;
        return MathUtils.lerp(42f, 34f, clampedZoom) * unit;
    }

    /**
     * @param height the window height in pixels
     * @param unit one HUD unit in pixels
     * @param weapon the active weapon slot, or null
     * @param zoom the normalized Rail Lance optic zoom
     * @return the bottom baseline of the charge bar / low-energy warning in pixels, staying below the interaction
     *     prompt box even when the Rail Lance zoom optic shifts that prompt downward
     */
    static float chargeBottomY(float height, float unit, WeaponSlot weapon, float zoom) {
        float clampedZoom = Float.isFinite(zoom) ? MathUtils.clamp(zoom, 0f, 1f) : 0f;
        float offset = weapon == WeaponSlot.RAIL_LANCE && clampedZoom > RAIL_ZOOM_OPTIC_THRESHOLD ? 90f : 70f;
        return height * 0.5f - offset * unit;
    }

    /**
     * @param prompt an interaction prompt
     * @return the red, green, blue and alpha it is drawn in. The colour goes by the prompt's kind and never by its
     *     words, so a reworded prompt keeps its colour.
     */
    static float[] promptColour(GameSimulation.InteractionPrompt prompt) {
        return sharedPromptColour(prompt).clone();
    }

    /** @return the array {@link #promptColour} copies, which drawing reads on every frame and must not write to */
    private static float[] sharedPromptColour(GameSimulation.InteractionPrompt prompt) {
        return prompt.kind == GameSimulation.InteractionPrompt.Kind.USABLE
            ? PROMPT_USABLE_COLOUR : PROMPT_BLOCKED_COLOUR;
    }

    private void drawInteractionPrompt(GameSimulation simulation) {
        GameSimulation.InteractionPrompt shown = simulation.getInteractionPrompt();
        if (shown == null) return;
        String prompt = strings.prompt(shown);
        float pixel = INTERACTION_PROMPT_PIXEL * unit;
        float textW = PixelFont.width(prompt, pixel);
        float padX = INTERACTION_PROMPT_PAD_X * unit;
        float padY = INTERACTION_PROMPT_PAD_Y * unit;
        float boxW = textW + padX * 2f;
        float boxH = interactionPromptHeight(unit);
        float cx = width * 0.5f;
        float bottom = interactionPromptBottomY(height, unit, simulation.getActiveWeapon(), simulation.getZoom());
        batch.setColor(0.06f, 0.08f, 0.14f, 0.78f);
        rect(cx - boxW * 0.5f, bottom, boxW, boxH);
        float[] colour = sharedPromptColour(shown);
        batch.setColor(colour[0], colour[1], colour[2], colour[3]);
        font.drawCentred(batch, textures.white, prompt, cx, bottom + padY, pixel);
    }

    private void drawHurtOverlay(Player player) {
        if (player.isDead()) {
            batch.setColor(0.45f, 0.04f, 0.06f, 0.42f);
            rect(0f, 0f, width, height);
            return;
        }
        float flash = player.getHurtFlash();
        if (flash <= 0f) return;

        // Subtle whole-screen wash.
        batch.setColor(0.85f, 0.10f, 0.12f, flash * 0.18f);
        rect(0f, 0f, width, height);

        float border = 26f * unit;
        if (!player.hasDirectionalHurt()) {
            batch.setColor(0.95f, 0.15f, 0.15f, flash * 0.45f);
            rect(0f, height - border, width, border);
            rect(0f, 0f, width, border);
            rect(0f, 0f, border, height);
            rect(width - border, 0f, border, height);
            return;
        }

        double rad = Math.toRadians(player.getHurtRelativeYaw());
        float frontWeight = Math.max(0f, (float) Math.cos(rad));
        float backWeight = Math.max(0f, (float) -Math.cos(rad));
        float rightWeight = Math.max(0f, (float) Math.sin(rad));
        float leftWeight = Math.max(0f, (float) -Math.sin(rad));

        if (frontWeight > 0.05f) {
            batch.setColor(1f, 0.15f, 0.15f, flash * (0.25f + 0.55f * frontWeight));
            rect(0f, height - border, width, border);
        }
        if (backWeight > 0.05f) {
            batch.setColor(1f, 0.15f, 0.15f, flash * (0.25f + 0.55f * backWeight));
            rect(0f, 0f, width, border);
        }
        if (leftWeight > 0.05f) {
            batch.setColor(1f, 0.15f, 0.15f, flash * (0.25f + 0.55f * leftWeight));
            rect(0f, 0f, border, height);
        }
        if (rightWeight > 0.05f) {
            batch.setColor(1f, 0.15f, 0.15f, flash * (0.25f + 0.55f * rightWeight));
            rect(width - border, 0f, border, height);
        }
    }

    private void drawCrosshair(GameSimulation simulation) {
        BlastCharger charger = simulation.charger;
        float centreX = width * 0.5f;
        float centreY = height * 0.5f;
        float thick = 2f * unit;
        if (simulation.getActiveWeapon() == WeaponSlot.SCATTERGUN) {
            float spread = (15f + simulation.scattergun.getPump() * 3.5f) * unit;
            float bracketArm = 6f * unit;
            batch.setColor(1f, 0.86f, 0.62f, 0.92f);
            rect(centreX - thick * 0.5f, centreY - thick * 0.5f, thick, thick);
            // Wide spread brackets indicating close-quarters pellet cone.
            rect(centreX - spread, centreY + spread - thick, bracketArm, thick);
            rect(centreX - spread, centreY + spread - bracketArm, thick, bracketArm);
            rect(centreX + spread - bracketArm, centreY + spread - thick, bracketArm, thick);
            rect(centreX + spread - thick, centreY + spread - bracketArm, thick, bracketArm);
            rect(centreX - spread, centreY - spread, bracketArm, thick);
            rect(centreX - spread, centreY - spread, thick, bracketArm);
            rect(centreX + spread - bracketArm, centreY - spread, bracketArm, thick);
            rect(centreX + spread - thick, centreY - spread, thick, bracketArm);
        } else if (simulation.getActiveWeapon() == WeaponSlot.RAIL_LANCE) {
            float zoom = MathUtils.clamp(simulation.getZoom(), 0f, 1f);
            float charge = simulation.railLance.getChargeProgress();
            boolean ready = charge >= 0.999f;
            float gap = MathUtils.lerp(5f, 3f, zoom) * unit;
            float arm = MathUtils.lerp(11f, 20f, zoom) * unit;
            batch.setColor(0.26f, 0.96f, 1.00f, 0.96f);
            rect(centreX - thick * 0.5f, centreY - thick * 0.5f, thick, thick);
            rect(centreX - gap - arm, centreY - thick * 0.5f, arm, thick);
            rect(centreX + gap, centreY - thick * 0.5f, arm, thick);
            rect(centreX - thick * 0.5f, centreY - gap - arm * 0.7f, thick, arm * 0.7f);
            rect(centreX - thick * 0.5f, centreY + gap, thick, arm * 0.7f);

            // Tactical zoom optic rangefinder brackets and horizontal tick ladder.
            if (zoom > RAIL_ZOOM_OPTIC_THRESHOLD) {
                float span = railZoomBracketSpan(unit, zoom);
                float corner = 12f * unit;
                batch.setColor(0.24f, 0.92f, 1.00f, 0.80f * zoom);
                rect(centreX - span, centreY + span - thick, corner, thick);
                rect(centreX - span, centreY + span - corner, thick, corner);
                rect(centreX + span - corner, centreY + span - thick, corner, thick);
                rect(centreX + span - thick, centreY + span - corner, thick, corner);
                rect(centreX - span, centreY - span, corner, thick);
                rect(centreX - span, centreY - span, thick, corner);
                rect(centreX + span - corner, centreY - span, corner, thick);
                rect(centreX + span - thick, centreY - span, thick, corner);
                // Rangefinder elevation sub-ticks.
                for (int r = 1; r <= 2; r++) {
                    float ty = centreY - (7f * r) * unit;
                    float tw = (9f - r * 2.5f) * unit;
                    rect(centreX - tw, ty, tw * 2f, 1.5f * unit);
                }
            }
            // Flanking vertical capacitor charge gauge on right/left of optic without overlapping [E] prompt.
            if (!ready || zoom > RAIL_ZOOM_OPTIC_THRESHOLD) {
                float gaugeX = centreX + MathUtils.lerp(22f, 44f, zoom) * unit;
                float gaugeH = 22f * unit;
                float gaugeY = centreY - gaugeH * 0.5f;
                batch.setColor(0.08f, 0.12f, 0.18f, 0.75f);
                rect(gaugeX - unit, gaugeY - unit, 5f * unit, gaugeH + 2f * unit);
                if (ready) batch.setColor(0.26f, 0.96f, 1.00f, 0.95f);
                else batch.setColor(1.00f, 0.48f, 0.18f, 0.92f);
                rect(gaugeX, gaugeY, 3f * unit, gaugeH * charge);
            }
        } else {
            float gap = 6f * unit;
            float length = 8f * unit;
            if (charger.isReadyToFire()) batch.setColor(0.8f, 0.55f, 1f, 0.95f);
            else batch.setColor(0.8f, 1f, 0.88f, 0.9f);
            rect(centreX - gap - length, centreY - thick * 0.5f, length, thick);
            rect(centreX + gap, centreY - thick * 0.5f, length, thick);
            rect(centreX - thick * 0.5f, centreY - gap - length, thick, length);
            rect(centreX - thick * 0.5f, centreY + gap, thick, length);
            rect(centreX - thick * 0.5f, centreY - thick * 0.5f, thick, thick);
        }

        float hit = simulation.getHitMarker();
        if (hit > 0f) {
            if (simulation.isHitMarkerKill()) batch.setColor(1f, 0.32f, 0.36f, hit);
            else batch.setColor(1f, 0.96f, 0.82f, hit);
            float d = (11f + (1f - hit) * 4f) * unit;
            float s = 4f * unit;
            float t = 2f * unit;
            // Four corner brackets around the crosshair.
            rect(centreX - d - s, centreY + d, s, t);
            rect(centreX - d - t, centreY + d - s + t, t, s);
            rect(centreX + d, centreY + d, s, t);
            rect(centreX + d, centreY + d - s + t, t, s);
            rect(centreX - d - s, centreY - d - t, s, t);
            rect(centreX - d - t, centreY - d, t, s);
            rect(centreX + d, centreY - d - t, s, t);
            rect(centreX + d, centreY - d, t, s);
        }
    }

    private void drawCharge(GameSimulation simulation) {
        BlastCharger charger = simulation.charger;
        Player player = simulation.player;
        float centreX = width * 0.5f;
        float barWidth = 180f * unit;
        float barHeight = 10f * unit;
        float left = centreX - barWidth * 0.5f;
        float bottom = chargeBottomY(height, unit, simulation.getActiveWeapon(), simulation.getZoom());
        if (charger.isRefused() || player.isDashRefused()) {
            batch.setColor(1f, 0.35f, 0.3f, 0.95f);
            font.drawCentred(batch, textures.white, strings.get(UiText.HUD_LOW_ENERGY), centreX, bottom, 3f * unit);
            return;
        }
        if (!charger.isCharging()) return;
        batch.setColor(0f, 0f, 0f, 0.6f);
        rect(left - 2f * unit, bottom - 2f * unit, barWidth + 4f * unit, barHeight + 4f * unit);
        if (charger.isReadyToFire()) batch.setColor(0.78f, 0.5f, 1f, 1f);
        else batch.setColor(0.5f, 0.5f, 0.6f, 1f);
        rect(left, bottom, barWidth * charger.getCharge(), barHeight);
        batch.setColor(1f, 1f, 1f, 0.9f);
        rect(left + barWidth * charger.getMinCharge() - unit, bottom - 4f * unit, 2f * unit,
            barHeight + 8f * unit);
        font.drawCentred(batch, textures.white, strings.get(UiText.HUD_CHARGE), centreX, bottom - 26f * unit,
            3f * unit);
    }

    private void drawVitalsAndEnergy(GameSimulation simulation) {
        Player player = simulation.player;
        EnergyMeter energy = simulation.energy;
        BlastCharger charger = simulation.charger;
        float left = 28f * unit;
        float energyBottom = 24f * unit;
        float segmentWidth = 20f * unit;
        float segmentHeight = 18f * unit;
        float gap = 4f * unit;
        float span = ENERGY_SEGMENTS * (segmentWidth + gap) - gap;

        boolean low = !energy.canSpend(charger.getMinCost());
        float filled = energy.getFraction() * ENERGY_SEGMENTS;
        for (int i = 0; i < ENERGY_SEGMENTS; i++) {
            float x = left + i * (segmentWidth + gap);
            batch.setColor(0.08f, 0.07f, 0.14f, 0.8f);
            rect(x, energyBottom, segmentWidth, segmentHeight);
            float part = MathUtils.clamp(filled - i, 0f, 1f);
            if (part <= 0f) continue;
            if (low) batch.setColor(0.9f, 0.3f, 0.3f, 1f);
            else batch.setColor(0.6f, 0.38f, 1f, 1f);
            rect(x, energyBottom, segmentWidth * part, segmentHeight);
            batch.setColor(1f, 1f, 1f, 0.25f);
            rect(x, energyBottom + segmentHeight - 3f * unit, segmentWidth * part, 3f * unit);
        }
        // Dash threshold marker at 25% of the energy bar.
        float dashFrac = Player.DASH_COST / energy.getCapacity();
        batch.setColor(0.85f, 0.95f, 1f, 0.7f);
        rect(left + span * dashFrac - unit, energyBottom - 2f * unit, 2f * unit, segmentHeight + 4f * unit);

        if (charger.isReadyToFire()) {
            float cost = charger.costFor(charger.getCharge()) / energy.getCapacity();
            float from = Math.max(0f, energy.getFraction() - cost);
            batch.setColor(1f, 1f, 1f, 0.85f);
            rect(left + span * from, energyBottom - 6f * unit, span * (energy.getFraction() - from), 3f * unit);
        }
        batch.setColor(0.8f, 0.72f, 1f, 0.95f);
        font.draw(batch, textures.white, strings.get(UiText.HUD_ENERGY), left,
            energyBottom + segmentHeight + 6f * unit, 2.3f * unit);

        // Health bar.
        float healthY = 68f * unit;
        float barW = 148f * unit;
        float barH = 14f * unit;
        float hpFrac = MathUtils.clamp(player.getHealth() / Player.MAX_HEALTH, 0f, 1f);
        batch.setColor(0.08f, 0.09f, 0.11f, 0.82f);
        rect(left, healthY, barW, barH);
        if (player.getHealth() <= 25f) batch.setColor(0.95f, 0.26f, 0.28f, 1f);
        else batch.setColor(0.24f, 0.88f, 0.56f, 1f);
        rect(left, healthY, barW * hpFrac, barH);
        batch.setColor(0.9f, 1f, 0.94f, 0.95f);
        int shownHp = player.isDead() ? 0 : Math.max(1, Math.round(player.getHealth()));
        font.draw(batch, textures.white, strings.format(UiText.HUD_HP, Integer.toString(shownHp)),
            left + barW + 10f * unit, healthY + unit, 2.6f * unit);

        // Armor bar.
        float armorY = 90f * unit;
        float armorFrac = MathUtils.clamp(player.getArmor() / Player.MAX_ARMOR, 0f, 1f);
        batch.setColor(0.07f, 0.09f, 0.14f, 0.82f);
        rect(left, armorY, barW, barH);
        if (armorFrac > 0f) {
            batch.setColor(0.26f, 0.72f, 1f, 1f);
            rect(left, armorY, barW * armorFrac, barH);
        }
        batch.setColor(0.72f, 0.90f, 1f, 0.95f);
        font.draw(batch, textures.white,
            strings.format(UiText.HUD_ARMOR, Integer.toString(Math.round(player.getArmor()))),
            left + barW + 10f * unit, armorY + unit, 2.6f * unit);

        // Active weapon slots and held security keycards.
        WeaponSlot selectedWeapon = simulation.getTargetWeapon();
        boolean pistolSelected = selectedWeapon == WeaponSlot.PISTOL;
        batch.setColor(pistolSelected ? 0.80f : 0.45f, pistolSelected ? 1f : 0.58f,
            pistolSelected ? 0.88f : 0.52f, pistolSelected ? 0.95f : 0.65f);
        font.draw(batch, textures.white, weaponRow(pistolSelected, strings.get(UiText.HUD_WEAPON_PISTOL)),
            left, 114f * unit, 2.4f * unit);

        boolean showScatterRow = simulation.hasScattergun() || simulation.getShells() > 0;
        if (showScatterRow) {
            boolean scatterSelected = selectedWeapon == WeaponSlot.SCATTERGUN;
            if (!simulation.hasScattergun()) {
                batch.setColor(0.75f, 0.58f, 0.44f, 0.72f);
                font.draw(batch, textures.white, weaponRow(false, ammoRow(strings, UiText.HUD_AMMO_SHELLS,
                    simulation.getShells(), simulation.getMaxShells())), left, 136f * unit, 2.4f * unit);
            } else {
                if (simulation.getShells() <= 0) {
                    batch.setColor(0.95f, 0.42f, 0.36f, scatterSelected ? 0.96f : 0.72f);
                } else if (scatterSelected) {
                    batch.setColor(1f, 0.82f, 0.45f, 0.98f);
                } else {
                    batch.setColor(0.55f, 0.52f, 0.48f, 0.68f);
                }
                font.draw(batch, textures.white, weaponRow(scatterSelected, ammoRow(strings,
                    UiText.HUD_WEAPON_SCATTERGUN, simulation.getShells(), simulation.getMaxShells())),
                    left, 136f * unit, 2.4f * unit);
            }
        }

        boolean showRailRow = simulation.hasRailLance() || simulation.getSlugs() > 0;
        if (showRailRow) {
            boolean railSelected = selectedWeapon == WeaponSlot.RAIL_LANCE;
            float railY = showScatterRow ? 158f * unit : 136f * unit;
            if (!simulation.hasRailLance()) {
                batch.setColor(0.42f, 0.72f, 0.80f, 0.72f);
                font.draw(batch, textures.white, weaponRow(false, ammoRow(strings, UiText.HUD_AMMO_SLUGS,
                    simulation.getSlugs(), simulation.getMaxSlugs())), left, railY, 2.4f * unit);
            } else {
                if (simulation.getSlugs() <= 0) {
                    batch.setColor(0.95f, 0.42f, 0.36f, railSelected ? 0.96f : 0.72f);
                } else if (railSelected) {
                    batch.setColor(0.32f, 0.96f, 1.00f, 0.98f);
                } else {
                    batch.setColor(0.44f, 0.62f, 0.68f, 0.70f);
                }
                font.draw(batch, textures.white, weaponRow(railSelected, ammoRow(strings,
                    UiText.HUD_WEAPON_RAIL_LANCE, simulation.getSlugs(), simulation.getMaxSlugs())),
                    left, railY, 2.4f * unit);
            }
        }

        int weaponRows = 1 + (showScatterRow ? 1 : 0) + (showRailRow ? 1 : 0);
        float keyX = left;
        float keyY = (114f + weaponRows * 22f + 2f) * unit;
        float keySize = 2.4f * unit;
        if (simulation.hasKeycard(Pickup.Kind.AMBER_KEYCARD)) {
            batch.setColor(1f, 0.76f, 0.22f, 0.96f);
            String badge = strings.get(UiText.HUD_KEY_AMBER);
            font.draw(batch, textures.white, badge, keyX, keyY, keySize);
            keyX += PixelFont.width(badge, keySize) + 14f * unit;
        }
        if (simulation.hasKeycard(Pickup.Kind.CYAN_KEYCARD)) {
            batch.setColor(0.26f, 0.94f, 1f, 0.96f);
            font.draw(batch, textures.white, strings.get(UiText.HUD_KEY_CYAN), keyX, keyY, keySize);
        }
    }

    /** @return a row of the weapon list, behind the marker of the selected weapon or the room kept for it. */
    static String weaponRow(boolean selected, String row) {
        return (selected ? "> " : "  ") + row;
    }

    /** @return a line that shows an amount of ammunition and the most that is carried. */
    static String ammoRow(UiStrings strings, UiText row, int ammo, int max) {
        return strings.format(row, Integer.toString(ammo), Integer.toString(max));
    }

    /**
     * Picks the row of the objective line. It sits under the status row while the longest line ends left of the
     * room kept for the boss bar's panel and the toast boxes. On a narrower window it drops to a row below the
     * toast that is shown under the boss bar, where nothing is drawn across the window.
     * @param width the window width in pixels
     * @param height the window height in pixels
     * @param unit one HUD unit in pixels
     * @return the bottom edge of the objective line in pixels
     */
    static float objectiveBottomY(float width, float height, float unit) {
        if (OBJECTIVE_RIGHT_LIMIT * unit <= width * 0.5f - CENTRE_HALF_WIDTH * unit) {
            return height - STATUS_TOP * unit - PixelFont.height(STATUS_PIXEL * unit) - OBJECTIVE_ROW_DROP * unit;
        }
        return height - OBJECTIVE_LOW_TOP * unit - PixelFont.height(OBJECTIVE_PIXEL * unit);
    }

    private void drawStatus(GameSimulation simulation) {
        float pixel = STATUS_PIXEL * unit;
        float topY = height - STATUS_TOP * unit - PixelFont.height(pixel);
        if (!simulation.enemies.isEmpty()) {
            int remaining = simulation.getEnemiesRemaining();
            if (remaining > 0) batch.setColor(1f, 0.52f, 0.46f, 0.95f);
            else batch.setColor(0.55f, 1f, 0.82f, 0.95f);
            String hostiles = strings.format(UiText.HUD_HOSTILES, Integer.toString(remaining),
                Integer.toString(simulation.enemies.size()));
            font.draw(batch, textures.white, hostiles, 28f * unit, topY, pixel);
        } else {
            batch.setColor(0.8f, 1f, 0.88f, 0.92f);
            String targets = strings.format(UiText.HUD_TARGETS,
                Integer.toString(simulation.getTargetsRemaining()), Integer.toString(simulation.targets.size()));
            font.draw(batch, textures.white, targets, 28f * unit, topY, pixel);
        }

        GameSimulation.Objective objective = simulation.getObjective();
        if (objective != null) {
            float objectivePixel = OBJECTIVE_PIXEL * unit;
            float objectiveY = objectiveBottomY(width, height, unit);
            batch.setColor(0.55f, 0.95f, 1f, 0.9f);
            // The whole line was put together at load, so none is built anew each frame.
            font.draw(batch, textures.white, strings.objectiveLine(objective), STATUS_LEFT * unit, objectiveY,
                objectivePixel);
        }

        String timer = strings.format(UiText.HUD_TIME, TextFormat.stopwatch(simulation.getTime()));
        batch.setColor(0.86f, 0.88f, 0.95f, 0.9f);
        font.draw(batch, textures.white, timer,
            width - 28f * unit - PixelFont.width(timer, pixel), topY, pixel);
    }

    private void drawBossBar(GameSimulation simulation) {
        Enemy boss = simulation.getActiveBoss();
        if (boss == null) return;
        boolean overdrive = boss.isOverdrive();
        float frac = Math.max(0f, Math.min(1f, boss.getHealth() / boss.getMaxHealth()));

        float barW = BOSS_BAR_WIDTH * unit;
        float barH = 14f * unit;
        float left = (width - barW) * 0.5f;
        float barBottom = height - BOSS_BAR_BOTTOM * unit;
        float labelPix = BOSS_LABEL_PIXEL * unit;
        float labelH = PixelFont.height(labelPix);

        batch.setColor(0.05f, 0.06f, 0.11f, 0.80f);
        rect(left - BOSS_PANEL_PAD_X * unit, barBottom - BOSS_PANEL_PAD_Y * unit,
            barW + 2f * BOSS_PANEL_PAD_X * unit, barH + labelH + 22f * unit);

        String title = strings.get(overdrive ? UiText.HUD_BOSS_OVERDRIVE : UiText.HUD_BOSS);
        if (overdrive) batch.setColor(0.42f, 0.86f, 1.00f, 0.98f);
        else batch.setColor(1.00f, 0.44f, 0.28f, 0.98f);
        font.drawCentred(batch, textures.white, title, width * 0.5f, barBottom + barH + 6f * unit, labelPix);

        batch.setColor(0.14f, 0.15f, 0.22f, 0.95f);
        rect(left, barBottom, barW, barH);
        if (overdrive) batch.setColor(0.40f, 0.58f, 1.00f, 0.96f);
        else batch.setColor(0.96f, 0.28f, 0.18f, 0.96f);
        rect(left, barBottom, barW * frac, barH);

        // 50% Phase 2 Overdrive threshold notch.
        batch.setColor(0.92f, 0.95f, 1f, 0.78f);
        rect(left + barW * 0.5f - 1f * unit, barBottom - 1f * unit, 2f * unit, barH + 2f * unit);
    }

    private void drawToast(GameSimulation simulation) {
        GameSimulation.Toast toast = simulation.getToast();
        if (toast == null) return;
        if (toast != shownToast) {
            shownToast = toast;
            toastText = strings.toast(toast);
        }
        String msg = toastText;
        float alpha = simulation.getToastAlpha();
        float pixel = TOAST_PIXEL * unit;
        float textW = PixelFont.width(msg, pixel);
        float padX = TOAST_PAD_X * unit;
        float padY = TOAST_PAD_Y * unit;
        float boxW = textW + padX * 2f;
        float boxH = PixelFont.height(pixel) + padY * 2f;
        float cx = width * 0.5f;
        float y = height - (simulation.getActiveBoss() != null ? TOAST_BOTTOM_UNDER_BOSS : TOAST_BOTTOM) * unit;
        batch.setColor(0.06f, 0.08f, 0.14f, 0.78f * alpha);
        rect(cx - boxW * 0.5f, y, boxW, boxH);
        batch.setColor(0.55f, 1f, 0.82f, alpha);
        font.drawCentred(batch, textures.white, msg, cx, y + padY, pixel);
    }

    // ---- Menu Screens ----

    private void drawTitleScreen(MenuState menuState) {
        drawBackdropShade(0.52f);
        float cx = width * 0.5f;
        float cy = height * 0.5f;

        drawPanel(cx, cy + 6f * unit, TITLE_PANEL_WIDTH * unit, 488f * unit);
        batch.setColor(0.76f, 0.56f, 1f, 1f);
        font.drawCentred(batch, textures.white, strings.get(UiText.SCREEN_TITLE_NAME), cx, cy + 168f * unit,
            TITLE_NAME_PIXEL * unit);
        batch.setColor(0.62f, 0.95f, 0.88f, 0.95f);
        font.drawCentred(batch, textures.white, strings.get(UiText.SCREEN_TITLE_SUBTITLE), cx, cy + 130f * unit,
            TITLE_SUBTITLE_PIXEL * unit);

        drawOptionRows(menuState, firstOptionBottomY(height, unit, MenuState.Screen.TITLE));
        drawQuitPrompt(menuState, cx, cy - 222f * unit);

        batch.setColor(0.65f, 0.70f, 0.78f, 0.85f);
        font.drawCentred(batch, textures.white, strings.get(UiText.SCREEN_TITLE_FOOTER), cx, 32f * unit,
            TITLE_FOOTER_PIXEL * unit);
    }

    private void drawStageSelectScreen(MenuState menuState) {
        drawBackdropShade(0.62f);
        float cx = width * 0.5f;
        float cy = height * 0.5f;
        drawPanel(cx, cy + 4f * unit, STAGE_SELECT_PANEL_WIDTH * unit, 468f * unit);
        batch.setColor(0.55f, 0.95f, 1f, 1f);
        font.drawCentred(batch, textures.white, strings.get(UiText.SCREEN_STAGE_SELECT_TITLE), cx,
            cy + 174f * unit, STAGE_SELECT_TITLE_PIXEL * unit);
        batch.setColor(0.70f, 0.78f, 0.90f, 0.88f);
        font.drawCentred(batch, textures.white, strings.get(UiText.SCREEN_STAGE_SELECT_HINT), cx,
            cy + 144f * unit, STAGE_SELECT_HINT_PIXEL * unit);
        drawOptionRows(menuState, firstOptionBottomY(height, unit, MenuState.Screen.STAGE_SELECT));
        // Medals of the highlighted stage, in the strip under BACK.
        String detail = menuState.getStageSelectDetail();
        if (detail != null) {
            batch.setColor(1f, 0.88f, 0.36f, 0.95f);
            font.drawCentred(batch, textures.white, detail, cx, cy - 208f * unit, MEDAL_PIXEL * unit);
        }
    }

    private void drawDifficultyScreen(MenuState menuState) {
        drawBackdropShade(0.62f);
        float cx = width * 0.5f;
        float cy = height * 0.5f;
        drawPanel(cx, cy, DIFFICULTY_PANEL_WIDTH * unit, 340f * unit);
        batch.setColor(0.78f, 0.62f, 1f, 1f);
        font.drawCentred(batch, textures.white, strings.get(UiText.SCREEN_DIFFICULTY_TITLE), cx,
            cy + 112f * unit, DIFFICULTY_TITLE_PIXEL * unit);
        drawOptionRows(menuState, firstOptionBottomY(height, unit, MenuState.Screen.DIFFICULTY_SELECT));
    }

    private void drawBriefingScreen(MenuState menuState) {
        drawBackdropShade(0.68f);
        Levels.Stage stage = menuState.getActiveStage();
        float cx = width * 0.5f;
        float cy = height * 0.5f;
        drawPanel(cx, cy, BRIEFING_PANEL_WIDTH * unit, 420f * unit);

        batch.setColor(0.55f, 0.95f, 1f, 1f);
        font.drawCentred(batch, textures.white, stage.title, cx, cy + 144f * unit, BRIEFING_TITLE_PIXEL * unit);

        String meta = briefingMetaLine(strings, menuState.getActiveLoadout().difficulty, stage.parSeconds);
        batch.setColor(0.76f, 0.72f, 0.95f, 0.9f);
        font.drawCentred(batch, textures.white, meta, cx, cy + 108f * unit, BRIEFING_META_PIXEL * unit);

        // Terminal log box.
        batch.setColor(0.04f, 0.06f, 0.10f, 0.85f);
        rect(cx - BRIEFING_LOG_WIDTH * 0.5f * unit, cy - 42f * unit, BRIEFING_LOG_WIDTH * unit, 128f * unit);
        batch.setColor(0.68f, 0.98f, 0.82f, 0.95f);
        List<String> briefing = menuState.getActiveBriefing();
        for (int i = 0; i < briefing.size(); i++) {
            font.drawCentred(batch, textures.white, briefingRow(briefing.get(i)), cx,
                cy + (52f - i * 32f) * unit, BRIEFING_LINE_PIXEL * unit);
        }

        drawOptionRows(menuState, firstOptionBottomY(height, unit, MenuState.Screen.BRIEFING));
    }

    private void drawPauseMenu(MenuState menuState) {
        drawBackdropShade(0.62f);
        float cx = width * 0.5f;
        float cy = height * 0.5f;
        drawPanel(cx, cy - 10f * unit, PAUSE_PANEL_WIDTH * unit, 370f * unit);
        batch.setColor(0.8f, 0.72f, 1f, 1f);
        font.drawCentred(batch, textures.white, strings.get(UiText.SCREEN_PAUSED_TITLE), cx, cy + 114f * unit,
            PAUSE_TITLE_PIXEL * unit);
        drawOptionRows(menuState, firstOptionBottomY(height, unit, MenuState.Screen.PAUSED));
        drawQuitPrompt(menuState, cx, cy - 178f * unit);
    }

    private void drawSettingsScreen(MenuState menuState) {
        drawBackdropShade(0.70f);
        float cx = width * 0.5f;
        float cy = height * 0.5f;
        drawPanel(cx, cy, SETTINGS_PANEL_WIDTH * unit, 472f * unit);
        batch.setColor(0.78f, 0.65f, 1f, 1f);
        font.drawCentred(batch, textures.white, strings.get(UiText.SCREEN_SETTINGS_TITLE), cx, cy + 168f * unit,
            SETTINGS_TITLE_PIXEL * unit);
        batch.setColor(0.66f, 0.78f, 0.86f, 0.88f);
        font.drawCentred(batch, textures.white, strings.get(UiText.SCREEN_SETTINGS_HELP), cx,
            cy + 140f * unit, SETTINGS_HELP_PIXEL * unit);
        drawOptionRows(menuState, firstOptionBottomY(height, unit, MenuState.Screen.SETTINGS));
    }

    private void drawDeathScreen(GameSimulation simulation, MenuState menuState) {
        drawBackdropShade(0.55f);
        float cx = width * 0.5f;
        float cy = height * 0.5f;
        drawPanel(cx, cy, DEATH_PANEL_WIDTH * unit, 310f * unit);
        batch.setColor(1f, 0.30f, 0.34f, 1f);
        font.drawCentred(batch, textures.white, strings.get(UiText.SCREEN_DEAD_TITLE), cx, cy + 78f * unit,
            DEATH_TITLE_PIXEL * unit);
        if (simulation != null) {
            String sub = simulation.enemies.isEmpty()
                ? survivedLine(strings, simulation.getTime(), false, simulation.getTargetsDestroyed(),
                    simulation.targets.size())
                : survivedLine(strings, simulation.getTime(), true, simulation.getEnemiesDestroyed(),
                    simulation.enemies.size());
            batch.setColor(0.90f, 0.80f, 0.82f, 0.92f);
            font.drawCentred(batch, textures.white, sub, cx, cy + 32f * unit, DEATH_SURVIVED_PIXEL * unit);
        }
        drawOptionRows(menuState, firstOptionBottomY(height, unit, MenuState.Screen.DEAD));
    }

    private void drawIntermissionScreen(MenuState menuState) {
        drawBackdropShade(0.72f);
        float cx = width * 0.5f;
        float cy = height * 0.5f;
        drawPanel(cx, cy - 6f * unit, TALLY_PANEL_WIDTH * unit, 480f * unit);

        Levels.Stage stage = menuState.getActiveStage();
        SaveData.CompletionSummary sum = menuState.getLastSummary();
        boolean campaignDone = menuState.isCampaignCompleted();

        if (campaignDone) {
            batch.setColor(1f, 0.88f, 0.36f, 1f);
            font.drawCentred(batch, textures.white, strings.get(UiText.SCREEN_COMPLETE_CAMPAIGN), cx,
                cy + 174f * unit, TALLY_CAMPAIGN_TITLE_PIXEL * unit);
        } else {
            batch.setColor(0.52f, 1f, 0.82f, 1f);
            font.drawCentred(batch, textures.white, strings.get(UiText.SCREEN_COMPLETE_STAGE), cx,
                cy + 174f * unit, TALLY_TITLE_PIXEL * unit);
        }
        batch.setColor(0.78f, 0.84f, 1f, 0.95f);
        font.drawCentred(batch, textures.white, stage.title, cx, cy + 138f * unit, TALLY_STAGE_PIXEL * unit);
        if (campaignDone) {
            batch.setColor(0.52f, 1f, 0.82f, 0.95f);
            font.drawCentred(batch, textures.white, strings.get(UiText.SCREEN_COMPLETE_CAMPAIGN_NOTE), cx,
                cy + 112f * unit, TALLY_NOTE_PIXEL * unit);
        }

        if (sum != null) {
            float statPix = TALLY_STAT_PIXEL * unit;
            boolean hostiles = !stage.map.getEnemySpawns().isEmpty();

            batch.setColor(0.92f, 0.95f, 1f, 0.95f);
            font.drawCentred(batch, textures.white, tallyTimeLine(strings, sum, stage.parSeconds), cx,
                cy + 86f * unit, statPix);
            if (sum.newBestTime) batch.setColor(1f, 0.88f, 0.36f, 1f);
            font.drawCentred(batch, textures.white,
                tallyBestLine(strings, sum, menuState.getActiveLoadout().difficulty), cx, cy + 62f * unit,
                statPix);
            batch.setColor(0.92f, 0.95f, 1f, 0.95f);
            font.drawCentred(batch, textures.white, tallyClearedLine(strings, sum, hostiles), cx,
                cy + 38f * unit, statPix);
            font.drawCentred(batch, textures.white, tallyStatsLine(strings, sum), cx, cy + 14f * unit, statPix);

            if (sum.rank == 'S') batch.setColor(1f, 0.88f, 0.36f, 1f);
            else if (sum.rank == 'A') batch.setColor(0.45f, 0.96f, 0.85f, 1f);
            else batch.setColor(0.80f, 0.68f, 1f, 1f);
            font.drawCentred(batch, textures.white, tallyRankLine(strings, sum), cx, cy - 28f * unit,
                TALLY_RANK_PIXEL * unit);

            if (!sum.newMedals.isEmpty()) batch.setColor(1f, 0.88f, 0.36f, 1f);
            else if (!sum.medals.isEmpty()) batch.setColor(0.52f, 1f, 0.82f, 0.95f);
            else batch.setColor(0.70f, 0.78f, 0.90f, 0.88f);
            font.drawCentred(batch, textures.white, tallyMedalLine(strings, sum), cx, cy - 58f * unit,
                MEDAL_PIXEL * unit);
        }

        drawOptionRows(menuState, firstOptionBottomY(height, unit, MenuState.Screen.INTERMISSION));
    }

    /** Draws the quit confirmation under the option rows while a first press of the quit key is pending. */
    private void drawQuitPrompt(MenuState menuState, float cx, float y) {
        if (!menuState.isQuitPromptVisible()) return;
        batch.setColor(1f, 0.72f, 0.30f, 0.95f);
        font.drawCentred(batch, textures.white, strings.get(UiText.MENU_QUIT_PROMPT), cx, y,
            QUIT_PROMPT_PIXEL * unit);
    }

    private void drawBackdropShade(float alpha) {
        batch.setColor(0.02f, 0.02f, 0.05f, alpha);
        rect(0f, 0f, width, height);
    }

    private void drawPanel(float cx, float cy, float w, float h) {
        float left = cx - w * 0.5f;
        float bottom = cy - h * 0.5f;
        batch.setColor(0.38f, 0.30f, 0.62f, 0.75f);
        rect(left - 2f * unit, bottom - 2f * unit, w + 4f * unit, h + 4f * unit);
        batch.setColor(0.06f, 0.07f, 0.12f, 0.90f);
        rect(left, bottom, w, h);
    }

    private void drawOptionRows(MenuState menuState, float firstBottomY) {
        List<String> options = menuState.getOptions();
        float cx = width * 0.5f;
        float boxW = MENU_BOX_WIDTH * unit;
        float rowH = MENU_ROW_HEIGHT * unit;
        float rowStep = MENU_ROW_STEP * unit;
        float textSize = OPTION_PIXEL * unit;

        for (int i = 0; i < options.size(); i++) {
            boolean selected = i == menuState.getSelectedIndex();
            float bottom = firstBottomY - i * rowStep;
            if (selected) {
                batch.setColor(0.42f, 0.28f, 0.76f, 0.85f);
                rect(cx - boxW * 0.5f, bottom, boxW, rowH);
                batch.setColor(1f, 1f, 1f, 1f);
                font.drawCentred(batch, textures.white, highlightedRow(options.get(i)), cx,
                    bottom + (rowH - PixelFont.height(textSize)) * 0.5f, textSize);
            } else {
                batch.setColor(0.12f, 0.13f, 0.20f, 0.65f);
                rect(cx - boxW * 0.5f, bottom, boxW, rowH);
                batch.setColor(0.78f, 0.84f, 0.90f, 0.92f);
                font.drawCentred(batch, textures.white, options.get(i), cx,
                    bottom + (rowH - PixelFont.height(textSize)) * 0.5f, textSize);
            }
        }
    }

    /** @return a menu option as its row shows it while it is highlighted. */
    static String highlightedRow(String option) {
        return "> " + option + " <";
    }

    /** @return a briefing line as the briefing's box shows it. */
    static String briefingRow(String line) {
        return "> " + line;
    }

    /** @return the line under the briefing's title: the difficulty of the run and the stage's par time. */
    static String briefingMetaLine(UiStrings strings, Difficulty difficulty, float parSeconds) {
        return strings.format(UiText.SCREEN_BRIEFING_META, strings.label(difficulty),
            TextFormat.stopwatch(parSeconds));
    }

    /**
     * @param strings the loaded text
     * @param seconds how long the run lasted
     * @param hostiles whether the stage has hostiles; otherwise its targets are counted
     * @param destroyed how many of them were destroyed
     * @param total how many there are
     * @return the line under the Death screen's title
     */
    static String survivedLine(UiStrings strings, float seconds, boolean hostiles, int destroyed, int total) {
        String tally = strings.format(hostiles ? UiText.SCREEN_DEAD_HOSTILES : UiText.SCREEN_DEAD_TARGETS,
            Integer.toString(destroyed), Integer.toString(total));
        return strings.format(UiText.SCREEN_DEAD_SURVIVED, TextFormat.stopwatch(seconds), tally);
    }

    /** @return the tally's clear time line, with the stage's par time. */
    static String tallyTimeLine(UiStrings strings, SaveData.CompletionSummary sum, float parSeconds) {
        return strings.format(UiText.TALLY_CLEAR_TIME, TextFormat.stopwatch(sum.clearTime),
            TextFormat.stopwatch(parSeconds));
    }

    /**
     * @param strings the loaded text
     * @param sum the run's summary
     * @param difficulty the difficulty the run was played on
     * @return the tally's best time line for that difficulty, marked when this run set it
     */
    static String tallyBestLine(UiStrings strings, SaveData.CompletionSummary sum, Difficulty difficulty) {
        return strings.format(sum.newBestTime ? UiText.TALLY_BEST_NEW : UiText.TALLY_BEST,
            strings.label(difficulty), TextFormat.stopwatch(sum.bestTime));
    }

    /**
     * @param strings the loaded text
     * @param sum the run's summary
     * @param hostiles whether the stage has hostiles; otherwise its targets are counted
     * @return the tally's line of hostiles or targets destroyed
     */
    static String tallyClearedLine(UiStrings strings, SaveData.CompletionSummary sum, boolean hostiles) {
        int pct = sum.targetsTotal <= 0 ? 100 : Math.round(sum.targetsDestroyed * 100f / sum.targetsTotal);
        return strings.format(hostiles ? UiText.TALLY_HOSTILES_CLEARED : UiText.TALLY_TARGETS_CLEARED,
            Integer.toString(sum.targetsDestroyed), Integer.toString(sum.targetsTotal), Integer.toString(pct));
    }

    /**
     * @return the tally's line of damage taken, pickups collected and accuracy; {@code --} when no shot was fired.
     *     Damage is rounded to the nearest whole and never below 1 once any was taken, so it reads 0 only on a run
     *     that took none and earns {@link Medal#UNTOUCHED}.
     */
    static String tallyStatsLine(UiStrings strings, SaveData.CompletionSummary sum) {
        int accuracy = sum.accuracyPercent();
        int damage = sum.damageTaken <= 0f ? 0 : Math.max(1, Math.round(sum.damageTaken));
        return strings.format(UiText.TALLY_STATS, Integer.toString(damage), Integer.toString(sum.pickupsCollected),
            Integer.toString(sum.pickupsTotal),
            accuracy < 0 ? strings.get(UiText.TALLY_NO_ACCURACY) : accuracy + "%");
    }

    /** @return the tally's line of the run's rank and the best rank on the stage. */
    static String tallyRankLine(UiStrings strings, SaveData.CompletionSummary sum) {
        return strings.format(UiText.TALLY_RANK, String.valueOf(sum.rank), String.valueOf(sum.bestRank));
    }

    /**
     * @return the tally's medal line: the medals this run earned, those earned for the first time between
     *     asterisks, as in {@code MEDALS: *UNTOUCHED*  SWEEP}; a run that could earn none says so
     */
    static String tallyMedalLine(UiStrings strings, SaveData.CompletionSummary sum) {
        if (!sum.medalsRecorded) return strings.get(UiText.TALLY_MEDALS_TRAINING);
        if (sum.medals.isEmpty()) return strings.get(UiText.TALLY_MEDALS_NONE);
        StringBuilder list = new StringBuilder();
        for (Medal medal : Medal.values()) {
            if (!sum.medals.contains(medal)) continue;
            if (list.length() > 0) list.append("  ");
            String label = strings.label(medal);
            list.append(sum.newMedals.contains(medal) ? strings.format(UiText.TALLY_MEDAL_NEW, label) : label);
        }
        return strings.format(UiText.TALLY_MEDALS, list.toString());
    }

    private void rect(float x, float y, float rectWidth, float rectHeight) {
        batch.draw(textures.white, x, y, rectWidth, rectHeight);
    }

    @Override
    public void dispose() {
        if (batch != null) batch.dispose();
    }
}

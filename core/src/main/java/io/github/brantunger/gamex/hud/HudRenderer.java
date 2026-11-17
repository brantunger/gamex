package io.github.brantunger.gamex.hud;

import com.badlogic.gdx.Gdx;
import com.badlogic.gdx.graphics.GL20;
import com.badlogic.gdx.graphics.g2d.SpriteBatch;
import com.badlogic.gdx.math.MathUtils;
import com.badlogic.gdx.utils.Disposable;
import io.github.brantunger.gamex.GameSimulation;
import io.github.brantunger.gamex.player.Player;
import io.github.brantunger.gamex.render.ProceduralTextures;
import io.github.brantunger.gamex.settings.SaveData;
import io.github.brantunger.gamex.weapon.BlastCharger;
import io.github.brantunger.gamex.weapon.EnergyMeter;
import io.github.brantunger.gamex.world.Levels;
import io.github.brantunger.gamex.world.Pickup;

import java.util.List;
import java.util.Locale;

/**
 * Draws the 2D overlay on top of the finished 3D view: combat HUD (crosshair, hit-markers, directional hurt vignette,
 * health/armor/energy bars, pickup toasts, objective/timer) and interactive menu screens (Title, Difficulty, Briefing,
 * Pause, Settings, Death and Intermission Tally).
 *
 * <p>Sizes are in units of 1/720 of the window height, so the overlay scales cleanly with any window size.
 */
public final class HudRenderer implements Disposable {

    private static final int ENERGY_SEGMENTS = 10;
    private static final float MENU_BOX_WIDTH = 520f;
    private static final float MENU_ROW_HEIGHT = 34f;
    private static final float MENU_ROW_STEP = 42f;

    private final SpriteBatch batch = new SpriteBatch();
    private final ProceduralTextures textures;
    private float width;
    private float height;
    /** One HUD unit in pixels. */
    private float unit;

    /** @param textures the textures to draw with; the caller keeps ownership of them */
    public HudRenderer(ProceduralTextures textures) {
        this.textures = textures;
        resize(Gdx.graphics.getWidth(), Gdx.graphics.getHeight());
    }

    /**
     * Adapts the overlay to a new window size.
     * @param newWidth the new width in pixels
     * @param newHeight the new height in pixels
     */
    public void resize(int newWidth, int newHeight) {
        width = newWidth;
        height = newHeight;
        unit = newHeight / 720f;
        batch.getProjectionMatrix().setToOrtho2D(0f, 0f, newWidth, newHeight);
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
        if (screen == MenuState.Screen.PLAYING
            || screen == MenuState.Screen.PAUSED
            || screen == MenuState.Screen.DEAD
            || (screen == MenuState.Screen.SETTINGS && simulation != null && !simulation.isLevelCleared())) {
            if (simulation != null) drawCombatHud(simulation);
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
     * Hit-tests which menu option row lies under OS window coordinates {@code (screenX, screenY)}, or -1 if none.
     */
    public static int optionAt(int windowWidth, int windowHeight, int screenX, int screenY, MenuState menuState) {
        List<String> options = menuState.getOptions();
        if (options.isEmpty() || windowHeight <= 0) return -1;
        float u = windowHeight / 720f;
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
                return cy + 58f * u;
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

    private void drawCombatHud(GameSimulation simulation) {
        drawHurtOverlay(simulation.player);
        if (!simulation.player.isDead()) {
            drawCrosshair(simulation);
            drawInteractionPrompt(simulation);
            drawCharge(simulation.charger, simulation.player);
        }
        drawVitalsAndEnergy(simulation);
        drawStatus(simulation);
        drawToast(simulation);
    }

    private void drawInteractionPrompt(GameSimulation simulation) {
        String prompt = simulation.getInteractionPrompt();
        if (prompt == null) return;
        float pixel = 2.4f * unit;
        float textW = PixelFont.width(prompt, pixel);
        float padX = 10f * unit;
        float padY = 5f * unit;
        float boxW = textW + padX * 2f;
        float boxH = PixelFont.height(pixel) + padY * 2f;
        float cx = width * 0.5f;
        float bottom = height * 0.5f - 44f * unit;
        batch.setColor(0.06f, 0.08f, 0.14f, 0.78f);
        rect(cx - boxW * 0.5f, bottom, boxW, boxH);
        if (prompt.startsWith("[E]")) batch.setColor(0.62f, 1f, 0.86f, 0.95f);
        else batch.setColor(1f, 0.72f, 0.30f, 0.95f);
        PixelFont.drawCentred(batch, textures.white, prompt, cx, bottom + padY, pixel);
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
        float gap = 6f * unit;
        float length = 8f * unit;
        float thick = 2f * unit;
        if (charger.isReadyToFire()) batch.setColor(0.8f, 0.55f, 1f, 0.95f);
        else batch.setColor(0.8f, 1f, 0.88f, 0.9f);
        rect(centreX - gap - length, centreY - thick * 0.5f, length, thick);
        rect(centreX + gap, centreY - thick * 0.5f, length, thick);
        rect(centreX - thick * 0.5f, centreY - gap - length, thick, length);
        rect(centreX - thick * 0.5f, centreY + gap, thick, length);
        rect(centreX - thick * 0.5f, centreY - thick * 0.5f, thick, thick);

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

    private void drawCharge(BlastCharger charger, Player player) {
        float centreX = width * 0.5f;
        float barWidth = 180f * unit;
        float barHeight = 10f * unit;
        float left = centreX - barWidth * 0.5f;
        float bottom = height * 0.5f - 70f * unit;
        if (charger.isRefused() || player.isDashRefused()) {
            batch.setColor(1f, 0.35f, 0.3f, 0.95f);
            PixelFont.drawCentred(batch, textures.white, "LOW ENERGY", centreX, bottom, 3f * unit);
            return;
        }
        if (!charger.isCharging()) return;
        batch.setColor(0f, 0f, 0f, 0.6f);
        rect(left - 2f * unit, bottom - 2f * unit, barWidth + 4f * unit, barHeight + 4f * unit);
        if (charger.isReadyToFire()) batch.setColor(0.78f, 0.5f, 1f, 1f);
        else batch.setColor(0.5f, 0.5f, 0.6f, 1f);
        rect(left, bottom, barWidth * charger.getCharge(), barHeight);
        batch.setColor(1f, 1f, 1f, 0.9f);
        rect(left + barWidth * BlastCharger.MIN_CHARGE - unit, bottom - 4f * unit, 2f * unit,
            barHeight + 8f * unit);
        PixelFont.drawCentred(batch, textures.white, "CHARGE", centreX, bottom - 26f * unit, 3f * unit);
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

        boolean low = !energy.canSpend(BlastCharger.MIN_COST);
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
            float cost = BlastCharger.costFor(charger.getCharge()) / energy.getCapacity();
            float from = Math.max(0f, energy.getFraction() - cost);
            batch.setColor(1f, 1f, 1f, 0.85f);
            rect(left + span * from, energyBottom - 6f * unit, span * (energy.getFraction() - from), 3f * unit);
        }
        batch.setColor(0.8f, 0.72f, 1f, 0.95f);
        PixelFont.draw(batch, textures.white, "ENERGY [SHIFT: DASH]", left,
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
        PixelFont.draw(batch, textures.white, "HP " + shownHp, left + barW + 10f * unit, healthY + unit,
            2.6f * unit);

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
        PixelFont.draw(batch, textures.white, "ARMOR " + Math.round(player.getArmor()),
            left + barW + 10f * unit, armorY + unit, 2.6f * unit);

        // Active weapon readout and held security keycards.
        batch.setColor(0.8f, 1f, 0.88f, 0.88f);
        PixelFont.draw(batch, textures.white, "1: PISTOL [INF]", left, 114f * unit, 2.5f * unit);

        float keyX = left;
        float keyY = 138f * unit;
        float keySize = 2.4f * unit;
        if (simulation.hasKeycard(Pickup.Kind.AMBER_KEYCARD)) {
            batch.setColor(1f, 0.76f, 0.22f, 0.96f);
            String badge = "[AMBER KEY]";
            PixelFont.draw(batch, textures.white, badge, keyX, keyY, keySize);
            keyX += PixelFont.width(badge, keySize) + 14f * unit;
        }
        if (simulation.hasKeycard(Pickup.Kind.CYAN_KEYCARD)) {
            batch.setColor(0.26f, 0.94f, 1f, 0.96f);
            PixelFont.draw(batch, textures.white, "[CYAN KEY]", keyX, keyY, keySize);
        }
    }

    private void drawStatus(GameSimulation simulation) {
        float pixel = 3f * unit;
        float topY = height - 28f * unit - PixelFont.height(pixel);
        if (!simulation.enemies.isEmpty()) {
            int remaining = simulation.getEnemiesRemaining();
            if (remaining > 0) batch.setColor(1f, 0.52f, 0.46f, 0.95f);
            else batch.setColor(0.55f, 1f, 0.82f, 0.95f);
            String hostiles = "HOSTILES " + remaining + "/" + simulation.enemies.size();
            PixelFont.draw(batch, textures.white, hostiles, 28f * unit, topY, pixel);
        } else {
            batch.setColor(0.8f, 1f, 0.88f, 0.92f);
            String targets = "TARGETS " + simulation.getTargetsRemaining() + "/" + simulation.targets.size();
            PixelFont.draw(batch, textures.white, targets, 28f * unit, topY, pixel);
        }

        if (simulation.map.hasExit()) {
            batch.setColor(0.55f, 0.95f, 1f, 0.9f);
            PixelFont.draw(batch, textures.white, "OBJECTIVE: REACH EXIT PAD", 28f * unit, topY - 24f * unit,
                2.4f * unit);
        }

        String timer = "TIME " + formatTime(simulation.getTime());
        batch.setColor(0.86f, 0.88f, 0.95f, 0.9f);
        PixelFont.draw(batch, textures.white, timer,
            width - 28f * unit - PixelFont.width(timer, pixel), topY, pixel);
    }

    private void drawToast(GameSimulation simulation) {
        String msg = simulation.getToastMessage();
        if (msg == null) return;
        float alpha = simulation.getToastAlpha();
        float pixel = 2.8f * unit;
        float textW = PixelFont.width(msg, pixel);
        float padX = 14f * unit;
        float padY = 8f * unit;
        float boxW = textW + padX * 2f;
        float boxH = PixelFont.height(pixel) + padY * 2f;
        float cx = width * 0.5f;
        float y = height - 64f * unit;
        batch.setColor(0.06f, 0.08f, 0.14f, 0.78f * alpha);
        rect(cx - boxW * 0.5f, y, boxW, boxH);
        batch.setColor(0.55f, 1f, 0.82f, alpha);
        PixelFont.drawCentred(batch, textures.white, msg, cx, y + padY, pixel);
    }

    // ---- Menu Screens ----

    private void drawTitleScreen(MenuState menuState) {
        drawBackdropShade(0.52f);
        float cx = width * 0.5f;
        float cy = height * 0.5f;

        drawPanel(cx, cy + 6f * unit, 600f * unit, 488f * unit);
        batch.setColor(0.76f, 0.56f, 1f, 1f);
        PixelFont.drawCentred(batch, textures.white, "GAMEX", cx, cy + 168f * unit, 12f * unit);
        batch.setColor(0.62f, 0.95f, 0.88f, 0.95f);
        PixelFont.drawCentred(batch, textures.white, "SECTOR BREACH // TACTICAL FPS", cx, cy + 130f * unit,
            3f * unit);

        drawOptionRows(menuState, firstOptionBottomY(height, unit, MenuState.Screen.TITLE));

        batch.setColor(0.65f, 0.70f, 0.78f, 0.85f);
        PixelFont.drawCentred(batch, textures.white,
            "WASD: MOVE   SHIFT: DASH   E: INTERACT   LMB: PISTOL   RMB: BLAST", cx, 32f * unit, 2.1f * unit);
    }

    private void drawStageSelectScreen(MenuState menuState) {
        drawBackdropShade(0.62f);
        float cx = width * 0.5f;
        float cy = height * 0.5f;
        drawPanel(cx, cy + 6f * unit, 620f * unit, 444f * unit);
        batch.setColor(0.55f, 0.95f, 1f, 1f);
        PixelFont.drawCentred(batch, textures.white, "SELECT STAGE", cx, cy + 152f * unit, 5.5f * unit);
        batch.setColor(0.70f, 0.78f, 0.90f, 0.88f);
        PixelFont.drawCentred(batch, textures.white, "CHOOSE ANY UNLOCKED STAGE TO DEPLOY", cx, cy + 122f * unit,
            2.2f * unit);
        drawOptionRows(menuState, firstOptionBottomY(height, unit, MenuState.Screen.STAGE_SELECT));
    }

    private void drawDifficultyScreen(MenuState menuState) {
        drawBackdropShade(0.62f);
        float cx = width * 0.5f;
        float cy = height * 0.5f;
        drawPanel(cx, cy, 580f * unit, 340f * unit);
        batch.setColor(0.78f, 0.62f, 1f, 1f);
        PixelFont.drawCentred(batch, textures.white, "SELECT DIFFICULTY", cx, cy + 112f * unit, 5.5f * unit);
        drawOptionRows(menuState, firstOptionBottomY(height, unit, MenuState.Screen.DIFFICULTY_SELECT));
    }

    private void drawBriefingScreen(MenuState menuState) {
        drawBackdropShade(0.68f);
        Levels.Stage stage = menuState.getActiveStage();
        float cx = width * 0.5f;
        float cy = height * 0.5f;
        drawPanel(cx, cy, 660f * unit, 420f * unit);

        batch.setColor(0.55f, 0.95f, 1f, 1f);
        PixelFont.drawCentred(batch, textures.white, stage.title, cx, cy + 144f * unit, 5.2f * unit);

        String meta = "DIFFICULTY: " + menuState.getActiveLoadout().difficulty.label
            + "   //   PAR TIME: " + formatTime(stage.parSeconds);
        batch.setColor(0.76f, 0.72f, 0.95f, 0.9f);
        PixelFont.drawCentred(batch, textures.white, meta, cx, cy + 108f * unit, 2.5f * unit);

        // Terminal log box.
        batch.setColor(0.04f, 0.06f, 0.10f, 0.85f);
        rect(cx - 290f * unit, cy - 42f * unit, 580f * unit, 128f * unit);
        batch.setColor(0.68f, 0.98f, 0.82f, 0.95f);
        for (int i = 0; i < stage.briefing.size(); i++) {
            PixelFont.drawCentred(batch, textures.white, "> " + stage.briefing.get(i), cx,
                cy + (52f - i * 32f) * unit, 2.4f * unit);
        }

        drawOptionRows(menuState, firstOptionBottomY(height, unit, MenuState.Screen.BRIEFING));
    }

    private void drawPauseMenu(MenuState menuState) {
        drawBackdropShade(0.62f);
        float cx = width * 0.5f;
        float cy = height * 0.5f;
        drawPanel(cx, cy - 10f * unit, 560f * unit, 370f * unit);
        batch.setColor(0.8f, 0.72f, 1f, 1f);
        PixelFont.drawCentred(batch, textures.white, "PAUSED", cx, cy + 114f * unit, 6.5f * unit);
        drawOptionRows(menuState, firstOptionBottomY(height, unit, MenuState.Screen.PAUSED));
    }

    private void drawSettingsScreen(MenuState menuState) {
        drawBackdropShade(0.70f);
        float cx = width * 0.5f;
        float cy = height * 0.5f;
        drawPanel(cx, cy, 600f * unit, 472f * unit);
        batch.setColor(0.78f, 0.65f, 1f, 1f);
        PixelFont.drawCentred(batch, textures.white, "SETTINGS", cx, cy + 168f * unit, 5.8f * unit);
        batch.setColor(0.66f, 0.78f, 0.86f, 0.88f);
        PixelFont.drawCentred(batch, textures.white, "LEFT/RIGHT OR CLICK TO ADJUST - ESC TO SAVE", cx,
            cy + 140f * unit, 2.2f * unit);
        drawOptionRows(menuState, firstOptionBottomY(height, unit, MenuState.Screen.SETTINGS));
    }

    private void drawDeathScreen(GameSimulation simulation, MenuState menuState) {
        drawBackdropShade(0.55f);
        float cx = width * 0.5f;
        float cy = height * 0.5f;
        drawPanel(cx, cy, 560f * unit, 310f * unit);
        batch.setColor(1f, 0.30f, 0.34f, 1f);
        PixelFont.drawCentred(batch, textures.white, "SIGNAL LOST", cx, cy + 78f * unit, 6.5f * unit);
        if (simulation != null) {
            String tally = simulation.enemies.isEmpty()
                ? "TARGETS " + simulation.getTargetsDestroyed() + "/" + simulation.targets.size()
                : "HOSTILES " + simulation.getEnemiesDestroyed() + "/" + simulation.enemies.size();
            String sub = "SURVIVED " + formatTime(simulation.getTime()) + "   //   " + tally;
            batch.setColor(0.90f, 0.80f, 0.82f, 0.92f);
            PixelFont.drawCentred(batch, textures.white, sub, cx, cy + 32f * unit, 2.5f * unit);
        }
        drawOptionRows(menuState, firstOptionBottomY(height, unit, MenuState.Screen.DEAD));
    }

    private void drawIntermissionScreen(MenuState menuState) {
        drawBackdropShade(0.72f);
        float cx = width * 0.5f;
        float cy = height * 0.5f;
        drawPanel(cx, cy - 6f * unit, 640f * unit, 480f * unit);

        Levels.Stage stage = menuState.getActiveStage();
        SaveData.CompletionSummary sum = menuState.getLastSummary();
        boolean campaignDone = menuState.isCampaignCompleted();

        if (campaignDone) {
            batch.setColor(1f, 0.88f, 0.36f, 1f);
            PixelFont.drawCentred(batch, textures.white, "CAMPAIGN COMPLETE", cx, cy + 174f * unit, 5.6f * unit);
        } else {
            batch.setColor(0.52f, 1f, 0.82f, 1f);
            PixelFont.drawCentred(batch, textures.white, "STAGE COMPLETE", cx, cy + 174f * unit, 5.8f * unit);
        }
        batch.setColor(0.78f, 0.84f, 1f, 0.95f);
        PixelFont.drawCentred(batch, textures.white, stage.title, cx, cy + 138f * unit, 3.2f * unit);
        if (campaignDone) {
            batch.setColor(0.52f, 1f, 0.82f, 0.95f);
            PixelFont.drawCentred(batch, textures.white, "SECTOR CLEARED - ALL STAGES COMPLETE", cx,
                cy + 112f * unit, 2.4f * unit);
        }

        if (sum != null) {
            float statPix = 2.8f * unit;
            String timeLine = "CLEAR TIME: " + formatTime(sum.clearTime) + "  (PAR " + formatTime(stage.parSeconds)
                + ")" + (sum.newBestTime ? "  *NEW BEST!*" : "");
            int pct = sum.targetsTotal <= 0 ? 100 : Math.round(sum.targetsDestroyed * 100f / sum.targetsTotal);
            String label = stage.map.getEnemySpawns().isEmpty() ? "TARGETS CLEARED: " : "HOSTILES CLEARED: ";
            String targetLine = label + sum.targetsDestroyed + "/" + sum.targetsTotal + " (" + pct + "%)";
            String dmgLine = "DAMAGE TAKEN: " + Math.round(sum.damageTaken);
            String rankLine = "RANK: [" + sum.rank + "]   BEST: [" + sum.bestRank + "]";

            batch.setColor(0.92f, 0.95f, 1f, 0.95f);
            PixelFont.drawCentred(batch, textures.white, timeLine, cx, cy + 78f * unit, statPix);
            PixelFont.drawCentred(batch, textures.white, targetLine, cx, cy + 46f * unit, statPix);
            PixelFont.drawCentred(batch, textures.white, dmgLine, cx, cy + 14f * unit, statPix);

            if (sum.rank == 'S') batch.setColor(1f, 0.88f, 0.36f, 1f);
            else if (sum.rank == 'A') batch.setColor(0.45f, 0.96f, 0.85f, 1f);
            else batch.setColor(0.80f, 0.68f, 1f, 1f);
            PixelFont.drawCentred(batch, textures.white, rankLine, cx, cy - 30f * unit, 4f * unit);
        }

        drawOptionRows(menuState, firstOptionBottomY(height, unit, MenuState.Screen.INTERMISSION));
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
        float textSize = 2.8f * unit;

        for (int i = 0; i < options.size(); i++) {
            boolean selected = i == menuState.getSelectedIndex();
            float bottom = firstBottomY - i * rowStep;
            if (selected) {
                batch.setColor(0.42f, 0.28f, 0.76f, 0.85f);
                rect(cx - boxW * 0.5f, bottom, boxW, rowH);
                batch.setColor(1f, 1f, 1f, 1f);
                PixelFont.drawCentred(batch, textures.white, "> " + options.get(i) + " <", cx,
                    bottom + (rowH - PixelFont.height(textSize)) * 0.5f, textSize);
            } else {
                batch.setColor(0.12f, 0.13f, 0.20f, 0.65f);
                rect(cx - boxW * 0.5f, bottom, boxW, rowH);
                batch.setColor(0.78f, 0.84f, 0.90f, 0.92f);
                PixelFont.drawCentred(batch, textures.white, options.get(i), cx,
                    bottom + (rowH - PixelFont.height(textSize)) * 0.5f, textSize);
            }
        }
    }

    static String formatTime(float seconds) {
        float clamped = Math.max(0f, seconds);
        int mins = (int) (clamped / 60f);
        float rem = clamped - mins * 60f;
        return String.format(Locale.ROOT, "%02d:%04.1f", mins, rem);
    }

    private void rect(float x, float y, float rectWidth, float rectHeight) {
        batch.draw(textures.white, x, y, rectWidth, rectHeight);
    }

    @Override
    public void dispose() {
        batch.dispose();
    }
}

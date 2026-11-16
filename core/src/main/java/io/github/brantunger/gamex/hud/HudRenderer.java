package io.github.brantunger.gamex.hud;

import com.badlogic.gdx.Gdx;
import com.badlogic.gdx.graphics.GL20;
import com.badlogic.gdx.graphics.g2d.SpriteBatch;
import com.badlogic.gdx.math.MathUtils;
import com.badlogic.gdx.utils.Disposable;
import io.github.brantunger.gamex.GameSimulation;
import io.github.brantunger.gamex.render.ProceduralTextures;
import io.github.brantunger.gamex.weapon.BlastCharger;
import io.github.brantunger.gamex.weapon.EnergyMeter;

/**
 * Draws the 2D overlay on top of the finished 3D view: the crosshair, the energy meter, the charge bar, the status
 * texts and the pause screen. Sizes are in units of 1/720 of the window height, so the overlay scales with the window.
 */
public final class HudRenderer implements Disposable {

    private static final int ENERGY_SEGMENTS = 10;

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
     * Draws the overlay.
     * @param simulation the game state to show
     * @param paused whether to show the pause screen over everything else
     */
    public void render(GameSimulation simulation, boolean paused) {
        // The 3D pass must not leak its depth test or culling into the 2D pass.
        Gdx.gl.glDisable(GL20.GL_DEPTH_TEST);
        Gdx.gl.glDisable(GL20.GL_CULL_FACE);
        batch.begin();
        drawCrosshair(simulation.charger);
        drawCharge(simulation.charger);
        drawEnergy(simulation.energy, simulation.charger);
        drawStatus(simulation);
        if (paused) drawPauseScreen();
        batch.setColor(1f, 1f, 1f, 1f);
        batch.end();
    }

    private void drawCrosshair(BlastCharger charger) {
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
    }

    /** The charge bar under the crosshair, shown while the blast button is held. */
    private void drawCharge(BlastCharger charger) {
        float centreX = width * 0.5f;
        float barWidth = 180f * unit;
        float barHeight = 10f * unit;
        float left = centreX - barWidth * 0.5f;
        float bottom = height * 0.5f - 70f * unit;
        if (charger.isRefused()) {
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
        // The mark a charge has to pass before it fires.
        batch.setColor(1f, 1f, 1f, 0.9f);
        rect(left + barWidth * BlastCharger.MIN_CHARGE - unit, bottom - 4f * unit, 2f * unit,
            barHeight + 8f * unit);
        PixelFont.drawCentred(batch, textures.white, "CHARGE", centreX, bottom - 26f * unit, 3f * unit);
    }

    /** The energy meter at the bottom left: a row of segments that fill from the left. */
    private void drawEnergy(EnergyMeter energy, BlastCharger charger) {
        float left = 28f * unit;
        float bottom = 28f * unit;
        float segmentWidth = 20f * unit;
        float segmentHeight = 22f * unit;
        float gap = 4f * unit;
        boolean low = !energy.canSpend(BlastCharger.MIN_COST);
        float filled = energy.getFraction() * ENERGY_SEGMENTS;
        for (int i = 0; i < ENERGY_SEGMENTS; i++) {
            float x = left + i * (segmentWidth + gap);
            batch.setColor(0.08f, 0.07f, 0.14f, 0.8f);
            rect(x, bottom, segmentWidth, segmentHeight);
            float part = MathUtils.clamp(filled - i, 0f, 1f);
            if (part <= 0f) continue;
            if (low) batch.setColor(0.9f, 0.3f, 0.3f, 1f);
            else batch.setColor(0.6f, 0.38f, 1f, 1f);
            rect(x, bottom, segmentWidth * part, segmentHeight);
            batch.setColor(1f, 1f, 1f, 0.25f);
            rect(x, bottom + segmentHeight - 4f * unit, segmentWidth * part, 4f * unit);
        }
        // What the charge built so far will cost, as a line over the meter.
        if (charger.isReadyToFire()) {
            float cost = BlastCharger.costFor(charger.getCharge()) / energy.getCapacity();
            float span = ENERGY_SEGMENTS * (segmentWidth + gap) - gap;
            float from = Math.max(0f, energy.getFraction() - cost);
            batch.setColor(1f, 1f, 1f, 0.85f);
            rect(left + span * from, bottom - 6f * unit, span * (energy.getFraction() - from), 3f * unit);
        }
        batch.setColor(0.8f, 0.72f, 1f, 1f);
        PixelFont.draw(batch, textures.white, "ENERGY", left, bottom + segmentHeight + 8f * unit, 3f * unit);
    }

    private void drawStatus(GameSimulation simulation) {
        float pixel = 3f * unit;
        String weapon = "PISTOL - NO AMMO LIMIT";
        batch.setColor(0.8f, 1f, 0.88f, 0.9f);
        // Above the energy meter: the lower right corner belongs to the pistol and the hand holding it.
        PixelFont.draw(batch, textures.white, weapon, 28f * unit, 88f * unit, pixel);
        String targets = "TARGETS " + simulation.getTargetsRemaining() + "/" + simulation.targets.size();
        PixelFont.draw(batch, textures.white, targets, 28f * unit, height - 28f * unit - PixelFont.height(pixel),
            pixel);
    }

    private void drawPauseScreen() {
        batch.setColor(0f, 0f, 0f, 0.6f);
        rect(0f, 0f, width, height);
        float centreX = width * 0.5f;
        batch.setColor(0.8f, 0.72f, 1f, 1f);
        PixelFont.drawCentred(batch, textures.white, "PAUSED", centreX, height * 0.5f + 70f * unit, 10f * unit);
        batch.setColor(0.85f, 0.9f, 0.88f, 1f);
        String[] lines = {
            "CLICK OR PRESS ESC TO RESUME",
            "PRESS Q TO QUIT",
            "",
            "WASD: MOVE - MOUSE: LOOK",
            "LEFT BUTTON: PISTOL",
            "HOLD AND RELEASE RIGHT BUTTON: BLAST"
        };
        for (int i = 0; i < lines.length; i++) {
            PixelFont.drawCentred(batch, textures.white, lines[i], centreX, height * 0.5f + (20f - i * 30f) * unit,
                3f * unit);
        }
    }

    private void rect(float x, float y, float rectWidth, float rectHeight) {
        batch.draw(textures.white, x, y, rectWidth, rectHeight);
    }

    @Override
    public void dispose() {
        batch.dispose();
    }
}

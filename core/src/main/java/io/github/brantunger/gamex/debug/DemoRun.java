package io.github.brantunger.gamex.debug;

import com.badlogic.gdx.Gdx;
import com.badlogic.gdx.graphics.Pixmap;
import com.badlogic.gdx.graphics.PixmapIO;
import io.github.brantunger.gamex.player.PlayerInput;

import java.nio.ByteBuffer;
import java.util.Locale;
import java.util.zip.Deflater;

/**
 * A debug-only unattended run that saves one frame as a PNG and quits. It exists so that a launch can be checked
 * without anyone at the keyboard. It is switched on by environment variables:
 * <ul>
 *  <li>{@code GAMEX_SCREENSHOT}: the path of the PNG to write. Without it, the game runs normally.</li>
 *  <li>{@code GAMEX_SCREENSHOT_FRAME}: the frame to save, counted from 1; the default is {@value #DEFAULT_FRAME}.</li>
 *  <li>{@code GAMEX_DEMO}: set to {@code action} to play a short script instead of standing still: the player turns
 *  towards a pillar, holds the blast button, releases it, and fires the pistol. Set to {@code targets} to turn
 *  towards the nearest canister and keep firing the pistol at it. Set to {@code breach} to turn, dash and then fire
 *  the pistol. Two values are read by {@code MainGame} through {@link #getScript()}: {@code breach} also loads the
 *  Outer Breach stage instead of the original first level, and {@code title} shows the Title screen over that stage
 *  with no scripted input and without advancing the game.</li>
 *  <li>{@code GAMEX_VIEWMODEL_ORBIT}: set to {@code yaw} or {@code yaw,pitch} in degrees to draw only the pistol and
 *  hand, on a plain background, seen from outside: 0 is from behind, 90 the left side, 180 the front, and a pitch
 *  of 90 is from above.</li>
 * </ul>
 * During such a run the real keyboard and mouse are ignored, the cursor stays free, and every frame advances the game
 * by the same {@link #FRAME_TIME}, so the saved frame is the same on every run.
 */
public final class DemoRun {

    /** The simulated time per frame, in seconds. */
    public static final float FRAME_TIME = 1f / 60f;
    /** The frame that is saved when {@code GAMEX_SCREENSHOT_FRAME} is not set. */
    public static final int DEFAULT_FRAME = 60;

    private static final int CHARGE_FROM_FRAME = 5;
    private static final int CHARGE_UNTIL_FRAME = 50;
    private static final int FIRE_FROM_FRAME = 57;
    /** The turn, in degrees, that points the player at a pillar near the spawn. */
    private static final float SCRIPT_TURN = -24f;
    /** The turn, in degrees, that points the player at the canister in the spawn hall. */
    private static final float TARGET_TURN = 71.57f;
    private static final int TARGET_FIRE_FROM_FRAME = 30;

    private final String path;
    private final int captureFrame;
    private final String script;
    private final float[] orbit = parseOrbit(System.getenv("GAMEX_VIEWMODEL_ORBIT"));
    private int frame;

    private DemoRun(String path, int captureFrame, String script) {
        this.path = path;
        this.captureFrame = captureFrame;
        this.script = script == null ? "" : script.toLowerCase(Locale.ROOT);
    }

    /** @return the run described by the environment, or null when {@code GAMEX_SCREENSHOT} is not set. */
    public static DemoRun fromEnvironment() {
        String path = System.getenv("GAMEX_SCREENSHOT");
        if (path == null || path.isEmpty()) return null;
        int captureFrame = DEFAULT_FRAME;
        String frameText = System.getenv("GAMEX_SCREENSHOT_FRAME");
        if (frameText != null && !frameText.isEmpty()) {
            try {
                captureFrame = Math.max(1, Integer.parseInt(frameText.trim()));
            } catch (NumberFormatException e) {
                System.err.println("GAMEX_SCREENSHOT_FRAME is not a whole number: '" + frameText + "'; using "
                    + DEFAULT_FRAME);
            }
        }
        return new DemoRun(path, captureFrame, System.getenv("GAMEX_DEMO"));
    }

    /**
     * Reads {@code GAMEX_VIEWMODEL_ORBIT}, which asks for the pistol and hand to be drawn alone from outside.
     * @return the yaw and pitch of that view in degrees, or null when the variable is not set or is malformed
     */
    public float[] getViewmodelOrbit() {
        return orbit;
    }

    /**
     * Reads a view of the pistol and hand from outside, such as {@code 90} or {@code 90,30}.
     * @param text the view as {@code yaw} or {@code yaw,pitch} in degrees, or null
     * @return the yaw and pitch in degrees, or null when the text is missing or malformed
     */
    static float[] parseOrbit(String text) {
        if (text == null || text.isEmpty()) return null;
        String[] parts = text.split(",");
        try {
            float yaw = Float.parseFloat(parts[0].trim());
            float pitch = parts.length > 1 ? Float.parseFloat(parts[1].trim()) : 0f;
            return new float[] {yaw, pitch};
        } catch (NumberFormatException | ArrayIndexOutOfBoundsException e) {
            System.err.println("GAMEX_VIEWMODEL_ORBIT is not 'yaw' or 'yaw,pitch' in degrees: '" + text + "'");
            return null;
        }
    }

    /** @return the lowercase script name from {@code GAMEX_DEMO}, or empty when unset. */
    public String getScript() {
        return script;
    }

    /**
     * Starts the next frame and fills in its scripted input.
     * @param out the input to fill
     */
    public void nextFrame(PlayerInput out) {
        frame++;
        out.clear();
        if ("action".equals(script)) {
            if (frame == 1) out.yawDelta = SCRIPT_TURN;
            out.charge = frame >= CHARGE_FROM_FRAME && frame < CHARGE_UNTIL_FRAME;
            out.fire = frame >= FIRE_FROM_FRAME;
        } else if ("targets".equals(script)) {
            if (frame == 1) out.yawDelta = TARGET_TURN;
            out.fire = frame >= TARGET_FIRE_FROM_FRAME;
        } else if ("breach".equals(script)) {
            out.moveForward = frame <= 155 ? 1f : 0f;
            if (frame == 25) out.dash = true;
            if (frame >= 115 && frame <= 135) out.moveStrafe = 1f;
            if (frame > 135 && frame <= 150) out.yawDelta = -1.2f;
            out.fire = frame >= 150;
        }
    }

    /** Call once the frame is fully drawn: saves it and asks the application to exit when its turn has come. */
    public void frameRendered() {
        if (frame != captureFrame) return;
        int width = Gdx.graphics.getBackBufferWidth();
        int height = Gdx.graphics.getBackBufferHeight();
        Pixmap pixmap = Pixmap.createFromFrameBuffer(0, 0, width, height);
        // The back buffer's alpha is whatever blending left there; a screenshot is opaque.
        ByteBuffer pixels = pixmap.getPixels();
        for (int i = 3; i < width * height * 4; i += 4) pixels.put(i, (byte) 0xFF);
        // The frame buffer's first row is the bottom of the picture, so the rows are flipped on the way out.
        PixmapIO.writePNG(Gdx.files.absolute(path), pixmap, Deflater.DEFAULT_COMPRESSION, true);
        pixmap.dispose();
        Gdx.app.log("DemoRun", "Saved frame " + frame + " to " + path);
        Gdx.app.exit();
    }
}

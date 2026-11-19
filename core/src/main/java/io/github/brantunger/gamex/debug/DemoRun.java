package io.github.brantunger.gamex.debug;

import com.badlogic.gdx.Gdx;
import com.badlogic.gdx.graphics.Pixmap;
import com.badlogic.gdx.graphics.PixmapIO;
import com.badlogic.gdx.utils.Disposable;
import io.github.brantunger.gamex.player.PlayerInput;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.nio.ByteBuffer;
import java.util.Locale;
import java.util.function.Consumer;
import java.util.function.Function;
import java.util.zip.Deflater;

/**
 * A debug-only unattended run that saves one frame as a PNG and quits. It exists so that a launch can be checked
 * without anyone at the keyboard. It is switched on by environment variables:
 * <ul>
 *  <li>{@code GAMEX_SCREENSHOT}: the path of the PNG to write. Without it, the game runs normally.</li>
 *  <li>{@code GAMEX_SCREENSHOT_FRAME}: the frame to save, counted from 1; the default is {@value #DEFAULT_FRAME}.</li>
 *  <li>{@code GAMEX_DEMO}: the name of a demo script to play instead of standing still in the original first level.
 *  The script {@code name} is the file {@code assets/demos/name.txt}, which holds the scene the run starts with (the
 *  screen, the stage, what the player carries, and where the player stands and looks) and the input played frame by
 *  frame; {@link DemoScript} and {@link DemoScene} give the format. The scripts that ship are {@code action},
 *  {@code targets}, {@code breach}, {@code title}, {@code lockdown}, {@code stages}, {@code scattergun},
 *  {@code raillance}, {@code warden}, {@code wardenidle} and {@code jump}; each file says in its first lines what it
 *  shows. A name with no file stops the start, saying so.</li>
 *  <li>{@code GAMEX_VIEWMODEL_ORBIT}: set to {@code yaw} or {@code yaw,pitch} in degrees to draw only the pistol and
 *  hand, on a plain background, seen from outside: 0 is from behind, 90 the left side, 180 the front, and a pitch
 *  of 90 is from above.</li>
 * </ul>
 * During such a run the real keyboard and mouse are ignored, the cursor stays free, and every frame advances the game
 * by the same {@link #FRAME_TIME}, so the saved frame is the same on every run.
 */
public final class DemoRun {

    private static final Logger log = LoggerFactory.getLogger(DemoRun.class);

    /** The simulated time per frame, in seconds. */
    public static final float FRAME_TIME = 1f / 60f;
    /** The frame that is saved when {@code GAMEX_SCREENSHOT_FRAME} is not set. */
    public static final int DEFAULT_FRAME = 60;

    private final String path;
    private final int captureFrame;
    private final String script;
    /** The script {@link #script} names, or null when it names none. */
    private final DemoScript demoScript;
    private final float[] orbit;
    private int frame;
    private boolean captured;

    DemoRun(String path, int captureFrame, String script) {
        this(path, captureFrame, script, System.getenv("GAMEX_VIEWMODEL_ORBIT"));
    }

    DemoRun(String path, int captureFrame, String script, String orbitText) {
        this.path = path == null ? "" : path.trim();
        this.captureFrame = Math.max(1, captureFrame);
        this.script = script == null ? "" : script.trim().toLowerCase(Locale.ROOT);
        this.demoScript = this.script.isEmpty() ? null : DemoScript.load(this.script);
        this.orbit = parseOrbit(orbitText);
    }

    /**
     * @return the run described by the environment, or null when {@code GAMEX_SCREENSHOT} is not set
     * @throws IllegalArgumentException if {@code GAMEX_DEMO} names a script that has no file in
     *     {@code assets/demos/}, or whose file breaks the format
     */
    public static DemoRun fromEnvironment() {
        return fromEnvironment(
            System.getenv("GAMEX_SCREENSHOT"),
            System.getenv("GAMEX_SCREENSHOT_FRAME"),
            System.getenv("GAMEX_DEMO"),
            System.getenv("GAMEX_VIEWMODEL_ORBIT"));
    }

    static DemoRun fromEnvironment(String screenshotPath, String frameText, String script, String orbitText) {
        if (screenshotPath == null || screenshotPath.trim().isEmpty()) return null;
        int captureFrame = DEFAULT_FRAME;
        if (frameText != null && !frameText.trim().isEmpty()) {
            try {
                captureFrame = Math.max(1, Integer.parseInt(frameText.trim()));
            } catch (NumberFormatException e) {
                log.warn("GAMEX_SCREENSHOT_FRAME is not a whole number: '{}'; using {}", frameText, DEFAULT_FRAME);
            }
        }
        return new DemoRun(screenshotPath.trim(), captureFrame, script, orbitText);
    }

    /**
     * Builds a run that plays a script without reading {@code GAMEX_SCREENSHOT}, so a test can step its input.
     * @param script the script name, as {@code GAMEX_DEMO} would give it
     * @return the run, which saves {@link #DEFAULT_FRAME} to no path
     */
    static DemoRun forScript(String script) {
        return new DemoRun("", DEFAULT_FRAME, script, null);
    }

    String getPath() {
        return path;
    }

    int getCaptureFrame() {
        return captureFrame;
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
        if (text == null || text.trim().isEmpty()) return null;
        String[] parts = text.trim().split(",", -1);
        try {
            if (parts.length > 2) throw new NumberFormatException("too many components");
            String yawText = parts[0].trim();
            if (yawText.isEmpty()) throw new NumberFormatException("missing yaw");
            float yaw = Float.parseFloat(yawText);
            float pitch = 0f;
            if (parts.length == 2) {
                String pitchText = parts[1].trim();
                if (pitchText.isEmpty()) throw new NumberFormatException("missing pitch");
                pitch = Float.parseFloat(pitchText);
            }
            if (!Float.isFinite(yaw) || !Float.isFinite(pitch)) {
                throw new NumberFormatException("non-finite orbit angle");
            }
            return new float[] {yaw, pitch};
        } catch (NumberFormatException e) {
            log.warn("GAMEX_VIEWMODEL_ORBIT is not 'yaw' or 'yaw,pitch' in degrees: '{}'", text);
            return null;
        }
    }

    /** @return the lowercase script name from {@code GAMEX_DEMO}, or empty when unset. */
    public String getScript() {
        return script;
    }

    /** @return the scene the run starts with: that of its script, or {@link DemoScene#STANDING} without one */
    public DemoScene getScene() {
        return demoScript == null ? DemoScene.STANDING : demoScript.scene;
    }

    /**
     * Starts the next frame and fills in its scripted input.
     * @param out the input to fill
     */
    public void nextFrame(PlayerInput out) {
        frame++;
        out.clear();
        if (demoScript != null) demoScript.apply(frame, out);
    }

    @FunctionalInterface
    interface FrameCapture<P extends Disposable> {
        P capture(int width, int height);
    }

    /** Call once the frame is fully drawn: saves it and asks the application to exit when its turn has come. */
    public void frameRendered() {
        if (captured || frame < captureFrame || Gdx.graphics == null) return;
        frameRendered(
            Gdx.graphics.getBackBufferWidth(),
            Gdx.graphics.getBackBufferHeight(),
            (width, height) -> Pixmap.createFromFrameBuffer(0, 0, width, height),
            Pixmap::getPixels,
            pixmap -> PixmapIO.writePNG(Gdx.files.absolute(path), pixmap, Deflater.DEFAULT_COMPRESSION, true),
            () -> {
                log.info("Saved frame {} to {}", frame, path);
                Gdx.app.exit();
            });
    }

    <P extends Disposable> boolean frameRendered(int width, int height,
                                                 FrameCapture<P> capture,
                                                 Function<P, ByteBuffer> pixelsOf,
                                                 Consumer<P> writer,
                                                 Runnable onComplete) {
        if (captured || frame < captureFrame) return false;
        if (width <= 0 || height <= 0) return false;
        P pixmap = capture.capture(width, height);
        try {
            // The back buffer's alpha is whatever blending left there; a screenshot is opaque.
            makeOpaque(pixelsOf.apply(pixmap), width, height);
            // The frame buffer's first row is the bottom of the picture, so the rows are flipped on the way out.
            writer.accept(pixmap);
        } finally {
            pixmap.dispose();
        }
        captured = true;
        onComplete.run();
        return true;
    }

    static void makeOpaque(ByteBuffer pixels, int width, int height) {
        int limit = Math.min(pixels.capacity(), width * height * 4);
        for (int i = 3; i < limit; i += 4) {
            pixels.put(i, (byte) 0xFF);
        }
    }
}

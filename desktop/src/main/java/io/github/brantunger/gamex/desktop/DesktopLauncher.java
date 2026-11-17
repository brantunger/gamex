package io.github.brantunger.gamex.desktop;

import com.badlogic.gdx.backends.lwjgl3.Lwjgl3Application;
import com.badlogic.gdx.backends.lwjgl3.Lwjgl3ApplicationConfiguration;
import io.github.brantunger.gamex.MainGame;

import java.util.Locale;

/** Launches the desktop (LWJGL3) application. */
public class DesktopLauncher {

    static final int MIN_WINDOW_WIDTH = 160;
    static final int MIN_WINDOW_HEIGHT = 120;
    static final int MAX_WINDOW_WIDTH = 7680;
    static final int MAX_WINDOW_HEIGHT = 4320;

    public static void main(String[] args) {
        // On macOS, GLFW needs -XstartOnFirstThread; without it (java -jar, a double-clicked JAR) this relaunches.
        int childExitCode = StartupHelper.startNewJvmIfRequired(args);
        if (childExitCode != StartupHelper.NOT_RESTARTED) System.exit(childExitCode);
        Lwjgl3ApplicationConfiguration config = new Lwjgl3ApplicationConfiguration();
        config.setTitle("gamex");
        int[] size = windowSize(System.getenv("GAMEX_WINDOW"));
        config.setWindowedMode(size[0], size[1]);
        // Let hardware VSync pace frames when enabled; disabling LWJGL3's software Sync.sync loop avoids
        // beat-frequency micro-stutter against glfwSwapBuffers and adapts across monitors.
        config.useVsync(true);
        config.setForegroundFPS(0);
        // Losing the focus pauses the game and frees the cursor, as minimising the window does.
        config.setPauseWhenLostFocus(true);
        // The icons are classpath resources, from src/main/resources.
        config.setWindowIcon("gamex128.png", "gamex64.png", "gamex32.png", "gamex16.png");
        // A 24-bit depth buffer keeps marks on distant walls from flickering; anti-aliasing with 4 samples.
        config.setBackBufferConfig(8, 8, 8, 8, 24, 0, 4);
        new Lwjgl3Application(new MainGame(), config);
    }

    /**
     * Reads a window size such as {@code 800x600}. The {@code GAMEX_WINDOW} environment variable carries one, for
     * trying the game at other sizes.
     * @param text the size as {@code WIDTHxHEIGHT}, or null
     * @return the width and height in pixels; 1280 by 720 when the text is missing, malformed, smaller than 160x120
     *     or larger than 7680x4320
     */
    static int[] windowSize(String text) {
        int[] fallback = {1280, 720};
        if (text == null || text.trim().isEmpty()) return fallback;
        String[] parts = text.trim().toLowerCase(Locale.ROOT).split("x", -1);
        try {
            if (parts.length != 2) throw new NumberFormatException("expected WIDTHxHEIGHT");
            int width = Integer.parseInt(parts[0].trim());
            int height = Integer.parseInt(parts[1].trim());
            if (width < MIN_WINDOW_WIDTH || height < MIN_WINDOW_HEIGHT
                || width > MAX_WINDOW_WIDTH || height > MAX_WINDOW_HEIGHT) {
                throw new NumberFormatException("out of bounds");
            }
            return new int[] {width, height};
        } catch (NumberFormatException e) {
            System.err.println("GAMEX_WINDOW is not a size between 160x120 and 7680x4320 like 800x600: '"
                + text + "'; using 1280x720");
            return fallback;
        }
    }
}

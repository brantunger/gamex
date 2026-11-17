package io.github.brantunger.gamex.desktop;

import com.badlogic.gdx.backends.lwjgl3.Lwjgl3Application;
import com.badlogic.gdx.backends.lwjgl3.Lwjgl3ApplicationConfiguration;
import io.github.brantunger.gamex.MainGame;

/** Launches the desktop (LWJGL3) application. */
public class DesktopLauncher {

    public static void main(String[] args) {
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
     * @return the width and height in pixels; 1280 by 720 when the text is missing or malformed
     */
    static int[] windowSize(String text) {
        int[] fallback = {1280, 720};
        if (text == null || text.isEmpty()) return fallback;
        String[] parts = text.trim().toLowerCase().split("x");
        try {
            int width = Integer.parseInt(parts[0].trim());
            int height = Integer.parseInt(parts[1].trim());
            if (width < 160 || height < 120) throw new NumberFormatException("too small");
            return new int[] {width, height};
        } catch (NumberFormatException | ArrayIndexOutOfBoundsException e) {
            System.err.println("GAMEX_WINDOW is not a size like 800x600: '" + text + "'; using 1280x720");
            return fallback;
        }
    }
}

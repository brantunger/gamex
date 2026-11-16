package io.github.brantunger.gamex.desktop;

import com.badlogic.gdx.backends.lwjgl3.Lwjgl3Application;
import com.badlogic.gdx.backends.lwjgl3.Lwjgl3ApplicationConfiguration;
import io.github.brantunger.gamex.MainGame;

/** Launches the desktop (LWJGL3) application. */
public class DesktopLauncher {

    public static void main(String[] args) {
        Lwjgl3ApplicationConfiguration config = new Lwjgl3ApplicationConfiguration();
        config.setTitle("gamex");
        config.setWindowedMode(1280, 720);
        // Vsync caps the frame rate at the display's refresh rate; the FPS limit is a fallback for when vsync is off.
        config.useVsync(true);
        // Some displays report no usable refresh rate; without this guard the limit could come out as 1 FPS.
        int refreshRate = Lwjgl3ApplicationConfiguration.getDisplayMode().refreshRate;
        config.setForegroundFPS(refreshRate < 30 ? 60 : refreshRate + 1);
        // Losing the focus pauses the game and frees the cursor, as minimising the window does.
        config.setPauseWhenLostFocus(true);
        // The icons are classpath resources, from src/main/resources.
        config.setWindowIcon("gamex128.png", "gamex64.png", "gamex32.png", "gamex16.png");
        // A 24-bit depth buffer keeps marks on distant walls from flickering; anti-aliasing with 4 samples.
        config.setBackBufferConfig(8, 8, 8, 8, 24, 0, 4);
        new Lwjgl3Application(new MainGame(), config);
    }
}

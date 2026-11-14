package io.github.brantunger.gamex.desktop;

import com.badlogic.gdx.backends.lwjgl3.Lwjgl3Application;
import com.badlogic.gdx.backends.lwjgl3.Lwjgl3ApplicationConfiguration;
import io.github.brantunger.gamex.MainGame;

/** Launches the desktop (LWJGL3) application. */
public class DesktopLauncher {

    public static void main(String[] args) {
        Lwjgl3ApplicationConfiguration config = new Lwjgl3ApplicationConfiguration();
        config.setForegroundFPS(60);
        config.setTitle("gamex");
        config.setWindowedMode(800, 600);
        config.setWindowIcon("icons/tank.png");
        config.setBackBufferConfig(8, 8, 8, 8,16, 0, 4);		// anti-aliasing 4 samples
        new Lwjgl3Application(new MainGame(), config);
    }
}

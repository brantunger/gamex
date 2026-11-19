package io.github.brantunger.gamex.desktop;

import com.badlogic.gdx.Graphics;
import com.badlogic.gdx.backends.lwjgl3.Lwjgl3Application;
import com.badlogic.gdx.backends.lwjgl3.Lwjgl3ApplicationConfiguration;
import com.badlogic.gdx.backends.lwjgl3.Lwjgl3Preferences;
import io.github.brantunger.gamex.MainGame;
import io.github.brantunger.gamex.settings.GameSettings;
import io.github.brantunger.gamex.settings.GdxPreferencesStore;
import io.github.brantunger.gamex.settings.KeyValueStore;
import io.github.brantunger.gamex.settings.SettingsDefaults;
import io.github.brantunger.gamex.settings.WindowDefaults;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.Locale;
import java.util.Properties;

/** Launches the desktop (LWJGL3) application. */
public class DesktopLauncher {

    /** The environment variable that sets the log level; see {@link LogLevel}. */
    static final String LOG_LEVEL_ENV = "GAMEX_LOG_LEVEL";

    // This must stay above the logger: Logback reads the level once, when the first logger is created.
    static {
        LogLevel.install(System.getenv(LOG_LEVEL_ENV));
    }

    private static final Logger log = LoggerFactory.getLogger(DesktopLauncher.class);

    static final int MIN_WINDOW_WIDTH = WindowDefaults.MIN_WIDTH;
    static final int MIN_WINDOW_HEIGHT = WindowDefaults.MIN_HEIGHT;
    static final int MAX_WINDOW_WIDTH = WindowDefaults.MAX_WIDTH;
    static final int MAX_WINDOW_HEIGHT = WindowDefaults.MAX_HEIGHT;

    public static void main(String[] args) {
        // On macOS, GLFW needs -XstartOnFirstThread; without it (java -jar, a double-clicked JAR) this relaunches.
        int childExitCode = StartupHelper.startNewJvmIfRequired(args);
        if (childExitCode != StartupHelper.NOT_RESTARTED) System.exit(childExitCode);
        // Logged after the relaunch check, so that a relaunched game says it once, from the JVM that runs it.
        String logLevel = logLevel(System.getenv(LOG_LEVEL_ENV));
        Lwjgl3ApplicationConfiguration config = new Lwjgl3ApplicationConfiguration();
        config.setTitle("gamex");
        // Both files are read before libGDX is up, so not through Gdx.files, but from where Gdx.files.internal will
        // look; a missing or broken one stops the start here, with a message naming the file and, where there is
        // one, the key.
        Path workingDir = Paths.get("");
        WindowDefaults windowDefaults = loadWindowDefaults(workingDir);
        SettingsDefaults settingsDefaults = loadSettingsDefaults(workingDir);
        String windowEnv = System.getenv("GAMEX_WINDOW");
        int[] size = windowSize(windowEnv, windowDefaults);
        Graphics.DisplayMode fullscreenMode = shouldStartFullscreen(
            windowEnv, System.getenv("GAMEX_SCREENSHOT"), loadSavedSettingsStore(), settingsDefaults)
            ? Lwjgl3ApplicationConfiguration.getDisplayMode()
            : null;
        if (fullscreenMode != null) {
            config.setFullscreenMode(fullscreenMode);
        } else {
            config.setWindowedMode(size[0], size[1]);
        }
        if (fullscreenMode != null) {
            log.info("Starting gamex {}: fullscreen {}x{}, log level {}", version(), fullscreenMode.width,
                fullscreenMode.height, logLevel);
        } else {
            log.info("Starting gamex {}: window {}x{}, log level {}", version(), size[0], size[1], logLevel);
        }
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
        // The game gets the two objects loaded above, so it cannot read another copy of either file.
        new Lwjgl3Application(new MainGame(size[0], size[1], isWindowForced(windowEnv), windowDefaults,
            settingsDefaults), config);
        log.info("Exited");
    }

    /**
     * Reads {@code assets/data/window.txt} from where the game will read its files once libGDX is up: under the
     * working directory if the file is there, and from the classpath otherwise.
     * @param workingDir the directory that stands for the working directory
     * @return the loaded window size
     */
    static WindowDefaults loadWindowDefaults(Path workingDir) {
        return WindowDefaults.load(workingDir);
    }

    /**
     * Reads {@code assets/data/settings.txt} from the places {@link #loadWindowDefaults} looks in.
     * @param workingDir the directory that stands for the working directory
     * @return the loaded settings defaults
     */
    static SettingsDefaults loadSettingsDefaults(Path workingDir) {
        return SettingsDefaults.load(workingDir);
    }

    /**
     * @return the version the build wrote into {@code gamex-version.properties}; {@code unknown} without the file,
     *     or when a build that does not process resources left its {@code ${...}} placeholder in
     */
    static String version() {
        Properties properties = new Properties();
        try (InputStream in = DesktopLauncher.class.getResourceAsStream("/gamex-version.properties")) {
            if (in != null) properties.load(in);
        } catch (IOException e) {
            log.warn("The version could not be read from gamex-version.properties", e);
        }
        return version(properties.getProperty("version"));
    }

    /**
     * @param text the {@code version} value of {@code gamex-version.properties}, or null
     * @return the version, or {@code unknown} when the text is missing, blank or an unfilled placeholder
     */
    static String version(String text) {
        if (text == null || text.trim().isEmpty() || text.trim().startsWith("${")) return "unknown";
        return text.trim();
    }

    /**
     * Names the log level in use and warns about a value that is not one. The level itself was set when this class
     * loaded, before its logger existed; see {@link LogLevel}.
     * @param text the value of the {@code GAMEX_LOG_LEVEL} environment variable, or null
     * @return the level in use: the one the text names, or {@code INFO} when it is missing, blank or not a level
     */
    static String logLevel(String text) {
        if (LogLevel.isRejected(text)) {
            log.warn("{} is not trace, debug, info, warn or error: '{}'; using {}", LOG_LEVEL_ENV, text,
                LogLevel.DEFAULT);
        }
        return LogLevel.resolve(text);
    }

    /**
     * @param windowEnv the value of the {@code GAMEX_WINDOW} environment variable, or null
     * @param screenshotEnv the value of the {@code GAMEX_SCREENSHOT} environment variable, or null
     * @param settingsStore the saved settings, or null when they could not be read
     * @param defaults the settings defaults, which say whether the game is fullscreen when nothing is saved
     * @return whether the game starts fullscreen
     */
    static boolean shouldStartFullscreen(String windowEnv, String screenshotEnv, KeyValueStore settingsStore,
                                         SettingsDefaults defaults) {
        if (screenshotEnv != null && !screenshotEnv.isEmpty()) return false;
        if (isWindowForced(windowEnv)) return false;
        return GameSettings.load(settingsStore, defaults).isFullscreen();
    }

    /**
     * @param windowEnv the value of the {@code GAMEX_WINDOW} environment variable, or null
     * @return whether it asks for a windowed start, which a saved fullscreen setting must not undo
     */
    static boolean isWindowForced(String windowEnv) {
        return windowEnv != null && !windowEnv.trim().isEmpty();
    }

    private static KeyValueStore loadSavedSettingsStore() {
        try {
            return new GdxPreferencesStore(new Lwjgl3Preferences(GameSettings.PREFERENCES_NAME, ".prefs/"));
        } catch (RuntimeException e) {
            log.warn("The saved settings could not be read; fullscreen is as the settings defaults say", e);
            return null;
        }
    }

    /**
     * Reads a window size such as {@code 800x600}. The {@code GAMEX_WINDOW} environment variable carries one, for
     * trying the game at other sizes.
     * @param text the size as {@code WIDTHxHEIGHT}, or null
     * @param defaults the size from {@code assets/data/window.txt}
     * @return the width and height in pixels; the size of {@code defaults} when the text is missing, malformed,
     *     smaller than 160x120 or larger than 7680x4320
     */
    static int[] windowSize(String text, WindowDefaults defaults) {
        int[] fallback = {defaults.width, defaults.height};
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
            log.warn("GAMEX_WINDOW is not a size between {}x{} and {}x{} like 800x600: '{}'; using {}x{}",
                MIN_WINDOW_WIDTH, MIN_WINDOW_HEIGHT, MAX_WINDOW_WIDTH, MAX_WINDOW_HEIGHT, text, fallback[0],
                fallback[1]);
            return fallback;
        }
    }
}

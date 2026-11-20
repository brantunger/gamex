package io.github.brantunger.gamex.settings;

import io.github.brantunger.gamex.data.AssetText;
import io.github.brantunger.gamex.data.TuningFile;

import java.nio.file.Path;

/**
 * The size of the game's window when nothing else sets one, read from {@code assets/data/window.txt}. The desktop
 * launcher opens the window with it unless {@code GAMEX_WINDOW} names another size, and the renderers size their
 * cameras and the HUD its overlay with it until the window reports its own size. The launcher loads it once and
 * hands it to the game. Tests take their own copy from {@code TestTuning} or hand text to {@link #parse}.
 *
 * <p>The file holds {@code window.width} and {@code window.height}, in pixels, and nothing else. There is no size
 * behind the file: a missing file, a missing key, a key this class does not list and a size smaller than
 * {@value #MIN_WIDTH}x{@value #MIN_HEIGHT} or larger than {@value #MAX_WIDTH}x{@value #MAX_HEIGHT} each fail the
 * load with a message naming the file and, where there is one, the key.
 *
 * <p>The launcher reads the file before it creates the window, so before libGDX is up, with {@link #load(Path)}:
 * that looks where the game will look for its own files, so that the two cannot go by different copies.
 *
 * <p>Instances are immutable and safe to share.
 */
public final class WindowDefaults {

    /** Internal asset path of the window defaults. */
    public static final String PATH = "data/window.txt";

    /** The narrowest window, in pixels. */
    public static final int MIN_WIDTH = 160;
    /** The lowest window, in pixels. */
    public static final int MIN_HEIGHT = 120;
    /** The widest window, in pixels. */
    public static final int MAX_WIDTH = 7680;
    /** The tallest window, in pixels. */
    public static final int MAX_HEIGHT = 4320;

    private static final String WIDTH = "window.width";
    private static final String HEIGHT = "window.height";

    /** The width of the window, in pixels. */
    public final int width;
    /** The height of the window, in pixels. */
    public final int height;

    private WindowDefaults(TuningFile file) {
        width = file.getInt(WIDTH);
        if (width < MIN_WIDTH || width > MAX_WIDTH) {
            throw file.invalid(WIDTH, "must be from " + MIN_WIDTH + " to " + MAX_WIDTH);
        }
        height = file.getInt(HEIGHT);
        if (height < MIN_HEIGHT || height > MAX_HEIGHT) {
            throw file.invalid(HEIGHT, "must be from " + MIN_HEIGHT + " to " + MAX_HEIGHT);
        }
    }

    /**
     * Reads the window size from {@link #PATH}.
     * @return the loaded size
     * @throws IllegalArgumentException if the file breaks the format, lacks a key, holds a key this class does not
     *     list, or holds a size outside the limits
     * @throws RuntimeException if the file is missing or cannot be read; see {@link TuningFile#load(String, String...)}
     */
    public static WindowDefaults load() {
        return new WindowDefaults(TuningFile.load(PATH, WIDTH, HEIGHT));
    }

    /**
     * Reads the window size from {@link #PATH} without libGDX, from the places libGDX will read the game's files
     * from once it is up (see {@link AssetText#readAsInternal}): under a working directory if the file is there,
     * and from the classpath otherwise. This is how the desktop launcher reads it.
     * @param workingDir the directory that stands for the working directory
     * @return the loaded size
     * @throws IllegalArgumentException as {@link #load()} does
     * @throws IllegalStateException if the file is in neither place
     */
    public static WindowDefaults load(Path workingDir) {
        return parse(PATH, AssetText.readAsInternal("data", PATH, workingDir));
    }

    /**
     * Parses the text of a window defaults file.
     * @param assetPath path label included in error messages
     * @param text raw file contents
     * @return the parsed size
     * @throws IllegalArgumentException as {@link #load()} does
     */
    public static WindowDefaults parse(String assetPath, String text) {
        return new WindowDefaults(TuningFile.parseText(assetPath, text, WIDTH, HEIGHT));
    }
}

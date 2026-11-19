package io.github.brantunger.gamex.data;

import com.badlogic.gdx.Gdx;
import com.badlogic.gdx.files.FileHandle;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.io.UncheckedIOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

/**
 * Reads UTF-8 text files from {@code assets/} and splits them into lines, with or without a running libGDX
 * application.
 *
 * <p>When libGDX is up ({@code Gdx.files} is set), a file is read through {@code Gdx.files.internal}: the file at
 * that path under the working directory if there is one, and the copy on the classpath otherwise. That is so in
 * the game and in the {@code tools} model previewer. Without libGDX (the tests and the {@code tools} generators),
 * the first of these that is a regular file is read:
 * <ol>
 *   <li>the folder named by the system property {@code gamex.assets}, which the {@code core:test} and
 *       {@code tools:test} tasks set to {@code assets/} and nothing else sets;</li>
 *   <li>{@code ../assets} (the working directory is a subproject);</li>
 *   <li>{@code assets} (the working directory is the root project);</li>
 *   <li>the working directory itself (it is {@code assets/});</li>
 *   <li>the classpath.</li>
 * </ol>
 * Which copy was read (the path of the file, or the classpath) is logged at debug level in both cases.
 *
 * <p>The desktop launcher reads two files before libGDX is up and the game reads the rest after; it uses
 * {@link #readAsInternal}, which looks where {@code Gdx.files.internal} will, so that both read the same copy.
 */
public final class AssetText {

    private static final Logger log = LoggerFactory.getLogger(AssetText.class);

    private static final char BYTE_ORDER_MARK = 0xFEFF;

    private AssetText() {}

    /**
     * Reads a text file from {@code assets/}.
     * @param kind what the file is, in one lower-case word used by the error messages (for example {@code "level"}
     *     or {@code "data"}); null or empty is taken as {@code "text"}
     * @param assetPath internal path inside {@code assets/} (for example {@code "levels/first.map"})
     * @return the file's contents, decoded as UTF-8
     * @throws IllegalArgumentException if the path is null or blank
     * @throws com.badlogic.gdx.utils.GdxRuntimeException if libGDX is running and the file is missing or cannot
     *     be read
     * @throws IllegalStateException if libGDX is not running and the file is in none of the places listed above;
     *     the message lists the paths tried
     * @throws UncheckedIOException if libGDX is not running and the file is found and cannot be read
     */
    public static String read(String kind, String assetPath) {
        if (kind == null || kind.isEmpty()) kind = "text";
        if (assetPath == null || assetPath.trim().isEmpty()) {
            throw new IllegalArgumentException(
                Character.toUpperCase(kind.charAt(0)) + kind.substring(1) + " asset path must not be blank");
        }
        if (Gdx.files != null) {
            FileHandle handle = Gdx.files.internal(assetPath);
            String text = handle.readString("UTF-8");
            // The test libGDX makes: the file under the working directory if it exists, the classpath otherwise.
            File loose = handle.file();
            log.debug("Read {} asset {} through Gdx internal from {}", kind, assetPath,
                loose.exists() ? loose.getAbsolutePath() : "classpath");
            return text;
        }
        return readHeadless(kind, assetPath, Paths.get(""));
    }

    /**
     * Reads a text file the way {@link #read} does without libGDX, with the relative places resolved against a given
     * directory.
     * @param kind what the file is, for the error messages
     * @param assetPath internal path inside {@code assets/}
     * @param workingDir the directory that stands for the working directory
     * @return the file's contents, decoded as UTF-8
     */
    static String readHeadless(String kind, String assetPath, Path workingDir) {
        List<Path> candidates = new ArrayList<>();
        String configured = System.getProperty("gamex.assets");
        if (configured != null && !configured.trim().isEmpty()) {
            candidates.add(Paths.get(configured, assetPath));
        }
        candidates.add(workingDir.resolve(Paths.get("..", "assets", assetPath)));
        candidates.add(workingDir.resolve(Paths.get("assets", assetPath)));
        candidates.add(workingDir.resolve(assetPath));
        String text = readFirst(kind, assetPath, candidates);
        if (text != null) return text;
        throw new IllegalStateException("The " + kind + " asset " + assetPath + " was not found. Set the system "
            + "property gamex.assets to the assets folder, or run from the root project or a subproject. Tried: "
            + absolute(candidates));
    }

    /**
     * Reads a text file without libGDX from the places {@code Gdx.files.internal} looks in once libGDX is up: the
     * file at that path under the working directory if there is one, and the copy on the classpath otherwise. The
     * {@code gamex.assets} property and the {@code assets} folders {@link #read} tries without libGDX are not
     * looked in. The desktop launcher reads with this before it creates the window, so that it and the game read
     * the same copy of a file.
     * @param kind what the file is, in one lower-case word used by the error messages
     * @param assetPath internal path inside {@code assets/}
     * @param workingDir the directory that stands for the working directory
     * @return the file's contents, decoded as UTF-8
     * @throws IllegalStateException if the file is in neither place; the message names the path tried
     * @throws UncheckedIOException if the file is found and cannot be read
     */
    public static String readAsInternal(String kind, String assetPath, Path workingDir) {
        List<Path> candidates = Collections.singletonList(workingDir.resolve(assetPath));
        String text = readFirst(kind, assetPath, candidates);
        if (text != null) return text;
        throw new IllegalStateException("The " + kind + " asset " + assetPath + " was not found on the classpath or"
            + " at " + absolute(candidates).get(0));
    }

    /** @return the text of the first candidate that is a regular file, else of the classpath copy, else null */
    private static String readFirst(String kind, String assetPath, List<Path> candidates) {
        for (Path candidate : candidates) {
            if (!Files.isRegularFile(candidate)) continue;
            try {
                String text = Files.readString(candidate, StandardCharsets.UTF_8);
                log.debug("Read {} asset {} from {}", kind, assetPath, candidate.toAbsolutePath().normalize());
                return text;
            } catch (IOException e) {
                throw new UncheckedIOException("Failed to read " + kind + " asset " + candidate, e);
            }
        }
        String resourcePath = assetPath.replace('\\', '/');
        ClassLoader loader = AssetText.class.getClassLoader();
        if (loader != null) {
            try (InputStream in = loader.getResourceAsStream(resourcePath)) {
                if (in != null) {
                    String text = new String(in.readAllBytes(), StandardCharsets.UTF_8);
                    log.debug("Read {} asset {} from classpath", kind, assetPath);
                    return text;
                }
            } catch (IOException e) {
                throw new UncheckedIOException("Failed to read " + kind + " resource " + resourcePath, e);
            }
        }
        return null;
    }

    private static List<Path> absolute(List<Path> candidates) {
        List<Path> tried = new ArrayList<>(candidates.size());
        for (Path candidate : candidates) {
            tried.add(candidate.toAbsolutePath().normalize());
        }
        return tried;
    }

    /**
     * Splits text into lines. A leading byte order mark is dropped, and CR, LF and CRLF each end a line. The line
     * at index {@code i} is line {@code i + 1} of the file.
     * @param text the file's contents
     * @return the lines, without their line endings
     */
    public static List<String> splitLines(String text) {
        int start = (!text.isEmpty() && text.charAt(0) == BYTE_ORDER_MARK) ? 1 : 0;
        List<String> lines = new ArrayList<>();
        int lineStart = start;
        int len = text.length();
        for (int i = start; i < len; i++) {
            char c = text.charAt(i);
            if (c == '\r' || c == '\n') {
                lines.add(text.substring(lineStart, i));
                if (c == '\r' && i + 1 < len && text.charAt(i + 1) == '\n') {
                    i++;
                }
                lineStart = i + 1;
            }
        }
        if (lineStart < len) {
            lines.add(text.substring(lineStart, len));
        }
        return lines;
    }

    /**
     * @param trimmedLine a line with its leading and trailing whitespace removed
     * @return whether the line is a {@code //} comment
     */
    public static boolean isComment(String trimmedLine) {
        return trimmedLine.startsWith("//");
    }
}

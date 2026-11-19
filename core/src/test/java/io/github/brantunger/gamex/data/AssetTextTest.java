package io.github.brantunger.gamex.data;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import ch.qos.logback.classic.Level;
import com.badlogic.gdx.Files;
import com.badlogic.gdx.Gdx;
import com.badlogic.gdx.files.FileHandle;
import io.github.brantunger.gamex.LogCapture;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.IOException;
import java.lang.reflect.Proxy;
import java.nio.charset.StandardCharsets;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;

/** Verifies where {@link AssetText} looks for a file with and without libGDX, and how it splits text into lines. */
class AssetTextTest {

    /** A file that is also on the test classpath, holding the word "classpath". */
    private static final String LOOKUP_PATH = "data-fixtures/lookup.txt";

    private Files previousFiles;
    private String previousAssets;

    @BeforeEach
    void saveGlobals() {
        previousFiles = Gdx.files;
        previousAssets = System.getProperty("gamex.assets");
    }

    @AfterEach
    void restoreGlobals() {
        Gdx.files = previousFiles;
        if (previousAssets == null) {
            System.clearProperty("gamex.assets");
        } else {
            System.setProperty("gamex.assets", previousAssets);
        }
    }

    private static Path write(Path file, String text) throws IOException {
        java.nio.file.Files.createDirectories(file.getParent());
        java.nio.file.Files.write(file, text.getBytes(StandardCharsets.UTF_8));
        return file;
    }

    @Test
    void headlessLookupTriesTheConfiguredFolderThenTheProjectFoldersThenTheClasspath(@TempDir Path temp)
        throws IOException {
        Gdx.files = null;
        Path workingDir = java.nio.file.Files.createDirectories(temp.resolve("project").resolve("core"));
        Path configured = write(temp.resolve("configured").resolve(LOOKUP_PATH), "configured");
        Path parentAssets = write(temp.resolve("project").resolve("assets").resolve(LOOKUP_PATH), "parent assets");
        Path childAssets = write(workingDir.resolve("assets").resolve(LOOKUP_PATH), "child assets");
        Path inWorkingDir = write(workingDir.resolve(LOOKUP_PATH), "working directory");
        System.setProperty("gamex.assets", temp.resolve("configured").toString());

        assertEquals("configured", AssetText.readHeadless("data", LOOKUP_PATH, workingDir));
        java.nio.file.Files.delete(configured);
        assertEquals("parent assets", AssetText.readHeadless("data", LOOKUP_PATH, workingDir));
        java.nio.file.Files.delete(parentAssets);
        assertEquals("child assets", AssetText.readHeadless("data", LOOKUP_PATH, workingDir));
        java.nio.file.Files.delete(childAssets);
        assertEquals("working directory", AssetText.readHeadless("data", LOOKUP_PATH, workingDir));
        java.nio.file.Files.delete(inWorkingDir);
        assertEquals("classpath", AssetText.readHeadless("data", LOOKUP_PATH, workingDir).trim());

        // A blank property is no folder at all: the lookup starts at ../assets.
        write(parentAssets, "parent assets");
        System.setProperty("gamex.assets", "  ");
        assertEquals("parent assets", AssetText.readHeadless("data", LOOKUP_PATH, workingDir));
        System.clearProperty("gamex.assets");
        assertEquals("parent assets", AssetText.readHeadless("data", LOOKUP_PATH, workingDir));
    }

    @Test
    void aFileFoundNowhereIsReportedWithEveryPathTriedInOrder(@TempDir Path temp) {
        Gdx.files = null;
        Path workingDir = temp.resolve("project").resolve("core");
        Path configured = temp.resolve("configured");
        System.setProperty("gamex.assets", configured.toString());

        IllegalStateException e = assertThrows(IllegalStateException.class,
            () -> AssetText.readHeadless("data", "data/no-such-file.txt", workingDir));
        List<Path> tried = Arrays.asList(
            configured.resolve("data").resolve("no-such-file.txt").toAbsolutePath().normalize(),
            temp.resolve("project").resolve("assets").resolve("data").resolve("no-such-file.txt").toAbsolutePath()
                .normalize(),
            workingDir.resolve("assets").resolve("data").resolve("no-such-file.txt").toAbsolutePath().normalize(),
            workingDir.resolve("data").resolve("no-such-file.txt").toAbsolutePath().normalize());
        assertEquals("The data asset data/no-such-file.txt was not found. Set the system property gamex.assets to "
            + "the assets folder, or run from the root project or a subproject. Tried: " + tried, e.getMessage());
    }

    @Test
    void readWithoutLibgdxUsesTheHeadlessLookupFromTheRealWorkingDirectory(@TempDir Path temp) throws IOException {
        Gdx.files = null;
        // The file is on the test classpath only, so the last place looked in is reached.
        assertEquals("classpath", AssetText.read("data", LOOKUP_PATH).trim());
        // A loose file in the configured folder is read in place of the classpath copy.
        write(temp.resolve(LOOKUP_PATH), "loose");
        System.setProperty("gamex.assets", temp.toString());
        assertEquals("loose", AssetText.read("data", LOOKUP_PATH));

        IllegalStateException missing = assertThrows(IllegalStateException.class,
            () -> AssetText.read("data", "data/no-such-file.txt"));
        assertTrue(missing.getMessage().startsWith("The data asset data/no-such-file.txt was not found."),
            missing.getMessage());
    }

    @Test
    void readWithLibgdxAsksGdxFilesForTheInternalFileOnce() {
        List<String> requested = new ArrayList<>();
        Gdx.files = (Files) Proxy.newProxyInstance(
            Files.class.getClassLoader(),
            new Class<?>[] {Files.class},
            (proxy, method, args) -> {
                if ("internal".equals(method.getName()) && args != null && args.length == 1) {
                    String path = (String) args[0];
                    requested.add(path);
                    return new FileHandle(path) {
                        @Override
                        public String readString(String charset) {
                            return "from gdx as " + charset;
                        }
                    };
                }
                return null;
            });

        // The path is on the test classpath too; with libGDX up, only Gdx.files is asked.
        assertEquals("from gdx as UTF-8", AssetText.read("data", LOOKUP_PATH));
        assertEquals(Collections.singletonList(LOOKUP_PATH), requested);
    }

    @Test
    void aBlankPathIsRejectedWithTheKindOfAsset() {
        IllegalArgumentException blank = assertThrows(IllegalArgumentException.class,
            () -> AssetText.read("data", " \t"));
        assertEquals("Data asset path must not be blank", blank.getMessage());
        IllegalArgumentException none = assertThrows(IllegalArgumentException.class,
            () -> AssetText.read("shader", null));
        assertEquals("Shader asset path must not be blank", none.getMessage());
        // Without a kind the message is still the one about the path.
        IllegalArgumentException noKind = assertThrows(IllegalArgumentException.class,
            () -> AssetText.read(null, ""));
        assertEquals("Text asset path must not be blank", noKind.getMessage());
        IllegalArgumentException emptyKind = assertThrows(IllegalArgumentException.class,
            () -> AssetText.read("", null));
        assertEquals("Text asset path must not be blank", emptyKind.getMessage());
    }

    @Test
    void theCopyThatWasReadIsLoggedAtDebugLevel(@TempDir Path temp) throws IOException {
        Path loose = write(temp.resolve(LOOKUP_PATH), "loose");
        try (LogCapture capture = LogCapture.of(AssetText.class, Level.DEBUG)) {
            Gdx.files = null;
            AssetText.read("data", LOOKUP_PATH);
            System.setProperty("gamex.assets", temp.toString());
            AssetText.read("data", LOOKUP_PATH);
            // With libGDX up, the file of the handle exists when a loose copy is read and does not when the
            // classpath copy is: "loose.txt" stands for a file under the working directory.
            Gdx.files = (Files) Proxy.newProxyInstance(
                Files.class.getClassLoader(),
                new Class<?>[] {Files.class},
                (proxy, method, args) -> new FileHandle(
                    "loose.txt".equals(args[0]) ? loose.toFile() : new java.io.File((String) args[0])) {
                    @Override
                    public String readString(String charset) {
                        return "from gdx";
                    }
                });
            AssetText.read("level", "loose.txt");
            AssetText.read("level", "no-loose-copy/bundled.txt");

            assertEquals(Arrays.asList(
                "Read data asset " + LOOKUP_PATH + " from classpath",
                "Read data asset " + LOOKUP_PATH + " from " + loose.toAbsolutePath().normalize(),
                "Read level asset loose.txt through Gdx internal from " + loose.toFile().getAbsolutePath(),
                "Read level asset no-loose-copy/bundled.txt through Gdx internal from classpath"),
                capture.messages(Level.DEBUG));
        }
    }

    @Test
    void splitLinesDropsAByteOrderMarkAndAcceptsEveryLineEnding() {
        assertEquals(Arrays.asList("a", "b", "", "c", "d"), AssetText.splitLines("\uFEFFa\r\nb\n\nc\rd"));
        assertEquals(Arrays.asList("a", "b"), AssetText.splitLines("a\nb\n"));
        assertEquals(Arrays.asList("", "a"), AssetText.splitLines("\r\na\r\n"));
        assertEquals(Collections.emptyList(), AssetText.splitLines(""));
        assertEquals(Collections.emptyList(), AssetText.splitLines("\uFEFF"));
        // Only a leading mark is dropped.
        assertEquals(Arrays.asList("a", "\uFEFFb"), AssetText.splitLines("a\n\uFEFFb"));
    }

    @Test
    void aCommentIsALineStartingWithTwoSlashes() {
        assertTrue(AssetText.isComment("// note"));
        assertTrue(AssetText.isComment("//"));
        assertFalse(AssetText.isComment("/ not a comment"));
        assertFalse(AssetText.isComment("key: value // not a comment"));
        assertFalse(AssetText.isComment(""));
    }
}

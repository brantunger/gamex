package io.github.brantunger.gamex.render;

import com.badlogic.gdx.Files;
import com.badlogic.gdx.Gdx;
import io.github.brantunger.gamex.TestTuning;
import io.github.brantunger.gamex.data.TuningFile;
import io.github.brantunger.gamex.world.GameMap;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class WallTexturesTest {

    private Files previousFiles;
    private String previousAssets;

    @BeforeEach
    void saveGlobals() {
        previousFiles = Gdx.files;
        previousAssets = System.getProperty("gamex.assets");
        Gdx.files = null;
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

    private static void assertError(String expected, Runnable action) {
        IllegalArgumentException e = assertThrows(IllegalArgumentException.class, action::run);
        assertEquals(expected, e.getMessage());
    }

    private static WallTextures parse(String text) {
        return WallTextures.from(
            TuningFile.parseText(WallTextures.FILE, text, WallTextures.wallKeys(), Collections.emptyList()));
    }

    /** A table in which no two wall types share a file, so a type that took another's line, or none, shows. */
    @Test
    void everyWallTypeTakesTheTextureOfItsOwnLine() {
        WallTextures table = parse("#: first.png\n%: second.png\n&: third.png\n");

        assertEquals("#%&", GameMap.WALL_TYPES);
        assertEquals("first.png", table.file('#'));
        assertEquals("second.png", table.file('%'));
        assertEquals("third.png", table.file('&'));
        assertEquals(Arrays.asList("first.png", "second.png", "third.png"), new ArrayList<>(table.files()));
    }

    /** Whatever textures the shipped table names, it loads and gives every wall type a .png file. */
    @Test
    void theShippedTableLoads() {
        WallTextures shipped = WallTextures.load();

        for (char wall : GameMap.WALL_TYPES.toCharArray()) {
            assertTrue(shipped.file(wall).endsWith(".png"), wall + ": " + shipped.file(wall));
            assertTrue(shipped.files().contains(shipped.file(wall)));
        }
    }

    @Test
    void aCharacterWithNoLineHasNoFallbackTexture() {
        WallTextures shipped = WallTextures.load();

        assertError("No texture for wall character '@' in data/walls.txt, which lists [#, %, &]",
            () -> shipped.file('@'));
        assertError("No texture for wall character '.' in data/walls.txt, which lists [#, %, &]",
            () -> shipped.file(GameMap.FLOOR));
    }

    @Test
    void aWallCharacterMissingFromTheFileFailsTheLoad() {
        assertError("Missing '&' key in data/walls.txt", () -> parse("#: brick.png\n%: panel.png\n"));
    }

    @Test
    void aLineForSomethingThatIsNotAWallCharacterFailsTheLoad() {
        assertError("Unknown key '@' in data/walls.txt at line 4",
            () -> parse("#: brick.png\n%: panel.png\n&: stone.png\n@: brick.png\n"));
        assertError("Duplicate '#' key in data/walls.txt at line 2",
            () -> parse("#: brick.png\n#: panel.png\n&: stone.png\n"));
    }

    @Test
    void aValueThatIsNotAPlainPngFileNameFailsTheLoad() {
        assertError("Empty '%' key in data/walls.txt at line 2", () -> parse("#: brick.png\n%:\n&: stone.png\n"));
        assertError("Invalid '%' in data/walls.txt at line 2: must be the name of a .png file in assets/textures/,"
            + " got 'panel'", () -> parse("#: brick.png\n%: panel\n&: stone.png\n"));
        assertError("Invalid '&' in data/walls.txt at line 3: must be the name of a .png file in assets/textures/,"
            + " got '.png'", () -> parse("#: brick.png\n%: panel.png\n&: .png\n"));
        assertError("Invalid '#' in data/walls.txt at line 1: must be a file name without a folder,"
            + " got '../models/brick.png'", () -> parse("#: ../models/brick.png\n%: panel.png\n&: stone.png\n"));
        assertError("Invalid '#' in data/walls.txt at line 1: must be a file name without a folder,"
            + " got 'walls\\brick.png'", () -> parse("#: walls\\brick.png\n%: panel.png\n&: stone.png\n"));
    }

    @Test
    void anEditedLooseCopyOfTheTableChangesTheTextureOfAWall(@TempDir Path temp) throws IOException {
        String shipped = TestTuning.text(WallTextures.FILE);
        assertTrue(shipped.contains("%: panel.png"), shipped);
        Path loose = temp.resolve(WallTextures.FILE);
        java.nio.file.Files.createDirectories(loose.getParent());
        java.nio.file.Files.write(loose,
            shipped.replace("%: panel.png", "%: stone.png").getBytes(StandardCharsets.UTF_8));
        System.setProperty("gamex.assets", temp.toString());

        WallTextures edited = WallTextures.load();

        assertEquals("stone.png", edited.file('%'));
        assertEquals("brick.png", edited.file('#'));
        assertEquals(Arrays.asList("brick.png", "stone.png"), new ArrayList<>(edited.files()));

        // A wall character whose line was deleted stops the load; it does not fall back to brick.
        java.nio.file.Files.write(loose, shipped.replace("%: panel.png", "").getBytes(StandardCharsets.UTF_8));
        assertError("Missing '%' key in data/walls.txt", WallTextures::load);
    }

    @Test
    void aTableBuiltInCodeIsCheckedAndCopied() {
        Map<Character, String> files = new LinkedHashMap<>();
        files.put('#', "brick.png");
        files.put('%', "panel.png");
        files.put('&', "stone.png");
        WallTextures table = new WallTextures(files);
        files.put('%', "stone.png");

        assertEquals("panel.png", table.file('%'));
        assertError("No texture for wall character '@' in data/walls.txt, which lists [#, %, &]",
            () -> table.file('@'));
        assertError("files must not be null", () -> new WallTextures(null));
        assertError("The texture of wall character '%' must be the name of a .png file in assets/textures/",
            () -> new WallTextures(Collections.singletonMap('%', "panel.txt")));
        assertError("The texture of wall character '%' must be the name of a .png file in assets/textures/",
            () -> new WallTextures(Collections.singletonMap('%', null)));
        assertError("A wall character must not be null",
            () -> new WallTextures(Collections.singletonMap(null, "panel.png")));
    }

    /** A table built in code holds exactly the wall characters, as one read from the file does. */
    @Test
    void aTableBuiltInCodeNeedsEveryWallCharacterAndNoOther() {
        Map<Character, String> files = new LinkedHashMap<>();
        files.put('#', "brick.png");
        files.put('%', "panel.png");
        assertError("No texture for wall character '&'", () -> new WallTextures(files));
        assertError("No texture for wall character '#'", () -> new WallTextures(Collections.emptyMap()));

        files.put('&', "stone.png");
        files.put('@', "brick.png");
        assertError("'@' is not a wall character; they are #%&", () -> new WallTextures(files));
    }

    @Test
    void eachTextureFileIsLoadedOnceHoweverManyWallTypesUseIt() {
        List<String> requested = new ArrayList<>();

        Map<String, Object> loaded = ProceduralTextures.loadEach(
            Arrays.asList("brick.png", "stone.png", "brick.png"), file -> {
                requested.add(file);
                return new Object();
            });

        assertEquals(Arrays.asList("brick.png", "stone.png"), requested);
        assertEquals(Arrays.asList("brick.png", "stone.png"), new ArrayList<>(loaded.keySet()));
        assertSame(loaded.get("brick.png"), loaded.get("brick.png"));
    }
}

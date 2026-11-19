package io.github.brantunger.gamex.hud;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.badlogic.gdx.Gdx;
import com.badlogic.gdx.graphics.g2d.Batch;
import io.github.brantunger.gamex.GameSimulation;
import io.github.brantunger.gamex.TestTuning;
import io.github.brantunger.gamex.data.AssetText;
import io.github.brantunger.gamex.player.Difficulty;
import io.github.brantunger.gamex.player.PlayerLoadout;
import io.github.brantunger.gamex.settings.GameSettings;
import io.github.brantunger.gamex.settings.MapKeyValueStore;
import io.github.brantunger.gamex.settings.Medal;
import io.github.brantunger.gamex.settings.RunStats;
import io.github.brantunger.gamex.settings.SaveData;
import io.github.brantunger.gamex.world.Levels;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.IOException;
import java.lang.reflect.Proxy;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;

class PixelFontTest {

    private static final PixelFont FONT = TestTuning.FONT;
    private static final UiStrings STRINGS = TestTuning.STRINGS;

    @Test
    void everySupportedCharacterHasAValidThreeByFiveGlyph() {
        String chars = FONT.supportedCharacters();
        assertTrue(chars.length() >= 50);
        for (int i = 0; i < chars.length(); i++) {
            char c = chars.charAt(i);
            String glyph = FONT.glyphFor(c);
            assertNotNull(glyph, "missing glyph for " + c);
            assertEquals(15, glyph.length(), "glyph length for " + c);
            boolean anyLit = false;
            for (int bit = 0; bit < glyph.length(); bit++) {
                char px = glyph.charAt(bit);
                assertTrue(px == '0' || px == '1', "invalid bit in glyph for " + c);
                if (px == '1') anyLit = true;
            }
            assertTrue(anyLit, "blank glyph for " + c);
        }
    }

    @Test
    void punctuationUsedInMenusAndTalliesIsSupported() {
        String sample = "+25% [S] <01:42.5> *NEW!*";
        for (int i = 0; i < sample.length(); i++) {
            char c = sample.charAt(i);
            if (c == ' ') continue;
            assertNotNull(FONT.glyphFor(c), "unsupported HUD character: " + c);
        }
        assertEquals((sample.length() * 4 - 1) * 2f, PixelFont.width(sample, 2f), 1e-5f);
    }

    @Test
    void everyCharacterOfStageTextAndMenuOptionsHasAGlyph() {
        List<String> texts = new ArrayList<>();
        List<Levels.Stage> stages = new ArrayList<>(Levels.campaign());
        stages.add(Levels.trainingRange());
        for (Levels.Stage stage : stages) {
            texts.add(stage.title);
            for (String line : stage.briefing) texts.add(STRINGS.fill(line, "a briefing of " + stage.id));
        }
        for (Difficulty difficulty : Difficulty.values()) texts.add(STRINGS.label(difficulty));
        SaveData save = new SaveData(new MapKeyValueStore());
        save.saveCampaignRun(0, PlayerLoadout.fresh());
        for (MenuState.Screen screen : MenuState.Screen.values()) {
            texts.addAll(new MenuState(new GameSettings(), save, screen).getOptions());
        }
        for (String text : texts) {
            for (int i = 0; i < text.length(); i++) {
                char c = text.charAt(i);
                if (c == ' ') continue;
                assertNotNull(FONT.glyphFor(c), "no glyph for '" + c + "' in: " + text);
            }
        }
    }

    @Test
    void everyCharacterOfObjectiveMedalAndRecordTextHasAGlyph() {
        List<String> texts = new ArrayList<>();
        for (GameSimulation.Objective objective : GameSimulation.Objective.values()) {
            texts.add(STRINGS.objectiveLine(objective));
        }
        for (Medal medal : Medal.values()) texts.add(STRINGS.label(medal));

        // A save with a time and medals on every difficulty, so Select Stage shows every suffix it can.
        SaveData save = new SaveData(new MapKeyValueStore());
        List<Levels.Stage> campaign = Levels.campaign();
        save.unlockStage(campaign.size() - 1);
        for (Difficulty difficulty : Difficulty.values()) {
            RunStats perfect = new RunStats(41.5f + difficulty.ordinal(), 8, 8, 0f, 9, 9, 10, 10);
            SaveData.CompletionSummary summary = save.recordCompletion(campaign.get(difficulty.ordinal()).id,
                difficulty, 50f, perfect, true);
            texts.add(HudRenderer.tallyTimeLine(STRINGS, summary, 50f));
            texts.add(HudRenderer.tallyBestLine(STRINGS, summary, difficulty));
            texts.add(HudRenderer.tallyClearedLine(STRINGS, summary, true));
            texts.add(HudRenderer.tallyClearedLine(STRINGS, summary, false));
            texts.add(HudRenderer.tallyStatsLine(STRINGS, summary));
            texts.add(HudRenderer.tallyMedalLine(STRINGS, summary));
            SaveData.CompletionSummary repeat = save.recordCompletion(campaign.get(difficulty.ordinal()).id,
                difficulty, 50f, new RunStats(90f, 0, 8, 40f, 0, 9, 0, 0), true);
            texts.add(HudRenderer.tallyBestLine(STRINGS, repeat, difficulty));
            texts.add(HudRenderer.tallyStatsLine(STRINGS, repeat));
            texts.add(HudRenderer.tallyMedalLine(STRINGS, repeat));
        }
        texts.add(HudRenderer.tallyMedalLine(STRINGS, save.recordCompletion("range", Difficulty.NORMAL, 45f,
            new RunStats(30f, 11, 11, 0f, 0, 0, 5, 5), false)));
        MenuState stageSelect = new MenuState(new GameSettings(), save, MenuState.Screen.STAGE_SELECT);
        texts.addAll(stageSelect.getOptions());
        assertTrue(stageSelect.getOptions().get(0).contains("(EASY)"), "the seeded save shows a best time");
        texts.add(stageSelect.getStageSelectDetail());

        for (String text : texts) {
            assertNotNull(text);
            for (int i = 0; i < text.length(); i++) {
                char c = text.charAt(i);
                if (c == ' ') continue;
                assertNotNull(FONT.glyphFor(c), "no glyph for '" + c + "' in: " + text);
            }
        }
    }

    @Test
    void tildeHasAGlyph() {
        assertEquals("000011110000000", FONT.glyphFor('~'), "a tilde two rows high, in the upper half");
    }

    @Test
    void shippedGlyphsAreTheBitStringsTheFontHeldInCode() {
        // The table PixelFont held before its glyphs moved to data/pixel-font.txt, copied unchanged.
        String characters = "ABCDEFGHIJKLMNOPQRSTUVWXYZ0123456789/-:.,%+!?()<>[]'*~";
        String[] glyphs = {
            "010101111101101", "110101110101110", "011100100100011", "110101101101110", "111100110100111",
            "111100110100100", "011100101101011", "101101111101101", "111010010010111", "001001001101010",
            "101101110101101", "100100100100111", "101111111101101", "110101101101101", "010101101101010",
            "110101110100100", "010101101110011", "110101110101101", "011100010001110", "111010010010010",
            "101101101101111", "101101101101010", "101101111111101", "101101010101101", "101101010010010",
            "111001010100111", "111101101101111", "010110010010111", "110001010100111", "110001010001110",
            "101101111001001", "111100110001110", "011100111101111", "111001010010010", "111101111101111",
            "111101111001110", "001001010100100", "000000111000000", "000010000010000",
            "000000000000010", "000000000010100", "101001010100101", "000010111010000", "010010010000010",
            "110001010000010", "001010010010001", "100010010010100", "001010100010001", "100010001010100",
            "110100100100110", "011001001001011", "010010000000000", "101010111010101",
            "000011110000000"
        };
        assertEquals(54, glyphs.length);
        assertEquals(characters, FONT.supportedCharacters());
        for (int i = 0; i < glyphs.length; i++) {
            assertEquals(glyphs[i], FONT.glyphFor(characters.charAt(i)), "glyph " + characters.charAt(i));
        }
    }

    @Test
    void anAlteredGlyphFileChangesWhatIsDrawn() {
        String shipped = shippedText();
        String block = "glyph A\n.#.\n#.#\n###\n#.#\n#.#\n";
        assertTrue(shipped.contains(block), "the shipped A");
        PixelFont altered = PixelFont.parse(PixelFont.PATH,
            shipped.replace(block, "glyph A\n###\n#.#\n###\n#.#\n#.#\n"));

        // One square per lit pixel, as "column,row" from the text's bottom left corner.
        assertEquals(Arrays.asList("1,4", "0,3", "2,3", "0,2", "1,2", "2,2", "0,1", "2,1", "0,0", "2,0"),
            drawnCells(FONT, "A"));
        assertEquals(
            Arrays.asList("0,4", "1,4", "2,4", "0,3", "2,3", "0,2", "1,2", "2,2", "0,1", "2,1", "0,0", "2,0"),
            drawnCells(altered, "A"));
        assertEquals("111101111101101", altered.glyphFor('A'));
        assertEquals(drawnCells(FONT, "B"), drawnCells(altered, "B"), "the other glyphs are untouched");
    }

    @Test
    void addingAGlyphToTheFileMakesItsCharacterDrawable() {
        assertFalse(FONT.hasGlyph('_'));
        assertFalse(GlyphCheck.isDrawable(FONT, '_'));
        assertEquals(Collections.emptyList(), drawnCells(FONT, "_"));

        PixelFont extended = PixelFont.parse(PixelFont.PATH, shippedText() + "\nglyph _\n...\n...\n...\n...\n###\n");

        assertEquals(FONT.supportedCharacters() + "_", extended.supportedCharacters());
        assertTrue(GlyphCheck.isDrawable(extended, '_'));
        assertEquals("HOLD_FIRE", GlyphCheck.require(extended, "HOLD_FIRE", "a test"));
        // The second cell of the text starts four font pixels along.
        assertEquals(Arrays.asList("4,0", "5,0", "6,0"), drawnCells(extended, " _"));
    }

    @Test
    void lowerCaseDrawsAsCapitalsAndUnknownCharactersAndSpacesAreGaps() {
        assertEquals(drawnCells(FONT, "HP 5"), drawnCells(FONT, "hp 5"));
        assertEquals(drawnCells(FONT, "A  B"), drawnCells(FONT, "A_\tB"));
        assertTrue(FONT.hasGlyph('q'));
        assertFalse(FONT.hasGlyph(' '));
    }

    @Test
    void glyphsForTheSignsTheFileSyntaxUsesLoad() {
        // The colon, the slash that starts a comment, and the two cell characters, with CRLF line ends, a byte
        // order mark, indented lines, and blank lines and comments inside a block.
        PixelFont font = PixelFont.parse("data/test-font.txt", "\uFEFF// a font\r\n"
            + "glyph :\r\n...\r\n.#.\r\n...\r\n.#.\r\n...\r\n"
            + "// the slash is not a comment\r\n"
            + "glyph /\r\n..#\r\n..#\r\n\r\n  .#.  \r\n// between rows\r\n#..\r\n#..\r\n"
            + "\tglyph   #\r\n#.#\r\n###\r\n#.#\r\n###\r\n#.#\r\n"
            + "glyph .\r\n...\r\n...\r\n...\r\n...\r\n.#.");

        assertEquals(":/#.", font.supportedCharacters());
        assertEquals("000010000010000", font.glyphFor(':'));
        assertEquals("001001010100100", font.glyphFor('/'));
        assertEquals("101111101111101", font.glyphFor('#'));
        assertEquals("000000000000010", font.glyphFor('.'));
        assertEquals(FONT.glyphFor(':'), font.glyphFor(':'));
        assertEquals(FONT.glyphFor('/'), font.glyphFor('/'));
        assertEquals(FONT.glyphFor('.'), font.glyphFor('.'));
    }

    @Test
    void aBrokenGlyphFileIsRejectedNamingTheFileTheGlyphAndTheLine() {
        String a = "glyph A\n.#.\n#.#\n###\n#.#\n#.#\n";
        assertBroken("Glyph 'B' has 4 rows, needs 5 in data/test-font.txt at line 7",
            a + "glyph B\n##.\n#.#\n##.\n#.#\n");
        assertBroken("Glyph 'A' has 2 rows, needs 5 in data/test-font.txt at line 2",
            "// short\nglyph A\n.#.\n#.#\nglyph B\n##.\n#.#\n##.\n#.#\n##.\n");
        assertBroken("Glyph 'A' has 0 rows, needs 5 in data/test-font.txt at line 1", "glyph A\n");
        assertBroken("Expected 'glyph' and one character after the 5 rows of glyph 'A', got '###' in "
            + "data/test-font.txt at line 7", a + "###\n");
        assertBroken("Glyph 'A' row '.##.' must be 3 cells wide, got 4 in data/test-font.txt at line 3",
            "glyph A\n.#.\n.##.\n###\n#.#\n#.#\n");
        assertBroken("Glyph 'A' row '#.' must be 3 cells wide, got 2 in data/test-font.txt at line 6",
            "glyph A\n.#.\n#.#\n###\n#.#\n#.\n");
        assertBroken("Glyph 'A' row '#1#' holds '1'; a cell is '#' or '.', in data/test-font.txt at line 3",
            "glyph A\n.#.\n#1#\n###\n#.#\n#.#\n");
        assertBroken("Glyph 'A' row '# #' holds ' '; a cell is '#' or '.', in data/test-font.txt at line 2",
            "glyph A\n# #\n#.#\n###\n#.#\n#.#\n");
        assertBroken("Duplicate glyph 'A' in data/test-font.txt at line 8", a + "\n" + a);
        assertBroken("Glyph '-' has no lit pixel in data/test-font.txt at line 7",
            a + "glyph -\n...\n...\n...\n...\n...\n");
        assertBroken("Expected 'glyph' and one character, got 'glyph' in data/test-font.txt at line 1", "glyph\n");
        assertBroken("Expected 'glyph' and one character, got 'glyph //' in data/test-font.txt at line 1",
            "glyph //\n");
        assertBroken("Expected 'glyph' and one character, got 'glyphA' in data/test-font.txt at line 1", "glyphA\n");
        assertBroken("Expected 'glyph' and one character, got 'A: 010101111101101' in data/test-font.txt at line 2",
            "\nA: 010101111101101\n");
        assertBroken("Glyph 'a' is lower case and text is drawn in capitals; define 'A' instead, in "
            + "data/test-font.txt at line 1", "glyph a\n.#.\n#.#\n###\n#.#\n#.#\n");
        assertBroken("No glyphs in data/test-font.txt", "// nothing drawn yet\n\n");
        assertBroken("No glyphs in data/test-font.txt", "");
    }

    @Test
    void loadsTheGlyphFileTheAssetsFolderHoldsWithoutLibgdx(@TempDir Path temp) throws IOException {
        com.badlogic.gdx.Files previousFiles = Gdx.files;
        String previousAssets = System.getProperty("gamex.assets");
        try {
            Gdx.files = null;
            assertEquals(FONT.supportedCharacters(), PixelFont.load().supportedCharacters());

            // No class is recompiled between the two loads: an edited file is all a new glyph takes.
            Path file = Files.createDirectories(temp.resolve("data")).resolve("pixel-font.txt");
            Files.write(file, "glyph _\n...\n...\n...\n...\n###\nglyph A\n###\n###\n###\n###\n###\n"
                .getBytes(StandardCharsets.UTF_8));
            System.setProperty("gamex.assets", temp.toString());
            PixelFont edited = PixelFont.load();
            assertEquals("_A", edited.supportedCharacters());
            assertEquals("111111111111111", edited.glyphFor('A'));

            Files.write(file, "glyph A\n###\n###\n".getBytes(StandardCharsets.UTF_8));
            IllegalArgumentException error = assertThrows(IllegalArgumentException.class, PixelFont::load);
            assertEquals("Glyph 'A' has 2 rows, needs 5 in data/pixel-font.txt at line 1", error.getMessage());
        } finally {
            Gdx.files = previousFiles;
            if (previousAssets == null) {
                System.clearProperty("gamex.assets");
            } else {
                System.setProperty("gamex.assets", previousAssets);
            }
        }
    }

    /** @return the text of the shipped glyph file, with LF line ends whatever the checkout gave it */
    private static String shippedText() {
        return String.join("\n", AssetText.splitLines(AssetText.read("data", PixelFont.PATH))) + "\n";
    }

    private static void assertBroken(String message, String text) {
        IllegalArgumentException error = assertThrows(IllegalArgumentException.class,
            () -> PixelFont.parse("data/test-font.txt", text));
        assertEquals(message, error.getMessage());
    }

    /**
     * Draws text at the origin with a font pixel of one unit, into a batch that only records.
     * @return the squares drawn, in drawing order, each as {@code "column,row"} counted from the bottom left
     */
    private static List<String> drawnCells(PixelFont font, String text) {
        List<String> cells = new ArrayList<>();
        Batch batch = (Batch) Proxy.newProxyInstance(Batch.class.getClassLoader(), new Class<?>[] {Batch.class},
            (proxy, method, args) -> {
                if (!method.getName().equals("draw") || args.length != 5) {
                    throw new UnsupportedOperationException(method.toString());
                }
                assertEquals(1f, (Float) args[3], 0f);
                assertEquals(1f, (Float) args[4], 0f);
                cells.add(Math.round((Float) args[1]) + "," + Math.round((Float) args[2]));
                return null;
            });
        font.draw(batch, null, text, 0f, 0f, 1f);
        return cells;
    }
}

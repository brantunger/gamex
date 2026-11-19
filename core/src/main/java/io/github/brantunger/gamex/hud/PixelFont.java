package io.github.brantunger.gamex.hud;

import com.badlogic.gdx.graphics.Texture;
import com.badlogic.gdx.graphics.g2d.Batch;
import io.github.brantunger.gamex.data.AssetText;

import java.util.ArrayList;
import java.util.List;

/**
 * A tiny font of capitals, digits and common signs, three pixels wide and five tall. Each glyph is drawn as flat
 * squares, so the font needs no texture of its own. The glyphs are loaded once at start-up from
 * {@code assets/data/pixel-font.txt} and the font is passed to whatever draws or checks text; tests build their
 * own with {@link #parse}.
 *
 * <p>The file holds one block per character: a header line {@code glyph A}, then five rows of three cells, top row
 * first, where {@code #} is a lit pixel and {@code .} an empty one. The character is the single character after
 * {@code "glyph "}, so {@code glyph :} and {@code glyph /} are the colon and the slash. Blank lines and whole-line
 * {@code //} comments are skipped. A block with the wrong number of rows, a row of the wrong width or with another
 * character in it, a glyph with no lit pixel, a character defined twice, a lower-case character (text is drawn in
 * capitals, so it could never be shown) and a file with no glyph each fail the load with a message naming the
 * file, and the glyph and the line where there is one (a file with no glyph names the file alone; a bad header line
 * is quoted in place of a glyph).
 *
 * <p>Instances are immutable and safe to share.
 */
public final class PixelFont {

    /** Internal asset path of the glyph file. */
    public static final String PATH = "data/pixel-font.txt";

    private static final int GLYPH_WIDTH = 3;
    private static final int GLYPH_HEIGHT = 5;
    private static final String HEADER = "glyph";
    private static final char LIT_CELL = '#';
    private static final char EMPTY_CELL = '.';

    /** Every character with a glyph, in file order. */
    private final String characters;
    /** One string per character of {@link #characters}: five rows of three pixels, top row first. */
    private final String[] glyphs;

    private PixelFont(String characters, String[] glyphs) {
        this.characters = characters;
        this.glyphs = glyphs;
    }

    /**
     * Reads the font from {@link #PATH}.
     * @return the loaded font
     * @throws IllegalArgumentException if the file breaks the format described on this class
     */
    public static PixelFont load() {
        return parse(PATH, AssetText.read("data", PATH));
    }

    /**
     * Parses the text of a glyph file.
     * @param assetPath path label included in error messages
     * @param text raw file contents
     * @return the parsed font
     * @throws IllegalArgumentException as {@link #load} does
     */
    public static PixelFont parse(String assetPath, String text) {
        StringBuilder characters = new StringBuilder();
        List<String> glyphs = new ArrayList<>();
        List<String> lines = AssetText.splitLines(text);
        // The block being read: its character, the line of its header and the rows read so far. No block is open
        // while rows is null.
        char current = 0;
        int headerLine = 0;
        StringBuilder rows = null;
        for (int i = 0; i < lines.size(); i++) {
            String line = lines.get(i).trim();
            int lineNumber = i + 1;
            if (line.isEmpty() || AssetText.isComment(line)) continue;
            if (rows == null || isHeader(line)) {
                if (rows != null) {
                    throw tooFewRows(current, rows, assetPath, headerLine);
                }
                if (!isHeader(line) && characters.length() > 0) {
                    // A sixth row, or anything else that follows a finished block: name the glyph it follows.
                    throw new IllegalArgumentException("Expected 'glyph' and one character after the " + GLYPH_HEIGHT
                        + " rows of glyph '" + current + "', got '" + line + "' in " + assetPath + " at line "
                        + lineNumber);
                }
                current = headerCharacter(line, assetPath, lineNumber);
                if (characters.indexOf(String.valueOf(current)) >= 0) {
                    throw new IllegalArgumentException("Duplicate glyph '" + current + "' in " + assetPath
                        + " at line " + lineNumber);
                }
                headerLine = lineNumber;
                rows = new StringBuilder();
                continue;
            }
            appendRow(rows, line, current, assetPath, lineNumber);
            if (rows.length() < GLYPH_WIDTH * GLYPH_HEIGHT) continue;
            if (rows.indexOf("1") < 0) {
                throw new IllegalArgumentException("Glyph '" + current + "' has no lit pixel in " + assetPath
                    + " at line " + headerLine);
            }
            characters.append(current);
            glyphs.add(rows.toString());
            rows = null;
        }
        if (rows != null) {
            throw tooFewRows(current, rows, assetPath, headerLine);
        }
        if (glyphs.isEmpty()) {
            throw new IllegalArgumentException("No glyphs in " + assetPath);
        }
        return new PixelFont(characters.toString(), glyphs.toArray(new String[0]));
    }

    /** @return whether a trimmed line is the word {@code glyph}, alone or followed by white space. */
    private static boolean isHeader(String line) {
        return line.startsWith(HEADER)
            && (line.length() == HEADER.length() || Character.isWhitespace(line.charAt(HEADER.length())));
    }

    private static char headerCharacter(String line, String assetPath, int lineNumber) {
        String name = isHeader(line) ? line.substring(HEADER.length()).trim() : "";
        if (name.length() != 1) {
            throw new IllegalArgumentException("Expected 'glyph' and one character, got '" + line + "' in "
                + assetPath + " at line " + lineNumber);
        }
        char c = name.charAt(0);
        if (Character.toUpperCase(c) != c) {
            throw new IllegalArgumentException("Glyph '" + c + "' is lower case and text is drawn in capitals; "
                + "define '" + Character.toUpperCase(c) + "' instead, in " + assetPath + " at line " + lineNumber);
        }
        return c;
    }

    private static void appendRow(StringBuilder rows, String line, char glyph, String assetPath, int lineNumber) {
        if (line.length() != GLYPH_WIDTH) {
            throw new IllegalArgumentException("Glyph '" + glyph + "' row '" + line + "' must be " + GLYPH_WIDTH
                + " cells wide, got " + line.length() + " in " + assetPath + " at line " + lineNumber);
        }
        for (int col = 0; col < GLYPH_WIDTH; col++) {
            char cell = line.charAt(col);
            if (cell != LIT_CELL && cell != EMPTY_CELL) {
                throw new IllegalArgumentException("Glyph '" + glyph + "' row '" + line + "' holds '" + cell
                    + "'; a cell is '" + LIT_CELL + "' or '" + EMPTY_CELL + "', in " + assetPath + " at line "
                    + lineNumber);
            }
            rows.append(cell == LIT_CELL ? '1' : '0');
        }
    }

    private static IllegalArgumentException tooFewRows(char glyph, StringBuilder rows, String assetPath,
                                                       int headerLine) {
        return new IllegalArgumentException("Glyph '" + glyph + "' has " + rows.length() / GLYPH_WIDTH
            + " rows, needs " + GLYPH_HEIGHT + " in " + assetPath + " at line " + headerLine);
    }

    /** @return all supported non-space characters in glyph-file order. */
    public String supportedCharacters() {
        return characters;
    }

    /**
     * @param c a character; a lower-case letter is looked up as its capital
     * @return the raw 15-character bitmap string for {@code c}, of {@code 1} for a lit pixel and {@code 0} for an
     *     empty one, or null if unsupported
     */
    String glyphFor(char c) {
        int index = characters.indexOf(Character.toUpperCase(c));
        return index < 0 ? null : glyphs[index];
    }

    /**
     * @param c a character
     * @return whether the font has a glyph for it; a lower-case letter has one if its capital does, and a space
     *     has none
     */
    public boolean hasGlyph(char c) {
        return glyphFor(c) != null;
    }

    /**
     * @param text the text
     * @param pixelSize the size of one font pixel in screen units
     * @return the width the text takes when drawn
     */
    public static float width(String text, float pixelSize) {
        return text.isEmpty() ? 0f : (text.length() * (GLYPH_WIDTH + 1) - 1) * pixelSize;
    }

    /**
     * @param pixelSize the size of one font pixel in screen units
     * @return the height of a line of text
     */
    public static float height(float pixelSize) {
        return GLYPH_HEIGHT * pixelSize;
    }

    /**
     * Draws text in the batch's current colour. Lower-case letters are drawn as capitals, and characters the font
     * lacks are left blank.
     * @param batch the batch, already begun
     * @param white a white pixel texture
     * @param text the text
     * @param x the left edge
     * @param y the bottom edge
     * @param pixelSize the size of one font pixel in screen units
     */
    public void draw(Batch batch, Texture white, String text, float x, float y, float pixelSize) {
        for (int i = 0; i < text.length(); i++) {
            String glyph = glyphFor(text.charAt(i));
            if (glyph == null) continue;
            float left = x + i * (GLYPH_WIDTH + 1) * pixelSize;
            for (int row = 0; row < GLYPH_HEIGHT; row++) {
                for (int col = 0; col < GLYPH_WIDTH; col++) {
                    if (glyph.charAt(row * GLYPH_WIDTH + col) != '1') continue;
                    batch.draw(white, left + col * pixelSize, y + (GLYPH_HEIGHT - 1 - row) * pixelSize, pixelSize,
                        pixelSize);
                }
            }
        }
    }

    /**
     * Draws text centred on a horizontal position.
     * @param batch the batch, already begun
     * @param white a white pixel texture
     * @param text the text
     * @param centreX the horizontal centre
     * @param y the bottom edge
     * @param pixelSize the size of one font pixel in screen units
     */
    public void drawCentred(Batch batch, Texture white, String text, float centreX, float y, float pixelSize) {
        draw(batch, white, text, centreX - width(text, pixelSize) * 0.5f, y, pixelSize);
    }
}

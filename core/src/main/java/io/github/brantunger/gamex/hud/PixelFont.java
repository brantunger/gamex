package io.github.brantunger.gamex.hud;

import com.badlogic.gdx.graphics.Texture;
import com.badlogic.gdx.graphics.g2d.SpriteBatch;

/**
 * A tiny built-in font of capitals, digits and common signs, three pixels wide and five tall. Each glyph is drawn as
 * flat squares, so the font needs no texture of its own.
 */
public final class PixelFont {

    private static final int GLYPH_WIDTH = 3;
    private static final int GLYPH_HEIGHT = 5;
    private static final String CHARACTERS = "ABCDEFGHIJKLMNOPQRSTUVWXYZ0123456789/-:.,%+!?()<>[]'*~";
    /** One string per character of {@link #CHARACTERS}: five rows of three pixels, top row first. */
    private static final String[] GLYPHS = {
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

    private PixelFont() {}

    /** @return all supported non-space characters in glyph-table order. */
    static String supportedCharacters() {
        return CHARACTERS;
    }

    /** @return the raw 15-character bitmap string for {@code c}, or null if unsupported. */
    static String glyphFor(char c) {
        int index = CHARACTERS.indexOf(Character.toUpperCase(c));
        return index < 0 ? null : GLYPHS[index];
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
    public static void draw(SpriteBatch batch, Texture white, String text, float x, float y, float pixelSize) {
        for (int i = 0; i < text.length(); i++) {
            int index = CHARACTERS.indexOf(Character.toUpperCase(text.charAt(i)));
            if (index < 0) continue;
            String glyph = GLYPHS[index];
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
    public static void drawCentred(SpriteBatch batch, Texture white, String text, float centreX, float y,
                                   float pixelSize) {
        draw(batch, white, text, centreX - width(text, pixelSize) * 0.5f, y, pixelSize);
    }
}

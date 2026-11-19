package io.github.brantunger.gamex.hud;

/**
 * Checks that player-visible text can be drawn with a loaded {@link PixelFont}. A character can be drawn if it is a
 * space or the font has a glyph for it; lower-case letters count, because the font draws them as capitals. Anything
 * else would be left blank on screen. Text read from a tuning file with {@code TuningFile.getDisplayString} or
 * {@code getDisplayStrings} is checked as it is read. Stage titles and briefings are not checked when a stage is
 * loaded; {@code LevelsTest} and {@code PixelFontTest} check the shipped ones.
 */
public final class GlyphCheck {

    private GlyphCheck() {}

    /**
     * @param font the loaded font
     * @param c a character
     * @return whether the font draws it: a space, or a character with a glyph
     */
    public static boolean isDrawable(PixelFont font, char c) {
        return c == ' ' || font.hasGlyph(c);
    }

    /**
     * @param font the loaded font
     * @param text the text
     * @return the index of the first character the font cannot draw, or -1 if it can draw them all
     */
    public static int firstUndrawable(PixelFont font, String text) {
        for (int i = 0; i < text.length(); i++) {
            if (!isDrawable(font, text.charAt(i))) return i;
        }
        return -1;
    }

    /**
     * Rejects text holding a character the font cannot draw.
     * @param font the loaded font
     * @param text the text
     * @param where where the text comes from, for the error message (for example
     *     {@code "'title' in data/hud.txt at line 3"})
     * @return the text, unchanged
     * @throws IllegalArgumentException if a character cannot be drawn; the message gives the character, its UTF-16
     *     code unit as {@code U+XXXX} and {@code where}
     */
    public static String require(PixelFont font, String text, String where) {
        int index = firstUndrawable(font, text);
        if (index >= 0) {
            char c = text.charAt(index);
            throw new IllegalArgumentException("Unsupported PixelFont character '" + c + "' ("
                + String.format("U+%04X", (int) c) + ") in " + where);
        }
        return text;
    }
}

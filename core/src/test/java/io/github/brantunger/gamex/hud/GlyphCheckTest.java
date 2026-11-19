package io.github.brantunger.gamex.hud;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import io.github.brantunger.gamex.TestTuning;
import org.junit.jupiter.api.Test;

/** Verifies that {@link GlyphCheck} accepts exactly the text {@link PixelFont} draws. */
class GlyphCheckTest {

    private static final PixelFont FONT = TestTuning.FONT;

    @Test
    void everyFontCharacterItsLowerCaseAndTheSpaceAreDrawable() {
        String chars = FONT.supportedCharacters();
        for (int i = 0; i < chars.length(); i++) {
            char c = chars.charAt(i);
            assertTrue(GlyphCheck.isDrawable(FONT, c), "font character " + c);
            assertTrue(GlyphCheck.isDrawable(FONT, Character.toLowerCase(c)), "lower case of " + c);
        }
        assertTrue(GlyphCheck.isDrawable(FONT, ' '));
        assertEquals(-1, GlyphCheck.firstUndrawable(FONT, chars + " " + chars.toLowerCase()));
        assertEquals(-1, GlyphCheck.firstUndrawable(FONT, ""));
    }

    @Test
    void charactersWithoutAGlyphAreNotDrawable() {
        for (char c : new char[] {'_', '#', '"', '=', ';', '&', '@', '\t', '\u00E9', '\u2014', '\u00A0'}) {
            assertFalse(GlyphCheck.isDrawable(FONT, c), "U+" + Integer.toHexString(c));
        }
        assertEquals(4, GlyphCheck.firstUndrawable(FONT, "HOLD_FIRE_"));
        assertEquals(0, GlyphCheck.firstUndrawable(FONT, "\tTAB"));
    }

    @Test
    void requireReturnsDrawableTextAndRejectsTheFirstCharacterWithoutAGlyph() {
        String text = "01: Outer Breach <42.5%>";
        assertSame(text, GlyphCheck.require(FONT, text, "a test"));

        IllegalArgumentException e = assertThrows(IllegalArgumentException.class,
            () -> GlyphCheck.require(FONT, "CAF\u00C9 #1", "'title' in data/hud.txt at line 3"));
        assertEquals("Unsupported PixelFont character '\u00C9' (U+00C9) in 'title' in data/hud.txt at line 3",
            e.getMessage());
    }
}

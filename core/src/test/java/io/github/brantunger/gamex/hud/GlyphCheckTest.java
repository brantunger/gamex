package io.github.brantunger.gamex.hud;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.Test;

/** Verifies that {@link GlyphCheck} accepts exactly the text {@link PixelFont} draws. */
class GlyphCheckTest {

    @Test
    void everyFontCharacterItsLowerCaseAndTheSpaceAreDrawable() {
        String chars = PixelFont.supportedCharacters();
        for (int i = 0; i < chars.length(); i++) {
            char c = chars.charAt(i);
            assertTrue(GlyphCheck.isDrawable(c), "font character " + c);
            assertTrue(GlyphCheck.isDrawable(Character.toLowerCase(c)), "lower case of " + c);
        }
        assertTrue(GlyphCheck.isDrawable(' '));
        assertEquals(-1, GlyphCheck.firstUndrawable(chars + " " + chars.toLowerCase()));
        assertEquals(-1, GlyphCheck.firstUndrawable(""));
    }

    @Test
    void charactersWithoutAGlyphAreNotDrawable() {
        for (char c : new char[] {'_', '#', '"', '=', ';', '&', '@', '\t', '\u00E9', '\u2014', '\u00A0'}) {
            assertFalse(GlyphCheck.isDrawable(c), "U+" + Integer.toHexString(c));
        }
        assertEquals(4, GlyphCheck.firstUndrawable("HOLD_FIRE_"));
        assertEquals(0, GlyphCheck.firstUndrawable("\tTAB"));
    }

    @Test
    void requireReturnsDrawableTextAndRejectsTheFirstCharacterWithoutAGlyph() {
        String text = "01: Outer Breach <42.5%>";
        assertSame(text, GlyphCheck.require(text, "a test"));

        IllegalArgumentException e = assertThrows(IllegalArgumentException.class,
            () -> GlyphCheck.require("CAF\u00C9 #1", "'title' in data/hud.txt at line 3"));
        assertEquals("Unsupported PixelFont character '\u00C9' (U+00C9) in 'title' in data/hud.txt at line 3",
            e.getMessage());
    }
}

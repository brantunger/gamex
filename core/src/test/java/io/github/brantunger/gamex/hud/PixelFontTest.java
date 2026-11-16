package io.github.brantunger.gamex.hud;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.Test;

class PixelFontTest {

    @Test
    void everySupportedCharacterHasAValidThreeByFiveGlyph() {
        String chars = PixelFont.supportedCharacters();
        assertTrue(chars.length() >= 50);
        for (int i = 0; i < chars.length(); i++) {
            char c = chars.charAt(i);
            String glyph = PixelFont.glyphFor(c);
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
            assertNotNull(PixelFont.glyphFor(c), "unsupported HUD character: " + c);
        }
        assertEquals((sample.length() * 4 - 1) * 2f, PixelFont.width(sample, 2f), 1e-5f);
    }
}

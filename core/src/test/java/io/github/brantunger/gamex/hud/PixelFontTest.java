package io.github.brantunger.gamex.hud;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import io.github.brantunger.gamex.player.Difficulty;
import io.github.brantunger.gamex.player.PlayerLoadout;
import io.github.brantunger.gamex.settings.GameSettings;
import io.github.brantunger.gamex.settings.MapKeyValueStore;
import io.github.brantunger.gamex.settings.SaveData;
import io.github.brantunger.gamex.world.Levels;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;

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

    @Test
    void everyCharacterOfStageTextAndMenuOptionsHasAGlyph() {
        List<String> texts = new ArrayList<>();
        List<Levels.Stage> stages = new ArrayList<>(Levels.campaign());
        stages.add(Levels.trainingRange());
        for (Levels.Stage stage : stages) {
            texts.add(stage.title);
            texts.addAll(stage.briefing);
        }
        for (Difficulty difficulty : Difficulty.values()) texts.add(difficulty.label);
        SaveData save = new SaveData(new MapKeyValueStore());
        save.saveCampaignRun(0, PlayerLoadout.fresh());
        for (MenuState.Screen screen : MenuState.Screen.values()) {
            texts.addAll(new MenuState(new GameSettings(), save, screen).getOptions());
        }
        for (String text : texts) {
            for (int i = 0; i < text.length(); i++) {
                char c = text.charAt(i);
                if (c == ' ') continue;
                assertNotNull(PixelFont.glyphFor(c), "no glyph for '" + c + "' in: " + text);
            }
        }
    }

    @Test
    void quitPromptAndTildeHaveGlyphs() {
        for (int i = 0; i < MenuState.QUIT_PROMPT.length(); i++) {
            char c = MenuState.QUIT_PROMPT.charAt(i);
            if (c == ' ') continue;
            assertNotNull(PixelFont.glyphFor(c), "no glyph for '" + c + "' in the quit prompt");
        }
        assertEquals("PRESS Q AGAIN TO QUIT", MenuState.QUIT_PROMPT);
        assertEquals("000011110000000", PixelFont.glyphFor('~'), "a tilde two rows high, in the upper half");
    }
}

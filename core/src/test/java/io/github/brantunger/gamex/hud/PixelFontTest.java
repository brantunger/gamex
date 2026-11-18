package io.github.brantunger.gamex.hud;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import io.github.brantunger.gamex.GameSimulation;
import io.github.brantunger.gamex.player.Difficulty;
import io.github.brantunger.gamex.player.PlayerLoadout;
import io.github.brantunger.gamex.settings.GameSettings;
import io.github.brantunger.gamex.settings.MapKeyValueStore;
import io.github.brantunger.gamex.settings.Medal;
import io.github.brantunger.gamex.settings.RunStats;
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
    void everyCharacterOfObjectiveMedalAndRecordTextHasAGlyph() {
        List<String> texts = new ArrayList<>();
        texts.add(HudRenderer.OBJECTIVE_PREFIX);
        texts.add(GameSimulation.OBJECTIVE_AMBER_KEYCARD);
        texts.add(GameSimulation.OBJECTIVE_CYAN_KEYCARD);
        texts.add(GameSimulation.OBJECTIVE_CONSOLE);
        texts.add(GameSimulation.OBJECTIVE_WARDEN);
        texts.add(GameSimulation.OBJECTIVE_EXIT);
        texts.add(GameSimulation.OBJECTIVE_ALL_TARGETS);
        texts.add(GameSimulation.OBJECTIVE_ALL_HOSTILES);
        texts.add(GameSimulation.OBJECTIVE_ALL_TARGETS_AND_HOSTILES);
        for (Medal medal : Medal.values()) texts.add(medal.label);

        // A save with a time and medals on every difficulty, so Select Stage shows every suffix it can.
        SaveData save = new SaveData(new MapKeyValueStore());
        List<Levels.Stage> campaign = Levels.campaign();
        save.unlockStage(campaign.size() - 1);
        for (Difficulty difficulty : Difficulty.values()) {
            RunStats perfect = new RunStats(41.5f + difficulty.ordinal(), 8, 8, 0f, 9, 9, 10, 10);
            SaveData.CompletionSummary summary = save.recordCompletion(campaign.get(difficulty.ordinal()).id,
                difficulty, 50f, perfect, true);
            texts.add(HudRenderer.tallyTimeLine(summary, 50f));
            texts.add(HudRenderer.tallyBestLine(summary, difficulty.label));
            texts.add(HudRenderer.tallyClearedLine(summary, true));
            texts.add(HudRenderer.tallyClearedLine(summary, false));
            texts.add(HudRenderer.tallyStatsLine(summary));
            texts.add(HudRenderer.tallyMedalLine(summary));
            SaveData.CompletionSummary repeat = save.recordCompletion(campaign.get(difficulty.ordinal()).id,
                difficulty, 50f, new RunStats(90f, 0, 8, 40f, 0, 9, 0, 0), true);
            texts.add(HudRenderer.tallyBestLine(repeat, difficulty.label));
            texts.add(HudRenderer.tallyStatsLine(repeat));
            texts.add(HudRenderer.tallyMedalLine(repeat));
        }
        texts.add(HudRenderer.tallyMedalLine(save.recordCompletion("range", Difficulty.NORMAL, 45f,
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

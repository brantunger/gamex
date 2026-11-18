package io.github.brantunger.gamex.world;

import static org.junit.jupiter.api.Assertions.assertTrue;

import io.github.brantunger.gamex.hud.PixelFont;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;

class LevelsTest {

    /** The briefing screen's terminal log box: its width, its text size and the lines it has room for. */
    private static final float BRIEFING_BOX_WIDTH = 580f;
    private static final float BRIEFING_PIXEL_SIZE = 2.4f;
    private static final int BRIEFING_BOX_LINES = 3;

    @Test
    void everyBriefingFitsTheBriefingScreensLogBox() {
        List<Levels.Stage> stages = new ArrayList<>();
        stages.add(Levels.trainingRange());
        stages.addAll(Levels.campaign());
        List<String> problems = new ArrayList<>();
        for (Levels.Stage stage : stages) {
            if (stage.briefing.size() > BRIEFING_BOX_LINES) {
                problems.add(stage.id + " has " + stage.briefing.size() + " briefing lines");
            }
            for (String line : stage.briefing) {
                // The screen draws each line behind a "> " prompt.
                float width = PixelFont.width("> " + line, BRIEFING_PIXEL_SIZE);
                if (width > BRIEFING_BOX_WIDTH) problems.add(stage.id + " line is " + width + " wide: " + line);
            }
        }
        assertTrue(problems.isEmpty(), problems.size() + " problems: " + String.join("; ", problems));
    }
}

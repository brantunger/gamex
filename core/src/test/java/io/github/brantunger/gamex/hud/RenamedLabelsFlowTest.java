package io.github.brantunger.gamex.hud;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import io.github.brantunger.gamex.TestTuning;
import io.github.brantunger.gamex.player.PlayerLoadout;
import io.github.brantunger.gamex.settings.MapKeyValueStore;
import io.github.brantunger.gamex.settings.SaveData;
import org.junit.jupiter.api.Test;

import java.util.List;

/**
 * Runs every menu flow of {@link MenuStateFlowTest} again with a {@code strings.txt} in which every line is
 * reworded: what an option does must not depend on what it is called.
 */
class RenamedLabelsFlowTest extends MenuStateFlowTest {

    private static final UiStrings REWORDED = TestTuning.rewordedStrings();

    @Override
    UiStrings strings() {
        return REWORDED;
    }

    @Test
    void everyOptionOfEveryScreenIsRewordedAndKeepsItsId() {
        SaveData save = new SaveData(new MapKeyValueStore());
        save.saveCampaignRun(0, PlayerLoadout.fresh());
        int compared = 0;
        for (MenuState.Screen screen : MenuState.Screen.values()) {
            List<MenuState.Option> shipped = new MenuState(TestTuning.settings(), save, screen, TestTuning.DIFFICULTY,
                TestTuning.WEAPONS, TestTuning.STRINGS).getMenuOptions();
            List<MenuState.Option> reworded = new MenuState(TestTuning.settings(), save, screen,
                TestTuning.DIFFICULTY, TestTuning.WEAPONS, REWORDED).getMenuOptions();
            assertEquals(shipped.size(), reworded.size(), screen.name());
            for (int i = 0; i < shipped.size(); i++) {
                MenuState.Option before = shipped.get(i);
                MenuState.Option after = reworded.get(i);
                assertEquals(before.id, after.id, screen + " row " + i);
                assertEquals(before.payload, after.payload, screen + " row " + i);
                // No word of the tests' own text is left for a check of the label to hold on to. A stage row begins
                // with the stage title, which comes from the stage file.
                String firstWord = before.label.split("[ :]")[0];
                assertFalse(before.label.equals(after.label), screen + " row " + i + " still reads " + after.label);
                if (before.id != MenuState.OptionId.STAGE) {
                    assertFalse(after.label.startsWith(firstWord),
                        screen + " row " + i + " still reads " + after.label);
                }
                compared++;
            }
        }
        assertTrue(compared > 25, "options of every screen were compared: " + compared);
        assertEquals("WVKOLB", REWORDED.get(UiText.MENU_DEPLOY));
        assertEquals("IVGIB HGZTV (R)", REWORDED.get(UiText.MENU_RETRY_STAGE), "placeholders are left alone");
    }
}

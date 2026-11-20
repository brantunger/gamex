package io.github.brantunger.gamex.hud;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertFalse;

import com.badlogic.gdx.Files;
import com.badlogic.gdx.Gdx;
import io.github.brantunger.gamex.TestTuning;
import io.github.brantunger.gamex.player.DifficultyTuning;
import io.github.brantunger.gamex.settings.SettingsDefaults;
import io.github.brantunger.gamex.world.Levels;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

/**
 * Checks the text the game ships with, whatever it says, together with the shipped font, bindings, tuning, settings
 * and stage files: it loads, and it passes the measuring the game does at start-up ({@link TextFit}). The other
 * tests of the menus and the HUD run on the tests' own copy of these files ({@link TestTuning}), so this is where a
 * reworded line of {@code assets/data/strings.txt}, a key of {@code assets/data/bindings.txt} with a longer name, or
 * a stage title or briefing that no longer fits is caught before the game is started.
 */
class ShippedTextTest {

    private Files previousFiles;

    /** The files are read without libGDX, whatever a test before left. */
    @BeforeEach
    void readWithoutLibgdx() {
        previousFiles = Gdx.files;
        Gdx.files = null;
    }

    @AfterEach
    void restoreFiles() {
        Gdx.files = previousFiles;
    }

    /** What {@code MainGame.create()} does with the files once they are loaded, on the shipped files. */
    @Test
    void theShippedFilesLoadTogetherAndPassTheFitCheckOfTheStartUp() {
        UiStrings shipped = TestTuning.shippedStrings();
        for (UiText text : UiText.values()) {
            if (text.placeholders.isEmpty()) assertFalse(shipped.get(text).isEmpty(), text.key);
        }
        assertDoesNotThrow(() -> TextFit.require(shipped, DifficultyTuning.load(), SettingsDefaults.load(),
            Levels.campaign(), Levels.trainingRange()));
    }
}

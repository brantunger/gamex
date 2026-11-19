package io.github.brantunger.gamex.world;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.badlogic.gdx.Files;
import com.badlogic.gdx.Gdx;
import io.github.brantunger.gamex.data.AssetText;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Path;

/** Pins the exact text of every error {@link Levels} reports for a stage file with one mistake in it. */
class LevelsErrorMessagesTest {

    private static final String FILE = "levels/bad.map";
    private static final String GRID = "\n#####\n#.>.#\n#####\n";

    private Files previousFiles;
    private String previousAssets;

    @BeforeEach
    void saveGdxFiles() {
        previousFiles = Gdx.files;
        previousAssets = System.getProperty("gamex.assets");
    }

    @AfterEach
    void restoreGdxFiles() {
        Gdx.files = previousFiles;
        if (previousAssets == null) {
            System.clearProperty("gamex.assets");
        } else {
            System.setProperty("gamex.assets", previousAssets);
        }
    }

    private static String stage(String... headers) {
        return String.join("\n", headers) + "\n" + GRID;
    }

    private static void assertStageError(String expected, String... headers) {
        IllegalArgumentException e = assertThrows(IllegalArgumentException.class,
            () -> Levels.parseStage(FILE, stage(headers)));
        assertEquals(expected, e.getMessage());
    }

    @Test
    void aLineThatIsNotAHeaderNamesTheFileAndLine() {
        assertStageError("Expected 'key: value' stage header or blank line before map layout in " + FILE + " at line 2",
            "id: s1", "no colon here");
        assertStageError("Expected 'key: value' stage header or blank line before map layout in " + FILE + " at line 1",
            ": value");
    }

    @Test
    void anUnknownHeaderNamesTheFileKeyAndLine() {
        assertStageError("Unknown stage header 'author' in " + FILE + " at line 6",
            "id: s1", "title: S1", "par: 30", "complete_on_all_targets: false", "briefing: GO", "author: someone");
        assertStageError("Unknown stage header 'ID' in " + FILE + " at line 2", "// comment", "ID: s1");
    }

    @Test
    void aDuplicateHeaderNamesTheFileKeyAndSecondLine() {
        assertStageError("Duplicate 'id' header in " + FILE + " at line 2", "id: s1", "id: s2");
        assertStageError("Duplicate 'title' header in " + FILE + " at line 3", "title: A", "id: s1", "title: B");
        assertStageError("Duplicate 'par' header in " + FILE + " at line 2", "par: 30", "par: 40");
        assertStageError("Duplicate 'complete_on_all_targets' header in " + FILE + " at line 2",
            "complete_on_all_targets: true", "complete_on_all_targets: false");
    }

    @Test
    void aBadLoadoutHeaderNamesTheFileLineAndWeapon() {
        String[] valid = {"id: s1", "title: S1", "par: 30", "complete_on_all_targets: false", "briefing: GO"};
        String weapons = "(the weapons are pistol, scattergun, raillance)";
        assertStageError("Invalid 'loadout' in " + FILE + " at line 6: unknown weapon 'rail_lance' " + weapons
            + ", got 'scattergun, rail_lance'",
            valid[0], valid[1], valid[2], valid[3], valid[4], "loadout: scattergun, rail_lance");
        assertStageError("Invalid 'loadout' in " + FILE + " at line 2: unknown weapon 'Scattergun' " + weapons
            + ", got 'Scattergun'",
            valid[0], "loadout: Scattergun", valid[1], valid[2], valid[3], valid[4]);
        assertStageError("Invalid 'loadout' in " + FILE + " at line 6: unknown weapon '' " + weapons
            + ", got 'scattergun,'",
            valid[0], valid[1], valid[2], valid[3], valid[4], "loadout: scattergun,");
        assertStageError("Invalid 'loadout' in " + FILE + " at line 6: weapon 'scattergun' is listed twice"
            + ", got 'scattergun, raillance, scattergun'",
            valid[0], valid[1], valid[2], valid[3], valid[4], "loadout: scattergun, raillance, scattergun");
        assertStageError("Empty 'loadout' header in " + FILE + " at line 6",
            valid[0], valid[1], valid[2], valid[3], valid[4], "loadout:");
        assertStageError("Duplicate 'loadout' header in " + FILE + " at line 7",
            valid[0], valid[1], valid[2], valid[3], valid[4], "loadout: scattergun", "loadout: raillance");
        assertStageError("Unknown stage header 'start_weapons' in " + FILE + " at line 6",
            valid[0], valid[1], valid[2], valid[3], valid[4], "start_weapons: scattergun");
    }

    @Test
    void aMissingHeaderNamesTheFileAndKey() {
        assertStageError("Missing 'id' header in " + FILE,
            "title: S1", "par: 30", "complete_on_all_targets: false", "briefing: GO");
        assertStageError("Missing 'title' header in " + FILE,
            "id: s1", "par: 30", "complete_on_all_targets: false", "briefing: GO");
        assertStageError("Missing 'par' header in " + FILE,
            "id: s1", "title: S1", "complete_on_all_targets: false", "briefing: GO");
        assertStageError("Missing 'complete_on_all_targets' header in " + FILE,
            "id: s1", "title: S1", "par: 30", "briefing: GO");
        assertStageError("Missing 'briefing' header in " + FILE,
            "id: s1", "title: S1", "par: 30", "complete_on_all_targets: false");
        IllegalArgumentException noHeaders = assertThrows(IllegalArgumentException.class,
            () -> Levels.parseStage(FILE, "// nothing\n\n"));
        assertEquals("Missing 'id' header in " + FILE, noHeaders.getMessage());
    }

    @Test
    void anEmptyOrUnparsableValueNamesTheFileKeyAndLine() {
        assertStageError("Empty 'title' header in " + FILE + " at line 2",
            "id: s1", "title:", "par: 30", "complete_on_all_targets: false", "briefing: GO");
        assertStageError("Empty 'briefing' line in " + FILE + " at line 5",
            "id: s1", "title: S1", "par: 30", "complete_on_all_targets: false", "briefing:   ");
        assertStageError("Invalid stage id 'bad id' in " + FILE + " at line 1",
            "id: bad id", "title: S1", "par: 30", "complete_on_all_targets: false", "briefing: GO");
        assertStageError("Invalid stage id '' in " + FILE + " at line 1",
            "id:", "title: S1", "par: 30", "complete_on_all_targets: false", "briefing: GO");
        assertStageError("Invalid 'par' seconds '30f' in " + FILE + " at line 3",
            "id: s1", "title: S1", "par: 30f", "complete_on_all_targets: false", "briefing: GO");
        assertStageError("Invalid 'par' seconds '' in " + FILE + " at line 3",
            "id: s1", "title: S1", "par:", "complete_on_all_targets: false", "briefing: GO");
        assertStageError("'par' seconds must be positive in " + FILE + " at line 3, got 0",
            "id: s1", "title: S1", "par: 0", "complete_on_all_targets: false", "briefing: GO");
        assertStageError("Expected 'true' or 'false' for 'complete_on_all_targets' in " + FILE
                + " at line 4, got 'yes'",
            "id: s1", "title: S1", "par: 30", "complete_on_all_targets: yes", "briefing: GO");
        assertStageError("Expected 'true' or 'false' for 'complete_on_all_targets' in " + FILE + " at line 4, got ''",
            "id: s1", "title: S1", "par: 30", "complete_on_all_targets:", "briefing: GO");
    }

    @Test
    void mapLayoutAndNullTextErrorsNameTheFile() {
        String headers = "id: s1\ntitle: S1\npar: 30\ncomplete_on_all_targets: false\nbriefing: GO\n";
        IllegalArgumentException noGrid = assertThrows(IllegalArgumentException.class,
            () -> Levels.parseStage(FILE, headers + "\n"));
        assertEquals("Stage " + FILE + " contains no map layout rows", noGrid.getMessage());
        IllegalArgumentException split = assertThrows(IllegalArgumentException.class,
            () -> Levels.parseStage(FILE, headers + "\n#####\n\n#.>.#\n#####"));
        assertEquals("Blank line inside map layout in " + FILE + " before line 9", split.getMessage());
        IllegalArgumentException nullStage = assertThrows(IllegalArgumentException.class,
            () -> Levels.parseStage(FILE, null));
        assertEquals("Stage text for " + FILE + " is null", nullStage.getMessage());
        IllegalArgumentException emptyMap = assertThrows(IllegalArgumentException.class,
            () -> Levels.parseMap(FILE, "// only a comment\n"));
        assertEquals("Map file " + FILE + " contains no layout rows", emptyMap.getMessage());
    }

    /**
     * The menu always enters the Training Range with the pistol alone, so a {@code loadout} header in its file
     * would be read and then ignored. The load refuses it instead.
     */
    @Test
    void aLoadoutHeaderInTheTrainingRangeFileIsRejectedNamingTheFileAndLine(@TempDir Path temp) throws IOException {
        Gdx.files = null;
        String shipped = AssetText.read("level", Levels.TRAINING_RANGE_PATH);
        String marker = "complete_on_all_targets: true\n";
        assertTrue(shipped.replace("\r\n", "\n").startsWith("id: range\ntitle: TRAINING RANGE\npar: 45\n" + marker),
            "the shipped file no longer starts with the four headers this test counts lines by");
        assertEquals("range", Levels.trainingRange().id, "the shipped file, which has no such header, loads");

        Path file = java.nio.file.Files.createDirectories(temp.resolve("levels")).resolve("training-range.map");
        java.nio.file.Files.write(file, shipped.replace("\r\n", "\n").replace(marker, marker + "loadout: scattergun\n")
            .getBytes(StandardCharsets.UTF_8));
        System.setProperty("gamex.assets", temp.toString());
        IllegalArgumentException e = assertThrows(IllegalArgumentException.class, Levels::trainingRange);
        assertEquals("Invalid 'loadout' in levels/training-range.map at line 5: the Training Range takes no loadout "
            + "header (from the menu it is entered with the pistol alone), got 'scattergun'", e.getMessage());

        // The same file is a campaign stage like any other when it is read as one.
        assertTrue(Levels.loadStage(Levels.TRAINING_RANGE_PATH).startingWeapons
            .contains(io.github.brantunger.gamex.weapon.WeaponSlot.SCATTERGUN));
    }

    @Test
    void aBlankOrMissingAssetPathIsReportedAsALevelAsset() {
        Gdx.files = null;
        IllegalArgumentException blank = assertThrows(IllegalArgumentException.class, () -> Levels.loadMap("  "));
        assertEquals("Level asset path must not be blank", blank.getMessage());
        IllegalStateException missing = assertThrows(IllegalStateException.class,
            () -> Levels.loadStage("levels/no-such-stage.map"));
        assertTrue(missing.getMessage().startsWith("The level asset levels/no-such-stage.map was not found. Set the "
            + "system property gamex.assets to the assets folder, or run from the root project or a subproject. "
            + "Tried: ["), missing.getMessage());
        assertTrue(missing.getMessage().endsWith("no-such-stage.map]"), missing.getMessage());
    }
}

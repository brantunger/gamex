package io.github.brantunger.gamex.world;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.badlogic.gdx.Files;
import com.badlogic.gdx.Gdx;
import com.badlogic.gdx.files.FileHandle;
import io.github.brantunger.gamex.TestTuning;
import io.github.brantunger.gamex.hud.PixelFont;
import io.github.brantunger.gamex.weapon.WeaponSlot;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.lang.reflect.Proxy;
import java.nio.charset.StandardCharsets;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.EnumSet;
import java.util.List;
import java.util.Set;
import java.util.TreeSet;
import java.util.stream.Stream;

/** Verifies shipped level and stage assets in {@code assets/levels/} and {@link Levels} file parsing. */
class LevelsTest {

    /** The briefing screen's terminal log box: its width, its text size and the lines it has room for. */
    private static final float BRIEFING_BOX_WIDTH = 580f;
    private static final float BRIEFING_PIXEL_SIZE = 2.4f;
    private static final int BRIEFING_BOX_LINES = 3;

    private Files previousFiles;

    @BeforeEach
    void saveGdxFiles() {
        previousFiles = Gdx.files;
    }

    @AfterEach
    void restoreGdxFiles() {
        Gdx.files = previousFiles;
    }

    private static Path resolveLevelsDir() {
        List<Path> candidates = new ArrayList<>();
        String assets = System.getProperty("gamex.assets");
        if (assets != null) candidates.add(Paths.get(assets, Levels.LEVELS_DIR));
        candidates.add(Paths.get("..", "assets", Levels.LEVELS_DIR));
        candidates.add(Paths.get("assets", Levels.LEVELS_DIR));
        for (Path candidate : candidates) {
            if (java.nio.file.Files.isDirectory(candidate)) {
                return candidate;
            }
        }
        throw new IllegalStateException("Could not find assets/" + Levels.LEVELS_DIR);
    }

    @Test
    void everyShippedLevelFileIsAccountedForAndValid() throws IOException {
        Path levelsDir = resolveLevelsDir();
        Set<String> actualFiles = new TreeSet<>();
        try (Stream<Path> stream = java.nio.file.Files.list(levelsDir)) {
            stream.filter(java.nio.file.Files::isRegularFile)
                .map(path -> path.getFileName().toString())
                .forEach(actualFiles::add);
        }

        Set<String> expectedFiles = new TreeSet<>(Arrays.asList(
            "campaign.txt",
            "first.map",
            "training-range.map",
            "01-outer-breach.map",
            "02-coolant-core.map",
            "03-security-hub.map",
            "04-lockdown-wing.map",
            "05-command-vault.map",
            "06-warden-core.map"
        ));
        assertEquals(expectedFiles, actualFiles, "assets/levels contents must match registered levels and stages");

        GameMap first = Levels.first();
        assertEquals(26, first.getWidth());
        assertEquals(20, first.getHeight());
        assertFalse(first.hasExit());

        Levels.Stage range = Levels.trainingRange();
        assertEquals("range", range.id);
        assertEquals("TRAINING RANGE", range.title);
        assertEquals(45f, range.parSeconds, 1e-5f);
        assertTrue(range.completeOnAllTargets);
        assertEquals(3, range.briefing.size());
        assertEquals(11, range.map.getTargetSpawns().size());
        assertFalse(range.map.hasExit());
        assertEquals(EnumSet.of(WeaponSlot.PISTOL), range.startingWeapons);
    }

    @Test
    void campaignStagesAreFoundByIdAndKeepTheirMetadata() {
        List<Levels.Stage> campaign = Levels.campaign();

        String[] expectedIds = {
            "01_outer_breach",
            "02_coolant_core",
            "03_security_hub",
            "04_lockdown_wing",
            "05_command_vault",
            "06_warden_core"
        };
        String[] expectedTitles = {
            "01: OUTER BREACH",
            "02: COOLANT CORE",
            "03: SECURITY HUB",
            "04: LOCKDOWN WING",
            "05: COMMAND VAULT",
            "06: WARDEN CORE"
        };
        float[] expectedPars = {50f, 65f, 80f, 85f, 95f, 110f};
        assertEquals(expectedIds.length, campaign.size());

        for (int i = 0; i < campaign.size(); i++) {
            Levels.Stage fromCampaign = campaign.get(i);
            Levels.Stage byId = Levels.stage(expectedIds[i]);
            assertEquals(expectedIds[i], fromCampaign.id);
            assertEquals(expectedTitles[i], fromCampaign.title);
            assertEquals(byId.id, fromCampaign.id);
            assertEquals(byId.title, fromCampaign.title);
            assertEquals(expectedPars[i], fromCampaign.parSeconds, 1e-5f);
            assertEquals(byId.parSeconds, fromCampaign.parSeconds, 1e-5f);
            assertFalse(fromCampaign.completeOnAllTargets);
            assertEquals(byId.completeOnAllTargets, fromCampaign.completeOnAllTargets);
            assertEquals(byId.briefing, fromCampaign.briefing);
            assertEquals(3, fromCampaign.briefing.size());
            assertEquals(byId.map.getWidth(), fromCampaign.map.getWidth());
            assertEquals(byId.map.getHeight(), fromCampaign.map.getHeight());
            assertEquals(byId.startingWeapons, fromCampaign.startingWeapons);
            assertTrue(fromCampaign.map.hasExit(), fromCampaign.id + " must have an exit pad");
        }
    }

    @Test
    void aStageIdTheCampaignDoesNotListIsReportedWithTheIdsItDoesList() {
        IllegalArgumentException unknown = assertThrows(IllegalArgumentException.class,
            () -> Levels.stage("07_no_such_stage"));
        assertEquals("Unknown stage id '07_no_such_stage': the stages listed in levels/campaign.txt are "
            + "01_outer_breach, 02_coolant_core, 03_security_hub, 04_lockdown_wing, 05_command_vault, 06_warden_core",
            unknown.getMessage());
        // The Training Range has an entry of its own; it is no campaign stage. Nor is a file name an id.
        assertThrows(IllegalArgumentException.class, () -> Levels.stage(Levels.trainingRange().id));
        assertThrows(IllegalArgumentException.class, () -> Levels.stage("01-outer-breach.map"));
        assertThrows(IllegalArgumentException.class, () -> Levels.stage(null));
    }

    @Test
    void shippedStagesListTheirStartingWeaponsInALoadoutHeader() {
        assertEquals(EnumSet.of(WeaponSlot.PISTOL), Levels.stage("01_outer_breach").startingWeapons);
        assertEquals(EnumSet.of(WeaponSlot.PISTOL, WeaponSlot.SCATTERGUN),
            Levels.stage("02_coolant_core").startingWeapons);
        assertEquals(EnumSet.of(WeaponSlot.PISTOL, WeaponSlot.SCATTERGUN),
            Levels.stage("03_security_hub").startingWeapons);
        for (String id : new String[] {"04_lockdown_wing", "05_command_vault", "06_warden_core"}) {
            assertEquals(EnumSet.allOf(WeaponSlot.class), Levels.stage(id).startingWeapons, id);
        }
    }

    private static Levels.Stage stageWithHeader(String loadoutHeader) {
        return Levels.parseStage("levels/custom.map", "id: s1\ntitle: S1\npar: 30\ncomplete_on_all_targets: false\n"
            + loadoutHeader + "briefing: GO\n\n#####\n#.>.#\n#####\n");
    }

    @Test
    void theLoadoutHeaderIsOptionalAndListsWeaponsByNameInAnyOrder() {
        assertEquals(EnumSet.of(WeaponSlot.PISTOL), stageWithHeader("").startingWeapons, "no header");
        assertEquals(EnumSet.of(WeaponSlot.PISTOL), stageWithHeader("loadout: pistol\n").startingWeapons);
        assertEquals(EnumSet.of(WeaponSlot.PISTOL, WeaponSlot.SCATTERGUN),
            stageWithHeader("loadout: scattergun\n").startingWeapons);
        assertEquals(EnumSet.of(WeaponSlot.PISTOL, WeaponSlot.RAIL_LANCE),
            stageWithHeader("loadout: raillance\n").startingWeapons, "the pistol is carried without being listed");
        assertEquals(EnumSet.allOf(WeaponSlot.class),
            stageWithHeader("loadout: scattergun, raillance\n").startingWeapons);
        assertEquals(EnumSet.allOf(WeaponSlot.class),
            stageWithHeader("loadout:raillance ,pistol,  scattergun\n").startingWeapons);
        assertThrows(UnsupportedOperationException.class,
            () -> stageWithHeader("loadout: scattergun\n").startingWeapons.add(WeaponSlot.RAIL_LANCE));
    }

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

    @Test
    void commandVaultBriefingLeadsTowardWardenCoreWithoutPrematureFinalLabels() {
        List<Levels.Stage> campaign = Levels.campaign();
        assertEquals("05_command_vault", campaign.get(4).id);
        assertEquals("06_warden_core", campaign.get(5).id);

        for (int i = 0; i < campaign.size() - 1; i++) {
            Levels.Stage stage = campaign.get(i);
            for (String line : stage.briefing) {
                assertFalse(line.contains("FINAL OBJECTIVE"), stage.id + " prematurely claims FINAL OBJECTIVE: " + line);
                assertFalse(line.contains("FINAL EXTRACTION"),
                    stage.id + " prematurely claims FINAL EXTRACTION: " + line);
            }
        }

        Levels.Stage vault = Levels.stage("05_command_vault");
        assertEquals(3, vault.briefing.size());
        assertTrue(vault.briefing.get(0).contains("COMMAND VAULT"), vault.briefing.get(0));
        assertTrue(vault.briefing.get(2).contains("WARDEN CORE"), vault.briefing.get(2));

        String allowedChars = " " + TestTuning.FONT.supportedCharacters();
        for (String line : vault.briefing) {
            for (int i = 0; i < line.length(); i++) {
                char c = line.charAt(i);
                assertTrue(allowedChars.indexOf(c) >= 0, "unsupported PixelFont character '" + c + "' in: " + line);
            }
        }
    }

    @Test
    void parsesBomCommentsCrlfAndColonsInStageHeaders() {
        String stageText = "\uFEFF"
            + "// Stage header comment\r\n"
            + "id: custom_01\r\n"
            + "title: 07: DEEP: VAULT\r\n"
            + "par: 72.5\r\n"
            + "complete_on_all_targets: true\r\n"
            + "briefing: LINE ONE: READY.\r\n"
            + "// Mid-header comment\r\n"
            + "briefing: LINE TWO.\r\n"
            + "\r\n"
            + "// Map grid comment\r\n"
            + "#####\r\n"
            + "#>.o#\r\n"
            + "#####\r\n";

        Levels.Stage stage = Levels.parseStage("levels/custom.map", stageText);
        assertEquals("custom_01", stage.id);
        assertEquals("07: DEEP: VAULT", stage.title);
        assertEquals(72.5f, stage.parSeconds, 1e-5f);
        assertTrue(stage.completeOnAllTargets);
        assertEquals(Arrays.asList("LINE ONE: READY.", "LINE TWO."), stage.briefing);
        assertEquals(5, stage.map.getWidth());
        assertEquals(3, stage.map.getHeight());
        assertEquals(1, stage.map.getTargetSpawns().size());

        GameMap map = Levels.parseMap("levels/raw.map", "\uFEFF// comment\r#####\r#..>#\r#####\r");
        assertEquals(5, map.getWidth());
        assertEquals(3, map.getHeight());
    }

    @Test
    void rejectsMalformedMapStageAndCampaignFilesWithContextualErrors() {
        IllegalArgumentException emptyMap = assertThrows(IllegalArgumentException.class,
            () -> Levels.parseMap("levels/empty.map", "  \n// only comment\n"));
        assertTrue(emptyMap.getMessage().contains("levels/empty.map"));

        IllegalArgumentException splitGrid = assertThrows(IllegalArgumentException.class,
            () -> Levels.parseMap("levels/split.map", "#####\n\n#.>.#\n#####"));
        assertTrue(splitGrid.getMessage().contains("levels/split.map"));

        String validHeaders = "id: s1\ntitle: S1\npar: 30\ncomplete_on_all_targets: false\nbriefing: GO\n";
        assertThrows(IllegalArgumentException.class,
            () -> Levels.parseStage("levels/no-blank.map", validHeaders + "#####\n#.>.#\n#####"));
        assertThrows(IllegalArgumentException.class,
            () -> Levels.parseStage("levels/no-grid.map", validHeaders + "\n"));
        assertThrows(IllegalArgumentException.class,
            () -> Levels.parseStage("levels/bad-id.map",
                "id: bad id\ntitle: S1\npar: 30\ncomplete_on_all_targets: false\nbriefing: GO\n\n>\n"));
        assertThrows(IllegalArgumentException.class,
            () -> Levels.parseStage("levels/bad-par.map",
                "id: s1\ntitle: S1\npar: 30f\ncomplete_on_all_targets: false\nbriefing: GO\n\n>\n"));
        assertThrows(IllegalArgumentException.class,
            () -> Levels.parseStage("levels/zero-par.map",
                "id: s1\ntitle: S1\npar: 0\ncomplete_on_all_targets: false\nbriefing: GO\n\n>\n"));
        assertThrows(IllegalArgumentException.class,
            () -> Levels.parseStage("levels/bad-bool.map",
                "id: s1\ntitle: S1\npar: 30\ncomplete_on_all_targets: yes\nbriefing: GO\n\n>\n"));
        assertThrows(IllegalArgumentException.class,
            () -> Levels.parseStage("levels/unknown-key.map",
                validHeaders + "author: someone\n\n>\n"));
        assertThrows(IllegalArgumentException.class,
            () -> Levels.parseStage("levels/missing-briefing.map",
                "id: s1\ntitle: S1\npar: 30\ncomplete_on_all_targets: false\n\n>\n"));

        assertThrows(IllegalArgumentException.class,
            () -> Levels.parseCampaign("levels/campaign.txt", "// empty\n", Levels::loadStage));
        assertThrows(IllegalArgumentException.class,
            () -> Levels.parseCampaign("levels/campaign.txt", "../secret.map\n", Levels::loadStage));
        assertThrows(IllegalArgumentException.class,
            () -> Levels.parseCampaign("levels/campaign.txt", "a.map\nb.map\n",
                path -> new Levels.Stage("dup", "DUP", 10f, false, GameMap.parse(">"), "GO")));
    }

    @Test
    void loadsViaGdxInternalFilesWhenGdxRuntimeIsActive() {
        List<String> requested = new ArrayList<>();
        Gdx.files = (Files) Proxy.newProxyInstance(
            Files.class.getClassLoader(),
            new Class<?>[] {Files.class},
            (proxy, method, args) -> {
                if ("internal".equals(method.getName()) && args != null && args.length == 1) {
                    String path = (String) args[0];
                    requested.add(path);
                    byte[] bytes = "#####\n#>.o#\n#####\n".getBytes(StandardCharsets.UTF_8);
                    return new FileHandle(path) {
                        @Override
                        public String readString(String charset) {
                            return new String(bytes, StandardCharsets.UTF_8);
                        }
                    };
                }
                return null;
            });

        GameMap loaded = Levels.loadMap("levels/mock.map");
        assertEquals(Arrays.asList("levels/mock.map"), requested);
        assertEquals(5, loaded.getWidth());
        assertEquals(1, loaded.getTargetSpawns().size());
    }
}

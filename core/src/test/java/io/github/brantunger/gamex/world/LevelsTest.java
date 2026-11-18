package io.github.brantunger.gamex.world;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.badlogic.gdx.Files;
import com.badlogic.gdx.Gdx;
import com.badlogic.gdx.files.FileHandle;
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
import java.util.List;
import java.util.Set;
import java.util.TreeSet;
import java.util.stream.Stream;

/** Verifies shipped level and stage assets in {@code assets/levels/} and {@link Levels} file parsing. */
class LevelsTest {

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
    }

    @Test
    void campaignManifestMatchesStageAccessorsAndPreservesMetadata() {
        List<Levels.Stage> campaign = Levels.campaign();
        List<Levels.Stage> direct = Arrays.asList(
            Levels.outerBreach(),
            Levels.coolantCore(),
            Levels.securityHub(),
            Levels.lockdownWing(),
            Levels.commandVault(),
            Levels.wardenCore()
        );
        assertEquals(direct.size(), campaign.size());

        String[] expectedIds = {
            "01_outer_breach",
            "02_coolant_core",
            "03_security_hub",
            "04_lockdown_wing",
            "05_command_vault",
            "06_warden_core"
        };
        float[] expectedPars = {50f, 65f, 80f, 85f, 95f, 110f};

        for (int i = 0; i < campaign.size(); i++) {
            Levels.Stage fromCampaign = campaign.get(i);
            Levels.Stage fromMethod = direct.get(i);
            assertEquals(expectedIds[i], fromCampaign.id);
            assertEquals(fromMethod.id, fromCampaign.id);
            assertEquals(fromMethod.title, fromCampaign.title);
            assertEquals(expectedPars[i], fromCampaign.parSeconds, 1e-5f);
            assertEquals(fromMethod.parSeconds, fromCampaign.parSeconds, 1e-5f);
            assertFalse(fromCampaign.completeOnAllTargets);
            assertEquals(fromMethod.briefing, fromCampaign.briefing);
            assertEquals(3, fromCampaign.briefing.size());
            assertEquals(fromMethod.map.getWidth(), fromCampaign.map.getWidth());
            assertEquals(fromMethod.map.getHeight(), fromCampaign.map.getHeight());
            assertTrue(fromCampaign.map.hasExit(), fromCampaign.id + " must have an exit pad");
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

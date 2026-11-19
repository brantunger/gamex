package io.github.brantunger.gamex.hud;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.badlogic.gdx.Gdx;
import io.github.brantunger.gamex.player.Difficulty;
import io.github.brantunger.gamex.player.Player;
import io.github.brantunger.gamex.player.PlayerLoadout;
import io.github.brantunger.gamex.settings.GameSettings;
import io.github.brantunger.gamex.settings.MapKeyValueStore;
import io.github.brantunger.gamex.settings.SaveData;
import io.github.brantunger.gamex.weapon.WeaponSlot;
import io.github.brantunger.gamex.world.Levels;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.stream.Stream;

/**
 * Covers the loadout {@code SELECT STAGE} deploys with, through the menu and the files in {@code assets/levels/}:
 * it belongs to the stage's file, not to the stage's place in {@code campaign.txt}.
 */
class StageLoadoutFileTest {

    private static final String[] STAGE_IDS = {
        "01_outer_breach", "02_coolant_core", "03_security_hub", "04_lockdown_wing", "05_command_vault",
        "06_warden_core"
    };
    /** Whether each stage of {@link #STAGE_IDS} starts with the Scattergun, and with the Rail Lance. */
    private static final boolean[] SCATTERGUN = {false, true, true, true, true, true};
    private static final boolean[] RAIL_LANCE = {false, false, false, true, true, true};
    /** One weapon pickup's 8 shells and 5 slugs, scaled by 1.25 on Easy, 1 on Normal and 0.8 on Hard. */
    private static final Difficulty[] DIFFICULTIES = {Difficulty.EASY, Difficulty.NORMAL, Difficulty.HARD};
    private static final int[] SHELLS = {10, 8, 6};
    private static final int[] SLUGS = {6, 5, 4};

    private com.badlogic.gdx.Files previousFiles;
    private String previousAssets;

    /** The files are read without libGDX, from the folder {@code gamex.assets} names, whatever a test before left. */
    @BeforeEach
    void saveAssetsFolder() {
        previousFiles = Gdx.files;
        previousAssets = System.getProperty("gamex.assets");
        Gdx.files = null;
    }

    @AfterEach
    void restoreAssetsFolder() {
        Gdx.files = previousFiles;
        if (previousAssets == null) {
            System.clearProperty("gamex.assets");
        } else {
            System.setProperty("gamex.assets", previousAssets);
        }
    }

    private static Path shippedAssets() {
        List<Path> candidates = new ArrayList<>();
        String assets = System.getProperty("gamex.assets");
        if (assets != null) candidates.add(Paths.get(assets));
        candidates.add(Paths.get("..", "assets"));
        candidates.add(Paths.get("assets"));
        for (Path candidate : candidates) {
            if (Files.isDirectory(candidate.resolve(Levels.LEVELS_DIR))) return candidate;
        }
        throw new IllegalStateException("Could not find assets/" + Levels.LEVELS_DIR);
    }

    /** Copies the shipped level and tuning files to a folder of their own and has the game read that folder. */
    private static Path useACopyOfTheAssets(Path temp) throws IOException {
        Path shipped = shippedAssets();
        for (String dir : new String[] {Levels.LEVELS_DIR, "data"}) {
            Files.createDirectories(temp.resolve(dir));
            try (Stream<Path> files = Files.list(shipped.resolve(dir))) {
                for (Path file : (Iterable<Path>) files.filter(Files::isRegularFile)::iterator) {
                    Files.copy(file, temp.resolve(dir).resolve(file.getFileName().toString()));
                }
            }
        }
        System.setProperty("gamex.assets", temp.toString());
        return temp.resolve(Levels.LEVELS_DIR);
    }

    private static int indexOf(MenuState menu, String prefix) {
        List<String> options = menu.getOptions();
        for (int i = 0; i < options.size(); i++) {
            if (options.get(i).startsWith(prefix)) return i;
        }
        throw new AssertionError("no option " + prefix + " in " + options);
    }

    /** @return the loadout the briefing shows after picking a stage and a difficulty on {@code SELECT STAGE}. */
    private static PlayerLoadout selectStage(String stageId, Difficulty difficulty) {
        List<Levels.Stage> campaign = Levels.campaign();
        int index = -1;
        for (int i = 0; i < campaign.size(); i++) {
            if (campaign.get(i).id.equals(stageId)) index = i;
        }
        assertTrue(index >= 0, stageId + " is in the campaign");
        SaveData save = new SaveData(new MapKeyValueStore());
        save.unlockStage(campaign.size() - 1);
        MenuState menu = new MenuState(new GameSettings(), save, MenuState.Screen.TITLE);
        menu.setSelectedIndex(indexOf(menu, "SELECT STAGE"));
        menu.confirm();
        menu.setSelectedIndex(index);
        menu.confirm();
        menu.setSelectedIndex(indexOf(menu, difficulty.label));
        menu.confirm();
        assertEquals(MenuState.Screen.BRIEFING, menu.getScreen());
        assertEquals(stageId, menu.getActiveStage().id);
        return menu.getActiveLoadout();
    }

    private static void assertLoadout(PlayerLoadout loadout, Difficulty difficulty, boolean scattergun, int shells,
                                      boolean railLance, int slugs, WeaponSlot inHand, String message) {
        assertEquals(Player.MAX_HEALTH, loadout.health, 1e-4f, message + ": health");
        assertEquals(0f, loadout.armor, 1e-4f, message + ": armor");
        assertEquals(difficulty, loadout.difficulty, message + ": difficulty");
        assertEquals(scattergun, loadout.hasScattergun, message + ": scattergun");
        assertEquals(shells, loadout.shells, message + ": shells");
        assertEquals(railLance, loadout.hasRailLance, message + ": rail lance");
        assertEquals(slugs, loadout.slugs, message + ": slugs");
        assertEquals(inHand, loadout.activeWeapon, message + ": weapon in hand");
    }

    /** Checks every stage on every difficulty against the loadouts the campaign has always started with. */
    private static void assertEveryStageStartsAsShipped(String message) {
        for (int stage = 0; stage < STAGE_IDS.length; stage++) {
            for (int d = 0; d < DIFFICULTIES.length; d++) {
                assertLoadout(selectStage(STAGE_IDS[stage], DIFFICULTIES[d]), DIFFICULTIES[d],
                    SCATTERGUN[stage], SCATTERGUN[stage] ? SHELLS[d] : 0,
                    RAIL_LANCE[stage], RAIL_LANCE[stage] ? SLUGS[d] : 0,
                    SCATTERGUN[stage] ? WeaponSlot.SCATTERGUN : WeaponSlot.PISTOL,
                    message + ", " + STAGE_IDS[stage] + " on " + DIFFICULTIES[d]);
            }
        }
    }

    @Test
    void everyShippedStageStartsWithTheLoadoutItAlwaysHad() {
        List<String> ids = new ArrayList<>();
        for (Levels.Stage stage : Levels.campaign()) ids.add(stage.id);
        assertEquals(List.of(STAGE_IDS), ids);
        assertEveryStageStartsAsShipped("shipped campaign");
    }

    @Test
    void reorderingTheCampaignMovesNoLoadout(@TempDir Path temp) throws IOException {
        Path levels = useACopyOfTheAssets(temp);
        Path manifest = levels.resolve("campaign.txt");
        List<String> entries = new ArrayList<>();
        for (String line : Files.readAllLines(manifest, StandardCharsets.UTF_8)) {
            if (line.trim().endsWith(".map")) entries.add(line.trim());
        }
        assertEquals(STAGE_IDS.length, entries.size());
        Collections.reverse(entries);
        Files.write(manifest, (String.join("\n", entries) + "\n").getBytes(StandardCharsets.UTF_8));

        assertEquals("06_warden_core", Levels.campaign().get(0).id, "the copy of the campaign is the one read");
        assertEquals("01_outer_breach", Levels.campaign().get(5).id);
        assertEveryStageStartsAsShipped("reversed campaign");
    }

    @Test
    void editingAStageFilesLoadoutHeaderChangesTheLoadout(@TempDir Path temp) throws IOException {
        Path levels = useACopyOfTheAssets(temp);
        String headerEnd = "complete_on_all_targets: false\n";

        // Stage 01 has no loadout header, and so the pistol alone; give it the Rail Lance.
        Path first = levels.resolve("01-outer-breach.map");
        String firstText = new String(Files.readAllBytes(first), StandardCharsets.UTF_8);
        assertTrue(firstText.contains(headerEnd) && !firstText.contains("loadout:"), firstText);
        Files.write(first, firstText.replace(headerEnd, headerEnd + "loadout: raillance\n")
            .getBytes(StandardCharsets.UTF_8));
        assertLoadout(selectStage("01_outer_breach", Difficulty.NORMAL), Difficulty.NORMAL, false, 0, true, 5,
            WeaponSlot.RAIL_LANCE, "stage 01 with a loadout header");

        // Stage 06 without its loadout header starts as a stage with none does.
        Path last = levels.resolve("06-warden-core.map");
        List<String> kept = new ArrayList<>();
        for (String line : Files.readAllLines(last, StandardCharsets.UTF_8)) {
            if (!line.startsWith("loadout:")) kept.add(line);
        }
        Files.write(last, (String.join("\n", kept) + "\n").getBytes(StandardCharsets.UTF_8));
        assertLoadout(selectStage("06_warden_core", Difficulty.NORMAL), Difficulty.NORMAL, false, 0, false, 0,
            WeaponSlot.PISTOL, "stage 06 without a loadout header");

        // The other stages are untouched.
        assertLoadout(selectStage("04_lockdown_wing", Difficulty.HARD), Difficulty.HARD, true, 6, true, 4,
            WeaponSlot.SCATTERGUN, "stage 04");
    }
}

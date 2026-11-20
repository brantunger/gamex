package io.github.brantunger.gamex.hud;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.badlogic.gdx.Gdx;
import io.github.brantunger.gamex.TestTuning;
import io.github.brantunger.gamex.player.Difficulty;
import io.github.brantunger.gamex.player.Player;
import io.github.brantunger.gamex.player.PlayerLoadout;
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
import java.util.EnumSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Stream;

/**
 * Covers the loadout {@code SELECT STAGE} deploys with, through the menu and the files in {@code assets/levels/}:
 * it belongs to the stage's file, not to the stage's place in {@code campaign.txt}.
 */
class StageLoadoutFileTest {

    private static final Difficulty[] DIFFICULTIES = {Difficulty.EASY, Difficulty.NORMAL, Difficulty.HARD};

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
        MenuState menu = TestTuning.menu(TestTuning.settings(), save, MenuState.Screen.TITLE);
        assertTrue(menu.selectId(MenuState.OptionId.SELECT_STAGE));
        menu.confirm();
        assertTrue(menu.selectId(MenuState.OptionId.STAGE, index));
        menu.confirm();
        assertTrue(menu.selectId(MenuState.OptionId.DIFFICULTY, difficulty.ordinal()));
        menu.confirm();
        assertEquals(MenuState.Screen.BRIEFING, menu.getScreen());
        assertEquals(stageId, menu.getActiveStage().id);
        return menu.getActiveLoadout();
    }

    /**
     * Checks a loadout against the weapons a stage file lists: full health, no armor, each weapon listed with the
     * ammunition of its pickup scaled by the difficulty, and in hand the Scattergun, else the Rail Lance, else the
     * pistol. The menus of these tests run on the tests' own tuning.
     */
    private static void assertLoadout(PlayerLoadout loadout, Difficulty difficulty, Set<WeaponSlot> granted,
                                      String message) {
        float pickupScale = TestTuning.DIFFICULTY.of(difficulty).pickupScale;
        boolean scattergun = granted.contains(WeaponSlot.SCATTERGUN);
        boolean railLance = granted.contains(WeaponSlot.RAIL_LANCE);
        int shells = !scattergun ? 0 : Math.min(TestTuning.WEAPONS.scattergunMaxShells,
            Math.max(1, Math.round(TestTuning.WEAPONS.scattergunWeaponPickupShells * pickupScale)));
        int slugs = !railLance ? 0 : Math.min(TestTuning.WEAPONS.railMaxSlugs,
            Math.max(1, Math.round(TestTuning.WEAPONS.railWeaponPickupSlugs * pickupScale)));
        assertEquals(Player.MAX_HEALTH, loadout.health, 1e-4f, message + ": health");
        assertEquals(0f, loadout.armor, 1e-4f, message + ": armor");
        assertEquals(difficulty, loadout.difficulty, message + ": difficulty");
        assertEquals(scattergun, loadout.hasScattergun, message + ": scattergun");
        assertEquals(shells, loadout.shells, message + ": shells");
        assertEquals(railLance, loadout.hasRailLance, message + ": rail lance");
        assertEquals(slugs, loadout.slugs, message + ": slugs");
        assertEquals(scattergun ? WeaponSlot.SCATTERGUN : railLance ? WeaponSlot.RAIL_LANCE : WeaponSlot.PISTOL,
            loadout.activeWeapon, message + ": weapon in hand");
    }

    /** @return the weapons each stage of the campaign being read starts with, by stage id, in campaign order */
    private static Map<String, Set<WeaponSlot>> weaponsByStage() {
        Map<String, Set<WeaponSlot>> weapons = new LinkedHashMap<>();
        for (Levels.Stage stage : Levels.campaign()) weapons.put(stage.id, stage.startingWeapons);
        return weapons;
    }

    /** Checks that every stage deploys, on every difficulty, with the weapons {@code expected} gives its id. */
    private static void assertEveryStageDeploysWith(Map<String, Set<WeaponSlot>> expected, String message) {
        for (Map.Entry<String, Set<WeaponSlot>> stage : expected.entrySet()) {
            for (Difficulty difficulty : DIFFICULTIES) {
                assertLoadout(selectStage(stage.getKey(), difficulty), difficulty, stage.getValue(),
                    message + ", " + stage.getKey() + " on " + difficulty);
            }
        }
    }

    /** @return the text of a stage file with its loadout header, if it has one, replaced by {@code header} */
    private static String withLoadoutHeader(String stageText, String header) {
        String headerEnd = "complete_on_all_targets: ";
        List<String> lines = new ArrayList<>();
        boolean placed = false;
        for (String line : stageText.split("\n", -1)) {
            if (line.startsWith("loadout:")) continue;
            lines.add(line);
            if (line.startsWith(headerEnd)) {
                if (!header.isEmpty()) lines.add(header);
                placed = true;
            }
        }
        assertTrue(placed, "the stage file has a " + headerEnd + "line");
        return String.join("\n", lines);
    }

    /** Whatever weapons the shipped stage files list, the menu deploys each stage with the ones its file lists. */
    @Test
    void everyShippedStageDeploysWithTheWeaponsItsFileLists() {
        Map<String, Set<WeaponSlot>> shipped = weaponsByStage();
        assertFalse(shipped.isEmpty());
        assertEveryStageDeploysWith(shipped, "shipped campaign");
    }

    @Test
    void reorderingTheCampaignMovesNoLoadout(@TempDir Path temp) throws IOException {
        Path levels = useACopyOfTheAssets(temp);
        Map<String, Set<WeaponSlot>> before = weaponsByStage();
        List<String> ids = new ArrayList<>(before.keySet());
        Path manifest = levels.resolve("campaign.txt");
        List<String> entries = new ArrayList<>();
        for (String line : Files.readAllLines(manifest, StandardCharsets.UTF_8)) {
            if (line.trim().endsWith(".map")) entries.add(line.trim());
        }
        assertEquals(ids.size(), entries.size());
        assertTrue(ids.size() > 1, "a campaign of one stage cannot be reordered");
        Collections.reverse(entries);
        Files.write(manifest, (String.join("\n", entries) + "\n").getBytes(StandardCharsets.UTF_8));

        assertEquals(ids.get(ids.size() - 1), Levels.campaign().get(0).id, "the copy of the campaign is the one read");
        assertEquals(ids.get(0), Levels.campaign().get(ids.size() - 1).id);
        assertEveryStageDeploysWith(before, "reversed campaign");
    }

    @Test
    void editingAStageFilesLoadoutHeaderChangesTheLoadout(@TempDir Path temp) throws IOException {
        Path levels = useACopyOfTheAssets(temp);
        Map<String, Set<WeaponSlot>> before = weaponsByStage();
        List<String> ids = new ArrayList<>(before.keySet());
        List<String> files = new ArrayList<>();
        for (String line : Files.readAllLines(levels.resolve("campaign.txt"), StandardCharsets.UTF_8)) {
            if (line.trim().endsWith(".map")) files.add(line.trim());
        }
        assertTrue(files.size() >= 3, "the campaign has a first, a last and a stage between them");

        // Whatever the first stage started with, its file now lists the Rail Lance alone.
        Path first = levels.resolve(files.get(0));
        Files.write(first, withLoadoutHeader(new String(Files.readAllBytes(first), StandardCharsets.UTF_8),
            "loadout: raillance").getBytes(StandardCharsets.UTF_8));
        assertLoadout(selectStage(ids.get(0), Difficulty.NORMAL), Difficulty.NORMAL,
            EnumSet.of(WeaponSlot.PISTOL, WeaponSlot.RAIL_LANCE), "the first stage with a loadout header");
        assertEquals(WeaponSlot.RAIL_LANCE, selectStage(ids.get(0), Difficulty.NORMAL).activeWeapon);

        // The last stage without a loadout header starts with the pistol alone.
        Path last = levels.resolve(files.get(files.size() - 1));
        Files.write(last, withLoadoutHeader(new String(Files.readAllBytes(last), StandardCharsets.UTF_8), "")
            .getBytes(StandardCharsets.UTF_8));
        assertLoadout(selectStage(ids.get(ids.size() - 1), Difficulty.NORMAL), Difficulty.NORMAL,
            EnumSet.of(WeaponSlot.PISTOL), "the last stage without a loadout header");

        // The stages between them are untouched.
        for (int i = 1; i < ids.size() - 1; i++) {
            assertLoadout(selectStage(ids.get(i), Difficulty.HARD), Difficulty.HARD, before.get(ids.get(i)),
                "the untouched " + ids.get(i));
        }
    }
}

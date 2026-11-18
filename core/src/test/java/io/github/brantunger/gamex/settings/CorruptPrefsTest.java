package io.github.brantunger.gamex.settings;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.badlogic.gdx.Preferences;
import io.github.brantunger.gamex.GameSimulation;
import io.github.brantunger.gamex.hud.MenuState;
import io.github.brantunger.gamex.player.Difficulty;
import io.github.brantunger.gamex.player.Player;
import io.github.brantunger.gamex.player.PlayerLoadout;
import io.github.brantunger.gamex.weapon.WeaponSlot;
import io.github.brantunger.gamex.world.Levels;
import org.junit.jupiter.api.Test;

import java.lang.reflect.Proxy;
import java.util.HashMap;
import java.util.Map;

/** Checks that damaged settings and save files fall back to defaults instead of breaking the game. */
class CorruptPrefsTest {

    /**
     * An in-memory {@link Preferences} that keeps text and parses it on reading, as the desktop backend's
     * {@code Lwjgl3Preferences} does, so text that is not a number throws {@link NumberFormatException}.
     */
    private static Preferences textPreferences(Map<String, String> values) {
        return (Preferences) Proxy.newProxyInstance(
            Preferences.class.getClassLoader(),
            new Class<?>[] {Preferences.class},
            (proxy, method, args) -> {
                String name = method.getName();
                if (name.startsWith("put") && args != null && args.length == 2) {
                    values.put((String) args[0], String.valueOf(args[1]));
                    return proxy;
                }
                if (args != null && args.length == 2) {
                    String text = values.getOrDefault((String) args[0], String.valueOf(args[1]));
                    switch (name) {
                        case "getFloat":
                            return Float.parseFloat(text);
                        case "getInteger":
                            return Integer.parseInt(text);
                        case "getBoolean":
                            return Boolean.parseBoolean(text);
                        case "getString":
                            return text;
                        default:
                            break;
                    }
                }
                if ("flush".equals(name)) return null;
                throw new UnsupportedOperationException(name);
            });
    }

    @Test
    void nanSettingsLoadAsDefaults() {
        MapKeyValueStore store = new MapKeyValueStore();
        store.putFloat("mouseSensitivity", Float.NaN);
        store.putFloat("fov", Float.NaN);
        store.putFloat("masterVolume", Float.NaN);

        GameSettings settings = GameSettings.load(store);

        assertEquals(GameSettings.DEFAULT_SENSITIVITY, settings.getMouseSensitivity(), 1e-4f, "sensitivity");
        assertEquals(GameSettings.DEFAULT_FOV, settings.getFov(), 1e-4f, "field of view");
        assertEquals(GameSettings.DEFAULT_MASTER_VOLUME, settings.getMasterVolume(), 1e-4f, "master volume");
    }

    @Test
    void infiniteSettingsLoadAsDefaults() {
        for (float stored : new float[] {Float.POSITIVE_INFINITY, Float.NEGATIVE_INFINITY}) {
            MapKeyValueStore store = new MapKeyValueStore();
            store.putFloat("mouseSensitivity", stored);
            store.putFloat("fov", stored);
            store.putFloat("masterVolume", stored);

            GameSettings settings = GameSettings.load(store);

            assertEquals(GameSettings.DEFAULT_SENSITIVITY, settings.getMouseSensitivity(), 1e-4f, "sens " + stored);
            assertEquals(GameSettings.DEFAULT_FOV, settings.getFov(), 1e-4f, "fov " + stored);
            assertEquals(GameSettings.DEFAULT_MASTER_VOLUME, settings.getMasterVolume(), 1e-4f, "vol " + stored);
        }
    }

    @Test
    void nanSavedHealthAndArmorFallBackToDefaultsAndThePlayerCanStillDie() {
        MapKeyValueStore store = new MapKeyValueStore();
        SaveData save = new SaveData(store);
        save.saveCampaignRun(0, PlayerLoadout.fresh());
        store.putFloat("run.health", Float.NaN);
        store.putFloat("run.armor", Float.NaN);

        PlayerLoadout loadout = save.getSavedLoadout();
        assertEquals(Player.MAX_HEALTH, loadout.health, 1e-4f, "health");
        assertEquals(0f, loadout.armor, 1e-4f, "armor");

        Levels.Stage stage = Levels.campaign().get(0);
        GameSimulation simulation = new GameSimulation(stage.map, loadout, stage.completeOnAllTargets);
        assertEquals(Player.MAX_HEALTH, simulation.player.getHealth(), 1e-4f);
        simulation.player.damage(500f);
        assertTrue(simulation.player.isDead(), "500 damage kills a player loaded from a damaged save");
    }

    @Test
    void infiniteLoadoutVitalsFallBackToDefaults() {
        PlayerLoadout negative = new PlayerLoadout(Float.NEGATIVE_INFINITY, Float.NEGATIVE_INFINITY,
            Difficulty.NORMAL);
        assertEquals(Player.MAX_HEALTH, negative.health, 1e-4f);
        assertEquals(0f, negative.armor, 1e-4f);

        PlayerLoadout positive = new PlayerLoadout(Float.POSITIVE_INFINITY, Float.POSITIVE_INFINITY,
            Difficulty.NORMAL);
        assertEquals(Player.MAX_HEALTH, positive.health, 1e-4f);
        assertEquals(0f, positive.armor, 1e-4f);
    }

    @Test
    void unknownSavedDifficultyAndWeaponFallBackToNormalAndPistol() {
        MapKeyValueStore store = new MapKeyValueStore();
        SaveData save = new SaveData(store);
        save.saveCampaignRun(2, new PlayerLoadout(70f, 10f, Difficulty.HARD, true, 4, WeaponSlot.SCATTERGUN));
        store.putString("run.difficulty", "NIGHTMARE");
        store.putString("run.activeWeapon", "BFG");

        PlayerLoadout loadout = save.getSavedLoadout();
        assertEquals(Difficulty.NORMAL, loadout.difficulty);
        assertEquals(WeaponSlot.PISTOL, loadout.activeWeapon);
        assertEquals(70f, loadout.health, 1e-4f, "the rest of the run is kept");
        assertEquals(4, loadout.shells);
    }

    @Test
    void nonNumericTextInPreferencesReadsAsTheDefault() {
        Map<String, String> values = new HashMap<>();
        values.put("aFloat", "fast");
        values.put("anInt", "3.5");
        values.put("goodFloat", "0.25");
        values.put("goodInt", "4");
        GdxPreferencesStore store = new GdxPreferencesStore(textPreferences(values));

        assertEquals(1.5f, store.getFloat("aFloat", 1.5f), 1e-6f);
        assertEquals(7, store.getInteger("anInt", 7));
        assertEquals(0.25f, store.getFloat("goodFloat", 1.5f), 1e-6f, "a valid number is still read");
        assertEquals(4, store.getInteger("goodInt", 7), "a valid number is still read");
        assertEquals(1.5f, store.getFloat("missing", 1.5f), 1e-6f);
        assertEquals(7, store.getInteger("missing", 7));
    }

    @Test
    void nonNumericSettingsFileLoadsAsDefaults() {
        Map<String, String> values = new HashMap<>();
        values.put("mouseSensitivity", "fast");
        values.put("fov", "wide");
        values.put("masterVolume", "NaN");
        values.put("fullscreen", "true");

        GameSettings settings = GameSettings.load(new GdxPreferencesStore(textPreferences(values)));

        assertEquals(GameSettings.DEFAULT_SENSITIVITY, settings.getMouseSensitivity(), 1e-4f);
        assertEquals(GameSettings.DEFAULT_FOV, settings.getFov(), 1e-4f);
        assertEquals(GameSettings.DEFAULT_MASTER_VOLUME, settings.getMasterVolume(), 1e-4f);
        assertTrue(settings.isFullscreen(), "the undamaged keys are still read");
    }

    @Test
    void continueSurvivesANonNumericSaveFile() {
        Map<String, String> values = new HashMap<>();
        values.put("run.active", "true");
        values.put("run.stageIndex", "three");
        values.put("run.health", "lots");
        values.put("run.armor", "");
        values.put("run.shells", "many");
        values.put("run.slugs", "1e3");
        values.put("campaign.unlockedStage", "all");
        SaveData save = new SaveData(new GdxPreferencesStore(textPreferences(values)));
        MenuState menu = new MenuState(new GameSettings(), save, MenuState.Screen.TITLE);

        assertEquals("CONTINUE CAMPAIGN", menu.getOptions().get(0));
        assertEquals(MenuState.Command.LAUNCH_STAGE, menu.confirm());
        assertEquals("01_outer_breach", menu.getActiveStage().id, "a bad stage index continues at stage 01");
        assertEquals(Player.MAX_HEALTH, menu.getActiveLoadout().health, 1e-4f);
        assertEquals(0f, menu.getActiveLoadout().armor, 1e-4f);
        assertEquals(0, menu.getActiveLoadout().shells);
        assertEquals(0, menu.getActiveLoadout().slugs);
        assertEquals(0, save.getUnlockedStageIndex());
        assertFalse(menu.getActiveLoadout().hasScattergun);
    }
}

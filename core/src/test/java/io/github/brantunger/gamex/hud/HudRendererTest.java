package io.github.brantunger.gamex.hud;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import io.github.brantunger.gamex.GameSimulation;
import io.github.brantunger.gamex.settings.GameSettings;
import io.github.brantunger.gamex.settings.MapKeyValueStore;
import io.github.brantunger.gamex.settings.SaveData;
import io.github.brantunger.gamex.world.Levels;
import org.junit.jupiter.api.Test;

class HudRendererTest {

    private static MenuState menuOn(MenuState.Screen screen) {
        return new MenuState(new GameSettings(), new SaveData(new MapKeyValueStore()), screen);
    }

    @Test
    void settingsOpenedFromTheTitleShowsNoCombatHud() {
        GameSimulation simulation = new GameSimulation(Levels.first());
        MenuState menu = menuOn(MenuState.Screen.TITLE);
        menu.setSelectedIndex(menu.getOptions().indexOf("SETTINGS"));
        menu.confirm();
        assertEquals(MenuState.Screen.SETTINGS, menu.getScreen());

        assertFalse(HudRenderer.showsCombatHud(menu, simulation));
    }

    @Test
    void settingsOpenedFromPauseKeepsTheCombatHud() {
        GameSimulation simulation = new GameSimulation(Levels.first());
        MenuState menu = menuOn(MenuState.Screen.PLAYING);
        menu.escape();
        assertTrue(HudRenderer.showsCombatHud(menu, simulation), "Pause");
        menu.setSelectedIndex(menu.getOptions().indexOf("SETTINGS"));
        menu.confirm();
        assertEquals(MenuState.Screen.SETTINGS, menu.getScreen());

        assertTrue(HudRenderer.showsCombatHud(menu, simulation));
        assertFalse(HudRenderer.showsCombatHud(menu, null), "no simulation, no combat HUD");
    }

    @Test
    void combatHudShowsInPlayOnPauseAndOnDeathOnly() {
        GameSimulation simulation = new GameSimulation(Levels.first());
        for (MenuState.Screen screen : MenuState.Screen.values()) {
            if (screen == MenuState.Screen.SETTINGS) continue;
            boolean expected = screen == MenuState.Screen.PLAYING || screen == MenuState.Screen.PAUSED
                || screen == MenuState.Screen.DEAD;
            assertEquals(expected, HudRenderer.showsCombatHud(menuOn(screen), simulation), screen.name());
        }
    }
}

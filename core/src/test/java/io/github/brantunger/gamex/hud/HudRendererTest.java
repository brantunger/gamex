package io.github.brantunger.gamex.hud;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.badlogic.gdx.Gdx;
import com.badlogic.gdx.Graphics;
import com.badlogic.gdx.math.MathUtils;
import com.badlogic.gdx.math.Matrix4;
import io.github.brantunger.gamex.GameSimulation;
import io.github.brantunger.gamex.player.Difficulty;
import io.github.brantunger.gamex.player.Player;
import io.github.brantunger.gamex.player.PlayerInput;
import io.github.brantunger.gamex.player.PlayerLoadout;
import io.github.brantunger.gamex.settings.GameSettings;
import io.github.brantunger.gamex.settings.MapKeyValueStore;
import io.github.brantunger.gamex.settings.SaveData;
import io.github.brantunger.gamex.weapon.WeaponSlot;
import io.github.brantunger.gamex.world.GameMap;
import io.github.brantunger.gamex.world.Levels;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.lang.reflect.Proxy;
import java.util.List;

class HudRendererTest {

    private Graphics previousGraphics;

    @BeforeEach
    void saveGraphics() {
        previousGraphics = Gdx.graphics;
    }

    @AfterEach
    void restoreGraphics() {
        Gdx.graphics = previousGraphics;
    }

    private static Graphics graphicsStub(int width, int height) {
        return (Graphics) Proxy.newProxyInstance(
            Graphics.class.getClassLoader(),
            new Class<?>[] {Graphics.class},
            (proxy, method, args) -> {
                if ("getWidth".equals(method.getName())) return width;
                if ("getHeight".equals(method.getName())) return height;
                Class<?> rt = method.getReturnType();
                if (!rt.isPrimitive()) return null;
                if (rt == boolean.class) return false;
                if (rt == int.class) return 0;
                if (rt == long.class) return 0L;
                if (rt == float.class) return 0f;
                if (rt == double.class) return 0d;
                if (rt == short.class) return (short) 0;
                if (rt == byte.class) return (byte) 0;
                if (rt == char.class) return '\0';
                return null;
            });
    }

    private static void assertFiniteMatrix(Matrix4 matrix) {
        for (float v : matrix.val) {
            assertTrue(Float.isFinite(v), "projection matrix element must be finite, got " + v);
        }
    }

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

    @Test
    void constructorAndResizeGuardZeroAndNegativeDimensionsWithoutNaNProjection() {
        Gdx.graphics = graphicsStub(0, 0);
        HudRenderer hud = new HudRenderer(null, null);
        assertEquals(1280f, hud.getWidth(), 1e-4f);
        assertEquals(720f, hud.getHeight(), 1e-4f);
        assertEquals(1f, hud.getUnit(), 1e-6f);
        assertFiniteMatrix(hud.getProjectionMatrix());

        hud.resize(0, 0);
        hud.resize(0, 720);
        hud.resize(1280, 0);
        hud.resize(-640, -480);
        assertEquals(1280f, hud.getWidth(), 1e-4f);
        assertEquals(720f, hud.getHeight(), 1e-4f);
        assertEquals(1f, hud.getUnit(), 1e-6f);
        assertFiniteMatrix(hud.getProjectionMatrix());

        hud.resize(960, 1080);
        assertEquals(960f, hud.getWidth(), 1e-4f);
        assertEquals(1080f, hud.getHeight(), 1e-4f);
        assertEquals(960f / 720f, hud.getUnit(), 1e-6f);
        assertFiniteMatrix(hud.getProjectionMatrix());
        hud.dispose();
    }

    @Test
    void formatTimeFloorsTenthsBeforeSplittingMinutesAndSeconds() {
        assertEquals("00:00.0", HudRenderer.formatTime(0f));
        assertEquals("00:00.0", HudRenderer.formatTime(-0.0f));
        assertEquals("00:00.0", HudRenderer.formatTime(-3.5f));
        assertEquals("00:00.0", HudRenderer.formatTime(Float.NaN));
        assertEquals("00:00.0", HudRenderer.formatTime(Float.POSITIVE_INFINITY));
        assertEquals("00:00.0", HudRenderer.formatTime(Float.NEGATIVE_INFINITY));

        // Minute rollover boundaries must never produce ":60.0".
        assertEquals("00:59.9", HudRenderer.formatTime(59.95f));
        assertEquals("00:59.9", HudRenderer.formatTime(59.96f));
        assertEquals("00:59.9", HudRenderer.formatTime(59.999f));
        assertEquals("01:00.0", HudRenderer.formatTime(60.0f));
        assertEquals("01:59.9", HudRenderer.formatTime(119.99f));
        assertEquals("02:00.0", HudRenderer.formatTime(120.0f));

        // Sub-par clears against a 50.0s par must never round up to "00:50.0".
        assertEquals("00:49.9", HudRenderer.formatTime(49.95f));
        assertEquals("00:49.9", HudRenderer.formatTime(49.96f));
        assertEquals("00:49.9", HudRenderer.formatTime(Math.nextDown(50.0f)));
        assertEquals("00:50.0", HudRenderer.formatTime(50.0f));
        assertEquals("100:00.0", HudRenderer.formatTime(6000f));
    }

    @Test
    void hudUnitPreservesLandscapeScaleAndPreventsHorizontalClippingOnNarrowOrPortraitWindows() {
        // Landscape & square windows keep height / 720f unchanged.
        for (int[] size : new int[][] {{1280, 720}, {1920, 1080}, {2560, 1080}, {1024, 768}, {800, 600}, {720, 720}}) {
            assertEquals(size[1] / 720f, HudRenderer.hudUnit(size[0], size[1]), 1e-6f,
                size[0] + "x" + size[1]);
        }

        // Half-screen and portrait windows clamp unit by width / 720f so 678-unit Title footer and 660-unit Briefing fit.
        String titleFooter = "WASD: MOVE  SPACE: JUMP  SHIFT: DASH  E: USE  1-3/Q/SCROLL: WEAPON  LMB/RMB: FIRE/ALT";
        for (int[] size : new int[][] {{960, 1080}, {1080, 1920}, {480, 720}, {680, 720}, {160, 320}}) {
            float w = size[0];
            float h = size[1];
            float u = HudRenderer.hudUnit(w, h);
            assertEquals(w / 720f, u, 1e-6f, w + "x" + h);
            assertTrue(PixelFont.width(titleFooter, 2.0f * u) <= w, "Title footer fits within " + w + "x" + h);
            assertTrue(664f * u <= w, "Briefing panel + border fits within " + w + "x" + h);
            assertTrue(492f * u <= h, "Tallest menu panel + border fits vertically within " + w + "x" + h);
        }
    }

    @Test
    void optionAtGuardsInvalidWindowSizesAndMatchesNarrowWindowRowScale() {
        MenuState title = menuOn(MenuState.Screen.TITLE);
        assertEquals(-1, HudRenderer.optionAt(0, 720, 0, 360, title));
        assertEquals(-1, HudRenderer.optionAt(1280, 0, 640, 0, title));
        assertEquals(-1, HudRenderer.optionAt(0, 0, 0, 0, title));
        assertEquals(-1, HudRenderer.optionAt(-400, 720, 100, 360, title));
        assertEquals(-1, HudRenderer.optionAt(1280, 720, 640, 360, null));

        // On half-screen 960x1080 and portrait 480x720, optionAt must match u = hudUnit(w, h).
        for (int[] size : new int[][] {{960, 1080}, {480, 720}}) {
            int w = size[0];
            int h = size[1];
            float u = HudRenderer.hudUnit(w, h);
            float firstBottomY = h * 0.5f + 22f * u;
            float rowH = 34f * u;
            float rowStep = 42f * u;
            List<String> options = title.getOptions();
            for (int i = 0; i < options.size(); i++) {
                float rowCentreUiY = firstBottomY - i * rowStep + rowH * 0.5f;
                int screenY = Math.round(h - rowCentreUiY);
                assertEquals(i, HudRenderer.optionAt(w, h, w / 2, screenY, title),
                    w + "x" + h + " row " + i);
            }
            int outsideRightX = Math.round(w * 0.5f + 260f * u + 4f);
            assertEquals(-1, HudRenderer.optionAt(w, h, outsideRightX, h / 2, title));
        }
    }

    @Test
    void interactionPromptClearsRailLanceZoomBracketsChargeAndBottomHudAcrossZoomLevelsAndWindowSizes() {
        int[][] sizes = {
            {1280, 720}, {1920, 1080}, {2560, 1080}, {800, 600}, {720, 720}, {960, 1080}, {480, 720}
        };
        float[] activeZooms = {
            Math.nextUp(HudRenderer.RAIL_ZOOM_OPTIC_THRESHOLD), 0.0501f, 0.0625f, 0.125f, 0.25f,
            0.5f, 0.75f, 0.95f, 1.0f, 1.5f
        };
        float[] inactiveZooms = {Float.NaN, -0.5f, 0f, 0.025f, HudRenderer.RAIL_ZOOM_OPTIC_THRESHOLD};

        for (int[] size : sizes) {
            float w = size[0];
            float h = size[1];
            float u = HudRenderer.hudUnit(w, h);
            float centreY = h * 0.5f;
            float promptH = HudRenderer.interactionPromptHeight(u);
            // Highest bottom-left HUD element: 3 weapon rows + keycard badges at y = 182*u..194*u.
            float highestBottomHudTop = (114f + 3 * 22f + 2f) * u + PixelFont.height(2.4f * u);
            String label = size[0] + "x" + size[1];

            for (float zoom : activeZooms) {
                float promptBottom = HudRenderer.interactionPromptBottomY(h, u, WeaponSlot.RAIL_LANCE, zoom);
                float promptTop = promptBottom + promptH;
                float bracketBottom = centreY - HudRenderer.railZoomBracketSpan(u, zoom);
                float chargeBottom = HudRenderer.chargeBottomY(h, u, WeaponSlot.RAIL_LANCE, zoom);
                float lowEnergyTop = chargeBottom + PixelFont.height(3f * u);

                assertTrue(promptTop <= bracketBottom - 4f * u,
                    label + " zoom=" + zoom + ": promptTop=" + promptTop + " must sit below bracketBottom="
                        + bracketBottom);
                assertTrue(lowEnergyTop <= promptBottom - 4f * u,
                    label + " zoom=" + zoom + ": lowEnergyTop=" + lowEnergyTop + " must sit below promptBottom="
                        + promptBottom);
                assertTrue(promptBottom > highestBottomHudTop && chargeBottom > highestBottomHudTop,
                    label + " zoom=" + zoom + ": centred prompts/warnings must clear bottom HUD vitals/keycards");
            }

            // Unzoomed Rail Lance and non-Rail weapons keep the standard prompt and charge offsets cleanly separated.
            for (float zoom : inactiveZooms) {
                float promptBottom = HudRenderer.interactionPromptBottomY(h, u, WeaponSlot.RAIL_LANCE, zoom);
                float promptTop = promptBottom + promptH;
                float clampedZoom = Float.isFinite(zoom) ? MathUtils.clamp(zoom, 0f, 1f) : 0f;
                float unzoomedRailLowest = centreY
                    - (MathUtils.lerp(5f, 3f, clampedZoom) + MathUtils.lerp(11f, 20f, clampedZoom) * 0.7f) * u;
                assertEquals(centreY - 44f * u, promptBottom, 1e-4f, label + " inactive zoom=" + zoom);
                assertTrue(promptTop < unzoomedRailLowest && promptTop < centreY - 18.5f * u,
                    label + " unzoomed prompt stays below all standard crosshairs and hit-markers");
            }

            for (WeaponSlot other : new WeaponSlot[] {WeaponSlot.PISTOL, WeaponSlot.SCATTERGUN, null}) {
                float promptBottom = HudRenderer.interactionPromptBottomY(h, u, other, 1.0f);
                float chargeBottom = HudRenderer.chargeBottomY(h, u, other, 1.0f);
                float chargeNotchTop = chargeBottom + 14f * u;
                float lowEnergyTop = chargeBottom + PixelFont.height(3f * u);
                float chargeLabelBottom = chargeBottom - 26f * u;
                assertEquals(centreY - 44f * u, promptBottom, 1e-4f, label + " weapon=" + other);
                assertEquals(centreY - 70f * u, chargeBottom, 1e-4f, label + " weapon=" + other);
                assertTrue(chargeNotchTop < promptBottom && lowEnergyTop < promptBottom,
                    label + " charge bar and LOW ENERGY stay below standard prompt");
                assertTrue(chargeLabelBottom > highestBottomHudTop,
                    label + " CHARGE label stays above bottom HUD vitals/keycards");
            }
        }
    }

    @Test
    void liveRailLanceZoomNearBulkheadKeepsInteractionPromptBelowRangefinderBrackets() {
        GameMap doorMap = GameMap.parse(
            "#######",
            "#>.=.X#",
            "#######");
        PlayerLoadout railLoadout = new PlayerLoadout(
            Player.MAX_HEALTH, 0f, Difficulty.NORMAL, true, 8, true, 5, WeaponSlot.RAIL_LANCE);
        GameSimulation sim = new GameSimulation(doorMap, railLoadout, false);
        sim.player.position.set(2.4f, 1.5f);
        sim.player.look(90f - sim.player.getYaw(), 0f);
        assertEquals("[E] OPEN BULKHEAD", sim.getInteractionPrompt());
        assertEquals(WeaponSlot.RAIL_LANCE, sim.getActiveWeapon());

        PlayerInput holdZoom = new PlayerInput();
        holdZoom.charge = true;
        boolean sawZoomTransition = false;
        boolean sawFullZoom = false;
        for (int step = 0; step < 30; step++) {
            sim.update(1f / 120f, holdZoom);
            assertEquals("[E] OPEN BULKHEAD", sim.getInteractionPrompt());
            float zoom = sim.getZoom();
            if (zoom > HudRenderer.RAIL_ZOOM_OPTIC_THRESHOLD) {
                if (zoom < 0.99f) sawZoomTransition = true;
                else sawFullZoom = true;
                float promptTop = HudRenderer.interactionPromptBottomY(720f, 1f, sim.getActiveWeapon(), zoom)
                    + HudRenderer.interactionPromptHeight(1f);
                float bracketBottom = 360f - HudRenderer.railZoomBracketSpan(1f, zoom);
                assertTrue(promptTop < bracketBottom,
                    "step " + step + " zoom=" + zoom + ": promptTop=" + promptTop + " vs bracketBottom="
                        + bracketBottom);
            }
        }
        assertTrue(sawZoomTransition, "sampled intermediate zoom transition > 0.05");
        assertTrue(sawFullZoom, "reached full Rail Lance optic zoom");
    }
}

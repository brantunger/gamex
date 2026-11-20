package io.github.brantunger.gamex.hud;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.badlogic.gdx.Gdx;
import com.badlogic.gdx.Graphics;
import com.badlogic.gdx.math.MathUtils;
import com.badlogic.gdx.math.Matrix4;
import io.github.brantunger.gamex.GameSimulation;
import io.github.brantunger.gamex.TestTuning;
import io.github.brantunger.gamex.input.Action;
import io.github.brantunger.gamex.input.BoundInput;
import io.github.brantunger.gamex.input.KeyBindings;
import io.github.brantunger.gamex.input.KeyNames;
import io.github.brantunger.gamex.player.Difficulty;
import io.github.brantunger.gamex.player.Player;
import io.github.brantunger.gamex.player.PlayerInput;
import io.github.brantunger.gamex.player.PlayerLoadout;
import io.github.brantunger.gamex.settings.MapKeyValueStore;
import io.github.brantunger.gamex.settings.SaveData;
import io.github.brantunger.gamex.settings.WindowDefaults;
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
        return TestTuning.menu(TestTuning.settings(), new SaveData(new MapKeyValueStore()), screen);
    }

    @Test
    void settingsOpenedFromTheTitleShowsNoCombatHud() {
        GameSimulation simulation = TestTuning.simulation(Levels.first());
        MenuState menu = menuOn(MenuState.Screen.TITLE);
        assertTrue(menu.selectId(MenuState.OptionId.SETTINGS));
        menu.confirm();
        assertEquals(MenuState.Screen.SETTINGS, menu.getScreen());

        assertFalse(HudRenderer.showsCombatHud(menu, simulation));
    }

    @Test
    void settingsOpenedFromPauseKeepsTheCombatHud() {
        GameSimulation simulation = TestTuning.simulation(Levels.first());
        MenuState menu = menuOn(MenuState.Screen.PLAYING);
        menu.escape();
        assertTrue(HudRenderer.showsCombatHud(menu, simulation), "Pause");
        assertTrue(menu.selectId(MenuState.OptionId.SETTINGS));
        menu.confirm();
        assertEquals(MenuState.Screen.SETTINGS, menu.getScreen());

        assertTrue(HudRenderer.showsCombatHud(menu, simulation));
        assertFalse(HudRenderer.showsCombatHud(menu, null), "no simulation, no combat HUD");
    }

    @Test
    void combatHudShowsInPlayOnPauseAndOnDeathOnly() {
        GameSimulation simulation = TestTuning.simulation(Levels.first());
        for (MenuState.Screen screen : MenuState.Screen.values()) {
            if (screen == MenuState.Screen.SETTINGS) continue;
            boolean expected = screen == MenuState.Screen.PLAYING || screen == MenuState.Screen.PAUSED
                || screen == MenuState.Screen.DEAD;
            assertEquals(expected, HudRenderer.showsCombatHud(menuOn(screen), simulation), screen.name());
        }
    }

    @Test
    void theOverlayTakesTheSizeOfTheWindowDefaultsUntilTheWindowReportsItsOwn() {
        WindowDefaults other = WindowDefaults.parse("window.txt", "window.width: 1024\nwindow.height: 768\n");
        for (Graphics graphics : new Graphics[] {null, graphicsStub(0, 0), graphicsStub(-1, 600)}) {
            Gdx.graphics = graphics;
            HudRenderer hud = new HudRenderer(null, null, TestTuning.STRINGS, other, null);
            assertEquals(1024f, hud.getWidth(), 0f);
            assertEquals(768f, hud.getHeight(), 0f);
            assertEquals(768f / HudRenderer.HUD_REFERENCE_SPAN, hud.getUnit(), 1e-6f);
        }

        // A window that reports a size is believed over the file.
        Gdx.graphics = graphicsStub(800, 600);
        HudRenderer sized = new HudRenderer(null, null, TestTuning.STRINGS, other, null);
        assertEquals(800f, sized.getWidth(), 0f);
        assertEquals(600f, sized.getHeight(), 0f);

        assertThrows(IllegalArgumentException.class,
            () -> new HudRenderer(null, null, TestTuning.STRINGS, null, null));
    }

    @Test
    void constructorAndResizeGuardZeroAndNegativeDimensionsWithoutNaNProjection() {
        Gdx.graphics = graphicsStub(0, 0);
        HudRenderer hud = new HudRenderer(null, null, TestTuning.STRINGS, TestTuning.WINDOW, null);
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
    void stopwatchTimeFloorsTenthsBeforeSplittingMinutesAndSeconds() {
        assertEquals("00:00.0", TextFormat.stopwatch(0f));
        assertEquals("00:00.0", TextFormat.stopwatch(-0.0f));
        assertEquals("00:00.0", TextFormat.stopwatch(-3.5f));
        assertEquals("00:00.0", TextFormat.stopwatch(Float.NaN));
        assertEquals("00:00.0", TextFormat.stopwatch(Float.POSITIVE_INFINITY));
        assertEquals("00:00.0", TextFormat.stopwatch(Float.NEGATIVE_INFINITY));

        // Minute rollover boundaries must never produce ":60.0".
        assertEquals("00:59.9", TextFormat.stopwatch(59.95f));
        assertEquals("00:59.9", TextFormat.stopwatch(59.96f));
        assertEquals("00:59.9", TextFormat.stopwatch(59.999f));
        assertEquals("01:00.0", TextFormat.stopwatch(60.0f));
        assertEquals("01:59.9", TextFormat.stopwatch(119.99f));
        assertEquals("02:00.0", TextFormat.stopwatch(120.0f));

        // Sub-par clears against a 50.0s par must never round up to "00:50.0".
        assertEquals("00:49.9", TextFormat.stopwatch(49.95f));
        assertEquals("00:49.9", TextFormat.stopwatch(49.96f));
        assertEquals("00:49.9", TextFormat.stopwatch(Math.nextDown(50.0f)));
        assertEquals("00:50.0", TextFormat.stopwatch(50.0f));
        assertEquals("100:00.0", TextFormat.stopwatch(6000f));
    }

    @Test
    void hudUnitPreservesLandscapeScaleAndPreventsHorizontalClippingOnNarrowOrPortraitWindows() {
        // Landscape & square windows keep height / 720f unchanged.
        for (int[] size : new int[][] {{1280, 720}, {1920, 1080}, {2560, 1080}, {1024, 768}, {800, 600}, {720, 720}}) {
            assertEquals(size[1] / 720f, HudRenderer.hudUnit(size[0], size[1]), 1e-6f,
                size[0] + "x" + size[1]);
        }

        // Half-screen and portrait windows clamp unit by width / 720f so 678-unit Title footer and 660-unit Briefing fit.
        String titleFooter = TestTuning.STRINGS.get(UiText.SCREEN_TITLE_FOOTER);
        assertEquals("WASD: MOVE  SPACE: JUMP  SHIFT: DASH  E: USE  1-3/Q/SCROLL: WEAPON  LMB/RMB: FIRE/ALT",
            titleFooter, "the tests' own footer with the key names of the tests' own bindings");
        for (int[] size : new int[][] {{960, 1080}, {1080, 1920}, {480, 720}, {680, 720}, {160, 320}}) {
            float w = size[0];
            float h = size[1];
            float u = HudRenderer.hudUnit(w, h);
            assertEquals(w / 720f, u, 1e-6f, w + "x" + h);
            assertTrue(PixelFont.width(titleFooter, HudRenderer.TITLE_FOOTER_PIXEL * u) <= w,
                "Title footer fits within " + w + "x" + h);
            assertTrue(664f * u <= w, "Briefing panel + border fits within " + w + "x" + h);
            assertTrue(492f * u <= h, "Tallest menu panel + border fits vertically within " + w + "x" + h);
        }
    }

    /** @return the Title footer as loaded with the key names {@code keys} gives */
    private static String titleFooter(KeyLabels keys) {
        return UiStrings.parse(UiStrings.PATH, TestTuning.text(UiStrings.PATH), TestTuning.FONT, keys,
            TestTuning.TEXT_VALUES).get(UiText.SCREEN_TITLE_FOOTER);
    }

    /** @return whether the Title footer, one centred line, stays inside a window of each 4:3 size */
    private static boolean footerFitsFourByThree(String footer) {
        boolean fits = true;
        for (int[] size : new int[][] {{640, 480}, {800, 600}, {1024, 768}, {1600, 1200}}) {
            float u = HudRenderer.hudUnit(size[0], size[1]);
            assertEquals(size[1] / 720f, u, 1e-6f, "the unit of a 4:3 window comes from its height");
            fits &= PixelFont.width(footer, HudRenderer.TITLE_FOOTER_PIXEL * u) <= size[0];
        }
        return fits;
    }

    @Test
    void titleFooterOfTheLoadedTextFitsAFourByThreeWindowWithAnyBoundInputAsTheHint() {
        // The hint of an action is the first input of its line. The widest footer the bindings can give is
        // the one that names, for each action, the input of its line with the longest name.
        KeyBindings bindings = TestTuning.BINDINGS;
        KeyLabels widest = id -> {
            String longest = bindings.hint(id);
            for (BoundInput input : bindings.inputs(Action.byId(id))) {
                String name = KeyNames.display(input);
                if (name.length() > longest.length()) longest = name;
            }
            return longest;
        };
        String ownFooter = TestTuning.STRINGS.get(UiText.SCREEN_TITLE_FOOTER);
        String widestFooter = titleFooter(widest);
        assertTrue(widestFooter.length() >= ownFooter.length(), widestFooter);
        assertTrue(footerFitsFourByThree(ownFooter), ownFooter);
        assertTrue(footerFitsFourByThree(widestFooter), widestFooter);

        // A 4:3 window is 960 HUD units wide, which is room for 120 characters of the footer's size.
        StringBuilder full = new StringBuilder();
        while (full.length() < 120) full.append('W');
        assertTrue(footerFitsFourByThree(full.toString()));
        assertFalse(footerFitsFourByThree(full + "W"));

        // The check sees a footer that long key names push past the window.
        String numpad = titleFooter(TestTuning.bindingsWith("move_forward", "Numpad 8", "move_left", "Numpad 4",
            "move_back", "Numpad 5", "move_right", "Numpad 6", "weapon_1", "Numpad 1", "weapon_3", "Numpad 3",
            "quick_swap", "Numpad 0")::hint);
        assertEquals("NUMPAD 8NUMPAD 4NUMPAD 5NUMPAD 6: MOVE  SPACE: JUMP  SHIFT: DASH  E: USE  "
            + "NUMPAD 1-NUMPAD 3/NUMPAD 0/SCROLL: WEAPON  LMB/RMB: FIRE/ALT", numpad);
        assertFalse(footerFitsFourByThree(numpad), numpad);
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
    void anInteractionPromptIsColouredByItsKindWhateverItsWords() {
        float[] usable = {0.62f, 1f, 0.86f, 0.95f};
        float[] blocked = {1f, 0.72f, 0.30f, 0.95f};
        // The four the interact key acts on, then the four of a bulkhead that stays shut.
        GameSimulation.InteractionPrompt[] prompts = GameSimulation.InteractionPrompt.values();
        assertEquals(8, prompts.length);
        for (GameSimulation.InteractionPrompt prompt : prompts) {
            boolean acts = prompt == GameSimulation.InteractionPrompt.OVERRIDE_CONSOLE
                || prompt == GameSimulation.InteractionPrompt.UNLOCK_AMBER
                || prompt == GameSimulation.InteractionPrompt.UNLOCK_CYAN
                || prompt == GameSimulation.InteractionPrompt.OPEN_BULKHEAD;
            assertArrayEquals(acts ? usable : blocked, HudRenderer.promptColour(prompt), 0f, prompt.name());
            // With the tests' own text that is the colour the first characters of the words used to pick.
            assertEquals(TestTuning.STRINGS.prompt(prompt).startsWith("[E]"), acts, prompt.name());
        }
        // The words are no part of the choice: a prompt reworded without its key keeps its colour.
        UiStrings reworded = TestTuning.stringsWith("prompt.open_bulkhead", "OPEN THE BULKHEAD");
        assertFalse(reworded.prompt(GameSimulation.InteractionPrompt.OPEN_BULKHEAD).startsWith("[E]"));
        assertArrayEquals(usable, HudRenderer.promptColour(GameSimulation.InteractionPrompt.OPEN_BULKHEAD), 0f);
        HudRenderer.promptColour(GameSimulation.InteractionPrompt.OPEN_BULKHEAD)[0] = 0f;
        assertArrayEquals(usable, HudRenderer.promptColour(GameSimulation.InteractionPrompt.OPEN_BULKHEAD), 0f,
            "the colour handed out is a copy");
    }

    @Test
    void liveRailLanceZoomNearBulkheadKeepsInteractionPromptBelowRangefinderBrackets() {
        GameMap doorMap = GameMap.parse(
            "#######",
            "#>.=.X#",
            "#######");
        PlayerLoadout railLoadout = new PlayerLoadout(
            Player.MAX_HEALTH, 0f, Difficulty.NORMAL, true, 8, true, 5, WeaponSlot.RAIL_LANCE);
        GameSimulation sim = TestTuning.simulation(doorMap, railLoadout, false);
        sim.player.position.set(2.4f, 1.5f);
        sim.player.look(90f - sim.player.getYaw(), 0f);
        assertEquals(GameSimulation.InteractionPrompt.OPEN_BULKHEAD, sim.getInteractionPrompt());
        assertEquals(WeaponSlot.RAIL_LANCE, sim.getActiveWeapon());

        PlayerInput holdZoom = new PlayerInput();
        holdZoom.charge = true;
        boolean sawZoomTransition = false;
        boolean sawFullZoom = false;
        for (int step = 0; step < 30; step++) {
            sim.update(1f / 120f, holdZoom);
            assertEquals(GameSimulation.InteractionPrompt.OPEN_BULKHEAD, sim.getInteractionPrompt());
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

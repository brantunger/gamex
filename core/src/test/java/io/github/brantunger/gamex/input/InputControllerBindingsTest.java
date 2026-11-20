package io.github.brantunger.gamex.input;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.badlogic.gdx.Gdx;
import com.badlogic.gdx.Graphics;
import com.badlogic.gdx.Input;
import com.badlogic.gdx.InputProcessor;
import io.github.brantunger.gamex.TestTuning;
import io.github.brantunger.gamex.data.AssetText;
import io.github.brantunger.gamex.hud.HudRenderer;
import io.github.brantunger.gamex.hud.MenuState;
import io.github.brantunger.gamex.hud.PixelFont;
import io.github.brantunger.gamex.player.PlayerInput;
import io.github.brantunger.gamex.settings.GameSettings;
import io.github.brantunger.gamex.settings.MapKeyValueStore;
import io.github.brantunger.gamex.settings.SaveData;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.lang.reflect.Proxy;
import java.util.HashSet;
import java.util.Set;

/**
 * Drives {@link InputController#poll} with stubbed devices and bindings other than the tests' own: an action
 * follows its line of the bindings file, and the input it had before does nothing. {@link InputControllerTest}
 * covers the tests' own bindings.
 */
class InputControllerBindingsTest {

    private static final String FILE = "data/test-bindings.txt";
    private static final float FRAME = 1f / 60f;
    private static final PixelFont FONT = TestTuning.FONT;

    private Input previousInput;
    private Graphics previousGraphics;
    /** Keys reported as just pressed on the next poll. */
    private final Set<Integer> justPressed = new HashSet<>();
    /** Keys reported as held down. */
    private final Set<Integer> held = new HashSet<>();
    /** Mouse buttons reported as just pressed on the next poll. */
    private final Set<Integer> buttonsJustPressed = new HashSet<>();
    /** Mouse buttons reported as held down. */
    private final Set<Integer> buttonsHeld = new HashSet<>();
    /** The processor last registered with the stub input; the key and button events are delivered through it. */
    private InputProcessor registered;
    private final PlayerInput out = new PlayerInput();

    @BeforeEach
    void installStubs() {
        previousInput = Gdx.input;
        previousGraphics = Gdx.graphics;
        Gdx.input = (Input) Proxy.newProxyInstance(
            Input.class.getClassLoader(),
            new Class<?>[] {Input.class},
            (proxy, method, args) -> {
                switch (method.getName()) {
                    case "setInputProcessor":
                        registered = (InputProcessor) args[0];
                        return null;
                    case "getInputProcessor":
                        return registered;
                    case "isKeyJustPressed":
                        return justPressed.contains((Integer) args[0]);
                    case "isKeyPressed":
                        return held.contains((Integer) args[0]);
                    case "isButtonPressed":
                        return buttonsHeld.contains((Integer) args[0]);
                    case "isButtonJustPressed":
                        return buttonsJustPressed.contains((Integer) args[0]);
                    default:
                        return defaultValue(method.getReturnType());
                }
            });
        Gdx.graphics = (Graphics) Proxy.newProxyInstance(
            Graphics.class.getClassLoader(),
            new Class<?>[] {Graphics.class},
            (proxy, method, args) -> {
                switch (method.getName()) {
                    case "getDeltaTime":
                        return FRAME;
                    case "getWidth":
                        return 1280;
                    case "getHeight":
                        return 720;
                    default:
                        return defaultValue(method.getReturnType());
                }
            });
    }

    @AfterEach
    void restoreGdxState() {
        Gdx.input = previousInput;
        Gdx.graphics = previousGraphics;
    }

    private static Object defaultValue(Class<?> returnType) {
        if (!returnType.isPrimitive()) return null;
        if (returnType == boolean.class) return false;
        if (returnType == int.class) return 0;
        if (returnType == long.class) return 0L;
        if (returnType == float.class) return 0f;
        if (returnType == double.class) return 0d;
        if (returnType == short.class) return (short) 0;
        if (returnType == byte.class) return (byte) 0;
        if (returnType == char.class) return '\0';
        return null;
    }

    /**
     * @param changes pairs of an action id and the value to give its line
     * @return the tests' own bindings with those lines changed
     */
    private static KeyBindings shippedWith(String... changes) {
        StringBuilder text = new StringBuilder();
        Set<String> changed = new HashSet<>();
        for (String line : AssetText.splitLines(TestTuning.text(KeyBindings.PATH))) {
            for (int i = 0; i < changes.length; i += 2) {
                if (line.startsWith(changes[i] + ":")) {
                    line = changes[i] + ": " + changes[i + 1];
                    changed.add(changes[i]);
                }
            }
            text.append(line).append('\n');
        }
        assertEquals(changes.length / 2, changed.size(), "every change must find its line in the tests' own file");
        return KeyBindings.parse(FILE, text.toString(), FONT);
    }

    private static MenuState playing() {
        return TestTuning.menu(TestTuning.settings(), new SaveData(new MapKeyValueStore()), MenuState.Screen.PLAYING);
    }

    private static MenuState paused() {
        MenuState menu = playing();
        menu.escape();
        assertEquals(MenuState.Screen.PAUSED, menu.getScreen());
        return menu;
    }

    private InputController controller(MenuState menu, KeyBindings bindings) {
        InputController controller = new InputController(menu, TestTuning.settings(), bindings);
        assertSame(controller, registered, "the controller must register itself as the input processor");
        return controller;
    }

    /** Polls one frame with {@code keys} just pressed, each delivered as a key-down event first. */
    private MenuState.Command poll(InputController controller, int... keys) {
        justPressed.clear();
        for (int key : keys) {
            justPressed.add(key);
            registered.keyDown(key);
        }
        return controller.poll(out);
    }

    /** Polls one frame of play with {@code keys} held down and nothing just pressed. */
    private void pollHolding(InputController controller, int... keys) {
        justPressed.clear();
        held.clear();
        for (int key : keys) {
            held.add(key);
        }
        assertEquals(MenuState.Command.NONE, controller.poll(out));
    }

    @Test
    void remappedMoveKeysMoveAndTheOldOnesDoNot() {
        InputController controller = controller(playing(), shippedWith("move_forward", "E", "move_back", "D",
            "move_left", "S", "move_right", "F", "interact", "G", "menu_down", "Down", "menu_right", "Right"));

        pollHolding(controller, Input.Keys.E);
        assertEquals(1f, out.moveForward);
        assertEquals(0f, out.moveStrafe);
        pollHolding(controller, Input.Keys.D);
        assertEquals(-1f, out.moveForward);
        assertEquals(0f, out.moveStrafe);
        pollHolding(controller, Input.Keys.S);
        assertEquals(0f, out.moveForward);
        assertEquals(-1f, out.moveStrafe);
        pollHolding(controller, Input.Keys.F);
        assertEquals(1f, out.moveStrafe);

        pollHolding(controller, Input.Keys.W, Input.Keys.A);
        assertEquals(0f, out.moveForward, "W no longer moves");
        assertEquals(0f, out.moveStrafe, "A no longer moves");
    }

    @Test
    void eachOfTwoKeysForOneActionActs() {
        InputController controller = controller(playing(), shippedWith("move_forward", "W, Up", "jump",
            "Space, L-Ctrl", "interact", "E, F"));

        pollHolding(controller, Input.Keys.UP);
        assertEquals(1f, out.moveForward);
        pollHolding(controller, Input.Keys.W);
        assertEquals(1f, out.moveForward);
        pollHolding(controller, Input.Keys.W, Input.Keys.UP);
        assertEquals(1f, out.moveForward, "two keys of one action do not add up");
        pollHolding(controller, Input.Keys.CONTROL_LEFT);
        assertTrue(out.jump);
        pollHolding(controller, Input.Keys.SPACE);
        assertTrue(out.jump);

        held.clear();
        assertEquals(MenuState.Command.NONE, poll(controller, Input.Keys.F));
        assertTrue(out.interact);
        assertEquals(MenuState.Command.NONE, poll(controller, Input.Keys.E));
        assertTrue(out.interact);
        assertEquals(MenuState.Command.NONE, poll(controller));
        assertFalse(out.interact);
    }

    @Test
    void fireOnAKeyAndDashOnAMouseButton() {
        InputController controller = controller(playing(), shippedWith("fire", "F", "alt_fire", "G",
            "dash", "Mouse-Right", "jump", "Mouse-Middle"));

        pollHolding(controller, Input.Keys.F);
        assertTrue(out.fire);
        assertFalse(out.charge);
        pollHolding(controller, Input.Keys.G);
        assertTrue(out.charge);
        assertFalse(out.fire);

        held.clear();
        buttonsHeld.add(Input.Buttons.LEFT);
        buttonsHeld.add(Input.Buttons.RIGHT);
        assertEquals(MenuState.Command.NONE, poll(controller, Input.Keys.SHIFT_LEFT));
        assertFalse(out.fire, "the left button no longer fires");
        assertFalse(out.charge, "the right button no longer charges");
        assertFalse(out.dash, "a held button is not a press, and Shift no longer dashes");

        buttonsJustPressed.add(Input.Buttons.RIGHT);
        assertEquals(MenuState.Command.NONE, poll(controller));
        assertTrue(out.dash);
        buttonsJustPressed.clear();
        buttonsHeld.clear();
        buttonsHeld.add(Input.Buttons.MIDDLE);
        assertEquals(MenuState.Command.NONE, poll(controller));
        assertFalse(out.dash);
        assertTrue(out.jump);
    }

    @Test
    void weaponKeysAndTheWheelFollowTheBindings() {
        InputController controller = controller(playing(), shippedWith("weapon_next", "Wheel-Up, E",
            "weapon_prev", "Wheel-Down", "interact", "F", "weapon_1", "Z", "quick_swap", "Mouse-Middle"));

        assertTrue(registered.scrolled(0f, -1f));
        assertEquals(MenuState.Command.NONE, poll(controller));
        assertEquals(PlayerInput.WEAPON_CYCLE_NEXT, out.weaponSlot);
        assertEquals(MenuState.Command.NONE, poll(controller));
        assertEquals(0, out.weaponSlot, "a turn of the wheel lasts one frame");
        assertTrue(registered.scrolled(0f, 1f));
        assertEquals(MenuState.Command.NONE, poll(controller));
        assertEquals(PlayerInput.WEAPON_CYCLE_PREV, out.weaponSlot);

        assertEquals(MenuState.Command.NONE, poll(controller, Input.Keys.E));
        assertEquals(PlayerInput.WEAPON_CYCLE_NEXT, out.weaponSlot);
        assertEquals(MenuState.Command.NONE, poll(controller, Input.Keys.Z));
        assertEquals(1, out.weaponSlot);
        assertEquals(MenuState.Command.NONE, poll(controller, Input.Keys.NUM_1, Input.Keys.Q));
        assertEquals(0, out.weaponSlot, "1 and Q no longer choose a weapon");
        buttonsJustPressed.add(Input.Buttons.MIDDLE);
        assertEquals(MenuState.Command.NONE, poll(controller));
        assertEquals(PlayerInput.WEAPON_QUICK_SWAP, out.weaponSlot);
    }

    @Test
    void theWheelCanFire() {
        InputController controller = controller(playing(), shippedWith("fire", "Wheel-Down", "weapon_next", "E",
            "interact", "F"));

        assertEquals(MenuState.Command.NONE, poll(controller));
        assertFalse(out.fire);
        assertTrue(registered.scrolled(0f, 1f));
        assertEquals(MenuState.Command.NONE, poll(controller));
        assertTrue(out.fire);
        assertEquals(0, out.weaponSlot);
        assertEquals(MenuState.Command.NONE, poll(controller));
        assertFalse(out.fire, "a turn of the wheel lasts one frame");
        assertTrue(registered.scrolled(0f, -1f));
        assertEquals(MenuState.Command.NONE, poll(controller));
        assertFalse(out.fire, "the other direction is another input");
        assertEquals(PlayerInput.WEAPON_CYCLE_PREV, out.weaponSlot);
    }

    @Test
    void pauseAndRetryFollowTheBindingsInPlayAndOnTheMenus() {
        KeyBindings bindings = shippedWith("pause", "P", "retry", "T");
        MenuState menu = playing();
        InputController controller = controller(menu, bindings);

        assertEquals(MenuState.Command.NONE, poll(controller, Input.Keys.ESCAPE));
        assertTrue(menu.isPlaying(), "Escape no longer pauses");
        poll(controller, Input.Keys.P);
        assertEquals(MenuState.Screen.PAUSED, menu.getScreen());
        assertEquals(MenuState.Command.NONE, poll(controller, Input.Keys.ESCAPE, Input.Keys.R));
        assertEquals(MenuState.Screen.PAUSED, menu.getScreen(), "Escape and R do nothing on the pause screen");
        poll(controller, Input.Keys.P);
        assertTrue(menu.isPlaying(), "P resumes");

        assertEquals(MenuState.Command.NONE, poll(controller, Input.Keys.R));
        assertEquals(MenuState.Command.LAUNCH_STAGE, poll(controller, Input.Keys.T));

        MenuState pausedMenu = paused();
        assertEquals(MenuState.Command.LAUNCH_STAGE, poll(controller(pausedMenu, bindings), Input.Keys.T));
    }

    @Test
    void menuKeysFollowTheBindings() {
        KeyBindings bindings = shippedWith("menu_up", "I", "menu_down", "K", "menu_confirm", "F", "quit", "X");
        MenuState menu = paused();
        InputController controller = controller(menu, bindings);
        int first = menu.getSelectedIndex();

        poll(controller, Input.Keys.DOWN);
        poll(controller, Input.Keys.S);
        assertEquals(first, menu.getSelectedIndex(), "Down and S no longer move the selection");
        poll(controller, Input.Keys.K);
        assertEquals(first + 1, menu.getSelectedIndex());
        poll(controller, Input.Keys.UP);
        poll(controller, Input.Keys.W);
        assertEquals(first + 1, menu.getSelectedIndex(), "Up and W no longer move the selection");
        poll(controller, Input.Keys.I);
        assertEquals(first, menu.getSelectedIndex());

        assertEquals(MenuState.Command.NONE, poll(controller, Input.Keys.Q));
        assertEquals(MenuState.Command.NONE, poll(controller, Input.Keys.Q));
        assertEquals(MenuState.Command.NONE, poll(controller, Input.Keys.X));
        assertEquals(MenuState.Command.QUIT, poll(controller, Input.Keys.X), "the second X quits; Q no longer does");

        MenuState resumeMenu = paused();
        InputController resumeController = controller(resumeMenu, bindings);
        poll(resumeController, Input.Keys.ENTER, Input.Keys.SPACE);
        assertEquals(MenuState.Screen.PAUSED, resumeMenu.getScreen(), "Enter and Space no longer confirm");
        poll(resumeController, Input.Keys.F);
        assertTrue(resumeMenu.isPlaying(), "F confirms RESUME");
    }

    @Test
    void settingsKeysFollowTheBindings() {
        GameSettings settings = TestTuning.settings();
        MenuState menu = TestTuning.menu(settings, new SaveData(new MapKeyValueStore()), MenuState.Screen.PLAYING);
        menu.escape();
        assertTrue(menu.selectId(MenuState.OptionId.SETTINGS));
        menu.confirm();
        assertEquals(MenuState.Screen.SETTINGS, menu.getScreen());
        assertTrue(menu.selectId(MenuState.OptionId.SET_FOV));
        float start = settings.getFov();
        GameSettings expected = TestTuning.settings();
        expected.adjustFov(1);
        InputController controller = new InputController(menu, settings,
            shippedWith("menu_left", "J", "menu_right", "L"));

        poll(controller, Input.Keys.RIGHT);
        poll(controller, Input.Keys.D);
        poll(controller, Input.Keys.LEFT);
        poll(controller, Input.Keys.A);
        assertEquals(start, settings.getFov(), "the arrows, A and D no longer adjust");
        poll(controller, Input.Keys.L);
        assertEquals(expected.getFov(), settings.getFov());
        poll(controller, Input.Keys.J);
        assertEquals(start, settings.getFov());
    }

    @Test
    void aMouseButtonCanConfirmAndTheClickFollowsItsBinding() {
        KeyBindings bindings = shippedWith("menu_click", "Mouse-Right", "menu_confirm", "Enter, Mouse-Middle");
        MenuState menu = paused();
        InputController controller = controller(menu, bindings);
        int[] row = new int[2];
        rowCentre(menu, 0, row);
        assertEquals(MenuState.OptionId.RESUME, menu.getMenuOptions().get(0).id);

        registered.touchDown(row[0], row[1], 0, Input.Buttons.LEFT);
        assertEquals(MenuState.Command.NONE, controller.poll(out));
        assertEquals(MenuState.Screen.PAUSED, menu.getScreen(), "the left button no longer clicks");
        registered.touchDown(row[0], row[1], 0, Input.Buttons.RIGHT);
        controller.poll(out);
        assertTrue(menu.isPlaying(), "the right button clicks RESUME");

        MenuState second = paused();
        InputController secondController = controller(second, bindings);
        // Off every row: the middle button confirms the selected row wherever the pointer is.
        registered.touchDown(0, 0, 0, Input.Buttons.MIDDLE);
        secondController.poll(out);
        assertTrue(second.isPlaying(), "the middle button confirms RESUME");
    }

    @Test
    void aReboundJumpHeldFromConfirmingResumeDoesNotJumpUntilItIsPressedAgain() {
        MenuState menu = paused();
        InputController controller = controller(menu, shippedWith("jump", "Enter", "fire", "Space"));

        held.add(Input.Keys.ENTER);
        poll(controller, Input.Keys.ENTER);
        assertTrue(menu.isPlaying());
        assertEquals(MenuState.Command.NONE, poll(controller));
        assertFalse(out.jump, "Enter, still held from confirming, must not jump");
        held.clear();
        assertEquals(MenuState.Command.NONE, poll(controller));
        held.add(Input.Keys.ENTER);
        assertEquals(MenuState.Command.NONE, poll(controller));
        assertTrue(out.jump, "a fresh press jumps");

        MenuState spaceMenu = paused();
        InputController spaceController = controller(spaceMenu, shippedWith("jump", "Enter", "fire", "Space"));
        held.clear();
        held.add(Input.Keys.SPACE);
        poll(spaceController, Input.Keys.SPACE);
        assertTrue(spaceMenu.isPlaying());
        assertEquals(MenuState.Command.NONE, poll(spaceController));
        assertFalse(out.fire, "Space, still held from confirming, must not fire");
        assertFalse(out.jump, "Space no longer jumps");
        held.clear();
        assertEquals(MenuState.Command.NONE, poll(spaceController));
        held.add(Input.Keys.SPACE);
        assertEquals(MenuState.Command.NONE, poll(spaceController));
        assertTrue(out.fire, "a fresh press fires");
    }

    /** Finds a screen point over menu row {@code index} and stores it in {@code point}. */
    private static void rowCentre(MenuState menu, int index, int[] point) {
        for (int y = 0; y < 720; y++) {
            if (HudRenderer.optionAt(1280, 720, 640, y, menu) == index) {
                point[0] = 640;
                point[1] = y;
                return;
            }
        }
        throw new AssertionError("no screen point is over row " + index);
    }
}

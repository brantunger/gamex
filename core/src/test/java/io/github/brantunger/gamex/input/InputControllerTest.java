package io.github.brantunger.gamex.input;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.junit.jupiter.api.Assertions.fail;

import com.badlogic.gdx.Gdx;
import com.badlogic.gdx.Graphics;
import com.badlogic.gdx.Input;
import com.badlogic.gdx.InputProcessor;
import io.github.brantunger.gamex.hud.HudRenderer;
import io.github.brantunger.gamex.hud.MenuState;
import io.github.brantunger.gamex.player.PlayerInput;
import io.github.brantunger.gamex.settings.GameSettings;
import io.github.brantunger.gamex.settings.MapKeyValueStore;
import io.github.brantunger.gamex.settings.SaveData;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.lang.reflect.Proxy;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

/** Drives {@link InputController#poll} with stubbed devices to check the key routing on menu screens. */
class InputControllerTest {

    private static final float FRAME = 1f / 60f;
    /** The index of the FIELD OF VIEW row on the Settings screen. */
    private static final int FOV_ROW = 2;

    private Input previousInput;
    private Graphics previousGraphics;
    /** Keys reported as just pressed on the next poll. */
    private final Set<Integer> justPressed = new HashSet<>();
    /** Keys reported as held down. */
    private final Set<Integer> held = new HashSet<>();
    /** Whether the left button is reported as held down. */
    private boolean leftHeld;
    /** Whether the left button is reported as just pressed, at ({@link #mouseX}, {@link #mouseY}). */
    private boolean leftJustPressed;
    private int mouseX;
    private int mouseY;
    /** The frame time the stub graphics reports. */
    private float deltaTime = FRAME;
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
                        return leftHeld && (Integer) args[0] == Input.Buttons.LEFT;
                    case "isButtonJustPressed":
                        return leftJustPressed && (Integer) args[0] == Input.Buttons.LEFT;
                    case "getX":
                        return mouseX;
                    case "getY":
                        return mouseY;
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
                        return deltaTime;
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

    private static MenuState menuOn(MenuState.Screen screen) {
        MenuState menu = new MenuState(new GameSettings(), new SaveData(new MapKeyValueStore()),
            MenuState.Screen.PLAYING);
        if (screen == MenuState.Screen.PAUSED) {
            menu.escape();
        } else if (screen == MenuState.Screen.DEAD) {
            menu.onPlayerDied();
        } else if (screen == MenuState.Screen.TITLE) {
            menu.escape();
            menu.setSelectedIndex(menu.getOptions().indexOf("RETURN TO TITLE"));
            menu.confirm();
        }
        assertEquals(screen, menu.getScreen());
        return menu;
    }

    /** @return a menu on the Settings screen, opened from the pause menu, with the FIELD OF VIEW row selected */
    private static MenuState settingsMenuOnFov(GameSettings settings) {
        MenuState menu = new MenuState(settings, new SaveData(new MapKeyValueStore()), MenuState.Screen.PLAYING);
        menu.escape();
        menu.setSelectedIndex(menu.getOptions().indexOf("SETTINGS"));
        menu.confirm();
        assertEquals(MenuState.Screen.SETTINGS, menu.getScreen());
        menu.setSelectedIndex(FOV_ROW);
        assertTrue(menu.getOptions().get(menu.getSelectedIndex()).startsWith("FIELD OF VIEW"));
        return menu;
    }

    /**
     * @return the processor the backend would deliver events to, which must be {@code controller}: an event reaches
     *     the controller only through the registration it makes when it is built
     */
    private InputProcessor processorOf(InputController controller) {
        assertSame(controller, registered, "the controller must register itself as the input processor");
        return registered;
    }

    /** Delivers a button-down event at ({@code x}, {@code y}) the way the backend does, through the registration. */
    private void click(InputController controller, int x, int y, int button) {
        processorOf(controller).touchDown(x, y, 0, button);
    }

    /** Polls one frame of {@code seconds} with {@code keys} just pressed, each delivered as a key-down event first. */
    private MenuState.Command poll(InputController controller, float seconds, int... keys) {
        justPressed.clear();
        for (int key : keys) {
            justPressed.add(key);
            processorOf(controller).keyDown(key);
        }
        deltaTime = seconds;
        return controller.poll(out);
    }

    /**
     * Minimises the window in the same event poll as a press of {@code key}, as the desktop backend does: the key
     * stays flagged as just pressed on every frame from then on, and its key-down event is not delivered.
     */
    private void minimiseWithKeyFlagged(InputController controller, int key) {
        justPressed.clear();
        justPressed.add(key);
        controller.pause();
    }

    /** Polls one frame of {@code seconds} without touching the just-pressed flags and without any input event. */
    private MenuState.Command pollMinimised(InputController controller, float seconds) {
        deltaTime = seconds;
        return controller.poll(out);
    }

    /** @return a window y coordinate inside the row of {@code option} on the current screen, at the centre x */
    private static int rowY(MenuState menu, String option) {
        int index = menu.getOptions().indexOf(option);
        for (int y = 0; y < 720; y++) {
            if (HudRenderer.optionAt(1280, 720, 640, y, menu) == index) return y + 2;
        }
        return fail("no row for " + option);
    }

    @Test
    void rRestartsTheStageFromThePauseMenu() {
        MenuState menu = menuOn(MenuState.Screen.PAUSED);
        InputController controller = new InputController(menu, new GameSettings());

        assertEquals(MenuState.Command.LAUNCH_STAGE, poll(controller, FRAME, Input.Keys.R));
        assertEquals(MenuState.Screen.PLAYING, menu.getScreen());
    }

    @Test
    void rRestartsTheStageInPlayAndOnTheDeathScreenButNotOnTheTitle() {
        MenuState playing = menuOn(MenuState.Screen.PLAYING);
        assertEquals(MenuState.Command.LAUNCH_STAGE,
            poll(new InputController(playing, new GameSettings()), FRAME, Input.Keys.R));

        MenuState dead = menuOn(MenuState.Screen.DEAD);
        assertEquals(MenuState.Command.LAUNCH_STAGE,
            poll(new InputController(dead, new GameSettings()), FRAME, Input.Keys.R));
        assertEquals(MenuState.Screen.PLAYING, dead.getScreen());

        MenuState title = menuOn(MenuState.Screen.TITLE);
        assertEquals(MenuState.Command.NONE,
            poll(new InputController(title, new GameSettings()), FRAME, Input.Keys.R));
        assertEquals(MenuState.Screen.TITLE, title.getScreen());
    }

    @Test
    void qQuitsOnlyOnASecondPressOnTitleAndPause() {
        for (MenuState.Screen screen : List.of(MenuState.Screen.TITLE, MenuState.Screen.PAUSED)) {
            MenuState menu = menuOn(screen);
            InputController controller = new InputController(menu, new GameSettings());

            assertEquals(MenuState.Command.NONE, poll(controller, FRAME, Input.Keys.Q), "first Q on " + screen);
            assertEquals(screen, menu.getScreen());
            assertEquals(MenuState.Command.NONE, poll(controller, 1.0f), "an idle frame on " + screen);
            assertEquals(MenuState.Command.QUIT, poll(controller, 0.9f, Input.Keys.Q),
                "second Q about 1.9 s later on " + screen);
        }
    }

    @Test
    void qPromptExpiresAfterAboutTwoSecondsAndThenNeedsTwoPressesAgain() {
        MenuState menu = menuOn(MenuState.Screen.TITLE);
        InputController controller = new InputController(menu, new GameSettings());

        assertEquals(MenuState.Command.NONE, poll(controller, FRAME, Input.Keys.Q));
        assertEquals(MenuState.Command.NONE, poll(controller, 1.0f));
        assertEquals(MenuState.Command.NONE, poll(controller, 1.1f));
        assertEquals(MenuState.Command.NONE, poll(controller, FRAME, Input.Keys.Q),
            "a Q after the window only shows the prompt again");
        assertEquals(MenuState.Command.QUIT, poll(controller, FRAME, Input.Keys.Q));
    }

    @Test
    void leavingTheScreenCancelsThePendingQuit() {
        MenuState menu = menuOn(MenuState.Screen.TITLE);
        InputController controller = new InputController(menu, new GameSettings());

        assertEquals(MenuState.Command.NONE, poll(controller, FRAME, Input.Keys.Q));
        menu.setSelectedIndex(menu.getOptions().indexOf("SETTINGS"));
        assertEquals(MenuState.Command.NONE, poll(controller, FRAME, Input.Keys.ENTER));
        assertEquals(MenuState.Screen.SETTINGS, menu.getScreen());
        assertEquals(MenuState.Command.NONE, poll(controller, FRAME, Input.Keys.Q), "Q does nothing in Settings");
        assertEquals(MenuState.Command.NONE, poll(controller, FRAME, Input.Keys.ESCAPE));
        assertEquals(MenuState.Screen.TITLE, menu.getScreen());

        assertEquals(MenuState.Command.NONE, poll(controller, FRAME, Input.Keys.Q),
            "back on the Title the first Q only shows the prompt");
        assertEquals(MenuState.Command.QUIT, poll(controller, FRAME, Input.Keys.Q));
    }

    @Test
    void enterOnTheQuitRowQuitsAtOnce() {
        for (MenuState.Screen screen : List.of(MenuState.Screen.TITLE, MenuState.Screen.PAUSED)) {
            MenuState menu = menuOn(screen);
            InputController controller = new InputController(menu, new GameSettings());
            menu.setSelectedIndex(menu.getOptions().indexOf("QUIT"));

            assertEquals(MenuState.Command.QUIT, poll(controller, FRAME, Input.Keys.ENTER), "QUIT row on " + screen);
        }
    }

    @Test
    void qInPlaySwapsWeaponsAndNeverQuits() {
        MenuState menu = menuOn(MenuState.Screen.PLAYING);
        InputController controller = new InputController(menu, new GameSettings());

        assertEquals(MenuState.Command.NONE, poll(controller, FRAME, Input.Keys.Q));
        assertEquals(PlayerInput.WEAPON_QUICK_SWAP, out.weaponSlot);
        assertEquals(MenuState.Command.NONE, poll(controller, FRAME, Input.Keys.Q));
        assertTrue(menu.isPlaying());
        assertFalse(out.fire);
    }

    @Test
    void qStuckAsJustPressedWhileMinimisedNeitherShowsThePromptNorQuits() {
        MenuState menu = menuOn(MenuState.Screen.PLAYING);
        InputController controller = new InputController(menu, new GameSettings());
        minimiseWithKeyFlagged(controller, Input.Keys.Q);
        assertEquals(MenuState.Screen.PAUSED, menu.getScreen());

        for (int frame = 1; frame <= 3; frame++) {
            assertEquals(MenuState.Command.NONE, pollMinimised(controller, FRAME), "minimised frame " + frame);
            assertFalse(menu.isQuitPromptVisible(), "prompt on minimised frame " + frame);
        }

        // The one real press arrives as an event once the window is restored, and counts once.
        assertEquals(MenuState.Command.NONE, poll(controller, FRAME, Input.Keys.Q));
        assertTrue(menu.isQuitPromptVisible());
        assertEquals(MenuState.Command.NONE, poll(controller, FRAME));
        assertEquals(MenuState.Command.QUIT, poll(controller, FRAME, Input.Keys.Q));
    }

    @Test
    void rStuckAsJustPressedWhileMinimisedDoesNotRestartTheStage() {
        for (MenuState.Screen screen : List.of(MenuState.Screen.PLAYING, MenuState.Screen.DEAD)) {
            MenuState menu = menuOn(screen);
            InputController controller = new InputController(menu, new GameSettings());
            minimiseWithKeyFlagged(controller, Input.Keys.R);
            MenuState.Screen minimised = menu.getScreen();
            assertFalse(menu.isPlaying());

            for (int frame = 1; frame <= 3; frame++) {
                assertEquals(MenuState.Command.NONE, pollMinimised(controller, FRAME),
                    "minimised frame " + frame + " from " + screen);
                assertEquals(minimised, menu.getScreen(), "minimised frame " + frame + " from " + screen);
            }
        }
    }

    @Test
    void escapeStuckAsJustPressedWhileMinimisedLeavesTheGamePaused() {
        MenuState menu = menuOn(MenuState.Screen.PLAYING);
        InputController controller = new InputController(menu, new GameSettings());
        minimiseWithKeyFlagged(controller, Input.Keys.ESCAPE);

        for (int frame = 1; frame <= 3; frame++) {
            assertEquals(MenuState.Command.NONE, pollMinimised(controller, FRAME), "minimised frame " + frame);
            assertEquals(MenuState.Screen.PAUSED, menu.getScreen(), "minimised frame " + frame);
        }
    }

    @Test
    void enterStuckAsJustPressedWhileMinimisedDoesNotConfirmResume() {
        for (int key : new int[] {Input.Keys.ENTER, Input.Keys.SPACE}) {
            MenuState menu = menuOn(MenuState.Screen.PLAYING);
            InputController controller = new InputController(menu, new GameSettings());
            minimiseWithKeyFlagged(controller, key);
            menu.setSelectedIndex(menu.getOptions().indexOf("RESUME"));

            for (int frame = 1; frame <= 3; frame++) {
                assertEquals(MenuState.Command.NONE, pollMinimised(controller, FRAME), "minimised frame " + frame);
                assertEquals(MenuState.Screen.PAUSED, menu.getScreen(), "minimised frame " + frame);
            }
        }
    }

    @Test
    void selectionKeysStuckAsJustPressedWhileMinimisedDoNotMoveTheSelection() {
        for (int key : new int[] {Input.Keys.DOWN, Input.Keys.S, Input.Keys.UP, Input.Keys.W}) {
            MenuState menu = menuOn(MenuState.Screen.PLAYING);
            InputController controller = new InputController(menu, new GameSettings());
            minimiseWithKeyFlagged(controller, key);
            menu.setSelectedIndex(1);

            for (int frame = 1; frame <= 3; frame++) {
                assertEquals(MenuState.Command.NONE, pollMinimised(controller, FRAME), "minimised frame " + frame);
                assertEquals(1, menu.getSelectedIndex(), "minimised frame " + frame);
            }
        }
    }

    @Test
    void horizontalKeysStuckAsJustPressedWhileMinimisedDoNotAdjustTheSetting() {
        for (int key : new int[] {Input.Keys.LEFT, Input.Keys.A, Input.Keys.RIGHT, Input.Keys.D}) {
            GameSettings settings = new GameSettings();
            MenuState menu = settingsMenuOnFov(settings);
            InputController controller = new InputController(menu, settings);
            minimiseWithKeyFlagged(controller, key);
            assertEquals(MenuState.Screen.SETTINGS, menu.getScreen());
            assertEquals(FOV_ROW, menu.getSelectedIndex());

            for (int frame = 1; frame <= 3; frame++) {
                String at = "minimised frame " + frame + " with " + Input.Keys.toString(key);
                assertEquals(MenuState.Command.NONE, pollMinimised(controller, FRAME), at);
                assertEquals(GameSettings.DEFAULT_FOV, settings.getFov(), at);
            }
        }
    }

    @Test
    void aHorizontalKeyPressAdjustsTheSettingOnce() {
        for (int key : new int[] {Input.Keys.LEFT, Input.Keys.A, Input.Keys.RIGHT, Input.Keys.D}) {
            GameSettings settings = new GameSettings();
            MenuState menu = settingsMenuOnFov(settings);
            InputController controller = new InputController(menu, settings);
            boolean lowers = key == Input.Keys.LEFT || key == Input.Keys.A;
            float adjusted = GameSettings.DEFAULT_FOV + (lowers ? -GameSettings.FOV_STEP : GameSettings.FOV_STEP);
            String name = Input.Keys.toString(key);

            assertEquals(MenuState.Command.NONE, poll(controller, FRAME, key), name);
            assertEquals(adjusted, settings.getFov(), "one press of " + name + " moves one step");
            assertEquals(MenuState.Command.NONE, poll(controller, FRAME), name);
            assertEquals(adjusted, settings.getFov(), "one press of " + name + " acts once");
            assertEquals(MenuState.Screen.SETTINGS, menu.getScreen());
        }
    }

    @Test
    void leftClickStuckAsJustPressedWhileMinimisedDoesNotConfirmTheRowUnderTheCursor() {
        MenuState menu = menuOn(MenuState.Screen.PLAYING);
        InputController controller = new InputController(menu, new GameSettings());
        controller.pause();
        mouseX = 640;
        mouseY = rowY(menu, "RESUME");
        leftJustPressed = true;

        for (int frame = 1; frame <= 3; frame++) {
            assertEquals(MenuState.Command.NONE, pollMinimised(controller, FRAME), "minimised frame " + frame);
            assertEquals(MenuState.Screen.PAUSED, menu.getScreen(), "minimised frame " + frame);
        }
    }

    @Test
    void leftClickOnAMenuRowConfirmsItOnce() {
        MenuState menu = menuOn(MenuState.Screen.PAUSED);
        InputController controller = new InputController(menu, new GameSettings());
        int x = 640;
        int y = rowY(menu, "QUIT");

        click(controller, x, y, Input.Buttons.RIGHT);
        assertEquals(MenuState.Command.NONE, pollMinimised(controller, FRAME), "a right click confirms nothing");
        click(controller, x, y, Input.Buttons.LEFT);
        assertEquals(MenuState.Command.QUIT, pollMinimised(controller, FRAME));
        assertEquals(MenuState.Command.NONE, pollMinimised(controller, FRAME), "one click acts once");
    }

    @Test
    void escapeStillPausesAndResumesWithOnePressEach() {
        MenuState menu = menuOn(MenuState.Screen.PLAYING);
        InputController controller = new InputController(menu, new GameSettings());

        assertEquals(MenuState.Command.NONE, poll(controller, FRAME, Input.Keys.ESCAPE));
        assertEquals(MenuState.Screen.PAUSED, menu.getScreen());
        assertEquals(MenuState.Command.NONE, poll(controller, FRAME));
        assertEquals(MenuState.Screen.PAUSED, menu.getScreen());
        assertEquals(MenuState.Command.NONE, poll(controller, FRAME, Input.Keys.ESCAPE));
        assertEquals(MenuState.Screen.PLAYING, menu.getScreen());
    }

    @Test
    void aKeyPressedInPlayDoesNotActOnTheMenuThatOpensLater() {
        MenuState menu = menuOn(MenuState.Screen.PLAYING);
        InputController controller = new InputController(menu, new GameSettings());

        assertEquals(MenuState.Command.NONE, poll(controller, FRAME, Input.Keys.Q, Input.Keys.ENTER));
        assertEquals(MenuState.Command.NONE, poll(controller, FRAME, Input.Keys.ESCAPE));
        assertEquals(MenuState.Screen.PAUSED, menu.getScreen());

        assertEquals(MenuState.Command.NONE, poll(controller, FRAME));
        assertEquals(MenuState.Screen.PAUSED, menu.getScreen());
        assertFalse(menu.isQuitPromptVisible());
    }

    @Test
    void spaceHeldFromConfirmingResumeDoesNotJumpUntilItIsPressedAgain() {
        MenuState menu = menuOn(MenuState.Screen.PAUSED);
        InputController controller = new InputController(menu, new GameSettings());
        menu.setSelectedIndex(menu.getOptions().indexOf("RESUME"));

        held.add(Input.Keys.SPACE);
        assertEquals(MenuState.Command.NONE, poll(controller, FRAME, Input.Keys.SPACE));
        assertTrue(menu.isPlaying());
        poll(controller, FRAME);
        assertFalse(out.jump, "the Space that confirmed RESUME must not jump");

        held.clear();
        poll(controller, FRAME);
        held.add(Input.Keys.SPACE);
        poll(controller, FRAME, Input.Keys.SPACE);
        assertTrue(out.jump, "a new press of Space jumps");
    }

    @Test
    void buttonHeldFromClickingResumeDoesNotFireUntilItIsPressedAgain() {
        MenuState menu = menuOn(MenuState.Screen.PAUSED);
        InputController controller = new InputController(menu, new GameSettings());

        leftHeld = true;
        click(controller, 640, rowY(menu, "RESUME"), Input.Buttons.LEFT);
        assertEquals(MenuState.Command.NONE, poll(controller, FRAME));
        assertTrue(menu.isPlaying());
        poll(controller, FRAME);
        assertFalse(out.fire, "the click that confirmed RESUME must not fire");

        leftHeld = false;
        poll(controller, FRAME);
        leftHeld = true;
        poll(controller, FRAME);
        assertTrue(out.fire, "a new press of the button fires");
    }
}

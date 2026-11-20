package io.github.brantunger.gamex.input;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.junit.jupiter.api.Assertions.fail;

import com.badlogic.gdx.Gdx;
import com.badlogic.gdx.Graphics;
import com.badlogic.gdx.Input;
import com.badlogic.gdx.InputProcessor;
import io.github.brantunger.gamex.audio.SoundEvent;
import io.github.brantunger.gamex.hud.HudRenderer;
import io.github.brantunger.gamex.hud.MenuState;
import io.github.brantunger.gamex.player.Player;
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

    private Input previousInput;
    private Graphics previousGraphics;
    /** Keys reported as just pressed on the next poll. */
    private final Set<Integer> justPressed = new HashSet<>();
    /** Keys reported as held down. */
    private final Set<Integer> held = new HashSet<>();
    /** Whether the left button is reported as held down. */
    private boolean leftHeld;
    /** Whether the right button is reported as held down. */
    private boolean rightHeld;
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
                    case "isButtonPressed": {
                        int button = (Integer) args[0];
                        return (button == Input.Buttons.LEFT && leftHeld)
                            || (button == Input.Buttons.RIGHT && rightHeld);
                    }
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
        if (screen == MenuState.Screen.STAGE_SELECT) {
            MenuState menu = new MenuState(new GameSettings(), new SaveData(new MapKeyValueStore()),
                MenuState.Screen.TITLE);
            assertTrue(menu.selectId(MenuState.OptionId.SELECT_STAGE));
            menu.confirm();
            assertEquals(screen, menu.getScreen());
            return menu;
        }
        if (screen == MenuState.Screen.DIFFICULTY_SELECT) {
            MenuState menu = new MenuState(new GameSettings(), new SaveData(new MapKeyValueStore()),
                MenuState.Screen.TITLE);
            assertTrue(menu.selectId(MenuState.OptionId.START_CAMPAIGN));
            menu.confirm();
            assertEquals(screen, menu.getScreen());
            return menu;
        }
        if (screen == MenuState.Screen.BRIEFING) {
            MenuState menu = new MenuState(new GameSettings(), new SaveData(new MapKeyValueStore()),
                MenuState.Screen.TITLE);
            assertTrue(menu.selectId(MenuState.OptionId.TRAINING_RANGE));
            menu.confirm();
            assertEquals(screen, menu.getScreen());
            return menu;
        }
        MenuState menu = new MenuState(new GameSettings(), new SaveData(new MapKeyValueStore()),
            MenuState.Screen.PLAYING);
        if (screen == MenuState.Screen.PAUSED) {
            menu.escape();
        } else if (screen == MenuState.Screen.DEAD) {
            menu.onPlayerDied();
        } else if (screen == MenuState.Screen.INTERMISSION) {
            menu.onStageCleared(new Player(1.5f, 1.5f, 0f, 100f, 0f), 30f, 5, 5, 0f);
        } else if (screen == MenuState.Screen.TITLE) {
            menu.escape();
            assertTrue(menu.selectId(MenuState.OptionId.RETURN_TO_TITLE));
            menu.confirm();
        }
        assertEquals(screen, menu.getScreen());
        return menu;
    }

    /** @return a menu on the Settings screen, opened from the pause menu, with the FIELD OF VIEW row selected */
    private static MenuState settingsMenuOnFov(GameSettings settings) {
        MenuState menu = new MenuState(settings, new SaveData(new MapKeyValueStore()), MenuState.Screen.PLAYING);
        menu.escape();
        assertTrue(menu.selectId(MenuState.OptionId.SETTINGS));
        menu.confirm();
        assertEquals(MenuState.Screen.SETTINGS, menu.getScreen());
        assertTrue(menu.selectId(MenuState.OptionId.SET_FOV));
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

    /** @return a window y coordinate inside the first row with the id on the current screen, at the centre x */
    private static int rowY(MenuState menu, MenuState.OptionId option) {
        List<MenuState.Option> options = menu.getMenuOptions();
        for (int index = 0; index < options.size(); index++) {
            if (options.get(index).id == option) return rowY(menu, index);
        }
        return fail("no row for " + option);
    }

    /** @return a window y coordinate inside the row at {@code index} on the current screen, at the centre x */
    private static int rowY(MenuState menu, int index) {
        for (int y = 0; y < 720; y++) {
            if (HudRenderer.optionAt(1280, 720, 640, y, menu) == index) return y + 2;
        }
        return fail("no row " + index);
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
        assertTrue(menu.selectId(MenuState.OptionId.SETTINGS));
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
            assertTrue(menu.selectId(MenuState.OptionId.QUIT));

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
            assertTrue(menu.selectId(MenuState.OptionId.RESUME));

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
            assertEquals(MenuState.OptionId.SET_FOV, menu.getSelectedOption().id);

            for (int frame = 1; frame <= 3; frame++) {
                String at = "minimised frame " + frame + " with " + Input.Keys.toString(key);
                assertEquals(MenuState.Command.NONE, pollMinimised(controller, FRAME), at);
                assertEquals(new GameSettings().getFov(), settings.getFov(), at);
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
            GameSettings expected = new GameSettings();
            expected.adjustFov(lowers ? -1 : 1);
            float adjusted = expected.getFov();
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
        mouseY = rowY(menu, MenuState.OptionId.RESUME);
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
        int y = rowY(menu, MenuState.OptionId.QUIT);

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
        assertTrue(menu.selectId(MenuState.OptionId.RESUME));

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
        click(controller, 640, rowY(menu, MenuState.OptionId.RESUME), Input.Buttons.LEFT);
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

    @Test
    void freshMouseButtonsFireAndChargeImmediatelyAfterKeyboardStartOrResume() {
        // Resuming from PAUSED via Esc with mouse buttons released must not swallow the first LMB/RMB press.
        MenuState menu = menuOn(MenuState.Screen.PAUSED);
        InputController controller = new InputController(menu, new GameSettings());
        assertEquals(MenuState.Command.NONE, poll(controller, FRAME, Input.Keys.ESCAPE));
        assertTrue(menu.isPlaying());

        leftHeld = true;
        rightHeld = true;
        poll(controller, FRAME);
        assertTrue(out.fire, "fresh LMB after keyboard resume fires on frame 1");
        assertTrue(out.charge, "fresh RMB after keyboard resume charges on frame 1");

        // Starting a controller directly on Screen.PLAYING with buttons initially up also fires on frame 1.
        leftHeld = false;
        rightHeld = false;
        MenuState directPlay = menuOn(MenuState.Screen.PLAYING);
        InputController directController = new InputController(directPlay, new GameSettings());
        leftHeld = true;
        rightHeld = true;
        poll(directController, FRAME);
        assertTrue(out.fire, "fresh LMB on initial PLAYING frame fires");
        assertTrue(out.charge, "fresh RMB on initial PLAYING frame charges");
    }

    @Test
    void rightButtonHeldDuringResumeIsSwallowedUntilReleased() {
        MenuState menu = menuOn(MenuState.Screen.PAUSED);
        InputController controller = new InputController(menu, new GameSettings());

        rightHeld = true;
        assertEquals(MenuState.Command.NONE, poll(controller, FRAME, Input.Keys.ESCAPE));
        assertTrue(menu.isPlaying());
        poll(controller, FRAME);
        assertFalse(out.charge, "RMB already held when play resumed must not start charging");

        rightHeld = false;
        poll(controller, FRAME);
        rightHeld = true;
        poll(controller, FRAME);
        assertTrue(out.charge, "RMB charges once released and pressed again");
    }

    @Test
    void quickRestartInPlayResetsHeldInputsLookDeltasAndScrollSwaps() {
        MenuState menu = menuOn(MenuState.Screen.PLAYING);
        InputController controller = new InputController(menu, new GameSettings());

        // Accumulate mouse movement, scroll wheel swap, and held Space/LMB/RMB on the restart frame.
        processorOf(controller).mouseMoved(100, 100);
        processorOf(controller).mouseMoved(160, 130);
        processorOf(controller).scrolled(0f, 1f);
        held.add(Input.Keys.SPACE);
        leftHeld = true;
        rightHeld = true;

        assertEquals(MenuState.Command.LAUNCH_STAGE, poll(controller, FRAME, Input.Keys.R));
        assertTrue(menu.isPlaying());

        // Frame 1 of the restarted stage with buttons/Space still held must not leak look, scroll, jump, fire, charge.
        processorOf(controller).mouseMoved(240, 200);
        poll(controller, FRAME);
        assertEquals(0f, out.yawDelta, 1e-6f, "pre-restart look X delta must be discarded");
        assertEquals(0f, out.pitchDelta, 1e-6f, "pre-restart look Y delta must be discarded");
        assertEquals(0, out.weaponSlot, "pre-restart scroll weapon swap must be discarded");
        assertFalse(out.jump, "held Space across R quick-restart must not jump at spawn");
        assertFalse(out.fire, "held LMB across R quick-restart must not fire at spawn");
        assertFalse(out.charge, "held RMB across R quick-restart must not charge at spawn");

        // Releasing and pressing fresh inputs works normally in the restarted stage.
        held.clear();
        leftHeld = false;
        rightHeld = false;
        poll(controller, FRAME);

        held.add(Input.Keys.SPACE);
        leftHeld = true;
        rightHeld = true;
        processorOf(controller).mouseMoved(250, 200);
        poll(controller, FRAME);
        assertTrue(out.jump);
        assertTrue(out.fire);
        assertTrue(out.charge);
        assertTrue(out.yawDelta > 0f);
    }

    @Test
    void quickRestartInPlayWithButtonsReleasedAllowsImmediateFreshFireAndJump() {
        MenuState menu = menuOn(MenuState.Screen.PLAYING);
        InputController controller = new InputController(menu, new GameSettings());

        assertEquals(MenuState.Command.LAUNCH_STAGE, poll(controller, FRAME, Input.Keys.R));
        held.add(Input.Keys.SPACE);
        leftHeld = true;
        rightHeld = true;
        poll(controller, FRAME);
        assertTrue(out.jump, "fresh Space on frame 1 after R quick-restart jumps");
        assertTrue(out.fire, "fresh LMB on frame 1 after R quick-restart fires");
        assertTrue(out.charge, "fresh RMB on frame 1 after R quick-restart charges");
    }

    @Test
    void gameplayKeyAndClickEventsInSameBatchBeforeWindowPauseDoNotActOnPauseMenu() {
        for (int key : new int[] {
            Input.Keys.SPACE, Input.Keys.ENTER, Input.Keys.ESCAPE, Input.Keys.R, Input.Keys.Q, Input.Keys.DOWN
        }) {
            MenuState menu = menuOn(MenuState.Screen.PLAYING);
            InputController controller = new InputController(menu, new GameSettings());

            // Simulate in-play events arriving in the same GLFW event batch right before focus loss / minimise.
            processorOf(controller).keyDown(key);
            click(controller, 640, 320, Input.Buttons.LEFT);
            controller.pause();
            assertEquals(MenuState.Screen.PAUSED, menu.getScreen());

            // Also click test directly at the RESUME row coordinates before pause on a second pass.
            MenuState menuClick = menuOn(MenuState.Screen.PLAYING);
            InputController clickController = new InputController(menuClick, new GameSettings());
            MenuState probePaused = menuOn(MenuState.Screen.PAUSED);
            click(clickController, 640, rowY(probePaused, MenuState.OptionId.RESUME), Input.Buttons.LEFT);
            clickController.pause();

            assertEquals(MenuState.Command.NONE, pollMinimised(controller, FRAME),
                "in-play key " + Input.Keys.toString(key) + " before pause must not act on PAUSED");
            assertEquals(MenuState.Screen.PAUSED, menu.getScreen());
            assertEquals(0, menu.getSelectedIndex());
            assertFalse(menu.isQuitPromptVisible());

            assertEquals(MenuState.Command.NONE, pollMinimised(clickController, FRAME),
                "in-play LMB click before pause must not click RESUME");
            assertEquals(MenuState.Screen.PAUSED, menuClick.getScreen());
        }
    }

    @Test
    void horizontalKeysOnNonSettingsMenusDoNotSwallowSameFrameConfirmOrMouseClick() {
        List<MenuState.Screen> nonSettingsMenus = List.of(
            MenuState.Screen.TITLE,
            MenuState.Screen.STAGE_SELECT,
            MenuState.Screen.DIFFICULTY_SELECT,
            MenuState.Screen.BRIEFING,
            MenuState.Screen.PAUSED,
            MenuState.Screen.DEAD,
            MenuState.Screen.INTERMISSION);
        int[] horizontalKeys = {Input.Keys.LEFT, Input.Keys.A, Input.Keys.RIGHT, Input.Keys.D};

        for (MenuState.Screen screen : nonSettingsMenus) {
            for (int hKey : horizontalKeys) {
                String context = screen + " + " + Input.Keys.toString(hKey);
                MenuState.Command expectedCmd = (screen == MenuState.Screen.BRIEFING || screen == MenuState.Screen.DEAD)
                    ? MenuState.Command.LAUNCH_STAGE
                    : MenuState.Command.NONE;

                // Same-frame Enter confirm with horizontal key pressed.
                MenuState enterMenu = menuOn(screen);
                InputController enterCtrl = new InputController(enterMenu, new GameSettings());
                assertEquals(expectedCmd, poll(enterCtrl, FRAME, hKey, Input.Keys.ENTER), context + " + ENTER");
                assertNotEquals(screen, enterMenu.getScreen(), context + " + ENTER must confirm");

                // Same-frame Space confirm with horizontal key pressed.
                MenuState spaceMenu = menuOn(screen);
                InputController spaceCtrl = new InputController(spaceMenu, new GameSettings());
                assertEquals(expectedCmd, poll(spaceCtrl, FRAME, hKey, Input.Keys.SPACE), context + " + SPACE");
                assertNotEquals(screen, spaceMenu.getScreen(), context + " + SPACE must confirm");

                // Same-frame left mouse click on default option row with horizontal key pressed.
                MenuState clickMenu = menuOn(screen);
                InputController clickCtrl = new InputController(clickMenu, new GameSettings());
                click(clickCtrl, 640, rowY(clickMenu, clickMenu.getSelectedIndex()), Input.Buttons.LEFT);
                assertEquals(expectedCmd, poll(clickCtrl, FRAME, hKey), context + " + click");
                assertNotEquals(screen, clickMenu.getScreen(), context + " + click must confirm");
            }
        }
    }

    @Test
    void opposingVerticalMenuKeysCancelOutWithoutMovingSelectionOrQueueingMoveSounds() {
        for (MenuState.Screen screen : List.of(
            MenuState.Screen.TITLE, MenuState.Screen.PAUSED, MenuState.Screen.SETTINGS)) {
            GameSettings settings = new GameSettings();
            MenuState menu = screen == MenuState.Screen.SETTINGS ? settingsMenuOnFov(settings) : menuOn(screen);
            InputController controller = new InputController(menu, settings);
            menu.setSelectedIndex(1);
            menu.drainSoundEvents();

            int[][] opposingPairs = {
                {Input.Keys.W, Input.Keys.S},
                {Input.Keys.UP, Input.Keys.DOWN},
                {Input.Keys.W, Input.Keys.DOWN},
                {Input.Keys.UP, Input.Keys.S},
                {Input.Keys.UP, Input.Keys.W, Input.Keys.DOWN, Input.Keys.S}
            };
            for (int[] keys : opposingPairs) {
                assertEquals(MenuState.Command.NONE, poll(controller, FRAME, keys), screen.name());
                assertEquals(1, menu.getSelectedIndex(), "opposing vertical keys cancel on " + screen);
                assertTrue(menu.drainSoundEvents().isEmpty(),
                    "opposing vertical keys must emit no MENU_MOVE sound on " + screen);
            }

            // Redundant same-direction keys (UP + W or DOWN + S) step once and emit exactly one MENU_MOVE sound.
            assertEquals(MenuState.Command.NONE, poll(controller, FRAME, Input.Keys.UP, Input.Keys.W));
            assertEquals(0, menu.getSelectedIndex());
            List<SoundEvent> upEvents = menu.drainSoundEvents();
            assertEquals(1, upEvents.size());
            assertEquals(SoundEvent.Type.MENU_MOVE, upEvents.get(0).type);

            assertEquals(MenuState.Command.NONE, poll(controller, FRAME, Input.Keys.DOWN, Input.Keys.S));
            assertEquals(1, menu.getSelectedIndex());
            List<SoundEvent> downEvents = menu.drainSoundEvents();
            assertEquals(1, downEvents.size());
            assertEquals(SoundEvent.Type.MENU_MOVE, downEvents.get(0).type);
        }
    }

    @Test
    void aClickOnASettingsRowChangesItsValueAndAClickOnAnyOtherRowConfirmsIt() {
        int rows = settingsMenuOnFov(new GameSettings()).getMenuOptions().size();
        int settingRows = 0;
        for (int row = 0; row < rows; row++) {
            GameSettings settings = new GameSettings();
            MenuState menu = settingsMenuOnFov(settings);
            InputController controller = new InputController(menu, settings);
            MenuState.Option option = menu.getMenuOptions().get(row);
            // Left of the centre: one step down for a slider, the other state for a switch.
            click(controller, 500, rowY(menu, row), Input.Buttons.LEFT);
            MenuState.Command command = poll(controller, FRAME);
            if (option.id.isSetting()) {
                settingRows++;
                assertEquals(MenuState.Screen.SETTINGS, menu.getScreen(), option.id + " stays on Settings");
                assertEquals(option.id, menu.getSelectedOption().id);
                assertNotEquals(option.label, menu.getSelectedOption().label, option.id + " shows a new value");
                // A click steps the way its side of the row says; confirming the row would step up.
                GameSettings start = new GameSettings();
                assertTrue(settings.getMouseSensitivity() <= start.getMouseSensitivity(), option.id.name());
                assertTrue(settings.getFov() <= start.getFov(), option.id.name());
                assertTrue(settings.getMasterVolume() <= start.getMasterVolume(), option.id.name());
                assertEquals(option.id == MenuState.OptionId.SET_FULLSCREEN
                    ? MenuState.Command.APPLY_DISPLAY : MenuState.Command.NONE, command, option.id.name());
            } else {
                assertEquals(MenuState.OptionId.BACK, option.id, "the one row of Settings that is no setting");
                assertEquals(MenuState.Command.NONE, command);
                assertEquals(MenuState.Screen.PAUSED, menu.getScreen(), "a click on BACK leaves Settings");
            }
        }
        assertEquals(7, settingRows);
    }

    @Test
    void opposingHorizontalSettingsKeysCancelOutWithoutSteppingSlidersFlippingTogglesOrSwallowingConfirm() {
        GameSettings settings = new GameSettings();
        MenuState menu = settingsMenuOnFov(settings);
        InputController controller = new InputController(menu, settings);
        menu.drainSoundEvents();

        int[][] opposingHorizontal = {
            {Input.Keys.A, Input.Keys.D},
            {Input.Keys.LEFT, Input.Keys.RIGHT},
            {Input.Keys.A, Input.Keys.RIGHT},
            {Input.Keys.LEFT, Input.Keys.D}
        };
        for (int[] keys : opposingHorizontal) {
            assertEquals(MenuState.Command.NONE, poll(controller, FRAME, keys));
            assertEquals(new GameSettings().getFov(), settings.getFov(), 1e-6f,
                "opposing horizontal keys must not step FOV");
            assertTrue(menu.drainSoundEvents().isEmpty(),
                "opposing horizontal keys must emit no sound");
        }

        // Boolean toggle rows (such as FULLSCREEN) must neither flip nor emit APPLY_DISPLAY.
        assertTrue(menu.selectId(MenuState.OptionId.SET_FULLSCREEN));
        menu.drainSoundEvents();
        boolean initialFullscreen = settings.isFullscreen();
        for (int[] keys : opposingHorizontal) {
            assertEquals(MenuState.Command.NONE, poll(controller, FRAME, keys));
            assertEquals(initialFullscreen, settings.isFullscreen(),
                "opposing horizontal keys must not flip FULLSCREEN");
            assertTrue(menu.drainSoundEvents().isEmpty());
        }

        // Cancelled A + D falls through cleanly to same-frame Enter confirm or mouse click on BACK.
        assertTrue(menu.selectId(MenuState.OptionId.BACK));
        menu.drainSoundEvents();
        assertEquals(MenuState.Command.NONE, poll(controller, FRAME, Input.Keys.A, Input.Keys.D, Input.Keys.ENTER));
        assertEquals(MenuState.Screen.PAUSED, menu.getScreen(),
            "cancelled A+D on Settings must fall through to same-frame Enter");

        MenuState clickSettings = settingsMenuOnFov(settings);
        InputController clickCtrl = new InputController(clickSettings, settings);
        click(clickCtrl, 640, rowY(clickSettings, MenuState.OptionId.BACK), Input.Buttons.LEFT);
        assertEquals(MenuState.Command.NONE, poll(clickCtrl, FRAME, Input.Keys.LEFT, Input.Keys.RIGHT));
        assertEquals(MenuState.Screen.PAUSED, clickSettings.getScreen(),
            "cancelled Left+Right on Settings must fall through to same-frame mouse click");
    }

    @Test
    void scrollCallbacksAccumulateNetVerticalDeltaPerFrameAndCancelOpposingScrolls() {
        MenuState menu = menuOn(MenuState.Screen.PLAYING);
        InputController controller = new InputController(menu, new GameSettings());

        // Single positive and negative scroll detents map to NEXT and PREV once, then reset.
        assertTrue(processorOf(controller).scrolled(0f, 1f));
        poll(controller, FRAME);
        assertEquals(PlayerInput.WEAPON_CYCLE_NEXT, out.weaponSlot);
        poll(controller, FRAME);
        assertEquals(PlayerInput.WEAPON_NONE, out.weaponSlot);

        assertTrue(processorOf(controller).scrolled(0f, -1f));
        poll(controller, FRAME);
        assertEquals(PlayerInput.WEAPON_CYCLE_PREV, out.weaponSlot);
        poll(controller, FRAME);
        assertEquals(PlayerInput.WEAPON_NONE, out.weaponSlot);

        // Opposing callbacks in the same frame (+1 then -1, -1 then +1, and fractional sums) cancel to WEAPON_NONE.
        assertTrue(processorOf(controller).scrolled(0f, 1f));
        assertTrue(processorOf(controller).scrolled(0f, -1f));
        poll(controller, FRAME);
        assertEquals(PlayerInput.WEAPON_NONE, out.weaponSlot, "+1f followed by -1f must cancel");

        assertTrue(processorOf(controller).scrolled(0f, -1f));
        assertTrue(processorOf(controller).scrolled(0f, 1f));
        poll(controller, FRAME);
        assertEquals(PlayerInput.WEAPON_NONE, out.weaponSlot, "-1f followed by +1f must cancel");

        assertTrue(processorOf(controller).scrolled(0f, 0.1f));
        assertTrue(processorOf(controller).scrolled(0f, 0.2f));
        assertTrue(processorOf(controller).scrolled(0f, -0.3f));
        poll(controller, FRAME);
        assertEquals(PlayerInput.WEAPON_NONE, out.weaponSlot, "opposing fractional scroll callbacks must cancel");

        // Net sign across multiple callbacks wins rather than the last callback's direction.
        assertTrue(processorOf(controller).scrolled(0f, 1f));
        assertTrue(processorOf(controller).scrolled(0f, 1f));
        assertTrue(processorOf(controller).scrolled(0f, -1f));
        poll(controller, FRAME);
        assertEquals(PlayerInput.WEAPON_CYCLE_NEXT, out.weaponSlot, "+1, +1, -1 has net +1 -> NEXT");

        assertTrue(processorOf(controller).scrolled(0f, -1f));
        assertTrue(processorOf(controller).scrolled(0f, -1f));
        assertTrue(processorOf(controller).scrolled(0f, 1f));
        poll(controller, FRAME);
        assertEquals(PlayerInput.WEAPON_CYCLE_PREV, out.weaponSlot, "-1, -1, +1 has net -1 -> PREV");

        // Zero, horizontal-only, and non-finite scroll values are ignored without corrupting accumulated scroll.
        assertFalse(processorOf(controller).scrolled(2f, 0f));
        assertFalse(processorOf(controller).scrolled(0f, -0f));
        assertFalse(processorOf(controller).scrolled(0f, Float.NaN));
        assertFalse(processorOf(controller).scrolled(0f, Float.POSITIVE_INFINITY));
        assertFalse(processorOf(controller).scrolled(0f, Float.NEGATIVE_INFINITY));
        assertTrue(processorOf(controller).scrolled(0f, 0.5f));
        assertFalse(processorOf(controller).scrolled(0f, Float.NaN));
        assertFalse(processorOf(controller).scrolled(0f, Float.NEGATIVE_INFINITY));
        poll(controller, FRAME);
        assertEquals(PlayerInput.WEAPON_CYCLE_NEXT, out.weaponSlot);
    }

    @Test
    void accumulatedScrollResetsOnWindowPauseEscapeMenuAndDirectWeaponKeys() {
        // Uncaptured scroll on menus returns false and does not leak on resume.
        MenuState paused = menuOn(MenuState.Screen.PAUSED);
        InputController pausedCtrl = new InputController(paused, new GameSettings());
        assertFalse(processorOf(pausedCtrl).scrolled(0f, 1f));
        poll(pausedCtrl, FRAME, Input.Keys.ESCAPE);
        assertTrue(paused.isPlaying());
        poll(pausedCtrl, FRAME);
        assertEquals(PlayerInput.WEAPON_NONE, out.weaponSlot);

        // Scroll accumulated in play before window pause() is cleared.
        MenuState playing = menuOn(MenuState.Screen.PLAYING);
        InputController ctrl = new InputController(playing, new GameSettings());
        assertTrue(processorOf(ctrl).scrolled(0f, 1f));
        ctrl.pause();
        assertEquals(MenuState.Screen.PAUSED, playing.getScreen());
        poll(ctrl, FRAME, Input.Keys.ESCAPE);
        assertTrue(playing.isPlaying());
        poll(ctrl, FRAME);
        assertEquals(PlayerInput.WEAPON_NONE, out.weaponSlot, "scroll before pause() must be discarded");

        // Scroll accumulated in the same frame as pressing Escape to pause is cleared on capture release.
        assertTrue(processorOf(ctrl).scrolled(0f, -1f));
        poll(ctrl, FRAME, Input.Keys.ESCAPE);
        assertEquals(MenuState.Screen.PAUSED, playing.getScreen());
        poll(ctrl, FRAME, Input.Keys.ESCAPE);
        assertTrue(playing.isPlaying());
        poll(ctrl, FRAME);
        assertEquals(PlayerInput.WEAPON_NONE, out.weaponSlot, "scroll on Escape pause frame must be discarded");

        // Direct weapon key (NUM_2) takes priority and still clears pendingScrollY for subsequent frames.
        assertTrue(processorOf(ctrl).scrolled(0f, 1f));
        poll(ctrl, FRAME, Input.Keys.NUM_2);
        assertEquals(2, out.weaponSlot);
        poll(ctrl, FRAME);
        assertEquals(PlayerInput.WEAPON_NONE, out.weaponSlot, "scroll superseded by NUM_2 must not fire next frame");

        // Direct resetCaptureForStageLaunch() clears pendingScrollY even while already captured.
        assertTrue(processorOf(ctrl).scrolled(0f, -1f));
        ctrl.resetCaptureForStageLaunch();
        poll(ctrl, FRAME);
        assertEquals(PlayerInput.WEAPON_NONE, out.weaponSlot);
    }

    @Test
    void minimapKeyTogglesPersistentSettingDuringPlayWithoutDroppingOtherInputs() {
        GameSettings settings = new GameSettings();
        assertTrue(settings.isMinimap());
        MenuState playing = new MenuState(settings, new SaveData(new MapKeyValueStore()), MenuState.Screen.PLAYING);
        InputController controller = new InputController(playing, settings);

        held.add(Input.Keys.W);
        leftHeld = true;
        assertEquals(MenuState.Command.SAVE_SETTINGS, poll(controller, FRAME, Input.Keys.M));
        assertFalse(settings.isMinimap());
        assertEquals(1f, out.moveForward, 0f, "movement still applies on the minimap toggle frame");
        assertTrue(out.fire, "fire still applies on the minimap toggle frame");
        List<io.github.brantunger.gamex.audio.SoundEvent> sounds = playing.drainSoundEvents();
        assertEquals(1, sounds.size());
        assertEquals(io.github.brantunger.gamex.audio.SoundEvent.Type.MENU_MOVE, sounds.get(0).type);

        held.clear();
        leftHeld = false;
        assertEquals(MenuState.Command.NONE, poll(controller, FRAME));
        assertFalse(settings.isMinimap());

        assertEquals(MenuState.Command.SAVE_SETTINGS, poll(controller, FRAME, Input.Keys.M));
        assertTrue(settings.isMinimap());
        sounds = playing.drainSoundEvents();
        assertEquals(1, sounds.size());
        assertEquals(io.github.brantunger.gamex.audio.SoundEvent.Type.MENU_MOVE, sounds.get(0).type);

        // Does not toggle when paused, on title, or when minimized.
        playing.escape();
        playing.drainSoundEvents();
        assertEquals(MenuState.Screen.PAUSED, playing.getScreen());
        assertEquals(MenuState.Command.NONE, poll(controller, FRAME, Input.Keys.M));
        assertTrue(settings.isMinimap());
        assertTrue(playing.drainSoundEvents().isEmpty());

        MenuState playingMin = menuOn(MenuState.Screen.PLAYING);
        GameSettings minSettings = new GameSettings();
        InputController minController = new InputController(playingMin, minSettings);
        minimiseWithKeyFlagged(minController, Input.Keys.M);
        for (int frame = 0; frame < 3; frame++) {
            assertEquals(MenuState.Command.NONE, pollMinimised(minController, FRAME));
            assertTrue(minSettings.isMinimap());
        }
    }
}

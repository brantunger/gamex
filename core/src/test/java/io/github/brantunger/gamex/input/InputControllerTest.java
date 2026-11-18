package io.github.brantunger.gamex.input;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.badlogic.gdx.Gdx;
import com.badlogic.gdx.Graphics;
import com.badlogic.gdx.Input;
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

    private Input previousInput;
    private Graphics previousGraphics;
    /** Keys reported as just pressed on the next poll. */
    private final Set<Integer> justPressed = new HashSet<>();
    /** The frame time the stub graphics reports. */
    private float deltaTime = FRAME;
    private final PlayerInput out = new PlayerInput();

    @BeforeEach
    void installStubs() {
        previousInput = Gdx.input;
        previousGraphics = Gdx.graphics;
        Gdx.input = (Input) Proxy.newProxyInstance(
            Input.class.getClassLoader(),
            new Class<?>[] {Input.class},
            (proxy, method, args) -> {
                if ("isKeyJustPressed".equals(method.getName())) return justPressed.contains((Integer) args[0]);
                return defaultValue(method.getReturnType());
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

    /** Polls one frame of {@code seconds} with {@code keys} just pressed. */
    private MenuState.Command poll(InputController controller, float seconds, int... keys) {
        justPressed.clear();
        for (int key : keys) justPressed.add(key);
        deltaTime = seconds;
        return controller.poll(out);
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
}

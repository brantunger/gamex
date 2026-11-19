package io.github.brantunger.gamex.input;

import com.badlogic.gdx.Gdx;
import com.badlogic.gdx.Input;
import com.badlogic.gdx.InputAdapter;
import com.badlogic.gdx.utils.IntSet;
import io.github.brantunger.gamex.hud.HudRenderer;
import io.github.brantunger.gamex.hud.MenuState;
import io.github.brantunger.gamex.player.PlayerInput;
import io.github.brantunger.gamex.settings.GameSettings;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Reads the keyboard and mouse for both active gameplay ({@link PlayerInput}) and menu navigation ({@link MenuState}).
 * Captures the cursor automatically while playing and frees it whenever a menu, death or intermission screen is up.
 *
 * <p>Menu actions are taken from the key-down and button-down events, not from the polled just-pressed flags. The
 * desktop backend keeps rendering a minimised window without clearing those flags, so a polled key pressed as the
 * window is minimised would repeat its action on every frame; an event is delivered once per press.
 */
public final class InputController extends InputAdapter {

    private static final Logger log = LoggerFactory.getLogger(InputController.class);

    private final LookAccumulator look = new LookAccumulator();
    private final MenuState menuState;
    private final GameSettings settings;
    private boolean captured;
    /** Set while a button that was down when gameplay resumed is still held, so that it does not fire a shot. */
    private boolean swallowFire;
    /** The same for the blast button, so that it does not start a charge. */
    private boolean swallowCharge;
    /** The same for Space, so confirming DEPLOY or RESUME does not jump on the first frame of play. */
    private boolean swallowJump;
    private static final float SCROLL_EPSILON = 1e-4f;
    /** Net vertical scroll wheel / trackpad delta accumulated since the last polled gameplay frame. */
    private float pendingScrollY;
    /** Keys whose key-down event arrived since the last poll; menu screens act on these. */
    private final IntSet pressedKeys = new IntSet();
    /** Set when a left-button-down event arrived since the last poll, at ({@link #clickX}, {@link #clickY}). */
    private boolean clickPending;
    private int clickX;
    private int clickY;

    /**
     * @param menuState screen & menu state machine
     * @param settings live player settings (sensitivity, invert Y)
     */
    public InputController(MenuState menuState, GameSettings settings) {
        this.menuState = menuState;
        this.settings = settings;
        Gdx.input.setInputProcessor(this);
        syncCursorCapture();
    }

    /** Releases the cursor and pauses active gameplay when the window loses focus or is minimised. */
    public void pause() {
        clearPresses();
        pendingScrollY = 0f;
        menuState.onWindowPaused();
        syncCursorCapture();
    }

    @Override
    public boolean keyDown(int keycode) {
        if (!menuState.isPlaying()) {
            pressedKeys.add(keycode);
        }
        return true;
    }

    @Override
    public boolean touchDown(int screenX, int screenY, int pointer, int button) {
        if (!menuState.isPlaying() && button == Input.Buttons.LEFT) {
            clickPending = true;
            clickX = screenX;
            clickY = screenY;
        }
        return true;
    }

    @Override
    public boolean mouseMoved(int screenX, int screenY) {
        if (captured) {
            look.moved(screenX, screenY);
        } else {
            int hovered = HudRenderer.optionAt(Gdx.graphics.getWidth(), Gdx.graphics.getHeight(), screenX, screenY,
                menuState);
            if (hovered >= 0) menuState.setSelectedIndex(hovered);
        }
        return true;
    }

    @Override
    public boolean touchDragged(int screenX, int screenY, int pointer) {
        if (captured) look.moved(screenX, screenY);
        return true;
    }

    @Override
    public boolean scrolled(float amountX, float amountY) {
        if (captured && Float.isFinite(amountY) && amountY != 0f) {
            pendingScrollY += amountY;
            return true;
        }
        return false;
    }

    /**
     * Polls devices for this frame, advancing either menu navigation or in-game {@code out} controls.
     * @param out player input record to populate; cleared when not in active play
     * @return any high-level command triggered by menu/key input this frame
     */
    public MenuState.Command poll(PlayerInput out) {
        out.clear();
        syncCursorCapture();

        if (!menuState.isPlaying()) {
            pendingScrollY = 0f;
            MenuState.Command cmd = pollMenu();
            clearPresses();
            if (cmd == MenuState.Command.LAUNCH_STAGE) {
                resetCaptureForStageLaunch();
            } else {
                syncCursorCapture();
            }
            return cmd;
        }
        // A press made during play must not act on a menu that opens later.
        clearPresses();

        if (Gdx.input.isKeyJustPressed(Input.Keys.ESCAPE)) {
            MenuState.Command cmd = menuState.escape();
            syncCursorCapture();
            return cmd;
        }
        if (Gdx.input.isKeyJustPressed(Input.Keys.R)) {
            MenuState.Command cmd = menuState.quickRestart();
            if (cmd == MenuState.Command.LAUNCH_STAGE) {
                resetCaptureForStageLaunch();
            } else {
                syncCursorCapture();
            }
            return cmd;
        }

        float sensitivity = settings.getMouseSensitivity();
        float invertSign = settings.isInvertY() ? 1f : -1f;
        out.yawDelta = look.consumeX() * sensitivity;
        out.pitchDelta = look.consumeY() * sensitivity * invertSign;

        if (Gdx.input.isKeyPressed(Input.Keys.W)) out.moveForward += 1f;
        if (Gdx.input.isKeyPressed(Input.Keys.S)) out.moveForward -= 1f;
        if (Gdx.input.isKeyPressed(Input.Keys.D)) out.moveStrafe += 1f;
        if (Gdx.input.isKeyPressed(Input.Keys.A)) out.moveStrafe -= 1f;

        out.dash = Gdx.input.isKeyJustPressed(Input.Keys.SHIFT_LEFT)
            || Gdx.input.isKeyJustPressed(Input.Keys.SHIFT_RIGHT);
        boolean spaceDown = Gdx.input.isKeyPressed(Input.Keys.SPACE);
        if (!spaceDown) swallowJump = false;
        out.jump = spaceDown && !swallowJump;
        out.interact = Gdx.input.isKeyJustPressed(Input.Keys.E);

        if (Gdx.input.isKeyJustPressed(Input.Keys.NUM_1)) out.weaponSlot = 1;
        else if (Gdx.input.isKeyJustPressed(Input.Keys.NUM_2)) out.weaponSlot = 2;
        else if (Gdx.input.isKeyJustPressed(Input.Keys.NUM_3)) out.weaponSlot = 3;
        else if (Gdx.input.isKeyJustPressed(Input.Keys.Q)) out.weaponSlot = PlayerInput.WEAPON_QUICK_SWAP;
        else if (Math.abs(pendingScrollY) > SCROLL_EPSILON) {
            out.weaponSlot = pendingScrollY > 0f ? PlayerInput.WEAPON_CYCLE_NEXT : PlayerInput.WEAPON_CYCLE_PREV;
        }
        pendingScrollY = 0f;

        boolean leftButton = Gdx.input.isButtonPressed(Input.Buttons.LEFT);
        boolean rightButton = Gdx.input.isButtonPressed(Input.Buttons.RIGHT);
        if (!leftButton) swallowFire = false;
        if (!rightButton) swallowCharge = false;
        out.fire = leftButton && !swallowFire;
        out.charge = rightButton && !swallowCharge;
        return MenuState.Command.NONE;
    }

    /** Forgets the key and button presses collected for the menus since the last poll. */
    private void clearPresses() {
        pressedKeys.clear();
        clickPending = false;
    }

    /** Advances the menu by one frame, acting once on each press delivered as an event since the last poll. */
    private MenuState.Command pollMenu() {
        menuState.update(Gdx.graphics.getDeltaTime());
        if (pressedKeys.contains(Input.Keys.ESCAPE)) {
            return menuState.escape();
        }
        if ((menuState.getScreen() == MenuState.Screen.DEAD || menuState.getScreen() == MenuState.Screen.PAUSED)
            && pressedKeys.contains(Input.Keys.R)) {
            return menuState.quickRestart();
        }
        // Q quits only on a second press, so one stray key does not close the game.
        if ((menuState.getScreen() == MenuState.Screen.PAUSED || menuState.getScreen() == MenuState.Screen.TITLE)
            && pressedKeys.contains(Input.Keys.Q)) {
            return menuState.requestQuit();
        }
        boolean up = pressedKeys.contains(Input.Keys.UP) || pressedKeys.contains(Input.Keys.W);
        boolean down = pressedKeys.contains(Input.Keys.DOWN) || pressedKeys.contains(Input.Keys.S);
        int vStep = (up ? -1 : 0) + (down ? 1 : 0);
        if (vStep != 0) {
            menuState.moveSelection(vStep);
        }
        if (menuState.getScreen() == MenuState.Screen.SETTINGS) {
            boolean left = pressedKeys.contains(Input.Keys.LEFT) || pressedKeys.contains(Input.Keys.A);
            boolean right = pressedKeys.contains(Input.Keys.RIGHT) || pressedKeys.contains(Input.Keys.D);
            int hStep = (left ? -1 : 0) + (right ? 1 : 0);
            if (hStep != 0) {
                return menuState.adjustHorizontal(hStep);
            }
        }
        if (pressedKeys.contains(Input.Keys.ENTER) || pressedKeys.contains(Input.Keys.SPACE)) {
            return menuState.confirm();
        }
        if (clickPending) {
            int sx = clickX;
            int sy = clickY;
            int clicked = HudRenderer.optionAt(Gdx.graphics.getWidth(), Gdx.graphics.getHeight(), sx, sy, menuState);
            if (clicked >= 0) {
                menuState.setSelectedIndex(clicked);
                if (menuState.getScreen() == MenuState.Screen.SETTINGS
                    && clicked < menuState.getOptions().size() - 1) {
                    return menuState.adjustHorizontal(HudRenderer.horizontalStepForClick(Gdx.graphics.getWidth(), sx));
                }
                return menuState.confirm();
            }
        }
        return MenuState.Command.NONE;
    }

    /**
     * Synchronises cursor capture and resets accumulated look deltas, scroll weapon swaps, menu presses and held-input
     * swallow latches for a newly launched or quick-restarted stage, even when the cursor was already captured.
     */
    public void resetCaptureForStageLaunch() {
        syncCursorCapture();
        clearPresses();
        pendingScrollY = 0f;
        if (captured) {
            look.capture();
            refreshHeldSwallows();
        }
    }

    /** Ensures OS cursor capture always matches whether {@link MenuState#isPlaying()} is active. */
    public void syncCursorCapture() {
        boolean shouldCapture = menuState.isPlaying();
        if (shouldCapture == captured) return;
        captured = shouldCapture;
        log.debug("Cursor {}", captured ? "captured for play" : "released for the menus");
        Gdx.input.setCursorCatched(captured);
        if (captured) {
            look.capture();
            refreshHeldSwallows();
            pendingScrollY = 0f;
        } else {
            look.release();
            swallowFire = false;
            swallowCharge = false;
            swallowJump = false;
            pendingScrollY = 0f;
        }
    }

    private void refreshHeldSwallows() {
        swallowFire = Gdx.input.isButtonPressed(Input.Buttons.LEFT);
        swallowCharge = Gdx.input.isButtonPressed(Input.Buttons.RIGHT);
        swallowJump = Gdx.input.isKeyPressed(Input.Keys.SPACE);
    }
}

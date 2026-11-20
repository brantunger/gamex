package io.github.brantunger.gamex.input;

import com.badlogic.gdx.Gdx;
import com.badlogic.gdx.InputAdapter;
import com.badlogic.gdx.utils.IntSet;
import io.github.brantunger.gamex.hud.HudRenderer;
import io.github.brantunger.gamex.hud.MenuState;
import io.github.brantunger.gamex.player.PlayerInput;
import io.github.brantunger.gamex.settings.GameSettings;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.List;

/**
 * Reads the keyboard and mouse for both active gameplay ({@link PlayerInput}) and menu navigation ({@link MenuState}).
 * Captures the cursor automatically while playing and frees it whenever a menu, death or intermission screen is up.
 * Which key, mouse button or wheel direction does what comes from the {@link KeyBindings}; this class names no key.
 * It decides what each {@link Action} does and when: movement, jump, fire and alt fire act while an input is held,
 * the other actions of play once per press, and a turn of the wheel counts as a press that lasts one frame.
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
    private final KeyBindings bindings;
    private boolean captured;
    /** Set while a fire input that was down when gameplay resumed is still held, so that it does not fire a shot. */
    private boolean swallowFire;
    /** The same for the alt fire inputs, so that they do not start a charge. */
    private boolean swallowCharge;
    /** The same for the jump inputs, so confirming DEPLOY or RESUME does not jump on the first frame of play. */
    private boolean swallowJump;
    private static final float SCROLL_EPSILON = 1e-4f;
    /** Net vertical scroll wheel / trackpad delta accumulated since the last polled gameplay frame. */
    private float pendingScrollY;
    /** Keys whose key-down event arrived since the last poll; menu screens act on these. */
    private final IntSet pressedKeys = new IntSet();
    /** The same for the mouse buttons whose button-down event arrived. */
    private final IntSet pressedButtons = new IntSet();
    /**
     * Set when the button-down event of a {@link Action#MENU_CLICK} button arrived since the last poll, at
     * ({@link #clickX}, {@link #clickY}).
     */
    private boolean clickPending;
    private int clickX;
    private int clickY;

    /**
     * @param menuState screen & menu state machine
     * @param settings live player settings (sensitivity, invert Y)
     * @param bindings the inputs of every action, as loaded at start-up
     */
    public InputController(MenuState menuState, GameSettings settings, KeyBindings bindings) {
        this.menuState = menuState;
        this.settings = settings;
        this.bindings = bindings;
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
        if (!menuState.isPlaying()) {
            pressedButtons.add(button);
            if (isBound(Action.MENU_CLICK, BoundInput.Kind.BUTTON, button)) {
                clickPending = true;
                clickX = screenX;
                clickY = screenY;
            }
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

        if (justPressed(Action.PAUSE)) {
            MenuState.Command cmd = menuState.escape();
            syncCursorCapture();
            return cmd;
        }
        if (justPressed(Action.RETRY)) {
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

        if (held(Action.MOVE_FORWARD)) out.moveForward += 1f;
        if (held(Action.MOVE_BACK)) out.moveForward -= 1f;
        if (held(Action.MOVE_RIGHT)) out.moveStrafe += 1f;
        if (held(Action.MOVE_LEFT)) out.moveStrafe -= 1f;

        out.dash = justPressed(Action.DASH);
        boolean jumpDown = held(Action.JUMP);
        if (!jumpDown) swallowJump = false;
        out.jump = jumpDown && !swallowJump;
        out.interact = justPressed(Action.INTERACT);

        if (justPressed(Action.WEAPON_1)) out.weaponSlot = 1;
        else if (justPressed(Action.WEAPON_2)) out.weaponSlot = 2;
        else if (justPressed(Action.WEAPON_3)) out.weaponSlot = 3;
        else if (justPressed(Action.QUICK_SWAP)) out.weaponSlot = PlayerInput.WEAPON_QUICK_SWAP;
        else if (justPressed(Action.WEAPON_NEXT)) out.weaponSlot = PlayerInput.WEAPON_CYCLE_NEXT;
        else if (justPressed(Action.WEAPON_PREV)) out.weaponSlot = PlayerInput.WEAPON_CYCLE_PREV;

        boolean fireDown = held(Action.FIRE);
        boolean altFireDown = held(Action.ALT_FIRE);
        if (!fireDown) swallowFire = false;
        if (!altFireDown) swallowCharge = false;
        out.fire = fireDown && !swallowFire;
        out.charge = altFireDown && !swallowCharge;
        // Every action above has read the wheel, so its turn ends with this frame.
        pendingScrollY = 0f;
        return MenuState.Command.NONE;
    }

    /**
     * @param action an action of play
     * @return whether one of its inputs is down, or is a wheel direction turned since the last frame of play
     */
    private boolean held(Action action) {
        List<BoundInput> inputs = bindings.inputs(action);
        for (int i = 0; i < inputs.size(); i++) {
            BoundInput input = inputs.get(i);
            switch (input.kind) {
                case KEY:
                    if (Gdx.input.isKeyPressed(input.code)) return true;
                    break;
                case BUTTON:
                    if (Gdx.input.isButtonPressed(input.code)) return true;
                    break;
                default:
                    if (wheelTurned(input.code)) return true;
                    break;
            }
        }
        return false;
    }

    /**
     * @param action an action of play
     * @return whether one of its inputs went down this frame, or is a wheel direction turned since the last frame
     *     of play
     */
    private boolean justPressed(Action action) {
        List<BoundInput> inputs = bindings.inputs(action);
        for (int i = 0; i < inputs.size(); i++) {
            BoundInput input = inputs.get(i);
            switch (input.kind) {
                case KEY:
                    if (Gdx.input.isKeyJustPressed(input.code)) return true;
                    break;
                case BUTTON:
                    if (Gdx.input.isButtonJustPressed(input.code)) return true;
                    break;
                default:
                    if (wheelTurned(input.code)) return true;
                    break;
            }
        }
        return false;
    }

    /**
     * @param direction {@link BoundInput#WHEEL_UP} or {@link BoundInput#WHEEL_DOWN}
     * @return whether the net turn of the wheel since the last frame of play is in that direction
     */
    private boolean wheelTurned(int direction) {
        return Math.abs(pendingScrollY) > SCROLL_EPSILON && (pendingScrollY > 0f) == (direction > 0);
    }

    /**
     * @param action an action of the menu screens
     * @return whether the key-down or button-down event of one of its inputs arrived since the last poll
     */
    private boolean pressed(Action action) {
        List<BoundInput> inputs = bindings.inputs(action);
        for (int i = 0; i < inputs.size(); i++) {
            BoundInput input = inputs.get(i);
            if (input.kind == BoundInput.Kind.KEY && pressedKeys.contains(input.code)) return true;
            if (input.kind == BoundInput.Kind.BUTTON && pressedButtons.contains(input.code)) return true;
        }
        return false;
    }

    /** @return whether {@code action} has the input of that kind and code among its inputs */
    private boolean isBound(Action action, BoundInput.Kind kind, int code) {
        List<BoundInput> inputs = bindings.inputs(action);
        for (int i = 0; i < inputs.size(); i++) {
            BoundInput input = inputs.get(i);
            if (input.kind == kind && input.code == code) return true;
        }
        return false;
    }

    /** Forgets the key and button presses collected for the menus since the last poll. */
    private void clearPresses() {
        pressedKeys.clear();
        pressedButtons.clear();
        clickPending = false;
    }

    /** Advances the menu by one frame, acting once on each press delivered as an event since the last poll. */
    private MenuState.Command pollMenu() {
        menuState.update(Gdx.graphics.getDeltaTime());
        if (pressed(Action.PAUSE)) {
            return menuState.escape();
        }
        if ((menuState.getScreen() == MenuState.Screen.DEAD || menuState.getScreen() == MenuState.Screen.PAUSED)
            && pressed(Action.RETRY)) {
            return menuState.quickRestart();
        }
        // Quit acts only on a second press, so one stray key does not close the game.
        if ((menuState.getScreen() == MenuState.Screen.PAUSED || menuState.getScreen() == MenuState.Screen.TITLE)
            && pressed(Action.QUIT)) {
            return menuState.requestQuit();
        }
        boolean up = pressed(Action.MENU_UP);
        boolean down = pressed(Action.MENU_DOWN);
        int vStep = (up ? -1 : 0) + (down ? 1 : 0);
        if (vStep != 0) {
            menuState.moveSelection(vStep);
        }
        if (menuState.getScreen() == MenuState.Screen.SETTINGS) {
            boolean left = pressed(Action.MENU_LEFT);
            boolean right = pressed(Action.MENU_RIGHT);
            int hStep = (left ? -1 : 0) + (right ? 1 : 0);
            if (hStep != 0) {
                return menuState.adjustHorizontal(hStep);
            }
        }
        if (pressed(Action.MENU_CONFIRM)) {
            return menuState.confirm();
        }
        if (clickPending) {
            int sx = clickX;
            int sy = clickY;
            int clicked = HudRenderer.optionAt(Gdx.graphics.getWidth(), Gdx.graphics.getHeight(), sx, sy, menuState);
            if (clicked >= 0) {
                menuState.setSelectedIndex(clicked);
                if (menuState.getMenuOptions().get(clicked).id.isSetting()) {
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
        swallowFire = held(Action.FIRE);
        swallowCharge = held(Action.ALT_FIRE);
        swallowJump = held(Action.JUMP);
    }
}

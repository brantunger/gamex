package io.github.brantunger.gamex.input;

import com.badlogic.gdx.Gdx;
import com.badlogic.gdx.Input;
import com.badlogic.gdx.InputAdapter;
import io.github.brantunger.gamex.hud.HudRenderer;
import io.github.brantunger.gamex.hud.MenuState;
import io.github.brantunger.gamex.player.PlayerInput;
import io.github.brantunger.gamex.settings.GameSettings;

/**
 * Reads the keyboard and mouse for both active gameplay ({@link PlayerInput}) and menu navigation ({@link MenuState}).
 * Captures the cursor automatically while playing and frees it whenever a menu, death or intermission screen is up.
 */
public final class InputController extends InputAdapter {

    private final LookAccumulator look = new LookAccumulator();
    private final MenuState menuState;
    private final GameSettings settings;
    private boolean captured;
    /** Set while a button that was down when gameplay resumed is still held, so that it does not fire a shot. */
    private boolean swallowFire;
    /** The same for the blast button, so that it does not start a charge. */
    private boolean swallowCharge;
    private int pendingScrollSwap;

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
        menuState.onWindowPaused();
        syncCursorCapture();
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
        if (captured && Math.abs(amountY) > 0f) {
            pendingScrollSwap = -1;
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
            MenuState.Command cmd = pollMenu();
            syncCursorCapture();
            return cmd;
        }

        if (Gdx.input.isKeyJustPressed(Input.Keys.ESCAPE)) {
            MenuState.Command cmd = menuState.escape();
            syncCursorCapture();
            return cmd;
        }
        if (Gdx.input.isKeyJustPressed(Input.Keys.R)) {
            return menuState.quickRestart();
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
            || Gdx.input.isKeyJustPressed(Input.Keys.SHIFT_RIGHT)
            || Gdx.input.isKeyJustPressed(Input.Keys.SPACE);
        out.interact = Gdx.input.isKeyJustPressed(Input.Keys.E);

        if (Gdx.input.isKeyJustPressed(Input.Keys.NUM_1)) out.weaponSlot = 1;
        else if (Gdx.input.isKeyJustPressed(Input.Keys.NUM_2)) out.weaponSlot = 2;
        else if (Gdx.input.isKeyJustPressed(Input.Keys.Q)) out.weaponSlot = -1;
        else if (pendingScrollSwap != 0) out.weaponSlot = pendingScrollSwap;
        pendingScrollSwap = 0;

        boolean leftButton = Gdx.input.isButtonPressed(Input.Buttons.LEFT);
        boolean rightButton = Gdx.input.isButtonPressed(Input.Buttons.RIGHT);
        if (!leftButton) swallowFire = false;
        if (!rightButton) swallowCharge = false;
        out.fire = leftButton && !swallowFire;
        out.charge = rightButton && !swallowCharge;
        return MenuState.Command.NONE;
    }

    private MenuState.Command pollMenu() {
        if (Gdx.input.isKeyJustPressed(Input.Keys.ESCAPE)) {
            return menuState.escape();
        }
        if (menuState.getScreen() == MenuState.Screen.DEAD && Gdx.input.isKeyJustPressed(Input.Keys.R)) {
            return menuState.quickRestart();
        }
        if ((menuState.getScreen() == MenuState.Screen.PAUSED || menuState.getScreen() == MenuState.Screen.TITLE)
            && Gdx.input.isKeyJustPressed(Input.Keys.Q)) {
            return MenuState.Command.QUIT;
        }
        if (Gdx.input.isKeyJustPressed(Input.Keys.UP) || Gdx.input.isKeyJustPressed(Input.Keys.W)) {
            menuState.moveSelection(-1);
        }
        if (Gdx.input.isKeyJustPressed(Input.Keys.DOWN) || Gdx.input.isKeyJustPressed(Input.Keys.S)) {
            menuState.moveSelection(1);
        }
        if (Gdx.input.isKeyJustPressed(Input.Keys.LEFT) || Gdx.input.isKeyJustPressed(Input.Keys.A)) {
            return menuState.adjustHorizontal(-1);
        }
        if (Gdx.input.isKeyJustPressed(Input.Keys.RIGHT) || Gdx.input.isKeyJustPressed(Input.Keys.D)) {
            return menuState.adjustHorizontal(1);
        }
        if (Gdx.input.isKeyJustPressed(Input.Keys.ENTER) || Gdx.input.isKeyJustPressed(Input.Keys.SPACE)) {
            return menuState.confirm();
        }
        if (Gdx.input.isButtonJustPressed(Input.Buttons.LEFT)) {
            int sx = Gdx.input.getX();
            int sy = Gdx.input.getY();
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

    /** Ensures OS cursor capture always matches whether {@link MenuState#isPlaying()} is active. */
    public void syncCursorCapture() {
        boolean shouldCapture = menuState.isPlaying();
        if (shouldCapture == captured) return;
        captured = shouldCapture;
        Gdx.input.setCursorCatched(captured);
        if (captured) {
            look.capture();
            swallowFire = true;
            swallowCharge = true;
            pendingScrollSwap = 0;
        } else {
            look.release();
            pendingScrollSwap = 0;
        }
    }
}

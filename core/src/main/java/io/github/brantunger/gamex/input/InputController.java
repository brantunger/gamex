package io.github.brantunger.gamex.input;

import com.badlogic.gdx.Gdx;
import com.badlogic.gdx.Input;
import com.badlogic.gdx.InputAdapter;
import io.github.brantunger.gamex.player.PlayerInput;

/**
 * Reads the keyboard and mouse into a {@link PlayerInput}. The mouse cursor is captured while playing. Esc releases
 * it and pauses; Esc again, or a click in the window, captures it and resumes. Q quits while paused.
 *
 * <p>It installs itself as the application's input processor, because mouse look is added up from every mouse event
 * (see {@link LookAccumulator}); the per-frame mouse delta of the input device keeps only the last event of a frame.
 */
public final class InputController extends InputAdapter {

    /** Degrees of turn per pixel of mouse movement. */
    private static final float MOUSE_SENSITIVITY = 0.12f;

    private final LookAccumulator look = new LookAccumulator();
    private boolean captured;
    /** Set while a button that was down when the game resumed is still held, so that it does not fire the pistol. */
    private boolean swallowFire;
    /** The same for the blast button, so that it does not start a charge. */
    private boolean swallowCharge;

    /** Captures the cursor, starts in the playing state and becomes the application's input processor. */
    public InputController() {
        Gdx.input.setInputProcessor(this);
        setCaptured(true);
    }

    /** @return whether the cursor is released and the game should stand still. */
    public boolean isPaused() {
        return !captured;
    }

    /** Releases the cursor and pauses, as Esc does. Used when the window loses the focus or is minimised. */
    public void pause() {
        if (captured) setCaptured(false);
    }

    @Override
    public boolean mouseMoved(int screenX, int screenY) {
        look.moved(screenX, screenY);
        return true;
    }

    @Override
    public boolean touchDragged(int screenX, int screenY, int pointer) {
        // Mouse movement with a button held arrives here instead of in mouseMoved.
        look.moved(screenX, screenY);
        return true;
    }

    /**
     * Reads the devices for this frame.
     * @param out the input to fill; it is cleared while paused
     */
    public void poll(PlayerInput out) {
        out.clear();
        if (Gdx.input.isKeyJustPressed(Input.Keys.ESCAPE)) {
            setCaptured(!captured);
        } else if (!captured && Gdx.input.justTouched()) {
            setCaptured(true);
        }
        if (!captured) {
            if (Gdx.input.isKeyJustPressed(Input.Keys.Q)) Gdx.app.exit();
            return;
        }

        out.yawDelta = look.consumeX() * MOUSE_SENSITIVITY;
        out.pitchDelta = -look.consumeY() * MOUSE_SENSITIVITY;
        if (Gdx.input.isKeyPressed(Input.Keys.W)) out.moveForward += 1f;
        if (Gdx.input.isKeyPressed(Input.Keys.S)) out.moveForward -= 1f;
        if (Gdx.input.isKeyPressed(Input.Keys.D)) out.moveStrafe += 1f;
        if (Gdx.input.isKeyPressed(Input.Keys.A)) out.moveStrafe -= 1f;

        boolean leftButton = Gdx.input.isButtonPressed(Input.Buttons.LEFT);
        boolean rightButton = Gdx.input.isButtonPressed(Input.Buttons.RIGHT);
        if (!leftButton) swallowFire = false;
        if (!rightButton) swallowCharge = false;
        out.fire = leftButton && !swallowFire;
        out.charge = rightButton && !swallowCharge;
    }

    private void setCaptured(boolean capture) {
        captured = capture;
        Gdx.input.setCursorCatched(capture);
        if (capture) {
            look.capture();
            // Whatever button is down at this moment was pressed to get back in, not to shoot.
            swallowFire = true;
            swallowCharge = true;
        } else {
            look.release();
        }
    }
}

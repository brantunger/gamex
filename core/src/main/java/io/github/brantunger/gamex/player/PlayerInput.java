package io.github.brantunger.gamex.player;

/** What the player asks for during one frame. It is filled from the real input devices or from a script. */
public final class PlayerInput {

    /** Forward movement from -1 (backwards) to 1 (forwards). */
    public float moveForward;
    /** Sideways movement from -1 (left) to 1 (right). */
    public float moveStrafe;
    /** The change in yaw this frame, in degrees; positive turns right. */
    public float yawDelta;
    /** The change in pitch this frame, in degrees; positive looks up. */
    public float pitchDelta;
    /** Whether the pistol trigger is held. */
    public boolean fire;
    /** Whether the blast charge button is held. */
    public boolean charge;
    /** Whether a horizontal energy dash was requested this frame. */
    public boolean dash;
    /** Whether the interact action was requested this frame. */
    public boolean interact;

    /** Resets every field to "nothing asked for". */
    public void clear() {
        moveForward = 0f;
        moveStrafe = 0f;
        yawDelta = 0f;
        pitchDelta = 0f;
        fire = false;
        charge = false;
        dash = false;
        interact = false;
    }
}

package io.github.brantunger.gamex.player;

/** What the player asks for during one frame. It is filled from the real input devices or from a script. */
public final class PlayerInput {

    /** No weapon switch requested. */
    public static final int WEAPON_NONE = 0;
    /** Quick-swap to the previously equipped weapon ({@code Q}). */
    public static final int WEAPON_QUICK_SWAP = -1;
    /** Cycle forward to the next owned weapon (mouse wheel down). */
    public static final int WEAPON_CYCLE_NEXT = -2;
    /** Cycle backward to the previous owned weapon (mouse wheel up). */
    public static final int WEAPON_CYCLE_PREV = -3;

    /** Forward movement from -1 (backwards) to 1 (forwards). */
    public float moveForward;
    /** Sideways movement from -1 (left) to 1 (right). */
    public float moveStrafe;
    /** The change in yaw this frame, in degrees; positive turns right. */
    public float yawDelta;
    /** The change in pitch this frame, in degrees; positive looks up. */
    public float pitchDelta;
    /** Whether the primary weapon trigger is held. */
    public boolean fire;
    /** Whether the secondary trigger (charge blast, double-blast, or zoom optic) is held. */
    public boolean charge;
    /** Whether a vertical jump is requested (Space). */
    public boolean jump;
    /** Whether a horizontal energy dash was requested this frame (Shift). */
    public boolean dash;
    /** Whether the interact action was requested this frame. */
    public boolean interact;
    /**
     * Requested weapon slot ({@code 1}=Pistol, {@code 2}=Scattergun, {@code 3}=Rail Lance,
     * {@link #WEAPON_QUICK_SWAP}, {@link #WEAPON_CYCLE_NEXT}, {@link #WEAPON_CYCLE_PREV}, or {@link #WEAPON_NONE}).
     */
    public int weaponSlot;

    /** Resets every field to "nothing asked for". */
    public void clear() {
        moveForward = 0f;
        moveStrafe = 0f;
        yawDelta = 0f;
        pitchDelta = 0f;
        fire = false;
        charge = false;
        jump = false;
        dash = false;
        interact = false;
        weaponSlot = 0;
    }
}

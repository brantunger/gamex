package io.github.brantunger.gamex.input;

/**
 * One input an action can be bound to: a key, a mouse button or a direction of the wheel. {@link KeyNames} turns a
 * name from the bindings file into one. Instances are immutable; two are equal when they are the same input,
 * whatever name each was written with.
 */
public final class BoundInput {

    /** What kind of input it is, which tells how {@link #code} is read. */
    public enum Kind {
        /** A key; the code is a libGDX {@code Input.Keys} keycode. */
        KEY,
        /** A mouse button; the code is a libGDX {@code Input.Buttons} number. */
        BUTTON,
        /** The wheel; the code is {@link #WHEEL_UP} or {@link #WHEEL_DOWN}. */
        WHEEL
    }

    /** The code of the wheel turned away from the player: the sign of the vertical scroll amount libGDX reports. */
    public static final int WHEEL_UP = -1;
    /** The code of the wheel turned towards the player. */
    public static final int WHEEL_DOWN = 1;

    public final Kind kind;
    /** The keycode, button number or wheel direction, according to {@link #kind}. */
    public final int code;
    /** The input's name as the bindings file writes it (for example {@code L-Shift}). */
    public final String name;

    BoundInput(Kind kind, int code, String name) {
        this.kind = kind;
        this.code = code;
        this.name = name;
    }

    @Override
    public boolean equals(Object other) {
        if (!(other instanceof BoundInput)) return false;
        BoundInput that = (BoundInput) other;
        return kind == that.kind && code == that.code;
    }

    @Override
    public int hashCode() {
        return kind.ordinal() * 1031 + code;
    }

    @Override
    public String toString() {
        return name;
    }
}

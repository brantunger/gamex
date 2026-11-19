package io.github.brantunger.gamex.input;

import com.badlogic.gdx.Input;

import java.util.HashMap;
import java.util.Locale;
import java.util.Map;

/**
 * The names of the inputs: how the bindings file writes a key, a mouse button or a wheel direction, and the short
 * name on-screen hints show for it.
 *
 * <h2>Names in the file</h2>
 * A key is written with the name libGDX gives it ({@code Input.Keys.toString}): {@code W}, {@code 1},
 * {@code Space}, {@code Enter}, {@code Escape}, {@code L-Shift}, {@code R-Ctrl}, {@code Up}, {@code F5},
 * {@code Numpad 1}, ... The case matters. The two keys libGDX names with a comma, which separates the inputs of a
 * line, are written {@code Comma} and {@code Num Comma}. The mouse buttons are {@code Mouse-Left},
 * {@code Mouse-Right}, {@code Mouse-Middle}, {@code Mouse-Back} and {@code Mouse-Forward}, and the wheel is
 * {@code Wheel-Up} (turned away from the player) or {@code Wheel-Down}.
 *
 * <h2>Names on screen</h2>
 * {@link #display} gives the name a hint shows: a short one from a table for the inputs whose name is long or says
 * more than a hint needs ({@code L-Shift} and {@code R-Shift} are both {@code SHIFT}, {@code Escape} is
 * {@code ESC}, {@code Mouse-Left} is {@code LMB}, both wheel directions are {@code SCROLL}), and the name in
 * capitals for the rest ({@code Space} is {@code SPACE}). The font cannot draw every such name ({@code =}, for one);
 * {@link KeyBindings} checks the ones it shows.
 */
public final class KeyNames {

    private static final String COMMA = "Comma";
    private static final String NUMPAD_COMMA = "Num Comma";

    /** The mouse buttons and wheel directions by the name the file gives them. */
    private static final Map<String, BoundInput> POINTER = new HashMap<>();
    /** The display names that are not the file name in capitals. */
    private static final Map<String, String> SHORT = new HashMap<>();

    static {
        pointer(BoundInput.Kind.BUTTON, Input.Buttons.LEFT, "Mouse-Left", "LMB");
        pointer(BoundInput.Kind.BUTTON, Input.Buttons.RIGHT, "Mouse-Right", "RMB");
        pointer(BoundInput.Kind.BUTTON, Input.Buttons.MIDDLE, "Mouse-Middle", "MMB");
        pointer(BoundInput.Kind.BUTTON, Input.Buttons.BACK, "Mouse-Back", "MB4");
        pointer(BoundInput.Kind.BUTTON, Input.Buttons.FORWARD, "Mouse-Forward", "MB5");
        pointer(BoundInput.Kind.WHEEL, BoundInput.WHEEL_UP, "Wheel-Up", "SCROLL");
        pointer(BoundInput.Kind.WHEEL, BoundInput.WHEEL_DOWN, "Wheel-Down", "SCROLL");
        SHORT.put("L-Shift", "SHIFT");
        SHORT.put("R-Shift", "SHIFT");
        SHORT.put("L-Ctrl", "CTRL");
        SHORT.put("R-Ctrl", "CTRL");
        SHORT.put("L-Alt", "ALT");
        SHORT.put("R-Alt", "ALT");
        SHORT.put("Escape", "ESC");
        SHORT.put(COMMA, ",");
        SHORT.put(NUMPAD_COMMA, "NUM ,");
    }

    private KeyNames() {}

    private static void pointer(BoundInput.Kind kind, int code, String name, String shortName) {
        POINTER.put(name, new BoundInput(kind, code, name));
        SHORT.put(name, shortName);
    }

    /**
     * @param name a name as the bindings file writes it, trimmed
     * @return the input with that name, or null if it names none
     */
    public static BoundInput parse(String name) {
        BoundInput pointer = POINTER.get(name);
        if (pointer != null) return pointer;
        if (COMMA.equals(name)) return new BoundInput(BoundInput.Kind.KEY, Input.Keys.COMMA, name);
        if (NUMPAD_COMMA.equals(name)) return new BoundInput(BoundInput.Kind.KEY, Input.Keys.NUMPAD_COMMA, name);
        // libGDX answers -1 for a name it does not know, and has the name "Unknown" for keycode 0.
        int keycode = Input.Keys.valueOf(name);
        if (keycode <= Input.Keys.UNKNOWN) return null;
        return new BoundInput(BoundInput.Kind.KEY, keycode, name);
    }

    /**
     * @param input an input from {@link #parse}
     * @return the name a hint shows for it, in capitals
     */
    public static String display(BoundInput input) {
        String shortName = SHORT.get(input.name);
        return shortName != null ? shortName : input.name.toUpperCase(Locale.ROOT);
    }
}

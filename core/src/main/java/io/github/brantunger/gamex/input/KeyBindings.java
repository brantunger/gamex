package io.github.brantunger.gamex.input;

import io.github.brantunger.gamex.data.TuningFile;
import io.github.brantunger.gamex.hud.GlyphCheck;
import io.github.brantunger.gamex.hud.PixelFont;

import java.util.ArrayList;
import java.util.Collections;
import java.util.EnumMap;
import java.util.List;
import java.util.Map;

/**
 * The inputs of every {@link Action}. Loaded once at start-up from {@code assets/data/bindings.txt} and passed to
 * {@link InputController}; tests check the rules by handing text to {@link #parse}.
 *
 * <p>The file holds one {@code key: value} line per action and nothing else: the key is the action's
 * {@link Action#id} and the value is one input or several, separated by commas, each written with a name
 * {@link KeyNames} knows.
 * <pre>
 * dash: L-Shift, R-Shift
 * weapon_next: Wheel-Down
 * fire: Mouse-Left
 * </pre>
 * There is no default, so an action cannot be left without an input. Each of these fails the load with a message
 * naming the file, the action and the line:
 * <ul>
 *   <li>an action without a line, a key that names no action, and a second line for an action (see
 *       {@link TuningFile}; a missing action has no line to name);</li>
 *   <li>an empty name, and a name that is no key, mouse button or wheel direction;</li>
 *   <li>an input written twice on one line;</li>
 *   <li>an input bound to two actions that act in the same {@link Action.Context}. {@code Q} can be both
 *       {@code quick_swap} (play) and {@code quit} (menus); it cannot be both {@code quit} and
 *       {@code menu_confirm};</li>
 *   <li>the wheel bound to an action that acts on the menu screens, which the wheel does not reach;</li>
 *   <li>a key or the wheel bound to {@link Action#MENU_CLICK}, which needs the pointer's position;</li>
 *   <li>a first input whose display name ({@link KeyNames#display}) the font cannot draw: the first input of a line
 *       is the one on-screen hints name (see {@link #hint(Action)}).</li>
 * </ul>
 *
 * <p>Instances are immutable and safe to share.
 */
public final class KeyBindings {

    /** Internal asset path of the bindings. */
    public static final String PATH = "data/bindings.txt";

    private final Map<Action, List<BoundInput>> inputs;
    private final Map<Action, String> hints;

    private KeyBindings(Map<Action, List<BoundInput>> inputs, Map<Action, String> hints) {
        this.inputs = inputs;
        this.hints = hints;
    }

    /**
     * Reads the bindings from {@link #PATH}.
     * @param font the loaded font the hints will be drawn with
     * @return the loaded bindings
     * @throws IllegalArgumentException if the file breaks one of the rules described on this class
     */
    public static KeyBindings load(PixelFont font) {
        return from(TuningFile.load(PATH, Action.ids(), Collections.emptyList()), font);
    }

    /**
     * Parses the text of a bindings file.
     * @param assetPath path label included in error messages
     * @param text raw file contents
     * @param font the loaded font the hints will be drawn with
     * @return the parsed bindings
     * @throws IllegalArgumentException as {@link #load} does
     */
    public static KeyBindings parse(String assetPath, String text, PixelFont font) {
        return from(TuningFile.parseText(assetPath, text, Action.ids(), Collections.emptyList()), font);
    }

    private static KeyBindings from(TuningFile file, PixelFont font) {
        Map<Action, List<BoundInput>> inputs = new EnumMap<>(Action.class);
        Map<Action, String> hints = new EnumMap<>(Action.class);
        for (Action action : Action.values()) {
            TuningFile.Entry entry = file.entry(action.id);
            List<BoundInput> bound = new ArrayList<>();
            for (String item : file.getString(action.id).split(",", -1)) {
                String name = item.trim();
                if (name.isEmpty()) {
                    throw file.invalid(entry, "an input name is empty");
                }
                BoundInput input = KeyNames.parse(name);
                if (input == null) {
                    throw file.invalid(entry, "'" + name + "' is not a key, mouse button or wheel name");
                }
                if (bound.contains(input)) {
                    throw file.invalid(entry, "'" + name + "' is bound twice");
                }
                checkKind(file, entry, action, input);
                checkClash(file, entry, action, input, inputs);
                bound.add(input);
            }
            inputs.put(action, Collections.unmodifiableList(bound));
            hints.put(action, GlyphCheck.require(font, KeyNames.display(bound.get(0)),
                "the hint for '" + action.id + "' in " + file.path() + " at line " + entry.line));
        }
        return new KeyBindings(inputs, hints);
    }

    /** Rejects an input of a kind the action cannot be given. */
    private static void checkKind(TuningFile file, TuningFile.Entry entry, Action action, BoundInput input) {
        if (action == Action.MENU_CLICK && input.kind != BoundInput.Kind.BUTTON) {
            throw file.invalid(entry, "'" + input.name + "' is not a mouse button, and a click needs the pointer");
        }
        if (action.actsIn(Action.Context.MENU) && input.kind == BoundInput.Kind.WHEEL) {
            throw file.invalid(entry, "'" + input.name + "' cannot be bound to an action of the menu screens");
        }
    }

    /** Rejects an input already bound to an action, on an earlier line, that acts in a context this one acts in. */
    private static void checkClash(TuningFile file, TuningFile.Entry entry, Action action, BoundInput input,
                                   Map<Action, List<BoundInput>> earlier) {
        for (Map.Entry<Action, List<BoundInput>> other : earlier.entrySet()) {
            if (!other.getValue().contains(input)) continue;
            Action otherAction = other.getKey();
            for (Action.Context context : Action.Context.values()) {
                if (action.actsIn(context) && otherAction.actsIn(context)) {
                    throw file.invalid(entry, "'" + input.name + "' is already bound to '" + otherAction.id
                        + "' at line " + file.entry(otherAction.id).line + ", and both act "
                        + (context == Action.Context.PLAY ? "during play" : "on the menu screens"));
                }
            }
        }
    }

    /**
     * @param action an action
     * @return its inputs, at least one, in the order the file lists them; the list cannot be changed
     */
    public List<BoundInput> inputs(Action action) {
        return inputs.get(action);
    }

    /**
     * @param action an action
     * @return the name on-screen text shows for the action's key: the display name of its first input
     *     ({@link KeyNames#display}), which the font can draw
     */
    public String hint(Action action) {
        return hints.get(action);
    }

    /**
     * The same for an action named by its id, as a {@code {key:id}} placeholder names it.
     * @param actionId one of {@link Action#ids()}
     * @return the name on-screen text shows for the action's key
     * @throws IllegalArgumentException if no action has that id
     */
    public String hint(String actionId) {
        Action action = Action.byId(actionId);
        if (action == null) {
            throw new IllegalArgumentException("Unknown action '" + actionId + "'; the actions are " + Action.ids());
        }
        return hint(action);
    }
}

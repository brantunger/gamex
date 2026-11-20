package io.github.brantunger.gamex.input;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.badlogic.gdx.Input;
import io.github.brantunger.gamex.hud.GlyphCheck;
import io.github.brantunger.gamex.hud.PixelFont;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.EnumMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Verifies {@link KeyBindings}, {@link KeyNames} and {@link Action}: the shipped file binds every action to the
 * inputs the game had in code, a changed line changes the inputs and the hint, and every mistake in a file is
 * reported with the file, the action and the line.
 */
class KeyBindingsTest {

    private static final String FILE = "data/test-bindings.txt";
    private static final PixelFont FONT = PixelFont.load();

    private static BoundInput key(int keycode) {
        return new BoundInput(BoundInput.Kind.KEY, keycode, Input.Keys.toString(keycode));
    }

    private static BoundInput button(int button) {
        return new BoundInput(BoundInput.Kind.BUTTON, button, "button " + button);
    }

    private static BoundInput wheel(int direction) {
        return new BoundInput(BoundInput.Kind.WHEEL, direction, "wheel " + direction);
    }

    /** @return the inputs {@code InputController} named in code before the bindings moved to a file */
    private static Map<Action, List<BoundInput>> inputsBeforeTheMove() {
        Map<Action, List<BoundInput>> expected = new EnumMap<>(Action.class);
        expected.put(Action.PAUSE, List.of(key(Input.Keys.ESCAPE)));
        expected.put(Action.RETRY, List.of(key(Input.Keys.R)));
        expected.put(Action.MOVE_FORWARD, List.of(key(Input.Keys.W)));
        expected.put(Action.MOVE_BACK, List.of(key(Input.Keys.S)));
        expected.put(Action.MOVE_LEFT, List.of(key(Input.Keys.A)));
        expected.put(Action.MOVE_RIGHT, List.of(key(Input.Keys.D)));
        expected.put(Action.DASH, List.of(key(Input.Keys.SHIFT_LEFT), key(Input.Keys.SHIFT_RIGHT)));
        expected.put(Action.JUMP, List.of(key(Input.Keys.SPACE)));
        expected.put(Action.INTERACT, List.of(key(Input.Keys.E)));
        expected.put(Action.WEAPON_1, List.of(key(Input.Keys.NUM_1)));
        expected.put(Action.WEAPON_2, List.of(key(Input.Keys.NUM_2)));
        expected.put(Action.WEAPON_3, List.of(key(Input.Keys.NUM_3)));
        expected.put(Action.QUICK_SWAP, List.of(key(Input.Keys.Q)));
        // A positive scroll amount, the wheel turned towards the player, chose the next weapon.
        expected.put(Action.WEAPON_NEXT, List.of(wheel(1)));
        expected.put(Action.WEAPON_PREV, List.of(wheel(-1)));
        expected.put(Action.FIRE, List.of(button(Input.Buttons.LEFT)));
        expected.put(Action.ALT_FIRE, List.of(button(Input.Buttons.RIGHT)));
        expected.put(Action.MINIMAP, List.of(key(Input.Keys.M)));
        expected.put(Action.MENU_UP, List.of(key(Input.Keys.UP), key(Input.Keys.W)));
        expected.put(Action.MENU_DOWN, List.of(key(Input.Keys.DOWN), key(Input.Keys.S)));
        expected.put(Action.MENU_LEFT, List.of(key(Input.Keys.LEFT), key(Input.Keys.A)));
        expected.put(Action.MENU_RIGHT, List.of(key(Input.Keys.RIGHT), key(Input.Keys.D)));
        expected.put(Action.MENU_CONFIRM, List.of(key(Input.Keys.ENTER), key(Input.Keys.SPACE)));
        expected.put(Action.QUIT, List.of(key(Input.Keys.Q)));
        expected.put(Action.MENU_CLICK, List.of(button(Input.Buttons.LEFT)));
        return expected;
    }

    /**
     * @return the key names the on-screen text spelled out before hints were taken from the bindings, and for the
     *     actions that text never named ({@code pause}, the menu arrows, {@code menu_confirm}, {@code menu_click},
     *     {@code weapon_prev}, {@code weapon_2}) the names the strings file was first written against
     */
    private static Map<Action, String> hintsBeforeTheMove() {
        Map<Action, String> expected = new EnumMap<>(Action.class);
        expected.put(Action.PAUSE, "ESC");
        expected.put(Action.RETRY, "R");
        expected.put(Action.MOVE_FORWARD, "W");
        expected.put(Action.MOVE_BACK, "S");
        expected.put(Action.MOVE_LEFT, "A");
        expected.put(Action.MOVE_RIGHT, "D");
        expected.put(Action.DASH, "SHIFT");
        expected.put(Action.JUMP, "SPACE");
        expected.put(Action.INTERACT, "E");
        expected.put(Action.WEAPON_1, "1");
        expected.put(Action.WEAPON_2, "2");
        expected.put(Action.WEAPON_3, "3");
        expected.put(Action.QUICK_SWAP, "Q");
        expected.put(Action.WEAPON_NEXT, "SCROLL");
        expected.put(Action.WEAPON_PREV, "SCROLL");
        expected.put(Action.FIRE, "LMB");
        expected.put(Action.ALT_FIRE, "RMB");
        expected.put(Action.MINIMAP, "M");
        expected.put(Action.MENU_UP, "UP");
        expected.put(Action.MENU_DOWN, "DOWN");
        expected.put(Action.MENU_LEFT, "LEFT");
        expected.put(Action.MENU_RIGHT, "RIGHT");
        expected.put(Action.MENU_CONFIRM, "ENTER");
        expected.put(Action.QUIT, "Q");
        expected.put(Action.MENU_CLICK, "LMB");
        return expected;
    }

    /**
     * @param changes pairs of an action id and the value to give its line; a null value leaves the line out
     * @return the text of a bindings file with one line per action in declaration order, so that an action's line
     *     number is its ordinal plus one (less the lines left out above it), holding the shipped inputs but for
     *     {@code changes}
     */
    private static String fileWith(String... changes) {
        Map<String, String> lines = new LinkedHashMap<>();
        lines.put("pause", "Escape");
        lines.put("retry", "R");
        lines.put("move_forward", "W");
        lines.put("move_back", "S");
        lines.put("move_left", "A");
        lines.put("move_right", "D");
        lines.put("dash", "L-Shift, R-Shift");
        lines.put("jump", "Space");
        lines.put("interact", "E");
        lines.put("weapon_1", "1");
        lines.put("weapon_2", "2");
        lines.put("weapon_3", "3");
        lines.put("quick_swap", "Q");
        lines.put("weapon_next", "Wheel-Down");
        lines.put("weapon_prev", "Wheel-Up");
        lines.put("fire", "Mouse-Left");
        lines.put("alt_fire", "Mouse-Right");
        lines.put("minimap", "M");
        lines.put("menu_up", "Up, W");
        lines.put("menu_down", "Down, S");
        lines.put("menu_left", "Left, A");
        lines.put("menu_right", "Right, D");
        lines.put("menu_confirm", "Enter, Space");
        lines.put("quit", "Q");
        lines.put("menu_click", "Mouse-Left");
        for (int i = 0; i < changes.length; i += 2) {
            assertTrue(lines.containsKey(changes[i]), changes[i] + " is not a line of the test file");
            if (changes[i + 1] == null) lines.remove(changes[i]);
            else lines.put(changes[i], changes[i + 1]);
        }
        StringBuilder text = new StringBuilder();
        for (Map.Entry<String, String> line : lines.entrySet()) {
            text.append(line.getKey()).append(": ").append(line.getValue()).append('\n');
        }
        return text.toString();
    }

    private static KeyBindings parseWith(String... changes) {
        return KeyBindings.parse(FILE, fileWith(changes), FONT);
    }

    private static String errorWith(String... changes) {
        return assertThrows(IllegalArgumentException.class, () -> parseWith(changes)).getMessage();
    }

    @Test
    void theActionIdsAreTheVocabularyOfTheKeyPlaceholders() {
        assertEquals(Arrays.asList("pause", "retry", "move_forward", "move_back", "move_left", "move_right", "dash",
            "jump", "interact", "weapon_1", "weapon_2", "weapon_3", "quick_swap", "weapon_next", "weapon_prev",
            "fire", "alt_fire", "minimap", "menu_up", "menu_down", "menu_left", "menu_right", "menu_confirm", "quit",
            "menu_click"), new ArrayList<>(Action.ids()));
        for (Action action : Action.values()) {
            assertEquals(action, Action.byId(action.id));
        }
        assertNull(Action.byId("crouch"));
        assertThrows(UnsupportedOperationException.class, () -> Action.ids().add("crouch"));
    }

    @Test
    void theShippedFileBindsEveryActionToTheInputsTheGameHadInCode() {
        KeyBindings shipped = KeyBindings.load(FONT);
        Map<Action, List<BoundInput>> expected = inputsBeforeTheMove();
        assertEquals(Action.values().length, expected.size(), "the table above must cover every action");
        for (Action action : Action.values()) {
            assertEquals(expected.get(action), shipped.inputs(action), action.id);
        }
    }

    @Test
    void theShippedHintsAreTheKeyNamesTheScreenSpelledOut() {
        KeyBindings shipped = KeyBindings.load(FONT);
        assertEquals(Action.values().length, hintsBeforeTheMove().size(), "the table above must cover every action");
        for (Map.Entry<Action, String> hint : hintsBeforeTheMove().entrySet()) {
            assertEquals(hint.getValue(), shipped.hint(hint.getKey()), hint.getKey().id);
            assertEquals(hint.getValue(), shipped.hint(hint.getKey().id), hint.getKey().id);
        }
        for (Action action : Action.values()) {
            assertEquals(-1, GlyphCheck.firstUndrawable(FONT, shipped.hint(action)), action.id);
        }
    }

    @Test
    void theTestFileHoldsTheShippedBindings() {
        KeyBindings shipped = KeyBindings.load(FONT);
        KeyBindings test = parseWith();
        for (Action action : Action.values()) {
            assertEquals(shipped.inputs(action), test.inputs(action), action.id);
            assertEquals(shipped.hint(action), test.hint(action), action.id);
        }
    }

    @Test
    void aChangedLineChangesTheInputsAndTheHint() {
        KeyBindings rebound = parseWith("dash", "F", "move_forward", "Up, Numpad 8", "fire", "L-Ctrl, Mouse-Left");

        assertEquals(List.of(key(Input.Keys.F)), rebound.inputs(Action.DASH));
        assertEquals("F", rebound.hint(Action.DASH));
        assertEquals(List.of(key(Input.Keys.UP), key(Input.Keys.NUMPAD_8)), rebound.inputs(Action.MOVE_FORWARD));
        assertEquals("UP", rebound.hint("move_forward"));
        assertEquals(List.of(key(Input.Keys.CONTROL_LEFT), button(Input.Buttons.LEFT)),
            rebound.inputs(Action.FIRE));
        assertEquals("CTRL", rebound.hint(Action.FIRE));
        // The lines left alone keep their inputs.
        assertEquals(List.of(key(Input.Keys.SPACE)), rebound.inputs(Action.JUMP));
    }

    @Test
    void anInputCanServeOneActionInPlayAndAnotherOnTheMenus() {
        KeyBindings rebound = parseWith("jump", "Enter", "interact", "Up", "fire", "Mouse-Left, Q",
            "quick_swap", "Tab");

        assertEquals(List.of(key(Input.Keys.ENTER)), rebound.inputs(Action.JUMP));
        assertEquals(key(Input.Keys.ENTER), rebound.inputs(Action.MENU_CONFIRM).get(0));
        assertEquals(key(Input.Keys.Q), rebound.inputs(Action.FIRE).get(1));
        assertEquals(List.of(key(Input.Keys.Q)), rebound.inputs(Action.QUIT));
    }

    @Test
    void anActionWithoutALineFailsNamingTheFileAndTheAction() {
        for (Action action : Action.values()) {
            assertEquals("Missing '" + action.id + "' key in " + FILE, errorWith(action.id, null));
        }
    }

    @Test
    void aLineThatNamesNoActionFailsNamingTheFileTheKeyAndTheLine() {
        IllegalArgumentException e = assertThrows(IllegalArgumentException.class,
            () -> KeyBindings.parse(FILE, fileWith() + "crouch: C\n", FONT));
        assertEquals("Unknown key 'crouch' in " + FILE + " at line 26", e.getMessage());

        e = assertThrows(IllegalArgumentException.class,
            () -> KeyBindings.parse(FILE, fileWith() + "dash: F\n", FONT));
        assertEquals("Duplicate 'dash' key in " + FILE + " at line 26", e.getMessage());
    }

    @Test
    void anUnknownInputNameFailsNamingTheFileTheActionAndTheLine() {
        assertEquals("Invalid 'dash' in " + FILE + " at line 7: 'Shift' is not a key, mouse button or wheel name, "
            + "got 'L-Shift, Shift'", errorWith("dash", "L-Shift, Shift"));
        // The case matters, and libGDX's name for no key at all is not a key.
        assertEquals("Invalid 'jump' in " + FILE + " at line 8: 'space' is not a key, mouse button or wheel name, "
            + "got 'space'", errorWith("jump", "space"));
        assertEquals("Invalid 'jump' in " + FILE + " at line 8: 'Unknown' is not a key, mouse button or wheel name, "
            + "got 'Unknown'", errorWith("jump", "Unknown"));
        assertEquals("Invalid 'fire' in " + FILE + " at line 16: 'Mouse-4' is not a key, mouse button or wheel "
            + "name, got 'Mouse-4'", errorWith("fire", "Mouse-4"));
    }

    @Test
    void anEmptyValueOrNameFails() {
        assertEquals("Empty 'jump' key in " + FILE + " at line 8", errorWith("jump", ""));
        assertEquals("Invalid 'dash' in " + FILE + " at line 7: an input name is empty, got 'L-Shift,'",
            errorWith("dash", "L-Shift,"));
        assertEquals("Invalid 'dash' in " + FILE + " at line 7: an input name is empty, got 'L-Shift,, R-Shift'",
            errorWith("dash", "L-Shift,, R-Shift"));
    }

    @Test
    void anInputWrittenTwiceOnALineFails() {
        assertEquals("Invalid 'dash' in " + FILE + " at line 7: 'L-Shift' is bound twice, got 'L-Shift, L-Shift'",
            errorWith("dash", "L-Shift, L-Shift"));
    }

    @Test
    void aClashWithinPlayFails() {
        assertEquals("Invalid 'interact' in " + FILE + " at line 9: 'Space' is already bound to 'jump' at line 8, "
            + "and both act during play, got 'E, Space'", errorWith("interact", "E, Space"));
        assertEquals("Invalid 'alt_fire' in " + FILE + " at line 17: 'Wheel-Up' is already bound to 'weapon_prev' "
            + "at line 15, and both act during play, got 'Wheel-Up'", errorWith("alt_fire", "Wheel-Up"));
        assertEquals("Invalid 'alt_fire' in " + FILE + " at line 17: 'Mouse-Left' is already bound to 'fire' "
            + "at line 16, and both act during play, got 'Mouse-Left'", errorWith("alt_fire", "Mouse-Left"));
    }

    @Test
    void aClashWithinTheMenusFails() {
        assertEquals("Invalid 'quit' in " + FILE + " at line 24: 'Enter' is already bound to 'menu_confirm' at "
            + "line 23, and both act on the menu screens, got 'Enter'", errorWith("quit", "Enter"));
        assertEquals("Invalid 'menu_click' in " + FILE + " at line 25: 'Mouse-Right' is already bound to "
            + "'menu_confirm' at line 23, and both act on the menu screens, got 'Mouse-Right'",
            errorWith("menu_confirm", "Enter, Mouse-Right", "menu_click", "Mouse-Right"));
    }

    @Test
    void pauseAndRetryClashWithTheActionsOfBothContexts() {
        assertEquals("Invalid 'fire' in " + FILE + " at line 16: 'R' is already bound to 'retry' at line 2, and "
            + "both act during play, got 'R'", errorWith("fire", "R"));
        assertEquals("Invalid 'menu_up' in " + FILE + " at line 19: 'Escape' is already bound to 'pause' at line 1, "
            + "and both act on the menu screens, got 'Up, Escape'", errorWith("menu_up", "Up, Escape"));
        assertEquals("Invalid 'retry' in " + FILE + " at line 2: 'Escape' is already bound to 'pause' at line 1, "
            + "and both act during play, got 'Escape'", errorWith("retry", "Escape"));
    }

    @Test
    void theWheelCannotBeBoundToAnActionOfTheMenus() {
        assertEquals("Invalid 'menu_down' in " + FILE + " at line 20: 'Wheel-Down' cannot be bound to an action of "
            + "the menu screens, got 'Down, Wheel-Down'",
            errorWith("weapon_next", "E", "interact", "F", "menu_down", "Down, Wheel-Down"));
        assertEquals("Invalid 'pause' in " + FILE + " at line 1: 'Wheel-Up' cannot be bound to an action of the "
            + "menu screens, got 'Wheel-Up'", errorWith("pause", "Wheel-Up", "weapon_prev", "Z"));
    }

    @Test
    void aClickTakesMouseButtonsOnly() {
        assertEquals("Invalid 'menu_click' in " + FILE + " at line 25: 'F' is not a mouse button, and a click "
            + "needs the pointer, got 'Mouse-Left, F'", errorWith("menu_click", "Mouse-Left, F"));
        assertEquals(List.of(button(Input.Buttons.RIGHT), button(Input.Buttons.MIDDLE)),
            parseWith("menu_click", "Mouse-Right, Mouse-Middle").inputs(Action.MENU_CLICK));
    }

    @Test
    void aHintTheFontCannotDrawFails() {
        assertEquals("Unsupported PixelFont character '=' (U+003D) in the hint for 'jump' in " + FILE
            + " at line 8", errorWith("jump", "=, Space"));
        // Only the first input of a line is shown.
        KeyBindings second = parseWith("jump", "Space, =");
        assertEquals("SPACE", second.hint(Action.JUMP));
        assertEquals(key(Input.Keys.EQUALS), second.inputs(Action.JUMP).get(1));
    }

    @Test
    void aHintForAnIdThatNamesNoActionFails() {
        IllegalArgumentException e = assertThrows(IllegalArgumentException.class, () -> parseWith().hint("crouch"));
        assertTrue(e.getMessage().startsWith("Unknown action 'crouch'; the actions are [pause, retry, "),
            e.getMessage());
    }

    @Test
    void theCommaKeysHaveNamesWithoutAComma() {
        KeyBindings rebound = parseWith("interact", "Comma, Num Comma");
        assertEquals(List.of(key(Input.Keys.COMMA), key(Input.Keys.NUMPAD_COMMA)),
            rebound.inputs(Action.INTERACT));
        assertEquals(",", rebound.hint(Action.INTERACT));
    }

    @Test
    void displayNamesAreShortOrTheFileNameInCapitals() {
        String[][] names = {
            {"L-Shift", "SHIFT"}, {"R-Shift", "SHIFT"}, {"L-Ctrl", "CTRL"}, {"R-Ctrl", "CTRL"}, {"L-Alt", "ALT"},
            {"R-Alt", "ALT"}, {"Escape", "ESC"}, {"Mouse-Left", "LMB"}, {"Mouse-Right", "RMB"},
            {"Mouse-Middle", "MMB"}, {"Mouse-Back", "MB4"}, {"Mouse-Forward", "MB5"}, {"Wheel-Up", "SCROLL"},
            {"Wheel-Down", "SCROLL"}, {"Space", "SPACE"}, {"Enter", "ENTER"}, {"Tab", "TAB"}, {"W", "W"},
            {"1", "1"}, {"F5", "F5"}, {"Numpad 1", "NUMPAD 1"}, {"Page Up", "PAGE UP"}, {"Num Comma", "NUM ,"}
        };
        for (String[] name : names) {
            BoundInput input = KeyNames.parse(name[0]);
            assertNotNull(input, name[0]);
            assertEquals(name[0], input.name);
            assertEquals(name[1], KeyNames.display(input), name[0]);
            assertEquals(-1, GlyphCheck.firstUndrawable(FONT, KeyNames.display(input)), name[0]);
        }
        assertNull(KeyNames.parse("Wheel-Left"));
    }

    @Test
    void everyKeyLibgdxNamesCanBeWritten() {
        for (int keycode = 1; keycode <= Input.Keys.MAX_KEYCODE; keycode++) {
            String name = Input.Keys.toString(keycode);
            if (name == null) continue;
            if (name.indexOf(',') >= 0) name = name.replace(",", "Comma");
            BoundInput input = KeyNames.parse(name);
            assertNotNull(input, name);
            assertEquals(key(keycode), input, name);
        }
    }

    @Test
    void inputsAreEqualByKindAndCode() {
        assertEquals(key(Input.Keys.A), KeyNames.parse("A"));
        assertEquals(key(Input.Keys.A).hashCode(), KeyNames.parse("A").hashCode());
        assertTrue(!key(1).equals(button(1)) && !button(1).equals(wheel(1)) && !key(1).equals(key(2)));
        assertTrue(!key(1).equals("Soft Left"));
        assertEquals("Mouse-Left", KeyNames.parse("Mouse-Left").toString());
        assertEquals(BoundInput.Kind.WHEEL, KeyNames.parse("Wheel-Down").kind);
        assertEquals(BoundInput.WHEEL_DOWN, KeyNames.parse("Wheel-Down").code);
        assertEquals(BoundInput.WHEEL_UP, KeyNames.parse("Wheel-Up").code);
    }
}

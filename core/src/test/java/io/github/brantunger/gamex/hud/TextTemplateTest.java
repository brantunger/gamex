package io.github.brantunger.gamex.hud;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

import io.github.brantunger.gamex.TestTuning;
import org.junit.jupiter.api.Test;

import java.util.Arrays;
import java.util.Collections;
import java.util.List;

class TextTemplateTest {

    private static final String WHERE = "'k' in data/strings.txt at line 7";

    private static TextTemplate parse(String raw, String... allowed) {
        return TextTemplate.parse(raw, WHERE, Arrays.asList(allowed), TestTuning.FONT, TestTuning.BINDINGS::hint,
            TestTuning.TEXT_VALUES);
    }

    private static String error(String raw, String... allowed) {
        return assertThrows(IllegalArgumentException.class, () -> parse(raw, allowed)).getMessage();
    }

    @Test
    void valuesAreFilledInByNameInTheDeclaredOrder() {
        TextTemplate hostiles = parse("HOSTILES {remaining}/{total}", "remaining", "total");
        assertEquals(2, hostiles.arity());
        assertEquals("HOSTILES 3/8", hostiles.format("3", "8"));
        assertEquals("8 IN ALL, 3 LEFT", parse("{total} IN ALL, {remaining} LEFT", "remaining", "total")
            .format("3", "8"), "the text may use them in another order");
        assertEquals("3 3 8", parse("{remaining} {remaining} {total}", "remaining", "total").format("3", "8"),
            "and more than once");
        assertEquals("3", parse("{value}", "value").format("3"));
        assertEquals("PLAIN TEXT", parse("PLAIN TEXT").format());
        assertEquals(0, parse("PLAIN TEXT").arity());
    }

    @Test
    void theNumberOfValuesMustMatch() {
        TextTemplate hostiles = parse("HOSTILES {remaining}/{total}", "remaining", "total");
        assertEquals("Expected 2 values, got 1",
            assertThrows(IllegalArgumentException.class, () -> hostiles.format("3")).getMessage());
        assertThrows(IllegalArgumentException.class, () -> hostiles.format("3", "8", "9"));
        assertThrows(IllegalArgumentException.class, () -> parse("PLAIN TEXT").format("3"));
    }

    @Test
    void keyNamesAndTuningValuesAreFilledInWhenTheTextIsParsed() {
        assertEquals("[E] OPEN", parse("[{key:interact}] OPEN").format());
        assertEquals("WASD", parse("{key:move_forward}{key:move_left}{key:move_back}{key:move_right}").format());
        assertEquals("1.8X ZOOM", parse("{rail.zoom_factor}X ZOOM").format());
        assertEquals("SHIFT: 4 LEFT", parse("{key:dash}: {count} LEFT", "count").format("4"));
        KeyLabels other = action -> "TAB";
        assertEquals("[TAB] OPEN", TextTemplate.parse("[{key:interact}] OPEN", WHERE, Collections.emptyList(),
            TestTuning.FONT, other, TestTuning.TEXT_VALUES).format());
    }

    @Test
    void aBrokenPlaceholderFailsNamingWhereTheTextIs() {
        assertEquals("Unknown placeholder {count} in " + WHERE + " (it may hold {key:action}, {rail.zoom_factor})",
            error("{count} LEFT"));
        assertEquals("Unknown placeholder {Count} in " + WHERE + " (it may hold {count}, {key:action},"
            + " {rail.zoom_factor})", error("{Count} LEFT", "count"), "names are matched as written");
        assertEquals("Missing placeholder {count} in " + WHERE, error("NONE LEFT", "count"));
        assertEquals("'{' without '}' after it in " + WHERE, error("{count LEFT", "count"));
        assertEquals("'{' without '}' after it in " + WHERE, error("{co{count} LEFT", "count"));
        assertEquals("'}' without '{' before it in " + WHERE, error("count} LEFT", "count"));
        assertEquals("Empty placeholder {} in " + WHERE, error("{} LEFT"));
        assertEquals("Unknown action in placeholder {key:fly} in " + WHERE + " (the actions are "
            + String.join(", ", KeyLabels.ACTIONS) + ")", error("{key:fly} LEFT"));
        assertEquals("Unknown action in placeholder {key:} in " + WHERE + " (the actions are "
            + String.join(", ", KeyLabels.ACTIONS) + ")", error("{key:} LEFT"));
    }

    @Test
    void aCharacterTheFontLacksFailsWhetherWrittenOrFilledIn() {
        assertEquals("Unsupported PixelFont character '@' (U+0040) in " + WHERE, error("{count} @ ONCE", "count"));
        assertEquals("Unsupported PixelFont character '_' (U+005F) in " + WHERE, error("A_B"));
        KeyLabels undrawable = action -> "F_1";
        assertEquals("Unsupported PixelFont character '_' (U+005F) in " + WHERE,
            assertThrows(IllegalArgumentException.class, () -> TextTemplate.parse("[{key:interact}]", WHERE,
                Collections.emptyList(), TestTuning.FONT, undrawable, TestTuning.TEXT_VALUES)).getMessage());
        assertEquals("lower case is drawn as capitals", parse("lower case is drawn as capitals").format());
    }

    @Test
    void placeholdersCanBeCheckedBeforeTheKeyNamesAndTheTuningAreKnown() {
        List<String> none = Collections.emptyList();
        assertEquals("HOLD  FOR THE X ZOOM", TextTemplate.checkPlaceholders(
            "HOLD {key:alt_fire} FOR THE {rail.zoom_factor}X ZOOM", WHERE, none));
        assertEquals("PLAIN", TextTemplate.checkPlaceholders("PLAIN", WHERE, none));
        assertEquals(" LEFT", TextTemplate.checkPlaceholders("{count} LEFT", WHERE, Arrays.asList("count", "more")),
            "a name that is allowed need not be used");
        for (String bad : new String[] {"{count} LEFT", "{key:fly}", "{rail.zoom}", "{open", "close}", "{}"}) {
            assertThrows(IllegalArgumentException.class, () -> TextTemplate.checkPlaceholders(bad, WHERE, none), bad);
        }
    }
}

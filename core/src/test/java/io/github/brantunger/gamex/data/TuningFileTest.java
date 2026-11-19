package io.github.brantunger.gamex.data;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.badlogic.gdx.Files;
import com.badlogic.gdx.Gdx;
import com.badlogic.gdx.files.FileHandle;
import io.github.brantunger.gamex.TestTuning;
import io.github.brantunger.gamex.hud.PixelFont;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.IOException;
import java.lang.reflect.Proxy;
import java.nio.charset.StandardCharsets;
import java.nio.file.Path;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;

/** Verifies {@link TuningFile}: the format, the typed getters, and that every mistake in a file is reported. */
class TuningFileTest {

    private static final String FILE = "data/test.txt";
    /** A sample file on the test classpath, {@code core/src/test/resources/data-fixtures/sample-tuning.txt}. */
    private static final String SAMPLE_PATH = "data-fixtures/sample-tuning.txt";
    private static final List<String> SAMPLE_KEYS = Arrays.asList(
        "pistol.fire_interval", "pistol.pellets", "pistol.auto", "pistol.name", "pistol.falloff");
    private static final List<String> SAMPLE_REPEATED_KEYS = Collections.singletonList("hint");
    /** The shipped font, which display text is checked against. */
    private static final PixelFont FONT = TestTuning.FONT;

    private Files previousFiles;
    private String previousAssets;

    @BeforeEach
    void saveGlobals() {
        previousFiles = Gdx.files;
        previousAssets = System.getProperty("gamex.assets");
    }

    @AfterEach
    void restoreGlobals() {
        Gdx.files = previousFiles;
        if (previousAssets == null) {
            System.clearProperty("gamex.assets");
        } else {
            System.setProperty("gamex.assets", previousAssets);
        }
    }

    private static void assertError(String expected, Runnable action) {
        IllegalArgumentException e = assertThrows(IllegalArgumentException.class, action::run);
        assertEquals(expected, e.getMessage());
    }

    private static TuningFile one(String key, String value) {
        return TuningFile.parseText(FILE, "// first line\n" + key + ": " + value + "\n", key);
    }

    @Test
    void readsEveryKindOfValue() {
        TuningFile file = TuningFile.parseText(FILE,
            "speed: 4.25\ncount: -12\nenabled: true\nmuted: false\nname: Rail Lance\nfalloff: 0.60, 1.10, 1.27\n",
            "speed", "count", "enabled", "muted", "name", "falloff");

        assertEquals(FILE, file.path());
        assertEquals(4.25f, file.getFloat("speed"), 0f);
        assertEquals(-12, file.getInt("count"));
        assertTrue(file.getBoolean("enabled"));
        assertFalse(file.getBoolean("muted"));
        assertEquals("Rail Lance", file.getString("name"));
        assertEquals("Rail Lance", file.getDisplayString("name", FONT));
        assertArrayEquals(new float[] {0.60f, 1.10f, 1.27f}, file.getFloats("falloff"), 0f);
        assertArrayEquals(new float[] {0.60f, 1.10f, 1.27f}, file.getFloats("falloff", 3), 0f);
        assertArrayEquals(new float[] {4.25f}, file.getFloats("speed"), 0f);
        // An int is also a float, and a single-valued key is a list of one.
        assertEquals(-12f, file.getFloat("count"), 0f);
        assertEquals(Collections.singletonList("Rail Lance"), file.getStrings("name"));
    }

    @Test
    void acceptsPlainDecimalFormsOfNumbers() {
        assertEquals(12f, one("v", "12").getFloat("v"), 0f);
        assertEquals(12f, one("v", "+12.").getFloat("v"), 0f);
        assertEquals(-0.5f, one("v", "-.5").getFloat("v"), 0f);
        assertEquals(0.0015f, one("v", "1.5e-3").getFloat("v"), 0f);
        assertEquals(1500f, one("v", "1.5E3").getFloat("v"), 0f);
        assertEquals(7, one("v", "+7").getInt("v"));
        assertEquals(Integer.MIN_VALUE, one("v", "-2147483648").getInt("v"));
        assertEquals(Integer.MAX_VALUE, one("v", "2147483647").getInt("v"));
    }

    @Test
    void handlesByteOrderMarkLineEndingsCommentsBlankLinesAndColonsInValues() {
        String text = "\uFEFF// A comment\r\n"
            + "\r\n"
            + "  title :  07: DEEP: VAULT  \r"
            + "   // An indented comment\n"
            + "speed:1.5\r\n"
            + "\n"
            + "note: value // not a comment\n"
            + "last: no line ending";
        TuningFile file = TuningFile.parseText(FILE, text, "title", "speed", "note", "last");

        assertEquals("07: DEEP: VAULT", file.getString("title"));
        assertEquals(3, file.entry("title").line);
        assertEquals(1.5f, file.getFloat("speed"), 0f);
        assertEquals(5, file.entry("speed").line);
        assertEquals("value // not a comment", file.getString("note"));
        assertEquals(7, file.entry("note").line);
        assertEquals("no line ending", file.getString("last"));
        assertEquals(8, file.entry("last").line);
        assertEquals(8, file.bodyStart());
    }

    @Test
    void aRepeatedKeyKeepsEveryLineInFileOrder() {
        TuningFile file = TuningFile.parseText(FILE,
            "hint: FIRST\nspeed: 2\nhint: SECOND: MORE\n// comment\nhint: THIRD\n",
            Collections.singletonList("speed"), Collections.singletonList("hint"));

        assertEquals(Arrays.asList("FIRST", "SECOND: MORE", "THIRD"), file.getStrings("hint"));
        assertEquals(Arrays.asList("FIRST", "SECOND: MORE", "THIRD"), file.getDisplayStrings("hint", FONT));
        List<TuningFile.Entry> entries = file.entries("hint");
        assertEquals(3, entries.size());
        assertEquals("hint", entries.get(1).key);
        assertEquals("SECOND: MORE", entries.get(1).value);
        assertEquals(3, entries.get(1).line);
        assertEquals(5, entries.get(2).line);
        assertThrows(UnsupportedOperationException.class, () -> file.getStrings("hint").add("x"));
        assertThrows(UnsupportedOperationException.class, () -> entries.remove(0));
    }

    @Test
    void aLineThatIsNotKeyValueNamesTheFileAndLine() {
        assertError("Expected 'key: value' line in " + FILE + " at line 2",
            () -> TuningFile.parseText(FILE, "speed: 1\nspeed = 2\n", "speed"));
        assertError("Expected 'key: value' line in " + FILE + " at line 1",
            () -> TuningFile.parseText(FILE, ": 2\n", "speed"));
    }

    @Test
    void anUnknownKeyNamesTheFileKeyAndLine() {
        assertError("Unknown key 'sped' in " + FILE + " at line 3",
            () -> TuningFile.parseText(FILE, "// typo below\ncount: 1\nsped: 2\n", "speed", "count"));
        // Keys are case-sensitive, and a file may hold no key that was not declared.
        assertError("Unknown key 'Speed' in " + FILE + " at line 1",
            () -> TuningFile.parseText(FILE, "Speed: 2\n", "speed"));
        assertError("Unknown key 'speed' in " + FILE + " at line 1", () -> TuningFile.parseText(FILE, "speed: 2\n"));
        assertError("Unknown key '' in " + FILE + " at line 1", () -> TuningFile.parseText(FILE, " : 2\n", "speed"));
    }

    @Test
    void aDuplicateKeyNamesTheFileKeyAndSecondLine() {
        assertError("Duplicate 'speed' key in " + FILE + " at line 4",
            () -> TuningFile.parseText(FILE, "speed: 1\ncount: 2\n\nspeed: 1\n", "speed", "count"));
    }

    @Test
    void aMissingKeyNamesTheFileAndKey() {
        assertError("Missing 'count' key in " + FILE,
            () -> TuningFile.parseText(FILE, "speed: 1\n", "speed", "count"));
        assertError("Missing 'speed' key in " + FILE, () -> TuningFile.parseText(FILE, "", "speed", "count"));
        assertError("Missing 'hint' key in " + FILE,
            () -> TuningFile.parseText(FILE, "speed: 1\n", Collections.singletonList("speed"),
                Collections.singletonList("hint")));
        // The lines are checked first: a typo is reported as the unknown key it is, not as the key it left missing.
        assertError("Unknown key 'cuont' in " + FILE + " at line 2",
            () -> TuningFile.parseText(FILE, "speed: 1\ncuont: 2\n", "speed", "count"));
    }

    @Test
    void anEmptyValueNamesTheFileKeyAndLine() {
        TuningFile file = TuningFile.parseText(FILE, "a: 1\nb:\nc:   \nc: x\n", Arrays.asList("a", "b"),
            Collections.singletonList("c"));

        assertError("Empty 'b' key in " + FILE + " at line 2", () -> file.getString("b"));
        assertError("Empty 'b' key in " + FILE + " at line 2", () -> file.getDisplayString("b", FONT));
        assertError("Empty 'b' key in " + FILE + " at line 2", () -> file.getFloat("b"));
        assertError("Empty 'b' key in " + FILE + " at line 2", () -> file.getInt("b"));
        assertError("Empty 'b' key in " + FILE + " at line 2", () -> file.getFloats("b"));
        assertError("Empty 'c' key in " + FILE + " at line 3", () -> file.getStrings("c"));
        assertError("Empty 'c' key in " + FILE + " at line 3", () -> file.getDisplayStrings("c", FONT));
        assertError("Expected 'true' or 'false' for 'b' in " + FILE + " at line 2, got ''",
            () -> file.getBoolean("b"));
        // The line is there for a caller that accepts an empty value.
        assertEquals("", file.entry("b").value);
        assertEquals("", file.entries("c").get(0).value);
    }

    @Test
    void anUnparsableFloatNamesTheFileKeyAndLine() {
        for (String bad : new String[] {"fast", "0.5f", "1,5", "0x10", "NaN", "Infinity", "1e99", "1 2", "--1",
            "1e", "."}) {
            assertError("Invalid float '" + bad + "' for 'v' in " + FILE + " at line 2",
                () -> one("v", bad).getFloat("v"));
        }
    }

    @Test
    void aNumberTooSmallForAFloatIsRejectedAndAWrittenZeroIsNot() {
        for (String bad : new String[] {"1e-46", "-1e-46", "0.5e-60", ".0001E-45", "100e-50"}) {
            assertError("Invalid float '" + bad + "' for 'v' in " + FILE + " at line 2",
                () -> one("v", bad).getFloat("v"));
        }
        assertError("Invalid float '1e-46' in the list for 'v' in " + FILE + " at line 2",
            () -> one("v", "1, 1e-46").getFloats("v"));
        for (String zero : new String[] {"0", "0.0", "-0", ".000", "0e5", "0.0E-99", "+00.00e10"}) {
            assertEquals(0f, one("v", zero).getFloat("v"), 0f, zero);
        }
        // The smallest float is still a float.
        assertEquals(Float.MIN_VALUE, one("v", "1.4e-45").getFloat("v"), 0f);
    }

    @Test
    void theLinesOfARepeatedKeyAreReadWithTheSameStrictGetters() {
        TuningFile file = TuningFile.parseText(FILE,
            "ring: 7, 0.12, 0\nring: 9, 0.5f, 1\nstep: 4\nstep: 2.5\nstep: 1e-46\nstep:\n",
            Collections.emptyList(), Arrays.asList("ring", "step"));
        List<TuningFile.Entry> rings = file.entries("ring");
        List<TuningFile.Entry> steps = file.entries("step");

        assertArrayEquals(new float[] {7f, 0.12f, 0f}, file.getFloats(rings.get(0)), 0f);
        assertArrayEquals(new float[] {7f, 0.12f, 0f}, file.getFloats(rings.get(0), 3), 0f);
        assertError("Invalid float '0.5f' in the list for 'ring' in " + FILE + " at line 2",
            () -> file.getFloats(rings.get(1)));
        assertError("Expected 2 floats for 'ring' in " + FILE + " at line 1, got 3",
            () -> file.getFloats(rings.get(0), 2));
        assertError("Invalid int '0.12' in the list for 'ring' in " + FILE + " at line 1",
            () -> file.getInts(rings.get(0)));

        assertEquals(4, file.getInt(steps.get(0)));
        assertEquals(4f, file.getFloat(steps.get(0)), 0f);
        assertEquals(2.5f, file.getFloat(steps.get(1)), 0f);
        assertArrayEquals(new int[] {4}, file.getInts(steps.get(0)));
        assertArrayEquals(new int[] {4}, file.getInts(steps.get(0), 1));
        assertError("Invalid int '2.5' for 'step' in " + FILE + " at line 4", () -> file.getInt(steps.get(1)));
        assertError("Invalid float '1e-46' for 'step' in " + FILE + " at line 5",
            () -> file.getFloat(steps.get(2)));
        assertError("Empty 'step' key in " + FILE + " at line 6", () -> file.getFloat(steps.get(3)));
        assertError("Empty 'step' key in " + FILE + " at line 6", () -> file.getInt(steps.get(3)));
        assertError("Empty 'step' key in " + FILE + " at line 6", () -> file.getFloats(steps.get(3)));
        assertError("Empty 'step' key in " + FILE + " at line 6", () -> file.getInts(steps.get(3)));
        // A single-valued getter does not pick a line of a repeated key.
        assertError("Key 'ring' is declared as repeated for " + FILE, () -> file.getFloats("ring", 3));
        assertError("Key 'ring' is declared as repeated for " + FILE, () -> file.getInts("ring"));
    }

    @Test
    void readsListsOfIntsAndReturnsAFreshArrayEachTime() {
        TuningFile file = TuningFile.parseText(FILE, "sizes: 3, -5,+8\nfalloff: 0.5, 1\n", "sizes", "falloff");

        assertArrayEquals(new int[] {3, -5, 8}, file.getInts("sizes"));
        assertArrayEquals(new int[] {3, -5, 8}, file.getInts("sizes", 3));
        assertError("Expected 2 ints for 'sizes' in " + FILE + " at line 1, got 3", () -> file.getInts("sizes", 2));
        assertError("Invalid int '0.5' in the list for 'falloff' in " + FILE + " at line 2",
            () -> file.getInts("falloff"));
        assertError("Invalid int '' in the list for 'v' in " + FILE + " at line 2",
            () -> one("v", "1,,2").getInts("v"));
        assertError("Invalid int '2147483648' in the list for 'v' in " + FILE + " at line 2",
            () -> one("v", "1, 2147483648").getInts("v"));

        // Changing a returned array changes nothing in the file.
        file.getInts("sizes")[0] = 99;
        file.getFloats("falloff")[0] = 99f;
        assertArrayEquals(new int[] {3, -5, 8}, file.getInts("sizes"));
        assertArrayEquals(new float[] {0.5f, 1f}, file.getFloats("falloff"), 0f);
    }

    @Test
    void anOptionalKeyMayBeLeftOutAndIsOtherwiseAKeyLikeAnyOther() {
        List<String> keys = Collections.singletonList("speed");
        List<String> repeated = Collections.singletonList("hint");
        List<String> optional = Arrays.asList("loadout", "bonus");
        TuningFile with = TuningFile.parseText(FILE, "speed: 1\nhint: A\nloadout: pistol, rail\n", keys, repeated,
            optional);

        assertTrue(with.has("loadout"));
        assertEquals("pistol, rail", with.getString("loadout"));
        assertEquals(3, with.entry("loadout").line);
        assertEquals(1, with.entries("loadout").size());
        assertTrue(with.has("speed"));
        assertTrue(with.has("hint"));

        assertFalse(with.has("bonus"));
        assertEquals(Collections.emptyList(), with.entries("bonus"));
        assertEquals(Collections.emptyList(), with.getStrings("bonus"));
        // Reading an optional key that is not there is a mistake in the calling code: it asks has() first.
        assertError("Missing 'bonus' key in " + FILE, () -> with.getFloat("bonus"));
        assertError("Missing 'bonus' key in " + FILE, () -> with.entry("bonus"));
        assertError("Key 'extra' is not declared for " + FILE, () -> with.has("extra"));

        // Unknown and duplicate keys still fail, and so does a required key left out.
        assertError("Unknown key 'loadouts' in " + FILE + " at line 3",
            () -> TuningFile.parseText(FILE, "speed: 1\nhint: A\nloadouts: pistol\n", keys, repeated, optional));
        assertError("Duplicate 'loadout' key in " + FILE + " at line 2",
            () -> TuningFile.parseText(FILE, "loadout: pistol\nloadout: rail\n", keys, repeated, optional));
        assertError("Missing 'speed' key in " + FILE,
            () -> TuningFile.parseText(FILE, "hint: A\nloadout: pistol\n", keys, repeated, optional));
        assertError("Key 'speed' is declared twice for " + FILE,
            () -> TuningFile.parseText(FILE, "speed: 1\n", keys, Collections.emptyList(), keys));
    }

    @Test
    void anOptionalHeaderMayBeLeftOutOfAHeaderBlock() {
        List<String> keys = Collections.singletonList("id");
        List<String> none = Collections.emptyList();
        List<String> optional = Collections.singletonList("loadout");

        TuningFile with = TuningFile.parseHeader("levels/x.map", Arrays.asList("id: s1", "loadout: pistol", "", ">"),
            "stage", "map layout", keys, none, optional);
        assertTrue(with.has("loadout"));
        assertEquals("pistol", with.getString("loadout"));
        assertEquals(3, with.bodyStart());

        TuningFile without = TuningFile.parseHeader("levels/x.map", Arrays.asList("id: s1", "", ">"), "stage",
            "map layout", keys, none, optional);
        assertFalse(without.has("loadout"));
        assertEquals(2, without.bodyStart());
        assertError("Missing 'loadout' header in levels/x.map", () -> without.getString("loadout"));
        assertError("Duplicate 'loadout' header in levels/x.map at line 3",
            () -> TuningFile.parseHeader("levels/x.map", Arrays.asList("id: s1", "loadout: a", "loadout: b"),
                "stage", "map layout", keys, none, optional));
    }

    @Test
    void aDeclaredKeyThatNoLineCanMatchIsRejectedWhenDeclared() {
        Gdx.files = null;
        String cannotMatch = " can match no line: a key is not empty, has no space around it and holds no colon";
        assertError("Key 'a:b' declared for " + FILE + cannotMatch, () -> TuningFile.parseText(FILE, "", "a:b"));
        assertError("Key ' speed' declared for " + FILE + cannotMatch,
            () -> TuningFile.parseText(FILE, "", " speed"));
        assertError("Key 'speed ' declared for " + FILE + cannotMatch,
            () -> TuningFile.parseText(FILE, "", "ok", "speed "));
        assertError("Key '' declared for " + FILE + cannotMatch, () -> TuningFile.parseText(FILE, "", ""));
        assertError("Key '' declared for " + FILE + cannotMatch,
            () -> TuningFile.parseText(FILE, "", Collections.emptyList(), Collections.singletonList("")));
        assertError("Key 'a:b' declared for " + FILE + cannotMatch,
            () -> TuningFile.parseText(FILE, "", Collections.emptyList(), Collections.emptyList(),
                Collections.singletonList("a:b")));
        assertError("A key declared for " + FILE + " is null",
            () -> TuningFile.parseText(FILE, "speed: 1\n", "speed", null));
        assertError("A key declared for " + SAMPLE_PATH + " is null",
            () -> TuningFile.load(SAMPLE_PATH, "speed", null));
    }

    @Test
    void anUnparsableIntNamesTheFileKeyAndLine() {
        for (String bad : new String[] {"eight", "8.0", "8f", "0x8", "2147483648", "-2147483649", "1e3", "8 9"}) {
            assertError("Invalid int '" + bad + "' for 'v' in " + FILE + " at line 2",
                () -> one("v", bad).getInt("v"));
        }
    }

    @Test
    void anUnparsableBooleanNamesTheFileKeyAndLine() {
        for (String bad : new String[] {"yes", "TRUE", "False", "1", "true false"}) {
            assertError("Expected 'true' or 'false' for 'v' in " + FILE + " at line 2, got '" + bad + "'",
                () -> one("v", bad).getBoolean("v"));
        }
    }

    @Test
    void anUnparsableFloatListNamesTheFileKeyAndLine() {
        assertError("Invalid float 'x' in the list for 'v' in " + FILE + " at line 2",
            () -> one("v", "0.5, x, 1").getFloats("v"));
        assertError("Invalid float '' in the list for 'v' in " + FILE + " at line 2",
            () -> one("v", "0.5, , 1").getFloats("v"));
        assertError("Invalid float '' in the list for 'v' in " + FILE + " at line 2",
            () -> one("v", "0.5, 1,").getFloats("v"));
        assertError("Invalid float '0.5 1' in the list for 'v' in " + FILE + " at line 2",
            () -> one("v", "0.5 1").getFloats("v"));
        assertError("Expected 3 floats for 'v' in " + FILE + " at line 2, got 2",
            () -> one("v", "0.5, 1").getFloats("v", 3));
    }

    @Test
    void displayTextWithACharacterTheFontLacksNamesTheFileKeyAndLine() {
        TuningFile file = TuningFile.parseText(FILE, "title: under_score\nhint: FINE\nhint: 50% \u00E9\n",
            Collections.singletonList("title"), Collections.singletonList("hint"));

        assertEquals("under_score", file.getString("title"));
        assertError("Unsupported PixelFont character '_' (U+005F) in 'title' in " + FILE + " at line 1",
            () -> file.getDisplayString("title", FONT));
        assertError("Unsupported PixelFont character '\u00E9' (U+00E9) in 'hint' in " + FILE + " at line 3",
            () -> file.getDisplayStrings("hint", FONT));
    }

    @Test
    void aCallerReportsAValueOutOfRangeWithTheFileKeyAndLine() {
        TuningFile file = TuningFile.parseText(FILE, "speed: -2\nhint: A\nhint: B\n",
            Collections.singletonList("speed"), Collections.singletonList("hint"));

        assertEquals("Invalid 'speed' in " + FILE + " at line 1: must be positive, got '-2'",
            file.invalid("speed", "must be positive").getMessage());
        assertEquals("Invalid 'hint' in " + FILE + " at line 3: must not be B, got 'B'",
            file.invalid(file.entries("hint").get(1), "must not be B").getMessage());
    }

    @Test
    void mistakesInTheCallingCodeAreReportedToo() {
        TuningFile file = TuningFile.parseText(FILE, "speed: 1\nhint: A\n", Collections.singletonList("speed"),
            Collections.singletonList("hint"));

        assertError("Key 'sped' is not declared for " + FILE, () -> file.getFloat("sped"));
        assertError("Key 'sped' is not declared for " + FILE, () -> file.entries("sped"));
        assertError("Key 'hint' is declared as repeated for " + FILE, () -> file.getString("hint"));
        assertError("Key 'hint' is declared as repeated for " + FILE, () -> file.entry("hint"));
        assertError("Key 'a' is declared twice for " + FILE, () -> TuningFile.parseText(FILE, "a: 1\n", "a", "a"));
        assertError("Key 'a' is declared twice for " + FILE,
            () -> TuningFile.parseText(FILE, "a: 1\n", Collections.singletonList("a"), Collections.singletonList("a")));
        assertError("Tuning text for " + FILE + " is null", () -> TuningFile.parseText(FILE, null, "a"));
    }

    @Test
    void aHeaderBlockEndsAtTheFirstBlankLineAfterAHeaderAndCallsItsKeysHeaders() {
        List<String> lines = Arrays.asList("// comment", "", "id: s1", "// comment", "note: A", "note: B", "",
            "body: not a header", "");
        TuningFile header = TuningFile.parseHeader("levels/x.map", lines, "stage", "map layout",
            Collections.singletonList("id"), Collections.singletonList("note"));

        assertEquals("s1", header.getString("id"));
        assertEquals(Arrays.asList("A", "B"), header.getStrings("note"));
        assertEquals(7, header.bodyStart());
        assertEquals("body: not a header", lines.get(header.bodyStart()));

        List<String> none = Collections.emptyList();
        assertEquals(1, TuningFile.parseHeader("levels/x.map", Collections.singletonList("id: s1"), "stage",
            "map layout", Collections.singletonList("id"), none).bodyStart());
        assertError("Expected 'key: value' stage header or blank line before map layout in levels/x.map at line 2",
            () -> TuningFile.parseHeader("levels/x.map", Arrays.asList("id: s1", "#####"), "stage", "map layout",
                Collections.singletonList("id"), none));
        assertError("Unknown stage header 'author' in levels/x.map at line 1",
            () -> TuningFile.parseHeader("levels/x.map", Collections.singletonList("author: me"), "stage",
                "map layout", Collections.singletonList("id"), none));
        assertError("Duplicate 'id' header in levels/x.map at line 2",
            () -> TuningFile.parseHeader("levels/x.map", Arrays.asList("id: a", "id: b"), "stage", "map layout",
                Collections.singletonList("id"), none));
        assertError("Missing 'title' header in levels/x.map",
            () -> TuningFile.parseHeader("levels/x.map", Arrays.asList("", "id: a"), "stage", "map layout",
                Arrays.asList("id", "title"), none));
        assertError("Empty 'id' header in levels/x.map at line 1",
            () -> TuningFile.parseHeader("levels/x.map", Collections.singletonList("id:"), "stage", "map layout",
                Collections.singletonList("id"), none).getString("id"));
    }

    @Test
    void loadsTheSampleFileWithoutLibgdx() {
        Gdx.files = null;
        TuningFile file = TuningFile.load(SAMPLE_PATH, SAMPLE_KEYS, SAMPLE_REPEATED_KEYS);

        assertEquals(SAMPLE_PATH, file.path());
        assertEquals(0.3f, file.getFloat("pistol.fire_interval"), 0f);
        assertEquals(8, file.getInt("pistol.pellets"));
        assertFalse(file.getBoolean("pistol.auto"));
        assertEquals("SERVICE PISTOL", file.getDisplayString("pistol.name", FONT));
        assertArrayEquals(new float[] {0.60f, 1.10f, 1.27f}, file.getFloats("pistol.falloff", 3), 0f);
        assertEquals(Arrays.asList("HOLD FIRE TO CHARGE.", "RELEASE TO LAUNCH."), file.getDisplayStrings("hint", FONT));
    }

    @Test
    void anEditedLooseCopyOfTheSampleFileChangesTheLoadedValue(@TempDir Path temp) throws IOException {
        Gdx.files = null;
        String shipped = AssetText.read("data", SAMPLE_PATH);
        assertTrue(shipped.contains("pistol.fire_interval: 0.3"), shipped);
        Path loose = temp.resolve(SAMPLE_PATH);
        java.nio.file.Files.createDirectories(loose.getParent());
        java.nio.file.Files.write(loose,
            shipped.replace("pistol.fire_interval: 0.3", "pistol.fire_interval: 0.45")
                .getBytes(StandardCharsets.UTF_8));
        System.setProperty("gamex.assets", temp.toString());

        // No class is recompiled between the two loads: the loose file is read in place of the classpath copy.
        TuningFile edited = TuningFile.load(SAMPLE_PATH, SAMPLE_KEYS, SAMPLE_REPEATED_KEYS);
        assertEquals(0.45f, edited.getFloat("pistol.fire_interval"), 0f);
        assertEquals(8, edited.getInt("pistol.pellets"));

        // A mistake made while editing is reported with the path the file was asked for by.
        java.nio.file.Files.write(loose,
            shipped.replace("pistol.pellets: 8", "pistol.pelets: 8").getBytes(StandardCharsets.UTF_8));
        assertError("Unknown key 'pistol.pelets' in " + SAMPLE_PATH + " at line 4",
            () -> TuningFile.load(SAMPLE_PATH, SAMPLE_KEYS, SAMPLE_REPEATED_KEYS));
    }

    @Test
    void loadsThroughGdxInternalFilesWhenLibgdxIsUp() {
        Gdx.files = (Files) Proxy.newProxyInstance(
            Files.class.getClassLoader(),
            new Class<?>[] {Files.class},
            (proxy, method, args) -> {
                if ("internal".equals(method.getName()) && args != null && args.length == 1) {
                    return new FileHandle((String) args[0]) {
                        @Override
                        public String readString(String charset) {
                            return "speed: 9.5\n";
                        }
                    };
                }
                return null;
            });

        assertEquals(9.5f, TuningFile.load("data/from-gdx.txt", "speed").getFloat("speed"), 0f);
    }

    @Test
    void aMissingFileIsReportedWithThePathsTried() {
        Gdx.files = null;
        IllegalStateException e = assertThrows(IllegalStateException.class,
            () -> TuningFile.load("data/no-such-file.txt", "speed"));
        assertTrue(e.getMessage().startsWith("The data asset data/no-such-file.txt was not found."), e.getMessage());
        assertTrue(e.getMessage().contains("Tried: ["), e.getMessage());
    }
}

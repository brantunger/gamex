package io.github.brantunger.gamex.data;

import io.github.brantunger.gamex.hud.GlyphCheck;
import io.github.brantunger.gamex.hud.PixelFont;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collection;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.regex.Pattern;

/**
 * A parsed {@code key: value} text file from {@code assets/}: the shared format of the tuning files in
 * {@code assets/data/} and of the stage headers in {@code assets/levels/}.
 *
 * <h2>Format</h2>
 * <pre>
 * // A line whose trimmed text begins with two slashes is a comment.
 * pistol.fire_interval: 0.3
 * pistol.pellets: 8
 * pistol.auto: false
 * pistol.name: SERVICE PISTOL
 * pistol.falloff: 0.60, 1.10, 1.27
 * hint: A KEY DECLARED AS REPEATED
 * hint: MAY APPEAR ON SEVERAL LINES.
 * </pre>
 * The key is the text before the first colon and the value is the rest of the line, both trimmed, so a value may
 * hold colons. A key is matched as a whole string and its case matters; a dot is part of the name and nothing more.
 * Blank lines are skipped. A comment takes a whole line: there are no comments after a value, and {@code //} there
 * is part of the value. A leading byte order mark is dropped and CR, LF and CRLF each end a line (see
 * {@link AssetText#splitLines}).
 *
 * <h2>Error policy: fail loudly</h2>
 * The code that loads a file declares every key the file may hold: the keys it must hold on exactly one line, the
 * keys it must hold on one line or more (repeated keys), and the keys it may hold on one line or leave out (optional
 * keys, which the caller asks about with {@link #has}). No value has a default here, so a mistyped key cannot leave
 * a value silently unchanged. Every mistake throws {@link IllegalArgumentException}:
 * <ul>
 *   <li>{@link #load}, {@link #parseText} and {@link #parseHeader} stop on the first line that is not
 *       {@code key: value} (the message names the file and the line; there is no key to name), that has an
 *       undeclared key, or that is a second line for a key not declared as repeated (file, key and line). When
 *       every line passes, they stop on a required key that has no line (file and key; there is no line to
 *       name).</li>
 *   <li>A getter stops on an empty value and on a value that is not of the form it reads (file, key and
 *       line).</li>
 *   <li>Mistakes in the calling code throw too: declaring a key twice, declaring a key no line can match (empty,
 *       not trimmed or holding a colon), asking for a key that was not declared, asking a single-value getter for
 *       a repeated key, and reading an optional key the file leaves out.</li>
 * </ul>
 * Getters check the form of a value only. Whether a number is in range (positive, at most one, ...) is for the
 * caller to decide; it reports a bad one with {@link #invalid(String, String)}, which names the file, key and line
 * the same way.
 *
 * <h2>How values reach the code</h2>
 * A loaded value cannot be a {@code static final} constant. Each tuning file has an immutable class with a final
 * field per value (public, except that an array is private with an accessor) and a {@code load()} method that
 * builds it from a {@code TuningFile}. The
 * game loads each once at start-up ({@code MainGame.create()} or later, never from a static initialiser) and passes
 * it to the classes that use it, through a constructor or a {@code GameSimulation} field; tests build their own
 * values with the constructor and read no file, or check {@code load()}'s rules by handing text to
 * {@link #parseText}.
 * <pre>
 * public final class PistolTuning {
 *     public final float fireInterval;
 *     public final int pellets;
 *     private final float[] falloff;
 *
 *     public PistolTuning(float fireInterval, int pellets, float[] falloff) { ... }
 *
 *     public float falloff(int step) { return falloff[step]; }
 *
 *     public static PistolTuning load() {
 *         TuningFile file = TuningFile.load("data/pistol.txt",
 *             List.of("pistol.fire_interval", "pistol.pellets", "pistol.auto", "pistol.name", "pistol.falloff"),
 *             List.of("hint"));
 *         float fireInterval = file.getFloat("pistol.fire_interval");
 *         if (fireInterval &lt;= 0f) throw file.invalid("pistol.fire_interval", "must be positive");
 *         return new PistolTuning(fireInterval, file.getInt("pistol.pellets"),
 *             file.getFloats("pistol.falloff", 3));
 *     }
 * }
 * </pre>
 * {@link #getFloats} and {@link #getInts} return a fresh array on every call. A tuning object keeps such an array
 * in a private field and gives out an indexed getter or a copy, so that it stays immutable.
 *
 * <p>Instances are immutable and safe to share.
 */
public final class TuningFile {

    private static final Logger log = LoggerFactory.getLogger(TuningFile.class);

    private static final Pattern DECIMAL = Pattern.compile("[+-]?([0-9]+(\\.[0-9]*)?|\\.[0-9]+)([eE][+-]?[0-9]+)?");
    private static final Pattern INTEGER = Pattern.compile("[+-]?[0-9]+");
    /** The digits of a decimal number before its exponent hold one that is not zero. */
    private static final Pattern NON_ZERO_MANTISSA = Pattern.compile("[^eE]*[1-9].*");

    /** One {@code key: value} line of a file. */
    public static final class Entry {
        /** The trimmed text before the line's first colon. */
        public final String key;
        /** The trimmed text after the line's first colon; it may be empty. */
        public final String value;
        /** The line's number in the file, starting at 1. */
        public final int line;

        Entry(String key, String value, int line) {
            this.key = key;
            this.value = value;
            this.line = line;
        }
    }

    private final String path;
    /** What a key is called in error messages: "key", or "header" for a header block. */
    private final String noun;
    private final Set<String> repeatedKeys;
    private final Set<String> optionalKeys;
    private final Map<String, List<Entry>> entries;
    private final int bodyStart;

    private TuningFile(String path, String noun, Set<String> repeatedKeys, Set<String> optionalKeys,
                       Map<String, List<Entry>> entries, int bodyStart) {
        this.path = path;
        this.noun = noun;
        this.repeatedKeys = repeatedKeys;
        this.optionalKeys = optionalKeys;
        this.entries = entries;
        this.bodyStart = bodyStart;
    }

    /**
     * Reads and parses a file of single-valued keys from {@code assets/} (see {@link AssetText#read} for where it
     * is looked for).
     * @param assetPath internal path inside {@code assets/} (for example {@code "data/pistol.txt"})
     * @param keys every key the file must hold, each on exactly one line
     * @return the parsed file
     * @throws IllegalArgumentException if the file breaks the format or its keys are not exactly {@code keys}
     * @throws com.badlogic.gdx.utils.GdxRuntimeException if libGDX is up and the file is missing or unreadable
     * @throws IllegalStateException if libGDX is not up and the file is found nowhere
     * @throws java.io.UncheckedIOException if libGDX is not up and the file is found and cannot be read
     */
    public static TuningFile load(String assetPath, String... keys) {
        return load(assetPath, Arrays.asList(keys), Collections.emptyList(), Collections.emptyList());
    }

    /**
     * Reads and parses a file from {@code assets/} (see {@link AssetText#read} for where it is looked for).
     * @param assetPath internal path inside {@code assets/} (for example {@code "data/pistol.txt"})
     * @param keys the keys the file must hold on exactly one line each
     * @param repeatedKeys the keys the file must hold on one line or more
     * @return the parsed file
     * @throws IllegalArgumentException if the file breaks the format or its keys are not the declared ones
     * @throws com.badlogic.gdx.utils.GdxRuntimeException if libGDX is up and the file is missing or unreadable
     * @throws IllegalStateException if libGDX is not up and the file is found nowhere
     * @throws java.io.UncheckedIOException if libGDX is not up and the file is found and cannot be read
     */
    public static TuningFile load(String assetPath, Collection<String> keys, Collection<String> repeatedKeys) {
        return load(assetPath, keys, repeatedKeys, Collections.emptyList());
    }

    /**
     * Reads and parses a file with optional keys from {@code assets/} (see {@link AssetText#read} for where it is
     * looked for).
     * @param assetPath internal path inside {@code assets/} (for example {@code "data/pistol.txt"})
     * @param keys the keys the file must hold on exactly one line each
     * @param repeatedKeys the keys the file must hold on one line or more
     * @param optionalKeys the keys the file may hold on one line or leave out; see {@link #has}
     * @return the parsed file
     * @throws IllegalArgumentException if the file breaks the format or its keys are not the declared ones
     * @throws com.badlogic.gdx.utils.GdxRuntimeException if libGDX is up and the file is missing or unreadable
     * @throws IllegalStateException if libGDX is not up and the file is found nowhere
     * @throws java.io.UncheckedIOException if libGDX is not up and the file is found and cannot be read
     */
    public static TuningFile load(String assetPath, Collection<String> keys, Collection<String> repeatedKeys,
                                  Collection<String> optionalKeys) {
        TuningFile file = parseText(assetPath, AssetText.read("data", assetPath), keys, repeatedKeys, optionalKeys);
        log.debug("Loaded {} keys from {}", file.entries.size(), assetPath);
        return file;
    }

    /**
     * Parses the text of a file of single-valued keys; nothing is read from disk.
     * @param assetPath path label included in error messages
     * @param text raw file contents
     * @param keys every key the file must hold, each on exactly one line
     * @return the parsed file
     * @throws IllegalArgumentException if the text breaks the format or its keys are not exactly {@code keys}
     */
    public static TuningFile parseText(String assetPath, String text, String... keys) {
        return parseText(assetPath, text, Arrays.asList(keys), Collections.emptyList(), Collections.emptyList());
    }

    /**
     * Parses the text of a file; nothing is read from disk.
     * @param assetPath path label included in error messages
     * @param text raw file contents
     * @param keys the keys the file must hold on exactly one line each
     * @param repeatedKeys the keys the file must hold on one line or more
     * @return the parsed file
     * @throws IllegalArgumentException if the text breaks the format or its keys are not the declared ones
     */
    public static TuningFile parseText(String assetPath, String text, Collection<String> keys,
                                       Collection<String> repeatedKeys) {
        return parseText(assetPath, text, keys, repeatedKeys, Collections.emptyList());
    }

    /**
     * Parses the text of a file with optional keys; nothing is read from disk.
     * @param assetPath path label included in error messages
     * @param text raw file contents
     * @param keys the keys the file must hold on exactly one line each
     * @param repeatedKeys the keys the file must hold on one line or more
     * @param optionalKeys the keys the file may hold on one line or leave out; see {@link #has}
     * @return the parsed file
     * @throws IllegalArgumentException if the text breaks the format or its keys are not the declared ones
     */
    public static TuningFile parseText(String assetPath, String text, Collection<String> keys,
                                       Collection<String> repeatedKeys, Collection<String> optionalKeys) {
        if (text == null) {
            throw new IllegalArgumentException("Tuning text for " + assetPath + " is null");
        }
        return parse(assetPath, AssetText.splitLines(text), false, "key", "key", "'key: value' line", keys,
            repeatedKeys, optionalKeys);
    }

    /**
     * Parses a header block at the top of a file that goes on with something else, as a stage file goes on with
     * its map layout. Blank lines and comments before the first header are skipped; the first blank line after a
     * header ends the block, and {@link #bodyStart()} tells where the rest begins. The messages for a duplicate,
     * a missing and an empty key call the key a "header", an unknown key is an "Unknown {@code kind} header", and a
     * line that is not {@code key: value} gives "Expected 'key: value' {@code kind} header or blank line before
     * {@code body}". The messages for a value of the wrong form are those of a whole file.
     * @param assetPath path label included in error messages
     * @param lines the file's lines, from {@link AssetText#splitLines}
     * @param kind what the headers describe, in one word used by the error messages (for example {@code "stage"})
     * @param body what follows the block, for the error messages (for example {@code "map layout"})
     * @param keys the headers the block must hold on exactly one line each
     * @param repeatedKeys the headers the block must hold on one line or more
     * @return the parsed header block
     * @throws IllegalArgumentException if the block breaks the format or its keys are not the declared ones
     */
    public static TuningFile parseHeader(String assetPath, List<String> lines, String kind, String body,
                                         Collection<String> keys, Collection<String> repeatedKeys) {
        return parseHeader(assetPath, lines, kind, body, keys, repeatedKeys, Collections.emptyList());
    }

    /**
     * Parses a header block with optional headers; see
     * {@link #parseHeader(String, List, String, String, Collection, Collection)}.
     * @param assetPath path label included in error messages
     * @param lines the file's lines, from {@link AssetText#splitLines}
     * @param kind what the headers describe, in one word used by the error messages (for example {@code "stage"})
     * @param body what follows the block, for the error messages (for example {@code "map layout"})
     * @param keys the headers the block must hold on exactly one line each
     * @param repeatedKeys the headers the block must hold on one line or more
     * @param optionalKeys the headers the block may hold on one line or leave out; see {@link #has}
     * @return the parsed header block
     * @throws IllegalArgumentException if the block breaks the format or its keys are not the declared ones
     */
    public static TuningFile parseHeader(String assetPath, List<String> lines, String kind, String body,
                                         Collection<String> keys, Collection<String> repeatedKeys,
                                         Collection<String> optionalKeys) {
        return parse(assetPath, lines, true, "header", kind + " header",
            "'key: value' " + kind + " header or blank line before " + body, keys, repeatedKeys, optionalKeys);
    }

    private static TuningFile parse(String path, List<String> lines, boolean headerBlock, String noun,
                                    String unknownNoun, String expected, Collection<String> keys,
                                    Collection<String> repeatedKeys, Collection<String> optionalKeys) {
        Set<String> declared = new LinkedHashSet<>();
        Set<String> required = declare(path, keys, declared);
        Set<String> repeated = declare(path, repeatedKeys, declared);
        Set<String> optional = declare(path, optionalKeys, declared);
        required.addAll(repeated);

        Map<String, List<Entry>> entries = new LinkedHashMap<>();
        int index = 0;
        while (index < lines.size()) {
            String line = lines.get(index);
            int lineNumber = index + 1;
            index++;
            String trimmed = line.trim();
            if (AssetText.isComment(trimmed)) continue;
            if (trimmed.isEmpty()) {
                if (headerBlock && !entries.isEmpty()) break;
                continue;
            }
            int colon = line.indexOf(':');
            if (colon <= 0) {
                throw new IllegalArgumentException("Expected " + expected + " in " + path + " at line " + lineNumber);
            }
            String key = line.substring(0, colon).trim();
            String value = line.substring(colon + 1).trim();
            if (!declared.contains(key)) {
                throw new IllegalArgumentException("Unknown " + unknownNoun + " '" + key + "' in " + path
                    + " at line " + lineNumber);
            }
            List<Entry> sameKey = entries.computeIfAbsent(key, k -> new ArrayList<>());
            if (!repeated.contains(key) && !sameKey.isEmpty()) {
                throw new IllegalArgumentException("Duplicate '" + key + "' " + noun + " in " + path
                    + " at line " + lineNumber);
            }
            sameKey.add(new Entry(key, value, lineNumber));
        }

        for (String key : required) {
            if (!entries.containsKey(key)) {
                throw new IllegalArgumentException("Missing '" + key + "' " + noun + " in " + path);
            }
        }
        return new TuningFile(path, noun, repeated, optional, entries, index);
    }

    /** Checks one group of declared keys and adds it to all those declared so far. */
    private static Set<String> declare(String path, Collection<String> keys, Set<String> declared) {
        Set<String> group = new LinkedHashSet<>();
        for (String key : keys) {
            if (key == null) {
                throw new IllegalArgumentException("A key declared for " + path + " is null");
            }
            if (key.isEmpty() || key.indexOf(':') >= 0 || !key.equals(key.trim())) {
                throw new IllegalArgumentException("Key '" + key + "' declared for " + path
                    + " can match no line: a key is not empty, has no space around it and holds no colon");
            }
            if (!declared.add(key)) {
                throw new IllegalArgumentException("Key '" + key + "' is declared twice for " + path);
            }
            group.add(key);
        }
        return group;
    }

    /** @return the path label the file was parsed with, as error messages give it. */
    public String path() {
        return path;
    }

    /**
     * @return the index, in the lines given to {@link #parseHeader}, of the first line after the header block and
     *     the blank line that ends it; the number of lines when the file was parsed whole
     */
    public int bodyStart() {
        return bodyStart;
    }

    /**
     * Tells whether the file holds a key. It is true for every required and repeated key, so it is of use for the
     * optional ones.
     * @param key a declared key
     * @return whether the key has a line
     */
    public boolean has(String key) {
        return !entries(key).isEmpty();
    }

    /**
     * Returns the line of a single-valued key as written, for a caller that checks the value itself.
     * @param key a key declared as required or optional
     * @return the key's line; its value may be empty
     * @throws IllegalArgumentException if the key is an optional one the file leaves out; ask {@link #has} first
     */
    public Entry entry(String key) {
        List<Entry> found = entries(key);
        if (repeatedKeys.contains(key)) {
            throw new IllegalArgumentException("Key '" + key + "' is declared as repeated for " + path);
        }
        if (found.isEmpty()) {
            throw new IllegalArgumentException("Missing '" + key + "' " + noun + " in " + path);
        }
        return found.get(0);
    }

    /**
     * Returns every line of a key as written, in file order.
     * @param key a declared key
     * @return the key's lines: one for a required key, one or more for a repeated key, and one or none for an
     *     optional key; their values may be empty
     */
    public List<Entry> entries(String key) {
        List<Entry> found = entries.get(key);
        if (found == null) {
            if (optionalKeys.contains(key)) return Collections.emptyList();
            throw new IllegalArgumentException("Key '" + key + "' is not declared for " + path);
        }
        return Collections.unmodifiableList(found);
    }

    /**
     * @param key a key declared as required or optional
     * @return the key's value
     * @throws IllegalArgumentException if the value is empty
     */
    public String getString(String key) {
        return nonEmpty(entry(key));
    }

    /**
     * @param key a declared key of any kind
     * @return the value of each of the key's lines, in file order
     * @throws IllegalArgumentException if a value is empty
     */
    public List<String> getStrings(String key) {
        List<String> values = new ArrayList<>();
        for (Entry entry : entries(key)) {
            values.add(nonEmpty(entry));
        }
        return Collections.unmodifiableList(values);
    }

    /**
     * Reads player-visible text.
     * @param key a key declared as required or optional
     * @param font the loaded font the text will be drawn with
     * @return the key's value
     * @throws IllegalArgumentException if the value is empty or holds a character the font cannot draw (see
     *     {@link GlyphCheck})
     */
    public String getDisplayString(String key, PixelFont font) {
        Entry entry = entry(key);
        return GlyphCheck.require(font, nonEmpty(entry), where(entry));
    }

    /**
     * Reads player-visible lines of text.
     * @param key a declared key of any kind
     * @param font the loaded font the text will be drawn with
     * @return the value of each of the key's lines, in file order
     * @throws IllegalArgumentException if a value is empty or holds a character the font cannot draw (see
     *     {@link GlyphCheck})
     */
    public List<String> getDisplayStrings(String key, PixelFont font) {
        List<String> values = new ArrayList<>();
        for (Entry entry : entries(key)) {
            values.add(GlyphCheck.require(font, nonEmpty(entry), where(entry)));
        }
        return Collections.unmodifiableList(values);
    }

    /**
     * Reads a decimal number such as {@code 12}, {@code -0.5}, {@code .25} or {@code 1.5e-3}. A type suffix
     * ({@code 0.5f}), a hexadecimal form, {@code NaN}, a number too large for a float and a number other than zero
     * too small for one ({@code 1e-46}) are all rejected, so the result is always finite and is zero only when
     * zero was written.
     * @param key a key declared as required or optional
     * @return the key's value
     * @throws IllegalArgumentException if the value is empty or not such a number
     */
    public float getFloat(String key) {
        return getFloat(entry(key));
    }

    /**
     * Reads one line's value the way {@link #getFloat(String)} does.
     * @param entry a line from {@link #entry} or {@link #entries}
     * @return the line's value
     * @throws IllegalArgumentException if the value is empty or not such a number
     */
    public float getFloat(Entry entry) {
        String value = nonEmpty(entry);
        Float parsed = parseFloat(value);
        if (parsed == null) {
            throw new IllegalArgumentException("Invalid float '" + value + "' for " + where(entry));
        }
        return parsed;
    }

    /**
     * Reads a whole number in decimal digits with an optional sign.
     * @param key a key declared as required or optional
     * @return the key's value
     * @throws IllegalArgumentException if the value is empty, not such a number or outside the range of an int
     */
    public int getInt(String key) {
        return getInt(entry(key));
    }

    /**
     * Reads one line's value the way {@link #getInt(String)} does.
     * @param entry a line from {@link #entry} or {@link #entries}
     * @return the line's value
     * @throws IllegalArgumentException if the value is empty, not such a number or outside the range of an int
     */
    public int getInt(Entry entry) {
        String value = nonEmpty(entry);
        Integer parsed = parseInt(value);
        if (parsed == null) {
            throw new IllegalArgumentException("Invalid int '" + value + "' for " + where(entry));
        }
        return parsed;
    }

    /**
     * @param key a key declared as required or optional
     * @return whether the key's value is {@code true}
     * @throws IllegalArgumentException if the value is neither {@code true} nor {@code false}, in lower case
     */
    public boolean getBoolean(String key) {
        Entry entry = entry(key);
        if ("true".equals(entry.value)) return true;
        if ("false".equals(entry.value)) return false;
        throw new IllegalArgumentException("Expected 'true' or 'false' for " + where(entry) + ", got '"
            + entry.value + "'");
    }

    /**
     * Reads a comma-separated list of numbers such as {@code 0.60, 1.10, 1.27}, each in the form
     * {@link #getFloat(String)} reads.
     * @param key a key declared as required or optional
     * @return the numbers, at least one, in a new array
     * @throws IllegalArgumentException if the value is empty or an item is not such a number
     */
    public float[] getFloats(String key) {
        return getFloats(entry(key));
    }

    /**
     * Reads a comma-separated list of exactly {@code count} numbers.
     * @param key a key declared as required or optional
     * @param count how many numbers the list must hold
     * @return the numbers, in a new array
     * @throws IllegalArgumentException if {@link #getFloats(String)} would throw, or the list is of another length
     */
    public float[] getFloats(String key, int count) {
        return getFloats(entry(key), count);
    }

    /**
     * Reads one line's value the way {@link #getFloats(String)} does.
     * @param entry a line from {@link #entry} or {@link #entries}
     * @return the numbers, at least one, in a new array
     * @throws IllegalArgumentException if the value is empty or an item is not such a number
     */
    public float[] getFloats(Entry entry) {
        String[] items = nonEmpty(entry).split(",", -1);
        float[] values = new float[items.length];
        for (int i = 0; i < items.length; i++) {
            String item = items[i].trim();
            Float parsed = parseFloat(item);
            if (parsed == null) {
                throw new IllegalArgumentException("Invalid float '" + item + "' in the list for " + where(entry));
            }
            values[i] = parsed;
        }
        return values;
    }

    /**
     * Reads one line's value the way {@link #getFloats(String, int)} does.
     * @param entry a line from {@link #entry} or {@link #entries}
     * @param count how many numbers the list must hold
     * @return the numbers, in a new array
     * @throws IllegalArgumentException if {@link #getFloats(Entry)} would throw, or the list is of another length
     */
    public float[] getFloats(Entry entry, int count) {
        float[] values = getFloats(entry);
        if (values.length != count) {
            throw new IllegalArgumentException("Expected " + count + " floats for " + where(entry) + ", got "
                + values.length);
        }
        return values;
    }

    /**
     * Reads a comma-separated list of whole numbers such as {@code 3, 5, 8}, each in the form
     * {@link #getInt(String)} reads.
     * @param key a key declared as required or optional
     * @return the numbers, at least one, in a new array
     * @throws IllegalArgumentException if the value is empty or an item is not such a number
     */
    public int[] getInts(String key) {
        return getInts(entry(key));
    }

    /**
     * Reads a comma-separated list of exactly {@code count} whole numbers.
     * @param key a key declared as required or optional
     * @param count how many numbers the list must hold
     * @return the numbers, in a new array
     * @throws IllegalArgumentException if {@link #getInts(String)} would throw, or the list is of another length
     */
    public int[] getInts(String key, int count) {
        return getInts(entry(key), count);
    }

    /**
     * Reads one line's value the way {@link #getInts(String)} does.
     * @param entry a line from {@link #entry} or {@link #entries}
     * @return the numbers, at least one, in a new array
     * @throws IllegalArgumentException if the value is empty or an item is not such a number
     */
    public int[] getInts(Entry entry) {
        String[] items = nonEmpty(entry).split(",", -1);
        int[] values = new int[items.length];
        for (int i = 0; i < items.length; i++) {
            String item = items[i].trim();
            Integer parsed = parseInt(item);
            if (parsed == null) {
                throw new IllegalArgumentException("Invalid int '" + item + "' in the list for " + where(entry));
            }
            values[i] = parsed;
        }
        return values;
    }

    /**
     * Reads one line's value the way {@link #getInts(String, int)} does.
     * @param entry a line from {@link #entry} or {@link #entries}
     * @param count how many numbers the list must hold
     * @return the numbers, in a new array
     * @throws IllegalArgumentException if {@link #getInts(Entry)} would throw, or the list is of another length
     */
    public int[] getInts(Entry entry, int count) {
        int[] values = getInts(entry);
        if (values.length != count) {
            throw new IllegalArgumentException("Expected " + count + " ints for " + where(entry) + ", got "
                + values.length);
        }
        return values;
    }

    /**
     * Builds the exception for a value a getter accepted and the caller does not, such as a number out of range.
     * The message is "Invalid 'key' in file at line n: problem, got 'value'".
     * @param key a key declared as required or optional
     * @param problem what is wrong (for example {@code "must be positive"}); the message adds the value itself,
     *     so this need not repeat it
     * @return the exception, for the caller to throw
     */
    public IllegalArgumentException invalid(String key, String problem) {
        return invalid(entry(key), problem);
    }

    /**
     * Builds the exception for one line whose value the caller does not accept, such as a line of a repeated key.
     * The message is "Invalid 'key' in file at line n: problem, got 'value'".
     * @param entry a line from {@link #entry} or {@link #entries}
     * @param problem what is wrong (for example {@code "must be positive"}); the message adds the value itself,
     *     so this need not repeat it
     * @return the exception, for the caller to throw
     */
    public IllegalArgumentException invalid(Entry entry, String problem) {
        return new IllegalArgumentException("Invalid " + where(entry) + ": " + problem + ", got '" + entry.value
            + "'");
    }

    private String where(Entry entry) {
        return "'" + entry.key + "' in " + path + " at line " + entry.line;
    }

    private String nonEmpty(Entry entry) {
        if (entry.value.isEmpty()) {
            throw new IllegalArgumentException("Empty '" + entry.key + "' " + noun + " in " + path + " at line "
                + entry.line);
        }
        return entry.value;
    }

    private static Float parseFloat(String text) {
        if (!DECIMAL.matcher(text).matches()) return null;
        float parsed = Float.parseFloat(text);
        if (!Float.isFinite(parsed)) return null;
        // A number other than zero that is too small for a float would silently become zero.
        if (parsed == 0f && NON_ZERO_MANTISSA.matcher(text).matches()) return null;
        return parsed;
    }

    private static Integer parseInt(String text) {
        if (!INTEGER.matcher(text).matches()) return null;
        try {
            return Integer.parseInt(text);
        } catch (NumberFormatException e) {
            // Too many digits for an int.
            return null;
        }
    }
}

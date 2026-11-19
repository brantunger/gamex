package io.github.brantunger.gamex.hud;

import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;

/**
 * A line of player-visible text with named placeholders in braces, such as {@code HOSTILES {remaining}/{total}}.
 *
 * <p>There are three kinds of placeholder:
 * <ul>
 *   <li>{@code {key:action}} stands for the name of the key bound to an action, one of {@link KeyLabels#ACTIONS};</li>
 *   <li>{@code {name}} with one of {@link TextValues#NAMES} stands for a value of a tuning file;</li>
 *   <li>any other {@code {name}} stands for a value the game fills in each time it shows the text. The code that
 *       loads the text declares these names, and the text must use every one of them at least once.</li>
 * </ul>
 * The first two kinds are filled in once, when the text is parsed. {@link #parse} fails on a brace without its
 * partner, on an empty or unknown placeholder, on a declared placeholder the text leaves out, and on a character
 * the font cannot draw (see {@link GlyphCheck}); braces cannot be written as plain text. Instances are immutable.
 */
public final class TextTemplate {

    private static final String KEY_PREFIX = "key:";

    /** The text between the placeholders: one more piece than there are placeholders. */
    private final String[] literals;
    /** For each placeholder left to fill, the index of its name among the declared ones. */
    private final int[] slots;
    private final int declared;
    /** The actions whose key names the text shows, each once, in the order the text names them. */
    private final List<String> keyActions;

    private TextTemplate(String[] literals, int[] slots, int declared, List<String> keyActions) {
        this.literals = literals;
        this.slots = slots;
        this.declared = declared;
        this.keyActions = keyActions;
    }

    /**
     * @param raw the text as written
     * @param where where the text comes from, for the error messages (for example
     *     {@code "'hud.hp' in data/strings.txt at line 3"})
     * @param allowed the names of the placeholders the game fills in, in the order {@link #format} takes them
     * @param font the loaded font the text will be drawn with
     * @param keys names the key of each action
     * @param values the tuning values a text may quote
     * @return the parsed text, with its key names and tuning values filled in
     * @throws IllegalArgumentException if the text breaks a rule given on this class; the message ends with
     *     {@code where}
     */
    public static TextTemplate parse(String raw, String where, List<String> allowed, PixelFont font, KeyLabels keys,
                                     TextValues values) {
        List<String> literals = new ArrayList<>();
        List<Integer> slots = new ArrayList<>();
        Set<String> used = new LinkedHashSet<>();
        Set<String> keyActions = new LinkedHashSet<>();
        StringBuilder literal = new StringBuilder();
        int at = 0;
        while (at < raw.length()) {
            char c = raw.charAt(at);
            if (c == '}') throw new IllegalArgumentException("'}' without '{' before it in " + where);
            if (c != '{') {
                literal.append(c);
                at++;
                continue;
            }
            String name = placeholderAt(raw, at, where);
            at += name.length() + 2;
            if (name.startsWith(KEY_PREFIX)) {
                String action = requireAction(name, where);
                keyActions.add(action);
                literal.append(keys.label(action));
            } else if (TextValues.NAMES.contains(name)) {
                literal.append(values.get(name));
            } else {
                int slot = allowed.indexOf(name);
                if (slot < 0) throw unknown(name, allowed, where);
                used.add(name);
                literals.add(literal.toString());
                literal.setLength(0);
                slots.add(slot);
            }
        }
        literals.add(literal.toString());
        for (String name : allowed) {
            if (!used.contains(name)) {
                throw new IllegalArgumentException("Missing placeholder {" + name + "} in " + where);
            }
        }
        for (String part : literals) GlyphCheck.require(font, part, where);
        int[] slotArray = new int[slots.size()];
        for (int i = 0; i < slotArray.length; i++) slotArray[i] = slots.get(i);
        return new TextTemplate(literals.toArray(new String[0]), slotArray, allowed.size(),
            Collections.unmodifiableList(new ArrayList<>(keyActions)));
    }

    /**
     * Checks the placeholders of a text without filling them in, for a text that is read where the key names and
     * the tuning values are not at hand.
     * @param raw the text as written
     * @param where where the text comes from, for the error messages
     * @param allowed the names of the placeholders the game fills in; the text need not use them
     * @return the text with every placeholder taken out, for a check of the characters that are left
     * @throws IllegalArgumentException on a brace without its partner and on an empty or unknown placeholder
     */
    public static String checkPlaceholders(String raw, String where, Collection<String> allowed) {
        StringBuilder literal = new StringBuilder();
        int at = 0;
        while (at < raw.length()) {
            char c = raw.charAt(at);
            if (c == '}') throw new IllegalArgumentException("'}' without '{' before it in " + where);
            if (c != '{') {
                literal.append(c);
                at++;
                continue;
            }
            String name = placeholderAt(raw, at, where);
            at += name.length() + 2;
            if (name.startsWith(KEY_PREFIX)) {
                requireAction(name, where);
            } else if (!TextValues.NAMES.contains(name) && !allowed.contains(name)) {
                throw unknown(name, allowed, where);
            }
        }
        return literal.toString();
    }

    /** @return the name between the brace at {@code open} and its partner. */
    private static String placeholderAt(String raw, int open, String where) {
        int close = raw.indexOf('}', open);
        int nested = raw.indexOf('{', open + 1);
        if (close < 0 || (nested >= 0 && nested < close)) {
            throw new IllegalArgumentException("'{' without '}' after it in " + where);
        }
        String name = raw.substring(open + 1, close);
        if (name.isEmpty()) throw new IllegalArgumentException("Empty placeholder {} in " + where);
        return name;
    }

    private static String requireAction(String name, String where) {
        String action = name.substring(KEY_PREFIX.length());
        if (!KeyLabels.ACTIONS.contains(action)) {
            throw new IllegalArgumentException("Unknown action in placeholder {" + name + "} in " + where
                + " (the actions are " + String.join(", ", KeyLabels.ACTIONS) + ")");
        }
        return action;
    }

    private static IllegalArgumentException unknown(String name, Collection<String> allowed, String where) {
        List<String> known = new ArrayList<>();
        for (String each : allowed) known.add("{" + each + "}");
        known.add("{key:action}");
        for (String each : TextValues.NAMES) known.add("{" + each + "}");
        return new IllegalArgumentException("Unknown placeholder {" + name + "} in " + where
            + " (it may hold " + String.join(", ", known) + ")");
    }

    /**
     * @return the actions of the text's {@code {key:action}} placeholders, each once, in the order the text names
     *     them; the list cannot be changed
     */
    public List<String> keyActions() {
        return keyActions;
    }

    /** @return how many values {@link #format} takes. */
    public int arity() {
        return declared;
    }

    /**
     * @param values a value for each declared placeholder, in the declared order
     * @return the text with the values in place
     * @throws IllegalArgumentException if the number of values is not {@link #arity()}
     */
    public String format(String... values) {
        if (values.length != declared) {
            throw new IllegalArgumentException("Expected " + declared + " values, got " + values.length);
        }
        if (slots.length == 0) return literals[0];
        StringBuilder out = new StringBuilder(literals[0]);
        for (int i = 0; i < slots.length; i++) {
            out.append(values[slots[i]]).append(literals[i + 1]);
        }
        return out.toString();
    }
}

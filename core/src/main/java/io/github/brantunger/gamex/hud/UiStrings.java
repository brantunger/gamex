package io.github.brantunger.gamex.hud;

import io.github.brantunger.gamex.GameSimulation;
import io.github.brantunger.gamex.data.TuningFile;
import io.github.brantunger.gamex.player.Difficulty;
import io.github.brantunger.gamex.settings.Medal;
import io.github.brantunger.gamex.world.Pickup;

import java.util.ArrayList;
import java.util.Collections;
import java.util.EnumMap;
import java.util.List;
import java.util.Map;

/**
 * The text of the menus and the HUD, read from {@code assets/data/strings.txt}. The game loads it once at start-up
 * and hands it to {@link MenuState} and {@link HudRenderer}. {@link UiText} lists the lines and the placeholders of
 * each; {@link TextTemplate} gives the rules a line must keep. Key names and tuning values are filled in at load,
 * so what is left to fill in when a line is shown are the values the game supplies. Instances are immutable.
 */
public final class UiStrings {

    /** The file inside {@code assets/} that {@link #load} reads. */
    public static final String PATH = "data/strings.txt";

    private final PixelFont font;
    private final KeyLabels keys;
    private final TextValues values;
    private final Map<UiText, TextTemplate> templates;
    /** Where each line comes from, as error messages name it: its key, the file and the line. */
    private final Map<UiText, String> sources;
    /** The lines without a placeholder left to fill. */
    private final Map<UiText, String> fixed;
    /** The whole objective line for each objective, so the HUD builds none while it draws. */
    private final Map<GameSimulation.Objective, String> objectiveLines;

    private UiStrings(TuningFile file, PixelFont font, KeyLabels keys, TextValues values) {
        this.font = font;
        this.keys = keys;
        this.values = values;
        templates = new EnumMap<>(UiText.class);
        sources = new EnumMap<>(UiText.class);
        fixed = new EnumMap<>(UiText.class);
        for (UiText text : UiText.values()) {
            TuningFile.Entry entry = file.entry(text.key);
            if (entry.value.isEmpty()) throw file.invalid(entry, "must not be empty");
            String source = "'" + entry.key + "' in " + file.path() + " at line " + entry.line;
            TextTemplate template = TextTemplate.parse(entry.value, source, text.placeholders, font, keys, values);
            templates.put(text, template);
            sources.put(text, source);
            if (text.placeholders.isEmpty()) fixed.put(text, template.format());
        }
        objectiveLines = new EnumMap<>(GameSimulation.Objective.class);
        for (GameSimulation.Objective objective : GameSimulation.Objective.values()) {
            objectiveLines.put(objective, format(UiText.HUD_OBJECTIVE, objective(objective)));
        }
        // Every kind the simulation can name has its words, or the load stops here.
        for (GameSimulation.InteractionPrompt prompt : GameSimulation.InteractionPrompt.values()) prompt(prompt);
        for (GameSimulation.ToastId id : GameSimulation.ToastId.values()) {
            if (id != GameSimulation.ToastId.PICKUP_GAIN && id != GameSimulation.ToastId.PICKUP_GRANT) toastText(id);
        }
        for (Pickup.Kind kind : Pickup.Kind.values()) label(kind);
        for (Difficulty difficulty : Difficulty.values()) label(difficulty);
        for (Medal medal : Medal.values()) label(medal);
    }

    /**
     * Reads {@code data/strings.txt} from {@code assets/}.
     * @param font the loaded font the text will be drawn with
     * @param keys names the key of each action, for the {@code {key:action}} placeholders
     * @param values the tuning values a text may quote
     * @return the text the game ships with, or that of the edited copy the working directory holds
     * @throws IllegalArgumentException if the file breaks the format, lacks a key, holds an unknown one, or holds a
     *     line that is empty, cannot be drawn or breaks a placeholder rule; the message names the file, the key and,
     *     except for a key that is missing, the line
     */
    public static UiStrings load(PixelFont font, KeyLabels keys, TextValues values) {
        return new UiStrings(TuningFile.load(PATH, keyList(), List.of()), font, keys, values);
    }

    /**
     * Builds the text from the contents of a {@code strings.txt}, for tests.
     * @param label path label included in error messages
     * @param text the file's contents
     * @param font the loaded font the text will be drawn with
     * @param keys names the key of each action
     * @param values the tuning values a text may quote
     * @return the text the contents describe
     * @throws IllegalArgumentException as {@link #load} does
     */
    public static UiStrings parse(String label, String text, PixelFont font, KeyLabels keys, TextValues values) {
        return new UiStrings(TuningFile.parseText(label, text, keyList(), List.of()), font, keys, values);
    }

    private static List<String> keyList() {
        List<String> keys = new ArrayList<>();
        for (UiText text : UiText.values()) keys.add(text.key);
        return keys;
    }

    /**
     * @param text a line without placeholders for the game to fill
     * @return the line
     * @throws IllegalArgumentException if the line has such placeholders; see {@link #format}
     */
    public String get(UiText text) {
        String line = fixed.get(text);
        if (line == null) {
            throw new IllegalArgumentException(text + " takes values for " + text.placeholders);
        }
        return line;
    }

    /**
     * @param text a line
     * @param values a value for each of the line's {@link UiText#placeholders}, in that order
     * @return the line with the values in place
     * @throws IllegalArgumentException if the number of values is not the number of placeholders
     */
    public String format(UiText text, String... values) {
        return templates.get(text).format(values);
    }

    /**
     * Fills in a line of text that comes from elsewhere, such as a stage briefing. It may hold
     * {@code {key:action}} and tuning value placeholders and no others.
     * @param raw the line as written
     * @param where where the line comes from, for the error messages
     * @return the line as shown
     * @throws IllegalArgumentException if the line breaks a placeholder rule or cannot be drawn
     */
    public String fill(String raw, String where) {
        return template(raw, where).format();
    }

    /**
     * Parses a line of text that comes from elsewhere, as {@link #fill} does.
     * @param raw the line as written
     * @param where where the line comes from, for the error messages
     * @return the line with its key names and tuning values in place; it takes no values
     * @throws IllegalArgumentException as {@link #fill} does
     */
    public TextTemplate template(String raw, String where) {
        return TextTemplate.parse(raw, where, Collections.emptyList(), font, keys, values);
    }

    /**
     * @param text a line
     * @return where the line comes from, as error messages name it: its key, the file and the line, as in
     *     {@code 'hud.hp' in data/strings.txt at line 61}
     */
    public String source(UiText text) {
        return sources.get(text);
    }

    /**
     * @param text a line
     * @return the actions whose key names the line shows, each once; empty for a line that names no key
     */
    public List<String> keyActions(UiText text) {
        return templates.get(text).keyActions();
    }

    /**
     * @param action one of {@link KeyLabels#ACTIONS}
     * @return the name the text shows for the action's key
     */
    public String keyLabel(String action) {
        return keys.label(action);
    }

    /**
     * Checks a text that is shown as written, such as a stage title.
     * @param text the text
     * @param where where the text comes from, for the error message
     * @return the text, unchanged
     * @throws IllegalArgumentException if the font cannot draw a character of it
     */
    public String requireDrawable(String text, String where) {
        return GlyphCheck.require(font, text, where);
    }

    /** @return the label of a difficulty, as in {@code HARD}. */
    public String label(Difficulty difficulty) {
        switch (difficulty) {
            case EASY:
                return get(UiText.DIFFICULTY_LABEL_EASY);
            case NORMAL:
                return get(UiText.DIFFICULTY_LABEL_NORMAL);
            case HARD:
                return get(UiText.DIFFICULTY_LABEL_HARD);
            default:
                throw new IllegalArgumentException("No label for the difficulty " + difficulty);
        }
    }

    /** @return the label of a medal, as in {@code UNDER PAR}. */
    public String label(Medal medal) {
        switch (medal) {
            case UNTOUCHED:
                return get(UiText.MEDAL_LABEL_UNTOUCHED);
            case SWEEP:
                return get(UiText.MEDAL_LABEL_SWEEP);
            case UNDER_PAR:
                return get(UiText.MEDAL_LABEL_UNDER_PAR);
            case SHARPSHOOTER:
                return get(UiText.MEDAL_LABEL_SHARPSHOOTER);
            default:
                throw new IllegalArgumentException("No label for the medal " + medal);
        }
    }

    /** @return the noun a pickup notification shows for a kind of pickup, as in {@code HEALTH}. */
    public String label(Pickup.Kind kind) {
        switch (kind) {
            case MEDKIT:
                return get(UiText.PICKUP_LABEL_MEDKIT);
            case ARMOR:
                return get(UiText.PICKUP_LABEL_ARMOR);
            case ENERGY_CELL:
                return get(UiText.PICKUP_LABEL_ENERGY_CELL);
            case AMBER_KEYCARD:
                return get(UiText.PICKUP_LABEL_AMBER_KEYCARD);
            case CYAN_KEYCARD:
                return get(UiText.PICKUP_LABEL_CYAN_KEYCARD);
            case SCATTERGUN:
                return get(UiText.PICKUP_LABEL_SCATTERGUN);
            case SHELLS:
                return get(UiText.PICKUP_LABEL_SHELLS);
            case RAIL_LANCE:
                return get(UiText.PICKUP_LABEL_RAIL_LANCE);
            case RAIL_SLUGS:
                return get(UiText.PICKUP_LABEL_RAIL_SLUGS);
            default:
                throw new IllegalArgumentException("No label for the pickup " + kind);
        }
    }

    /** @return the words of an objective, as in {@code REACH EXIT PAD}. */
    public String objective(GameSimulation.Objective objective) {
        return get(objectiveText(objective));
    }

    /** @return the line that holds the words of an objective. */
    static UiText objectiveText(GameSimulation.Objective objective) {
        switch (objective) {
            case AMBER_KEYCARD:
                return UiText.OBJECTIVE_AMBER_KEYCARD;
            case CYAN_KEYCARD:
                return UiText.OBJECTIVE_CYAN_KEYCARD;
            case CONSOLE:
                return UiText.OBJECTIVE_CONSOLE;
            case WARDEN:
                return UiText.OBJECTIVE_WARDEN;
            case EXIT:
                return UiText.OBJECTIVE_EXIT;
            case ALL_TARGETS:
                return UiText.OBJECTIVE_ALL_TARGETS;
            case ALL_HOSTILES:
                return UiText.OBJECTIVE_ALL_HOSTILES;
            case ALL_TARGETS_AND_HOSTILES:
                return UiText.OBJECTIVE_ALL_TARGETS_AND_HOSTILES;
            default:
                throw new IllegalArgumentException("No text for the objective " + objective);
        }
    }

    /** @return the HUD's whole objective line, as in {@code OBJECTIVE: REACH EXIT PAD}. */
    public String objectiveLine(GameSimulation.Objective objective) {
        return objectiveLines.get(objective);
    }

    /** @return the words of an interaction prompt, as in {@code [E] OPEN BULKHEAD}. */
    public String prompt(GameSimulation.InteractionPrompt prompt) {
        return get(promptText(prompt));
    }

    /** @return the line that holds the words of an interaction prompt. */
    static UiText promptText(GameSimulation.InteractionPrompt prompt) {
        switch (prompt) {
            case OVERRIDE_CONSOLE:
                return UiText.PROMPT_OVERRIDE_CONSOLE;
            case UNLOCK_AMBER:
                return UiText.PROMPT_UNLOCK_AMBER;
            case UNLOCK_CYAN:
                return UiText.PROMPT_UNLOCK_CYAN;
            case OPEN_BULKHEAD:
                return UiText.PROMPT_OPEN_BULKHEAD;
            case NEEDS_AMBER:
                return UiText.BLOCKED_NEEDS_AMBER;
            case NEEDS_CYAN:
                return UiText.BLOCKED_NEEDS_CYAN;
            case SEALED_BY_CONSOLE:
                return UiText.BLOCKED_SEALED_CONSOLE;
            case SEALED_BY_WARDEN:
                return UiText.BLOCKED_SEALED_WARDEN;
            default:
                throw new IllegalArgumentException("No text for the prompt " + prompt);
        }
    }

    /** @return the words of a notification, as in {@code +25 HEALTH} or {@code LOCKDOWN OVERRIDDEN}. */
    public String toast(GameSimulation.Toast toast) {
        if (toast.id == GameSimulation.ToastId.PICKUP_GAIN) {
            return format(UiText.PICKUP_GAIN, Integer.toString(toast.amount), label(toast.pickup));
        }
        if (toast.id == GameSimulation.ToastId.PICKUP_GRANT) return format(UiText.PICKUP_GRANT, label(toast.pickup));
        return get(toastText(toast.id));
    }

    /** @return the line of a notification that is not about a pickup. */
    static UiText toastText(GameSimulation.ToastId id) {
        switch (id) {
            case OUT_OF_SHELLS:
                return UiText.TOAST_OUT_OF_SHELLS;
            case OUT_OF_SLUGS:
                return UiText.TOAST_OUT_OF_SLUGS;
            case WARDEN_OVERDRIVE:
                return UiText.TOAST_WARDEN_OVERDRIVE;
            case LOCKDOWN_OVERRIDDEN:
                return UiText.TOAST_LOCKDOWN_OVERRIDDEN;
            case AMBER_UNLOCKED:
                return UiText.TOAST_AMBER_UNLOCKED;
            case CYAN_UNLOCKED:
                return UiText.TOAST_CYAN_UNLOCKED;
            case NEEDS_AMBER:
                return UiText.BLOCKED_NEEDS_AMBER;
            case NEEDS_CYAN:
                return UiText.BLOCKED_NEEDS_CYAN;
            case SEALED_BY_WARDEN:
                return UiText.BLOCKED_SEALED_WARDEN;
            case SEALED_BY_CONSOLE:
                return UiText.BLOCKED_SEALED_CONSOLE;
            case WARDEN_DESTROYED:
                return UiText.TOAST_WARDEN_DESTROYED;
            default:
                throw new IllegalArgumentException("No text for the toast " + id);
        }
    }
}

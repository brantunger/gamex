package io.github.brantunger.gamex.debug;

import com.badlogic.gdx.utils.GdxRuntimeException;
import io.github.brantunger.gamex.data.AssetText;
import io.github.brantunger.gamex.player.PlayerInput;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.ArrayList;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * One demo script of the debug screenshot mode, read from {@code assets/demos/<name>.txt}: the scene the run starts
 * with and the input it plays, frame by frame. {@code GAMEX_DEMO} names the script (see {@link DemoRun}).
 *
 * <p>A script is a header block, a blank line and a timeline:
 * <pre>
 * // A line whose trimmed text begins with two slashes is a comment.
 * screen: playing
 * stage: 01_outer_breach
 * loadout: fresh
 *
 * 1-155: move_forward
 * 25: dash
 * 136-150: turn -1.2
 * 150-: fire
 * </pre>
 * The header block is the scene; {@link DemoScene} lists its lines. A script may end there, and then no input is
 * played. Each line of the timeline is {@code frames: input} or {@code frames: input amount}. Frames are counted
 * from 1 and are one frame ({@code 25}), a first and a last frame ({@code 115-135}) or a first frame with no end
 * ({@code 150-}). The inputs are named as the key bindings name the actions:
 * <ul>
 *   <li>{@code move_forward}, {@code move_back}, {@code move_left}, {@code move_right}: held for the frames, at full
 *       speed or at the amount given, which is more than 0 and at most 1.</li>
 *   <li>{@code fire}, {@code alt_fire} (the charge, double blast or zoom trigger), {@code jump}: held for the
 *       frames.</li>
 *   <li>{@code dash}, {@code interact}, {@code weapon_1}, {@code weapon_2}, {@code weapon_3}, {@code quick_swap},
 *       {@code weapon_next}, {@code weapon_prev}: asked for on each of the frames.</li>
 *   <li>{@code turn} and {@code look_up}, which the mouse does and no key: the degrees turned right or looked up on
 *       each of the frames; the amount is needed and may be negative.</li>
 * </ul>
 * A line that is not of this form, an input this class does not list, an amount where none belongs or out of range,
 * and two lines that set the same thing on the same frame each fail the load with a message naming the file and
 * the line.
 *
 * <p>Instances are immutable and safe to share.
 */
public final class DemoScript {

    private static final Logger log = LoggerFactory.getLogger(DemoScript.class);

    /** The folder inside {@code assets/} that holds the demo scripts. */
    public static final String DEMOS_DIR = "demos";

    private static final Pattern NAME = Pattern.compile("[a-z0-9_-]+");
    private static final Pattern FRAMES = Pattern.compile("([0-9]+)(?:(-)([0-9]+)?)?");
    private static final Pattern DECIMAL = Pattern.compile("[+-]?([0-9]+(\\.[0-9]*)?|\\.[0-9]+)");
    /** A timeline line with no last frame runs to here. */
    private static final int NO_END = Integer.MAX_VALUE;

    /** What of {@link PlayerInput} an input sets; two lines may not set the same one on the same frame. */
    private enum Channel { FORWARD, STRAFE, YAW, PITCH, FIRE, CHARGE, JUMP, DASH, INTERACT, WEAPON }

    /** Whether an input takes an amount. */
    private enum Amount { NONE, SPEED, DEGREES }

    /** The inputs a timeline line may name. */
    private enum Input {
        MOVE_FORWARD("move_forward", Channel.FORWARD, Amount.SPEED),
        MOVE_BACK("move_back", Channel.FORWARD, Amount.SPEED),
        MOVE_LEFT("move_left", Channel.STRAFE, Amount.SPEED),
        MOVE_RIGHT("move_right", Channel.STRAFE, Amount.SPEED),
        TURN("turn", Channel.YAW, Amount.DEGREES),
        LOOK_UP("look_up", Channel.PITCH, Amount.DEGREES),
        FIRE("fire", Channel.FIRE, Amount.NONE),
        ALT_FIRE("alt_fire", Channel.CHARGE, Amount.NONE),
        JUMP("jump", Channel.JUMP, Amount.NONE),
        DASH("dash", Channel.DASH, Amount.NONE),
        INTERACT("interact", Channel.INTERACT, Amount.NONE),
        WEAPON_1("weapon_1", Channel.WEAPON, Amount.NONE),
        WEAPON_2("weapon_2", Channel.WEAPON, Amount.NONE),
        WEAPON_3("weapon_3", Channel.WEAPON, Amount.NONE),
        QUICK_SWAP("quick_swap", Channel.WEAPON, Amount.NONE),
        WEAPON_NEXT("weapon_next", Channel.WEAPON, Amount.NONE),
        WEAPON_PREV("weapon_prev", Channel.WEAPON, Amount.NONE);

        final String id;
        final Channel channel;
        final Amount amount;

        Input(String id, Channel channel, Amount amount) {
            this.id = id;
            this.channel = channel;
            this.amount = amount;
        }

        static Input of(String id) {
            for (Input input : values()) {
                if (input.id.equals(id)) return input;
            }
            return null;
        }

        static String ids() {
            StringBuilder ids = new StringBuilder();
            for (Input input : values()) {
                if (ids.length() > 0) ids.append(", ");
                ids.append(input.id);
            }
            return ids.toString();
        }
    }

    /** One line of the timeline. */
    private static final class Event {
        final int from;
        final int to;
        final Input input;
        final float amount;
        final int line;

        Event(int from, int to, Input input, float amount, int line) {
            this.from = from;
            this.to = to;
            this.input = input;
            this.amount = amount;
            this.line = line;
        }
    }

    /** The scene the run starts with. */
    public final DemoScene scene;

    private final Event[] events;

    private DemoScript(DemoScene scene, Event[] events) {
        this.scene = scene;
        this.events = events;
    }

    /**
     * @param name the name of a script, as {@code GAMEX_DEMO} gives it in lower case
     * @return the internal path of its file inside {@code assets/}
     * @throws IllegalArgumentException if the name holds anything but lower-case letters, digits, {@code _} and
     *     {@code -}, so that it cannot name a file outside {@link #DEMOS_DIR}
     */
    public static String path(String name) {
        if (name == null || !NAME.matcher(name).matches()) {
            throw new IllegalArgumentException("Invalid demo script name '" + name + "': a name is made of the "
                + "letters a to z, digits, '_' and '-', and names the file " + DEMOS_DIR + "/<name>.txt in assets");
        }
        return DEMOS_DIR + "/" + name + ".txt";
    }

    /**
     * Reads and parses the script {@code assets/demos/<name>.txt} (see {@link AssetText#read} for where it is
     * looked for).
     * @param name the name of the script
     * @return the parsed script
     * @throws IllegalArgumentException if the name is not a script name, no file has it, or the file breaks the
     *     format
     */
    public static DemoScript load(String name) {
        String assetPath = path(name);
        String text;
        try {
            text = AssetText.read("demo", assetPath);
        } catch (GdxRuntimeException | IllegalStateException e) {
            throw new IllegalArgumentException("Unknown demo script '" + name + "': the file " + assetPath
                + " was not found in assets", e);
        }
        DemoScript script = parse(assetPath, text);
        log.debug("Loaded demo script {} with {} timeline lines", assetPath, script.events.length);
        return script;
    }

    /**
     * Parses the text of a script; nothing is read from disk but the levels a {@code stage} line is checked against.
     * @param assetPath path label included in error messages
     * @param text raw file contents
     * @return the parsed script
     * @throws IllegalArgumentException if the text breaks the format
     */
    public static DemoScript parse(String assetPath, String text) {
        if (text == null) {
            throw new IllegalArgumentException("Demo script text for " + assetPath + " is null");
        }
        List<String> lines = AssetText.splitLines(text);
        DemoScene.Parsed parsed = DemoScene.parse(assetPath, lines);
        List<Event> events = new ArrayList<>();
        for (int index = parsed.header.bodyStart(); index < lines.size(); index++) {
            String trimmed = lines.get(index).trim();
            if (trimmed.isEmpty() || AssetText.isComment(trimmed)) continue;
            Event event = parseEvent(assetPath, index + 1, trimmed);
            for (Event other : events) {
                if (other.input.channel == event.input.channel && other.from <= event.to && event.from <= other.to) {
                    throw new IllegalArgumentException("Invalid timeline line in " + assetPath + " at line "
                        + event.line + ": '" + event.input.id + "' overlaps '" + other.input.id + "' of line "
                        + other.line + " on frame " + Math.max(event.from, other.from));
                }
            }
            events.add(event);
        }
        return new DemoScript(parsed.scene, events.toArray(new Event[0]));
    }

    private static Event parseEvent(String assetPath, int line, String trimmed) {
        String where = " in " + assetPath + " at line " + line;
        int colon = trimmed.indexOf(':');
        String[] words = colon < 0 ? new String[0] : trimmed.substring(colon + 1).trim().split("\\s+");
        if (colon <= 0 || words[0].isEmpty() || words.length > 2) {
            throw new IllegalArgumentException("Expected 'frames: input' or 'frames: input amount' timeline line"
                + where);
        }
        String frames = trimmed.substring(0, colon).trim();
        Matcher matcher = FRAMES.matcher(frames);
        int from;
        int to;
        try {
            if (!matcher.matches()) throw new NumberFormatException(frames);
            from = Integer.parseInt(matcher.group(1));
            to = matcher.group(2) == null ? from : matcher.group(3) == null ? NO_END
                : Integer.parseInt(matcher.group(3));
        } catch (NumberFormatException e) {
            throw new IllegalArgumentException("Invalid frames '" + frames + "'" + where
                + ": expected one frame (25), a first and a last frame (115-135) or a first frame (150-)");
        }
        if (from < 1 || to < from) {
            throw new IllegalArgumentException("Invalid frames '" + frames + "'" + where
                + ": frames are counted from 1 and the last is not before the first");
        }
        Input input = Input.of(words[0]);
        if (input == null) {
            throw new IllegalArgumentException("Unknown input '" + words[0] + "'" + where + " (the inputs are "
                + Input.ids() + ")");
        }
        float amount = 1f;
        if (words.length == 2) {
            if (input.amount == Amount.NONE) {
                throw new IllegalArgumentException("Input '" + input.id + "' takes no amount" + where + ", got '"
                    + words[1] + "'");
            }
            amount = DECIMAL.matcher(words[1]).matches() ? Float.parseFloat(words[1]) : Float.NaN;
            if (!Float.isFinite(amount) || (input.amount == Amount.SPEED && (amount <= 0f || amount > 1f))) {
                throw new IllegalArgumentException("Invalid amount of '" + input.id + "'" + where + ": "
                    + (input.amount == Amount.SPEED ? "must be a number more than 0 and at most 1"
                    : "must be a number of degrees") + ", got '" + words[1] + "'");
            }
        } else if (input.amount == Amount.DEGREES) {
            throw new IllegalArgumentException("Input '" + input.id + "' needs an amount in degrees" + where);
        }
        return new Event(from, to, input, amount, line);
    }

    /**
     * Adds the script's input for one frame to an input that was cleared.
     * @param frame the frame, counted from 1
     * @param out the input to fill
     */
    public void apply(int frame, PlayerInput out) {
        for (Event event : events) {
            if (frame < event.from || frame > event.to) continue;
            switch (event.input) {
                case MOVE_FORWARD:
                    out.moveForward = event.amount;
                    break;
                case MOVE_BACK:
                    out.moveForward = -event.amount;
                    break;
                case MOVE_LEFT:
                    out.moveStrafe = -event.amount;
                    break;
                case MOVE_RIGHT:
                    out.moveStrafe = event.amount;
                    break;
                case TURN:
                    out.yawDelta = event.amount;
                    break;
                case LOOK_UP:
                    out.pitchDelta = event.amount;
                    break;
                case FIRE:
                    out.fire = true;
                    break;
                case ALT_FIRE:
                    out.charge = true;
                    break;
                case JUMP:
                    out.jump = true;
                    break;
                case DASH:
                    out.dash = true;
                    break;
                case INTERACT:
                    out.interact = true;
                    break;
                case WEAPON_1:
                    out.weaponSlot = 1;
                    break;
                case WEAPON_2:
                    out.weaponSlot = 2;
                    break;
                case WEAPON_3:
                    out.weaponSlot = 3;
                    break;
                case QUICK_SWAP:
                    out.weaponSlot = PlayerInput.WEAPON_QUICK_SWAP;
                    break;
                case WEAPON_NEXT:
                    out.weaponSlot = PlayerInput.WEAPON_CYCLE_NEXT;
                    break;
                case WEAPON_PREV:
                    out.weaponSlot = PlayerInput.WEAPON_CYCLE_PREV;
                    break;
                default:
                    throw new IllegalStateException("Unhandled input " + event.input);
            }
        }
    }
}

package io.github.brantunger.gamex.hud;

import io.github.brantunger.gamex.GameSimulation;
import io.github.brantunger.gamex.player.Difficulty;
import io.github.brantunger.gamex.player.DifficultyTuning;
import io.github.brantunger.gamex.settings.Medal;
import io.github.brantunger.gamex.settings.SettingsDefaults;
import io.github.brantunger.gamex.world.Levels;
import io.github.brantunger.gamex.world.Pickup;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

/**
 * Checks at start-up that the text of the menus and the HUD fits where it is drawn. The lines of
 * {@code assets/data/strings.txt} and the stage briefings can be edited, and a line that names a key grows with the
 * key name {@code assets/data/bindings.txt} gives it, so a line can be too long for the box {@link HudRenderer}
 * draws it in. {@link #require} measures every line that has such a box, at the size it is drawn in and in HUD
 * units, and stops the start on the first that does not fit.
 *
 * <p>The lines measured, and the room each is given:
 * <ul>
 *   <li>the Title footer: the width of a 4:3 window, the narrowest landscape shape the footer is kept to;</li>
 *   <li>every toast, with its box: the room kept free around the centre of the window
 *       ({@link HudRenderer#CENTRE_HALF_WIDTH} on each side);</li>
 *   <li>every objective line: from {@link HudRenderer#STATUS_LEFT} to
 *       {@link HudRenderer#OBJECTIVE_RIGHT_LIMIT};</li>
 *   <li>every interaction prompt, with its box: the narrowest window, {@link HudRenderer#HUD_REFERENCE_SPAN} wide;
 *       </li>
 *   <li>every menu option as its highlighted row shows it, the Select Stage, Select Difficulty and Settings rows
 *       among them: {@link HudRenderer#OPTION_ROW_WIDTH};</li>
 *   <li>the title and the captions of every menu screen, the quit prompt, the Settings help line, the medal line
 *       of Select Stage, every line of the Intermission Tally, and every stage's title where the Briefing screen and
 *       the tally show it: the panel they are drawn in, less {@link HudRenderer#PANEL_TEXT_MARGIN} on each side;
 *       </li>
 *   <li>the label over the boss health bar: the bar's panel;</li>
 *   <li>the minimap header label: {@link MinimapRenderer#MAP_SIZE} less {@link MinimapRenderer#HEADER_PAD_X} on each
 *       side;</li>
 *   <li>every briefing line of every stage: {@link HudRenderer#BRIEFING_LOG_WIDTH}. The box has room for
 *       {@link HudRenderer#BRIEFING_LINES} lines, and a stage with more stops the start too.</li>
 * </ul>
 * A line that takes values while the game runs is measured with the longest values it is given room for: the
 * constants of this class, every stage title, every label, the ends of the Settings ranges, and each stage's par
 * time where it takes more room than {@link #WORST_STOPWATCH}. A pickup toast is measured with the labels the
 * simulation gives it: an amount of health, armor, energy, shells or slugs, and a keycard or a weapon without one.
 * The Select Difficulty rows are measured with the loaded multipliers, which do not change while the game runs.
 *
 * <p>Not measured, because nothing bounds them on the screen: the status row, the timer, the health and armor
 * figures, the weapon list and the keycard badges, the energy caption, and the low-energy and charge captions under
 * the crosshair.
 */
public final class TextFit {

    /** The longest count of hostiles, targets or pickups a line is given room for. */
    static final String WORST_COUNT = "999";
    /** The longest percentage of hostiles or targets destroyed, and of shots that hit. */
    static final String WORST_PERCENT = "100";
    /** The longest amount of damage taken. */
    static final String WORST_DAMAGE = "9999";
    /** The longest amount a pickup toast names, which no pickup reaches. */
    static final String WORST_PICKUP_AMOUNT = "999";
    /** The longest time written by {@link TextFormat#stopwatch} that has two digits of minutes. */
    static final String WORST_STOPWATCH = "99:59.9";
    /** The longest time {@link TextFormat#clock} writes. */
    static final String WORST_CLOCK = "99:59";
    /** A rank; every rank is one character. */
    static final String WORST_RANK = "S";

    /** Width of a 4:3 window in HUD units: its height is {@link HudRenderer#HUD_REFERENCE_SPAN}. */
    static final float FOUR_BY_THREE_WIDTH = HudRenderer.HUD_REFERENCE_SPAN * 4f / 3f;

    private static final String BINDINGS_FILE = "data/bindings.txt";

    private final UiStrings strings;

    private TextFit(UiStrings strings) {
        this.strings = strings;
    }

    /**
     * Measures every line listed on this class.
     * @param strings the loaded text, with the key names of the loaded bindings in place
     * @param difficulty what each difficulty multiplies, for the Select Difficulty rows
     * @param settings the ranges of the settings, for the Settings rows
     * @param campaign the campaign's stages, for the Select Stage rows and the briefings
     * @param trainingRange the Training Range, for its briefing
     * @throws IllegalArgumentException if a line does not fit; the message names the line's key, file and line
     *     number (for a stage's text, the stage and its file), the text as shown and how many characters it is too
     *     long by, and the key names it shows with the actions of {@code data/bindings.txt} they belong to. Also
     *     if a stage has more briefing lines than their box holds, and if a briefing line breaks a placeholder rule
     *     or holds a character the font cannot draw (see {@link UiStrings#fill}); the message names the stage and
     *     its file
     */
    public static void require(UiStrings strings, DifficultyTuning difficulty, SettingsDefaults settings,
                               List<Levels.Stage> campaign, Levels.Stage trainingRange) {
        TextFit fit = new TextFit(strings);
        fit.titleFooter();
        fit.toasts();
        fit.objectives();
        fit.prompts();
        fit.options(difficulty, settings, campaign);
        fit.panelLines();
        fit.tally();
        List<Levels.Stage> stages = new ArrayList<>(campaign);
        stages.add(trainingRange);
        for (Levels.Stage stage : stages) fit.stage(stage);
    }

    private void titleFooter() {
        line(UiText.SCREEN_TITLE_FOOTER, strings.get(UiText.SCREEN_TITLE_FOOTER), HudRenderer.TITLE_FOOTER_PIXEL,
            FOUR_BY_THREE_WIDTH, "a 4:3 window");
    }

    private void toasts() {
        float room = 2f * HudRenderer.CENTRE_HALF_WIDTH - 2f * HudRenderer.TOAST_PAD_X;
        String box = "a toast";
        for (GameSimulation.ToastId id : GameSimulation.ToastId.values()) {
            if (id == GameSimulation.ToastId.PICKUP_GAIN || id == GameSimulation.ToastId.PICKUP_GRANT) continue;
            UiText text = UiStrings.toastText(id);
            line(text, strings.get(text), HudRenderer.TOAST_PIXEL, room, box);
        }
        // Which toast a kind of pickup gets is Pickup.collect's choice: a weapon already held gives its ammunition.
        for (Pickup.Kind kind : new Pickup.Kind[] {Pickup.Kind.MEDKIT, Pickup.Kind.ARMOR, Pickup.Kind.ENERGY_CELL,
            Pickup.Kind.SHELLS, Pickup.Kind.RAIL_SLUGS}) {
            line(UiText.PICKUP_GAIN, strings.format(UiText.PICKUP_GAIN, WORST_PICKUP_AMOUNT, strings.label(kind)),
                HudRenderer.TOAST_PIXEL, room, box);
        }
        for (Pickup.Kind kind : new Pickup.Kind[] {Pickup.Kind.AMBER_KEYCARD, Pickup.Kind.CYAN_KEYCARD,
            Pickup.Kind.SCATTERGUN, Pickup.Kind.RAIL_LANCE}) {
            line(UiText.PICKUP_GRANT, strings.format(UiText.PICKUP_GRANT, strings.label(kind)),
                HudRenderer.TOAST_PIXEL, room, box);
        }
    }

    private void objectives() {
        float room = HudRenderer.OBJECTIVE_RIGHT_LIMIT - HudRenderer.STATUS_LEFT;
        for (GameSimulation.Objective objective : GameSimulation.Objective.values()) {
            line(UiStrings.objectiveText(objective), strings.objectiveLine(objective), HudRenderer.OBJECTIVE_PIXEL,
                room, "the objective line ('" + UiText.HUD_OBJECTIVE.key + "' around it)");
        }
    }

    private void prompts() {
        float room = HudRenderer.HUD_REFERENCE_SPAN - 2f * HudRenderer.INTERACTION_PROMPT_PAD_X;
        for (GameSimulation.InteractionPrompt prompt : GameSimulation.InteractionPrompt.values()) {
            UiText text = UiStrings.promptText(prompt);
            line(text, strings.get(text), HudRenderer.INTERACTION_PROMPT_PIXEL, room, "an interaction prompt");
        }
    }

    private void options(DifficultyTuning difficulty, SettingsDefaults settings, List<Levels.Stage> campaign) {
        for (UiText text : new UiText[] {UiText.MENU_CONTINUE_CAMPAIGN, UiText.MENU_START_CAMPAIGN,
            UiText.MENU_SELECT_STAGE, UiText.MENU_TRAINING_RANGE, UiText.MENU_SETTINGS, UiText.MENU_QUIT,
            UiText.MENU_BACK, UiText.MENU_DEPLOY, UiText.MENU_RETURN_TO_TITLE, UiText.MENU_RESUME,
            UiText.MENU_RESTART_STAGE, UiText.MENU_RETRY_STAGE, UiText.MENU_NEXT_STAGE, UiText.MENU_PLAY_AGAIN}) {
            option(text, strings.get(text));
        }
        for (Levels.Stage stage : campaign) {
            option(UiText.MENU_STAGE_READY, strings.format(UiText.MENU_STAGE_READY, stage.title));
            option(UiText.MENU_STAGE_LOCKED, strings.format(UiText.MENU_STAGE_LOCKED, stage.title));
            option(UiText.MENU_STAGE_BEST, strings.format(UiText.MENU_STAGE_BEST, stage.title, WORST_RANK));
            for (Difficulty each : Difficulty.values()) {
                option(UiText.MENU_STAGE_BEST_TIME, strings.format(UiText.MENU_STAGE_BEST_TIME, stage.title,
                    WORST_RANK, WORST_CLOCK, strings.label(each)));
            }
        }
        for (Difficulty each : Difficulty.values()) {
            option(MenuState.difficultyRowText(difficulty, each), MenuState.difficultyRow(strings, difficulty, each));
        }
        String sensitivity = widerOf(String.format(Locale.ROOT, "%.2f", settings.sensitivity.min),
            String.format(Locale.ROOT, "%.2f", settings.sensitivity.max));
        String fov = widerOf(Integer.toString(Math.round(settings.fov.min)),
            Integer.toString(Math.round(settings.fov.max)));
        String volume = widerOf(TextFormat.percent(settings.volume.min), TextFormat.percent(settings.volume.max));
        String onOff = widerOf(strings.get(UiText.MENU_SETTINGS_ON), strings.get(UiText.MENU_SETTINGS_OFF));
        option(UiText.MENU_SETTINGS_SENSITIVITY, strings.format(UiText.MENU_SETTINGS_SENSITIVITY, sensitivity));
        option(UiText.MENU_SETTINGS_INVERT_Y, strings.format(UiText.MENU_SETTINGS_INVERT_Y, onOff));
        option(UiText.MENU_SETTINGS_FOV, strings.format(UiText.MENU_SETTINGS_FOV, fov));
        option(UiText.MENU_SETTINGS_VIEW_BOB, strings.format(UiText.MENU_SETTINGS_VIEW_BOB, onOff));
        option(UiText.MENU_SETTINGS_VOLUME, strings.format(UiText.MENU_SETTINGS_VOLUME, volume));
        option(UiText.MENU_SETTINGS_FULLSCREEN, strings.format(UiText.MENU_SETTINGS_FULLSCREEN, onOff));
        option(UiText.MENU_SETTINGS_MINIMAP, strings.format(UiText.MENU_SETTINGS_MINIMAP, onOff));
    }

    private void option(UiText text, String label) {
        line(text, HudRenderer.highlightedRow(label), HudRenderer.OPTION_PIXEL, HudRenderer.OPTION_ROW_WIDTH,
            "a highlighted menu row");
    }

    private void panelLines() {
        float bossPanel = HudRenderer.BOSS_BAR_WIDTH + 2f * HudRenderer.BOSS_PANEL_PAD_X;
        fixed(UiText.HUD_BOSS, HudRenderer.BOSS_LABEL_PIXEL, bossPanel, "the boss bar's panel");
        fixed(UiText.HUD_BOSS_OVERDRIVE, HudRenderer.BOSS_LABEL_PIXEL, bossPanel, "the boss bar's panel");

        float minimapHeader = MinimapRenderer.MAP_SIZE - 2f * MinimapRenderer.HEADER_PAD_X;
        fixed(UiText.HUD_MINIMAP_NORTH, MinimapRenderer.NORTH_LABEL_PIXEL, minimapHeader, "the minimap header");

        float title = inPanel(HudRenderer.TITLE_PANEL_WIDTH);
        fixed(UiText.SCREEN_TITLE_NAME, HudRenderer.TITLE_NAME_PIXEL, title, "the Title panel");
        fixed(UiText.SCREEN_TITLE_SUBTITLE, HudRenderer.TITLE_SUBTITLE_PIXEL, title, "the Title panel");
        float stageSelect = inPanel(HudRenderer.STAGE_SELECT_PANEL_WIDTH);
        fixed(UiText.SCREEN_STAGE_SELECT_TITLE, HudRenderer.STAGE_SELECT_TITLE_PIXEL, stageSelect,
            "the Select Stage panel");
        fixed(UiText.SCREEN_STAGE_SELECT_HINT, HudRenderer.STAGE_SELECT_HINT_PIXEL, stageSelect,
            "the Select Stage panel");
        fixed(UiText.SCREEN_DIFFICULTY_TITLE, HudRenderer.DIFFICULTY_TITLE_PIXEL,
            inPanel(HudRenderer.DIFFICULTY_PANEL_WIDTH), "the Select Difficulty panel");
        fixed(UiText.SCREEN_PAUSED_TITLE, HudRenderer.PAUSE_TITLE_PIXEL, inPanel(HudRenderer.PAUSE_PANEL_WIDTH),
            "the Pause panel");
        fixed(UiText.SCREEN_SETTINGS_TITLE, HudRenderer.SETTINGS_TITLE_PIXEL,
            inPanel(HudRenderer.SETTINGS_PANEL_WIDTH), "the Settings panel");
        float death = inPanel(HudRenderer.DEATH_PANEL_WIDTH);
        fixed(UiText.SCREEN_DEAD_TITLE, HudRenderer.DEATH_TITLE_PIXEL, death, "the Death panel");
        for (UiText counted : new UiText[] {UiText.SCREEN_DEAD_HOSTILES, UiText.SCREEN_DEAD_TARGETS}) {
            line(UiText.SCREEN_DEAD_SURVIVED, strings.format(UiText.SCREEN_DEAD_SURVIVED, WORST_STOPWATCH,
                strings.format(counted, WORST_COUNT, WORST_COUNT)), HudRenderer.DEATH_SURVIVED_PIXEL, death,
                "the Death panel ('" + counted.key + "' in it)");
        }

        line(UiText.MENU_QUIT_PROMPT, strings.get(UiText.MENU_QUIT_PROMPT), HudRenderer.QUIT_PROMPT_PIXEL,
            inPanel(HudRenderer.PAUSE_PANEL_WIDTH), "the Pause panel");
        line(UiText.SCREEN_SETTINGS_HELP, strings.get(UiText.SCREEN_SETTINGS_HELP), HudRenderer.SETTINGS_HELP_PIXEL,
            inPanel(HudRenderer.SETTINGS_PANEL_WIDTH), "the Settings panel");
        StringBuilder counts = new StringBuilder();
        String total = Integer.toString(Medal.values().length);
        for (Difficulty each : Difficulty.values()) {
            if (counts.length() > 0) counts.append("  ");
            counts.append(strings.format(UiText.MENU_STAGE_MEDAL_COUNT, strings.label(each), total, total));
        }
        line(UiText.MENU_STAGE_MEDALS, strings.format(UiText.MENU_STAGE_MEDALS, counts.toString()),
            HudRenderer.MEDAL_PIXEL, inPanel(HudRenderer.STAGE_SELECT_PANEL_WIDTH),
            "the Select Stage panel ('" + UiText.MENU_STAGE_MEDAL_COUNT.key + "' for each difficulty)");
    }

    private void tally() {
        float room = inPanel(HudRenderer.TALLY_PANEL_WIDTH);
        String box = "the Intermission Tally panel";
        float stat = HudRenderer.TALLY_STAT_PIXEL;
        fixed(UiText.SCREEN_COMPLETE_STAGE, HudRenderer.TALLY_TITLE_PIXEL, room, box);
        fixed(UiText.SCREEN_COMPLETE_CAMPAIGN, HudRenderer.TALLY_CAMPAIGN_TITLE_PIXEL, room, box);
        fixed(UiText.SCREEN_COMPLETE_CAMPAIGN_NOTE, HudRenderer.TALLY_NOTE_PIXEL, room, box);
        for (Difficulty each : Difficulty.values()) {
            for (UiText best : new UiText[] {UiText.TALLY_BEST, UiText.TALLY_BEST_NEW}) {
                line(best, strings.format(best, strings.label(each), WORST_STOPWATCH), stat, room, box);
            }
        }
        for (UiText cleared : new UiText[] {UiText.TALLY_HOSTILES_CLEARED, UiText.TALLY_TARGETS_CLEARED}) {
            line(cleared, strings.format(cleared, WORST_COUNT, WORST_COUNT, WORST_PERCENT), stat, room, box);
        }
        String accuracy = widerOf(WORST_PERCENT + "%", strings.get(UiText.TALLY_NO_ACCURACY));
        line(UiText.TALLY_STATS, strings.format(UiText.TALLY_STATS, WORST_DAMAGE, WORST_COUNT, WORST_COUNT,
            accuracy), stat, room, box);
        line(UiText.TALLY_RANK, strings.format(UiText.TALLY_RANK, WORST_RANK, WORST_RANK),
            HudRenderer.TALLY_RANK_PIXEL, room, box);
        StringBuilder medals = new StringBuilder();
        for (Medal medal : Medal.values()) {
            if (medals.length() > 0) medals.append("  ");
            medals.append(strings.format(UiText.TALLY_MEDAL_NEW, strings.label(medal)));
        }
        line(UiText.TALLY_MEDALS, strings.format(UiText.TALLY_MEDALS, medals.toString()), HudRenderer.MEDAL_PIXEL,
            room, box + " ('" + UiText.TALLY_MEDAL_NEW.key + "' around every medal label)");
        line(UiText.TALLY_MEDALS_NONE, strings.get(UiText.TALLY_MEDALS_NONE), HudRenderer.MEDAL_PIXEL, room, box);
        line(UiText.TALLY_MEDALS_TRAINING, strings.get(UiText.TALLY_MEDALS_TRAINING), HudRenderer.MEDAL_PIXEL,
            room, box);
    }

    /**
     * Measures the lines that show a stage's par time, the stage's title where the Briefing screen and the tally
     * show it, and its briefing lines.
     */
    private void stage(Levels.Stage stage) {
        String of = " of stage " + stage.id + (stage.path == null ? "" : " in " + stage.path);
        // A par of 100 minutes or more takes more room than the longest time a run is given room for.
        String par = widerOf(WORST_STOPWATCH, TextFormat.stopwatch(stage.parSeconds));
        for (Difficulty each : Difficulty.values()) {
            line(UiText.SCREEN_BRIEFING_META, strings.format(UiText.SCREEN_BRIEFING_META, strings.label(each), par),
                HudRenderer.BRIEFING_META_PIXEL, inPanel(HudRenderer.BRIEFING_PANEL_WIDTH), "the Briefing panel");
        }
        line(UiText.TALLY_CLEAR_TIME, strings.format(UiText.TALLY_CLEAR_TIME, WORST_STOPWATCH, par),
            HudRenderer.TALLY_STAT_PIXEL, inPanel(HudRenderer.TALLY_PANEL_WIDTH), "the Intermission Tally panel");
        if (stage.briefing.size() > HudRenderer.BRIEFING_LINES) {
            throw new IllegalArgumentException("Invalid briefing" + of + ": it has " + stage.briefing.size()
                + " lines and the briefing's box has room for " + HudRenderer.BRIEFING_LINES);
        }
        String title = "title" + of;
        check(title, List.of(), stage.title, HudRenderer.BRIEFING_TITLE_PIXEL,
            inPanel(HudRenderer.BRIEFING_PANEL_WIDTH), "the Briefing panel");
        check(title, List.of(), stage.title, HudRenderer.TALLY_STAGE_PIXEL, inPanel(HudRenderer.TALLY_PANEL_WIDTH),
            "the Intermission Tally panel");
        for (int i = 0; i < stage.briefing.size(); i++) {
            String source = "briefing line " + (i + 1) + of;
            TextTemplate template = strings.template(stage.briefing.get(i), source);
            check(source, template.keyActions(), HudRenderer.briefingRow(template.format()),
                HudRenderer.BRIEFING_LINE_PIXEL, HudRenderer.BRIEFING_LOG_WIDTH, "the briefing's box");
        }
    }

    private static float inPanel(float panelWidth) {
        return panelWidth - 2f * HudRenderer.PANEL_TEXT_MARGIN;
    }

    private static String widerOf(String first, String second) {
        return second.length() > first.length() ? second : first;
    }

    /** Measures a line that takes no values. */
    private void fixed(UiText text, float pixel, float room, String box) {
        line(text, strings.get(text), pixel, room, box);
    }

    private void line(UiText text, String shown, float pixel, float room, String box) {
        check(strings.source(text), strings.keyActions(text), shown, pixel, room, box);
    }

    /**
     * @param source where the line comes from
     * @param keyActions the actions whose key names the line shows
     * @param shown the text as drawn
     * @param pixel the font pixel size it is drawn in, in HUD units
     * @param room the width it may take, in HUD units
     * @param box what gives it that room, for the message
     */
    private void check(String source, List<String> keyActions, String shown, float pixel, float room, String box) {
        float width = PixelFont.width(shown, pixel);
        if (width <= room) return;
        float character = PixelFont.width("WW", pixel) - PixelFont.width("W", pixel);
        int over = Math.max(1, (int) Math.ceil((width - room) / character - 1e-4));
        StringBuilder message = new StringBuilder("Invalid ").append(source).append(": too long for ").append(box)
            .append(" by ").append(over).append(over == 1 ? " character" : " characters")
            .append(": \"").append(shown).append("\" is ").append(number(width)).append(" HUD units wide and ")
            .append(number(room)).append(" fit");
        if (!keyActions.isEmpty()) {
            message.append("; it shows the key ").append(keyActions.size() == 1 ? "name" : "names").append(" of ");
            for (int i = 0; i < keyActions.size(); i++) {
                if (i > 0) message.append(", ");
                String action = keyActions.get(i);
                message.append('\'').append(action).append("' (").append(strings.keyLabel(action)).append(')');
            }
            message.append(" from ").append(BINDINGS_FILE).append(", and a shorter key name there shortens it");
        }
        throw new IllegalArgumentException(message.toString());
    }

    private static String number(float value) {
        return TextFormat.decimal(Math.round(value * 10f) / 10f);
    }
}

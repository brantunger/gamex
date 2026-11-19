package io.github.brantunger.gamex.world;

import io.github.brantunger.gamex.data.AssetText;
import io.github.brantunger.gamex.data.TuningFile;
import io.github.brantunger.gamex.hud.TextTemplate;
import io.github.brantunger.gamex.weapon.WeaponSlot;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.EnumSet;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.function.Function;
import java.util.regex.Pattern;

/**
 * Loads level layouts ({@code .map}) and stage definitions (headers + {@code .map} layout, plus {@code campaign.txt})
 * from {@code assets/levels/}.
 *
 * <p>Map-only files (such as {@link #FIRST_PATH}) contain rectangular {@link GameMap} rows directly. Stage files
 * begin with {@code key: value} metadata headers ({@code id}, {@code title}, {@code par},
 * {@code complete_on_all_targets}, and one or more {@code briefing} lines), followed by a blank line and the
 * rectangular {@link GameMap} layout. Lines whose trimmed text begins with {@code //} are treated as comments.
 *
 * <p>A stage file may also hold one {@code loadout} header: the weapons a player deployed straight to the stage
 * starts with, by name and separated by commas (for example {@code loadout: scattergun, raillance}). The names are
 * {@code pistol}, {@code scattergun} and {@code raillance}; the pistol is carried whether it is listed or not, so a
 * stage without the header starts with the pistol alone. The header counts for campaign stages only: from the
 * menu the Training Range is entered with the pistol alone, and {@link #trainingRange()} refuses a file that holds
 * the header.
 *
 * <p>A {@code briefing} line may hold placeholders in braces, which the menus fill in when they show it:
 * {@code {key:action}} for the name of the key bound to an action and {@code {rail.zoom_factor}} for that value of
 * {@code data/weapons.txt} (see {@link TextTemplate}). {@code Levels} is given neither the key names nor the
 * tuning, so here a line keeps its placeholders; a brace without its partner and an unknown placeholder are
 * rejected.
 *
 * <p>The campaign's stages are the files listed in {@link #CAMPAIGN_PATH}, in play order; {@link #stage(String)}
 * finds one of them by its {@code id}. {@link #FIRST_PATH} and {@link #TRAINING_RANGE_PATH} are not campaign stages
 * and have entries of their own.
 *
 * <p>The files are read with {@link AssetText} and the headers are parsed by {@link TuningFile}, the loader shared
 * with the tuning files in {@code assets/data/}; the checks particular to a stage (the form of its {@code id}, a
 * positive {@code par}) are made here.
 */
public final class Levels {

    private static final Logger log = LoggerFactory.getLogger(Levels.class);

    /** Directory inside {@code assets/} holding level layouts, stage definitions, and {@code campaign.txt}. */
    public static final String LEVELS_DIR = "levels";
    /** Internal asset path of the ordered campaign stage manifest. */
    public static final String CAMPAIGN_PATH = LEVELS_DIR + "/campaign.txt";
    /** Internal asset path of the original first level map layout. */
    public static final String FIRST_PATH = LEVELS_DIR + "/first.map";
    /** Internal asset path of the Training Range stage. */
    public static final String TRAINING_RANGE_PATH = LEVELS_DIR + "/training-range.map";

    private static final List<String> SINGLE_HEADERS =
        Arrays.asList("id", "title", "par", "complete_on_all_targets");
    private static final List<String> REPEATED_HEADERS = Collections.singletonList("briefing");
    private static final String LOADOUT_HEADER = "loadout";
    private static final List<String> OPTIONAL_HEADERS = Collections.singletonList(LOADOUT_HEADER);
    /** The weapon names a {@code loadout} header may list, in slot order. */
    private static final Map<String, WeaponSlot> WEAPON_NAMES = weaponNames();
    private static final Pattern STAGE_ID_PATTERN = Pattern.compile("[A-Za-z0-9_-]+");
    private static final Pattern POSITIVE_DECIMAL = Pattern.compile("[+]?([0-9]+(\\.[0-9]*)?|\\.[0-9]+)");

    /** Metadata and map layout for a playable stage. */
    public static final class Stage {
        /** Persistent identifier used for save records. */
        public final String id;
        /** Uppercase stage title shown in briefings and tallies. */
        public final String title;
        /**
         * Short retro terminal log lines displayed before entering the stage, as written: a line may hold
         * placeholders for the menus to fill in.
         */
        public final List<String> briefing;
        /** Par clear time in seconds. */
        public final float parSeconds;
        /** Whether destroying every target completes the stage automatically. */
        public final boolean completeOnAllTargets;
        /** The parsed level map. */
        public final GameMap map;
        /**
         * The weapons a player deployed straight to the stage starts with, from its {@code loadout} header; the
         * pistol is always among them, and is the only one when the stage has no such header. Unmodifiable.
         */
        public final Set<WeaponSlot> startingWeapons;
        /** The file the stage was read from, as error messages name it; null for a stage built in code. */
        public final String path;

        Stage(String id, String title, float parSeconds, boolean completeOnAllTargets, GameMap map,
              String... briefing) {
            this(id, title, parSeconds, completeOnAllTargets, map, EnumSet.of(WeaponSlot.PISTOL), briefing);
        }

        Stage(String id, String title, float parSeconds, boolean completeOnAllTargets, GameMap map,
              Set<WeaponSlot> startingWeapons, String... briefing) {
            this(null, id, title, parSeconds, completeOnAllTargets, map, startingWeapons, briefing);
        }

        Stage(String path, String id, String title, float parSeconds, boolean completeOnAllTargets, GameMap map,
              Set<WeaponSlot> startingWeapons, String... briefing) {
            this.path = path;
            this.id = id;
            this.title = title;
            this.parSeconds = parSeconds;
            this.completeOnAllTargets = completeOnAllTargets;
            this.map = map;
            EnumSet<WeaponSlot> weapons = EnumSet.of(WeaponSlot.PISTOL);
            weapons.addAll(startingWeapons);
            this.startingWeapons = Collections.unmodifiableSet(weapons);
            this.briefing = Collections.unmodifiableList(Arrays.asList(briefing));
        }
    }

    private Levels() {}

    private static Map<String, WeaponSlot> weaponNames() {
        Map<String, WeaponSlot> names = new LinkedHashMap<>();
        names.put("pistol", WeaponSlot.PISTOL);
        names.put("scattergun", WeaponSlot.SCATTERGUN);
        names.put("raillance", WeaponSlot.RAIL_LANCE);
        return Collections.unmodifiableMap(names);
    }

    /**
     * Loads the original first level from {@link #FIRST_PATH}: a brick hall with pillars where the player starts, a
     * stone hall to the east, and two panelled rooms to the south, joined by corridors into a loop.
     * @return the parsed map
     */
    public static GameMap first() {
        return loadMap(FIRST_PATH);
    }

    /**
     * Loads the Training Range from {@link #TRAINING_RANGE_PATH}. Its file may not hold a {@code loadout} header:
     * from the menu the range is entered with the pistol alone, so the header would be read and then ignored.
     * @return the Training Range stage
     * @throws IllegalArgumentException if the file holds a {@code loadout} header; the message names the file and
     *     the line
     */
    public static Stage trainingRange() {
        return loadStage(TRAINING_RANGE_PATH, false);
    }

    /** @return the ordered list of campaign stages listed in {@link #CAMPAIGN_PATH}. */
    public static List<Stage> campaign() {
        return loadCampaign(CAMPAIGN_PATH);
    }

    /**
     * Finds a campaign stage by the {@code id} header of its file (for example {@code "01_outer_breach"}), wherever
     * {@link #CAMPAIGN_PATH} lists it. The Training Range is not a campaign stage; see {@link #trainingRange()}.
     * @param id the stage's persistent identifier
     * @return the campaign stage with that id
     * @throws IllegalArgumentException if no stage listed in {@link #CAMPAIGN_PATH} has the id
     */
    public static Stage stage(String id) {
        List<Stage> campaign = campaign();
        List<String> ids = new ArrayList<>();
        for (Stage stage : campaign) {
            if (stage.id.equals(id)) return stage;
            ids.add(stage.id);
        }
        throw new IllegalArgumentException("Unknown stage id '" + id + "': the stages listed in " + CAMPAIGN_PATH
            + " are " + String.join(", ", ids));
    }

    /**
     * Reads and parses a raw {@link GameMap} layout file from {@code assets/}.
     * @param assetPath internal path inside {@code assets/} (for example {@code "levels/first.map"})
     * @return the parsed map
     */
    public static GameMap loadMap(String assetPath) {
        GameMap map = parseMap(assetPath, AssetText.read("level", assetPath));
        log.debug("Loaded map {}: {}x{}", assetPath, map.getWidth(), map.getHeight());
        return map;
    }

    /**
     * Reads and parses a playable {@link Stage} file (metadata headers + blank line + map layout) from {@code assets/}.
     * @param assetPath internal path inside {@code assets/} (for example {@code "levels/01-outer-breach.map"})
     * @return the parsed stage
     */
    public static Stage loadStage(String assetPath) {
        return loadStage(assetPath, true);
    }

    private static Stage loadStage(String assetPath, boolean loadoutAllowed) {
        Stage stage = parseStage(assetPath, AssetText.read("level", assetPath), loadoutAllowed);
        log.debug("Loaded stage {} from {}: {}x{}, par {} s", stage.id, assetPath, stage.map.getWidth(),
            stage.map.getHeight(), stage.parSeconds);
        return stage;
    }

    /**
     * Reads and parses an ordered campaign manifest file from {@code assets/}, loading each referenced stage inside
     * {@link #LEVELS_DIR}.
     * @param assetPath internal path inside {@code assets/} (for example {@link #CAMPAIGN_PATH})
     * @return unmodifiable ordered list of campaign stages
     */
    public static List<Stage> loadCampaign(String assetPath) {
        List<Stage> stages = parseCampaign(assetPath, AssetText.read("level", assetPath), Levels::loadStage);
        log.info("Loaded {} campaign stages from {}", stages.size(), assetPath);
        return stages;
    }

    /**
     * Parses raw map-file text into a {@link GameMap}. Leading/trailing blank lines and {@code //} comments are
     * ignored; blank lines inside the grid or invalid layout rows throw {@link IllegalArgumentException}.
     * @param assetPath path label included in error messages
     * @param text raw UTF-8 file contents
     * @return parsed map
     */
    public static GameMap parseMap(String assetPath, String text) {
        if (text == null) {
            throw new IllegalArgumentException("Map text for " + assetPath + " is null");
        }
        List<String> lines = AssetText.splitLines(text);
        List<String> rows = new ArrayList<>();
        boolean afterRows = false;
        for (int i = 0; i < lines.size(); i++) {
            String line = lines.get(i);
            String trimmed = line.trim();
            if (AssetText.isComment(trimmed)) continue;
            if (trimmed.isEmpty()) {
                if (!rows.isEmpty()) afterRows = true;
                continue;
            }
            if (afterRows) {
                throw new IllegalArgumentException(
                    "Blank line inside map layout in " + assetPath + " before line " + (i + 1));
            }
            rows.add(line);
        }
        if (rows.isEmpty()) {
            throw new IllegalArgumentException("Map file " + assetPath + " contains no layout rows");
        }
        try {
            return GameMap.parse(rows.toArray(new String[0]));
        } catch (IllegalArgumentException e) {
            throw new IllegalArgumentException("Invalid map layout in " + assetPath + ": " + e.getMessage(), e);
        }
    }

    /**
     * Parses stage-file text (header {@code key: value} block + blank line + {@link GameMap} layout) into a
     * {@link Stage}. The {@code loadout} header may be left out.
     * @param assetPath path label included in error messages
     * @param text raw UTF-8 file contents
     * @return parsed stage
     */
    public static Stage parseStage(String assetPath, String text) {
        return parseStage(assetPath, text, true);
    }

    private static Stage parseStage(String assetPath, String text, boolean loadoutAllowed) {
        if (text == null) {
            throw new IllegalArgumentException("Stage text for " + assetPath + " is null");
        }
        List<String> lines = AssetText.splitLines(text);
        TuningFile header = TuningFile.parseHeader(assetPath, lines, "stage", "map layout", SINGLE_HEADERS,
            REPEATED_HEADERS, OPTIONAL_HEADERS);

        TuningFile.Entry idHeader = header.entry("id");
        String id = idHeader.value;
        if (!STAGE_ID_PATTERN.matcher(id).matches()) {
            throw new IllegalArgumentException("Invalid stage id '" + id + "' in " + assetPath
                + " at line " + idHeader.line);
        }
        String title = header.getString("title");
        TuningFile.Entry parHeader = header.entry("par");
        float parSeconds = parseParSeconds(assetPath, parHeader.line, parHeader.value);
        boolean completeOnAllTargets = header.getBoolean("complete_on_all_targets");
        List<String> briefing = new ArrayList<>();
        for (TuningFile.Entry line : header.entries("briefing")) {
            if (line.value.isEmpty()) {
                throw new IllegalArgumentException("Empty 'briefing' line in " + assetPath
                    + " at line " + line.line);
            }
            TextTemplate.checkPlaceholders(line.value, "'briefing' in " + assetPath + " at line " + line.line,
                Collections.emptyList());
            briefing.add(line.value);
        }
        Set<WeaponSlot> startingWeapons = parseLoadout(header, loadoutAllowed);

        int index = header.bodyStart();
        List<String> rows = new ArrayList<>();
        boolean afterRows = false;
        while (index < lines.size()) {
            String line = lines.get(index);
            int lineNumber = index + 1;
            index++;
            String trimmed = line.trim();
            if (AssetText.isComment(trimmed)) continue;
            if (trimmed.isEmpty()) {
                if (!rows.isEmpty()) afterRows = true;
                continue;
            }
            if (afterRows) {
                throw new IllegalArgumentException(
                    "Blank line inside map layout in " + assetPath + " before line " + lineNumber);
            }
            rows.add(line);
        }
        if (rows.isEmpty()) {
            throw new IllegalArgumentException("Stage " + assetPath + " contains no map layout rows");
        }
        GameMap map;
        try {
            map = GameMap.parse(rows.toArray(new String[0]));
        } catch (IllegalArgumentException e) {
            throw new IllegalArgumentException("Invalid map layout in " + assetPath + ": " + e.getMessage(), e);
        }
        return new Stage(assetPath, id, title, parSeconds, completeOnAllTargets, map, startingWeapons,
            briefing.toArray(new String[0]));
    }

    /**
     * Parses a campaign manifest listing stage file names inside {@link #LEVELS_DIR}, in play order.
     * @param assetPath manifest path label included in error messages
     * @param text raw UTF-8 manifest contents
     * @param stageLoader resolves each {@code "levels/<entry>"} path into a {@link Stage}
     * @return unmodifiable ordered list of campaign stages
     */
    public static List<Stage> parseCampaign(String assetPath, String text, Function<String, Stage> stageLoader) {
        if (text == null) {
            throw new IllegalArgumentException("Campaign manifest text for " + assetPath + " is null");
        }
        List<String> lines = AssetText.splitLines(text);
        List<Stage> stages = new ArrayList<>();
        Set<String> seenIds = new HashSet<>();
        for (int i = 0; i < lines.size(); i++) {
            int lineNumber = i + 1;
            String entry = lines.get(i).trim();
            if (entry.isEmpty() || AssetText.isComment(entry)) continue;
            if (entry.contains("/") || entry.contains("\\") || entry.contains("..") || !entry.endsWith(".map")) {
                throw new IllegalArgumentException("Invalid stage file name '" + entry + "' in " + assetPath
                    + " at line " + lineNumber);
            }
            Stage stage = stageLoader.apply(LEVELS_DIR + "/" + entry);
            if (!seenIds.add(stage.id)) {
                throw new IllegalArgumentException("Duplicate campaign stage id '" + stage.id + "' in " + assetPath
                    + " at line " + lineNumber);
            }
            stages.add(stage);
        }
        if (stages.isEmpty()) {
            throw new IllegalArgumentException("Campaign manifest " + assetPath + " lists no stages");
        }
        return Collections.unmodifiableList(stages);
    }

    /**
     * Reads the weapons of the optional {@code loadout} header; a stage without it starts with the pistol. The
     * Training Range is not allowed the header, and is refused when it holds one.
     */
    private static Set<WeaponSlot> parseLoadout(TuningFile header, boolean loadoutAllowed) {
        Set<WeaponSlot> weapons = EnumSet.of(WeaponSlot.PISTOL);
        if (!header.has(LOADOUT_HEADER)) return weapons;
        if (!loadoutAllowed) {
            throw header.invalid(LOADOUT_HEADER,
                "the Training Range takes no loadout header (from the menu it is entered with the pistol alone)");
        }
        Set<String> listed = new HashSet<>();
        // The limit keeps the empty name a trailing comma leaves, so it is reported and not dropped.
        for (String item : header.getString(LOADOUT_HEADER).split(",", -1)) {
            String name = item.trim();
            WeaponSlot weapon = WEAPON_NAMES.get(name);
            if (weapon == null) {
                throw header.invalid(LOADOUT_HEADER, "unknown weapon '" + name + "' (the weapons are "
                    + String.join(", ", WEAPON_NAMES.keySet()) + ")");
            }
            if (!listed.add(name)) {
                throw header.invalid(LOADOUT_HEADER, "weapon '" + name + "' is listed twice");
            }
            weapons.add(weapon);
        }
        return weapons;
    }

    private static float parseParSeconds(String assetPath, int lineNumber, String value) {
        if (!POSITIVE_DECIMAL.matcher(value).matches()) {
            throw new IllegalArgumentException("Invalid 'par' seconds '" + value + "' in " + assetPath
                + " at line " + lineNumber);
        }
        float parsed;
        try {
            parsed = Float.parseFloat(value);
        } catch (NumberFormatException e) {
            throw new IllegalArgumentException("Invalid 'par' seconds '" + value + "' in " + assetPath
                + " at line " + lineNumber, e);
        }
        if (!Float.isFinite(parsed) || parsed <= 0f) {
            throw new IllegalArgumentException("'par' seconds must be positive in " + assetPath
                + " at line " + lineNumber + ", got " + value);
        }
        return parsed;
    }
}

package io.github.brantunger.gamex.world;

import io.github.brantunger.gamex.data.AssetText;
import io.github.brantunger.gamex.data.TuningFile;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.HashSet;
import java.util.List;
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
    /** Internal asset path of Campaign Stage 01 ("01: OUTER BREACH"). */
    public static final String OUTER_BREACH_PATH = LEVELS_DIR + "/01-outer-breach.map";
    /** Internal asset path of Campaign Stage 02 ("02: COOLANT CORE"). */
    public static final String COOLANT_CORE_PATH = LEVELS_DIR + "/02-coolant-core.map";
    /** Internal asset path of Campaign Stage 03 ("03: SECURITY HUB"). */
    public static final String SECURITY_HUB_PATH = LEVELS_DIR + "/03-security-hub.map";
    /** Internal asset path of Campaign Stage 04 ("04: LOCKDOWN WING"). */
    public static final String LOCKDOWN_WING_PATH = LEVELS_DIR + "/04-lockdown-wing.map";
    /** Internal asset path of Campaign Stage 05 ("05: COMMAND VAULT"). */
    public static final String COMMAND_VAULT_PATH = LEVELS_DIR + "/05-command-vault.map";
    /** Internal asset path of Campaign Stage 06 ("06: WARDEN CORE"). */
    public static final String WARDEN_CORE_PATH = LEVELS_DIR + "/06-warden-core.map";

    private static final List<String> SINGLE_HEADERS =
        Arrays.asList("id", "title", "par", "complete_on_all_targets");
    private static final List<String> REPEATED_HEADERS = Collections.singletonList("briefing");
    private static final Pattern STAGE_ID_PATTERN = Pattern.compile("[A-Za-z0-9_-]+");
    private static final Pattern POSITIVE_DECIMAL = Pattern.compile("[+]?([0-9]+(\\.[0-9]*)?|\\.[0-9]+)");

    /** Metadata and map layout for a playable stage. */
    public static final class Stage {
        /** Persistent identifier used for save records. */
        public final String id;
        /** Uppercase stage title shown in briefings and tallies. */
        public final String title;
        /** Short retro terminal log lines displayed before entering the stage. */
        public final List<String> briefing;
        /** Par clear time in seconds. */
        public final float parSeconds;
        /** Whether destroying every target completes the stage automatically. */
        public final boolean completeOnAllTargets;
        /** The parsed level map. */
        public final GameMap map;

        Stage(String id, String title, float parSeconds, boolean completeOnAllTargets, GameMap map,
              String... briefing) {
            this.id = id;
            this.title = title;
            this.parSeconds = parSeconds;
            this.completeOnAllTargets = completeOnAllTargets;
            this.map = map;
            this.briefing = Collections.unmodifiableList(Arrays.asList(briefing));
        }
    }

    private Levels() {}

    /**
     * Loads the original first level from {@link #FIRST_PATH}: a brick hall with pillars where the player starts, a
     * stone hall to the east, and two panelled rooms to the south, joined by corridors into a loop.
     * @return the parsed map
     */
    public static GameMap first() {
        return loadMap(FIRST_PATH);
    }

    /** @return the Training Range stage loaded from {@link #TRAINING_RANGE_PATH}. */
    public static Stage trainingRange() {
        return loadStage(TRAINING_RANGE_PATH);
    }

    /** @return Campaign Stage 01 ("01: OUTER BREACH") loaded from {@link #OUTER_BREACH_PATH}. */
    public static Stage outerBreach() {
        return loadStage(OUTER_BREACH_PATH);
    }

    /** @return Campaign Stage 02 ("02: COOLANT CORE") loaded from {@link #COOLANT_CORE_PATH}. */
    public static Stage coolantCore() {
        return loadStage(COOLANT_CORE_PATH);
    }

    /** @return Campaign Stage 03 ("03: SECURITY HUB") loaded from {@link #SECURITY_HUB_PATH}. */
    public static Stage securityHub() {
        return loadStage(SECURITY_HUB_PATH);
    }

    /** @return Campaign Stage 04 ("04: LOCKDOWN WING") loaded from {@link #LOCKDOWN_WING_PATH}. */
    public static Stage lockdownWing() {
        return loadStage(LOCKDOWN_WING_PATH);
    }

    /** @return Campaign Stage 05 ("05: COMMAND VAULT") loaded from {@link #COMMAND_VAULT_PATH}. */
    public static Stage commandVault() {
        return loadStage(COMMAND_VAULT_PATH);
    }

    /** @return Campaign Stage 06 ("06: WARDEN CORE") loaded from {@link #WARDEN_CORE_PATH}. */
    public static Stage wardenCore() {
        return loadStage(WARDEN_CORE_PATH);
    }

    /** @return the ordered list of campaign stages listed in {@link #CAMPAIGN_PATH}. */
    public static List<Stage> campaign() {
        return loadCampaign(CAMPAIGN_PATH);
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
        Stage stage = parseStage(assetPath, AssetText.read("level", assetPath));
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
     * {@link Stage}.
     * @param assetPath path label included in error messages
     * @param text raw UTF-8 file contents
     * @return parsed stage
     */
    public static Stage parseStage(String assetPath, String text) {
        if (text == null) {
            throw new IllegalArgumentException("Stage text for " + assetPath + " is null");
        }
        List<String> lines = AssetText.splitLines(text);
        TuningFile header = TuningFile.parseHeader(assetPath, lines, "stage", "map layout", SINGLE_HEADERS,
            REPEATED_HEADERS);

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
            briefing.add(line.value);
        }

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
        return new Stage(id, title, parSeconds, completeOnAllTargets, map, briefing.toArray(new String[0]));
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

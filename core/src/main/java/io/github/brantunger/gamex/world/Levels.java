package io.github.brantunger.gamex.world;

import com.badlogic.gdx.Gdx;

import java.io.IOException;
import java.io.InputStream;
import java.io.UncheckedIOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
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
 */
public final class Levels {

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

    private static final char BYTE_ORDER_MARK = 0xFEFF;
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
        return parseMap(assetPath, readAssetText(assetPath));
    }

    /**
     * Reads and parses a playable {@link Stage} file (metadata headers + blank line + map layout) from {@code assets/}.
     * @param assetPath internal path inside {@code assets/} (for example {@code "levels/01-outer-breach.map"})
     * @return the parsed stage
     */
    public static Stage loadStage(String assetPath) {
        return parseStage(assetPath, readAssetText(assetPath));
    }

    /**
     * Reads and parses an ordered campaign manifest file from {@code assets/}, loading each referenced stage inside
     * {@link #LEVELS_DIR}.
     * @param assetPath internal path inside {@code assets/} (for example {@link #CAMPAIGN_PATH})
     * @return unmodifiable ordered list of campaign stages
     */
    public static List<Stage> loadCampaign(String assetPath) {
        return parseCampaign(assetPath, readAssetText(assetPath), Levels::loadStage);
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
        List<String> lines = splitLines(text);
        List<String> rows = new ArrayList<>();
        boolean afterRows = false;
        for (int i = 0; i < lines.size(); i++) {
            String line = lines.get(i);
            String trimmed = line.trim();
            if (isComment(trimmed)) continue;
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
        List<String> lines = splitLines(text);
        String id = null;
        String title = null;
        Float parSeconds = null;
        Boolean completeOnAllTargets = null;
        List<String> briefing = new ArrayList<>();
        boolean seenAnyHeader = false;
        int index = 0;

        while (index < lines.size()) {
            String line = lines.get(index);
            int lineNumber = index + 1;
            index++;
            String trimmed = line.trim();
            if (isComment(trimmed)) continue;
            if (trimmed.isEmpty()) {
                if (seenAnyHeader) break;
                continue;
            }
            int colon = line.indexOf(':');
            if (colon <= 0) {
                throw new IllegalArgumentException(
                    "Expected 'key: value' stage header or blank line before map layout in "
                        + assetPath + " at line " + lineNumber);
            }
            seenAnyHeader = true;
            String key = line.substring(0, colon).trim();
            String value = line.substring(colon + 1).trim();
            switch (key) {
                case "id":
                    if (id != null) {
                        throw new IllegalArgumentException("Duplicate 'id' header in " + assetPath
                            + " at line " + lineNumber);
                    }
                    if (!STAGE_ID_PATTERN.matcher(value).matches()) {
                        throw new IllegalArgumentException("Invalid stage id '" + value + "' in " + assetPath
                            + " at line " + lineNumber);
                    }
                    id = value;
                    break;
                case "title":
                    if (title != null) {
                        throw new IllegalArgumentException("Duplicate 'title' header in " + assetPath
                            + " at line " + lineNumber);
                    }
                    if (value.isEmpty()) {
                        throw new IllegalArgumentException("Empty 'title' header in " + assetPath
                            + " at line " + lineNumber);
                    }
                    title = value;
                    break;
                case "par":
                    if (parSeconds != null) {
                        throw new IllegalArgumentException("Duplicate 'par' header in " + assetPath
                            + " at line " + lineNumber);
                    }
                    parSeconds = parseParSeconds(assetPath, lineNumber, value);
                    break;
                case "complete_on_all_targets":
                    if (completeOnAllTargets != null) {
                        throw new IllegalArgumentException(
                            "Duplicate 'complete_on_all_targets' header in " + assetPath + " at line " + lineNumber);
                    }
                    if ("true".equals(value)) {
                        completeOnAllTargets = Boolean.TRUE;
                    } else if ("false".equals(value)) {
                        completeOnAllTargets = Boolean.FALSE;
                    } else {
                        throw new IllegalArgumentException(
                            "Expected 'true' or 'false' for 'complete_on_all_targets' in "
                                + assetPath + " at line " + lineNumber + ", got '" + value + "'");
                    }
                    break;
                case "briefing":
                    if (value.isEmpty()) {
                        throw new IllegalArgumentException("Empty 'briefing' line in " + assetPath
                            + " at line " + lineNumber);
                    }
                    briefing.add(value);
                    break;
                default:
                    throw new IllegalArgumentException("Unknown stage header '" + key + "' in " + assetPath
                        + " at line " + lineNumber);
            }
        }

        if (id == null) {
            throw new IllegalArgumentException("Missing 'id' header in " + assetPath);
        }
        if (title == null) {
            throw new IllegalArgumentException("Missing 'title' header in " + assetPath);
        }
        if (parSeconds == null) {
            throw new IllegalArgumentException("Missing 'par' header in " + assetPath);
        }
        if (completeOnAllTargets == null) {
            throw new IllegalArgumentException("Missing 'complete_on_all_targets' header in " + assetPath);
        }
        if (briefing.isEmpty()) {
            throw new IllegalArgumentException("Missing 'briefing' header in " + assetPath);
        }

        List<String> rows = new ArrayList<>();
        boolean afterRows = false;
        while (index < lines.size()) {
            String line = lines.get(index);
            int lineNumber = index + 1;
            index++;
            String trimmed = line.trim();
            if (isComment(trimmed)) continue;
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
        List<String> lines = splitLines(text);
        List<Stage> stages = new ArrayList<>();
        Set<String> seenIds = new HashSet<>();
        for (int i = 0; i < lines.size(); i++) {
            int lineNumber = i + 1;
            String entry = lines.get(i).trim();
            if (entry.isEmpty() || isComment(entry)) continue;
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

    private static boolean isComment(String trimmedLine) {
        return trimmedLine.startsWith("//");
    }

    private static List<String> splitLines(String text) {
        int start = (!text.isEmpty() && text.charAt(0) == BYTE_ORDER_MARK) ? 1 : 0;
        List<String> lines = new ArrayList<>();
        int lineStart = start;
        int len = text.length();
        for (int i = start; i < len; i++) {
            char c = text.charAt(i);
            if (c == '\r' || c == '\n') {
                lines.add(text.substring(lineStart, i));
                if (c == '\r' && i + 1 < len && text.charAt(i + 1) == '\n') {
                    i++;
                }
                lineStart = i + 1;
            }
        }
        if (lineStart < len) {
            lines.add(text.substring(lineStart, len));
        }
        return lines;
    }

    private static String readAssetText(String assetPath) {
        if (assetPath == null || assetPath.trim().isEmpty()) {
            throw new IllegalArgumentException("Level asset path must not be blank");
        }
        if (Gdx.files != null) {
            return Gdx.files.internal(assetPath).readString("UTF-8");
        }
        List<Path> candidates = new ArrayList<>();
        String configured = System.getProperty("gamex.assets");
        if (configured != null && !configured.trim().isEmpty()) {
            candidates.add(Paths.get(configured, assetPath));
        }
        candidates.add(Paths.get("..", "assets", assetPath));
        candidates.add(Paths.get("assets", assetPath));
        candidates.add(Paths.get(assetPath));
        for (Path candidate : candidates) {
            if (!Files.isRegularFile(candidate)) continue;
            try {
                return Files.readString(candidate, StandardCharsets.UTF_8);
            } catch (IOException e) {
                throw new UncheckedIOException("Failed to read level asset " + candidate, e);
            }
        }
        String resourcePath = assetPath.replace('\\', '/');
        ClassLoader loader = Levels.class.getClassLoader();
        if (loader != null) {
            try (InputStream in = loader.getResourceAsStream(resourcePath)) {
                if (in != null) {
                    return new String(in.readAllBytes(), StandardCharsets.UTF_8);
                }
            } catch (IOException e) {
                throw new UncheckedIOException("Failed to read level resource " + resourcePath, e);
            }
        }
        List<Path> tried = new ArrayList<>(candidates.size());
        for (Path candidate : candidates) {
            tried.add(candidate.toAbsolutePath().normalize());
        }
        throw new IllegalStateException("The level asset " + assetPath + " was not found. Set the system property "
            + "gamex.assets to the assets folder, or run from the root project or a subproject. Tried: " + tried);
    }
}

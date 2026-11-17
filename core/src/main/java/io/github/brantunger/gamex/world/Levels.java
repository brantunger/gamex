package io.github.brantunger.gamex.world;

import java.util.Arrays;
import java.util.Collections;
import java.util.List;

/** The built-in level layouts and stage definitions. */
public final class Levels {

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
     * The original first level: a brick hall with pillars where the player starts, a stone hall to the east, and two
     * panelled rooms to the south, joined by corridors into a loop. Kept intact for deterministic demo runs and tests.
     * @return the parsed map
     */
    public static GameMap first() {
        return GameMap.parse(
            "#############&&&&&&&&&&&&&",
            "#........####............&",
            "#........####..o......O..&",
            "#..#..#..####....&..&....&",
            "#...>....................&",
            "#..#..#..####....&..&....&",
            "#........####............&",
            "#....o...####.....o......&",
            "####.########............&",
            "####.########......O.....&",
            "####.########&&&&&&&.&&&&&",
            "%%%%.%%%%%%%%%%%%%%%.%%%%%",
            "%..........%%%%%.........%",
            "%..o...%%..%%%%%...%.%...%",
            "%......%%..%%%%%.........%",
            "%........................%",
            "%..........%%%%%....O....%",
            "%.O......o.%%%%%.o.....o.%",
            "%..........%%%%%.........%",
            "%%%%%%%%%%%%%%%%%%%%%%%%%%");
    }

    /** @return the Training Range stage, stocked with practice targets, recovery pickups and the Scattergun. */
    public static Stage trainingRange() {
        GameMap map = GameMap.parse(
            "#############&&&&&&&&&&&&&",
            "#.a......####..........e.&",
            "#.....W..####..o......O..&",
            "#..#..#..####....&..&....&",
            "#...>....................&",
            "#..#..#..####....&..&....&",
            "#.....s..####............&",
            "#.+..o...####.....o......&",
            "####.########............&",
            "####.########......O..+..&",
            "####.########&&&&&&&.&&&&&",
            "%%%%.%%%%%%%%%%%%%%%.%%%%%",
            "%........e.%%%%%.........%",
            "%..o...%%..%%%%%...%.%...%",
            "%......%%..%%%%%.........%",
            "%..........~~.s..........%",
            "%..........%%%%%....O....%",
            "%.O......o.%%%%%.o.....o.%",
            "%.+........%%%%%.......a.%",
            "%%%%%%%%%%%%%%%%%%%%%%%%%%");
        return new Stage(
            "range",
            "TRAINING RANGE",
            45f,
            true,
            map,
            "LIVE FIRE CALIBRATION COURSE ONLINE.",
            "TEST SCATTERGUN, ENERGY DASH, BLAST OCCLUSION AND CHAIN DETONATIONS.",
            "DESTROY ALL 11 TARGETS TO COMPLETE THE RUN.");
    }

    /** @return Campaign Stage 01 ("01: OUTER BREACH"), ending at an extraction portal. */
    public static Stage outerBreach() {
        GameMap map = GameMap.parse(
            "#############&&&&&&&&&&&&&&&",
            "#.a......####..............&",
            "#......W.####..o.D......O..&",
            "#..#..#..####....&...&.....&",
            "#...>................T.....&",
            "#..#..#..####....&...&.....&",
            "#........####...........e..&",
            "#.+..o...####.....o.D......&",
            "####.########..............&",
            "####.########.......O..T+..&",
            "####.########&&&&&&&&.&&&&&&",
            "%%%%.%%%%%%%%%%%%%%%%.%%%%%%",
            "%....s...e.%%%%%%..........%",
            "%..o.o.%%..%%%%%%...%..%...%",
            "%...D..%%..%%%%%%..D.......%",
            "%...........o.........T.s..%",
            "%......T...%%%%%%....O.....%",
            "%.O......o.%%%%%%.o..D...o.%",
            "%.+......a.%%%%%%....+...X.%",
            "%%%%%%%%%%%%%%%%%%%%%%%%%%%%");
        return new Stage(
            "01_outer_breach",
            "01: OUTER BREACH",
            50f,
            false,
            map,
            "AUTOMATED CONTAINMENT LOCKDOWN DETECTED IN SECTOR 01.",
            "COLLECT THE COMBAT SCATTERGUN TO SHRED CLOSE-QUARTERS DRONES.",
            "USE COOLANT CANISTERS AND DASH TO REACH THE EXTRACTION PAD.");
    }

    /** @return Campaign Stage 02 ("02: COOLANT CORE"), introducing mobile Guard Bots across conduit lanes. */
    public static Stage coolantCore() {
        GameMap map = GameMap.parse(
            "&&&&&&&&&&&&&&&&&&&&&&&&&&&&&&",
            "&.>....o..&........&..a.....e&",
            "&.........&..G..o..&....T....&",
            "&...&.&.......&.......&..&...&",
            "&......s..&........&.........&",
            "&.a..D....&..o..D..&..o...G..&",
            "&.+...O...&&&.&&&.&&&&&..&&&&&",
            "&&&&.&&&&&###.#########..#####",
            "#.........#.......#..........#",
            "#...o.G...#..o.T..#...D..o...#",
            "#.........#.......#..........#",
            "#..##.##.....###.....##..##..#",
            "#.........#...s...#..........#",
            "#..e..+...#..G.o..#...G...a..#",
            "#.........#.......#..........#",
            "#####.#######.#######.########",
            "%%%%%.%%%%%%%.%%%%%%%.%%%%%%%%",
            "%....s....+.......o..........%",
            "%..G..o......T.......o...G...%",
            "%.......%.......%............%",
            "%..e....%..D....%....+...X...%",
            "%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%");
        return new Stage(
            "02_coolant_core",
            "02: COOLANT CORE",
            65f,
            false,
            map,
            "HEAVY GUARD BOTS PATROL THE PRIMARY COOLANT CONDUITS.",
            "STRAFE INCOMING BOLTS AND BAIT FIRE INTO VOLATILE CANISTERS.",
            "BREACH THE REACTOR LANES AND REACH THE SOUTH EXTRACTION PAD.");
    }

    /** @return Campaign Stage 03 ("03: SECURITY HUB"), combining all three enemy archetypes in a final gauntlet. */
    public static Stage securityHub() {
        GameMap map = GameMap.parse(
            "#############&&&&&&&&&&&&&&&&&&&",
            "#........####..................&",
            "#.>......####..o.D...&...T....a&",
            "#..#..#..####...s....&.........&",
            "#............G.......&...o..G..&",
            "#..#..#..####..&..o......&.....&",
            "#.a......####..&.....G...&..e..&",
            "#.+..o...&&&&..&&&.&&&&&&&.&&&&&",
            "####.########.....#............%",
            "%%%%.%%%%%%%%.#.#.#..%...%..%..%",
            "%........e..%.#.#.#....T.......%",
            "%..D...o....%.#.#.#..o..G...T..%",
            "%.....%..s....#.#..............%",
            "%..G..%..T..%.#.#.%%%%..%%%%%%.%",
            "%.....%.....%..........o....+..%",
            "%..+..o..D..%.#.#.%.T..........%",
            "%%%%%.%%%%%%%&#.#.%....%%.%%...%",
            "#..........#..#...%..D.%%.%%.G.%",
            "#..o..G..s.#......%............%",
            "#..........#.####.%..o...T.....%",
            "#..#...#..........%.........o..%",
            "#..a...e...#.+....%..G....e..X.%",
            "################################");
        return new Stage(
            "03_security_hub",
            "03: SECURITY HUB",
            80f,
            false,
            map,
            "CENTRAL SECURITY HUB ALERT LEVEL MAXIMUM.",
            "COORDINATED DRONE, TURRET AND GUARD SQUADS PROTECT THE EXIT.",
            "CLEAR THE COMMAND DECK AND EXTRACT TO COMPLETE THE CAMPAIGN.");
    }

    /** @return Campaign Stage 04 ("04: LOCKDOWN WING"), introducing bulkhead doors, Amber keycard, and console lockdown. */
    public static Stage lockdownWing() {
        GameMap map = GameMap.parse(
            "#####&&&&&&&&&&&&&#&&&&&&&&&&&",
            "#...#......o......#.....o...a&",
            "#.>.=....o....G...#..D....T..&",
            "#.a.#.............#..........&",
            "#...#..&...&..D...=....&.....&",
            "#####.............#....&..k..&",
            "&&&&&....o....+...#..G....e..&",
            "&...e..&.....s....#..........&",
            "&&&&&&&&&&&A&&&&&&############",
            "%%%%%%%%#...........##########",
            "%.D..G..#...o...T...#...o....#",
            "%.e.....#.....s.....#....T...#",
            "%%%%L%%%#..#.....#..#........#",
            "%.......#...........#..#..#..#",
            "%...o...=.....G.....#........#",
            "%.......#...........L...G....#",
            "%...S...#..o.....D..#........#",
            "%.+...a.#....s......#..+..o..#",
            "%.......#...e...G...#.....X..#",
            "%%%%%%%%######################");
        return new Stage(
            "04_lockdown_wing",
            "04: LOCKDOWN WING",
            85f,
            false,
            map,
            "BULKHEAD LOCKDOWNS SEAL THE LOWER RESEARCH SECTOR.",
            "LOCATE THE AMBER KEYCARD AND OVERRIDE THE SECURITY CONSOLE.",
            "WATCH FOR AMBUSH BAYS UNSEALED BY CONSOLE OVERRIDE.");
    }

    /** @return Campaign Stage 05 ("05: COMMAND VAULT"), chaining Amber/Cyan keycards and console lockdown override. */
    public static Stage commandVault() {
        GameMap map = GameMap.parse(
            "######&&&&&&&&&&&&&&#&&&&&&&&&&&",
            "#....#......o.......#.....o....&",
            "#.a..#..D.......G...#..G....k..&",
            "#.>..=..............#..........&",
            "#.+..#....&...&.....=....&..T..&",
            "#....#....s...o..T..#....&.....&",
            "######..e.......D...#......+...&",
            "&&&&&&&&&&A&&&&&&&&&############",
            "#.....#.....o....#....o.......+#",
            "#.c...#..G....D..#..G.....T....#",
            "#...o.#.s..##....#.....##..s...#",
            "#.....=..........C.........S...#",
            "#.a.e.#..T....o..#...o.....a...#",
            "#.....#......+...#......e......#",
            "%%%%%%%%%%%L%%%%%%%%%%%####L####",
            "%....o.........o....%..D....G..#",
            "%..G.....%...%....T.%....e..D..#",
            "%........%...%......%..+.......#",
            "%.a..T...s...o....G.############",
            "%..X....o.....+..e..############",
            "%......D.......T....############",
            "%%%%%%%%%%%%%%%%%%%%############");
        return new Stage(
            "05_command_vault",
            "05: COMMAND VAULT",
            95f,
            false,
            map,
            "FINAL OBJECTIVE: BREACH THE DEEP COMMAND VAULT.",
            "CHAIN AMBER AND CYAN CLEARANCES TO REACH OVERWATCH CONTROL.",
            "OVERRIDE VAULT LOCKDOWN AND REACH THE FINAL EXTRACTION PAD.");
    }

    /** @return Campaign Stage 06 ("06: WARDEN CORE"), featuring Shock Grate trenches and the Warden Mech boss. */
    public static Stage wardenCore() {
        GameMap map = GameMap.parse(
            "######&&&&&&&&&&################",
            "#....#..........#..............#",
            "#.W..#..o....k..#..a........s..#",
            "#.s..#....~~....#....&&..&&....#",
            "#.>..=...D..G...#..o........o..#",
            "#.a..#....~~....L......~~......#",
            "#.+..#..e....s..#..D........T..#",
            "#....#..........#....&....&....#",
            "######&&&&A&&&&&#......B.......#",
            "%%%%%%..........#....&....&....#",
            "%..o.%..........#..e........+..#",
            "%.T..=...~~..G..#......~~......#",
            "%....%..........L..o........o..#",
            "%.a..%..........#....&&..&&....#",
            "%..s.%...o...D..#..............#",
            "%..+.%..e....S..#######M########",
            "%..o.%....~~....#..a........+..#",
            "%....%..........#..s...X...e...#",
            "%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%");
        return new Stage(
            "06_warden_core",
            "06: WARDEN CORE",
            110f,
            false,
            map,
            "CORE THREAT DETECTED: DESTROY THE WARDEN MECH TO UNSEAL EXTRACTION.",
            "DASH ACROSS LIVE SHOCK GRATES (~) WITHOUT TAKING ARC DAMAGE.",
            "DEFEAT THE WARDEN MECH (B) TO UNLOCK THE SEALED EXTRACTION BULKHEAD (M).");
    }

    /** @return the ordered list of campaign stages available so far. */
    public static List<Stage> campaign() {
        return Collections.unmodifiableList(Arrays.asList(
            outerBreach(), coolantCore(), securityHub(), lockdownWing(), commandVault(), wardenCore()));
    }
}

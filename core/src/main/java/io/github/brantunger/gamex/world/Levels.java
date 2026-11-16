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

    /** @return the Training Range stage, stocked with practice targets and recovery pickups. */
    public static Stage trainingRange() {
        GameMap map = GameMap.parse(
            "#############&&&&&&&&&&&&&",
            "#.a......####..........e.&",
            "#........####..o......O..&",
            "#..#..#..####....&..&....&",
            "#...>....................&",
            "#..#..#..####....&..&....&",
            "#........####............&",
            "#.+..o...####.....o......&",
            "####.########............&",
            "####.########......O..+..&",
            "####.########&&&&&&&.&&&&&",
            "%%%%.%%%%%%%%%%%%%%%.%%%%%",
            "%........e.%%%%%.........%",
            "%..o...%%..%%%%%...%.%...%",
            "%......%%..%%%%%.........%",
            "%........................%",
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
            "TEST ENERGY DASH, BLAST OCCLUSION AND CHAIN DETONATIONS.",
            "DESTROY ALL 11 TARGETS TO COMPLETE THE RUN.");
    }

    /** @return Campaign Stage 01 ("01: OUTER BREACH"), ending at an extraction portal. */
    public static Stage outerBreach() {
        GameMap map = GameMap.parse(
            "#############&&&&&&&&&&&&&&&",
            "#.a......####..............&",
            "#........####..o.o......O..&",
            "#..#..#..####....&...&.....&",
            "#...>......................&",
            "#..#..#..####....&...&.....&",
            "#........####...........e..&",
            "#.+..o...####.....o.o......&",
            "####.########..............&",
            "####.########.......O...+..&",
            "####.########&&&&&&&&.&&&&&&",
            "%%%%.%%%%%%%%%%%%%%%%.%%%%%%",
            "%........e.%%%%%%..........%",
            "%..o.o.%%..%%%%%%...%..%...%",
            "%......%%..%%%%%%..........%",
            "%...........o..............%",
            "%..........%%%%%%....O.....%",
            "%.O......o.%%%%%%.o......o.%",
            "%.+......a.%%%%%%....+...X.%",
            "%%%%%%%%%%%%%%%%%%%%%%%%%%%%");
        return new Stage(
            "01_outer_breach",
            "01: OUTER BREACH",
            40f,
            false,
            map,
            "AUTOMATED CONTAINMENT LOCKDOWN DETECTED IN SECTOR 01.",
            "VOLATILE COOLANT CANISTERS LINE THE EASTERN AND SOUTHERN HALLS.",
            "CLEAR A PATH AND REACH THE EXTRACTION TELEPORTER PAD.");
    }

    /** @return the ordered list of campaign stages available so far. */
    public static List<Stage> campaign() {
        return Collections.singletonList(outerBreach());
    }
}

package io.github.brantunger.gamex.world;

/** The built-in level layouts. */
public final class Levels {

    private Levels() {}

    /**
     * The first level: a brick hall with pillars where the player starts, a stone hall to the east, and two panelled
     * rooms to the south, joined by corridors into a loop.
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
}

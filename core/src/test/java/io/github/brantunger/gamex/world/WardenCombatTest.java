package io.github.brantunger.gamex.world;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.badlogic.gdx.math.Vector2;
import io.github.brantunger.gamex.GameSimulation;
import io.github.brantunger.gamex.player.Difficulty;
import io.github.brantunger.gamex.player.Player;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

/** Checks the Warden's melee: its walk and run, its three-punch combo with its lunges, and its overdrive. */
class WardenCombatTest {

    private static final float DT = 1f / 60f;
    /** The yaw that faces +x, the way the Warden faces in these arenas. */
    private static final float EAST = 90f;
    private static final float[] PUNCH_TIMES = {0.60f, 1.10f, 1.27f};
    private static final float[] PUNCH_DAMAGE = {16f, 16f, 28f};

    private static GameMap arena() {
        String wall = "########################################";
        String floor = "#......................................#";
        String[] rows = new String[21];
        rows[0] = wall;
        rows[20] = wall;
        for (int row = 1; row < 20; row++) rows[row] = floor;
        rows[10] = "#>.....................................#";
        return GameMap.parse(rows);
    }

    /** What a player does from the moment a combo starts; it is told how long ago that was. */
    private interface Dodge {
        void move(Player player, Enemy warden, float sinceStart);
    }

    /** One combo, from its first step to the end of the recovery after it. */
    private static final class Combo {
        final List<Float> hitTimes = new ArrayList<>();
        final List<Float> hitDamage = new ArrayList<>();
        float comboSeconds;
        float recoverSeconds;
        int punchesThrown;
        /** How far into the clip the Warden was held through its recovery. */
        float heldAt;
    }

    private static void step(Enemy warden, GameMap map, Player player, Difficulty difficulty) {
        warden.update(DT, map, new Pathfinder(), player, difficulty, Collections.emptyList(), null);
    }

    /** Steps a Warden that stands in reach of the player until its combo starts. */
    private static void startCombo(Enemy warden, GameMap map, Player player, Difficulty difficulty) {
        for (int i = 0; i < 120 && warden.getState() != Enemy.State.WINDUP; i++) {
            step(warden, map, player, difficulty);
        }
        assertEquals(Enemy.State.WINDUP, warden.getState(), "the combo starts with the player in reach");
        // The step that starts the combo does not advance it.
        assertEquals(0f, warden.getComboTime(), 1e-6f);
    }

    /** Plays the combo that has just started to the end of its recovery, moving the player as {@code dodge} says. */
    private static Combo playCombo(Enemy warden, GameMap map, Player player, Difficulty difficulty, Dodge dodge) {
        Combo combo = new Combo();
        float elapsed = 0f;
        boolean over = false;
        for (int i = 0; i < 600 && !over; i++) {
            dodge.move(player, warden, elapsed);
            float before = player.getHealth();
            boolean wasCombo = warden.getState() == Enemy.State.WINDUP;
            if (!wasCombo) combo.heldAt = warden.getComboTime();
            step(warden, map, player, difficulty);
            elapsed += DT;
            if (wasCombo) combo.comboSeconds += DT;
            else combo.recoverSeconds += DT;
            // The recovery ends in a chase or, with the player still in reach, straight in the next combo.
            over = !wasCombo && warden.getState() != Enemy.State.RECOVERING;
            if (!over) combo.punchesThrown += warden.consumePunchesThrown();
            if (player.getHealth() < before) {
                combo.hitTimes.add(elapsed);
                combo.hitDamage.add(before - player.getHealth());
            }
            assertFalse(Collision.overlapsWall(map, Collections.emptyList(), warden.getX(), warden.getZ(),
                warden.getRadius() - 1e-3f), "the Warden is inside a wall at " + warden.getX() + ", " + warden.getZ());
        }
        assertTrue(over, "the Warden never came out of its recovery; it is in state " + warden.getState());
        return combo;
    }

    private static final Dodge STAND = (player, warden, sinceStart) -> { };

    /** Puts the player {@code distance} from the Warden, {@code degrees} to the right of where it faces. */
    private static Dodge heldAt(float distance, float degrees) {
        return (player, warden, sinceStart) -> {
            double rad = Math.toRadians(warden.getYaw() + degrees);
            player.position.set(warden.getX() + (float) Math.sin(rad) * distance,
                warden.getZ() - (float) Math.cos(rad) * distance);
        };
    }

    @Test
    void aStandingPlayerTakesTheThreePunchesWhenTheClipThrowsThem() {
        GameMap map = arena();
        Enemy warden = new Enemy(Enemy.Kind.WARDEN, 10.5f, 10.5f, EAST);
        Player player = new Player(11.3f, 10.5f, 270f);
        startCombo(warden, map, player, Difficulty.NORMAL);

        Combo combo = playCombo(warden, map, player, Difficulty.NORMAL, STAND);

        assertEquals(3, combo.hitTimes.size(), "hits at " + combo.hitTimes);
        for (int i = 0; i < 3; i++) {
            assertEquals(PUNCH_TIMES[i], combo.hitTimes.get(i), DT + 1e-4f, "punch " + (i + 1));
            assertEquals(PUNCH_DAMAGE[i], combo.hitDamage.get(i), 1e-3f, "punch " + (i + 1));
        }
        assertEquals(3, combo.punchesThrown);
        assertEquals(Enemy.WARDEN_COMBO_TIME, combo.comboSeconds, DT + 1e-4f, "the whole clip plays");
        assertEquals(Enemy.WARDEN_RECOVER_TIME, combo.recoverSeconds, DT + 1e-4f);
        assertEquals(Enemy.WARDEN_COMBO_TIME, combo.heldAt, 1e-4f, "the recovery holds the end of the clip");
        assertEquals(Enemy.State.WINDUP, warden.getState(), "the player is still there, so the next combo follows");
        assertEquals(0, warden.getPunchesLanded(), "and its count of punches starts again");
    }

    @Test
    void aPunchLandsOnlyWithinItsReach() {
        GameMap map = arena();
        Enemy near = new Enemy(Enemy.Kind.WARDEN, 10.5f, 10.5f, EAST);
        Player nearPlayer = new Player(11.3f, 10.5f, 270f);
        startCombo(near, map, nearPlayer, Difficulty.NORMAL);
        Combo inReach = playCombo(near, map, nearPlayer, Difficulty.NORMAL,
            heldAt(Enemy.WARDEN_PUNCH_REACH - 0.05f, 0f));
        assertEquals(3, inReach.hitTimes.size(), "dead ahead and just inside the reach, every punch lands");

        Enemy far = new Enemy(Enemy.Kind.WARDEN, 10.5f, 10.5f, EAST);
        Player farPlayer = new Player(11.3f, 10.5f, 270f);
        startCombo(far, map, farPlayer, Difficulty.NORMAL);
        // The player is placed before each step and the Warden's lunge then gains about 0.13 on them within it.
        Combo outOfReach = playCombo(far, map, farPlayer, Difficulty.NORMAL,
            heldAt(Enemy.WARDEN_PUNCH_REACH + 0.2f, 0f));
        assertEquals(0, outOfReach.hitTimes.size(), "dead ahead and just outside the reach, none does");
        assertEquals(Player.MAX_HEALTH, farPlayer.getHealth(), 1e-6f);
        assertEquals(3, outOfReach.punchesThrown, "a punch that misses is still thrown, and heard");
        assertEquals(0, far.getPunchesLanded());
    }

    @Test
    void aPunchLandsOnlyWithinItsFrontalArc() {
        GameMap map = arena();
        for (float side : new float[] {-1f, 1f}) {
            Enemy inside = new Enemy(Enemy.Kind.WARDEN, 10.5f, 10.5f, EAST);
            Player insidePlayer = new Player(11.3f, 10.5f, 270f);
            startCombo(inside, map, insidePlayer, Difficulty.NORMAL);
            Combo inArc = playCombo(inside, map, insidePlayer, Difficulty.NORMAL,
                heldAt(0.8f, side * (Enemy.WARDEN_PUNCH_ARC_DEG - 15f)));
            // The player is placed before each step, and the Warden's lunge within it then widens the angle by
            // up to 6 degrees; hence the margin on this side of the arc.
            assertEquals(3, inArc.hitTimes.size(), "inside the arc on side " + side);

            Enemy outside = new Enemy(Enemy.Kind.WARDEN, 10.5f, 10.5f, EAST);
            Player outsidePlayer = new Player(11.3f, 10.5f, 270f);
            startCombo(outside, map, outsidePlayer, Difficulty.NORMAL);
            Combo outOfArc = playCombo(outside, map, outsidePlayer, Difficulty.NORMAL,
                heldAt(0.8f, side * (Enemy.WARDEN_PUNCH_ARC_DEG + 3f)));
            assertEquals(0, outOfArc.hitTimes.size(), "just outside the arc on side " + side + ", well within reach");
        }
    }

    @Test
    void aPlayerStandingOffToOneSideWhenTheComboStartsIsStillHit() {
        GameMap map = arena();
        for (float degrees : new float[] {-35f, -25f, -15f, -5f, 5f, 15f, 25f, 35f}) {
            Enemy warden = new Enemy(Enemy.Kind.WARDEN, 10.5f, 10.5f, EAST);
            Player player = new Player(11.3f, 10.5f, 270f, 100f, 100f);
            startCombo(warden, map, player, Difficulty.NORMAL);
            // Off its facing, as after a step to one side as the combo starts, and standing there from then on.
            // The lunge must stop at them: one that slid round them would leave them behind the Warden's back.
            heldAt(0.8f, degrees).move(player, warden, 0f);

            Combo combo = playCombo(warden, map, player, Difficulty.NORMAL, STAND);

            assertEquals(3, combo.hitTimes.size(), "standing " + degrees + " degrees off its facing: hits at "
                + combo.hitTimes + ", the Warden ended at (" + warden.getX() + ", " + warden.getZ() + ")");
        }
    }

    @Test
    void aPlayerWalkingStraightBackIsCaughtByTheFirstLunge() {
        GameMap map = arena();
        Enemy warden = new Enemy(Enemy.Kind.WARDEN, 10.5f, 10.5f, EAST);
        Player player = new Player(10.5f + Enemy.WARDEN_ATTACK_RANGE - 0.01f, 10.5f, 270f);
        startCombo(warden, map, player, Difficulty.NORMAL);

        Combo combo = playCombo(warden, map, player, Difficulty.NORMAL,
            (p, w, sinceStart) -> p.position.x += Player.MOVE_SPEED * DT);

        assertTrue(combo.hitTimes.size() >= 1, "walking away at " + Player.MOVE_SPEED + " is not enough");
        assertEquals(PUNCH_TIMES[0], combo.hitTimes.get(0), DT + 1e-4f, "it is the first punch that catches them");
        assertTrue(warden.getX() > 12f, "the Warden lunged after them: it is at x " + warden.getX());
    }

    @Test
    void aSidestepFromTheStartOfTheComboAvoidsEveryPunch() {
        GameMap map = arena();
        for (float side : new float[] {-1f, 1f}) {
            Enemy warden = new Enemy(Enemy.Kind.WARDEN, 10.5f, 10.5f, EAST);
            Player player = new Player(10.5f + Enemy.WARDEN_ATTACK_RANGE - 0.01f, 10.5f, 270f);
            startCombo(warden, map, player, Difficulty.NORMAL);

            Combo combo = playCombo(warden, map, player, Difficulty.NORMAL, (p, w, sinceStart) -> {
                // Sideways for as long as the punches come, within the arena.
                if (sinceStart < 1.4f) p.position.y += side * Player.MOVE_SPEED * DT;
            });

            assertEquals(0, combo.hitTimes.size(), "sidestepping to side " + side + ": hits at " + combo.hitTimes);
            assertEquals(3, combo.punchesThrown);
        }
    }

    @Test
    void aDashStraightBackAtTheStartOfTheComboAvoidsEveryPunch() {
        GameMap map = arena();
        Enemy warden = new Enemy(Enemy.Kind.WARDEN, 10.5f, 10.5f, EAST);
        Player player = new Player(10.5f + Enemy.WARDEN_ATTACK_RANGE - 0.01f, 10.5f, 270f);
        startCombo(warden, map, player, Difficulty.NORMAL);

        Combo combo = playCombo(warden, map, player, Difficulty.NORMAL, (p, w, sinceStart) -> {
            float speed = sinceStart < Player.DASH_DURATION ? Player.DASH_SPEED : Player.MOVE_SPEED;
            if (sinceStart < 3f) p.position.x += speed * DT;
        });

        assertEquals(0, combo.hitTimes.size(), "hits at " + combo.hitTimes);
    }

    @Test
    void onEveryDifficultyAndInOverdriveWalkingBackIsCaughtAndASidestepOrADashIsNot() {
        GameMap map = arena();
        for (Difficulty difficulty : Difficulty.values()) {
            for (boolean overdrive : new boolean[] {false, true}) {
                String when = "on " + difficulty + (overdrive ? " in overdrive" : "");
                Dodge[] dodges = {
                    (p, w, sinceStart) -> p.position.x += Player.MOVE_SPEED * DT,
                    (p, w, sinceStart) -> p.position.y += Player.MOVE_SPEED * DT,
                    (p, w, sinceStart) -> p.position.y -= Player.MOVE_SPEED * DT,
                    (p, w, sinceStart) -> p.position.x +=
                        (sinceStart < Player.DASH_DURATION ? Player.DASH_SPEED : Player.MOVE_SPEED) * DT
                };
                String[] names = {"walking back", "sidestepping south", "sidestepping north", "dashing back"};
                for (int i = 0; i < dodges.length; i++) {
                    // A wider arena than the usual one is not needed: the longest of these ends 9.5 from the start.
                    Enemy warden = new Enemy(Enemy.Kind.WARDEN, 10.5f, 10.5f, EAST);
                    if (overdrive) warden.damage(Enemy.Kind.WARDEN.maxHealth * 0.5f);
                    Player player = new Player(10.5f + Enemy.WARDEN_ATTACK_RANGE - 0.01f, 10.5f, 270f, 100f, 100f);
                    startCombo(warden, map, player, difficulty);
                    Dodge dodge = dodges[i];
                    Combo combo = playCombo(warden, map, player, difficulty, (p, w, sinceStart) -> {
                        if (sinceStart < 2.6f) dodge.move(p, w, sinceStart);
                    });
                    if (i == 0) {
                        assertTrue(combo.hitTimes.size() >= 1, names[i] + " " + when + " must not be enough");
                    } else {
                        assertEquals(0, combo.hitTimes.size(), names[i] + " " + when + ": hits at " + combo.hitTimes);
                    }
                }
            }
        }
    }

    @Test
    void theWardenLungesAlongItsFacingAndStopsAtAWall() {
        // The wall is 1.2 ahead of the Warden; its lunges add up to 3.7.
        GameMap map = GameMap.parse(
            "#############",
            "#>..........#",
            "#...........#",
            "#...........#",
            "#############");
        Enemy warden = new Enemy(Enemy.Kind.WARDEN, 10.5f, 2.5f, EAST);
        Player player = new Player(11.3f, 2.5f, 270f);
        startCombo(warden, map, player, Difficulty.NORMAL);

        // The player steps out of the way at once, to where the Warden's slow turn does not find them.
        playCombo(warden, map, player, Difficulty.NORMAL, (p, w, sinceStart) -> p.position.set(2.5f, 1.5f));

        assertTrue(warden.getX() > 11f, "it lunged: x " + warden.getX());
        assertTrue(warden.getX() <= 12f - warden.getRadius() + 1e-3f, "and stopped at the wall: x " + warden.getX());
    }

    @Test
    void aPunchDoesNotLandThroughAClosedDoor() {
        // A corridor with a closed door across it at x 5.5; the door is 0.18 thick.
        GameSimulation sim = new GameSimulation(GameMap.parse(
            "##########",
            "#>...=...#",
            "##########"));
        List<Door> doors = sim.doors;
        assertEquals(1, doors.size());
        assertFalse(doors.get(0).isOpening());
        GameMap map = sim.map;
        Enemy warden = new Enemy(Enemy.Kind.WARDEN, 3.9f, 1.5f, EAST);
        Player player = new Player(4.7f, 1.5f, 270f);
        for (int i = 0; i < 120 && warden.getState() != Enemy.State.WINDUP; i++) {
            warden.update(DT, map, new Pathfinder(), player, Difficulty.NORMAL, Collections.emptyList(), doors, null);
        }
        assertEquals(Enemy.State.WINDUP, warden.getState());

        // The player is at once on the far side of the door, as close to it as they can stand.
        player.position.set(5.5f + Door.HALF_THICKNESS + Player.RADIUS + 0.02f, 1.5f);
        float nearest = Float.MAX_VALUE;
        int thrown = 0;
        while (warden.getState() == Enemy.State.WINDUP) {
            warden.update(DT, map, new Pathfinder(), player, Difficulty.NORMAL, Collections.emptyList(), doors, null);
            int punches = warden.consumePunchesThrown();
            if (punches > 0) {
                nearest = Math.min(nearest, player.position.x - warden.getX());
                thrown += punches;
            }
            assertFalse(Collision.overlapsWall(map, doors, warden.getX(), warden.getZ(), warden.getRadius() - 1e-3f),
                "the Warden lunged into the door: x " + warden.getX());
        }

        assertEquals(3, thrown);
        assertTrue(nearest < Enemy.WARDEN_PUNCH_REACH, "the player was within reach of a punch, " + nearest
            + " away, dead ahead; only the door was between them");
        assertEquals(Player.MAX_HEALTH, player.getHealth(), 1e-6f, "a closed door stops a punch");
        assertEquals(0, warden.getPunchesLanded());
    }

    @Test
    void aLungeDoesNotGoThroughSomethingSolidInItsWay() {
        GameMap map = arena();
        Enemy warden = new Enemy(Enemy.Kind.WARDEN, 10.5f, 10.5f, EAST);
        // A turret stands 1.5 ahead of the Warden: the first lunge alone is 2.3 long.
        Enemy turret = new Enemy(Enemy.Kind.TURRET, 12.0f, 10.5f, 0f);
        List<Damageable> solids = new ArrayList<>();
        solids.add(warden);
        solids.add(turret);
        Player player = new Player(11.3f, 10.5f, 270f);
        for (int i = 0; i < 120 && warden.getState() != Enemy.State.WINDUP; i++) {
            warden.update(DT, map, new Pathfinder(), player, Difficulty.NORMAL, solids, null);
        }
        assertEquals(Enemy.State.WINDUP, warden.getState());

        // The player steps out from between them, far off to one side.
        player.position.set(10.5f, 2.5f);
        float touching = warden.getRadius() + turret.getRadius();
        float nearest = Float.MAX_VALUE;
        while (warden.getState() == Enemy.State.WINDUP) {
            warden.update(DT, map, new Pathfinder(), player, Difficulty.NORMAL, solids, null);
            float apart = Vector2.dst(warden.getX(), warden.getZ(), turret.getX(), turret.getZ());
            assertTrue(apart >= touching - 1e-3f, "the Warden went into the turret: " + apart + " apart at "
                + warden.getComboTime() + " s, and " + touching + " is touching");
            nearest = Math.min(nearest, apart);
        }

        // It slides round the turret, as every body that meets one does, rather than stop dead against it.
        assertTrue(warden.getX() > 10.9f, "it did lunge: x " + warden.getX());
        assertTrue(nearest < touching + 0.05f, "and met the turret on its way: " + nearest + " apart at the nearest");
        assertEquals(Enemy.Kind.TURRET.maxHealth, turret.getHealth(), 1e-6f, "the lunge itself does no damage");
    }

    @Test
    void theTelegraphBuildsUpBeforeEachPunchAndIsGoneBetweenThem() {
        GameMap map = arena();
        Enemy warden = new Enemy(Enemy.Kind.WARDEN, 10.5f, 10.5f, EAST);
        Player player = new Player(11.3f, 10.5f, 270f, 100f, 100f);
        startCombo(warden, map, player, Difficulty.NORMAL);
        assertEquals(0f, warden.getWindupProgress(), 1e-6f, "nothing warns of a punch 0.6 s away");

        float lastBeforeFirst = 0f;
        float betweenPunches = -1f;
        float afterLast = -1f;
        while (warden.getState() == Enemy.State.WINDUP) {
            float before = warden.getWindupProgress();
            float time = warden.getComboTime();
            step(warden, map, player, Difficulty.NORMAL);
            if (time < PUNCH_TIMES[0] && warden.getComboTime() >= PUNCH_TIMES[0]) lastBeforeFirst = before;
            if (time < 0.75f && warden.getComboTime() >= 0.75f) betweenPunches = warden.getWindupProgress();
            if (time < 1.5f && warden.getComboTime() >= 1.5f) afterLast = warden.getWindupProgress();
        }
        assertTrue(lastBeforeFirst > 0.85f, "the telegraph is all but full as the punch lands: " + lastBeforeFirst);
        assertEquals(0f, betweenPunches, 1e-6f, "between the first punch and the build-up to the second");
        assertEquals(0f, afterLast, 1e-6f, "after the last punch");
        assertEquals(0f, warden.getWindupProgress(), 1e-6f, "and in the recovery");
    }

    @Test
    void oneLongStepThatPassesTwoPunchesThrowsBoth() {
        GameMap map = arena();
        Enemy warden = new Enemy(Enemy.Kind.WARDEN, 10.5f, 10.5f, EAST);
        Player player = new Player(11.3f, 10.5f, 270f);
        startCombo(warden, map, player, Difficulty.NORMAL);
        while (warden.getComboTime() < 1.0f) step(warden, map, player, Difficulty.NORMAL);
        assertEquals(1, warden.consumePunchesThrown());
        float before = player.getHealth();

        // The lunges of both punches fall in this step too; the Warden must not pass through the player.
        warden.update(0.3f, map, new Pathfinder(), player, Difficulty.NORMAL, Collections.emptyList(), null);

        assertEquals(2, warden.consumePunchesThrown(), "the punches at 1.10 s and 1.27 s");
        assertEquals(0, warden.consumePunchesThrown(), "each is reported once");
        assertEquals(PUNCH_DAMAGE[1] + PUNCH_DAMAGE[2], before - player.getHealth(), 1e-3f);
    }

    /**
     * @return how far the Warden travels in {@code seconds}; it also checks the speed the Warden reports in every
     *     step, unless {@code expectedSpeed} is negative
     */
    private static float travel(Enemy warden, GameMap map, Pathfinder pathfinder, Player player, float seconds,
                                float expectedSpeed) {
        float travelled = 0f;
        for (int i = 0; i < Math.round(seconds / DT); i++) {
            float x = warden.getX();
            float z = warden.getZ();
            warden.alert();
            pathfinder.compute(map, player.position.x, player.position.y);
            warden.update(DT, map, pathfinder, player, Difficulty.NORMAL, Collections.emptyList(), null);
            travelled += (float) Math.hypot(warden.getX() - x, warden.getZ() - z);
            if (expectedSpeed >= 0f) {
                assertEquals(expectedSpeed, warden.getGroundSpeed(), 1e-4f, "after " + i + " steps");
            }
        }
        return travelled;
    }

    @Test
    void theWardenRunsAtAPlayerItSeesAndOutpacesTheirWalk() {
        GameMap map = arena();
        Enemy warden = new Enemy(Enemy.Kind.WARDEN, 5.5f, 10.5f, EAST);
        Player player = new Player(30.5f, 10.5f, 270f);

        float travelled = travel(warden, map, new Pathfinder(), player, 1f, Enemy.WARDEN_RUN_SPEED);

        assertTrue(warden.isChasing());
        assertEquals(Enemy.WARDEN_RUN_SPEED, travelled, 0.05f);
        assertTrue(Enemy.WARDEN_RUN_SPEED > Player.MOVE_SPEED + 0.3f, "walking away must not work");
        assertTrue(Enemy.WARDEN_RUN_SPEED < Player.DASH_SPEED, "a dash must");
    }

    /** A wall with one gap, far from both of them, between the Warden and the player. */
    private static GameMap dividedMap() {
        return GameMap.parse(
            "#####################",
            "#>........#.........#",
            "#.........#.........#",
            "#.........#.........#",
            "#.........#.........#",
            "#.........#.........#",
            "#...................#",
            "#####################");
    }

    @Test
    void theWardenOnlyWalksAfterAPlayerItCannotSee() {
        GameMap map = dividedMap();
        Enemy warden = new Enemy(Enemy.Kind.WARDEN, 4.5f, 1.5f, EAST);
        Player player = new Player(16.5f, 1.5f, 270f);
        assertFalse(Hitscan.hasLineOfSight(map, Collections.emptyList(), 4.5f, 1.5f, 16.5f, 1.5f));

        float travelled = travel(warden, map, new Pathfinder(), player, 2f, Enemy.Kind.WARDEN.moveSpeed);

        assertFalse(warden.isChasing());
        assertEquals(2f * Enemy.Kind.WARDEN.moveSpeed, travelled, 0.1f, "it walks the way round");
        assertTrue(Enemy.Kind.WARDEN.moveSpeed < 0.5f * Enemy.WARDEN_RUN_SPEED, "a walk, not a slower run");
    }

    @Test
    void theWardenKeepsRunningForAMomentAfterItLosesSightThenWalks() {
        GameMap map = dividedMap();
        Enemy warden = new Enemy(Enemy.Kind.WARDEN, 3.5f, 1.5f, EAST);
        Player player = new Player(8.5f, 1.5f, 270f);
        Pathfinder pathfinder = new Pathfinder();
        travel(warden, map, pathfinder, player, 0.25f, Enemy.WARDEN_RUN_SPEED);

        // The player is suddenly behind the wall.
        player.position.set(16.5f, 1.5f);
        travel(warden, map, pathfinder, player, Enemy.WARDEN_CHASE_MEMORY - 2f * DT, Enemy.WARDEN_RUN_SPEED);
        assertTrue(warden.isChasing(), "a sightline lost for a moment does not slow it");

        travel(warden, map, pathfinder, player, 3f * DT, -1f);
        travel(warden, map, pathfinder, player, 0.5f, Enemy.Kind.WARDEN.moveSpeed);
        assertFalse(warden.isChasing());
    }

    @Test
    void overdriveMakesTheWardenRunFasterPunchHarderAndSoonerAndRecoverSooner() {
        GameMap map = arena();
        Enemy runner = new Enemy(Enemy.Kind.WARDEN, 5.5f, 10.5f, EAST);
        runner.damage(Enemy.Kind.WARDEN.maxHealth * 0.5f);
        assertTrue(runner.isOverdrive());
        float fast = Enemy.WARDEN_RUN_SPEED * Enemy.WARDEN_OVERDRIVE_SPEED_SCALE;
        assertEquals(fast, travel(runner, map, new Pathfinder(), new Player(30.5f, 10.5f, 270f), 1f, fast), 0.05f);
        assertTrue(fast > Enemy.WARDEN_RUN_SPEED + 0.5f);
        assertTrue(fast < Player.DASH_SPEED, "a dash still gets away");

        Enemy warden = new Enemy(Enemy.Kind.WARDEN, 10.5f, 10.5f, EAST);
        warden.damage(Enemy.Kind.WARDEN.maxHealth * 0.5f);
        Player player = new Player(11.3f, 10.5f, 270f);
        startCombo(warden, map, player, Difficulty.NORMAL);
        Combo combo = playCombo(warden, map, player, Difficulty.NORMAL, STAND);

        assertEquals(3, combo.hitTimes.size());
        for (int i = 0; i < 3; i++) {
            assertEquals(PUNCH_TIMES[i] / Enemy.WARDEN_OVERDRIVE_COMBO_RATE, combo.hitTimes.get(i), DT + 1e-4f,
                "punch " + (i + 1) + " comes sooner");
            assertEquals(PUNCH_DAMAGE[i] * Enemy.WARDEN_OVERDRIVE_DAMAGE_SCALE, combo.hitDamage.get(i), 1e-3f,
                "punch " + (i + 1) + " hits harder");
        }
        assertEquals(Enemy.WARDEN_COMBO_TIME / Enemy.WARDEN_OVERDRIVE_COMBO_RATE, combo.comboSeconds, DT + 1e-4f);
        assertEquals(Enemy.WARDEN_RECOVER_TIME * Enemy.WARDEN_OVERDRIVE_RECOVER_SCALE, combo.recoverSeconds,
            DT + 1e-4f);
        assertTrue(Enemy.WARDEN_OVERDRIVE_DAMAGE_SCALE > 1.2f && Enemy.WARDEN_OVERDRIVE_COMBO_RATE > 1.05f
            && Enemy.WARDEN_OVERDRIVE_RECOVER_SCALE < 0.8f && Enemy.WARDEN_OVERDRIVE_TURN_SCALE > 1.2f);
    }

    @Test
    void overdriveTurnsTheWardenFasterInItsCombo() {
        GameMap map = arena();
        float[] turned = new float[2];
        for (int phase = 0; phase < 2; phase++) {
            Enemy warden = new Enemy(Enemy.Kind.WARDEN, 10.5f, 10.5f, EAST);
            if (phase == 1) warden.damage(Enemy.Kind.WARDEN.maxHealth * 0.5f);
            Player player = new Player(11.3f, 10.5f, 270f);
            startCombo(warden, map, player, Difficulty.NORMAL);
            // The player is at once far off to its right, out of every punch's way.
            player.position.set(10.5f, 18.5f);
            for (int i = 0; i < 12; i++) step(warden, map, player, Difficulty.NORMAL);
            turned[phase] = warden.getYaw() - EAST;
        }
        assertEquals(Enemy.WARDEN_COMBO_TURN_RATE * 12f * DT, turned[0], 0.05f, "slowly: a sidestep gets round it");
        assertEquals(turned[0] * Enemy.WARDEN_OVERDRIVE_TURN_SCALE, turned[1], 0.05f);
    }

    @Test
    void difficultyChangesTheTimingOfTheComboAndItsDamage() {
        GameMap map = arena();
        Enemy warden = new Enemy(Enemy.Kind.WARDEN, 10.5f, 10.5f, EAST);
        Player player = new Player(11.3f, 10.5f, 270f);
        startCombo(warden, map, player, Difficulty.HARD);
        Combo combo = playCombo(warden, map, player, Difficulty.HARD, STAND);

        assertEquals(Enemy.WARDEN_COMBO_TIME / Difficulty.HARD.enemySpeedScale, combo.comboSeconds, DT + 1e-4f);
        assertEquals(PUNCH_DAMAGE[0] * Difficulty.HARD.damageTakenScale, combo.hitDamage.get(0), 1e-3f);
        assertEquals(Enemy.WARDEN_RECOVER_TIME / Difficulty.HARD.enemySpeedScale, combo.recoverSeconds, DT + 1e-4f);
    }

    @Test
    void theWardenDoesNotStartItsComboAtAPlayerBehindIt() {
        GameMap map = arena();
        // Facing west with the player in reach to the east: it has to turn round first.
        Enemy warden = new Enemy(Enemy.Kind.WARDEN, 10.5f, 10.5f, 270f);
        Player player = new Player(11.3f, 10.5f, 270f);
        warden.alert();
        step(warden, map, player, Difficulty.NORMAL);
        assertEquals(Enemy.State.PURSUING, warden.getState());

        startCombo(warden, map, player, Difficulty.NORMAL);
        float off = Math.abs(((warden.getYaw() - EAST) % 360f + 540f) % 360f - 180f);
        assertTrue(off <= Enemy.WARDEN_ATTACK_FACING_DEG + 1e-3f, "it faces the player when it starts: off by " + off);
    }
}

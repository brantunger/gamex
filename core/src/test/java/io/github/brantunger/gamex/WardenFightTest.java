package io.github.brantunger.gamex;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.junit.jupiter.api.Assertions.fail;

import com.badlogic.gdx.math.Vector2;
import io.github.brantunger.gamex.player.Difficulty;
import io.github.brantunger.gamex.player.Player;
import io.github.brantunger.gamex.player.PlayerInput;
import io.github.brantunger.gamex.player.PlayerLoadout;
import io.github.brantunger.gamex.weapon.WeaponSlot;
import io.github.brantunger.gamex.world.Collision;
import io.github.brantunger.gamex.world.Damageable;
import io.github.brantunger.gamex.world.Door;
import io.github.brantunger.gamex.world.Enemy;
import io.github.brantunger.gamex.world.GameMap;
import io.github.brantunger.gamex.world.Levels;
import org.junit.jupiter.api.Test;

import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.List;

/** Fights the Warden through the whole simulation, with a player driven by the same input a person gives. */
class WardenFightTest {

    private static final float DT = 1f / 60f;

    /** An open room with the Warden at its west end and the player 10 away in the middle. */
    private static GameMap room() {
        return room(5);
    }

    /** An open room with the Warden in column {@code wardenCol} and the player in column 15, on the same row. */
    private static GameMap room(int wardenCol) {
        String wall = "########################################";
        String floor = "#......................................#";
        String[] rows = new String[21];
        rows[0] = wall;
        rows[20] = wall;
        for (int row = 1; row < 20; row++) rows[row] = floor;
        StringBuilder row = new StringBuilder(floor);
        row.setCharAt(wardenCol, 'B');
        row.setCharAt(15, '>');
        rows[10] = row.toString();
        return GameMap.parse(rows);
    }

    private static Enemy warden(GameSimulation sim) {
        for (Enemy enemy : sim.enemies) {
            if (enemy.getKind() == Enemy.Kind.WARDEN) return enemy;
        }
        return fail("the map has no Warden");
    }

    /** Turns the player to face the Warden, as a person tracking it with the mouse does. */
    private static void aimAt(GameSimulation sim, Enemy warden, PlayerInput input) {
        float wanted = (float) Math.toDegrees(Math.atan2(warden.getX() - sim.player.position.x,
            -(warden.getZ() - sim.player.position.y)));
        input.yawDelta = ((wanted - sim.player.getYaw()) % 360f + 540f) % 360f - 180f;
    }

    private static float gap(GameSimulation sim, Enemy warden) {
        return Vector2.dst(warden.getX(), warden.getZ(), sim.player.position.x, sim.player.position.y);
    }

    @Test
    void theWardenReachesAPlayerAnywhereInItsArenaOnStageSixWithoutOpeningADoor() {
        assertTrue(cellsTheWardenReachesOnStageSix(false) > 140);
    }

    @Test
    void withTheLockdownDoorsOpenTheWardenReachesAPlayerInTheRoomsBeyondThemToo() {
        int shut = cellsTheWardenReachesOnStageSix(false);
        int open = cellsTheWardenReachesOnStageSix(true);
        assertTrue(open > shut + 50, "the open doors let it out of its arena: " + shut + " -> " + open + " cells");
    }

    /**
     * Stands a player in every cell the Warden's room connects to on stage 06 and checks that the Warden gets to
     * them and starts its combo, without any door changing.
     * @param lockdownOpen whether the two lockdown doors are open, as after the console is overridden; otherwise
     *     every door is as the stage starts it, shut
     * @return how many cells a player was stood in
     */
    private static int cellsTheWardenReachesOnStageSix(boolean lockdownOpen) {
        Levels.Stage stage = Levels.stage("06_warden_core");
        GameSimulation survey = new GameSimulation(stage.map, PlayerLoadout.fresh(Difficulty.NORMAL), false);
        openLockdown(survey, lockdownOpen);
        Enemy boss = warden(survey);
        List<Damageable> props = new ArrayList<>();
        props.addAll(survey.targets);
        props.addAll(survey.consoles);

        // Every cell the Warden's room connects to through the doors that are open.
        int width = stage.map.getWidth();
        int height = stage.map.getHeight();
        boolean[][] seen = new boolean[height][width];
        ArrayDeque<int[]> queue = new ArrayDeque<>();
        List<int[]> cells = new ArrayList<>();
        queue.add(new int[] {(int) boss.getX(), (int) boss.getZ()});
        seen[(int) boss.getZ()][(int) boss.getX()] = true;
        while (!queue.isEmpty()) {
            int[] cell = queue.remove();
            cells.add(cell);
            for (int[] step : new int[][] {{1, 0}, {-1, 0}, {0, 1}, {0, -1}}) {
                int col = cell[0] + step[0];
                int row = cell[1] + step[1];
                if (col < 0 || row < 0 || col >= width || row >= height || seen[row][col]) continue;
                if (Collision.overlapsWall(stage.map, survey.doors, col + 0.5f, row + 0.5f, 0.3f)) continue;
                seen[row][col] = true;
                queue.add(new int[] {col, row});
            }
        }
        assertTrue(cells.size() > 150, "the Warden's arena is large: " + cells.size() + " cells");
        List<Boolean> opening = new ArrayList<>();
        for (Door door : survey.doors) opening.add(door.isOpening());

        int tried = 0;
        PlayerInput idle = new PlayerInput();
        Vector2 at = new Vector2();
        for (int[] cell : cells) {
            at.set(cell[0] + 0.5f, cell[1] + 0.5f);
            // A player cannot stand in a canister or a console, and does not stand on a live shock grate.
            if (Collision.overlapsSolid(props, at, Player.RADIUS) || stage.map.isHazard(cell[0], cell[1])) continue;
            GameSimulation sim = new GameSimulation(stage.map, PlayerLoadout.fresh(Difficulty.NORMAL), false);
            Enemy subject = warden(sim);
            for (Enemy enemy : sim.enemies) {
                if (enemy != subject) enemy.damage(99999f);
            }
            openLockdown(sim, lockdownOpen);
            if (Vector2.dst(at.x, at.y, subject.getX(), subject.getZ()) < Player.RADIUS + subject.getRadius()) continue;
            sim.player.position.set(at);
            tried++;

            boolean reached = false;
            for (int i = 0; i < 60 * 60 && !reached; i++) {
                subject.alert();
                sim.update(DT, idle);
                assertFalse(Collision.overlapsWall(sim.map, sim.doors, subject.getX(), subject.getZ(),
                    subject.getRadius() - 1e-3f), "the Warden is inside a wall or a door");
                reached = subject.getState() == Enemy.State.WINDUP;
            }
            assertTrue(reached, "the Warden never got to a player at " + at + "; it is at (" + subject.getX() + ", "
                + subject.getZ() + ") in state " + subject.getState());
            for (int d = 0; d < sim.doors.size(); d++) {
                assertEquals(opening.get(d), sim.doors.get(d).isOpening(),
                    "a door changed while the Warden hunted a player at " + at);
            }
        }
        return tried;
    }

    /** Opens the stage's lockdown doors, as overriding the console does, when {@code open} is set. */
    private static void openLockdown(GameSimulation sim, boolean open) {
        if (!open) return;
        for (Door door : sim.doors) {
            if (door.getLockKind() == Door.LockKind.LOCKDOWN) door.open();
        }
        // The doors take a moment to lift out of the way; the player waits at the stage's start meanwhile.
        PlayerInput idle = new PlayerInput();
        for (int i = 0; i < 180; i++) sim.update(DT, idle);
    }

    @Test
    void aPlayerWhoOnlyBackpedalsIsRunDownAndPunched() {
        // The Warden starts 3 away and gains 0.5 a second, so it is on them well before the far wall, 24 away.
        GameSimulation sim = new GameSimulation(room(12), PlayerLoadout.fresh(Difficulty.NORMAL), false);
        Enemy boss = warden(sim);
        PlayerInput input = new PlayerInput();
        float startGap = gap(sim, boss);
        assertEquals(3f, startGap, 1e-3f);

        float caughtAfter = -1f;
        float gapAfterTwoSeconds = -1f;
        for (int i = 0; i < 20 * 60 && caughtAfter < 0f; i++) {
            aimAt(sim, boss, input);
            input.moveForward = -1f;
            sim.update(DT, input);
            if (i == 120) gapAfterTwoSeconds = gap(sim, boss);
            if (sim.getDamageTaken() > 0f) caughtAfter = (i + 1) * DT;
        }

        assertTrue(gapAfterTwoSeconds < startGap - 0.5f,
            "the Warden gains on a backpedalling player: " + startGap + " -> " + gapAfterTwoSeconds);
        assertTrue(caughtAfter > 0f, "the Warden never hurt a player who only backpedalled; the gap is "
            + gap(sim, boss));
        assertTrue(boss.getPunchesLanded() >= 1);
        assertTrue(sim.player.position.x < 35f, "they were caught in the open, not against the far wall: x "
            + sim.player.position.x);
    }

    /**
     * Stands in the open until the Warden starts a combo, then walks straight back from it, or does what
     * {@code dodge} changes that to, until the combo is over.
     * @return how many of the combo's three punches landed
     */
    private static int punchesLandedOnAPlayerWho(Dodge dodge) {
        GameSimulation sim = new GameSimulation(room(), PlayerLoadout.fresh(Difficulty.NORMAL), false);
        Enemy boss = warden(sim);
        PlayerInput input = new PlayerInput();
        boolean inCombo = false;
        boolean first = true;
        for (int i = 0; i < 30 * 60; i++) {
            aimAt(sim, boss, input);
            input.moveForward = 0f;
            input.moveStrafe = 0f;
            input.dash = false;
            if (boss.getState() == Enemy.State.WINDUP) {
                inCombo = true;
                input.moveForward = -1f;
                dodge.steer(input, first);
                first = false;
            } else if (inCombo) {
                assertEquals(boss.getPunchesLanded() > 0, sim.getDamageTaken() > 0f, "only punches hurt here");
                assertTrue(sim.player.position.x < 35f && sim.player.position.y > 3f && sim.player.position.y < 18f,
                    "the player had room to move throughout: " + sim.player.position);
                return boss.getPunchesLanded();
            }
            sim.update(DT, input);
        }
        return fail("the Warden never finished a combo");
    }

    /** Sets the input for one frame of a combo; {@code first} is true on the combo's first frame. */
    private interface Dodge {
        void steer(PlayerInput input, boolean first);
    }

    @Test
    void walkingStraightBackFromAComboStillTakesAPunch() {
        assertTrue(punchesLandedOnAPlayerWho((input, first) -> { }) >= 1);
    }

    @Test
    void aDashBackAsTheComboStartsAvoidsEveryPunch() {
        assertEquals(0, punchesLandedOnAPlayerWho((input, first) -> input.dash = first));
    }

    @Test
    void sidesteppingThroughTheComboAvoidsEveryPunch() {
        for (float side : new float[] {-1f, 1f}) {
            assertEquals(0, punchesLandedOnAPlayerWho((input, first) -> {
                input.moveForward = 0f;
                input.moveStrafe = side;
            }), "sidestepping to side " + side);
        }
    }

    /**
     * Fights the Warden with one weapon and a plain plan: stand and shoot while it comes, sidestep while it punches,
     * to the other side each combo.
     * @return the simulation when the Warden is destroyed, the player is dead or two minutes have passed
     */
    private static GameSimulation fight(PlayerLoadout loadout) {
        GameSimulation sim = new GameSimulation(room(), loadout, false);
        Enemy boss = warden(sim);
        PlayerInput input = new PlayerInput();
        float side = 1f;
        boolean wasCombo = false;
        for (int i = 0; i < 120 * 60 && !boss.isDestroyed() && !sim.player.isDead(); i++) {
            aimAt(sim, boss, input);
            boolean combo = boss.getState() == Enemy.State.WINDUP;
            if (combo && !wasCombo) side = -side;
            wasCombo = combo;
            input.moveForward = 0f;
            input.moveStrafe = combo && boss.getComboTime() < 1.4f ? side : 0f;
            // A trigger pulled and let go, every other frame.
            input.fire = i % 2 == 0;
            sim.update(DT, input);
        }
        return sim;
    }

    private static void assertWardenBeaten(GameSimulation sim, String weapon) {
        Enemy boss = warden(sim);
        assertTrue(boss.isDestroyed(), "the " + weapon + " left the Warden with " + boss.getHealth() + " health after "
            + sim.getTime() + " s; the player has " + sim.player.getHealth());
        assertFalse(sim.player.isDead(), "the player died beating the Warden with the " + weapon);
        assertTrue(sim.getTime() < 90f, "the " + weapon + " took " + sim.getTime() + " s");
    }

    @Test
    void thePistolAloneBeatsTheWarden() {
        assertWardenBeaten(fight(new PlayerLoadout(100f, 50f, Difficulty.NORMAL)), "pistol");
    }

    @Test
    void theScattergunAloneBeatsTheWarden() {
        GameSimulation sim = fight(new PlayerLoadout(100f, 50f, Difficulty.NORMAL, true, 24, WeaponSlot.SCATTERGUN));
        assertEquals(WeaponSlot.SCATTERGUN, sim.getActiveWeapon());
        assertWardenBeaten(sim, "scattergun");
    }

    @Test
    void theRailLanceAloneBeatsTheWarden() {
        GameSimulation sim = fight(
            new PlayerLoadout(100f, 50f, Difficulty.NORMAL, false, 0, true, 15, WeaponSlot.RAIL_LANCE));
        assertEquals(WeaponSlot.RAIL_LANCE, sim.getActiveWeapon());
        assertWardenBeaten(sim, "rail lance");
    }

    @Test
    void theWardenIsDangerousToAPlayerWhoStandsAndShoots() {
        GameSimulation sim = new GameSimulation(room(), new PlayerLoadout(100f, 0f, Difficulty.NORMAL), false);
        Enemy boss = warden(sim);
        PlayerInput input = new PlayerInput();
        for (int i = 0; i < 60 * 60 && !boss.isDestroyed() && !sim.player.isDead(); i++) {
            aimAt(sim, boss, input);
            input.fire = i % 2 == 0;
            sim.update(DT, input);
        }
        assertTrue(sim.player.isDead(), "standing still in front of the Warden with a pistol must not win; the player"
            + " has " + sim.player.getHealth() + " health and the Warden " + boss.getHealth());
    }
}

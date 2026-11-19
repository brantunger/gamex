package io.github.brantunger.gamex;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import io.github.brantunger.gamex.audio.SoundEvent;
import io.github.brantunger.gamex.player.Difficulty;
import io.github.brantunger.gamex.player.DifficultyTuning;
import io.github.brantunger.gamex.player.PlayerInput;
import io.github.brantunger.gamex.player.PlayerLoadout;
import io.github.brantunger.gamex.weapon.RailLance;
import io.github.brantunger.gamex.weapon.WeaponSlot;
import io.github.brantunger.gamex.world.Door;
import io.github.brantunger.gamex.world.Effect;
import io.github.brantunger.gamex.world.Enemy;
import io.github.brantunger.gamex.world.EnemyTuning;
import io.github.brantunger.gamex.world.GameMap;
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.List;
import java.util.stream.Stream;

/**
 * Checks that the simulation takes its per-kind rules and its difficulty multipliers from the tuning it is given:
 * each test plays the same scene with the shipped tuning and with one value changed.
 */
class TunedSimulationTest {

    private static final float FRAME = 1f / 60f;

    private static GameSimulation sim(EnemyTuning enemies, String... rows) {
        return new GameSimulation(GameMap.parse(rows), PlayerLoadout.fresh(), false, enemies, TestTuning.DIFFICULTY);
    }

    private static SoundEvent sound(List<SoundEvent> events, SoundEvent.Type type) {
        for (SoundEvent event : events) {
            if (event.type == type) return event;
        }
        return null;
    }

    private static Effect explosion(GameSimulation sim) {
        for (Effect effect : sim.effects) {
            if (effect.type == Effect.Type.EXPLOSION) return effect;
        }
        return null;
    }

    /** Fires one rail slug along a corridor at the enemy standing in it and returns what health it lost. */
    private static float railDamage(EnemyTuning enemies, char enemy) {
        GameSimulation sim = new GameSimulation(
            GameMap.parse(
                "###########",
                "#.>..." + enemy + "...#",
                "###########"),
            new PlayerLoadout(100f, 0f, Difficulty.NORMAL, false, 0, true, 2, WeaponSlot.RAIL_LANCE),
            false, enemies, TestTuning.DIFFICULTY);
        sim.update(FRAME, new PlayerInput());
        PlayerInput fire = new PlayerInput();
        fire.fire = true;
        sim.update(FRAME, fire);
        Enemy hit = sim.enemies.get(0);
        return hit.getMaxHealth() - hit.getHealth();
    }

    @Test
    void railDamageToAKindIsTheRailDamageTimesItsScale() {
        assertEquals(90f, railDamage(TestTuning.ENEMIES, 'B'), 1e-3f);
        EnemyTuning halfHide = TestTuning.enemiesWith("warden.rail_damage_scale", "0.5");
        assertEquals(RailLance.DAMAGE * 0.5f, railDamage(halfHide, 'B'), 1e-3f);
        assertEquals(RailLance.DAMAGE, railDamage(TestTuning.enemiesWith("warden.rail_damage_scale", "1"), 'B'), 1e-3f);
        // A guard has 100 health, so only a scaled slug leaves it standing.
        assertEquals(100f, railDamage(TestTuning.ENEMIES, 'G'), 1e-3f);
        EnemyTuning armouredGuard = TestTuning.enemiesWith("guard.rail_damage_scale", "0.25");
        assertEquals(RailLance.DAMAGE * 0.25f, railDamage(armouredGuard, 'G'), 1e-3f);
    }

    /** Sends the enemy through a one-cell choke with a shock grate in it and returns what health it lost. */
    private static float lostCrossingAGrate(EnemyTuning enemies, char enemy) {
        GameSimulation sim = sim(enemies,
            "######",
            "#>...#",
            "####~#",
            "#" + enemy + "...#",
            "######");
        sim.enemies.get(0).alert();
        PlayerInput idle = new PlayerInput();
        for (int i = 0; i < 180; i++) sim.update(FRAME, idle);
        Enemy crossed = sim.enemies.get(0);
        return crossed.getMaxHealth() - crossed.getHealth();
    }

    @Test
    void shockGratesHurtTheKindsWhoseHazardDamageIsOn() {
        assertTrue(lostCrossingAGrate(TestTuning.ENEMIES, 'G') > 0f, "the shipped guard is hurt");
        assertEquals(0f, lostCrossingAGrate(TestTuning.enemiesWith("guard.hazard_damage", "false"), 'G'), 0f);
        assertEquals(0f, lostCrossingAGrate(TestTuning.ENEMIES, 'D'), 0f, "the shipped drone hovers over it");
        assertTrue(lostCrossingAGrate(TestTuning.enemiesWith("drone.hazard_damage", "true"), 'D') > 0f);
    }

    /** A guard closes in along a corridor with a grate across it; returns the guard after four seconds. */
    private static Enemy guardBehindAGrate(EnemyTuning enemies) {
        GameSimulation sim = sim(enemies,
            "#################",
            "#G..~..........>#",
            "#################");
        sim.enemies.get(0).alert();
        PlayerInput idle = new PlayerInput();
        for (int i = 0; i < 240; i++) sim.update(FRAME, idle);
        return sim.enemies.get(0);
    }

    @Test
    void onlyAKindThatAvoidsHazardsStopsShortOfAGrate() {
        Enemy careful = guardBehindAGrate(TestTuning.ENEMIES);
        assertTrue(careful.getX() < 4f, "the shipped guard stays this side of the grate, at " + careful.getX());
        assertEquals(careful.getMaxHealth(), careful.getHealth(), 0f);

        Enemy careless = guardBehindAGrate(TestTuning.enemiesWith("guard.avoids_hazards", "false"));
        assertTrue(careless.getX() > 5f, "it walks over the grate, to " + careless.getX());
        assertTrue(careless.getHealth() < careless.getMaxHealth(), "and is zapped on the way");
    }

    private static final String[] BOSS_ROOM = {
        "#########",
        "#.>...B.#",
        "####M####",
        "#...X...#",
        "#########"};

    @Test
    void theBossFlagGivesAKindTheHealthBarTheDeathSoundAndTheSealedDoors() {
        GameSimulation shipped = sim(TestTuning.ENEMIES, BOSS_ROOM);
        shipped.enemies.get(0).alert();
        assertSame(shipped.enemies.get(0), shipped.getActiveBoss());
        shipped.enemies.get(0).damage(Float.MAX_VALUE);
        shipped.update(FRAME, new PlayerInput());
        List<SoundEvent> events = shipped.drainSoundEvents();
        assertNotNull(sound(events, SoundEvent.Type.BOSS_DESTROYED));
        assertNull(sound(events, SoundEvent.Type.ENEMY_DESTROYED));
        assertTrue(shipped.doors.get(0).isOpening(), "the boss door unseals");

        GameSimulation demoted = sim(TestTuning.enemiesWith("warden.boss", "false"), BOSS_ROOM);
        demoted.enemies.get(0).alert();
        assertNull(demoted.getActiveBoss());
        demoted.enemies.get(0).damage(Float.MAX_VALUE);
        demoted.update(FRAME, new PlayerInput());
        events = demoted.drainSoundEvents();
        assertNull(sound(events, SoundEvent.Type.BOSS_DESTROYED));
        assertNotNull(sound(events, SoundEvent.Type.ENEMY_DESTROYED));
        assertEquals(Door.LockKind.BOSS, demoted.doors.get(0).getLockKind());
        assertFalse(demoted.doors.get(0).isOpening(), "no boss died, so the door stays sealed");

        GameSimulation promoted = sim(TestTuning.enemiesWith("turret.boss", "true"),
            "#########",
            "#.>...T.#",
            "#########");
        assertNull(promoted.getActiveBoss(), "a dormant boss has no health bar yet");
        promoted.enemies.get(0).alert();
        assertSame(promoted.enemies.get(0), promoted.getActiveBoss());
    }

    @Test
    void aKindsDeathBurstSoundAndNoiseComeFromItsBlock() {
        // The turret in the far room hears a death 7 cells away through the wall only with a longer muffled range.
        String[] rooms = {
            "###########",
            "#>.D#....T#",
            "###########"};
        GameSimulation shipped = sim(TestTuning.ENEMIES, rooms);
        shipped.enemies.get(0).damage(Float.MAX_VALUE);
        shipped.update(FRAME, new PlayerInput());
        assertEquals(Enemy.Kind.DRONE.height * 0.8f, explosion(shipped).size, 0f);
        assertEquals(1.0f, sound(shipped.drainSoundEvents(), SoundEvent.Type.ENEMY_DESTROYED).intensity, 0f);
        assertEquals(Enemy.State.DORMANT, shipped.enemies.get(1).getState());

        EnemyTuning loud = TestTuning.enemiesWith("drone.death_noise_muffled_range", "8");
        GameSimulation heard = sim(loud, rooms);
        heard.enemies.get(0).damage(Float.MAX_VALUE);
        heard.update(FRAME, new PlayerInput());
        assertEquals(Enemy.State.PURSUING, heard.enemies.get(1).getState());

        GameSimulation big = sim(TestTuning.enemiesWith("drone.death_burst_size", "2.5"), rooms);
        big.enemies.get(0).damage(Float.MAX_VALUE);
        big.update(FRAME, new PlayerInput());
        assertEquals(2.5f, explosion(big).size, 0f);

        GameSimulation quiet = sim(TestTuning.enemiesWith("drone.death_sound_gain", "0.4"), rooms);
        quiet.enemies.get(0).damage(Float.MAX_VALUE);
        quiet.update(FRAME, new PlayerInput());
        assertEquals(0.4f, sound(quiet.drainSoundEvents(), SoundEvent.Type.ENEMY_DESTROYED).intensity, 0f);

        // With a line of sight the longer range counts. The turret is 4 cells from the drone and too far from the
        // player to see them.
        String[] hall = {
            "#########################",
            "#>................D...T.#",
            "#########################"};
        GameSimulation inEarshot = sim(TestTuning.ENEMIES, hall);
        inEarshot.enemies.get(0).damage(Float.MAX_VALUE);
        inEarshot.update(FRAME, new PlayerInput());
        assertEquals(Enemy.State.PURSUING, inEarshot.enemies.get(1).getState());

        GameSimulation outOfEarshot = sim(TestTuning.enemiesWith("drone.death_noise_range", "3.5"), hall);
        outOfEarshot.enemies.get(0).damage(Float.MAX_VALUE);
        outOfEarshot.update(FRAME, new PlayerInput());
        assertEquals(Enemy.State.DORMANT, outOfEarshot.enemies.get(1).getState());
    }

    @Test
    void theOverdriveBurstAndSoundsComeFromTheWardenFile() {
        GameSimulation shipped = sim(TestTuning.ENEMIES, BOSS_ROOM);
        shipped.enemies.get(0).damage(470f);
        shipped.update(FRAME, new PlayerInput());
        assertEquals(1.25f, sound(shipped.drainSoundEvents(), SoundEvent.Type.BOSS_OVERDRIVE).intensity, 0f);
        assertEquals(1.6f, explosion(shipped).size, 0f);

        GameSimulation tuned = sim(TestTuning.wardenWith("overdrive_sound_gain", "0.5"), BOSS_ROOM);
        tuned.enemies.get(0).damage(470f);
        tuned.update(FRAME, new PlayerInput());
        assertEquals(0.5f, sound(tuned.drainSoundEvents(), SoundEvent.Type.BOSS_OVERDRIVE).intensity, 0f);

        GameSimulation burst = sim(TestTuning.wardenWith("overdrive_burst_size", "3"), BOSS_ROOM);
        burst.enemies.get(0).damage(470f);
        burst.update(FRAME, new PlayerInput());
        assertEquals(3f, explosion(burst).size, 0f);
    }

    /** Stands the player in the Warden's reach until its first punch is heard and returns that sound. */
    private static SoundEvent firstPunch(EnemyTuning enemies) {
        GameSimulation sim = sim(enemies,
            "##########",
            "#...>B...#",
            "##########");
        PlayerInput idle = new PlayerInput();
        for (int i = 0; i < 120; i++) {
            sim.update(FRAME, idle);
            SoundEvent slam = sound(sim.drainSoundEvents(), SoundEvent.Type.BOSS_SLAM);
            if (slam != null) return slam;
        }
        throw new AssertionError("the Warden threw no punch");
    }

    @Test
    void thePunchSoundGainComesFromTheWardenFile() {
        assertEquals(1.15f, firstPunch(TestTuning.ENEMIES).intensity, 0f);
        assertEquals(0.7f, firstPunch(TestTuning.wardenWith("punch_sound_gain", "0.7")).intensity, 0f);
    }

    /** Walks the player over a medkit at 60 health and across a shock grate; returns the health after each. */
    private static float[] medkitThenGrate(DifficultyTuning difficulty, Difficulty played) {
        GameSimulation pickup = new GameSimulation(
            GameMap.parse(
                "#####",
                "#>+.#",
                "#####"),
            new PlayerLoadout(60f, 0f, played), false, TestTuning.ENEMIES, difficulty);
        PlayerInput forward = new PlayerInput();
        forward.moveForward = 1f;
        for (int i = 0; i < 40; i++) pickup.update(FRAME, forward);

        GameSimulation grate = new GameSimulation(
            GameMap.parse(
                "#####",
                "#>~.#",
                "#####"),
            new PlayerLoadout(100f, 0f, played), false, TestTuning.ENEMIES, difficulty);
        while (grate.getDamageTaken() <= 0f && grate.getTime() < 2f) grate.update(FRAME, forward);
        return new float[] {pickup.player.getHealth(), grate.getDamageTaken()};
    }

    @Test
    void pickupsAndDamageAreScaledByTheDifficultyTuningGiven() {
        float[] normal = medkitThenGrate(TestTuning.DIFFICULTY, Difficulty.NORMAL);
        assertEquals(85f, normal[0], 1e-3f);
        assertEquals(GameSimulation.HAZARD_DAMAGE, normal[1], 1e-3f);

        float[] easy = medkitThenGrate(TestTuning.DIFFICULTY, Difficulty.EASY);
        assertEquals(60f + Math.round(25f * 1.25f), easy[0], 1e-3f, "a pickup gives a whole amount");
        assertEquals(GameSimulation.HAZARD_DAMAGE * 0.7f, easy[1], 1e-3f);

        DifficultyTuning tuned = TestTuning.difficultyWith("normal.pickup", "0.4");
        assertEquals(70f, medkitThenGrate(tuned, Difficulty.NORMAL)[0], 1e-3f);
        tuned = TestTuning.difficultyWith("normal.damage_taken", "2.5");
        assertEquals(GameSimulation.HAZARD_DAMAGE * 2.5f, medkitThenGrate(tuned, Difficulty.NORMAL)[1], 1e-3f);
        // A value of another difficulty changes nothing on this one.
        tuned = TestTuning.difficultyWith("hard.damage_taken", "9");
        assertEquals(GameSimulation.HAZARD_DAMAGE, medkitThenGrate(tuned, Difficulty.NORMAL)[1], 1e-3f);
    }

    @Test
    void theSimulationNeedsItsTuning() {
        GameMap map = GameMap.parse("###", "#>#", "###");
        assertThrows(IllegalArgumentException.class,
            () -> new GameSimulation(map, PlayerLoadout.fresh(), false, null, TestTuning.DIFFICULTY));
        assertThrows(IllegalArgumentException.class,
            () -> new GameSimulation(map, PlayerLoadout.fresh(), false, TestTuning.ENEMIES, null));
    }

    /** @return the number of arguments of each {@code new Name(...)} in the text, with where it is */
    private static List<String> shortCalls(Path file, String text, String name, int fullArguments) {
        List<String> found = new ArrayList<>();
        String call = "new " + name + "(";
        for (int at = text.indexOf(call); at >= 0; at = text.indexOf(call, at + 1)) {
            int depth = 0;
            int arguments = 1;
            for (int i = at + call.length() - 1; i < text.length(); i++) {
                char c = text.charAt(i);
                if (c == '(') depth++;
                if (c == ')' && --depth == 0) break;
                if (c == ',' && depth == 1) arguments++;
            }
            if (arguments < fullArguments) {
                int line = 1 + (int) text.substring(0, at).chars().filter(c -> c == '\n').count();
                found.add(file + ":" + line + " calls " + call + "...) with " + arguments + " arguments");
            }
        }
        return found;
    }

    /**
     * The constructors of {@link GameSimulation} and {@code MenuState} that read the tuning files themselves are for
     * tests. The game reads them once in {@code MainGame.create()}, so no main source may call the short forms.
     */
    @Test
    void noMainSourceBuildsASimulationOrTheMenusWithoutTheLoadedTuning() throws IOException {
        List<String> offenders = new ArrayList<>();
        int scanned = 0;
        for (String root : new String[] {"src/main/java", "../desktop/src/main/java", "../tools/src/main/java"}) {
            Path dir = Paths.get(root);
            assertTrue(Files.isDirectory(dir), "no such source folder: " + dir.toAbsolutePath());
            List<Path> sources;
            try (Stream<Path> walk = Files.walk(dir)) {
                sources = walk.filter(p -> p.toString().endsWith(".java")).toList();
            }
            for (Path source : sources) {
                String text = new String(Files.readAllBytes(source), StandardCharsets.UTF_8);
                scanned++;
                offenders.addAll(shortCalls(source, text, "GameSimulation", 5));
                offenders.addAll(shortCalls(source, text, "MenuState", 4));
            }
        }
        assertTrue(scanned > 50, "only " + scanned + " sources were found");
        assertEquals(List.of(), offenders);
        // The check itself: a short call is seen, a full one is not.
        Path sample = Paths.get("Sample.java");
        assertEquals(1, shortCalls(sample, "a = new GameSimulation(map, f(a, b), false);", "GameSimulation", 5).size());
        assertEquals(0, shortCalls(sample, "a = new GameSimulation(m, l, false, e,\n d);", "GameSimulation", 5).size());
    }
}

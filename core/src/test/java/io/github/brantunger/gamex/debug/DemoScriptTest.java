package io.github.brantunger.gamex.debug;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import io.github.brantunger.gamex.GameSimulation;
import io.github.brantunger.gamex.TestTuning;
import io.github.brantunger.gamex.hud.MenuState;
import io.github.brantunger.gamex.player.Difficulty;
import io.github.brantunger.gamex.player.PlayerInput;
import io.github.brantunger.gamex.player.PlayerLoadout;
import io.github.brantunger.gamex.weapon.WeaponSlot;
import io.github.brantunger.gamex.world.Enemy;
import io.github.brantunger.gamex.world.Levels;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.EnumSource;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.Set;
import java.util.TreeSet;
import java.util.stream.Stream;

/**
 * Checks the demo scripts: that every script in {@code assets/demos/} loads and plays, that a script's header and
 * timeline are the scene and the input played, and that a file the game cannot read stops the load.
 */
class DemoScriptTest {

    private static final int FRAMES = 300;

    private static final String HEADER = "screen: playing\nstage: first_level\nloadout: fresh\n";

    private static void assertSameInput(PlayerInput expected, PlayerInput actual, String at) {
        assertEquals(expected.moveForward, actual.moveForward, 0f, "moveForward " + at);
        assertEquals(expected.moveStrafe, actual.moveStrafe, 0f, "moveStrafe " + at);
        assertEquals(expected.yawDelta, actual.yawDelta, 0f, "yawDelta " + at);
        assertEquals(expected.pitchDelta, actual.pitchDelta, 0f, "pitchDelta " + at);
        assertEquals(expected.fire, actual.fire, "fire " + at);
        assertEquals(expected.charge, actual.charge, "charge " + at);
        assertEquals(expected.jump, actual.jump, "jump " + at);
        assertEquals(expected.dash, actual.dash, "dash " + at);
        assertEquals(expected.interact, actual.interact, "interact " + at);
        assertEquals(expected.weaponSlot, actual.weaponSlot, "weaponSlot " + at);
    }

    private static IllegalArgumentException rejected(String text) {
        return assertThrows(IllegalArgumentException.class, () -> DemoScript.parse("demos/test.txt", text), text);
    }

    private static void assertRejected(String text, String... parts) {
        String message = rejected(text).getMessage();
        assertTrue(message.contains("demos/test.txt"), message);
        for (String part : parts) assertTrue(message.contains(part), part + " in: " + message);
    }

    private static PlayerInput frame(DemoScript script, int frame) {
        PlayerInput input = new PlayerInput();
        script.apply(frame, input);
        return input;
    }

    /** @return the names of the scripts in {@code assets/demos/}, without {@code .txt} */
    private static Set<String> shippedScripts() throws IOException {
        Path demos = null;
        String assets = System.getProperty("gamex.assets");
        for (Path candidate : new Path[] {assets == null ? null : Paths.get(assets), Paths.get("..", "assets"),
            Paths.get("assets")}) {
            if (candidate != null && Files.isDirectory(candidate.resolve(DemoScript.DEMOS_DIR))) {
                demos = candidate.resolve(DemoScript.DEMOS_DIR);
                break;
            }
        }
        assertTrue(demos != null, "the demos folder was not found");
        Set<String> names = new TreeSet<>();
        try (Stream<Path> files = Files.list(demos)) {
            files.forEach(file -> {
                String name = file.getFileName().toString();
                assertTrue(name.endsWith(".txt"), name + " is a script");
                names.add(name.substring(0, name.length() - ".txt".length()));
            });
        }
        return names;
    }

    /**
     * Whatever the scripts in {@code assets/demos/} hold, each loads, names a stage the game has, and plays: its
     * scene builds a level and its timeline gives every frame an input the simulation takes, on the tests' own
     * balance and on the shipped one.
     */
    @ParameterizedTest(name = "{displayName} with {0}")
    @EnumSource(TestTuning.Balance.class)
    void everyShippedScriptLoadsAndPlays(TestTuning.Balance balance) throws IOException {
        Set<String> scripts = shippedScripts();
        assertFalse(scripts.isEmpty());
        PlayerInput input = new PlayerInput();
        for (String script : scripts) {
            DemoRun demo = DemoRun.forScript(script);
            assertEquals(script, demo.getScript());
            GameSimulation simulation = demo.getScene().createSimulation(balance.enemies(), balance.difficulty(),
                balance.weapons(), balance.world());
            for (int frame = 1; frame <= FRAMES; frame++) {
                // Left dirty with values no script can ask for, to see that the run clears the frame before.
                input.weaponSlot = Integer.MIN_VALUE;
                input.pitchDelta = Float.NaN;
                demo.nextFrame(input);
                assertTrue(Float.isFinite(input.moveForward) && Float.isFinite(input.moveStrafe)
                    && Float.isFinite(input.yawDelta) && Float.isFinite(input.pitchDelta),
                    "'" + script + "' on frame " + frame);
                assertTrue(input.weaponSlot != Integer.MIN_VALUE, "'" + script + "' clears the input");
                simulation.update(DemoRun.FRAME_TIME, input);
            }
            assertTrue(Float.isFinite(simulation.player.position.x) && Float.isFinite(simulation.player.position.y),
                script);
        }
    }

    /** A script with the {@code fresh} loadout starts as a new game does, whatever stage it names. */
    @Test
    void aFreshLoadoutIsTheOneANewGameStartsWith() {
        PlayerLoadout fresh = PlayerLoadout.fresh();
        PlayerLoadout loadout = DemoScript.parse("demos/test.txt", HEADER).scene.loadout;
        assertEquals(fresh.health, loadout.health, 0f);
        assertEquals(fresh.armor, loadout.armor, 0f);
        assertEquals(fresh.difficulty, loadout.difficulty);
        assertEquals(fresh.hasScattergun, loadout.hasScattergun);
        assertEquals(fresh.shells, loadout.shells);
        assertEquals(fresh.hasRailLance, loadout.hasRailLance);
        assertEquals(fresh.slugs, loadout.slugs);
        assertEquals(fresh.activeWeapon, loadout.activeWeapon);
    }

    @Test
    void aRunWithoutAScriptStandsStillInTheFirstLevelAndReadsNoFile() {
        for (String unset : new String[] {null, "", "  "}) {
            DemoRun demo = DemoRun.forScript(unset);
            assertSame(DemoScene.STANDING, demo.getScene());
            assertEquals(MenuState.Screen.PLAYING, demo.getScene().startScreen);
            assertEquals(DemoScene.FIRST_LEVEL, demo.getScene().stage);
            assertFalse(demo.getScene().unlockAllStages);
        }
    }

    @Test
    void aScriptNameWithNoFileStopsTheRunNamingTheFile() {
        IllegalArgumentException error =
            assertThrows(IllegalArgumentException.class, () -> DemoRun.forScript("no-such-demo"));
        assertTrue(error.getMessage().contains("'no-such-demo'"), error.getMessage());
        assertTrue(error.getMessage().contains("demos/no-such-demo.txt"), error.getMessage());
        assertThrows(IllegalArgumentException.class,
            () -> DemoRun.fromEnvironment("shot.png", null, "no-such-demo", null));

        // A name cannot reach a file outside assets/demos/.
        for (String name : new String[] {"../levels/first", "..\\data\\window", "a/b", "jump.txt", "a b", "."}) {
            IllegalArgumentException invalid =
                assertThrows(IllegalArgumentException.class, () -> DemoRun.forScript(name), name);
            assertTrue(invalid.getMessage().startsWith("Invalid demo script name"), invalid.getMessage());
        }
        assertEquals("demos/jump.txt", DemoScript.path("jump"));
    }

    @Test
    void aChangedTimelineChangesTheInputPlayed() {
        String text = HEADER + "\n1-: move_forward\n19-23: jump\n";
        DemoScript before = DemoScript.parse("demos/test.txt", text);
        DemoScript after = DemoScript.parse("demos/test.txt",
            text.replace("19-23: jump", "30-31: jump\n40-: move_right 0.25"));

        assertTrue(frame(before, 19).jump);
        assertTrue(frame(before, 23).jump);
        assertFalse(frame(before, 30).jump);
        assertEquals(0f, frame(before, 40).moveStrafe, 0f);

        assertFalse(frame(after, 19).jump);
        assertFalse(frame(after, 29).jump);
        assertTrue(frame(after, 30).jump);
        assertTrue(frame(after, 31).jump);
        assertFalse(frame(after, 32).jump);
        assertEquals(0f, frame(after, 39).moveStrafe, 0f);
        assertEquals(0.25f, frame(after, 40).moveStrafe, 0f);
        assertEquals(0.25f, frame(after, 100000).moveStrafe, 0f);
        assertEquals(1f, frame(after, 100000).moveForward, 0f);
    }

    @Test
    void aChangedHeaderChangesTheScene() {
        DemoScript script = DemoScript.parse("demos/test.txt", String.join("\n",
            "// A comment before the header block.",
            "screen: title",
            "stage: training_range",
            "loadout: custom",
            "loadout.health: 40",
            "loadout.armor: 25",
            "loadout.slugs: 3",
            "loadout.weapon: raillance",
            "position: 3.5, 2.5",
            "look: 90, 10",
            "unlock_all_stages: true",
            "keep_enemies: turret, drone"));
        DemoScene scene = script.scene;
        assertEquals(MenuState.Screen.TITLE, scene.startScreen);
        assertEquals("training_range", scene.stage);
        assertTrue(scene.unlockAllStages);
        assertEquals(40f, scene.loadout.health, 0f);
        assertEquals(25f, scene.loadout.armor, 0f);
        assertEquals(Difficulty.NORMAL, scene.loadout.difficulty);
        assertFalse(scene.loadout.hasScattergun);
        assertEquals(0, scene.loadout.shells);
        assertTrue(scene.loadout.hasRailLance);
        assertEquals(3, scene.loadout.slugs);
        assertEquals(WeaponSlot.RAIL_LANCE, scene.loadout.activeWeapon);

        Levels.Stage range = Levels.trainingRange();
        GameSimulation plain = new GameSimulation(range.map, scene.loadout, range.completeOnAllTargets,
            TestTuning.ENEMIES, TestTuning.DIFFICULTY, TestTuning.WEAPONS, TestTuning.WORLD);
        GameSimulation simulation = scene.createSimulation(TestTuning.ENEMIES, TestTuning.DIFFICULTY,
            TestTuning.WEAPONS, TestTuning.WORLD);
        assertEquals(3.5f, simulation.player.position.x, 0f);
        assertEquals(2.5f, simulation.player.position.y, 0f);
        assertEquals(plain.player.getYaw() + 90f, simulation.player.getYaw(), 1e-4f);
        assertEquals(plain.player.getPitch() + 10f, simulation.player.getPitch(), 1e-4f);
        assertEquals(40f, simulation.player.getHealth(), 0f);
        assertEquals(WeaponSlot.RAIL_LANCE, simulation.getActiveWeapon());
        assertEquals(3, simulation.getSlugs());
        assertSameInput(new PlayerInput(), frame(script, 1), "of a script without a timeline");
    }

    @Test
    void keepEnemiesDestroysEveryEnemyOfAnotherKind() {
        String wardenCore = "screen: playing\nstage: 06_warden_core\nloadout: fresh\n";
        GameSimulation all = DemoScript.parse("demos/test.txt", wardenCore).scene.createSimulation(
            TestTuning.ENEMIES, TestTuning.DIFFICULTY, TestTuning.WEAPONS, TestTuning.WORLD);
        GameSimulation kept = DemoScript.parse("demos/test.txt", wardenCore + "keep_enemies: warden\n").scene
            .createSimulation(TestTuning.ENEMIES, TestTuning.DIFFICULTY, TestTuning.WEAPONS, TestTuning.WORLD);
        assertEquals(all.enemies.size(), kept.enemies.size());
        int wardens = 0;
        int others = 0;
        for (int i = 0; i < all.enemies.size(); i++) {
            assertFalse(all.enemies.get(i).isDestroyed(), "enemy " + i + " of the script that keeps all");
            Enemy enemy = kept.enemies.get(i);
            if (enemy.getKind() == Enemy.Kind.WARDEN) {
                wardens++;
                assertFalse(enemy.isDestroyed(), "the Warden stays");
            } else {
                others++;
                assertTrue(enemy.isDestroyed(), "enemy " + i + " (" + enemy.getKind() + ") is destroyed");
            }
        }
        assertEquals(1, wardens);
        assertTrue(others > 0, "the stage holds other enemies to take out");
    }

    @Test
    void everyInputSetsItsFieldOfThePlayerInput() {
        DemoScript script = DemoScript.parse("demos/test.txt", HEADER + String.join("\n",
            "",
            "1: move_forward",
            "2: move_back",
            "3: move_left",
            "4: move_right",
            "5: move_forward 0.25",
            "5: move_left 0.5",
            "6: turn 12.5",
            "6: look_up -3",
            "7: fire",
            "8: alt_fire",
            "9: jump",
            "10: dash",
            "11: interact",
            "12: weapon_1",
            "13: weapon_2",
            "14: weapon_3",
            "15: quick_swap",
            "16: weapon_next",
            "17: weapon_prev",
            "// A comment in the timeline, and a blank line.",
            "",
            "  18-19 :   fire  "));
        assertEquals(1f, frame(script, 1).moveForward, 0f);
        assertEquals(-1f, frame(script, 2).moveForward, 0f);
        assertEquals(-1f, frame(script, 3).moveStrafe, 0f);
        assertEquals(1f, frame(script, 4).moveStrafe, 0f);
        assertEquals(0.25f, frame(script, 5).moveForward, 0f);
        assertEquals(-0.5f, frame(script, 5).moveStrafe, 0f);
        assertEquals(12.5f, frame(script, 6).yawDelta, 0f);
        assertEquals(-3f, frame(script, 6).pitchDelta, 0f);
        assertTrue(frame(script, 7).fire);
        assertTrue(frame(script, 8).charge);
        assertTrue(frame(script, 9).jump);
        assertTrue(frame(script, 10).dash);
        assertTrue(frame(script, 11).interact);
        assertEquals(1, frame(script, 12).weaponSlot);
        assertEquals(2, frame(script, 13).weaponSlot);
        assertEquals(3, frame(script, 14).weaponSlot);
        assertEquals(PlayerInput.WEAPON_QUICK_SWAP, frame(script, 15).weaponSlot);
        assertEquals(PlayerInput.WEAPON_CYCLE_NEXT, frame(script, 16).weaponSlot);
        assertEquals(PlayerInput.WEAPON_CYCLE_PREV, frame(script, 17).weaponSlot);
        assertTrue(frame(script, 18).fire);
        assertTrue(frame(script, 19).fire);

        PlayerInput idle = frame(script, 20);
        PlayerInput cleared = new PlayerInput();
        assertSameInput(cleared, idle, "on a frame no line names");
    }

    @Test
    void aBrokenTimelineStopsTheLoadNamingTheFileAndTheLine() {
        assertRejected(HEADER + "\nfire\n", "at line 5", "frames: input");
        assertRejected(HEADER + "\n: fire\n", "at line 5", "frames: input");
        assertRejected(HEADER + "\n5:\n", "at line 5", "frames: input");
        assertRejected(HEADER + "\n5: fire now please\n", "at line 5", "frames: input");
        assertRejected(HEADER + "\n// ok\n\n1: fire\nfive: fire\n", "at line 8", "Invalid frames 'five'");
        assertRejected(HEADER + "\n0: fire\n", "at line 5", "Invalid frames '0'");
        assertRejected(HEADER + "\n9-5: fire\n", "at line 5", "Invalid frames '9-5'");
        assertRejected(HEADER + "\n-5: fire\n", "at line 5", "Invalid frames '-5'");
        assertRejected(HEADER + "\n5-6-7: fire\n", "at line 5", "Invalid frames '5-6-7'");
        assertRejected(HEADER + "\n99999999999: fire\n", "at line 5", "Invalid frames");
        assertRejected(HEADER + "\n5: shoot\n", "at line 5", "Unknown input 'shoot'", "alt_fire");
        assertRejected(HEADER + "\n5: Fire\n", "at line 5", "Unknown input 'Fire'");
        assertRejected(HEADER + "\n5: fire 1\n", "at line 5", "'fire' takes no amount");
        assertRejected(HEADER + "\n5: turn\n", "at line 5", "'turn' needs an amount");
        assertRejected(HEADER + "\n5: turn left\n", "at line 5", "Invalid amount of 'turn'", "'left'");
        assertRejected(HEADER + "\n5: turn 12f\n", "at line 5", "Invalid amount of 'turn'");
        assertRejected(HEADER + "\n5: turn NaN\n", "at line 5", "Invalid amount of 'turn'");
        assertRejected(HEADER + "\n5: move_forward 0\n", "at line 5", "Invalid amount of 'move_forward'");
        assertRejected(HEADER + "\n5: move_forward 1.5\n", "at line 5", "Invalid amount of 'move_forward'");
        assertRejected(HEADER + "\n5: move_forward -1\n", "at line 5", "Invalid amount of 'move_forward'");
        // Two lines may not set the same thing on the same frame.
        assertRejected(HEADER + "\n1-10: fire\n10-: fire\n", "at line 6", "overlaps 'fire' of line 5", "frame 10");
        assertRejected(HEADER + "\n5-: move_forward\n9: move_back\n", "at line 6", "overlaps 'move_forward'");
        assertRejected(HEADER + "\n5: weapon_1\n5: weapon_next\n", "at line 6", "overlaps 'weapon_1'");
        // Lines that meet without sharing a frame, and different things on one frame, are fine.
        DemoScript.parse("demos/test.txt", HEADER + "\n1-10: fire\n11-: fire\n5: alt_fire\n5: move_forward\n");
    }

    @Test
    void aBrokenHeaderStopsTheLoadNamingTheFileAndTheKey() {
        assertRejected("stage: first_level\nloadout: fresh\n", "'screen'");
        assertRejected("screen: playing\nloadout: fresh\n", "'stage'");
        assertRejected("screen: playing\nstage: first_level\n", "'loadout'");
        assertRejected(HEADER + "speed: 2\n", "'speed'", "at line 4");
        assertRejected(HEADER + "screen: title\n", "'screen'", "at line 4");
        assertRejected(HEADER.replace("playing", "paused"), "'screen'", "playing, title, stage_select");
        assertRejected(HEADER.replace("first_level", "07_no_such_stage"), "'stage'", "training_range",
            "07_no_such_stage");
        assertRejected(HEADER.replace("fresh", "full"), "'loadout'", "fresh or custom");
        assertRejected(HEADER + "loadout.health: 50\n", "'loadout.health'", "at line 4");
        assertRejected(HEADER + "loadout.shells: 4\n", "'loadout.shells'");

        String custom = HEADER.replace("fresh", "custom");
        assertRejected(custom + "loadout.armor: 0\nloadout.weapon: pistol\n", "'loadout'", "'loadout.health'");
        assertRejected(custom + "loadout.health: 100\nloadout.weapon: pistol\n", "'loadout'", "'loadout.armor'");
        assertRejected(custom + "loadout.health: 100\nloadout.armor: 0\n", "'loadout'", "'loadout.weapon'");
        String vitals = custom + "loadout.health: 100\nloadout.armor: 0\n";
        assertRejected(vitals.replace("health: 100", "health: 0") + "loadout.weapon: pistol\n", "'loadout.health'");
        assertRejected(vitals.replace("health: 100", "health: 101") + "loadout.weapon: pistol\n",
            "'loadout.health'");
        assertRejected(vitals.replace("armor: 0", "armor: -1") + "loadout.weapon: pistol\n", "'loadout.armor'");
        assertRejected(vitals.replace("armor: 0", "armor: 500") + "loadout.weapon: pistol\n", "'loadout.armor'");
        assertRejected(vitals + "loadout.weapon: blaster\n", "'loadout.weapon'", "pistol, scattergun, raillance");
        assertRejected(vitals + "loadout.weapon: scattergun\n", "'loadout.weapon'", "no loaded scattergun");
        assertRejected(vitals + "loadout.shells: 0\nloadout.weapon: scattergun\n", "'loadout.weapon'");
        assertRejected(vitals + "loadout.weapon: raillance\n", "'loadout.weapon'", "no loaded raillance");
        assertRejected(vitals + "loadout.shells: -1\nloadout.weapon: pistol\n", "'loadout.shells'");
        assertRejected(vitals + "loadout.slugs: two\nloadout.weapon: pistol\n", "'loadout.slugs'");

        assertRejected(HEADER + "position: 4.5\n", "'position'");
        assertRejected(HEADER + "position: 4.5, 4.5, 1\n", "'position'");
        assertRejected(HEADER + "look: north, 0\n", "'look'");
        assertRejected(HEADER + "unlock_all_stages: yes\n", "'unlock_all_stages'");
        assertRejected(HEADER + "keep_enemies: boss\n", "'keep_enemies'", "'boss'", "drone, turret, guard, warden");
        assertRejected(HEADER + "keep_enemies: warden,\n", "'keep_enemies'");
        // The timeline starts after a blank line: a timeline line inside the header block is no header.
        assertRejected(HEADER + "1: fire\n", "'1'", "at line 4");
        assertThrows(IllegalArgumentException.class, () -> DemoScript.parse("demos/test.txt", null));
    }
}

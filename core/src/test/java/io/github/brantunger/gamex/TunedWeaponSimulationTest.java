package io.github.brantunger.gamex;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import io.github.brantunger.gamex.player.Difficulty;
import io.github.brantunger.gamex.player.PlayerInput;
import io.github.brantunger.gamex.player.PlayerLoadout;
import io.github.brantunger.gamex.settings.MapKeyValueStore;
import io.github.brantunger.gamex.settings.SaveData;
import io.github.brantunger.gamex.weapon.WeaponSlot;
import io.github.brantunger.gamex.weapon.WeaponTuning;
import io.github.brantunger.gamex.world.Effect;
import io.github.brantunger.gamex.world.Enemy;
import io.github.brantunger.gamex.world.GameMap;
import io.github.brantunger.gamex.world.Levels;
import io.github.brantunger.gamex.world.WorldTuning;
import org.junit.jupiter.api.Test;

/**
 * Checks that the simulation takes its weapon numbers and the level's own rules from the tuning it is given: each
 * test plays the same scene with the shipped tuning and with one value changed.
 */
class TunedWeaponSimulationTest {

    private static final float FRAME = 1f / 60f;

    private static GameSimulation sim(WeaponTuning weapons, WorldTuning world, PlayerLoadout loadout,
                                      String... rows) {
        return new GameSimulation(GameMap.parse(rows), loadout, false, TestTuning.ENEMIES, TestTuning.DIFFICULTY,
            weapons, world);
    }

    private static PlayerInput firing() {
        PlayerInput input = new PlayerInput();
        input.fire = true;
        return input;
    }

    private static PlayerLoadout with(WeaponSlot active, int shells, int slugs) {
        return new PlayerLoadout(100f, 0f, Difficulty.NORMAL, true, shells, true, slugs, active);
    }

    /** Fires one shot of the active weapon at a ring board down the corridor; returns the health it lost. */
    private static float lostToOneShot(WeaponTuning weapons, WeaponSlot active, boolean alt) {
        GameSimulation sim = sim(weapons, TestTuning.WORLD, with(active, 4, 2),
            "##########",
            "#>..O....#",
            "##########");
        sim.update(FRAME, new PlayerInput());
        PlayerInput input = new PlayerInput();
        if (alt) input.charge = true;
        else input.fire = true;
        sim.update(FRAME, input);
        return 120f - sim.targets.get(0).getHealth();
    }

    @Test
    void aShotDoesTheDamageOfTheWeaponFile() {
        assertEquals(20f, lostToOneShot(TestTuning.WEAPONS, WeaponSlot.PISTOL, false), 1e-4f);
        assertEquals(35f,
            lostToOneShot(TestTuning.weaponsWith("pistol.damage", "35"), WeaponSlot.PISTOL, false), 1e-4f);

        assertEquals(120f, lostToOneShot(TestTuning.WEAPONS, WeaponSlot.RAIL_LANCE, false), 1e-4f);
        assertEquals(45f,
            lostToOneShot(TestTuning.weaponsWith("rail.damage", "45"), WeaponSlot.RAIL_LANCE, false), 1e-4f);

        // Three cells off, inside the falloff start, every pellet that hits does the full pellet damage.
        float shipped = lostToOneShot(TestTuning.WEAPONS, WeaponSlot.SCATTERGUN, false);
        assertTrue(shipped >= 12f && shipped % 12f == 0f, "a whole number of 12-damage pellets: " + shipped);
        assertEquals(shipped / 12f * 5f,
            lostToOneShot(TestTuning.weaponsWith("scattergun.pellet_damage", "5"), WeaponSlot.SCATTERGUN, false),
            1e-4f);
        // A pattern of the centre pellet and one tight ring of two puts three pellets on the board.
        assertEquals(36f, lostToOneShot(TestTuning.weaponsWith("scattergun.primary.ring", "2, 0.5, 0"),
            WeaponSlot.SCATTERGUN, false), 1e-4f);
    }

    @Test
    void aShotOutOfRangeHitsNothing() {
        assertEquals(0f, lostToOneShot(TestTuning.weaponsWith("pistol.range", "2"), WeaponSlot.PISTOL, false), 0f);
        assertEquals(0f, lostToOneShot(TestTuning.weaponsWith("rail.range", "2"), WeaponSlot.RAIL_LANCE, false), 0f);
    }

    /** Fires the active weapon once and returns the strongest recoil of the viewmodel over the next frames. */
    private static float peakRecoil(WeaponTuning weapons, WeaponSlot active, boolean alt) {
        GameSimulation sim = sim(weapons, TestTuning.WORLD, with(active, 4, 2),
            "##########",
            "#>.......#",
            "##########");
        sim.update(FRAME, new PlayerInput());
        PlayerInput input = new PlayerInput();
        if (alt) input.charge = true;
        else input.fire = true;
        sim.update(FRAME, input);
        float peak = sim.viewmodel.getRecoil();
        PlayerInput idle = new PlayerInput();
        for (int i = 0; i < 20; i++) {
            sim.update(FRAME, idle);
            peak = Math.max(peak, sim.viewmodel.getRecoil());
        }
        return peak;
    }

    @Test
    void theRecoilKicksComeFromTheWeaponFile() {
        float pistol = peakRecoil(TestTuning.WEAPONS, WeaponSlot.PISTOL, false);
        assertTrue(pistol > 0.5f, "the pistol kicks: " + pistol);
        // The shipped kicks, measured against the pistol's 1: they were literals in the simulation before the file.
        assertEquals(1.6f, peakRecoil(TestTuning.WEAPONS, WeaponSlot.SCATTERGUN, false) / pistol, 1e-4f);
        assertEquals(2.4f, peakRecoil(TestTuning.WEAPONS, WeaponSlot.SCATTERGUN, true) / pistol, 1e-4f);
        assertEquals(2.6f, peakRecoil(TestTuning.WEAPONS, WeaponSlot.RAIL_LANCE, false) / pistol, 1e-4f);

        assertEquals(3f, peakRecoil(TestTuning.weaponsWith("pistol.recoil_kick", "3"), WeaponSlot.PISTOL, false)
            / pistol, 1e-4f);
        assertEquals(0.5f, peakRecoil(TestTuning.weaponsWith("scattergun.primary.recoil_kick", "0.5"),
            WeaponSlot.SCATTERGUN, false) / pistol, 1e-4f);
        assertEquals(4f, peakRecoil(TestTuning.weaponsWith("scattergun.alt.recoil_kick", "4"),
            WeaponSlot.SCATTERGUN, true) / pistol, 1e-4f);
        assertEquals(1.25f, peakRecoil(TestTuning.weaponsWith("rail.recoil_kick", "1.25"),
            WeaponSlot.RAIL_LANCE, false) / pistol, 1e-4f);
    }

    /** Charges a blast for a second, releases it and returns the strongest recoil the release gave. */
    private static float blastRecoil(WeaponTuning weapons) {
        GameSimulation sim = sim(weapons, TestTuning.WORLD, PlayerLoadout.fresh(),
            "##########",
            "#>.......#",
            "##########");
        PlayerInput hold = new PlayerInput();
        hold.charge = true;
        for (int i = 0; i < 60; i++) sim.update(FRAME, hold);
        PlayerInput idle = new PlayerInput();
        sim.update(FRAME, idle);
        assertEquals(1, sim.projectiles.size(), "the blast left");
        float peak = sim.viewmodel.getRecoil();
        for (int i = 0; i < 20; i++) {
            sim.update(FRAME, idle);
            peak = Math.max(peak, sim.viewmodel.getRecoil());
        }
        return peak;
    }

    @Test
    void theBlastKickAndSizeComeFromTheWeaponFile() {
        float shipped = blastRecoil(TestTuning.WEAPONS);
        assertTrue(shipped > 0f);
        assertEquals(2f, blastRecoil(TestTuning.weaponsWith("blast.recoil_kick", "2")) / shipped, 1e-4f);

        assertEquals(3f, blastExplosionSize(TestTuning.WEAPONS), 1e-4f);
        assertEquals(4.5f, blastExplosionSize(TestTuning.weaponsWith("blast.max_radius", "4.5")), 1e-4f);
    }

    /** Fires a full blast down a short corridor and returns the size of the explosion it leaves on the wall. */
    private static float blastExplosionSize(WeaponTuning weapons) {
        GameSimulation sim = sim(weapons, TestTuning.WORLD, PlayerLoadout.fresh(),
            "########",
            "#>.....#",
            "########");
        PlayerInput hold = new PlayerInput();
        hold.charge = true;
        for (int i = 0; i < 90; i++) sim.update(FRAME, hold);
        PlayerInput idle = new PlayerInput();
        for (int i = 0; i < 120; i++) {
            sim.update(FRAME, idle);
            for (Effect effect : sim.effects) {
                if (effect.type == Effect.Type.EXPLOSION) return effect.size;
            }
        }
        throw new AssertionError("the blast never exploded");
    }

    /** Fires the pistol at a wall with a turret four cells off behind another wall; returns whether it woke. */
    private static boolean turretBehindAWallHears(WorldTuning world) {
        return turretBehindAWallHears(world, WeaponSlot.PISTOL, false, 4);
    }

    /**
     * Fires the weapon at a wall with a turret behind another wall, {@code cells} from the player; returns whether
     * it woke.
     */
    private static boolean turretBehindAWallHears(WorldTuning world, WeaponSlot active, boolean alt, int cells) {
        GameSimulation sim = sim(TestTuning.WEAPONS, world, with(active, 4, 2),
            "###########",
            "#>..#" + ".......".substring(0, cells - 4) + "T" + "....".substring(cells - 4) + "#",
            "###########");
        sim.update(FRAME, new PlayerInput());
        assertEquals(Enemy.State.DORMANT, sim.enemies.get(0).getState(), "it does not see the player");
        PlayerInput input = new PlayerInput();
        if (alt) input.charge = true;
        else input.fire = true;
        sim.update(FRAME, input);
        return sim.enemies.get(0).getState() != Enemy.State.DORMANT;
    }

    /**
     * Fires the pistol along a hall with a turret 17 cells down it, too far to see the player and a row off the
     * line of the shot; returns whether it woke.
     */
    private static boolean turretDownTheHallHears(WorldTuning world) {
        GameSimulation sim = sim(TestTuning.WEAPONS, world, PlayerLoadout.fresh(),
            "#####################",
            "#>..................#",
            "#.................T.#",
            "#####################");
        sim.update(FRAME, new PlayerInput());
        if (sim.enemies.get(0).getState() != Enemy.State.DORMANT) throw new AssertionError("it saw the player");
        sim.update(FRAME, firing());
        return sim.enemies.get(0).getState() != Enemy.State.DORMANT;
    }

    @Test
    void aShotIsHeardAsFarAsTheNoiseTableSays() {
        // Four cells through a wall: inside the shipped muffled range of 4.5.
        assertTrue(turretBehindAWallHears(TestTuning.WORLD));
        assertFalse(turretBehindAWallHears(TestTuning.worldWith("noise.pistol_shot", "9, 3.5")));
        // Seventeen cells with a line of sight: outside the shipped range of 9.
        assertFalse(turretDownTheHallHears(TestTuning.WORLD));
        assertTrue(turretDownTheHallHears(TestTuning.worldWith("noise.pistol_shot", "18, 4.5")));
        // Another noise's line changes nothing for a pistol shot.
        assertTrue(turretBehindAWallHears(TestTuning.worldWith("noise.rail_shot", "1, 1")));
    }

    /** Each kind of shot reads its own line: the same turret, with that line cut short and with the others cut. */
    @Test
    void eachKindOfShotIsHeardAsFarAsItsOwnLineSays() {
        String[] keys = {"noise.pistol_shot", "noise.rail_shot", "noise.scattergun_shot",
            "noise.scattergun_double_shot"};
        WeaponSlot[] weapons = {WeaponSlot.PISTOL, WeaponSlot.RAIL_LANCE, WeaponSlot.SCATTERGUN,
            WeaponSlot.SCATTERGUN};
        boolean[] alt = {false, false, false, true};
        // Just inside each shipped muffled range: 4.5, 7.5, 5.5 and 6.5.
        int[] cells = {4, 7, 5, 6};
        for (int shot = 0; shot < keys.length; shot++) {
            assertTrue(turretBehindAWallHears(TestTuning.WORLD, weapons[shot], alt[shot], cells[shot]), keys[shot]);
            for (String key : keys) {
                boolean hears = turretBehindAWallHears(TestTuning.worldWith(key, "20, 3.5"), weapons[shot],
                    alt[shot], cells[shot]);
                assertEquals(!key.equals(keys[shot]), hears, keys[shot] + " with " + key + " cut short");
            }
        }
    }

    /** Walks the player from 60 health over a medkit; returns the health after it. */
    private static float healthAfterAMedkit(WorldTuning world) {
        return afterPickups(world).player.getHealth();
    }

    /** Walks the player, at 60 health and no armor, over a medkit and an armor pickup. */
    private static GameSimulation afterPickups(WorldTuning world) {
        GameSimulation sim = sim(TestTuning.WEAPONS, world, new PlayerLoadout(60f, 0f, Difficulty.NORMAL),
            "######",
            "#>+a.#",
            "######");
        PlayerInput forward = new PlayerInput();
        forward.moveForward = 1f;
        for (int i = 0; i < 70; i++) sim.update(FRAME, forward);
        return sim;
    }

    /** Walks the player, with the meter at 10, over an energy cell; returns the energy it gave. */
    private static float energyFromACell(WorldTuning world) {
        GameSimulation sim = sim(TestTuning.WEAPONS, world, PlayerLoadout.fresh(),
            "########",
            "#>....e#",
            "########");
        sim.energy.setEnergy(10f);
        PlayerInput forward = new PlayerInput();
        forward.moveForward = 1f;
        float before = 0f;
        for (int i = 0; i < 200 && !sim.pickups.get(0).isCollected(); i++) {
            before = sim.energy.getEnergy();
            sim.update(FRAME, forward);
        }
        assertTrue(sim.pickups.get(0).isCollected());
        // The step that collects also refills a frame's worth; the tolerance of the caller covers it.
        return sim.energy.getEnergy() - before;
    }

    /** Walks the player onto a shock grate and stands there; returns the damage taken after {@code seconds}. */
    private static float grateDamageTaken(WorldTuning world, float seconds) {
        GameSimulation sim = sim(TestTuning.WEAPONS, world, PlayerLoadout.fresh(),
            "#####",
            "#>~~#",
            "#####");
        PlayerInput forward = new PlayerInput();
        forward.moveForward = 1f;
        while (sim.getDamageTaken() <= 0f && sim.getTime() < 2f) sim.update(FRAME, forward);
        float first = sim.getTime();
        while (sim.getTime() < first + seconds) sim.update(FRAME, forward);
        return sim.getDamageTaken();
    }

    @Test
    void pickupsAndGratesFollowTheWorldFile() {
        assertEquals(85f, healthAfterAMedkit(TestTuning.WORLD), 1e-3f);
        assertEquals(72f, healthAfterAMedkit(TestTuning.worldWith("pickup.medkit", "12")), 1e-3f);
        // The medkit and the armor ship with the same 25: each reads its own key.
        assertEquals(25f, afterPickups(TestTuning.WORLD).player.getArmor(), 1e-3f);
        GameSimulation tuned = afterPickups(TestTuning.worldWith("pickup.armor", "40"));
        assertEquals(40f, tuned.player.getArmor(), 1e-3f);
        assertEquals(85f, tuned.player.getHealth(), 1e-3f);
        assertEquals(25f, afterPickups(TestTuning.worldWith("pickup.medkit", "12")).player.getArmor(), 1e-3f);
        assertEquals(35f, energyFromACell(TestTuning.WORLD), 0.5f);
        assertEquals(60f, energyFromACell(TestTuning.worldWith("pickup.energy_cell", "60")), 0.5f);

        // The first tick, then one every 0.35 seconds: three within 0.8 seconds.
        assertEquals(24f, grateDamageTaken(TestTuning.WORLD, 0.8f), 1e-3f);
        assertEquals(9f, grateDamageTaken(TestTuning.worldWith("grate.damage", "3"), 0.8f), 1e-3f);
        assertEquals(16f, grateDamageTaken(TestTuning.worldWith("grate.interval", "0.6"), 0.8f), 1e-3f);
    }

    /** Shoots the canister two cells off until it explodes; returns the damage its explosion did to the player. */
    private static float canisterDamageToThePlayer(WorldTuning world) {
        GameSimulation sim = sim(TestTuning.WEAPONS, world, PlayerLoadout.fresh(),
            "#####",
            "#>o.#",
            "#####");
        PlayerInput fire = firing();
        for (int i = 0; i < 90 && !sim.targets.get(0).isDestroyed(); i++) sim.update(FRAME, fire);
        assertTrue(sim.targets.get(0).isDestroyed());
        return sim.getDamageTaken();
    }

    @Test
    void theCanisterExplosionFollowsTheWorldFile() {
        float shipped = canisterDamageToThePlayer(TestTuning.WORLD);
        assertTrue(shipped > 0f, "the player stands inside the shipped radius of 2.2");
        assertEquals(shipped * 2f, canisterDamageToThePlayer(TestTuning.worldWith("canister.blast_damage", "160")),
            1e-3f);
        assertEquals(shipped * 0.5f,
            canisterDamageToThePlayer(TestTuning.worldWith("canister.self_damage_scale", "0.25")), 1e-3f);
        assertEquals(0f, canisterDamageToThePlayer(TestTuning.worldWith("canister.blast_radius", "0.6")), 0f);
    }

    @Test
    void theToastAndTheHitMarkerLastAsLongAsTheWorldFileSays() {
        WorldTuning brief = TestTuning.worldWith("feedback.toast_time", "0.5");
        for (WorldTuning world : new WorldTuning[] {TestTuning.WORLD, brief}) {
            GameSimulation sim = sim(TestTuning.WEAPONS, world, new PlayerLoadout(60f, 0f, Difficulty.NORMAL),
                "#####",
                "#>+.#",
                "#####");
            PlayerInput forward = new PlayerInput();
            forward.moveForward = 1f;
            while (sim.getToast() == null && sim.getTime() < 2f) sim.update(FRAME, forward);
            assertEquals("+25 HEALTH", TestTuning.STRINGS.toast(sim.getToast()));
            PlayerInput idle = new PlayerInput();
            for (int i = 0; i < 60; i++) sim.update(FRAME, idle);
            assertEquals(world == brief, sim.getToast() == null, "a second later");
        }

        WorldTuning longMarker = TestTuning.worldWith("feedback.hit_marker_time", "0.64");
        float[] left = new float[2];
        WorldTuning[] worlds = {TestTuning.WORLD, longMarker};
        for (int w = 0; w < worlds.length; w++) {
            GameSimulation sim = sim(TestTuning.WEAPONS, worlds[w], PlayerLoadout.fresh(),
                "##########",
                "#>..O....#",
                "##########");
            sim.update(FRAME, firing());
            assertEquals(1f, sim.getHitMarker(), 1e-4f);
            for (int i = 0; i < 4; i++) sim.update(FRAME, new PlayerInput());
            left[w] = 1f - sim.getHitMarker();
        }
        assertEquals(left[0] / 4f, left[1], 1e-3f, "four times the time, a quarter of the fade");
    }

    /** A damaged save can hold more ammunition than the player carries; the level plays with the caps. */
    @Test
    void aLoadoutOverTheCapsPlaysWithTheCaps() {
        PlayerLoadout stuffed = with(WeaponSlot.SCATTERGUN, 999, 999);
        assertEquals(999, stuffed.shells, "the loadout itself does not know the caps");
        GameSimulation sim = sim(TestTuning.WEAPONS, TestTuning.WORLD, stuffed,
            "##########",
            "#>s.....r#",
            "##########");
        // What the HUD draws, before the first update: the count and the cap come from these four getters.
        assertEquals(24, sim.getShells());
        assertEquals(24, sim.getMaxShells());
        assertEquals(15, sim.getSlugs());
        assertEquals(15, sim.getMaxSlugs());

        sim.update(FRAME, new PlayerInput());
        sim.update(FRAME, firing());
        assertEquals(23, sim.getShells(), "a shot spends one of the 24");
        PlayerInput forward = new PlayerInput();
        forward.moveForward = 1f;
        for (int i = 0; i < 60; i++) sim.update(FRAME, forward);
        assertEquals(24, sim.getShells(), "the shell box tops up to the cap");

        // The loadout carried on from the level holds what the level played with, and that is what is saved.
        PlayerLoadout carried = sim.captureCarryOverLoadout();
        assertEquals(24, carried.shells);
        assertEquals(15, carried.slugs);
        SaveData save = new SaveData(new MapKeyValueStore());
        save.saveCampaignRun(1, carried);
        assertEquals(24, save.getSavedLoadout().shells);
        assertEquals(15, save.getSavedLoadout().slugs);

        // The caps are the file's: with more room the same loadout keeps more.
        GameSimulation roomy = sim(TestTuning.weaponsWith("scattergun.max_shells", "40"), TestTuning.WORLD, stuffed,
            "###", "#>#", "###");
        assertEquals(40, roomy.getShells());
        assertEquals(40, roomy.getMaxShells());
        assertEquals(15, roomy.getSlugs());
        assertEquals(0, new PlayerLoadout(100f, 0f, Difficulty.NORMAL, true, -5, true, -5, WeaponSlot.PISTOL).shells);
    }

    @Test
    void theStarterAmmunitionIsTheWeaponPickupsOfTheWeaponFile() {
        Levels.Stage lockdownWing = Levels.stage("04_lockdown_wing");
        PlayerLoadout shipped = PlayerLoadout.forStage(lockdownWing, Difficulty.NORMAL, TestTuning.DIFFICULTY,
            TestTuning.WEAPONS);
        assertEquals(8, shipped.shells);
        assertEquals(5, shipped.slugs);
        WeaponTuning generous = TestTuning.weaponsWith("scattergun.weapon_pickup_shells", "20");
        assertEquals(20,
            PlayerLoadout.forStage(lockdownWing, Difficulty.NORMAL, TestTuning.DIFFICULTY, generous).shells);
        // Easy scales the 20 to 25; the cap of 24 holds it.
        assertEquals(24, PlayerLoadout.forStage(Levels.stage("02_coolant_core"), Difficulty.EASY,
            TestTuning.DIFFICULTY, generous).shells);
        assertEquals(2, PlayerLoadout.forStage(lockdownWing, Difficulty.NORMAL, TestTuning.DIFFICULTY,
            TestTuning.weaponsWith("rail.weapon_pickup_slugs", "2")).slugs);
    }

    @Test
    void theSimulationNeedsItsWeaponAndWorldTuning() {
        GameMap map = GameMap.parse("###", "#>#", "###");
        assertThrows(IllegalArgumentException.class, () -> new GameSimulation(map, PlayerLoadout.fresh(), false,
            TestTuning.ENEMIES, TestTuning.DIFFICULTY, null, TestTuning.WORLD));
        assertThrows(IllegalArgumentException.class, () -> new GameSimulation(map, PlayerLoadout.fresh(), false,
            TestTuning.ENEMIES, TestTuning.DIFFICULTY, TestTuning.WEAPONS, null));
    }
}

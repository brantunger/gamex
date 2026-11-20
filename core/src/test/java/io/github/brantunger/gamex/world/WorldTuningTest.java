package io.github.brantunger.gamex.world;

import static org.junit.jupiter.api.Assertions.assertAll;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import io.github.brantunger.gamex.TestTuning;
import io.github.brantunger.gamex.data.AssetText;
import io.github.brantunger.gamex.weapon.WeaponTuning;
import io.github.brantunger.gamex.world.WorldTuning.NoiseEvent;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.function.Executable;

import java.util.ArrayList;
import java.util.List;

/**
 * Checks the world tuning file: that each key sets its own value, that the shipped file loads, what a changed value
 * does, and its errors.
 */
class WorldTuningTest {

    private static final WorldTuning G = TestTuning.WORLD;

    /** The tests' own files, in which every number differs from the shipped one and from every other. */
    private static final String FIXTURE = "data-fixtures/world.txt";
    private static final String WEAPONS_FIXTURE = "data-fixtures/weapons.txt";

    private static void assertNoise(WorldTuning tuning, NoiseEvent event, float losRange, float muffledRange) {
        assertEquals(losRange, tuning.noise(event).losRange, 0f, event + " line of sight");
        assertEquals(muffledRange, tuning.noise(event).muffledRange, 0f, event + " muffled");
    }

    /**
     * The values on the right are those of the test's own file, where no two numbers are alike, so a key read into
     * another key's field, or a value still held in code, shows.
     */
    @Test
    void everyKeyOfAWorldFileSetsItsOwnValue() {
        WorldTuning t = WorldTuning.parse(FIXTURE, AssetText.read("data", FIXTURE));
        assertEquals(26f, t.pickupMedkit, 0f);
        assertEquals(27f, t.pickupArmor, 0f);
        assertEquals(36f, t.pickupEnergyCell, 0f);

        assertEquals(2.244f, t.canisterBlastRadius, 0f);
        assertEquals(81f, t.canisterBlastDamage, 0f);
        assertEquals(0.515f, t.canisterSelfDamageScale, 0f);

        assertEquals(9f, t.grateDamage, 0f);
        assertEquals(0.364f, t.grateInterval, 0f);
        assertEquals(0.063f, t.grateClearanceHeight, 0f);

        assertEquals(0.1696f, t.hitMarkerTime, 0f);
        assertEquals(2.14f, t.toastTime, 0f);

        assertEquals(10, NoiseEvent.values().length, "a new noise needs its line here");
        assertNoise(t, NoiseEvent.BLAST_DETONATION, 12f, 5.94f);
        assertNoise(t, NoiseEvent.CONSOLE_OVERRIDE, 13f, 8f);
        assertNoise(t, NoiseEvent.PISTOL_SHOT, 10f, 4.905f);
        assertNoise(t, NoiseEvent.PISTOL_HIT, 6f, 4f);
        assertNoise(t, NoiseEvent.RAIL_SHOT, 16f, 8.25f);
        assertNoise(t, NoiseEvent.RAIL_HIT, 11f, 5f);
        assertNoise(t, NoiseEvent.SCATTERGUN_SHOT, 14f, 6.105f);
        assertNoise(t, NoiseEvent.SCATTERGUN_DOUBLE_SHOT, 15f, 7.28f);
        assertNoise(t, NoiseEvent.SCATTERGUN_HIT, 7f, 17f);
        assertNoise(t, NoiseEvent.CANISTER_EXPLOSION, 18f, 6.215f);
    }

    /** Each kind of pickup gives the amount of its own key, of the world file or of the weapon file. */
    @Test
    void everyKindOfPickupGivesTheAmountOfItsOwnKey() {
        WorldTuning world = WorldTuning.parse(FIXTURE, AssetText.read("data", FIXTURE));
        WeaponTuning weapons = WeaponTuning.parse(WEAPONS_FIXTURE, AssetText.read("data", WEAPONS_FIXTURE));
        assertEquals(26f, Pickup.baseAmount(Pickup.Kind.MEDKIT, world, weapons), 0f);
        assertEquals(27f, Pickup.baseAmount(Pickup.Kind.ARMOR, world, weapons), 0f);
        assertEquals(36f, Pickup.baseAmount(Pickup.Kind.ENERGY_CELL, world, weapons), 0f);
        assertEquals(0f, Pickup.baseAmount(Pickup.Kind.AMBER_KEYCARD, world, weapons), 0f);
        assertEquals(0f, Pickup.baseAmount(Pickup.Kind.CYAN_KEYCARD, world, weapons), 0f);
        assertEquals(9f, Pickup.baseAmount(Pickup.Kind.SCATTERGUN, world, weapons), 0f);
        assertEquals(7f, Pickup.baseAmount(Pickup.Kind.SHELLS, world, weapons), 0f);
        assertEquals(12f, Pickup.baseAmount(Pickup.Kind.RAIL_LANCE, world, weapons), 0f);
        assertEquals(4f, Pickup.baseAmount(Pickup.Kind.RAIL_SLUGS, world, weapons), 0f);
        assertEquals(9, Pickup.Kind.values().length, "a new kind needs its amount here");
        // The collision radius is geometry the models are sculpted to; it stays in code.
        assertEquals(0.22f, Pickup.RADIUS, 0f);
    }

    /** Whatever values the shipped file holds, it loads and gives every noise its two ranges. */
    @Test
    void theShippedFileLoads() {
        WorldTuning shipped = WorldTuning.load();
        for (NoiseEvent event : NoiseEvent.values()) {
            assertTrue(shipped.noise(event).losRange >= 0f && shipped.noise(event).muffledRange >= 0f,
                event.toString());
        }
        assertTrue(shipped.pickupMedkit >= 1f && shipped.pickupArmor >= 1f && shipped.pickupEnergyCell >= 1f);
    }

    @Test
    void aChangedLineChangesTheLoadedValue() {
        WorldTuning tuned = TestTuning.worldWith("noise.rail_shot", "20, 2.5");
        assertEquals(20f, tuned.noise(NoiseEvent.RAIL_SHOT).losRange, 0f);
        assertEquals(2.5f, tuned.noise(NoiseEvent.RAIL_SHOT).muffledRange, 0f);
        assertEquals(7f, tuned.noise(NoiseEvent.RAIL_HIT).losRange, 0f, "the other noises are untouched");
        assertEquals(40f, TestTuning.worldWith("pickup.medkit", "40").pickupMedkit, 0f);
        assertEquals(40f, Pickup.baseAmount(Pickup.Kind.MEDKIT, TestTuning.worldWith("pickup.medkit", "40"),
            TestTuning.WEAPONS), 0f);
        assertEquals(11f, Pickup.baseAmount(Pickup.Kind.SHELLS, G,
            TestTuning.weaponsWith("scattergun.box_pickup_shells", "11")), 0f);
    }

    private static String error(String key, String value) {
        String text = TestTuning.withValue(TestTuning.text(WorldTuning.PATH), key, value);
        return assertThrows(IllegalArgumentException.class, () -> WorldTuning.parse(WorldTuning.PATH, text))
            .getMessage();
    }

    /** A pickup's scaled amount is floored at 1, so a base amount below 1 would not be what the player gets. */
    @Test
    void aPickupGivesAtLeastOne() {
        List<Executable> checks = new ArrayList<>();
        for (String key : new String[] {"pickup.medkit", "pickup.armor", "pickup.energy_cell"}) {
            for (String value : new String[] {"0", "0.5"}) {
                checks.add(() -> {
                    String message = error(key, value);
                    assertTrue(message.startsWith("Invalid '" + key + "' in data/world.txt at line ")
                        && message.endsWith(": must be at least 1, got '" + value + "'"), message);
                });
            }
        }
        assertAll(checks);
        assertEquals(1f, TestTuning.worldWith("pickup.medkit", "1").pickupMedkit, 0f);
        assertEquals(1f, TestTuning.worldWith("pickup.armor", "1").pickupArmor, 0f);
        assertEquals(1f, TestTuning.worldWith("pickup.energy_cell", "1").pickupEnergyCell, 0f);
    }

    @Test
    void aMissingUnknownOrInvalidValueFailsNamingTheFileAndTheKey() {
        assertEquals("Missing 'grate.damage' key in data/world.txt", error("grate.damage", null));
        assertEquals("Missing 'noise.pistol_hit' key in data/world.txt", error("noise.pistol_hit", null));

        String unknown = assertThrows(IllegalArgumentException.class, () -> WorldTuning.parse(
            WorldTuning.PATH, "noise.footstep: 3, 1\n" + TestTuning.text(WorldTuning.PATH))).getMessage();
        assertEquals("Unknown key 'noise.footstep' in data/world.txt at line 1", unknown);

        for (String[] bad : new String[][] {
            {"pickup.medkit", "-1"}, {"pickup.armor", "25f"}, {"pickup.energy_cell", ""},
            {"canister.blast_radius", "0"}, {"canister.blast_damage", "-80"},
            {"canister.self_damage_scale", "-0.5"},
            {"grate.damage", "-8"}, {"grate.interval", "0"}, {"grate.clearance_height", "-0.1"},
            {"feedback.hit_marker_time", "0"}, {"feedback.toast_time", "-2"},
            {"noise.pistol_shot", "9"}, {"noise.pistol_shot", "9, 4.5, 1"}, {"noise.pistol_shot", "-9, 4.5"},
            {"noise.rail_hit", "7, -4"}, {"noise.canister_explosion", "far, near"},
        }) {
            String message = error(bad[0], bad[1]);
            assertTrue(message.contains("data/world.txt") && message.contains("'" + bad[0] + "'")
                && message.contains(" at line "), bad[0] + ": " + bad[1] + " gave: " + message);
        }
    }
}

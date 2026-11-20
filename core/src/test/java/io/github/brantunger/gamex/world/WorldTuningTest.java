package io.github.brantunger.gamex.world;

import static org.junit.jupiter.api.Assertions.assertAll;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import io.github.brantunger.gamex.TestTuning;
import io.github.brantunger.gamex.data.AssetText;
import io.github.brantunger.gamex.world.WorldTuning.NoiseEvent;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.function.Executable;

import java.util.ArrayList;
import java.util.List;

/** Checks the world tuning file: what it ships with, what a changed value does, and its errors. */
class WorldTuningTest {

    private static final WorldTuning G = TestTuning.WORLD;

    private static void assertNoise(NoiseEvent event, float losRange, float muffledRange) {
        assertEquals(losRange, G.noise(event).losRange, 0f, event + " line of sight");
        assertEquals(muffledRange, G.noise(event).muffledRange, 0f, event + " muffled");
    }

    /** The values are the constants and literals {@code Pickup.Kind} and the simulation held in commit 10cd4a4. */
    @Test
    void theShippedFileHoldsTheValuesTheCodeHadBeforeIt() {
        assertEquals(25f, G.pickupMedkit, 0f);
        assertEquals(25f, G.pickupArmor, 0f);
        assertEquals(35f, G.pickupEnergyCell, 0f);

        assertEquals(2.2f, G.canisterBlastRadius, 0f);
        assertEquals(80f, G.canisterBlastDamage, 0f);
        assertEquals(0.5f, G.canisterSelfDamageScale, 0f);

        assertEquals(8f, G.grateDamage, 0f);
        assertEquals(0.35f, G.grateInterval, 0f);
        assertEquals(0.06f, G.grateClearanceHeight, 0f);

        assertEquals(0.16f, G.hitMarkerTime, 0f);
        assertEquals(2.0f, G.toastTime, 0f);
        assertEquals(12f, G.minimapRevealRadius, 0f);

        assertEquals(10, NoiseEvent.values().length, "a new noise needs its line here");
        assertNoise(NoiseEvent.BLAST_DETONATION, 11.0f, 5.5f);
        assertNoise(NoiseEvent.CONSOLE_OVERRIDE, 12.0f, 7.0f);
        assertNoise(NoiseEvent.PISTOL_SHOT, 9.0f, 4.5f);
        assertNoise(NoiseEvent.PISTOL_HIT, 5.0f, 3.0f);
        assertNoise(NoiseEvent.RAIL_SHOT, 15.0f, 7.5f);
        assertNoise(NoiseEvent.RAIL_HIT, 7.0f, 4.0f);
        assertNoise(NoiseEvent.SCATTERGUN_SHOT, 11.0f, 5.5f);
        assertNoise(NoiseEvent.SCATTERGUN_DOUBLE_SHOT, 13.0f, 6.5f);
        assertNoise(NoiseEvent.SCATTERGUN_HIT, 5.0f, 3.0f);
        assertNoise(NoiseEvent.CANISTER_EXPLOSION, 11.0f, 5.5f);
    }

    /** What each kind of pickup gave on Normal in commit 10cd4a4, where {@code Pickup.Kind} held the amounts. */
    @Test
    void thePickupsGiveWhatTheirKindsHeldBeforeTheFile() {
        assertEquals(25f, Pickup.baseAmount(Pickup.Kind.MEDKIT, G, TestTuning.WEAPONS), 0f);
        assertEquals(25f, Pickup.baseAmount(Pickup.Kind.ARMOR, G, TestTuning.WEAPONS), 0f);
        assertEquals(35f, Pickup.baseAmount(Pickup.Kind.ENERGY_CELL, G, TestTuning.WEAPONS), 0f);
        assertEquals(0f, Pickup.baseAmount(Pickup.Kind.AMBER_KEYCARD, G, TestTuning.WEAPONS), 0f);
        assertEquals(0f, Pickup.baseAmount(Pickup.Kind.CYAN_KEYCARD, G, TestTuning.WEAPONS), 0f);
        assertEquals(8f, Pickup.baseAmount(Pickup.Kind.SCATTERGUN, G, TestTuning.WEAPONS), 0f);
        assertEquals(6f, Pickup.baseAmount(Pickup.Kind.SHELLS, G, TestTuning.WEAPONS), 0f);
        assertEquals(5f, Pickup.baseAmount(Pickup.Kind.RAIL_LANCE, G, TestTuning.WEAPONS), 0f);
        assertEquals(3f, Pickup.baseAmount(Pickup.Kind.RAIL_SLUGS, G, TestTuning.WEAPONS), 0f);
        assertEquals(9, Pickup.Kind.values().length, "a new kind needs its amount here");
        // The collision radius is geometry the models are sculpted to; it stays in code.
        assertEquals(0.22f, Pickup.RADIUS, 0f);
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
        assertEquals(16f, TestTuning.worldWith("minimap.reveal_radius", "16").minimapRevealRadius, 0f);
    }

    private static String error(String key, String value) {
        String text = TestTuning.withValue(AssetText.read("data", WorldTuning.PATH), key, value);
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
        assertEquals("Missing 'minimap.reveal_radius' key in data/world.txt", error("minimap.reveal_radius", null));
        assertEquals("Missing 'noise.pistol_hit' key in data/world.txt", error("noise.pistol_hit", null));

        String unknown = assertThrows(IllegalArgumentException.class, () -> WorldTuning.parse(
            WorldTuning.PATH, "noise.footstep: 3, 1\n" + AssetText.read("data", WorldTuning.PATH))).getMessage();
        assertEquals("Unknown key 'noise.footstep' in data/world.txt at line 1", unknown);

        for (String[] bad : new String[][] {
            {"pickup.medkit", "-1"}, {"pickup.armor", "25f"}, {"pickup.energy_cell", ""},
            {"canister.blast_radius", "0"}, {"canister.blast_damage", "-80"},
            {"canister.self_damage_scale", "-0.5"},
            {"grate.damage", "-8"}, {"grate.interval", "0"}, {"grate.clearance_height", "-0.1"},
            {"feedback.hit_marker_time", "0"}, {"feedback.toast_time", "-2"},
            {"minimap.reveal_radius", "0"}, {"minimap.reveal_radius", "-4"}, {"minimap.reveal_radius", "near"},
            {"noise.pistol_shot", "9"}, {"noise.pistol_shot", "9, 4.5, 1"}, {"noise.pistol_shot", "-9, 4.5"},
            {"noise.rail_hit", "7, -4"}, {"noise.canister_explosion", "far, near"},
        }) {
            String message = error(bad[0], bad[1]);
            assertTrue(message.contains("data/world.txt") && message.contains("'" + bad[0] + "'")
                && message.contains(" at line "), bad[0] + ": " + bad[1] + " gave: " + message);
        }
    }
}

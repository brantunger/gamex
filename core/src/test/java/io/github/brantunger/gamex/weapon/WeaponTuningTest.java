package io.github.brantunger.gamex.weapon;

import static org.junit.jupiter.api.Assertions.assertAll;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.badlogic.gdx.math.Vector3;
import io.github.brantunger.gamex.TestTuning;
import io.github.brantunger.gamex.data.AssetText;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;

/** Checks the weapon tuning file: what it ships with, what a changed value does, and its errors. */
class WeaponTuningTest {

    private static final WeaponTuning W = TestTuning.WEAPONS;

    private static void assertRing(WeaponTuning.Ring ring, int count, float coneDegrees, float twistDegrees) {
        assertEquals(count, ring.count);
        assertEquals(coneDegrees, ring.coneDegrees, 0f);
        assertEquals(twistDegrees, ring.twistDegrees, 0f);
    }

    /** The values are the constants the weapon classes and the simulation held before the file (commit 10cd4a4). */
    @Test
    void theShippedFileHoldsTheValuesTheCodeHadBeforeIt() {
        assertEquals(0.3f, W.pistolFireInterval, 0f);
        assertEquals(20f, W.pistolDamage, 0f);
        assertEquals(60f, W.pistolRange, 0f);
        assertEquals(0.07f, W.pistolFlashTime, 0f);
        assertEquals(1f, W.pistolRecoilKick, 0f);

        assertEquals(24, W.scattergunMaxShells);
        assertEquals(8, W.scattergunWeaponPickupShells);
        assertEquals(6, W.scattergunBoxPickupShells);
        assertEquals(8, W.scattergunPrimaryPellets);
        assertEquals(0.75f, W.scattergunPrimaryInterval, 0f);
        assertEquals(1.6f, W.scattergunPrimaryRecoilKick, 0f);
        assertEquals(0.08f, W.scattergunPrimaryPumpDelay, 0f);
        assertEquals(0.48f, W.scattergunPrimaryPumpTime, 0f);
        assertEquals(16, W.scattergunAltPellets);
        assertEquals(1.15f, W.scattergunAltInterval, 0f);
        assertEquals(2.4f, W.scattergunAltRecoilKick, 0f);
        assertEquals(0.12f, W.scattergunAltPumpDelay, 0f);
        assertEquals(0.68f, W.scattergunAltPumpTime, 0f);
        assertEquals(12f, W.scattergunPelletDamage, 0f);
        assertEquals(7f, W.scattergunFalloffStart, 0f);
        assertEquals(26f, W.scattergunRange, 0f);
        assertEquals(0.35f, W.scattergunMinFalloffScale, 0f);
        assertEquals(0.09f, W.scattergunFlashTime, 0f);
        assertEquals(0.35f, W.scattergunPumpPullShare, 0f);

        assertEquals(15, W.railMaxSlugs);
        assertEquals(5, W.railWeaponPickupSlugs);
        assertEquals(3, W.railPackPickupSlugs);
        assertEquals(120f, W.railDamage, 0f);
        assertEquals(60f, W.railRange, 0f);
        assertEquals(1.10f, W.railFireInterval, 0f);
        assertEquals(0.10f, W.railFlashTime, 0f);
        assertEquals(0.04f, W.railBoltDelay, 0f);
        assertEquals(0.68f, W.railBoltTime, 0f);
        assertEquals(0.25f, W.railBoltPullShare, 0f);
        assertEquals(1.8f, W.railZoomFactor, 0f);
        assertEquals(7.5f, W.railZoomSpeed, 0f);
        assertEquals(2.6f, W.railRecoilKick, 0f);

        assertEquals(1.2f, W.blastFullChargeTime, 0f);
        assertEquals(0.25f, W.blastMinCharge, 0f);
        assertEquals(20f, W.blastMinCost, 0f);
        assertEquals(60f, W.blastMaxCost, 0f);
        assertEquals(1f, W.blastMinRadius, 0f);
        assertEquals(3f, W.blastMaxRadius, 0f);
        assertEquals(30f, W.blastMinDamage, 0f);
        assertEquals(130f, W.blastMaxDamage, 0f);
        assertEquals(1f, W.blastRecoilKick, 0f);

        assertEquals(100f, W.energyCapacity, 0f);
        assertEquals(14f, W.energyRegen, 0f);
    }

    /** The rings the code built from literals before the file: 7 at 4.2, and 5 at 3.4 with 10 at 6.8 turned PI/10. */
    @Test
    void theShippedRingsAreTheOnesTheCodeBuiltBeforeTheFile() {
        assertEquals(1, W.scattergunPrimaryRings().size());
        assertRing(W.scattergunPrimaryRings().get(0), 7, 4.2f, 0f);
        assertEquals(2, W.scattergunAltRings().size());
        assertRing(W.scattergunAltRings().get(0), 5, 3.4f, 0f);
        assertRing(W.scattergunAltRings().get(1), 10, 6.8f, 18f);
        // The outer ring's twist was written as PI / 10 radians; 18 degrees must give the very same number.
        assertEquals(Math.PI / 10.0, Math.toRadians(W.scattergunAltRings().get(1).twistDegrees), 0.0);
        assertEquals(0.0, Math.toRadians(W.scattergunPrimaryRings().get(0).twistDegrees), 0.0);
        assertThrows(UnsupportedOperationException.class, () -> W.scattergunAltRings().remove(0));
    }

    /** A copy of {@code Scattergun.pelletDirections} and {@code addConeRing} as they were in commit 10cd4a4. */
    private static List<Vector3> pelletDirectionsBeforeTheFile(Vector3 forward, boolean doubleBlast) {
        Vector3 centre = new Vector3(forward);
        float len2 = centre.len2();
        if (!Float.isFinite(len2) || len2 < 1e-12f) centre.set(0f, 0f, -1f);
        else centre.nor();

        Vector3 right = new Vector3(centre).crs(Vector3.Y);
        if (right.len2() < 1e-8f) right.set(Vector3.X);
        else right.nor();
        Vector3 up = new Vector3(right).crs(centre).nor();

        List<Vector3> dirs = new ArrayList<>(doubleBlast ? 16 : 8);
        dirs.add(new Vector3(centre));
        if (!doubleBlast) {
            addConeRingBeforeTheFile(dirs, centre, right, up, 7, 4.2f, 0.0);
        } else {
            addConeRingBeforeTheFile(dirs, centre, right, up, 5, 3.4f, 0.0);
            addConeRingBeforeTheFile(dirs, centre, right, up, 10, 6.8f, Math.PI / 10.0);
        }
        return dirs;
    }

    private static void addConeRingBeforeTheFile(List<Vector3> out, Vector3 centre, Vector3 right, Vector3 up,
                                                 int count, float angleDeg, double phaseRad) {
        float spread = (float) Math.tan(Math.toRadians(angleDeg));
        for (int i = 0; i < count; i++) {
            double theta = phaseRad + i * (2.0 * Math.PI / count);
            float rx = (float) Math.cos(theta) * spread;
            float ry = (float) Math.sin(theta) * spread;
            out.add(new Vector3(centre).mulAdd(right, rx).mulAdd(up, ry).nor());
        }
    }

    @Test
    void theShippedPelletPatternIsBitForBitTheOneBeforeTheFile() {
        Scattergun gun = new Scattergun(W);
        Vector3[] aims = {
            new Vector3(0f, 0f, -1f), new Vector3(1f, 0f, 0f), new Vector3(0.4f, -0.15f, -0.9f),
            new Vector3(-0.31f, 0.22f, 0.77f).nor(), new Vector3(3f, 1f, -2f), new Vector3(0f, 1f, 0f),
            new Vector3(0.6f, -0.64f, 0.48f), new Vector3(0f, 0f, 0f), new Vector3(Float.NaN, 0f, -1f),
        };
        for (Vector3 aim : aims) {
            for (boolean doubleBlast : new boolean[] {false, true}) {
                List<Vector3> before = pelletDirectionsBeforeTheFile(aim, doubleBlast);
                List<Vector3> now = gun.pelletDirections(aim, doubleBlast);
                String where = aim + (doubleBlast ? " double" : " primary");
                assertEquals(doubleBlast ? 16 : 8, now.size(), where);
                assertEquals(before.size(), now.size(), where);
                for (int i = 0; i < before.size(); i++) {
                    assertEquals(Float.floatToIntBits(before.get(i).x), Float.floatToIntBits(now.get(i).x), where);
                    assertEquals(Float.floatToIntBits(before.get(i).y), Float.floatToIntBits(now.get(i).y), where);
                    assertEquals(Float.floatToIntBits(before.get(i).z), Float.floatToIntBits(now.get(i).z), where);
                }
            }
        }
    }

    /** The formulas as they were written in {@code BlastProjectile} and {@code BlastCharger} in commit 10cd4a4. */
    @Test
    void theShippedBlastGivesBitForBitTheRadiusDamageAndCostOfTheFormulasBeforeTheFile() {
        for (int i = -40; i <= 440; i++) {
            float charge = i / 400f;
            assertEquals(Float.floatToIntBits(1f + 2f * charge), Float.floatToIntBits(W.blastRadius(charge)));
            assertEquals(Float.floatToIntBits(30f + 100f * charge), Float.floatToIntBits(W.blastDamage(charge)));
            float t = Math.max(0f, Math.min(1f, (charge - 0.25f) / (1f - 0.25f)));
            assertEquals(Float.floatToIntBits(20f + (60f - 20f) * t), Float.floatToIntBits(W.blastCost(charge)));
        }
    }

    @Test
    void theShippedPelletDamageFallsOffAsItDidBeforeTheFile() {
        Scattergun gun = new Scattergun(W);
        for (float distance : new float[] {0f, 3f, 7f, 7.01f, 12.5f, 16.5f, 25.99f, 26f, 40f, Float.NaN}) {
            float before;
            if (Float.isNaN(distance) || distance <= 7f) before = 12f;
            else if (distance >= 26f) before = 12f * 0.35f;
            else before = 12f * (1f - (distance - 7f) / (26f - 7f) * (1f - 0.35f));
            assertEquals(Float.floatToIntBits(before), Float.floatToIntBits(gun.damageAtDistance(distance)),
                "at " + distance);
        }
    }

    private static int pistolShotsInTenSeconds(WeaponTuning tuning) {
        Pistol pistol = new Pistol(tuning);
        int shots = 0;
        for (int i = 0; i < 600; i++) {
            if (pistol.update(1f / 60f, true)) shots++;
        }
        return shots;
    }

    @Test
    void aChangedIntervalChangesTheFireRate() {
        assertEquals(34, pistolShotsInTenSeconds(W));
        assertEquals(17, pistolShotsInTenSeconds(TestTuning.weaponsWith("pistol.fire_interval", "0.6")));

        Scattergun slow = new Scattergun(TestTuning.weaponsWith("scattergun.primary.interval", "2"));
        assertEquals(Scattergun.Shot.PRIMARY, slow.update(1f / 60f, true, false, 4));
        slow.updateIdle(1.5f);
        assertFalse(slow.isReady(), "the shipped 0.75 would be over");

        RailLance rail = new RailLance(TestTuning.weaponsWith("rail.fire_interval", "3"));
        assertTrue(rail.update(1f / 60f, true, false, 2));
        rail.updateIdle(1.5f);
        assertEquals(0.5f, rail.getChargeProgress(), 1e-4f);
    }

    @Test
    void theRingRowsAreThePelletPattern() {
        WeaponTuning three = TestTuning.weaponsWith("scattergun.primary.ring", "3, 10, 0");
        assertEquals(4, three.scattergunPrimaryPellets);
        assertEquals(16, three.scattergunAltPellets, "the other pattern is untouched");
        Vector3 forward = new Vector3(0f, 0f, -1f);
        List<Vector3> pellets = new Scattergun(three).pelletDirections(forward, false);
        assertEquals(4, pellets.size());
        assertEquals(1f, pellets.get(0).dot(forward), 1e-6f);
        float cos = (float) Math.cos(Math.toRadians(10.0));
        for (int i = 1; i < pellets.size(); i++) assertEquals(cos, pellets.get(i).dot(forward), 1e-5f);
        // The first pellet of an untwisted ring is straight to the right; a quarter turn puts it straight up.
        assertTrue(pellets.get(1).x > 0.17f && Math.abs(pellets.get(1).y) < 1e-6f, pellets.get(1).toString());
        Vector3 turned = new Scattergun(TestTuning.weaponsWith("scattergun.primary.ring", "3, 10, 90"))
            .pelletDirections(forward, false).get(1);
        assertTrue(turned.y > 0.17f && Math.abs(turned.x) < 1e-6f, turned.toString());

        // A second row adds a ring: the pellet count follows the rows, with no count of its own to disagree.
        String text = AssetText.read("data", WeaponTuning.PATH)
            .replace("scattergun.primary.ring: 7, 4.2, 0", "scattergun.primary.ring: 7, 4.2, 0\n"
                + "scattergun.primary.ring: 4, 2, 45");
        WeaponTuning twoRings = WeaponTuning.parse(WeaponTuning.PATH, text);
        assertEquals(12, twoRings.scattergunPrimaryPellets);
        assertEquals(12, new Scattergun(twoRings).pelletDirections(forward, false).size());
    }

    @Test
    void theBlastAndTheMeterFollowTheirValues() {
        WeaponTuning tuned = TestTuning.weaponsWith("blast.max_radius", "5");
        assertEquals(5f, tuned.blastRadius(1f), 0f);
        assertEquals(2f, tuned.blastRadius(0.25f), 0f);
        assertEquals(190f, TestTuning.weaponsWith("blast.max_damage", "190").blastDamage(1f), 0f);
        assertEquals(45f, TestTuning.weaponsWith("blast.min_damage", "45").blastDamage(0f), 0f);

        tuned = TestTuning.weaponsWith("blast.min_cost", "40");
        EnergyMeter meter = new EnergyMeter(tuned);
        BlastCharger charger = new BlastCharger(meter, tuned);
        assertEquals(40f, charger.getMinCost(), 0f);
        assertEquals(40f, charger.costFor(charger.getMinCharge()), 0f);
        meter.setEnergy(30f);
        charger.update(0.1f, true);
        assertTrue(charger.isRefused(), "30 energy pays for the shipped smallest blast, not for this one");

        tuned = TestTuning.weaponsWith("blast.full_charge_time", "4");
        charger = new BlastCharger(new EnergyMeter(tuned), tuned);
        charger.update(1f, true);
        assertEquals(0.25f, charger.getCharge(), 1e-5f);

        meter = new EnergyMeter(TestTuning.weaponsWith("energy.capacity", "250"));
        assertEquals(250f, meter.getEnergy(), 0f);
        meter = new EnergyMeter(TestTuning.weaponsWith("energy.regen", "30"));
        meter.setEnergy(10f);
        meter.regenerate(1f);
        assertEquals(40f, meter.getEnergy(), 1e-4f);
    }

    @Test
    void theZoomAndTheFlashFollowTheirValues() {
        RailLance rail = new RailLance(TestTuning.weaponsWith("rail.zoom_factor", "4"));
        for (int i = 0; i < 60; i++) rail.update(1f / 60f, false, true, 0);
        assertEquals(0.25f, rail.getZoomFovScale(), 1e-5f);

        rail = new RailLance(TestTuning.weaponsWith("rail.zoom_speed", "1"));
        rail.update(0.5f, false, true, 0);
        assertEquals(0.5f, rail.getZoom(), 1e-5f);

        Pistol pistol = new Pistol(TestTuning.weaponsWith("pistol.flash_time", "0.5"));
        assertTrue(pistol.update(1f / 60f, true));
        pistol.update(0.3f, false);
        assertTrue(pistol.isFlashVisible(), "the shipped 0.07 would be over");
    }

    private static String error(String key, String value) {
        String text = TestTuning.withValue(AssetText.read("data", WeaponTuning.PATH), key, value);
        return assertThrows(IllegalArgumentException.class, () -> WeaponTuning.parse(WeaponTuning.PATH, text))
            .getMessage();
    }

    @Test
    void aMissingUnknownOrInvalidValueFailsNamingTheFileAndTheKey() {
        assertEquals("Missing 'pistol.damage' key in data/weapons.txt", error("pistol.damage", null));
        assertEquals("Missing 'scattergun.primary.ring' key in data/weapons.txt",
            error("scattergun.primary.ring", null));

        String unknown = assertThrows(IllegalArgumentException.class, () -> WeaponTuning.parse(
            WeaponTuning.PATH, "pistol.dammage: 20\n" + AssetText.read("data", WeaponTuning.PATH))).getMessage();
        assertEquals("Unknown key 'pistol.dammage' in data/weapons.txt at line 1", unknown);

        String twice = assertThrows(IllegalArgumentException.class, () -> WeaponTuning.parse(
            WeaponTuning.PATH, AssetText.read("data", WeaponTuning.PATH) + "rail.damage: 1\n")).getMessage();
        assertTrue(twice.startsWith("Duplicate 'rail.damage' key in data/weapons.txt at line "), twice);

        for (String[] bad : new String[][] {
            {"pistol.fire_interval", "0"}, {"pistol.fire_interval", "0.3f"}, {"pistol.damage", "-1"},
            {"pistol.range", "0"}, {"pistol.recoil_kick", "0"}, {"pistol.flash_time", ""},
            {"scattergun.max_shells", "0"}, {"scattergun.max_shells", "24.5"},
            {"scattergun.weapon_pickup_shells", "-1"}, {"scattergun.primary.interval", "0"},
            {"scattergun.primary.pump_time", "0"}, {"scattergun.range", "7"},
            {"scattergun.min_falloff_scale", "1.5"}, {"scattergun.pump_pull_share", "1"},
            {"scattergun.primary.ring", "0, 4.2, 0"}, {"scattergun.primary.ring", "2.5, 4.2, 0"},
            {"scattergun.primary.ring", "7, 90, 0"}, {"scattergun.primary.ring", "7, 4.2"},
            {"scattergun.primary.ring", "seven, 4.2, 0"},
            {"rail.max_slugs", "0"}, {"rail.fire_interval", "0"}, {"rail.bolt_time", "0"},
            {"rail.bolt_pull_share", "0"}, {"rail.zoom_factor", "0"}, {"rail.zoom_speed", "-1"},
            {"blast.full_charge_time", "0"}, {"blast.min_charge", "0"}, {"blast.min_charge", "1"},
            {"blast.max_cost", "20"}, {"blast.max_radius", "0.5"}, {"blast.max_damage", "10"},
            {"energy.capacity", "0"}, {"energy.regen", "-1"}, {"energy.regen", "NaN"},
        }) {
            String message = error(bad[0], bad[1]);
            assertTrue(message.contains("data/weapons.txt") && message.contains("'" + bad[0] + "'")
                && message.contains(" at line "), bad[0] + ": " + bad[1] + " gave: " + message);
        }
    }

    /** A pickup's scaled amount is floored at 1, so a pickup of nothing could never be had. */
    @Test
    void anAmmunitionPickupHoldsAtLeastOneRound() {
        List<org.junit.jupiter.api.function.Executable> checks = new ArrayList<>();
        for (String key : new String[] {"scattergun.weapon_pickup_shells", "scattergun.box_pickup_shells",
            "rail.weapon_pickup_slugs", "rail.pack_pickup_slugs"}) {
            checks.add(() -> {
                String message = error(key, "0");
                assertTrue(message.startsWith("Invalid '" + key + "' in data/weapons.txt at line ")
                    && message.endsWith(": must be at least 1, got '0'"), message);
            });
        }
        assertAll(checks);
        assertEquals(1, TestTuning.weaponsWith("scattergun.weapon_pickup_shells", "1").scattergunWeaponPickupShells);
        assertEquals(1, TestTuning.weaponsWith("scattergun.box_pickup_shells", "1").scattergunBoxPickupShells);
        assertEquals(1, TestTuning.weaponsWith("rail.weapon_pickup_slugs", "1").railWeaponPickupSlugs);
        assertEquals(1, TestTuning.weaponsWith("rail.pack_pickup_slugs", "1").railPackPickupSlugs);
    }

    /** A factor below 1 would widen the view where the optic is meant to narrow it. */
    @Test
    void theZoomFactorIsAtLeastOne() {
        String message = error("rail.zoom_factor", "0.5");
        assertTrue(message.startsWith("Invalid 'rail.zoom_factor' in data/weapons.txt at line ")
            && message.endsWith(": must be at least 1, got '0.5'"), message);
        assertEquals(1f, TestTuning.weaponsWith("rail.zoom_factor", "1").railZoomFactor, 0f);
    }

    /** A meter that cannot hold the cost of a dash or of the smallest blast would refuse them for ever. */
    @Test
    void theEnergyMeterHoldsADashAndTheSmallestBlast() {
        String shipped = AssetText.read("data", WeaponTuning.PATH);
        assertAll(
            () -> {
                String message = error("energy.capacity", "24");
                assertTrue(message.startsWith("Invalid 'energy.capacity' in data/weapons.txt at line ")
                    && message.endsWith(": must be at least 25.0, the cost of a dash, got '24'"), message);
            },
            () -> {
                // The shipped smallest blast costs less than a dash, so this case raises it above one first.
                String text = TestTuning.withValue(TestTuning.withValue(shipped, "blast.min_cost", "40"),
                    "energy.capacity", "30");
                String message = assertThrows(IllegalArgumentException.class,
                    () -> WeaponTuning.parse(WeaponTuning.PATH, text)).getMessage();
                assertTrue(message.startsWith("Invalid 'energy.capacity' in data/weapons.txt at line ")
                    && message.endsWith(": must be at least blast.min_cost, got '30'"), message);
            });
        assertEquals(25f, TestTuning.weaponsWith("energy.capacity", "25").energyCapacity, 0f);
        String both = TestTuning.withValue(TestTuning.withValue(shipped, "blast.min_cost", "40"),
            "energy.capacity", "40");
        assertEquals(40f, WeaponTuning.parse(WeaponTuning.PATH, both).energyCapacity, 0f);
    }

    @Test
    void theWeaponsNeedTheirTuning() {
        assertThrows(IllegalArgumentException.class, () -> new Pistol(null));
        assertThrows(IllegalArgumentException.class, () -> new Scattergun(null));
        assertThrows(IllegalArgumentException.class, () -> new RailLance(null));
        assertThrows(IllegalArgumentException.class, () -> new EnergyMeter(null));
        assertThrows(IllegalArgumentException.class, () -> new BlastCharger(new EnergyMeter(W), null));
        assertThrows(IllegalArgumentException.class,
            () -> new BlastProjectile(new Vector3(), new Vector3(1f, 0f, 0f), 1f, null));
    }
}

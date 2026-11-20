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

/**
 * Checks the weapon tuning file: that each key sets its own value, that the weapons work by the loaded values, what
 * a changed value does, and its errors.
 */
class WeaponTuningTest {

    private static final WeaponTuning W = TestTuning.WEAPONS;
    /** The test's own file, in which every number differs from the shipped one and from every other. */
    private static final String FIXTURE = "data-fixtures/weapons.txt";

    private static void assertRing(WeaponTuning.Ring ring, int count, float coneDegrees, float twistDegrees) {
        assertEquals(count, ring.count);
        assertEquals(coneDegrees, ring.coneDegrees, 0f);
        assertEquals(twistDegrees, ring.twistDegrees, 0f);
    }

    /**
     * The values on the right are those of the test's own file, where no two numbers are alike, so a key read into
     * another key's field, or a value still held in code, shows.
     */
    @Test
    void everyKeyOfAWeaponFileSetsItsOwnValue() {
        WeaponTuning t = WeaponTuning.parse(FIXTURE, AssetText.read("data", FIXTURE));
        assertEquals(0.306f, t.pistolFireInterval, 0f);
        assertEquals(21f, t.pistolDamage, 0f);
        assertEquals(61f, t.pistolRange, 0f);
        assertEquals(0.0721f, t.pistolFlashTime, 0f);
        assertEquals(2f, t.pistolRecoilKick, 0f);

        assertEquals(25, t.scattergunMaxShells);
        assertEquals(9, t.scattergunWeaponPickupShells);
        assertEquals(7, t.scattergunBoxPickupShells);
        assertEquals(1 + 8, t.scattergunPrimaryPellets);
        assertEquals(0.7875f, t.scattergunPrimaryInterval, 0f);
        assertEquals(1.696f, t.scattergunPrimaryRecoilKick, 0f);
        assertEquals(0.0856f, t.scattergunPrimaryPumpDelay, 0f);
        assertEquals(0.5184f, t.scattergunPrimaryPumpTime, 0f);
        assertEquals(1 + 6 + 11, t.scattergunAltPellets);
        assertEquals(1.2765f, t.scattergunAltInterval, 0f);
        assertEquals(2.688f, t.scattergunAltRecoilKick, 0f);
        assertEquals(0.1356f, t.scattergunAltPumpDelay, 0f);
        assertEquals(0.7752f, t.scattergunAltPumpTime, 0f);
        assertEquals(13f, t.scattergunPelletDamage, 0f);
        assertEquals(10f, t.scattergunFalloffStart, 0f);
        assertEquals(27f, t.scattergunRange, 0f);
        assertEquals(0.4025f, t.scattergunMinFalloffScale, 0f);
        assertEquals(0.1044f, t.scattergunFlashTime, 0f);
        assertEquals(0.4095f, t.scattergunPumpPullShare, 0f);

        assertEquals(16, t.railMaxSlugs);
        assertEquals(12, t.railWeaponPickupSlugs);
        assertEquals(4, t.railPackPickupSlugs);
        assertEquals(121f, t.railDamage, 0f);
        assertEquals(62f, t.railRange, 0f);
        assertEquals(1.298f, t.railFireInterval, 0f);
        assertEquals(0.119f, t.railFlashTime, 0f);
        assertEquals(0.048f, t.railBoltDelay, 0f);
        assertEquals(0.8228f, t.railBoltTime, 0f);
        assertEquals(0.305f, t.railBoltPullShare, 0f);
        assertEquals(2.214f, t.railZoomFactor, 0f);
        assertEquals(7.575f, t.railZoomSpeed, 0f);
        assertEquals(2.652f, t.railRecoilKick, 0f);

        assertEquals(1.236f, t.blastFullChargeTime, 0f);
        assertEquals(0.26f, t.blastMinCharge, 0f);
        assertEquals(22f, t.blastMinCost, 0f);
        assertEquals(63f, t.blastMaxCost, 0f);
        assertEquals(5f, t.blastMinRadius, 0f);
        assertEquals(14f, t.blastMaxRadius, 0f);
        assertEquals(31f, t.blastMinDamage, 0f);
        assertEquals(131f, t.blastMaxDamage, 0f);
        assertEquals(15f, t.blastRecoilKick, 0f);

        assertEquals(101f, t.energyCapacity, 0f);
        assertEquals(17f, t.energyRegen, 0f);
    }

    @Test
    void everyRingRowOfAWeaponFileIsARingOfItsPattern() {
        WeaponTuning t = WeaponTuning.parse(FIXTURE, AssetText.read("data", FIXTURE));
        assertEquals(1, t.scattergunPrimaryRings().size());
        assertRing(t.scattergunPrimaryRings().get(0), 8, 4.368f, 1f);
        assertEquals(2, t.scattergunAltRings().size());
        assertRing(t.scattergunAltRings().get(0), 6, 3.706f, 3f);
        assertRing(t.scattergunAltRings().get(1), 11, 7.48f, 19f);
        assertThrows(UnsupportedOperationException.class, () -> t.scattergunAltRings().remove(0));
    }

    /** Whatever values the shipped file holds, it loads, and each shot has a ring and so more than one pellet. */
    @Test
    void theShippedFileLoads() {
        WeaponTuning shipped = WeaponTuning.load();
        assertFalse(shipped.scattergunPrimaryRings().isEmpty());
        assertFalse(shipped.scattergunAltRings().isEmpty());
        assertTrue(shipped.scattergunPrimaryPellets > 1 && shipped.scattergunAltPellets > 1);
        assertTrue(shipped.pistolFireInterval > 0f && shipped.railFireInterval > 0f);
    }

    /**
     * The pattern of a tuning, whatever its rings: one pellet down the aim line, then each ring's pellets at that
     * ring's cone angle from it, the first of a ring turned by its twist from straight to the right.
     */
    private static void assertPatternFollowsTheRings(WeaponTuning tuning) {
        Scattergun gun = new Scattergun(tuning);
        Vector3[] aims = {
            new Vector3(0f, 0f, -1f), new Vector3(1f, 0f, 0f), new Vector3(0.4f, -0.15f, -0.9f),
            new Vector3(-0.31f, 0.22f, 0.77f).nor(), new Vector3(3f, 1f, -2f), new Vector3(0.6f, -0.64f, 0.48f),
        };
        for (Vector3 aim : aims) {
            for (boolean doubleBlast : new boolean[] {false, true}) {
                List<WeaponTuning.Ring> rings =
                    doubleBlast ? tuning.scattergunAltRings() : tuning.scattergunPrimaryRings();
                List<Vector3> pellets = gun.pelletDirections(aim, doubleBlast);
                String where = aim + (doubleBlast ? " double" : " primary");
                assertEquals(doubleBlast ? tuning.scattergunAltPellets : tuning.scattergunPrimaryPellets,
                    pellets.size(), where);
                Vector3 centre = new Vector3(aim).nor();
                Vector3 right = new Vector3(centre).crs(Vector3.Y).nor();
                Vector3 up = new Vector3(right).crs(centre).nor();
                assertEquals(1f, pellets.get(0).dot(centre), 1e-6f, where);
                int next = 1;
                for (WeaponTuning.Ring ring : rings) {
                    // A pellet is the aim line plus tan(cone) to the side, made a unit vector again.
                    double tan = Math.tan(Math.toRadians(ring.coneDegrees));
                    double length = Math.sqrt(1.0 + tan * tan);
                    for (int i = 0; i < ring.count; i++) {
                        Vector3 pellet = pellets.get(next++);
                        String which = where + " pellet " + i + " of a ring of " + ring.count;
                        double turn = Math.toRadians(ring.twistDegrees) + i * (2.0 * Math.PI / ring.count);
                        assertEquals(1f, pellet.len(), 1e-5f, which);
                        assertEquals(1.0 / length, pellet.dot(centre), 1e-5, which);
                        assertEquals(Math.cos(turn) * tan / length, pellet.dot(right), 1e-5, which);
                        assertEquals(Math.sin(turn) * tan / length, pellet.dot(up), 1e-5, which);
                    }
                }
                assertEquals(pellets.size(), next, where);
            }
        }
        // An aim with no direction is taken as straight ahead, and one straight up still has a right-hand side.
        assertEquals(new Vector3(0f, 0f, -1f), gun.pelletDirections(new Vector3(0f, 0f, 0f), false).get(0));
        assertEquals(new Vector3(0f, 0f, -1f), gun.pelletDirections(new Vector3(Float.NaN, 0f, -1f), true).get(0));
        for (Vector3 pellet : gun.pelletDirections(new Vector3(0f, 1f, 0f), true)) {
            assertTrue(Float.isFinite(pellet.x) && Float.isFinite(pellet.y) && Float.isFinite(pellet.z));
        }
    }

    @Test
    void thePelletPatternIsACentrePelletAndTheLoadedRings() {
        assertPatternFollowsTheRings(W);
        assertPatternFollowsTheRings(WeaponTuning.load());
        assertPatternFollowsTheRings(WeaponTuning.parse(FIXTURE, AssetText.read("data", FIXTURE)));
    }

    /** The explosion and the cost grow in a straight line between the loaded values, and the cost stays in them. */
    private static void assertBlastFollowsItsValues(WeaponTuning t) {
        assertEquals(t.blastMinRadius, t.blastRadius(0f), 0f);
        assertEquals(t.blastMaxRadius, t.blastRadius(1f), 1e-4f);
        assertEquals(t.blastMinDamage, t.blastDamage(0f), 0f);
        assertEquals(t.blastMaxDamage, t.blastDamage(1f), 1e-3f);
        assertEquals(t.blastMinCost, t.blastCost(t.blastMinCharge), 0f);
        assertEquals(t.blastMaxCost, t.blastCost(1f), 1e-3f);
        for (int i = -40; i <= 440; i++) {
            float charge = i / 400f;
            assertEquals(t.blastMinRadius + (t.blastMaxRadius - t.blastMinRadius) * charge, t.blastRadius(charge),
                1e-3f);
            assertEquals(t.blastMinDamage + (t.blastMaxDamage - t.blastMinDamage) * charge, t.blastDamage(charge),
                1e-2f);
            float share = Math.max(0f, Math.min(1f, (charge - t.blastMinCharge) / (1f - t.blastMinCharge)));
            assertEquals(t.blastMinCost + (t.blastMaxCost - t.blastMinCost) * share, t.blastCost(charge), 1e-2f);
        }
    }

    @Test
    void theBlastGrowsInAStraightLineBetweenTheLoadedValues() {
        assertBlastFollowsItsValues(W);
        assertBlastFollowsItsValues(WeaponTuning.load());
        assertBlastFollowsItsValues(WeaponTuning.parse(FIXTURE, AssetText.read("data", FIXTURE)));
    }

    private static void assertPelletDamageFallsOffByItsValues(WeaponTuning t) {
        Scattergun gun = new Scattergun(t);
        float start = t.scattergunFalloffStart;
        float range = t.scattergunRange;
        float full = t.scattergunPelletDamage;
        float least = full * t.scattergunMinFalloffScale;
        assertEquals(full, gun.damageAtDistance(0f), 0f);
        assertEquals(full, gun.damageAtDistance(start), 0f);
        assertEquals(full, gun.damageAtDistance(Float.NaN), 0f);
        assertEquals(least, gun.damageAtDistance(range), 1e-4f);
        assertEquals(least, gun.damageAtDistance(range + 14f), 1e-4f);
        for (float share : new float[] {0.01f, 0.25f, 0.5f, 0.9f}) {
            assertEquals(full + (least - full) * share, gun.damageAtDistance(start + (range - start) * share), 1e-3f,
                "at " + share + " of the way");
        }
    }

    @Test
    void pelletDamageFallsOffBetweenTheLoadedDistances() {
        assertPelletDamageFallsOffByItsValues(W);
        assertPelletDamageFallsOffByItsValues(WeaponTuning.load());
        assertPelletDamageFallsOffByItsValues(WeaponTuning.parse(FIXTURE, AssetText.read("data", FIXTURE)));
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
        assertFalse(slow.isReady(), "the 0.75 of the tests' own file would be over");

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
        String text = TestTuning.text(WeaponTuning.PATH)
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
        assertTrue(charger.isRefused(),
            "30 energy pays for the smallest blast of the tests' own file, not for this one");

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
        assertTrue(pistol.isFlashVisible(), "the 0.07 of the tests' own file would be over");
    }

    private static String error(String key, String value) {
        String text = TestTuning.withValue(TestTuning.text(WeaponTuning.PATH), key, value);
        return assertThrows(IllegalArgumentException.class, () -> WeaponTuning.parse(WeaponTuning.PATH, text))
            .getMessage();
    }

    @Test
    void aMissingUnknownOrInvalidValueFailsNamingTheFileAndTheKey() {
        assertEquals("Missing 'pistol.damage' key in data/weapons.txt", error("pistol.damage", null));
        assertEquals("Missing 'scattergun.primary.ring' key in data/weapons.txt",
            error("scattergun.primary.ring", null));

        String unknown = assertThrows(IllegalArgumentException.class, () -> WeaponTuning.parse(
            WeaponTuning.PATH, "pistol.dammage: 20\n" + TestTuning.text(WeaponTuning.PATH))).getMessage();
        assertEquals("Unknown key 'pistol.dammage' in data/weapons.txt at line 1", unknown);

        String twice = assertThrows(IllegalArgumentException.class, () -> WeaponTuning.parse(
            WeaponTuning.PATH, TestTuning.text(WeaponTuning.PATH) + "rail.damage: 1\n")).getMessage();
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
        String shipped = TestTuning.text(WeaponTuning.PATH);
        assertAll(
            () -> {
                String message = error("energy.capacity", "24");
                assertTrue(message.startsWith("Invalid 'energy.capacity' in data/weapons.txt at line ")
                    && message.endsWith(": must be at least 25.0, the cost of a dash, got '24'"), message);
            },
            () -> {
                // The smallest blast of the tests' own file costs less than a dash, so this case raises it above
                // one first.
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

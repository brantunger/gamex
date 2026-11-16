package io.github.brantunger.gamex.weapon;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.Test;

class BlastChargerTest {

    private static final float EPSILON = 1e-3f;
    private static final float REGEN = 10f;

    private final EnergyMeter energy = new EnergyMeter(100f, REGEN);
    private final BlastCharger charger = new BlastCharger(energy);

    /** Holds the button for a time in small steps, as frames would. */
    private void hold(float seconds) {
        int steps = Math.round(seconds / 0.01f);
        for (int i = 0; i < steps; i++) assertEquals(0f, charger.update(0.01f, true), "nothing fires while held");
    }

    @Test
    void chargeBuildsWhileTheButtonIsHeld() {
        hold(BlastCharger.FULL_CHARGE_TIME / 2f);

        assertTrue(charger.isCharging());
        assertEquals(0.5f, charger.getCharge(), EPSILON);
    }

    @Test
    void chargeStopsAtFull() {
        hold(BlastCharger.FULL_CHARGE_TIME * 3f);

        assertEquals(1f, charger.getCharge(), EPSILON);
    }

    @Test
    void releaseFiresTheChargeAndSpendsItsCost() {
        hold(BlastCharger.FULL_CHARGE_TIME / 2f);

        float fired = charger.update(0.01f, false);

        // Half charge is a third of the way from the minimum to full: 20 + 40 / 3.
        assertEquals(0.5f, fired, EPSILON);
        assertEquals(100f - 33.333f, energy.getEnergy(), 0.05f);
        assertFalse(charger.isCharging());
        assertEquals(0f, charger.getCharge(), EPSILON);
    }

    @Test
    void fullChargeCostsTheMaximum() {
        hold(BlastCharger.FULL_CHARGE_TIME * 2f);

        assertEquals(1f, charger.update(0.01f, false), EPSILON);
        assertEquals(100f - BlastCharger.MAX_COST, energy.getEnergy(), EPSILON);
    }

    @Test
    void releaseBelowTheMinimumChargeFizzlesAndCostsNothing() {
        hold(BlastCharger.FULL_CHARGE_TIME * BlastCharger.MIN_CHARGE * 0.8f);
        assertFalse(charger.isReadyToFire());

        assertEquals(0f, charger.update(0.01f, false));
        assertEquals(100f, energy.getEnergy(), EPSILON);
    }

    @Test
    void releaseJustAtTheMinimumChargeFiresAtTheMinimumCost() {
        // One step that carries the charge a hair past the minimum.
        charger.update(BlastCharger.FULL_CHARGE_TIME * BlastCharger.MIN_CHARGE + 1e-4f, true);
        assertTrue(charger.isReadyToFire());

        float fired = charger.update(0.01f, false);

        assertEquals(BlastCharger.MIN_CHARGE, fired, 2e-4f);
        assertEquals(100f - BlastCharger.MIN_COST, energy.getEnergy(), 0.01f);
        assertEquals(BlastCharger.MIN_COST, BlastCharger.costFor(BlastCharger.MIN_CHARGE), 0f);
    }

    @Test
    void releaseJustBelowTheMinimumChargeFizzles() {
        charger.update(BlastCharger.FULL_CHARGE_TIME * BlastCharger.MIN_CHARGE - 1e-4f, true);
        assertFalse(charger.isReadyToFire());

        assertEquals(0f, charger.update(0.01f, false));
        assertEquals(100f, energy.getEnergy(), 0f);
    }

    @Test
    void cancelDropsTheChargeWithoutFiringOrSpending() {
        hold(BlastCharger.FULL_CHARGE_TIME);
        assertTrue(charger.isReadyToFire());

        charger.cancel();

        assertFalse(charger.isCharging());
        assertEquals(0f, charger.getCharge());
        assertEquals(0f, charger.update(0.01f, false), "the button was let go during the pause: nothing fires");
        assertEquals(100f, energy.getEnergy(), 0f);
    }

    @Test
    void chargeStartsFromZeroAgainAfterACancelWithTheButtonStillHeld() {
        hold(BlastCharger.FULL_CHARGE_TIME);
        charger.cancel();

        charger.update(0.12f, true);

        assertTrue(charger.isCharging());
        assertEquals(0.1f, charger.getCharge(), EPSILON);
    }

    @Test
    void cannotStartChargingWithoutEnergyForTheSmallestBlast() {
        energy.spend(100f - BlastCharger.MIN_COST + 5f);

        float fired = charger.update(0.01f, true);

        assertEquals(0f, fired);
        assertFalse(charger.isCharging());
        assertTrue(charger.isRefused());
        assertEquals(0f, charger.update(0.01f, false), "releasing a refused charge fires nothing");
        assertFalse(charger.isRefused());
    }

    @Test
    void chargeIsCappedAtWhatTheMeterCanPayFor() {
        // 40 energy pays for a blast halfway between the minimum (20) and the maximum (60) cost.
        energy.spend(60f);

        hold(BlastCharger.FULL_CHARGE_TIME * 2f);
        float fired = charger.update(0.01f, false);

        assertEquals(0.625f, fired, EPSILON);
        assertEquals(0f, energy.getEnergy(), EPSILON);
    }

    @Test
    void meterRegeneratesWhileIdleButNotWhileCharging() {
        energy.spend(50f);

        hold(0.5f);
        assertEquals(50f, energy.getEnergy(), EPSILON, "no regeneration while charging");
        charger.update(0.01f, false);
        float afterFiring = energy.getEnergy();

        for (int i = 0; i < 100; i++) charger.update(0.01f, false);

        assertEquals(afterFiring + REGEN, energy.getEnergy(), 0.01f);
    }

    @Test
    void firesOnlyOncePerRelease() {
        hold(BlastCharger.FULL_CHARGE_TIME);

        assertTrue(charger.update(0.01f, false) > 0f);
        assertEquals(0f, charger.update(0.01f, false));
    }
}

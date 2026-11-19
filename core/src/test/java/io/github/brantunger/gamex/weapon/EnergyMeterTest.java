package io.github.brantunger.gamex.weapon;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.Test;

class EnergyMeterTest {

    private static final float EPSILON = 1e-4f;

    @Test
    void startsFull() {
        EnergyMeter meter = new EnergyMeter(80f, 10f);

        assertEquals(80f, meter.getEnergy(), EPSILON);
        assertEquals(80f, meter.getCapacity(), EPSILON);
        assertEquals(1f, meter.getFraction(), EPSILON);
    }

    @Test
    void spendingTakesEnergyOut() {
        EnergyMeter meter = new EnergyMeter(80f, 10f);

        assertTrue(meter.spend(30f));

        assertEquals(50f, meter.getEnergy(), EPSILON);
        assertEquals(0.625f, meter.getFraction(), EPSILON);
    }

    @Test
    void spendingMoreThanIsThereIsRefusedAndTakesNothing() {
        EnergyMeter meter = new EnergyMeter(80f, 10f);
        meter.spend(70f);

        assertFalse(meter.canSpend(10.5f));
        assertFalse(meter.spend(10.5f));

        assertEquals(10f, meter.getEnergy(), EPSILON);
        assertTrue(meter.spend(10f), "exactly what is left can be spent");
    }

    @Test
    void regeneratesAtItsRate() {
        EnergyMeter meter = new EnergyMeter(80f, 10f);
        meter.spend(60f);

        meter.regenerate(2.5f);

        assertEquals(45f, meter.getEnergy(), EPSILON);
    }

    @Test
    void regenerationStopsAtTheCapacity() {
        EnergyMeter meter = new EnergyMeter(80f, 10f);
        meter.spend(5f);

        meter.regenerate(100f);

        assertEquals(80f, meter.getEnergy(), EPSILON);
    }

    @Test
    void addRestoresUpToCapacityAndIgnoresNonPositiveAmounts() {
        EnergyMeter meter = new EnergyMeter(100f, 10f);
        meter.spend(60f);

        assertEquals(0f, meter.add(-10f), EPSILON);
        assertEquals(35f, meter.add(35f), EPSILON);
        assertEquals(75f, meter.getEnergy(), EPSILON);
        assertEquals(25f, meter.add(50f), EPSILON);
        assertEquals(100f, meter.getEnergy(), EPSILON);
    }

    @Test
    void rejectsNonFiniteAndNegativeInputsAcrossSpendRegenerateAddAndSetEnergy() {
        EnergyMeter meter = new EnergyMeter(100f, 10f);
        assertTrue(meter.spend(40f));
        assertEquals(60f, meter.getEnergy(), EPSILON);

        for (float invalidSpend : new float[] {
            Float.NaN, Float.POSITIVE_INFINITY, Float.NEGATIVE_INFINITY, -0.01f, -25f, 60.5f
        }) {
            assertFalse(meter.canSpend(invalidSpend), "canSpend must reject " + invalidSpend);
            assertFalse(meter.spend(invalidSpend), "spend must reject " + invalidSpend);
            assertEquals(60f, meter.getEnergy(), EPSILON);
        }

        assertTrue(meter.canSpend(0f));
        assertTrue(meter.spend(0f));
        assertEquals(60f, meter.getEnergy(), EPSILON);

        for (float invalidDt : new float[] {
            Float.NaN, Float.POSITIVE_INFINITY, Float.NEGATIVE_INFINITY, 0f, -2f
        }) {
            meter.regenerate(invalidDt);
            assertEquals(60f, meter.getEnergy(), EPSILON, "regenerate must ignore " + invalidDt);
        }

        for (float invalidAdd : new float[] {
            Float.NaN, Float.POSITIVE_INFINITY, Float.NEGATIVE_INFINITY, 0f, -10f
        }) {
            assertEquals(0f, meter.add(invalidAdd), EPSILON);
            assertEquals(60f, meter.getEnergy(), EPSILON);
        }

        meter.setEnergy(Float.NaN);
        assertEquals(60f, meter.getEnergy(), EPSILON, "setEnergy(NaN) must not corrupt energy");

        meter.setEnergy(Float.NEGATIVE_INFINITY);
        assertEquals(0f, meter.getEnergy(), EPSILON);

        meter.setEnergy(Float.POSITIVE_INFINITY);
        assertEquals(100f, meter.getEnergy(), EPSILON);
    }
}

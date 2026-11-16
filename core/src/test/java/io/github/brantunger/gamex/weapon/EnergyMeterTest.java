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
}

package com.gamex.logic;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.Test;

class HealthTest {

    @Test
    void startsAtMax() {
        Health health = new Health(10);

        assertEquals(10, health.current());
        assertFalse(health.isDead());
    }

    @Test
    void damageStopsAtZero() {
        Health health = new Health(10);

        health.damage(25);

        assertEquals(0, health.current());
        assertTrue(health.isDead());
    }

    @Test
    void healStopsAtMax() {
        Health health = new Health(10);
        health.damage(4);

        health.heal(100);

        assertEquals(10, health.current());
    }

    @Test
    void healDoesNotReviveTheDead() {
        Health health = new Health(10);
        health.damage(10);

        health.heal(5);

        assertTrue(health.isDead());
    }

    @Test
    void rejectsNegativeAmountsAndNonPositiveMax() {
        Health health = new Health(10);

        assertThrows(IllegalArgumentException.class, () -> health.damage(-1));
        assertThrows(IllegalArgumentException.class, () -> health.heal(-1));
        assertThrows(IllegalArgumentException.class, () -> new Health(0));
    }
}

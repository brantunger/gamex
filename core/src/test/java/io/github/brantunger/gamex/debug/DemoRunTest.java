package io.github.brantunger.gamex.debug;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertNull;

import org.junit.jupiter.api.Test;

class DemoRunTest {

    @Test
    void orbitIsAYawWithAnOptionalPitch() {
        assertArrayEquals(new float[] {30f, 0f}, DemoRun.parseOrbit("30"), 1e-6f);
        assertArrayEquals(new float[] {30f, 15f}, DemoRun.parseOrbit("30,15"), 1e-6f);
        assertArrayEquals(new float[] {-90f, 22.5f}, DemoRun.parseOrbit(" -90 , 22.5 "), 1e-6f);
    }

    @Test
    void missingOrbitIsNoOrbit() {
        assertNull(DemoRun.parseOrbit(null));
        assertNull(DemoRun.parseOrbit(""));
    }

    @Test
    void malformedOrbitIsNoOrbit() {
        assertNull(DemoRun.parseOrbit("abc"));
        assertNull(DemoRun.parseOrbit("30,abc"));
        assertNull(DemoRun.parseOrbit(" "));
        assertNull(DemoRun.parseOrbit(",5"));
        assertNull(DemoRun.parseOrbit("30, "));
    }

    @Test
    void orbitMadeOnlyOfCommasIsNoOrbitRatherThanACrash() {
        // Splitting such text leaves no parts at all.
        assertNull(DemoRun.parseOrbit(","));
        assertNull(DemoRun.parseOrbit(",,"));
    }
}

package io.github.brantunger.gamex.input;

import static org.junit.jupiter.api.Assertions.assertEquals;

import org.junit.jupiter.api.Test;

class LookAccumulatorTest {

    @Test
    void firstEventAfterCaptureOnlySetsTheStartingPoint() {
        LookAccumulator look = new LookAccumulator();
        look.capture();

        // The cursor is in the middle of a 1280x720 window when the first event arrives.
        look.moved(640, 360);

        assertEquals(0, look.consumeX());
        assertEquals(0, look.consumeY());
    }

    @Test
    void everyEventOfAFrameCounts() {
        LookAccumulator look = new LookAccumulator();
        look.capture();
        look.moved(640, 360);

        look.moved(650, 357);
        look.moved(655, 361);
        look.moved(690, 340);

        assertEquals(50, look.consumeX());
        assertEquals(-20, look.consumeY());
    }

    @Test
    void consumingStartsTheNextFrameFromZeroButKeepsTheCursorPosition() {
        LookAccumulator look = new LookAccumulator();
        look.capture();
        look.moved(100, 100);
        look.moved(130, 90);
        assertEquals(30, look.consumeX());
        assertEquals(-10, look.consumeY());

        assertEquals(0, look.consumeX(), "nothing moved since");
        assertEquals(0, look.consumeY());

        look.moved(125, 96);
        assertEquals(-5, look.consumeX());
        assertEquals(6, look.consumeY());
    }

    @Test
    void movementWhileReleasedIsIgnoredAndDoesNotJumpTheViewOnRecapture() {
        LookAccumulator look = new LookAccumulator();
        look.capture();
        look.moved(100, 100);
        look.moved(110, 100);
        look.release();

        // Paused: the cursor wanders right across the window.
        look.moved(900, 600);
        look.moved(1200, 50);
        assertEquals(0, look.consumeX(), "released movement and the unconsumed rest are dropped");

        look.capture();
        look.moved(640, 360);
        assertEquals(0, look.consumeX(), "the first event after the new capture is the new starting point");
        assertEquals(0, look.consumeY());

        look.moved(644, 362);
        assertEquals(4, look.consumeX());
        assertEquals(2, look.consumeY());
    }

    @Test
    void nothingIsCountedBeforeTheFirstCapture() {
        LookAccumulator look = new LookAccumulator();

        look.moved(10, 10);
        look.moved(500, 500);

        assertEquals(0, look.consumeX());
        assertEquals(0, look.consumeY());
    }
}

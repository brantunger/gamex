package io.github.brantunger.gamex.input;

/**
 * Adds up mouse movement between frames from the absolute cursor positions of every mouse event. Several events may
 * arrive within one frame, and all of them count. The first event after each capture only sets the starting point:
 * the cursor may be anywhere at that moment, and the jump to it is not a turn of the head. It has no graphics or
 * input-device types, so it can be tested on its own.
 */
public final class LookAccumulator {

    private boolean capturing;
    private boolean hasLast;
    private int lastX;
    private int lastY;
    private int pendingX;
    private int pendingY;

    /** Starts counting movement, from wherever the next event says the cursor is. */
    public void capture() {
        capturing = true;
        hasLast = false;
        pendingX = 0;
        pendingY = 0;
    }

    /** Stops counting movement and throws away what has not been consumed. */
    public void release() {
        capturing = false;
        hasLast = false;
        pendingX = 0;
        pendingY = 0;
    }

    /**
     * Takes in one mouse event.
     * @param x the cursor's absolute x in pixels
     * @param y the cursor's absolute y in pixels, growing downwards
     */
    public void moved(int x, int y) {
        if (!capturing) return;
        if (hasLast) {
            pendingX += x - lastX;
            pendingY += y - lastY;
        }
        hasLast = true;
        lastX = x;
        lastY = y;
    }

    /** @return the horizontal movement in pixels since the last call, positive to the right. */
    public int consumeX() {
        int x = pendingX;
        pendingX = 0;
        return x;
    }

    /** @return the vertical movement in pixels since the last call, positive downwards. */
    public int consumeY() {
        int y = pendingY;
        pendingY = 0;
        return y;
    }
}

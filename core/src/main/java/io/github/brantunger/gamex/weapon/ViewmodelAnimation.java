package io.github.brantunger.gamex.weapon;

/**
 * The motion of the first-person pistol: the kick and slide travel of each shot, the bob while walking and the lag
 * behind fast turns. It only produces numbers; the renderer turns them into a transform. It has no graphics types,
 * so it can be tested on its own.
 */
public final class ViewmodelAnimation {

    /** How long, in seconds, the gun takes to kick and settle after a shot; shorter than the fire interval. */
    public static final float RECOIL_TIME = 0.24f;
    /** How long, in seconds, the slide takes to snap back and return after a shot. */
    public static final float SLIDE_TIME = 0.13f;
    /** The furthest the bob moves the gun sideways, in view units. */
    public static final float BOB_WIDTH = 0.006f;
    /** The furthest the bob moves the gun up or down, in view units. */
    public static final float BOB_HEIGHT = 0.0035f;
    /** The furthest the gun lags behind a turn, in degrees. */
    public static final float MAX_SWAY = 2.5f;

    /** The share of a recoil or slide cycle spent snapping back; the rest is the return. */
    private static final float ATTACK = 0.15f;
    /** The bob's speed at a full walk, in radians per second. */
    private static final float BOB_RATE = 9f;
    /** How fast the bob fades in and out, per second. */
    private static final float BOB_FADE = 7f;
    /** The lag, in degrees, per degree-per-second of turn rate. */
    private static final float SWAY_PER_RATE = 0.006f;
    /** How fast the lag follows the turn rate, per second. */
    private static final float SWAY_FOLLOW = 9f;

    private float sinceShot = Float.POSITIVE_INFINITY;
    private float bobAmount;
    private float bobPhase;
    private float swayYaw;
    private float swayPitch;

    /**
     * Advances the animation by one step.
     * @param dt the time step in seconds
     * @param fired whether the pistol fired during this step
     * @param walkSpeed how fast the player is walking, from 0 (still) to 1 (full speed)
     * @param yawDelta the turn of the view during this step in degrees, positive to the right
     * @param pitchDelta the tilt of the view during this step in degrees, positive upwards
     */
    public void update(float dt, boolean fired, float walkSpeed, float yawDelta, float pitchDelta) {
        if (fired) sinceShot = 0f;
        else sinceShot += dt;
        if (dt <= 0f) return;

        float speed = Math.max(0f, Math.min(1f, walkSpeed));
        bobAmount += (speed - bobAmount) * Math.min(1f, dt * BOB_FADE);
        bobPhase += dt * BOB_RATE * speed;

        float follow = Math.min(1f, dt * SWAY_FOLLOW);
        swayYaw += (clampSway(-yawDelta / dt * SWAY_PER_RATE) - swayYaw) * follow;
        swayPitch += (clampSway(-pitchDelta / dt * SWAY_PER_RATE) - swayPitch) * follow;
    }

    private static float clampSway(float degrees) {
        return Math.max(-MAX_SWAY, Math.min(MAX_SWAY, degrees));
    }

    /** A pulse that rises quickly from 0 to 1 and eases back to 0 by the end of its duration. */
    private static float pulse(float time, float duration) {
        float progress = time / duration;
        if (progress >= 1f) return 0f;
        if (progress < ATTACK) return progress / ATTACK;
        float back = 1f - (progress - ATTACK) / (1f - ATTACK);
        return back * back;
    }

    /** @return the kick of the gun, from 0 at rest to 1 at the peak just after a shot. */
    public float getRecoil() {
        return pulse(sinceShot, RECOIL_TIME);
    }

    /** @return how far back the slide is, from 0 (closed) to 1 (fully back). */
    public float getSlide() {
        return pulse(sinceShot, SLIDE_TIME);
    }

    /** @return the sideways bob offset in view units, within {@link #BOB_WIDTH} either way; 0 when still. */
    public float getBobX() {
        return (float) Math.sin(bobPhase) * BOB_WIDTH * bobAmount;
    }

    /** @return the vertical bob offset in view units, within {@link #BOB_HEIGHT} either way; 0 when still. */
    public float getBobY() {
        // Twice the sideways rate, which draws a figure of eight.
        return (float) Math.sin(bobPhase * 2f) * BOB_HEIGHT * bobAmount;
    }

    /** @return the yaw lag in degrees, within {@link #MAX_SWAY}: negative (to the left) while turning right. */
    public float getSwayYaw() {
        return swayYaw;
    }

    /** @return the pitch lag in degrees, within {@link #MAX_SWAY}: negative (downwards) while tilting up. */
    public float getSwayPitch() {
        return swayPitch;
    }
}

package io.github.brantunger.gamex.player;

import com.badlogic.gdx.math.Vector2;
import com.badlogic.gdx.math.Vector3;
import io.github.brantunger.gamex.world.Collision;
import io.github.brantunger.gamex.world.Damageable;
import io.github.brantunger.gamex.world.GameMap;

import java.util.Collections;
import java.util.List;

/**
 * The player, "gamerx": a position on the floor plus a yaw and a pitch. The view direction is always derived from
 * those two angles, never accumulated, so it cannot drift or pick up roll.
 */
public final class Player {

    /** The height of the eyes above the floor. */
    public static final float EYE_HEIGHT = 0.5f;
    /** The collision radius on the XZ plane. */
    public static final float RADIUS = 0.25f;
    /** The walking speed in world units per second. */
    public static final float MOVE_SPEED = 3.2f;
    /** The steepest pitch, up or down, in degrees. */
    public static final float MAX_PITCH = 80f;
    /** How fast the walk bob cycles, in radians per world unit walked. */
    private static final float BOB_RATE = 5.5f;

    /** The position on the floor as (x, z). */
    public final Vector2 position = new Vector2();
    private float yaw;
    private float pitch;
    private float bobPhase;

    /**
     * @param x the starting world x
     * @param z the starting world z
     * @param yaw the starting yaw in degrees
     */
    public Player(float x, float z, float yaw) {
        position.set(x, z);
        this.yaw = yaw;
    }

    /** @return the yaw in degrees, from 0 up to 360: 0 faces north (negative z) and 90 faces east (positive x). */
    public float getYaw() {
        return yaw;
    }

    /** @return the pitch in degrees, positive looking up, within {@link #MAX_PITCH} either way. */
    public float getPitch() {
        return pitch;
    }

    /** @return the walk bob phase in radians, which advances with the distance walked. */
    public float getBobPhase() {
        return bobPhase;
    }

    /**
     * Turns the view.
     * @param yawDelta the change in yaw in degrees; positive turns right
     * @param pitchDelta the change in pitch in degrees; positive looks up
     */
    public void look(float yawDelta, float pitchDelta) {
        yaw = ((yaw + yawDelta) % 360f + 360f) % 360f;
        pitch = Math.max(-MAX_PITCH, Math.min(MAX_PITCH, pitch + pitchDelta));
    }

    /**
     * Computes the unit view direction from the yaw and pitch.
     * @param out the vector to fill
     * @return {@code out}
     */
    public Vector3 direction(Vector3 out) {
        double yawRad = Math.toRadians(yaw);
        double pitchRad = Math.toRadians(pitch);
        double flat = Math.cos(pitchRad);
        return out.set((float) (Math.sin(yawRad) * flat), (float) Math.sin(pitchRad),
            (float) (-Math.cos(yawRad) * flat));
    }

    /**
     * Computes the eye position.
     * @param out the vector to fill
     * @return {@code out}
     */
    public Vector3 eye(Vector3 out) {
        return out.set(position.x, EYE_HEIGHT, position.y);
    }

    /**
     * Walks relative to the yaw, sliding along walls.
     * @param map the map to collide with
     * @param forward forward movement from -1 to 1
     * @param strafe sideways movement from -1 to 1, positive to the right
     * @param dt the time step in seconds
     */
    public void move(GameMap map, float forward, float strafe, float dt) {
        move(map, Collections.<Damageable>emptyList(), forward, strafe, dt);
    }

    /**
     * Walks relative to the yaw, sliding along walls and round solid objects.
     * @param map the map to collide with
     * @param solids the objects that block the way while they are not destroyed
     * @param forward forward movement from -1 to 1
     * @param strafe sideways movement from -1 to 1, positive to the right
     * @param dt the time step in seconds
     */
    public void move(GameMap map, List<? extends Damageable> solids, float forward, float strafe, float dt) {
        float length = (float) Math.sqrt(forward * forward + strafe * strafe);
        if (length < 1e-4f) return;
        // Diagonal movement is no faster than straight movement.
        float scale = MOVE_SPEED * dt / Math.max(1f, length);
        double yawRad = Math.toRadians(yaw);
        float sin = (float) Math.sin(yawRad);
        float cos = (float) Math.cos(yawRad);
        float dx = (sin * forward + cos * strafe) * scale;
        float dz = (-cos * forward + sin * strafe) * scale;
        float beforeX = position.x;
        float beforeZ = position.y;
        Collision.move(map, solids, position, dx, dz, RADIUS);
        bobPhase += Vector2.dst(beforeX, beforeZ, position.x, position.y) * BOB_RATE;
    }
}

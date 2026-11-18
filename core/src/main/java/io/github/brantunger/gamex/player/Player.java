package io.github.brantunger.gamex.player;

import com.badlogic.gdx.math.Vector2;
import com.badlogic.gdx.math.Vector3;
import io.github.brantunger.gamex.weapon.EnergyMeter;
import io.github.brantunger.gamex.world.Collision;
import io.github.brantunger.gamex.world.Damageable;
import io.github.brantunger.gamex.world.Door;
import io.github.brantunger.gamex.world.GameMap;

import java.util.Collections;
import java.util.List;

/**
 * The player, "gamerx": a position on the floor plus vertical jump kinematics, a yaw and a pitch, health and armor,
 * a free physical jump, and an energy-powered horizontal dash. The view direction is always derived from yaw and pitch,
 * never accumulated, so it cannot drift or pick up roll.
 */
public final class Player {

    /** The height of the eyes above the floor while alive and grounded. */
    public static final float EYE_HEIGHT = 0.5f;
    /** Total standing body height from floor to crown, used for 3D projectile hitboxes. */
    public static final float BODY_HEIGHT = EYE_HEIGHT + 0.18f;
    /** The height the camera settles to when the player dies. */
    public static final float DEAD_EYE_HEIGHT = 0.18f;
    /** The collision radius on the XZ plane. */
    public static final float RADIUS = 0.25f;
    /** The walking speed in world units per second. */
    public static final float MOVE_SPEED = 3.2f;
    /** The steepest pitch, up or down, in degrees. */
    public static final float MAX_PITCH = 80f;

    /** Upward launch velocity of a free physical jump, in world units per second (~0.32u peak, ~0.48s airtime). */
    public static final float JUMP_VELOCITY = 2.68f;
    /** Downward gravity acceleration while airborne, in world units per second squared. */
    public static final float GRAVITY = 11.2f;
    /** Maximum feet elevation above the floor so the eye camera stays safely clear of the 1.0u ceiling. */
    public static final float MAX_JUMP_HEIGHT = GameMap.WALL_HEIGHT - EYE_HEIGHT - 0.14f;
    /** Forgiving pre-landing window during which an early jump press is queued for touchdown, in seconds. */
    public static final float JUMP_BUFFER_TIME = 0.10f;
    private static final float LANDING_DIP_TIME = 0.14f;
    private static final float LANDING_DIP_EYE = 0.026f;

    /** Maximum player health. */
    public static final float MAX_HEALTH = 100f;
    /** Maximum player armor. */
    public static final float MAX_ARMOR = 100f;
    /** Share of incoming damage absorbed by armor while armor remains. */
    public static final float ARMOR_ABSORPTION = 0.5f;
    /** Duration of the screen hurt flash after taking damage, in seconds. */
    public static final float HURT_FLASH_TIME = 0.35f;

    /** Energy cost of one horizontal dash. */
    public static final float DASH_COST = 25f;
    /** Duration of the dash burst, in seconds. */
    public static final float DASH_DURATION = 0.14f;
    /** Movement speed during a dash, in world units per second. */
    public static final float DASH_SPEED = 11.5f;
    /** Minimum time from the start of one dash to the start of the next, in seconds. */
    public static final float DASH_COOLDOWN = 0.35f;
    /** How long a refused dash flashes the low-energy warning, in seconds. */
    private static final float DASH_REFUSED_TIME = 0.45f;

    /** How fast the walk bob cycles, in radians per world unit walked. */
    private static final float BOB_RATE = 5.5f;
    /** How fast the camera drops to the floor on death, in world units per second. */
    private static final float DEATH_DROP_SPEED = 1.6f;

    /** The position on the floor as (x, z). */
    public final Vector2 position = new Vector2();
    private float yaw;
    private float pitch;
    private float bobPhase;
    private float currentEyeHeight = EYE_HEIGHT;

    private float health;
    private float armor;
    private float hurtFlash;
    private boolean directionalHurt;
    private float hurtRelativeYaw;

    private float dashRemaining;
    private float dashCooldown;
    private float dashRefusedTimer;
    private float dashDirX;
    private float dashDirZ;

    private float jumpHeight;
    private float verticalVelocity;
    private float jumpBufferTimer;
    private float landingDipTimer;
    private boolean jumpTriggered;
    private boolean landedTriggered;

    /**
     * @param x the starting world x
     * @param z the starting world z
     * @param yaw the starting yaw in degrees
     */
    public Player(float x, float z, float yaw) {
        this(x, z, yaw, MAX_HEALTH, 0f);
    }

    /**
     * @param x the starting world x
     * @param z the starting world z
     * @param yaw the starting yaw in degrees
     * @param startingHealth initial health, clamped to (0, {@link #MAX_HEALTH}]
     * @param startingArmor initial armor, clamped to [0, {@link #MAX_ARMOR}]
     */
    public Player(float x, float z, float yaw, float startingHealth, float startingArmor) {
        position.set(x, z);
        this.yaw = ((yaw % 360f) + 360f) % 360f;
        this.health = Math.max(1f, Math.min(MAX_HEALTH, startingHealth));
        this.armor = Math.max(0f, Math.min(MAX_ARMOR, startingArmor));
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

    /** @return remaining health, never below zero. */
    public float getHealth() {
        return health;
    }

    /** @return remaining armor, never below zero. */
    public float getArmor() {
        return armor;
    }

    /** @return whether the player's health has reached zero. */
    public boolean isDead() {
        return health <= 0f;
    }

    /** @return hurt flash intensity from 0 (none) to 1 (just hit). */
    public float getHurtFlash() {
        return hurtFlash / HURT_FLASH_TIME;
    }

    /** @return whether the most recent hit came from a distinct horizontal direction. */
    public boolean hasDirectionalHurt() {
        return directionalHurt;
    }

    /**
     * @return relative angle in degrees from the player's forward view to the damage source: 0 ahead, +90 right,
     *     -90 left, and +/-180 behind
     */
    public float getHurtRelativeYaw() {
        return hurtRelativeYaw;
    }

    /** @return whether the player is currently mid-dash. */
    public boolean isDashing() {
        return dashRemaining > 0f;
    }

    /** @return whether a recent dash attempt was refused because energy was below {@link #DASH_COST}. */
    public boolean isDashRefused() {
        return dashRefusedTimer > 0f;
    }

    /** @return current feet height above the floor in world units (0 when grounded). */
    public float getJumpHeight() {
        return jumpHeight;
    }

    /** @return current vertical velocity in world units per second (positive rising, negative falling). */
    public float getVerticalVelocity() {
        return verticalVelocity;
    }

    /** @return whether the player is currently off the floor or rising into a jump. */
    public boolean isAirborne() {
        return jumpHeight > 0f || verticalVelocity > 0f;
    }

    /** @return bottom of the player's 3D body cylinder in world Y. */
    public float getFeetY() {
        return jumpHeight;
    }

    /** @return top of the player's 3D body cylinder in world Y, clamped to the ceiling. */
    public float getTopY() {
        return Math.min(GameMap.WALL_HEIGHT, jumpHeight + BODY_HEIGHT);
    }

    /** @return upper-torso world Y aimed at by ranged enemy projectiles. */
    public float getAimTargetY() {
        return (EYE_HEIGHT - 0.06f) + jumpHeight;
    }

    /** @return normalized knee-flex cushion in [0, 1] right after landing on the floor, otherwise 0. */
    public float getLandingDip() {
        if (landingDipTimer <= 0f || isDead()) return 0f;
        float progress = 1f - landingDipTimer / LANDING_DIP_TIME;
        return (float) Math.sin(Math.PI * Math.max(0f, Math.min(1f, progress)));
    }

    /** @return true once after each jump takeoff (including buffered touchdown jumps). */
    public boolean consumeJumpTriggered() {
        boolean triggered = jumpTriggered;
        jumpTriggered = false;
        return triggered;
    }

    /** @return true once each time the player touches back down on the floor from a jump. */
    public boolean consumeLandedTriggered() {
        boolean triggered = landedTriggered;
        landedTriggered = false;
        return triggered;
    }

    /** Clears any queued pre-landing jump input (for example when pausing). */
    public void clearJumpBuffer() {
        jumpBufferTimer = 0f;
    }

    /**
     * Advances non-movement timers (hurt flash, dash cooldown, landing cushion, death camera settle).
     * @param dt the time step in seconds
     */
    public void updateTimers(float dt) {
        if (dt <= 0f) return;
        hurtFlash = Math.max(0f, hurtFlash - dt);
        dashCooldown = Math.max(0f, dashCooldown - dt);
        dashRefusedTimer = Math.max(0f, dashRefusedTimer - dt);
        landingDipTimer = Math.max(0f, landingDipTimer - dt);
        if (isDead()) {
            jumpBufferTimer = 0f;
            landingDipTimer = 0f;
            if (jumpHeight > 0f || verticalVelocity != 0f) {
                jumpHeight += verticalVelocity * dt - 0.5f * GRAVITY * dt * dt;
                verticalVelocity -= GRAVITY * dt;
                if (jumpHeight <= 0f) {
                    jumpHeight = 0f;
                    verticalVelocity = 0f;
                }
            }
            currentEyeHeight = Math.max(DEAD_EYE_HEIGHT, currentEyeHeight - DEATH_DROP_SPEED * dt);
        }
    }

    /**
     * Applies omnidirectional damage (such as floor hazards or self-centred blasts).
     * @param amount raw damage amount
     * @return total health + armor lost
     */
    public float damage(float amount) {
        return damage(amount, position.x, position.y);
    }

    /**
     * Applies damage from a world source position, splitting {@link #ARMOR_ABSORPTION} into armor up to what remains.
     * @param amount raw damage amount; values at or below zero do nothing
     * @param sourceX world x of the damage origin
     * @param sourceZ world z of the damage origin
     * @return total health + armor lost
     */
    public float damage(float amount, float sourceX, float sourceZ) {
        if (amount <= 0f || isDead()) return 0f;
        float absorbed = Math.min(armor, amount * ARMOR_ABSORPTION);
        armor = Math.max(0f, armor - absorbed);
        float beforeHealth = health;
        health = Math.max(0f, health - (amount - absorbed));
        hurtFlash = HURT_FLASH_TIME;

        float dx = sourceX - position.x;
        float dz = sourceZ - position.y;
        if (dx * dx + dz * dz > 1e-4f) {
            float sourceYaw = (float) Math.toDegrees(Math.atan2(dx, -dz));
            hurtRelativeYaw = ((sourceYaw - yaw) % 360f + 540f) % 360f - 180f;
            directionalHurt = true;
        } else {
            directionalHurt = false;
            hurtRelativeYaw = 0f;
        }
        return absorbed + (beforeHealth - health);
    }

    /**
     * Restores health up to {@link #MAX_HEALTH} while alive.
     * @param amount health to restore
     * @return health actually restored
     */
    public float heal(float amount) {
        if (amount <= 0f || isDead() || health >= MAX_HEALTH) return 0f;
        float before = health;
        health = Math.min(MAX_HEALTH, health + amount);
        return health - before;
    }

    /**
     * Adds armor up to {@link #MAX_ARMOR} while alive.
     * @param amount armor to add
     * @return armor actually added
     */
    public float addArmor(float amount) {
        if (amount <= 0f || isDead() || armor >= MAX_ARMOR) return 0f;
        float before = armor;
        armor = Math.min(MAX_ARMOR, armor + amount);
        return armor - before;
    }

    /**
     * Attempts to trigger a horizontal dash along the requested movement direction (or straight ahead when standing
     * still), spending {@link #DASH_COST} from {@code energy}.
     * @param forward forward input (-1 to 1)
     * @param strafe strafe input (-1 to 1)
     * @param energy the player's energy meter
     * @return whether the dash started
     */
    public boolean tryDash(float forward, float strafe, EnergyMeter energy) {
        if (isDead() || dashRemaining > 0f || dashCooldown > 0f) return false;
        if (!energy.spend(DASH_COST)) {
            dashRefusedTimer = DASH_REFUSED_TIME;
            return false;
        }
        double yawRad = Math.toRadians(yaw);
        float sin = (float) Math.sin(yawRad);
        float cos = (float) Math.cos(yawRad);
        float localLen = (float) Math.hypot(forward, strafe);
        if (localLen < 1e-4f) {
            dashDirX = sin;
            dashDirZ = -cos;
        } else {
            float nf = forward / localLen;
            float ns = strafe / localLen;
            dashDirX = sin * nf + cos * ns;
            dashDirZ = -cos * nf + sin * ns;
        }
        dashRemaining = DASH_DURATION;
        dashCooldown = DASH_COOLDOWN;
        dashRefusedTimer = 0f;
        return true;
    }

    /**
     * Attempts a free physical jump. Launches immediately when grounded, or arms a short pre-landing jump buffer
     * ({@link #JUMP_BUFFER_TIME}) when already airborne so pressing {@code Space} just before touchdown feels crisp
     * without allowing mid-air double jumps.
     * @return whether an immediate takeoff occurred on this call
     */
    public boolean tryJump() {
        if (isDead()) return false;
        if (!isAirborne()) {
            launchJump();
            return true;
        }
        jumpBufferTimer = JUMP_BUFFER_TIME;
        return false;
    }

    private void launchJump() {
        verticalVelocity = JUMP_VELOCITY;
        jumpBufferTimer = 0f;
        landingDipTimer = 0f;
        jumpTriggered = true;
    }

    /**
     * Turns the view.
     * @param yawDelta the change in yaw in degrees; positive turns right
     * @param pitchDelta the change in pitch in degrees; positive looks up
     */
    public void look(float yawDelta, float pitchDelta) {
        if (isDead()) return;
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
     * Computes the eye position, including active jump elevation and landing knee-flex cushion.
     * @param out the vector to fill
     * @return {@code out}
     */
    public Vector3 eye(Vector3 out) {
        float dip = getLandingDip() * LANDING_DIP_EYE;
        float y = isDead()
            ? currentEyeHeight + jumpHeight
            : Math.max(DEAD_EYE_HEIGHT, currentEyeHeight + jumpHeight - dip);
        return out.set(position.x, y, position.y);
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
     * Walks (or advances an active dash) relative to the yaw, sliding along walls and round solid objects.
     * @param map the map to collide with
     * @param solids the objects that block the way while they are not destroyed
     * @param forward forward movement from -1 to 1
     * @param strafe sideways movement from -1 to 1, positive to the right
     * @param dt the time step in seconds
     */
    public void move(GameMap map, List<? extends Damageable> solids, float forward, float strafe, float dt) {
        move(map, solids, Collections.<Door>emptyList(), forward, strafe, dt);
    }

    /**
     * Walks (or advances an active dash) relative to the yaw, sliding along walls, closed bulkhead doors, and solid
     * objects.
     * @param map the map to collide with
     * @param solids the objects that block the way while they are not destroyed
     * @param doors bulkhead doors in the level; closed ones block movement
     * @param forward forward movement from -1 to 1
     * @param strafe sideways movement from -1 to 1, positive to the right
     * @param dt the time step in seconds
     */
    public void move(GameMap map, List<? extends Damageable> solids, List<Door> doors, float forward, float strafe,
                     float dt) {
        if (isDead() || dt <= 0f) return;
        float remainingDt = dt;
        float dashDt = 0f;
        if (dashRemaining > 0f) {
            dashDt = Math.min(remainingDt, dashRemaining);
            dashRemaining -= dashDt;
            remainingDt -= dashDt;
            Collision.move(map, solids, doors, position, dashDirX * DASH_SPEED * dashDt, dashDirZ * DASH_SPEED * dashDt,
                RADIUS);
        }
        advanceVertical(dt, dashDt);
        if (remainingDt <= 0f) return;
        float length = (float) Math.sqrt(forward * forward + strafe * strafe);
        if (length < 1e-4f) return;
        // Diagonal movement is no faster than straight movement.
        float scale = MOVE_SPEED * remainingDt / Math.max(1f, length);
        double yawRad = Math.toRadians(yaw);
        float sin = (float) Math.sin(yawRad);
        float cos = (float) Math.cos(yawRad);
        float dx = (sin * forward + cos * strafe) * scale;
        float dz = (-cos * forward + sin * strafe) * scale;
        float beforeX = position.x;
        float beforeZ = position.y;
        Collision.move(map, solids, doors, position, dx, dz, RADIUS);
        if (!isAirborne()) {
            bobPhase += Vector2.dst(beforeX, beforeZ, position.x, position.y) * BOB_RATE;
        }
    }

    private void advanceVertical(float dt, float dashDt) {
        if (jumpBufferTimer > 0f) {
            jumpBufferTimer = Math.max(0f, jumpBufferTimer - dt);
        }
        if (!isAirborne()) return;

        if (dashDt > 0f) {
            if (verticalVelocity > 0f) {
                jumpHeight += verticalVelocity * dashDt - 0.5f * GRAVITY * dashDt * dashDt;
                verticalVelocity = Math.max(0f, verticalVelocity - GRAVITY * dashDt);
                if (jumpHeight >= MAX_JUMP_HEIGHT) {
                    jumpHeight = MAX_JUMP_HEIGHT;
                    verticalVelocity = 0f;
                }
            } else {
                verticalVelocity = 0f;
            }
        }

        float freeDt = dt - dashDt;
        if (freeDt > 0f) {
            jumpHeight += verticalVelocity * freeDt - 0.5f * GRAVITY * freeDt * freeDt;
            verticalVelocity -= GRAVITY * freeDt;
            if (jumpHeight >= MAX_JUMP_HEIGHT) {
                jumpHeight = MAX_JUMP_HEIGHT;
                if (verticalVelocity > 0f) verticalVelocity = 0f;
            }
            if (jumpHeight <= 0f) {
                jumpHeight = 0f;
                verticalVelocity = 0f;
                landedTriggered = true;
                if (jumpBufferTimer > 0f) {
                    launchJump();
                } else {
                    landingDipTimer = LANDING_DIP_TIME;
                }
            }
        }
    }
}

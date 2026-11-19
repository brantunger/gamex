package io.github.brantunger.gamex.weapon;

/**
 * The Slot 3 electromagnetic Rail Lance: fires a 60-unit piercing hitscan beam consuming 1 slug on primary trigger,
 * with a zero-cost 1.8x tactical zoom optic on alternate trigger.
 */
public final class RailLance {

    /** Maximum slugs carried in the player's slug pool. */
    public static final int MAX_SLUGS = 15;
    /** Baseline slugs granted on Normal difficulty when picking up the Rail Lance itself. */
    public static final int PICKUP_WEAPON_SLUGS = 5;
    /** Baseline slugs granted on Normal difficulty when picking up a Slug Pack. */
    public static final int PICKUP_PACK_SLUGS = 3;

    /** Piercing beam damage dealt to standard targets and enemies (one-shots Drones, Turrets, Guards, Ring Boards). */
    public static final float DAMAGE = 120f;
    /** Piercing beam damage dealt to the Warden's heavy hide (75% of base damage). */
    public static final float WARDEN_DAMAGE = 90f;
    /** Maximum rail beam range in world units. */
    public static final float RANGE = 60f;
    /** Capacitor recharge cycle duration between consecutive rail shots, in seconds. */
    public static final float FIRE_INTERVAL = 1.10f;

    /** How long the muzzle flash decal remains visible after firing, in seconds. */
    public static final float FLASH_TIME = 0.10f;
    /** Delay after shot trigger before the magnetic breech bolt cycles back, in seconds. */
    public static final float BOLT_DELAY = 0.04f;
    /** Duration of the breech bolt recoil/return cycle, in seconds. */
    public static final float BOLT_TIME = 0.68f;
    /** Magnification factor applied to world FOV and inverted onto look sensitivity at full optic zoom. */
    public static final float ZOOM_FACTOR = 1.8f;
    /** Zoom interpolation speed per second (reaches full zoom in ~0.133s). */
    public static final float ZOOM_SPEED = 7.5f;

    private static final float BOLT_PULL_SHARE = 0.25f;

    private float cooldown;
    private float sinceShot = Float.POSITIVE_INFINITY;
    private float zoom;
    private boolean suppressZoomUntilRelease;
    private boolean zoomEngaged;
    private boolean lastZoomHeld;
    private boolean zoomInTriggered;
    private boolean zoomOutTriggered;

    /**
     * Advances capacitor recharge and settles optic zoom when another weapon is equipped.
     * @param dt time step in seconds
     */
    public void updateIdle(float dt) {
        if (!Float.isFinite(dt) || dt < 0f) return;
        cooldown = Math.max(0f, cooldown - dt);
        sinceShot += dt;
        zoom = Math.max(0f, zoom - ZOOM_SPEED * 2f * dt);
        zoomEngaged = false;
        lastZoomHeld = false;
    }

    /**
     * Advances capacitor recharge and settles optic zoom while tracking RMB state (such as during a weapon swap)
     * so releasing RMB clears zoom suppression and holding a fresh RMB press during draw zooms on completion.
     * @param dt time step in seconds
     * @param zoomHeld whether tactical optic zoom (RMB) is held
     */
    public void updateIdle(float dt, boolean zoomHeld) {
        if (!Float.isFinite(dt) || dt < 0f) return;
        updateIdle(dt);
        lastZoomHeld = zoomHeld;
        if (!zoomHeld) {
            suppressZoomUntilRelease = false;
        }
    }

    /**
     * Advances capacitor cooldown, optic zoom interpolation, and firing state for one frame.
     * @param dt time step in seconds
     * @param fireTrigger whether primary fire (LMB) is held
     * @param zoomHeld whether tactical optic zoom (RMB) is held
     * @param availableSlugs slugs currently in the player's pool
     * @return {@code true} if a rail beam fired on this step
     */
    public boolean update(float dt, boolean fireTrigger, boolean zoomHeld, int availableSlugs) {
        return update(dt, fireTrigger, zoomHeld, false, availableSlugs);
    }

    /**
     * Advances capacitor cooldown, optic zoom interpolation, and firing state for one frame.
     * @param dt time step in seconds
     * @param fireTrigger whether primary fire (LMB) is held
     * @param zoomHeld whether tactical optic zoom (RMB) is physically held
     * @param zoomBlocked whether zoom engagement is temporarily blocked (such as during an active dash)
     * @param availableSlugs slugs currently in the player's pool
     * @return {@code true} if a rail beam fired on this step
     */
    public boolean update(float dt, boolean fireTrigger, boolean zoomHeld, boolean zoomBlocked, int availableSlugs) {
        if (!Float.isFinite(dt) || dt < 0f) return false;
        boolean wasReady = cooldown <= 0f;
        cooldown -= dt;
        sinceShot += dt;
        lastZoomHeld = zoomHeld;

        if (!zoomHeld) {
            suppressZoomUntilRelease = false;
        }
        boolean zooming = zoomHeld && !zoomBlocked && !suppressZoomUntilRelease;
        if (zooming && !zoomEngaged) {
            zoomEngaged = true;
            zoomInTriggered = true;
        } else if (!zooming && zoomEngaged) {
            zoomEngaged = false;
            if (zoom > 0.05f) {
                zoomOutTriggered = true;
            }
        }
        if (zooming) {
            zoom = Math.min(1f, zoom + ZOOM_SPEED * dt);
        } else {
            zoom = Math.max(0f, zoom - ZOOM_SPEED * dt);
        }

        if (!fireTrigger || availableSlugs <= 0 || cooldown > 0f) {
            cooldown = Math.max(0f, cooldown);
            return false;
        }

        cooldown = wasReady ? FIRE_INTERVAL : cooldown + FIRE_INTERVAL;
        sinceShot = 0f;
        return true;
    }

    /**
     * Immediately drops optic zoom and prevents re-zooming if zoom or RMB was active until RMB is released.
     */
    public void cancelZoom() {
        if (zoom > 0f || zoomEngaged || lastZoomHeld) {
            suppressZoomUntilRelease = true;
        }
        zoom = 0f;
        zoomEngaged = false;
        zoomInTriggered = false;
        zoomOutTriggered = false;
    }

    /** @return whether the Rail Lance capacitor is fully charged and ready to fire. */
    public boolean isReady() {
        return cooldown <= 0f;
    }

    /**
     * @param dt upcoming frame duration in seconds
     * @return whether the capacitor will be fully charged once {@code dt} elapses
     */
    public boolean isReadyAfter(float dt) {
        float step = Float.isFinite(dt) && dt > 0f ? dt : 0f;
        return cooldown - step <= 0f;
    }

    /** @return whether the muzzle flash decal should currently be drawn. */
    public boolean isFlashVisible() {
        return sinceShot < FLASH_TIME;
    }

    /** @return normalized optic zoom in [0, 1]. */
    public float getZoom() {
        return zoom;
    }

    /** @return world FOV and mouse-sensitivity multiplier for the current {@link #getZoom()}. */
    public float getZoomFovScale() {
        return 1f / (1f + (ZOOM_FACTOR - 1f) * zoom);
    }

    /** @return normalized capacitor recharge progress from 0 (just fired) to 1 (ready). */
    public float getChargeProgress() {
        if (cooldown <= 0f) return 1f;
        return Math.max(0f, Math.min(1f, 1f - cooldown / FIRE_INTERVAL));
    }

    /** @return normalized breech bolt recoil travel in [0, 1]. */
    public float getBolt() {
        float elapsed = sinceShot - BOLT_DELAY;
        if (elapsed <= 0f || elapsed >= BOLT_TIME) return 0f;
        float progress = elapsed / BOLT_TIME;
        if (progress < BOLT_PULL_SHARE) {
            float u = progress / BOLT_PULL_SHARE;
            return u * (2f - u);
        }
        float back = 1f - (progress - BOLT_PULL_SHARE) / (1f - BOLT_PULL_SHARE);
        return back * back;
    }

    /** @return {@code true} once when tactical zoom engages on RMB press. */
    public boolean consumeZoomInTriggered() {
        if (!zoomInTriggered) return false;
        zoomInTriggered = false;
        return true;
    }

    /** @return {@code true} once when tactical zoom disengages after zooming in. */
    public boolean consumeZoomOutTriggered() {
        if (!zoomOutTriggered) return false;
        zoomOutTriggered = false;
        return true;
    }
}

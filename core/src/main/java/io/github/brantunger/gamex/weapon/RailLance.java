package io.github.brantunger.gamex.weapon;

/**
 * The Slot 3 electromagnetic Rail Lance: fires a piercing hitscan beam consuming 1 slug on primary trigger, with a
 * zero-cost tactical zoom optic on alternate trigger. Its numbers are the {@code rail} values of
 * {@link WeaponTuning}.
 */
public final class RailLance {

    private final WeaponTuning tuning;
    private float cooldown;
    private float sinceShot = Float.POSITIVE_INFINITY;
    private float zoom;
    private boolean suppressZoomUntilRelease;
    private boolean zoomEngaged;
    private boolean lastZoomHeld;
    private boolean zoomInTriggered;
    private boolean zoomOutTriggered;

    /** @param tuning the weapon balance; its rail values set the fire rate, the bolt cycle and the optic */
    public RailLance(WeaponTuning tuning) {
        if (tuning == null) throw new IllegalArgumentException("Weapon tuning must not be null");
        this.tuning = tuning;
    }

    /**
     * Advances capacitor recharge and settles optic zoom when another weapon is equipped.
     * @param dt time step in seconds
     */
    public void updateIdle(float dt) {
        if (!Float.isFinite(dt) || dt < 0f) return;
        cooldown = Math.max(0f, cooldown - dt);
        sinceShot += dt;
        zoom = Math.max(0f, zoom - tuning.railZoomSpeed * 2f * dt);
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
            zoom = Math.min(1f, zoom + tuning.railZoomSpeed * dt);
        } else {
            zoom = Math.max(0f, zoom - tuning.railZoomSpeed * dt);
        }

        if (!fireTrigger || availableSlugs <= 0 || cooldown > 0f) {
            cooldown = Math.max(0f, cooldown);
            return false;
        }

        cooldown = wasReady ? tuning.railFireInterval : cooldown + tuning.railFireInterval;
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
        return sinceShot < tuning.railFlashTime;
    }

    /** @return normalized optic zoom in [0, 1]. */
    public float getZoom() {
        return zoom;
    }

    /** @return world FOV and mouse-sensitivity multiplier for the current {@link #getZoom()}. */
    public float getZoomFovScale() {
        return 1f / (1f + (tuning.railZoomFactor - 1f) * zoom);
    }

    /** @return normalized capacitor recharge progress from 0 (just fired) to 1 (ready). */
    public float getChargeProgress() {
        if (cooldown <= 0f) return 1f;
        return Math.max(0f, Math.min(1f, 1f - cooldown / tuning.railFireInterval));
    }

    /** @return normalized breech bolt recoil travel in [0, 1]. */
    public float getBolt() {
        float elapsed = sinceShot - tuning.railBoltDelay;
        if (elapsed <= 0f || elapsed >= tuning.railBoltTime) return 0f;
        float progress = elapsed / tuning.railBoltTime;
        float pullShare = tuning.railBoltPullShare;
        if (progress < pullShare) {
            float u = progress / pullShare;
            return u * (2f - u);
        }
        float back = 1f - (progress - pullShare) / (1f - pullShare);
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

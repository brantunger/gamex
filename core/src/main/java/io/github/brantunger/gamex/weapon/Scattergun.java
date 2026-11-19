package io.github.brantunger.gamex.weapon;

import com.badlogic.gdx.math.Vector3;

import java.util.ArrayList;
import java.util.List;

/**
 * The pump-action combat scattergun: fires deterministic multi-pellet close-quarters blasts on primary fire (1 shell)
 * or heavy double-blasts on alternate fire (2 shells, falling back cleanly to 1 shell if only 1 remains), with linear
 * range falloff and pump-rack animation timing.
 */
public final class Scattergun {

    /** Result of evaluating a simulation step on the scattergun trigger. */
    public enum Shot {
        NONE(0),
        PRIMARY(1),
        DOUBLE_BLAST(2);

        /** Number of shells expended by this shot. */
        public final int shellsSpent;

        Shot(int shellsSpent) {
            this.shellsSpent = shellsSpent;
        }
    }

    private final WeaponTuning tuning;
    private float cooldown;
    private float sinceShot = Float.POSITIVE_INFINITY;
    private float pumpDelay;
    private float pumpDuration;
    private boolean lastDoubleBlast;

    /** @param tuning the weapon balance; its scattergun values set the fire rate, the pellets and the pump */
    public Scattergun(WeaponTuning tuning) {
        if (tuning == null) throw new IllegalArgumentException("Weapon tuning must not be null");
        this.tuning = tuning;
        this.pumpDelay = tuning.scattergunPrimaryPumpDelay;
        this.pumpDuration = tuning.scattergunPrimaryPumpTime;
    }

    /**
     * Advances scattergun timers when the player has another weapon active.
     * @param dt step duration in seconds
     */
    public void updateIdle(float dt) {
        update(dt, false, false, 0);
    }

    /**
     * Advances timers and fires a primary or double-blast shot if triggered, off cooldown, and shells remain.
     * @param dt time step in seconds
     * @param primaryTrigger whether left mouse trigger is held
     * @param altTrigger whether right mouse trigger is held
     * @param availableShells current shells in the pool
     * @return the shot fired during this step, or {@link Shot#NONE}
     */
    public Shot update(float dt, boolean primaryTrigger, boolean altTrigger, int availableShells) {
        if (!Float.isFinite(dt) || dt < 0f) return Shot.NONE;
        boolean wasReady = cooldown <= 0f;
        cooldown -= dt;
        sinceShot += dt;
        if ((!primaryTrigger && !altTrigger) || availableShells <= 0) {
            cooldown = Math.max(0f, cooldown);
            return Shot.NONE;
        }
        if (cooldown > 0f) return Shot.NONE;

        if (altTrigger && availableShells >= 2) {
            cooldown = wasReady ? tuning.scattergunAltInterval : cooldown + tuning.scattergunAltInterval;
            sinceShot = 0f;
            pumpDelay = tuning.scattergunAltPumpDelay;
            pumpDuration = tuning.scattergunAltPumpTime;
            lastDoubleBlast = true;
            return Shot.DOUBLE_BLAST;
        }

        cooldown = wasReady ? tuning.scattergunPrimaryInterval : cooldown + tuning.scattergunPrimaryInterval;
        sinceShot = 0f;
        pumpDelay = tuning.scattergunPrimaryPumpDelay;
        pumpDuration = tuning.scattergunPrimaryPumpTime;
        lastDoubleBlast = false;
        return Shot.PRIMARY;
    }

    /** @return whether the pump cycle has finished and the scattergun can fire immediately. */
    public boolean isReady() {
        return cooldown <= 0f;
    }

    /**
     * @param dt upcoming frame duration in seconds
     * @return whether the scattergun will be off cooldown once {@code dt} elapses
     */
    public boolean isReadyAfter(float dt) {
        float step = Float.isFinite(dt) && dt > 0f ? dt : 0f;
        return cooldown - step <= 0f;
    }

    /** @return whether the muzzle flash decal should currently be drawn. */
    public boolean isFlashVisible() {
        return sinceShot < tuning.scattergunFlashTime;
    }

    /** @return whether the most recent shot was a 2-shell double-blast. */
    public boolean isLastShotDoubleBlast() {
        return lastDoubleBlast;
    }

    /** @return normalized forend pump travel in [0, 1], peaking rearward after a shot before chambering home. */
    public float getPump() {
        float elapsed = sinceShot - pumpDelay;
        if (elapsed <= 0f || elapsed >= pumpDuration) return 0f;
        float progress = elapsed / pumpDuration;
        float pullShare = tuning.scattergunPumpPullShare;
        if (progress < pullShare) {
            float u = progress / pullShare;
            return u * (2f - u);
        }
        float back = 1f - (progress - pullShare) / (1f - pullShare);
        return back * back;
    }

    /**
     * Generates deterministic unit pellet ray directions around {@code forward}: one down the aim line, then those
     * of each ring of the pattern, in the order of {@link WeaponTuning#scattergunPrimaryRings()} or
     * {@link WeaponTuning#scattergunAltRings()}.
     * @param forward centre aim direction (normalized or non-zero)
     * @param doubleBlast true for the alt-fire pattern, false for the primary pattern
     * @return list of unit direction vectors
     */
    public List<Vector3> pelletDirections(Vector3 forward, boolean doubleBlast) {
        Vector3 centre = new Vector3(forward);
        float len2 = centre.len2();
        if (!Float.isFinite(len2) || len2 < 1e-12f) centre.set(0f, 0f, -1f);
        else centre.nor();

        Vector3 right = new Vector3(centre).crs(Vector3.Y);
        if (right.len2() < 1e-8f) right.set(Vector3.X);
        else right.nor();
        Vector3 up = new Vector3(right).crs(centre).nor();

        List<Vector3> dirs = new ArrayList<>(
            doubleBlast ? tuning.scattergunAltPellets : tuning.scattergunPrimaryPellets);
        dirs.add(new Vector3(centre));
        List<WeaponTuning.Ring> rings = doubleBlast ? tuning.scattergunAltRings() : tuning.scattergunPrimaryRings();
        for (int i = 0; i < rings.size(); i++) {
            WeaponTuning.Ring ring = rings.get(i);
            addConeRing(dirs, centre, right, up, ring.count, ring.coneDegrees, Math.toRadians(ring.twistDegrees));
        }
        return dirs;
    }

    private static void addConeRing(List<Vector3> out, Vector3 centre, Vector3 right, Vector3 up,
                                    int count, float angleDeg, double phaseRad) {
        float spread = (float) Math.tan(Math.toRadians(angleDeg));
        for (int i = 0; i < count; i++) {
            double theta = phaseRad + i * (2.0 * Math.PI / count);
            float rx = (float) Math.cos(theta) * spread;
            float ry = (float) Math.sin(theta) * spread;
            out.add(new Vector3(centre).mulAdd(right, rx).mulAdd(up, ry).nor());
        }
    }

    /**
     * Computes single-pellet damage at impact distance {@code distance}.
     * @param distance hit distance along the ray in world units
     * @return pellet damage after range falloff
     */
    public float damageAtDistance(float distance) {
        float pelletDamage = tuning.scattergunPelletDamage;
        float falloffStart = tuning.scattergunFalloffStart;
        float range = tuning.scattergunRange;
        float minScale = tuning.scattergunMinFalloffScale;
        if (Float.isNaN(distance) || distance <= falloffStart) return pelletDamage;
        if (distance >= range) return pelletDamage * minScale;
        float t = (distance - falloffStart) / (range - falloffStart);
        float scale = 1f - t * (1f - minScale);
        return pelletDamage * scale;
    }
}

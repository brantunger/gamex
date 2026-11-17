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

    /** Maximum shells carried in the player's shell pool. */
    public static final int MAX_SHELLS = 24;
    /** Baseline shells granted on Normal difficulty when picking up the Scattergun itself. */
    public static final int PICKUP_WEAPON_SHELLS = 8;
    /** Baseline shells granted on Normal difficulty when picking up a Shell Box. */
    public static final int PICKUP_BOX_SHELLS = 6;

    /** Number of pellets fired in a standard 1-shell primary blast. */
    public static final int PRIMARY_PELLETS = 8;
    /** Half-angle cone of the primary ring pellets around aim forward, in degrees. */
    public static final float PRIMARY_CONE_DEGREES = 4.2f;
    /** Minimum time between standard primary shots, in seconds. */
    public static final float PRIMARY_INTERVAL = 0.75f;

    /** Number of pellets fired in a 2-shell double-blast. */
    public static final int ALT_PELLETS = 16;
    /** Half-angle cone of the inner ring in a double-blast, in degrees. */
    public static final float ALT_INNER_CONE_DEGREES = 3.4f;
    /** Half-angle cone of the outer ring in a double-blast, in degrees. */
    public static final float ALT_CONE_DEGREES = 6.8f;
    /** Minimum recovery interval after a 2-shell double-blast, in seconds. */
    public static final float ALT_INTERVAL = 1.15f;

    /** Base damage per pellet at or inside {@link #FALLOFF_START}. */
    public static final float PELLET_DAMAGE = 12f;
    /** Distance at which pellet damage starts tapering down, in world units. */
    public static final float FALLOFF_START = 7f;
    /** Maximum scattergun pellet range, in world units. */
    public static final float RANGE = 26f;
    /** Minimum damage multiplier at maximum {@link #RANGE}. */
    public static final float MIN_FALLOFF_SCALE = 0.35f;

    /** How long the muzzle flash stays visible after a shot, in seconds. */
    public static final float FLASH_TIME = 0.09f;
    /** Primary pump-rack duration, starting shortly after recoil kick. */
    public static final float PRIMARY_PUMP_TIME = 0.48f;
    /** Double-blast pump-rack duration. */
    public static final float ALT_PUMP_TIME = 0.68f;

    private static final float PRIMARY_PUMP_DELAY = 0.08f;
    private static final float ALT_PUMP_DELAY = 0.12f;
    private static final float PUMP_PULL_SHARE = 0.35f;

    private float cooldown;
    private float sinceShot = Float.POSITIVE_INFINITY;
    private float pumpDelay = PRIMARY_PUMP_DELAY;
    private float pumpDuration = PRIMARY_PUMP_TIME;
    private boolean lastDoubleBlast;

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
        boolean wasReady = cooldown <= 0f;
        cooldown -= dt;
        sinceShot += dt;
        if ((!primaryTrigger && !altTrigger) || availableShells <= 0) {
            cooldown = Math.max(0f, cooldown);
            return Shot.NONE;
        }
        if (cooldown > 0f) return Shot.NONE;

        if (altTrigger && availableShells >= 2) {
            cooldown = wasReady ? ALT_INTERVAL : cooldown + ALT_INTERVAL;
            sinceShot = 0f;
            pumpDelay = ALT_PUMP_DELAY;
            pumpDuration = ALT_PUMP_TIME;
            lastDoubleBlast = true;
            return Shot.DOUBLE_BLAST;
        }

        cooldown = wasReady ? PRIMARY_INTERVAL : cooldown + PRIMARY_INTERVAL;
        sinceShot = 0f;
        pumpDelay = PRIMARY_PUMP_DELAY;
        pumpDuration = PRIMARY_PUMP_TIME;
        lastDoubleBlast = false;
        return Shot.PRIMARY;
    }

    /** @return whether the pump cycle has finished and the scattergun can fire immediately. */
    public boolean isReady() {
        return cooldown <= 0f;
    }

    /** @return whether the muzzle flash decal should currently be drawn. */
    public boolean isFlashVisible() {
        return sinceShot < FLASH_TIME;
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
        if (progress < PUMP_PULL_SHARE) {
            float u = progress / PUMP_PULL_SHARE;
            return u * (2f - u);
        }
        float back = 1f - (progress - PUMP_PULL_SHARE) / (1f - PUMP_PULL_SHARE);
        return back * back;
    }

    /**
     * Generates deterministic unit pellet ray directions around {@code forward}.
     * @param forward centre aim direction (normalized or non-zero)
     * @param doubleBlast true for 16-pellet alt-fire pattern, false for 8-pellet primary pattern
     * @return list of unit direction vectors
     */
    public static List<Vector3> pelletDirections(Vector3 forward, boolean doubleBlast) {
        Vector3 centre = new Vector3(forward);
        if (centre.len2() < 1e-12f) centre.set(0f, 0f, -1f);
        else centre.nor();

        Vector3 right = new Vector3(centre).crs(Vector3.Y);
        if (right.len2() < 1e-8f) right.set(Vector3.X);
        else right.nor();
        Vector3 up = new Vector3(right).crs(centre).nor();

        List<Vector3> dirs = new ArrayList<>(doubleBlast ? ALT_PELLETS : PRIMARY_PELLETS);
        dirs.add(new Vector3(centre));
        if (!doubleBlast) {
            addConeRing(dirs, centre, right, up, 7, PRIMARY_CONE_DEGREES, 0.0);
        } else {
            addConeRing(dirs, centre, right, up, 5, ALT_INNER_CONE_DEGREES, 0.0);
            addConeRing(dirs, centre, right, up, 10, ALT_CONE_DEGREES, Math.PI / 10.0);
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
    public static float damageAtDistance(float distance) {
        if (distance <= FALLOFF_START) return PELLET_DAMAGE;
        if (distance >= RANGE) return PELLET_DAMAGE * MIN_FALLOFF_SCALE;
        float t = (distance - FALLOFF_START) / (RANGE - FALLOFF_START);
        float scale = 1f - t * (1f - MIN_FALLOFF_SCALE);
        return PELLET_DAMAGE * scale;
    }
}

package io.github.brantunger.gamex;

import com.badlogic.gdx.math.Vector3;
import io.github.brantunger.gamex.player.Player;
import io.github.brantunger.gamex.player.PlayerInput;
import io.github.brantunger.gamex.weapon.BlastCharger;
import io.github.brantunger.gamex.weapon.BlastProjectile;
import io.github.brantunger.gamex.weapon.EnergyMeter;
import io.github.brantunger.gamex.weapon.Pistol;
import io.github.brantunger.gamex.weapon.ViewmodelAnimation;
import io.github.brantunger.gamex.world.Damageable;
import io.github.brantunger.gamex.world.Effect;
import io.github.brantunger.gamex.world.GameMap;
import io.github.brantunger.gamex.world.Hitscan;
import io.github.brantunger.gamex.world.RayHit;
import io.github.brantunger.gamex.world.Target;

import java.util.ArrayList;
import java.util.List;

/**
 * The whole game state and the rules that advance it. It uses no graphics types, so it runs without a window: the
 * renderers only read it, and each frame's {@link PlayerInput} is its only input.
 */
public final class GameSimulation {

    /** The longest time step simulated at once, in seconds; a longer frame is cut down to this. */
    public static final float MAX_DT = 0.05f;
    /** How far a mark sits off the surface it lies on, so it does not fight with the surface for depth. */
    private static final float MARK_OFFSET = 0.012f;
    /** How far below the eyes a blast leaves, so it does not fill the view. */
    private static final float BLAST_DROP = 0.1f;

    /** The level. */
    public final GameMap map;
    /** The player. */
    public final Player player;
    /** Every target of the level, destroyed ones included. */
    public final List<Target> targets = new ArrayList<>();
    /** The pistol. */
    public final Pistol pistol = new Pistol();
    /** The energy that pays for blasts. */
    public final EnergyMeter energy = new EnergyMeter();
    /** The blast's charge state. */
    public final BlastCharger charger = new BlastCharger(energy);
    /** The motion of the first-person pistol: recoil, bob and sway. */
    public final ViewmodelAnimation viewmodel = new ViewmodelAnimation();
    /** The blasts in flight. */
    public final List<BlastProjectile> projectiles = new ArrayList<>();
    /** The visual effects that are still running. */
    public final List<Effect> effects = new ArrayList<>();

    private final Vector3 eye = new Vector3();
    private final Vector3 direction = new Vector3();
    private final boolean[] wasDestroyed;
    private float time;

    /** @param map the level to play */
    public GameSimulation(GameMap map) {
        this.map = map;
        this.player = new Player(map.getSpawnX(), map.getSpawnZ(), map.getSpawnYaw());
        for (GameMap.TargetSpawn spawn : map.getTargetSpawns()) {
            targets.add(new Target(spawn.kind, spawn.x, spawn.z));
        }
        wasDestroyed = new boolean[targets.size()];
    }

    /**
     * Advances the game by one frame.
     * @param frameTime the time since the last frame in seconds; it is clamped to between 0 and {@link #MAX_DT}
     * @param input what the player asks for this frame
     */
    public void update(float frameTime, PlayerInput input) {
        float dt = Math.max(0f, Math.min(MAX_DT, frameTime));
        time += dt;
        player.look(input.yawDelta, input.pitchDelta);
        float beforeX = player.position.x;
        float beforeZ = player.position.y;
        player.move(map, targets, input.moveForward, input.moveStrafe, dt);
        player.eye(eye);
        player.direction(direction);

        boolean fired = pistol.update(dt, input.fire);
        if (fired) firePistol();
        // The gun bobs with the distance actually covered, so it settles when the player is held up by a wall.
        float walked = player.position.dst(beforeX, beforeZ);
        float walkSpeed = dt > 0f ? walked / (Player.MOVE_SPEED * dt) : 0f;
        viewmodel.update(dt, fired, walkSpeed, input.yawDelta, input.pitchDelta);

        float firedCharge = charger.update(dt, input.charge);
        if (firedCharge > 0f) {
            eye.y -= BLAST_DROP;
            projectiles.add(new BlastProjectile(eye, direction, firedCharge));
        }

        for (int i = projectiles.size() - 1; i >= 0; i--) {
            BlastProjectile projectile = projectiles.get(i);
            for (int t = 0; t < wasDestroyed.length; t++) wasDestroyed[t] = targets.get(t).isDestroyed();
            if (!projectile.update(dt, map, targets)) continue;
            projectiles.remove(i);
            float radius = BlastProjectile.blastRadius(projectile.getCharge());
            effects.add(new Effect(Effect.Type.EXPLOSION, projectile.position, Vector3.Y, radius));
            for (int t = 0; t < wasDestroyed.length; t++) {
                if (!wasDestroyed[t] && targets.get(t).isDestroyed()) addBreakEffect(targets.get(t));
            }
        }

        for (int i = 0; i < targets.size(); i++) targets.get(i).update(dt);
        for (int i = effects.size() - 1; i >= 0; i--) {
            Effect effect = effects.get(i);
            effect.update(dt);
            if (effect.isFinished()) effects.remove(i);
        }
    }

    /** @return the simulated time in seconds: the sum of the clamped time steps so far. */
    public float getTime() {
        return time;
    }

    /**
     * Call when the game is paused. A blast that is charging is dropped at no cost, so that letting go of the button
     * during the pause does not fire it on return.
     */
    public void pause() {
        charger.cancel();
    }

    /** @return the number of targets that are not destroyed. */
    public int getTargetsRemaining() {
        int remaining = 0;
        for (int i = 0; i < targets.size(); i++) {
            if (!targets.get(i).isDestroyed()) remaining++;
        }
        return remaining;
    }

    private void firePistol() {
        RayHit hit = Hitscan.cast(map, targets, eye, direction, Pistol.RANGE);
        if (!hit.isHit()) return;
        // Lift the effect off the surface towards the open side.
        hit.point.mulAdd(hit.normal, MARK_OFFSET);
        if (hit.kind != RayHit.Kind.TARGET) {
            effects.add(new Effect(Effect.Type.IMPACT, hit.point, hit.normal, 0.11f));
            return;
        }
        hit.target.damage(Pistol.DAMAGE);
        effects.add(new Effect(Effect.Type.SPARK, hit.point, hit.normal, 0.2f));
        if (hit.target.isDestroyed()) addBreakEffect(hit.target);
    }

    private void addBreakEffect(Damageable target) {
        Vector3 centre = new Vector3(target.getX(), target.getHeight() * 0.5f, target.getZ());
        effects.add(new Effect(Effect.Type.EXPLOSION, centre, Vector3.Y, target.getHeight() * 0.7f));
    }
}

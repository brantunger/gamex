package io.github.brantunger.gamex.render;

import io.github.brantunger.gamex.world.Enemy;

/**
 * What the Warden's body is doing, as far as its animation goes. The renderer plays the clip of the pose that
 * {@link #of} picks from the simulation; the renderer only delays the change to standing.
 */
public enum WardenPose {
    /** Standing still: a long clip of the Warden shifting and gesturing on the spot, looped at its own speed. */
    IDLE("Stand_Talking_Angry", 0f),
    /**
     * Walking, looped. In the clip a planted foot moves back at 1.56 units of the model per second (1.557 for the
     * left foot and 1.572 for the right, averaged over the frames in which the toe is within 3 cm of the ground),
     * so that is how fast the body must move for the feet not to slide.
     */
    WALK("Walking", 1.56f),
    /** Running, looped. Its planted foot moves back at 5.33 units of the model per second (5.397 and 5.273). */
    RUN("Running", 5.33f),
    /**
     * Attacking and recovering: the punch combo. The renderer does not play this clip by the clock; it shows the
     * moment of it that the simulation's combo has reached, so that the fists land when the punches do.
     */
    ATTACK("Punch_Combo", 0f);

    /** The slowest a walk or a run is played, so that a Warden held up against something still moves its legs. */
    static final float MIN_PLAYBACK = 0.5f;
    /** The fastest a walk or a run is played; beyond it the legs blur. */
    static final float MAX_PLAYBACK = 2.5f;

    /** The name of the animation in the model file. */
    public final String clip;
    /**
     * How fast the Warden moves over the ground when this clip plays at the speed it was made at, in world units
     * per second; 0 for a pose that does not travel.
     */
    public final float strideSpeed;

    WardenPose(String clip, float modelStrideSpeed) {
        this.clip = clip;
        this.strideSpeed = modelStrideSpeed * WardenModel.SCALE;
    }

    /**
     * Picks the pose for a Warden. Its combo and the recovery after it are {@link #ATTACK}. Otherwise a Warden that
     * is not moving is {@link #IDLE}, one that moves at the player it can see is {@link #RUN} and one that moves
     * without seeing them is {@link #WALK}.
     * @param state the state of the Warden
     * @param moving whether the Warden is on the move
     * @param chasing whether the Warden runs at a player it can see; see {@link Enemy#isChasing()}
     * @return the pose to show
     */
    public static WardenPose of(Enemy.State state, boolean moving, boolean chasing) {
        switch (state) {
            case WINDUP:
            case RECOVERING:
                return ATTACK;
            case PURSUING:
            case DORMANT:
            default:
                if (!moving) return IDLE;
                return chasing ? RUN : WALK;
        }
    }

    /**
     * @param groundSpeed how fast the Warden moves, in world units per second
     * @return how fast to play this pose's clip, where 1 is the speed the clip was made at: for a walk or a run the
     *     speed at which the feet keep up with the ground, for {@link #IDLE} 1, and for {@link #ATTACK} 0, as its
     *     clip is not played by the clock
     */
    public float playbackSpeed(float groundSpeed) {
        if (this == IDLE) return 1f;
        if (strideSpeed <= 0f) return 0f;
        return Math.max(MIN_PLAYBACK, Math.min(MAX_PLAYBACK, groundSpeed / strideSpeed));
    }
}

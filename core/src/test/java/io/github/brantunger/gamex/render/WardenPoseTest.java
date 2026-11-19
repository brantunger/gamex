package io.github.brantunger.gamex.render;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import io.github.brantunger.gamex.world.Enemy;
import org.junit.jupiter.api.Test;

/** Checks which animation the Warden shows in each state of the simulation, and how fast it plays. */
class WardenPoseTest {

    @Test
    void theComboAndTheRecoveryAfterItShowTheAttack() {
        for (boolean moving : new boolean[] {false, true}) {
            for (boolean chasing : new boolean[] {false, true}) {
                assertEquals(WardenPose.ATTACK, WardenPose.of(Enemy.State.WINDUP, moving, chasing));
                assertEquals(WardenPose.ATTACK, WardenPose.of(Enemy.State.RECOVERING, moving, chasing),
                    "the recovery holds the end of the combo's clip");
            }
        }
    }

    @Test
    void aWardenThatSeesThePlayerRunsAndOneThatDoesNotWalks() {
        assertEquals(WardenPose.RUN, WardenPose.of(Enemy.State.PURSUING, true, true));
        assertEquals(WardenPose.WALK, WardenPose.of(Enemy.State.PURSUING, true, false));
    }

    @Test
    void aWardenThatIsNotMovingStandsWhetherOrNotItSeesThePlayer() {
        assertEquals(WardenPose.IDLE, WardenPose.of(Enemy.State.PURSUING, false, true));
        assertEquals(WardenPose.IDLE, WardenPose.of(Enemy.State.PURSUING, false, false));
        assertEquals(WardenPose.IDLE, WardenPose.of(Enemy.State.DORMANT, false, false));
    }

    @Test
    void eachPoseNamesItsClip() {
        assertEquals("Walking", WardenPose.WALK.clip);
        assertEquals("Running", WardenPose.RUN.clip);
        assertEquals("Punch_Combo", WardenPose.ATTACK.clip);
        assertEquals("Stand_Talking_Angry", WardenPose.IDLE.clip);
    }

    @Test
    void theStrideSpeedsAreTheClipsFootSpeedsAtTheScaleOfTheModel() {
        assertEquals(1.56f * WardenModel.SCALE, WardenPose.WALK.strideSpeed, 1e-5f);
        assertEquals(5.33f * WardenModel.SCALE, WardenPose.RUN.strideSpeed, 1e-5f);
        assertEquals(0f, WardenPose.IDLE.strideSpeed);
        assertEquals(0f, WardenPose.ATTACK.strideSpeed);
    }

    @Test
    void walkAndRunPlayAsFastAsTheWardenMovesSoTheFeetDoNotSlide() {
        // Played at this speed, a planted foot moves back over the ground exactly as fast as the body moves on.
        float walk = WardenPose.WALK.playbackSpeed(Enemy.Kind.WARDEN.moveSpeed);
        assertEquals(Enemy.Kind.WARDEN.moveSpeed, walk * WardenPose.WALK.strideSpeed, 1e-4f);
        float run = WardenPose.RUN.playbackSpeed(Enemy.WARDEN_RUN_SPEED);
        assertEquals(Enemy.WARDEN_RUN_SPEED, run * WardenPose.RUN.strideSpeed, 1e-4f);
        assertTrue(walk > 1f && walk < 2f, "the walk is a brisk one, not a blur: " + walk);
        assertTrue(run > 1f && run < 1.5f, "the run is close to the clip's own speed: " + run);
    }

    @Test
    void overdriveSpeedsTheRunClipUpInStepWithTheRun() {
        float run = WardenPose.RUN.playbackSpeed(Enemy.WARDEN_RUN_SPEED);
        float overdrive = WardenPose.RUN.playbackSpeed(Enemy.WARDEN_RUN_SPEED * Enemy.WARDEN_OVERDRIVE_SPEED_SCALE);
        assertEquals(Enemy.WARDEN_OVERDRIVE_SPEED_SCALE, overdrive / run, 1e-4f);
        assertTrue(overdrive < WardenPose.MAX_PLAYBACK, "inside the limit, so still in step: " + overdrive);
    }

    @Test
    void playbackIsLimitedAndOnlyTheComboIsNotPlayedByTheClock() {
        assertEquals(WardenPose.MIN_PLAYBACK, WardenPose.WALK.playbackSpeed(0.01f));
        assertEquals(WardenPose.MAX_PLAYBACK, WardenPose.WALK.playbackSpeed(100f));
        assertEquals(1f, WardenPose.IDLE.playbackSpeed(0f), "the standing clip plays at its own speed");
        assertEquals(1f, WardenPose.IDLE.playbackSpeed(3f));
        assertEquals(0f, WardenPose.ATTACK.playbackSpeed(3f), "the combo is shown at the simulation's time");
    }
}

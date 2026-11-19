package io.github.brantunger.gamex.weapon;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import io.github.brantunger.gamex.TestTuning;
import io.github.brantunger.gamex.player.Player;
import io.github.brantunger.gamex.world.Damageable;
import io.github.brantunger.gamex.world.Door;
import io.github.brantunger.gamex.world.Enemy;
import io.github.brantunger.gamex.world.GameMap;
import io.github.brantunger.gamex.world.Hitscan;
import io.github.brantunger.gamex.world.SecurityConsole;
import io.github.brantunger.gamex.world.Target;
import org.junit.jupiter.api.Test;

import java.util.Arrays;
import java.util.Collections;
import java.util.List;

class ExplosionTest {

    private static final float EPSILON = 1e-3f;

    @Test
    void lateralCylinderRimRaysHitWideEntitiesPastDoorwayWallCorners() {
        // Wall row 2 spans x in [0, 3], leaving an open doorway at x in [3, 6].
        GameMap doorway = GameMap.parse(
            "#######",
            "#>....#",
            "###...#",
            "#.....#",
            "#######");

        // Wall cell (2, 2) covers x in [2, 3], z in [2, 3]. From blast (2.82, 1.88), both the center ray and the
        // collinear front-rim ray to each cylinder cross z = 2.0 before x = 3.0 and are blocked by corner (2, 2),
        // while each cylinder's east lateral rim protrudes into open cell (3, 2) with clear line of sight.
        float blastX = 2.82f;
        float blastZ = 1.88f;
        Enemy warden = new Enemy(TestTuning.ENEMIES, Enemy.Kind.WARDEN, 3.33f, 2.50f, 0f);
        Target ringBoard = new Target(Target.Kind.RING_BOARD, 3.26f, 2.40f);
        Player player = new Player(3.27f, 2.42f, 0f);

        assertFalse(Hitscan.hasLineOfSight(doorway, blastX, blastZ, warden.getX(), warden.getZ()),
            "center ray clips doorway corner");
        assertFalse(Hitscan.hasLineOfSight(doorway, blastX, blastZ, ringBoard.getX(), ringBoard.getZ()),
            "target center ray clips doorway corner");
        assertFalse(Hitscan.hasLineOfSight(doorway, blastX, blastZ, player.position.x, player.position.y),
            "player center ray clips doorway corner");

        int damaged = Explosion.apply(doorway, Collections.emptyList(), blastX, 0.5f, blastZ, 2.5f, 100f,
            Arrays.asList(warden, ringBoard), player, 0.5f);

        assertEquals(2, damaged, "both wide targets hit via exposed lateral rim");
        assertTrue(warden.getHealth() < TestTuning.ENEMIES.of(Enemy.Kind.WARDEN).maxHealth);
        assertTrue(ringBoard.getHealth() < Target.Kind.RING_BOARD.maxHealth);
        assertTrue(player.getHealth() < Player.MAX_HEALTH, "player hit via exposed lateral rim");
    }

    @Test
    void closedBulkheadStillShieldsEntitiesCompletelyBehindIt() {
        GameMap hall = GameMap.parse(
            "#########",
            "#>......#",
            "#########");
        Door closed = new Door(Door.LockKind.NONE, Door.Orientation.SPAN_Z, 4, 1);
        Target behindDoor = new Target(Target.Kind.CANISTER, 5.2f, 1.5f);
        Player playerBehindDoor = new Player(5.2f, 1.5f, 0f);

        int damaged = Explosion.apply(hall, Collections.singletonList(closed), 4.2f, 0.5f, 1.5f, 3f, 100f,
            Collections.singletonList(behindDoor), playerBehindDoor, 1f);

        assertEquals(0, damaged);
        assertEquals(Target.Kind.CANISTER.maxHealth, behindDoor.getHealth(), EPSILON);
        assertEquals(Player.MAX_HEALTH, playerBehindDoor.getHealth(), EPSILON);
    }

    @Test
    void indestructibleSecurityConsoleDoesNotInflateDamagedCount() {
        GameMap room = GameMap.parse(
            "#######",
            "#>....#",
            "#######");
        SecurityConsole console = new SecurityConsole(2.5f, 1.5f);
        Target canister = new Target(Target.Kind.CANISTER, 3.0f, 1.5f);
        Target destroyedBoard = new Target(Target.Kind.RING_BOARD, 2.8f, 1.5f);
        destroyedBoard.damage(1000f);
        assertTrue(destroyedBoard.isDestroyed());

        int consoleOnly = Explosion.apply(room, Collections.emptyList(), 2.2f, 0.4f, 1.5f, 3f, 100f,
            Collections.singletonList(console), null, 0f);
        assertEquals(0, consoleOnly, "indestructible console takes 0 damage and is not counted");

        List<Damageable> mixed = Arrays.asList(console, canister, destroyedBoard);
        int mixedDamaged = Explosion.apply(room, Collections.emptyList(), 2.2f, 0.4f, 1.5f, 3f, 100f,
            mixed, null, 0f);
        assertEquals(1, mixedDamaged, "only the live canister whose health dropped is counted");
        assertTrue(canister.isDestroyed());
    }

    @Test
    void tenArgumentApplyCombinesHorizontalAndVerticalGapsInThreeDimensions() {
        GameMap room = GameMap.parse(
            "#######",
            "#>....#",
            "#######");

        // Ceiling blast at y = 0.90 directly above a canister [0, 0.55] -> horizGap = 0, vertGap = 0.35.
        Target underCeiling = new Target(Target.Kind.CANISTER, 2.5f, 1.5f);
        Player groundedPlayer = new Player(2.5f, 1.5f, 0f); // [0, 0.68] -> vertGap = 0.22
        int damaged = Explosion.apply(room, Collections.emptyList(), 2.5f, 0.90f, 1.5f, 0.50f, 100f,
            Collections.singletonList(underCeiling), groundedPlayer, 1f);

        assertEquals(1, damaged);
        float expectedCanisterDamage = Explosion.damageAt(0.90f - Target.Kind.CANISTER.height, 0.50f, 100f);
        float expectedPlayerDamage = Explosion.damageAt(0.90f - groundedPlayer.getTopY(), 0.50f, 100f);
        assertEquals(Target.Kind.CANISTER.maxHealth - expectedCanisterDamage, underCeiling.getHealth(), EPSILON);
        assertEquals(Player.MAX_HEALTH - expectedPlayerDamage, groundedPlayer.getHealth(), EPSILON);

        // Smaller radius 0.20 cannot reach across the 0.22 vertical gap to the grounded player or 0.35 to canister.
        Target outOfVertReach = new Target(Target.Kind.CANISTER, 2.5f, 1.5f);
        Player unharmedPlayer = new Player(2.5f, 1.5f, 0f);
        int missedVertically = Explosion.apply(room, Collections.emptyList(), 2.5f, 0.90f, 1.5f, 0.20f, 100f,
            Collections.singletonList(outOfVertReach), unharmedPlayer, 1f);
        assertEquals(0, missedVertically);
        assertEquals(Target.Kind.CANISTER.maxHealth, outOfVertReach.getHealth(), EPSILON);
        assertEquals(Player.MAX_HEALTH, unharmedPlayer.getHealth(), EPSILON);

        // Floor blast at y = 0.05 combined with horizontal offset against an airborne player uses hypot(horiz, vert).
        Player jumping = new Player(3.05f, 1.5f, 0f);
        assertTrue(jumping.tryJump());
        while (jumping.getVerticalVelocity() > 0f) {
            jumping.move(room, 0f, 0f, 1f / 60f);
        }
        float horizGap = (3.05f - 2.5f) - Player.RADIUS; // 0.30
        float vertGap = jumping.getFeetY() - 0.05f;
        assertTrue(vertGap > 0.2f);
        float dist3D = (float) Math.hypot(horizGap, vertGap);
        Explosion.apply(room, Collections.emptyList(), 2.5f, 0.05f, 1.5f, 1.0f, 100f,
            Collections.emptyList(), jumping, 1f);
        assertEquals(Player.MAX_HEALTH - Explosion.damageAt(dist3D, 1.0f, 100f), jumping.getHealth(), EPSILON);

        // Legacy 9-arg apply remains purely 2D XZ (vertGap = 0) for backward compatibility.
        Player jumping2D = new Player(3.05f, 1.5f, 0f);
        assertTrue(jumping2D.tryJump());
        while (jumping2D.getVerticalVelocity() > 0f) {
            jumping2D.move(room, 0f, 0f, 1f / 60f);
        }
        Explosion.apply(room, Collections.emptyList(), 2.5f, 1.5f, 1.0f, 100f,
            Collections.emptyList(), jumping2D, 1f);
        assertEquals(Player.MAX_HEALTH - Explosion.damageAt(horizGap, 1.0f, 100f), jumping2D.getHealth(), EPSILON);
    }

    @Test
    void guardsNonFiniteAndNonPositiveInputs() {
        Target canister = new Target(Target.Kind.CANISTER, 2.5f, 1.5f);
        assertEquals(0f, Explosion.damageAt(Float.NaN, 2f, 100f), 1e-6f);
        assertEquals(0f, Explosion.damageAt(0.5f, Float.NaN, 100f), 1e-6f);
        assertEquals(0f, Explosion.damageAt(0.5f, 2f, Float.NaN), 1e-6f);
        assertEquals(0, Explosion.apply(null, Collections.emptyList(), Float.NaN, 0.5f, 1.5f, 2f, 100f,
            Collections.singletonList(canister), null, 0f));
        assertEquals(0, Explosion.apply(null, Collections.emptyList(), 2.5f, Float.NaN, 1.5f, 2f, 100f,
            Collections.singletonList(canister), null, 0f));
        assertEquals(Target.Kind.CANISTER.maxHealth, canister.getHealth(), 1e-6f);
    }
}

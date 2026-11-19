package io.github.brantunger.gamex.weapon;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.badlogic.gdx.math.Vector3;
import io.github.brantunger.gamex.player.Player;
import io.github.brantunger.gamex.world.Door;
import io.github.brantunger.gamex.world.Effect;
import io.github.brantunger.gamex.world.Enemy;
import io.github.brantunger.gamex.world.GameMap;
import io.github.brantunger.gamex.world.SecurityConsole;
import io.github.brantunger.gamex.world.Target;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

class EnemyProjectileTest {

    private static final float DT = 1f / 60f;

    @Test
    void hitsTargetsLikeCanistersAndIsAbsorbedHarmlesslyByOtherEnemies() {
        GameMap hall = GameMap.parse(
            "##########",
            "#>.......#",
            "##########");
        Enemy shooter = new Enemy(Enemy.Kind.GUARD, 1.5f, 1.5f, 90f);
        Enemy squadmate = new Enemy(Enemy.Kind.DRONE, 3.5f, 1.5f, 90f);
        List<Enemy> enemies = List.of(shooter, squadmate);
        List<Effect> effects = new ArrayList<>();

        EnemyProjectile friendlyBolt = new EnemyProjectile(new Vector3(1.7f, 0.4f, 1.5f),
            new Vector3(1f, 0f, 0f), 8f, 18f, shooter);
        for (int i = 0; i < 60 && !friendlyBolt.isFinished(); i++) {
            friendlyBolt.update(DT, hall, null, 1f, Collections.emptyList(), enemies, effects);
        }
        assertTrue(friendlyBolt.isFinished(), "bolt stops on fellow enemy");
        assertEquals(Enemy.Kind.DRONE.maxHealth, squadmate.getHealth(), 1e-6f, "no friendly fire");

        Target canister = new Target(Target.Kind.CANISTER, 5.5f, 1.5f);
        EnemyProjectile canisterBolt = new EnemyProjectile(new Vector3(2.0f, 0.35f, 1.5f),
            new Vector3(1f, 0f, 0f), 8f, 18f, shooter);
        for (int i = 0; i < 60 && !canisterBolt.isFinished(); i++) {
            canisterBolt.update(DT, hall, null, 1f, List.of(canister), Collections.emptyList(), effects);
        }
        assertTrue(canisterBolt.isFinished());
        assertEquals(Target.Kind.CANISTER.maxHealth - 18f, canister.getHealth(), 1e-4f);
    }

    @Test
    void scalesDamageAgainstPlayerAndGeneratesDirectionalHurt() {
        GameMap hall = GameMap.parse(
            "########",
            "#>.....#",
            "########");
        Player player = new Player(4.5f, 1.5f, 0f); // facing north, hit from west
        EnemyProjectile bolt = new EnemyProjectile(new Vector3(3.0f, 0.44f, 1.5f),
            new Vector3(1f, 0f, 0f), 8f, 15f, null);
        for (int i = 0; i < 60 && !bolt.isFinished(); i++) {
            bolt.update(DT, hall, player, 1.4f, Collections.emptyList(), Collections.emptyList(), new ArrayList<>());
        }
        assertTrue(bolt.isFinished());
        assertEquals(Player.MAX_HEALTH - 21f, player.getHealth(), 1e-4f);
        assertTrue(player.hasDirectionalHurt());
        assertEquals(-90f, player.getHurtRelativeYaw(), 5f);
    }

    @Test
    void lowBoltHitsGroundedPlayerButPassesHarmlesslyUnderJumpingPlayer() {
        GameMap hall = GameMap.parse(
            "##########",
            "#>.......#",
            "##########");
        Player grounded = new Player(4.5f, 1.5f, 0f);
        EnemyProjectile lowHit = new EnemyProjectile(new Vector3(3.5f, 0.16f, 1.5f),
            new Vector3(1f, 0f, 0f), 8f, 20f, null);
        for (int i = 0; i < 30 && !lowHit.isFinished(); i++) {
            lowHit.update(DT, hall, grounded, 1f, Collections.emptyList(), Collections.emptyList(), new ArrayList<>());
        }
        assertEquals(Player.MAX_HEALTH - 20f, grounded.getHealth(), 1e-4f);

        Player jumping = new Player(4.5f, 1.5f, 0f);
        assertTrue(jumping.tryJump());
        while (jumping.getVerticalVelocity() > 0f) {
            jumping.move(hall, 0f, 0f, DT);
        }
        assertTrue(jumping.getFeetY() > 0.16f + EnemyProjectile.RADIUS);

        EnemyProjectile lowMiss = new EnemyProjectile(new Vector3(3.5f, 0.16f, 1.5f),
            new Vector3(1f, 0f, 0f), 8f, 20f, null);
        for (int i = 0; i < 30 && !lowMiss.isFinished(); i++) {
            lowMiss.update(DT, hall, jumping, 1f, Collections.emptyList(), Collections.emptyList(), new ArrayList<>());
        }
        assertEquals(Player.MAX_HEALTH, jumping.getHealth(), 1e-4f, "airborne player clears low bolt");
    }

    @Test
    void grazingPlayerHitReportsUpstreamBoltDirectionInsteadOfRimContactAngle() {
        GameMap room = GameMap.parse(
            "########",
            "#......#",
            "#>.....#",
            "#......#",
            "########");
        Player player = new Player(4.5f, 2.5f, 0f); // facing north; shooter is due west (-90 relative yaw)
        float grazingZ = 2.5f + 0.31f; // just inside Player.RADIUS + RADIUS = 0.325f on southern rim
        EnemyProjectile grazingBolt = new EnemyProjectile(
            new Vector3(2.0f, 0.44f, grazingZ), new Vector3(1f, 0f, 0f), 8f, 15f, null);

        for (int i = 0; i < 60 && !grazingBolt.isFinished(); i++) {
            grazingBolt.update(DT, room, player, 1f,
                Collections.emptyList(), Collections.emptyList(), new ArrayList<>());
        }

        assertTrue(grazingBolt.isFinished());
        assertEquals(Player.MAX_HEALTH - 15f, player.getHealth(), 1e-4f);
        assertTrue(player.hasDirectionalHurt());
        assertEquals(-90f, player.getHurtRelativeYaw(), 0.5f,
            "hurt vignette points upstream toward shooter in the west, not the grazed southern rim");

        // Purely vertical bolt falls back to shooter/origin horizontal coordinates.
        Player underShooter = new Player(4.5f, 2.5f, 0f);
        Enemy overheadOwner = new Enemy(Enemy.Kind.DRONE, 2.5f, 2.5f, 90f);
        EnemyProjectile verticalBolt = new EnemyProjectile(
            new Vector3(4.5f, 0.65f, 2.5f), new Vector3(0f, -1f, 0f), 6f, 10f, overheadOwner);
        assertTrue(verticalBolt.update(DT, room, underShooter, 1f,
            Collections.emptyList(), List.of(overheadOwner), new ArrayList<>()));
        assertTrue(underShooter.hasDirectionalHurt());
        assertEquals(-90f, underShooter.getHurtRelativeYaw(), 0.5f);
    }

    @Test
    void stopsAtInitialSpawnOverlapForSurfacesAndEntitiesWhileIgnoringOwner() {
        GameMap hall = GameMap.parse(
            "##########",
            "#>.......#",
            "##########");
        Door retracting = new Door(Door.LockKind.NONE, Door.Orientation.SPAN_Z, 3, 1);
        retracting.open();
        retracting.update(0.82f * Door.OPEN_DURATION);

        // Spawn near the east face of the 3D door slab so a 0.06u step on i=0 would tunnel past it.
        float doorSpawnX = retracting.getX() + Door.HALF_THICKNESS + 0.04f;
        float doorSpawnY = Math.min(0.88f, retracting.getBottomY() + 0.02f);
        assertTrue(retracting.overlapsSphere(doorSpawnX, doorSpawnY, 1.5f, EnemyProjectile.RADIUS));

        List<Effect> doorEffects = new ArrayList<>();
        EnemyProjectile doorBolt = new EnemyProjectile(
            new Vector3(doorSpawnX, doorSpawnY, 1.5f), new Vector3(1f, 0f, 0f), 8f, 12f, null);
        assertTrue(doorBolt.update(DT, hall, null, 1f, Collections.emptyList(), Collections.emptyList(),
            List.of(retracting), Collections.emptyList(), doorEffects));
        assertEquals(doorSpawnX, doorBolt.position.x, 1e-6f);
        assertEquals(1, doorEffects.size());

        // Spawn overlapping a target's far edge so i=0 would step outside the target without t=0 check.
        Target canister = new Target(Target.Kind.CANISTER, 5.0f, 1.5f);
        float targetSpawnX = 5.0f + canister.getRadius() + 0.04f;
        EnemyProjectile targetBolt = new EnemyProjectile(
            new Vector3(targetSpawnX, 0.35f, 1.5f), new Vector3(1f, 0f, 0f), 8f, 12f, null);
        assertTrue(targetBolt.update(DT, hall, null, 1f, List.of(canister), Collections.emptyList(),
            new ArrayList<>()));
        assertEquals(Target.Kind.CANISTER.maxHealth - 12f, canister.getHealth(), 1e-4f);

        // Spawn overlapping a SecurityConsole stops immediately at t=0.
        SecurityConsole console = new SecurityConsole(6.0f, 1.5f);
        float consoleSpawnX = 6.0f + console.getRadius() + 0.04f;
        EnemyProjectile consoleBolt = new EnemyProjectile(
            new Vector3(consoleSpawnX, 0.4f, 1.5f), new Vector3(1f, 0f, 0f), 8f, 12f, null);
        assertTrue(consoleBolt.update(DT, hall, null, 1f, Collections.emptyList(), Collections.emptyList(),
            Collections.emptyList(), List.of(console), new ArrayList<>()));
        assertEquals(consoleSpawnX, consoleBolt.position.x, 1e-6f);
    }

    @Test
    void bisectsWallDoorFloorAndCeilingImpactsSoSparksSpawnOnContactSurface() {
        GameMap hall = GameMap.parse(
            "##########",
            "#>.......#",
            "##########");

        // Wall impact at x = 9.0 -> contact x = 9.0 - RADIUS.
        List<Effect> wallEffects = new ArrayList<>();
        EnemyProjectile wallBolt = new EnemyProjectile(
            new Vector3(8.5f, 0.5f, 1.5f), new Vector3(1f, 0f, 0f), 9f, 10f, null);
        assertTrue(wallBolt.update(1f, hall, null, 1f,
            Collections.emptyList(), Collections.emptyList(), wallEffects));
        float wallContact = 9f - EnemyProjectile.RADIUS;
        assertTrue(wallBolt.position.x <= wallContact);
        assertTrue(wallContact - wallBolt.position.x < 0.002f,
            "spark bisected to wall surface, gap=" + (wallContact - wallBolt.position.x));
        assertEquals(wallBolt.position.x, wallEffects.get(0).position.x, 1e-6f);

        // Closed bulkhead door impact at x = 4.5 - HALF_THICKNESS - RADIUS.
        Door door = new Door(Door.LockKind.NONE, Door.Orientation.SPAN_Z, 4, 1);
        List<Effect> doorEffects = new ArrayList<>();
        EnemyProjectile doorBolt = new EnemyProjectile(
            new Vector3(3.5f, 0.5f, 1.5f), new Vector3(1f, 0f, 0f), 9f, 10f, null);
        assertTrue(doorBolt.update(1f, hall, null, 1f, Collections.emptyList(), Collections.emptyList(),
            List.of(door), Collections.emptyList(), doorEffects));
        float doorContact = door.getX() - Door.HALF_THICKNESS - EnemyProjectile.RADIUS;
        assertTrue(doorBolt.position.x <= doorContact);
        assertTrue(doorContact - doorBolt.position.x < 0.002f);
        assertEquals(doorBolt.position.x, doorEffects.get(0).position.x, 1e-6f);

        // Floor impact -> contact y = RADIUS.
        List<Effect> floorEffects = new ArrayList<>();
        EnemyProjectile floorBolt = new EnemyProjectile(
            new Vector3(2.5f, 0.5f, 1.5f), new Vector3(1f, -1f, 0f), 9f, 10f, null);
        assertTrue(floorBolt.update(1f, hall, null, 1f,
            Collections.emptyList(), Collections.emptyList(), floorEffects));
        assertTrue(floorBolt.position.y > EnemyProjectile.RADIUS);
        assertTrue(floorBolt.position.y - EnemyProjectile.RADIUS < 0.002f);
        assertEquals(floorBolt.position.y, floorEffects.get(0).position.y, 1e-6f);

        // Ceiling impact -> contact y = WALL_HEIGHT - RADIUS.
        List<Effect> ceilingEffects = new ArrayList<>();
        EnemyProjectile ceilingBolt = new EnemyProjectile(
            new Vector3(2.5f, 0.5f, 1.5f), new Vector3(1f, 1f, 0f), 9f, 10f, null);
        assertTrue(ceilingBolt.update(1f, hall, null, 1f,
            Collections.emptyList(), Collections.emptyList(), ceilingEffects));
        float ceilingContact = GameMap.WALL_HEIGHT - EnemyProjectile.RADIUS;
        assertTrue(ceilingBolt.position.y < ceilingContact);
        assertTrue(ceilingContact - ceilingBolt.position.y < 0.002f);
        assertEquals(ceilingBolt.position.y, ceilingEffects.get(0).position.y, 1e-6f);
    }

    @Test
    void guardsNonFiniteAndNegativeSpeedOrDtAndExpiresZeroSpeedBolts() {
        GameMap hall = GameMap.parse(
            "##########",
            "#>.......#",
            "##########");
        EnemyProjectile normal = new EnemyProjectile(
            new Vector3(2.5f, 0.5f, 1.5f), new Vector3(1f, 0f, 0f), 8f, 10f, null);
        for (float badDt : new float[]{0f, -0.25f, Float.NaN, Float.POSITIVE_INFINITY, Float.NEGATIVE_INFINITY}) {
            assertFalse(normal.update(badDt, hall, null, 1f,
                Collections.emptyList(), Collections.emptyList(), new ArrayList<>()));
            assertFalse(normal.isFinished());
            assertEquals(2.5f, normal.position.x, 1e-6f);
        }

        for (float badSpeed : new float[]{0f, -6f, Float.NaN, Float.POSITIVE_INFINITY, Float.NEGATIVE_INFINITY}) {
            List<Effect> effects = new ArrayList<>();
            EnemyProjectile inert = new EnemyProjectile(
                new Vector3(2.5f, 0.5f, 1.5f), new Vector3(1f, 0f, 0f), badSpeed, 10f, null);
            assertFalse(inert.update(DT, hall, null, 1f,
                Collections.emptyList(), Collections.emptyList(), effects));
            assertTrue(Float.isFinite(inert.position.x));
            assertEquals(2.5f, inert.position.x, 1e-6f, "never flies backward or corrupts to NaN");
            assertFalse(inert.isFinished());

            assertTrue(inert.update(EnemyProjectile.MAX_LIFETIME, hall, null, 1f,
                Collections.emptyList(), Collections.emptyList(), effects));
            assertTrue(inert.isFinished(), "zero/invalid-speed bolt expires instead of leaking forever");
            assertTrue(effects.isEmpty(), "expiring stationary bolt does not spawn mid-air impact spark");
        }
    }
}

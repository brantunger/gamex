package io.github.brantunger.gamex.world;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.badlogic.gdx.Gdx;
import io.github.brantunger.gamex.TestTuning;
import io.github.brantunger.gamex.data.AssetText;
import io.github.brantunger.gamex.player.Player;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Collections;

/** Checks the enemy and Warden tuning files: what they ship with, what a changed value does, and their errors. */
class EnemyTuningTest {

    private static final float DT = 1f / 60f;

    private com.badlogic.gdx.Files previousFiles;
    private String previousAssets;

    /** The files are read without libGDX, from the folder {@code gamex.assets} names, whatever a test before left. */
    @BeforeEach
    void saveGlobals() {
        previousFiles = Gdx.files;
        previousAssets = System.getProperty("gamex.assets");
        Gdx.files = null;
    }

    @AfterEach
    void restoreGlobals() {
        Gdx.files = previousFiles;
        if (previousAssets == null) {
            System.clearProperty("gamex.assets");
        } else {
            System.setProperty("gamex.assets", previousAssets);
        }
    }

    private static String enemiesText() {
        return AssetText.read("data", EnemyTuning.PATH);
    }

    private static String wardenText() {
        return AssetText.read("data", WardenTuning.PATH);
    }

    private static void assertKind(Enemy.Kind kind, float health, float moveSpeed, float railDamageScale,
                                   boolean hazardDamage, boolean avoidsHazards, boolean boss, float deathBurstSize,
                                   float deathSoundGain, float deathNoiseRange, float deathNoiseMuffledRange) {
        EnemyTuning.KindStats stats = TestTuning.ENEMIES.of(kind);
        assertEquals(health, stats.maxHealth, 0f, kind + " health");
        assertEquals(moveSpeed, stats.moveSpeed, 0f, kind + " move speed");
        assertEquals(railDamageScale, stats.railDamageScale, 0f, kind + " rail damage scale");
        assertEquals(hazardDamage, stats.hazardDamage, kind + " hazard damage");
        assertEquals(avoidsHazards, stats.avoidsHazards, kind + " avoids hazards");
        assertEquals(boss, stats.boss, kind + " boss");
        assertEquals(deathBurstSize, stats.deathBurstSize, 0f, kind + " death burst size");
        assertEquals(deathSoundGain, stats.deathSoundGain, 0f, kind + " death sound gain");
        assertEquals(deathNoiseRange, stats.deathNoiseRange, 0f, kind + " death noise range");
        assertEquals(deathNoiseMuffledRange, stats.deathNoiseMuffledRange, 0f, kind + " death noise muffled range");
    }

    /**
     * The values on the right are the constants and literals the code held before the files existed (commit
     * 10cd4a4), so moving them changed no behaviour. A deliberate rebalance changes the file and this test together.
     */
    @Test
    void theShippedEnemyFileHoldsTheValuesTheCodeHadBeforeIt() {
        // Before the file a kind's death burst was its height times 0.8, and the rail did 120 or, to the Warden, 90.
        assertKind(Enemy.Kind.DRONE, 40f, 3.5f, 1f, false, false, false, Enemy.Kind.DRONE.height * 0.8f, 1.0f, 5.0f,
            3.0f);
        assertKind(Enemy.Kind.TURRET, 80f, 0f, 1f, false, false, false, Enemy.Kind.TURRET.height * 0.8f, 1.0f, 5.0f,
            3.0f);
        assertKind(Enemy.Kind.GUARD, 100f, 2.15f, 1f, true, true, false, Enemy.Kind.GUARD.height * 0.8f, 1.0f, 5.0f,
            3.0f);
        assertKind(Enemy.Kind.WARDEN, 900f, 1.30f, 90f / 120f, false, false, true, 1.8f, 1.25f, 10.0f, 6.0f);

        EnemyTuning t = TestTuning.ENEMIES;
        assertEquals(0.15f, t.hitFlashTime, 0f);
        assertEquals(15f, t.sightRange, 0f);
        assertEquals(3.2f, t.closeSenseRange, 0f);
        assertEquals(200f, t.fovDegrees, 0f);
        assertEquals((float) Math.cos(Math.toRadians(100.0)), t.fovCos, 0f);
        assertEquals(6.0f, t.aggroMemoryTime, 0f);
        assertEquals(1.25f, t.aggroSightScale, 0f);
        assertEquals(0.25f, t.wakeCooldown, 0f);

        assertEquals(150f, t.turretTurnRate, 0f);
        assertEquals(0.35f, t.turretWindupTime, 0f);
        assertEquals(3, t.turretBurstCount);
        assertEquals(0.11f, t.turretBurstInterval, 0f);
        assertEquals(1.35f, t.turretCooldown, 0f);
        assertEquals(8.5f, t.turretBoltSpeed, 0f);
        assertEquals(12f, t.turretBoltDamage, 0f);
        assertEquals(28f, t.turretMaxPitch, 0f);
        assertEquals(90f, t.turretPitchRate, 0f);
        assertEquals(60f, t.turretPitchReturnRate, 0f);
        assertEquals(0.3f, t.turretLostSightCooldown, 0f);
        assertEquals(18f, t.turretFireArc, 0f);

        assertEquals(0.28f, t.guardWindupTime, 0f);
        assertEquals(1.15f, t.guardCooldown, 0f);
        assertEquals(7.2f, t.guardBoltSpeed, 0f);
        assertEquals(18f, t.guardBoltDamage, 0f);
        assertEquals(3.5f, t.guardMinRange, 0f);
        assertEquals(7.5f, t.guardMaxRange, 0f);
        assertEquals(5.5f, t.guardPreferredRange, 0f);
        assertEquals(0.2f, t.guardRangeAdjustGain, 0f);
        assertEquals(15.5f, t.guardAttackRange, 0f);
        assertEquals(0.35f, t.guardAdvanceStrafe, 0f);
        assertEquals(0.8f, t.guardRetreatBack, 0f);
        assertEquals(0.6f, t.guardRetreatStrafe, 0f);
        assertEquals(1.8f, t.guardStrafeSwitchTime, 0f);
        assertEquals(0.45f, t.guardWindupMoveScale, 0f);
        assertEquals(420f, t.guardTurnRate, 0f);
        assertEquals(360f, t.guardPathTurnRate, 0f);
        assertEquals(7.5f, t.guardWalkPhaseRate, 0f);
        assertEquals(28f, t.guardFireArc, 0f);
        assertEquals(35f, t.guardBoltMaxPitch, 0f);

        assertEquals(0.95f, t.droneTriggerRange, 0f);
        assertEquals(0.26f, t.droneWindupTime, 0f);
        assertEquals(0.18f, t.droneLungeTime, 0f);
        assertEquals(8.5f, t.droneLungeSpeed, 0f);
        assertEquals(22f, t.droneLungeDamage, 0f);
        assertEquals(0.12f, t.droneContactMargin, 0f);
        assertEquals(0.50f, t.droneRecoverTime, 0f);
        assertEquals(0.25f, t.droneCooldown, 0f);
        assertEquals(540f, t.droneTurnRate, 0f);
    }

    @Test
    void theShippedWardenFileHoldsTheValuesTheCodeHadBeforeIt() {
        WardenTuning w = TestTuning.WARDEN;
        assertEquals(0.5f, w.overdriveHealthRatio, 0f);
        assertEquals(3.70f, w.runSpeed, 0f);
        assertEquals(0.5f, w.chaseMemory, 0f);
        assertEquals(320f, w.turnRate, 0f);
        assertEquals(280f, w.pathTurnRate, 0f);
        assertEquals(0.90f, w.attackRange, 0f);
        assertEquals(15f, w.attackFacing, 0f);
        assertEquals(2.4666667f, w.comboTime, 0f);
        assertEquals(1.10f, w.punchReach, 0f);
        assertEquals(50f, w.punchArc, 0f);
        assertEquals(45f, w.comboTurnRate, 0f);
        assertEquals(0.22f, w.telegraphTime, 0f);
        assertEquals(0.90f, w.recoverTime, 0f);
        assertEquals(1.15f, w.punchSoundGain, 0f);
        assertEquals(1.25f, w.overdriveSpeedScale, 0f);
        assertEquals(1.50f, w.overdriveTurnScale, 0f);
        assertEquals(1.35f, w.overdriveDamageScale, 0f);
        assertEquals(1.15f, w.overdriveComboRate, 0f);
        assertEquals(0.60f, w.overdriveRecoverScale, 0f);
        assertEquals(1.25f, w.overdriveSoundGain, 0f);
        assertEquals(1.6f, w.overdriveBurstSize, 0f);

        float[] times = {0.60f, 1.10f, 1.27f};
        float[] damage = {16f, 16f, 28f};
        float[] lungeDistance = {2.30f, 0.70f, 0.70f};
        float[] lungeTime = {0.30f, 0.20f, 0.14f};
        assertEquals(3, w.punchCount());
        for (int i = 0; i < 3; i++) {
            assertEquals(times[i], w.punchTime(i), 0f, "time of punch " + i);
            assertEquals(damage[i], w.punchDamage(i), 0f, "damage of punch " + i);
            assertEquals(lungeDistance[i], w.lungeDistance(i), 0f, "lunge distance of punch " + i);
            assertEquals(lungeTime[i], w.lungeTime(i), 0f, "lunge time of punch " + i);
        }
    }

    @Test
    void aChangedHealthOrSpeedIsWhatASpawnedEnemyHas() {
        EnemyTuning tuned = TestTuning.enemiesWith("guard.health", "55");
        Enemy guard = new Enemy(tuned, Enemy.Kind.GUARD, 2.5f, 1.5f, 90f);
        assertEquals(55f, guard.getHealth(), 0f);
        assertEquals(55f, guard.getMaxHealth(), 0f);
        assertEquals(100f, new Enemy(TestTuning.ENEMIES, Enemy.Kind.GUARD, 2.5f, 1.5f, 90f).getHealth(), 0f);

        // A drone with a third of its speed covers a third of the corridor in the same time.
        GameMap corridor = GameMap.parse(
            "####################",
            "#>.................#",
            "####################");
        float[] travelled = new float[2];
        EnemyTuning[] tunings = {TestTuning.ENEMIES, TestTuning.enemiesWith("drone.move_speed", "1.0")};
        for (int i = 0; i < 2; i++) {
            Enemy drone = new Enemy(tunings[i], Enemy.Kind.DRONE, 15.5f, 1.5f, 270f);
            Player player = new Player(1.5f, 1.5f, 90f);
            drone.alert();
            for (int step = 0; step < 60; step++) {
                drone.update(DT, corridor, new Pathfinder(), player, TestTuning.NORMAL, Collections.emptyList(), null);
            }
            travelled[i] = 15.5f - drone.getX();
        }
        assertEquals(3.5f, travelled[0], 0.1f);
        assertEquals(1.0f, travelled[1], 0.05f);
    }

    @Test
    void theDetectionConeIsReadAsAnAngleAndItsCosineWorkedOut() {
        EnemyTuning narrow = TestTuning.enemiesWith("perception.fov_degrees", "90");
        assertEquals(90f, narrow.fovDegrees, 0f);
        assertEquals((float) Math.cos(Math.toRadians(45.0)), narrow.fovCos, 0f);

        // A player 60 degrees off the facing, beyond the close sense: inside the shipped 200-degree cone, outside
        // a 90-degree one.
        GameMap room = GameMap.parse(
            "############",
            "#>.........#",
            "#..........#",
            "#..........#",
            "#..........#",
            "#..........#",
            "#..........#",
            "#..........#",
            "#..........#",
            "#..........#",
            "#..........#",
            "############");
        float angle = (float) Math.toRadians(60.0);
        Player player = new Player(2.5f + 6f * (float) Math.sin(angle), 9.5f - 6f * (float) Math.cos(angle), 0f);
        Enemy wide = new Enemy(TestTuning.ENEMIES, Enemy.Kind.TURRET, 2.5f, 9.5f, 0f);
        Enemy tight = new Enemy(narrow, Enemy.Kind.TURRET, 2.5f, 9.5f, 0f);
        wide.update(DT, room, new Pathfinder(), player, TestTuning.NORMAL, Collections.emptyList(), null);
        tight.update(DT, room, new Pathfinder(), player, TestTuning.NORMAL, Collections.emptyList(), null);
        assertEquals(Enemy.State.PURSUING, wide.getState());
        assertEquals(Enemy.State.DORMANT, tight.getState());
    }

    @Test
    void changedPunchDamageIsWhatTheComboDoes() {
        GameMap map = GameMap.parse(
            "##########",
            "#>.......#",
            "##########");
        float[] lost = new float[2];
        EnemyTuning[] tunings = {TestTuning.ENEMIES, TestTuning.wardenWith("punch_damage", "5, 6, 7")};
        for (int i = 0; i < 2; i++) {
            Enemy warden = new Enemy(tunings[i], Enemy.Kind.WARDEN, 4.5f, 1.5f, 90f);
            Player player = new Player(5.3f, 1.5f, 270f);
            // One combo and no more: the clip is 2.47 s long and the next one cannot start for 0.9 s after it.
            for (int step = 0; step < 170; step++) {
                warden.update(DT, map, new Pathfinder(), player, TestTuning.NORMAL, Collections.emptyList(), null);
            }
            assertEquals(3, warden.getPunchesLanded());
            lost[i] = Player.MAX_HEALTH - player.getHealth();
        }
        assertEquals(60f, lost[0], 1e-3f);
        assertEquals(18f, lost[1], 1e-3f);
    }

    @Test
    void theOverdriveThresholdComesFromTheWardenFile() {
        Enemy shipped = new Enemy(TestTuning.ENEMIES, Enemy.Kind.WARDEN, 4.5f, 1.5f, 90f);
        Enemy early = new Enemy(TestTuning.wardenWith("overdrive_health_ratio", "0.9"), Enemy.Kind.WARDEN, 4.5f, 1.5f,
            90f);
        shipped.damage(100f);
        early.damage(100f);
        assertFalse(shipped.isOverdrive());
        assertTrue(early.isOverdrive(), "800 of 900 health is below nine tenths");
        assertTrue(early.consumeOverdriveTriggered());
    }

    @Test
    void loadReadsAnEditedCopyOfTheFilesWithNoRebuild(@TempDir Path temp) throws IOException {
        Path data = Files.createDirectories(temp.resolve("data"));
        Files.write(data.resolve("enemies.txt"),
            TestTuning.withValue(enemiesText(), "guard.health", "250").getBytes(StandardCharsets.UTF_8));
        Files.write(data.resolve("warden.txt"),
            TestTuning.withValue(wardenText(), "run_speed", "4.5").getBytes(StandardCharsets.UTF_8));
        System.setProperty("gamex.assets", temp.toString());
        EnemyTuning edited = EnemyTuning.load();
        assertEquals(250f, edited.of(Enemy.Kind.GUARD).maxHealth, 0f);
        assertEquals(4.5f, edited.warden.runSpeed, 0f);

        if (previousAssets == null) {
            System.clearProperty("gamex.assets");
        } else {
            System.setProperty("gamex.assets", previousAssets);
        }
        assertEquals(100f, EnemyTuning.load().of(Enemy.Kind.GUARD).maxHealth, 0f, "the shipped file is read again");
    }

    private static String enemiesError(String key, String value) {
        String text = TestTuning.withValue(enemiesText(), key, value);
        return assertThrows(IllegalArgumentException.class,
            () -> EnemyTuning.parse(EnemyTuning.PATH, text, TestTuning.WARDEN)).getMessage();
    }

    private static String wardenError(String key, String value) {
        String text = TestTuning.withValue(wardenText(), key, value);
        return assertThrows(IllegalArgumentException.class,
            () -> WardenTuning.parse(WardenTuning.PATH, text)).getMessage();
    }

    private static void assertNames(String message, String file, String key) {
        assertTrue(message.contains(file), message);
        assertTrue(message.contains("'" + key + "'"), message);
    }

    @Test
    void aMissingUnknownOrMistypedKeyFailsNamingTheFileAndTheKey() {
        assertEquals("Missing 'guard.health' key in data/enemies.txt", enemiesError("guard.health", null));
        assertEquals("Missing 'punch_reach' key in data/warden.txt", wardenError("punch_reach", null));

        String unknown = assertThrows(IllegalArgumentException.class, () -> EnemyTuning.parse(EnemyTuning.PATH,
            "guard.helth: 100\n" + enemiesText(), TestTuning.WARDEN)).getMessage();
        assertEquals("Unknown key 'guard.helth' in data/enemies.txt at line 1", unknown);

        String message = enemiesError("drone.lunge_speed", "8.5f");
        assertNames(message, "data/enemies.txt", "drone.lunge_speed");
        assertTrue(message.contains("at line "), message);
        assertNames(enemiesError("guard.avoids_hazards", "yes"), "data/enemies.txt", "guard.avoids_hazards");
        assertNames(enemiesError("turret.burst_count", "2.5"), "data/enemies.txt", "turret.burst_count");
        assertNames(wardenError("punch_damage", "16, 16"), "data/warden.txt", "punch_damage");
        assertNames(wardenError("lunge_time", "0.30, 0.20, 0.14, 0.1"), "data/warden.txt", "lunge_time");
    }

    /** Only the guard's movement steps round shock grates, so no other kind may be told to. */
    @Test
    void avoidsHazardsIsRejectedForEveryKindButTheGuard() {
        for (String kind : new String[] {"drone", "turret", "warden"}) {
            String key = kind + ".avoids_hazards";
            String message = enemiesError(key, "true");
            assertTrue(message.startsWith("Invalid '" + key + "' in data/enemies.txt at line "), message);
            assertTrue(message.endsWith(": must be false: only the guard steps round shock grates, got 'true'"),
                message);
        }
        assertTrue(TestTuning.enemiesWith("guard.avoids_hazards", "true").of(Enemy.Kind.GUARD).avoidsHazards);
        assertFalse(TestTuning.enemiesWith("guard.avoids_hazards", "false").of(Enemy.Kind.GUARD).avoidsHazards);
    }

    @Test
    void aValueOutOfRangeFailsNamingTheFileTheKeyAndTheLine() {
        String message = enemiesError("guard.health", "0");
        assertNames(message, "data/enemies.txt", "guard.health");
        assertTrue(message.contains("at line ") && message.contains("must be positive"), message);

        assertNames(enemiesError("drone.move_speed", "-1"), "data/enemies.txt", "drone.move_speed");
        assertNames(enemiesError("perception.fov_degrees", "0"), "data/enemies.txt", "perception.fov_degrees");
        assertNames(enemiesError("perception.fov_degrees", "361"), "data/enemies.txt", "perception.fov_degrees");
        assertNames(enemiesError("perception.hit_flash_time", "0"), "data/enemies.txt", "perception.hit_flash_time");
        assertNames(enemiesError("turret.burst_count", "0"), "data/enemies.txt", "turret.burst_count");
        assertNames(enemiesError("guard.max_range", "3"), "data/enemies.txt", "guard.max_range");

        assertNames(wardenError("overdrive_health_ratio", "1.5"), "data/warden.txt", "overdrive_health_ratio");
        assertNames(wardenError("overdrive_health_ratio", "-0.1"), "data/warden.txt", "overdrive_health_ratio");
        assertNames(wardenError("combo_time", "0"), "data/warden.txt", "combo_time");
        assertNames(wardenError("punch_times", "1.10, 0.60, 1.27"), "data/warden.txt", "punch_times");
        assertNames(wardenError("punch_times", "0.60, 1.10, 2.5"), "data/warden.txt", "punch_times");
        assertNames(wardenError("punch_damage", "16, -1, 28"), "data/warden.txt", "punch_damage");
        assertNames(wardenError("lunge_distance", "-2.30, 0.70, 0.70"), "data/warden.txt", "lunge_distance");
        assertNames(wardenError("lunge_time", "0.30, 0, 0.14"), "data/warden.txt", "lunge_time");
        assertNames(wardenError("telegraph_time", "0"), "data/warden.txt", "telegraph_time");
        assertNames(wardenError("overdrive_combo_rate", "0"), "data/warden.txt", "overdrive_combo_rate");
    }

    @Test
    void tuningIsRequired() {
        assertThrows(IllegalArgumentException.class, () -> new Enemy(null, Enemy.Kind.GUARD, 1.5f, 1.5f, 0f));
        assertThrows(IllegalArgumentException.class,
            () -> EnemyTuning.parse(EnemyTuning.PATH, enemiesText(), null));
    }
}

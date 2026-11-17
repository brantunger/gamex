package io.github.brantunger.gamex.weapon;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.badlogic.gdx.math.Vector3;
import io.github.brantunger.gamex.player.Player;
import io.github.brantunger.gamex.world.Effect;
import io.github.brantunger.gamex.world.Enemy;
import io.github.brantunger.gamex.world.GameMap;
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
}

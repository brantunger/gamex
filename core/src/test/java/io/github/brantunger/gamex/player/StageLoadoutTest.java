package io.github.brantunger.gamex.player;

import static org.junit.jupiter.api.Assertions.assertEquals;

import io.github.brantunger.gamex.TestTuning;
import io.github.brantunger.gamex.weapon.WeaponSlot;
import io.github.brantunger.gamex.weapon.WeaponTuning;
import io.github.brantunger.gamex.world.Levels;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

/** Covers {@link PlayerLoadout#forStage}: the loadout comes from the stage's file, not from its campaign position. */
class StageLoadoutTest {

    private static final Difficulty[] DIFFICULTIES = {Difficulty.EASY, Difficulty.NORMAL, Difficulty.HARD, null};

    /**
     * The loadout by campaign position, as {@code forStage} worked it out before the stage files listed their
     * weapons: the pistol for the first stage, the Scattergun for the next two, both weapons from the fourth on.
     */
    private static PlayerLoadout byCampaignPosition(int stageIndex, Difficulty difficulty, DifficultyTuning tuning,
                                                    WeaponTuning weapons) {
        Difficulty safe = difficulty == null ? Difficulty.NORMAL : difficulty;
        float pickupScale = tuning.of(safe).pickupScale;
        if (stageIndex <= 0) return PlayerLoadout.fresh(safe);
        int starterShells = Math.min(weapons.scattergunMaxShells,
            Math.max(1, Math.round(weapons.scattergunWeaponPickupShells * pickupScale)));
        if (stageIndex < 3) {
            return new PlayerLoadout(Player.MAX_HEALTH, 0f, safe, true, starterShells,
                false, 0, WeaponSlot.SCATTERGUN);
        }
        int starterSlugs = Math.min(weapons.railMaxSlugs,
            Math.max(1, Math.round(weapons.railWeaponPickupSlugs * pickupScale)));
        return new PlayerLoadout(Player.MAX_HEALTH, 0f, safe, true, starterShells,
            true, starterSlugs, WeaponSlot.SCATTERGUN);
    }

    private static void assertLoadout(PlayerLoadout expected, PlayerLoadout actual, String message) {
        assertEquals(expected.health, actual.health, 1e-4f, message + ": health");
        assertEquals(expected.armor, actual.armor, 1e-4f, message + ": armor");
        assertEquals(expected.difficulty, actual.difficulty, message + ": difficulty");
        assertEquals(expected.hasScattergun, actual.hasScattergun, message + ": scattergun");
        assertEquals(expected.shells, actual.shells, message + ": shells");
        assertEquals(expected.hasRailLance, actual.hasRailLance, message + ": rail lance");
        assertEquals(expected.slugs, actual.slugs, message + ": slugs");
        assertEquals(expected.activeWeapon, actual.activeWeapon, message + ": weapon");
    }

    private static Levels.Stage stageWithHeader(String loadoutHeader) {
        return Levels.parseStage("levels/custom.map", "id: s1\ntitle: S1\npar: 30\ncomplete_on_all_targets: false\n"
            + loadoutHeader + "briefing: GO\n\n#####\n#.>.#\n#####\n");
    }

    private static PlayerLoadout normal(Levels.Stage stage) {
        return PlayerLoadout.forStage(stage, Difficulty.NORMAL, TestTuning.DIFFICULTY, TestTuning.WEAPONS);
    }

    @Test
    void everyCampaignStageStartsAsItDidWhenItsPositionChoseTheLoadout() {
        List<Levels.Stage> campaign = Levels.campaign();
        assertEquals(6, campaign.size());
        // A scarce and a generous weapon file as well, so the ammunition rule is compared and not only 8 and 5.
        WeaponTuning[] weaponFiles = {
            TestTuning.WEAPONS,
            TestTuning.weaponsWith("scattergun.weapon_pickup_shells", "20"),
            TestTuning.weaponsWith("rail.weapon_pickup_slugs", "1")
        };
        for (int i = 0; i < campaign.size(); i++) {
            for (Difficulty difficulty : DIFFICULTIES) {
                for (WeaponTuning weapons : weaponFiles) {
                    assertLoadout(byCampaignPosition(i, difficulty, TestTuning.DIFFICULTY, weapons),
                        PlayerLoadout.forStage(campaign.get(i), difficulty, TestTuning.DIFFICULTY, weapons),
                        campaign.get(i).id + " on " + difficulty);
                }
            }
        }
    }

    @Test
    void aReorderedCampaignKeepsEachStagesLoadout() {
        List<Levels.Stage> shipped = Levels.campaign();
        List<String> files = new ArrayList<>(List.of("01-outer-breach.map", "02-coolant-core.map",
            "03-security-hub.map", "04-lockdown-wing.map", "05-command-vault.map", "06-warden-core.map"));
        Collections.reverse(files);
        List<Levels.Stage> reversed = Levels.parseCampaign("levels/reversed.txt", String.join("\n", files),
            Levels::loadStage);
        assertEquals("06_warden_core", reversed.get(0).id);

        for (int i = 0; i < reversed.size(); i++) {
            int shippedIndex = shipped.size() - 1 - i;
            assertEquals(shipped.get(shippedIndex).id, reversed.get(i).id);
            for (Difficulty difficulty : DIFFICULTIES) {
                assertLoadout(
                    byCampaignPosition(shippedIndex, difficulty, TestTuning.DIFFICULTY, TestTuning.WEAPONS),
                    PlayerLoadout.forStage(reversed.get(i), difficulty, TestTuning.DIFFICULTY, TestTuning.WEAPONS),
                    reversed.get(i).id + " at place " + i + " on " + difficulty);
            }
        }
    }

    @Test
    void aStageWithoutALoadoutHeaderStartsWithThePistolAlone() {
        assertLoadout(PlayerLoadout.fresh(Difficulty.HARD),
            PlayerLoadout.forStage(stageWithHeader(""), Difficulty.HARD, TestTuning.DIFFICULTY, TestTuning.WEAPONS),
            "no header");
        assertLoadout(PlayerLoadout.fresh(), normal(stageWithHeader("loadout: pistol\n")), "the pistol listed");
        assertLoadout(PlayerLoadout.fresh(), normal(Levels.trainingRange()), "the Training Range");
    }

    @Test
    void theScattergunIsInHandWhenGrantedThenTheRailLanceThenThePistol() {
        PlayerLoadout both = normal(stageWithHeader("loadout: raillance, scattergun\n"));
        assertEquals(WeaponSlot.SCATTERGUN, both.activeWeapon);

        PlayerLoadout scattergun = normal(stageWithHeader("loadout: scattergun\n"));
        assertEquals(WeaponSlot.SCATTERGUN, scattergun.activeWeapon);
        assertEquals(8, scattergun.shells);
        assertEquals(0, scattergun.slugs);

        PlayerLoadout railLance = normal(stageWithHeader("loadout: raillance\n"));
        assertEquals(WeaponSlot.RAIL_LANCE, railLance.activeWeapon);
        assertEquals(false, railLance.hasScattergun);
        assertEquals(0, railLance.shells);
        assertEquals(5, railLance.slugs);

        assertEquals(WeaponSlot.PISTOL, normal(stageWithHeader("")).activeWeapon);
    }
}

package io.github.brantunger.gamex.player;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import io.github.brantunger.gamex.TestTuning;
import io.github.brantunger.gamex.weapon.WeaponSlot;
import io.github.brantunger.gamex.weapon.WeaponTuning;
import io.github.brantunger.gamex.world.Levels;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.EnumSet;
import java.util.List;
import java.util.Set;

/** Covers {@link PlayerLoadout#forStage}: the loadout comes from the stage's file, not from its campaign position. */
class StageLoadoutTest {

    private static final Difficulty[] DIFFICULTIES = {Difficulty.EASY, Difficulty.NORMAL, Difficulty.HARD, null};

    /**
     * The loadout a stage that starts with {@code granted} gives: full health, no armor, each weapon granted with
     * the ammunition of its pickup scaled by the difficulty, and in hand the Scattergun, else the Rail Lance, else
     * the pistol.
     */
    private static PlayerLoadout byWeapons(Set<WeaponSlot> granted, Difficulty difficulty, DifficultyTuning tuning,
                                           WeaponTuning weapons) {
        Difficulty safe = difficulty == null ? Difficulty.NORMAL : difficulty;
        float pickupScale = tuning.of(safe).pickupScale;
        boolean scattergun = granted.contains(WeaponSlot.SCATTERGUN);
        boolean railLance = granted.contains(WeaponSlot.RAIL_LANCE);
        int shells = !scattergun ? 0 : Math.min(weapons.scattergunMaxShells,
            Math.max(1, Math.round(weapons.scattergunWeaponPickupShells * pickupScale)));
        int slugs = !railLance ? 0 : Math.min(weapons.railMaxSlugs,
            Math.max(1, Math.round(weapons.railWeaponPickupSlugs * pickupScale)));
        WeaponSlot inHand = scattergun ? WeaponSlot.SCATTERGUN : railLance ? WeaponSlot.RAIL_LANCE : WeaponSlot.PISTOL;
        return new PlayerLoadout(Player.MAX_HEALTH, 0f, safe, scattergun, shells, railLance, slugs, inHand);
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

    /** Whatever weapons the shipped stage files list, each stage starts with the ones its own file lists. */
    @Test
    void everyCampaignStageStartsWithTheWeaponsItsFileLists() {
        List<Levels.Stage> campaign = Levels.campaign();
        assertFalse(campaign.isEmpty());
        // A scarce and a generous weapon file as well, so the ammunition rule is compared and not only 8 and 5.
        WeaponTuning[] weaponFiles = {
            TestTuning.WEAPONS,
            TestTuning.weaponsWith("scattergun.weapon_pickup_shells", "20"),
            TestTuning.weaponsWith("rail.weapon_pickup_slugs", "1")
        };
        for (Levels.Stage stage : campaign) {
            assertTrue(stage.startingWeapons.contains(WeaponSlot.PISTOL), stage.id + " starts with the pistol");
            for (Difficulty difficulty : DIFFICULTIES) {
                for (WeaponTuning weapons : weaponFiles) {
                    assertLoadout(byWeapons(stage.startingWeapons, difficulty, TestTuning.DIFFICULTY, weapons),
                        PlayerLoadout.forStage(stage, difficulty, TestTuning.DIFFICULTY, weapons),
                        stage.id + " on " + difficulty);
                }
            }
        }
    }

    /** Every list of weapons a header can hold, at every place of a campaign: the header alone sets the loadout. */
    @Test
    void theLoadoutHeaderSetsTheLoadoutWhereverTheStageStandsInTheCampaign() {
        String[] headers = {"", "loadout: scattergun\n", "loadout: raillance\n", "loadout: scattergun, raillance\n"};
        List<Set<WeaponSlot>> granted = List.of(EnumSet.of(WeaponSlot.PISTOL),
            EnumSet.of(WeaponSlot.PISTOL, WeaponSlot.SCATTERGUN), EnumSet.of(WeaponSlot.PISTOL, WeaponSlot.RAIL_LANCE),
            EnumSet.allOf(WeaponSlot.class));
        for (int first = 0; first < headers.length; first++) {
            // The same four stages, each time starting the campaign with another of them.
            List<String> files = new ArrayList<>();
            for (int i = 0; i < headers.length; i++) files.add("stage-" + (first + i) % headers.length + ".map");
            List<Levels.Stage> campaign = Levels.parseCampaign("levels/test-campaign.txt", String.join("\n", files),
                path -> {
                    int header = path.charAt(path.length() - ".map".length() - 1) - '0';
                    return Levels.parseStage(path, "id: s" + header + "\ntitle: S" + header
                        + "\npar: 30\ncomplete_on_all_targets: false\n" + headers[header]
                        + "briefing: GO\n\n#####\n#.>.#\n#####\n");
                });
            assertEquals(headers.length, campaign.size());
            for (int place = 0; place < campaign.size(); place++) {
                int header = (first + place) % headers.length;
                assertEquals("s" + header, campaign.get(place).id);
                for (Difficulty difficulty : DIFFICULTIES) {
                    assertLoadout(byWeapons(granted.get(header), difficulty, TestTuning.DIFFICULTY, TestTuning.WEAPONS),
                        PlayerLoadout.forStage(campaign.get(place), difficulty, TestTuning.DIFFICULTY,
                            TestTuning.WEAPONS),
                        "header " + header + " at place " + place + " on " + difficulty);
                }
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

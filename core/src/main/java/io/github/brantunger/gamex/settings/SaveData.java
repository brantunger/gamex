package io.github.brantunger.gamex.settings;

import io.github.brantunger.gamex.player.Difficulty;
import io.github.brantunger.gamex.player.PlayerLoadout;
import io.github.brantunger.gamex.weapon.WeaponSlot;

/**
 * Persists campaign progress (active "Continue" snapshot, unlocked stages) and per-stage best completion records.
 */
public final class SaveData {

    public static final String PREFERENCES_NAME = "io.github.brantunger.gamex.save";

    private static final String KEY_HAS_RUN = "run.active";
    private static final String KEY_RUN_STAGE = "run.stageIndex";
    private static final String KEY_RUN_HEALTH = "run.health";
    private static final String KEY_RUN_ARMOR = "run.armor";
    private static final String KEY_RUN_DIFFICULTY = "run.difficulty";
    private static final String KEY_RUN_HAS_SCATTERGUN = "run.hasScattergun";
    private static final String KEY_RUN_SHELLS = "run.shells";
    private static final String KEY_RUN_HAS_RAIL_LANCE = "run.hasRailLance";
    private static final String KEY_RUN_SLUGS = "run.slugs";
    private static final String KEY_RUN_WEAPON = "run.activeWeapon";
    private static final String KEY_UNLOCKED_STAGE = "campaign.unlockedStage";

    /** Summary produced when finishing a stage, comparing this run against stored records. */
    public static final class CompletionSummary {
        public final float clearTime;
        public final float bestTime;
        public final boolean newBestTime;
        public final int targetsDestroyed;
        public final int targetsTotal;
        public final float damageTaken;
        public final char rank;
        public final char bestRank;

        CompletionSummary(float clearTime, float bestTime, boolean newBestTime, int targetsDestroyed,
                          int targetsTotal, float damageTaken, char rank, char bestRank) {
            this.clearTime = clearTime;
            this.bestTime = bestTime;
            this.newBestTime = newBestTime;
            this.targetsDestroyed = targetsDestroyed;
            this.targetsTotal = targetsTotal;
            this.damageTaken = damageTaken;
            this.rank = rank;
            this.bestRank = bestRank;
        }
    }

    private final KeyValueStore store;

    /** @param store backing key-value store */
    public SaveData(KeyValueStore store) {
        this.store = store == null ? new MapKeyValueStore() : store;
    }

    /**
     * Computes a letter grade ('S', 'A', 'B', or 'C') from clear time vs par and target destruction rate.
     */
    public static char rankFor(float clearTime, float parSeconds, int destroyed, int totalTargets) {
        boolean allDestroyed = totalTargets <= 0 || destroyed >= totalTargets;
        boolean mostDestroyed = totalTargets <= 0 || destroyed * 4 >= totalTargets * 3;
        if (clearTime <= parSeconds && allDestroyed) return 'S';
        if (clearTime <= parSeconds * 1.25f && mostDestroyed) return 'A';
        if (clearTime <= parSeconds * 1.65f) return 'B';
        return 'C';
    }

    /** @return whether an in-progress campaign snapshot can be resumed from the Title Screen. */
    public boolean hasSavedRun() {
        return store.getBoolean(KEY_HAS_RUN, false);
    }

    /** @return zero-based campaign stage index of the saved run. */
    public int getSavedStageIndex() {
        return Math.max(0, store.getInteger(KEY_RUN_STAGE, 0));
    }

    /** @return player entry loadout of the saved campaign run. */
    public PlayerLoadout getSavedLoadout() {
        float health = store.getFloat(KEY_RUN_HEALTH, 100f);
        float armor = store.getFloat(KEY_RUN_ARMOR, 0f);
        String diffName = store.getString(KEY_RUN_DIFFICULTY, Difficulty.NORMAL.name());
        Difficulty diff;
        try {
            diff = Difficulty.valueOf(diffName);
        } catch (IllegalArgumentException e) {
            diff = Difficulty.NORMAL;
        }
        boolean hasScattergun = store.getBoolean(KEY_RUN_HAS_SCATTERGUN, false);
        int shells = store.getInteger(KEY_RUN_SHELLS, 0);
        boolean hasRailLance = store.getBoolean(KEY_RUN_HAS_RAIL_LANCE, false);
        int slugs = store.getInteger(KEY_RUN_SLUGS, 0);
        WeaponSlot weapon = WeaponSlot.fromName(store.getString(KEY_RUN_WEAPON, WeaponSlot.PISTOL.name()));
        return new PlayerLoadout(health, armor, diff, hasScattergun, shells, hasRailLance, slugs, weapon);
    }

    /** Saves an active campaign entry point so the player can resume later via Continue. */
    public void saveCampaignRun(int stageIndex, PlayerLoadout loadout) {
        store.putBoolean(KEY_HAS_RUN, true);
        store.putInteger(KEY_RUN_STAGE, Math.max(0, stageIndex));
        store.putFloat(KEY_RUN_HEALTH, loadout.health);
        store.putFloat(KEY_RUN_ARMOR, loadout.armor);
        store.putString(KEY_RUN_DIFFICULTY, loadout.difficulty.name());
        store.putBoolean(KEY_RUN_HAS_SCATTERGUN, loadout.hasScattergun);
        store.putInteger(KEY_RUN_SHELLS, loadout.shells);
        store.putBoolean(KEY_RUN_HAS_RAIL_LANCE, loadout.hasRailLance);
        store.putInteger(KEY_RUN_SLUGS, loadout.slugs);
        store.putString(KEY_RUN_WEAPON, loadout.activeWeapon.name());
        if (stageIndex > getUnlockedStageIndex()) {
            store.putInteger(KEY_UNLOCKED_STAGE, stageIndex);
        }
        store.flush();
    }

    /** Clears the in-progress campaign snapshot (for example after completing the final stage). */
    public void clearSavedRun() {
        store.putBoolean(KEY_HAS_RUN, false);
        store.flush();
    }

    /** @return highest zero-based campaign stage index unlocked so far. */
    public int getUnlockedStageIndex() {
        return Math.max(0, store.getInteger(KEY_UNLOCKED_STAGE, 0));
    }

    /** Unlocks up to {@code stageIndex} if higher than current progress. */
    public void unlockStage(int stageIndex) {
        if (stageIndex > getUnlockedStageIndex()) {
            store.putInteger(KEY_UNLOCKED_STAGE, stageIndex);
            store.flush();
        }
    }

    /**
     * Records a completed stage run and returns a comparison summary for the Intermission Tally screen.
     */
    public CompletionSummary recordCompletion(String stageId, Difficulty difficulty, float clearTime,
                                              float parSeconds, int destroyed, int totalTargets, float damageTaken) {
        String prefix = "best." + stageId + "." + difficulty.name() + ".";
        float prevBestTime = store.getFloat(prefix + "time", Float.POSITIVE_INFINITY);
        boolean newBestTime = clearTime < prevBestTime;
        float bestTime = newBestTime ? clearTime : prevBestTime;
        if (newBestTime) store.putFloat(prefix + "time", bestTime);

        char rank = rankFor(clearTime, parSeconds, destroyed, totalTargets);
        String prevRankText = store.getString(prefix + "rank", "");
        char prevRank = prevRankText.isEmpty() ? 'Z' : prevRankText.charAt(0);
        char bestRank = rankOrder(rank) < rankOrder(prevRank) ? rank : prevRank;
        store.putString(prefix + "rank", String.valueOf(bestRank));
        store.flush();

        return new CompletionSummary(clearTime, bestTime, newBestTime, destroyed, totalTargets, damageTaken, rank,
            bestRank);
    }

    /** @return stored best rank ('S', 'A', 'B', 'C') for {@code stageId} on {@code difficulty}, or '-' if unplayed. */
    public char getBestRank(String stageId, Difficulty difficulty) {
        String text = store.getString("best." + stageId + "." + difficulty.name() + ".rank", "");
        return text.isEmpty() ? '-' : text.charAt(0);
    }

    /** @return stored best clear time in seconds for {@code stageId} on {@code difficulty}, or +Infinity if unplayed. */
    public float getBestTime(String stageId, Difficulty difficulty) {
        return store.getFloat("best." + stageId + "." + difficulty.name() + ".time", Float.POSITIVE_INFINITY);
    }

    /** @return highest rank earned on {@code stageId} across all difficulties, or '-' if never cleared. */
    public char getBestOverallRank(String stageId) {
        char best = '-';
        int bestOrd = 99;
        for (Difficulty diff : Difficulty.values()) {
            char rank = getBestRank(stageId, diff);
            int ord = rankOrder(rank);
            if (ord < bestOrd) {
                bestOrd = ord;
                best = rank;
            }
        }
        return best;
    }

    private static int rankOrder(char rank) {
        switch (rank) {
            case 'S':
                return 0;
            case 'A':
                return 1;
            case 'B':
                return 2;
            case 'C':
                return 3;
            default:
                return 99;
        }
    }
}

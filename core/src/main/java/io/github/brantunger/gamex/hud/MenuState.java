package io.github.brantunger.gamex.hud;

import io.github.brantunger.gamex.audio.SoundEvent;
import io.github.brantunger.gamex.player.Difficulty;
import io.github.brantunger.gamex.player.Player;
import io.github.brantunger.gamex.player.PlayerLoadout;
import io.github.brantunger.gamex.settings.GameSettings;
import io.github.brantunger.gamex.settings.SaveData;
import io.github.brantunger.gamex.world.Levels;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Locale;

/**
 * Pure-Java screen and menu state machine driving Title, Difficulty Select, Briefing, Pause, Settings, Death and
 * Intermission Tally flows. Has no graphics or input-device dependencies so it can be unit-tested directly.
 */
public final class MenuState {

    /** Active top-level UI screen. */
    public enum Screen {
        TITLE,
        STAGE_SELECT,
        DIFFICULTY_SELECT,
        BRIEFING,
        PLAYING,
        PAUSED,
        SETTINGS,
        DEAD,
        INTERMISSION
    }

    /** High-level commands emitted to {@code MainGame} when a menu choice changes the simulation or window. */
    public enum Command {
        NONE,
        /** Load {@link #getActiveStage()} with {@link #getActiveLoadout()} and enter {@link Screen#PLAYING}. */
        LAUNCH_STAGE,
        /** Apply fullscreen/windowed setting to the OS window. */
        APPLY_DISPLAY,
        /** Exit the application. */
        QUIT
    }

    private static final int MAX_QUEUED_SOUNDS = 64;

    private final List<Levels.Stage> campaign = Levels.campaign();
    private final Levels.Stage trainingRange = Levels.trainingRange();
    private final GameSettings settings;
    private final SaveData saveData;
    private final List<SoundEvent> soundEvents = new ArrayList<>();

    private Screen screen;
    private Screen settingsReturnScreen = Screen.TITLE;
    private Screen difficultyReturnScreen = Screen.TITLE;
    private int selectedIndex;
    private int pendingStageIndex;
    private boolean inTrainingRange;
    private boolean campaignCompleted;
    private int stageIndex;
    private Levels.Stage activeStage;
    private PlayerLoadout activeLoadout;
    private SaveData.CompletionSummary lastSummary;

    /**
     * @param settings live settings model
     * @param saveData campaign & record persistence
     * @param startScreen initial screen ({@link Screen#TITLE} in normal play, {@link Screen#PLAYING} in demo runs)
     */
    public MenuState(GameSettings settings, SaveData saveData, Screen startScreen) {
        this.settings = settings;
        this.saveData = saveData;
        this.activeStage = campaign.get(0);
        this.activeLoadout = PlayerLoadout.fresh(Difficulty.NORMAL);
        setScreen(startScreen);
    }

    public Screen getScreen() {
        return screen;
    }

    public boolean isPlaying() {
        return screen == Screen.PLAYING;
    }

    public int getSelectedIndex() {
        return selectedIndex;
    }

    public Levels.Stage getActiveStage() {
        return activeStage;
    }

    public PlayerLoadout getActiveLoadout() {
        return activeLoadout;
    }

    public boolean isCampaignCompleted() {
        return campaignCompleted;
    }

    public SaveData.CompletionSummary getLastSummary() {
        return lastSummary;
    }

    /** Appends pending UI sound events to {@code out} and clears the internal queue. */
    public void drainSoundEvents(List<SoundEvent> out) {
        if (soundEvents.isEmpty()) return;
        if (out != null) out.addAll(soundEvents);
        soundEvents.clear();
    }

    /** @return a copy of pending UI sound events, clearing the internal queue. */
    public List<SoundEvent> drainSoundEvents() {
        List<SoundEvent> copy = new ArrayList<>(soundEvents);
        soundEvents.clear();
        return copy;
    }

    /** @return visible menu option labels for the current screen. */
    public List<String> getOptions() {
        List<String> items = new ArrayList<>();
        switch (screen) {
            case TITLE:
                if (saveData.hasSavedRun()) items.add("CONTINUE CAMPAIGN");
                items.add("START CAMPAIGN");
                items.add("SELECT STAGE");
                items.add("TRAINING RANGE");
                items.add("SETTINGS");
                items.add("QUIT");
                break;
            case STAGE_SELECT: {
                int unlocked = saveData.getUnlockedStageIndex();
                for (int i = 0; i < campaign.size(); i++) {
                    Levels.Stage stage = campaign.get(i);
                    if (i <= unlocked) {
                        char best = saveData.getBestOverallRank(stage.id);
                        String badge = best == '-' ? "[READY]" : "[BEST: " + best + "]";
                        items.add(stage.title + " " + badge);
                    } else {
                        items.add(stage.title + " [LOCKED]");
                    }
                }
                items.add("BACK");
                break;
            }
            case DIFFICULTY_SELECT:
                items.add("EASY - 70% DAMAGE / +25% PICKUPS");
                items.add("NORMAL - STANDARD CHALLENGE");
                items.add("HARD - 140% DAMAGE / -20% PICKUPS");
                items.add("BACK");
                break;
            case BRIEFING:
                items.add("DEPLOY");
                items.add("RETURN TO TITLE");
                break;
            case PAUSED:
                items.add("RESUME");
                items.add("RESTART STAGE");
                items.add("SETTINGS");
                items.add("RETURN TO TITLE");
                items.add("QUIT");
                break;
            case SETTINGS:
                items.add(String.format(Locale.ROOT, "SENSITIVITY: < %.2f >", settings.getMouseSensitivity()));
                items.add("INVERT LOOK Y: < " + (settings.isInvertY() ? "ON" : "OFF") + " >");
                items.add("FIELD OF VIEW: < " + Math.round(settings.getFov()) + " >");
                items.add("VIEW BOB: < " + (settings.isViewBob() ? "ON" : "OFF") + " >");
                items.add("MASTER VOLUME: < " + Math.round(settings.getMasterVolume() * 100f) + "% >");
                items.add("FULLSCREEN: < " + (settings.isFullscreen() ? "ON" : "OFF") + " >");
                items.add("BACK");
                break;
            case DEAD:
                items.add("RETRY STAGE (R)");
                items.add("RETURN TO TITLE");
                break;
            case INTERMISSION:
                if (!inTrainingRange && stageIndex + 1 < campaign.size()) {
                    items.add("NEXT STAGE");
                }
                items.add("PLAY AGAIN");
                items.add("RETURN TO TITLE");
                break;
            case PLAYING:
            default:
                return Collections.emptyList();
        }
        return items;
    }

    /** Moves the highlighted row up or down, wrapping cleanly. */
    public void moveSelection(int delta) {
        List<String> options = getOptions();
        if (options.isEmpty() || delta == 0) return;
        int n = options.size();
        int next = ((selectedIndex + delta) % n + n) % n;
        if (next != selectedIndex) {
            selectedIndex = next;
            emitUiSound(SoundEvent.Type.MENU_MOVE);
        }
    }

    /** Sets the highlighted row directly (used for mouse hover). */
    public void setSelectedIndex(int index) {
        List<String> options = getOptions();
        if (index >= 0 && index < options.size() && index != selectedIndex) {
            selectedIndex = index;
            emitUiSound(SoundEvent.Type.MENU_MOVE);
        }
    }

    /** Adjusts the currently highlighted setting left (-1) or right (+1) on the Settings screen. */
    public Command adjustHorizontal(int direction) {
        if (screen != Screen.SETTINGS || direction == 0) return Command.NONE;
        switch (selectedIndex) {
            case 0:
                settings.adjustSensitivity(direction);
                emitUiSound(SoundEvent.Type.MENU_CONFIRM);
                return Command.NONE;
            case 1:
                settings.setInvertY(!settings.isInvertY());
                emitUiSound(SoundEvent.Type.MENU_CONFIRM);
                return Command.NONE;
            case 2:
                settings.adjustFov(direction);
                emitUiSound(SoundEvent.Type.MENU_CONFIRM);
                return Command.NONE;
            case 3:
                settings.setViewBob(!settings.isViewBob());
                emitUiSound(SoundEvent.Type.MENU_CONFIRM);
                return Command.NONE;
            case 4:
                settings.adjustMasterVolume(direction);
                emitUiSound(SoundEvent.Type.MENU_CONFIRM);
                return Command.NONE;
            case 5:
                settings.setFullscreen(!settings.isFullscreen());
                emitUiSound(SoundEvent.Type.MENU_CONFIRM);
                return Command.APPLY_DISPLAY;
            default:
                return Command.NONE;
        }
    }

    /** Confirms the currently highlighted menu item. */
    public Command confirm() {
        List<String> options = getOptions();
        if (options.isEmpty()) return Command.NONE;
        String choice = options.get(Math.min(selectedIndex, options.size() - 1));
        switch (screen) {
            case TITLE:
                if (choice.startsWith("CONTINUE")) {
                    inTrainingRange = false;
                    campaignCompleted = false;
                    stageIndex = Math.min(saveData.getSavedStageIndex(), campaign.size() - 1);
                    activeStage = campaign.get(stageIndex);
                    activeLoadout = saveData.getSavedLoadout();
                    setScreen(Screen.PLAYING);
                    emitUiSound(SoundEvent.Type.MENU_CONFIRM);
                    return Command.LAUNCH_STAGE;
                }
                if (choice.startsWith("START CAMPAIGN")) {
                    pendingStageIndex = 0;
                    difficultyReturnScreen = Screen.TITLE;
                    setScreen(Screen.DIFFICULTY_SELECT);
                    selectedIndex = 1; // Default to NORMAL.
                    emitUiSound(SoundEvent.Type.MENU_CONFIRM);
                    return Command.NONE;
                }
                if (choice.startsWith("SELECT STAGE")) {
                    setScreen(Screen.STAGE_SELECT);
                    emitUiSound(SoundEvent.Type.MENU_CONFIRM);
                    return Command.NONE;
                }
                if (choice.startsWith("TRAINING RANGE")) {
                    inTrainingRange = true;
                    campaignCompleted = false;
                    stageIndex = 0;
                    activeStage = trainingRange;
                    activeLoadout = PlayerLoadout.fresh(Difficulty.NORMAL);
                    setScreen(Screen.BRIEFING);
                    emitUiSound(SoundEvent.Type.MENU_CONFIRM);
                    return Command.NONE;
                }
                if (choice.startsWith("SETTINGS")) {
                    settingsReturnScreen = Screen.TITLE;
                    setScreen(Screen.SETTINGS);
                    emitUiSound(SoundEvent.Type.MENU_CONFIRM);
                    return Command.NONE;
                }
                if (choice.startsWith("QUIT")) return Command.QUIT;
                return Command.NONE;

            case STAGE_SELECT:
                if (choice.equals("BACK")) {
                    setScreen(Screen.TITLE);
                    emitUiSound(SoundEvent.Type.MENU_BACK);
                    return Command.NONE;
                }
                if (selectedIndex < campaign.size() && selectedIndex <= saveData.getUnlockedStageIndex()) {
                    pendingStageIndex = selectedIndex;
                    difficultyReturnScreen = Screen.STAGE_SELECT;
                    setScreen(Screen.DIFFICULTY_SELECT);
                    selectedIndex = 1; // Default to NORMAL.
                    emitUiSound(SoundEvent.Type.MENU_CONFIRM);
                } else {
                    emitUiSound(SoundEvent.Type.MENU_BACK);
                }
                return Command.NONE;

            case DIFFICULTY_SELECT:
                if (choice.startsWith("BACK")) {
                    setScreen(difficultyReturnScreen);
                    emitUiSound(SoundEvent.Type.MENU_BACK);
                    return Command.NONE;
                }
                Difficulty diff = choice.startsWith("EASY") ? Difficulty.EASY
                    : choice.startsWith("HARD") ? Difficulty.HARD : Difficulty.NORMAL;
                inTrainingRange = false;
                campaignCompleted = false;
                stageIndex = Math.min(pendingStageIndex, campaign.size() - 1);
                activeStage = campaign.get(stageIndex);
                activeLoadout = PlayerLoadout.forStage(stageIndex, diff);
                saveData.saveCampaignRun(stageIndex, activeLoadout);
                setScreen(Screen.BRIEFING);
                emitUiSound(SoundEvent.Type.MENU_CONFIRM);
                return Command.NONE;

            case BRIEFING:
                if (choice.startsWith("DEPLOY")) {
                    setScreen(Screen.PLAYING);
                    emitUiSound(SoundEvent.Type.MENU_CONFIRM);
                    return Command.LAUNCH_STAGE;
                }
                setScreen(Screen.TITLE);
                emitUiSound(SoundEvent.Type.MENU_BACK);
                return Command.NONE;

            case PAUSED:
                if (choice.startsWith("RESUME")) {
                    setScreen(Screen.PLAYING);
                    emitUiSound(SoundEvent.Type.MENU_CONFIRM);
                    return Command.NONE;
                }
                if (choice.startsWith("RESTART")) {
                    setScreen(Screen.PLAYING);
                    emitUiSound(SoundEvent.Type.MENU_CONFIRM);
                    return Command.LAUNCH_STAGE;
                }
                if (choice.startsWith("SETTINGS")) {
                    settingsReturnScreen = Screen.PAUSED;
                    setScreen(Screen.SETTINGS);
                    emitUiSound(SoundEvent.Type.MENU_CONFIRM);
                    return Command.NONE;
                }
                if (choice.startsWith("RETURN TO TITLE")) {
                    setScreen(Screen.TITLE);
                    emitUiSound(SoundEvent.Type.MENU_BACK);
                    return Command.NONE;
                }
                if (choice.startsWith("QUIT")) return Command.QUIT;
                return Command.NONE;

            case SETTINGS:
                if (choice.startsWith("BACK")) {
                    setScreen(settingsReturnScreen);
                    emitUiSound(SoundEvent.Type.MENU_BACK);
                    return Command.NONE;
                }
                return adjustHorizontal(1);

            case DEAD:
                if (choice.startsWith("RETRY")) {
                    setScreen(Screen.PLAYING);
                    emitUiSound(SoundEvent.Type.MENU_CONFIRM);
                    return Command.LAUNCH_STAGE;
                }
                setScreen(Screen.TITLE);
                emitUiSound(SoundEvent.Type.MENU_BACK);
                return Command.NONE;

            case INTERMISSION:
                if (choice.startsWith("NEXT STAGE")) {
                    campaignCompleted = false;
                    stageIndex++;
                    activeStage = campaign.get(stageIndex);
                    saveData.saveCampaignRun(stageIndex, activeLoadout);
                    setScreen(Screen.BRIEFING);
                    emitUiSound(SoundEvent.Type.MENU_CONFIRM);
                    return Command.NONE;
                }
                if (choice.startsWith("PLAY AGAIN")) {
                    campaignCompleted = false;
                    if (!inTrainingRange) {
                        activeLoadout = saveData.hasSavedRun() && saveData.getSavedStageIndex() == stageIndex
                            ? saveData.getSavedLoadout()
                            : PlayerLoadout.forStage(stageIndex, activeLoadout.difficulty);
                    } else {
                        activeLoadout = PlayerLoadout.fresh(Difficulty.NORMAL);
                    }
                    setScreen(Screen.PLAYING);
                    emitUiSound(SoundEvent.Type.MENU_CONFIRM);
                    return Command.LAUNCH_STAGE;
                }
                setScreen(Screen.TITLE);
                emitUiSound(SoundEvent.Type.MENU_BACK);
                return Command.NONE;

            default:
                return Command.NONE;
        }
    }

    /** Handles pressing Escape in any state. */
    public Command escape() {
        switch (screen) {
            case PLAYING:
                setScreen(Screen.PAUSED);
                emitUiSound(SoundEvent.Type.MENU_BACK);
                return Command.NONE;
            case PAUSED:
                setScreen(Screen.PLAYING);
                emitUiSound(SoundEvent.Type.MENU_BACK);
                return Command.NONE;
            case SETTINGS:
                setScreen(settingsReturnScreen);
                emitUiSound(SoundEvent.Type.MENU_BACK);
                return Command.NONE;
            case DIFFICULTY_SELECT:
                setScreen(difficultyReturnScreen);
                emitUiSound(SoundEvent.Type.MENU_BACK);
                return Command.NONE;
            case STAGE_SELECT:
            case BRIEFING:
            case DEAD:
            case INTERMISSION:
                setScreen(Screen.TITLE);
                emitUiSound(SoundEvent.Type.MENU_BACK);
                return Command.NONE;
            case TITLE:
            default:
                return Command.NONE;
        }
    }

    /** Quick-restarts the active stage from its entry loadout (for example via R on death or during play). */
    public Command quickRestart() {
        if (screen != Screen.PLAYING && screen != Screen.DEAD && screen != Screen.PAUSED) return Command.NONE;
        setScreen(Screen.PLAYING);
        emitUiSound(SoundEvent.Type.MENU_CONFIRM);
        return Command.LAUNCH_STAGE;
    }

    /** Pauses active gameplay when the OS window loses focus or is minimised. */
    public void onWindowPaused() {
        if (screen == Screen.PLAYING) setScreen(Screen.PAUSED);
    }

    /** Transitions from {@link Screen#PLAYING} to {@link Screen#DEAD} once the player dies. */
    public void onPlayerDied() {
        if (screen == Screen.PLAYING) setScreen(Screen.DEAD);
    }

    /**
     * Records stage completion, updates carried-over loadout or clears finished campaign state, and opens
     * {@link Screen#INTERMISSION}.
     */
    public void onStageCleared(Player survivingPlayer, float clearTime, int destroyed, int totalTargets,
                               float damageTaken) {
        onStageCleared(PlayerLoadout.fromPlayer(survivingPlayer, activeLoadout.difficulty),
            clearTime, destroyed, totalTargets, damageTaken);
    }

    /**
     * Records stage completion with a full carried-over weapon/vital snapshot and opens {@link Screen#INTERMISSION}.
     */
    public void onStageCleared(PlayerLoadout carryOver, float clearTime, int destroyed, int totalTargets,
                               float damageTaken) {
        if (screen != Screen.PLAYING) return;
        lastSummary = saveData.recordCompletion(activeStage.id, activeLoadout.difficulty, clearTime,
            activeStage.parSeconds, destroyed, totalTargets, damageTaken);
        campaignCompleted = !inTrainingRange && (stageIndex + 1 >= campaign.size());
        if (!inTrainingRange) {
            PlayerLoadout nextLoadout = carryOver == null ? PlayerLoadout.forStage(stageIndex + 1, activeLoadout.difficulty)
                : carryOver;
            if (stageIndex + 1 < campaign.size()) {
                saveData.unlockStage(stageIndex + 1);
                activeLoadout = nextLoadout;
            } else {
                saveData.clearSavedRun();
            }
        }
        setScreen(Screen.INTERMISSION);
    }

    private void setScreen(Screen next) {
        this.screen = next;
        this.selectedIndex = 0;
    }

    private void emitUiSound(SoundEvent.Type type) {
        if (soundEvents.size() < MAX_QUEUED_SOUNDS) {
            soundEvents.add(SoundEvent.ui(type));
        }
    }
}

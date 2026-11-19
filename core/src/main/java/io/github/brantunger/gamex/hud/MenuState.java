package io.github.brantunger.gamex.hud;

import io.github.brantunger.gamex.audio.SoundEvent;
import io.github.brantunger.gamex.player.Difficulty;
import io.github.brantunger.gamex.player.DifficultyTuning;
import io.github.brantunger.gamex.player.Player;
import io.github.brantunger.gamex.player.PlayerLoadout;
import io.github.brantunger.gamex.settings.GameSettings;
import io.github.brantunger.gamex.settings.Medal;
import io.github.brantunger.gamex.settings.RunStats;
import io.github.brantunger.gamex.settings.SaveData;
import io.github.brantunger.gamex.weapon.WeaponTuning;
import io.github.brantunger.gamex.world.Levels;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Locale;

/**
 * Pure-Java screen and menu state machine driving Title, Difficulty Select, Briefing, Pause, Settings, Death and
 * Intermission Tally flows. Has no graphics or input-device dependencies so it can be unit-tested directly.
 */
public final class MenuState {

    private static final Logger log = LoggerFactory.getLogger(MenuState.class);

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

    /** Prompt shown on the Title and Pause screens after a first press of the quit key. */
    public static final String QUIT_PROMPT = "PRESS Q AGAIN TO QUIT";
    /** How long a first press of the quit key stays armed, in seconds. */
    public static final float QUIT_CONFIRM_SECONDS = 2f;

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
    /** Loadout the active stage was entered with; PLAY AGAIN replays from it after a clear. */
    private PlayerLoadout entryLoadout;
    private SaveData.CompletionSummary lastSummary;
    /** Seconds left in which a second press of the quit key quits; 0 when no quit is pending. */
    private float quitConfirmTimer;
    private final DifficultyTuning difficultyTuning;
    private final WeaponTuning weaponTuning;

    /**
     * Builds the menus with the difficulty and weapon tuning files read here and now. This is for tests only:
     * the game loads its tuning once at start-up and uses
     * {@link #MenuState(GameSettings, SaveData, Screen, DifficultyTuning, WeaponTuning)}.
     * @param settings live settings model
     * @param saveData campaign & record persistence
     * @param startScreen initial screen ({@link Screen#TITLE} in normal play, {@link Screen#PLAYING} in demo runs)
     */
    public MenuState(GameSettings settings, SaveData saveData, Screen startScreen) {
        this(settings, saveData, startScreen, DifficultyTuning.load(), WeaponTuning.load());
    }

    /**
     * @param settings live settings model
     * @param saveData campaign & record persistence
     * @param startScreen initial screen ({@link Screen#TITLE} in normal play, {@link Screen#PLAYING} in demo runs)
     * @param difficultyTuning what each difficulty multiplies, as loaded at start-up
     * @param weaponTuning the weapon balance, as loaded at start-up
     */
    public MenuState(GameSettings settings, SaveData saveData, Screen startScreen,
                     DifficultyTuning difficultyTuning, WeaponTuning weaponTuning) {
        if (difficultyTuning == null) throw new IllegalArgumentException("Difficulty tuning must not be null");
        if (weaponTuning == null) throw new IllegalArgumentException("Weapon tuning must not be null");
        this.settings = settings;
        this.saveData = saveData;
        this.difficultyTuning = difficultyTuning;
        this.weaponTuning = weaponTuning;
        this.activeStage = campaign.get(0);
        this.activeLoadout = PlayerLoadout.fresh(Difficulty.NORMAL);
        this.entryLoadout = activeLoadout;
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

    boolean isInTrainingRange() {
        return inTrainingRange;
    }

    public SaveData.CompletionSummary getLastSummary() {
        return lastSummary;
    }

    /** @return the screen that Settings goes back to: {@link Screen#TITLE} or {@link Screen#PAUSED}. */
    public Screen getSettingsReturnScreen() {
        return settingsReturnScreen;
    }

    /** @return whether {@link #QUIT_PROMPT} should be shown, after a first press of the quit key. */
    public boolean isQuitPromptVisible() {
        return quitConfirmTimer > 0f;
    }

    /** Advances menu timers by {@code dt} seconds; a pending quit expires {@link #QUIT_CONFIRM_SECONDS} on. */
    public void update(float dt) {
        if (quitConfirmTimer > 0f && dt > 0f) quitConfirmTimer = Math.max(0f, quitConfirmTimer - dt);
    }

    /**
     * Handles the quit key on the Title and Pause screens. The first press only shows {@link #QUIT_PROMPT}; a second
     * press within {@link #QUIT_CONFIRM_SECONDS} quits. Does nothing on other screens.
     */
    public Command requestQuit() {
        if (screen != Screen.TITLE && screen != Screen.PAUSED) return Command.NONE;
        if (quitConfirmTimer > 0f) {
            quitConfirmTimer = 0f;
            return Command.QUIT;
        }
        quitConfirmTimer = QUIT_CONFIRM_SECONDS;
        log.debug("Quit asked for on {}; waiting for a second press", screen);
        emitUiSound(SoundEvent.Type.MENU_BACK);
        return Command.NONE;
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
                        Difficulty fastest = saveData.getFastestDifficulty(stage.id);
                        float bestTime = fastest == null ? Float.POSITIVE_INFINITY
                            : saveData.getBestTime(stage.id, fastest);
                        items.add(stageSelectRow(stage.title, saveData.getBestOverallRank(stage.id), bestTime,
                            fastest));
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

    /**
     * @param title the stage title
     * @param bestRank the best rank over all difficulties, or '-' if the stage was never cleared
     * @param bestTime the fastest clear over all difficulties in seconds; ignored when {@code fastest} is null
     * @param fastest the difficulty that clear was made on, or null if no time is stored
     * @return the Select Stage row of an unlocked stage: the title and {@code [READY]} before its first clear,
     *     afterwards the best rank and the fastest clear with its difficulty, as in {@code [BEST: S] 1:42 (HARD)}
     */
    static String stageSelectRow(String title, char bestRank, float bestTime, Difficulty fastest) {
        if (bestRank == '-') return title + " [READY]";
        String row = title + " [BEST: " + bestRank + "]";
        if (fastest == null) return row;
        return row + " " + formatShortTime(bestTime) + " (" + fastest.label + ")";
    }

    /** @return the time as minutes and whole seconds, as in {@code 1:42}; it stops at {@code 99:59}. */
    static String formatShortTime(float seconds) {
        int whole = (int) Math.min(99 * 60 + 59, Math.max(0f, seconds));
        return String.format(Locale.ROOT, "%d:%02d", whole / 60, whole % 60);
    }

    /**
     * @return on Select Stage, the medals earned on the highlighted stage as a count per difficulty, as in
     *     {@code MEDALS: EASY 2/4  NORMAL 4/4  HARD 0/4}; null on other screens, on BACK, on a locked stage and
     *     on a stage with no medal yet
     */
    public String getStageSelectDetail() {
        if (screen != Screen.STAGE_SELECT || selectedIndex >= campaign.size()
            || selectedIndex > saveData.getUnlockedStageIndex()) {
            return null;
        }
        String stageId = campaign.get(selectedIndex).id;
        StringBuilder line = new StringBuilder("MEDALS:");
        int earned = 0;
        for (Difficulty diff : Difficulty.values()) {
            int count = saveData.getMedals(stageId, diff).size();
            earned += count;
            line.append(diff.ordinal() == 0 ? " " : "  ").append(diff.label).append(' ').append(count).append('/')
                .append(Medal.values().length);
        }
        return earned == 0 ? null : line.toString();
    }

    /** Moves the highlighted row up or down, wrapping cleanly. */
    public void moveSelection(int delta) {
        List<String> options = getOptions();
        if (options.isEmpty() || delta == 0) return;
        int n = options.size();
        int next = ((selectedIndex + delta) % n + n) % n;
        if (next != selectedIndex) {
            selectedIndex = next;
            log.debug("Menu {} moved to '{}'", screen, options.get(next));
            emitUiSound(SoundEvent.Type.MENU_MOVE);
        }
    }

    /** Sets the highlighted row directly (used for mouse hover). */
    public void setSelectedIndex(int index) {
        List<String> options = getOptions();
        if (index >= 0 && index < options.size() && index != selectedIndex) {
            selectedIndex = index;
            log.debug("Menu {} pointed at '{}'", screen, options.get(index));
            emitUiSound(SoundEvent.Type.MENU_MOVE);
        }
    }

    /** Adjusts the currently highlighted setting left (-1) or right (+1) on the Settings screen. */
    public Command adjustHorizontal(int direction) {
        if (screen != Screen.SETTINGS || direction == 0) return Command.NONE;
        switch (selectedIndex) {
            case 0: {
                float before = settings.getMouseSensitivity();
                settings.adjustSensitivity(direction);
                // At the end of its range the value stays where it was, and then nothing changed.
                if (settings.getMouseSensitivity() != before) {
                    log.debug("Setting changed: mouse sensitivity {}", settings.getMouseSensitivity());
                }
                emitUiSound(SoundEvent.Type.MENU_CONFIRM);
                return Command.NONE;
            }
            case 1:
                settings.setInvertY(!settings.isInvertY());
                log.debug("Setting changed: invert Y {}", settings.isInvertY());
                emitUiSound(SoundEvent.Type.MENU_CONFIRM);
                return Command.NONE;
            case 2: {
                float before = settings.getFov();
                settings.adjustFov(direction);
                if (settings.getFov() != before) log.debug("Setting changed: field of view {}", settings.getFov());
                emitUiSound(SoundEvent.Type.MENU_CONFIRM);
                return Command.NONE;
            }
            case 3:
                settings.setViewBob(!settings.isViewBob());
                log.debug("Setting changed: view bob {}", settings.isViewBob());
                emitUiSound(SoundEvent.Type.MENU_CONFIRM);
                return Command.NONE;
            case 4: {
                float before = settings.getMasterVolume();
                settings.adjustMasterVolume(direction);
                if (settings.getMasterVolume() != before) {
                    log.debug("Setting changed: master volume {}", settings.getMasterVolume());
                }
                emitUiSound(SoundEvent.Type.MENU_CONFIRM);
                return Command.NONE;
            }
            case 5:
                settings.setFullscreen(!settings.isFullscreen());
                log.debug("Setting changed: fullscreen {}", settings.isFullscreen());
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
        log.debug("Menu {} confirmed '{}'", screen, choice);
        switch (screen) {
            case TITLE:
                if (choice.startsWith("CONTINUE")) {
                    inTrainingRange = false;
                    campaignCompleted = false;
                    stageIndex = Math.min(saveData.getSavedStageIndex(), campaign.size() - 1);
                    activeStage = campaign.get(stageIndex);
                    activeLoadout = saveData.getSavedLoadout();
                    log.info("Continuing the saved run at stage {} on {}", activeStage.id, activeLoadout.difficulty);
                    entryLoadout = activeLoadout;
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
                    entryLoadout = activeLoadout;
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
                activeLoadout = PlayerLoadout.forStage(activeStage, diff, difficultyTuning, weaponTuning);
                entryLoadout = activeLoadout;
                // The run is saved on DEPLOY, so backing out of the briefing keeps the old CONTINUE save.
                setScreen(Screen.BRIEFING);
                emitUiSound(SoundEvent.Type.MENU_CONFIRM);
                return Command.NONE;

            case BRIEFING:
                if (choice.startsWith("DEPLOY")) {
                    if (!inTrainingRange) saveData.saveCampaignRun(stageIndex, activeLoadout);
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
                    entryLoadout = activeLoadout;
                    saveData.saveCampaignRun(stageIndex, activeLoadout);
                    setScreen(Screen.BRIEFING);
                    emitUiSound(SoundEvent.Type.MENU_CONFIRM);
                    return Command.NONE;
                }
                if (choice.startsWith("PLAY AGAIN")) {
                    campaignCompleted = false;
                    // A side trip: replay from the stage's entry loadout and leave the CONTINUE save alone.
                    activeLoadout = entryLoadout;
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

    /**
     * Quick-restarts the active stage from its entry loadout (for example via R during play, on the Pause menu or on
     * death).
     */
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
     * The run carries no pickup or shot counts.
     */
    public void onStageCleared(PlayerLoadout carryOver, float clearTime, int destroyed, int totalTargets,
                               float damageTaken) {
        onStageCleared(carryOver, new RunStats(clearTime, destroyed, totalTargets, damageTaken, 0, 0, 0, 0));
    }

    /**
     * Records stage completion with a full carried-over weapon/vital snapshot and opens {@link Screen#INTERMISSION}.
     * Clearing a campaign stage other than the last saves the next stage with the carry-over at once, so progress
     * survives leaving the tally without NEXT STAGE; clearing the last stage clears the saved run. A Training Range
     * run keeps its best time and rank but earns no medals.
     */
    public void onStageCleared(PlayerLoadout carryOver, RunStats stats) {
        if (screen != Screen.PLAYING) return;
        lastSummary = saveData.recordCompletion(activeStage.id, activeLoadout.difficulty, activeStage.parSeconds,
            stats, !inTrainingRange);
        campaignCompleted = !inTrainingRange && (stageIndex + 1 >= campaign.size());
        if (!inTrainingRange) {
            if (stageIndex + 1 < campaign.size()) {
                PlayerLoadout nextLoadout = carryOver == null
                    ? PlayerLoadout.forStage(campaign.get(stageIndex + 1), activeLoadout.difficulty,
                        difficultyTuning, weaponTuning)
                    : carryOver;
                saveData.unlockStage(stageIndex + 1);
                saveData.saveCampaignRun(stageIndex + 1, nextLoadout);
                activeLoadout = nextLoadout;
            } else {
                saveData.clearSavedRun();
            }
        }
        setScreen(Screen.INTERMISSION);
    }

    private void setScreen(Screen next) {
        if (screen == null) {
            log.info("Screen {} shown", next);
        } else if (next != screen) {
            log.info("Screen {} shown after {}", next, screen);
        }
        this.screen = next;
        this.selectedIndex = 0;
        this.quitConfirmTimer = 0f;
        if (next == Screen.TITLE) {
            this.activeStage = campaign.get(0);
            this.inTrainingRange = false;
        }
    }

    private void emitUiSound(SoundEvent.Type type) {
        if (soundEvents.size() < MAX_QUEUED_SOUNDS) {
            soundEvents.add(SoundEvent.ui(type));
        }
    }
}

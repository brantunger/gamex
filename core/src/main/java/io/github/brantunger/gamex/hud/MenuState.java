package io.github.brantunger.gamex.hud;

import io.github.brantunger.gamex.audio.SoundEvent;
import io.github.brantunger.gamex.input.KeyBindings;
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
import io.github.brantunger.gamex.world.WorldTuning;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.ArrayList;
import java.util.Collections;
import java.util.IdentityHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

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

    /**
     * What a menu option does. An option is told apart by its id alone: its label is only what is shown, and comes
     * from {@code assets/data/strings.txt}.
     */
    public enum OptionId {
        CONTINUE,
        START_CAMPAIGN,
        SELECT_STAGE,
        TRAINING_RANGE,
        SETTINGS,
        QUIT,
        /** A stage row on Select Stage; {@link Option#payload} is the stage's place in the campaign, from 0. */
        STAGE,
        BACK,
        /** A row on Select Difficulty; {@link Option#payload} is the ordinal of its {@link Difficulty}. */
        DIFFICULTY,
        DEPLOY,
        RETURN_TO_TITLE,
        RESUME,
        RESTART,
        SET_SENSITIVITY,
        SET_INVERT_Y,
        SET_FOV,
        SET_VIEW_BOB,
        SET_VOLUME,
        SET_FULLSCREEN,
        RETRY,
        NEXT_STAGE,
        PLAY_AGAIN;

        /** @return whether this is a Settings row whose value the left and right keys and a click change. */
        public boolean isSetting() {
            return this == SET_SENSITIVITY || this == SET_INVERT_Y || this == SET_FOV || this == SET_VIEW_BOB
                || this == SET_VOLUME || this == SET_FULLSCREEN;
        }
    }

    /** One row of a menu: what it does and what it shows. Immutable. */
    public static final class Option {
        /** Value of {@link #payload} for an option that carries none. */
        public static final int NO_PAYLOAD = -1;

        /** What the option does. */
        public final OptionId id;
        /** The text shown on the row. */
        public final String label;
        /** Which stage or difficulty the row stands for (see {@link OptionId}); otherwise {@link #NO_PAYLOAD}. */
        public final int payload;

        Option(OptionId id, String label, int payload) {
            this.id = id;
            this.label = label;
            this.payload = payload;
        }
    }

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
    private final UiStrings strings;
    /** The briefing of each stage as shown, with its placeholders filled in. */
    private final Map<Levels.Stage, List<String>> briefings = new IdentityHashMap<>();

    /**
     * Builds the menus with the difficulty and weapon tuning files and the text read here and now. This is for
     * tests only: the game loads them once at start-up and uses
     * {@link #MenuState(GameSettings, SaveData, Screen, DifficultyTuning, WeaponTuning, UiStrings)}.
     * @param settings live settings model
     * @param saveData campaign & record persistence
     * @param startScreen initial screen ({@link Screen#TITLE} in normal play, {@link Screen#PLAYING} in demo runs)
     */
    public MenuState(GameSettings settings, SaveData saveData, Screen startScreen) {
        this(settings, saveData, startScreen, DifficultyTuning.load(), WeaponTuning.load());
    }

    /**
     * Builds the menus with the text and the key bindings read here and now. This is for tests only, as the shorter
     * form is.
     * @param settings live settings model
     * @param saveData campaign & record persistence
     * @param startScreen initial screen ({@link Screen#TITLE} in normal play, {@link Screen#PLAYING} in demo runs)
     * @param difficultyTuning what each difficulty multiplies
     * @param weaponTuning the weapon balance
     */
    public MenuState(GameSettings settings, SaveData saveData, Screen startScreen,
                     DifficultyTuning difficultyTuning, WeaponTuning weaponTuning) {
        this(settings, saveData, startScreen, difficultyTuning, weaponTuning,
            weaponTuning == null || difficultyTuning == null ? null : readStrings(difficultyTuning, weaponTuning));
    }

    /** Reads the text of the menus, with the key names of the bindings file, for the constructor above. */
    private static UiStrings readStrings(DifficultyTuning difficultyTuning, WeaponTuning weaponTuning) {
        PixelFont font = PixelFont.load();
        return UiStrings.load(font, KeyBindings.load(font)::hint,
            TextValues.of(weaponTuning, difficultyTuning, WorldTuning.load()));
    }

    /**
     * @param settings live settings model
     * @param saveData campaign & record persistence
     * @param startScreen initial screen ({@link Screen#TITLE} in normal play, {@link Screen#PLAYING} in demo runs)
     * @param difficultyTuning what each difficulty multiplies, as loaded at start-up
     * @param weaponTuning the weapon balance, as loaded at start-up
     * @param strings the text of the menus, as loaded at start-up
     * @throws IllegalArgumentException if a stage's title cannot be drawn, or a line of its briefing cannot be
     *     drawn or breaks a placeholder rule
     */
    public MenuState(GameSettings settings, SaveData saveData, Screen startScreen,
                     DifficultyTuning difficultyTuning, WeaponTuning weaponTuning, UiStrings strings) {
        if (difficultyTuning == null) throw new IllegalArgumentException("Difficulty tuning must not be null");
        if (weaponTuning == null) throw new IllegalArgumentException("Weapon tuning must not be null");
        if (strings == null) throw new IllegalArgumentException("Strings must not be null");
        this.settings = settings;
        this.saveData = saveData;
        this.difficultyTuning = difficultyTuning;
        this.weaponTuning = weaponTuning;
        this.strings = strings;
        for (Levels.Stage stage : campaign) fillBriefing(stage);
        fillBriefing(trainingRange);
        this.activeStage = campaign.get(0);
        this.activeLoadout = PlayerLoadout.fresh(Difficulty.NORMAL);
        this.entryLoadout = activeLoadout;
        setScreen(startScreen);
    }

    /** Checks that a stage's title can be drawn and fills in the placeholders of its briefing. */
    private void fillBriefing(Levels.Stage stage) {
        strings.requireDrawable(stage.title, "the title of stage " + stage.id);
        List<String> lines = new ArrayList<>();
        for (int i = 0; i < stage.briefing.size(); i++) {
            lines.add(strings.fill(stage.briefing.get(i), "briefing line " + (i + 1) + " of stage " + stage.id));
        }
        briefings.put(stage, Collections.unmodifiableList(lines));
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

    /**
     * @return the briefing of {@link #getActiveStage()} as shown: its lines with the key names and tuning values
     *     filled in
     */
    public List<String> getActiveBriefing() {
        return briefings.get(activeStage);
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

    /** @return whether the quit prompt should be shown, after a first press of the quit key. */
    public boolean isQuitPromptVisible() {
        return quitConfirmTimer > 0f;
    }

    /** Advances menu timers by {@code dt} seconds; a pending quit expires {@link #QUIT_CONFIRM_SECONDS} on. */
    public void update(float dt) {
        if (quitConfirmTimer > 0f && dt > 0f) quitConfirmTimer = Math.max(0f, quitConfirmTimer - dt);
    }

    /**
     * Handles the quit key on the Title and Pause screens. The first press only shows the quit prompt; a second
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

    /** @return visible menu option labels for the current screen, in the order of {@link #getMenuOptions()}. */
    public List<String> getOptions() {
        List<Option> options = getMenuOptions();
        if (options.isEmpty()) return Collections.emptyList();
        List<String> labels = new ArrayList<>(options.size());
        for (int i = 0; i < options.size(); i++) labels.add(options.get(i).label);
        return labels;
    }

    /** @return the visible menu options of the current screen, top row first. */
    public List<Option> getMenuOptions() {
        List<Option> items = new ArrayList<>();
        switch (screen) {
            case TITLE:
                if (saveData.hasSavedRun()) add(items, OptionId.CONTINUE, UiText.MENU_CONTINUE_CAMPAIGN);
                add(items, OptionId.START_CAMPAIGN, UiText.MENU_START_CAMPAIGN);
                add(items, OptionId.SELECT_STAGE, UiText.MENU_SELECT_STAGE);
                add(items, OptionId.TRAINING_RANGE, UiText.MENU_TRAINING_RANGE);
                add(items, OptionId.SETTINGS, UiText.MENU_SETTINGS);
                add(items, OptionId.QUIT, UiText.MENU_QUIT);
                break;
            case STAGE_SELECT: {
                int unlocked = saveData.getUnlockedStageIndex();
                for (int i = 0; i < campaign.size(); i++) {
                    Levels.Stage stage = campaign.get(i);
                    if (i <= unlocked) {
                        Difficulty fastest = saveData.getFastestDifficulty(stage.id);
                        float bestTime = fastest == null ? Float.POSITIVE_INFINITY
                            : saveData.getBestTime(stage.id, fastest);
                        items.add(new Option(OptionId.STAGE, stageSelectRow(stage.title,
                            saveData.getBestOverallRank(stage.id), bestTime, fastest), i));
                    } else {
                        items.add(new Option(OptionId.STAGE, strings.format(UiText.MENU_STAGE_LOCKED, stage.title),
                            i));
                    }
                }
                add(items, OptionId.BACK, UiText.MENU_BACK);
                break;
            }
            case DIFFICULTY_SELECT:
                for (Difficulty difficulty : Difficulty.values()) {
                    items.add(new Option(OptionId.DIFFICULTY, difficultyRow(difficulty), difficulty.ordinal()));
                }
                add(items, OptionId.BACK, UiText.MENU_BACK);
                break;
            case BRIEFING:
                add(items, OptionId.DEPLOY, UiText.MENU_DEPLOY);
                add(items, OptionId.RETURN_TO_TITLE, UiText.MENU_RETURN_TO_TITLE);
                break;
            case PAUSED:
                add(items, OptionId.RESUME, UiText.MENU_RESUME);
                add(items, OptionId.RESTART, UiText.MENU_RESTART_STAGE);
                add(items, OptionId.SETTINGS, UiText.MENU_SETTINGS);
                add(items, OptionId.RETURN_TO_TITLE, UiText.MENU_RETURN_TO_TITLE);
                add(items, OptionId.QUIT, UiText.MENU_QUIT);
                break;
            case SETTINGS:
                addSetting(items, OptionId.SET_SENSITIVITY, UiText.MENU_SETTINGS_SENSITIVITY,
                    String.format(Locale.ROOT, "%.2f", settings.getMouseSensitivity()));
                addSetting(items, OptionId.SET_INVERT_Y, UiText.MENU_SETTINGS_INVERT_Y, onOff(settings.isInvertY()));
                addSetting(items, OptionId.SET_FOV, UiText.MENU_SETTINGS_FOV,
                    Integer.toString(Math.round(settings.getFov())));
                addSetting(items, OptionId.SET_VIEW_BOB, UiText.MENU_SETTINGS_VIEW_BOB,
                    onOff(settings.isViewBob()));
                addSetting(items, OptionId.SET_VOLUME, UiText.MENU_SETTINGS_VOLUME,
                    TextFormat.percent(settings.getMasterVolume()));
                addSetting(items, OptionId.SET_FULLSCREEN, UiText.MENU_SETTINGS_FULLSCREEN,
                    onOff(settings.isFullscreen()));
                add(items, OptionId.BACK, UiText.MENU_BACK);
                break;
            case DEAD:
                add(items, OptionId.RETRY, UiText.MENU_RETRY_STAGE);
                add(items, OptionId.RETURN_TO_TITLE, UiText.MENU_RETURN_TO_TITLE);
                break;
            case INTERMISSION:
                if (!inTrainingRange && stageIndex + 1 < campaign.size()) {
                    add(items, OptionId.NEXT_STAGE, UiText.MENU_NEXT_STAGE);
                }
                add(items, OptionId.PLAY_AGAIN, UiText.MENU_PLAY_AGAIN);
                add(items, OptionId.RETURN_TO_TITLE, UiText.MENU_RETURN_TO_TITLE);
                break;
            case PLAYING:
            default:
                return Collections.emptyList();
        }
        return items;
    }

    private void add(List<Option> items, OptionId id, UiText label) {
        items.add(new Option(id, strings.get(label), Option.NO_PAYLOAD));
    }

    private void addSetting(List<Option> items, OptionId id, UiText row, String value) {
        items.add(new Option(id, strings.format(row, value), Option.NO_PAYLOAD));
    }

    private String onOff(boolean on) {
        return strings.get(on ? UiText.MENU_SETTINGS_ON : UiText.MENU_SETTINGS_OFF);
    }

    /** @return the highlighted option, or null on a screen without options. */
    public Option getSelectedOption() {
        List<Option> options = getMenuOptions();
        if (options.isEmpty()) return null;
        return options.get(Math.min(selectedIndex, options.size() - 1));
    }

    /**
     * Highlights the first option with an id, as {@link #setSelectedIndex} does for a row.
     * @param id what the option does
     * @return whether the current screen has such an option
     */
    public boolean selectId(OptionId id) {
        return selectAt(indexOf(id, Option.NO_PAYLOAD, false));
    }

    /**
     * Highlights the option with an id that stands for a given stage or difficulty.
     * @param id {@link OptionId#STAGE} or {@link OptionId#DIFFICULTY}
     * @param payload the stage's place in the campaign, or the ordinal of the {@link Difficulty}
     * @return whether the current screen has such an option
     */
    public boolean selectId(OptionId id, int payload) {
        return selectAt(indexOf(id, payload, true));
    }

    private boolean selectAt(int index) {
        if (index < 0) return false;
        setSelectedIndex(index);
        return true;
    }

    private int indexOf(OptionId id, int payload, boolean matchPayload) {
        List<Option> options = getMenuOptions();
        for (int i = 0; i < options.size(); i++) {
            Option option = options.get(i);
            if (option.id == id && (!matchPayload || option.payload == payload)) return i;
        }
        return -1;
    }

    /**
     * @param difficulty a difficulty
     * @return its Select Difficulty row, made from what {@link DifficultyTuning} holds for it: its label with the
     *     damage taken and the change in what pickups give, as in {@code EASY - 70% DAMAGE / +25% PICKUPS}, or
     *     the standard row when it multiplies everything by 1
     */
    String difficultyRow(Difficulty difficulty) {
        return difficultyRow(strings, difficultyTuning, difficulty);
    }

    /** @return the row {@link #difficultyRow(Difficulty)} makes from a given text and tuning. */
    static String difficultyRow(UiStrings strings, DifficultyTuning tuning, Difficulty difficulty) {
        DifficultyTuning.Scales scales = tuning.of(difficulty);
        String label = strings.label(difficulty);
        if (difficultyRowText(tuning, difficulty) == UiText.MENU_DIFFICULTY_ROW_STANDARD) {
            return strings.format(UiText.MENU_DIFFICULTY_ROW_STANDARD, label);
        }
        return strings.format(UiText.MENU_DIFFICULTY_ROW, label, TextFormat.percent(scales.damageTakenScale),
            TextFormat.signedPercent(scales.pickupScale));
    }

    /** @return the line a difficulty's Select Difficulty row is made from: the standard one when it changes nothing. */
    static UiText difficultyRowText(DifficultyTuning tuning, Difficulty difficulty) {
        DifficultyTuning.Scales scales = tuning.of(difficulty);
        boolean standard = scales.damageTakenScale == 1f && scales.pickupScale == 1f && scales.enemySpeedScale == 1f;
        return standard ? UiText.MENU_DIFFICULTY_ROW_STANDARD : UiText.MENU_DIFFICULTY_ROW;
    }

    /**
     * @param title the stage title
     * @param bestRank the best rank over all difficulties, or '-' if the stage was never cleared
     * @param bestTime the fastest clear over all difficulties in seconds; ignored when {@code fastest} is null
     * @param fastest the difficulty that clear was made on, or null if no time is stored
     * @return the Select Stage row of an unlocked stage: the title and {@code [READY]} before its first clear,
     *     afterwards the best rank and the fastest clear with its difficulty, as in {@code [BEST: S] 1:42 (HARD)}
     */
    String stageSelectRow(String title, char bestRank, float bestTime, Difficulty fastest) {
        if (bestRank == '-') return strings.format(UiText.MENU_STAGE_READY, title);
        String rank = String.valueOf(bestRank);
        if (fastest == null) return strings.format(UiText.MENU_STAGE_BEST, title, rank);
        return strings.format(UiText.MENU_STAGE_BEST_TIME, title, rank, TextFormat.clock(bestTime),
            strings.label(fastest));
    }

    /**
     * @return on Select Stage, the medals earned on the highlighted stage as a count per difficulty, as in
     *     {@code MEDALS: EASY 2/4  NORMAL 4/4  HARD 0/4}; null on other screens, on BACK, on a locked stage and
     *     on a stage with no medal yet
     */
    public String getStageSelectDetail() {
        if (screen != Screen.STAGE_SELECT) return null;
        // The rows here are the campaign's stages in order and then BACK (see getMenuOptions), so the highlighted
        // stage is known without building the rows, which the HUD has already built once for this frame.
        int stage = Math.min(selectedIndex, campaign.size());
        if (stage >= campaign.size() || stage > saveData.getUnlockedStageIndex()) return null;
        String stageId = campaign.get(stage).id;
        StringBuilder counts = new StringBuilder();
        int earned = 0;
        for (Difficulty diff : Difficulty.values()) {
            int count = saveData.getMedals(stageId, diff).size();
            earned += count;
            if (counts.length() > 0) counts.append("  ");
            counts.append(strings.format(UiText.MENU_STAGE_MEDAL_COUNT, strings.label(diff),
                Integer.toString(count), Integer.toString(Medal.values().length)));
        }
        return earned == 0 ? null : strings.format(UiText.MENU_STAGE_MEDALS, counts.toString());
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
        switch (getSelectedOption().id) {
            case SET_SENSITIVITY: {
                float before = settings.getMouseSensitivity();
                settings.adjustSensitivity(direction);
                // At the end of its range the value stays where it was, and then nothing changed.
                if (settings.getMouseSensitivity() != before) {
                    log.debug("Setting changed: mouse sensitivity {}", settings.getMouseSensitivity());
                }
                emitUiSound(SoundEvent.Type.MENU_CONFIRM);
                return Command.NONE;
            }
            case SET_INVERT_Y:
                settings.setInvertY(!settings.isInvertY());
                log.debug("Setting changed: invert Y {}", settings.isInvertY());
                emitUiSound(SoundEvent.Type.MENU_CONFIRM);
                return Command.NONE;
            case SET_FOV: {
                float before = settings.getFov();
                settings.adjustFov(direction);
                if (settings.getFov() != before) log.debug("Setting changed: field of view {}", settings.getFov());
                emitUiSound(SoundEvent.Type.MENU_CONFIRM);
                return Command.NONE;
            }
            case SET_VIEW_BOB:
                settings.setViewBob(!settings.isViewBob());
                log.debug("Setting changed: view bob {}", settings.isViewBob());
                emitUiSound(SoundEvent.Type.MENU_CONFIRM);
                return Command.NONE;
            case SET_VOLUME: {
                float before = settings.getMasterVolume();
                settings.adjustMasterVolume(direction);
                if (settings.getMasterVolume() != before) {
                    log.debug("Setting changed: master volume {}", settings.getMasterVolume());
                }
                emitUiSound(SoundEvent.Type.MENU_CONFIRM);
                return Command.NONE;
            }
            case SET_FULLSCREEN:
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
        Option choice = getSelectedOption();
        if (choice == null) return Command.NONE;
        log.debug("Menu {} confirmed '{}'", screen, choice.label);
        switch (screen) {
            case TITLE:
                if (choice.id == OptionId.CONTINUE) {
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
                if (choice.id == OptionId.START_CAMPAIGN) {
                    pendingStageIndex = 0;
                    difficultyReturnScreen = Screen.TITLE;
                    setScreen(Screen.DIFFICULTY_SELECT);
                    selectDefaultDifficulty();
                    emitUiSound(SoundEvent.Type.MENU_CONFIRM);
                    return Command.NONE;
                }
                if (choice.id == OptionId.SELECT_STAGE) {
                    setScreen(Screen.STAGE_SELECT);
                    emitUiSound(SoundEvent.Type.MENU_CONFIRM);
                    return Command.NONE;
                }
                if (choice.id == OptionId.TRAINING_RANGE) {
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
                if (choice.id == OptionId.SETTINGS) {
                    settingsReturnScreen = Screen.TITLE;
                    setScreen(Screen.SETTINGS);
                    emitUiSound(SoundEvent.Type.MENU_CONFIRM);
                    return Command.NONE;
                }
                if (choice.id == OptionId.QUIT) return Command.QUIT;
                return Command.NONE;

            case STAGE_SELECT:
                if (choice.id == OptionId.BACK) {
                    setScreen(Screen.TITLE);
                    emitUiSound(SoundEvent.Type.MENU_BACK);
                    return Command.NONE;
                }
                if (choice.id == OptionId.STAGE && choice.payload <= saveData.getUnlockedStageIndex()) {
                    pendingStageIndex = choice.payload;
                    difficultyReturnScreen = Screen.STAGE_SELECT;
                    setScreen(Screen.DIFFICULTY_SELECT);
                    selectDefaultDifficulty();
                    emitUiSound(SoundEvent.Type.MENU_CONFIRM);
                } else {
                    emitUiSound(SoundEvent.Type.MENU_BACK);
                }
                return Command.NONE;

            case DIFFICULTY_SELECT:
                if (choice.id == OptionId.BACK) {
                    setScreen(difficultyReturnScreen);
                    emitUiSound(SoundEvent.Type.MENU_BACK);
                    return Command.NONE;
                }
                Difficulty diff = Difficulty.values()[choice.payload];
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
                if (choice.id == OptionId.DEPLOY) {
                    if (!inTrainingRange) saveData.saveCampaignRun(stageIndex, activeLoadout);
                    setScreen(Screen.PLAYING);
                    emitUiSound(SoundEvent.Type.MENU_CONFIRM);
                    return Command.LAUNCH_STAGE;
                }
                setScreen(Screen.TITLE);
                emitUiSound(SoundEvent.Type.MENU_BACK);
                return Command.NONE;

            case PAUSED:
                if (choice.id == OptionId.RESUME) {
                    setScreen(Screen.PLAYING);
                    emitUiSound(SoundEvent.Type.MENU_CONFIRM);
                    return Command.NONE;
                }
                if (choice.id == OptionId.RESTART) {
                    setScreen(Screen.PLAYING);
                    emitUiSound(SoundEvent.Type.MENU_CONFIRM);
                    return Command.LAUNCH_STAGE;
                }
                if (choice.id == OptionId.SETTINGS) {
                    settingsReturnScreen = Screen.PAUSED;
                    setScreen(Screen.SETTINGS);
                    emitUiSound(SoundEvent.Type.MENU_CONFIRM);
                    return Command.NONE;
                }
                if (choice.id == OptionId.RETURN_TO_TITLE) {
                    setScreen(Screen.TITLE);
                    emitUiSound(SoundEvent.Type.MENU_BACK);
                    return Command.NONE;
                }
                if (choice.id == OptionId.QUIT) return Command.QUIT;
                return Command.NONE;

            case SETTINGS:
                if (choice.id == OptionId.BACK) {
                    setScreen(settingsReturnScreen);
                    emitUiSound(SoundEvent.Type.MENU_BACK);
                    return Command.NONE;
                }
                return adjustHorizontal(1);

            case DEAD:
                if (choice.id == OptionId.RETRY) {
                    setScreen(Screen.PLAYING);
                    emitUiSound(SoundEvent.Type.MENU_CONFIRM);
                    return Command.LAUNCH_STAGE;
                }
                setScreen(Screen.TITLE);
                emitUiSound(SoundEvent.Type.MENU_BACK);
                return Command.NONE;

            case INTERMISSION:
                if (choice.id == OptionId.NEXT_STAGE) {
                    campaignCompleted = false;
                    stageIndex++;
                    activeStage = campaign.get(stageIndex);
                    entryLoadout = activeLoadout;
                    saveData.saveCampaignRun(stageIndex, activeLoadout);
                    setScreen(Screen.BRIEFING);
                    emitUiSound(SoundEvent.Type.MENU_CONFIRM);
                    return Command.NONE;
                }
                if (choice.id == OptionId.PLAY_AGAIN) {
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

    /** Highlights NORMAL, the difficulty a new run is offered first. */
    private void selectDefaultDifficulty() {
        int normal = indexOf(OptionId.DIFFICULTY, Difficulty.NORMAL.ordinal(), true);
        if (normal >= 0) selectedIndex = normal;
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

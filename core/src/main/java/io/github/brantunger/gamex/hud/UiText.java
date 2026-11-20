package io.github.brantunger.gamex.hud;

import java.util.Arrays;
import java.util.Collections;
import java.util.List;

/**
 * Every line of text {@code assets/data/strings.txt} holds: its key in the file and the names of the placeholders
 * the game fills in when it shows the line, in the order {@link UiStrings#format} takes their values. The file
 * must hold every key, and a line must use every placeholder declared for it (see {@link TextTemplate}).
 */
public enum UiText {
    // Menu options.
    MENU_CONTINUE_CAMPAIGN("menu.continue_campaign"),
    MENU_START_CAMPAIGN("menu.start_campaign"),
    MENU_SELECT_STAGE("menu.select_stage"),
    MENU_TRAINING_RANGE("menu.training_range"),
    MENU_SETTINGS("menu.settings"),
    MENU_QUIT("menu.quit"),
    MENU_BACK("menu.back"),
    MENU_DEPLOY("menu.deploy"),
    MENU_RETURN_TO_TITLE("menu.return_to_title"),
    MENU_RESUME("menu.resume"),
    MENU_RESTART_STAGE("menu.restart_stage"),
    MENU_RETRY_STAGE("menu.retry_stage"),
    MENU_NEXT_STAGE("menu.next_stage"),
    MENU_PLAY_AGAIN("menu.play_again"),
    /** Shown on the Title and Pause screens after a first press of the quit key. */
    MENU_QUIT_PROMPT("menu.quit_prompt"),
    /** Select Stage row of an unlocked stage that was never cleared. */
    MENU_STAGE_READY("menu.stage.ready", "title"),
    /** Select Stage row of a cleared stage with no time stored. */
    MENU_STAGE_BEST("menu.stage.best", "title", "rank"),
    /** Select Stage row of a cleared stage: best rank, fastest clear and the difficulty it was made on. */
    MENU_STAGE_BEST_TIME("menu.stage.best_time", "title", "rank", "time", "difficulty"),
    MENU_STAGE_LOCKED("menu.stage.locked", "title"),
    /** Medal line under Select Stage; {@code counts} is one {@link #MENU_STAGE_MEDAL_COUNT} per difficulty. */
    MENU_STAGE_MEDALS("menu.stage.medals", "counts"),
    MENU_STAGE_MEDAL_COUNT("menu.stage.medal_count", "difficulty", "earned", "total"),
    /** Select Difficulty row of a difficulty that changes the damage taken or the pickups. */
    MENU_DIFFICULTY_ROW("menu.difficulty.row", "label", "damage", "pickups"),
    /** Select Difficulty row of a difficulty that changes nothing. */
    MENU_DIFFICULTY_ROW_STANDARD("menu.difficulty.row_standard", "label"),
    MENU_SETTINGS_SENSITIVITY("menu.settings.sensitivity", "value"),
    MENU_SETTINGS_INVERT_Y("menu.settings.invert_y", "value"),
    MENU_SETTINGS_FOV("menu.settings.fov", "value"),
    MENU_SETTINGS_VIEW_BOB("menu.settings.view_bob", "value"),
    MENU_SETTINGS_VOLUME("menu.settings.volume", "value"),
    MENU_SETTINGS_FULLSCREEN("menu.settings.fullscreen", "value"),
    MENU_SETTINGS_MINIMAP("menu.settings.minimap", "value"),
    MENU_SETTINGS_ON("menu.settings.on"),
    MENU_SETTINGS_OFF("menu.settings.off"),

    // Combat HUD.
    HUD_LOW_ENERGY("hud.low_energy"),
    HUD_CHARGE("hud.charge"),
    HUD_ENERGY("hud.energy"),
    HUD_HP("hud.hp", "value"),
    HUD_ARMOR("hud.armor", "value"),
    HUD_WEAPON_PISTOL("hud.weapon.pistol"),
    HUD_WEAPON_SCATTERGUN("hud.weapon.scattergun", "ammo", "max"),
    HUD_WEAPON_RAIL_LANCE("hud.weapon.rail_lance", "ammo", "max"),
    /** Shells held before the Scattergun is. */
    HUD_AMMO_SHELLS("hud.ammo.shells", "ammo", "max"),
    /** Slugs held before the Rail Lance is. */
    HUD_AMMO_SLUGS("hud.ammo.slugs", "ammo", "max"),
    HUD_KEY_AMBER("hud.key.amber"),
    HUD_KEY_CYAN("hud.key.cyan"),
    HUD_HOSTILES("hud.hostiles", "remaining", "total"),
    HUD_TARGETS("hud.targets", "remaining", "total"),
    HUD_TIME("hud.time", "time"),
    HUD_OBJECTIVE("hud.objective", "text"),
    HUD_BOSS("hud.boss"),
    HUD_BOSS_OVERDRIVE("hud.boss_overdrive"),
    HUD_MINIMAP_NORTH("hud.minimap_north"),

    // Screen titles and captions.
    SCREEN_TITLE_NAME("screen.title.name"),
    SCREEN_TITLE_SUBTITLE("screen.title.subtitle"),
    SCREEN_TITLE_FOOTER("screen.title.footer"),
    SCREEN_STAGE_SELECT_TITLE("screen.stage_select.title"),
    SCREEN_STAGE_SELECT_HINT("screen.stage_select.hint"),
    SCREEN_DIFFICULTY_TITLE("screen.difficulty.title"),
    SCREEN_BRIEFING_META("screen.briefing.meta", "difficulty", "par"),
    SCREEN_PAUSED_TITLE("screen.paused.title"),
    SCREEN_SETTINGS_TITLE("screen.settings.title"),
    SCREEN_SETTINGS_HELP("screen.settings.help"),
    SCREEN_DEAD_TITLE("screen.dead.title"),
    /** Under the Death screen's title; {@code tally} is {@link #SCREEN_DEAD_HOSTILES} or the targets line. */
    SCREEN_DEAD_SURVIVED("screen.dead.survived", "time", "tally"),
    SCREEN_DEAD_HOSTILES("screen.dead.hostiles", "destroyed", "total"),
    SCREEN_DEAD_TARGETS("screen.dead.targets", "destroyed", "total"),
    SCREEN_COMPLETE_STAGE("screen.complete.stage"),
    SCREEN_COMPLETE_CAMPAIGN("screen.complete.campaign"),
    SCREEN_COMPLETE_CAMPAIGN_NOTE("screen.complete.campaign_note"),

    // Intermission Tally lines.
    TALLY_CLEAR_TIME("tally.clear_time", "time", "par"),
    TALLY_BEST("tally.best", "difficulty", "time"),
    /** The best time line of a run that set the best time. */
    TALLY_BEST_NEW("tally.best_new", "difficulty", "time"),
    TALLY_HOSTILES_CLEARED("tally.hostiles_cleared", "destroyed", "total", "percent"),
    TALLY_TARGETS_CLEARED("tally.targets_cleared", "destroyed", "total", "percent"),
    /** {@code accuracy} is a percentage, or {@link #TALLY_NO_ACCURACY} when no shot was fired. */
    TALLY_STATS("tally.stats", "damage", "collected", "total", "accuracy"),
    TALLY_NO_ACCURACY("tally.no_accuracy"),
    TALLY_RANK("tally.rank", "rank", "best"),
    /** {@code list} is the labels of the medals the run earned, a new one as {@link #TALLY_MEDAL_NEW}. */
    TALLY_MEDALS("tally.medals", "list"),
    TALLY_MEDAL_NEW("tally.medal_new", "label"),
    TALLY_MEDALS_NONE("tally.medals_none"),
    TALLY_MEDALS_TRAINING("tally.medals_training"),

    // Objectives.
    OBJECTIVE_AMBER_KEYCARD("objective.amber_keycard"),
    OBJECTIVE_CYAN_KEYCARD("objective.cyan_keycard"),
    OBJECTIVE_CONSOLE("objective.console"),
    OBJECTIVE_WARDEN("objective.warden"),
    OBJECTIVE_EXIT("objective.exit"),
    OBJECTIVE_ALL_TARGETS("objective.all_targets"),
    OBJECTIVE_ALL_HOSTILES("objective.all_hostiles"),
    OBJECTIVE_ALL_TARGETS_AND_HOSTILES("objective.all_targets_and_hostiles"),

    // Interaction prompts of something the player can use now.
    PROMPT_OVERRIDE_CONSOLE("prompt.override_console"),
    PROMPT_UNLOCK_AMBER("prompt.unlock_amber"),
    PROMPT_UNLOCK_CYAN("prompt.unlock_cyan"),
    PROMPT_OPEN_BULKHEAD("prompt.open_bulkhead"),

    // Why a bulkhead stays shut: both the prompt in front of it and the toast on trying it.
    BLOCKED_NEEDS_AMBER("blocked.needs_amber"),
    BLOCKED_NEEDS_CYAN("blocked.needs_cyan"),
    BLOCKED_SEALED_CONSOLE("blocked.sealed_console"),
    BLOCKED_SEALED_WARDEN("blocked.sealed_warden"),

    // Toasts.
    TOAST_OUT_OF_SHELLS("toast.out_of_shells"),
    TOAST_OUT_OF_SLUGS("toast.out_of_slugs"),
    TOAST_WARDEN_OVERDRIVE("toast.warden_overdrive"),
    TOAST_LOCKDOWN_OVERRIDDEN("toast.lockdown_overridden"),
    TOAST_AMBER_UNLOCKED("toast.amber_unlocked"),
    TOAST_CYAN_UNLOCKED("toast.cyan_unlocked"),
    TOAST_WARDEN_DESTROYED("toast.warden_destroyed"),
    /** Toast of a pickup that gave an amount of something. */
    PICKUP_GAIN("pickup.gain", "amount", "label"),
    /** Toast of a pickup that gave a keycard or a weapon. */
    PICKUP_GRANT("pickup.grant", "label"),

    // Labels.
    PICKUP_LABEL_MEDKIT("pickup.label.medkit"),
    PICKUP_LABEL_ARMOR("pickup.label.armor"),
    PICKUP_LABEL_ENERGY_CELL("pickup.label.energy_cell"),
    PICKUP_LABEL_AMBER_KEYCARD("pickup.label.amber_keycard"),
    PICKUP_LABEL_CYAN_KEYCARD("pickup.label.cyan_keycard"),
    PICKUP_LABEL_SCATTERGUN("pickup.label.scattergun"),
    PICKUP_LABEL_SHELLS("pickup.label.shells"),
    PICKUP_LABEL_RAIL_LANCE("pickup.label.rail_lance"),
    PICKUP_LABEL_RAIL_SLUGS("pickup.label.rail_slugs"),
    DIFFICULTY_LABEL_EASY("difficulty.label.easy"),
    DIFFICULTY_LABEL_NORMAL("difficulty.label.normal"),
    DIFFICULTY_LABEL_HARD("difficulty.label.hard"),
    MEDAL_LABEL_UNTOUCHED("medal.label.untouched"),
    MEDAL_LABEL_SWEEP("medal.label.sweep"),
    MEDAL_LABEL_UNDER_PAR("medal.label.under_par"),
    MEDAL_LABEL_SHARPSHOOTER("medal.label.sharpshooter");

    /** The line's key in {@code strings.txt}. */
    public final String key;
    /** The names of the placeholders the game fills in, in the order their values are given. Unmodifiable. */
    public final List<String> placeholders;

    UiText(String key, String... placeholders) {
        this.key = key;
        this.placeholders = Collections.unmodifiableList(Arrays.asList(placeholders));
    }
}

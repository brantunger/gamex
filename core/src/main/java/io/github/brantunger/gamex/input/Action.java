package io.github.brantunger.gamex.input;

import java.util.Collections;
import java.util.LinkedHashSet;
import java.util.Set;

/**
 * Everything the player can do with a key, a mouse button or the wheel. {@link KeyBindings} gives each action its
 * inputs, read from {@code assets/data/bindings.txt}, where an action is named by its {@link #id}; what an action
 * does, and whether it acts while its input is held or once per press, stays in {@link InputController}.
 *
 * <p>An action acts during play, on the menu screens, or both ({@link #PAUSE} and {@link #RETRY}). Two actions that
 * act in the same {@link Context} cannot share an input; two that never act together can, as {@link #QUICK_SWAP}
 * and {@link #QUIT} do with the shipped bindings.
 */
public enum Action {
    /** Pauses play; on a menu screen, goes back or resumes. */
    PAUSE("pause", Context.PLAY, Context.MENU),
    /** Restarts the stage, during play and on the death and pause screens. */
    RETRY("retry", Context.PLAY, Context.MENU),
    MOVE_FORWARD("move_forward", Context.PLAY),
    MOVE_BACK("move_back", Context.PLAY),
    MOVE_LEFT("move_left", Context.PLAY),
    MOVE_RIGHT("move_right", Context.PLAY),
    DASH("dash", Context.PLAY),
    JUMP("jump", Context.PLAY),
    INTERACT("interact", Context.PLAY),
    WEAPON_1("weapon_1", Context.PLAY),
    WEAPON_2("weapon_2", Context.PLAY),
    WEAPON_3("weapon_3", Context.PLAY),
    /** Switches back to the weapon held before the current one. */
    QUICK_SWAP("quick_swap", Context.PLAY),
    WEAPON_NEXT("weapon_next", Context.PLAY),
    WEAPON_PREV("weapon_prev", Context.PLAY),
    FIRE("fire", Context.PLAY),
    ALT_FIRE("alt_fire", Context.PLAY),
    /** Toggles the top-down HUD minimap on or off during play. */
    MINIMAP("minimap", Context.PLAY),
    MENU_UP("menu_up", Context.MENU),
    MENU_DOWN("menu_down", Context.MENU),
    /** Lowers the selected setting. */
    MENU_LEFT("menu_left", Context.MENU),
    /** Raises the selected setting. */
    MENU_RIGHT("menu_right", Context.MENU),
    MENU_CONFIRM("menu_confirm", Context.MENU),
    /** Closes the game from the title and pause screens, on a second press. */
    QUIT("quit", Context.MENU),
    /** Chooses the menu option under the pointer; it takes mouse buttons only. */
    MENU_CLICK("menu_click", Context.MENU);

    /** When an action acts: two actions of one context cannot share an input. */
    public enum Context {
        /** Active gameplay, with the cursor captured. */
        PLAY,
        /** Every menu, death and intermission screen. */
        MENU
    }

    private static final Set<String> IDS;

    static {
        Set<String> ids = new LinkedHashSet<>();
        for (Action action : values()) {
            ids.add(action.id);
        }
        IDS = Collections.unmodifiableSet(ids);
    }

    /** The action's key in the bindings file, and its name in a {@code {key:id}} placeholder of on-screen text. */
    public final String id;
    private final boolean play;
    private final boolean menu;

    Action(String id, Context... contexts) {
        this.id = id;
        boolean inPlay = false;
        boolean inMenu = false;
        for (Context context : contexts) {
            if (context == Context.PLAY) inPlay = true;
            else inMenu = true;
        }
        this.play = inPlay;
        this.menu = inMenu;
    }

    /**
     * @param context a context
     * @return whether the action acts in it
     */
    public boolean actsIn(Context context) {
        return context == Context.PLAY ? play : menu;
    }

    /** @return the id of every action, in declaration order; the set cannot be changed */
    public static Set<String> ids() {
        return IDS;
    }

    /**
     * @param id an action id
     * @return the action with that id, or null if there is none
     */
    public static Action byId(String id) {
        for (Action action : values()) {
            if (action.id.equals(id)) return action;
        }
        return null;
    }
}

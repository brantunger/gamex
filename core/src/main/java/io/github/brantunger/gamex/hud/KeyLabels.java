package io.github.brantunger.gamex.hud;

import java.util.Arrays;
import java.util.Collections;
import java.util.LinkedHashSet;
import java.util.Set;

/**
 * Gives the name shown for the key or button of an action, for the {@code {key:action}} placeholders of
 * {@code assets/data/strings.txt} and of stage briefings (see {@link TextTemplate}). The game takes the names from
 * the bindings file ({@code KeyBindings.hint}), so a rebound key changes every text that names it.
 */
public interface KeyLabels {

    /**
     * The action ids a {@code {key:action}} placeholder may name: the player's actions in play, then those of the
     * menus. They are the ids of the {@code Action} enum; the stage files are checked against this list, which
     * needs no loaded bindings.
     */
    Set<String> ACTIONS = Collections.unmodifiableSet(new LinkedHashSet<>(Arrays.asList(
        "pause", "retry", "move_forward", "move_back", "move_left", "move_right", "dash", "jump", "interact",
        "weapon_1", "weapon_2", "weapon_3", "quick_swap", "weapon_next", "weapon_prev", "fire", "alt_fire", "minimap",
        "menu_up", "menu_down", "menu_left", "menu_right", "menu_confirm", "quit", "menu_click")));

    /**
     * @param action one of {@link #ACTIONS}
     * @return the name shown for the action's key or button, such as {@code SHIFT}
     * @throws IllegalArgumentException if the action is not one of {@link #ACTIONS}
     */
    String label(String action);
}

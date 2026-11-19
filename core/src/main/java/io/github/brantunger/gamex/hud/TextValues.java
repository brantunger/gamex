package io.github.brantunger.gamex.hud;

import io.github.brantunger.gamex.player.DifficultyTuning;
import io.github.brantunger.gamex.weapon.WeaponTuning;
import io.github.brantunger.gamex.world.WorldTuning;

import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Set;

/**
 * The tuning values a text may quote, for the {@code {name}} placeholders of {@code assets/data/strings.txt} and of
 * stage briefings (see {@link TextTemplate}). A value has the name of its key in the tuning file it comes from, so
 * {@code {rail.zoom_factor}} is {@code rail.zoom_factor} of {@code data/weapons.txt}. Only the values listed in
 * {@link #NAMES} can be quoted. Instances are immutable.
 */
public final class TextValues {

    /** The Rail Lance's magnification at full zoom, written as {@link TextFormat#decimal} does. */
    public static final String RAIL_ZOOM_FACTOR = "rail.zoom_factor";

    /** The names a text may quote. */
    public static final Set<String> NAMES = Collections.singleton(RAIL_ZOOM_FACTOR);

    private final Map<String, String> values;

    private TextValues(Map<String, String> values) {
        this.values = values;
    }

    /**
     * @param weapons the weapon balance, as loaded at start-up
     * @param difficulty what each difficulty multiplies; no text quotes it yet
     * @param world the level's balance; no text quotes it yet
     * @return the values a text may quote, written the way they are shown
     */
    public static TextValues of(WeaponTuning weapons, DifficultyTuning difficulty, WorldTuning world) {
        if (weapons == null || difficulty == null || world == null) {
            throw new IllegalArgumentException("Text values need the weapon, difficulty and world tuning");
        }
        Map<String, String> values = new LinkedHashMap<>();
        values.put(RAIL_ZOOM_FACTOR, TextFormat.decimal(weapons.railZoomFactor));
        return new TextValues(values);
    }

    /**
     * @param name one of {@link #NAMES}
     * @return the value as shown
     * @throws IllegalArgumentException if the name is not one of {@link #NAMES}
     */
    public String get(String name) {
        String value = values.get(name);
        if (value == null) throw new IllegalArgumentException("Unknown text value '" + name + "'");
        return value;
    }
}

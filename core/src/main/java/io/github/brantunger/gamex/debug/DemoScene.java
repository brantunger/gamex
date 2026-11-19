package io.github.brantunger.gamex.debug;

import io.github.brantunger.gamex.GameSimulation;
import io.github.brantunger.gamex.data.TuningFile;
import io.github.brantunger.gamex.hud.MenuState;
import io.github.brantunger.gamex.player.Difficulty;
import io.github.brantunger.gamex.player.DifficultyTuning;
import io.github.brantunger.gamex.player.Player;
import io.github.brantunger.gamex.player.PlayerLoadout;
import io.github.brantunger.gamex.weapon.WeaponSlot;
import io.github.brantunger.gamex.weapon.WeaponTuning;
import io.github.brantunger.gamex.world.Enemy;
import io.github.brantunger.gamex.world.EnemyTuning;
import io.github.brantunger.gamex.world.GameMap;
import io.github.brantunger.gamex.world.Levels;
import io.github.brantunger.gamex.world.WorldTuning;

import java.util.Arrays;
import java.util.Collections;
import java.util.EnumSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;

/**
 * What a debug screenshot run starts with: the screen, the level, what the player carries, and where the player
 * stands and looks. It is the header block of a demo script in {@code assets/demos/} (see {@link DemoScript}); a run
 * without a script starts with {@link #STANDING}.
 *
 * <p>The header block holds these lines, each once, and ends at the first blank line:
 * <ul>
 *   <li>{@code screen}: {@code playing}, {@code title} or {@code stage_select}, the screen the run opens on.</li>
 *   <li>{@code stage}: the {@code id} of a campaign stage (for example {@code 04_lockdown_wing}),
 *       {@value #TRAINING_RANGE} for the Training Range, or {@value #FIRST_LEVEL} for the original first level,
 *       which is a map without a stage around it.</li>
 *   <li>{@code loadout}: {@value #FRESH} for full health, no armor and the pistol alone, or {@value #CUSTOM} for
 *       what the {@code loadout.*} lines say. Those lines belong to a custom loadout only: {@code loadout.health},
 *       {@code loadout.armor} and {@code loadout.weapon} (the weapon in hand: {@code pistol}, {@code scattergun} or
 *       {@code raillance}) are needed, and {@code loadout.shells} and {@code loadout.slugs} may be left out. A
 *       {@code loadout.shells} line gives the player the Scattergun with that many shells, and a
 *       {@code loadout.slugs} line the Rail Lance with that many slugs. The weapon in hand must be the pistol or
 *       a weapon given this way with at least one round.</li>
 *   <li>{@code position} (may be left out): {@code x, y} on the map, in cells; without it the player stands on the
 *       map's spawn.</li>
 *   <li>{@code look} (may be left out): {@code yaw, pitch} in degrees, turned from where the spawn faces.</li>
 *   <li>{@code unlock_all_stages} (may be left out): {@code true} to open every campaign stage on the Select Stage
 *       screen.</li>
 *   <li>{@code keep_enemies} (may be left out): the kinds of enemy that stay, in lower case and separated by commas
 *       (for example {@code warden}); every enemy of another kind is destroyed before the first frame.</li>
 * </ul>
 * The difficulty is always Normal. A missing line, a line this class does not list and a value it cannot read each
 * fail the load with a message naming the file and, except for a missing line, the line.
 *
 * <p>Instances are immutable and safe to share.
 */
public final class DemoScene {

    /** The {@code stage} value of the Training Range. */
    public static final String TRAINING_RANGE = "training_range";
    /** The {@code stage} value of the original first level, {@link Levels#first()}. */
    public static final String FIRST_LEVEL = "first_level";
    /** The {@code loadout} value of {@link PlayerLoadout#fresh()}. */
    public static final String FRESH = "fresh";
    /** The {@code loadout} value of a loadout spelled out by the {@code loadout.*} lines. */
    public static final String CUSTOM = "custom";

    /** The scene of a run without a script: the original first level with a fresh loadout, in play. */
    public static final DemoScene STANDING = new DemoScene(MenuState.Screen.PLAYING, FIRST_LEVEL,
        PlayerLoadout.fresh(), null, null, false, null);

    private static final String SCREEN = "screen";
    private static final String STAGE = "stage";
    private static final String LOADOUT = "loadout";
    private static final String HEALTH = "loadout.health";
    private static final String ARMOR = "loadout.armor";
    private static final String WEAPON = "loadout.weapon";
    private static final String SHELLS = "loadout.shells";
    private static final String SLUGS = "loadout.slugs";
    private static final String POSITION = "position";
    private static final String LOOK = "look";
    private static final String UNLOCK_ALL_STAGES = "unlock_all_stages";
    private static final String KEEP_ENEMIES = "keep_enemies";

    private static final List<String> REQUIRED = Arrays.asList(SCREEN, STAGE, LOADOUT);
    private static final List<String> OPTIONAL = Arrays.asList(HEALTH, ARMOR, WEAPON, SHELLS, SLUGS, POSITION, LOOK,
        UNLOCK_ALL_STAGES, KEEP_ENEMIES);
    private static final List<String> CUSTOM_REQUIRED = Arrays.asList(HEALTH, ARMOR, WEAPON);
    private static final List<String> CUSTOM_KEYS = Arrays.asList(HEALTH, ARMOR, WEAPON, SHELLS, SLUGS);

    private static final Map<String, MenuState.Screen> SCREENS = screens();
    private static final Map<String, WeaponSlot> WEAPONS = weapons();

    /** The screen the run opens on. */
    public final MenuState.Screen startScreen;
    /** A campaign stage id, {@link #TRAINING_RANGE} or {@link #FIRST_LEVEL}. */
    public final String stage;
    /** What the player carries into the level. */
    public final PlayerLoadout loadout;
    /** Whether every campaign stage is open on the Select Stage screen. */
    public final boolean unlockAllStages;

    /** The player's x and y on the map, or null for the map's spawn. */
    private final float[] position;
    /** The yaw and pitch the player turns by from where the spawn faces, or null. */
    private final float[] look;
    /** The kinds of enemy that stay, or null when all do. */
    private final Set<Enemy.Kind> keptEnemies;

    private DemoScene(MenuState.Screen startScreen, String stage, PlayerLoadout loadout, float[] position,
                      float[] look, boolean unlockAllStages, Set<Enemy.Kind> keptEnemies) {
        this.startScreen = startScreen;
        this.stage = stage;
        this.loadout = loadout;
        this.position = position;
        this.look = look;
        this.unlockAllStages = unlockAllStages;
        this.keptEnemies = keptEnemies;
    }

    /**
     * Builds the level the run plays: the stage's map with the scene's loadout, the enemies the scene does not keep
     * destroyed, and the player placed and turned.
     * @param enemyTuning the enemy balance, as loaded at start-up
     * @param difficultyTuning what each difficulty multiplies, as loaded at start-up
     * @param weaponTuning the weapon balance, as loaded at start-up
     * @param worldTuning the balance of pickups, canisters, shock grates and noise, as loaded at start-up
     * @return the level, before its first frame
     * @throws IllegalArgumentException if {@link #stage} is not the id of a campaign stage, {@link #TRAINING_RANGE}
     *     or {@link #FIRST_LEVEL}
     */
    public GameSimulation createSimulation(EnemyTuning enemyTuning, DifficultyTuning difficultyTuning,
                                           WeaponTuning weaponTuning, WorldTuning worldTuning) {
        GameMap map;
        boolean completeOnAllTargets = false;
        if (FIRST_LEVEL.equals(stage)) {
            map = Levels.first();
        } else {
            Levels.Stage loaded = TRAINING_RANGE.equals(stage) ? Levels.trainingRange() : Levels.stage(stage);
            map = loaded.map;
            completeOnAllTargets = loaded.completeOnAllTargets;
        }
        GameSimulation simulation = new GameSimulation(map, loadout, completeOnAllTargets, enemyTuning,
            difficultyTuning, weaponTuning, worldTuning);
        if (keptEnemies != null) {
            for (Enemy enemy : simulation.enemies) {
                if (!keptEnemies.contains(enemy.getKind())) enemy.damage(Float.MAX_VALUE);
            }
        }
        if (position != null) simulation.player.position.set(position[0], position[1]);
        if (look != null) simulation.player.look(look[0], look[1]);
        return simulation;
    }

    /**
     * Reads a scene from the header block of a demo script.
     * @param assetPath path label included in error messages
     * @param lines the script's lines
     * @return the header block, for {@link TuningFile#bodyStart()}, and the scene it holds
     */
    static Parsed parse(String assetPath, List<String> lines) {
        TuningFile header = TuningFile.parseHeader(assetPath, lines, "demo", "timeline", REQUIRED,
            Collections.emptyList(), OPTIONAL);

        String screenName = header.getString(SCREEN);
        MenuState.Screen screen = SCREENS.get(screenName);
        if (screen == null) {
            throw header.invalid(SCREEN, "must be one of " + String.join(", ", SCREENS.keySet()));
        }
        String stage = header.getString(STAGE);
        if (!FIRST_LEVEL.equals(stage) && !TRAINING_RANGE.equals(stage)) {
            // A level file that cannot be read fails here under its own name, not as a bad stage line.
            boolean listed = false;
            for (Levels.Stage campaignStage : Levels.campaign()) {
                if (campaignStage.id.equals(stage)) listed = true;
            }
            if (!listed) {
                throw header.invalid(STAGE, "must be " + FIRST_LEVEL + ", " + TRAINING_RANGE
                    + " or the id of a stage listed in " + Levels.CAMPAIGN_PATH);
            }
        }

        PlayerLoadout loadout;
        String loadoutKind = header.getString(LOADOUT);
        if (FRESH.equals(loadoutKind)) {
            for (String key : CUSTOM_KEYS) {
                if (header.has(key)) {
                    throw header.invalid(key, "belongs to 'loadout: " + CUSTOM + "' and this loadout is " + FRESH);
                }
            }
            loadout = PlayerLoadout.fresh();
        } else if (CUSTOM.equals(loadoutKind)) {
            for (String key : CUSTOM_REQUIRED) {
                if (!header.has(key)) {
                    throw header.invalid(LOADOUT, "a custom loadout needs a '" + key + "' line");
                }
            }
            float health = header.getFloat(HEALTH);
            if (health < 1f || health > Player.MAX_HEALTH) {
                throw header.invalid(HEALTH, "must be from 1 to " + Math.round(Player.MAX_HEALTH));
            }
            float armor = header.getFloat(ARMOR);
            if (armor < 0f || armor > Player.MAX_ARMOR) {
                throw header.invalid(ARMOR, "must be from 0 to " + Math.round(Player.MAX_ARMOR));
            }
            boolean hasScattergun = header.has(SHELLS);
            int shells = hasScattergun ? count(header, SHELLS) : 0;
            boolean hasRailLance = header.has(SLUGS);
            int slugs = hasRailLance ? count(header, SLUGS) : 0;
            WeaponSlot weapon = WEAPONS.get(header.getString(WEAPON));
            if (weapon == null) {
                throw header.invalid(WEAPON, "must be one of " + String.join(", ", WEAPONS.keySet()));
            }
            loadout = new PlayerLoadout(health, armor, Difficulty.NORMAL, hasScattergun, shells, hasRailLance, slugs,
                weapon);
            if (loadout.activeWeapon != weapon) {
                throw header.invalid(WEAPON, "the loadout holds no loaded " + header.getString(WEAPON));
            }
        } else {
            throw header.invalid(LOADOUT, "must be " + FRESH + " or " + CUSTOM);
        }

        float[] position = header.has(POSITION) ? header.getFloats(POSITION, 2) : null;
        float[] look = header.has(LOOK) ? header.getFloats(LOOK, 2) : null;
        boolean unlockAllStages = header.has(UNLOCK_ALL_STAGES) && header.getBoolean(UNLOCK_ALL_STAGES);
        Set<Enemy.Kind> kept = null;
        if (header.has(KEEP_ENEMIES)) {
            kept = EnumSet.noneOf(Enemy.Kind.class);
            // The limit keeps the empty name a trailing comma leaves, so it is reported and not dropped.
            for (String item : header.getString(KEEP_ENEMIES).split(",", -1)) {
                Enemy.Kind kind = enemyKind(item.trim());
                if (kind == null) {
                    throw header.invalid(KEEP_ENEMIES, "unknown enemy kind '" + item.trim() + "' (the kinds are "
                        + enemyKindNames() + ")");
                }
                kept.add(kind);
            }
        }
        return new Parsed(header, new DemoScene(screen, stage, loadout, position, look, unlockAllStages, kept));
    }

    /** A scene and the header block it was read from. */
    static final class Parsed {
        final TuningFile header;
        final DemoScene scene;

        Parsed(TuningFile header, DemoScene scene) {
            this.header = header;
            this.scene = scene;
        }
    }

    private static int count(TuningFile header, String key) {
        int count = header.getInt(key);
        if (count < 0) throw header.invalid(key, "must not be negative");
        return count;
    }

    private static Enemy.Kind enemyKind(String name) {
        for (Enemy.Kind kind : Enemy.Kind.values()) {
            if (kind.name().toLowerCase(Locale.ROOT).equals(name)) return kind;
        }
        return null;
    }

    private static String enemyKindNames() {
        StringBuilder names = new StringBuilder();
        for (Enemy.Kind kind : Enemy.Kind.values()) {
            if (names.length() > 0) names.append(", ");
            names.append(kind.name().toLowerCase(Locale.ROOT));
        }
        return names.toString();
    }

    private static Map<String, MenuState.Screen> screens() {
        Map<String, MenuState.Screen> screens = new LinkedHashMap<>();
        screens.put("playing", MenuState.Screen.PLAYING);
        screens.put("title", MenuState.Screen.TITLE);
        screens.put("stage_select", MenuState.Screen.STAGE_SELECT);
        return Collections.unmodifiableMap(screens);
    }

    private static Map<String, WeaponSlot> weapons() {
        Map<String, WeaponSlot> weapons = new LinkedHashMap<>();
        weapons.put("pistol", WeaponSlot.PISTOL);
        weapons.put("scattergun", WeaponSlot.SCATTERGUN);
        weapons.put("raillance", WeaponSlot.RAIL_LANCE);
        return Collections.unmodifiableMap(weapons);
    }
}

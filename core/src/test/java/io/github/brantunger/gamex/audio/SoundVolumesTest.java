package io.github.brantunger.gamex.audio;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.badlogic.gdx.Audio;
import com.badlogic.gdx.Files;
import com.badlogic.gdx.Gdx;
import com.badlogic.gdx.audio.Sound;
import com.badlogic.gdx.files.FileHandle;
import io.github.brantunger.gamex.TestTuning;
import io.github.brantunger.gamex.hud.MenuState;
import io.github.brantunger.gamex.settings.GameSettings;
import io.github.brantunger.gamex.settings.MapKeyValueStore;
import io.github.brantunger.gamex.settings.SaveData;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.lang.reflect.Proxy;
import java.util.ArrayList;
import java.util.EnumMap;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * Verifies {@link SoundVolumes}: every cue takes the gain of its own line, the shipped table loads, every mistake
 * in a table is reported, and {@link SoundManager} plays a cue at the gain its table gives.
 */
class SoundVolumesTest {

    private static final String FILE = "data/test-volumes.txt";

    private Audio previousAudio;
    private Files previousFiles;
    /** The volume of each {@code play} call, per asset path. */
    private final Map<String, List<Float>> playedVolumes = new HashMap<>();

    @BeforeEach
    void saveGlobals() {
        previousAudio = Gdx.audio;
        previousFiles = Gdx.files;
    }

    @AfterEach
    void restoreGlobals() {
        Gdx.audio = previousAudio;
        Gdx.files = previousFiles;
    }

    /** @return a table built in code that gives every cue the gain 0.5 */
    private static Map<SoundEvent.Type, Float> aGainForEveryCue() {
        Map<SoundEvent.Type, Float> gains = new EnumMap<>(SoundEvent.Type.class);
        for (SoundEvent.Type type : SoundEvent.Type.values()) gains.put(type, 0.5f);
        return gains;
    }

    /** @return the text of a table giving every cue the gain 0.5 */
    private static String table() {
        return table(new EnumMap<>(SoundEvent.Type.class));
    }

    /** @return the text of a table giving every cue the gain 0.5, except that {@code overrides} replace lines */
    private static String table(Map<SoundEvent.Type, String> overrides) {
        StringBuilder text = new StringBuilder("// A test table.\n");
        for (SoundEvent.Type type : SoundEvent.Type.values()) {
            String line = overrides.getOrDefault(type, SoundVolumes.key(type) + ": 0.5");
            if (!line.isEmpty()) text.append(line).append('\n');
        }
        return text.toString();
    }

    /** @return the 0.5 table with the line of {@code type} replaced by {@code line}, or left out if it is empty */
    private static String tableWith(SoundEvent.Type type, String line) {
        Map<SoundEvent.Type, String> overrides = new EnumMap<>(SoundEvent.Type.class);
        overrides.put(type, line);
        return table(overrides);
    }

    private static String messageOf(String text) {
        return assertThrows(IllegalArgumentException.class, () -> SoundVolumes.parse(FILE, text)).getMessage();
    }

    /** Stubs libGDX so that every sound loads and records the volume it is played at. */
    private void installRecordingAudio() {
        Gdx.files = (Files) Proxy.newProxyInstance(
            Files.class.getClassLoader(),
            new Class<?>[] {Files.class},
            (proxy, method, args) -> {
                if ("internal".equals(method.getName())) return new FileHandle((String) args[0]);
                throw new UnsupportedOperationException(method.getName());
            });
        Gdx.audio = (Audio) Proxy.newProxyInstance(
            Audio.class.getClassLoader(),
            new Class<?>[] {Audio.class},
            (proxy, method, args) -> {
                if (!"newSound".equals(method.getName())) throw new UnsupportedOperationException(method.getName());
                return soundStub(((FileHandle) args[0]).path());
            });
    }

    private Sound soundStub(String path) {
        return (Sound) Proxy.newProxyInstance(
            Sound.class.getClassLoader(),
            new Class<?>[] {Sound.class},
            (proxy, method, args) -> {
                if ("play".equals(method.getName()) && args != null) {
                    playedVolumes.computeIfAbsent(path, p -> new ArrayList<>()).add((Float) args[0]);
                }
                return method.getReturnType() == long.class ? (Object) 7L : null;
            });
    }

    /** @return the volumes a manager with {@code volumes} plays one MENU_MOVE event at */
    private List<Float> playMenuMove(SoundVolumes volumes, GameSettings settings) {
        playedVolumes.clear();
        // The menu reads the campaign, so it is built before the stubs, which serve no file contents, go in.
        restoreGlobals();
        MenuState menu = TestTuning.menu(settings, new SaveData(new MapKeyValueStore()), MenuState.Screen.TITLE);
        menu.moveSelection(1);
        installRecordingAudio();
        SoundManager manager = new SoundManager(false, volumes);
        manager.update(null, menu, settings);
        manager.dispose();
        return playedVolumes.getOrDefault(SoundManager.assetPath(SoundEvent.Type.MENU_MOVE), new ArrayList<>());
    }

    /** A table in which no two cues have the same gain, so a cue that took another's line, or none, shows. */
    @Test
    void everyCueTakesTheGainOfItsOwnLine() {
        Map<SoundEvent.Type, String> lines = new EnumMap<>(SoundEvent.Type.class);
        for (SoundEvent.Type type : SoundEvent.Type.values()) {
            lines.put(type, SoundVolumes.key(type) + ": " + (type.ordinal() + 1) / 128f);
        }

        SoundVolumes volumes = SoundVolumes.parse(FILE, table(lines));

        for (SoundEvent.Type type : SoundEvent.Type.values()) {
            assertEquals((type.ordinal() + 1) / 128f, volumes.gain(type), 0f, type.name());
        }
    }

    /** Whatever gains the shipped table holds, it loads and gives every cue one. */
    @Test
    void theShippedTableLoads() {
        SoundVolumes shipped = SoundVolumes.load();

        for (SoundEvent.Type type : SoundEvent.Type.values()) {
            assertTrue(shipped.gain(type) >= 0f, type.name());
        }
    }

    @Test
    void aCueIsKeyedByItsFileNameWithoutTheExtension() {
        assertEquals("player-jump", SoundVolumes.key(SoundEvent.Type.PLAYER_JUMP));
        assertEquals("scattergun-alt-fire", SoundVolumes.key(SoundEvent.Type.SCATTERGUN_ALT_FIRE));
        for (SoundEvent.Type type : SoundEvent.Type.values()) {
            assertEquals(SoundManager.fileName(type), SoundVolumes.key(type) + ".wav");
        }
    }

    @Test
    void anAlteredLineChangesThatCueAndNoOther() {
        SoundVolumes altered = SoundVolumes.parse(FILE, tableWith(SoundEvent.Type.MENU_MOVE, "menu-move: 0.125"));

        for (SoundEvent.Type type : SoundEvent.Type.values()) {
            float expected = type == SoundEvent.Type.MENU_MOVE ? 0.125f : 0.5f;
            assertEquals(expected, altered.gain(type), 0f, type.name());
        }
    }

    @Test
    void theManagerPlaysACueAtTheGainOfItsTable() {
        GameSettings settings = TestTuning.settings();
        float master = settings.getMasterVolume();
        SoundVolumes quiet = SoundVolumes.parse(FILE, tableWith(SoundEvent.Type.MENU_MOVE, "menu-move: 0.125"));
        SoundVolumes loud = SoundVolumes.parse(FILE, tableWith(SoundEvent.Type.MENU_MOVE, "menu-move: 0.75"));

        assertEquals(List.of(0.125f * master * 1f * 1f), playMenuMove(quiet, settings));
        assertEquals(List.of(0.75f * master * 1f * 1f), playMenuMove(loud, settings));
    }

    @Test
    void aGainOfZeroSilencesTheCue() {
        SoundVolumes silent = SoundVolumes.parse(FILE, tableWith(SoundEvent.Type.MENU_MOVE, "menu-move: 0"));

        assertEquals(List.of(), playMenuMove(silent, TestTuning.settings()));
    }

    @Test
    void aCueWithoutALineFailsNamingTheFileAndTheKey() {
        assertEquals("Missing 'door-open' key in " + FILE, messageOf(tableWith(SoundEvent.Type.DOOR_OPEN, "")));
    }

    @Test
    void aKeyThatNamesNoCueFailsNamingTheFileTheKeyAndTheLine() {
        assertEquals("Unknown key 'menu_move' in " + FILE + " at line 2",
            messageOf("// A mistyped key.\nmenu_move: 0.35\n" + table()));
    }

    @Test
    void thereIsNoDefaultLine() {
        assertEquals("Unknown key 'default' in " + FILE + " at line 1", messageOf("default: 0.70\n" + table()));
    }

    @Test
    void aSecondLineForACueFails() {
        int lastLine = SoundEvent.Type.values().length + 2;
        assertEquals("Duplicate 'explosion' key in " + FILE + " at line " + lastLine,
            messageOf(table() + "explosion: 0.9\n"));
    }

    @Test
    void aNegativeGainFailsNamingTheFileTheKeyAndTheLine() {
        int line = SoundEvent.Type.PISTOL_SHOT.ordinal() + 2;
        assertEquals("Invalid 'pistol-shot' in " + FILE + " at line " + line + ": must be zero or more, got '-0.1'",
            messageOf(tableWith(SoundEvent.Type.PISTOL_SHOT, "pistol-shot: -0.1")));
    }

    @Test
    void aGainThatIsNotAFiniteDecimalNumberFails() {
        int line = SoundEvent.Type.PISTOL_SHOT.ordinal() + 2;
        for (String value : new String[] {"0.5f", "NaN", "Infinity", "1e99", "loud"}) {
            assertEquals("Invalid float '" + value + "' for 'pistol-shot' in " + FILE + " at line " + line,
                messageOf(tableWith(SoundEvent.Type.PISTOL_SHOT, "pistol-shot: " + value)));
        }
        assertEquals("Empty 'pistol-shot' key in " + FILE + " at line " + line,
            messageOf(tableWith(SoundEvent.Type.PISTOL_SHOT, "pistol-shot:")));
    }

    @Test
    void theConstructorRejectsAMissingCueAndABadGain() {
        Map<SoundEvent.Type, Float> gains = aGainForEveryCue();
        gains.remove(SoundEvent.Type.EXPLOSION);
        assertEquals("Missing sound volume for EXPLOSION",
            assertThrows(IllegalArgumentException.class, () -> new SoundVolumes(gains)).getMessage());

        for (float bad : new float[] {-0.5f, Float.NaN, Float.POSITIVE_INFINITY}) {
            Map<SoundEvent.Type, Float> withBad = aGainForEveryCue();
            withBad.put(SoundEvent.Type.EXPLOSION, bad);
            assertEquals("Sound volume for EXPLOSION must be finite and zero or more, got " + bad,
                assertThrows(IllegalArgumentException.class, () -> new SoundVolumes(withBad)).getMessage());
        }
    }

    @Test
    void theManagerRejectsAMissingTable() {
        assertThrows(NullPointerException.class, () -> new SoundManager(true, null));
    }
}

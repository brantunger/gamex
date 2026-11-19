package io.github.brantunger.gamex.render;

import io.github.brantunger.gamex.data.TuningFile;
import io.github.brantunger.gamex.world.GameMap;

import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * Which texture file covers each wall type: the table in {@code assets/data/walls.txt}, one
 * {@code wall character: file name} line per character of {@link GameMap#WALL_TYPES}. The files are {@code .png}
 * images in {@code assets/textures/}.
 *
 * <p>There is no fallback texture. {@link #load()} fails when a wall character has no line or a line names
 * something that is not a wall character, the constructor fails the same way for a table built in code, and
 * {@link #file(char)} fails for a character that is not a wall character.
 * Instances are immutable and safe to share.
 */
public final class WallTextures {

    /** The table's path inside {@code assets/}. */
    public static final String FILE = "data/walls.txt";

    private final Map<Character, String> files;

    /**
     * @param files the texture file name of each wall character, as {@link #load()} reads them: one for every
     *     character of {@link GameMap#WALL_TYPES} and no other
     * @throws IllegalArgumentException if a character or a file name is null, a name is not a plain {@code .png}
     *     file name, a character is not a wall character, or a wall character has no file
     */
    public WallTextures(Map<Character, String> files) {
        if (files == null) {
            throw new IllegalArgumentException("files must not be null");
        }
        Map<Character, String> copy = new LinkedHashMap<>();
        for (Map.Entry<Character, String> entry : files.entrySet()) {
            if (entry.getKey() == null) {
                throw new IllegalArgumentException("A wall character must not be null");
            }
            String problem = fileNameProblem(entry.getValue());
            if (problem != null) {
                throw new IllegalArgumentException("The texture of wall character '" + entry.getKey() + "' "
                    + problem);
            }
            if (GameMap.WALL_TYPES.indexOf(entry.getKey()) < 0) {
                throw new IllegalArgumentException("'" + entry.getKey() + "' is not a wall character; they are "
                    + GameMap.WALL_TYPES);
            }
            copy.put(entry.getKey(), entry.getValue());
        }
        for (int i = 0; i < GameMap.WALL_TYPES.length(); i++) {
            if (!copy.containsKey(GameMap.WALL_TYPES.charAt(i))) {
                throw new IllegalArgumentException("No texture for wall character '" + GameMap.WALL_TYPES.charAt(i)
                    + "'");
            }
        }
        this.files = Collections.unmodifiableMap(copy);
    }

    /**
     * Reads the table from {@code assets/data/walls.txt}.
     * @return the table, with a file for every character of {@link GameMap#WALL_TYPES} and no other
     * @throws IllegalArgumentException if a wall character has no line, a line's key is not a wall character, or a
     *     value is not a plain {@code .png} file name; the message names the file, the key and, except for a
     *     character that has no line, the line
     */
    public static WallTextures load() {
        return from(TuningFile.load(FILE, wallKeys(), Collections.emptyList()));
    }

    /**
     * Builds the table from a parsed file.
     * @param file a file parsed with {@link #wallKeys()} as its keys
     * @return the table
     * @throws IllegalArgumentException if a value is not a plain {@code .png} file name
     */
    static WallTextures from(TuningFile file) {
        Map<Character, String> files = new LinkedHashMap<>();
        for (String key : wallKeys()) {
            String name = file.getString(key);
            String problem = fileNameProblem(name);
            if (problem != null) {
                throw file.invalid(key, problem);
            }
            files.put(key.charAt(0), name);
        }
        return new WallTextures(files);
    }

    /** @return the keys the table must hold: each character of {@link GameMap#WALL_TYPES}, as a string. */
    static List<String> wallKeys() {
        List<String> keys = new ArrayList<>(GameMap.WALL_TYPES.length());
        for (int i = 0; i < GameMap.WALL_TYPES.length(); i++) {
            keys.add(String.valueOf(GameMap.WALL_TYPES.charAt(i)));
        }
        return keys;
    }

    /**
     * @param wallType a wall type from the map layout
     * @return the name of the file in {@code assets/textures/} that covers that wall type
     * @throws IllegalArgumentException if the table has no line for the character; the message names it
     */
    public String file(char wallType) {
        String name = files.get(wallType);
        if (name == null) {
            throw new IllegalArgumentException("No texture for wall character '" + wallType + "' in " + FILE
                + ", which lists " + files.keySet());
        }
        return name;
    }

    /** @return the distinct texture file names of the table, in the order their wall characters were given. */
    public Set<String> files() {
        return Collections.unmodifiableSet(new LinkedHashSet<>(files.values()));
    }

    /** @return what is wrong with a texture file name, or null when it is a plain {@code .png} file name. */
    private static String fileNameProblem(String name) {
        if (name == null || name.length() <= ".png".length() || !name.endsWith(".png")) {
            return "must be the name of a .png file in assets/textures/";
        }
        if (name.indexOf('/') >= 0 || name.indexOf('\\') >= 0) {
            return "must be a file name without a folder";
        }
        return null;
    }
}

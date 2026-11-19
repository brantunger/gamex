package io.github.brantunger.gamex.render;

import com.badlogic.gdx.utils.GdxRuntimeException;
import io.github.brantunger.gamex.data.AssetText;

/**
 * The GLSL shader files in {@code assets/shaders/} and how their text is read. The game and the model previewer both
 * read the files as they are, with no copy in code to fall back on: a missing or unreadable file stops the start with
 * an error that names it.
 *
 * <p>The files repeat two things that also live in code, because the game must look the same wherever they are used:
 * the four face shades that {@link io.github.brantunger.gamex.world.MapGeometry} bakes into the map's vertices
 * (1.0, 0.72, 0.9 and 0.62, in {@code model.vert.glsl} and {@code viewmodel.vert.glsl}), and the direction of the
 * world models' light ({@link #MODEL_LIGHT_X}, {@link #MODEL_LIGHT_Y}, {@link #MODEL_LIGHT_Z}, in
 * {@code model.vert.glsl}).
 */
public final class Shaders {

    /** The map's walls, floor and ceiling. */
    public static final String WORLD_VERTEX = "shaders/world.vert.glsl";
    public static final String WORLD_FRAGMENT = "shaders/world.frag.glsl";
    /** The flat-shaded world models; the model previewer draws with the same two files. */
    public static final String MODEL_VERTEX = "shaders/model.vert.glsl";
    public static final String MODEL_FRAGMENT = "shaders/model.frag.glsl";
    /** The first-person weapon and hand, which keep a light of their own, fixed to the view. */
    public static final String VIEWMODEL_VERTEX = "shaders/viewmodel.vert.glsl";
    public static final String VIEWMODEL_FRAGMENT = "shaders/viewmodel.frag.glsl";

    /**
     * Where the world models' light comes from, in world space, before it is normalised. {@code model.vert.glsl}
     * states the same direction as {@code lightDir}, and {@link WardenModel} lights the Warden from it.
     */
    static final float MODEL_LIGHT_X = -0.3f;
    static final float MODEL_LIGHT_Y = 0.9f;
    static final float MODEL_LIGHT_Z = 0.4f;

    private Shaders() {}

    /**
     * Reads a shader file as text, through {@link AssetText#read}: an internal asset when libGDX is running, and
     * otherwise (headless tests and tools) the first copy found in the places listed there.
     *
     * @param assetPath the path below the assets folder, such as {@link #MODEL_VERTEX}
     * @return the text of the file, exactly as it is stored
     * @throws IllegalArgumentException if the path is null or blank
     * @throws GdxRuntimeException if the file is missing, unreadable or empty; the message names the file
     */
    public static String load(String assetPath) {
        if (assetPath == null || assetPath.trim().isEmpty()) {
            throw new IllegalArgumentException("Shader asset path must not be blank");
        }
        String text;
        try {
            text = AssetText.read("shader", assetPath);
        } catch (RuntimeException e) {
            throw new GdxRuntimeException("The shader file " + assetPath + " could not be read: " + e.getMessage(), e);
        }
        if (text == null || text.trim().isEmpty()) {
            throw new GdxRuntimeException("The shader file " + assetPath + " is empty");
        }
        return text;
    }

    /** Names both files of a shader program, for the error of a program that did not compile. */
    static String describe(String vertexPath, String fragmentPath) {
        return " (" + vertexPath + ", " + fragmentPath + ")";
    }
}

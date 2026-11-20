package io.github.brantunger.gamex.render;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import io.github.brantunger.gamex.world.GameMap;
import io.github.brantunger.gamex.world.MapGeometry;
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.lang.reflect.Field;
import java.lang.reflect.Modifier;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.List;
import java.util.Set;
import java.util.TreeSet;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Checks the shader files in {@code assets/shaders/} as text; there is no OpenGL context in the tests. The files are
 * read straight from the assets folder here, not through the game's loader, which has tests of its own.
 */
class ShaderFilesTest {

    static final String WORLD_VERTEX = "shaders/world.vert.glsl";
    static final String WORLD_FRAGMENT = "shaders/world.frag.glsl";
    static final String MODEL_VERTEX = "shaders/model.vert.glsl";
    static final String MODEL_FRAGMENT = "shaders/model.frag.glsl";
    static final String VIEWMODEL_VERTEX = "shaders/viewmodel.vert.glsl";
    static final String VIEWMODEL_FRAGMENT = "shaders/viewmodel.frag.glsl";

    private static final String[] ALL_FILES = {
        WORLD_VERTEX, WORLD_FRAGMENT, MODEL_VERTEX, MODEL_FRAGMENT, VIEWMODEL_VERTEX, VIEWMODEL_FRAGMENT
    };

    private static final Pattern DECLARATION =
        Pattern.compile("(?m)^\\s*(uniform|attribute)\\s+\\w+\\s+(\\w+)\\s*;");

    /**
     * Finds a file of the assets folder. The build passes the folder in as {@code gamex.assets}; a run from an IDE
     * finds it from the root project or the core project.
     */
    static Path resolveAsset(String assetPath) {
        List<Path> candidates = new ArrayList<>();
        String assets = System.getProperty("gamex.assets");
        if (assets != null && !assets.isEmpty()) {
            candidates.add(Paths.get(assets, assetPath));
        }
        candidates.add(Paths.get("assets", assetPath));
        candidates.add(Paths.get("..", "assets", assetPath));
        for (Path candidate : candidates) {
            if (Files.isRegularFile(candidate)) return candidate;
        }
        List<Path> tried = new ArrayList<>(candidates.size());
        for (Path candidate : candidates) {
            tried.add(candidate.toAbsolutePath().normalize());
        }
        throw new IllegalStateException("The shader file " + assetPath + " was not found. Set the system property "
            + "gamex.assets to the assets folder, or run from the root project or the core project. Tried: " + tried);
    }

    static String readShaderFile(String assetPath) {
        try {
            return new String(Files.readAllBytes(resolveAsset(assetPath)), StandardCharsets.UTF_8);
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
    }

    /** Takes the comments, the blank lines and any carriage returns out of GLSL text, and nothing else. */
    static String withoutComments(String glsl) {
        StringBuilder out = new StringBuilder();
        for (String line : glsl.replace("\r", "").split("\n")) {
            int comment = line.indexOf("//");
            String code = comment >= 0 ? line.substring(0, comment) : line;
            if (code.trim().isEmpty()) continue;
            out.append(code.stripTrailing()).append('\n');
        }
        return out.toString();
    }

    private static Set<String> declared(String kind, String... assetPaths) {
        Set<String> names = new TreeSet<>();
        for (String assetPath : assetPaths) {
            Matcher matcher = DECLARATION.matcher(withoutComments(readShaderFile(assetPath)));
            while (matcher.find()) {
                if (matcher.group(1).equals(kind)) names.add(matcher.group(2));
            }
        }
        return names;
    }

    private static Set<String> names(String... names) {
        return new TreeSet<>(List.of(names));
    }

    @Test
    void everyShaderFileIsThereWithPlainAsciiText() {
        for (String assetPath : ALL_FILES) {
            String text = readShaderFile(assetPath);
            assertTrue(withoutComments(text).contains("void main()"), assetPath + " has no main function");
            assertFalse(text.contains("/*"), assetPath + ": use // comments only; the tests take just those out");
            for (int i = 0; i < text.length(); i++) {
                char c = text.charAt(i);
                assertTrue(c == '\n' || c == '\r' || (c >= ' ' && c < 127),
                    assetPath + " has a character that is not plain ASCII at offset " + i);
            }
        }
    }

    private static final Pattern VARYING = Pattern.compile("(?m)^\\s*varying\\s+(\\w+)\\s+(\\w+)\\s*;");

    private static Set<String> varyings(String assetPath) {
        Set<String> found = new TreeSet<>();
        Matcher matcher = VARYING.matcher(withoutComments(readShaderFile(assetPath)));
        while (matcher.find()) found.add(matcher.group(1) + " " + matcher.group(2));
        return found;
    }

    /**
     * Whatever arithmetic a shader file holds: a vertex shader sets the vertex position and hands its fragment
     * shader exactly the varyings that one reads, with the same types, and the fragment shader sets the colour.
     */
    @Test
    void eachPairOfShaderFilesFitsTogether() {
        String[][] pairs = {
            {WORLD_VERTEX, WORLD_FRAGMENT}, {MODEL_VERTEX, MODEL_FRAGMENT}, {VIEWMODEL_VERTEX, VIEWMODEL_FRAGMENT}
        };
        for (String[] pair : pairs) {
            String vertex = withoutComments(readShaderFile(pair[0]));
            String fragment = withoutComments(readShaderFile(pair[1]));
            assertTrue(vertex.contains("gl_Position ="), pair[0] + " sets no position");
            assertTrue(fragment.contains("gl_FragColor ="), pair[1] + " sets no colour");
            assertFalse(fragment.contains("attribute "), pair[1] + " declares an attribute");
            assertEquals(varyings(pair[0]), varyings(pair[1]), pair[0] + " and " + pair[1] + " share their varyings");
            assertFalse(varyings(pair[0]).isEmpty(), pair[0]);
            assertEquals(count(vertex, '{'), count(vertex, '}'), pair[0] + " braces");
            assertEquals(count(fragment, '{'), count(fragment, '}'), pair[1] + " braces");
            assertEquals(count(vertex, '('), count(vertex, ')'), pair[0] + " brackets");
            assertEquals(count(fragment, '('), count(fragment, ')'), pair[1] + " brackets");
        }
    }

    private static long count(String text, char c) {
        return text.chars().filter(x -> x == c).count();
    }

    /** The names are the contract between the files and the code that sets them: a renamed one must fail here. */
    @Test
    void shaderFilesDeclareExactlyTheUniformsAndAttributesTheRenderersSet() {
        assertEquals(names("a_position", "a_texCoord0", "a_shade"),
            declared("attribute", WORLD_VERTEX, WORLD_FRAGMENT));
        assertEquals(
            names("u_projViewTrans", "u_cameraPosition", "u_fogRange", "u_fogColour", "u_light", "u_texture"),
            declared("uniform", WORLD_VERTEX, WORLD_FRAGMENT));

        assertEquals(names("a_position", "a_normal", "a_colour", "a_gloss", "a_accent"),
            declared("attribute", MODEL_VERTEX, MODEL_FRAGMENT));
        assertEquals(
            names("u_projViewTrans", "u_model", "u_normalMatrix", "u_accentColour", "u_cameraPosition", "u_fogRange",
                "u_fogColour", "u_light", "u_hitFlash"),
            declared("uniform", MODEL_VERTEX, MODEL_FRAGMENT));

        assertEquals(names("a_position", "a_normal", "a_colour", "a_gloss", "a_accent"),
            declared("attribute", VIEWMODEL_VERTEX, VIEWMODEL_FRAGMENT));
        assertEquals(
            names("u_projTrans", "u_model", "u_normalMatrix", "u_viewToWorld", "u_accentColour", "u_light",
                "u_charge"),
            declared("uniform", VIEWMODEL_VERTEX, VIEWMODEL_FRAGMENT));
    }

    @Test
    void renderersKeepNoShaderSourceInJavaStrings() throws IllegalAccessException {
        for (Class<?> renderer : new Class<?>[] {WorldRenderer.class, ViewmodelRenderer.class}) {
            for (Field field : renderer.getDeclaredFields()) {
                if (!Modifier.isStatic(field.getModifiers()) || field.getType() != String.class) continue;
                field.setAccessible(true);
                String value = (String) field.get(null);
                assertFalse(value != null && (value.contains("gl_Position") || value.contains("gl_FragColor")),
                    renderer.getSimpleName() + "." + field.getName() + " still holds GLSL source");
            }
        }
    }

    /**
     * A model face must be as bright as a wall, floor or ceiling facing the same way, so the two vertex shaders that
     * light models repeat the shades {@link MapGeometry} bakes into the map. The shades are read from a built map.
     */
    @Test
    void modelAndViewmodelShadersRepeatTheShadesBakedIntoTheMap() {
        MapGeometry geometry = MapGeometry.build(GameMap.parse(
            "###",
            "#>#",
            "###"));
        int shadeOffset = MapGeometry.VERTEX_SIZE - 1;
        float floor = geometry.floor[shadeOffset];
        float ceiling = geometry.ceiling[shadeOffset];
        float northSouth = Float.NaN;
        float eastWest = Float.NaN;
        for (float[] quads : geometry.walls.values()) {
            for (int quad = 0; quad < quads.length; quad += MapGeometry.QUAD_SIZE) {
                // A face whose first two corners share their x faces east or west; the others face north or south.
                boolean facesEastWest = quads[quad] == quads[quad + MapGeometry.VERTEX_SIZE];
                if (facesEastWest) eastWest = quads[quad + shadeOffset];
                else northSouth = quads[quad + shadeOffset];
            }
        }
        assertEquals(1f, northSouth, "the shaders weigh north-south faces by 1, with no factor written out");
        String expected = "float shade = weight.z + weight.x * " + eastWest + " + weight.y * (worldNormal.y > 0.0 ? "
            + floor + " : " + ceiling + ");";
        for (String assetPath : new String[] {MODEL_VERTEX, VIEWMODEL_VERTEX}) {
            assertTrue(withoutComments(readShaderFile(assetPath)).contains(expected),
                assetPath + " must shade faces as the map does: " + expected);
        }
    }
}

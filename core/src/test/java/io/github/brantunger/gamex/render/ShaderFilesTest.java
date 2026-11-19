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

    // The shader text as it was written in WorldRenderer and ViewmodelRenderer before it moved into files (commit
    // 10cd4a4), copied without a change. The files must say the same once their comments are taken out.
    static final String BASE_WORLD_VERTEX =
        "attribute vec3 a_position;\n"
            + "attribute vec2 a_texCoord0;\n"
            + "attribute float a_shade;\n"
            + "uniform mat4 u_projViewTrans;\n"
            + "uniform vec3 u_cameraPosition;\n"
            + "uniform vec2 u_fogRange;\n"
            + "varying vec2 v_texCoord;\n"
            + "varying float v_shade;\n"
            + "varying float v_fog;\n"
            + "void main() {\n"
            + "    v_texCoord = a_texCoord0;\n"
            + "    v_shade = a_shade;\n"
            + "    float range = length(a_position - u_cameraPosition);\n"
            + "    v_fog = clamp((range - u_fogRange.x) / (u_fogRange.y - u_fogRange.x), 0.0, 1.0);\n"
            + "    gl_Position = u_projViewTrans * vec4(a_position, 1.0);\n"
            + "}\n";

    static final String BASE_WORLD_FRAGMENT =
        "#ifdef GL_ES\n"
            + "precision mediump float;\n"
            + "#endif\n"
            + "uniform sampler2D u_texture;\n"
            + "uniform vec3 u_fogColour;\n"
            + "uniform float u_light;\n"
            + "varying vec2 v_texCoord;\n"
            + "varying float v_shade;\n"
            + "varying float v_fog;\n"
            + "void main() {\n"
            + "    vec3 colour = texture2D(u_texture, v_texCoord).rgb * v_shade * u_light;\n"
            + "    gl_FragColor = vec4(mix(colour, u_fogColour, v_fog), 1.0);\n"
            + "}\n";

    static final String BASE_MODEL_VERTEX =
        "attribute vec3 a_position;\n"
            + "attribute vec3 a_normal;\n"
            + "attribute vec3 a_colour;\n"
            + "attribute float a_gloss;\n"
            + "attribute float a_accent;\n"
            + "uniform mat4 u_projViewTrans;\n"
            + "uniform mat4 u_model;\n"
            + "uniform mat3 u_normalMatrix;\n"
            + "uniform vec4 u_accentColour;\n"
            + "uniform vec3 u_cameraPosition;\n"
            + "uniform vec2 u_fogRange;\n"
            + "uniform vec3 u_fogColour;\n"
            + "uniform float u_light;\n"
            + "uniform float u_hitFlash;\n"
            + "varying vec3 v_colour;\n"
            + "void main() {\n"
            + "    vec4 worldPos = u_model * vec4(a_position, 1.0);\n"
            + "    vec3 worldNormal = normalize(u_normalMatrix * a_normal);\n"
            + "    vec3 weight = worldNormal * worldNormal;\n"
            + "    float shade = weight.z + weight.x * 0.72 + weight.y * (worldNormal.y > 0.0 ? 0.9 : 0.62);\n"
            + "    vec3 lightDir = normalize(vec3(-0.3, 0.9, 0.4));\n"
            + "    float key = 0.65 + 0.55 * max(dot(worldNormal, lightDir), 0.0);\n"
            + "    vec3 delta = u_cameraPosition - worldPos.xyz;\n"
            + "    float range = length(delta);\n"
            + "    vec3 toCamera = range > 1e-4 ? delta / range : vec3(0.0, 0.0, 1.0);\n"
            + "    vec3 halfVec = normalize(lightDir + toCamera);\n"
            + "    vec3 surfaceColour = mix(a_colour, u_accentColour.rgb, a_accent);\n"
            + "    float surfaceGloss = mix(a_gloss, u_accentColour.a, a_accent);\n"
            + "    float sheen = surfaceGloss * pow(max(dot(worldNormal, halfVec), 0.0), 12.0) * 0.35;\n"
            + "    float rim = pow(1.0 - max(dot(worldNormal, toCamera), 0.0), 2.5) * 0.12;\n"
            + "    vec3 lit = (surfaceColour * shade * key + vec3(sheen + rim)) * u_light;\n"
            + "    lit = mix(lit, vec3(1.0, 0.25, 0.25), u_hitFlash * 0.65);\n"
            + "    float fog = clamp((range - u_fogRange.x) / (u_fogRange.y - u_fogRange.x), 0.0, 1.0);\n"
            + "    v_colour = mix(lit, u_fogColour, fog);\n"
            + "    gl_Position = u_projViewTrans * worldPos;\n"
            + "}\n";

    static final String BASE_MODEL_FRAGMENT =
        "#ifdef GL_ES\n"
            + "precision mediump float;\n"
            + "#endif\n"
            + "varying vec3 v_colour;\n"
            + "void main() {\n"
            + "    gl_FragColor = vec4(v_colour, 1.0);\n"
            + "}\n";

    static final String BASE_VIEWMODEL_VERTEX =
        "attribute vec3 a_position;\n"
            + "attribute vec3 a_normal;\n"
            + "attribute vec3 a_colour;\n"
            + "attribute float a_gloss;\n"
            + "attribute float a_accent;\n"
            + "uniform mat4 u_projTrans;\n"
            + "uniform mat4 u_model;\n"
            + "uniform mat3 u_normalMatrix;\n"
            + "uniform mat4 u_viewToWorld;\n"
            + "uniform vec4 u_accentColour;\n"
            + "uniform float u_light;\n"
            + "uniform float u_charge;\n"
            + "varying vec3 v_colour;\n"
            + "void main() {\n"
            + "    vec3 viewNormal = normalize(u_normalMatrix * a_normal);\n"
            + "    vec3 worldNormal = (u_viewToWorld * vec4(viewNormal, 0.0)).xyz;\n"
            // The same shades as the world's faces: north-south 1.0, east-west 0.72, floor 0.9, ceiling 0.62.
            + "    vec3 weight = worldNormal * worldNormal;\n"
            + "    float shade = weight.z + weight.x * 0.72 + weight.y * (worldNormal.y > 0.0 ? 0.9 : 0.62);\n"
            // A fixed light from above the left shoulder, so the form reads whichever way the player faces.
            + "    float key = 0.5 + 0.75 * max(dot(viewNormal, normalize(vec3(-0.3, 0.9, 0.3))), 0.0);\n"
            // The blast charge glows violet towards the muzzle, which is at negative z in gun space.
            + "    float front = clamp((-a_position.z - 0.01) / 0.1, 0.0, 1.0);\n"
            + "    vec3 baseColour = mix(a_colour, u_accentColour.rgb, a_accent);\n"
            + "    float baseGloss = mix(a_gloss, u_accentColour.a, a_accent);\n"
            // Glossy faces that sit halfway between the light and the eye pick up a sheen.
            + "    float facing = max(dot(viewNormal, normalize(vec3(-0.3, 0.9, 1.3))), 0.0);\n"
            + "    float sheen = baseGloss * pow(facing, 10.0) * 0.3;\n"
            // Faces seen nearly edge-on catch a faint rim of light, which lifts dark shapes off a dark background.
            + "    float rim = pow(1.0 - abs(viewNormal.z), 2.0) * 0.09;\n"
            + "    v_colour = (baseColour * shade * key + vec3(sheen + rim)) * u_light\n"
            + "        + vec3(0.5, 0.22, 1.0) * u_charge * front * 0.55;\n"
            + "    gl_Position = u_projTrans * u_model * vec4(a_position, 1.0);\n"
            + "}\n";

    static final String BASE_VIEWMODEL_FRAGMENT =
        "#ifdef GL_ES\n"
            + "precision mediump float;\n"
            + "#endif\n"
            + "varying vec3 v_colour;\n"
            + "void main() {\n"
            + "    gl_FragColor = vec4(v_colour, 1.0);\n"
            + "}\n";

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

    @Test
    void shaderFilesSayWhatTheInlineStringsSaidBeforeTheMove() {
        assertEquals(BASE_WORLD_VERTEX, withoutComments(readShaderFile(WORLD_VERTEX)), WORLD_VERTEX);
        assertEquals(BASE_WORLD_FRAGMENT, withoutComments(readShaderFile(WORLD_FRAGMENT)), WORLD_FRAGMENT);
        assertEquals(BASE_MODEL_VERTEX, withoutComments(readShaderFile(MODEL_VERTEX)), MODEL_VERTEX);
        assertEquals(BASE_MODEL_FRAGMENT, withoutComments(readShaderFile(MODEL_FRAGMENT)), MODEL_FRAGMENT);
        assertEquals(BASE_VIEWMODEL_VERTEX, withoutComments(readShaderFile(VIEWMODEL_VERTEX)), VIEWMODEL_VERTEX);
        assertEquals(BASE_VIEWMODEL_FRAGMENT, withoutComments(readShaderFile(VIEWMODEL_FRAGMENT)), VIEWMODEL_FRAGMENT);
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

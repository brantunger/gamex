package io.github.brantunger.gamex.render;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.badlogic.gdx.Files;
import com.badlogic.gdx.Gdx;
import com.badlogic.gdx.files.FileHandle;
import com.badlogic.gdx.utils.GdxRuntimeException;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.lang.reflect.Proxy;
import java.util.ArrayList;
import java.util.List;

/** How the shader files are read: headless and through libGDX, and what a missing file does. */
class ShadersTest {

    private static final String[] ALL_PATHS = {
        Shaders.WORLD_VERTEX, Shaders.WORLD_FRAGMENT, Shaders.MODEL_VERTEX, Shaders.MODEL_FRAGMENT,
        Shaders.VIEWMODEL_VERTEX, Shaders.VIEWMODEL_FRAGMENT
    };

    private Files previousFiles;

    @BeforeEach
    void saveGdxFiles() {
        previousFiles = Gdx.files;
        Gdx.files = null;
    }

    @AfterEach
    void restoreGdxFiles() {
        Gdx.files = previousFiles;
    }

    /** A libGDX file system whose internal files are the files of the assets folder, as in the running game. */
    private static Files assetFiles(List<String> asked) {
        return (Files) Proxy.newProxyInstance(
            Files.class.getClassLoader(),
            new Class<?>[] {Files.class},
            (proxy, method, args) -> {
                if (!method.getName().equals("internal")) {
                    throw new UnsupportedOperationException(method.getName());
                }
                String assetPath = (String) args[0];
                asked.add(assetPath);
                if (assetPath.equals("shaders/missing.vert.glsl")) {
                    return new FileHandle("no-such-folder/" + assetPath);
                }
                return new FileHandle(ShaderFilesTest.resolveAsset(assetPath).toFile());
            });
    }

    @Test
    void everyShaderFileLoadsHeadlessExactlyAsItIsStored() {
        for (String assetPath : ALL_PATHS) {
            String text = Shaders.load(assetPath);
            assertFalse(text.trim().isEmpty(), assetPath);
            assertEquals(ShaderFilesTest.readShaderFile(assetPath), text, assetPath);
        }
    }

    @Test
    void everyShaderFileLoadsAsAnInternalAssetWhenLibgdxRuns() {
        List<String> asked = new ArrayList<>();
        Gdx.files = assetFiles(asked);
        for (String assetPath : ALL_PATHS) {
            assertEquals(ShaderFilesTest.readShaderFile(assetPath), Shaders.load(assetPath), assetPath);
        }
        assertEquals(List.of(ALL_PATHS), asked);
    }

    @Test
    void theGameAndThePreviewerShareTheModelShaderPaths() {
        assertEquals(ShaderFilesTest.MODEL_VERTEX, Shaders.MODEL_VERTEX);
        assertEquals(ShaderFilesTest.MODEL_FRAGMENT, Shaders.MODEL_FRAGMENT);
        assertEquals(ShaderFilesTest.WORLD_VERTEX, Shaders.WORLD_VERTEX);
        assertEquals(ShaderFilesTest.WORLD_FRAGMENT, Shaders.WORLD_FRAGMENT);
        assertEquals(ShaderFilesTest.VIEWMODEL_VERTEX, Shaders.VIEWMODEL_VERTEX);
        assertEquals(ShaderFilesTest.VIEWMODEL_FRAGMENT, Shaders.VIEWMODEL_FRAGMENT);
    }

    @Test
    void missingShaderFileFailsLoudlyAndNamesTheFile() {
        GdxRuntimeException headless = assertThrows(GdxRuntimeException.class,
            () -> Shaders.load("shaders/missing.vert.glsl"));
        assertTrue(headless.getMessage().startsWith("The shader file shaders/missing.vert.glsl could not be read: "
            + "The shader asset shaders/missing.vert.glsl was not found."), headless.getMessage());

        Gdx.files = assetFiles(new ArrayList<>());
        GdxRuntimeException internal = assertThrows(GdxRuntimeException.class,
            () -> Shaders.load("shaders/missing.vert.glsl"));
        assertTrue(internal.getMessage().contains("The shader file shaders/missing.vert.glsl could not be read"),
            internal.getMessage());

        assertThrows(IllegalArgumentException.class, () -> Shaders.load(" "));
        assertThrows(IllegalArgumentException.class, () -> Shaders.load(null));
    }

    @Test
    void compileErrorsNameBothFilesOfTheProgram() {
        assertEquals(" (shaders/model.vert.glsl, shaders/model.frag.glsl)",
            Shaders.describe(Shaders.MODEL_VERTEX, Shaders.MODEL_FRAGMENT));
    }

    /** The Warden is lit in code and the other models in the shader; both must take the light from the same side. */
    @Test
    void modelShaderLightComesFromTheDirectionTheWardenIsLitFrom() {
        String expected = "vec3 lightDir = normalize(vec3(" + Shaders.MODEL_LIGHT_X + ", " + Shaders.MODEL_LIGHT_Y
            + ", " + Shaders.MODEL_LIGHT_Z + "));";
        assertEquals("vec3 lightDir = normalize(vec3(-0.3, 0.9, 0.4));", expected);
        assertTrue(ShaderFilesTest.withoutComments(Shaders.load(Shaders.MODEL_VERTEX)).contains(expected),
            Shaders.MODEL_VERTEX + " must state the light direction of Shaders.MODEL_LIGHT_*: " + expected);
    }
}

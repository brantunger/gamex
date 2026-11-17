package io.github.brantunger.gamex.model;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import javax.tools.JavaCompiler;
import javax.tools.ToolProvider;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.PrintStream;
import java.lang.reflect.InvocationTargetException;
import java.net.URL;
import java.net.URLClassLoader;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.List;
import java.util.Set;
import java.util.TreeSet;
import java.util.stream.Stream;

/** Verifies every committed {@code .obj} model matches its standalone Java generator byte-for-byte. */
public class GeneratedModelsTest {

    public static Path resolveDir(String propertyName, String folderName) {
        List<Path> candidates = new ArrayList<>();
        String configured = System.getProperty(propertyName);
        if (configured != null) candidates.add(Paths.get(configured));
        candidates.add(Paths.get("..", folderName));
        candidates.add(Paths.get(folderName));
        for (Path candidate : candidates) {
            if (Files.isDirectory(candidate)) return candidate.toAbsolutePath().normalize();
        }
        List<Path> tried = new ArrayList<>();
        for (Path candidate : candidates) tried.add(candidate.toAbsolutePath().normalize());
        throw new IllegalStateException("The " + folderName + " folder was not found. Set " + propertyName
            + " or run from the root project or the core project. Tried: " + tried);
    }

    public static Class<?> compileTool(Path sourceFile, String className, Path classesDir) throws IOException {
        JavaCompiler compiler = ToolProvider.getSystemJavaCompiler();
        assertNotNull(compiler, "JDK compiler is required to run model generators");
        Files.createDirectories(classesDir);
        ByteArrayOutputStream output = new ByteArrayOutputStream();
        int exitCode = compiler.run(null, output, output,
            "--release", "21",
            "-encoding", "UTF-8",
            "-d", classesDir.toString(),
            sourceFile.toString());
        assertEquals(0, exitCode, "Compiling " + sourceFile + " failed:\n" + output.toString(StandardCharsets.UTF_8));
        try {
            return URLClassLoader.newInstance(new URL[] {classesDir.toUri().toURL()}, null).loadClass(className);
        } catch (ClassNotFoundException e) {
            throw new IllegalStateException("Compiled class not found: " + className, e);
        }
    }

    private static void invokeMain(Class<?> toolClass, String... args) {
        PrintStream previousOut = System.out;
        try {
            System.setOut(new PrintStream(new ByteArrayOutputStream(), true, StandardCharsets.UTF_8));
            toolClass.getMethod("main", String[].class).invoke(null, (Object) args);
        } catch (IllegalAccessException | NoSuchMethodException e) {
            throw new IllegalStateException("Failed to invoke " + toolClass.getName() + ".main", e);
        } catch (InvocationTargetException e) {
            throw new RuntimeException(e.getCause());
        } finally {
            System.setOut(previousOut);
        }
    }

    private static Set<String> listObjFiles(Path directory) throws IOException {
        Set<String> names = new TreeSet<>();
        try (Stream<Path> stream = Files.list(directory)) {
            stream.filter(Files::isRegularFile)
                .map(path -> path.getFileName().toString())
                .filter(name -> name.endsWith(".obj"))
                .forEach(names::add);
        }
        return names;
    }

    @Test
    void committedObjModelsMatchTheirGeneratorsByteForByte(@TempDir Path tempDir) throws IOException {
        Path toolsDir = resolveDir("gamex.tools", "tools");
        Path committedDir = resolveDir("gamex.assets", "assets").resolve("models");
        Path classesDir = tempDir.resolve("classes");
        Path generatedDir = tempDir.resolve("models");
        Files.createDirectories(generatedDir);

        Class<?> handSculpt = compileTool(toolsDir.resolve("viewmodel").resolve("HandSculpt.java"),
            "HandSculpt", classesDir);
        Class<?> pistolSculpt = compileTool(toolsDir.resolve("viewmodel").resolve("PistolSculpt.java"),
            "PistolSculpt", classesDir);
        Class<?> worldSculpt = compileTool(toolsDir.resolve("world").resolve("WorldSculpt.java"),
            "WorldSculpt", classesDir);

        invokeMain(handSculpt, generatedDir.resolve("viewmodel-hand.obj").toString());
        invokeMain(pistolSculpt, generatedDir.resolve("viewmodel-pistol.obj").toString());
        invokeMain(worldSculpt, generatedDir.toString());

        Set<String> committedNames = listObjFiles(committedDir);
        Set<String> generatedNames = listObjFiles(generatedDir);
        assertEquals(committedNames, generatedNames, "committed and generated .obj sets differ");

        for (String name : committedNames) {
            byte[] expected = Files.readAllBytes(committedDir.resolve(name));
            byte[] actual = Files.readAllBytes(generatedDir.resolve(name));
            assertArrayEquals(expected, actual,
                name + " does not match generator output; regenerate and commit both together");
        }
    }
}

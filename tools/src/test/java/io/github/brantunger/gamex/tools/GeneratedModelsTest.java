package io.github.brantunger.gamex.tools;

import io.github.brantunger.gamex.model.ModelVertices;
import io.github.brantunger.gamex.model.ObjModel;
import io.github.brantunger.gamex.weapon.HandModel;
import io.github.brantunger.gamex.weapon.PistolModel;
import io.github.brantunger.gamex.weapon.ScattergunModel;
import io.github.brantunger.gamex.world.WorldModels;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.Set;
import java.util.TreeSet;
import java.util.stream.Stream;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class GeneratedModelsTest {

    @Test
    void generatedModelsMatchShippedAssetsAndCompressCleanly(@TempDir Path generatedDir) throws IOException {
        Path modelsDir = resolveModelsDir();
        GenerateAllModels.generateAll(generatedDir);

        Set<String> expectedNames = new TreeSet<>(GenerateAllModels.generators().keySet());
        assertEquals(expectedNames, listObjNames(modelsDir), "Shipped models must match generator registry");
        assertEquals(expectedNames, listObjNames(generatedDir), "Generated output must match generator registry");

        for (String name : expectedNames) {
            Path shipped = modelsDir.resolve(name);
            Path fresh = generatedDir.resolve(name);
            assertArrayEquals(Files.readAllBytes(fresh), Files.readAllBytes(shipped),
                name + " is out of date; re-run ./gradlew tools:generateModels");

            String text = new String(Files.readAllBytes(shipped), StandardCharsets.UTF_8);
            ObjModel model = ObjModel.parse(text);
            ModelVertices.IndexedMesh indexed = packRealModel(name, model);
            assertTrue(indexed.vertexCount() > 0 && indexed.vertexCount() <= ModelVertices.MAX_INDEXED_VERTICES,
                name + " indexed vertex count out of 16-bit range: " + indexed.vertexCount());
            assertEquals(0, indexed.indexCount() % 3, name + " indices must form whole triangles");

            if ("viewmodel-hand.obj".equals(name)) {
                int unindexedCorners = HandModel.vertices(model).length / ModelVertices.VERTEX_SIZE;
                assertTrue(indexed.vertexCount() * 2 < unindexedCorners,
                    "Expected >50% vertex deduplication on viewmodel-hand.obj, got "
                        + indexed.vertexCount() + " unique vs " + unindexedCorners + " corners");
            }
        }
    }

    private static ModelVertices.IndexedMesh packRealModel(String name, ObjModel model) {
        if ("viewmodel-hand.obj".equals(name)) {
            return HandModel.packIndexed(model);
        }
        if ("viewmodel-pistol.obj".equals(name)) {
            PistolModel pistol = PistolModel.from(model);
            assertTrue(pistol.bodyMesh.vertexCount() > 0 && pistol.slideMesh.vertexCount() > 0);
            float[] fallbackColour = {0.5f, 0.5f, 0.5f, 0.5f};
            return ModelVertices.packIndexed(model, mat -> fallbackColour);
        }
        if ("viewmodel-scattergun.obj".equals(name)) {
            ScattergunModel scatter = ScattergunModel.from(model);
            assertTrue(scatter.bodyMesh.vertexCount() > 0 && scatter.pumpMesh.vertexCount() > 0);
            float[] fallbackColour = {0.5f, 0.5f, 0.5f, 0.5f};
            return ModelVertices.packIndexed(model, mat -> fallbackColour);
        }
        return WorldModels.packIndexed(model);
    }

    private static Set<String> listObjNames(Path dir) throws IOException {
        Set<String> names = new TreeSet<>();
        try (Stream<Path> entries = Files.list(dir)) {
            entries.filter(Files::isRegularFile)
                .map(path -> path.getFileName().toString())
                .filter(name -> name.endsWith(".obj"))
                .forEach(names::add);
        }
        return names;
    }

    private static Path resolveModelsDir() {
        String configured = System.getProperty("gamex.assets");
        if (configured != null && !configured.trim().isEmpty()) {
            Path candidate = Paths.get(configured).toAbsolutePath().normalize().resolve("models");
            if (Files.isDirectory(candidate)) {
                return candidate;
            }
        }
        Path current = Paths.get("").toAbsolutePath().normalize();
        while (current != null) {
            Path candidate = current.resolve("assets").resolve("models");
            if (Files.isDirectory(candidate)) {
                return candidate;
            }
            current = current.getParent();
        }
        try {
            throw new IOException("Could not find assets/models directory");
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
    }
}

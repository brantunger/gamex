package io.github.brantunger.gamex.render;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.badlogic.gdx.graphics.Color;
import com.badlogic.gdx.utils.JsonReader;
import com.badlogic.gdx.utils.JsonValue;
import io.github.brantunger.gamex.GameSimulation;
import io.github.brantunger.gamex.model.GlbFile;
import io.github.brantunger.gamex.player.PlayerLoadout;
import io.github.brantunger.gamex.world.Enemy;
import io.github.brantunger.gamex.world.Levels;
import io.github.brantunger.gamex.world.WorldModels;
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.io.InputStream;
import java.io.UncheckedIOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;

/**
 * Checks the shipped Warden model against what the renderer asks of it, by reading the JSON part of the file, and
 * checks the parts of the renderer that need no graphics context.
 */
class WardenModelTest {

    private static JsonValue gltf;

    /**
     * @return the JSON of the shipped model. It is read by each test that asks until one read succeeds, so that
     *     without the model every test that reads it fails with the reason, such as a Git LFS pointer in the model's place.
     */
    private static JsonValue gltf() {
        if (gltf == null) gltf = readJson(WorldModels.WARDEN_PATH);
        return gltf;
    }

    private static Path find(String assetPath) {
        List<Path> candidates = new ArrayList<>();
        String assets = System.getProperty("gamex.assets");
        if (assets != null) candidates.add(Paths.get(assets, assetPath));
        candidates.add(Paths.get("..", "assets", assetPath));
        candidates.add(Paths.get("assets", assetPath));
        for (Path candidate : candidates) {
            if (Files.isRegularFile(candidate)) return candidate;
        }
        List<Path> tried = new ArrayList<>();
        for (Path candidate : candidates) tried.add(candidate.toAbsolutePath().normalize());
        throw new IllegalStateException("The model " + assetPath + " was not found. Set the system property "
            + "gamex.assets to the assets folder, or run from the root project or the core project. Tried: " + tried);
    }

    /** Reads the header and the JSON chunk of a binary glTF file, and none of the about 50 MB that follow them. */
    private static JsonValue readJson(String assetPath) {
        Path file = find(assetPath);
        try (InputStream in = Files.newInputStream(file)) {
            return new JsonReader().parse(GlbFile.readJson(in, file.toString()));
        } catch (IllegalArgumentException e) {
            throw new IllegalStateException(e.getMessage(), e);
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
    }

    private static Set<String> names(String array) {
        Set<String> names = new LinkedHashSet<>();
        for (JsonValue entry = gltf().get(array).child; entry != null; entry = entry.next) {
            names.add(entry.getString("name"));
        }
        return names;
    }

    /** @return the accessor of the mesh's positions, which carries the model's bounds */
    private static JsonValue positions() {
        JsonValue primitives = gltf().get("meshes").get(0).get("primitives");
        assertEquals(1, primitives.size, "the model is one mesh part, drawn with one shader");
        return gltf().get("accessors").get(primitives.get(0).get("attributes").getInt("POSITION"));
    }

    @Test
    void theModelHasTheClipOfEveryPose() {
        Set<String> clips = names("animations");
        assertEquals(Set.of("Running", "Walking", "Punch_Combo", "Stand_Talking_Angry"), clips);
        for (WardenPose pose : WardenPose.values()) {
            assertTrue(clips.contains(pose.clip), pose + " plays '" + pose.clip + "', which the model lacks: " + clips);
        }
    }

    /** @return the length of a clip of the model in seconds: the last key time of its channels */
    private static float clipLength(String name) {
        for (JsonValue animation = gltf().get("animations").child; animation != null; animation = animation.next) {
            if (!name.equals(animation.getString("name"))) continue;
            float length = 0f;
            for (JsonValue sampler = animation.get("samplers").child; sampler != null; sampler = sampler.next) {
                JsonValue times = gltf().get("accessors").get(sampler.getInt("input"));
                length = Math.max(length, times.get("max").asFloatArray()[0]);
            }
            return length;
        }
        throw new AssertionError("the model has no animation named " + name);
    }

    @Test
    void theComboOfTheSimulationLastsAsLongAsItsClip() {
        assertEquals(clipLength(WardenPose.ATTACK.clip), Enemy.WARDEN_COMBO_TIME, 1e-4f,
            "the simulation times the punches against this clip; a new clip needs new punch times");
    }

    @Test
    void eachWardenStandsFromItsOwnMomentOfTheStandingClip() {
        float length = clipLength(WardenPose.IDLE.clip);
        assertTrue(length > 10f, "a long clip, so that standing does not look like a loop: " + length);
        float here = WardenModel.idleStart(23.5f, 8.5f, length);
        assertEquals(here, WardenModel.idleStart(23.5f, 8.5f, length), "the same in every run");
        assertTrue(here >= 0f && here < length);
        float there = WardenModel.idleStart(6.5f, 3.5f, length);
        assertTrue(there >= 0f && there < length);
        assertTrue(Math.abs(here - there) > 1f, "two Wardens do not move as one: " + here + " and " + there);
    }

    @Test
    void theModelIsOneSkinnedMeshOnTwentyEightJoints() {
        assertEquals(1, gltf().get("meshes").size);
        assertEquals(1, gltf().get("skins").size);
        assertEquals(28, gltf().get("skins").get(0).get("joints").size,
            "the shader is compiled for as many bones as the model has; a change here changes that shader");
        Set<String> attributes = new LinkedHashSet<>();
        for (JsonValue attribute = gltf().get("meshes").get(0).get("primitives").get(0).get("attributes").child;
             attribute != null; attribute = attribute.next) {
            attributes.add(attribute.name);
        }
        assertTrue(attributes.containsAll(Set.of("POSITION", "NORMAL", "TEXCOORD_0", "JOINTS_0", "WEIGHTS_0")),
            "the skinned, textured mesh the PBR shader draws: " + attributes);
    }

    @Test
    void theModelStandsOnTheFloorAndScalesToTheCollisionHeight() {
        float[] min = positions().get("min").asFloatArray();
        float[] max = positions().get("max").asFloatArray();
        assertEquals(0f, min[1], 1e-4f, "the feet are at the model's origin, so it needs no lift");
        assertEquals(WorldModels.WARDEN_MODEL_HEIGHT, max[1], 1e-4f);
        assertEquals(Enemy.Kind.WARDEN.height, max[1] * WardenModel.SCALE, 1e-4f);

        // Front to back the body fits the collision cylinder. Side to side it does not, and is not meant to: these
        // bounds are of the bind pose, whose spread arms reach 0.40 at this scale, past the radius of 0.32. The
        // cylinder was fitted to the height, as the levels were built for it.
        float radius = Enemy.Kind.WARDEN.radius;
        assertTrue(min[2] * WardenModel.SCALE >= -radius && max[2] * WardenModel.SCALE <= radius,
            "depth " + min[2] * WardenModel.SCALE + " to " + max[2] * WardenModel.SCALE);
        assertEquals(-min[0], max[0], 1e-4f, "the model is centred on its origin");
    }

    @Test
    void theModelBringsItsThreeTexturesInsideTheFile() {
        assertEquals(3, gltf().get("images").size);
        for (JsonValue image = gltf().get("images").child; image != null; image = image.next) {
            assertTrue(image.has("bufferView"), "image '" + image.getString("name") + "' is not in the file");
            assertEquals("image/png", image.getString("mimeType"));
        }
        assertEquals(1, gltf().get("materials").size);
    }

    @Test
    void onlyAStageWithALiveWardenNeedsTheModel() {
        List<Levels.Stage> stages = new ArrayList<>(Levels.campaign());
        stages.add(Levels.trainingRange());
        List<String> needing = new ArrayList<>();
        for (Levels.Stage stage : stages) {
            GameSimulation simulation = new GameSimulation(stage.map, PlayerLoadout.fresh(),
                stage.completeOnAllTargets);
            boolean hasWarden = false;
            for (Enemy enemy : simulation.enemies) hasWarden |= enemy.getKind() == Enemy.Kind.WARDEN;
            assertEquals(hasWarden, WardenModel.isNeeded(simulation.enemies), stage.id);
            if (hasWarden) needing.add(stage.id);
        }
        assertEquals(Collections.singletonList("06_warden_core"), needing,
            "the model, a second to load, is loaded for these stages only");
    }

    @Test
    void aStageWithoutEnemiesOrWithADestroyedWardenDoesNotNeedTheModel() {
        assertFalse(WardenModel.isNeeded(Collections.emptyList()));

        List<Enemy> enemies = new ArrayList<>();
        enemies.add(new Enemy(Enemy.Kind.GUARD, 2.5f, 2.5f, 0f));
        enemies.add(new Enemy(Enemy.Kind.TURRET, 4.5f, 2.5f, 0f));
        assertFalse(WardenModel.isNeeded(enemies));

        Enemy warden = new Enemy(Enemy.Kind.WARDEN, 6.5f, 2.5f, 0f);
        enemies.add(warden);
        assertTrue(WardenModel.isNeeded(enemies));

        warden.damage(Enemy.Kind.WARDEN.maxHealth);
        assertTrue(warden.isDestroyed());
        assertFalse(WardenModel.isNeeded(enemies), "a destroyed Warden is not drawn");
    }

    @Test
    void theGlowIsTheAccentOfThePhaseAndBrightensWithTheWindup() {
        Color rest = WardenModel.glow(false, 0f, 0f, 0f, new Color());
        float[] phase1 = WorldModels.ACCENT_WARDEN_PHASE1;
        assertTrue(rest.r > 0f && rest.r < 0.05f, "a faint tint, not a repaint: " + rest);
        assertEquals(phase1[1] / phase1[0], rest.g / rest.r, 1e-4f, "the hue of the phase 1 accent");
        assertEquals(phase1[2] / phase1[0], rest.b / rest.r, 1e-4f);

        Color wound = WardenModel.glow(false, 1f, 0f, 0f, new Color());
        assertEquals(3f * rest.r, wound.r, 1e-5f, "a full wind-up triples the glow");

        float[] overdrive = WorldModels.ACCENT_WARDEN_OVERDRIVE;
        Color cold = WardenModel.glow(true, 0f, 0f, 0f, new Color());
        assertEquals(overdrive[0] / overdrive[2], cold.r / cold.b, 1e-4f, "the hue of the overdrive accent");
        assertTrue(cold.b > cold.r, "overdrive glows blue: " + cold);
    }

    @Test
    void theOverdriveGlowPulsesWithTime() {
        // sin(9t) is 1 at t = pi / 18 and -1 at t = pi / 6.
        Color peak = WardenModel.glow(true, 0f, 0f, (float) (Math.PI / 18.0), new Color());
        Color trough = WardenModel.glow(true, 0f, 0f, (float) (Math.PI / 6.0), new Color());
        assertEquals(2.5f, peak.b / trough.b, 1e-3f);

        Color early = WardenModel.glow(false, 0f, 0f, 0f, new Color());
        Color late = WardenModel.glow(false, 0f, 0f, 1f, new Color());
        assertEquals(early, late, "phase 1 does not pulse");
    }

    @Test
    void aHitFlashesTheGlowRed() {
        Color rest = WardenModel.glow(false, 0f, 0f, 0f, new Color());
        Color hit = WardenModel.glow(false, 0f, 1f, 0f, new Color());
        assertTrue(hit.r - rest.r > 0.5f, "a fresh hit is far brighter than the accent: " + hit);
        assertEquals(0.25f * (hit.r - rest.r), hit.g - rest.g, 1e-5f, "and red");
        assertEquals(0.25f * (hit.r - rest.r), hit.b - rest.b, 1e-5f);
    }
}

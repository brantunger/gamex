package io.github.brantunger.gamex.tools;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.function.Supplier;

/**
 * Generates every shipped {@code .obj} model in {@code assets/models/} deterministically via the {@link MeshBuilder}
 * sculptors. The Warden is not one of them: its model, {@code enemy-warden.glb}, is made outside this project.
 */
public final class GenerateAllModels {

    private static final Map<String, Supplier<MeshBuilder>> GENERATORS;

    static {
        Map<String, Supplier<MeshBuilder>> map = new LinkedHashMap<>();
        map.put("viewmodel-pistol.obj", PistolSculpt::build);
        map.put("viewmodel-scattergun.obj", ScattergunSculpt::build);
        map.put("viewmodel-raillance.obj", RailLanceSculpt::build);
        map.put("viewmodel-hand.obj", HandSculpt::build);
        map.put("canister.obj", PropSculpt::buildCanister);
        map.put("ring-board.obj", PropSculpt::buildRingBoard);
        map.put("blast-orb.obj", PropSculpt::buildBlastOrb);
        map.put("rail-beam.obj", PropSculpt::buildRailBeam);
        map.put("pickup-medkit.obj", PropSculpt::buildMedkit);
        map.put("pickup-armor.obj", PropSculpt::buildArmor);
        map.put("pickup-energy.obj", PropSculpt::buildEnergyCell);
        map.put("pickup-keycard.obj", PropSculpt::buildKeycard);
        map.put("pickup-scattergun.obj", PropSculpt::buildScattergunPickup);
        map.put("pickup-shells.obj", PropSculpt::buildShellsPickup);
        map.put("pickup-raillance.obj", PropSculpt::buildRailLancePickup);
        map.put("pickup-slugs.obj", PropSculpt::buildRailSlugsPickup);
        map.put("door-bulkhead.obj", PropSculpt::buildDoorBulkhead);
        map.put("hazard-grate.obj", PropSculpt::buildHazardGrate);
        map.put("security-console.obj", PropSculpt::buildSecurityConsole);
        map.put("enemy-turret.obj", EnemySculpt::buildEnemyTurret);
        map.put("enemy-drone.obj", EnemySculpt::buildEnemyDrone);
        map.put("enemy-guard.obj", EnemySculpt::buildEnemyGuard);
        map.put("enemy-bolt.obj", PropSculpt::buildEnemyBolt);
        GENERATORS = Collections.unmodifiableMap(map);
    }

    private GenerateAllModels() {}

    /** @return ordered map from {@code .obj} file name to its generator. */
    public static Map<String, Supplier<MeshBuilder>> generators() {
        return GENERATORS;
    }

    /** Regenerates every {@code .obj} file into {@code outputDir}. */
    public static void generateAll(Path outputDir) throws IOException {
        Files.createDirectories(outputDir);
        for (Map.Entry<String, Supplier<MeshBuilder>> entry : GENERATORS.entrySet()) {
            MeshBuilder builder = entry.getValue().get();
            Path target = outputDir.resolve(entry.getKey());
            Files.writeString(target, builder.toObjString());
            System.out.println("Wrote " + target + ": " + builder.getGroupCount() + " groups, "
                + builder.getVertexCount() + " vertices, " + builder.getFaceCount() + " faces");
        }
    }

    public static void main(String[] args) throws IOException {
        if (args.length != 1) {
            System.err.println("usage: ./gradlew tools:generateModels  (or pass <output-dir>)");
            System.exit(2);
        }
        generateAll(Paths.get(args[0]));
    }
}

package io.github.brantunger.gamex.render;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.badlogic.gdx.math.Matrix4;
import com.badlogic.gdx.math.Vector3;
import com.badlogic.gdx.utils.Disposable;
import com.badlogic.gdx.utils.GdxRuntimeException;
import io.github.brantunger.gamex.data.AssetText;
import io.github.brantunger.gamex.model.ModelPalette;
import io.github.brantunger.gamex.model.ModelRig;
import io.github.brantunger.gamex.model.ModelVertices;
import io.github.brantunger.gamex.model.ObjModel;
import io.github.brantunger.gamex.weapon.HandModel;
import io.github.brantunger.gamex.weapon.PistolModel;
import io.github.brantunger.gamex.weapon.RailLanceModel;
import io.github.brantunger.gamex.weapon.ScattergunModel;
import io.github.brantunger.gamex.world.WorldModels;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;

class AssetPreparerTest {

    private static final class RecordingDisposable implements Disposable {
        final String label;
        final AtomicInteger disposeCount = new AtomicInteger();

        RecordingDisposable(String label) {
            this.label = label;
        }

        @Override
        public void dispose() {
            disposeCount.incrementAndGet();
        }
    }

    private static ObjModel loadObj(String assetPath, String label) {
        return ModelMesh.parseModel(assetPath, label, AssetText.read("model", assetPath));
    }

    @Test
    void parallelModelPreparationMatchesSingleThreadedPackingBitForBit() {
        WallTextures walls = WallTextures.load();
        Palettes palettes = Palettes.load();
        Set<String> expectedTextureFiles = ProceduralTextures.allFiles(walls);
        Set<String> workerThreads = ConcurrentHashMap.newKeySet();
        Set<String> decodedFiles = ConcurrentHashMap.newKeySet();
        List<RecordingDisposable> createdTextures = Collections.synchronizedList(new ArrayList<>());
        CountDownLatch concurrentStart = new CountDownLatch(2);
        AtomicInteger modelTaskCounter = new AtomicInteger();

        ExecutorService pool = AssetPreparer.newWorkerPool();
        try {
            AssetPreparer.StartupBatch batch = AssetPreparer.prepareStartup(
                walls,
                palettes,
                pool,
                file -> {
                    workerThreads.add(Thread.currentThread().getName());
                    decodedFiles.add(file);
                    RecordingDisposable tex = new RecordingDisposable(file);
                    createdTextures.add(tex);
                    return tex;
                },
                (assetPath, label) -> {
                    workerThreads.add(Thread.currentThread().getName());
                    if (modelTaskCounter.incrementAndGet() <= 2) {
                        concurrentStart.countDown();
                        try {
                            assertTrue(concurrentStart.await(5, TimeUnit.SECONDS),
                                "expected at least 2 model prep tasks to execute concurrently");
                        } catch (InterruptedException e) {
                            Thread.currentThread().interrupt();
                            throw new AssertionError("Interrupted waiting for concurrent worker", e);
                        }
                    }
                    return loadObj(assetPath, label);
                });

            AssetPreparer.PreparedTextures preparedTextures = null;
            AssetPreparer.PreparedWorldModels preparedWorld;
            AssetPreparer.PreparedViewmodels preparedViewmodels;
            try {
                preparedTextures = batch.awaitTextures();
                preparedWorld = batch.awaitWorldModels();
                preparedViewmodels = batch.awaitViewmodels();
            } finally {
                batch.dispose();
            }

            assertTrue(workerThreads.size() >= 2, "expected multiple gamex-worker threads, saw: " + workerThreads);
            for (String threadName : workerThreads) {
                assertTrue(threadName.startsWith("gamex-worker-"), "unexpected worker thread name: " + threadName);
            }
            assertEquals(expectedTextureFiles, decodedFiles);
            for (String file : expectedTextureFiles) {
                Disposable taken = preparedTextures.takeDisposable(file);
                assertNotNull(taken, "missing prepared texture for " + file);
                taken.dispose();
            }
            preparedTextures.dispose();
            for (RecordingDisposable tex : createdTextures) {
                assertEquals(1, tex.disposeCount.get(), "texture " + tex.label + " was not cleanly disposed");
            }

            assertEquals(19, preparedWorld.modelCount);
            assertEquals(4, preparedViewmodels.modelCount);

            ModelPalette worldPalette = palettes.world;
            assertMeshEqual("canister",
                WorldRenderer.unpackWorldModel(loadObj(WorldModels.CANISTER_PATH, "canister"),
                    WorldModels.CANISTER_PATH, "canister", worldPalette),
                preparedWorld.canister);
            assertMeshEqual("ringBoard",
                WorldRenderer.unpackWorldModel(loadObj(WorldModels.RING_BOARD_PATH, "ring board"),
                    WorldModels.RING_BOARD_PATH, "ring board", worldPalette),
                preparedWorld.ringBoard);
            assertMeshEqual("blastOrb",
                WorldRenderer.unpackWorldModel(loadObj(WorldModels.BLAST_ORB_PATH, "blast orb"),
                    WorldModels.BLAST_ORB_PATH, "blast orb", worldPalette),
                preparedWorld.blastOrb);
            assertMeshEqual("medkit",
                WorldRenderer.unpackWorldModel(loadObj(WorldModels.MEDKIT_PATH, "medkit"),
                    WorldModels.MEDKIT_PATH, "medkit", worldPalette),
                preparedWorld.medkit);
            assertMeshEqual("armor",
                WorldRenderer.unpackWorldModel(loadObj(WorldModels.ARMOR_PATH, "armor"),
                    WorldModels.ARMOR_PATH, "armor", worldPalette),
                preparedWorld.armor);
            assertMeshEqual("energyCell",
                WorldRenderer.unpackWorldModel(loadObj(WorldModels.ENERGY_CELL_PATH, "energy cell"),
                    WorldModels.ENERGY_CELL_PATH, "energy cell", worldPalette),
                preparedWorld.energyCell);
            assertMeshEqual("scattergunPickup",
                WorldRenderer.unpackWorldModel(loadObj(WorldModels.SCATTERGUN_PICKUP_PATH, "scattergun pickup"),
                    WorldModels.SCATTERGUN_PICKUP_PATH, "scattergun pickup", worldPalette),
                preparedWorld.scattergunPickup);
            assertMeshEqual("shellsPickup",
                WorldRenderer.unpackWorldModel(loadObj(WorldModels.SHELLS_PICKUP_PATH, "shells pickup"),
                    WorldModels.SHELLS_PICKUP_PATH, "shells pickup", worldPalette),
                preparedWorld.shellsPickup);
            assertMeshEqual("railLancePickup",
                WorldRenderer.unpackWorldModel(loadObj(WorldModels.RAIL_LANCE_PICKUP_PATH, "rail lance pickup"),
                    WorldModels.RAIL_LANCE_PICKUP_PATH, "rail lance pickup", worldPalette),
                preparedWorld.railLancePickup);
            assertMeshEqual("railSlugsPickup",
                WorldRenderer.unpackWorldModel(loadObj(WorldModels.RAIL_SLUGS_PICKUP_PATH, "rail slugs pickup"),
                    WorldModels.RAIL_SLUGS_PICKUP_PATH, "rail slugs pickup", worldPalette),
                preparedWorld.railSlugsPickup);
            assertMeshEqual("railBeam",
                WorldRenderer.unpackWorldModel(loadObj(WorldModels.RAIL_BEAM_PATH, "rail beam"),
                    WorldModels.RAIL_BEAM_PATH, "rail beam", worldPalette),
                preparedWorld.railBeam);

            ObjModel turretObj = loadObj(WorldModels.TURRET_PATH, "turret");
            assertRigEqual(new ModelRig(turretObj), preparedWorld.turretRig, "muzzle_l", "head");
            assertMeshEqual("turretBase",
                WorldRenderer.unpackWorldPart(turretObj, WorldModels.TURRET_PATH, "turret", "base", worldPalette),
                preparedWorld.turretBase);
            assertMeshEqual("turretHead",
                WorldRenderer.unpackWorldPart(turretObj, WorldModels.TURRET_PATH, "turret", "head", worldPalette),
                preparedWorld.turretHead);
            assertMeshEqual("turretBarrels",
                WorldRenderer.unpackWorldPart(turretObj, WorldModels.TURRET_PATH, "turret", "barrels", worldPalette),
                preparedWorld.turretBarrels);

            ObjModel droneObj = loadObj(WorldModels.DRONE_PATH, "drone");
            assertRigEqual(new ModelRig(droneObj), preparedWorld.droneRig, "telegraph", ObjModel.ROOT_PART);
            assertMeshEqual("drone",
                WorldRenderer.unpackWorldModel(droneObj, WorldModels.DRONE_PATH, "drone", worldPalette),
                preparedWorld.drone);

            ObjModel guardObj = loadObj(WorldModels.GUARD_PATH, "guard");
            assertRigEqual(new ModelRig(guardObj), preparedWorld.guardRig, "muzzle", "arm_r");
            assertMeshEqual("guardPelvis",
                WorldRenderer.unpackWorldPart(guardObj, WorldModels.GUARD_PATH, "guard", "pelvis", worldPalette),
                preparedWorld.guardPelvis);
            assertMeshEqual("guardLegLeft",
                WorldRenderer.unpackWorldPart(guardObj, WorldModels.GUARD_PATH, "guard", "leg_l", worldPalette),
                preparedWorld.guardLegLeft);
            assertMeshEqual("guardLegRight",
                WorldRenderer.unpackWorldPart(guardObj, WorldModels.GUARD_PATH, "guard", "leg_r", worldPalette),
                preparedWorld.guardLegRight);
            assertMeshEqual("guardTorso",
                WorldRenderer.unpackWorldPart(guardObj, WorldModels.GUARD_PATH, "guard", "torso", worldPalette),
                preparedWorld.guardTorso);
            assertMeshEqual("guardArmLeft",
                WorldRenderer.unpackWorldPart(guardObj, WorldModels.GUARD_PATH, "guard", "arm_l", worldPalette),
                preparedWorld.guardArmLeft);
            assertMeshEqual("guardArmRight",
                WorldRenderer.unpackWorldPart(guardObj, WorldModels.GUARD_PATH, "guard", "arm_r", worldPalette),
                preparedWorld.guardArmRight);

            assertMeshEqual("enemyBolt",
                WorldRenderer.unpackWorldModel(loadObj(WorldModels.ENEMY_BOLT_PATH, "enemy bolt"),
                    WorldModels.ENEMY_BOLT_PATH, "enemy bolt", worldPalette),
                preparedWorld.enemyBolt);
            assertMeshEqual("door",
                WorldRenderer.unpackWorldModel(loadObj(WorldModels.DOOR_BULKHEAD_PATH, "door bulkhead"),
                    WorldModels.DOOR_BULKHEAD_PATH, "door bulkhead", worldPalette),
                preparedWorld.door);
            assertMeshEqual("hazardGrate",
                WorldRenderer.unpackWorldModel(loadObj(WorldModels.HAZARD_GRATE_PATH, "hazard grate"),
                    WorldModels.HAZARD_GRATE_PATH, "hazard grate", worldPalette),
                preparedWorld.hazardGrate);
            assertMeshEqual("keycard",
                WorldRenderer.unpackWorldModel(loadObj(WorldModels.KEYCARD_PATH, "keycard"),
                    WorldModels.KEYCARD_PATH, "keycard", worldPalette),
                preparedWorld.keycard);
            assertMeshEqual("console",
                WorldRenderer.unpackWorldModel(loadObj(WorldModels.SECURITY_CONSOLE_PATH, "security console"),
                    WorldModels.SECURITY_CONSOLE_PATH, "security console", worldPalette),
                preparedWorld.console);

            ObjModel pistolObj = loadObj(PistolModel.ASSET_PATH, "viewmodel pistol");
            PistolModel expectedPistol = ViewmodelRenderer.unpackPistol(pistolObj, palettes.pistol);
            assertMeshEqual("pistol.bodyMesh", expectedPistol.bodyMesh, preparedViewmodels.pistol.bodyMesh);
            assertMeshEqual("pistol.slideMesh", expectedPistol.slideMesh, preparedViewmodels.pistol.slideMesh);
            assertSocketEqual("pistolMuzzle", pistolObj.getSocket("muzzle"), preparedViewmodels.pistolMuzzle);

            ObjModel scatterObj = loadObj(ScattergunModel.ASSET_PATH, "viewmodel scattergun");
            ScattergunModel expectedScatter = ViewmodelRenderer.unpackScattergun(scatterObj, palettes.scattergun);
            assertMeshEqual("scatter.bodyMesh", expectedScatter.bodyMesh, preparedViewmodels.scattergun.bodyMesh);
            assertMeshEqual("scatter.pumpMesh", expectedScatter.pumpMesh, preparedViewmodels.scattergun.pumpMesh);
            assertSocketEqual("scatterMuzzle", scatterObj.getSocket("muzzle"), preparedViewmodels.scatterMuzzle);

            ObjModel railObj = loadObj(RailLanceModel.ASSET_PATH, "viewmodel rail lance");
            RailLanceModel expectedRail = ViewmodelRenderer.unpackRailLance(railObj, palettes.railLance);
            assertMeshEqual("rail.bodyMesh", expectedRail.bodyMesh, preparedViewmodels.railLance.bodyMesh);
            assertMeshEqual("rail.boltMesh", expectedRail.boltMesh, preparedViewmodels.railLance.boltMesh);
            assertSocketEqual("railMuzzle", railObj.getSocket("muzzle"), preparedViewmodels.railMuzzle);

            ObjModel handObj = loadObj(HandModel.ASSET_PATH, "viewmodel hand");
            assertMeshEqual("hand", ViewmodelRenderer.unpackHand(handObj, palettes.hand), preparedViewmodels.hand);
        } finally {
            pool.shutdownNow();
        }
    }

    @Test
    void failedPreparationDisposesAlreadyDecodedTexturesAndUnwrapsCause() {
        WallTextures walls = WallTextures.load();
        Palettes palettes = Palettes.load();
        ExecutorService pool = AssetPreparer.newWorkerPool();
        AtomicInteger callIndex = new AtomicInteger();
        List<RecordingDisposable> decodedBeforeFailure = Collections.synchronizedList(new ArrayList<>());
        GdxRuntimeException boom = new GdxRuntimeException("Corrupt PNG header in startup batch");

        try {
            AssetPreparer.StartupBatch batch = AssetPreparer.prepareStartup(
                walls,
                palettes,
                pool,
                file -> {
                    if (callIndex.incrementAndGet() == 4) {
                        throw boom;
                    }
                    RecordingDisposable px = new RecordingDisposable(file);
                    decodedBeforeFailure.add(px);
                    return px;
                },
                AssetPreparerTest::loadObj);

            try {
                GdxRuntimeException thrown = assertThrows(GdxRuntimeException.class, batch::awaitTextures);
                assertSame(boom, thrown);
            } finally {
                batch.dispose();
            }

            assertTrue(decodedBeforeFailure.size() >= 1);
            for (RecordingDisposable px : decodedBeforeFailure) {
                assertEquals(1, px.disposeCount.get(), "texture " + px.label + " leaked on batch failure");
            }
        } finally {
            pool.shutdownNow();
        }
    }

    @Test
    void closingBatchWithoutAwaitingTexturesStillDisposesCompletedDecodes() {
        WallTextures walls = WallTextures.load();
        Palettes palettes = Palettes.load();
        ExecutorService pool = AssetPreparer.newWorkerPool();
        List<RecordingDisposable> decoded = Collections.synchronizedList(new ArrayList<>());
        CountDownLatch twoTexturesDecoded = new CountDownLatch(2);

        try {
            AssetPreparer.StartupBatch batch = AssetPreparer.prepareStartup(
                walls,
                palettes,
                pool,
                file -> {
                    RecordingDisposable px = new RecordingDisposable(file);
                    decoded.add(px);
                    twoTexturesDecoded.countDown();
                    return px;
                },
                AssetPreparerTest::loadObj);

            try {
                assertTrue(twoTexturesDecoded.await(5, TimeUnit.SECONDS));
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
            } finally {
                batch.dispose();
            }

            assertTrue(decoded.size() >= 2);
            for (RecordingDisposable px : decoded) {
                assertEquals(1, px.disposeCount.get(), "unclaimed texture " + px.label + " must dispose on close");
            }
        } finally {
            pool.shutdownNow();
        }
    }

    private static void assertMeshEqual(String label, ModelVertices.IndexedMesh expected,
                                        ModelVertices.IndexedMesh actual) {
        assertNotNull(actual, label + " must not be null");
        assertEquals(expected.vertexCount(), actual.vertexCount(), label + " vertexCount");
        assertEquals(expected.indexCount(), actual.indexCount(), label + " indexCount");
        assertEquals(expected.triangleCount(), actual.triangleCount(), label + " triangleCount");
        assertArrayEquals(expected.vertices, actual.vertices, 0f, label + " vertices");
        assertArrayEquals(expected.indices, actual.indices, label + " indices");
    }

    private static void assertSocketEqual(String label, ObjModel.SocketSpec expected, ObjModel.SocketSpec actual) {
        assertNotNull(actual, label + " socket must not be null");
        assertEquals(expected.part, actual.part, label + " part");
        assertEquals(expected.x, actual.x, 0f, label + " x");
        assertEquals(expected.y, actual.y, 0f, label + " y");
        assertEquals(expected.z, actual.z, 0f, label + " z");
    }

    private static void assertRigEqual(ModelRig expected, ModelRig actual, String socketName, String animatedPart) {
        assertNotNull(actual);
        Matrix4 root = new Matrix4().translate(3f, -1f, 7f).rotate(Vector3.Y, 35f);
        Map<String, Matrix4> pose = Map.of(animatedPart, new Matrix4().rotate(Vector3.X, 20f));
        Vector3 expectedWorld = expected.evaluateSocketWorld(socketName, pose, root, new Vector3());
        Vector3 actualWorld = actual.evaluateSocketWorld(socketName, pose, root, new Vector3());
        assertEquals(expectedWorld.x, actualWorld.x, 1e-6f);
        assertEquals(expectedWorld.y, actualWorld.y, 1e-6f);
        assertEquals(expectedWorld.z, actualWorld.z, 1e-6f);
    }
}

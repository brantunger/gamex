package io.github.brantunger.gamex.render;

import com.badlogic.gdx.graphics.Pixmap;
import com.badlogic.gdx.utils.Disposable;
import io.github.brantunger.gamex.model.ModelPalette;
import io.github.brantunger.gamex.model.ModelRig;
import io.github.brantunger.gamex.model.ModelVertices;
import io.github.brantunger.gamex.model.ObjModel;
import io.github.brantunger.gamex.weapon.HandModel;
import io.github.brantunger.gamex.weapon.PistolModel;
import io.github.brantunger.gamex.weapon.RailLanceModel;
import io.github.brantunger.gamex.weapon.ScattergunModel;
import io.github.brantunger.gamex.world.WorldModels;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.function.BiFunction;
import java.util.function.Function;

/**
 * Prepares CPU-side startup assets (PNG texture decoding, OBJ parsing, hierarchical rig setup, and indexed vertex
 * deduplication) concurrently across a bounded worker pool before OpenGL uploads run on the main thread.
 */
public final class AssetPreparer {

    private static final int MAX_WORKERS = 8;

    /** Pre-decoded startup texture pixmaps awaiting GL texture upload. */
    public static final class PreparedTextures implements Disposable {
        private final Map<String, Disposable> pixmaps;

        PreparedTextures(Map<String, Disposable> pixmaps) {
            this.pixmaps = new LinkedHashMap<>(pixmaps);
        }

        Pixmap take(String file) {
            Disposable item = pixmaps.remove(file);
            return item instanceof Pixmap ? (Pixmap) item : null;
        }

        Disposable takeDisposable(String file) {
            return pixmaps.remove(file);
        }

        @Override
        public void dispose() {
            for (Disposable item : pixmaps.values()) {
                if (item != null) {
                    try {
                        item.dispose();
                    } catch (RuntimeException ignored) {
                        // Isolate cleanup so all remaining pre-decoded pixmaps are released.
                    }
                }
            }
            pixmaps.clear();
        }
    }

    /** Pre-packed indexed vertex data and skeletal rigs for all 19 startup world models. */
    public static final class PreparedWorldModels {
        public final int modelCount = 19;
        public final ModelVertices.IndexedMesh canister;
        public final ModelVertices.IndexedMesh ringBoard;
        public final ModelVertices.IndexedMesh blastOrb;
        public final ModelVertices.IndexedMesh medkit;
        public final ModelVertices.IndexedMesh armor;
        public final ModelVertices.IndexedMesh energyCell;
        public final ModelVertices.IndexedMesh scattergunPickup;
        public final ModelVertices.IndexedMesh shellsPickup;
        public final ModelVertices.IndexedMesh railLancePickup;
        public final ModelVertices.IndexedMesh railSlugsPickup;
        public final ModelVertices.IndexedMesh railBeam;
        public final ModelRig turretRig;
        public final ModelVertices.IndexedMesh turretBase;
        public final ModelVertices.IndexedMesh turretHead;
        public final ModelVertices.IndexedMesh turretBarrels;
        public final ModelRig droneRig;
        public final ModelVertices.IndexedMesh drone;
        public final ModelRig guardRig;
        public final ModelVertices.IndexedMesh guardPelvis;
        public final ModelVertices.IndexedMesh guardLegLeft;
        public final ModelVertices.IndexedMesh guardLegRight;
        public final ModelVertices.IndexedMesh guardTorso;
        public final ModelVertices.IndexedMesh guardArmLeft;
        public final ModelVertices.IndexedMesh guardArmRight;
        public final ModelVertices.IndexedMesh enemyBolt;
        public final ModelVertices.IndexedMesh door;
        public final ModelVertices.IndexedMesh hazardGrate;
        public final ModelVertices.IndexedMesh keycard;
        public final ModelVertices.IndexedMesh console;

        PreparedWorldModels(ModelVertices.IndexedMesh canister, ModelVertices.IndexedMesh ringBoard,
                            ModelVertices.IndexedMesh blastOrb, ModelVertices.IndexedMesh medkit,
                            ModelVertices.IndexedMesh armor, ModelVertices.IndexedMesh energyCell,
                            ModelVertices.IndexedMesh scattergunPickup, ModelVertices.IndexedMesh shellsPickup,
                            ModelVertices.IndexedMesh railLancePickup, ModelVertices.IndexedMesh railSlugsPickup,
                            ModelVertices.IndexedMesh railBeam, ModelRig turretRig,
                            ModelVertices.IndexedMesh turretBase, ModelVertices.IndexedMesh turretHead,
                            ModelVertices.IndexedMesh turretBarrels, ModelRig droneRig, ModelVertices.IndexedMesh drone,
                            ModelRig guardRig, ModelVertices.IndexedMesh guardPelvis,
                            ModelVertices.IndexedMesh guardLegLeft, ModelVertices.IndexedMesh guardLegRight,
                            ModelVertices.IndexedMesh guardTorso, ModelVertices.IndexedMesh guardArmLeft,
                            ModelVertices.IndexedMesh guardArmRight, ModelVertices.IndexedMesh enemyBolt,
                            ModelVertices.IndexedMesh door, ModelVertices.IndexedMesh hazardGrate,
                            ModelVertices.IndexedMesh keycard, ModelVertices.IndexedMesh console) {
            this.canister = canister;
            this.ringBoard = ringBoard;
            this.blastOrb = blastOrb;
            this.medkit = medkit;
            this.armor = armor;
            this.energyCell = energyCell;
            this.scattergunPickup = scattergunPickup;
            this.shellsPickup = shellsPickup;
            this.railLancePickup = railLancePickup;
            this.railSlugsPickup = railSlugsPickup;
            this.railBeam = railBeam;
            this.turretRig = turretRig;
            this.turretBase = turretBase;
            this.turretHead = turretHead;
            this.turretBarrels = turretBarrels;
            this.droneRig = droneRig;
            this.drone = drone;
            this.guardRig = guardRig;
            this.guardPelvis = guardPelvis;
            this.guardLegLeft = guardLegLeft;
            this.guardLegRight = guardLegRight;
            this.guardTorso = guardTorso;
            this.guardArmLeft = guardArmLeft;
            this.guardArmRight = guardArmRight;
            this.enemyBolt = enemyBolt;
            this.door = door;
            this.hazardGrate = hazardGrate;
            this.keycard = keycard;
            this.console = console;
        }
    }

    /** Pre-packed indexed vertex data and muzzle sockets for all 4 viewmodel models. */
    public static final class PreparedViewmodels {
        public final int modelCount = 4;
        public final PistolModel pistol;
        public final ObjModel.SocketSpec pistolMuzzle;
        public final ScattergunModel scattergun;
        public final ObjModel.SocketSpec scatterMuzzle;
        public final RailLanceModel railLance;
        public final ObjModel.SocketSpec railMuzzle;
        public final ModelVertices.IndexedMesh hand;

        PreparedViewmodels(PistolModel pistol, ObjModel.SocketSpec pistolMuzzle, ScattergunModel scattergun,
                           ObjModel.SocketSpec scatterMuzzle, RailLanceModel railLance,
                           ObjModel.SocketSpec railMuzzle, ModelVertices.IndexedMesh hand) {
            this.pistol = pistol;
            this.pistolMuzzle = pistolMuzzle;
            this.scattergun = scattergun;
            this.scatterMuzzle = scatterMuzzle;
            this.railLance = railLance;
            this.railMuzzle = railMuzzle;
            this.hand = hand;
        }
    }

    private static final class TurretPrepared {
        final ModelRig rig;
        final ModelVertices.IndexedMesh base;
        final ModelVertices.IndexedMesh head;
        final ModelVertices.IndexedMesh barrels;

        TurretPrepared(ModelRig rig, ModelVertices.IndexedMesh base, ModelVertices.IndexedMesh head,
                       ModelVertices.IndexedMesh barrels) {
            this.rig = rig;
            this.base = base;
            this.head = head;
            this.barrels = barrels;
        }
    }

    private static final class DronePrepared {
        final ModelRig rig;
        final ModelVertices.IndexedMesh mesh;

        DronePrepared(ModelRig rig, ModelVertices.IndexedMesh mesh) {
            this.rig = rig;
            this.mesh = mesh;
        }
    }

    private static final class GuardPrepared {
        final ModelRig rig;
        final ModelVertices.IndexedMesh pelvis;
        final ModelVertices.IndexedMesh legLeft;
        final ModelVertices.IndexedMesh legRight;
        final ModelVertices.IndexedMesh torso;
        final ModelVertices.IndexedMesh armLeft;
        final ModelVertices.IndexedMesh armRight;

        GuardPrepared(ModelRig rig, ModelVertices.IndexedMesh pelvis, ModelVertices.IndexedMesh legLeft,
                      ModelVertices.IndexedMesh legRight, ModelVertices.IndexedMesh torso,
                      ModelVertices.IndexedMesh armLeft, ModelVertices.IndexedMesh armRight) {
            this.rig = rig;
            this.pelvis = pelvis;
            this.legLeft = legLeft;
            this.legRight = legRight;
            this.torso = torso;
            this.armLeft = armLeft;
            this.armRight = armRight;
        }
    }

    private static final class PistolPrepared {
        final PistolModel model;
        final ObjModel.SocketSpec muzzle;

        PistolPrepared(PistolModel model, ObjModel.SocketSpec muzzle) {
            this.model = model;
            this.muzzle = muzzle;
        }
    }

    private static final class ScatterPrepared {
        final ScattergunModel model;
        final ObjModel.SocketSpec muzzle;

        ScatterPrepared(ScattergunModel model, ObjModel.SocketSpec muzzle) {
            this.model = model;
            this.muzzle = muzzle;
        }
    }

    private static final class RailPrepared {
        final RailLanceModel model;
        final ObjModel.SocketSpec muzzle;

        RailPrepared(RailLanceModel model, ObjModel.SocketSpec muzzle) {
            this.model = model;
            this.muzzle = muzzle;
        }
    }

    /** Handle to in-flight startup asset preparation tasks, guaranteeing cleanup on failure or early close. */
    public static final class StartupBatch implements Disposable {
        private final Map<String, Future<? extends Disposable>> textureFutures;
        private final Future<ModelVertices.IndexedMesh> canister;
        private final Future<ModelVertices.IndexedMesh> ringBoard;
        private final Future<ModelVertices.IndexedMesh> blastOrb;
        private final Future<ModelVertices.IndexedMesh> medkit;
        private final Future<ModelVertices.IndexedMesh> armor;
        private final Future<ModelVertices.IndexedMesh> energyCell;
        private final Future<ModelVertices.IndexedMesh> scattergunPickup;
        private final Future<ModelVertices.IndexedMesh> shellsPickup;
        private final Future<ModelVertices.IndexedMesh> railLancePickup;
        private final Future<ModelVertices.IndexedMesh> railSlugsPickup;
        private final Future<ModelVertices.IndexedMesh> railBeam;
        private final Future<TurretPrepared> turret;
        private final Future<DronePrepared> drone;
        private final Future<GuardPrepared> guard;
        private final Future<ModelVertices.IndexedMesh> enemyBolt;
        private final Future<ModelVertices.IndexedMesh> door;
        private final Future<ModelVertices.IndexedMesh> hazardGrate;
        private final Future<ModelVertices.IndexedMesh> keycard;
        private final Future<ModelVertices.IndexedMesh> console;
        private final Future<PistolPrepared> pistol;
        private final Future<ScatterPrepared> scattergun;
        private final Future<RailPrepared> railLance;
        private final Future<ModelVertices.IndexedMesh> hand;
        private boolean texturesCollected;

        private StartupBatch(
            Map<String, Future<? extends Disposable>> textureFutures,
            Future<ModelVertices.IndexedMesh> canister, Future<ModelVertices.IndexedMesh> ringBoard,
            Future<ModelVertices.IndexedMesh> blastOrb, Future<ModelVertices.IndexedMesh> medkit,
            Future<ModelVertices.IndexedMesh> armor, Future<ModelVertices.IndexedMesh> energyCell,
            Future<ModelVertices.IndexedMesh> scattergunPickup, Future<ModelVertices.IndexedMesh> shellsPickup,
            Future<ModelVertices.IndexedMesh> railLancePickup, Future<ModelVertices.IndexedMesh> railSlugsPickup,
            Future<ModelVertices.IndexedMesh> railBeam, Future<TurretPrepared> turret, Future<DronePrepared> drone,
            Future<GuardPrepared> guard, Future<ModelVertices.IndexedMesh> enemyBolt,
            Future<ModelVertices.IndexedMesh> door, Future<ModelVertices.IndexedMesh> hazardGrate,
            Future<ModelVertices.IndexedMesh> keycard, Future<ModelVertices.IndexedMesh> console,
            Future<PistolPrepared> pistol, Future<ScatterPrepared> scattergun,
            Future<RailPrepared> railLance, Future<ModelVertices.IndexedMesh> hand) {
            this.textureFutures = textureFutures;
            this.canister = canister;
            this.ringBoard = ringBoard;
            this.blastOrb = blastOrb;
            this.medkit = medkit;
            this.armor = armor;
            this.energyCell = energyCell;
            this.scattergunPickup = scattergunPickup;
            this.shellsPickup = shellsPickup;
            this.railLancePickup = railLancePickup;
            this.railSlugsPickup = railSlugsPickup;
            this.railBeam = railBeam;
            this.turret = turret;
            this.drone = drone;
            this.guard = guard;
            this.enemyBolt = enemyBolt;
            this.door = door;
            this.hazardGrate = hazardGrate;
            this.keycard = keycard;
            this.console = console;
            this.pistol = pistol;
            this.scattergun = scattergun;
            this.railLance = railLance;
            this.hand = hand;
        }

        /** Waits for all texture decodes to finish, disposing all completed pixmaps if any fails. */
        public PreparedTextures awaitTextures() {
            texturesCollected = true;
            Map<String, Disposable> ready = new LinkedHashMap<>();
            Throwable failure = null;
            for (Map.Entry<String, Future<? extends Disposable>> entry : textureFutures.entrySet()) {
                try {
                    Disposable pixmap = awaitFuture(entry.getValue());
                    if (pixmap != null) {
                        ready.put(entry.getKey(), pixmap);
                    }
                } catch (Throwable t) {
                    if (failure == null) {
                        failure = t;
                    }
                }
            }
            if (failure != null) {
                new PreparedTextures(ready).dispose();
                rethrow(failure);
            }
            return new PreparedTextures(ready);
        }

        /** Waits for all 19 world models to be parsed and vertex-packed. */
        public PreparedWorldModels awaitWorldModels() {
            TurretPrepared t = awaitFuture(turret);
            DronePrepared d = awaitFuture(drone);
            GuardPrepared g = awaitFuture(guard);
            return new PreparedWorldModels(
                awaitFuture(canister), awaitFuture(ringBoard), awaitFuture(blastOrb), awaitFuture(medkit),
                awaitFuture(armor), awaitFuture(energyCell), awaitFuture(scattergunPickup), awaitFuture(shellsPickup),
                awaitFuture(railLancePickup), awaitFuture(railSlugsPickup), awaitFuture(railBeam),
                t.rig, t.base, t.head, t.barrels, d.rig, d.mesh,
                g.rig, g.pelvis, g.legLeft, g.legRight, g.torso, g.armLeft, g.armRight,
                awaitFuture(enemyBolt), awaitFuture(door), awaitFuture(hazardGrate),
                awaitFuture(keycard), awaitFuture(console));
        }

        /** Waits for all 4 viewmodels to be parsed and vertex-packed. */
        public PreparedViewmodels awaitViewmodels() {
            PistolPrepared p = awaitFuture(pistol);
            ScatterPrepared s = awaitFuture(scattergun);
            RailPrepared r = awaitFuture(railLance);
            ModelVertices.IndexedMesh h = awaitFuture(hand);
            return new PreparedViewmodels(p.model, p.muzzle, s.model, s.muzzle, r.model, r.muzzle, h);
        }

        @Override
        public void dispose() {
            if (!texturesCollected) {
                texturesCollected = true;
                for (Future<? extends Disposable> future : textureFutures.values()) {
                    future.cancel(false);
                    try {
                        Disposable item = awaitFuture(future);
                        if (item != null) item.dispose();
                    } catch (Throwable ignored) {
                        // Cancelled or failed texture decodes leave nothing else to free.
                    }
                }
            }
            List<Future<?>> remaining = new ArrayList<>();
            remaining.add(canister);
            remaining.add(ringBoard);
            remaining.add(blastOrb);
            remaining.add(medkit);
            remaining.add(armor);
            remaining.add(energyCell);
            remaining.add(scattergunPickup);
            remaining.add(shellsPickup);
            remaining.add(railLancePickup);
            remaining.add(railSlugsPickup);
            remaining.add(railBeam);
            remaining.add(turret);
            remaining.add(drone);
            remaining.add(guard);
            remaining.add(enemyBolt);
            remaining.add(door);
            remaining.add(hazardGrate);
            remaining.add(keycard);
            remaining.add(console);
            remaining.add(pistol);
            remaining.add(scattergun);
            remaining.add(railLance);
            remaining.add(hand);
            for (Future<?> future : remaining) {
                future.cancel(false);
            }
        }
    }

    private AssetPreparer() {}

    /** @return a bounded daemon worker pool sized to available hardware threads (clamped to 2..8). */
    public static ExecutorService newWorkerPool() {
        int threads = Math.max(2, Math.min(Runtime.getRuntime().availableProcessors(), MAX_WORKERS));
        AtomicInteger nextId = new AtomicInteger(1);
        return Executors.newFixedThreadPool(threads, runnable -> {
            Thread thread = new Thread(runnable, "gamex-worker-" + nextId.getAndIncrement());
            thread.setDaemon(true);
            return thread;
        });
    }

    /**
     * Launches concurrent CPU preparation of all startup PNG textures, world OBJ models, and viewmodel OBJ models.
     * Every task is submitted directly to {@code workerPool} so tasks never wait on child tasks.
     */
    public static StartupBatch prepareStartup(WallTextures wallTextures, Palettes palettes,
                                              ExecutorService workerPool) {
        return prepareStartup(wallTextures, palettes, workerPool, ProceduralTextures::read, ModelMesh::loadModel);
    }

    static StartupBatch prepareStartup(WallTextures wallTextures, Palettes palettes, ExecutorService workerPool,
                                       Function<String, ? extends Disposable> textureDecoder,
                                       BiFunction<String, String, ObjModel> modelLoader) {
        if (wallTextures == null) throw new IllegalArgumentException("wallTextures must not be null");
        if (palettes == null) throw new IllegalArgumentException("palettes must not be null");
        if (workerPool == null) throw new IllegalArgumentException("workerPool must not be null");

        Map<String, Future<? extends Disposable>> textureFutures = new LinkedHashMap<>();
        for (String file : ProceduralTextures.allFiles(wallTextures)) {
            textureFutures.put(file, workerPool.submit(() -> textureDecoder.apply(file)));
        }

        ModelPalette worldPalette = palettes.world;
        Future<ModelVertices.IndexedMesh> canister = worldSingle(
            workerPool, modelLoader, WorldModels.CANISTER_PATH, "canister", worldPalette);
        Future<ModelVertices.IndexedMesh> ringBoard = worldSingle(
            workerPool, modelLoader, WorldModels.RING_BOARD_PATH, "ring board", worldPalette);
        Future<ModelVertices.IndexedMesh> blastOrb = worldSingle(
            workerPool, modelLoader, WorldModels.BLAST_ORB_PATH, "blast orb", worldPalette);
        Future<ModelVertices.IndexedMesh> medkit = worldSingle(
            workerPool, modelLoader, WorldModels.MEDKIT_PATH, "medkit", worldPalette);
        Future<ModelVertices.IndexedMesh> armor = worldSingle(
            workerPool, modelLoader, WorldModels.ARMOR_PATH, "armor", worldPalette);
        Future<ModelVertices.IndexedMesh> energyCell = worldSingle(
            workerPool, modelLoader, WorldModels.ENERGY_CELL_PATH, "energy cell", worldPalette);
        Future<ModelVertices.IndexedMesh> scattergunPickup = worldSingle(
            workerPool, modelLoader, WorldModels.SCATTERGUN_PICKUP_PATH, "scattergun pickup", worldPalette);
        Future<ModelVertices.IndexedMesh> shellsPickup = worldSingle(
            workerPool, modelLoader, WorldModels.SHELLS_PICKUP_PATH, "shells pickup", worldPalette);
        Future<ModelVertices.IndexedMesh> railLancePickup = worldSingle(
            workerPool, modelLoader, WorldModels.RAIL_LANCE_PICKUP_PATH, "rail lance pickup", worldPalette);
        Future<ModelVertices.IndexedMesh> railSlugsPickup = worldSingle(
            workerPool, modelLoader, WorldModels.RAIL_SLUGS_PICKUP_PATH, "rail slugs pickup", worldPalette);
        Future<ModelVertices.IndexedMesh> railBeam = worldSingle(
            workerPool, modelLoader, WorldModels.RAIL_BEAM_PATH, "rail beam", worldPalette);

        Future<TurretPrepared> turret = workerPool.submit(() -> {
            ObjModel obj = modelLoader.apply(WorldModels.TURRET_PATH, "turret");
            return new TurretPrepared(
                new ModelRig(obj),
                WorldRenderer.unpackWorldPart(obj, WorldModels.TURRET_PATH, "turret", "base", worldPalette),
                WorldRenderer.unpackWorldPart(obj, WorldModels.TURRET_PATH, "turret", "head", worldPalette),
                WorldRenderer.unpackWorldPart(obj, WorldModels.TURRET_PATH, "turret", "barrels", worldPalette));
        });
        Future<DronePrepared> drone = workerPool.submit(() -> {
            ObjModel obj = modelLoader.apply(WorldModels.DRONE_PATH, "drone");
            return new DronePrepared(
                new ModelRig(obj),
                WorldRenderer.unpackWorldModel(obj, WorldModels.DRONE_PATH, "drone", worldPalette));
        });
        Future<GuardPrepared> guard = workerPool.submit(() -> {
            ObjModel obj = modelLoader.apply(WorldModels.GUARD_PATH, "guard");
            return new GuardPrepared(
                new ModelRig(obj),
                WorldRenderer.unpackWorldPart(obj, WorldModels.GUARD_PATH, "guard", "pelvis", worldPalette),
                WorldRenderer.unpackWorldPart(obj, WorldModels.GUARD_PATH, "guard", "leg_l", worldPalette),
                WorldRenderer.unpackWorldPart(obj, WorldModels.GUARD_PATH, "guard", "leg_r", worldPalette),
                WorldRenderer.unpackWorldPart(obj, WorldModels.GUARD_PATH, "guard", "torso", worldPalette),
                WorldRenderer.unpackWorldPart(obj, WorldModels.GUARD_PATH, "guard", "arm_l", worldPalette),
                WorldRenderer.unpackWorldPart(obj, WorldModels.GUARD_PATH, "guard", "arm_r", worldPalette));
        });

        Future<ModelVertices.IndexedMesh> enemyBolt = worldSingle(
            workerPool, modelLoader, WorldModels.ENEMY_BOLT_PATH, "enemy bolt", worldPalette);
        Future<ModelVertices.IndexedMesh> door = worldSingle(
            workerPool, modelLoader, WorldModels.DOOR_BULKHEAD_PATH, "door bulkhead", worldPalette);
        Future<ModelVertices.IndexedMesh> hazardGrate = worldSingle(
            workerPool, modelLoader, WorldModels.HAZARD_GRATE_PATH, "hazard grate", worldPalette);
        Future<ModelVertices.IndexedMesh> keycard = worldSingle(
            workerPool, modelLoader, WorldModels.KEYCARD_PATH, "keycard", worldPalette);
        Future<ModelVertices.IndexedMesh> console = worldSingle(
            workerPool, modelLoader, WorldModels.SECURITY_CONSOLE_PATH, "security console", worldPalette);

        Future<PistolPrepared> pistol = workerPool.submit(() -> {
            ObjModel obj = modelLoader.apply(PistolModel.ASSET_PATH, "viewmodel pistol");
            return new PistolPrepared(ViewmodelRenderer.unpackPistol(obj, palettes.pistol), obj.getSocket("muzzle"));
        });
        Future<ScatterPrepared> scattergun = workerPool.submit(() -> {
            ObjModel obj = modelLoader.apply(ScattergunModel.ASSET_PATH, "viewmodel scattergun");
            return new ScatterPrepared(
                ViewmodelRenderer.unpackScattergun(obj, palettes.scattergun), obj.getSocket("muzzle"));
        });
        Future<RailPrepared> railLance = workerPool.submit(() -> {
            ObjModel obj = modelLoader.apply(RailLanceModel.ASSET_PATH, "viewmodel rail lance");
            return new RailPrepared(
                ViewmodelRenderer.unpackRailLance(obj, palettes.railLance), obj.getSocket("muzzle"));
        });
        Future<ModelVertices.IndexedMesh> hand = workerPool.submit(() -> {
            ObjModel obj = modelLoader.apply(HandModel.ASSET_PATH, "viewmodel hand");
            return ViewmodelRenderer.unpackHand(obj, palettes.hand);
        });

        return new StartupBatch(
            textureFutures, canister, ringBoard, blastOrb, medkit, armor, energyCell, scattergunPickup, shellsPickup,
            railLancePickup, railSlugsPickup, railBeam, turret, drone, guard, enemyBolt, door, hazardGrate, keycard,
            console, pistol, scattergun, railLance, hand);
    }

    private static Future<ModelVertices.IndexedMesh> worldSingle(ExecutorService pool,
                                                                 BiFunction<String, String, ObjModel> modelLoader,
                                                                 String assetPath, String label,
                                                                 ModelPalette palette) {
        return pool.submit(() -> WorldRenderer.unpackWorldModel(
            modelLoader.apply(assetPath, label), assetPath, label, palette));
    }

    private static <T> T awaitFuture(Future<T> future) {
        try {
            return future.get();
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new IllegalStateException("Startup asset preparation was interrupted", e);
        } catch (ExecutionException e) {
            rethrow(e.getCause());
            return null;
        }
    }

    private static void rethrow(Throwable cause) {
        if (cause instanceof RuntimeException) throw (RuntimeException) cause;
        if (cause instanceof Error) throw (Error) cause;
        throw new IllegalStateException("Startup asset preparation failed", cause);
    }
}

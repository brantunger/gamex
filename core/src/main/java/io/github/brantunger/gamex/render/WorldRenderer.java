package io.github.brantunger.gamex.render;

import com.badlogic.gdx.Gdx;
import com.badlogic.gdx.graphics.Color;
import com.badlogic.gdx.graphics.GL20;
import com.badlogic.gdx.graphics.Mesh;
import com.badlogic.gdx.graphics.PerspectiveCamera;
import com.badlogic.gdx.graphics.Texture;
import com.badlogic.gdx.graphics.VertexAttribute;
import com.badlogic.gdx.graphics.VertexAttributes;
import com.badlogic.gdx.graphics.g2d.TextureRegion;
import com.badlogic.gdx.graphics.g3d.decals.CameraGroupStrategy;
import com.badlogic.gdx.graphics.g3d.decals.Decal;
import com.badlogic.gdx.graphics.g3d.decals.DecalBatch;
import com.badlogic.gdx.graphics.glutils.ShaderProgram;
import com.badlogic.gdx.math.MathUtils;
import com.badlogic.gdx.math.Matrix3;
import com.badlogic.gdx.math.Matrix4;
import com.badlogic.gdx.math.Vector3;
import com.badlogic.gdx.utils.Array;
import com.badlogic.gdx.utils.Disposable;
import com.badlogic.gdx.utils.GdxRuntimeException;
import com.badlogic.gdx.utils.ScreenUtils;
import io.github.brantunger.gamex.GameSimulation;
import io.github.brantunger.gamex.model.ModelRig;
import io.github.brantunger.gamex.model.ObjModel;
import io.github.brantunger.gamex.player.Player;
import io.github.brantunger.gamex.settings.GameSettings;
import io.github.brantunger.gamex.weapon.BlastProjectile;
import io.github.brantunger.gamex.weapon.EnemyProjectile;
import io.github.brantunger.gamex.world.Door;
import io.github.brantunger.gamex.world.Effect;
import io.github.brantunger.gamex.world.Enemy;
import io.github.brantunger.gamex.world.GameMap;
import io.github.brantunger.gamex.world.MapGeometry;
import io.github.brantunger.gamex.world.Pickup;
import io.github.brantunger.gamex.world.SecurityConsole;
import io.github.brantunger.gamex.world.Target;
import io.github.brantunger.gamex.world.WorldModels;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * Draws the 3D view: the map as a few static meshes, one per texture, the targets, pickups and blast projectiles as
 * 3D models loaded from OBJ files, and the halos, exit pad and effects as billboards.
 */
public final class WorldRenderer implements Disposable {

    private static final float FIELD_OF_VIEW = 70f;
    private static final float NEAR = 0.1f;
    private static final float FAR = 60f;
    private static final Color FOG_COLOUR = new Color(0.035f, 0.035f, 0.055f, 1f);
    private static final float FOG_START = 3.5f;
    private static final float FOG_END = 20f;
    /** How far the view rises and falls with each step, in world units. */
    private static final float BOB_HEIGHT = 0.012f;
    /** How long the spark of a pistol impact shows over its scorch mark, in seconds. */
    private static final float IMPACT_SPARK_TIME = 0.12f;
    /** The widest an explosion's fireball is drawn, so that it fits between the floor and the ceiling. */
    private static final float MAX_FIREBALL_SIZE = 0.8f;
    /** Explosions with at least this radius also light up the floor they cover. */
    private static final float MIN_FLOOR_GLOW_RADIUS = 1f;
    /** How far the floor glow sits above the floor. */
    private static final float FLOOR_GLOW_HEIGHT = 0.02f;

    private static final String VERTEX_SHADER =
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

    private static final String FRAGMENT_SHADER =
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

    private static final String MODEL_VERTEX_SHADER =
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

    private static final String MODEL_FRAGMENT_SHADER =
        "#ifdef GL_ES\n"
            + "precision mediump float;\n"
            + "#endif\n"
            + "varying vec3 v_colour;\n"
            + "void main() {\n"
            + "    gl_FragColor = vec4(v_colour, 1.0);\n"
            + "}\n";

    /** A static mesh and the texture it is drawn with. */
    private static final class Surface {
        final Mesh mesh;
        final Texture texture;

        Surface(Mesh mesh, Texture texture) {
            this.mesh = mesh;
            this.texture = texture;
        }
    }

    private final PerspectiveCamera camera;
    private final ProceduralTextures textures;
    private final ShaderProgram shader;
    private final ShaderProgram modelShader;
    private final Array<Surface> surfaces = new Array<>();
    private GameMap currentMap;
    private final Mesh canisterMesh;
    private final Mesh ringBoardMesh;
    private final Mesh blastOrbMesh;
    private final Mesh medkitMesh;
    private final Mesh armorMesh;
    private final Mesh energyCellMesh;
    private final Mesh scattergunPickupMesh;
    private final Mesh shellsPickupMesh;
    private final Mesh turretBaseMesh;
    private final Mesh turretHeadMesh;
    private final Mesh turretBarrelsMesh;
    private final Mesh droneMesh;
    private final Mesh guardPelvisMesh;
    private final Mesh guardLegLeftMesh;
    private final Mesh guardLegRightMesh;
    private final Mesh guardTorsoMesh;
    private final Mesh guardArmLeftMesh;
    private final Mesh guardArmRightMesh;
    private final Mesh wardenPelvisMesh;
    private final Mesh wardenLegLeftMesh;
    private final Mesh wardenLegRightMesh;
    private final Mesh wardenTorsoMesh;
    private final Mesh wardenArmLeftMesh;
    private final Mesh wardenArmRightMesh;
    private final Mesh enemyBoltMesh;
    private final Mesh doorMesh;
    private final Mesh hazardGrateMesh;
    private final Mesh keycardMesh;
    private final Mesh consoleMesh;
    private final ModelRig turretRig;
    private final ModelRig droneRig;
    private final ModelRig guardRig;
    private final ModelRig wardenRig;
    private final CameraGroupStrategy groupStrategy;
    private final DecalBatch decalBatch;
    private final Array<Decal> decals = new Array<>();
    private int decalsUsed;

    private final TextureRegion scorch;
    private final TextureRegion spark;
    private final TextureRegion orb;
    private final TextureRegion explosion;
    private final TextureRegion exitPad;

    private final Matrix4 modelTransform = new Matrix4();
    private final Matrix4 rootTransform = new Matrix4();
    private final Matrix3 normalMatrix = new Matrix3();
    private final Matrix4 rotYaw = new Matrix4();
    private final Matrix4 rotPitch = new Matrix4();
    private final Matrix4 rotLegL = new Matrix4();
    private final Matrix4 rotLegR = new Matrix4();
    private final Matrix4 rotArmL = new Matrix4();
    private final Matrix4 rotArmR = new Matrix4();
    private final Map<String, Matrix4> turretRotations = new HashMap<>();
    private final Map<String, Matrix4> guardRotations = new HashMap<>();
    private final Vector3 direction = new Vector3();
    private final Vector3 scratch = new Vector3();

    /**
     * @param map the initial map to build the static meshes from
     * @param textures the textures to draw with; the caller keeps ownership of them
     */
    public WorldRenderer(GameMap map, ProceduralTextures textures) {
        this.textures = textures;
        camera = new PerspectiveCamera(FIELD_OF_VIEW, Gdx.graphics.getWidth(), Gdx.graphics.getHeight());
        camera.near = NEAR;
        camera.far = FAR;

        shader = new ShaderProgram(VERTEX_SHADER, FRAGMENT_SHADER);
        if (!shader.isCompiled()) {
            throw new GdxRuntimeException("The world shader did not compile: " + shader.getLog());
        }
        modelShader = new ShaderProgram(MODEL_VERTEX_SHADER, MODEL_FRAGMENT_SHADER);
        if (!modelShader.isCompiled()) {
            throw new GdxRuntimeException("The world model shader did not compile: " + modelShader.getLog());
        }

        setMap(map);

        canisterMesh = ModelMesh.loadIndexedMesh(WorldModels.CANISTER_PATH, "canister", WorldModels::packIndexed);
        ringBoardMesh = ModelMesh.loadIndexedMesh(WorldModels.RING_BOARD_PATH, "ring board", WorldModels::packIndexed);
        blastOrbMesh = ModelMesh.loadIndexedMesh(WorldModels.BLAST_ORB_PATH, "blast orb", WorldModels::packIndexed);
        medkitMesh = ModelMesh.loadIndexedMesh(WorldModels.MEDKIT_PATH, "medkit", WorldModels::packIndexed);
        armorMesh = ModelMesh.loadIndexedMesh(WorldModels.ARMOR_PATH, "armor", WorldModels::packIndexed);
        energyCellMesh = ModelMesh.loadIndexedMesh(WorldModels.ENERGY_CELL_PATH, "energy cell",
            WorldModels::packIndexed);
        scattergunPickupMesh = ModelMesh.loadIndexedMesh(WorldModels.SCATTERGUN_PICKUP_PATH, "scattergun pickup",
            WorldModels::packIndexed);
        shellsPickupMesh = ModelMesh.loadIndexedMesh(WorldModels.SHELLS_PICKUP_PATH, "shells pickup",
            WorldModels::packIndexed);

        ObjModel turretObj = ModelMesh.loadModel(WorldModels.TURRET_PATH, "turret");
        turretRig = new ModelRig(turretObj);
        turretBaseMesh = ModelMesh.buildIndexed(WorldModels.packPartIndexed(turretObj, "base"));
        turretHeadMesh = ModelMesh.buildIndexed(WorldModels.packPartIndexed(turretObj, "head"));
        turretBarrelsMesh = ModelMesh.buildIndexed(WorldModels.packPartIndexed(turretObj, "barrels"));

        ObjModel droneObj = ModelMesh.loadModel(WorldModels.DRONE_PATH, "drone");
        droneRig = new ModelRig(droneObj);
        droneMesh = ModelMesh.buildIndexed(WorldModels.packIndexed(droneObj));

        ObjModel guardObj = ModelMesh.loadModel(WorldModels.GUARD_PATH, "guard");
        guardRig = new ModelRig(guardObj);
        guardPelvisMesh = ModelMesh.buildIndexed(WorldModels.packPartIndexed(guardObj, "pelvis"));
        guardLegLeftMesh = ModelMesh.buildIndexed(WorldModels.packPartIndexed(guardObj, "leg_l"));
        guardLegRightMesh = ModelMesh.buildIndexed(WorldModels.packPartIndexed(guardObj, "leg_r"));
        guardTorsoMesh = ModelMesh.buildIndexed(WorldModels.packPartIndexed(guardObj, "torso"));
        guardArmLeftMesh = ModelMesh.buildIndexed(WorldModels.packPartIndexed(guardObj, "arm_l"));
        guardArmRightMesh = ModelMesh.buildIndexed(WorldModels.packPartIndexed(guardObj, "arm_r"));

        ObjModel wardenObj = ModelMesh.loadModel(WorldModels.WARDEN_PATH, "warden");
        wardenRig = new ModelRig(wardenObj);
        wardenPelvisMesh = ModelMesh.buildIndexed(WorldModels.packPartIndexed(wardenObj, "pelvis"));
        wardenLegLeftMesh = ModelMesh.buildIndexed(WorldModels.packPartIndexed(wardenObj, "leg_l"));
        wardenLegRightMesh = ModelMesh.buildIndexed(WorldModels.packPartIndexed(wardenObj, "leg_r"));
        wardenTorsoMesh = ModelMesh.buildIndexed(WorldModels.packPartIndexed(wardenObj, "torso"));
        wardenArmLeftMesh = ModelMesh.buildIndexed(WorldModels.packPartIndexed(wardenObj, "arm_l"));
        wardenArmRightMesh = ModelMesh.buildIndexed(WorldModels.packPartIndexed(wardenObj, "arm_r"));

        enemyBoltMesh = ModelMesh.loadIndexedMesh(WorldModels.ENEMY_BOLT_PATH, "enemy bolt", WorldModels::packIndexed);
        doorMesh = ModelMesh.loadIndexedMesh(WorldModels.DOOR_BULKHEAD_PATH, "door bulkhead", WorldModels::packIndexed);
        hazardGrateMesh = ModelMesh.loadIndexedMesh(WorldModels.HAZARD_GRATE_PATH, "hazard grate",
            WorldModels::packIndexed);
        keycardMesh = ModelMesh.loadIndexedMesh(WorldModels.KEYCARD_PATH, "keycard", WorldModels::packIndexed);
        consoleMesh = ModelMesh.loadIndexedMesh(WorldModels.SECURITY_CONSOLE_PATH, "security console",
            WorldModels::packIndexed);

        turretRotations.put("head", rotYaw);
        turretRotations.put("barrels", rotPitch);
        guardRotations.put("leg_l", rotLegL);
        guardRotations.put("leg_r", rotLegR);
        guardRotations.put("arm_l", rotArmL);
        guardRotations.put("arm_r", rotArmR);

        groupStrategy = new CameraGroupStrategy(camera);
        decalBatch = new DecalBatch(groupStrategy);
        scorch = new TextureRegion(textures.scorch);
        spark = new TextureRegion(textures.spark);
        orb = new TextureRegion(textures.orb);
        explosion = new TextureRegion(textures.explosion);
        exitPad = new TextureRegion(textures.exitPad);
    }

    /** Rebuilds the static map geometry when loading a different stage. */
    public void setMap(GameMap map) {
        if (map == currentMap) return;
        for (Surface surface : surfaces) surface.mesh.dispose();
        surfaces.clear();
        currentMap = map;
        MapGeometry geometry = MapGeometry.build(map);
        for (Map.Entry<Character, float[]> entry : geometry.walls.entrySet()) {
            surfaces.add(new Surface(buildMesh(entry.getValue()), textures.wall(entry.getKey())));
        }
        surfaces.add(new Surface(buildMesh(geometry.floor), textures.floor));
        surfaces.add(new Surface(buildMesh(geometry.ceiling), textures.ceiling));
    }

    /** Turns quads from {@link MapGeometry} into an indexed mesh of two triangles per quad. */
    private static Mesh buildMesh(float[] quads) {
        int quadCount = quads.length / MapGeometry.QUAD_SIZE;
        if (quadCount * 4 > 65535) {
            throw new GdxRuntimeException("Too many quads for one mesh: " + quadCount);
        }
        short[] indices = new short[quadCount * 6];
        for (int quad = 0; quad < quadCount; quad++) {
            int first = quad * 4;
            int at = quad * 6;
            indices[at] = (short) first;
            indices[at + 1] = (short) (first + 1);
            indices[at + 2] = (short) (first + 2);
            indices[at + 3] = (short) first;
            indices[at + 4] = (short) (first + 2);
            indices[at + 5] = (short) (first + 3);
        }
        Mesh mesh = new Mesh(true, quadCount * 4, indices.length,
            new VertexAttribute(VertexAttributes.Usage.Position, 3, ShaderProgram.POSITION_ATTRIBUTE),
            new VertexAttribute(VertexAttributes.Usage.TextureCoordinates, 2, ShaderProgram.TEXCOORD_ATTRIBUTE + "0"),
            new VertexAttribute(VertexAttributes.Usage.Generic, 1, "a_shade"));
        mesh.setVertices(quads);
        mesh.setIndices(indices);
        return mesh;
    }

    /**
     * Adapts the camera to a new window size.
     * @param width the new width in pixels
     * @param height the new height in pixels
     */
    public void resize(int width, int height) {
        camera.viewportWidth = width;
        camera.viewportHeight = height;
    }

    /**
     * Clears the screen and draws the world from the player's eyes.
     * @param simulation the game state to draw
     * @param settings the live gameplay settings (FOV, view bob)
     */
    public void render(GameSimulation simulation, GameSettings settings) {
        setMap(simulation.map);
        Player player = simulation.player;
        camera.fieldOfView = settings.getFov();
        player.eye(camera.position);
        if (settings.isViewBob() && !player.isDead()) {
            camera.position.y += MathUtils.sin(player.getBobPhase()) * BOB_HEIGHT;
        }
        camera.direction.set(player.direction(direction));
        camera.up.set(Vector3.Y);
        camera.update();

        float roomLight = simulation.isMuzzleFlashVisible() ? 1.3f : 1f;
        beginWorldPass(roomLight);
        renderWorldModels(simulation, roomLight);

        // Billboards are seen from both sides.
        Gdx.gl.glDisable(GL20.GL_CULL_FACE);
        decalsUsed = 0;
        addExitPad(simulation.map, simulation.getTime());
        addPickupGlows(simulation);
        addConsoleGlows(simulation);
        addProjectiles(simulation);
        addEnemyTelegraphsAndBolts(simulation);
        addEffects(simulation);
        decalBatch.flush();

        endWorldPass();
    }

    /**
     * Draws a gentle panning camera preview of {@code simulation} for the Title and Briefing backdrops.
     */
    public void renderBackdrop(GameSimulation simulation, GameSettings settings, float previewTime) {
        setMap(simulation.map);
        camera.fieldOfView = settings.getFov();
        camera.position.set(simulation.map.getSpawnX(), Player.EYE_HEIGHT + 0.05f, simulation.map.getSpawnZ());
        float yaw = simulation.map.getSpawnYaw() + MathUtils.sin(previewTime * 0.35f) * 26f;
        float pitch = MathUtils.sin(previewTime * 0.22f) * 4f - 2f;
        double yawRad = Math.toRadians(yaw);
        double pitchRad = Math.toRadians(pitch);
        double flat = Math.cos(pitchRad);
        camera.direction.set((float) (Math.sin(yawRad) * flat), (float) Math.sin(pitchRad),
            (float) (-Math.cos(yawRad) * flat));
        camera.up.set(Vector3.Y);
        camera.update();

        beginWorldPass(0.92f);
        renderWorldModels(simulation, 0.92f);
        Gdx.gl.glDisable(GL20.GL_CULL_FACE);
        decalsUsed = 0;
        addExitPad(simulation.map, previewTime);
        addPickupGlows(simulation);
        addConsoleGlows(simulation);
        addEnemyTelegraphsAndBolts(simulation);
        decalBatch.flush();
        endWorldPass();
    }

    private void beginWorldPass(float roomLight) {
        Gdx.gl.glDepthMask(true);
        ScreenUtils.clear(FOG_COLOUR, true);
        Gdx.gl.glEnable(GL20.GL_DEPTH_TEST);
        Gdx.gl.glDepthFunc(GL20.GL_LEQUAL);
        Gdx.gl.glEnable(GL20.GL_CULL_FACE);
        Gdx.gl.glCullFace(GL20.GL_BACK);
        Gdx.gl.glDisable(GL20.GL_BLEND);

        shader.bind();
        shader.setUniformMatrix("u_projViewTrans", camera.combined);
        shader.setUniformf("u_cameraPosition", camera.position);
        shader.setUniformf("u_fogRange", FOG_START, FOG_END);
        shader.setUniformf("u_fogColour", FOG_COLOUR.r, FOG_COLOUR.g, FOG_COLOUR.b);
        shader.setUniformf("u_light", roomLight);
        shader.setUniformi("u_texture", 0);
        for (Surface surface : surfaces) {
            surface.texture.bind(0);
            surface.mesh.render(shader, GL20.GL_TRIANGLES);
        }
    }

    private void endWorldPass() {
        Gdx.gl.glDepthMask(true);
        Gdx.gl.glDisable(GL20.GL_DEPTH_TEST);
        Gdx.gl.glDisable(GL20.GL_BLEND);
    }

    private void renderWorldModels(GameSimulation simulation, float roomLight) {
        modelShader.bind();
        modelShader.setUniformMatrix("u_projViewTrans", camera.combined);
        modelShader.setUniformf("u_cameraPosition", camera.position);
        modelShader.setUniformf("u_fogRange", FOG_START, FOG_END);
        modelShader.setUniformf("u_fogColour", FOG_COLOUR.r, FOG_COLOUR.g, FOG_COLOUR.b);

        modelShader.setUniformf("u_light", roomLight);
        setAccent(null);
        List<GameMap.HazardCell> hazardCells = simulation.map.getHazardCells();
        if (!hazardCells.isEmpty()) {
            modelShader.setUniformf("u_hitFlash", 0f);
            float t = simulation.getTime();
            for (int i = 0; i < hazardCells.size(); i++) {
                GameMap.HazardCell cell = hazardCells.get(i);
                float pulse = 0.5f + 0.5f * MathUtils.sin(t * 7.5f + cell.col * 1.1f + cell.row * 0.7f);
                modelShader.setUniformf("u_light", Math.max(roomLight, 0.92f + 0.34f * pulse));
                modelShader.setUniformf("u_accentColour",
                    WorldModels.ACCENT_HAZARD_LIVE[0] * (0.72f + 0.28f * pulse),
                    WorldModels.ACCENT_HAZARD_LIVE[1] * (0.78f + 0.22f * pulse),
                    WorldModels.ACCENT_HAZARD_LIVE[2],
                    WorldModels.ACCENT_HAZARD_LIVE[3]);
                modelTransform.idt().translate(cell.x, 0f, cell.z);
                drawModelMesh(hazardGrateMesh, modelTransform);
            }
            modelShader.setUniformf("u_light", roomLight);
            setAccent(null);
        }

        for (Target target : simulation.targets) {
            if (target.isDestroyed()) continue;
            float dx = camera.position.x - target.getX();
            float dz = camera.position.z - target.getZ();
            if (dx * dx + dz * dz < 1e-6f) dz = 1f;
            float yaw = MathUtils.atan2(dx, dz) * MathUtils.radiansToDegrees + 180f;
            modelTransform.idt()
                .translate(target.getX(), 0f, target.getZ())
                .rotate(Vector3.Y, yaw);
            modelShader.setUniformf("u_hitFlash", target.getHitFlash());
            Mesh mesh = target.getKind() == Target.Kind.CANISTER ? canisterMesh : ringBoardMesh;
            drawModelMesh(mesh, modelTransform);
        }

        // Hostile 3D enemies with hierarchical local-pivot articulation and wind-up luminance.
        for (Enemy enemy : simulation.enemies) {
            if (enemy.isDestroyed()) continue;
            float windup = enemy.getWindupProgress();
            modelShader.setUniformf("u_light", roomLight + 0.45f * windup);
            modelShader.setUniformf("u_hitFlash", enemy.getHitFlash());
            switch (enemy.getKind()) {
                case TURRET:
                    setAccent(null);
                    prepareTurretPose(enemy);
                    drawModelMesh(turretBaseMesh,
                        turretRig.evaluatePartTransform("base", turretRotations, rootTransform, modelTransform));
                    drawModelMesh(turretHeadMesh,
                        turretRig.evaluatePartTransform("head", turretRotations, rootTransform, modelTransform));
                    drawModelMesh(turretBarrelsMesh,
                        turretRig.evaluatePartTransform("barrels", turretRotations, rootTransform, modelTransform));
                    break;
                case GUARD:
                    setAccent(null);
                    prepareGuardPose(enemy);
                    drawModelMesh(guardPelvisMesh,
                        guardRig.evaluatePartTransform("pelvis", guardRotations, rootTransform, modelTransform));
                    drawModelMesh(guardLegLeftMesh,
                        guardRig.evaluatePartTransform("leg_l", guardRotations, rootTransform, modelTransform));
                    drawModelMesh(guardLegRightMesh,
                        guardRig.evaluatePartTransform("leg_r", guardRotations, rootTransform, modelTransform));
                    drawModelMesh(guardTorsoMesh,
                        guardRig.evaluatePartTransform("torso", guardRotations, rootTransform, modelTransform));
                    drawModelMesh(guardArmLeftMesh,
                        guardRig.evaluatePartTransform("arm_l", guardRotations, rootTransform, modelTransform));
                    drawModelMesh(guardArmRightMesh,
                        guardRig.evaluatePartTransform("arm_r", guardRotations, rootTransform, modelTransform));
                    break;
                case WARDEN:
                    setWardenAccent(enemy, windup, simulation.getTime());
                    prepareWardenPose(enemy);
                    drawModelMesh(wardenPelvisMesh,
                        wardenRig.evaluatePartTransform("pelvis", guardRotations, rootTransform, modelTransform));
                    drawModelMesh(wardenLegLeftMesh,
                        wardenRig.evaluatePartTransform("leg_l", guardRotations, rootTransform, modelTransform));
                    drawModelMesh(wardenLegRightMesh,
                        wardenRig.evaluatePartTransform("leg_r", guardRotations, rootTransform, modelTransform));
                    drawModelMesh(wardenTorsoMesh,
                        wardenRig.evaluatePartTransform("torso", guardRotations, rootTransform, modelTransform));
                    drawModelMesh(wardenArmLeftMesh,
                        wardenRig.evaluatePartTransform("arm_l", guardRotations, rootTransform, modelTransform));
                    drawModelMesh(wardenArmRightMesh,
                        wardenRig.evaluatePartTransform("arm_r", guardRotations, rootTransform, modelTransform));
                    break;
                case DRONE:
                default:
                    setAccent(null);
                    prepareDronePose(enemy, simulation.getTime());
                    drawModelMesh(droneMesh, rootTransform);
                    break;
            }
        }

        // Hovering and spinning 3D pickups.
        modelShader.setUniformf("u_light", 1.2f);
        modelShader.setUniformf("u_hitFlash", 0f);
        float pickupSpin = simulation.getTime() * 95f;
        for (Pickup pickup : simulation.pickups) {
            if (pickup.isCollected()) continue;
            float hover = 0.08f + 0.035f * MathUtils.sin(simulation.getTime() * 3.5f + pickup.getX() + pickup.getZ());
            modelTransform.idt()
                .translate(pickup.getX(), hover, pickup.getZ())
                .rotate(Vector3.Y, pickupSpin + (pickup.getX() + pickup.getZ()) * 45f);
            setAccent(pickupAccent(pickup.getKind()));
            drawModelMesh(pickupMesh(pickup.getKind()), modelTransform);
        }

        setAccent(null);
        modelShader.setUniformf("u_light", 1.35f);
        modelShader.setUniformf("u_hitFlash", 0f);
        float spin = simulation.getTime() * 180f;
        for (BlastProjectile projectile : simulation.projectiles) {
            float scale = 0.85f + 0.55f * projectile.getCharge();
            modelTransform.idt()
                .translate(projectile.position)
                .rotate(Vector3.Z, 26f)
                .rotate(Vector3.Y, spin)
                .scale(scale, scale, scale);
            drawModelMesh(blastOrbMesh, modelTransform);
        }

        modelShader.setUniformf("u_light", 1.4f);
        float boltSpin = simulation.getTime() * 220f;
        for (EnemyProjectile bolt : simulation.enemyProjectiles) {
            modelTransform.idt()
                .translate(bolt.position)
                .rotate(Vector3.Z, 26f)
                .rotate(Vector3.Y, boltSpin);
            drawModelMesh(enemyBoltMesh, modelTransform);
        }

        // Sliding 3D bulkhead doors and security console pedestals (single shared mesh each with u_accentColour).
        modelShader.setUniformf("u_hitFlash", 0f);
        for (Door door : simulation.doors) {
            float yaw = door.getOrientation() == Door.Orientation.SPAN_Z ? 90f : 0f;
            modelTransform.idt()
                .translate(door.getX(), door.getBottomY(), door.getZ())
                .rotate(Vector3.Y, yaw);
            modelShader.setUniformf("u_light", door.isOpening() ? Math.max(roomLight, 1.08f) : roomLight);
            setAccent(doorAccent(door));
            drawModelMesh(doorMesh, modelTransform);
        }

        modelShader.setUniformf("u_light", Math.max(roomLight, 1.05f));
        for (SecurityConsole console : simulation.consoles) {
            modelTransform.idt().translate(console.getX(), 0f, console.getZ());
            setAccent(console.isActivated() ? WorldModels.ACCENT_CONSOLE_ACTIVE : WorldModels.ACCENT_CONSOLE_LOCKED);
            drawModelMesh(consoleMesh, modelTransform);
        }
    }

    private void drawModelMesh(Mesh mesh, Matrix4 transform) {
        modelShader.setUniformMatrix("u_model", transform);
        normalMatrix.set(transform).inv().transpose();
        modelShader.setUniformMatrix("u_normalMatrix", normalMatrix);
        mesh.render(modelShader, GL20.GL_TRIANGLES);
    }

    private void setAccent(float[] rgbaGloss) {
        if (rgbaGloss == null) {
            modelShader.setUniformf("u_accentColour", 0f, 0f, 0f, 0f);
        } else {
            modelShader.setUniformf("u_accentColour", rgbaGloss[0], rgbaGloss[1], rgbaGloss[2], rgbaGloss[3]);
        }
    }

    private void prepareTurretPose(Enemy enemy) {
        rootTransform.idt().translate(enemy.getX(), 0f, enemy.getZ());
        rotYaw.setToRotation(Vector3.Y, -enemy.getYaw());
        rotPitch.setToRotation(Vector3.X, enemy.getAimPitch());
    }

    private void prepareGuardPose(Enemy enemy) {
        float swing = MathUtils.sin(enemy.getWalkPhase());
        float bobY = Math.abs(swing) * 0.018f;
        float strideDeg = swing * 26f;
        rootTransform.idt()
            .translate(enemy.getX(), bobY, enemy.getZ())
            .rotate(Vector3.Y, -enemy.getYaw());
        rotLegL.setToRotation(Vector3.X, strideDeg);
        rotLegR.setToRotation(Vector3.X, -strideDeg);
        rotArmL.setToRotation(Vector3.X, -strideDeg * 0.8f);
        rotArmR.setToRotation(Vector3.X, -strideDeg * 0.15f);
    }

    private void prepareWardenPose(Enemy enemy) {
        float swing = MathUtils.sin(enemy.getWalkPhase());
        float bobY = Math.abs(swing) * 0.015f;
        float strideDeg = swing * 20f;
        rootTransform.idt()
            .translate(enemy.getX(), bobY, enemy.getZ())
            .rotate(Vector3.Y, -enemy.getYaw());
        rotLegL.setToRotation(Vector3.X, strideDeg);
        rotLegR.setToRotation(Vector3.X, -strideDeg);

        float armPitchL;
        float armPitchR;
        if (enemy.isSlamAttack()) {
            float windup = enemy.getWindupProgress();
            float slamPitch = enemy.getState() == Enemy.State.LUNGING ? -44f : windup * 68f;
            armPitchL = slamPitch;
            armPitchR = slamPitch;
        } else {
            float windup = enemy.getWindupProgress();
            float recoil = enemy.getShootRecoil() * 16f;
            armPitchL = -strideDeg * 0.45f + windup * 10f + recoil;
            armPitchR = strideDeg * 0.45f + windup * 10f + recoil;
        }
        rotArmL.setToRotation(Vector3.X, armPitchL);
        rotArmR.setToRotation(Vector3.X, armPitchR);
    }

    private void setWardenAccent(Enemy enemy, float windup, float time) {
        if (enemy.isOverdrive()) {
            float pulse = 0.5f + 0.5f * MathUtils.sin(time * 9f);
            modelShader.setUniformf("u_accentColour",
                WorldModels.ACCENT_WARDEN_OVERDRIVE[0] * (0.72f + 0.28f * pulse),
                WorldModels.ACCENT_WARDEN_OVERDRIVE[1] * (0.85f + 0.15f * pulse) + 0.15f * windup,
                WorldModels.ACCENT_WARDEN_OVERDRIVE[2],
                WorldModels.ACCENT_WARDEN_OVERDRIVE[3]);
        } else {
            float boost = 0.22f * windup;
            modelShader.setUniformf("u_accentColour",
                Math.min(1f, WorldModels.ACCENT_WARDEN_PHASE1[0] + boost),
                Math.min(1f, WorldModels.ACCENT_WARDEN_PHASE1[1] + 0.35f * boost),
                WorldModels.ACCENT_WARDEN_PHASE1[2],
                WorldModels.ACCENT_WARDEN_PHASE1[3]);
        }
    }

    private void prepareDronePose(Enemy enemy, float time) {
        float hoverY = 0.03f * MathUtils.sin(time * 5.5f + enemy.getX() * 2f);
        float tiltPitch = (enemy.getState() == Enemy.State.WINDUP
            || enemy.getState() == Enemy.State.LUNGING)
            ? -16f
            : (enemy.getState() == Enemy.State.PURSUING ? -7f : 0f);
        rootTransform.idt()
            .translate(enemy.getX(), hoverY, enemy.getZ())
            .rotate(Vector3.Y, -enemy.getYaw())
            .rotate(Vector3.X, tiltPitch);
    }

    private static float[] doorAccent(Door door) {
        if (door.isOpening()) return WorldModels.ACCENT_DOOR_OPEN;
        switch (door.getLockKind()) {
            case AMBER_KEY:
                return WorldModels.ACCENT_DOOR_AMBER;
            case CYAN_KEY:
                return WorldModels.ACCENT_DOOR_CYAN;
            case LOCKDOWN:
                return WorldModels.ACCENT_DOOR_LOCKDOWN;
            case BOSS:
                return WorldModels.ACCENT_DOOR_BOSS;
            case NONE:
            default:
                return WorldModels.ACCENT_DOOR_UNLOCKED;
        }
    }

    private static float[] pickupAccent(Pickup.Kind kind) {
        switch (kind) {
            case AMBER_KEYCARD:
                return WorldModels.ACCENT_KEYCARD_AMBER;
            case CYAN_KEYCARD:
                return WorldModels.ACCENT_KEYCARD_CYAN;
            default:
                return null;
        }
    }

    private Mesh pickupMesh(Pickup.Kind kind) {
        switch (kind) {
            case MEDKIT:
                return medkitMesh;
            case ARMOR:
                return armorMesh;
            case AMBER_KEYCARD:
            case CYAN_KEYCARD:
                return keycardMesh;
            case SCATTERGUN:
                return scattergunPickupMesh;
            case SHELLS:
                return shellsPickupMesh;
            case ENERGY_CELL:
            default:
                return energyCellMesh;
        }
    }

    private void addExitPad(GameMap map, float animTime) {
        if (!map.hasExit()) return;
        float pulse = 0.82f + 0.14f * MathUtils.sin(animTime * 4.5f);
        Decal pad = nextDecal(exitPad, 0.86f, 0.86f, true);
        pad.setPosition(map.getExitX(), FLOOR_GLOW_HEIGHT, map.getExitZ());
        pad.setRotation(Vector3.Y, Vector3.Z);
        pad.setColor(0.55f, 1f, 0.95f, pulse);
        decalBatch.add(pad);

        Decal beacon = nextDecal(orb, 0.42f * pulse, 0.62f, true);
        beacon.setPosition(map.getExitX(), 0.36f, map.getExitZ());
        beacon.lookAt(camera.position, camera.up);
        beacon.setColor(0.45f, 1f, 0.92f, 0.55f * pulse);
        decalBatch.add(beacon);
    }

    private void addPickupGlows(GameSimulation simulation) {
        for (Pickup pickup : simulation.pickups) {
            if (pickup.isCollected()) continue;
            Decal halo = nextDecal(orb, 0.42f, 0.42f, true);
            halo.setPosition(pickup.getX(), FLOOR_GLOW_HEIGHT, pickup.getZ());
            halo.setRotation(Vector3.Y, Vector3.Z);
            switch (pickup.getKind()) {
                case MEDKIT:
                    halo.setColor(1f, 0.35f, 0.4f, 0.4f);
                    break;
                case ARMOR:
                    halo.setColor(0.3f, 0.78f, 1f, 0.45f);
                    break;
                case AMBER_KEYCARD:
                    halo.setColor(1f, 0.74f, 0.18f, 0.52f);
                    break;
                case CYAN_KEYCARD:
                    halo.setColor(0.2f, 0.92f, 1f, 0.52f);
                    break;
                case SCATTERGUN:
                    halo.setColor(1f, 0.56f, 0.20f, 0.54f);
                    break;
                case SHELLS:
                    halo.setColor(0.95f, 0.34f, 0.24f, 0.48f);
                    break;
                case ENERGY_CELL:
                default:
                    halo.setColor(0.75f, 0.42f, 1f, 0.48f);
                    break;
            }
            decalBatch.add(halo);
        }
    }

    private void addConsoleGlows(GameSimulation simulation) {
        for (SecurityConsole console : simulation.consoles) {
            Decal halo = nextDecal(orb, 0.46f, 0.46f, true);
            halo.setPosition(console.getX(), FLOOR_GLOW_HEIGHT, console.getZ());
            halo.setRotation(Vector3.Y, Vector3.Z);
            if (console.isActivated()) halo.setColor(0.25f, 0.96f, 0.52f, 0.42f);
            else halo.setColor(1f, 0.28f, 0.22f, 0.38f);
            decalBatch.add(halo);
        }
    }

    private void addProjectiles(GameSimulation simulation) {
        for (BlastProjectile projectile : simulation.projectiles) {
            float meshScale = 0.85f + 0.55f * projectile.getCharge();
            float meshOuterRadius = BlastProjectile.RADIUS * meshScale;
            float baseSize = 0.26f + 0.20f * projectile.getCharge();
            scratch.set(camera.position).sub(projectile.position);
            float camDist = scratch.len();
            float pullForward = camDist > 1e-4f
                ? Math.min(meshOuterRadius + 0.02f, Math.max(0f, camDist - NEAR - 0.03f))
                : 0f;
            float decalDist = camDist - pullForward;
            float perspectiveScale = camDist > 1e-4f ? decalDist / camDist : 1f;
            float size = baseSize * perspectiveScale;
            if (camDist > 1e-4f) scratch.scl(pullForward / camDist).add(projectile.position);
            else scratch.set(projectile.position);
            Decal decal = nextDecal(orb, size, size, true);
            decal.setPosition(scratch);
            decal.lookAt(camera.position, camera.up);
            float visible = 1f - fogAt(projectile.position);
            float shimmer = 0.92f + 0.08f * MathUtils.sin(simulation.getTime() * 14f);
            decal.setColor(visible, visible, visible, visible * shimmer * 0.82f);
            decalBatch.add(decal);
        }
    }

    private void addEnemyTelegraphsAndBolts(GameSimulation simulation) {
        for (Enemy enemy : simulation.enemies) {
            if (enemy.isDestroyed()) continue;
            float windup = enemy.getWindupProgress();
            if (windup <= 0f) continue;
            switch (enemy.getKind()) {
                case TURRET:
                    prepareTurretPose(enemy);
                    turretRig.evaluateSocketWorld("telegraph", turretRotations, rootTransform, scratch);
                    break;
                case GUARD:
                    prepareGuardPose(enemy);
                    guardRig.evaluateSocketWorld("telegraph", guardRotations, rootTransform, scratch);
                    break;
                case WARDEN:
                    prepareWardenPose(enemy);
                    wardenRig.evaluateSocketWorld("telegraph", guardRotations, rootTransform, scratch);
                    break;
                case DRONE:
                default:
                    prepareDronePose(enemy, simulation.getTime());
                    droneRig.evaluateSocketWorld("telegraph", null, rootTransform, scratch);
                    break;
            }
            float size = (enemy.getKind() == Enemy.Kind.WARDEN ? 0.24f : 0.14f)
                + (enemy.getKind() == Enemy.Kind.WARDEN ? 0.24f : 0.18f) * windup;
            Decal flare = nextDecal(orb, size, size, true);
            flare.setPosition(scratch);
            flare.lookAt(camera.position, camera.up);
            if (enemy.getKind() == Enemy.Kind.WARDEN && enemy.isOverdrive()) {
                flare.setColor(0.36f, 0.72f + 0.25f * windup, 1f, 0.55f + 0.40f * windup);
            } else {
                flare.setColor(1f, 0.35f + 0.25f * windup, 0.18f, 0.45f + 0.45f * windup);
            }
            decalBatch.add(flare);
        }
        for (EnemyProjectile bolt : simulation.enemyProjectiles) {
            Decal halo = nextDecal(orb, 0.24f, 0.24f, true);
            halo.setPosition(bolt.position);
            halo.lookAt(camera.position, camera.up);
            halo.setColor(1f, 0.28f, 0.32f, 0.95f);
            decalBatch.add(halo);
        }
    }

    private void addEffects(GameSimulation simulation) {
        for (Effect effect : simulation.effects) {
            float progress = effect.getProgress();
            switch (effect.type) {
                case IMPACT: {
                    Decal mark = nextDecal(scorch, effect.size, effect.size, false);
                    mark.setPosition(effect.position);
                    // Lie flat on the surface; a floor or ceiling mark needs an up vector that is not its normal.
                    mark.setRotation(effect.normal, Math.abs(effect.normal.y) > 0.5f ? Vector3.Z : Vector3.Y);
                    float visible = 1f - fogAt(effect.position);
                    mark.setColor(visible, visible, visible, Math.min(1f, (1f - progress) * 5f));
                    decalBatch.add(mark);
                    if (effect.getAge() < IMPACT_SPARK_TIME) {
                        float fade = 1f - effect.getAge() / IMPACT_SPARK_TIME;
                        addGlow(spark, scratch.set(effect.normal).scl(0.03f).add(effect.position),
                            effect.size * (1.6f + 1.4f * fade), fade);
                    }
                    break;
                }
                case SPARK:
                    addGlow(spark, effect.position, effect.size * (1f + progress), 1f - progress);
                    break;
                case EXPLOSION:
                default: {
                    float grown = 0.2f + 0.8f * (1f - (1f - progress) * (1f - progress));
                    float alpha = 1f - progress * progress;
                    addGlow(explosion, effect.position, Math.min(effect.size * 2f, MAX_FIREBALL_SIZE) * grown, alpha);
                    if (effect.size >= MIN_FLOOR_GLOW_RADIUS) {
                        float span = effect.size * 2f * grown;
                        Decal glow = nextDecal(explosion, span, span, true);
                        glow.setPosition(effect.position.x, FLOOR_GLOW_HEIGHT, effect.position.z);
                        glow.setRotation(Vector3.Y, Vector3.Z);
                        glow.setColor(1f, 1f, 1f, alpha * 0.7f);
                        decalBatch.add(glow);
                    }
                    break;
                }
            }
        }
    }

    private void addGlow(TextureRegion region, Vector3 position, float size, float alpha) {
        Decal decal = nextDecal(region, size, size, true);
        decal.setPosition(position);
        decal.lookAt(camera.position, camera.up);
        decal.setColor(1f, 1f, 1f, alpha);
        decalBatch.add(decal);
    }

    private Decal nextDecal(TextureRegion region, float width, float height, boolean additive) {
        if (decalsUsed == decals.size) decals.add(Decal.newDecal(width, height, region, true));
        Decal decal = decals.get(decalsUsed++);
        decal.setTextureRegion(region);
        decal.setDimensions(width, height);
        decal.setBlending(GL20.GL_SRC_ALPHA, additive ? GL20.GL_ONE : GL20.GL_ONE_MINUS_SRC_ALPHA);
        return decal;
    }

    private float fogAt(Vector3 position) {
        return MathUtils.clamp((position.dst(camera.position) - FOG_START) / (FOG_END - FOG_START), 0f, 1f);
    }

    @Override
    public void dispose() {
        for (Surface surface : surfaces) surface.mesh.dispose();
        surfaces.clear();
        canisterMesh.dispose();
        ringBoardMesh.dispose();
        blastOrbMesh.dispose();
        medkitMesh.dispose();
        armorMesh.dispose();
        energyCellMesh.dispose();
        scattergunPickupMesh.dispose();
        shellsPickupMesh.dispose();
        turretBaseMesh.dispose();
        turretHeadMesh.dispose();
        turretBarrelsMesh.dispose();
        droneMesh.dispose();
        guardPelvisMesh.dispose();
        guardLegLeftMesh.dispose();
        guardLegRightMesh.dispose();
        guardTorsoMesh.dispose();
        guardArmLeftMesh.dispose();
        guardArmRightMesh.dispose();
        wardenPelvisMesh.dispose();
        wardenLegLeftMesh.dispose();
        wardenLegRightMesh.dispose();
        wardenTorsoMesh.dispose();
        wardenArmLeftMesh.dispose();
        wardenArmRightMesh.dispose();
        enemyBoltMesh.dispose();
        doorMesh.dispose();
        hazardGrateMesh.dispose();
        keycardMesh.dispose();
        consoleMesh.dispose();
        shader.dispose();
        modelShader.dispose();
        decalBatch.dispose();
        groupStrategy.dispose();
    }
}

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
import com.badlogic.gdx.math.Matrix4;
import com.badlogic.gdx.math.Vector3;
import com.badlogic.gdx.utils.Array;
import com.badlogic.gdx.utils.Disposable;
import com.badlogic.gdx.utils.GdxRuntimeException;
import com.badlogic.gdx.utils.ScreenUtils;
import io.github.brantunger.gamex.GameSimulation;
import io.github.brantunger.gamex.player.Player;
import io.github.brantunger.gamex.settings.GameSettings;
import io.github.brantunger.gamex.weapon.BlastProjectile;
import io.github.brantunger.gamex.weapon.EnemyProjectile;
import io.github.brantunger.gamex.world.Effect;
import io.github.brantunger.gamex.world.Enemy;
import io.github.brantunger.gamex.world.GameMap;
import io.github.brantunger.gamex.world.MapGeometry;
import io.github.brantunger.gamex.world.Pickup;
import io.github.brantunger.gamex.world.Target;
import io.github.brantunger.gamex.world.WorldModels;

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
            + "uniform mat4 u_projViewTrans;\n"
            + "uniform mat4 u_model;\n"
            + "uniform vec3 u_cameraPosition;\n"
            + "uniform vec2 u_fogRange;\n"
            + "uniform vec3 u_fogColour;\n"
            + "uniform float u_light;\n"
            + "uniform float u_hitFlash;\n"
            + "varying vec3 v_colour;\n"
            + "void main() {\n"
            + "    vec4 worldPos = u_model * vec4(a_position, 1.0);\n"
            + "    vec3 worldNormal = normalize((u_model * vec4(a_normal, 0.0)).xyz);\n"
            + "    vec3 weight = worldNormal * worldNormal;\n"
            + "    float shade = weight.z + weight.x * 0.72 + weight.y * (worldNormal.y > 0.0 ? 0.9 : 0.62);\n"
            + "    vec3 lightDir = normalize(vec3(-0.3, 0.9, 0.4));\n"
            + "    float key = 0.65 + 0.55 * max(dot(worldNormal, lightDir), 0.0);\n"
            + "    vec3 delta = u_cameraPosition - worldPos.xyz;\n"
            + "    float range = length(delta);\n"
            + "    vec3 toCamera = range > 1e-4 ? delta / range : vec3(0.0, 0.0, 1.0);\n"
            + "    vec3 halfVec = normalize(lightDir + toCamera);\n"
            + "    float sheen = a_gloss * pow(max(dot(worldNormal, halfVec), 0.0), 12.0) * 0.35;\n"
            + "    float rim = pow(1.0 - max(dot(worldNormal, toCamera), 0.0), 2.5) * 0.12;\n"
            + "    vec3 lit = (a_colour * shade * key + vec3(sheen + rim)) * u_light;\n"
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
    private final Mesh turretBaseMesh;
    private final Mesh turretHeadMesh;
    private final Mesh droneMesh;
    private final Mesh guardBaseMesh;
    private final Mesh guardHeadMesh;
    private final Mesh enemyBoltMesh;
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

        canisterMesh = ModelMesh.loadMesh(WorldModels.CANISTER_PATH, "canister", WorldModels::vertices);
        ringBoardMesh = ModelMesh.loadMesh(WorldModels.RING_BOARD_PATH, "ring board", WorldModels::vertices);
        blastOrbMesh = ModelMesh.loadMesh(WorldModels.BLAST_ORB_PATH, "blast orb", WorldModels::vertices);
        medkitMesh = ModelMesh.loadMesh(WorldModels.MEDKIT_PATH, "medkit", WorldModels::vertices);
        armorMesh = ModelMesh.loadMesh(WorldModels.ARMOR_PATH, "armor", WorldModels::vertices);
        energyCellMesh = ModelMesh.loadMesh(WorldModels.ENERGY_CELL_PATH, "energy cell", WorldModels::vertices);
        turretBaseMesh = ModelMesh.loadMesh(WorldModels.TURRET_PATH, "turret base",
            m -> WorldModels.verticesForGroupPrefix(m, "base_"));
        turretHeadMesh = ModelMesh.loadMesh(WorldModels.TURRET_PATH, "turret head",
            m -> WorldModels.verticesForGroupPrefix(m, "head_"));
        droneMesh = ModelMesh.loadMesh(WorldModels.DRONE_PATH, "drone", WorldModels::vertices);
        guardBaseMesh = ModelMesh.loadMesh(WorldModels.GUARD_PATH, "guard base",
            m -> WorldModels.verticesForGroupPrefix(m, "base_"));
        guardHeadMesh = ModelMesh.loadMesh(WorldModels.GUARD_PATH, "guard head",
            m -> WorldModels.verticesForGroupPrefix(m, "head_"));
        enemyBoltMesh = ModelMesh.loadMesh(WorldModels.ENEMY_BOLT_PATH, "enemy bolt", WorldModels::vertices);

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

        beginWorldPass(simulation.pistol.isFlashVisible() ? 1.3f : 1f);
        renderWorldModels(simulation, simulation.pistol.isFlashVisible() ? 1.3f : 1f);

        // Billboards are seen from both sides.
        Gdx.gl.glDisable(GL20.GL_CULL_FACE);
        decalsUsed = 0;
        addExitPad(simulation.map, simulation.getTime());
        addPickupGlows(simulation);
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
        for (Target target : simulation.targets) {
            if (target.isDestroyed()) continue;
            float dx = camera.position.x - target.getX();
            float dz = camera.position.z - target.getZ();
            if (dx * dx + dz * dz < 1e-6f) dz = 1f;
            float yaw = MathUtils.atan2(dx, dz) * MathUtils.radiansToDegrees + 180f;
            modelTransform.idt()
                .translate(target.getX(), 0f, target.getZ())
                .rotate(Vector3.Y, yaw);
            modelShader.setUniformMatrix("u_model", modelTransform);
            modelShader.setUniformf("u_hitFlash", target.getHitFlash());
            Mesh mesh = target.getKind() == Target.Kind.CANISTER ? canisterMesh : ringBoardMesh;
            mesh.render(modelShader, GL20.GL_TRIANGLES);
        }

        // Hostile 3D enemies with articulated parts and wind-up luminance.
        for (Enemy enemy : simulation.enemies) {
            if (enemy.isDestroyed()) continue;
            float windup = enemy.getWindupProgress();
            modelShader.setUniformf("u_light", roomLight + 0.45f * windup);
            modelShader.setUniformf("u_hitFlash", enemy.getHitFlash());
            switch (enemy.getKind()) {
                case TURRET:
                    modelTransform.idt().translate(enemy.getX(), 0f, enemy.getZ());
                    modelShader.setUniformMatrix("u_model", modelTransform);
                    turretBaseMesh.render(modelShader, GL20.GL_TRIANGLES);

                    modelTransform.idt()
                        .translate(enemy.getX(), 0f, enemy.getZ())
                        .rotate(Vector3.Y, -enemy.getYaw());
                    modelShader.setUniformMatrix("u_model", modelTransform);
                    turretHeadMesh.render(modelShader, GL20.GL_TRIANGLES);
                    break;
                case GUARD:
                    float bobY = Math.abs(MathUtils.sin(enemy.getWalkPhase())) * 0.022f;
                    modelTransform.idt()
                        .translate(enemy.getX(), 0f, enemy.getZ())
                        .rotate(Vector3.Y, -enemy.getYaw());
                    modelShader.setUniformMatrix("u_model", modelTransform);
                    guardBaseMesh.render(modelShader, GL20.GL_TRIANGLES);

                    modelTransform.idt()
                        .translate(enemy.getX(), bobY, enemy.getZ())
                        .rotate(Vector3.Y, -enemy.getYaw());
                    modelShader.setUniformMatrix("u_model", modelTransform);
                    guardHeadMesh.render(modelShader, GL20.GL_TRIANGLES);
                    break;
                case DRONE:
                default:
                    float hoverY = 0.03f * MathUtils.sin(simulation.getTime() * 5.5f + enemy.getX() * 2f);
                    float tiltPitch = (enemy.getState() == Enemy.State.WINDUP
                        || enemy.getState() == Enemy.State.LUNGING)
                        ? -16f
                        : (enemy.getState() == Enemy.State.PURSUING ? -7f : 0f);
                    modelTransform.idt()
                        .translate(enemy.getX(), hoverY, enemy.getZ())
                        .rotate(Vector3.Y, -enemy.getYaw())
                        .rotate(Vector3.X, tiltPitch);
                    modelShader.setUniformMatrix("u_model", modelTransform);
                    droneMesh.render(modelShader, GL20.GL_TRIANGLES);
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
            modelShader.setUniformMatrix("u_model", modelTransform);
            Mesh mesh = pickupMesh(pickup.getKind());
            mesh.render(modelShader, GL20.GL_TRIANGLES);
        }

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
            modelShader.setUniformMatrix("u_model", modelTransform);
            blastOrbMesh.render(modelShader, GL20.GL_TRIANGLES);
        }

        modelShader.setUniformf("u_light", 1.4f);
        float boltSpin = simulation.getTime() * 220f;
        for (EnemyProjectile bolt : simulation.enemyProjectiles) {
            modelTransform.idt()
                .translate(bolt.position)
                .rotate(Vector3.Z, 26f)
                .rotate(Vector3.Y, boltSpin);
            modelShader.setUniformMatrix("u_model", modelTransform);
            enemyBoltMesh.render(modelShader, GL20.GL_TRIANGLES);
        }
    }

    private Mesh pickupMesh(Pickup.Kind kind) {
        switch (kind) {
            case MEDKIT:
                return medkitMesh;
            case ARMOR:
                return armorMesh;
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
                case ENERGY_CELL:
                default:
                    halo.setColor(0.75f, 0.42f, 1f, 0.48f);
                    break;
            }
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
            double rad = Math.toRadians(enemy.getYaw());
            float fx = (float) Math.sin(rad);
            float fz = (float) -Math.cos(rad);
            float rx = (float) Math.cos(rad);
            float rz = (float) Math.sin(rad);
            float gx = enemy.getX() + fx * 0.18f;
            float gy = 0.44f;
            float gz = enemy.getZ() + fz * 0.18f;
            if (enemy.getKind() == Enemy.Kind.GUARD) {
                gx = enemy.getX() + fx * 0.17f + rx * 0.162f;
                gy = 0.46f;
                gz = enemy.getZ() + fz * 0.17f + rz * 0.162f;
            } else if (enemy.getKind() == Enemy.Kind.DRONE) {
                gy = 0.34f;
            }
            float size = 0.14f + 0.18f * windup;
            Decal flare = nextDecal(orb, size, size, true);
            flare.setPosition(gx, gy, gz);
            flare.lookAt(camera.position, camera.up);
            flare.setColor(1f, 0.35f + 0.25f * windup, 0.18f, 0.45f + 0.45f * windup);
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
        turretBaseMesh.dispose();
        turretHeadMesh.dispose();
        droneMesh.dispose();
        guardBaseMesh.dispose();
        guardHeadMesh.dispose();
        enemyBoltMesh.dispose();
        shader.dispose();
        modelShader.dispose();
        decalBatch.dispose();
        groupStrategy.dispose();
    }
}

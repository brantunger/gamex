package io.github.brantunger.gamex.tools;

import com.badlogic.gdx.math.Matrix4;
import com.badlogic.gdx.math.Vector3;
import io.github.brantunger.gamex.model.ModelRig;
import io.github.brantunger.gamex.model.ModelVertices;
import io.github.brantunger.gamex.model.ObjModel;
import io.github.brantunger.gamex.world.WorldModels;
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.Collections;
import java.util.HashMap;
import java.util.Map;
import java.util.function.Supplier;
import java.util.regex.Pattern;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class MeshBuilderTest {

    @Test
    void leadingRootGeometryRemainsAssignedToRootWhenChildPartsAreDeclared() {
        MeshBuilder builder = new MeshBuilder()
            .comment("Mixed root and articulated child mesh")
            .box("hull", "bot_armor", -0.2f, 0.2f, 0f, 0.3f, -0.2f, 0.2f)
            .part("turret", "root", 0f, 0.3f, 0f)
            .box("gun", "bot_dark", -0.1f, 0.1f, 0.3f, 0.5f, -0.3f, 0.1f);

        String obj = builder.toObjString();
        assertTrue(obj.contains("# @part turret parent=root pivot=0.000000,0.300000,0.000000\n"), obj);
        assertTrue(obj.contains("# @part root\ng hull\n"), obj);
        assertTrue(obj.contains("# @part turret\ng gun\n"), obj);
        assertFalse(obj.contains("# @part root parent="), obj);

        ObjModel model = ObjModel.parse(obj);
        assertEquals(24, model.getTriangleCount());
        for (int t = 0; t < 12; t++) {
            assertEquals("hull", model.getGroup(t));
            assertEquals(ObjModel.ROOT_PART, model.getPart(t));
        }
        for (int t = 12; t < 24; t++) {
            assertEquals("gun", model.getGroup(t));
            assertEquals("turret", model.getPart(t));
        }

        ModelVertices.IndexedMesh rootMesh = WorldModels.packPartIndexed(model, ObjModel.ROOT_PART);
        ModelVertices.IndexedMesh turretMesh = WorldModels.packPartIndexed(model, "turret");
        assertEquals(36, rootMesh.indexCount());
        assertEquals(36, turretMesh.indexCount());
    }

    @Test
    void switchingBackToRootEmitsPlainPartRootDirectiveAndPreservesRigHierarchy() {
        MeshBuilder builder = new MeshBuilder()
            .part("base", "root", 0f, 0f, 0f)
            .part("arm", "base", 0f, 0.4f, 0f)
            .socket("muzzle", "arm", 0f, 0.4f, -0.25f);

        builder.usePart("arm")
            .box("arm_mesh", "bot_dark", -0.05f, 0.05f, 0.4f, 0.5f, -0.25f, 0f);
        builder.usePart("root")
            .box("plinth_mesh", "bot_armor", -0.2f, 0.2f, 0f, 0.1f, -0.2f, 0.2f);
        builder.usePart("base")
            .box("column_mesh", "bot_trim", -0.1f, 0.1f, 0.1f, 0.4f, -0.1f, 0.1f);
        builder.usePart(null)
            .box("foot_mesh", "bot_armor", -0.25f, 0.25f, 0f, 0.04f, -0.25f, 0.25f);

        String obj = builder.toObjString();
        assertFalse(obj.contains("# @part root parent="), obj);
        assertFalse(obj.contains("# @part root pivot="), obj);
        assertTrue(obj.contains("# @part root\ng plinth_mesh\n"), obj);
        assertTrue(obj.contains("# @part root\ng foot_mesh\n"), obj);

        ObjModel model = ObjModel.parse(obj);
        assertEquals(48, model.getTriangleCount());
        for (int t = 0; t < 12; t++) assertEquals("arm", model.getPart(t));
        for (int t = 12; t < 24; t++) assertEquals(ObjModel.ROOT_PART, model.getPart(t));
        for (int t = 24; t < 36; t++) assertEquals("base", model.getPart(t));
        for (int t = 36; t < 48; t++) assertEquals(ObjModel.ROOT_PART, model.getPart(t));

        assertNull(model.getPartSpec(ObjModel.ROOT_PART).parent);
        assertNull(model.getPartSpec("base").parent);
        assertEquals("base", model.getPartSpec("arm").parent);

        ModelRig rig = new ModelRig(model);
        Map<String, Matrix4> local = Collections.singletonMap("arm", new Matrix4().rotate(Vector3.X, 90f));
        Vector3 muzzle = rig.evaluateSocketWorld("muzzle", local, new Matrix4(), new Vector3());
        assertEquals(0f, muzzle.x, 1e-5f);
        assertEquals(0.65f, muzzle.y, 1e-5f);
        assertEquals(0f, muzzle.z, 1e-5f);
    }

    @Test
    void partlessModelOmitsPartDirectivesCompletely() {
        MeshBuilder builder = new MeshBuilder()
            .comment("Static prop")
            .accent("rail_core")
            .socket("telegraph", "root", 0f, 0.25f, -0.15f)
            .box("core", "rail_core", -0.1f, 0.1f, 0f, 0.2f, -0.1f, 0.1f);

        String obj = builder.toObjString();
        assertFalse(obj.contains("# @part"), obj);

        ObjModel model = ObjModel.parse(obj);
        assertEquals(12, model.getTriangleCount());
        assertTrue(model.isAccentMaterial("rail_core"));
        assertNotNull(model.getSocket("telegraph"));
        for (int t = 0; t < model.getTriangleCount(); t++) {
            assertEquals(ObjModel.ROOT_PART, model.getPart(t));
        }
    }

    @Test
    void undeclaredUsePartHoistsDefaultHeaderDeclarationAndSelectsPart() {
        MeshBuilder builder = new MeshBuilder();
        builder.usePart("panel")
            .box("panel_geo", "bot_trim", -0.1f, 0.1f, 0f, 0.2f, -0.05f, 0.05f);
        builder.usePart("root")
            .box("frame_geo", "bot_armor", -0.2f, 0.2f, 0f, 0.3f, -0.1f, 0.1f);

        String obj = builder.toObjString();
        ObjModel model = ObjModel.parse(obj);
        assertNotNull(model.getPartSpec("panel"));
        assertNull(model.getPartSpec("panel").parent);
        for (int t = 0; t < 12; t++) assertEquals("panel", model.getPart(t));
        for (int t = 12; t < 24; t++) assertEquals(ObjModel.ROOT_PART, model.getPart(t));
    }

    @Test
    void rejectsInvalidPartDeclarationsAndTransformStackUnderflow() {
        MeshBuilder builder = new MeshBuilder();
        assertThrows(IllegalArgumentException.class, () -> builder.part("root", "root", 0f, 0f, 0f));
        assertThrows(IllegalArgumentException.class, () -> builder.part("", "root", 0f, 0f, 0f));
        assertThrows(IllegalArgumentException.class, () -> builder.part("   ", "root", 0f, 0f, 0f));
        assertThrows(IllegalArgumentException.class, () -> builder.part(null, "root", 0f, 0f, 0f));
        assertThrows(IllegalStateException.class, builder::popTransform);
    }

    @Test
    void allSculptedGeneratorsSerializeValidStrictObjModels() {
        for (Map.Entry<String, Supplier<MeshBuilder>> entry : GenerateAllModels.generators().entrySet()) {
            String name = entry.getKey();
            String obj = entry.getValue().get().toObjString();
            assertFalse(obj.contains("# @part root parent="), name + " emitted invalid root redefinition");
            ObjModel model = ObjModel.parse(obj);
            assertTrue(model.getTriangleCount() > 0, name + " produced an empty mesh");
        }
    }

    @Test
    void frustumYCylinderZAndCylinderXSwapRadiiWhenBoundsAreReversed() {
        for (boolean smooth : new boolean[] {false, true}) {
            ObjModel forwardY = ObjModel.parse(new MeshBuilder()
                .frustumY("fy", "bot_armor", 0.25f, -0.15f, 0.10f, 0.70f, 0.30f, 0.12f, 10, smooth)
                .toObjString());
            ObjModel swappedY = ObjModel.parse(new MeshBuilder()
                .frustumY("fy", "bot_armor", 0.25f, -0.15f, 0.70f, 0.10f, 0.12f, 0.30f, 10, smooth)
                .toObjString());
            assertClosedOutwardManifold(forwardY, "forwardY smooth=" + smooth);
            assertClosedOutwardManifold(swappedY, "swappedY smooth=" + smooth);
            assertEquals(0.12f, maxRadiusAtAxis(swappedY, 1, 0.70f, 0.25f, -0.15f), 1e-5f);
            assertEquals(0.30f, maxRadiusAtAxis(swappedY, 1, 0.10f, 0.25f, -0.15f), 1e-5f);
            assertEquals(signedVolume(forwardY), signedVolume(swappedY), 1e-6);

            // Reversed bounds with distinct radii (0.70 -> r0=0.35, 0.10 -> r1=0.08):
            ObjModel invertedCallY = ObjModel.parse(new MeshBuilder()
                .frustumY("fy_inv", "bot_armor", 0f, 0f, 0.70f, 0.10f, 0.35f, 0.08f, 8, smooth)
                .toObjString());
            assertClosedOutwardManifold(invertedCallY, "invertedCallY smooth=" + smooth);
            assertEquals(0.35f, maxRadiusAtAxis(invertedCallY, 1, 0.70f, 0f, 0f), 1e-5f);
            assertEquals(0.08f, maxRadiusAtAxis(invertedCallY, 1, 0.10f, 0f, 0f), 1e-5f);

            ObjModel swappedZ = ObjModel.parse(new MeshBuilder()
                .cylinderZ("cz", "bot_armor", 0.20f, -0.30f, 0.28f, 0.09f, 0.50f, -0.40f, 10, smooth)
                .toObjString());
            assertClosedOutwardManifold(swappedZ, "swappedZ smooth=" + smooth);
            assertEquals(0.28f, maxRadiusAtAxis(swappedZ, 2, 0.50f, 0.20f, -0.30f), 1e-5f);
            assertEquals(0.09f, maxRadiusAtAxis(swappedZ, 2, -0.40f, 0.20f, -0.30f), 1e-5f);

            ObjModel swappedX = ObjModel.parse(new MeshBuilder()
                .cylinderX("cx", "bot_armor", -0.10f, 0.40f, 0.26f, 0.07f, 0.60f, -0.20f, 10, smooth)
                .toObjString());
            assertClosedOutwardManifold(swappedX, "swappedX smooth=" + smooth);
            assertEquals(0.26f, maxRadiusAtAxis(swappedX, 0, 0.60f, -0.10f, 0.40f), 1e-5f);
            assertEquals(0.07f, maxRadiusAtAxis(swappedX, 0, -0.20f, -0.10f, 0.40f), 1e-5f);
        }
    }

    @Test
    void zeroRadiusConesAreClosedTwoManifoldsWithoutDegenerateFacesOrZeroRadiusCaps() {
        int sides = 8;
        for (boolean smooth : new boolean[] {false, true}) {
            int expectedTris = smooth ? 2 * sides : 2 * sides - 2;
            int expectedVerts = smooth ? sides + 2 : sides + 1;

            // Top-apex cone (r0 > 0 at y0, r1 == 0 at y1) and swapped equivalent.
            MeshBuilder topConeBuilder = new MeshBuilder()
                .frustumY("cone_top", "bot_armor", 0.1f, -0.2f, 0.0f, 0.6f, 0.25f, 0f, sides, smooth);
            assertEquals(expectedVerts, topConeBuilder.getVertexCount());
            ObjModel topCone = ObjModel.parse(topConeBuilder.toObjString());
            assertEquals(expectedTris, topCone.getTriangleCount());
            assertClosedOutwardManifold(topCone, "topCone smooth=" + smooth);
            assertEquals(0.25f, maxRadiusAtAxis(topCone, 1, 0.0f, 0.1f, -0.2f), 1e-5f);
            assertEquals(0.0f, maxRadiusAtAxis(topCone, 1, 0.6f, 0.1f, -0.2f), 1e-5f);

            ObjModel topConeSwapped = ObjModel.parse(new MeshBuilder()
                .frustumY("cone_top_swap", "bot_armor", 0.1f, -0.2f, 0.6f, 0.0f, 0f, 0.25f, sides, smooth)
                .toObjString());
            assertEquals(expectedTris, topConeSwapped.getTriangleCount());
            assertClosedOutwardManifold(topConeSwapped, "topConeSwapped smooth=" + smooth);
            assertEquals(0.25f, maxRadiusAtAxis(topConeSwapped, 1, 0.0f, 0.1f, -0.2f), 1e-5f);
            assertEquals(0.0f, maxRadiusAtAxis(topConeSwapped, 1, 0.6f, 0.1f, -0.2f), 1e-5f);

            // Bottom-apex cone (r0 == 0 at y0, r1 > 0 at y1) and swapped equivalent.
            MeshBuilder bottomConeBuilder = new MeshBuilder()
                .frustumY("cone_bot", "bot_armor", -0.1f, 0.3f, -0.2f, 0.5f, 0f, 0.20f, sides, smooth);
            assertEquals(expectedVerts, bottomConeBuilder.getVertexCount());
            ObjModel bottomCone = ObjModel.parse(bottomConeBuilder.toObjString());
            assertEquals(expectedTris, bottomCone.getTriangleCount());
            assertClosedOutwardManifold(bottomCone, "bottomCone smooth=" + smooth);
            assertEquals(0.0f, maxRadiusAtAxis(bottomCone, 1, -0.2f, -0.1f, 0.3f), 1e-5f);
            assertEquals(0.20f, maxRadiusAtAxis(bottomCone, 1, 0.5f, -0.1f, 0.3f), 1e-5f);

            // Cones along Z and X (both forward and reversed bounds) + mirrored transform check.
            ObjModel coneZ = ObjModel.parse(new MeshBuilder()
                .cylinderZ("cone_z", "bot_armor", 0.15f, 0.25f, 0f, 0.18f, 0.40f, -0.20f, sides, smooth)
                .toObjString());
            assertEquals(expectedTris, coneZ.getTriangleCount());
            assertClosedOutwardManifold(coneZ, "coneZ smooth=" + smooth);
            assertEquals(0.0f, maxRadiusAtAxis(coneZ, 2, 0.40f, 0.15f, 0.25f), 1e-5f);
            assertEquals(0.18f, maxRadiusAtAxis(coneZ, 2, -0.20f, 0.15f, 0.25f), 1e-5f);

            ObjModel coneX = ObjModel.parse(new MeshBuilder()
                .cylinderX("cone_x", "bot_armor", -0.20f, 0.10f, 0.22f, 0f, 0.30f, -0.30f, sides, smooth)
                .toObjString());
            assertEquals(expectedTris, coneX.getTriangleCount());
            assertClosedOutwardManifold(coneX, "coneX smooth=" + smooth);
            assertEquals(0.22f, maxRadiusAtAxis(coneX, 0, 0.30f, -0.20f, 0.10f), 1e-5f);
            assertEquals(0.0f, maxRadiusAtAxis(coneX, 0, -0.30f, -0.20f, 0.10f), 1e-5f);

            ObjModel mirroredCone = ObjModel.parse(new MeshBuilder()
                .pushTransform()
                .scale(-1f, 1f, 1f)
                .frustumY("cone_mirror", "bot_armor", 0f, 0f, 0f, 0.4f, 0.2f, 0f, sides, smooth)
                .popTransform()
                .toObjString());
            assertClosedOutwardManifold(mirroredCone, "mirroredCone smooth=" + smooth);
        }
    }

    @Test
    void rejectsNegativeAllZeroNonFiniteOrDegenerateFrustumArgumentsWithoutLeakingTransformStack() {
        MeshBuilder b = new MeshBuilder();
        assertThrows(IllegalArgumentException.class,
            () -> b.frustumY("g", "m", 0f, 0f, 0f, 1f, -0.1f, 0.2f, 8, false));
        assertThrows(IllegalArgumentException.class,
            () -> b.frustumY("g", "m", 0f, 0f, 0f, 1f, 0.2f, -0.1f, 8, true));
        assertThrows(IllegalArgumentException.class,
            () -> b.frustumY("g", "m", 0f, 0f, 0f, 1f, 0f, 0f, 8, false));
        assertThrows(IllegalArgumentException.class,
            () -> b.frustumY("g", "m", 0f, 0f, 0f, 1f, Float.NaN, 0.2f, 8, false));
        assertThrows(IllegalArgumentException.class,
            () -> b.frustumY("g", "m", 0f, 0f, 0f, 1f, 0.2f, Float.POSITIVE_INFINITY, 8, false));
        assertThrows(IllegalArgumentException.class,
            () -> b.frustumY("g", "m", 0f, 0f, 0.5f, 0.5f, 0.2f, 0.2f, 8, false));
        assertThrows(IllegalArgumentException.class,
            () -> b.frustumY("g", "m", 0f, 0f, 0f, 1f, 0.2f, 0.2f, 2, false));

        assertThrows(IllegalArgumentException.class,
            () -> b.cylinderZ("g", "m", 0f, 0f, 0f, 0f, 0f, 1f, 8, false));
        assertThrows(IllegalArgumentException.class,
            () -> b.cylinderX("g", "m", 0f, 0f, -0.1f, 0.2f, 0f, 1f, 8, false));
        assertThrows(IllegalArgumentException.class,
            () -> b.cylinderZ("g", "m", Float.NaN, 0f, 0.1f, 0.1f, 0f, 1f, 8, false));
        assertThrows(IllegalArgumentException.class,
            () -> b.cylinderX("g", "m", 0f, Float.NEGATIVE_INFINITY, 0.1f, 0.1f, 0f, 1f, 8, false));
        // Transform stack must still be at depth 1 (root) after failed cylinderZ/cylinderX calls.
        assertThrows(IllegalStateException.class, b::popTransform);
    }

    @Test
    void gradleScriptsDeclareGeneratorOutputsAndConsumerMustRunAfterOrdering() throws IOException {
        String toolsScript = readProjectFile("tools", "build.gradle");
        String coreScript = readProjectFile("core", "build.gradle");
        String desktopScript = readProjectFile("desktop", "build.gradle");

        assertTrue(Pattern.compile(
            "tasks\\.register\\(\\s*['\"]generateModels['\"][\\s\\S]*?outputs\\.dir\\(\\s*rootProject\\.file\\(\\s*['\"]assets/models['\"]\\s*\\)\\s*\\)")
            .matcher(toolsScript).find(), "tools:generateModels must declare outputs.dir('assets/models')");
        assertTrue(Pattern.compile(
            "tasks\\.register\\(\\s*['\"]generateSounds['\"][\\s\\S]*?outputs\\.dir\\(\\s*rootProject\\.file\\(\\s*['\"]assets/sounds['\"]\\s*\\)\\s*\\)")
            .matcher(toolsScript).find(), "tools:generateSounds must declare outputs.dir('assets/sounds')");

        for (String scriptName : new String[] {"tools", "core", "desktop"}) {
            String script = "tools".equals(scriptName) ? toolsScript
                : ("core".equals(scriptName) ? coreScript : desktopScript);
            assertFalse(script.contains("mustRunBefore"), scriptName + "/build.gradle must not use invalid mustRunBefore");
            assertTrue(script.contains("mustRunAfter(':tools:generateModels', ':tools:generateSounds')")
                    || script.contains("mustRunAfter(\":tools:generateModels\", \":tools:generateSounds\")"),
                scriptName + "/build.gradle must order asset consumers after generateModels and generateSounds");
        }
        assertTrue(Pattern.compile(
            "tasks\\.register\\(\\s*['\"]generateAssetList['\"][\\s\\S]*?mustRunAfter\\(\\s*['\"]:tools:generateModels['\"]\\s*,\\s*['\"]:tools:generateSounds['\"]\\s*\\)")
            .matcher(desktopScript).find(), "desktop:generateAssetList mustRunAfter generateModels and generateSounds");
        assertTrue(desktopScript.contains("processResources.mustRunAfter(':tools:generateModels', ':tools:generateSounds')")
                || Pattern.compile("processResources[\\s\\S]*?mustRunAfter\\(\\s*['\"]:tools:generateModels['\"]")
                    .matcher(desktopScript).find(),
            "desktop:processResources mustRunAfter generateModels and generateSounds");
    }

    @Test
    void gradleScriptsDeclareTheTextureGeneratorOutputAndOrderItsConsumersAfterIt() throws IOException {
        String toolsScript = readProjectFile("tools", "build.gradle");
        String coreScript = readProjectFile("core", "build.gradle");
        String desktopScript = readProjectFile("desktop", "build.gradle");

        assertTrue(Pattern.compile(
            "tasks\\.register\\(\\s*'generateTextures'[\\s\\S]*?outputs\\.dir\\(\\s*rootProject\\.file\\(\\s*'assets/textures'\\s*\\)\\s*\\)")
            .matcher(toolsScript).find(), "tools:generateTextures must declare outputs.dir('assets/textures')");
        assertTrue(Pattern.compile("\\ntest \\{[\\s\\S]*?\\n  mustRunAfter\\(':tools:generateTextures'\\)")
            .matcher(toolsScript).find(), "tools:test mustRunAfter generateTextures");
        assertTrue(Pattern.compile("\\ntest \\{[\\s\\S]*?\\n  mustRunAfter\\(':tools:generateTextures'\\)")
            .matcher(coreScript).find(), "core:test mustRunAfter generateTextures");
        assertTrue(Pattern.compile(
            "tasks\\.register\\(\\s*'generateAssetList'[\\s\\S]*?\\n  mustRunAfter\\(':tools:generateTextures'\\)")
            .matcher(desktopScript).find(), "desktop:generateAssetList mustRunAfter generateTextures");
        assertTrue(desktopScript.contains("\nprocessResources.mustRunAfter(':tools:generateTextures')"),
            "desktop:processResources mustRunAfter generateTextures");
    }

    private static String readProjectFile(String subproject, String fileName) throws IOException {
        for (Path candidate : new Path[] {
            Paths.get(subproject, fileName),
            Paths.get("..", subproject, fileName),
            "tools".equals(subproject) ? Paths.get(fileName) : Paths.get("..", subproject, fileName)
        }) {
            if (Files.isRegularFile(candidate)) {
                return Files.readString(candidate, StandardCharsets.UTF_8);
            }
        }
        throw new IllegalStateException("Could not find " + subproject + "/" + fileName);
    }

    private static void assertClosedOutwardManifold(ObjModel model, String label) {
        assertTrue(model.getTriangleCount() > 0, label + " has no triangles");
        float[] positions = model.getPositions();
        float[] normals = model.getNormals();
        Map<String, Integer> edges = new HashMap<>();
        for (int t = 0; t < model.getTriangleCount(); t++) {
            Vector3 a = cornerPos(positions, t, 0);
            Vector3 b = cornerPos(positions, t, 1);
            Vector3 c = cornerPos(positions, t, 2);
            Vector3 geoNormal = new Vector3(b).sub(a).crs(new Vector3(c).sub(a));
            assertTrue(geoNormal.len() > 1e-8f, label + " has degenerate zero-area triangle " + t);
            geoNormal.nor();
            for (int corner = 0; corner < 3; corner++) {
                int nBase = (t * 3 + corner) * 3;
                Vector3 vn = new Vector3(normals[nBase], normals[nBase + 1], normals[nBase + 2]);
                assertEquals(1f, vn.len(), 1e-3f, label + " non-unit normal");
                assertTrue(vn.dot(geoNormal) > 0f, label + " normal faces opposite geometric winding");

                String from = posKey(cornerPos(positions, t, corner));
                String to = posKey(cornerPos(positions, t, (corner + 1) % 3));
                assertFalse(from.equals(to), label + " has collapsed edge at " + from);
                edges.merge(from + ">" + to, 1, Integer::sum);
            }
        }
        for (Map.Entry<String, Integer> e : edges.entrySet()) {
            String[] ends = e.getKey().split(">");
            assertEquals(1, e.getValue().intValue(), label + " repeats directed edge " + e.getKey());
            assertEquals(1, edges.getOrDefault(ends[1] + ">" + ends[0], 0).intValue(),
                label + " has open or mis-wound edge " + e.getKey());
        }
        double volume = signedVolume(model);
        assertTrue(volume > 1e-7, label + " must have positive outward volume, got " + volume);

        // Also ensure WorldModels.packIndexed succeeds cleanly on every generated primitive.
        ModelVertices.IndexedMesh indexed = WorldModels.packIndexed(model);
        assertTrue(indexed.vertexCount() > 0 && indexed.indexCount() == model.getTriangleCount() * 3);
    }

    private static double signedVolume(ObjModel model) {
        float[] positions = model.getPositions();
        double volume = 0.0;
        for (int t = 0; t < model.getTriangleCount(); t++) {
            Vector3 a = cornerPos(positions, t, 0);
            Vector3 b = cornerPos(positions, t, 1);
            Vector3 c = cornerPos(positions, t, 2);
            volume += (double) a.dot(new Vector3(b).crs(c)) / 6.0;
        }
        return volume;
    }

    private static float maxRadiusAtAxis(ObjModel model, int axis, float targetCoord, float c1, float c2) {
        float[] positions = model.getPositions();
        float maxR = -1f;
        for (int t = 0; t < model.getTriangleCount(); t++) {
            for (int corner = 0; corner < 3; corner++) {
                Vector3 p = cornerPos(positions, t, corner);
                float coord = axis == 0 ? p.x : (axis == 1 ? p.y : p.z);
                if (Math.abs(coord - targetCoord) <= 1e-4f) {
                    float d1 = (axis == 0 ? p.y : p.x) - c1;
                    float d2 = (axis == 2 ? p.y : p.z) - c2;
                    maxR = Math.max(maxR, (float) Math.hypot(d1, d2));
                }
            }
        }
        assertTrue(maxR >= 0f, "No vertices found at axis=" + axis + " coord=" + targetCoord);
        return maxR;
    }

    private static Vector3 cornerPos(float[] positions, int triangle, int corner) {
        int base = (triangle * 3 + corner) * 3;
        return new Vector3(positions[base], positions[base + 1], positions[base + 2]);
    }

    private static String posKey(Vector3 v) {
        return Math.round(v.x * 1e6f) + "," + Math.round(v.y * 1e6f) + "," + Math.round(v.z * 1e6f);
    }
}

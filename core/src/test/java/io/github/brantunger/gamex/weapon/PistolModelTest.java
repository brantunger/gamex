package io.github.brantunger.gamex.weapon;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.badlogic.gdx.math.Vector3;
import io.github.brantunger.gamex.model.ObjModel;
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

/** Checks the shipped pistol model file and how it is turned into body and slide meshes. */
class PistolModelTest {

    private static final PistolModel MODEL = PistolModel.from(load(PistolModel.ASSET_PATH));

    private static ObjModel load(String assetPath) {
        List<Path> candidates = new ArrayList<>();
        String assets = System.getProperty("gamex.assets");
        if (assets != null) candidates.add(Paths.get(assets, assetPath));
        candidates.add(Paths.get("..", "assets", assetPath));
        candidates.add(Paths.get("assets", assetPath));
        for (Path candidate : candidates) {
            if (!Files.isRegularFile(candidate)) continue;
            try {
                return ObjModel.parse(new String(Files.readAllBytes(candidate), StandardCharsets.UTF_8));
            } catch (IOException e) {
                throw new UncheckedIOException(e);
            }
        }
        List<Path> tried = new ArrayList<>();
        for (Path candidate : candidates) tried.add(candidate.toAbsolutePath().normalize());
        throw new IllegalStateException("The pistol model was not found. Set the system property gamex.assets to the "
            + "assets folder, or run from the root project or the core project. Tried: " + tried);
    }

    private static Vector3 position(float[] vertices, int vertex) {
        int at = vertex * PistolModel.VERTEX_SIZE;
        return new Vector3(vertices[at], vertices[at + 1], vertices[at + 2]);
    }

    private static Vector3 normal(float[] vertices, int vertex) {
        int at = vertex * PistolModel.VERTEX_SIZE;
        return new Vector3(vertices[at + 3], vertices[at + 4], vertices[at + 5]);
    }

    /** A corner as a map key; corners shared between triangles are built from the same floats. */
    private static String key(Vector3 corner) {
        return Math.round(corner.x * 1e6f) + "," + Math.round(corner.y * 1e6f) + "," + Math.round(corner.z * 1e6f);
    }

    private static Vector3 centre(float[] vertices, PistolModel.Solid solid) {
        Vector3 sum = new Vector3();
        for (int i = 0; i < solid.vertexCount; i++) sum.add(position(vertices, solid.firstVertex + i));
        return sum.scl(1f / solid.vertexCount);
    }

    @Test
    void bothGroupsHoldWholeTrianglesAndTheSolidsCoverThemExactly() {
        for (PistolModel.Group group : PistolModel.Group.values()) {
            float[] vertices = MODEL.vertices(group);
            assertTrue(vertices.length > 0, group + " is empty");
            assertEquals(0, vertices.length % (3 * PistolModel.VERTEX_SIZE), group + " holds whole triangles");
            int next = 0;
            for (PistolModel.Solid solid : MODEL.solids) {
                if (solid.group != group) continue;
                assertEquals(next, solid.firstVertex, solid.name + " follows the solid before it");
                assertEquals(0, solid.vertexCount % 3, solid.name);
                next += solid.vertexCount;
            }
            assertEquals(vertices.length / PistolModel.VERTEX_SIZE, next, "the solids of " + group);
        }
    }

    @Test
    void everySolidIsClosedWithConsistentWinding() {
        for (PistolModel.Solid solid : MODEL.solids) {
            float[] vertices = MODEL.vertices(solid.group);
            // In a closed, consistently wound surface every directed edge a->b is matched by exactly one b->a.
            Map<String, Integer> edges = new HashMap<>();
            for (int i = 0; i < solid.vertexCount; i += 3) {
                for (int corner = 0; corner < 3; corner++) {
                    String from = key(position(vertices, solid.firstVertex + i + corner));
                    String to = key(position(vertices, solid.firstVertex + i + (corner + 1) % 3));
                    assertFalse(from.equals(to), solid.name + " has a degenerate edge");
                    edges.merge(from + ">" + to, 1, Integer::sum);
                }
            }
            for (Map.Entry<String, Integer> edge : edges.entrySet()) {
                String[] ends = edge.getKey().split(">");
                assertEquals(1, edge.getValue().intValue(), solid.name + " repeats edge " + edge.getKey());
                assertEquals(1, edges.getOrDefault(ends[1] + ">" + ends[0], 0).intValue(),
                    solid.name + " has an open or mis-wound edge " + edge.getKey());
            }
        }
    }

    @Test
    void everyTriangleIsWoundCounterClockwiseSeenFromOutside() {
        for (PistolModel.Solid solid : MODEL.solids) {
            float[] vertices = MODEL.vertices(solid.group);
            Vector3 middle = centre(vertices, solid);
            for (int i = 0; i < solid.vertexCount; i += 3) {
                Vector3 a = position(vertices, solid.firstVertex + i);
                Vector3 b = position(vertices, solid.firstVertex + i + 1);
                Vector3 c = position(vertices, solid.firstVertex + i + 2);
                Vector3 wound = new Vector3(b).sub(a).crs(new Vector3(c).sub(a));
                assertTrue(wound.len() > 1e-9f, solid.name + " has a triangle without area");
                // The solids are convex, so "outside" is away from the solid's centre.
                Vector3 outward = new Vector3(a).add(b).add(c).scl(1f / 3f).sub(middle);
                assertTrue(wound.dot(outward) > 0f, solid.name + " triangle " + i / 3 + " faces inwards");
            }
        }
    }

    @Test
    void everyVertexCarriesTheUnitNormalOfItsTriangle() {
        for (PistolModel.Solid solid : MODEL.solids) {
            float[] vertices = MODEL.vertices(solid.group);
            for (int i = 0; i < solid.vertexCount; i += 3) {
                Vector3 a = position(vertices, solid.firstVertex + i);
                Vector3 b = position(vertices, solid.firstVertex + i + 1);
                Vector3 c = position(vertices, solid.firstVertex + i + 2);
                Vector3 expected = new Vector3(b).sub(a).crs(new Vector3(c).sub(a)).nor();
                for (int corner = 0; corner < 3; corner++) {
                    Vector3 stored = normal(vertices, solid.firstVertex + i + corner);
                    assertEquals(1f, stored.len(), 1e-4f, solid.name + " normal length");
                    assertEquals(1f, stored.dot(expected), 1e-3f, solid.name + " normal direction");
                }
            }
        }
    }

    @Test
    void coloursAreWithinRange() {
        for (PistolModel.Group group : PistolModel.Group.values()) {
            float[] vertices = MODEL.vertices(group);
            for (int at = 0; at < vertices.length; at += PistolModel.VERTEX_SIZE) {
                for (int channel = 6; channel < 9; channel++) {
                    assertTrue(vertices[at + channel] >= 0f && vertices[at + channel] <= 1f, "colour channel");
                }
            }
        }
    }

    @Test
    void modelHasTheRecognisablePartsOfAPistol() {
        Set<String> names = new HashSet<>();
        for (PistolModel.Solid solid : MODEL.solids) names.add(solid.name);

        for (String part : new String[] {"slide", "slide top", "slide serration 0", "slide front sight",
            "slide rear sight left", "slide rear sight right", "frame", "barrel", "bore", "trigger guard 0",
            "trigger", "hammer", "beavertail", "grip", "magazine plate", "stipple left 0/0"}) {
            assertTrue(names.contains(part), "missing " + part);
        }
    }

    @Test
    void everySolidIsConvex() {
        for (PistolModel.Solid solid : MODEL.solids) {
            float[] vertices = MODEL.vertices(solid.group);
            for (int i = 0; i < solid.vertexCount; i += 3) {
                Vector3 onFace = position(vertices, solid.firstVertex + i);
                Vector3 faceNormal = normal(vertices, solid.firstVertex + i);
                // No corner of a convex solid lies in front of any of its faces.
                for (int other = 0; other < solid.vertexCount; other++) {
                    float ahead = position(vertices, solid.firstVertex + other).sub(onFace).dot(faceNormal);
                    assertTrue(ahead < 2e-5f, solid.name + " has a corner " + ahead + " in front of triangle " + i / 3);
                }
            }
        }
    }

    @Test
    void glossIsWithinRangeAndOnlyMetalShines() {
        for (PistolModel.Solid solid : MODEL.solids) {
            float gloss = MODEL.vertices(solid.group)[solid.firstVertex * PistolModel.VERTEX_SIZE + 9];
            assertTrue(gloss >= 0f && gloss <= 1f, solid.name + " gloss " + gloss);
            if (solid.name.equals("slide top") || solid.name.equals("barrel")) assertEquals(1f, gloss, 0f);
            if (solid.name.equals("grip") || solid.name.equals("frame")) {
                assertEquals(0f, gloss, 0f, solid.name + " is matt");
            }
        }
    }

    @Test
    void slideGroupHoldsExactlyTheSolidsNamedAsPartsOfTheSlide() {
        for (PistolModel.Solid solid : MODEL.solids) {
            assertEquals(solid.name.startsWith("slide") ? PistolModel.Group.SLIDE : PistolModel.Group.BODY,
                solid.group, solid.name);
        }
    }

    @Test
    void muzzleIsTheForemostPointOfTheGunOnTheBarrelAxis() {
        float foremost = Float.POSITIVE_INFINITY;
        for (PistolModel.Group group : PistolModel.Group.values()) {
            float[] vertices = MODEL.vertices(group);
            for (int at = 0; at < vertices.length; at += PistolModel.VERTEX_SIZE) {
                foremost = Math.min(foremost, vertices[at + 2]);
            }
        }

        // The barrel points along negative z.
        assertEquals(PistolModel.MUZZLE_Z, foremost, 1e-6f);
        PistolModel.Solid bore = null;
        for (PistolModel.Solid solid : MODEL.solids) {
            if (solid.name.equals("bore")) bore = solid;
        }
        Vector3 low = new Vector3(Float.MAX_VALUE, Float.MAX_VALUE, 0f);
        Vector3 high = new Vector3(-Float.MAX_VALUE, -Float.MAX_VALUE, 0f);
        for (int i = 0; i < bore.vertexCount; i++) {
            Vector3 corner = position(MODEL.body, bore.firstVertex + i);
            low.set(Math.min(low.x, corner.x), Math.min(low.y, corner.y), 0f);
            high.set(Math.max(high.x, corner.x), Math.max(high.y, corner.y), 0f);
        }
        assertEquals(PistolModel.MUZZLE_X, (low.x + high.x) * 0.5f, 1e-5f);
        assertEquals(PistolModel.MUZZLE_Y, (low.y + high.y) * 0.5f, 1e-5f);
    }

    @Test
    void gripIsRakedBackBelowTheSlide() {
        for (PistolModel.Solid solid : MODEL.solids) {
            if (!solid.name.equals("grip")) continue;
            float topZ = 0f;
            float bottomZ = 0f;
            float topY = Float.NEGATIVE_INFINITY;
            float bottomY = Float.POSITIVE_INFINITY;
            for (int i = 0; i < solid.vertexCount; i++) {
                Vector3 corner = position(MODEL.body, solid.firstVertex + i);
                if (corner.y > topY || (corner.y == topY && corner.z > topZ)) {
                    topY = corner.y;
                    topZ = corner.z;
                }
                if (corner.y < bottomY || (corner.y == bottomY && corner.z > bottomZ)) {
                    bottomY = corner.y;
                    bottomZ = corner.z;
                }
            }
            assertTrue(topY < 0f, "the grip hangs below the slide");
            assertTrue(bottomZ > topZ, "the heel of the grip is further back than its top");
        }
    }

    @Test
    void modelWithAnUnknownMaterialOrMissingGroupIsRejected() {
        ObjModel unknownMaterial = ObjModel.parse("v 0 0 0\nv 1 0 0\nv 0 1 0\ng part\nusemtl chrome\nf 1 2 3\n");
        IllegalArgumentException materialError = assertThrows(IllegalArgumentException.class,
            () -> PistolModel.from(unknownMaterial));
        assertTrue(materialError.getMessage().contains("chrome"), materialError.getMessage());

        ObjModel missingGroup = ObjModel.parse("v 0 0 0\nv 1 0 0\nv 0 1 0\nusemtl gunmetal\nf 1 2 3\n");
        IllegalArgumentException groupError = assertThrows(IllegalArgumentException.class,
            () -> PistolModel.from(missingGroup));
        assertTrue(groupError.getMessage().contains("group"), groupError.getMessage());
    }

    @Test
    void indexedMeshesAndMuzzleSocketMatchSplitModel() {
        assertTrue(MODEL.bodyMesh.vertexCount() > 0);
        assertTrue(MODEL.slideMesh.vertexCount() > 0);
        assertEquals(MODEL.body.length / PistolModel.VERTEX_SIZE, MODEL.bodyMesh.indexCount());
        assertEquals(MODEL.slide.length / PistolModel.VERTEX_SIZE, MODEL.slideMesh.indexCount());
        assertTrue(MODEL.bodyMesh.vertexCount() < MODEL.bodyMesh.indexCount());

        ObjModel obj = load(PistolModel.ASSET_PATH);
        assertTrue(obj.getPartSpec("slide") != null, "missing # @part slide");
        ObjModel.SocketSpec muzzle = obj.getSocket("muzzle");
        assertTrue(muzzle != null, "missing # @socket muzzle");
        assertEquals(PistolModel.MUZZLE_X, muzzle.x, 1e-5f);
        assertEquals(PistolModel.MUZZLE_Y, muzzle.y, 1e-5f);
        assertEquals(PistolModel.MUZZLE_Z, muzzle.z, 1e-5f);
    }
}

package io.github.brantunger.gamex.tools.preview;

import com.badlogic.gdx.Application;
import com.badlogic.gdx.Gdx;
import com.badlogic.gdx.Graphics;
import com.badlogic.gdx.graphics.GL20;
import com.badlogic.gdx.graphics.PerspectiveCamera;
import com.badlogic.gdx.graphics.glutils.ShaderProgram;
import com.badlogic.gdx.math.Matrix4;
import com.badlogic.gdx.utils.Disposable;
import com.badlogic.gdx.utils.GdxRuntimeException;
import io.github.brantunger.gamex.model.ModelVertices;
import io.github.brantunger.gamex.tools.GenerateAllModels;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.IOException;
import java.lang.reflect.Proxy;
import java.nio.ByteBuffer;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class ModelPreviewAppTest {

    private Application previousApp;
    private Graphics previousGraphics;
    private GL20 previousGl;
    private GL20 previousGl20;

    @BeforeEach
    void saveGdxGlobals() {
        previousApp = Gdx.app;
        previousGraphics = Gdx.graphics;
        previousGl = Gdx.gl;
        previousGl20 = Gdx.gl20;
        Gdx.app = null;
    }

    @AfterEach
    void restoreGdxGlobals() {
        Gdx.app = previousApp;
        Gdx.graphics = previousGraphics;
        Gdx.gl = previousGl;
        Gdx.gl20 = previousGl20;
    }

    @Test
    void buildWireframeIndicesEmitsTrueTriangleEdgesWithoutCrossTriangleSpaghettiAndHandlesUnsigned16Bit() {
        assertArrayEquals(
            new short[] {0, 1, 1, 2, 2, 0},
            ModelPreviewApp.buildWireframeIndices(new short[] {0, 1, 2}));

        // Two separate triangles must never connect corner 2 of triangle 0 to corner 3 of triangle 1.
        short[] disjoint = ModelPreviewApp.buildWireframeIndices(new short[] {0, 1, 2, 3, 4, 5});
        assertArrayEquals(new short[] {0, 1, 1, 2, 2, 0, 3, 4, 4, 5, 5, 3}, disjoint);
        assertFalse(containsUndirectedEdge(disjoint, 2, 3));

        // Shared quad diagonal (0, 2) / (2, 0) is deduplicated cleanly while keeping all 5 edges.
        short[] quad = ModelPreviewApp.buildWireframeIndices(new short[] {0, 1, 2, 0, 2, 3}, 4);
        assertEquals(10, quad.length);
        assertTrue(containsUndirectedEdge(quad, 0, 1));
        assertTrue(containsUndirectedEdge(quad, 1, 2));
        assertTrue(containsUndirectedEdge(quad, 0, 2));
        assertTrue(containsUndirectedEdge(quad, 2, 3));
        assertTrue(containsUndirectedEdge(quad, 3, 0));

        // Unsigned 16-bit indices above 32,767 (negative as signed Java shorts) are preserved without sign errors.
        short hi0 = (short) 40_000;
        short hi1 = (short) 52_000;
        short hi2 = (short) 65_534;
        short[] high = ModelPreviewApp.buildWireframeIndices(
            new short[] {hi0, hi1, hi2}, ModelVertices.MAX_INDEXED_VERTICES);
        assertArrayEquals(new short[] {hi0, hi1, hi1, hi2, hi2, hi0}, high);

        assertThrows(IllegalArgumentException.class, () -> ModelPreviewApp.buildWireframeIndices(null));
        assertThrows(IllegalArgumentException.class, () -> ModelPreviewApp.buildWireframeIndices(new short[0]));
        assertThrows(IllegalArgumentException.class,
            () -> ModelPreviewApp.buildWireframeIndices(new short[] {0, 1, 2, 3}));
        assertThrows(IllegalArgumentException.class,
            () -> ModelPreviewApp.buildWireframeIndices(new short[] {0, 1, 2}, 0));
        assertThrows(IllegalArgumentException.class,
            () -> ModelPreviewApp.buildWireframeIndices(new short[] {0, 1, 2}, ModelVertices.MAX_INDEXED_VERTICES + 1));
        assertThrows(IllegalArgumentException.class,
            () -> ModelPreviewApp.buildWireframeIndices(new short[] {0, 1, 4}, 4));
        assertThrows(IllegalArgumentException.class,
            () -> ModelPreviewApp.buildWireframeIndices(new short[] {2, 2, 2}, 4));
    }

    @Test
    void atomicLiveReloadRollsBackPartialMeshesOnFailureAndSwapsCleanlyOnSuccess(@TempDir Path modelsDir)
        throws IOException {
        GenerateAllModels.generateAll(modelsDir);
        RecordingMeshFactory factory = new RecordingMeshFactory();
        ModelPreviewApp app = new ModelPreviewApp(null, null, modelsDir, factory);

        // Empty-list navigation/reset/dispose before initial load must not throw IndexOutOfBounds or / by zero.
        assertDoesNotThrow(() -> app.stepEntry(1));
        assertDoesNotThrow(() -> app.stepEntry(-1));
        assertDoesNotThrow(app::resetCameraForEntry);
        assertDoesNotThrow(app::dispose);

        app.loadAllEntries();
        int initialCount = app.getEntryCount();
        // 3 combined viewmodels + 19 world/prop/enemy entries.
        assertEquals(22, initialCount);
        List<RecordedMesh> gen1Solid = new ArrayList<>(factory.solidMeshes);
        List<RecordedMesh> gen1Wire = new ArrayList<>(factory.wireMeshes);
        assertFalse(gen1Solid.isEmpty());
        assertEquals(gen1Solid.size(), gen1Wire.size(), "every solid part mesh must have a wireframe companion");
        for (int i = 0; i < gen1Solid.size(); i++) {
            RecordedMesh solid = gen1Solid.get(i);
            RecordedMesh wire = gen1Wire.get(i);
            assertEquals(0, solid.disposeCount);
            assertEquals(0, wire.disposeCount);
            assertAllTriangleEdgesCoveredWithoutSpaghetti(solid.data.indices, wire.wireIndices);
        }

        // Select the last entry so a naive clear-before-load would immediately go out of bounds.
        app.setCurrentIndex(initialCount - 1);
        String selectedBefore = app.getEntryLabel(app.getCurrentIndex());
        assertEquals("enemy-bolt.obj", selectedBefore);

        // 1) Corrupt canister.obj (loaded near the end of loadAllEntries after 17 entries have built meshes).
        Files.writeString(modelsDir.resolve("canister.obj"), "v broken obj syntax\nf 99 100 101\n");
        factory.clearBatchLists();
        app.reloadModels(null);

        assertTrue(app.getStatusToast().startsWith("RELOAD ERROR: "), app.getStatusToast());
        assertEquals(4.0f, app.getStatusTimer(), 1e-6f);
        assertEquals(initialCount, app.getEntryCount(), "existing entries must stay intact on failed reload");
        assertEquals(initialCount - 1, app.getCurrentIndex());
        assertEquals(selectedBefore, app.getEntryLabel(app.getCurrentIndex()));
        assertDoesNotThrow(() -> app.stepEntry(1));
        assertEquals(0, app.getCurrentIndex());
        app.stepEntry(-1);
        assertEquals(initialCount - 1, app.getCurrentIndex());

        // Original generation-1 meshes must still be alive, while ALL partial failed-reload meshes were disposed.
        for (RecordedMesh m : gen1Solid) assertEquals(0, m.disposeCount, "active solid mesh must not be disposed");
        for (RecordedMesh m : gen1Wire) assertEquals(0, m.disposeCount, "active wire mesh must not be disposed");
        assertTrue(factory.solidMeshes.size() >= 20, "expected partial entries built before canister.obj failed");
        for (RecordedMesh m : factory.solidMeshes) {
            assertEquals(1, m.disposeCount, "rolled-back solid mesh must be disposed");
        }
        for (RecordedMesh m : factory.wireMeshes) {
            assertEquals(1, m.disposeCount, "rolled-back wireframe mesh must be disposed");
        }

        // 2) Simulate a mid-part wireframe allocation failure right after solid mesh creation on part #2.
        GenerateAllModels.generateAll(modelsDir);
        factory.clearBatchLists();
        factory.failOnWireBuildCall = 2;
        app.reloadModels(null);
        assertTrue(app.getStatusToast().startsWith("RELOAD ERROR: "), app.getStatusToast());
        assertEquals(2, factory.solidMeshes.size(), "two solid meshes allocated prior to 2nd wire failure");
        assertEquals(1, factory.wireMeshes.size(), "one wire mesh allocated prior to 2nd wire failure");
        for (RecordedMesh m : factory.solidMeshes) assertEquals(1, m.disposeCount);
        for (RecordedMesh m : factory.wireMeshes) assertEquals(1, m.disposeCount);
        for (RecordedMesh m : gen1Solid) assertEquals(0, m.disposeCount);
        for (RecordedMesh m : gen1Wire) assertEquals(0, m.disposeCount);

        // 3) Generator IOException also surfaces RELOAD ERROR without touching active entries.
        factory.failOnWireBuildCall = -1;
        app.reloadModels(() -> {
            throw new IOException("simulated disk write failure");
        });
        assertEquals("RELOAD ERROR: simulated disk write failure", app.getStatusToast());
        assertEquals(4.0f, app.getStatusTimer(), 1e-6f);
        assertEquals(initialCount, app.getEntryCount());

        // 4) Successful regenerate + reload disposes generation-1 meshes once and activates generation-2 meshes.
        factory.clearBatchLists();
        app.reloadModels(() -> GenerateAllModels.generateAll(modelsDir));
        assertEquals("REGENERATED ALL MODELS", app.getStatusToast());
        assertEquals(2.0f, app.getStatusTimer(), 1e-6f);
        assertEquals(initialCount, app.getEntryCount());
        assertEquals(initialCount - 1, app.getCurrentIndex());
        for (RecordedMesh m : gen1Solid) assertEquals(1, m.disposeCount, "old solid mesh disposed on swap");
        for (RecordedMesh m : gen1Wire) assertEquals(1, m.disposeCount, "old wire mesh disposed on swap");

        List<RecordedMesh> gen2Solid = new ArrayList<>(factory.solidMeshes);
        List<RecordedMesh> gen2Wire = new ArrayList<>(factory.wireMeshes);
        for (RecordedMesh m : gen2Solid) assertEquals(0, m.disposeCount);
        for (RecordedMesh m : gen2Wire) assertEquals(0, m.disposeCount);

        // 5) Teardown disposes all active solid and wireframe meshes cleanly.
        app.dispose();
        assertEquals(0, app.getEntryCount());
        for (RecordedMesh m : gen2Solid) assertEquals(1, m.disposeCount, "active solid mesh disposed on teardown");
        for (RecordedMesh m : gen2Wire) assertEquals(1, m.disposeCount, "active wire mesh disposed on teardown");
    }

    @Test
    void resizeAndViewportUsePhysicalBackBufferDimensionsAndIgnoreZeroOrNegativeSizes() {
        PerspectiveCamera fallbackNull = ModelPreviewApp.createPreviewCamera(null);
        assertEquals(1280f, fallbackNull.viewportWidth, 1e-5f);
        assertEquals(800f, fallbackNull.viewportHeight, 1e-5f);
        assertTrue(Float.isFinite(fallbackNull.projection.val[Matrix4.M00]));

        for (int[] invalid : new int[][] {{0, 0}, {0, 800}, {1280, 0}, {-320, 240}, {320, -240}}) {
            PerspectiveCamera fallbackMin = ModelPreviewApp.createPreviewCamera(
                graphicsStub(invalid[0], invalid[1], invalid[0], invalid[1]));
            assertEquals(1280f, fallbackMin.viewportWidth, 1e-5f);
            assertEquals(800f, fallbackMin.viewportHeight, 1e-5f);
            assertTrue(Float.isFinite(fallbackMin.combined.val[Matrix4.M00]));
        }

        ViewportRecorder recorder = new ViewportRecorder();
        // HiDPI Retina scale: logical 1440x900, physical backbuffer 2880x1800.
        Gdx.graphics = graphicsStub(1440, 900, 2880, 1800);
        Gdx.gl = recorder.gl;
        Gdx.gl20 = recorder.gl;

        ModelPreviewApp app = new ModelPreviewApp(null, null, null, new RecordingMeshFactory());
        PerspectiveCamera cam = ModelPreviewApp.createPreviewCamera(Gdx.graphics);
        app.setCameraForTest(cam);
        app.applyBackBufferViewport();
        assertEquals(1, recorder.calls);
        assertArrayEquals(new int[] {0, 0, 2880, 1800}, recorder.lastViewport);

        Gdx.graphics = graphicsStub(1600, 1000, 3200, 2000);
        app.resize(1600, 1000);
        assertEquals(2, recorder.calls);
        assertArrayEquals(new int[] {0, 0, 3200, 2000}, recorder.lastViewport);
        assertEquals(1600f, app.getCamera().viewportWidth, 1e-5f);
        assertEquals(1000f, app.getCamera().viewportHeight, 1e-5f);

        // Minimized 0x0 or negative resize must not clobber glViewport or camera viewport.
        Gdx.graphics = graphicsStub(0, 0, 0, 0);
        for (int[] invalid : new int[][] {{0, 0}, {0, 1000}, {1600, 0}, {-200, 300}, {200, -300}}) {
            app.resize(invalid[0], invalid[1]);
        }
        assertEquals(2, recorder.calls);
        assertEquals(1600f, app.getCamera().viewportWidth, 1e-5f);
        assertEquals(1000f, app.getCamera().viewportHeight, 1e-5f);
    }

    @Test
    void screenshotCaptureDefersZeroBackBufferAndAlwaysDisposesPixmapWhenWritePngThrows(@TempDir Path tempDir) {
        ModelPreviewApp app = new ModelPreviewApp(
            null, tempDir.resolve("preview.png").toString(), tempDir, new RecordingMeshFactory());
        List<FakePixmap> captures = new ArrayList<>();
        AtomicInteger writes = new AtomicInteger();
        AtomicBoolean exited = new AtomicBoolean();

        // Frame 1 (< 2): must not capture even with positive backbuffer.
        app.advanceFrameForTest();
        assertFalse(app.maybeCaptureAutomatedScreenshot(800, 600,
            (w, h) -> {
                FakePixmap p = new FakePixmap(w, h);
                captures.add(p);
                return p;
            },
            p -> p.pixels, p -> writes.incrementAndGet(), () -> exited.set(true)));
        assertEquals(0, captures.size());
        assertFalse(exited.get());

        // Frame 2 (== 2) while minimized (0x0 / non-positive): defers without capturing or exiting.
        app.advanceFrameForTest();
        for (int[] invalid : new int[][] {{0, 0}, {0, 600}, {800, 0}, {-10, 600}, {800, -10}}) {
            assertFalse(app.maybeCaptureAutomatedScreenshot(invalid[0], invalid[1],
                (w, h) -> {
                    FakePixmap p = new FakePixmap(w, h);
                    captures.add(p);
                    return p;
                },
                p -> p.pixels, p -> writes.incrementAndGet(), () -> exited.set(true)));
        }
        assertEquals(0, captures.size());
        assertFalse(exited.get());

        // Also check static saveScreenshot(String) directly against a 0x0 Graphics stub.
        Gdx.graphics = graphicsStub(0, 0, 0, 0);
        assertFalse(ModelPreviewApp.saveScreenshot(tempDir.resolve("skipped.png").toString()));

        // Frame 3 (> 2) with writePNG failure: pixmap MUST still be disposed in finally and app must not mark captured.
        app.advanceFrameForTest();
        GdxRuntimeException writeError = new GdxRuntimeException("writePNG failed: unwritable target");
        GdxRuntimeException thrown = assertThrows(GdxRuntimeException.class, () ->
            app.maybeCaptureAutomatedScreenshot(2, 2,
                (w, h) -> {
                    FakePixmap p = new FakePixmap(w, h);
                    captures.add(p);
                    return p;
                },
                p -> p.pixels,
                p -> {
                    throw writeError;
                },
                () -> exited.set(true)));
        assertSame(writeError, thrown);
        assertEquals(1, captures.size());
        assertEquals(1, captures.get(0).disposeCount, "Pixmap must be disposed when writePNG throws");
        assertFalse(exited.get());

        // Frame 4 (> 2) with valid 2x2 backbuffer and working writer: captures opaque pixels, disposes, and exits once.
        app.advanceFrameForTest();
        assertTrue(app.maybeCaptureAutomatedScreenshot(2, 2,
            (w, h) -> {
                FakePixmap p = new FakePixmap(w, h);
                captures.add(p);
                return p;
            },
            p -> p.pixels,
            p -> {
                assertEquals(0, p.disposeCount);
                writes.incrementAndGet();
            },
            () -> exited.set(true)));
        assertEquals(2, captures.size());
        FakePixmap saved = captures.get(1);
        assertEquals(1, saved.disposeCount);
        assertEquals(1, writes.get());
        assertTrue(exited.get());
        for (int px = 0; px < 4; px++) {
            assertEquals((byte) 0xFF, saved.pixels.get(px * 4 + 3));
        }

        // Frame 5 after capture succeeded: no duplicate capture.
        app.advanceFrameForTest();
        assertFalse(app.maybeCaptureAutomatedScreenshot(2, 2,
            (w, h) -> {
                FakePixmap p = new FakePixmap(w, h);
                captures.add(p);
                return p;
            },
            p -> p.pixels, p -> writes.incrementAndGet(), () -> {}));
        assertEquals(2, captures.size());
        assertEquals(1, writes.get());
    }

    private static boolean containsUndirectedEdge(short[] wireIndices, int a, int b) {
        int lo = Math.min(a, b);
        int hi = Math.max(a, b);
        for (int i = 0; i < wireIndices.length; i += 2) {
            int u = wireIndices[i] & 0xFFFF;
            int v = wireIndices[i + 1] & 0xFFFF;
            if (Math.min(u, v) == lo && Math.max(u, v) == hi) return true;
        }
        return false;
    }

    private static void assertAllTriangleEdgesCoveredWithoutSpaghetti(short[] triIndices, short[] wireIndices) {
        assertTrue(wireIndices.length > 0 && wireIndices.length % 2 == 0);
        Set<Long> expectedEdges = new HashSet<>();
        for (int i = 0; i < triIndices.length; i += 3) {
            int i0 = triIndices[i] & 0xFFFF;
            int i1 = triIndices[i + 1] & 0xFFFF;
            int i2 = triIndices[i + 2] & 0xFFFF;
            addEdgeKey(expectedEdges, i0, i1);
            addEdgeKey(expectedEdges, i1, i2);
            addEdgeKey(expectedEdges, i2, i0);
        }
        Set<Long> actualEdges = new HashSet<>();
        for (int i = 0; i < wireIndices.length; i += 2) {
            int u = wireIndices[i] & 0xFFFF;
            int v = wireIndices[i + 1] & 0xFFFF;
            assertTrue(u != v, "Wireframe must not contain zero-length edge");
            long key = ((long) Math.min(u, v) << 32) | (Math.max(u, v) & 0xFFFFFFFFL);
            assertTrue(actualEdges.add(key), "Duplicate undirected wireframe edge");
        }
        assertEquals(expectedEdges, actualEdges);
    }

    private static void addEdgeKey(Set<Long> set, int a, int b) {
        if (a != b) {
            set.add(((long) Math.min(a, b) << 32) | (Math.max(a, b) & 0xFFFFFFFFL));
        }
    }

    private static Graphics graphicsStub(int width, int height, int backBufferWidth, int backBufferHeight) {
        return (Graphics) Proxy.newProxyInstance(
            Graphics.class.getClassLoader(),
            new Class<?>[] {Graphics.class},
            (proxy, method, args) -> {
                switch (method.getName()) {
                    case "getWidth":
                        return width;
                    case "getHeight":
                        return height;
                    case "getBackBufferWidth":
                        return backBufferWidth;
                    case "getBackBufferHeight":
                        return backBufferHeight;
                    default:
                        return defaultPrimitive(method.getReturnType());
                }
            });
    }

    private static final class ViewportRecorder {
        int calls;
        int[] lastViewport;
        final GL20 gl = (GL20) Proxy.newProxyInstance(
            GL20.class.getClassLoader(),
            new Class<?>[] {GL20.class},
            (proxy, method, args) -> {
                if ("glViewport".equals(method.getName()) && args != null && args.length == 4) {
                    calls++;
                    lastViewport = new int[] {(int) args[0], (int) args[1], (int) args[2], (int) args[3]};
                    return null;
                }
                return defaultPrimitive(method.getReturnType());
            });
    }

    private static Object defaultPrimitive(Class<?> returnType) {
        if (!returnType.isPrimitive()) return null;
        if (returnType == boolean.class) return false;
        if (returnType == int.class) return 0;
        if (returnType == long.class) return 0L;
        if (returnType == float.class) return 0f;
        if (returnType == double.class) return 0d;
        if (returnType == short.class) return (short) 0;
        if (returnType == byte.class) return (byte) 0;
        if (returnType == char.class) return '\0';
        return null;
    }

    private static final class RecordedMesh implements ModelPreviewApp.PreviewMesh {
        final ModelVertices.IndexedMesh data;
        final short[] wireIndices;
        int disposeCount;

        RecordedMesh(ModelVertices.IndexedMesh data, short[] wireIndices) {
            this.data = data;
            this.wireIndices = wireIndices;
        }

        @Override
        public void render(ShaderProgram shader, int primitiveType) {}

        @Override
        public void dispose() {
            disposeCount++;
        }
    }

    private static final class RecordingMeshFactory implements ModelPreviewApp.MeshFactory {
        final List<RecordedMesh> solidMeshes = new ArrayList<>();
        final List<RecordedMesh> wireMeshes = new ArrayList<>();
        int failOnWireBuildCall = -1;
        private int wireCalls;

        void clearBatchLists() {
            solidMeshes.clear();
            wireMeshes.clear();
            wireCalls = 0;
        }

        @Override
        public ModelPreviewApp.PreviewMesh buildTriangles(ModelVertices.IndexedMesh data) {
            RecordedMesh m = new RecordedMesh(data, null);
            solidMeshes.add(m);
            return m;
        }

        @Override
        public ModelPreviewApp.PreviewMesh buildWireframe(ModelVertices.IndexedMesh data, short[] wireframeIndices) {
            wireCalls++;
            if (wireCalls == failOnWireBuildCall) {
                throw new GdxRuntimeException("simulated wireframe VBO allocation failure");
            }
            RecordedMesh m = new RecordedMesh(data, wireframeIndices);
            wireMeshes.add(m);
            return m;
        }
    }

    private static final class FakePixmap implements Disposable {
        final ByteBuffer pixels;
        int disposeCount;

        FakePixmap(int w, int h) {
            this.pixels = ByteBuffer.allocate(w * h * 4);
            for (int i = 3; i < w * h * 4; i += 4) {
                this.pixels.put(i, (byte) 0x10);
            }
        }

        @Override
        public void dispose() {
            disposeCount++;
        }
    }
}

package io.github.brantunger.gamex.debug;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import ch.qos.logback.classic.Level;
import com.badlogic.gdx.Gdx;
import com.badlogic.gdx.Graphics;
import com.badlogic.gdx.utils.Disposable;
import com.badlogic.gdx.utils.GdxRuntimeException;
import io.github.brantunger.gamex.GameSimulation;
import io.github.brantunger.gamex.LogCapture;
import io.github.brantunger.gamex.player.Difficulty;
import io.github.brantunger.gamex.player.PlayerInput;
import io.github.brantunger.gamex.player.PlayerLoadout;
import io.github.brantunger.gamex.weapon.WeaponSlot;
import io.github.brantunger.gamex.world.Levels;
import org.junit.jupiter.api.Test;

import java.lang.reflect.Proxy;
import java.nio.ByteBuffer;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.atomic.AtomicInteger;

class DemoRunTest {

    @Test
    void orbitIsAYawWithAnOptionalPitch() {
        assertArrayEquals(new float[] {30f, 0f}, DemoRun.parseOrbit("30"), 1e-6f);
        assertArrayEquals(new float[] {30f, 15f}, DemoRun.parseOrbit("30,15"), 1e-6f);
        assertArrayEquals(new float[] {-90f, 22.5f}, DemoRun.parseOrbit(" -90 , 22.5 "), 1e-6f);
    }

    @Test
    void missingOrbitIsNoOrbit() {
        assertNull(DemoRun.parseOrbit(null));
        assertNull(DemoRun.parseOrbit(""));
    }

    @Test
    void malformedOrbitIsNoOrbit() {
        assertNull(DemoRun.parseOrbit("abc"));
        assertNull(DemoRun.parseOrbit("30,abc"));
        assertNull(DemoRun.parseOrbit(" "));
        assertNull(DemoRun.parseOrbit(",5"));
        assertNull(DemoRun.parseOrbit("30, "));
    }

    @Test
    void orbitMadeOnlyOfCommasIsNoOrbitRatherThanACrash() {
        // Splitting such text leaves no parts at all.
        assertNull(DemoRun.parseOrbit(","));
        assertNull(DemoRun.parseOrbit(",,"));
    }

    @Test
    void jumpScriptVaultsTheTrainingRangeShockGrateWithoutTakingDamage() {
        // The same setup MainGame gives the "jump" script.
        Levels.Stage range = Levels.trainingRange();
        GameSimulation simulation = new GameSimulation(range.map,
            new PlayerLoadout(100f, 0f, Difficulty.NORMAL, true, 16, WeaponSlot.SCATTERGUN),
            range.completeOnAllTargets);
        simulation.player.position.set(9.8f, 15.5f);
        simulation.player.look(0f, -14f);

        DemoRun demo = DemoRun.forScript("jump");
        PlayerInput input = new PlayerInput();
        boolean wasOverTheGrate = false;
        for (int frame = 0; frame < 240; frame++) {
            demo.nextFrame(input);
            simulation.update(DemoRun.FRAME_TIME, input);
            if (range.map.isHazardAt(simulation.player.position.x, simulation.player.position.y)) {
                wasOverTheGrate = true;
                assertTrue(simulation.player.getJumpHeight() > GameSimulation.HAZARD_CLEARANCE_HEIGHT,
                    "airborne over the grate on frame " + (frame + 1));
            }
        }

        assertTrue(wasOverTheGrate, "the run crosses the grate");
        assertTrue(simulation.player.position.x > 13f, "the run ends past the grate");
        assertEquals(0f, simulation.getDamageTaken(), 0f);
    }

    @Test
    void frameRenderedDefersZeroOrNegativeBackBufferUntilFirstPositiveFrameAtOrAfterCaptureFrame() {
        DemoRun demo = new DemoRun("out.png", 3, "");
        PlayerInput input = new PlayerInput();
        List<FakeCapture> captures = new ArrayList<>();
        AtomicInteger written = new AtomicInteger();
        AtomicInteger completed = new AtomicInteger();

        // Frames 1 and 2 are prior to captureFrame=3 and must not capture even with positive dimensions.
        for (int f = 1; f <= 2; f++) {
            demo.nextFrame(input);
            assertFalse(demo.frameRendered(640, 480, FakeCapture::new, c -> c.pixels,
                c -> written.incrementAndGet(), completed::incrementAndGet));
        }
        assertEquals(0, captures.size());
        assertEquals(0, written.get());
        assertEquals(0, completed.get());

        // Frame 3 (== captureFrame) with 0x0 or negative dimensions defers without capturing or exiting.
        demo.nextFrame(input);
        Graphics previousGraphics = Gdx.graphics;
        try {
            Gdx.graphics = backBufferStub(0, 0);
            assertDoesNotThrow(() -> demo.frameRendered());
        } finally {
            Gdx.graphics = previousGraphics;
        }
        for (int[] invalid : new int[][] {{0, 0}, {0, 480}, {640, 0}, {-320, 240}, {320, -240}}) {
            assertFalse(demo.frameRendered(invalid[0], invalid[1],
                (w, h) -> {
                    FakeCapture c = new FakeCapture(w, h);
                    captures.add(c);
                    return c;
                },
                c -> c.pixels, c -> written.incrementAndGet(), completed::incrementAndGet));
        }
        assertEquals(0, captures.size());
        assertEquals(0, written.get());
        assertEquals(0, completed.get());

        // Frame 4 (> captureFrame) still minimized (0x0): continues to defer.
        demo.nextFrame(input);
        assertFalse(demo.frameRendered(0, 0,
            (w, h) -> {
                FakeCapture c = new FakeCapture(w, h);
                captures.add(c);
                return c;
            },
            c -> c.pixels, c -> written.incrementAndGet(), completed::incrementAndGet));
        assertEquals(0, captures.size());

        // Frame 5 (> captureFrame) restored to 2x2: captures once, forces alpha=0xFF, writes, disposes, and exits.
        demo.nextFrame(input);
        assertTrue(demo.frameRendered(2, 2,
            (w, h) -> {
                FakeCapture c = new FakeCapture(w, h);
                captures.add(c);
                return c;
            },
            c -> c.pixels,
            c -> {
                assertEquals(0, c.disposeCount, "pixmap must remain alive during writePNG");
                written.incrementAndGet();
            },
            completed::incrementAndGet));

        assertEquals(1, captures.size());
        FakeCapture captured = captures.get(0);
        assertEquals(2, captured.width);
        assertEquals(2, captured.height);
        assertEquals(1, captured.disposeCount);
        assertEquals(1, written.get());
        assertEquals(1, completed.get());
        for (int p = 0; p < 4; p++) {
            int base = p * 4;
            assertEquals((byte) (p + 1), captured.pixels.get(base));
            assertEquals((byte) (p + 11), captured.pixels.get(base + 1));
            assertEquals((byte) (p + 21), captured.pixels.get(base + 2));
            assertEquals((byte) 0xFF, captured.pixels.get(base + 3));
        }

        // Frame 6 after capture succeeded must not capture or exit again.
        demo.nextFrame(input);
        assertFalse(demo.frameRendered(2, 2,
            (w, h) -> {
                FakeCapture c = new FakeCapture(w, h);
                captures.add(c);
                return c;
            },
            c -> c.pixels, c -> written.incrementAndGet(), completed::incrementAndGet));
        assertEquals(1, captures.size());
        assertEquals(1, written.get());
        assertEquals(1, completed.get());
    }

    @Test
    void frameRenderedAlwaysDisposesPixmapInFinallyWhenWritePngOrPixelAccessThrows() {
        DemoRun demo = new DemoRun("/nonexistent/dir/shot.png", 1, "");
        demo.nextFrame(new PlayerInput());
        List<FakeCapture> captures = new ArrayList<>();
        AtomicInteger completed = new AtomicInteger();
        GdxRuntimeException writeError = new GdxRuntimeException("Error writing PNG: /nonexistent/dir/shot.png");

        GdxRuntimeException thrown = assertThrows(GdxRuntimeException.class, () ->
            demo.frameRendered(4, 3,
                (w, h) -> {
                    FakeCapture c = new FakeCapture(w, h);
                    captures.add(c);
                    return c;
                },
                c -> c.pixels,
                c -> {
                    throw writeError;
                },
                completed::incrementAndGet));

        assertSame(writeError, thrown);
        assertEquals(1, captures.size());
        assertEquals(1, captures.get(0).disposeCount, "native Pixmap must be disposed when writePNG throws");
        assertEquals(0, completed.get(), "app must not report clean save/exit when writePNG failed");

        // Also verify disposal if pixel buffer extraction itself fails.
        IllegalStateException pixelError = new IllegalStateException("corrupt buffer");
        IllegalStateException thrownPixel = assertThrows(IllegalStateException.class, () ->
            demo.frameRendered(2, 2,
                (w, h) -> {
                    FakeCapture c = new FakeCapture(w, h);
                    captures.add(c);
                    return c;
                },
                c -> {
                    throw pixelError;
                },
                c -> {},
                completed::incrementAndGet));

        assertSame(pixelError, thrownPixel);
        assertEquals(2, captures.size());
        assertEquals(1, captures.get(1).disposeCount, "native Pixmap must be disposed if pixel handling throws");
        assertEquals(0, completed.get());
    }

    @Test
    void fromEnvironmentTrimsPathsAndScriptsAndIgnoresBlankScreenshotOrFrameSilently() {
        try (LogCapture capture = LogCapture.of(DemoRun.class, Level.WARN)) {
            assertNull(DemoRun.fromEnvironment(null, null, null, null));
            assertNull(DemoRun.fromEnvironment("", "10", "breach", "30"));
            assertNull(DemoRun.fromEnvironment("   \t\n", "10", "breach", "30"));

            DemoRun trimmed = DemoRun.fromEnvironment("  build/shot.png \n", "   ", "  Breach \t", "   ");
            assertNotNull(trimmed);
            assertEquals("build/shot.png", trimmed.getPath());
            assertEquals(DemoRun.DEFAULT_FRAME, trimmed.getCaptureFrame());
            assertEquals("breach", trimmed.getScript());
            assertNull(trimmed.getViewmodelOrbit());

            DemoRun configured = DemoRun.fromEnvironment("shot.png", " 24 ", "   ", " 45 , -15 ");
            assertNotNull(configured);
            assertEquals("shot.png", configured.getPath());
            assertEquals(24, configured.getCaptureFrame());
            assertEquals("", configured.getScript());
            assertArrayEquals(new float[] {45f, -15f}, configured.getViewmodelOrbit(), 1e-6f);

            DemoRun forTrimmedScript = DemoRun.forScript("  Jump \n");
            assertEquals("jump", forTrimmedScript.getScript());
            PlayerInput input = new PlayerInput();
            forTrimmedScript.nextFrame(input);
            assertEquals(1f, input.moveForward, 1e-6f);

            assertEquals(List.of(), capture.messages(Level.WARN), "blank or padded valid env vars must not warn");

            DemoRun badFrame = DemoRun.fromEnvironment("shot.png", "not-an-int", "action", null);
            assertNotNull(badFrame);
            assertEquals(DemoRun.DEFAULT_FRAME, badFrame.getCaptureFrame());
            assertEquals(
                List.of("GAMEX_SCREENSHOT_FRAME is not a whole number: 'not-an-int'; using " + DemoRun.DEFAULT_FRAME),
                capture.messages(Level.WARN));
        }
    }

    @Test
    void parseOrbitRejectsTrailingCommasExtraComponentsAndNonFiniteAnglesWithWarning() {
        try (LogCapture capture = LogCapture.of(DemoRun.class, Level.WARN)) {
            for (String blank : new String[] {null, "", " ", "   \t\n"}) {
                assertNull(DemoRun.parseOrbit(blank), "blank orbit '" + blank + "'");
            }
            assertEquals(List.of(), capture.messages(Level.WARN), "blank orbit must not log a warning");
        }

        String[] malformedOrNonFinite = {
            "30,", "30, ", ",15", "30,15,0", "30,15,", ",", ",,",
            "NaN", "90,NaN", "NaN,15", "Infinity", "-Infinity,15", "90,Infinity", "90,-Infinity", "1e39,0"
        };
        for (String bad : malformedOrNonFinite) {
            try (LogCapture capture = LogCapture.of(DemoRun.class, Level.WARN)) {
                assertNull(DemoRun.parseOrbit(bad), "expected null for '" + bad + "'");
                assertEquals(
                    List.of("GAMEX_VIEWMODEL_ORBIT is not 'yaw' or 'yaw,pitch' in degrees: '" + bad + "'"),
                    capture.messages(Level.WARN),
                    "warning for '" + bad + "'");
            }
        }
    }

    private static Graphics backBufferStub(int backBufferWidth, int backBufferHeight) {
        return (Graphics) Proxy.newProxyInstance(
            Graphics.class.getClassLoader(),
            new Class<?>[] {Graphics.class},
            (proxy, method, args) -> {
                switch (method.getName()) {
                    case "getBackBufferWidth":
                        return backBufferWidth;
                    case "getBackBufferHeight":
                        return backBufferHeight;
                    default:
                        Class<?> rt = method.getReturnType();
                        if (!rt.isPrimitive()) return null;
                        if (rt == boolean.class) return false;
                        if (rt == int.class) return 0;
                        if (rt == long.class) return 0L;
                        if (rt == float.class) return 0f;
                        if (rt == double.class) return 0d;
                        if (rt == short.class) return (short) 0;
                        if (rt == byte.class) return (byte) 0;
                        if (rt == char.class) return '\0';
                        return null;
                }
            });
    }

    private static final class FakeCapture implements Disposable {
        final int width;
        final int height;
        final ByteBuffer pixels;
        int disposeCount;

        FakeCapture(int width, int height) {
            this.width = width;
            this.height = height;
            this.pixels = ByteBuffer.allocate(width * height * 4);
            for (int p = 0; p < width * height; p++) {
                pixels.put(p * 4, (byte) (p + 1));
                pixels.put(p * 4 + 1, (byte) (p + 11));
                pixels.put(p * 4 + 2, (byte) (p + 21));
                pixels.put(p * 4 + 3, (byte) 0x20);
            }
        }

        @Override
        public void dispose() {
            disposeCount++;
        }
    }
}

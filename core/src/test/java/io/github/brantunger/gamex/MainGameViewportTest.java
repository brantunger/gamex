package io.github.brantunger.gamex;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;

import com.badlogic.gdx.Gdx;
import com.badlogic.gdx.Graphics;
import com.badlogic.gdx.graphics.GL20;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.lang.reflect.Proxy;

class MainGameViewportTest {

    private Graphics previousGraphics;
    private GL20 previousGl;
    private GL20 previousGl20;

    @BeforeEach
    void saveGdxState() {
        previousGraphics = Gdx.graphics;
        previousGl = Gdx.gl;
        previousGl20 = Gdx.gl20;
    }

    @AfterEach
    void restoreGdxState() {
        Gdx.graphics = previousGraphics;
        Gdx.gl = previousGl;
        Gdx.gl20 = previousGl20;
    }

    private static Graphics graphicsStub(int logicalWidth, int logicalHeight, int backBufferWidth,
        int backBufferHeight, boolean fullscreen) {
        return (Graphics) Proxy.newProxyInstance(
            Graphics.class.getClassLoader(),
            new Class<?>[] {Graphics.class},
            (proxy, method, args) -> {
                switch (method.getName()) {
                    case "getWidth":
                        return logicalWidth;
                    case "getHeight":
                        return logicalHeight;
                    case "getBackBufferWidth":
                        return backBufferWidth;
                    case "getBackBufferHeight":
                        return backBufferHeight;
                    case "isFullscreen":
                        return fullscreen;
                    default:
                        return defaultValue(method.getReturnType());
                }
            });
    }

    private static final class ViewportRecorder {
        int calls;
        int[] lastArgs;
        final GL20 gl;

        ViewportRecorder() {
            gl = (GL20) Proxy.newProxyInstance(
                GL20.class.getClassLoader(),
                new Class<?>[] {GL20.class},
                (proxy, method, args) -> {
                    if ("glViewport".equals(method.getName()) && args != null && args.length == 4) {
                        calls++;
                        lastArgs = new int[] {(int) args[0], (int) args[1], (int) args[2], (int) args[3]};
                        return null;
                    }
                    return defaultValue(method.getReturnType());
                });
        }
    }

    private static Object defaultValue(Class<?> returnType) {
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

    @Test
    void resizeUpdatesGlViewportToFullBackBufferOnLargeFullscreenResolution() {
        ViewportRecorder recorder = new ViewportRecorder();
        Gdx.graphics = graphicsStub(3840, 2160, 3840, 2160, true);
        Gdx.gl = recorder.gl;
        Gdx.gl20 = recorder.gl;

        MainGame game = new MainGame(1280, 720);
        game.resize(3840, 2160);

        assertEquals(1, recorder.calls);
        assertArrayEquals(new int[] {0, 0, 3840, 2160}, recorder.lastArgs);
        assertEquals(1280, game.getWindowedWidth());
        assertEquals(720, game.getWindowedHeight());
    }

    @Test
    void resizeUsesPhysicalBackBufferPixelsOnHiDpiDisplay() {
        ViewportRecorder recorder = new ViewportRecorder();
        Gdx.graphics = graphicsStub(1920, 1080, 3840, 2160, true);
        Gdx.gl = recorder.gl;
        Gdx.gl20 = recorder.gl;

        MainGame game = new MainGame(1280, 720);
        game.syncDisplayViewport();

        assertEquals(1, recorder.calls);
        assertArrayEquals(new int[] {0, 0, 3840, 2160}, recorder.lastArgs);
    }

    @Test
    void windowedResizeUpdatesWindowedFallbackAndIgnoresMinimizedZeroSize() {
        ViewportRecorder recorder = new ViewportRecorder();
        Gdx.graphics = graphicsStub(1600, 900, 1600, 900, false);
        Gdx.gl = recorder.gl;
        Gdx.gl20 = recorder.gl;

        MainGame game = new MainGame();
        game.resize(1600, 900);

        assertEquals(1, recorder.calls);
        assertArrayEquals(new int[] {0, 0, 1600, 900}, recorder.lastArgs);
        assertEquals(1600, game.getWindowedWidth());
        assertEquals(900, game.getWindowedHeight());

        game.resize(0, 0);
        assertEquals(1, recorder.calls, "minimized zero size must not clobber glViewport");
        assertEquals(1600, game.getWindowedWidth());
        assertEquals(900, game.getWindowedHeight());
    }
}

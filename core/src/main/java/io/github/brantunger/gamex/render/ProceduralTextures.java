package io.github.brantunger.gamex.render;

import com.badlogic.gdx.graphics.Pixmap;
import com.badlogic.gdx.graphics.Texture;
import com.badlogic.gdx.utils.Array;
import com.badlogic.gdx.utils.Disposable;

import java.util.function.BiConsumer;
import java.util.function.Consumer;
import java.util.function.Function;
import java.util.function.Supplier;

/**
 * Every texture of the game, painted in code at load time. Nothing is read from a file. The textures are small and
 * use nearest-neighbour filtering, which gives the chunky look of the genre.
 */
public final class ProceduralTextures implements Disposable {

    private static final int WALL_SIZE = 32;

    /** The red-brown brick wall, layout character {@code #}. */
    public final Texture brick;
    /** The teal metal panel wall, layout character {@code %}. */
    public final Texture panel;
    /** The grey-green stone block wall, layout character {@code &}. */
    public final Texture stone;
    /** The slate floor tiles. */
    public final Texture floor;
    /** The dark beamed ceiling. */
    public final Texture ceiling;
    /** The pistol's muzzle flash. */
    public final Texture muzzleFlash;
    /** The scorch mark a pistol shot leaves. */
    public final Texture scorch;
    /** The spark of a pistol shot landing. */
    public final Texture spark;
    /** The glowing ball of a blast in flight. */
    public final Texture orb;
    /** The fireball of an explosion. */
    public final Texture explosion;
    /** The glowing extraction pad on exit cells. */
    public final Texture exitPad;
    /** A single white pixel, for drawing flat rectangles. */
    public final Texture white;

    private final Array<Texture> all = new Array<>();

    /** Paints every texture. Needs a GL context. */
    public ProceduralTextures() {
        try {
            brick = upload(paintBrick());
            panel = upload(paintPanel());
            stone = upload(paintStone());
            floor = upload(paintFloor());
            ceiling = upload(paintCeiling());
            muzzleFlash = upload(paintMuzzleFlash());
            scorch = upload(paintScorch());
            spark = upload(paintSpark());
            orb = upload(paintOrb(), Texture.TextureFilter.Linear);
            explosion = upload(paintExplosion());
            exitPad = upload(paintExitPad());
            white = upload(paintWhite());
        } catch (Throwable t) {
            try {
                dispose();
            } catch (Throwable suppressed) {
                t.addSuppressed(suppressed);
            }
            throw t;
        }
    }

    /**
     * @param wallType a wall type from the map layout
     * @return the texture of that wall type; unknown types get the brick texture
     */
    public Texture wall(char wallType) {
        switch (wallType) {
            case '%':
                return panel;
            case '&':
                return stone;
            default:
                return brick;
        }
    }

    @Override
    public void dispose() {
        disposeAll(all);
    }

    static void disposeAll(Array<? extends Disposable> resources) {
        if (resources == null) return;
        for (int i = 0; i < resources.size; i++) {
            Disposable resource = resources.get(i);
            if (resource != null) {
                try {
                    resource.dispose();
                } catch (RuntimeException ignored) {
                    // Isolate cleanup so one failing texture disposal never skips the remaining textures.
                }
            }
        }
        resources.clear();
    }

    /** Turns a pixmap into a nearest-filtered texture and disposes of the pixmap. */
    private Texture upload(Pixmap pixmap) {
        return upload(pixmap, Texture.TextureFilter.Nearest);
    }

    private Texture upload(Pixmap pixmap, Texture.TextureFilter filter) {
        return uploadPixmap(pixmap, all, Texture::new, texture -> texture.setFilter(filter, filter));
    }

    static <P extends Disposable, T extends Disposable> T uploadPixmap(
        P pixmap,
        Array<T> tracked,
        Function<P, T> textureFactory,
        Consumer<T> configure) {
        if (pixmap == null) {
            throw new IllegalArgumentException("pixmap must not be null");
        }
        Throwable primary = null;
        try {
            T texture = textureFactory.apply(pixmap);
            if (texture != null) {
                if (configure != null) {
                    try {
                        configure.accept(texture);
                    } catch (Throwable t) {
                        disposeSuppressing(texture, t);
                        throw t;
                    }
                }
                if (tracked != null) {
                    tracked.add(texture);
                }
            }
            return texture;
        } catch (Throwable t) {
            primary = t;
            throw t;
        } finally {
            if (primary != null) {
                disposeSuppressing(pixmap, primary);
            } else {
                pixmap.dispose();
            }
        }
    }

    static <P extends Disposable, T extends Disposable> Array<T> uploadAll(
        Iterable<? extends Supplier<P>> painters,
        Function<P, T> textureFactory,
        BiConsumer<Integer, T> configure) {
        Array<T> tracked = new Array<>();
        try {
            int index = 0;
            for (Supplier<P> painter : painters) {
                final int current = index++;
                uploadPixmap(painter.get(), tracked, textureFactory,
                    configure != null ? texture -> configure.accept(current, texture) : null);
            }
            return tracked;
        } catch (Throwable t) {
            try {
                disposeAll(tracked);
            } catch (Throwable suppressed) {
                t.addSuppressed(suppressed);
            }
            throw t;
        }
    }

    private static void disposeSuppressing(Disposable resource, Throwable primary) {
        if (resource == null) return;
        try {
            resource.dispose();
        } catch (Throwable suppressed) {
            primary.addSuppressed(suppressed);
        }
    }

    private static Pixmap paintWhite() {
        Pixmap pixel = blank(1, 1);
        try {
            pixel.drawPixel(0, 0, 0xFFFFFFFF);
            return pixel;
        } catch (Throwable t) {
            pixel.dispose();
            throw t;
        }
    }

    private static Pixmap blank(int width, int height) {
        Pixmap pixmap = new Pixmap(width, height, Pixmap.Format.RGBA8888);
        pixmap.setBlending(Pixmap.Blending.None);
        return pixmap;
    }

    /** Packs a colour, clamping each channel to 0..255. */
    private static int rgba(float r, float g, float b, float a) {
        return (channel(r) << 24) | (channel(g) << 16) | (channel(b) << 8) | channel(a);
    }

    private static int rgb(float r, float g, float b) {
        return rgba(r, g, b, 255f);
    }

    private static int channel(float value) {
        return Math.max(0, Math.min(255, Math.round(value)));
    }

    /** A repeatable pseudo-random value from 0 up to 1 for a pair of integers. */
    private static float noise(int a, int b) {
        int h = a * 374761393 + b * 668265263;
        h = (h ^ (h >>> 13)) * 1274126177;
        h ^= h >>> 16;
        return (h & 0xFFFF) / 65536f;
    }

    private static void rect(Pixmap pixmap, int x, int y, int width, int height, int colour) {
        pixmap.setColor(colour);
        pixmap.fillRectangle(x, y, width, height);
    }

    private static Pixmap paintBrick() {
        Pixmap pixmap = blank(WALL_SIZE, WALL_SIZE);
        for (int y = 0; y < WALL_SIZE; y++) {
            int course = y / 8;
            int shift = (course % 2) * 8;
            for (int x = 0; x < WALL_SIZE; x++) {
                int along = (x + shift) % 16;
                int within = y % 8;
                float grain = noise(x, y) * 16f - 8f;
                if (along == 0 || within == 0) {
                    pixmap.drawPixel(x, y, rgb(64f + grain, 58f + grain, 56f + grain));
                    continue;
                }
                float tone = noise(course * 5 + ((x + shift) / 16) % 2, 77) * 36f - 18f + grain;
                if (within == 1) tone += 16f;
                if (within == 7) tone -= 18f;
                pixmap.drawPixel(x, y, rgb(148f + tone, 70f + tone * 0.6f, 50f + tone * 0.5f));
            }
        }
        return pixmap;
    }

    private static Pixmap paintPanel() {
        Pixmap pixmap = blank(WALL_SIZE, WALL_SIZE);
        for (int y = 0; y < WALL_SIZE; y++) {
            for (int x = 0; x < WALL_SIZE; x++) {
                // Brushed metal: the grain runs down each column.
                float tone = noise(x, 3) * 14f - 7f + noise(x, y) * 6f;
                boolean seam = y == 15 || y == 16;
                if (x == 0 || y == 0 || y == 17) tone += 30f;
                if (x == WALL_SIZE - 1 || y == WALL_SIZE - 1 || y == 14) tone -= 30f;
                if (seam) tone -= 45f;
                pixmap.drawPixel(x, y, rgb(54f + tone, 88f + tone, 100f + tone));
            }
        }
        int rivet = rgb(150f, 176f, 184f);
        int[][] rivets = {{3, 3}, {27, 3}, {3, 11}, {27, 11}, {3, 20}, {27, 20}, {3, 27}, {27, 27}};
        for (int[] at : rivets) {
            rect(pixmap, at[0], at[1], 2, 2, rivet);
            pixmap.drawPixel(at[0] + 1, at[1] + 1, rgb(30f, 50f, 58f));
        }
        // A lit strip across the lower plate.
        rect(pixmap, 8, 22, 16, 4, rgb(22f, 40f, 46f));
        rect(pixmap, 9, 23, 14, 2, rgb(96f, 232f, 214f));
        return pixmap;
    }

    private static Pixmap paintStone() {
        Pixmap pixmap = blank(WALL_SIZE, WALL_SIZE);
        int[] courseTop = {0, 11, 21, 32};
        for (int course = 0; course < 3; course++) {
            // Each course is cut into blocks at its own places.
            int cutA = 6 + (int) (noise(course, 11) * 8f);
            int cutB = cutA + 9 + (int) (noise(course, 23) * 8f);
            for (int y = courseTop[course]; y < courseTop[course + 1]; y++) {
                for (int x = 0; x < WALL_SIZE; x++) {
                    int blockIndex = x < cutA ? 0 : x < cutB ? 1 : 2;
                    float grain = noise(x, y) * 22f - 11f;
                    boolean mortar = y == courseTop[course] || x == cutA || x == cutB || x == 0;
                    if (mortar) {
                        pixmap.drawPixel(x, y, rgb(40f + grain * 0.5f, 44f + grain * 0.5f, 40f + grain * 0.5f));
                        continue;
                    }
                    float tone = noise(course * 3 + blockIndex, 5) * 30f - 15f + grain;
                    if (y == courseTop[course] + 1) tone += 14f;
                    pixmap.drawPixel(x, y, rgb(98f + tone, 108f + tone, 92f + tone));
                }
            }
        }
        // A few cracks.
        int crack = rgb(46f, 50f, 44f);
        for (int i = 0; i < 6; i++) {
            pixmap.drawPixel(18 + i / 2, 3 + i, crack);
            pixmap.drawPixel(9 - i / 3, 23 + i, crack);
        }
        return pixmap;
    }

    private static Pixmap paintFloor() {
        Pixmap pixmap = blank(WALL_SIZE, WALL_SIZE);
        for (int y = 0; y < WALL_SIZE; y++) {
            for (int x = 0; x < WALL_SIZE; x++) {
                float grain = noise(x, y + 100) * 18f - 9f;
                boolean grout = x % 16 == 0 || y % 16 == 0;
                float tone = grout ? -26f : noise(x / 16, y / 16 + 9) * 14f + grain;
                if (!grout && (x % 16 == 1 || y % 16 == 1)) tone += 10f;
                pixmap.drawPixel(x, y, rgb(70f + tone, 72f + tone, 82f + tone));
            }
        }
        return pixmap;
    }

    private static Pixmap paintCeiling() {
        Pixmap pixmap = blank(WALL_SIZE, WALL_SIZE);
        for (int y = 0; y < WALL_SIZE; y++) {
            for (int x = 0; x < WALL_SIZE; x++) {
                float tone = noise(x + 50, y) * 14f - 7f;
                boolean beam = x % 16 < 3;
                if (beam) tone += x % 16 == 0 ? 26f : 14f;
                else if (y % 8 == 0) tone -= 14f;
                pixmap.drawPixel(x, y, rgb(72f + tone, 62f + tone, 58f + tone));
            }
        }
        return pixmap;
    }

    private static Pixmap paintMuzzleFlash() {
        int size = 32;
        Pixmap pixmap = blank(size, size);
        for (int y = 0; y < size; y++) {
            for (int x = 0; x < size; x++) {
                float dx = (x + 0.5f - size / 2f) / (size / 2f);
                float dy = (y + 0.5f - size / 2f) / (size / 2f);
                float distance = (float) Math.sqrt(dx * dx + dy * dy);
                double angle = Math.atan2(dy, dx);
                // Six uneven spikes.
                float reach = 0.4f + 0.58f * (float) Math.pow(Math.abs(Math.cos(angle * 3.0 + 0.5)), 4.0);
                if (distance >= reach) continue;
                float heat = 1f - distance / reach;
                pixmap.drawPixel(x, y, rgba(255f, 190f + 65f * heat, 60f + 195f * heat * heat,
                    Math.min(1f, heat * 2.2f) * 255f));
            }
        }
        return pixmap;
    }

    private static Pixmap paintScorch() {
        int size = 16;
        Pixmap pixmap = blank(size, size);
        for (int y = 0; y < size; y++) {
            for (int x = 0; x < size; x++) {
                float dx = (x + 0.5f - size / 2f) / (size / 2f);
                float dy = (y + 0.5f - size / 2f) / (size / 2f);
                float distance = (float) Math.sqrt(dx * dx + dy * dy) + (noise(x, y + 700) - 0.5f) * 0.3f;
                if (distance >= 1f) continue;
                float depth = 1f - distance;
                pixmap.drawPixel(x, y, rgba(20f, 16f, 14f, Math.min(1f, depth * 1.8f) * 235f));
            }
        }
        return pixmap;
    }

    private static Pixmap paintSpark() {
        int size = 16;
        Pixmap pixmap = blank(size, size);
        for (int y = 0; y < size; y++) {
            for (int x = 0; x < size; x++) {
                float dx = Math.abs(x + 0.5f - size / 2f) / (size / 2f);
                float dy = Math.abs(y + 0.5f - size / 2f) / (size / 2f);
                // A four-pointed star: bright where either offset is small.
                float heat = 1f - (float) Math.sqrt(dx * dx + dy * dy) - Math.min(dx, dy) * 2.2f;
                if (heat <= 0f) continue;
                pixmap.drawPixel(x, y, rgba(255f, 214f + 41f * heat, 110f + 145f * heat,
                    Math.min(1f, heat * 2.5f) * 255f));
            }
        }
        return pixmap;
    }

    private static Pixmap paintOrb() {
        int size = 64;
        Pixmap pixmap = blank(size, size);
        for (int y = 0; y < size; y++) {
            for (int x = 0; x < size; x++) {
                float dx = (x + 0.5f - size / 2f) / (size / 2f);
                float dy = (y + 0.5f - size / 2f) / (size / 2f);
                float distance = (float) Math.sqrt(dx * dx + dy * dy);
                if (distance >= 1f) continue;
                float glow = (1f - distance) * (1f - distance);
                float coreT = Math.max(0f, 1f - distance / 0.42f);
                float core = coreT * coreT * (3f - 2f * coreT);
                float bright = Math.max(glow, core);
                pixmap.drawPixel(x, y, rgba(155f + 100f * bright, 75f + 180f * bright,
                    255f, Math.min(1f, glow * 2.1f + core * 0.95f) * 255f));
            }
        }
        return pixmap;
    }

    private static Pixmap paintExplosion() {
        int size = 64;
        Pixmap pixmap = blank(size, size);
        for (int y = 0; y < size; y++) {
            for (int x = 0; x < size; x++) {
                float dx = (x + 0.5f - size / 2f) / (size / 2f);
                float dy = (y + 0.5f - size / 2f) / (size / 2f);
                float distance = (float) Math.sqrt(dx * dx + dy * dy);
                double angle = Math.atan2(dy, dx);
                // A ragged edge.
                float reach = 0.78f + 0.11f * (float) Math.sin(angle * 5.0 + 1.0)
                    + 0.09f * (float) Math.sin(angle * 11.0 + 2.0);
                if (distance >= reach) continue;
                float heat = 1f - distance / reach;
                // Bands of colour, from a white core out to deep violet.
                float step = (float) Math.floor(heat * 5f) / 4f;
                pixmap.drawPixel(x, y, rgba(110f + 145f * step, 40f + 215f * step * step, 200f + 55f * step,
                    Math.min(1f, 0.45f + heat * 1.6f) * 255f));
            }
        }
        return pixmap;
    }

    private static Pixmap paintExitPad() {
        int size = 32;
        Pixmap pixmap = blank(size, size);
        for (int y = 0; y < size; y++) {
            for (int x = 0; x < size; x++) {
                float dx = (x + 0.5f - size / 2f) / (size / 2f);
                float dy = (y + 0.5f - size / 2f) / (size / 2f);
                float distance = (float) Math.sqrt(dx * dx + dy * dy);
                if (distance >= 1f) continue;
                float ringA = Math.max(0f, 1f - Math.abs(distance - 0.82f) * 9f);
                float ringB = Math.max(0f, 1f - Math.abs(distance - 0.52f) * 9f);
                float core = distance < 0.28f ? 1f - distance / 0.28f : 0f;
                float glow = Math.max(Math.max(ringA, ringB), core * 1.1f);
                float base = (1f - distance) * 0.35f;
                float alpha = Math.min(1f, glow + base);
                pixmap.drawPixel(x, y, rgba(90f + 150f * glow, 220f + 35f * core, 255f, alpha * 255f));
            }
        }
        return pixmap;
    }
}

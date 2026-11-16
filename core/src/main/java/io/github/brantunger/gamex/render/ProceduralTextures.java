package io.github.brantunger.gamex.render;

import com.badlogic.gdx.graphics.Pixmap;
import com.badlogic.gdx.graphics.Texture;
import com.badlogic.gdx.utils.Array;
import com.badlogic.gdx.utils.Disposable;

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
    /** The first-person pistol and the hand holding it. */
    public final Texture pistol;
    /** The pistol's muzzle flash. */
    public final Texture muzzleFlash;
    /** The canister target. */
    public final Texture canister;
    /** The ring board target. */
    public final Texture ringBoard;
    /** The scorch mark a pistol shot leaves. */
    public final Texture scorch;
    /** The spark of a pistol shot landing. */
    public final Texture spark;
    /** The glowing ball of a blast in flight. */
    public final Texture orb;
    /** The fireball of an explosion. */
    public final Texture explosion;
    /** A single white pixel, for drawing flat rectangles. */
    public final Texture white;

    private final Array<Texture> all = new Array<>();

    /** Paints every texture. Needs a GL context. */
    public ProceduralTextures() {
        brick = upload(paintBrick());
        panel = upload(paintPanel());
        stone = upload(paintStone());
        floor = upload(paintFloor());
        ceiling = upload(paintCeiling());
        pistol = upload(paintPistol());
        muzzleFlash = upload(paintMuzzleFlash());
        canister = upload(paintCanister());
        ringBoard = upload(paintRingBoard());
        scorch = upload(paintScorch());
        spark = upload(paintSpark());
        orb = upload(paintOrb());
        explosion = upload(paintExplosion());
        Pixmap pixel = blank(1, 1);
        pixel.drawPixel(0, 0, 0xFFFFFFFF);
        white = upload(pixel);
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
        for (Texture texture : all) texture.dispose();
        all.clear();
    }

    /** Turns a pixmap into a nearest-filtered texture and disposes of the pixmap. */
    private Texture upload(Pixmap pixmap) {
        Texture texture = new Texture(pixmap);
        texture.setFilter(Texture.TextureFilter.Nearest, Texture.TextureFilter.Nearest);
        pixmap.dispose();
        all.add(texture);
        return texture;
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

    /** A filled rectangle with a one pixel border. */
    private static void block(Pixmap pixmap, int x, int y, int width, int height, int fill, int border) {
        rect(pixmap, x, y, width, height, border);
        rect(pixmap, x + 1, y + 1, width - 2, height - 2, fill);
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

    /** The pistol seen from behind and above, held in a gloved hand, on a transparent background. */
    private static Pixmap paintPistol() {
        Pixmap pixmap = blank(64, 64);
        int outline = rgb(16f, 16f, 22f);
        // Sleeve.
        block(pixmap, 23, 50, 22, 14, rgb(44f, 52f, 78f), outline);
        rect(pixmap, 24, 51, 20, 2, rgb(70f, 80f, 112f));
        // Front sight, then the slide with a highlight along its top.
        block(pixmap, 31, 7, 4, 6, rgb(60f, 62f, 72f), outline);
        block(pixmap, 26, 12, 14, 26, rgb(112f, 118f, 132f), outline);
        rect(pixmap, 30, 13, 6, 24, rgb(160f, 168f, 184f));
        rect(pixmap, 32, 13, 2, 24, rgb(204f, 210f, 222f));
        for (int y = 26; y < 36; y += 2) {
            rect(pixmap, 27, y, 2, 1, rgb(70f, 74f, 86f));
            rect(pixmap, 37, y, 2, 1, rgb(70f, 74f, 86f));
        }
        // Rear sight posts and the hammer.
        rect(pixmap, 27, 13, 3, 3, rgb(34f, 36f, 44f));
        rect(pixmap, 36, 13, 3, 3, rgb(34f, 36f, 44f));
        block(pixmap, 30, 35, 6, 6, rgb(52f, 54f, 64f), outline);
        // Gloved hand around the grip, with finger creases and a thumb.
        block(pixmap, 21, 38, 24, 14, rgb(202f, 122f, 54f), outline);
        rect(pixmap, 22, 39, 22, 2, rgb(232f, 156f, 84f));
        for (int y = 42; y < 51; y += 3) rect(pixmap, 22, y, 9, 1, rgb(140f, 78f, 32f));
        block(pixmap, 40, 31, 6, 12, rgb(202f, 122f, 54f), outline);
        rect(pixmap, 41, 32, 2, 9, rgb(232f, 156f, 84f));
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

    private static Pixmap paintCanister() {
        Pixmap pixmap = blank(32, 44);
        int outline = rgb(14f, 30f, 30f);
        // Cap and body, shaded as a cylinder lit from the front.
        block(pixmap, 12, 1, 8, 6, rgb(128f, 136f, 140f), outline);
        rect(pixmap, 5, 6, 22, 37, outline);
        for (int y = 7; y < 42; y++) {
            for (int x = 6; x < 26; x++) {
                float round = (float) Math.cos((x - 15.5f) / 10.5f * Math.PI * 0.5);
                float light = 0.45f + 0.55f * round + (noise(x, y + 300) - 0.5f) * 0.06f;
                boolean band = y < 10 || y > 38 || (y >= 23 && y <= 25);
                if (band) pixmap.drawPixel(x, y, rgb(40f * light, 70f * light, 76f * light));
                else pixmap.drawPixel(x, y, rgb(56f * light, 210f * light, 180f * light));
            }
        }
        // A pale diamond emblem on the upper half.
        for (int i = -3; i <= 3; i++) {
            int half = 3 - Math.abs(i);
            rect(pixmap, 16 - half, 16 + i, half * 2, 1, rgb(232f, 244f, 206f));
        }
        return pixmap;
    }

    private static Pixmap paintRingBoard() {
        Pixmap pixmap = blank(32, 52);
        int outline = rgb(20f, 16f, 30f);
        // Post and foot.
        block(pixmap, 14, 26, 4, 24, rgb(120f, 116f, 108f), outline);
        block(pixmap, 8, 48, 16, 4, rgb(92f, 88f, 84f), outline);
        // The round board with its rings.
        for (int y = 0; y < 31; y++) {
            for (int x = 0; x < 32; x++) {
                float dx = x + 0.5f - 16f;
                float dy = y + 0.5f - 15.5f;
                float distance = (float) Math.sqrt(dx * dx + dy * dy);
                if (distance > 15f) continue;
                float grain = (noise(x, y + 500) - 0.5f) * 14f;
                if (distance > 13.8f) pixmap.drawPixel(x, y, outline);
                else if (distance > 10.5f) pixmap.drawPixel(x, y, rgb(232f + grain, 230f + grain, 218f + grain));
                else if (distance > 7f) pixmap.drawPixel(x, y, rgb(104f + grain, 70f + grain, 196f + grain));
                else if (distance > 3.5f) pixmap.drawPixel(x, y, rgb(232f + grain, 230f + grain, 218f + grain));
                else pixmap.drawPixel(x, y, rgb(226f + grain, 62f + grain, 142f + grain));
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
        int size = 32;
        Pixmap pixmap = blank(size, size);
        for (int y = 0; y < size; y++) {
            for (int x = 0; x < size; x++) {
                float dx = (x + 0.5f - size / 2f) / (size / 2f);
                float dy = (y + 0.5f - size / 2f) / (size / 2f);
                float distance = (float) Math.sqrt(dx * dx + dy * dy);
                if (distance >= 1f) continue;
                float glow = (1f - distance) * (1f - distance);
                float core = distance < 0.35f ? 1f : 0f;
                pixmap.drawPixel(x, y, rgba(150f + 105f * Math.max(glow, core), 70f + 185f * Math.max(glow, core),
                    255f, Math.min(1f, glow * 2.4f + core) * 255f));
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
}

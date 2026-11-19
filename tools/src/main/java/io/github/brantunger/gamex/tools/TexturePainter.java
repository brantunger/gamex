package io.github.brantunger.gamex.tools;

/**
 * Paints the wall, floor and effect textures of the game pixel by pixel. {@link GenerateAllTextures} writes each one
 * to a {@code .png} file in {@code assets/textures/}. The painting uses {@link StrictMath} for the rounded
 * functions, so every JDK and CPU paints the same pixels.
 */
final class TexturePainter {

    private static final int WALL_SIZE = 32;

    private TexturePainter() {}

    /** A painted image: RGBA8888 pixels in rows from the top, each packed as {@code 0xRRGGBBAA}. */
    static final class Image {
        final int width;
        final int height;
        final int[] rgba;

        Image(int width, int height) {
            this.width = width;
            this.height = height;
            this.rgba = new int[width * height];
        }

        /** Sets one pixel; a pixel outside the image is ignored. */
        void drawPixel(int x, int y, int colour) {
            if (x < 0 || y < 0 || x >= width || y >= height) return;
            rgba[y * width + x] = colour;
        }
    }

    private static Image blank(int width, int height) {
        return new Image(width, height);
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

    private static void rect(Image image, int x, int y, int width, int height, int colour) {
        for (int row = y; row < y + height; row++) {
            for (int col = x; col < x + width; col++) {
                image.drawPixel(col, row, colour);
            }
        }
    }

    static Image paintBrick() {
        Image image = blank(WALL_SIZE, WALL_SIZE);
        for (int y = 0; y < WALL_SIZE; y++) {
            int course = y / 8;
            int shift = (course % 2) * 8;
            for (int x = 0; x < WALL_SIZE; x++) {
                int along = (x + shift) % 16;
                int within = y % 8;
                float grain = noise(x, y) * 16f - 8f;
                if (along == 0 || within == 0) {
                    image.drawPixel(x, y, rgb(64f + grain, 58f + grain, 56f + grain));
                    continue;
                }
                float tone = noise(course * 5 + ((x + shift) / 16) % 2, 77) * 36f - 18f + grain;
                if (within == 1) tone += 16f;
                if (within == 7) tone -= 18f;
                image.drawPixel(x, y, rgb(148f + tone, 70f + tone * 0.6f, 50f + tone * 0.5f));
            }
        }
        return image;
    }

    static Image paintPanel() {
        Image image = blank(WALL_SIZE, WALL_SIZE);
        for (int y = 0; y < WALL_SIZE; y++) {
            for (int x = 0; x < WALL_SIZE; x++) {
                // Brushed metal: the grain runs down each column.
                float tone = noise(x, 3) * 14f - 7f + noise(x, y) * 6f;
                boolean seam = y == 15 || y == 16;
                if (x == 0 || y == 0 || y == 17) tone += 30f;
                if (x == WALL_SIZE - 1 || y == WALL_SIZE - 1 || y == 14) tone -= 30f;
                if (seam) tone -= 45f;
                image.drawPixel(x, y, rgb(54f + tone, 88f + tone, 100f + tone));
            }
        }
        int rivet = rgb(150f, 176f, 184f);
        int[][] rivets = {{3, 3}, {27, 3}, {3, 11}, {27, 11}, {3, 20}, {27, 20}, {3, 27}, {27, 27}};
        for (int[] at : rivets) {
            rect(image, at[0], at[1], 2, 2, rivet);
            image.drawPixel(at[0] + 1, at[1] + 1, rgb(30f, 50f, 58f));
        }
        // A lit strip across the lower plate.
        rect(image, 8, 22, 16, 4, rgb(22f, 40f, 46f));
        rect(image, 9, 23, 14, 2, rgb(96f, 232f, 214f));
        return image;
    }

    static Image paintStone() {
        Image image = blank(WALL_SIZE, WALL_SIZE);
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
                        image.drawPixel(x, y, rgb(40f + grain * 0.5f, 44f + grain * 0.5f, 40f + grain * 0.5f));
                        continue;
                    }
                    float tone = noise(course * 3 + blockIndex, 5) * 30f - 15f + grain;
                    if (y == courseTop[course] + 1) tone += 14f;
                    image.drawPixel(x, y, rgb(98f + tone, 108f + tone, 92f + tone));
                }
            }
        }
        // A few cracks.
        int crack = rgb(46f, 50f, 44f);
        for (int i = 0; i < 6; i++) {
            image.drawPixel(18 + i / 2, 3 + i, crack);
            image.drawPixel(9 - i / 3, 23 + i, crack);
        }
        return image;
    }

    static Image paintFloor() {
        Image image = blank(WALL_SIZE, WALL_SIZE);
        for (int y = 0; y < WALL_SIZE; y++) {
            for (int x = 0; x < WALL_SIZE; x++) {
                float grain = noise(x, y + 100) * 18f - 9f;
                boolean grout = x % 16 == 0 || y % 16 == 0;
                float tone = grout ? -26f : noise(x / 16, y / 16 + 9) * 14f + grain;
                if (!grout && (x % 16 == 1 || y % 16 == 1)) tone += 10f;
                image.drawPixel(x, y, rgb(70f + tone, 72f + tone, 82f + tone));
            }
        }
        return image;
    }

    static Image paintCeiling() {
        Image image = blank(WALL_SIZE, WALL_SIZE);
        for (int y = 0; y < WALL_SIZE; y++) {
            for (int x = 0; x < WALL_SIZE; x++) {
                float tone = noise(x + 50, y) * 14f - 7f;
                boolean beam = x % 16 < 3;
                if (beam) tone += x % 16 == 0 ? 26f : 14f;
                else if (y % 8 == 0) tone -= 14f;
                image.drawPixel(x, y, rgb(72f + tone, 62f + tone, 58f + tone));
            }
        }
        return image;
    }

    static Image paintMuzzleFlash() {
        int size = 32;
        Image image = blank(size, size);
        for (int y = 0; y < size; y++) {
            for (int x = 0; x < size; x++) {
                float dx = (x + 0.5f - size / 2f) / (size / 2f);
                float dy = (y + 0.5f - size / 2f) / (size / 2f);
                float distance = (float) StrictMath.sqrt(dx * dx + dy * dy);
                double angle = StrictMath.atan2(dy, dx);
                // Six uneven spikes.
                float reach = 0.4f
                    + 0.58f * (float) StrictMath.pow(Math.abs(StrictMath.cos(angle * 3.0 + 0.5)), 4.0);
                if (distance >= reach) continue;
                float heat = 1f - distance / reach;
                image.drawPixel(x, y, rgba(255f, 190f + 65f * heat, 60f + 195f * heat * heat,
                    Math.min(1f, heat * 2.2f) * 255f));
            }
        }
        return image;
    }

    static Image paintScorch() {
        int size = 16;
        Image image = blank(size, size);
        for (int y = 0; y < size; y++) {
            for (int x = 0; x < size; x++) {
                float dx = (x + 0.5f - size / 2f) / (size / 2f);
                float dy = (y + 0.5f - size / 2f) / (size / 2f);
                float distance = (float) StrictMath.sqrt(dx * dx + dy * dy) + (noise(x, y + 700) - 0.5f) * 0.3f;
                if (distance >= 1f) continue;
                float depth = 1f - distance;
                image.drawPixel(x, y, rgba(20f, 16f, 14f, Math.min(1f, depth * 1.8f) * 235f));
            }
        }
        return image;
    }

    static Image paintSpark() {
        int size = 16;
        Image image = blank(size, size);
        for (int y = 0; y < size; y++) {
            for (int x = 0; x < size; x++) {
                float dx = Math.abs(x + 0.5f - size / 2f) / (size / 2f);
                float dy = Math.abs(y + 0.5f - size / 2f) / (size / 2f);
                // A four-pointed star: bright where either offset is small.
                float heat = 1f - (float) StrictMath.sqrt(dx * dx + dy * dy) - Math.min(dx, dy) * 2.2f;
                if (heat <= 0f) continue;
                image.drawPixel(x, y, rgba(255f, 214f + 41f * heat, 110f + 145f * heat,
                    Math.min(1f, heat * 2.5f) * 255f));
            }
        }
        return image;
    }

    static Image paintOrb() {
        int size = 64;
        Image image = blank(size, size);
        for (int y = 0; y < size; y++) {
            for (int x = 0; x < size; x++) {
                float dx = (x + 0.5f - size / 2f) / (size / 2f);
                float dy = (y + 0.5f - size / 2f) / (size / 2f);
                float distance = (float) StrictMath.sqrt(dx * dx + dy * dy);
                if (distance >= 1f) continue;
                float glow = (1f - distance) * (1f - distance);
                float coreT = Math.max(0f, 1f - distance / 0.42f);
                float core = coreT * coreT * (3f - 2f * coreT);
                float bright = Math.max(glow, core);
                image.drawPixel(x, y, rgba(155f + 100f * bright, 75f + 180f * bright,
                    255f, Math.min(1f, glow * 2.1f + core * 0.95f) * 255f));
            }
        }
        return image;
    }

    static Image paintExplosion() {
        int size = 64;
        Image image = blank(size, size);
        for (int y = 0; y < size; y++) {
            for (int x = 0; x < size; x++) {
                float dx = (x + 0.5f - size / 2f) / (size / 2f);
                float dy = (y + 0.5f - size / 2f) / (size / 2f);
                float distance = (float) StrictMath.sqrt(dx * dx + dy * dy);
                double angle = StrictMath.atan2(dy, dx);
                // A ragged edge.
                float reach = 0.78f + 0.11f * (float) StrictMath.sin(angle * 5.0 + 1.0)
                    + 0.09f * (float) StrictMath.sin(angle * 11.0 + 2.0);
                if (distance >= reach) continue;
                float heat = 1f - distance / reach;
                // Bands of colour, from a white core out to deep violet.
                float step = (float) StrictMath.floor(heat * 5f) / 4f;
                image.drawPixel(x, y, rgba(110f + 145f * step, 40f + 215f * step * step, 200f + 55f * step,
                    Math.min(1f, 0.45f + heat * 1.6f) * 255f));
            }
        }
        return image;
    }

    static Image paintExitPad() {
        int size = 32;
        Image image = blank(size, size);
        for (int y = 0; y < size; y++) {
            for (int x = 0; x < size; x++) {
                float dx = (x + 0.5f - size / 2f) / (size / 2f);
                float dy = (y + 0.5f - size / 2f) / (size / 2f);
                float distance = (float) StrictMath.sqrt(dx * dx + dy * dy);
                if (distance >= 1f) continue;
                float ringA = Math.max(0f, 1f - Math.abs(distance - 0.82f) * 9f);
                float ringB = Math.max(0f, 1f - Math.abs(distance - 0.52f) * 9f);
                float core = distance < 0.28f ? 1f - distance / 0.28f : 0f;
                float glow = Math.max(Math.max(ringA, ringB), core * 1.1f);
                float base = (1f - distance) * 0.35f;
                float alpha = Math.min(1f, glow + base);
                image.drawPixel(x, y, rgba(90f + 150f * glow, 220f + 35f * core, 255f, alpha * 255f));
            }
        }
        return image;
    }
}

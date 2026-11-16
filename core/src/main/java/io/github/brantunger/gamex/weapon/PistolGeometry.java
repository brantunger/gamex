package io.github.brantunger.gamex.weapon;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;

/**
 * The first-person pistol, modelled in code as plain float arrays with no graphics types, so the winding and normals
 * can be checked without a GL context. The hand that holds it is a sculpted model: see {@link HandModel}.
 *
 * <p>Gun space: the barrel points along negative z, y is up and x is to the right of the shooter; the origin is the
 * middle of the underside of the slide. Lengths are in view units, roughly metres. Every solid is closed and convex:
 * a prism. Its triangles run counter-clockwise when seen from
 * outside, and each vertex carries the flat normal of its face. A vertex is {@link #VERTEX_SIZE} floats: position
 * x, y, z, normal x, y, z, colour r, g, b, and a gloss from 0 (matt) to 1 (polished metal).
 *
 * <p>The model is in two groups, because the slide moves on its own when the pistol fires. The names of the slide
 * group's solids all start with "slide".
 */
public final class PistolGeometry {

    /** The number of floats per vertex. */
    public static final int VERTEX_SIZE = 10;
    /** The x of the muzzle's centre in gun space. */
    public static final float MUZZLE_X = 0f;
    /** The y of the muzzle's centre in gun space. */
    public static final float MUZZLE_Y = 0.0125f;
    /** The z of the muzzle's face in gun space. */
    public static final float MUZZLE_Z = -0.111f;

    /** The groups the model is split into. */
    public enum Group {
        /** The frame, barrel and grip, which move together. */
        BODY,
        /** The slide with its sights, which also travels back along z on each shot. */
        SLIDE
    }

    /** One closed convex solid of the model: a run of triangles in its group's vertex array. */
    public static final class Solid {
        /** What the solid is, for messages. */
        public final String name;
        /** The group whose array holds the solid. */
        public final Group group;
        /** The index of the solid's first vertex in that array. */
        public final int firstVertex;
        /** The number of vertices, three per triangle. */
        public final int vertexCount;

        Solid(String name, Group group, int firstVertex, int vertexCount) {
            this.name = name;
            this.group = group;
            this.firstVertex = firstVertex;
            this.vertexCount = vertexCount;
        }
    }

    /** A colour with a gloss: red, green, blue, gloss. */
    private static final float[] GUNMETAL = {0.23f, 0.25f, 0.30f, 0.6f};
    private static final float[] GUNMETAL_TOP = {0.28f, 0.30f, 0.36f, 1f};
    private static final float[] DARK_METAL = {0.08f, 0.09f, 0.11f, 0.4f};
    private static final float[] STEEL = {0.60f, 0.62f, 0.68f, 1f};
    private static final float[] BORE = {0.02f, 0.02f, 0.03f, 0f};
    private static final float[] BRASS = {0.95f, 0.66f, 0.20f, 0.6f};
    private static final float[] SIGHT_DOT = {0.88f, 0.90f, 0.86f, 0f};
    private static final float[] POLYMER = {0.34f, 0.29f, 0.21f, 0f};
    private static final float[] POLYMER_DARK = {0.24f, 0.205f, 0.15f, 0f};
    private static final float[] STIPPLE = {0.41f, 0.355f, 0.26f, 0f};

    private static final int AXIS_X = 0;
    private static final int AXIS_Z = 2;

    /** The triangles of the {@link Group#BODY} group. */
    public final float[] body;
    /** The triangles of the {@link Group#SLIDE} group. */
    public final float[] slide;
    /** Every solid of the model, in build order. */
    public final List<Solid> solids;

    private final FloatList bodyList = new FloatList();
    private final FloatList slideList = new FloatList();
    private final List<Solid> solidList = new ArrayList<>();

    private PistolGeometry() {
        buildSlide();
        buildFrame();
        buildGrip();
        body = bodyList.toArray();
        slide = slideList.toArray();
        solids = Collections.unmodifiableList(solidList);
    }

    /** @return the pistol, built from scratch. */
    public static PistolGeometry build() {
        return new PistolGeometry();
    }

    /**
     * @param group a group
     * @return that group's triangles
     */
    public float[] vertices(Group group) {
        return group == Group.SLIDE ? slide : body;
    }

    private void buildSlide() {
        // The slide in two bands: flat flanks with a chamfered underside, and a top whose edges are bevelled twice.
        prism(Group.SLIDE, "slide", AXIS_Z, new float[] {
            -0.0125f, 0f, 0.0125f, 0f, 0.014f, 0.002f, 0.014f, 0.017f, -0.014f, 0.017f, -0.014f, 0.002f
        }, -0.105f, 0.082f, GUNMETAL);
        prism(Group.SLIDE, "slide top", AXIS_Z, new float[] {
            -0.014f, 0.017f, 0.014f, 0.017f, 0.0125f, 0.0225f, 0.009f, 0.0262f, 0.005f, 0.0275f,
            -0.005f, 0.0275f, -0.009f, 0.0262f, -0.0125f, 0.0225f
        }, -0.105f, 0.082f, GUNMETAL_TOP);
        // Grip serrations at the rear and a shorter set at the front, standing a little proud of both flanks.
        for (int i = 0; i < 7; i++) {
            float z = 0.034f + i * 0.0065f;
            box(Group.SLIDE, "slide serration " + i, -0.0147f, 0.0147f, 0.003f, 0.0165f, z, z + 0.003f, DARK_METAL);
        }
        for (int i = 0; i < 4; i++) {
            float z = -0.094f + i * 0.0065f;
            box(Group.SLIDE, "slide front serration " + i, -0.0147f, 0.0147f, 0.003f, 0.0165f, z, z + 0.003f,
                DARK_METAL);
        }
        box(Group.SLIDE, "slide top groove", -0.003f, 0.003f, 0.0270f, 0.0281f, -0.086f, 0.060f, DARK_METAL);
        // A ramped front sight with a brass bead, and a notched rear sight with two pale dots.
        prism(Group.SLIDE, "slide front sight", AXIS_X, new float[] {
            0.0275f, -0.102f, 0.0275f, -0.090f, 0.0345f, -0.090f, 0.0345f, -0.094f
        }, -0.0022f, 0.0022f, DARK_METAL);
        box(Group.SLIDE, "slide front sight bead", -0.0013f, 0.0013f, 0.0305f, 0.0338f, -0.0900f, -0.0894f, BRASS);
        box(Group.SLIDE, "slide rear sight base", -0.0105f, 0.0105f, 0.0275f, 0.0305f, 0.0655f, 0.0795f,
            DARK_METAL);
        box(Group.SLIDE, "slide rear sight left", -0.0105f, -0.0033f, 0.0305f, 0.0362f, 0.0655f, 0.0795f,
            DARK_METAL);
        box(Group.SLIDE, "slide rear sight right", 0.0033f, 0.0105f, 0.0305f, 0.0362f, 0.0655f, 0.0795f,
            DARK_METAL);
        box(Group.SLIDE, "slide rear sight dot left", -0.0083f, -0.0055f, 0.0320f, 0.0348f, 0.0795f, 0.0800f,
            SIGHT_DOT);
        box(Group.SLIDE, "slide rear sight dot right", 0.0055f, 0.0083f, 0.0320f, 0.0348f, 0.0795f, 0.0800f,
            SIGHT_DOT);
        box(Group.SLIDE, "slide ejection port", 0.006f, 0.0143f, 0.011f, 0.0172f, -0.030f, 0.004f, DARK_METAL);
        box(Group.SLIDE, "slide back plate", -0.006f, 0.006f, 0.005f, 0.019f, 0.082f, 0.0827f, DARK_METAL);
    }

    private void buildFrame() {
        // The barrel stays put when the slide moves, so it shows when the slide is back.
        prism(Group.BODY, "barrel", AXIS_Z, ring(MUZZLE_X, MUZZLE_Y, 0.0078f, 8), -0.110f, 0.010f, STEEL);
        prism(Group.BODY, "bore", AXIS_Z, ring(MUZZLE_X, MUZZLE_Y, 0.0046f, 8), MUZZLE_Z, -0.110f, BORE);
        // Side profiles are (y, z) pairs, extruded across x. The polymer frame's nose is cut back underneath.
        prism(Group.BODY, "frame", AXIS_X, new float[] {
            0f, -0.092f, 0f, 0.082f, -0.018f, 0.082f, -0.018f, -0.080f, -0.009f, -0.092f
        }, -0.013f, 0.013f, POLYMER);
        for (int i = 0; i < 3; i++) {
            float z = -0.078f + i * 0.012f;
            box(Group.BODY, "rail slot " + i, -0.009f, 0.009f, -0.0215f, -0.018f, z, z + 0.007f, POLYMER_DARK);
        }
        box(Group.BODY, "stop lever", -0.0148f, -0.013f, -0.008f, -0.003f, -0.036f, -0.006f, DARK_METAL);
        prism(Group.BODY, "magazine release", AXIS_X, ring(-0.027f, 0.027f, 0.0032f, 8), -0.0152f, -0.013f,
            DARK_METAL);
        // The trigger guard is a bar bent round a rounded front corner: one short solid per bend.
        float[] outer = {-0.018f, -0.043f, -0.036f, -0.044f, -0.046f, -0.037f, -0.050f, -0.025f, -0.050f, 0.010f,
            -0.045f, 0.027f};
        float[] inner = {-0.018f, -0.039f, -0.034f, -0.040f, -0.042f, -0.034f, -0.0455f, -0.024f, -0.0455f, 0.009f,
            -0.041f, 0.024f};
        for (int i = 0; i < 5; i++) {
            prism(Group.BODY, "trigger guard " + i, AXIS_X, new float[] {
                inner[i * 2], inner[i * 2 + 1], inner[i * 2 + 2], inner[i * 2 + 3],
                outer[i * 2 + 2], outer[i * 2 + 3], outer[i * 2], outer[i * 2 + 1]
            }, -0.0055f, 0.0055f, POLYMER);
        }
        // A curved trigger in two pieces.
        prism(Group.BODY, "trigger", AXIS_X, new float[] {
            -0.018f, -0.013f, -0.018f, -0.007f, -0.028f, -0.004f, -0.028f, -0.009f
        }, -0.003f, 0.003f, DARK_METAL);
        prism(Group.BODY, "trigger tip", AXIS_X, new float[] {
            -0.028f, -0.009f, -0.028f, -0.004f, -0.037f, -0.0055f, -0.037f, -0.0095f
        }, -0.003f, 0.003f, DARK_METAL);
        // A rounded spur hammer and a beavertail that curls down over the hand.
        prism(Group.BODY, "hammer", AXIS_X, new float[] {
            0.002f, 0.082f, 0.014f, 0.084f, 0.019f, 0.090f, 0.017f, 0.095f, 0.008f, 0.092f, 0.002f, 0.088f
        }, -0.004f, 0.004f, DARK_METAL);
        prism(Group.BODY, "beavertail", AXIS_X, new float[] {
            -0.004f, 0.080f, -0.004f, 0.094f, -0.007f, 0.100f, -0.011f, 0.102f, -0.016f, 0.098f, -0.018f, 0.082f
        }, -0.011f, 0.011f, POLYMER);
    }

    private void buildGrip() {
        // The grip is raked back, with a back strap that swells into the palm.
        prism(Group.BODY, "grip", AXIS_X, new float[] {
            -0.018f, 0.022f, -0.018f, 0.082f, -0.045f, 0.093f, -0.075f, 0.104f, -0.105f, 0.110f, -0.105f, 0.050f
        }, -0.013f, 0.013f, POLYMER);
        prism(Group.BODY, "magazine plate", AXIS_X, new float[] {
            -0.105f, 0.046f, -0.105f, 0.113f, -0.112f, 0.115f, -0.112f, 0.048f
        }, -0.0145f, 0.0145f, POLYMER_DARK);
        // Stippling: a chequered field of small studs on both sides, following the rake.
        for (int row = 0; row < 11; row++) {
            float y = -0.030f - row * 0.0066f;
            for (int column = 0; column < 6; column++) {
                float z = frontStrap(y) + 0.008f + column * 0.0068f;
                float[] colour = (row + column) % 2 == 0 ? STIPPLE : POLYMER_DARK;
                box(Group.BODY, "stipple left " + row + "/" + column, -0.0146f, -0.013f, y - 0.0036f, y, z,
                    z + 0.0036f, colour);
                box(Group.BODY, "stipple right " + row + "/" + column, 0.013f, 0.0146f, y - 0.0036f, y, z,
                    z + 0.0036f, colour);
            }
        }
    }

    /** @return the z of the front of the grip at a height, following the grip's rake. */
    private static float frontStrap(float y) {
        return 0.022f + (-0.018f - y) * (0.050f - 0.022f) / (0.105f - 0.018f);
    }

    /** @return the corners of a regular polygon, as an outline for {@link #prism}. */
    private static float[] ring(float centreU, float centreV, float radius, int sides) {
        float[] outline = new float[sides * 2];
        for (int i = 0; i < sides; i++) {
            double angle = (i + 0.5) * 2.0 * Math.PI / sides;
            outline[i * 2] = centreU + radius * (float) Math.cos(angle);
            outline[i * 2 + 1] = centreV + radius * (float) Math.sin(angle);
        }
        return outline;
    }

    private void box(Group group, String name, float x0, float x1, float y0, float y1, float z0, float z1,
                     float[] colour) {
        prism(group, name, AXIS_Z, new float[] {x0, y0, x1, y0, x1, y1, x0, y1}, z0, z1, colour);
    }

    /**
     * Adds a prism: a convex outline swept along an axis.
     * @param axis {@link #AXIS_Z}, with the outline given as (x, y) pairs, or {@link #AXIS_X}, with (y, z) pairs
     * @param outline the corners of the convex outline, in either direction round it
     * @param from one end of the sweep along the axis
     * @param to the other end
     */
    private void prism(Group group, String name, int axis, float[] outline, float from, float to, float[] colour) {
        int corners = outline.length / 2;
        float low = Math.min(from, to);
        float high = Math.max(from, to);
        // Work with the outline counter-clockwise as seen from the positive end of the axis.
        boolean reversed = signedArea(outline) < 0f;
        float[][] lowLoop = new float[corners][3];
        float[][] highLoop = new float[corners][3];
        for (int i = 0; i < corners; i++) {
            int corner = reversed ? corners - 1 - i : i;
            float u = outline[corner * 2];
            float v = outline[corner * 2 + 1];
            if (axis == AXIS_X) {
                // (y, z, x) keeps the turning sense of (u, v) about the axis.
                lowLoop[i] = new float[] {low, u, v};
                highLoop[i] = new float[] {high, u, v};
            } else {
                lowLoop[i] = new float[] {u, v, low};
                highLoop[i] = new float[] {u, v, high};
            }
        }
        loft(group, name, lowLoop, highLoop, colour);
    }

    /**
     * Adds the solid between two flat loops of the same number of corners. Both loops must run counter-clockwise as
     * seen from beyond the high loop, looking back at the low one.
     */
    private void loft(Group group, String name, float[][] lowLoop, float[][] highLoop, float[] colour) {
        FloatList out = group == Group.SLIDE ? slideList : bodyList;
        int first = out.size / VERTEX_SIZE;
        int corners = lowLoop.length;
        // The two caps, as fans.
        for (int i = 1; i < corners - 1; i++) {
            out.triangle(highLoop[0], highLoop[i], highLoop[i + 1], colour);
            out.triangle(lowLoop[0], lowLoop[i + 1], lowLoop[i], colour);
        }
        // The sides, two triangles per edge of the loops.
        for (int i = 0; i < corners; i++) {
            int next = (i + 1) % corners;
            out.triangle(highLoop[i], lowLoop[i], lowLoop[next], colour);
            out.triangle(highLoop[i], lowLoop[next], highLoop[next], colour);
        }
        solidList.add(new Solid(name, group, first, out.size / VERTEX_SIZE - first));
    }

    private static float signedArea(float[] outline) {
        float twiceArea = 0f;
        int corners = outline.length / 2;
        for (int i = 0; i < corners; i++) {
            int next = (i + 1) % corners;
            twiceArea += outline[i * 2] * outline[next * 2 + 1] - outline[next * 2] * outline[i * 2 + 1];
        }
        return twiceArea * 0.5f;
    }

    /** A growing float array that knows how to append a flat-shaded triangle. */
    private static final class FloatList {
        private float[] items = new float[VERTEX_SIZE * 4096];
        private int size;

        void triangle(float[] a, float[] b, float[] c, float[] colour) {
            float ux = b[0] - a[0];
            float uy = b[1] - a[1];
            float uz = b[2] - a[2];
            float vx = c[0] - a[0];
            float vy = c[1] - a[1];
            float vz = c[2] - a[2];
            float nx = uy * vz - uz * vy;
            float ny = uz * vx - ux * vz;
            float nz = ux * vy - uy * vx;
            float length = (float) Math.sqrt(nx * nx + ny * ny + nz * nz);
            nx /= length;
            ny /= length;
            nz /= length;
            vertex(a, nx, ny, nz, colour);
            vertex(b, nx, ny, nz, colour);
            vertex(c, nx, ny, nz, colour);
        }

        private void vertex(float[] position, float nx, float ny, float nz, float[] colour) {
            if (size + VERTEX_SIZE > items.length) items = Arrays.copyOf(items, items.length * 2);
            items[size++] = position[0];
            items[size++] = position[1];
            items[size++] = position[2];
            items[size++] = nx;
            items[size++] = ny;
            items[size++] = nz;
            items[size++] = colour[0];
            items[size++] = colour[1];
            items[size++] = colour[2];
            items[size++] = colour[3];
        }

        float[] toArray() {
            return Arrays.copyOf(items, size);
        }
    }
}

package io.github.brantunger.gamex.tools;

import com.badlogic.gdx.math.Matrix4;
import com.badlogic.gdx.math.Vector3;

import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Deque;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;

/**
 * Shared procedural geometry DSL and deterministic Wavefront OBJ serializer for {@code gamex}. Supports a
 * {@link Matrix4} transform stack, automatic bounding-box/outline winding correction, hierarchical {@code # @part}
 * pivots, named {@code # @socket} markers, {@code # @accent} shader materials, and clean flat-vs-smooth normal
 * separation.
 */
public final class MeshBuilder {

    /** Extrusion axis constants for {@link #prism}. */
    public static final int AXIS_X = 0;
    public static final int AXIS_Y = 1;
    public static final int AXIS_Z = 2;

    private final List<String> headerComments = new ArrayList<>();
    private final Set<String> accentMaterials = new LinkedHashSet<>();
    private final Map<String, PartDecl> parts = new LinkedHashMap<>();
    private final List<SocketDecl> sockets = new ArrayList<>();
    private final List<GroupEntry> entries = new ArrayList<>();
    private final Deque<Matrix4> transformStack = new ArrayDeque<>();
    private final Map<PosKey, Integer> positionPool = new HashMap<>();
    private final Map<PosKey, Integer> normalPool = new HashMap<>();
    private final List<double[]> exportedPositions = new ArrayList<>();
    private final List<double[]> exportedNormals = new ArrayList<>();

    private String currentPart = "root";
    private int faceCount;

    public MeshBuilder() {
        transformStack.push(new Matrix4().idt());
    }

    /** Appends a leading file comment line (without leading {@code # }). */
    public MeshBuilder comment(String line) {
        headerComments.add(line);
        return this;
    }

    /** Marks a material as accent-driven via {@code # @accent <material>}. */
    public MeshBuilder accent(String material) {
        accentMaterials.add(material);
        return this;
    }

    /**
     * Declares (or switches to) an articulated hierarchical part with rest pivot {@code (px, py, pz)}.
     * @param name unique part name
     * @param parent parent part name, or {@code "root"}
     */
    public MeshBuilder part(String name, String parent, float px, float py, float pz) {
        parts.put(name, new PartDecl(name, parent == null || parent.isEmpty() ? "root" : parent, px, py, pz));
        currentPart = name;
        return this;
    }

    /** Selects an already-declared part for subsequent geometry. */
    public MeshBuilder usePart(String name) {
        currentPart = (name == null || name.isEmpty()) ? "root" : name;
        return this;
    }

    /** Declares a named attachment/muzzle/telegraph socket bound to {@code partName} at rest coordinates. */
    public MeshBuilder socket(String name, String partName, float x, float y, float z) {
        sockets.add(new SocketDecl(name, partName == null || partName.isEmpty() ? "root" : partName, x, y, z));
        return this;
    }

    public MeshBuilder pushTransform() {
        transformStack.push(new Matrix4(transformStack.peek()));
        return this;
    }

    public MeshBuilder popTransform() {
        if (transformStack.size() <= 1) {
            throw new IllegalStateException("Transform stack underflow");
        }
        transformStack.pop();
        return this;
    }

    public MeshBuilder translate(float x, float y, float z) {
        transformStack.peek().translate(x, y, z);
        return this;
    }

    public MeshBuilder rotateX(float degrees) {
        transformStack.peek().rotate(Vector3.X, degrees);
        return this;
    }

    public MeshBuilder rotateY(float degrees) {
        transformStack.peek().rotate(Vector3.Y, degrees);
        return this;
    }

    public MeshBuilder rotateZ(float degrees) {
        transformStack.peek().rotate(Vector3.Z, degrees);
        return this;
    }

    public MeshBuilder scale(float sx, float sy, float sz) {
        transformStack.peek().scale(sx, sy, sz);
        return this;
    }

    /**
     * Emits a closed axis-aligned box with automatic bounds sorting and outward winding.
     */
    public MeshBuilder box(String group, String material,
                           float x0, float x1, float y0, float y1, float z0, float z1) {
        float minX = Math.min(x0, x1);
        float maxX = Math.max(x0, x1);
        float minY = Math.min(y0, y1);
        float maxY = Math.max(y0, y1);
        float minZ = Math.min(z0, z1);
        float maxZ = Math.max(z0, z1);
        float[] rect = {
            maxX, maxY,
            minX, maxY,
            minX, minY,
            maxX, minY
        };
        return prism(group, material, AXIS_Z, rect, minZ, maxZ);
    }

    /**
     * Extrudes a 2D outline along {@code axis} from {@code from} to {@code to}. Bounds are sorted automatically and
     * clockwise outlines are reversed so faces always wind counter-clockwise outwards.
     */
    public MeshBuilder prism(String group, String material, int axis, float[] outline, float from, float to) {
        float lo = Math.min(from, to);
        float hi = Math.max(from, to);
        float[] ccw = ensureCcw2D(outline);
        int n = ccw.length / 2;
        double[][] low = new double[n][3];
        double[][] high = new double[n][3];
        for (int i = 0; i < n; i++) {
            float u = ccw[i * 2];
            float v = ccw[i * 2 + 1];
            low[i] = axisPoint(axis, u, v, lo);
            high[i] = axisPoint(axis, u, v, hi);
        }
        return loftFlat(group, material, low, high);
    }

    /**
     * Builds an arbitrary-center vertical frustum/cylinder between {@code y0} and {@code y1}.
     */
    public MeshBuilder frustumY(String group, String material, float cx, float cz, float y0, float y1,
                                float r0, float r1, int sides, boolean smoothSides) {
        float lo = Math.min(y0, y1);
        float hi = Math.max(y0, y1);
        double[][] low = new double[sides][3];
        double[][] high = new double[sides][3];
        for (int i = 0; i < sides; i++) {
            double angle = i * 2.0 * Math.PI / sides;
            double cos = Math.cos(angle);
            double sin = Math.sin(angle);
            low[i] = new double[] {cx + r0 * cos, lo, cz - r0 * sin};
            high[i] = new double[] {cx + r1 * cos, hi, cz - r1 * sin};
        }
        if (!smoothSides) {
            return loftFlat(group, material, low, high);
        }
        Surface surface = beginSurface(group, material);
        int[] loIdx = new int[sides];
        int[] hiIdx = new int[sides];
        for (int i = 0; i < sides; i++) {
            loIdx[i] = surface.addVertex(low[i]);
            hiIdx[i] = surface.addVertex(high[i]);
        }
        for (int i = 0; i < sides; i++) {
            int next = (i + 1) % sides;
            surface.addSmoothFace(loIdx[i], loIdx[next], hiIdx[next], hiIdx[i]);
        }
        surface.addFlatCapFan(loIdx, true);
        surface.addFlatCapFan(hiIdx, false);
        return surface.finish();
    }

    /**
     * Builds a Z-aligned cylinder/frustum centered at {@code (cx, cy)} from {@code z0} to {@code z1}.
     */
    public MeshBuilder cylinderZ(String group, String material, float cx, float cy, float r0, float r1,
                                 float z0, float z1, int sides, boolean smoothSides) {
        pushTransform();
        translate(cx, cy, 0f);
        rotateX(90f);
        frustumY(group, material, 0f, 0f, z0, z1, r0, r1, sides, smoothSides);
        popTransform();
        return this;
    }

    /**
     * Builds an X-aligned cylinder/frustum centered at {@code (cy, cz)} from {@code x0} to {@code x1}.
     */
    public MeshBuilder cylinderX(String group, String material, float cy, float cz, float r0, float r1,
                                 float x0, float x1, int sides, boolean smoothSides) {
        pushTransform();
        translate(0f, cy, cz);
        rotateZ(-90f);
        frustumY(group, material, 0f, 0f, x0, x1, r0, r1, sides, smoothSides);
        popTransform();
        return this;
    }

    /**
     * Builds a UV sphere centered at {@code (cx, cy, cz)}.
     */
    public MeshBuilder sphere(String group, String material, float cx, float cy, float cz,
                              float radius, int slices, int stacks, boolean smooth) {
        Surface surface = beginSurface(group, material);
        int bottomPole = surface.addVertex(new double[] {cx, cy - radius, cz});
        int[][] rings = new int[stacks - 1][slices];
        for (int stack = 1; stack < stacks; stack++) {
            double phi = -Math.PI * 0.5 + Math.PI * stack / stacks;
            double ringY = cy + radius * Math.sin(phi);
            double ringR = radius * Math.cos(phi);
            for (int slice = 0; slice < slices; slice++) {
                double theta = slice * 2.0 * Math.PI / slices;
                double vx = cx + ringR * Math.cos(theta);
                double vz = cz - ringR * Math.sin(theta);
                rings[stack - 1][slice] = surface.addVertex(new double[] {vx, ringY, vz});
            }
        }
        int topPole = surface.addVertex(new double[] {cx, cy + radius, cz});

        int[] firstRing = rings[0];
        for (int s = 0; s < slices; s++) {
            int next = (s + 1) % slices;
            addTri(surface, smooth, bottomPole, firstRing[next], firstRing[s]);
        }
        for (int stack = 0; stack < rings.length - 1; stack++) {
            int[] lo = rings[stack];
            int[] hi = rings[stack + 1];
            for (int s = 0; s < slices; s++) {
                int next = (s + 1) % slices;
                if (smooth) {
                    surface.addSmoothFace(lo[s], lo[next], hi[next], hi[s]);
                } else {
                    surface.addFlatFace(lo[s], lo[next], hi[next], hi[s]);
                }
            }
        }
        int[] lastRing = rings[rings.length - 1];
        for (int s = 0; s < slices; s++) {
            int next = (s + 1) % slices;
            addTri(surface, smooth, topPole, lastRing[s], lastRing[next]);
        }
        return surface.finish();
    }

    private static void addTri(Surface surface, boolean smooth, int a, int b, int c) {
        if (smooth) surface.addSmoothFace(a, b, c);
        else surface.addFlatFace(a, b, c);
    }

    /** Starts a custom smooth/flat surface helper for sculpted skinned meshes such as limbs and gloves. */
    public Surface beginSurface(String group, String material) {
        return new Surface(group, material);
    }

    /** Connects two congruent polygon rings into a closed flat-shaded solid. */
    public MeshBuilder loftFlat(String group, String material, double[][] low, double[][] high) {
        Surface surface = beginSurface(group, material);
        int n = low.length;
        int[] loIdx = new int[n];
        int[] hiIdx = new int[n];
        for (int i = 0; i < n; i++) {
            loIdx[i] = surface.addVertex(low[i]);
            hiIdx[i] = surface.addVertex(high[i]);
        }
        for (int i = 0; i < n; i++) {
            int next = (i + 1) % n;
            surface.addFlatFace(loIdx[i], loIdx[next], hiIdx[next], hiIdx[i]);
        }
        int[] revLow = new int[n];
        for (int i = 0; i < n; i++) revLow[i] = loIdx[n - 1 - i];
        surface.addFlatFace(revLow);
        surface.addFlatFace(hiIdx);
        return surface.finish();
    }

    public int getVertexCount() {
        return exportedPositions.size();
    }

    public int getFaceCount() {
        return faceCount;
    }

    public int getGroupCount() {
        return entries.size();
    }

    /**
     * Returns the value, or positive zero when it would print as zero at the given precision. Without this, a value
     * such as {@code -1e-17} prints as {@code -0.000000}, and whether such values appear depends on the platform's
     * trigonometry, so the committed models would not regenerate identically everywhere.
     */
    static double zeroed(double value, int decimals) {
        return Math.abs(value) < 0.5 * Math.pow(10, -decimals) ? 0.0 : value;
    }

    /** Serializes the complete model deterministically with deduplicated {@code v} and {@code vn} pools. */
    public String toObjString() {
        StringBuilder sb = new StringBuilder();
        for (String c : headerComments) {
            sb.append("# ").append(c).append('\n');
        }
        for (String mat : accentMaterials) {
            sb.append("# @accent ").append(mat).append('\n');
        }
        Set<String> declaredParts = new LinkedHashSet<>();
        for (Map.Entry<String, PartDecl> entry : parts.entrySet()) {
            PartDecl p = entry.getValue();
            sb.append(String.format(Locale.ROOT, "# @part %s parent=%s pivot=%.6f,%.6f,%.6f%n",
                p.name, p.parent, zeroed(p.px, 6), zeroed(p.py, 6), zeroed(p.pz, 6)).replace("\r", ""));
            declaredParts.add(p.name);
        }
        for (SocketDecl s : sockets) {
            sb.append(String.format(Locale.ROOT, "# @socket %s part=%s pos=%.6f,%.6f,%.6f%n",
                s.name, s.part, zeroed(s.x, 6), zeroed(s.y, 6), zeroed(s.z, 6)).replace("\r", ""));
        }
        for (double[] v : exportedPositions) {
            sb.append(String.format(Locale.ROOT, "v %.6f %.6f %.6f%n", zeroed(v[0], 6), zeroed(v[1], 6), zeroed(v[2], 6)).replace("\r", ""));
        }
        for (double[] n : exportedNormals) {
            sb.append(String.format(Locale.ROOT, "vn %.5f %.5f %.5f%n", zeroed(n[0], 5), zeroed(n[1], 5), zeroed(n[2], 5)).replace("\r", ""));
        }
        String emittedPart = "root";
        for (GroupEntry entry : entries) {
            if (!entry.part.equals(emittedPart)) {
                emittedPart = entry.part;
                if (!declaredParts.contains(emittedPart)) {
                    sb.append("# @part ").append(emittedPart).append(" parent=root pivot=0,0,0\n");
                    declaredParts.add(emittedPart);
                } else {
                    sb.append("# @part ").append(emittedPart).append('\n');
                }
            }
            sb.append("g ").append(entry.group).append('\n');
            sb.append("usemtl ").append(entry.material).append('\n');
            for (String face : entry.faces) {
                sb.append(face).append('\n');
            }
        }
        return sb.toString();
    }

    private int internPosition(double[] pos) {
        PosKey key = new PosKey(pos[0], pos[1], pos[2], 1e6);
        Integer existing = positionPool.get(key);
        if (existing != null) return existing;
        int idx = exportedPositions.size() + 1;
        positionPool.put(key, idx);
        exportedPositions.add(pos.clone());
        return idx;
    }

    private int internNormal(double[] normal) {
        double[] unit = normalise(normal);
        PosKey key = new PosKey(unit[0], unit[1], unit[2], 1e5);
        Integer existing = normalPool.get(key);
        if (existing != null) return existing;
        int idx = exportedNormals.size() + 1;
        normalPool.put(key, idx);
        exportedNormals.add(unit);
        return idx;
    }

    /** Surface builder supporting smooth vertex normals and independent hard flat faces/caps within the same group. */
    public final class Surface {
        private final String group;
        private final String material;
        private final List<double[]> localVerts = new ArrayList<>();
        private final List<FaceSpec> localFaces = new ArrayList<>();

        private Surface(String group, String material) {
            this.group = group;
            this.material = material;
        }

        public int addVertex(double[] xyz) {
            int idx = localVerts.size();
            localVerts.add(xyz.clone());
            return idx;
        }

        public void addSmoothFace(int... corners) {
            localFaces.add(new FaceSpec(corners, true));
        }

        public void addFlatFace(int... corners) {
            localFaces.add(new FaceSpec(corners, false));
        }

        /** Closes a perimeter ring with a flat-shaded fan while keeping closed manifold position edge sharing. */
        public void addFlatCapFan(int[] ring, boolean reverseForStart) {
            double[] centre = new double[3];
            for (int idx : ring) {
                double[] v = localVerts.get(idx);
                centre[0] += v[0] / ring.length;
                centre[1] += v[1] / ring.length;
                centre[2] += v[2] / ring.length;
            }
            addFlatCapFanAt(ring, centre, reverseForStart);
        }

        /** Closes a perimeter ring with a flat-shaded fan round {@code apex}. */
        public void addFlatCapFanAt(int[] ring, double[] apex, boolean reverseForStart) {
            int centreIdx = addVertex(apex);
            for (int i = 0; i < ring.length; i++) {
                int a = ring[i];
                int b = ring[(i + 1) % ring.length];
                if (reverseForStart) {
                    addFlatFace(centreIdx, b, a);
                } else {
                    addFlatFace(centreIdx, a, b);
                }
            }
        }

        /** Closes a perimeter ring with smooth-shaded pole triangles round {@code apex} (for organic spheres/caps). */
        public void addSmoothCapFanAt(int[] ring, double[] apex, boolean reverseForStart) {
            int centreIdx = addVertex(apex);
            for (int i = 0; i < ring.length; i++) {
                int a = ring[i];
                int b = ring[(i + 1) % ring.length];
                if (reverseForStart) {
                    addSmoothFace(centreIdx, b, a);
                } else {
                    addSmoothFace(centreIdx, a, b);
                }
            }
        }

        public MeshBuilder finish() {
            Matrix4 m = transformStack.peek();
            boolean flip = matrixDet3(m) < 0.0;
            Vector3 tmp = new Vector3();

            // Transform vertices into model space first so normals & positions are evaluated accurately.
            double[][] transformed = new double[localVerts.size()][3];
            int[] globalVertIndices = new int[localVerts.size()];
            for (int i = 0; i < localVerts.size(); i++) {
                double[] v = localVerts.get(i);
                tmp.set((float) v[0], (float) v[1], (float) v[2]).mul(m);
                transformed[i] = new double[] {tmp.x, tmp.y, tmp.z};
                globalVertIndices[i] = internPosition(transformed[i]);
            }

            // Accumulate area-weighted normals strictly from smooth faces; flat faces get own unshared normal.
            double[][] smoothNormals = new double[localVerts.size()][3];
            boolean[] hasSmooth = new boolean[localVerts.size()];
            double[][] flatFaceNormals = new double[localFaces.size()][];
            for (int f = 0; f < localFaces.size(); f++) {
                FaceSpec spec = localFaces.get(f);
                int[] corners = oriented(spec.corners, flip);
                double[] faceNormalSum = new double[3];
                for (int i = 1; i < corners.length - 1; i++) {
                    double[] a = transformed[corners[0]];
                    double[] b = transformed[corners[i]];
                    double[] c = transformed[corners[i + 1]];
                    double[] n = crossDiff(a, b, c);
                    faceNormalSum[0] += n[0];
                    faceNormalSum[1] += n[1];
                    faceNormalSum[2] += n[2];
                    if (spec.smooth) {
                        for (int idx : new int[] {corners[0], corners[i], corners[i + 1]}) {
                            smoothNormals[idx][0] += n[0];
                            smoothNormals[idx][1] += n[1];
                            smoothNormals[idx][2] += n[2];
                            hasSmooth[idx] = true;
                        }
                    }
                }
                if (!spec.smooth) {
                    flatFaceNormals[f] = faceNormalSum;
                }
            }

            int[] smoothVn = new int[localVerts.size()];
            for (int i = 0; i < localVerts.size(); i++) {
                if (hasSmooth[i]) {
                    smoothVn[i] = internNormal(smoothNormals[i]);
                }
            }

            List<String> lines = new ArrayList<>(localFaces.size());
            StringBuilder line = new StringBuilder();
            for (int f = 0; f < localFaces.size(); f++) {
                FaceSpec spec = localFaces.get(f);
                int[] corners = oriented(spec.corners, flip);
                line.setLength(0);
                line.append('f');
                int flatVn = spec.smooth ? 0 : internNormal(flatFaceNormals[f]);
                for (int corner : corners) {
                    int vIdx = globalVertIndices[corner];
                    int vnIdx = spec.smooth ? smoothVn[corner] : flatVn;
                    line.append(' ').append(vIdx).append("//").append(vnIdx);
                }
                lines.add(line.toString());
            }
            faceCount += lines.size();
            entries.add(new GroupEntry(currentPart, group, material, lines));
            return MeshBuilder.this;
        }
    }

    private static int[] oriented(int[] corners, boolean flip) {
        if (!flip) return corners;
        int[] out = new int[corners.length];
        out[0] = corners[0];
        for (int i = 1; i < corners.length; i++) {
            out[i] = corners[corners.length - i];
        }
        return out;
    }

    private static float[] ensureCcw2D(float[] outline) {
        int n = outline.length / 2;
        double area = 0.0;
        for (int i = 0; i < n; i++) {
            int next = (i + 1) % n;
            area += outline[i * 2] * outline[next * 2 + 1] - outline[next * 2] * outline[i * 2 + 1];
        }
        if (area >= 0.0) return outline;
        float[] out = new float[outline.length];
        for (int i = 0; i < n; i++) {
            int src = (n - 1 - i) * 2;
            out[i * 2] = outline[src];
            out[i * 2 + 1] = outline[src + 1];
        }
        return out;
    }

    private static double[] axisPoint(int axis, float u, float v, float along) {
        switch (axis) {
            case AXIS_X: return new double[] {along, u, v};
            case AXIS_Y: return new double[] {v, along, u};
            case AXIS_Z:
            default: return new double[] {u, v, along};
        }
    }

    private static double[] crossDiff(double[] a, double[] b, double[] c) {
        double ux = b[0] - a[0];
        double uy = b[1] - a[1];
        double uz = b[2] - a[2];
        double vx = c[0] - a[0];
        double vy = c[1] - a[1];
        double vz = c[2] - a[2];
        return new double[] {uy * vz - uz * vy, uz * vx - ux * vz, ux * vy - uy * vx};
    }

    private static double[] normalise(double[] v) {
        double len = Math.sqrt(v[0] * v[0] + v[1] * v[1] + v[2] * v[2]);
        if (len < 1e-12) return new double[] {0.0, 1.0, 0.0};
        return new double[] {v[0] / len, v[1] / len, v[2] / len};
    }

    private static double matrixDet3(Matrix4 m) {
        float[] v = m.val;
        return v[Matrix4.M00] * (v[Matrix4.M11] * v[Matrix4.M22] - v[Matrix4.M12] * v[Matrix4.M21])
            - v[Matrix4.M01] * (v[Matrix4.M10] * v[Matrix4.M22] - v[Matrix4.M12] * v[Matrix4.M20])
            + v[Matrix4.M02] * (v[Matrix4.M10] * v[Matrix4.M21] - v[Matrix4.M11] * v[Matrix4.M20]);
    }

    private static final class PartDecl {
        final String name;
        final String parent;
        final float px;
        final float py;
        final float pz;

        PartDecl(String name, String parent, float px, float py, float pz) {
            this.name = name;
            this.parent = parent;
            this.px = px;
            this.py = py;
            this.pz = pz;
        }
    }

    private static final class SocketDecl {
        final String name;
        final String part;
        final float x;
        final float y;
        final float z;

        SocketDecl(String name, String part, float x, float y, float z) {
            this.name = name;
            this.part = part;
            this.x = x;
            this.y = y;
            this.z = z;
        }
    }

    private static final class GroupEntry {
        final String part;
        final String group;
        final String material;
        final List<String> faces;

        GroupEntry(String part, String group, String material, List<String> faces) {
            this.part = part;
            this.group = group;
            this.material = material;
            this.faces = faces;
        }
    }

    private static final class FaceSpec {
        final int[] corners;
        final boolean smooth;

        FaceSpec(int[] corners, boolean smooth) {
            this.corners = corners;
            this.smooth = smooth;
        }
    }

    private static final class PosKey {
        final long qx;
        final long qy;
        final long qz;
        final int hash;

        PosKey(double x, double y, double z, double scale) {
            this.qx = Math.round(x * scale);
            this.qy = Math.round(y * scale);
            this.qz = Math.round(z * scale);
            this.hash = Arrays.hashCode(new long[] {qx, qy, qz});
        }

        @Override
        public int hashCode() {
            return hash;
        }

        @Override
        public boolean equals(Object obj) {
            if (!(obj instanceof PosKey)) return false;
            PosKey o = (PosKey) obj;
            return qx == o.qx && qy == o.qy && qz == o.qz;
        }
    }
}

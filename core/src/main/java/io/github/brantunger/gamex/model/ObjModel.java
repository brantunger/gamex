package io.github.brantunger.gamex.model;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.regex.Pattern;

/**
 * A triangle mesh read from Wavefront OBJ text. It has no graphics types, so it can be parsed and checked without a
 * GL context.
 *
 * <p>The parser understands the part of the format that a sculpted model needs: {@code v} positions, {@code vn}
 * normals, {@code f} faces of three or more corners written as {@code v}, {@code v/vt}, {@code v//vn} or
 * {@code v/vt/vn} with indices counted from 1 or, when negative, back from the latest, {@code g} and {@code o} group
 * names and {@code usemtl} material names. The three numbers of a {@code v} or {@code vn} line are plain decimals
 * with an optional sign and exponent, such as {@code -0.5}, {@code +2} or {@code 1e-3}; any further words on the line
 * are read past. Indices are whole numbers with an optional sign. {@code vt} lines are counted (with their coordinates
 * read past) so texture indices on face corners are validated with the same rules as vertex and normal indices. Faces
 * with more than three corners are triangulated by ear clipping in the face's plane; self-crossing faces are rejected. A
 * face has a normal on every corner or on none. A face without normals gets the flat normal of the whole face computed
 * from its perimeter (Newell's method), independent of which corner comes first; one with no area at all has no such
 * normal and is an error. {@code mtllib} and {@code s} lines and comments are read past, as is a byte-order mark at the
 * start; lines may end in LF, CRLF or a lone CR. Anything else is an error, so a file that uses a feature this parser
 * lacks is not silently drawn wrong.
 */
public final class ObjModel {

    /** The material name of faces that come before any {@code usemtl} line. */
    public static final String NO_MATERIAL = "";
    /** The group name of faces that come before any {@code g} or {@code o} line. */
    public static final String NO_GROUP = "";
    /** Default hierarchical part name before any {@code # @part} directive. */
    public static final String ROOT_PART = "root";

    /** Named hierarchical articulated part declared with {@code # @part}. */
    public static final class PartSpec {
        /** Unique part identifier. */
        public final String name;
        /** Parent part name, or {@code null} when parented directly to {@link #ROOT_PART}. */
        public final String parent;
        /** Pivot X in model rest coordinates. */
        public final float pivotX;
        /** Pivot Y in model rest coordinates. */
        public final float pivotY;
        /** Pivot Z in model rest coordinates. */
        public final float pivotZ;

        PartSpec(String name, String parent, float pivotX, float pivotY, float pivotZ) {
            this.name = name;
            this.parent = parent;
            this.pivotX = pivotX;
            this.pivotY = pivotY;
            this.pivotZ = pivotZ;
        }
    }

    /** Named attachment point declared with {@code # @socket}. */
    public static final class SocketSpec {
        /** Unique socket identifier (e.g. {@code muzzle}, {@code telegraph}). */
        public final String name;
        /** Owning part identifier whose transform carries this socket. */
        public final String part;
        /** Rest position X in model coordinates. */
        public final float x;
        /** Rest position Y in model coordinates. */
        public final float y;
        /** Rest position Z in model coordinates. */
        public final float z;

        SocketSpec(String name, String part, float x, float y, float z) {
            this.name = name;
            this.part = part;
            this.x = x;
            this.y = y;
            this.z = z;
        }
    }

    /** The character some editors put at the very start of a text file. */
    private static final char BYTE_ORDER_MARK = 0xFEFF;
    /** A plain decimal number. {@code Float.parseFloat} alone would also take {@code 1f} and {@code 0x1p1}. */
    private static final Pattern NUMBER = Pattern.compile("[+-]?([0-9]+(\\.[0-9]*)?|\\.[0-9]+)([eE][+-]?[0-9]+)?");
    /** A whole number. {@code Integer.parseInt} alone would also take digits of other scripts. */
    private static final Pattern INDEX = Pattern.compile("[+-]?[0-9]+");
    /** A vector shorter than this has no direction to speak of. */
    private static final double ZERO_LENGTH = 1e-12;

    private final int vertexCount;
    private final float[] positions;
    private final float[] normals;
    private final String[] materials;
    private final String[] groups;
    private final String[] parts;
    private final Map<String, PartSpec> partSpecs;
    private final Map<String, SocketSpec> socketSpecs;
    private final Set<String> accentMaterials;

    private ObjModel(int vertexCount, float[] positions, float[] normals, String[] materials, String[] groups,
                     String[] parts, Map<String, PartSpec> partSpecs, Map<String, SocketSpec> socketSpecs,
                     Set<String> accentMaterials) {
        this.vertexCount = vertexCount;
        this.positions = positions;
        this.normals = normals;
        this.materials = materials;
        this.groups = groups;
        this.parts = parts;
        this.partSpecs = Collections.unmodifiableMap(partSpecs);
        this.socketSpecs = Collections.unmodifiableMap(socketSpecs);
        this.accentMaterials = Collections.unmodifiableSet(accentMaterials);
    }

    /** @return the number of {@code v} lines in the file: the distinct vertices of the model. */
    public int getVertexCount() {
        return vertexCount;
    }

    /** @return the number of triangles, after faces with more corners have been split. */
    public int getTriangleCount() {
        return materials.length;
    }

    /** @return the corner positions: nine floats per triangle, x, y, z for each of its three corners in order. */
    public float[] getPositions() {
        return positions;
    }

    /** @return the corner normals, laid out like {@link #getPositions()}; each is a unit vector. */
    public float[] getNormals() {
        return normals;
    }

    /**
     * @param triangle the index of a triangle
     * @return the name of its material, or {@link #NO_MATERIAL}
     */
    public String getMaterial(int triangle) {
        return materials[triangle];
    }

    /**
     * @param triangle the index of a triangle
     * @return the name of its group, or {@link #NO_GROUP}
     */
    public String getGroup(int triangle) {
        return groups[triangle];
    }

    /**
     * @param triangle the index of a triangle
     * @return the name of its hierarchical part, or {@link #ROOT_PART}
     */
    public String getPart(int triangle) {
        return parts[triangle];
    }

    /** @return declared parts keyed by name. */
    public Map<String, PartSpec> getPartSpecs() {
        return partSpecs;
    }

    /** @return part metadata for {@code name}, or null if undeclared. */
    public PartSpec getPartSpec(String name) {
        return partSpecs.get(name);
    }

    /** @return declared sockets keyed by name. */
    public Map<String, SocketSpec> getSocketSpecs() {
        return socketSpecs;
    }

    /** @return socket metadata for {@code name}, or null if undeclared. */
    public SocketSpec getSocket(String name) {
        return socketSpecs.get(name);
    }

    /** @return whether {@code material} is marked via {@code # @accent} for shader tinting. */
    public boolean isAccentMaterial(String material) {
        return accentMaterials.contains(material);
    }

    /** @return materials marked via {@code # @accent}. */
    public Set<String> getAccentMaterials() {
        return accentMaterials;
    }

    /**
     * Parses OBJ text.
     * @param text the contents of an OBJ file
     * @return the model
     * @throws IllegalArgumentException if a line is malformed, has among the three numbers of a {@code v} or
     *     {@code vn} one that is not a plain decimal or is too large for a float, refers to a vertex, texture coordinate
     *     or normal that does not exist, is a face with normals on only some of its corners, a face without normals that
     *     has no area or a self-crossing face, or uses a part of the format this parser does not support; the message
     *     names the line
     */
    public static ObjModel parse(String text) {
        List<float[]> vertices = new ArrayList<>();
        List<float[]> vertexNormals = new ArrayList<>();
        FloatList positions = new FloatList();
        FloatList normals = new FloatList();
        List<String> materials = new ArrayList<>();
        List<String> groups = new ArrayList<>();
        List<String> triangleParts = new ArrayList<>();
        Map<String, PartSpec> partSpecs = new LinkedHashMap<>();
        partSpecs.put(ROOT_PART, new PartSpec(ROOT_PART, null, 0f, 0f, 0f));
        Map<String, SocketSpec> socketSpecs = new LinkedHashMap<>();
        Set<String> accentMaterials = new LinkedHashSet<>();
        String material = NO_MATERIAL;
        String group = NO_GROUP;
        String currentPart = ROOT_PART;
        int textureCount = 0;

        // A file may start with a byte-order mark, and may end its lines the Unix, Windows or old Mac way.
        boolean marked = !text.isEmpty() && text.charAt(0) == BYTE_ORDER_MARK;
        String content = marked ? text.substring(1) : text;
        String[] lines = content.split("\r\n|\r|\n", -1);
        for (int number = 1; number <= lines.length; number++) {
            String rawLine = lines[number - 1];
            String trimmedRaw = rawLine.trim();
            if (trimmedRaw.startsWith("# @")) {
                currentPart = parseDirective(trimmedRaw, number, currentPart, partSpecs, socketSpecs, accentMaterials);
                continue;
            }
            int comment = rawLine.indexOf('#');
            String line = comment >= 0 ? rawLine.substring(0, comment) : rawLine;
            line = line.trim();
            if (line.isEmpty()) continue;
            String[] words = line.split("\\s+");
            switch (words[0]) {
                case "v":
                    vertices.add(vector(words, number));
                    break;
                case "vn":
                    vertexNormals.add(unit(vector(words, number), number));
                    break;
                case "vt":
                    textureCount++;
                    break;
                case "f":
                    face(words, number, vertices, textureCount, vertexNormals, positions, normals);
                    for (int i = 0; i < words.length - 3; i++) {
                        materials.add(material);
                        groups.add(group);
                        triangleParts.add(currentPart);
                    }
                    break;
                case "g":
                case "o":
                    group = words.length > 1 ? line.substring(words[0].length()).trim() : NO_GROUP;
                    break;
                case "usemtl":
                    material = words.length > 1 ? line.substring(words[0].length()).trim() : NO_MATERIAL;
                    break;
                case "mtllib":
                case "s":
                    break;
                default:
                    throw new IllegalArgumentException("Line " + number + ": unsupported OBJ statement '" + words[0]
                        + "'");
            }
        }
        return new ObjModel(vertices.size(), positions.toArray(), normals.toArray(),
            materials.toArray(new String[0]), groups.toArray(new String[0]), triangleParts.toArray(new String[0]),
            partSpecs, socketSpecs, accentMaterials);
    }

    private static String parseDirective(String trimmedComment, int number, String currentPart,
                                         Map<String, PartSpec> partSpecs, Map<String, SocketSpec> socketSpecs,
                                         Set<String> accentMaterials) {
        String body = trimmedComment.substring(2).trim();
        String[] tokens = body.split("\\s+");
        if (tokens.length == 0) return currentPart;
        switch (tokens[0]) {
            case "@part": {
                if (tokens.length < 2) {
                    throw new IllegalArgumentException("Line " + number + ": '@part' requires a part name");
                }
                String name = tokens[1];
                if (tokens.length == 2) {
                    if (!partSpecs.containsKey(name)) {
                        partSpecs.put(name, new PartSpec(name, null, 0f, 0f, 0f));
                    }
                    return name;
                }
                String parent = null;
                float px = 0f;
                float py = 0f;
                float pz = 0f;
                for (int i = 2; i < tokens.length; i++) {
                    String tok = tokens[i];
                    if (tok.startsWith("parent=")) {
                        String val = tok.substring("parent=".length());
                        parent = ROOT_PART.equals(val) || val.isEmpty() ? null : val;
                    } else if (tok.startsWith("pivot=")) {
                        float[] p = parseTriplet(tok.substring("pivot=".length()), number, "@part pivot");
                        px = p[0];
                        py = p[1];
                        pz = p[2];
                    }
                }
                partSpecs.put(name, new PartSpec(name, parent, px, py, pz));
                return name;
            }
            case "@socket": {
                if (tokens.length < 2) {
                    throw new IllegalArgumentException("Line " + number + ": '@socket' requires a socket name");
                }
                String name = tokens[1];
                String part = ROOT_PART;
                float sx = 0f;
                float sy = 0f;
                float sz = 0f;
                for (int i = 2; i < tokens.length; i++) {
                    String tok = tokens[i];
                    if (tok.startsWith("part=")) {
                        part = tok.substring("part=".length());
                    } else if (tok.startsWith("pos=")) {
                        float[] p = parseTriplet(tok.substring("pos=".length()), number, "@socket pos");
                        sx = p[0];
                        sy = p[1];
                        sz = p[2];
                    }
                }
                socketSpecs.put(name, new SocketSpec(name, part, sx, sy, sz));
                return currentPart;
            }
            case "@accent":
                if (tokens.length < 2) {
                    throw new IllegalArgumentException("Line " + number + ": '@accent' requires a material name");
                }
                accentMaterials.add(body.substring("@accent".length()).trim());
                return currentPart;
            default:
                return currentPart;
        }
    }

    private static float[] parseTriplet(String csv, int number, String label) {
        String[] parts = csv.split(",", -1);
        if (parts.length != 3) {
            throw new IllegalArgumentException("Line " + number + ": " + label + " needs x,y,z");
        }
        float[] out = new float[3];
        for (int i = 0; i < 3; i++) {
            String p = parts[i].trim();
            if (!NUMBER.matcher(p).matches()) {
                throw new IllegalArgumentException("Line " + number + ": '" + p + "' in " + label + " is not a number");
            }
            out[i] = Float.parseFloat(p);
            if (Float.isInfinite(out[i])) {
                throw new IllegalArgumentException("Line " + number + ": '" + p + "' in " + label + " is not finite");
            }
        }
        return out;
    }

    private static float[] vector(String[] words, int number) {
        if (words.length < 4) {
            throw new IllegalArgumentException("Line " + number + ": '" + words[0] + "' needs three numbers");
        }
        float[] vector = new float[3];
        for (int i = 0; i < 3; i++) {
            if (!NUMBER.matcher(words[i + 1]).matches()) {
                throw new IllegalArgumentException("Line " + number + ": '" + words[i + 1] + "' is not a number");
            }
            vector[i] = Float.parseFloat(words[i + 1]);
            if (Float.isInfinite(vector[i])) {
                throw new IllegalArgumentException("Line " + number + ": '" + words[i + 1] + "' is not finite");
            }
        }
        return vector;
    }

    private static float[] unit(float[] vector, int number) {
        return unit(vector[0], vector[1], vector[2], number, "a normal of zero length");
    }

    /** Normalizes a 3D vector in double precision so large float components never overflow length squared. */
    private static float[] unit(double x, double y, double z, int number, String zeroMessage) {
        double length = length(x, y, z);
        if (length < ZERO_LENGTH) throw new IllegalArgumentException("Line " + number + ": " + zeroMessage);
        return new float[] {(float) (x / length), (float) (y / length), (float) (z / length)};
    }

    private static double length(double x, double y, double z) {
        return Math.sqrt(x * x + y * y + z * z);
    }

    /** Reads one face, checks indices and normals, and appends its triangulated triangles. */
    private static void face(String[] words, int number, List<float[]> vertices, int textureCount,
                             List<float[]> vertexNormals, FloatList positions, FloatList normals) {
        int corners = words.length - 1;
        if (corners < 3) throw new IllegalArgumentException("Line " + number + ": a face needs at least three corners");
        float[][] cornerPositions = new float[corners][];
        float[][] cornerNormals = new float[corners][];
        int withNormals = 0;
        String emptyThirdSlotCorner = null;
        for (int i = 0; i < corners; i++) {
            String[] parts = words[i + 1].split("/", -1);
            if (parts.length > 3 || (parts.length == 2 && parts[1].isEmpty())) {
                throw new IllegalArgumentException("Line " + number + ": '" + words[i + 1] + "' is not a face corner");
            }
            cornerPositions[i] = vertices.get(index(parts[0], vertices.size(), number, "vertex"));
            if (parts.length >= 2 && !parts[1].isEmpty()) {
                index(parts[1], textureCount, number, "texture coordinate");
            }
            if (parts.length == 3) {
                if (parts[2].isEmpty()) {
                    if (emptyThirdSlotCorner == null) emptyThirdSlotCorner = words[i + 1];
                } else {
                    cornerNormals[i] = vertexNormals.get(index(parts[2], vertexNormals.size(), number, "normal"));
                    withNormals++;
                }
            }
        }
        if (withNormals != 0 && withNormals != corners) {
            throw new IllegalArgumentException("Line " + number + ": a face with normals on only " + withNormals
                + " of its " + corners + " corners");
        }
        if (emptyThirdSlotCorner != null) {
            throw new IllegalArgumentException("Line " + number + ": '" + emptyThirdSlotCorner + "' is not a face"
                + " corner");
        }

        double[] centroid = centroid(cornerPositions);
        double[] area = faceAreaVector(cornerPositions, centroid);
        double areaLength = length(area[0], area[1], area[2]);
        if (areaLength < ZERO_LENGTH) {
            if (corners >= 4 && hasAnyTriangleWithArea(cornerPositions)) {
                throw new IllegalArgumentException("Line " + number + ": a self-crossing face");
            }
            if (withNormals == 0) {
                throw new IllegalArgumentException("Line " + number + ": a face with no area: its corners are in a"
                    + " line or coincide");
            }
            for (int i = 1; i < corners - 1; i++) {
                emitTriangle(cornerPositions, null, cornerNormals, 0, i, i + 1, positions, normals);
            }
            return;
        }

        float[] flat = withNormals == 0
            ? unit(area[0], area[1], area[2], number, "a face with no area: its corners are in a line or coincide")
            : null;
        if (corners == 3) {
            emitTriangle(cornerPositions, flat, cornerNormals, 0, 1, 2, positions, normals);
            return;
        }
        for (int[] triangle : triangulate(cornerPositions, centroid, area, areaLength, number)) {
            emitTriangle(cornerPositions, flat, cornerNormals, triangle[0], triangle[1], triangle[2],
                positions, normals);
        }
    }

    private static void emitTriangle(float[][] cornerPositions, float[] flat, float[][] cornerNormals,
                                     int a, int b, int c, FloatList positions, FloatList normals) {
        positions.add(cornerPositions[a]);
        normals.add(flat != null ? flat : cornerNormals[a]);
        positions.add(cornerPositions[b]);
        normals.add(flat != null ? flat : cornerNormals[b]);
        positions.add(cornerPositions[c]);
        normals.add(flat != null ? flat : cornerNormals[c]);
    }

    private static double[] centroid(float[][] corners) {
        double cx = 0.0;
        double cy = 0.0;
        double cz = 0.0;
        for (float[] point : corners) {
            cx += point[0];
            cy += point[1];
            cz += point[2];
        }
        int count = corners.length;
        return new double[] {cx / count, cy / count, cz / count};
    }

    /** Newell's twice-area normal vector relative to the centroid, computed in double precision. */
    private static double[] faceAreaVector(float[][] corners, double[] centroid) {
        double nx = 0.0;
        double ny = 0.0;
        double nz = 0.0;
        int count = corners.length;
        for (int i = 0; i < count; i++) {
            float[] a = corners[i];
            float[] b = corners[(i + 1) % count];
            double ax = a[0] - centroid[0];
            double ay = a[1] - centroid[1];
            double az = a[2] - centroid[2];
            double bx = b[0] - centroid[0];
            double by = b[1] - centroid[1];
            double bz = b[2] - centroid[2];
            nx += ay * bz - az * by;
            ny += az * bx - ax * bz;
            nz += ax * by - ay * bx;
        }
        return new double[] {nx, ny, nz};
    }

    private static boolean hasAnyTriangleWithArea(float[][] corners) {
        for (int i = 1; i < corners.length - 1; i++) {
            double[] normal = cross(corners[0], corners[i], corners[i + 1]);
            if (length(normal[0], normal[1], normal[2]) >= ZERO_LENGTH) return true;
        }
        return false;
    }

    /** Triangulates a polygon of four or more corners in its Newell plane by ear clipping. */
    private static int[][] triangulate(float[][] corners, double[] centroid, double[] area, double areaLength,
                                       int lineNumber) {
        int count = corners.length;
        double nx = area[0] / areaLength;
        double ny = area[1] / areaLength;
        double nz = area[2] / areaLength;

        double rx = 0.0;
        double ry = 0.0;
        double rz = 0.0;
        if (Math.abs(nx) <= Math.abs(ny) && Math.abs(nx) <= Math.abs(nz)) rx = 1.0;
        else if (Math.abs(ny) <= Math.abs(nz)) ry = 1.0;
        else rz = 1.0;

        // u = normalize(ref x n), v = n x u so (u, v, n) is right-handed and counter-clockwise round +n matches +cross.
        double ux = ry * nz - rz * ny;
        double uy = rz * nx - rx * nz;
        double uz = rx * ny - ry * nx;
        double uLen = length(ux, uy, uz);
        ux /= uLen;
        uy /= uLen;
        uz /= uLen;
        double vx = ny * uz - nz * uy;
        double vy = nz * ux - nx * uz;
        double vz = nx * uy - ny * ux;

        double[] pu = new double[count];
        double[] pv = new double[count];
        double maxSpanSq = 0.0;
        for (int i = 0; i < count; i++) {
            double dx = corners[i][0] - centroid[0];
            double dy = corners[i][1] - centroid[1];
            double dz = corners[i][2] - centroid[2];
            pu[i] = dx * ux + dy * uy + dz * uz;
            pv[i] = dx * vx + dy * vy + dz * vz;
            maxSpanSq = Math.max(maxSpanSq, pu[i] * pu[i] + pv[i] * pv[i]);
        }

        double areaEps = 1e-12 * maxSpanSq;
        if (hasSelfCrossingEdges(pu, pv, areaEps)) {
            throw new IllegalArgumentException("Line " + lineNumber + ": a self-crossing face");
        }

        int[] active = new int[count];
        for (int i = 0; i < count; i++) active[i] = i;
        int remaining = count;
        int[][] triangles = new int[count - 2][];
        int emitted = 0;
        int lastPrev = 0;

        while (remaining > 3) {
            int earSlot = -1;
            // Search from slot 1 so a convex quad emits (0, 1, 2) and (0, 2, 3), keeping fan quad pairing intact.
            for (int step = 0; step < remaining; step++) {
                int slot = (1 + step) % remaining;
                if (isValidEar(pu, pv, active, remaining, slot, areaEps, maxSpanSq)) {
                    earSlot = slot;
                    break;
                }
            }
            if (earSlot < 0) {
                throw new IllegalArgumentException("Line " + lineNumber + ": a self-crossing face");
            }
            int prevSlot = (earSlot + remaining - 1) % remaining;
            int nextSlot = (earSlot + 1) % remaining;
            triangles[emitted++] = new int[] {active[prevSlot], active[earSlot], active[nextSlot]};
            System.arraycopy(active, earSlot + 1, active, earSlot, remaining - earSlot - 1);
            remaining--;
            lastPrev = earSlot == 0 ? remaining - 1 : earSlot - 1;
        }
        triangles[emitted] = new int[] {
            active[lastPrev], active[(lastPrev + 1) % 3], active[(lastPrev + 2) % 3]
        };
        return triangles;
    }

    private static boolean isValidEar(double[] pu, double[] pv, int[] active, int remaining, int slot,
                                      double areaEps, double maxSpanSq) {
        int prev = active[(slot + remaining - 1) % remaining];
        int curr = active[slot];
        int next = active[(slot + 1) % remaining];
        double cross = cross2D(pu, pv, prev, curr, next);
        if (cross < -areaEps) return false;
        if (cross <= areaEps) {
            double forwardDot = (pu[curr] - pu[prev]) * (pu[next] - pu[curr])
                + (pv[curr] - pv[prev]) * (pv[next] - pv[curr]);
            if (forwardDot < -1e-15 * maxSpanSq) return false;
            return !anyOtherPointOnSegment(pu, pv, active, remaining, prev, curr, next, areaEps);
        }
        for (int k = 0; k < remaining; k++) {
            int other = active[k];
            if (other == prev || other == curr || other == next) continue;
            if (cross2D(pu, pv, prev, curr, other) >= -areaEps
                && cross2D(pu, pv, curr, next, other) >= -areaEps
                && cross2D(pu, pv, next, prev, other) >= -areaEps) {
                return false;
            }
        }
        return true;
    }

    private static boolean anyOtherPointOnSegment(double[] pu, double[] pv, int[] active, int remaining,
                                                  int a, int skip, int b, double areaEps) {
        for (int k = 0; k < remaining; k++) {
            int other = active[k];
            if (other == a || other == skip || other == b) continue;
            if (onSegment2D(pu, pv, a, b, other, areaEps)) return true;
        }
        return false;
    }

    private static boolean hasSelfCrossingEdges(double[] pu, double[] pv, double areaEps) {
        int count = pu.length;
        for (int i = 0; i < count; i++) {
            int a = i;
            int b = (i + 1) % count;
            for (int j = i + 2; j < count; j++) {
                if (i == 0 && j == count - 1) continue;
                int c = j;
                int d = (j + 1) % count;
                if (segmentsCross2D(pu, pv, a, b, c, d, areaEps)) return true;
            }
        }
        return false;
    }

    private static boolean segmentsCross2D(double[] pu, double[] pv, int a, int b, int c, int d, double areaEps) {
        double c1 = cross2D(pu, pv, a, b, c);
        double c2 = cross2D(pu, pv, a, b, d);
        double c3 = cross2D(pu, pv, c, d, a);
        double c4 = cross2D(pu, pv, c, d, b);
        boolean opp12 = (c1 > areaEps && c2 < -areaEps) || (c1 < -areaEps && c2 > areaEps);
        boolean opp34 = (c3 > areaEps && c4 < -areaEps) || (c3 < -areaEps && c4 > areaEps);
        if (opp12 && opp34) return true;
        return onSegment2D(pu, pv, a, b, c, areaEps)
            || onSegment2D(pu, pv, a, b, d, areaEps)
            || onSegment2D(pu, pv, c, d, a, areaEps)
            || onSegment2D(pu, pv, c, d, b, areaEps);
    }

    private static boolean onSegment2D(double[] pu, double[] pv, int a, int b, int p, double areaEps) {
        if (Math.abs(cross2D(pu, pv, a, b, p)) > areaEps) return false;
        double dot = (pu[a] - pu[p]) * (pu[b] - pu[p]) + (pv[a] - pv[p]) * (pv[b] - pv[p]);
        return dot <= areaEps;
    }

    private static double cross2D(double[] pu, double[] pv, int a, int b, int c) {
        return (pu[b] - pu[a]) * (pv[c] - pv[a]) - (pv[b] - pv[a]) * (pu[c] - pu[a]);
    }

    /** Turns an OBJ index, counted from 1 or negative from the end, into an index counted from 0. */
    private static int index(String word, int count, int number, String what) {
        if (!INDEX.matcher(word).matches()) {
            throw new IllegalArgumentException("Line " + number + ": '" + word + "' is not a " + what + " index");
        }
        int value;
        try {
            value = Integer.parseInt(word);
        } catch (NumberFormatException e) {
            throw new IllegalArgumentException("Line " + number + ": '" + word + "' is not a " + what + " index", e);
        }
        int index = value < 0 ? count + value : value - 1;
        if (value == 0 || index < 0 || index >= count) {
            throw new IllegalArgumentException("Line " + number + ": there is no " + what + " " + value
                + " (" + count + " so far)");
        }
        return index;
    }

    private static double[] cross(float[] a, float[] b, float[] c) {
        double ux = (double) b[0] - a[0];
        double uy = (double) b[1] - a[1];
        double uz = (double) b[2] - a[2];
        double vx = (double) c[0] - a[0];
        double vy = (double) c[1] - a[1];
        double vz = (double) c[2] - a[2];
        return new double[] {uy * vz - uz * vy, uz * vx - ux * vz, ux * vy - uy * vx};
    }

    /** A growing float array. */
    private static final class FloatList {
        private float[] items = new float[4096];
        private int size;

        void add(float[] three) {
            if (size + 3 > items.length) items = Arrays.copyOf(items, items.length * 2);
            items[size++] = three[0];
            items[size++] = three[1];
            items[size++] = three[2];
        }

        float[] toArray() {
            return Arrays.copyOf(items, size);
        }
    }
}

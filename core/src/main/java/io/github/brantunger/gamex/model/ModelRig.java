package io.github.brantunger.gamex.model;

import com.badlogic.gdx.math.Matrix4;
import com.badlogic.gdx.math.Vector3;

import java.util.HashMap;
import java.util.Map;

/**
 * Headless forward-kinematics helper evaluating hierarchical {@link ObjModel.PartSpec} and
 * {@link ObjModel.SocketSpec} transforms for articulated 3D models.
 */
public final class ModelRig {

    private static final int MAX_DEPTH = 16;

    private final ObjModel model;
    private final Map<String, Matrix4> cache = new HashMap<>();
    private final Map<String, Matrix4> pool = new HashMap<>();
    private final Matrix4 socketPartMatrix = new Matrix4();

    /**
     * @param model parsed OBJ model whose part/socket metadata this rig evaluates
     */
    public ModelRig(ObjModel model) {
        this.model = model;
    }

    /**
     * @param partName part name
     * @param out receives the rest offset from the parent's pivot (or origin for root-attached parts)
     * @return {@code out}
     */
    public Vector3 localPivotOffset(String partName, Vector3 out) {
        ObjModel.PartSpec spec = model.getPartSpec(partName);
        if (spec == null) return out.setZero();
        ObjModel.PartSpec parent = spec.parent == null ? null : model.getPartSpec(spec.parent);
        float px = parent == null ? 0f : parent.pivotX;
        float py = parent == null ? 0f : parent.pivotY;
        float pz = parent == null ? 0f : parent.pivotZ;
        return out.set(spec.pivotX - px, spec.pivotY - py, spec.pivotZ - pz);
    }

    /**
     * Computes the world transform matrix for {@code partName}, positioning vertices stored relative to that part's
     * pivot into world space.
     * @param partName target part name
     * @param localRotations optional per-part local transform/rotation around its pivot (may be null)
     * @param rootTransform world transform of the model root
     * @param out receives the composed world matrix
     * @return {@code out}
     */
    public Matrix4 evaluatePartTransform(String partName, Map<String, Matrix4> localRotations,
                                         Matrix4 rootTransform, Matrix4 out) {
        cache.clear();
        return evalPart(partName, localRotations, rootTransform, out, 0);
    }

    /**
     * Computes the world position of a named {@link ObjModel.SocketSpec}.
     * @param socketName socket name declared via {@code # @socket}
     * @param localRotations optional per-part local transforms (may be null)
     * @param rootTransform world transform of the model root
     * @param out receives the world socket coordinates
     * @return {@code out}
     */
    public Vector3 evaluateSocketWorld(String socketName, Map<String, Matrix4> localRotations,
                                       Matrix4 rootTransform, Vector3 out) {
        ObjModel.SocketSpec socket = model.getSocket(socketName);
        if (socket == null) {
            return out.setZero().mul(rootTransform);
        }
        ObjModel.PartSpec part = model.getPartSpec(socket.part);
        float px = part == null ? 0f : part.pivotX;
        float py = part == null ? 0f : part.pivotY;
        float pz = part == null ? 0f : part.pivotZ;
        evaluatePartTransform(socket.part, localRotations, rootTransform, socketPartMatrix);
        return out.set(socket.x - px, socket.y - py, socket.z - pz).mul(socketPartMatrix);
    }

    private Matrix4 evalPart(String partName, Map<String, Matrix4> localRotations, Matrix4 rootTransform,
                             Matrix4 out, int depth) {
        if (depth > MAX_DEPTH) {
            throw new IllegalStateException("Part hierarchy cycle or excessive depth at '" + partName + "'");
        }
        if (partName == null || ObjModel.ROOT_PART.equals(partName)) {
            out.set(rootTransform);
            Matrix4 rootLocal = localRotations == null ? null : localRotations.get(ObjModel.ROOT_PART);
            if (rootLocal != null) out.mul(rootLocal);
            return out;
        }
        ObjModel.PartSpec spec = model.getPartSpec(partName);
        if (spec == null) {
            return out.set(rootTransform);
        }
        String parentKey = spec.parent == null ? ObjModel.ROOT_PART : spec.parent;
        Matrix4 parentMat = cache.get(parentKey);
        if (parentMat == null) {
            parentMat = pool.computeIfAbsent(parentKey, k -> new Matrix4());
            evalPart(spec.parent, localRotations, rootTransform, parentMat, depth + 1);
            cache.put(parentKey, parentMat);
        }
        ObjModel.PartSpec parentSpec = spec.parent == null ? null : model.getPartSpec(spec.parent);
        float px = parentSpec == null ? 0f : parentSpec.pivotX;
        float py = parentSpec == null ? 0f : parentSpec.pivotY;
        float pz = parentSpec == null ? 0f : parentSpec.pivotZ;
        out.set(parentMat).translate(spec.pivotX - px, spec.pivotY - py, spec.pivotZ - pz);
        Matrix4 local = localRotations == null ? null : localRotations.get(partName);
        if (local != null) out.mul(local);
        return out;
    }
}

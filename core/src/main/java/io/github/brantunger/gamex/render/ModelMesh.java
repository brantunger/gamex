package io.github.brantunger.gamex.render;

import com.badlogic.gdx.Gdx;
import com.badlogic.gdx.graphics.Mesh;
import com.badlogic.gdx.graphics.VertexAttribute;
import com.badlogic.gdx.graphics.VertexAttributes;
import com.badlogic.gdx.graphics.glutils.ShaderProgram;
import com.badlogic.gdx.utils.GdxRuntimeException;
import io.github.brantunger.gamex.model.ModelVertices;
import io.github.brantunger.gamex.model.ObjModel;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.function.Function;

/** Loads Wavefront OBJ assets into GPU {@link Mesh}es using the shared 11-float indexed vertex layout. */
public final class ModelMesh {

    private static final Logger log = LoggerFactory.getLogger(ModelMesh.class);

    /** How many model files have been read so far; the renderers log the number each of them read. */
    private static int loadedCount;

    private ModelMesh() {}

    /** @return how many model files have been read since the game started. */
    static int getLoadedCount() {
        return loadedCount;
    }

    static void modelLoaded(String assetPath, String label) {
        loadedCount++;
        log.debug("Loaded the {} model {}", label, assetPath);
    }

    /** Parses an OBJ file once with contextual error wrapping. */
    public static ObjModel loadModel(String assetPath, String label) {
        String text = Gdx.files.internal(assetPath).readString("UTF-8");
        return parseModel(assetPath, label, text);
    }

    static ObjModel parseModel(String assetPath, String label, String text) {
        if (text == null) {
            throw new GdxRuntimeException(
                "The " + label + " model " + assetPath + " could not be read: OBJ text is null");
        }
        try {
            ObjModel model = ObjModel.parse(text);
            modelLoaded(assetPath, label);
            return model;
        } catch (IllegalArgumentException e) {
            throw new GdxRuntimeException("The " + label + " model " + assetPath + " could not be read: "
                + e.getMessage(), e);
        }
    }

    /**
     * Converts a parsed {@link ObjModel} with {@code converter}, wrapping any {@link IllegalArgumentException} or null
     * result in a contextual {@link GdxRuntimeException} naming both {@code label} and {@code assetPath}.
     */
    static <T> T convert(ObjModel model, String assetPath, String label, Function<ObjModel, T> converter) {
        if (model == null) {
            throw new GdxRuntimeException(
                "The " + label + " model " + assetPath + " could not be read: parsed model is null");
        }
        T result;
        try {
            result = converter.apply(model);
        } catch (IllegalArgumentException e) {
            throw new GdxRuntimeException("The " + label + " model " + assetPath + " could not be read: "
                + e.getMessage(), e);
        }
        if (result == null) {
            throw new GdxRuntimeException("The " + label + " model " + assetPath + " produced null model data");
        }
        return result;
    }

    /**
     * Converts a parsed {@link ObjModel} into an {@link ModelVertices.IndexedMesh} and validates that it is non-null
     * and non-empty before touching OpenGL.
     */
    static ModelVertices.IndexedMesh convertMesh(ObjModel model, String assetPath, String label,
                                                 Function<ObjModel, ModelVertices.IndexedMesh> converter) {
        if (model == null) {
            throw new GdxRuntimeException(
                "The " + label + " model " + assetPath + " could not be read: parsed model is null");
        }
        ModelVertices.IndexedMesh data;
        try {
            data = converter.apply(model);
        } catch (IllegalArgumentException e) {
            throw new GdxRuntimeException("The " + label + " model " + assetPath + " could not be read: "
                + e.getMessage(), e);
        }
        return requireMesh(data, assetPath, label);
    }

    /**
     * Validates that {@code data} is non-null and holds non-empty aligned vertex and index arrays before GPU upload.
     */
    static ModelVertices.IndexedMesh requireMesh(ModelVertices.IndexedMesh data, String assetPath, String label) {
        if (data == null) {
            throw new GdxRuntimeException("The " + label + " model " + assetPath + " has no mesh triangles");
        }
        if (data.vertices == null || data.indices == null
            || data.vertices.length == 0 || data.vertices.length % ModelVertices.VERTEX_SIZE != 0
            || data.indexCount() == 0) {
            throw new GdxRuntimeException(
                "The " + label + " model " + assetPath + " has empty or invalid vertex/index data");
        }
        return data;
    }

    static float[] requireVertices(float[] vertices, String assetPath, String label) {
        if (vertices == null || vertices.length == 0 || vertices.length % ModelVertices.VERTEX_SIZE != 0) {
            throw new GdxRuntimeException("The " + label + " model " + assetPath + " has empty or invalid vertex data");
        }
        return vertices;
    }

    public static <T> T load(String assetPath, String label, Function<ObjModel, T> converter) {
        String text = Gdx.files.internal(assetPath).readString("UTF-8");
        try {
            T converted = ModelVertices.read(assetPath, label, text, converter);
            modelLoaded(assetPath, label);
            return converted;
        } catch (IllegalArgumentException e) {
            throw new GdxRuntimeException(e.getMessage(), e);
        }
    }

    public static Mesh loadMesh(String assetPath, String label, Function<ObjModel, float[]> converter) {
        return build(requireVertices(load(assetPath, label, converter), assetPath, label));
    }

    public static Mesh loadIndexedMesh(String assetPath, String label,
                                       Function<ObjModel, ModelVertices.IndexedMesh> converter) {
        return buildIndexed(requireMesh(load(assetPath, label, converter), assetPath, label));
    }

    public static Mesh buildIndexed(ModelVertices.IndexedMesh data, String assetPath, String label) {
        return buildIndexed(requireMesh(data, assetPath, label));
    }

    public static Mesh buildIndexed(ModelVertices.IndexedMesh data) {
        if (data == null) {
            throw new GdxRuntimeException("Cannot build an indexed mesh from null mesh data");
        }
        if (data.vertices == null || data.indices == null
            || data.vertices.length == 0 || data.vertices.length % ModelVertices.VERTEX_SIZE != 0
            || data.indexCount() == 0) {
            throw new GdxRuntimeException("Cannot build an indexed mesh from empty or invalid vertex/index data");
        }
        Mesh mesh = new Mesh(true, data.vertexCount(), data.indexCount(),
            new VertexAttribute(VertexAttributes.Usage.Position, 3, ShaderProgram.POSITION_ATTRIBUTE),
            new VertexAttribute(VertexAttributes.Usage.Normal, 3, ShaderProgram.NORMAL_ATTRIBUTE),
            new VertexAttribute(VertexAttributes.Usage.Generic, 3, "a_colour"),
            new VertexAttribute(VertexAttributes.Usage.Generic, 1, "a_gloss"),
            new VertexAttribute(VertexAttributes.Usage.Generic, 1, "a_accent"));
        mesh.setVertices(data.vertices);
        mesh.setIndices(data.indices);
        return mesh;
    }

    public static Mesh build(float[] vertices) {
        if (vertices == null || vertices.length == 0 || vertices.length % ModelVertices.VERTEX_SIZE != 0) {
            throw new GdxRuntimeException("Cannot build a mesh from null, empty, or invalid vertex data");
        }
        Mesh mesh = new Mesh(true, vertices.length / ModelVertices.VERTEX_SIZE, 0,
            new VertexAttribute(VertexAttributes.Usage.Position, 3, ShaderProgram.POSITION_ATTRIBUTE),
            new VertexAttribute(VertexAttributes.Usage.Normal, 3, ShaderProgram.NORMAL_ATTRIBUTE),
            new VertexAttribute(VertexAttributes.Usage.Generic, 3, "a_colour"),
            new VertexAttribute(VertexAttributes.Usage.Generic, 1, "a_gloss"),
            new VertexAttribute(VertexAttributes.Usage.Generic, 1, "a_accent"));
        mesh.setVertices(vertices);
        return mesh;
    }
}

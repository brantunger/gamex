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

    private static void modelLoaded(String assetPath, String label) {
        loadedCount++;
        log.debug("Loaded the {} model {}", label, assetPath);
    }

    /** Parses an OBJ file once with contextual error wrapping. */
    public static ObjModel loadModel(String assetPath, String label) {
        String text = Gdx.files.internal(assetPath).readString("UTF-8");
        try {
            ObjModel model = ObjModel.parse(text);
            modelLoaded(assetPath, label);
            return model;
        } catch (IllegalArgumentException e) {
            throw new GdxRuntimeException("The " + label + " model " + assetPath + " could not be read: "
                + e.getMessage(), e);
        }
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
        return build(load(assetPath, label, converter));
    }

    public static Mesh loadIndexedMesh(String assetPath, String label,
                                       Function<ObjModel, ModelVertices.IndexedMesh> converter) {
        return buildIndexed(load(assetPath, label, converter));
    }

    public static Mesh buildIndexed(ModelVertices.IndexedMesh data) {
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

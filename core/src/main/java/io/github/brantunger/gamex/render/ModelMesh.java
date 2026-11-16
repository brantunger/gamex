package io.github.brantunger.gamex.render;

import com.badlogic.gdx.Gdx;
import com.badlogic.gdx.graphics.Mesh;
import com.badlogic.gdx.graphics.VertexAttribute;
import com.badlogic.gdx.graphics.VertexAttributes;
import com.badlogic.gdx.graphics.glutils.ShaderProgram;
import com.badlogic.gdx.utils.GdxRuntimeException;
import io.github.brantunger.gamex.model.ModelVertices;
import io.github.brantunger.gamex.model.ObjModel;

import java.util.function.Function;

/** Loads Wavefront OBJ assets into GPU {@link Mesh}es using the shared 10-float vertex layout. */
final class ModelMesh {

    private ModelMesh() {}

    static <T> T load(String assetPath, String label, Function<ObjModel, T> converter) {
        String text = Gdx.files.internal(assetPath).readString("UTF-8");
        try {
            return converter.apply(ObjModel.parse(text));
        } catch (IllegalArgumentException e) {
            // The parser or converter names the line or material; say which file it was reading as well.
            throw new GdxRuntimeException("The " + label + " model " + assetPath + " could not be read: "
                + e.getMessage(), e);
        }
    }

    static Mesh loadMesh(String assetPath, String label, Function<ObjModel, float[]> converter) {
        return build(load(assetPath, label, converter));
    }

    static Mesh build(float[] vertices) {
        Mesh mesh = new Mesh(true, vertices.length / ModelVertices.VERTEX_SIZE, 0,
            new VertexAttribute(VertexAttributes.Usage.Position, 3, ShaderProgram.POSITION_ATTRIBUTE),
            new VertexAttribute(VertexAttributes.Usage.Normal, 3, ShaderProgram.NORMAL_ATTRIBUTE),
            new VertexAttribute(VertexAttributes.Usage.Generic, 3, "a_colour"),
            new VertexAttribute(VertexAttributes.Usage.Generic, 1, "a_gloss"));
        mesh.setVertices(vertices);
        return mesh;
    }
}

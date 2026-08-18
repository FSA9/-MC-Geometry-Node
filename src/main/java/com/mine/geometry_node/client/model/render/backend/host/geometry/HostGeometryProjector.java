package com.mine.geometry_node.client.model.render.backend.host.geometry;

import com.mine.geometry_node.client.model.runtime.StaticModelTexture;
import com.mine.geometry_node.core.engine.system.model.domain.ModelAttributeSemantic;
import com.mine.geometry_node.core.engine.system.model.domain.ModelBounds;
import com.mine.geometry_node.core.engine.system.model.domain.ModelPrimitive;
import com.mine.geometry_node.core.engine.system.model.domain.ModelTextureTransform;
import org.joml.Vector3f;

import java.util.Objects;

/** Projects immutable canonical indexed geometry into the host entity vertex contract. */
public final class HostGeometryProjector {
    private HostGeometryProjector() {}

    public static Vector3f boundsCenter(ModelBounds bounds) {
        return new Vector3f((bounds.min().x() + bounds.max().x()) * 0.5F,
                (bounds.min().y() + bounds.max().y()) * 0.5F,
                (bounds.min().z() + bounds.max().z()) * 0.5F);
    }

    public static HostEntityGeometry project(ModelPrimitive primitive, StaticModelTexture coordinateSource) {
        return project(HostCanonicalPrimitive.from(0, 0, primitive), coordinateSource);
    }

    public static HostEntityGeometry project(HostCanonicalPrimitive primitive,
                                             StaticModelTexture coordinateSource) {
        Objects.requireNonNull(primitive, "primitive");
        Objects.requireNonNull(coordinateSource, "coordinateSource");
        HostCanonicalPrimitive.Attribute positions = required(primitive, ModelAttributeSemantic.POSITION);
        HostCanonicalPrimitive.Attribute normals = primitive.attribute(ModelAttributeSemantic.NORMAL);
        HostCanonicalPrimitive.Attribute uv = primitive.attribute(ModelAttributeSemantic.indexed(
                ModelAttributeSemantic.Kind.TEXCOORD, coordinateSource.texCoord()));
        HostCanonicalPrimitive.Attribute colors = primitive.attribute(ModelAttributeSemantic.COLOR_0);
        int[] indices = primitive.projectionIndices();
        if (indices.length == 0 || indices.length % 3 != 0) {
            throw new IllegalArgumentException("HOST projection requires indexed triangles");
        }
        float[] output = new float[Math.multiplyExact(indices.length, 12)];
        int cursor = 0;
        for (int occurrence = 0; occurrence < indices.length; occurrence++) {
            int vertex = indices[occurrence];
            cursor = copy(output, cursor, positions, vertex, 3, new float[]{0, 0, 0});
            cursor = copy(output, cursor, normals, vertex, 3, new float[]{0, 1, 0});
            float[] selectedUv = uv == null && !coordinateSource.present()
                    ? syntheticTriangleUv(occurrence % 3) : values(uv, vertex, 2, new float[]{0, 0});
            ModelTextureTransform transform = coordinateSource.transform();
            float x = selectedUv[0] * transform.scaleX(), y = selectedUv[1] * transform.scaleY();
            float cosine = (float) Math.cos(transform.rotation()), sine = (float) Math.sin(transform.rotation());
            output[cursor++] = cosine * x - sine * y + transform.offsetX();
            output[cursor++] = sine * x + cosine * y + transform.offsetY();
            cursor = copy(output, cursor, colors, vertex, 4, new float[]{1, 1, 1, 1});
        }
        if (normals == null) generateFaceNormals(output);
        return new HostEntityGeometry(output, indices, primitive.vertexCount());
    }

    private static HostCanonicalPrimitive.Attribute required(HostCanonicalPrimitive primitive,
                                                             ModelAttributeSemantic semantic) {
        HostCanonicalPrimitive.Attribute value = primitive.attribute(semantic);
        if (value == null) throw new IllegalStateException("validated primitive lacks " + semantic);
        return value;
    }

    private static int copy(float[] target, int cursor, HostCanonicalPrimitive.Attribute source, int element,
                            int components, float[] fallback) {
        for (float value : values(source, element, components, fallback)) target[cursor++] = value;
        return cursor;
    }

    private static float[] values(HostCanonicalPrimitive.Attribute attribute, int element,
                                  int count, float[] fallback) {
        if (attribute == null) return fallback.clone();
        float[] result = fallback.clone();
        for (int component = 0; component < Math.min(count, attribute.componentCount()); component++) {
            result[component] = attribute.component(element, component);
        }
        return result;
    }

    private static float[] syntheticTriangleUv(int triangleVertex) {
        return switch (triangleVertex) {
            case 0 -> new float[]{0, 0};
            case 1 -> new float[]{1, 0};
            default -> new float[]{0, 1};
        };
    }

    private static void generateFaceNormals(float[] vertices) {
        for (int triangle = 0; triangle < vertices.length; triangle += 36) {
            float ax = vertices[triangle + 12] - vertices[triangle];
            float ay = vertices[triangle + 13] - vertices[triangle + 1];
            float az = vertices[triangle + 14] - vertices[triangle + 2];
            float bx = vertices[triangle + 24] - vertices[triangle];
            float by = vertices[triangle + 25] - vertices[triangle + 1];
            float bz = vertices[triangle + 26] - vertices[triangle + 2];
            float nx = ay * bz - az * by, ny = az * bx - ax * bz, nz = ax * by - ay * bx;
            float length = (float) Math.sqrt(nx * nx + ny * ny + nz * nz);
            if (length > 1.0E-12F) { nx /= length; ny /= length; nz /= length; }
            else { nx = 0; ny = 1; nz = 0; }
            for (int vertex = 0; vertex < 3; vertex++) {
                int offset = triangle + vertex * 12;
                vertices[offset + 3] = nx; vertices[offset + 4] = ny; vertices[offset + 5] = nz;
            }
        }
    }
}

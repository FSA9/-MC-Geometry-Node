package com.mine.geometry_node.client.model.render.backend.host.geometry;

import com.mine.geometry_node.client.model.runtime.StaticModelTexture;
import com.mine.geometry_node.core.engine.system.model.domain.*;

import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import org.joml.Vector3f;

/** Projects canonical indexed geometry into the host entity vertex contract. */
public final class HostGeometryProjector {
    private HostGeometryProjector() {}

    public static Vector3f boundsCenter(ModelBounds bounds) {
        return new Vector3f((bounds.min().x() + bounds.max().x()) * 0.5F,
                (bounds.min().y() + bounds.max().y()) * 0.5F,
                (bounds.min().z() + bounds.max().z()) * 0.5F);
    }

    public static HostEntityGeometry project(ModelPrimitive primitive, StaticModelTexture coordinateSource) {
        int[] indices = new int[primitive.indices().indexCount()];
        for (int index = 0; index < indices.length; index++) indices[index] = Math.toIntExact(primitive.indices().indexAt(index));
        return project(primitive, coordinateSource, indices);
    }

    public static HostEntityGeometry project(ModelPrimitive primitive, StaticModelTexture coordinateSource,
                                             int[] indices) {
        ModelVertexAttribute positions = required(primitive, ModelAttributeSemantic.POSITION);
        ModelVertexAttribute normals = primitive.attributes().get(ModelAttributeSemantic.NORMAL);
        ModelVertexAttribute uv = primitive.attributes().get(ModelAttributeSemantic.indexed(
                ModelAttributeSemantic.Kind.TEXCOORD, coordinateSource.texCoord()));
        ModelVertexAttribute colors = primitive.attributes().get(ModelAttributeSemantic.COLOR_0);
        if (indices == null || indices.length == 0 || indices.length % 3 != 0) {
            throw new IllegalArgumentException("HOST projection requires indexed triangles");
        }
        float[] output = new float[Math.multiplyExact(indices.length, 12)];
        int cursor = 0;
        for (int i = 0; i < indices.length; i++) {
            int vertex = indices[i];
            if (vertex < 0 || vertex >= primitive.vertexCount()) throw new IllegalArgumentException("HOST index outside vertex data");
            cursor = copy(output, cursor, positions, vertex, 3, new float[]{0, 0, 0});
            cursor = copy(output, cursor, normals, vertex, 3, new float[]{0, 1, 0});
            float[] selectedUv = uv == null && !coordinateSource.present()
                    ? syntheticTriangleUv(i % 3) : values(uv, vertex, 2, new float[]{0, 0});
            ModelTextureTransform transform = coordinateSource.transform();
            float x = selectedUv[0] * transform.scaleX(), y = selectedUv[1] * transform.scaleY();
            float cosine = (float) Math.cos(transform.rotation()), sine = (float) Math.sin(transform.rotation());
            output[cursor++] = cosine * x - sine * y + transform.offsetX();
            output[cursor++] = sine * x + cosine * y + transform.offsetY();
            cursor = copy(output, cursor, colors, vertex, 4, new float[]{1, 1, 1, 1});
        }
        if (normals == null) generateFaceNormals(output);
        return new HostEntityGeometry(output);
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

    private static ModelVertexAttribute required(ModelPrimitive primitive, ModelAttributeSemantic semantic) {
        ModelVertexAttribute value = primitive.attributes().get(semantic);
        if (value == null) throw new IllegalStateException("validated primitive lacks " + semantic);
        return value;
    }

    private static int copy(float[] target, int cursor, ModelVertexAttribute source, int element,
                            int components, float[] fallback) {
        for (float value : values(source, element, components, fallback)) target[cursor++] = value;
        return cursor;
    }

    private static float[] values(ModelVertexAttribute attribute, int element, int count, float[] fallback) {
        if (attribute == null) return fallback.clone();
        ByteBuffer data = attribute.readOnlyData().order(ByteOrder.LITTLE_ENDIAN);
        int stride = attribute.componentType().byteSize() * attribute.componentCount();
        float[] result = fallback.clone();
        for (int component = 0; component < Math.min(count, attribute.componentCount()); component++) {
            int offset = element * stride + component * attribute.componentType().byteSize();
            result[component] = component(data, offset, attribute.componentType(), attribute.normalized());
        }
        return result;
    }

    private static float component(ByteBuffer data, int offset, ModelComponentType type, boolean normalized) {
        return switch (type) {
            case FLOAT32 -> data.getFloat(offset);
            case UINT8 -> normalized ? Byte.toUnsignedInt(data.get(offset)) / 255F : Byte.toUnsignedInt(data.get(offset));
            case INT8 -> normalized ? Math.max(data.get(offset) / 127F, -1F) : data.get(offset);
            case UINT16 -> normalized ? Short.toUnsignedInt(data.getShort(offset)) / 65535F : Short.toUnsignedInt(data.getShort(offset));
            case INT16 -> normalized ? Math.max(data.getShort(offset) / 32767F, -1F) : data.getShort(offset);
            case UINT32 -> normalized ? Integer.toUnsignedLong(data.getInt(offset)) / 4294967295F
                    : Integer.toUnsignedLong(data.getInt(offset));
        };
    }
}

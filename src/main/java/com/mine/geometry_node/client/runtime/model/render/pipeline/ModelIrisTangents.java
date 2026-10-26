package com.mine.geometry_node.client.runtime.model.render.pipeline;

import com.mojang.blaze3d.vertex.VertexFormat;
import com.mojang.blaze3d.vertex.VertexFormatElement;
import java.nio.ByteBuffer;

/** Copies authored/imported normal-UV tangents into Iris's expanded entity vertex stream.
 * Iris NormalHelper defines B=(T cross N)*w; glTF defines B=(N cross T)*w.
 * No Iris class is linked, keeping this utility safe when Iris is absent.
 */
public final class ModelIrisTangents {
    private ModelIrisTangents() {}

    public static void write(ByteBuffer vertices, VertexFormat format, float[] tangents, int[] indices,
                      int offset, int count) {
        if (tangents.length == 0) return;
        int attribute = format.getElementAttributeNames().indexOf("at_tangent");
        if (attribute < 0) return;
        VertexFormatElement element = format.getElements().get(attribute);
        if (element.type() != VertexFormatElement.Type.BYTE || element.count() != 4)
            throw new IllegalStateException("unsupported Iris tangent vertex layout");
        int stride = format.getVertexSize(), field = format.getOffset(element), start = vertices.position();
        if (offset < 0 || count < 0 || offset > indices.length - count || vertices.remaining() < (long) count * stride)
            throw new IllegalArgumentException("invalid Iris tangent chunk");
        for (int i = 0; i < count; i++) {
            int source = Math.multiplyExact(indices[offset + i], 4), target = start + i * stride + field;
            if (source < 0 || source > tangents.length - 4) throw new IllegalArgumentException("tangent index outside primitive");
            double x = tangents[source], y = tangents[source + 1], z = tangents[source + 2];
            double length = Math.sqrt(x*x + y*y + z*z);
            // Keep Iris's generated fallback for invalid authored zero tangents.
            if (!(length > 1e-20) || !Double.isFinite(length)) continue;
            vertices.put(target, component(x / length));
            vertices.put(target + 1, component(y / length));
            vertices.put(target + 2, component(z / length));
            vertices.put(target + 3, (byte)(tangents[source + 3] < 0 ? 127 : -127));
        }
    }
    /** Tangents are surface directions, so use the linear transform, not inverse-transpose. */
    public static org.joml.Vector4f transformed(float[] tangents, int vertex, org.joml.Matrix4fc transform,
                                         org.joml.Vector3fc transformedNormal) {
        if (tangents.length==0) return new org.joml.Vector4f();
        int at = vertex*4;
        var direction = new org.joml.Vector3f(tangents[at], tangents[at+1], tangents[at+2]);
        transform.transformDirection(direction);
        float dot = direction.dot(transformedNormal);
        direction.sub(transformedNormal.x()*dot, transformedNormal.y()*dot, transformedNormal.z()*dot);
        if (direction.lengthSquared()>1e-20f) direction.normalize();
        float sign = transform.determinant3x3()<0 ? -1 : 1;
        return new org.joml.Vector4f(direction,tangents[at+3]*sign);
    }

    private static byte component(double value) {
        return (byte)(Math.clamp(value, -1, 1) * 127);
    }
}

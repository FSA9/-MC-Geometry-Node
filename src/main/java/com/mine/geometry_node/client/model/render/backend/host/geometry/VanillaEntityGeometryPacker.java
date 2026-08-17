package com.mine.geometry_node.client.model.render.backend.host.geometry;

import org.joml.Matrix3fc;
import org.joml.Matrix4fc;
import org.joml.Vector3f;

import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.util.Arrays;
import java.util.Objects;

/** Packs a CPU HOST plan into a validated vanilla ENTITY/QUADS layout descriptor. */
public final class VanillaEntityGeometryPacker {
    private VanillaEntityGeometryPacker() {}

    public static PackedGeometry pack(HostStaticGeometryPlan plan, Matrix4fc poseTransform,
                                      Matrix3fc normalTransform, EntityLayout layout,
                                      int packedOverlay, int packedLight, boolean mirrored) {
        Objects.requireNonNull(plan, "plan");
        Objects.requireNonNull(poseTransform, "poseTransform");
        Objects.requireNonNull(normalTransform, "normalTransform");
        Objects.requireNonNull(layout, "layout");
        byte[] sourceBytes = plan.packedSource();
        ByteBuffer source = ByteBuffer.wrap(sourceBytes).order(ByteOrder.LITTLE_ENDIAN);
        int[] order = plan.quadOrder(mirrored);
        int vertexCount = order.length;
        ByteBuffer output = ByteBuffer.allocate(Math.multiplyExact(vertexCount, layout.stride()))
                .order(ByteOrder.LITTLE_ENDIAN);
        Vector3f position = new Vector3f();
        Vector3f normal = new Vector3f();
        for (int outputVertex = 0; outputVertex < vertexCount; outputVertex++) {
            int sourceBase = Math.multiplyExact(order[outputVertex], HostStaticGeometryPlan.SOURCE_VERTEX_BYTES);
            int targetBase = Math.multiplyExact(outputVertex, layout.stride());
            poseTransform.transformPosition(source.getFloat(sourceBase), source.getFloat(sourceBase + 4),
                    source.getFloat(sourceBase + 8), position);
            normalTransform.transform(source.getFloat(sourceBase + 12), source.getFloat(sourceBase + 16),
                    source.getFloat(sourceBase + 20), normal);
            if (!finite(position.x) || !finite(position.y) || !finite(position.z)) {
                throw new IllegalArgumentException("baked ENTITY position must be finite");
            }
            float normalLengthSquared = normal.lengthSquared();
            if (!Float.isFinite(normalLengthSquared) || normalLengthSquared <= 1.0E-12F) {
                throw new IllegalArgumentException("baked ENTITY normal must be finite and non-zero");
            }
            normal.mul((float) (1.0 / Math.sqrt(normalLengthSquared)));

            putPosition(output, targetBase + layout.position(), position);
            putColor(output, targetBase + layout.color(), source, sourceBase + 32);
            output.putFloat(targetBase + layout.uv0(), source.getFloat(sourceBase + 24));
            output.putFloat(targetBase + layout.uv0() + Float.BYTES, source.getFloat(sourceBase + 28));
            output.putInt(targetBase + layout.overlay(), packedOverlay);
            output.putInt(targetBase + layout.light(), packedLight);
            output.put(targetBase + layout.normal(), normalByte(normal.x));
            output.put(targetBase + layout.normal() + 1, normalByte(normal.y));
            output.put(targetBase + layout.normal() + 2, normalByte(normal.z));
        }
        int indexCount = Math.multiplyExact(plan.triangleCount(), 6);
        return new PackedGeometry(output.array(), vertexCount, indexCount, layout.stride());
    }

    private static void putPosition(ByteBuffer output, int offset, Vector3f value) {
        output.putFloat(offset, value.x);
        output.putFloat(offset + Float.BYTES, value.y);
        output.putFloat(offset + Float.BYTES * 2, value.z);
    }

    private static void putColor(ByteBuffer output, int target, ByteBuffer source, int sourceColor) {
        for (int component = 0; component < 4; component++) {
            output.put(target + component, (byte) (int) (source.getFloat(sourceColor + component * Float.BYTES) * 255));
        }
    }

    private static byte normalByte(float value) {
        return (byte) ((int) (Math.max(-1, Math.min(1, value)) * 127) & 0xFF);
    }

    private static boolean finite(float value) { return Float.isFinite(value); }

    public record EntityLayout(int stride, int position, int color, int uv0,
                               int overlay, int light, int normal) {
        public EntityLayout {
            if (stride < 1 || !fits(position, 12, stride) || !fits(color, 4, stride)
                    || !fits(uv0, 8, stride) || !fits(overlay, 4, stride)
                    || !fits(light, 4, stride) || !fits(normal, 3, stride)) {
                throw new IllegalArgumentException("ENTITY layout elements must fit within the vertex stride");
            }
        }

        private static boolean fits(int offset, int bytes, int stride) {
            return offset >= 0 && offset <= stride - bytes;
        }
    }

    public record PackedGeometry(byte[] vertexData, int vertexCount, int indexCount, int vertexStride) {
        public PackedGeometry {
            vertexData = Arrays.copyOf(Objects.requireNonNull(vertexData, "vertexData"), vertexData.length);
            if (vertexCount < 0 || indexCount < 0 || vertexStride < 1
                    || vertexData.length != Math.multiplyExact(vertexCount, vertexStride)) {
                throw new IllegalArgumentException("invalid packed ENTITY geometry");
            }
        }

        @Override public byte[] vertexData() { return Arrays.copyOf(vertexData, vertexData.length); }
    }
}

package com.mine.geometry_node.client.model.render.backend.host.geometry;

import com.mojang.blaze3d.vertex.BufferBuilder;
import com.mojang.blaze3d.vertex.ByteBufferBuilder;
import com.mojang.blaze3d.vertex.MeshData;
import com.mojang.blaze3d.vertex.VertexFormat;
import org.joml.Matrix3fc;
import org.joml.Matrix4fc;

import java.nio.ByteBuffer;
import java.util.Arrays;
import java.util.Objects;

/** Builds one complete-quad chunk through the active Minecraft/Iris BufferBuilder contract. */
public final class HostEntityMeshChunkBuilder {
    private HostEntityMeshChunkBuilder() {}

    public static BuiltChunk build(HostEntityGeometry geometry, int firstTriangle, int triangleCount,
                                   Matrix4fc pose, Matrix3fc normal, VertexFormat requestedFormat,
                                   float red, float green, float blue, float alpha,
                                   int packedLight, boolean mirrored) {
        Objects.requireNonNull(geometry, "geometry");
        Objects.requireNonNull(pose, "pose");
        Objects.requireNonNull(normal, "normal");
        Objects.requireNonNull(requestedFormat, "requestedFormat");
        if (triangleCount < 1) throw new IllegalArgumentException("mesh chunk must contain triangles");
        int estimated = Math.multiplyExact(Math.multiplyExact(triangleCount, 4), requestedFormat.getVertexSize());
        try (ByteBufferBuilder storage = new ByteBufferBuilder(Math.max(estimated, 1))) {
            BufferBuilder builder = new BufferBuilder(storage, VertexFormat.Mode.QUADS, requestedFormat);
            geometry.emitStaticRange(pose, normal, builder, red, green, blue, alpha, packedLight, mirrored,
                    firstTriangle, triangleCount);
            try (MeshData mesh = builder.buildOrThrow()) {
                MeshData.DrawState state = mesh.drawState();
                if (state.format() != requestedFormat || state.mode() != VertexFormat.Mode.QUADS) {
                    throw new IllegalStateException("active entity vertex layout changed while building static chunk");
                }
                int expectedVertices = Math.multiplyExact(triangleCount, 4);
                int expectedIndices = Math.multiplyExact(triangleCount, 6);
                ByteBuffer vertices = mesh.vertexBuffer();
                int expectedBytes = Math.multiplyExact(expectedVertices, requestedFormat.getVertexSize());
                if (state.vertexCount() != expectedVertices || state.indexCount() != expectedIndices
                        || vertices.remaining() != expectedBytes) {
                    throw new IllegalStateException("static entity chunk size does not match its draw state");
                }
                byte[] copy = new byte[vertices.remaining()];
                vertices.get(copy);
                return new BuiltChunk(requestedFormat, copy, expectedVertices, expectedIndices);
            }
        }
    }

    public record BuiltChunk(VertexFormat format, byte[] vertexData, int vertexCount, int indexCount) {
        public BuiltChunk {
            Objects.requireNonNull(format, "format");
            vertexData = Arrays.copyOf(Objects.requireNonNull(vertexData, "vertexData"), vertexData.length);
            if (vertexCount < 1 || indexCount < 1
                    || vertexData.length != Math.multiplyExact(vertexCount, format.getVertexSize())) {
                throw new IllegalArgumentException("invalid static entity mesh chunk");
            }
        }

        @Override public byte[] vertexData() { return Arrays.copyOf(vertexData, vertexData.length); }
    }
}

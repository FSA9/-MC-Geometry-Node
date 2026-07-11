package com.mine.geometry_node.core.network.packet.s2c;

import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.Identifier;

import java.util.ArrayList;
import java.util.List;

public record PacketGeometryDebugSnapshot(
        boolean enabled,
        double radius,
        List<Mesh> meshes
) implements CustomPacketPayload {
    public static final Type<PacketGeometryDebugSnapshot> TYPE =
            new Type<>(Identifier.fromNamespaceAndPath("geometry_node", "geometry_debug_snapshot"));

    public static final StreamCodec<RegistryFriendlyByteBuf, PacketGeometryDebugSnapshot> STREAM_CODEC = StreamCodec.of(
            (buf, packet) -> packet.write(buf),
            PacketGeometryDebugSnapshot::new
    );

    private static final int MAX_MESHES = 256;
    private static final int MAX_VERTICES = 65535;
    private static final int MAX_EDGES = 262144;
    private static final int MAX_FACES = 262144;

    public PacketGeometryDebugSnapshot {
        meshes = List.copyOf(meshes);
    }

    public PacketGeometryDebugSnapshot(RegistryFriendlyByteBuf buf) {
        this(buf.readBoolean(), buf.readDouble(), readMeshes(buf));
    }

    public void write(RegistryFriendlyByteBuf buf) {
        buf.writeBoolean(enabled);
        buf.writeDouble(radius);
        buf.writeInt(meshes.size());
        for (Mesh mesh : meshes) {
            mesh.write(buf);
        }
    }

    private static List<Mesh> readMeshes(RegistryFriendlyByteBuf buf) {
        int size = readBoundedCount(buf, MAX_MESHES, "mesh");
        List<Mesh> meshes = new ArrayList<>(size);
        for (int i = 0; i < size; i++) {
            meshes.add(new Mesh(buf));
        }
        return meshes;
    }

    private static int readBoundedCount(RegistryFriendlyByteBuf buf, int max, String label) {
        int count = buf.readInt();
        if (count < 0 || count > max) {
            throw new IllegalArgumentException("Invalid geometry debug " + label + " count: " + count);
        }
        return count;
    }

    @Override
    public Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }

    public record Mesh(
            String id,
            String graphId,
            double centerX,
            double centerY,
            double centerZ,
            float[] vertices,
            int[] edges,
            int[] faces
    ) {
        public Mesh {
            vertices = vertices != null ? vertices : new float[0];
            edges = edges != null ? edges : new int[0];
            faces = faces != null ? faces : new int[0];
        }

        private Mesh(RegistryFriendlyByteBuf buf) {
            this(
                    buf.readUtf(32767),
                    buf.readUtf(32767),
                    buf.readDouble(),
                    buf.readDouble(),
                    buf.readDouble(),
                    readFloatArray(buf, MAX_VERTICES, 3, "vertex"),
                    readIntArray(buf, MAX_EDGES, 2, "edge"),
                    readIntArray(buf, MAX_FACES, 4, "face")
            );
        }

        private void write(RegistryFriendlyByteBuf buf) {
            buf.writeUtf(id, 32767);
            buf.writeUtf(graphId, 32767);
            buf.writeDouble(centerX);
            buf.writeDouble(centerY);
            buf.writeDouble(centerZ);
            writeFloatArray(buf, vertices, 3);
            writeIntArray(buf, edges, 2);
            writeIntArray(buf, faces, 4);
        }

        public int vertexCount() {
            return vertices.length / 3;
        }

        public int edgeCount() {
            return edges.length / 2;
        }

        public int faceCount() {
            return faces.length / 4;
        }

        private static float[] readFloatArray(RegistryFriendlyByteBuf buf, int maxRecords, int stride, String label) {
            int count = readBoundedCount(buf, maxRecords, label);
            float[] values = new float[count * stride];
            for (int i = 0; i < values.length; i++) {
                values[i] = buf.readFloat();
            }
            return values;
        }

        private static int[] readIntArray(RegistryFriendlyByteBuf buf, int maxRecords, int stride, String label) {
            int count = readBoundedCount(buf, maxRecords, label);
            int[] values = new int[count * stride];
            for (int i = 0; i < values.length; i++) {
                values[i] = buf.readInt();
            }
            return values;
        }

        private static void writeFloatArray(RegistryFriendlyByteBuf buf, float[] values, int stride) {
            buf.writeInt(values.length / stride);
            for (float value : values) {
                buf.writeFloat(value);
            }
        }

        private static void writeIntArray(RegistryFriendlyByteBuf buf, int[] values, int stride) {
            buf.writeInt(values.length / stride);
            for (int value : values) {
                buf.writeInt(value);
            }
        }
    }
}

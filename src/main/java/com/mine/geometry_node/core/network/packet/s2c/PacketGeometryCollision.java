package com.mine.geometry_node.core.network.packet.s2c;

import com.mine.geometry_node.core.engine.system.model.collision.ModelCollisionMesh;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.Identifier;
import net.minecraft.world.phys.Vec3;

import java.util.ArrayList;
import java.util.UUID;

/** Meshes are sent only on creation/shape changes; movement has a constant-size payload. */
public record PacketGeometryCollision(Identifier dimension, String resourceId, int operation,
                                      Vec3 position, ModelCollisionMesh mesh, UUID excludedEntity) implements CustomPacketPayload {
    public PacketGeometryCollision(Identifier dimension, String resourceId, int operation, Vec3 position, ModelCollisionMesh mesh) {
        this(dimension, resourceId, operation, position, mesh, null);
    }
    public static final int UPSERT = 0, MOVE = 1, REMOVE = 2;
    public static final int MAX_PARTS = 128;
    public static final long MAX_BYTES = 512 * 1024;
    public static final Type<PacketGeometryCollision> TYPE = new Type<>(
            Identifier.fromNamespaceAndPath("geometry_node", "geometry_collision"));
    public static final StreamCodec<RegistryFriendlyByteBuf, PacketGeometryCollision> STREAM_CODEC =
            StreamCodec.of(PacketGeometryCollision::write, PacketGeometryCollision::read);

    public static void validateMesh(ModelCollisionMesh mesh) {
        if (mesh == null || mesh.parts().isEmpty() || mesh.parts().size() > MAX_PARTS
                || mesh.parts().stream().mapToLong(ModelCollisionMesh.Part::estimatedGeometryBytes).sum() > MAX_BYTES)
            throw new IllegalArgumentException("geometry collision exceeds network geometry budget");
    }

    private static void write(RegistryFriendlyByteBuf buf, PacketGeometryCollision packet) {
        buf.writeIdentifier(packet.dimension);
        buf.writeUtf(packet.resourceId);
        buf.writeByte(packet.operation);
        if (packet.operation == REMOVE) return;
        buf.writeDouble(packet.position.x); buf.writeDouble(packet.position.y); buf.writeDouble(packet.position.z);
        if (packet.operation != UPSERT) return;
        buf.writeBoolean(packet.excludedEntity != null);
        if (packet.excludedEntity != null) buf.writeUUID(packet.excludedEntity);
        validateMesh(packet.mesh);
        buf.writeVarInt(packet.mesh.parts().size());
        for (var part : packet.mesh.parts()) {
            writeDoubles(buf, part.vertices());
            int[] triangles = part.triangles();
            buf.writeVarInt(triangles.length);
            for (int index : triangles) buf.writeVarInt(index);
            writeDoubles(buf, part.planes());
        }
    }

    private static PacketGeometryCollision read(RegistryFriendlyByteBuf buf) {
        Identifier dimension = buf.readIdentifier();
        String id = buf.readUtf();
        int operation = buf.readUnsignedByte();
        if (operation < UPSERT || operation > REMOVE) throw new IllegalArgumentException("invalid collision operation");
        if (operation == REMOVE) return new PacketGeometryCollision(dimension, id, operation, Vec3.ZERO, null);
        Vec3 position = new Vec3(buf.readDouble(), buf.readDouble(), buf.readDouble());
        if (!Double.isFinite(position.x) || !Double.isFinite(position.y) || !Double.isFinite(position.z))
            throw new IllegalArgumentException("invalid collision position");
        if (operation == MOVE) return new PacketGeometryCollision(dimension, id, operation, position, null);
        UUID excludedEntity = buf.readBoolean() ? buf.readUUID() : null;
        int count = bounded(buf.readVarInt(), MAX_PARTS);
        var parts = new ArrayList<ModelCollisionMesh.Part>(count);
        int trianglesTotal = 0;
        long bytes = 0;
        for (int i = 0; i < count; i++) {
            double[] vertices = readDoubles(buf, 384);
            int[] triangles = new int[bounded(buf.readVarInt(), 768)];
            for (int t = 0; t < triangles.length; t++) triangles[t] = buf.readVarInt();
            double[] planes = readDoubles(buf, 512);
            var part = new ModelCollisionMesh.Part(vertices, triangles, planes);
            bytes += part.estimatedGeometryBytes();
            if (bytes > MAX_BYTES) throw new IllegalArgumentException("collision payload budget exceeded");
            parts.add(part);
            trianglesTotal += triangles.length / 3;
        }
        ModelCollisionMesh mesh = new ModelCollisionMesh(parts, trianglesTotal);
        validateMesh(mesh);
        return new PacketGeometryCollision(dimension, id, operation, position, mesh, excludedEntity);
    }

    private static int bounded(int value, int maximum) {
        if (value < 1 || value > maximum) throw new IllegalArgumentException("invalid collision array length");
        return value;
    }

    private static void writeDoubles(RegistryFriendlyByteBuf buf, double[] values) {
        buf.writeVarInt(values.length);
        for (double value : values) buf.writeDouble(value);
    }

    private static double[] readDoubles(RegistryFriendlyByteBuf buf, int maximum) {
        double[] values = new double[bounded(buf.readVarInt(), maximum)];
        for (int i = 0; i < values.length; i++) values[i] = buf.readDouble();
        return values;
    }

    @Override public Type<? extends CustomPacketPayload> type() { return TYPE; }
}

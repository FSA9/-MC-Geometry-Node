package com.mine.geometry_node.core.network.packet.viewport;

import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.Identifier;
import net.minecraft.world.phys.Vec3;
import java.util.*;

public record PacketViewportEntityMove(UUID requestId, Identifier dimension, List<UUID> entities,
                                      Vec3 camera, Vec3 origin, Vec3 direction, double length) implements CustomPacketPayload {
    public static final int MAX_ENTITIES = 128;
    public static final double MAX_RAY = 4096;
    public static final Type<PacketViewportEntityMove> TYPE = new Type<>(Identifier.fromNamespaceAndPath("geometry_node", "viewport_entity_move"));
    public PacketViewportEntityMove {
        Objects.requireNonNull(requestId); Objects.requireNonNull(dimension);
        entities = List.copyOf(entities);
        if (entities.isEmpty() || entities.size() > MAX_ENTITIES || new HashSet<>(entities).size() != entities.size()
                || !finite(camera) || !finite(origin) || !finite(direction) || Math.abs(direction.lengthSqr() - 1) > .001
                || !Double.isFinite(length) || length <= 0 || length > MAX_RAY
                || camera.distanceToSqr(origin) > MAX_RAY * MAX_RAY)
            throw new IllegalArgumentException("Invalid viewport movement request");
    }
    private static boolean finite(Vec3 p) { return p != null && Double.isFinite(p.x) && Double.isFinite(p.y) && Double.isFinite(p.z)
            && Math.abs(p.x) <= 30_000_000 && Math.abs(p.y) <= 30_000_000 && Math.abs(p.z) <= 30_000_000; }
    public static final StreamCodec<RegistryFriendlyByteBuf, PacketViewportEntityMove> STREAM_CODEC = StreamCodec.of((b,p) -> {
        b.writeUUID(p.requestId); b.writeIdentifier(p.dimension); b.writeVarInt(p.entities.size());
        p.entities.forEach(b::writeUUID); vector(b,p.camera); vector(b,p.origin); vector(b,p.direction); b.writeDouble(p.length);
    }, b -> {
        UUID request = b.readUUID(); Identifier dimension = b.readIdentifier(); int n = b.readVarInt();
        if (n < 1 || n > MAX_ENTITIES) throw new IllegalArgumentException("Invalid entity count");
        List<UUID> ids = new ArrayList<>(n); for (int i=0;i<n;i++) ids.add(b.readUUID());
        return new PacketViewportEntityMove(request,dimension,ids,vector(b),vector(b),vector(b),b.readDouble());
    });
    private static void vector(RegistryFriendlyByteBuf b, Vec3 p) { b.writeDouble(p.x); b.writeDouble(p.y); b.writeDouble(p.z); }
    private static Vec3 vector(RegistryFriendlyByteBuf b) { return new Vec3(b.readDouble(),b.readDouble(),b.readDouble()); }
    @Override public Type<? extends CustomPacketPayload> type() { return TYPE; }
}

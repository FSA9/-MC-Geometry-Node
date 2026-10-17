package com.mine.geometry_node.core.network.packet.viewport;

import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.Identifier;
import net.minecraft.world.phys.Vec3;
import java.util.Objects;
import java.util.UUID;

public record PacketViewportInteractionCreate(UUID requestId, Identifier dimension, Vec3 position)
        implements CustomPacketPayload {
    public static final Type<PacketViewportInteractionCreate> TYPE = new Type<>(
            Identifier.fromNamespaceAndPath("geometry_node", "viewport_interaction_create"));
    public PacketViewportInteractionCreate {
        Objects.requireNonNull(requestId);
        Objects.requireNonNull(dimension);
        Objects.requireNonNull(position);
        for (double value : new double[]{position.x, position.y, position.z})
            if (!Double.isFinite(value) || Math.abs(value) > 30_000_000)
                throw new IllegalArgumentException("Invalid interaction position");
    }
    public static final StreamCodec<RegistryFriendlyByteBuf, PacketViewportInteractionCreate> STREAM_CODEC = StreamCodec.of(
            (buf, packet) -> {
                buf.writeUUID(packet.requestId); buf.writeIdentifier(packet.dimension);
                buf.writeDouble(packet.position.x); buf.writeDouble(packet.position.y); buf.writeDouble(packet.position.z);
            }, buf -> new PacketViewportInteractionCreate(buf.readUUID(), buf.readIdentifier(),
                    new Vec3(buf.readDouble(), buf.readDouble(), buf.readDouble())));
    @Override public Type<? extends CustomPacketPayload> type() { return TYPE; }
}

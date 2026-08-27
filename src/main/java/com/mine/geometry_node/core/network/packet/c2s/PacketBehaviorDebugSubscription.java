package com.mine.geometry_node.core.network.packet.c2s;

import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.Identifier;

import java.util.UUID;

/** Explicit subscribe/cancel request for one behavior instance. */
public record PacketBehaviorDebugSubscription(UUID instanceId, boolean subscribe)
        implements CustomPacketPayload {
    public static final Type<PacketBehaviorDebugSubscription> TYPE = new Type<>(
            Identifier.fromNamespaceAndPath("geometry_node", "behavior_debug_subscription"));
    public static final StreamCodec<RegistryFriendlyByteBuf, PacketBehaviorDebugSubscription> STREAM_CODEC =
            StreamCodec.of((buffer, packet) -> {
                buffer.writeUUID(packet.instanceId);
                buffer.writeBoolean(packet.subscribe);
            }, buffer -> new PacketBehaviorDebugSubscription(buffer.readUUID(), buffer.readBoolean()));

    @Override
    public Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }
}

package com.mine.geometry_node.core.network.packet.c2s;

import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;

public record PacketRemoteGraphCapabilitiesRequest(int requestId) implements CustomPacketPayload {
    public static final Type<PacketRemoteGraphCapabilitiesRequest> TYPE =
            new Type<>(ResourceLocation.fromNamespaceAndPath("geometry_node", "remote_graph_capabilities_request"));

    public static final StreamCodec<RegistryFriendlyByteBuf, PacketRemoteGraphCapabilitiesRequest> STREAM_CODEC = StreamCodec.of(
            (buf, packet) -> buf.writeInt(packet.requestId),
            buf -> new PacketRemoteGraphCapabilitiesRequest(buf.readInt())
    );

    @Override
    public Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }
}

package com.mine.geometry_node.core.network.packet.c2s;

import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.Identifier;

public record PacketRemoteAssetCapabilitiesRequest(int requestId) implements CustomPacketPayload {
    public static final Type<PacketRemoteAssetCapabilitiesRequest> TYPE =
            new Type<>(Identifier.fromNamespaceAndPath("geometry_node", "remote_asset_capabilities_request"));

    public static final StreamCodec<RegistryFriendlyByteBuf, PacketRemoteAssetCapabilitiesRequest> STREAM_CODEC = StreamCodec.of(
            (buf, packet) -> buf.writeInt(packet.requestId),
            buf -> new PacketRemoteAssetCapabilitiesRequest(buf.readInt())
    );

    @Override
    public Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }
}

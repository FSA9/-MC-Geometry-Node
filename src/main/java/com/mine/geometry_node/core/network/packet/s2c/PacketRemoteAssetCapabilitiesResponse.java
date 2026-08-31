package com.mine.geometry_node.core.network.packet.s2c;

import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.Identifier;

public record PacketRemoteAssetCapabilitiesResponse(
        int requestId,
        boolean canBrowse,
        boolean canUpload,
        boolean canDownload,
        boolean canManage
) implements CustomPacketPayload {
    public static final Type<PacketRemoteAssetCapabilitiesResponse> TYPE =
            new Type<>(Identifier.fromNamespaceAndPath("geometry_node", "remote_asset_capabilities_response"));

    public static final StreamCodec<RegistryFriendlyByteBuf, PacketRemoteAssetCapabilitiesResponse> STREAM_CODEC = StreamCodec.of(
            (buf, packet) -> {
                buf.writeInt(packet.requestId);
                buf.writeBoolean(packet.canBrowse);
                buf.writeBoolean(packet.canUpload);
                buf.writeBoolean(packet.canDownload);
                buf.writeBoolean(packet.canManage);
            },
            buf -> new PacketRemoteAssetCapabilitiesResponse(buf.readInt(), buf.readBoolean(), buf.readBoolean(), buf.readBoolean(), buf.readBoolean())
    );

    @Override
    public Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }
}

package com.mine.geometry_node.core.network.packet.s2c;

import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;

public record PacketRemoteGraphCapabilitiesResponse(
        int requestId,
        boolean canBrowse,
        boolean canUpload,
        boolean canDownload,
        boolean canManage
) implements CustomPacketPayload {
    public static final Type<PacketRemoteGraphCapabilitiesResponse> TYPE =
            new Type<>(ResourceLocation.fromNamespaceAndPath("geometry_node", "remote_graph_capabilities_response"));

    public static final StreamCodec<RegistryFriendlyByteBuf, PacketRemoteGraphCapabilitiesResponse> STREAM_CODEC = StreamCodec.of(
            (buf, packet) -> {
                buf.writeInt(packet.requestId);
                buf.writeBoolean(packet.canBrowse);
                buf.writeBoolean(packet.canUpload);
                buf.writeBoolean(packet.canDownload);
                buf.writeBoolean(packet.canManage);
            },
            buf -> new PacketRemoteGraphCapabilitiesResponse(buf.readInt(), buf.readBoolean(), buf.readBoolean(), buf.readBoolean(), buf.readBoolean())
    );

    @Override
    public Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }
}

package com.mine.geometry_node.core.network.packet.s2c;

import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.Identifier;

public record PacketRemoteGraphFileOperationResponse(
        int requestId,
        boolean success,
        String message
) implements CustomPacketPayload {
    public static final Type<PacketRemoteGraphFileOperationResponse> TYPE =
            new Type<>(Identifier.fromNamespaceAndPath("geometry_node", "remote_graph_file_operation_response"));

    public static final StreamCodec<RegistryFriendlyByteBuf, PacketRemoteGraphFileOperationResponse> STREAM_CODEC = StreamCodec.of(
            (buf, packet) -> {
                buf.writeInt(packet.requestId);
                buf.writeBoolean(packet.success);
                buf.writeUtf(packet.message, 32767);
            },
            buf -> new PacketRemoteGraphFileOperationResponse(buf.readInt(), buf.readBoolean(), buf.readUtf(32767))
    );

    @Override
    public Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }
}

package com.mine.geometry_node.core.network.packet.c2s;

import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.Identifier;

public record PacketRemoteGraphListRequest(int requestId, String directory, boolean createIfMissing) implements CustomPacketPayload {
    public static final Type<PacketRemoteGraphListRequest> TYPE =
            new Type<>(Identifier.fromNamespaceAndPath("geometry_node", "remote_graph_list_request"));

    public static final StreamCodec<RegistryFriendlyByteBuf, PacketRemoteGraphListRequest> STREAM_CODEC = StreamCodec.of(
            (buf, packet) -> {
                buf.writeInt(packet.requestId);
                buf.writeUtf(packet.directory);
                buf.writeBoolean(packet.createIfMissing);
            },
            buf -> new PacketRemoteGraphListRequest(buf.readInt(), buf.readUtf(32767), buf.readBoolean())
    );

    public PacketRemoteGraphListRequest(int requestId, String directory) {
        this(requestId, directory, false);
    }

    @Override
    public Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }
}

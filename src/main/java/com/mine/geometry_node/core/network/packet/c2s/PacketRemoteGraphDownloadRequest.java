package com.mine.geometry_node.core.network.packet.c2s;

import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;

import java.util.ArrayList;
import java.util.List;

public record PacketRemoteGraphDownloadRequest(int requestId, List<String> paths) implements CustomPacketPayload {
    public static final Type<PacketRemoteGraphDownloadRequest> TYPE =
            new Type<>(ResourceLocation.fromNamespaceAndPath("geometry_node", "remote_graph_download_request"));

    public static final StreamCodec<RegistryFriendlyByteBuf, PacketRemoteGraphDownloadRequest> STREAM_CODEC = StreamCodec.of(
            (buf, packet) -> {
                buf.writeInt(packet.requestId);
                buf.writeInt(packet.paths.size());
                for (String path : packet.paths) {
                    buf.writeUtf(path, 32767);
                }
            },
            buf -> {
                int requestId = buf.readInt();
                int size = buf.readInt();
                List<String> paths = new ArrayList<>(size);
                for (int i = 0; i < size; i++) {
                    paths.add(buf.readUtf(32767));
                }
                return new PacketRemoteGraphDownloadRequest(requestId, paths);
            }
    );

    @Override
    public Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }
}

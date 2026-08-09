package com.mine.geometry_node.core.network.packet.s2c;

import com.mine.geometry_node.core.engine.system.asset.RemoteAssetFile;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.Identifier;

import java.util.ArrayList;
import java.util.List;

public record PacketRemoteGraphDownloadResponse(
        int requestId,
        boolean success,
        boolean terminal,
        int processed,
        int total,
        String message,
        List<RemoteAssetFile> files
) implements CustomPacketPayload {
    public static final Type<PacketRemoteGraphDownloadResponse> TYPE =
            new Type<>(Identifier.fromNamespaceAndPath("geometry_node", "remote_graph_download_response"));

    public static final StreamCodec<RegistryFriendlyByteBuf, PacketRemoteGraphDownloadResponse> STREAM_CODEC = StreamCodec.of(
            (buf, packet) -> packet.write(buf),
            PacketRemoteGraphDownloadResponse::new
    );

    public PacketRemoteGraphDownloadResponse(RegistryFriendlyByteBuf buf) {
        this(buf.readInt(), buf.readBoolean(), buf.readBoolean(),
                buf.readInt(), buf.readInt(), buf.readUtf(32767), readFiles(buf));
    }

    public void write(RegistryFriendlyByteBuf buf) {
        buf.writeInt(requestId);
        buf.writeBoolean(success);
        buf.writeBoolean(terminal);
        buf.writeInt(processed);
        buf.writeInt(total);
        buf.writeUtf(message, 32767);
        buf.writeInt(files.size());
        for (RemoteAssetFile file : files) {
            buf.writeUtf(file.targetPath(), 32767);
            buf.writeByteArray(file.content());
        }
    }

    private static List<RemoteAssetFile> readFiles(RegistryFriendlyByteBuf buf) {
        int size = buf.readInt();
        List<RemoteAssetFile> files = new ArrayList<>(size);
        for (int i = 0; i < size; i++) {
            files.add(new RemoteAssetFile(
                    buf.readUtf(32767),
                    buf.readByteArray(RemoteAssetFile.MAX_CONTENT_BYTES)));
        }
        return files;
    }

    @Override
    public Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }
}

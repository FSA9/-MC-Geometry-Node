package com.mine.geometry_node.core.network.packet.s2c;

import com.mine.geometry_node.core.engine.graph.storage.RemoteGraphConflict;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;

import java.util.ArrayList;
import java.util.List;

public record PacketRemoteGraphUploadResponse(
        int requestId,
        boolean preflight,
        boolean success,
        int processed,
        int total,
        String message,
        List<RemoteGraphConflict> conflicts
) implements CustomPacketPayload {
    public static final Type<PacketRemoteGraphUploadResponse> TYPE =
            new Type<>(ResourceLocation.fromNamespaceAndPath("geometry_node", "remote_graph_upload_response"));

    public static final StreamCodec<RegistryFriendlyByteBuf, PacketRemoteGraphUploadResponse> STREAM_CODEC = StreamCodec.of(
            (buf, packet) -> packet.write(buf),
            PacketRemoteGraphUploadResponse::new
    );

    public PacketRemoteGraphUploadResponse(RegistryFriendlyByteBuf buf) {
        this(buf.readInt(), buf.readBoolean(), buf.readBoolean(), buf.readInt(), buf.readInt(), buf.readUtf(32767), readConflicts(buf));
    }

    public void write(RegistryFriendlyByteBuf buf) {
        buf.writeInt(requestId);
        buf.writeBoolean(preflight);
        buf.writeBoolean(success);
        buf.writeInt(processed);
        buf.writeInt(total);
        buf.writeUtf(message, 32767);
        buf.writeInt(conflicts.size());
        for (RemoteGraphConflict conflict : conflicts) {
            buf.writeUtf(conflict.sourcePath(), 32767);
            buf.writeUtf(conflict.targetPath(), 32767);
            buf.writeBoolean(conflict.directory());
        }
    }

    private static List<RemoteGraphConflict> readConflicts(RegistryFriendlyByteBuf buf) {
        int size = buf.readInt();
        List<RemoteGraphConflict> conflicts = new ArrayList<>(size);
        for (int i = 0; i < size; i++) {
            conflicts.add(new RemoteGraphConflict(buf.readUtf(32767), buf.readUtf(32767), buf.readBoolean()));
        }
        return conflicts;
    }

    @Override
    public Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }
}

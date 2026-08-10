package com.mine.geometry_node.core.network.packet.s2c;

import com.mine.geometry_node.core.engine.graph.storage.RemoteGraphEntry;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.Identifier;

import java.util.ArrayList;
import java.util.List;

public record PacketRemoteGraphListResponse(
        int requestId,
        boolean success,
        String directory,
        String message,
        List<RemoteGraphEntry> entries
) implements CustomPacketPayload {
    public static final Type<PacketRemoteGraphListResponse> TYPE =
            new Type<>(Identifier.fromNamespaceAndPath("geometry_node", "remote_graph_list_response"));

    public static final StreamCodec<RegistryFriendlyByteBuf, PacketRemoteGraphListResponse> STREAM_CODEC = StreamCodec.of(
            (buf, packet) -> packet.write(buf),
            PacketRemoteGraphListResponse::new
    );

    public PacketRemoteGraphListResponse(RegistryFriendlyByteBuf buf) {
        this(buf.readInt(), buf.readBoolean(), buf.readUtf(32767), buf.readUtf(32767), readEntries(buf));
    }

    public void write(RegistryFriendlyByteBuf buf) {
        buf.writeInt(requestId);
        buf.writeBoolean(success);
        buf.writeUtf(directory, 32767);
        buf.writeUtf(message, 32767);
        buf.writeInt(entries.size());
        for (RemoteGraphEntry entry : entries) {
            buf.writeUtf(entry.path(), 32767);
            buf.writeUtf(entry.name(), 32767);
            buf.writeBoolean(entry.directory());
            buf.writeLong(entry.size());
            buf.writeLong(entry.lastModified());
            buf.writeUtf(entry.graphTypeId(), 32767);
        }
    }

    private static List<RemoteGraphEntry> readEntries(RegistryFriendlyByteBuf buf) {
        int size = buf.readInt();
        List<RemoteGraphEntry> entries = new ArrayList<>(size);
        for (int i = 0; i < size; i++) {
            entries.add(new RemoteGraphEntry(
                    buf.readUtf(32767),
                    buf.readUtf(32767),
                    buf.readBoolean(),
                    buf.readLong(),
                    buf.readLong(),
                    buf.readUtf(32767)
            ));
        }
        return entries;
    }

    @Override
    public Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }
}

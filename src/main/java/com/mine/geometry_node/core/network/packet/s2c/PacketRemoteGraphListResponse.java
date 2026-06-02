package com.mine.geometry_node.core.network.packet.s2c;

import com.mine.geometry_node.core.engine.blueprint.execution.storage.RemoteGraphFileService;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;

import java.util.ArrayList;
import java.util.List;

public record PacketRemoteGraphListResponse(
        int requestId,
        boolean success,
        String directory,
        String message,
        List<RemoteGraphFileService.Entry> entries
) implements CustomPacketPayload {
    public static final Type<PacketRemoteGraphListResponse> TYPE =
            new Type<>(ResourceLocation.fromNamespaceAndPath("geometry_node", "remote_graph_list_response"));

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
        for (RemoteGraphFileService.Entry entry : entries) {
            buf.writeUtf(entry.path(), 32767);
            buf.writeUtf(entry.name(), 32767);
            buf.writeBoolean(entry.directory());
            buf.writeLong(entry.size());
        }
    }

    private static List<RemoteGraphFileService.Entry> readEntries(RegistryFriendlyByteBuf buf) {
        int size = buf.readInt();
        List<RemoteGraphFileService.Entry> entries = new ArrayList<>(size);
        for (int i = 0; i < size; i++) {
            entries.add(new RemoteGraphFileService.Entry(
                    buf.readUtf(32767),
                    buf.readUtf(32767),
                    buf.readBoolean(),
                    buf.readLong()
            ));
        }
        return entries;
    }

    @Override
    public Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }
}

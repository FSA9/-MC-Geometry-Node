package com.mine.geometry_node.core.network.packet.asset.repository;

import com.mine.geometry_node.core.engine.system.asset.RemoteAssetEntry;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.Identifier;

import java.util.ArrayList;
import java.util.List;

public record PacketRemoteAssetListResponse(
        int requestId,
        boolean success,
        String directory,
        String message,
        List<RemoteAssetEntry> entries
) implements CustomPacketPayload {
    public static final Type<PacketRemoteAssetListResponse> TYPE =
            new Type<>(Identifier.fromNamespaceAndPath("geometry_node", "remote_asset_list_response"));

    public static final StreamCodec<RegistryFriendlyByteBuf, PacketRemoteAssetListResponse> STREAM_CODEC = StreamCodec.of(
            (buf, packet) -> packet.write(buf),
            PacketRemoteAssetListResponse::new
    );

    public PacketRemoteAssetListResponse(RegistryFriendlyByteBuf buf) {
        this(buf.readInt(), buf.readBoolean(), buf.readUtf(32767), buf.readUtf(32767), readEntries(buf));
    }

    public void write(RegistryFriendlyByteBuf buf) {
        buf.writeInt(requestId);
        buf.writeBoolean(success);
        buf.writeUtf(directory, 32767);
        buf.writeUtf(message, 32767);
        buf.writeInt(entries.size());
        for (RemoteAssetEntry entry : entries) {
            buf.writeUtf(entry.path(), 32767);
            buf.writeUtf(entry.name(), 32767);
            buf.writeBoolean(entry.directory());
            buf.writeLong(entry.size());
            buf.writeLong(entry.lastModified());
            buf.writeUtf(entry.assetTypeId(), 32767);
            buf.writeUtf(entry.variantId(), 32767);
        }
    }

    private static List<RemoteAssetEntry> readEntries(RegistryFriendlyByteBuf buf) {
        int size = buf.readInt();
        List<RemoteAssetEntry> entries = new ArrayList<>(size);
        for (int i = 0; i < size; i++) {
            entries.add(new RemoteAssetEntry(
                    buf.readUtf(32767),
                    buf.readUtf(32767),
                    buf.readBoolean(),
                    buf.readLong(),
                    buf.readLong(),
                    buf.readUtf(32767),
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

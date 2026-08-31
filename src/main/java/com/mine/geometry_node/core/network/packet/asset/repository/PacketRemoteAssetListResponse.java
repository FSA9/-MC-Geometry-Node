package com.mine.geometry_node.core.network.packet.asset.repository;

import com.mine.geometry_node.core.engine.system.asset.RemoteAssetEntry;
import com.mine.geometry_node.core.network.packet.asset.AssetPacketCodecs;
import com.mine.geometry_node.core.network.packet.asset.AssetPacketLimits;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.Identifier;

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
        this(buf.readInt(), buf.readBoolean(),
                buf.readUtf(AssetPacketLimits.MAX_PATH_LENGTH),
                buf.readUtf(AssetPacketLimits.MAX_MESSAGE_LENGTH),
                AssetPacketCodecs.readRemoteAssetEntries(buf, AssetPacketLimits.MAX_REPOSITORY_ENTRIES));
    }

    public PacketRemoteAssetListResponse {
        directory = directory == null ? "" : directory;
        message = message == null ? "" : message;
        entries = entries == null ? List.of() : List.copyOf(entries);
        AssetPacketLimits.requireCount(entries.size(), AssetPacketLimits.MAX_REPOSITORY_ENTRIES,
                "remote repository entry");
    }

    public void write(RegistryFriendlyByteBuf buf) {
        buf.writeInt(requestId);
        buf.writeBoolean(success);
        buf.writeUtf(directory, AssetPacketLimits.MAX_PATH_LENGTH);
        buf.writeUtf(message, AssetPacketLimits.MAX_MESSAGE_LENGTH);
        AssetPacketCodecs.writeRemoteAssetEntries(buf, entries, AssetPacketLimits.MAX_REPOSITORY_ENTRIES);
    }

    @Override
    public Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }
}

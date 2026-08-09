package com.mine.geometry_node.core.network.packet.asset;

import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.Identifier;

import java.util.Objects;
import java.util.UUID;

public record PacketAssetTransferDownloadComplete(UUID transferId) implements CustomPacketPayload {
    public static final Type<PacketAssetTransferDownloadComplete> TYPE =
            new Type<>(Identifier.fromNamespaceAndPath("geometry_node", "asset_transfer_download_complete"));
    public static final StreamCodec<RegistryFriendlyByteBuf, PacketAssetTransferDownloadComplete> STREAM_CODEC =
            StreamCodec.of((buffer, packet) -> buffer.writeUUID(packet.transferId),
                    buffer -> new PacketAssetTransferDownloadComplete(buffer.readUUID()));

    public PacketAssetTransferDownloadComplete { transferId = Objects.requireNonNull(transferId, "transferId"); }
    @Override public Type<? extends CustomPacketPayload> type() { return TYPE; }
}

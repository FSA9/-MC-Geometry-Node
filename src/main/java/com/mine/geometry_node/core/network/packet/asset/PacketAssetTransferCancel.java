package com.mine.geometry_node.core.network.packet.asset;

import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.Identifier;

import java.util.Objects;
import java.util.UUID;

public record PacketAssetTransferCancel(UUID transferId, String reason) implements CustomPacketPayload {
    public static final Type<PacketAssetTransferCancel> TYPE =
            new Type<>(Identifier.fromNamespaceAndPath("geometry_node", "asset_transfer_cancel"));
    public static final StreamCodec<RegistryFriendlyByteBuf, PacketAssetTransferCancel> STREAM_CODEC = StreamCodec.of(
            (buffer, packet) -> {
                buffer.writeUUID(packet.transferId);
                buffer.writeUtf(packet.reason, AssetTransferPacketCodecs.MAX_DETAIL_LENGTH);
            }, buffer -> new PacketAssetTransferCancel(buffer.readUUID(),
                    buffer.readUtf(AssetTransferPacketCodecs.MAX_DETAIL_LENGTH)));

    public PacketAssetTransferCancel {
        transferId = Objects.requireNonNull(transferId, "transferId");
        reason = Objects.requireNonNullElse(reason, "");
    }

    @Override public Type<? extends CustomPacketPayload> type() { return TYPE; }
}

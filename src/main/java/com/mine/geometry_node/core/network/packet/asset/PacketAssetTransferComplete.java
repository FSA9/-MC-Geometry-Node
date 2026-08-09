package com.mine.geometry_node.core.network.packet.asset;

import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.Identifier;

import java.util.Objects;
import java.util.UUID;

public record PacketAssetTransferComplete(UUID transferId) implements CustomPacketPayload {
    public static final Type<PacketAssetTransferComplete> TYPE =
            new Type<>(Identifier.fromNamespaceAndPath("geometry_node", "asset_transfer_complete"));
    public static final StreamCodec<RegistryFriendlyByteBuf, PacketAssetTransferComplete> STREAM_CODEC = StreamCodec.of(
            (buffer, packet) -> buffer.writeUUID(packet.transferId),
            buffer -> new PacketAssetTransferComplete(buffer.readUUID()));

    public PacketAssetTransferComplete { transferId = Objects.requireNonNull(transferId, "transferId"); }
    @Override public Type<? extends CustomPacketPayload> type() { return TYPE; }
}

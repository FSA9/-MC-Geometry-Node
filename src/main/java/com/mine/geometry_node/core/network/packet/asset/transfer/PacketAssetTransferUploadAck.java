package com.mine.geometry_node.core.network.packet.asset.transfer;

import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.Identifier;

import java.util.Objects;
import java.util.UUID;

public record PacketAssetTransferUploadAck(UUID transferId, int nextSequence, long nextOffset)
        implements CustomPacketPayload {
    public static final Type<PacketAssetTransferUploadAck> TYPE =
            new Type<>(Identifier.fromNamespaceAndPath("geometry_node", "asset_transfer_upload_ack"));
    public static final StreamCodec<RegistryFriendlyByteBuf, PacketAssetTransferUploadAck> STREAM_CODEC = StreamCodec.of(
            (buffer, packet) -> {
                buffer.writeUUID(packet.transferId);
                buffer.writeVarInt(packet.nextSequence);
                buffer.writeLong(packet.nextOffset);
            }, buffer -> new PacketAssetTransferUploadAck(buffer.readUUID(), buffer.readVarInt(), buffer.readLong()));

    public PacketAssetTransferUploadAck {
        transferId = Objects.requireNonNull(transferId, "transferId");
        if (nextSequence < 0 || nextOffset < 0L) throw new IllegalArgumentException("Negative acknowledgement position");
    }

    @Override public Type<? extends CustomPacketPayload> type() { return TYPE; }
}

package com.mine.geometry_node.core.network.packet.asset;

import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.Identifier;

import java.util.Objects;
import java.util.UUID;

public record PacketAssetTransferAck(UUID transferId, int nextSequence, long nextOffset)
        implements CustomPacketPayload {
    public static final Type<PacketAssetTransferAck> TYPE =
            new Type<>(Identifier.fromNamespaceAndPath("geometry_node", "asset_transfer_ack"));
    public static final StreamCodec<RegistryFriendlyByteBuf, PacketAssetTransferAck> STREAM_CODEC = StreamCodec.of(
            (buffer, packet) -> {
                buffer.writeUUID(packet.transferId);
                buffer.writeVarInt(packet.nextSequence);
                buffer.writeLong(packet.nextOffset);
            }, buffer -> new PacketAssetTransferAck(buffer.readUUID(), buffer.readVarInt(), buffer.readLong()));

    public PacketAssetTransferAck {
        transferId = Objects.requireNonNull(transferId, "transferId");
        if (nextSequence < 0 || nextOffset < 0L) throw new IllegalArgumentException("Negative acknowledgement position");
    }

    @Override public Type<? extends CustomPacketPayload> type() { return TYPE; }
}

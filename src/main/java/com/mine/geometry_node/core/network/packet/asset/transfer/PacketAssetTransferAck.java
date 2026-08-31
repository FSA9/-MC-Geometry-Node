package com.mine.geometry_node.core.network.packet.asset.transfer;

import com.mine.geometry_node.core.engine.system.asset.transfer.model.AssetTransferCursor;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.Identifier;

import java.util.UUID;

public record PacketAssetTransferAck(UUID transferId, int nextSequence, long nextOffset)
        implements CustomPacketPayload {
    public static final Type<PacketAssetTransferAck> TYPE =
            new Type<>(Identifier.fromNamespaceAndPath("geometry_node", "asset_transfer_ack"));
    public static final StreamCodec<RegistryFriendlyByteBuf, PacketAssetTransferAck> STREAM_CODEC = StreamCodec.of(
            (buffer, packet) -> AssetTransferPacketCodecs.writeAcknowledgement(buffer, packet.transferId,
                    new AssetTransferCursor(packet.nextSequence, packet.nextOffset)),
            buffer -> new PacketAssetTransferAck(AssetTransferPacketCodecs.readAcknowledgement(buffer)));

    public PacketAssetTransferAck {
        transferId = AssetTransferPacketCodecs.requireTransferId(transferId);
        AssetTransferCursor cursor = new AssetTransferCursor(nextSequence, nextOffset);
        nextSequence = cursor.sequence();
        nextOffset = cursor.offset();
    }

    private PacketAssetTransferAck(AssetTransferPacketCodecs.AcknowledgementFrame frame) {
        this(frame.transferId(), frame.cursor().sequence(), frame.cursor().offset());
    }

    @Override public Type<? extends CustomPacketPayload> type() { return TYPE; }
}

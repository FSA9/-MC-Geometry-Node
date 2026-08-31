package com.mine.geometry_node.core.network.packet.asset.transfer;

import com.mine.geometry_node.core.engine.system.asset.transfer.model.AssetTransferCursor;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.Identifier;

import java.util.UUID;

public record PacketAssetTransferUploadAck(UUID transferId, int nextSequence, long nextOffset)
        implements CustomPacketPayload {
    public static final Type<PacketAssetTransferUploadAck> TYPE =
            new Type<>(Identifier.fromNamespaceAndPath("geometry_node", "asset_transfer_upload_ack"));
    public static final StreamCodec<RegistryFriendlyByteBuf, PacketAssetTransferUploadAck> STREAM_CODEC = StreamCodec.of(
            (buffer, packet) -> AssetTransferPacketCodecs.writeAcknowledgement(buffer, packet.transferId,
                    new AssetTransferCursor(packet.nextSequence, packet.nextOffset)),
            buffer -> new PacketAssetTransferUploadAck(AssetTransferPacketCodecs.readAcknowledgement(buffer)));

    public PacketAssetTransferUploadAck {
        transferId = AssetTransferPacketCodecs.requireTransferId(transferId);
        AssetTransferCursor cursor = new AssetTransferCursor(nextSequence, nextOffset);
        nextSequence = cursor.sequence();
        nextOffset = cursor.offset();
    }

    private PacketAssetTransferUploadAck(AssetTransferPacketCodecs.AcknowledgementFrame frame) {
        this(frame.transferId(), frame.cursor().sequence(), frame.cursor().offset());
    }

    @Override public Type<? extends CustomPacketPayload> type() { return TYPE; }
}

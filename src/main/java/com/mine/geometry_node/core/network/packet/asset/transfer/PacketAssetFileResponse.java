package com.mine.geometry_node.core.network.packet.asset.transfer;

import com.mine.geometry_node.core.engine.system.asset.transfer.AssetTransferLimits;
import com.mine.geometry_node.core.engine.system.asset.transfer.model.AssetTransferErrorCode;
import com.mine.geometry_node.core.engine.system.asset.transfer.model.AssetTransferState;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.Identifier;

import java.util.Objects;
import java.util.UUID;

/** Terminal result for one upload or download; successful downloads carry the complete file content. */
public record PacketAssetFileResponse(
        UUID transferId,
        AssetTransferState state,
        AssetTransferErrorCode errorCode,
        String messageKey,
        String detail,
        boolean contentCommitted,
        long sourceSize,
        long sourceLastModified,
        byte[] content
) implements CustomPacketPayload {
    public static final Type<PacketAssetFileResponse> TYPE =
            new Type<>(Identifier.fromNamespaceAndPath("geometry_node", "asset_file_response"));
    public static final StreamCodec<RegistryFriendlyByteBuf, PacketAssetFileResponse> STREAM_CODEC = StreamCodec.of(
            PacketAssetFileResponse::write, PacketAssetFileResponse::read);

    public PacketAssetFileResponse {
        transferId = Objects.requireNonNull(transferId, "transferId");
        state = Objects.requireNonNull(state, "state");
        errorCode = Objects.requireNonNull(errorCode, "errorCode");
        messageKey = AssetTransferPacketCodecs.bounded(
                messageKey, AssetTransferPacketCodecs.MAX_MESSAGE_KEY_LENGTH);
        detail = AssetTransferPacketCodecs.bounded(
                detail, AssetTransferPacketCodecs.MAX_DETAIL_LENGTH);
        content = Objects.requireNonNull(content, "content");
        if (!state.isTerminal()) {
            throw new IllegalArgumentException("Asset file response state must be terminal");
        }
        if (content.length > AssetTransferLimits.MAX_FILE_BYTES) {
            throw new IllegalArgumentException("Asset file exceeds the 64 MiB protocol limit");
        }
        if (sourceSize < 0L || sourceLastModified < 0L) {
            throw new IllegalArgumentException("Invalid asset source revision");
        }
        if (contentCommitted && state != AssetTransferState.COMPLETED) {
            throw new IllegalArgumentException("Invalid committed asset revision");
        }
    }

    private static void write(RegistryFriendlyByteBuf buffer, PacketAssetFileResponse packet) {
        buffer.writeUUID(packet.transferId);
        buffer.writeVarInt(packet.state.ordinal());
        buffer.writeVarInt(packet.errorCode.ordinal());
        buffer.writeUtf(packet.messageKey, AssetTransferPacketCodecs.MAX_MESSAGE_KEY_LENGTH);
        buffer.writeUtf(packet.detail, AssetTransferPacketCodecs.MAX_DETAIL_LENGTH);
        buffer.writeBoolean(packet.contentCommitted);
        buffer.writeLong(packet.sourceSize);
        buffer.writeLong(packet.sourceLastModified);
        buffer.writeByteArray(packet.content);
    }

    private static PacketAssetFileResponse read(RegistryFriendlyByteBuf buffer) {
        return new PacketAssetFileResponse(
                buffer.readUUID(),
                AssetTransferPacketCodecs.readEnum(buffer, AssetTransferState.values()),
                AssetTransferPacketCodecs.readEnum(buffer, AssetTransferErrorCode.values()),
                buffer.readUtf(AssetTransferPacketCodecs.MAX_MESSAGE_KEY_LENGTH),
                buffer.readUtf(AssetTransferPacketCodecs.MAX_DETAIL_LENGTH),
                buffer.readBoolean(),
                buffer.readLong(),
                buffer.readLong(),
                buffer.readByteArray(AssetTransferLimits.MAX_FILE_BYTES));
    }

    @Override
    public Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }
}

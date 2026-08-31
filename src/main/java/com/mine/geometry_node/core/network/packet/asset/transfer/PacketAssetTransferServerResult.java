package com.mine.geometry_node.core.network.packet.asset.transfer;

import com.mine.geometry_node.core.engine.system.asset.transfer.model.AssetTransferErrorCode;
import com.mine.geometry_node.core.engine.system.asset.transfer.model.AssetTransferState;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.Identifier;

import java.util.Objects;
import java.util.UUID;

public record PacketAssetTransferServerResult(
        UUID transferId,
        AssetTransferState state,
        AssetTransferErrorCode errorCode,
        String messageKey,
        String detail,
        boolean contentCommitted,
        long sourceSize,
        long sourceLastModified
) implements CustomPacketPayload {
    public static final Type<PacketAssetTransferServerResult> TYPE =
            new Type<>(Identifier.fromNamespaceAndPath("geometry_node", "asset_transfer_server_result"));
    public static final StreamCodec<RegistryFriendlyByteBuf, PacketAssetTransferServerResult> STREAM_CODEC =
            StreamCodec.of((buffer, packet) -> packet.write(buffer), PacketAssetTransferServerResult::new);

    public PacketAssetTransferServerResult {
        transferId = Objects.requireNonNull(transferId, "transferId");
        state = Objects.requireNonNull(state, "state");
        errorCode = Objects.requireNonNull(errorCode, "errorCode");
        messageKey = Objects.requireNonNullElse(messageKey, "");
        detail = Objects.requireNonNullElse(detail, "");
        if (!state.isTerminal()) throw new IllegalArgumentException("Transfer result state must be terminal");
        if (contentCommitted && (state != AssetTransferState.COMPLETED || sourceSize < 0L || sourceLastModified < 0L)) {
            throw new IllegalArgumentException("Invalid committed asset revision");
        }
        if (!contentCommitted) {
            sourceSize = 0L;
            sourceLastModified = 0L;
        }
    }

    private PacketAssetTransferServerResult(RegistryFriendlyByteBuf buffer) {
        this(buffer.readUUID(), AssetTransferPacketCodecs.readEnum(buffer, AssetTransferState.values()),
                AssetTransferPacketCodecs.readEnum(buffer, AssetTransferErrorCode.values()),
                buffer.readUtf(AssetTransferPacketCodecs.MAX_MESSAGE_KEY_LENGTH),
                buffer.readUtf(AssetTransferPacketCodecs.MAX_DETAIL_LENGTH), buffer.readBoolean(),
                buffer.readLong(), buffer.readLong());
    }

    private void write(RegistryFriendlyByteBuf buffer) {
        buffer.writeUUID(transferId);
        buffer.writeVarInt(state.ordinal());
        buffer.writeVarInt(errorCode.ordinal());
        buffer.writeUtf(messageKey, AssetTransferPacketCodecs.MAX_MESSAGE_KEY_LENGTH);
        buffer.writeUtf(detail, AssetTransferPacketCodecs.MAX_DETAIL_LENGTH);
        buffer.writeBoolean(contentCommitted);
        buffer.writeLong(sourceSize);
        buffer.writeLong(sourceLastModified);
    }

    @Override public Type<? extends CustomPacketPayload> type() { return TYPE; }
}

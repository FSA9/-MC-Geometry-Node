package com.mine.geometry_node.core.network.packet.asset.transfer;

import com.mine.geometry_node.core.engine.system.asset.transfer.model.AssetTransferErrorCode;
import com.mine.geometry_node.core.engine.system.asset.transfer.model.AssetTransferState;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.Identifier;

import java.util.Objects;
import java.util.UUID;

public record PacketAssetTransferResult(
        UUID transferId,
        AssetTransferState state,
        AssetTransferErrorCode errorCode,
        String messageKey,
        String detail
) implements CustomPacketPayload {
    public static final Type<PacketAssetTransferResult> TYPE =
            new Type<>(Identifier.fromNamespaceAndPath("geometry_node", "asset_transfer_result"));
    public static final StreamCodec<RegistryFriendlyByteBuf, PacketAssetTransferResult> STREAM_CODEC = StreamCodec.of(
            (buffer, packet) -> packet.write(buffer), PacketAssetTransferResult::new);

    public PacketAssetTransferResult {
        transferId = Objects.requireNonNull(transferId, "transferId");
        state = Objects.requireNonNull(state, "state");
        errorCode = Objects.requireNonNull(errorCode, "errorCode");
        messageKey = Objects.requireNonNullElse(messageKey, "");
        detail = Objects.requireNonNullElse(detail, "");
        if (!state.isTerminal()) throw new IllegalArgumentException("Transfer result state must be terminal");
    }

    private PacketAssetTransferResult(RegistryFriendlyByteBuf buffer) {
        this(buffer.readUUID(), AssetTransferPacketCodecs.readEnum(buffer, AssetTransferState.values()),
                AssetTransferPacketCodecs.readEnum(buffer, AssetTransferErrorCode.values()),
                buffer.readUtf(AssetTransferPacketCodecs.MAX_MESSAGE_KEY_LENGTH),
                buffer.readUtf(AssetTransferPacketCodecs.MAX_DETAIL_LENGTH));
    }

    private void write(RegistryFriendlyByteBuf buffer) {
        buffer.writeUUID(transferId);
        buffer.writeVarInt(state.ordinal());
        buffer.writeVarInt(errorCode.ordinal());
        buffer.writeUtf(messageKey, AssetTransferPacketCodecs.MAX_MESSAGE_KEY_LENGTH);
        buffer.writeUtf(detail, AssetTransferPacketCodecs.MAX_DETAIL_LENGTH);
    }

    @Override public Type<? extends CustomPacketPayload> type() { return TYPE; }
}

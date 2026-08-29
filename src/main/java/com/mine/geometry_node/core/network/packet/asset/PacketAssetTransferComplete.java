package com.mine.geometry_node.core.network.packet.asset;

import com.mine.geometry_node.core.engine.system.asset.preview.AssetPreviewFormat;
import com.mine.geometry_node.core.engine.system.asset.preview.AssetPreviewLimits;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.Identifier;

import java.util.Arrays;
import java.util.Objects;
import java.util.UUID;

public record PacketAssetTransferComplete(UUID transferId, AssetPreviewFormat previewFormat,
                                          int previewWidth, int previewHeight, byte[] previewContent)
        implements CustomPacketPayload {
    public static final Type<PacketAssetTransferComplete> TYPE =
            new Type<>(Identifier.fromNamespaceAndPath("geometry_node", "asset_transfer_complete"));
    public static final StreamCodec<RegistryFriendlyByteBuf, PacketAssetTransferComplete> STREAM_CODEC = StreamCodec.of(
            (buffer, packet) -> packet.write(buffer), PacketAssetTransferComplete::new);

    public PacketAssetTransferComplete {
        transferId = Objects.requireNonNull(transferId, "transferId");
        previewContent = previewContent == null ? new byte[0] : Arrays.copyOf(previewContent, previewContent.length);
        if (previewContent.length == 0) {
            previewFormat = null;
            previewWidth = 0;
            previewHeight = 0;
        } else if (previewFormat == null || !AssetPreviewLimits.validDimensions(previewWidth, previewHeight)
                || !AssetPreviewLimits.validEncodedSize(previewContent.length)) {
            throw new IllegalArgumentException("Invalid asset nativepreview attachment");
        }
    }

    public PacketAssetTransferComplete(UUID transferId) {
        this(transferId, null, 0, 0, new byte[0]);
    }

    private PacketAssetTransferComplete(RegistryFriendlyByteBuf buffer) {
        this(buffer.readUUID(), readFormat(buffer), buffer.readVarInt(), buffer.readVarInt(),
                buffer.readByteArray(AssetPreviewLimits.MAX_ENCODED_BYTES));
    }

    private void write(RegistryFriendlyByteBuf buffer) {
        buffer.writeUUID(transferId);
        buffer.writeVarInt(previewFormat == null ? 0 : previewFormat.ordinal() + 1);
        buffer.writeVarInt(previewWidth);
        buffer.writeVarInt(previewHeight);
        buffer.writeByteArray(previewContent);
    }

    private static AssetPreviewFormat readFormat(RegistryFriendlyByteBuf buffer) {
        int encoded = buffer.readVarInt();
        if (encoded == 0) return null;
        int ordinal = encoded - 1;
        if (ordinal < 0 || ordinal >= AssetPreviewFormat.values().length) {
            throw new IllegalArgumentException("Invalid nativepreview format");
        }
        return AssetPreviewFormat.values()[ordinal];
    }

    public boolean hasPreview() {
        return previewContent.length > 0;
    }

    @Override
    public byte[] previewContent() {
        return Arrays.copyOf(previewContent, previewContent.length);
    }

    @Override public Type<? extends CustomPacketPayload> type() { return TYPE; }
}

package com.mine.geometry_node.core.network.packet.asset;

import com.mine.geometry_node.core.engine.system.asset.transfer.config.AssetTransferProtocolLimits;

import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.Identifier;

import java.util.Objects;
import java.util.UUID;

public record PacketAssetTransferAccepted(UUID transferId, long totalBytes, String sha256, int acceptedChunkBytes)
        implements CustomPacketPayload {
    public static final Type<PacketAssetTransferAccepted> TYPE =
            new Type<>(Identifier.fromNamespaceAndPath("geometry_node", "asset_transfer_accepted"));
    public static final StreamCodec<RegistryFriendlyByteBuf, PacketAssetTransferAccepted> STREAM_CODEC = StreamCodec.of(
            (buffer, packet) -> packet.write(buffer), PacketAssetTransferAccepted::new);

    public PacketAssetTransferAccepted {
        transferId = Objects.requireNonNull(transferId, "transferId");
        sha256 = AssetTransferPacketCodecs.normalizeHash(sha256);
        if (totalBytes < 0L || totalBytes > AssetTransferProtocolLimits.MAX_FILE_BYTES || sha256.isEmpty()) {
            throw new IllegalArgumentException("Invalid accepted transfer metadata");
        }
        if (acceptedChunkBytes < AssetTransferProtocolLimits.MIN_CHUNK_BYTES
                || acceptedChunkBytes > AssetTransferProtocolLimits.MAX_CHUNK_BYTES) {
            throw new IllegalArgumentException("Invalid accepted chunk size: " + acceptedChunkBytes);
        }
    }

    private PacketAssetTransferAccepted(RegistryFriendlyByteBuf buffer) {
        this(buffer.readUUID(), buffer.readLong(), buffer.readUtf(AssetTransferPacketCodecs.SHA256_HEX_LENGTH),
                buffer.readVarInt());
    }

    private void write(RegistryFriendlyByteBuf buffer) {
        buffer.writeUUID(transferId);
        buffer.writeLong(totalBytes);
        buffer.writeUtf(sha256, AssetTransferPacketCodecs.SHA256_HEX_LENGTH);
        buffer.writeVarInt(acceptedChunkBytes);
    }

    @Override public Type<? extends CustomPacketPayload> type() { return TYPE; }
}

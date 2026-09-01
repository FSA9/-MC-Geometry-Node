package com.mine.geometry_node.core.network.packet.asset.transfer;

import com.mine.geometry_node.core.network.packet.asset.AssetPacketLimits;
import com.mine.geometry_node.core.engine.system.asset.transfer.config.AssetTransferProtocolLimits;
import com.mine.geometry_node.core.engine.system.asset.transfer.model.AssetTransferConflictPolicy;
import com.mine.geometry_node.core.engine.system.asset.transfer.model.AssetTransferDirection;
import com.mine.geometry_node.core.engine.system.asset.transfer.model.AssetTransferPurpose;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.Identifier;

import java.util.Objects;
import java.util.UUID;

public record PacketAssetTransferOpen(
        UUID transferId,
        AssetTransferDirection direction,
        String relativePath,
        long totalBytes,
        String sha256,
        int requestedChunkBytes,
        AssetTransferConflictPolicy conflictPolicy,
        AssetTransferPurpose purpose
) implements CustomPacketPayload {
    public static final Type<PacketAssetTransferOpen> TYPE =
            new Type<>(Identifier.fromNamespaceAndPath("geometry_node", "asset_transfer_open"));
    public static final StreamCodec<RegistryFriendlyByteBuf, PacketAssetTransferOpen> STREAM_CODEC = StreamCodec.of(
            (buffer, packet) -> packet.write(buffer), PacketAssetTransferOpen::new);

    public PacketAssetTransferOpen {
        transferId = Objects.requireNonNull(transferId, "transferId");
        direction = Objects.requireNonNull(direction, "direction");
        relativePath = Objects.requireNonNullElse(relativePath, "");
        sha256 = AssetTransferPacketCodecs.normalizeHash(sha256);
        conflictPolicy = Objects.requireNonNull(conflictPolicy, "conflictPolicy");
        purpose = Objects.requireNonNull(purpose, "purpose");
        if (totalBytes < 0L || totalBytes > AssetTransferProtocolLimits.MAX_FILE_BYTES) {
            throw new IllegalArgumentException("Invalid declared transfer size: " + totalBytes);
        }
        if (requestedChunkBytes < AssetTransferProtocolLimits.MIN_CHUNK_BYTES
                || requestedChunkBytes > AssetTransferProtocolLimits.MAX_CHUNK_BYTES) {
            throw new IllegalArgumentException("Invalid requested chunk size: " + requestedChunkBytes);
        }
        if (direction == AssetTransferDirection.UPLOAD && sha256.isEmpty()) {
            throw new IllegalArgumentException("Upload transfer requires SHA-256");
        }
    }

    public PacketAssetTransferOpen(UUID transferId, AssetTransferDirection direction, String relativePath,
                                   long totalBytes, String sha256, int requestedChunkBytes,
                                   AssetTransferConflictPolicy conflictPolicy) {
        this(transferId, direction, relativePath, totalBytes, sha256, requestedChunkBytes,
                conflictPolicy, AssetTransferPurpose.ASSET_REPOSITORY);
    }


    private PacketAssetTransferOpen(RegistryFriendlyByteBuf buffer) {
        this(buffer.readUUID(),
                AssetTransferPacketCodecs.readEnum(buffer, AssetTransferDirection.values()),
                buffer.readUtf(AssetPacketLimits.MAX_PATH_LENGTH), buffer.readLong(),
                buffer.readUtf(AssetTransferPacketCodecs.SHA256_HEX_LENGTH), buffer.readVarInt(),
                AssetTransferPacketCodecs.readEnum(buffer, AssetTransferConflictPolicy.values()),
                AssetTransferPacketCodecs.readEnum(buffer, AssetTransferPurpose.values()));
    }

    private void write(RegistryFriendlyByteBuf buffer) {
        buffer.writeUUID(transferId);
        buffer.writeVarInt(direction.ordinal());
        buffer.writeUtf(relativePath, AssetPacketLimits.MAX_PATH_LENGTH);
        buffer.writeLong(totalBytes);
        buffer.writeUtf(sha256, AssetTransferPacketCodecs.SHA256_HEX_LENGTH);
        buffer.writeVarInt(requestedChunkBytes);
        buffer.writeVarInt(conflictPolicy.ordinal());
        buffer.writeVarInt(purpose.ordinal());
    }

    @Override
    public Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }
}

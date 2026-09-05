package com.mine.geometry_node.core.network.packet.asset.transfer;

import com.mine.geometry_node.core.engine.system.asset.transfer.AssetTransferLimits;
import com.mine.geometry_node.core.engine.system.asset.transfer.model.AssetTransferConflictPolicy;
import com.mine.geometry_node.core.engine.system.asset.transfer.model.AssetTransferPurpose;
import com.mine.geometry_node.core.network.packet.asset.AssetPacketLimits;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.Identifier;

import java.util.Objects;
import java.util.UUID;

/** One complete file upload. NeoForge handles any necessary network-level packet splitting. */
public record PacketAssetFileUpload(
        UUID transferId,
        String remotePath,
        AssetTransferConflictPolicy conflictPolicy,
        AssetTransferPurpose purpose,
        byte[] content
) implements CustomPacketPayload {
    public static final Type<PacketAssetFileUpload> TYPE =
            new Type<>(Identifier.fromNamespaceAndPath("geometry_node", "asset_file_upload"));
    public static final StreamCodec<RegistryFriendlyByteBuf, PacketAssetFileUpload> STREAM_CODEC = StreamCodec.of(
            PacketAssetFileUpload::write, PacketAssetFileUpload::read);

    public PacketAssetFileUpload {
        transferId = Objects.requireNonNull(transferId, "transferId");
        remotePath = AssetTransferPacketCodecs.requireBounded(
                remotePath, AssetPacketLimits.MAX_PATH_LENGTH, "Asset path");
        conflictPolicy = Objects.requireNonNull(conflictPolicy, "conflictPolicy");
        purpose = Objects.requireNonNull(purpose, "purpose");
        content = Objects.requireNonNull(content, "content");
        if (content.length > AssetTransferLimits.MAX_FILE_BYTES) {
            throw new IllegalArgumentException("Asset file exceeds the 64 MiB protocol limit");
        }
    }

    private static void write(RegistryFriendlyByteBuf buffer, PacketAssetFileUpload packet) {
        buffer.writeUUID(packet.transferId);
        buffer.writeUtf(packet.remotePath, AssetPacketLimits.MAX_PATH_LENGTH);
        buffer.writeVarInt(packet.conflictPolicy.ordinal());
        buffer.writeVarInt(packet.purpose.ordinal());
        buffer.writeByteArray(packet.content);
    }

    private static PacketAssetFileUpload read(RegistryFriendlyByteBuf buffer) {
        return new PacketAssetFileUpload(
                buffer.readUUID(),
                buffer.readUtf(AssetPacketLimits.MAX_PATH_LENGTH),
                AssetTransferPacketCodecs.readEnum(buffer, AssetTransferConflictPolicy.values()),
                AssetTransferPacketCodecs.readEnum(buffer, AssetTransferPurpose.values()),
                buffer.readByteArray(AssetTransferLimits.MAX_FILE_BYTES));
    }

    @Override
    public Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }
}

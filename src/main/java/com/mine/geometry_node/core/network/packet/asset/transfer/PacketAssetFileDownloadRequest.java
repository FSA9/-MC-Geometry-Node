package com.mine.geometry_node.core.network.packet.asset.transfer;

import com.mine.geometry_node.core.engine.system.asset.transfer.model.AssetTransferPurpose;
import com.mine.geometry_node.core.network.packet.asset.AssetPacketLimits;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.Identifier;

import java.util.Objects;
import java.util.UUID;

public record PacketAssetFileDownloadRequest(
        UUID transferId,
        String remotePath,
        AssetTransferPurpose purpose
) implements CustomPacketPayload {
    public static final Type<PacketAssetFileDownloadRequest> TYPE =
            new Type<>(Identifier.fromNamespaceAndPath("geometry_node", "asset_file_download_request"));
    public static final StreamCodec<RegistryFriendlyByteBuf, PacketAssetFileDownloadRequest> STREAM_CODEC =
            StreamCodec.of(PacketAssetFileDownloadRequest::write, PacketAssetFileDownloadRequest::read);

    public PacketAssetFileDownloadRequest {
        transferId = Objects.requireNonNull(transferId, "transferId");
        remotePath = AssetTransferPacketCodecs.requireBounded(
                remotePath, AssetPacketLimits.MAX_PATH_LENGTH, "Asset path");
        purpose = Objects.requireNonNull(purpose, "purpose");
    }

    private static void write(RegistryFriendlyByteBuf buffer, PacketAssetFileDownloadRequest packet) {
        buffer.writeUUID(packet.transferId);
        buffer.writeUtf(packet.remotePath, AssetPacketLimits.MAX_PATH_LENGTH);
        buffer.writeVarInt(packet.purpose.ordinal());
    }

    private static PacketAssetFileDownloadRequest read(RegistryFriendlyByteBuf buffer) {
        return new PacketAssetFileDownloadRequest(
                buffer.readUUID(),
                buffer.readUtf(AssetPacketLimits.MAX_PATH_LENGTH),
                AssetTransferPacketCodecs.readEnum(buffer, AssetTransferPurpose.values()));
    }

    @Override
    public Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }
}

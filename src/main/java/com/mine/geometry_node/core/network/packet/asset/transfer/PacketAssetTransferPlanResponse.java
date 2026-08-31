package com.mine.geometry_node.core.network.packet.asset.transfer;

import com.mine.geometry_node.core.engine.system.asset.RemoteAssetConflict;
import com.mine.geometry_node.core.engine.system.asset.RemoteAssetEntry;
import com.mine.geometry_node.core.network.packet.asset.AssetPacketCodecs;
import com.mine.geometry_node.core.network.packet.asset.AssetPacketLimits;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.Identifier;

import java.util.ArrayList;
import java.util.List;

public record PacketAssetTransferPlanResponse(
        int requestId,
        AssetTransferPlanKind kind,
        boolean success,
        String message,
        List<RemoteAssetEntry> files,
        List<RemoteAssetConflict> conflicts
) implements CustomPacketPayload {
    public static final Type<PacketAssetTransferPlanResponse> TYPE =
            new Type<>(Identifier.fromNamespaceAndPath("geometry_node", "asset_transfer_plan_response"));
    public static final StreamCodec<RegistryFriendlyByteBuf, PacketAssetTransferPlanResponse> STREAM_CODEC = StreamCodec.of(
            (buffer, packet) -> packet.write(buffer), PacketAssetTransferPlanResponse::new);

    public PacketAssetTransferPlanResponse {
        kind = java.util.Objects.requireNonNull(kind, "kind");
        message = java.util.Objects.requireNonNullElse(message, "");
        files = files == null ? List.of() : List.copyOf(files);
        conflicts = conflicts == null ? List.of() : List.copyOf(conflicts);
        AssetPacketLimits.requireCount(files.size(), AssetPacketLimits.MAX_TRANSFER_MANIFEST_ENTRIES,
                "transfer manifest entry");
        AssetPacketLimits.requireCount(conflicts.size(), AssetPacketLimits.MAX_TRANSFER_CONFLICTS,
                "transfer conflict");
    }

    private PacketAssetTransferPlanResponse(RegistryFriendlyByteBuf buffer) {
        this(buffer.readInt(), AssetTransferPacketCodecs.readEnum(buffer, AssetTransferPlanKind.values()),
                buffer.readBoolean(), buffer.readUtf(AssetTransferPacketCodecs.MAX_DETAIL_LENGTH),
                readFiles(buffer), readConflicts(buffer));
    }

    private void write(RegistryFriendlyByteBuf buffer) {
        buffer.writeInt(requestId);
        buffer.writeVarInt(kind.ordinal());
        buffer.writeBoolean(success);
        buffer.writeUtf(message, AssetTransferPacketCodecs.MAX_DETAIL_LENGTH);
        AssetPacketCodecs.writeRemoteAssetEntries(
                buffer, files, AssetPacketLimits.MAX_TRANSFER_MANIFEST_ENTRIES);
        AssetPacketCodecs.writeBoundedCount(
                buffer, conflicts.size(), AssetPacketLimits.MAX_TRANSFER_CONFLICTS, "transfer conflict");
        for (RemoteAssetConflict conflict : conflicts) {
            buffer.writeUtf(conflict.sourcePath(), AssetPacketLimits.MAX_PATH_LENGTH);
            buffer.writeUtf(conflict.targetPath(), AssetPacketLimits.MAX_PATH_LENGTH);
            buffer.writeBoolean(conflict.directory());
        }
    }

    private static List<RemoteAssetEntry> readFiles(RegistryFriendlyByteBuf buffer) {
        return AssetPacketCodecs.readRemoteAssetEntries(
                buffer, AssetPacketLimits.MAX_TRANSFER_MANIFEST_ENTRIES);
    }

    private static List<RemoteAssetConflict> readConflicts(RegistryFriendlyByteBuf buffer) {
        int size = AssetPacketCodecs.readBoundedCount(
                buffer, AssetPacketLimits.MAX_TRANSFER_CONFLICTS, "transfer conflict");
        List<RemoteAssetConflict> conflicts = new ArrayList<>(size);
        for (int i = 0; i < size; i++) conflicts.add(new RemoteAssetConflict(
                buffer.readUtf(AssetPacketLimits.MAX_PATH_LENGTH),
                buffer.readUtf(AssetPacketLimits.MAX_PATH_LENGTH), buffer.readBoolean()));
        return conflicts;
    }

    @Override public Type<? extends CustomPacketPayload> type() { return TYPE; }
}

package com.mine.geometry_node.core.network.packet.asset;

import com.mine.geometry_node.core.engine.graph.storage.RemoteGraphConflict;
import com.mine.geometry_node.core.engine.graph.storage.RemoteGraphEntry;
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
        List<RemoteGraphEntry> files,
        List<RemoteGraphConflict> conflicts
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
        buffer.writeVarInt(files.size());
        for (RemoteGraphEntry file : files) {
            buffer.writeUtf(file.path(), AssetTransferPacketCodecs.MAX_PATH_LENGTH);
            buffer.writeUtf(file.name(), AssetTransferPacketCodecs.MAX_PATH_LENGTH);
            buffer.writeBoolean(file.directory());
            buffer.writeLong(file.size());
            buffer.writeUtf(file.graphTypeId(), AssetTransferPacketCodecs.MAX_PATH_LENGTH);
        }
        buffer.writeVarInt(conflicts.size());
        for (RemoteGraphConflict conflict : conflicts) {
            buffer.writeUtf(conflict.sourcePath(), AssetTransferPacketCodecs.MAX_PATH_LENGTH);
            buffer.writeUtf(conflict.targetPath(), AssetTransferPacketCodecs.MAX_PATH_LENGTH);
            buffer.writeBoolean(conflict.directory());
        }
    }

    private static List<RemoteGraphEntry> readFiles(RegistryFriendlyByteBuf buffer) {
        int size = buffer.readVarInt();
        if (size < 0 || size > 65_536) throw new IllegalArgumentException("Invalid transfer manifest size");
        List<RemoteGraphEntry> files = new ArrayList<>(size);
        for (int i = 0; i < size; i++) files.add(new RemoteGraphEntry(
                buffer.readUtf(AssetTransferPacketCodecs.MAX_PATH_LENGTH),
                buffer.readUtf(AssetTransferPacketCodecs.MAX_PATH_LENGTH), buffer.readBoolean(), buffer.readLong(),
                buffer.readUtf(AssetTransferPacketCodecs.MAX_PATH_LENGTH)));
        return files;
    }

    private static List<RemoteGraphConflict> readConflicts(RegistryFriendlyByteBuf buffer) {
        int size = buffer.readVarInt();
        if (size < 0 || size > 16_384) throw new IllegalArgumentException("Invalid transfer conflict count");
        List<RemoteGraphConflict> conflicts = new ArrayList<>(size);
        for (int i = 0; i < size; i++) conflicts.add(new RemoteGraphConflict(
                buffer.readUtf(AssetTransferPacketCodecs.MAX_PATH_LENGTH),
                buffer.readUtf(AssetTransferPacketCodecs.MAX_PATH_LENGTH), buffer.readBoolean()));
        return conflicts;
    }

    @Override public Type<? extends CustomPacketPayload> type() { return TYPE; }
}

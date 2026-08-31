package com.mine.geometry_node.core.network.packet.asset.transfer;

import com.mine.geometry_node.core.network.packet.asset.AssetPacketCodecs;
import com.mine.geometry_node.core.network.packet.asset.AssetPacketLimits;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.Identifier;

import java.util.ArrayList;
import java.util.List;

public record PacketAssetTransferPlanRequest(int requestId, AssetTransferPlanKind kind, List<String> paths)
        implements CustomPacketPayload {
    public static final Type<PacketAssetTransferPlanRequest> TYPE =
            new Type<>(Identifier.fromNamespaceAndPath("geometry_node", "asset_transfer_plan_request"));
    public static final StreamCodec<RegistryFriendlyByteBuf, PacketAssetTransferPlanRequest> STREAM_CODEC = StreamCodec.of(
            (buffer, packet) -> {
                buffer.writeInt(packet.requestId);
                buffer.writeVarInt(packet.kind.ordinal());
                AssetPacketCodecs.writeBoundedCount(buffer, packet.paths.size(),
                        AssetPacketLimits.MAX_TRANSFER_PLAN_PATHS, "transfer plan path");
                for (String path : packet.paths) buffer.writeUtf(path, AssetPacketLimits.MAX_PATH_LENGTH);
            }, buffer -> {
                int requestId = buffer.readInt();
                AssetTransferPlanKind kind = AssetTransferPacketCodecs.readEnum(buffer, AssetTransferPlanKind.values());
                int size = AssetPacketCodecs.readBoundedCount(buffer,
                        AssetPacketLimits.MAX_TRANSFER_PLAN_PATHS, "transfer plan path");
                List<String> paths = new ArrayList<>(size);
                for (int i = 0; i < size; i++) paths.add(buffer.readUtf(AssetPacketLimits.MAX_PATH_LENGTH));
                return new PacketAssetTransferPlanRequest(requestId, kind, paths);
            });

    public PacketAssetTransferPlanRequest {
        if (requestId < 0) throw new IllegalArgumentException("Negative transfer plan request ID");
        kind = java.util.Objects.requireNonNull(kind, "kind");
        paths = paths == null ? List.of() : List.copyOf(paths);
        AssetPacketLimits.requireCount(paths.size(), AssetPacketLimits.MAX_TRANSFER_PLAN_PATHS,
                "transfer plan path");
    }

    @Override public Type<? extends CustomPacketPayload> type() { return TYPE; }
}

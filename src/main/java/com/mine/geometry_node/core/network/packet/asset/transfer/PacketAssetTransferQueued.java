package com.mine.geometry_node.core.network.packet.asset.transfer;

import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.Identifier;

import java.util.Objects;
import java.util.UUID;

/** Confirms that the server owns this request, but has not assigned an active transfer slot yet. */
public record PacketAssetTransferQueued(UUID transferId) implements CustomPacketPayload {
    public static final Type<PacketAssetTransferQueued> TYPE =
            new Type<>(Identifier.fromNamespaceAndPath("geometry_node", "asset_transfer_queued"));
    public static final StreamCodec<RegistryFriendlyByteBuf, PacketAssetTransferQueued> STREAM_CODEC = StreamCodec.of(
            (buffer, packet) -> buffer.writeUUID(packet.transferId),
            buffer -> new PacketAssetTransferQueued(buffer.readUUID()));

    public PacketAssetTransferQueued {
        transferId = Objects.requireNonNull(transferId, "transferId");
    }

    @Override public Type<? extends CustomPacketPayload> type() { return TYPE; }
}

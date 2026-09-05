package com.mine.geometry_node.core.network.packet.asset.preview;

import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.Identifier;

import java.util.Objects;
import java.util.UUID;

/** Releases server-side work when a preview is no longer observed by the client. */
public record PacketAssetPreviewCancel(UUID requestId) implements CustomPacketPayload {
    public static final Type<PacketAssetPreviewCancel> TYPE =
            new Type<>(Identifier.fromNamespaceAndPath("geometry_node", "asset_preview_cancel"));
    public static final StreamCodec<RegistryFriendlyByteBuf, PacketAssetPreviewCancel> STREAM_CODEC = StreamCodec.of(
            (buffer, packet) -> buffer.writeUUID(packet.requestId),
            buffer -> new PacketAssetPreviewCancel(buffer.readUUID()));

    public PacketAssetPreviewCancel {
        requestId = Objects.requireNonNull(requestId, "requestId");
    }

    @Override
    public Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }
}

package com.mine.geometry_node.core.network.packet.asset.transfer;

import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.Identifier;

import java.util.Objects;
import java.util.UUID;

/** Best-effort cancellation for one active asset transfer. */
public record PacketAssetFileCancel(UUID transferId) implements CustomPacketPayload {
    public static final Type<PacketAssetFileCancel> TYPE =
            new Type<>(Identifier.fromNamespaceAndPath("geometry_node", "asset_file_cancel"));
    public static final StreamCodec<RegistryFriendlyByteBuf, PacketAssetFileCancel> STREAM_CODEC = StreamCodec.of(
            PacketAssetFileCancel::write, PacketAssetFileCancel::read);

    public PacketAssetFileCancel {
        transferId = Objects.requireNonNull(transferId, "transferId");
    }

    private static void write(RegistryFriendlyByteBuf buffer, PacketAssetFileCancel packet) {
        buffer.writeUUID(packet.transferId);
    }

    private static PacketAssetFileCancel read(RegistryFriendlyByteBuf buffer) {
        return new PacketAssetFileCancel(buffer.readUUID());
    }

    @Override
    public Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }
}

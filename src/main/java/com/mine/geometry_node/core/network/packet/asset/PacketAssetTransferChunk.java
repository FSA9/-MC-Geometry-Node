package com.mine.geometry_node.core.network.packet.asset;

import com.mine.geometry_node.core.engine.system.asset.transfer.config.AssetTransferProtocolLimits;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.Identifier;

import java.util.Arrays;
import java.util.Objects;
import java.util.UUID;

public record PacketAssetTransferChunk(UUID transferId, int sequence, long offset, byte[] content)
        implements CustomPacketPayload {
    public static final Type<PacketAssetTransferChunk> TYPE =
            new Type<>(Identifier.fromNamespaceAndPath("geometry_node", "asset_transfer_chunk"));
    public static final StreamCodec<RegistryFriendlyByteBuf, PacketAssetTransferChunk> STREAM_CODEC = StreamCodec.of(
            (buffer, packet) -> packet.write(buffer), PacketAssetTransferChunk::new);

    public PacketAssetTransferChunk {
        transferId = Objects.requireNonNull(transferId, "transferId");
        if (sequence < 0 || offset < 0L) throw new IllegalArgumentException("Negative chunk position");
        content = content == null ? new byte[0] : Arrays.copyOf(content, content.length);
        if (content.length == 0 || content.length > AssetTransferProtocolLimits.MAX_CHUNK_BYTES) {
            throw new IllegalArgumentException("Invalid asset transfer chunk length: " + content.length);
        }
    }

    private PacketAssetTransferChunk(RegistryFriendlyByteBuf buffer) {
        this(buffer.readUUID(), buffer.readVarInt(), buffer.readLong(), AssetTransferPacketCodecs.readChunk(buffer));
    }

    private void write(RegistryFriendlyByteBuf buffer) {
        buffer.writeUUID(transferId);
        buffer.writeVarInt(sequence);
        buffer.writeLong(offset);
        buffer.writeByteArray(content);
    }

    @Override public byte[] content() { return Arrays.copyOf(content, content.length); }
    @Override public Type<? extends CustomPacketPayload> type() { return TYPE; }
}

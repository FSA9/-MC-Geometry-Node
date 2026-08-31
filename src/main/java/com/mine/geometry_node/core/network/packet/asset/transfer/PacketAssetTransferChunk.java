package com.mine.geometry_node.core.network.packet.asset.transfer;

import com.mine.geometry_node.core.engine.system.asset.transfer.model.AssetTransferChunkData;
import com.mine.geometry_node.core.engine.system.asset.transfer.model.AssetTransferCursor;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.Identifier;

import java.util.Arrays;
import java.util.UUID;

public record PacketAssetTransferChunk(UUID transferId, int sequence, long offset, byte[] content)
        implements CustomPacketPayload {
    public static final Type<PacketAssetTransferChunk> TYPE =
            new Type<>(Identifier.fromNamespaceAndPath("geometry_node", "asset_transfer_chunk"));
    public static final StreamCodec<RegistryFriendlyByteBuf, PacketAssetTransferChunk> STREAM_CODEC = StreamCodec.of(
            (buffer, packet) -> packet.write(buffer), PacketAssetTransferChunk::new);

    public PacketAssetTransferChunk {
        transferId = AssetTransferPacketCodecs.requireTransferId(transferId);
        AssetTransferChunkData chunk = new AssetTransferChunkData(new AssetTransferCursor(sequence, offset), content);
        content = chunk.content();
    }

    private PacketAssetTransferChunk(RegistryFriendlyByteBuf buffer) {
        this(AssetTransferPacketCodecs.readChunk(buffer));
    }

    private PacketAssetTransferChunk(AssetTransferPacketCodecs.ChunkFrame frame) {
        this(frame.transferId(), frame.chunk().cursor().sequence(), frame.chunk().cursor().offset(), frame.chunk().content());
    }

    private void write(RegistryFriendlyByteBuf buffer) {
        AssetTransferPacketCodecs.writeChunk(buffer, transferId,
                new AssetTransferChunkData(new AssetTransferCursor(sequence, offset), content));
    }

    @Override public byte[] content() { return Arrays.copyOf(content, content.length); }
    @Override public Type<? extends CustomPacketPayload> type() { return TYPE; }
}

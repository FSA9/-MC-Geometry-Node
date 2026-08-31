package com.mine.geometry_node.core.network.packet.asset.transfer;

import com.mine.geometry_node.core.engine.system.asset.transfer.config.AssetTransferProtocolLimits;
import com.mine.geometry_node.core.engine.system.asset.transfer.model.AssetContentHash;
import com.mine.geometry_node.core.engine.system.asset.transfer.model.AssetTransferChunkData;
import com.mine.geometry_node.core.engine.system.asset.transfer.model.AssetTransferCursor;
import io.netty.handler.codec.DecoderException;
import net.minecraft.network.RegistryFriendlyByteBuf;

import java.util.UUID;
import java.util.Objects;

final class AssetTransferPacketCodecs {
    static final int SHA256_HEX_LENGTH = AssetContentHash.HEX_LENGTH;
    static final int MAX_MESSAGE_KEY_LENGTH = 256;
    static final int MAX_DETAIL_LENGTH = 2_048;

    private AssetTransferPacketCodecs() {
    }

    static <E extends Enum<E>> E readEnum(RegistryFriendlyByteBuf buffer, E[] values) {
        int ordinal = buffer.readVarInt();
        if (ordinal < 0 || ordinal >= values.length) {
            throw new DecoderException("Invalid asset transfer enum ordinal: " + ordinal);
        }
        return values[ordinal];
    }

    static void writeChunk(RegistryFriendlyByteBuf buffer, UUID transferId, AssetTransferChunkData chunk) {
        buffer.writeUUID(transferId);
        writeCursor(buffer, chunk.cursor());
        buffer.writeByteArray(chunk.content());
    }

    static UUID requireTransferId(UUID transferId) {
        return Objects.requireNonNull(transferId, "transferId");
    }

    static ChunkFrame readChunk(RegistryFriendlyByteBuf buffer) {
        UUID transferId = buffer.readUUID();
        AssetTransferCursor cursor = readCursor(buffer);
        byte[] content = buffer.readByteArray(AssetTransferProtocolLimits.MAX_CHUNK_BYTES);
        return new ChunkFrame(transferId, new AssetTransferChunkData(cursor, content));
    }

    static void writeAcknowledgement(RegistryFriendlyByteBuf buffer, UUID transferId,
                                     AssetTransferCursor cursor) {
        buffer.writeUUID(transferId);
        writeCursor(buffer, cursor);
    }

    static AcknowledgementFrame readAcknowledgement(RegistryFriendlyByteBuf buffer) {
        return new AcknowledgementFrame(buffer.readUUID(), readCursor(buffer));
    }

    private static void writeCursor(RegistryFriendlyByteBuf buffer, AssetTransferCursor cursor) {
        buffer.writeVarInt(cursor.sequence());
        buffer.writeLong(cursor.offset());
    }

    private static AssetTransferCursor readCursor(RegistryFriendlyByteBuf buffer) {
        return new AssetTransferCursor(buffer.readVarInt(), buffer.readLong());
    }

    static String normalizeHash(String hash) {
        return AssetContentHash.normalizeOptional(hash);
    }

    record ChunkFrame(UUID transferId, AssetTransferChunkData chunk) { }
    record AcknowledgementFrame(UUID transferId, AssetTransferCursor cursor) { }
}

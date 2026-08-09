package com.mine.geometry_node.core.network.packet.asset;

import com.mine.geometry_node.core.engine.system.asset.transfer.config.AssetTransferProtocolLimits;
import io.netty.handler.codec.DecoderException;
import net.minecraft.network.RegistryFriendlyByteBuf;

final class AssetTransferPacketCodecs {
    static final int MAX_PATH_LENGTH = 32_767;
    static final int SHA256_HEX_LENGTH = 64;
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

    static byte[] readChunk(RegistryFriendlyByteBuf buffer) {
        return buffer.readByteArray(AssetTransferProtocolLimits.MAX_CHUNK_BYTES);
    }

    static String normalizeHash(String hash) {
        String normalized = hash == null ? "" : hash.trim().toLowerCase(java.util.Locale.ROOT);
        if (!normalized.isEmpty() && (!normalized.matches("[0-9a-f]{64}"))) {
            throw new IllegalArgumentException("Invalid SHA-256 value");
        }
        return normalized;
    }
}

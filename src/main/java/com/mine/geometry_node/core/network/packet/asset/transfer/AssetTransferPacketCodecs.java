package com.mine.geometry_node.core.network.packet.asset.transfer;

import io.netty.handler.codec.DecoderException;
import net.minecraft.network.RegistryFriendlyByteBuf;

final class AssetTransferPacketCodecs {
    static final int MAX_MESSAGE_KEY_LENGTH = 256;
    static final int MAX_DETAIL_LENGTH = 2_048;

    private AssetTransferPacketCodecs() {
    }

    static String bounded(String value, int maximumLength) {
        String normalized = value == null ? "" : value;
        return normalized.length() <= maximumLength
                ? normalized
                : normalized.substring(0, maximumLength);
    }

    static String requireBounded(String value, int maximumLength, String label) {
        String normalized = value == null ? "" : value;
        if (normalized.length() > maximumLength) {
            throw new IllegalArgumentException(label + " exceeds protocol limit");
        }
        return normalized;
    }

    static <E extends Enum<E>> E readEnum(RegistryFriendlyByteBuf buffer, E[] values) {
        int ordinal = buffer.readVarInt();
        if (ordinal < 0 || ordinal >= values.length) {
            throw new DecoderException("Invalid asset transfer enum ordinal: " + ordinal);
        }
        return values[ordinal];
    }

}

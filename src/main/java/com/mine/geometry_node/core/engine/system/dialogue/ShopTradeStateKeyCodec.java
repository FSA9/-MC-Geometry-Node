package com.mine.geometry_node.core.engine.system.dialogue;

import java.nio.charset.StandardCharsets;
import java.util.Base64;

/** Lossless, versioned string boundary for {@link ShopTradeStateKey}. */
public final class ShopTradeStateKeyCodec {
    private static final String PREFIX = "geometry_node.shop_trade_uses.v1";
    private static final String EPOCH_KIND = "epoch";
    private static final String USES_KIND = "uses";
    private static final Base64.Encoder ENCODER = Base64.getUrlEncoder().withoutPadding();
    private static final Base64.Decoder DECODER = Base64.getUrlDecoder();

    private ShopTradeStateKeyCodec() {
    }

    public static String encode(ShopTradeStateKey key) {
        if (key instanceof ShopTradeStateKey.Epoch epoch) {
            return PREFIX + "." + EPOCH_KIND
                    + "." + encodePart(epoch.graphId())
                    + "." + encodePart(epoch.shopId());
        }
        if (key instanceof ShopTradeStateKey.Uses uses) {
            return PREFIX + "." + USES_KIND
                    + "." + encodePart(uses.graphId())
                    + "." + encodePart(uses.shopId())
                    + "." + encodePart(uses.offerId());
        }
        throw new IllegalArgumentException("Unsupported shop trade state key: " + key);
    }

    public static ShopTradeStateKey decode(String encoded) {
        if (encoded == null) {
            throw new IllegalArgumentException("Shop trade state key cannot be null");
        }
        String[] parts = encoded.split("\\.", -1);
        if (parts.length == 6 && PREFIX.equals(joinPrefix(parts)) && EPOCH_KIND.equals(parts[3])) {
            return new ShopTradeStateKey.Epoch(decodePart(parts[4]), decodePart(parts[5]));
        }
        if (parts.length == 7 && PREFIX.equals(joinPrefix(parts)) && USES_KIND.equals(parts[3])) {
            return new ShopTradeStateKey.Uses(
                    decodePart(parts[4]), decodePart(parts[5]), decodePart(parts[6]));
        }
        throw new IllegalArgumentException("Invalid shop trade state key");
    }

    private static String joinPrefix(String[] parts) {
        return parts.length >= 3 ? parts[0] + "." + parts[1] + "." + parts[2] : "";
    }

    private static String encodePart(String value) {
        return ENCODER.encodeToString(value.getBytes(StandardCharsets.UTF_8));
    }

    private static String decodePart(String value) {
        try {
            return new String(DECODER.decode(value), StandardCharsets.UTF_8);
        } catch (IllegalArgumentException exception) {
            throw new IllegalArgumentException("Invalid shop trade state key component", exception);
        }
    }
}

package com.mine.geometry_node.core.engine.system.dialogue;

/** Structured identity for persistent shop trade counters. */
public sealed interface ShopTradeStateKey permits ShopTradeStateKey.Epoch, ShopTradeStateKey.Uses {
    String graphId();

    String shopId();

    record Epoch(String graphId, String shopId) implements ShopTradeStateKey {
        public Epoch {
            graphId = normalizePart(graphId);
            shopId = normalizePart(shopId);
        }
    }

    record Uses(String graphId, String shopId, String offerId) implements ShopTradeStateKey {
        public Uses {
            graphId = normalizePart(graphId);
            shopId = normalizePart(shopId);
            offerId = normalizePart(offerId);
        }
    }

    private static String normalizePart(String value) {
        if (value == null || value.isBlank()) {
            throw new IllegalArgumentException("Shop trade state key component cannot be blank");
        }
        return value;
    }
}

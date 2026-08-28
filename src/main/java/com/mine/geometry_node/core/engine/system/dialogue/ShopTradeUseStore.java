package com.mine.geometry_node.core.engine.system.dialogue;

import com.mine.geometry_node.core.engine.graph.runtime.GraphRuntimeContext;
import com.mine.geometry_node.core.engine.service.GraphEngineServices;
import com.mine.geometry_node.core.engine.graph.scoped.ScopedStateTarget;
import com.mine.geometry_node.core.engine.graph.scoped.ScopedStateNamespace;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.Entity;
import org.jetbrains.annotations.Nullable;

import java.util.Map;

public final class ShopTradeUseStore {
    public static final String SHOP_ID = "shop_id";
    private static final String LEGACY_SHOP_NODE_ID = "shop_node_id";
    private static final String KEY_PREFIX = "geometry_node.shop_trade_uses";

    private ShopTradeUseStore() {
    }

    public static String defaultShopId(@Nullable String nodeStableId, int fallbackNodeId) {
        if (nodeStableId != null && !nodeStableId.isBlank()) {
            return nodeStableId;
        }
        return fallbackNodeId >= 0 ? String.valueOf(fallbackNodeId) : "unknown";
    }

    public static void attachShopId(Map<String, Object> shopData, String shopId) {
        if (shopData != null && shopId != null && !shopId.isBlank()) {
            shopData.put(SHOP_ID, shopId);
        }
    }

    public static String shopId(Map<String, Object> shopData, String fallback) {
        Object value = shopData != null ? shopData.get(SHOP_ID) : null;
        if (value instanceof String string && !string.isBlank()) {
            return string;
        }
        Object legacyValue = shopData != null ? shopData.get(LEGACY_SHOP_NODE_ID) : null;
        if (legacyValue instanceof String string && !string.isBlank()) {
            return string;
        }
        return fallback == null || fallback.isBlank() ? "unknown" : fallback;
    }

    public static int getUses(ServerLevel level, @Nullable Entity owner, String graphId, String shopId, String offerId) {
        int epoch = getEpoch(level, owner, graphId, shopId);
        Object value = getAttribute(level, owner, usesKey(graphId, shopId, offerId));
        return Math.max(0, usesValue(value, epoch));
    }

    public static int incrementUses(ServerLevel level,
                                    @Nullable Entity owner,
                                    String graphId,
                                    String shopId,
                                    String offerId,
                                    int maxUses) {
        return adjustUses(level, owner, graphId, shopId, offerId, 1, maxUses);
    }

    public static int adjustUses(ServerLevel level,
                                 @Nullable Entity owner,
                                 String graphId,
                                 String shopId,
                                 String offerId,
                                 int delta,
                                 int maxUses) {
        int epoch = getEpoch(level, owner, graphId, shopId);
        int current = getUses(level, owner, graphId, shopId, offerId);
        int next = clampUses((long) current + delta, maxUses);
        setAttribute(level, owner, usesKey(graphId, shopId, offerId), epoch + ":" + next);
        return next;
    }

    private static int getEpoch(ServerLevel level, @Nullable Entity owner, String graphId, String shopId) {
        Object value = getAttribute(level, owner, epochKey(graphId, shopId));
        return Math.max(0, intValue(value, 0));
    }

    private static String usesKey(String graphId, String shopId, String offerId) {
        return baseKey(graphId, shopId)
                + ":offer:" + sanitizeKeyPart(offerId);
    }

    private static String epochKey(String graphId, String shopId) {
        return baseKey(graphId, shopId) + ":epoch";
    }

    private static String baseKey(String graphId, String shopId) {
        return KEY_PREFIX + ":" + sanitizeKeyPart(graphId) + ":" + sanitizeKeyPart(shopId);
    }

    private static Object getAttribute(ServerLevel level, @Nullable Entity owner, String key) {
        return GraphEngineServices.INSTANCE.scopedState().get(
                new GraphRuntimeContext(level, owner),
                ScopedStateNamespace.SHOP,
                ScopedStateTarget.shared(),
                key
        );
    }

    private static void setAttribute(ServerLevel level, @Nullable Entity owner, String key, Object value) {
        GraphEngineServices.INSTANCE.scopedState().set(
                new GraphRuntimeContext(level, owner),
                ScopedStateNamespace.SHOP,
                ScopedStateTarget.shared(),
                key,
                value
        );
    }

    private static String sanitizeKeyPart(String value) {
        if (value == null || value.isBlank()) {
            return "unknown";
        }
        return value.replace(':', '_');
    }

    private static int intValue(Object value, int fallback) {
        if (value instanceof Number number) {
            return number.intValue();
        }
        if (value instanceof String string) {
            try {
                return Integer.parseInt(string.trim());
            } catch (NumberFormatException ignored) {
            }
        }
        return fallback;
    }

    private static int clampUses(long value, int maxUses) {
        int safeMax = Math.max(0, maxUses);
        if (value <= 0) {
            return 0;
        }
        if (value >= safeMax) {
            return safeMax;
        }
        return (int) value;
    }

    private static int usesValue(Object value, int currentEpoch) {
        if (value instanceof String string) {
            int separator = string.indexOf(':');
            if (separator > 0) {
                int storedEpoch = intValue(string.substring(0, separator), -1);
                if (storedEpoch != currentEpoch) {
                    return 0;
                }
                return intValue(string.substring(separator + 1), 0);
            }
        }
        return currentEpoch == 0 ? intValue(value, 0) : 0;
    }
}

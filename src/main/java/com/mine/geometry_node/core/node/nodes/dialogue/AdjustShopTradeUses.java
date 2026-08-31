package com.mine.geometry_node.core.node.nodes.dialogue;

import com.mine.geometry_node.core.engine.blueprint.runtime.ExecutionContext;
import com.mine.geometry_node.core.engine.blueprint.runtime.ExecutionResult;
import com.mine.geometry_node.core.engine.system.dialogue.ShopTradeUseStore;
import com.mine.geometry_node.core.node.nodes.BaseNode;
import com.mine.geometry_node.core.node.definition.node.NodeDef;
import com.mine.geometry_node.core.node.definition.node.NodeType;
import com.mine.geometry_node.core.node.definition.port.PortRow;
import com.mine.geometry_node.core.node.definition.port.StandardPorts;
import com.mine.geometry_node.core.node.definition.port.UIHint;
import net.minecraft.network.chat.Component;

import java.util.Map;

public class AdjustShopTradeUses extends BaseNode {
    public static final String TYPE_ID = "adjust_shop_trade_uses";
    private static final String OFFERS = "offers";
    private static final String OFFER_ID = "id";
    private static final String MAX_USES = "max_uses";

    @Override
    public NodeDef getDefaultDefinition() {
        return NodeDef.builder(TYPE_ID, NodeType.DIALOGUE, Component.translatable("geometry_node.node.adjust_shop_trade_uses"))
                .addRow(new PortRow(StandardPorts.FLOW_IN.toExec(), StandardPorts.FLOW_OUT.toExec(), UIHint.DEFAULT, null, null))
                .addRow(new PortRow(StandardPorts.SHOP_ID.toInput(""), null, UIHint.INPUT, null, null))
                .addRow(new PortRow(StandardPorts.OFFER_ID.toInput(""), null, UIHint.INPUT, null, null))
                .addRow(new PortRow(StandardPorts.INT.toInput(1), null, UIHint.INPUT, null, null))
                .addRow(new PortRow(StandardPorts.SHOP_DATA.toInput(), null, UIHint.DEFAULT, null, null))
                .build();
    }

    @Override
    public ExecutionResult execute(ExecutionContext context) {
        Map<String, Object> shopData = getInputDict(context, StandardPorts.SHOP_DATA.getId());
        String shopId = getInput(context, StandardPorts.SHOP_ID.getId(), String.class);
        if (shopId == null || shopId.isBlank()) {
            shopId = ShopTradeUseStore.shopId(shopData, "");
        }
        if (shopId == null || shopId.isBlank() || "unknown".equals(shopId)) {
            return next(StandardPorts.FLOW_OUT.getId());
        }
        shopId = shopId.trim();

        Integer inputDelta = getInput(context, StandardPorts.INT.getId(), Integer.class);
        int delta = inputDelta == null ? 0 : inputDelta;
        if (delta == 0) {
            return next(StandardPorts.FLOW_OUT.getId());
        }

        String offerId = getInput(context, StandardPorts.OFFER_ID.getId(), String.class);
        if (offerId == null || offerId.isBlank()) {
            adjustAllOffers(context, shopId, shopData, delta);
        } else {
            adjustOffer(context, shopId, offerId.trim(), maxUsesForOffer(shopData, offerId.trim()), delta);
        }

        return next(StandardPorts.FLOW_OUT.getId());
    }

    private void adjustAllOffers(ExecutionContext context, String shopId, Map<String, Object> shopData, int delta) {
        Object offersObj = shopData.get(OFFERS);
        if (!(offersObj instanceof Iterable<?> offers)) {
            return;
        }
        for (Object offerObj : offers) {
            if (!(offerObj instanceof Map<?, ?> offer)) {
                continue;
            }
            String offerId = stringValue(offer.get(OFFER_ID), "");
            if (offerId.isBlank()) {
                continue;
            }
            adjustOffer(context, shopId, offerId, intValue(offer.get(MAX_USES), 0), delta);
        }
    }

    private void adjustOffer(ExecutionContext context, String shopId, String offerId, int maxUses, int delta) {
        if (offerId.isBlank() || maxUses <= 0) {
            return;
        }
        ShopTradeUseStore.adjustUses(
                context.getLevel(),
                context.getEntity(),
                context.getGraphId(),
                shopId,
                offerId,
                delta,
                maxUses
        );
    }

    private int maxUsesForOffer(Map<String, Object> shopData, String offerId) {
        Object offersObj = shopData.get(OFFERS);
        if (!(offersObj instanceof Iterable<?> offers)) {
            return 0;
        }
        for (Object offerObj : offers) {
            if (!(offerObj instanceof Map<?, ?> offer)) {
                continue;
            }
            if (offerId.equals(stringValue(offer.get(OFFER_ID), ""))) {
                return intValue(offer.get(MAX_USES), 0);
            }
        }
        return 0;
    }

    private static String stringValue(Object value, String fallback) {
        if (value instanceof String string) {
            return string;
        }
        return value == null ? fallback : String.valueOf(value);
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
}

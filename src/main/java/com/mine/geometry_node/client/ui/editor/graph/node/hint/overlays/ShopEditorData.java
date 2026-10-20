package com.mine.geometry_node.client.ui.editor.graph.node.hint.overlays;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

/** Detached shop drafts and the editor's legacy-compatible data encoding; no UI or game objects. */
final class ShopEditorData {
    private ShopEditorData() {}

    record ShopState(List<OfferState> offers) {
        ShopState { offers = List.copyOf(offers); }
    }

    record OfferState(String id, String title, int maxUses,
                      boolean consumeSellerItems, boolean sellerReceivesPayment,
                      String visibleCondition, String enabledCondition, String disabledReason,
                      List<String> costs, List<String> rewards) {
        OfferState {
            costs = List.copyOf(costs);
            rewards = List.copyOf(rewards);
        }
    }

    /** Keeps uncommitted numeric text separate from the parsed, persisted offer state. */
    record OfferDraft(String id, String title, String maxUses, boolean infiniteUses,
                      boolean consumeSellerItems, boolean sellerReceivesPayment,
                      String visibleCondition, String enabledCondition, String disabledReason,
                      List<String> costs, List<String> rewards) {
        OfferDraft {
            costs = List.copyOf(costs);
            rewards = List.copyOf(rewards);
        }
    }

    static Map<String, Object> encode(List<OfferDraft> drafts, List<String> conditionPorts) {
        Map<String, Object> shopData = new LinkedHashMap<>();
        List<Object> offers = new ArrayList<>();
        Set<String> usedIds = new HashSet<>();
        for (int i = 0; i < drafts.size(); i++) {
            OfferDraft draft = drafts.get(i);
            Map<String, Object> offer = new LinkedHashMap<>();
            offer.put("id", normalizeOfferId(draft.id(), usedIds, i + 1));
            offer.put("title", draft.title().trim());
            offer.put("costs", encodeStacks(draft.costs()));
            offer.put("rewards", encodeStacks(draft.rewards()));
            offer.put("max_uses", draft.infiniteUses() ? 0 : Math.max(1, intValue(draft.maxUses(), 1)));
            offer.put("consume_seller_items", draft.consumeSellerItems());
            offer.put("seller_receives_payment", draft.sellerReceivesPayment());
            offer.put("visible_condition", validConditionId(draft.visibleCondition(), conditionPorts));
            offer.put("enabled_condition", validConditionId(draft.enabledCondition(), conditionPorts));
            offer.put("disabled_reason", draft.disabledReason().trim());
            offers.add(offer);
        }
        shopData.put("offers", offers);
        return shopData;
    }

    private static List<Object> encodeStacks(List<String> stacks) {
        List<Object> result = new ArrayList<>();
        for (String stack : stacks) {
            if (stack == null || stack.isBlank()) continue;
            Map<String, Object> item = new LinkedHashMap<>();
            item.put("stack", stack);
            result.add(item);
        }
        return result;
    }

    static ShopState parseShopState(Object raw) {
        if (!(raw instanceof Map<?, ?> root)) {
            return new ShopState(List.of());
        }
        return new ShopState(parseOffers(root.get("offers")));
    }

    private static List<OfferState> parseOffers(Object offersObj) {
        List<OfferState> result = new ArrayList<>();
        if (!(offersObj instanceof List<?> offers)) {
            return result;
        }
        int index = 1;
        for (Object offerObj : offers) {
            if (!(offerObj instanceof Map<?, ?> offerMap)) {
                continue;
            }
            String id = stringValue(offerMap.get("id"), "trade_" + index);
            String title = stringValue(offerMap.get("title"), "");
            int maxUses = intValue(offerMap.get("max_uses"), 0);
            boolean consumeSellerItems = boolValue(offerMap.get("consume_seller_items"), false);
            boolean sellerReceivesPayment = boolValue(offerMap.get("seller_receives_payment"), false);
            String visibleCondition = stringValue(offerMap.get("visible_condition"), "");
            String enabledCondition = stringValue(offerMap.get("enabled_condition"), "");
            String disabledReason = stringValue(offerMap.get("disabled_reason"), "");
            result.add(new OfferState(
                    id,
                    title,
                    maxUses,
                    consumeSellerItems,
                    sellerReceivesPayment,
                    visibleCondition,
                    enabledCondition,
                    disabledReason,
                    parseStacks(offerMap.get("costs")),
                    parseStacks(offerMap.get("rewards"))
            ));
            index++;
        }
        return result;
    }

    private static List<String> parseStacks(Object raw) {
        List<String> stacks = new ArrayList<>();
        if (!(raw instanceof List<?> list)) {
            return stacks;
        }
        for (Object item : list) {
            String stack = "";
            if (item instanceof Map<?, ?> map) {
                stack = stringValue(map.get("stack"), "");
            } else if (item instanceof String string) {
                stack = string;
            }
            if (!stack.isBlank()) {
                stacks.add(stack);
            }
        }
        return stacks;
    }

    static OfferState defaultOffer() {
        return new OfferState("", "", 0, false, false, "", "", "", List.of(), List.of());
    }


    private static String normalizeOfferId(String raw, Set<String> usedIds, int index) {
        String base = raw == null ? "" : raw.trim();
        if (base.isEmpty()) {
            base = "trade_" + index;
        }
        base = base.replaceAll("\\s+", "_");
        String candidate = base;
        int suffix = 2;
        while (usedIds.contains(candidate)) {
            candidate = base + "_" + suffix++;
        }
        usedIds.add(candidate);
        return candidate;
    }

    private static String stringValue(Object value, String fallback) {
        if (value instanceof String string) {
            return string;
        }
        return fallback;
    }

    static int intValue(Object value, int fallback) {
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

    private static boolean boolValue(Object value, boolean fallback) {
        if (value instanceof Boolean bool) {
            return bool;
        }
        if (value instanceof String string) {
            if ("true".equalsIgnoreCase(string) || "1".equals(string)) {
                return true;
            }
            if ("false".equalsIgnoreCase(string) || "0".equals(string)) {
                return false;
            }
        }
        return fallback;
    }

    static String validConditionId(String conditionId, List<String> conditionPorts) {
        if (conditionId == null || conditionId.isBlank() || conditionPorts == null) {
            return "";
        }
        for (String portId : conditionPorts) {
            if (portId.equals(conditionId)) {
                return conditionId;
            }
        }
        return "";
    }

}

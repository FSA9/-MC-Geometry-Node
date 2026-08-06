package com.mine.geometry_node.core.engine.dialogue.model.shop;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.function.ToIntFunction;

/** Converts authored shop maps into the immutable page model shared by runtime and preview. */
public final class ShopPagePayloadFactory {
    private static final String OFFERS = "offers";
    private static final String MAX_USES = "max_uses";
    private static final String USES = "uses";
    private static final String CONSUME_SELLER_ITEMS = "consume_seller_items";
    private static final String SELLER_RECEIVES_PAYMENT = "seller_receives_payment";
    private static final String VISIBLE_CONDITION = "visible_condition";
    private static final String ENABLED_CONDITION = "enabled_condition";
    private static final String DISABLED_REASON = "disabled_reason";

    private ShopPagePayloadFactory() {
    }

    public static Map<String, Object> normalize(Map<?, ?> raw) {
        if (raw == null || raw.isEmpty()) {
            return new LinkedHashMap<>(Map.of(OFFERS, List.of()));
        }
        Map<String, Object> result = normalizePlainMap(raw);
        result.put(OFFERS, normalizeOffers(result.get(OFFERS)));
        return result;
    }

    public static ShopPagePayload create(Map<?, ?> rawShopData,
                                         Map<String, Boolean> conditionValues,
                                         String shopId,
                                         String title,
                                         ToIntFunction<String> usesResolver) {
        Map<String, Object> shopData = normalize(rawShopData);
        Map<String, Boolean> safeConditions = conditionValues == null ? Map.of() : conditionValues;
        ToIntFunction<String> safeUsesResolver = usesResolver == null ? ignored -> 0 : usesResolver;
        Object offersObj = shopData.get(OFFERS);
        if (!(offersObj instanceof List<?> offers)) {
            return new ShopPagePayload(shopId, title, ShopPagePayload.Feedback.EMPTY, List.of());
        }

        List<ShopPagePayload.Offer> displayOffers = new ArrayList<>();
        for (Object offerObj : offers) {
            if (!(offerObj instanceof Map<?, ?> rawOffer)) {
                continue;
            }
            Map<String, Object> offer = normalizePlainMap(rawOffer);
            String visibleCondition = stringValue(offer.get(VISIBLE_CONDITION), "");
            if (!visibleCondition.isBlank() && !Boolean.TRUE.equals(safeConditions.get(visibleCondition))) {
                continue;
            }

            String enabledCondition = stringValue(offer.get(ENABLED_CONDITION), "");
            boolean enabled = enabledCondition.isBlank() || Boolean.TRUE.equals(safeConditions.get(enabledCondition));
            String offerId = stringValue(offer.get("id"), "");
            if (offerId.isBlank()) {
                continue;
            }
            displayOffers.add(new ShopPagePayload.Offer(
                    offerId,
                    stringValue(offer.get("title"), ""),
                    intValue(offer.get(MAX_USES), 0),
                    Math.max(0, safeUsesResolver.applyAsInt(offerId)),
                    enabled,
                    enabled ? "" : stringValue(offer.get(DISABLED_REASON), ""),
                    boolValue(offer.get(CONSUME_SELLER_ITEMS), false),
                    boolValue(offer.get(SELLER_RECEIVES_PAYMENT), false),
                    itemPayloads(offer.get("costs")),
                    itemPayloads(offer.get("rewards"))
            ));
        }
        return new ShopPagePayload(shopId, title, ShopPagePayload.Feedback.EMPTY, displayOffers);
    }

    private static List<ShopPagePayload.Item> itemPayloads(Object raw) {
        if (!(raw instanceof List<?> list)) {
            return List.of(new ShopPagePayload.Item(""));
        }
        List<ShopPagePayload.Item> result = new ArrayList<>(list.size());
        for (Object item : list) {
            String stackJson = "";
            if (item instanceof Map<?, ?> map && map.get("stack") instanceof String stack) {
                stackJson = stack;
            } else if (item instanceof String stack) {
                stackJson = stack;
            }
            result.add(new ShopPagePayload.Item(stackJson));
        }
        return result;
    }

    private static List<Object> normalizeOffers(Object raw) {
        if (!(raw instanceof List<?> list)) {
            return List.of();
        }
        List<Object> result = new ArrayList<>();
        int index = 1;
        for (Object item : list) {
            if (!(item instanceof Map<?, ?> map)) {
                continue;
            }
            Map<String, Object> offer = normalizePlainMap(map);
            offer.putIfAbsent("id", "trade_" + index);
            offer.putIfAbsent("title", "");
            offer.putIfAbsent("costs", List.of());
            offer.putIfAbsent("rewards", List.of());
            offer.put(MAX_USES, Math.max(0, intValue(offer.get(MAX_USES), 0)));
            offer.put(USES, Math.max(0, intValue(offer.get(USES), 0)));
            offer.put(CONSUME_SELLER_ITEMS, boolValue(offer.get(CONSUME_SELLER_ITEMS), false));
            offer.put(SELLER_RECEIVES_PAYMENT, boolValue(offer.get(SELLER_RECEIVES_PAYMENT), false));
            offer.put(VISIBLE_CONDITION, stringValue(offer.get(VISIBLE_CONDITION), ""));
            offer.put(ENABLED_CONDITION, stringValue(offer.get(ENABLED_CONDITION), ""));
            offer.put(DISABLED_REASON, stringValue(offer.get(DISABLED_REASON), ""));
            result.add(offer);
            index++;
        }
        return result;
    }

    private static Map<String, Object> normalizePlainMap(Map<?, ?> raw) {
        Map<String, Object> result = new LinkedHashMap<>();
        for (Map.Entry<?, ?> entry : raw.entrySet()) {
            if (entry.getKey() != null && entry.getValue() != null) {
                result.put(String.valueOf(entry.getKey()), normalizeValue(entry.getValue()));
            }
        }
        return result;
    }

    private static List<Object> normalizeList(List<?> raw) {
        List<Object> result = new ArrayList<>();
        for (Object value : raw) {
            if (value != null) {
                result.add(normalizeValue(value));
            }
        }
        return result;
    }

    private static Object normalizeValue(Object value) {
        if (value instanceof Map<?, ?> map) {
            return normalizePlainMap(map);
        }
        if (value instanceof List<?> list) {
            return normalizeList(list);
        }
        if (value instanceof String || value instanceof Number || value instanceof Boolean) {
            return value;
        }
        return String.valueOf(value);
    }

    private static String stringValue(Object value, String fallback) {
        return value instanceof String string ? string : fallback;
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

    private static boolean boolValue(Object value, boolean fallback) {
        if (value instanceof Boolean bool) {
            return bool;
        }
        if (value instanceof String string) {
            if ("true".equalsIgnoreCase(string) || "1".equals(string)) return true;
            if ("false".equalsIgnoreCase(string) || "0".equals(string)) return false;
        }
        return fallback;
    }
}

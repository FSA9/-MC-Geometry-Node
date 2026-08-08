package com.mine.geometry_node.core.engine.system.dialogue.model.shop;

import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Immutable shop page snapshot.
 */
public record ShopPagePayload(
        String shopId,
        String title,
        Feedback feedback,
        List<Offer> offers
) {
    public ShopPagePayload {
        shopId = shopId == null ? "" : shopId;
        title = title == null ? "" : title;
        feedback = feedback == null ? Feedback.EMPTY : feedback;
        offers = offers == null ? List.of() : List.copyOf(offers);
    }

    @Nullable
    public Offer findOffer(String offerId) {
        for (Offer offer : offers) {
            if (offer.id().equals(offerId)) {
                return offer;
            }
        }
        return null;
    }

    public ShopPagePayload withFeedback(Feedback value) {
        return new ShopPagePayload(shopId, title, value, offers);
    }

    public ShopPagePayload withOfferUses(String offerId, int uses) {
        List<Offer> updated = new ArrayList<>(offers.size());
        for (Offer offer : offers) {
            updated.add(offer.id().equals(offerId) ? offer.withUses(uses) : offer);
        }
        return new ShopPagePayload(shopId, title, feedback, updated);
    }

    public Map<String, Object> toMap() {
        Map<String, Object> result = new LinkedHashMap<>();
        result.put("shop_id", shopId);
        List<Object> offerMaps = new ArrayList<>(offers.size());
        for (Offer offer : offers) {
            Map<String, Object> map = new LinkedHashMap<>();
            map.put("id", offer.id());
            map.put("title", offer.title());
            map.put("max_uses", offer.maxUses());
            map.put("uses", offer.uses());
            map.put("enabled", offer.enabled());
            map.put("disabled_reason", offer.disabledReason());
            map.put("consume_seller_items", offer.consumeSellerItems());
            map.put("seller_receives_payment", offer.sellerReceivesPayment());
            map.put("costs", itemMaps(offer.costs()));
            map.put("rewards", itemMaps(offer.rewards()));
            offerMaps.add(map);
        }
        result.put("offers", offerMaps);
        return result;
    }

    private static List<Object> itemMaps(List<Item> items) {
        List<Object> result = new ArrayList<>(items.size());
        for (Item item : items) {
            result.add(Map.of("stack", item.stackJson()));
        }
        return result;
    }

    /**
     * Last trade result shown by a shop page.
     */
    public record Feedback(String message, String messageKey, boolean success) {
        public static final Feedback EMPTY = new Feedback("", "", true);

        public Feedback {
            message = message == null ? "" : message;
            messageKey = messageKey == null ? "" : messageKey;
        }

        public static Feedback message(String message, boolean success) {
            return new Feedback(message, "", success);
        }

        public static Feedback key(String messageKey, boolean success) {
            return new Feedback("", messageKey, success);
        }
    }

    /**
     * Serialized item configuration retained for registry-aware decoding at trade time.
     */
    public record Item(String stackJson) {
        public Item {
            stackJson = stackJson == null ? "" : stackJson;
        }
    }

    /**
     * Immutable offer snapshot shared by the server runtime and client view.
     */
    public record Offer(
            String id,
            String title,
            int maxUses,
            int uses,
            boolean enabled,
            String disabledReason,
            boolean consumeSellerItems,
            boolean sellerReceivesPayment,
            List<Item> costs,
            List<Item> rewards
    ) {
        public Offer {
            if (id == null || id.isBlank()) {
                throw new IllegalArgumentException("Shop offer id must not be blank");
            }
            title = title == null ? "" : title;
            maxUses = Math.max(0, maxUses);
            uses = Math.max(0, uses);
            disabledReason = disabledReason == null ? "" : disabledReason;
            costs = costs == null ? List.of() : List.copyOf(costs);
            rewards = rewards == null ? List.of() : List.copyOf(rewards);
        }

        public Offer withUses(int value) {
            return new Offer(id, title, maxUses, value, enabled, disabledReason,
                    consumeSellerItems, sellerReceivesPayment, costs, rewards);
        }
    }
}

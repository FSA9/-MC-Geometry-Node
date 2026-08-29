package com.mine.geometry_node.core.engine.system.dialogue;

import com.mine.geometry_node.core.engine.blueprint.event.GraphEventData;
import com.mine.geometry_node.core.engine.blueprint.BlueprintRuntime;
import com.mine.geometry_node.core.engine.system.dialogue.model.shop.ShopPagePayload;
import com.mine.geometry_node.core.node.nodes.events.dialogue.OnShopTradeSuccess;
import com.mine.geometry_node.core.node.port.StandardPorts;
import com.mine.geometry_node.core.utils.ItemCodecUtils;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.Container;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.neoforged.neoforge.capabilities.Capabilities;
import net.neoforged.neoforge.transfer.ResourceHandler;
import net.neoforged.neoforge.transfer.item.ItemResource;
import net.neoforged.neoforge.transfer.item.ItemUtil;
import net.neoforged.neoforge.transfer.transaction.Transaction;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.List;

final class ShopTradeService {
    private ShopTradeService() {
    }

    static ShopPagePayload trade(ServerPlayer player,
                                 @Nullable Entity seller,
                                 String graphId,
                                 ShopPagePayload shop,
                                 String offerId) {
        ShopPagePayload.Offer offerPayload = shop.findOffer(offerId);
        if (offerPayload == null) {
            return feedbackKey(shop, "geometry_node.shop.message.offer_missing", false);
        }

        String shopId = shop.shopId();
        int globalUses = ShopTradeUseStore.getUses(player.level(), player, graphId, shopId, offerId);
        shop = shop.withOfferUses(offerId, globalUses);
        offerPayload = shop.findOffer(offerId);
        if (offerPayload == null) {
            return feedbackKey(shop, "geometry_node.shop.message.offer_missing", false);
        }

        ShopOffer offer = parseShopOffer(offerPayload, player);
        if (!offer.valid()) {
            return feedbackKey(shop, "geometry_node.shop.message.invalid_offer", false);
        }
        if (!offer.enabled()) {
            String reason = offerPayload.disabledReason();
            return reason.isBlank()
                    ? feedbackKey(shop, "geometry_node.shop.message.condition_not_met", false)
                    : feedbackMessage(shop, reason, false);
        }
        if (offer.costs().isEmpty() && offer.rewards().isEmpty()) {
            return feedbackKey(shop, "geometry_node.shop.message.empty_offer", false);
        }
        if (offer.maxUses() > 0 && offer.uses() >= offer.maxUses()) {
            return feedbackKey(shop, "geometry_node.shop.message.sold_out", false);
        }
        if (!hasStacks(player.getInventory(), offer.costs())) {
            return feedbackKey(shop, "geometry_node.shop.message.player_items_missing", false);
        }

        SellerInventory sellerInventory = seller == null ? null : sellerInventory(seller);
        if ((offer.consumeSellerItems() || offer.sellerReceivesPayment())
                && (seller == null || sellerInventory == null)) {
            return feedbackKey(shop, "geometry_node.shop.message.seller_inventory_unavailable", false);
        }
        if (offer.consumeSellerItems() && !sellerInventory.hasAll(offer.rewards())) {
            return feedbackKey(shop, "geometry_node.shop.message.seller_items_missing", false);
        }

        List<ItemStack> rewards = copyStacks(offer.rewards());
        if (offer.consumeSellerItems()) {
            rewards = sellerInventory.extractAll(offer.rewards());
        }
        if (offer.consumeSellerItems() && !hasStacks(copyStacks(rewards), offer.rewards())) {
            sellerInventory.insertOrDrop(rewards, seller);
            return feedbackKey(shop, "geometry_node.shop.message.seller_extract_failed", false);
        }
        if (!hasStacks(player.getInventory(), offer.costs())) {
            if (offer.consumeSellerItems()) {
                sellerInventory.insertOrDrop(rewards, seller);
            }
            return feedbackKey(shop, "geometry_node.shop.message.player_items_missing", false);
        }

        removeStacks(player.getInventory(), offer.costs());
        if (offer.sellerReceivesPayment()) {
            sellerInventory.insertOrDrop(copyStacks(offer.costs()), seller);
        }
        giveStacks(player, rewards);

        if (offer.maxUses() > 0) {
            int uses = ShopTradeUseStore.incrementUses(player.level(), player, graphId, shopId, offerId, offer.maxUses());
            shop = shop.withOfferUses(offerId, uses);
        }
        dispatchShopTradeSuccess(player, seller, offerId, shop, offer.costs(), rewards);
        return feedbackKey(shop, "geometry_node.shop.message.trade_complete", true);
    }

    private static ShopPagePayload feedbackMessage(ShopPagePayload shop, String message, boolean success) {
        return shop.withFeedback(ShopPagePayload.Feedback.message(message, success));
    }

    private static ShopPagePayload feedbackKey(ShopPagePayload shop, String messageKey, boolean success) {
        return shop.withFeedback(ShopPagePayload.Feedback.key(messageKey, success));
    }

    private static void dispatchShopTradeSuccess(ServerPlayer player,
                                                 @Nullable Entity seller,
                                                 String offerId,
                                                 ShopPagePayload shop,
                                                 List<ItemStack> costs,
                                                 List<ItemStack> rewards) {
        BlueprintRuntime.INSTANCE.dispatchEvent(player.level(), player, OnShopTradeSuccess.TYPE_ID, GraphEventData.of(
                StandardPorts.BUYER.getId(), player,
                StandardPorts.SELLER.getId(), seller,
                StandardPorts.SHOP_ID.getId(), shop.shopId(),
                StandardPorts.OFFER_ID.getId(), offerId,
                StandardPorts.COSTS.getId(), copyStacks(costs),
                StandardPorts.REWARDS.getId(), copyStacks(rewards),
                StandardPorts.SHOP_DATA.getId(), shop.toMap()
        ));
    }

    private static ShopOffer parseShopOffer(ShopPagePayload.Offer offer, ServerPlayer player) {
        StackParseResult costs = parseStacks(offer.costs(), player);
        StackParseResult rewards = parseStacks(offer.rewards(), player);
        return new ShopOffer(
                offer.maxUses(),
                offer.uses(),
                offer.consumeSellerItems(),
                offer.sellerReceivesPayment(),
                offer.enabled(),
                costs.valid() && rewards.valid(),
                costs.stacks(),
                rewards.stacks()
        );
    }

    private static StackParseResult parseStacks(List<ShopPagePayload.Item> items, ServerPlayer player) {
        List<ItemStack> result = new ArrayList<>();
        for (ShopPagePayload.Item item : items) {
            String stackJson = item.stackJson();
            if (stackJson.isBlank()) {
                return StackParseResult.invalid();
            }
            ItemStack stack = ItemCodecUtils.fromJson(stackJson, player.registryAccess());
            if (stack.isEmpty()) {
                return StackParseResult.invalid();
            }
            result.add(stack);
        }
        return StackParseResult.valid(result);
    }

    @Nullable
    private static SellerInventory sellerInventory(Entity seller) {
        ResourceHandler<ItemResource> handler = seller.getCapability(Capabilities.Item.ENTITY);
        if (handler != null) {
            return new ResourceHandlerSellerInventory(handler);
        }
        if (seller instanceof Player player) {
            return new ContainerSellerInventory(player.getInventory());
        }
        if (seller instanceof Container container) {
            return new ContainerSellerInventory(container);
        }
        return null;
    }

    private static boolean hasStacks(Container container, List<ItemStack> requiredStacks) {
        if (requiredStacks.isEmpty()) {
            return true;
        }
        List<ItemStack> available = new ArrayList<>();
        for (int i = 0; i < container.getContainerSize(); i++) {
            ItemStack stack = container.getItem(i);
            if (!stack.isEmpty()) {
                available.add(stack.copy());
            }
        }
        return hasStacks(available, requiredStacks);
    }

    private static boolean hasStacks(List<ItemStack> available, List<ItemStack> requiredStacks) {
        for (ItemStack required : requiredStacks) {
            int remaining = required.getCount();
            for (ItemStack candidate : available) {
                if (remaining <= 0) {
                    break;
                }
                if (!candidate.isEmpty() && ItemStack.isSameItemSameComponents(candidate, required)) {
                    int taken = Math.min(remaining, candidate.getCount());
                    candidate.shrink(taken);
                    remaining -= taken;
                }
            }
            if (remaining > 0) {
                return false;
            }
        }
        return true;
    }

    private static void removeStacks(Inventory inventory, List<ItemStack> requiredStacks) {
        for (ItemStack required : requiredStacks) {
            int remaining = required.getCount();
            for (int i = 0; i < inventory.getContainerSize() && remaining > 0; i++) {
                ItemStack current = inventory.getItem(i);
                if (current.isEmpty() || !ItemStack.isSameItemSameComponents(current, required)) {
                    continue;
                }
                int taken = Math.min(remaining, current.getCount());
                current.shrink(taken);
                if (current.isEmpty()) {
                    inventory.setItem(i, ItemStack.EMPTY);
                }
                remaining -= taken;
            }
        }
        inventory.setChanged();
    }

    private static void giveStacks(ServerPlayer player, List<ItemStack> stacks) {
        for (ItemStack stack : stacks) {
            if (stack.isEmpty()) {
                continue;
            }
            ItemStack copy = stack.copy();
            player.getInventory().add(copy);
            if (!copy.isEmpty()) {
                player.drop(copy, false);
            }
        }
    }

    private static List<ItemStack> copyStacks(List<ItemStack> stacks) {
        List<ItemStack> result = new ArrayList<>();
        for (ItemStack stack : stacks) {
            if (!stack.isEmpty()) {
                result.add(stack.copy());
            }
        }
        return result;
    }

    private static void dropAt(Entity entity, ItemStack stack) {
        if (!stack.isEmpty() && entity.level() instanceof ServerLevel level) {
            entity.spawnAtLocation(level, stack);
        }
    }

    private record ShopOffer(
            int maxUses,
            int uses,
            boolean consumeSellerItems,
            boolean sellerReceivesPayment,
            boolean enabled,
            boolean valid,
            List<ItemStack> costs,
            List<ItemStack> rewards
    ) {
    }

    private record StackParseResult(boolean valid, List<ItemStack> stacks) {
        private static StackParseResult valid(List<ItemStack> stacks) {
            return new StackParseResult(true, List.copyOf(stacks));
        }

        private static StackParseResult invalid() {
            return new StackParseResult(false, List.of());
        }
    }

    private interface SellerInventory {
        boolean hasAll(List<ItemStack> requiredStacks);

        List<ItemStack> extractAll(List<ItemStack> requiredStacks);

        void insertOrDrop(List<ItemStack> stacks, Entity seller);
    }

    private static final class ContainerSellerInventory implements SellerInventory {
        private final Container container;

        private ContainerSellerInventory(Container container) {
            this.container = container;
        }

        @Override
        public boolean hasAll(List<ItemStack> requiredStacks) {
            return hasStacks(container, requiredStacks);
        }

        @Override
        public List<ItemStack> extractAll(List<ItemStack> requiredStacks) {
            List<ItemStack> extracted = new ArrayList<>();
            for (ItemStack required : requiredStacks) {
                int remaining = required.getCount();
                for (int slot = 0; slot < container.getContainerSize() && remaining > 0; slot++) {
                    ItemStack current = container.getItem(slot);
                    if (current.isEmpty() || !ItemStack.isSameItemSameComponents(current, required)) {
                        continue;
                    }
                    int taken = Math.min(remaining, current.getCount());
                    ItemStack stack = container.removeItem(slot, taken);
                    if (!stack.isEmpty()) {
                        extracted.add(stack);
                        remaining -= stack.getCount();
                    }
                }
            }
            container.setChanged();
            return extracted;
        }

        @Override
        public void insertOrDrop(List<ItemStack> stacks, Entity seller) {
            for (ItemStack stack : stacks) {
                ItemStack remaining = insert(stack.copy());
                dropAt(seller, remaining);
            }
            container.setChanged();
        }

        private ItemStack insert(ItemStack stack) {
            if (stack.isEmpty()) {
                return ItemStack.EMPTY;
            }
            for (int slot = 0; slot < container.getContainerSize() && !stack.isEmpty(); slot++) {
                ItemStack current = container.getItem(slot);
                if (current.isEmpty() || !ItemStack.isSameItemSameComponents(current, stack)) {
                    continue;
                }
                int limit = Math.min(container.getMaxStackSize(stack), current.getMaxStackSize());
                int space = limit - current.getCount();
                if (space <= 0) {
                    continue;
                }
                int moved = Math.min(space, stack.getCount());
                current.grow(moved);
                stack.shrink(moved);
            }
            for (int slot = 0; slot < container.getContainerSize() && !stack.isEmpty(); slot++) {
                ItemStack current = container.getItem(slot);
                if (!current.isEmpty() || !container.canPlaceItem(slot, stack)) {
                    continue;
                }
                int moved = Math.min(stack.getCount(), Math.min(container.getMaxStackSize(stack), stack.getMaxStackSize()));
                container.setItem(slot, stack.copyWithCount(moved));
                stack.shrink(moved);
            }
            return stack;
        }
    }

    private static final class ResourceHandlerSellerInventory implements SellerInventory {
        private final ResourceHandler<ItemResource> handler;

        private ResourceHandlerSellerInventory(ResourceHandler<ItemResource> handler) {
            this.handler = handler;
        }

        @Override
        public boolean hasAll(List<ItemStack> requiredStacks) {
            List<ItemStack> available = new ArrayList<>();
            for (int slot = 0; slot < handler.size(); slot++) {
                ItemStack stack = ItemUtil.getStack(handler, slot);
                if (!stack.isEmpty()) {
                    try (Transaction transaction = Transaction.openRoot()) {
                        int extractable = handler.extract(slot, ItemResource.of(stack), stack.getCount(), transaction);
                        if (extractable > 0) {
                            available.add(stack.copyWithCount(extractable));
                        }
                    }
                }
            }
            return hasStacks(available, requiredStacks);
        }

        @Override
        public List<ItemStack> extractAll(List<ItemStack> requiredStacks) {
            List<ItemStack> extracted = new ArrayList<>();
            for (ItemStack required : requiredStacks) {
                int remaining = required.getCount();
                for (int slot = 0; slot < handler.size() && remaining > 0; slot++) {
                    ItemStack current = ItemUtil.getStack(handler, slot);
                    if (current.isEmpty() || !ItemStack.isSameItemSameComponents(current, required)) {
                        continue;
                    }
                    try (Transaction transaction = Transaction.openRoot()) {
                        int taken = handler.extract(slot, ItemResource.of(required), remaining, transaction);
                        if (taken > 0) {
                            transaction.commit();
                            extracted.add(required.copyWithCount(taken));
                            remaining -= taken;
                        }
                    }
                }
            }
            return extracted;
        }

        @Override
        public void insertOrDrop(List<ItemStack> stacks, Entity seller) {
            for (ItemStack stack : stacks) {
                ItemStack remaining = stack.copy();
                for (int slot = 0; slot < handler.size() && !remaining.isEmpty(); slot++) {
                    try (Transaction transaction = Transaction.openRoot()) {
                        int inserted = handler.insert(slot, ItemResource.of(remaining), remaining.getCount(), transaction);
                        if (inserted > 0) {
                            transaction.commit();
                            remaining.shrink(inserted);
                        }
                    }
                }
                dropAt(seller, remaining);
            }
        }
    }
}

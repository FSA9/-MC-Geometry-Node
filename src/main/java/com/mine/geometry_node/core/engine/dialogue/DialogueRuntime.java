package com.mine.geometry_node.core.engine.dialogue;

import com.mine.geometry_node.core.engine.dialogue.context.DialogueContext;
import com.mine.geometry_node.core.engine.dialogue.payload.DialogueChoicePayload;
import com.mine.geometry_node.core.engine.dialogue.payload.DialoguePagePayload;
import com.mine.geometry_node.core.engine.dialogue.payload.DialogueWaitRequest;
import com.mine.geometry_node.core.engine.dialogue.presenter.ChatDialoguePresenter;
import com.mine.geometry_node.core.engine.dialogue.presenter.DialoguePresenter;
import com.mine.geometry_node.core.engine.dialogue.presenter.PacketDialoguePresenter;
import com.mine.geometry_node.core.engine.dialogue.session.DialogueCloseReason;
import com.mine.geometry_node.core.engine.dialogue.session.DialogueSession;
import com.mine.geometry_node.core.engine.dialogue.session.DialogueSessionManager;
import com.mine.geometry_node.core.engine.dialogue.session.DialogueSessionPolicy;
import com.mine.geometry_node.core.engine.blueprint.event.GraphEventData;
import com.mine.geometry_node.core.engine.blueprint.runtime.GraphEngine;
import com.mine.geometry_node.core.engine.graph.GraphKind;
import com.mine.geometry_node.core.engine.graph.runtime.ExternalWaitRequest;
import com.mine.geometry_node.core.engine.graph.runtime.GraphExecutionHandle;
import com.mine.geometry_node.core.engine.graph.runtime.GraphRuntime;
import com.mine.geometry_node.core.node.nodes.events.dialogue.OnShopTradeSuccess;
import com.mine.geometry_node.core.node.port.StandardPorts;
import com.mine.geometry_node.core.utils.ItemCodecUtils;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.Container;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
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
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.UUID;

/**
 * Minimal server-side facade for dialogue runtime state.
 */
public class DialogueRuntime implements GraphRuntime {
    public static final DialogueRuntime INSTANCE = new DialogueRuntime();

    private final DialogueSessionManager sessionManager;
    private final DialoguePresenter chatPresenter;
    private final DialoguePresenter packetPresenter;

    public DialogueRuntime() {
        this(new DialogueSessionManager());
    }

    public DialogueRuntime(DialogueSessionManager sessionManager) {
        this(sessionManager, ChatDialoguePresenter.INSTANCE, PacketDialoguePresenter.INSTANCE);
    }

    public DialogueRuntime(DialogueSessionManager sessionManager,
                           DialoguePresenter chatPresenter,
                           DialoguePresenter packetPresenter) {
        this.sessionManager = sessionManager;
        this.chatPresenter = chatPresenter;
        this.packetPresenter = packetPresenter;
    }

    @Override
    public GraphKind kind() {
        return GraphKind.DIALOGUE;
    }

    @Override
    public String id() {
        return "geometry_node:dialogue";
    }

    @Override
    public void init() {
        DialogueEventHandler.init();
    }

    @Override
    public boolean beginExternalWait(GraphExecutionHandle handle, ExternalWaitRequest request) {
        if (!(request instanceof DialogueWaitRequest dialogueRequest)) {
            return false;
        }
        DialogueContext dialogueContext = dialogueRequest.context();
        ServerPlayer player = dialogueContext.player();
        if (player == null) {
            return false;
        }

        closeForPlayer(player, DialogueCloseReason.REPLACED);

        DialogueSessionPolicy policy = dialogueContext.policy();
        UUID lockEntityId = lockEntityId(dialogueContext);
        if (lockEntityId != null) {
            boolean includeSharedSessions = !policy.allowMultiPlayer();
            if (sessionManager.findEntityOccupant(lockEntityId, player.getUUID(), includeSharedSessions) != null) {
                handle.resume("closed");
                return true;
            }
        }

        DialogueSession session = sessionManager.createSession(player.getUUID(), handleGraphId(handle));
        session.setPages(dialogueRequest.pages());
        session.setExecutionHandle(handle);
        session.setDialogueContext(dialogueContext);
        session.setPolicy(policy);
        long gameTime = player.level().getGameTime();
        session.setCreatedGameTime(gameTime);
        session.touch(gameTime);
        openForPlayer(player, session);
        return true;
    }

    @Override
    public void completeExternalWait(GraphExecutionHandle handle, String outputPortName, GraphRuntime.ExternalWaitCompletion completion) {
        DialogueSession match = findSessionByHandle(handle);
        if (match != null) {
            sessionManager.removeSession(match.getSessionId());
            match.setExecutionHandle(null);
            match.close(completion == GraphRuntime.ExternalWaitCompletion.NO_TARGET
                    ? DialogueCloseReason.CLOSED
                    : DialogueCloseReason.CHOSEN);
        }
    }

    @Override
    public void endExternalWait(GraphExecutionHandle handle, @Nullable String reason) {
        DialogueSession match = findSessionByHandle(handle);
        if (match != null) {
            closeSessionInternal(match, reason == null ? DialogueCloseReason.CLOSED : reason, "closed", true, false);
        }
    }

    @Nullable
    public DialogueSession choose(ServerPlayer player, UUID sessionId, String choiceId) {
        DialogueSession session = sessionManager.getSession(sessionId);
        if (session == null || !session.isActive() || !session.getPlayerId().equals(player.getUUID())
                || session.getCurrentPage() == null) {
            return null;
        }

        for (DialogueChoicePayload choice : session.getCurrentPage().getChoices()) {
            if (choice.getId().equals(choiceId) && choice.isEnabled()) {
                if (DialogueWaitRequest.isContinuePageChoice(choice.getId())) {
                    if (!session.advancePage()) {
                        return null;
                    }
                    session.touch(player.level().getGameTime());
                    openForPlayer(player, session);
                    return session;
                }
                closeSessionInternal(session, DialogueCloseReason.CHOSEN, choice.getId(), true, true);
                return session;
            }
        }
        return null;
    }

    @Nullable
    public DialogueSession chooseCurrent(ServerPlayer player, String choiceId) {
        DialogueSession session = sessionManager.getSessionForPlayer(player.getUUID());
        if (session == null) {
            return null;
        }
        return choose(player, session.getSessionId(), choiceId);
    }

    @Nullable
    public DialogueSession tradeShopOffer(ServerPlayer player, UUID sessionId, String offerId) {
        DialogueSession session = sessionManager.getSession(sessionId);
        if (session == null || !session.isActive() || !session.getPlayerId().equals(player.getUUID())) {
            return null;
        }
        DialoguePagePayload page = session.getCurrentPage();
        if (page == null || !"shop".equals(page.getStyleId())) {
            return null;
        }

        Map<String, Object> shopData = shopData(page);
        Map<String, Object> offerMap = findOfferMap(shopData, offerId);
        if (offerMap == null) {
            refreshShopSessionKey(player, session, "geometry_node.shop.message.offer_missing", false);
            return session;
        }

        String graphId = session.getGraphId();
        String shopId = ShopTradeUseStore.shopId(shopData, "");
        int globalUses = ShopTradeUseStore.getUses(player.level(), player, graphId, shopId, offerId);
        offerMap.put("uses", globalUses);
        ShopOffer offer = parseShopOffer(offerMap, player);
        if (!offer.enabled()) {
            String reason = stringValue(offerMap.get("disabled_reason"), "");
            if (reason.isBlank()) {
                refreshShopSessionKey(player, session, "geometry_node.shop.message.condition_not_met", false);
            } else {
                refreshShopSession(player, session, reason, false);
            }
            return session;
        }
        if (offer.costs().isEmpty() && offer.rewards().isEmpty()) {
            refreshShopSessionKey(player, session, "geometry_node.shop.message.empty_offer", false);
            return session;
        }
        if (offer.maxUses() > 0 && offer.uses() >= offer.maxUses()) {
            refreshShopSessionKey(player, session, "geometry_node.shop.message.sold_out", false);
            return session;
        }
        if (!hasStacks(player.getInventory(), offer.costs())) {
            refreshShopSessionKey(player, session, "geometry_node.shop.message.player_items_missing", false);
            return session;
        }

        Entity seller = resolveSellerEntity(player.level(), session.getDialogueContext());
        SellerInventory sellerInventory = seller == null ? null : sellerInventory(seller);
        if (offer.consumeSellerItems() && seller != null) {
            if (sellerInventory == null) {
                refreshShopSessionKey(player, session, "geometry_node.shop.message.seller_inventory_unavailable", false);
                return session;
            }
            if (!sellerInventory.hasAll(offer.rewards())) {
                refreshShopSessionKey(player, session, "geometry_node.shop.message.seller_items_missing", false);
                return session;
            }
        }

        List<ItemStack> rewards = copyStacks(offer.rewards());
        if (offer.consumeSellerItems() && sellerInventory != null) {
            rewards = sellerInventory.extractAll(offer.rewards());
        }
        if (offer.consumeSellerItems() && sellerInventory != null && !hasStacks(copyStacks(rewards), offer.rewards())) {
            sellerInventory.insertOrDrop(rewards, seller);
            refreshShopSessionKey(player, session, "geometry_node.shop.message.seller_extract_failed", false);
            return session;
        }
        if (!hasStacks(player.getInventory(), offer.costs())) {
            if (offer.consumeSellerItems() && sellerInventory != null) {
                sellerInventory.insertOrDrop(rewards, seller);
            }
            refreshShopSessionKey(player, session, "geometry_node.shop.message.player_items_missing", false);
            return session;
        }

        removeStacks(player.getInventory(), offer.costs());
        if (offer.sellerReceivesPayment() && seller != null) {
            if (sellerInventory != null) {
                sellerInventory.insertOrDrop(copyStacks(offer.costs()), seller);
            } else {
                for (ItemStack cost : copyStacks(offer.costs())) {
                    dropAt(seller, cost);
                }
            }
        }
        giveStacks(player, rewards);

        if (offer.maxUses() > 0) {
            offerMap.put("uses", ShopTradeUseStore.incrementUses(player.level(), player, graphId, shopId, offerId, offer.maxUses()));
        }
        dispatchShopTradeSuccess(player, seller, offerId, shopData, offer.costs(), rewards);
        refreshShopSessionKey(player, session, "geometry_node.shop.message.trade_complete", true);
        return session;
    }

    @Nullable
    public DialogueSession closeFromClient(ServerPlayer player, UUID sessionId) {
        DialogueSession session = sessionManager.getSession(sessionId);
        if (session == null || !session.getPlayerId().equals(player.getUUID())) {
            return null;
        }
        closeSessionInternal(session, DialogueCloseReason.CLIENT, "closed", true, true);
        return session;
    }

    @Nullable
    public DialogueSession closeCurrentFromClient(ServerPlayer player) {
        DialogueSession session = sessionManager.getSessionForPlayer(player.getUUID());
        if (session == null) {
            return null;
        }
        return closeFromClient(player, session.getSessionId());
    }

    @Nullable
    public DialogueSession closeSession(UUID sessionId) {
        return closeSession(sessionId, DialogueCloseReason.CLOSED);
    }

    @Nullable
    public DialogueSession closeSession(UUID sessionId, String reason) {
        DialogueSession session = sessionManager.getSession(sessionId);
        if (session != null) {
            closeSessionInternal(session, reason, "closed", true, true);
        }
        return session;
    }

    @Nullable
    public DialogueSession closeForPlayer(ServerPlayer player, String reason) {
        DialogueSession session = sessionManager.getSessionForPlayer(player.getUUID());
        if (session == null) {
            return null;
        }
        closeSessionInternal(session, reason, "closed", true, true);
        return session;
    }

    public void onServerLevelTick(ServerLevel level) {
        for (DialogueSession session : sessionManager.snapshotSessions()) {
            if (!session.isActive()) {
                continue;
            }
            GraphExecutionHandle handle = session.getExecutionHandle();
            if (handle == null || handle.level() != level) {
                continue;
            }
            String closeReason = evaluateCloseReason(level, session);
            if (closeReason != null) {
                closeSessionInternal(session, closeReason, "closed", true, true);
            }
        }
    }

    public void onPlayerLogout(ServerPlayer player) {
        closeForPlayer(player, DialogueCloseReason.PLAYER_LOGOUT);
    }

    public void onEntityDeath(Entity entity) {
        if (entity instanceof ServerPlayer player) {
            closeForPlayer(player, DialogueCloseReason.PLAYER_DEAD);
        }
        closeSessionsForEntity(entity.getUUID(), DialogueCloseReason.ACTOR_DEAD);
    }

    public void onEntityChangeDimension(Entity entity) {
        if (entity instanceof ServerPlayer player) {
            closeForPlayer(player, DialogueCloseReason.DIMENSION_CHANGED);
        }
        closeSessionsForEntity(entity.getUUID(), DialogueCloseReason.DIMENSION_CHANGED);
    }

    public void onServerStopping() {
        for (DialogueSession session : sessionManager.snapshotSessions()) {
            closeSessionInternal(session, DialogueCloseReason.SERVER_SHUTDOWN, "closed", false, true);
        }
    }

    public DialogueSessionManager getSessionManager() {
        return sessionManager;
    }

    private void openForPlayer(ServerPlayer player, DialogueSession session) {
        DialoguePagePayload page = session.getCurrentPage();
        DialoguePresenter presenter = page != null && "default".equals(page.getStyleId())
                ? chatPresenter
                : packetPresenter;
        session.setPresenterId(presenter.id());
        presenter.open(player, session);
    }

    private DialoguePresenter getPresenter(DialogueSession session) {
        return chatPresenter.id().equals(session.getPresenterId()) ? chatPresenter : packetPresenter;
    }

    @Nullable
    private DialogueSession findSessionByHandle(GraphExecutionHandle handle) {
        for (DialogueSession session : sessionManager.getSessions()) {
            if (session.getExecutionHandle() == handle) {
                return session;
            }
        }
        return null;
    }

    private String handleGraphId(GraphExecutionHandle handle) {
        return handle.graphId();
    }

    private void closeSessionsForEntity(UUID entityId, String reason) {
        for (DialogueSession session : sessionManager.snapshotSessions()) {
            DialogueContext context = session.getDialogueContext();
            if (context == null) {
                continue;
            }
            if (entityId.equals(context.speakerEntityId()) || entityId.equals(context.targetEntityId())) {
                closeSessionInternal(session, reason, "closed", true, true);
            }
        }
    }

    private void closeSessionInternal(DialogueSession session, String reason, String resumePort, boolean notifyClient, boolean resumeHandle) {
        if (session == null) {
            return;
        }
        String closeReason = reason == null || reason.isBlank() ? DialogueCloseReason.CLOSED : reason;
        GraphExecutionHandle handle = session.getExecutionHandle();
        ServerPlayer player = findPlayer(session);
        sessionManager.removeSession(session.getSessionId());
        session.setExecutionHandle(null);
        session.close(closeReason);

        if (notifyClient && player != null) {
            getPresenter(session).close(player, session, closeReason);
        }
        if (resumeHandle && handle != null) {
            handle.resume(resumePort == null || resumePort.isBlank() ? "closed" : resumePort);
        }
    }

    @Nullable
    private String evaluateCloseReason(ServerLevel level, DialogueSession session) {
        ServerPlayer player = level.getServer().getPlayerList().getPlayer(session.getPlayerId());
        if (player == null || player.isRemoved()) {
            return DialogueCloseReason.PLAYER_LOGOUT;
        }
        if (!player.isAlive()) {
            return DialogueCloseReason.PLAYER_DEAD;
        }
        if (player.level() != level) {
            return DialogueCloseReason.DIMENSION_CHANGED;
        }

        DialogueContext context = session.getDialogueContext();
        if (context != null) {
            Entity speakerEntity = context.resolveSpeakerEntity(level);
            Entity targetEntity = context.resolveTargetEntity(level);
            if (context.speakerEntityId() != null && speakerEntity == null) {
                return DialogueCloseReason.ACTOR_REMOVED;
            }
            if (context.targetEntityId() != null && targetEntity == null) {
                return DialogueCloseReason.ACTOR_REMOVED;
            }
            if (isDead(speakerEntity) || isDead(targetEntity)) {
                return DialogueCloseReason.ACTOR_DEAD;
            }
        }

        if (session.getPolicy().hasTimeout()) {
            long lastInteraction = session.getLastInteractionGameTime() >= 0
                    ? session.getLastInteractionGameTime()
                    : session.getCreatedGameTime();
            if (lastInteraction >= 0 && level.getGameTime() - lastInteraction > session.getPolicy().timeoutSeconds() * 20L) {
                return DialogueCloseReason.TIMEOUT;
            }
        }
        return null;
    }

    @Nullable
    private UUID lockEntityId(@Nullable DialogueContext context) {
        if (context == null) {
            return null;
        }
        return context.speakerEntityId() != null ? context.speakerEntityId() : context.targetEntityId();
    }

    private void refreshShopSession(ServerPlayer player, DialogueSession session, String message, boolean success) {
        DialoguePagePayload page = session.getCurrentPage();
        if (page != null) {
            page.getMetadata().put("last_trade_message", message == null ? "" : message);
            page.getMetadata().remove("last_trade_message_key");
            page.getMetadata().put("last_trade_success", success);
        }
        session.touch(player.level().getGameTime());
        getPresenter(session).open(player, session);
    }

    private void refreshShopSessionKey(ServerPlayer player, DialogueSession session, String messageKey, boolean success) {
        DialoguePagePayload page = session.getCurrentPage();
        if (page != null) {
            page.getMetadata().remove("last_trade_message");
            page.getMetadata().put("last_trade_message_key", messageKey == null ? "" : messageKey);
            page.getMetadata().put("last_trade_success", success);
        }
        session.touch(player.level().getGameTime());
        getPresenter(session).open(player, session);
    }

    private void dispatchShopTradeSuccess(ServerPlayer player,
                                          @Nullable Entity seller,
                                          String offerId,
                                          Map<String, Object> shopData,
                                          List<ItemStack> costs,
                                          List<ItemStack> rewards) {
        GraphEngine.dispatchEvent(player.level(), player, OnShopTradeSuccess.TYPE_ID, GraphEventData.of(
                StandardPorts.BUYER.getId(), player,
                StandardPorts.SELLER.getId(), seller,
                StandardPorts.SHOP_ID.getId(), ShopTradeUseStore.shopId(shopData, ""),
                StandardPorts.OFFER_ID.getId(), offerId,
                StandardPorts.COSTS.getId(), copyStacks(costs),
                StandardPorts.REWARDS.getId(), copyStacks(rewards),
                StandardPorts.SHOP_DATA.getId(), copyPlainValue(shopData)
        ));
    }

    private static Object copyPlainValue(Object value) {
        if (value instanceof Map<?, ?> map) {
            Map<String, Object> copy = new LinkedHashMap<>();
            for (Map.Entry<?, ?> entry : map.entrySet()) {
                if (entry.getKey() instanceof String key) {
                    copy.put(key, copyPlainValue(entry.getValue()));
                }
            }
            return copy;
        }
        if (value instanceof List<?> list) {
            List<Object> copy = new ArrayList<>();
            for (Object item : list) {
                copy.add(copyPlainValue(item));
            }
            return copy;
        }
        return value;
    }

    @SuppressWarnings("unchecked")
    private static Map<String, Object> shopData(DialoguePagePayload page) {
        Object value = page.getMetadata().get("shop_data");
        if (value instanceof Map<?, ?> map) {
            return (Map<String, Object>) map;
        }
        Map<String, Object> created = new LinkedHashMap<>();
        created.put("offers", List.of());
        page.getMetadata().put("shop_data", created);
        return created;
    }

    @Nullable
    @SuppressWarnings("unchecked")
    private static Map<String, Object> findOfferMap(Map<String, Object> shopData, String offerId) {
        Object offersObj = shopData.get("offers");
        if (!(offersObj instanceof List<?> offers)) {
            return null;
        }
        for (Object offerObj : offers) {
            if (!(offerObj instanceof Map<?, ?> rawOffer)) {
                continue;
            }
            Object id = rawOffer.get("id");
            if (Objects.equals(String.valueOf(id), offerId)) {
                return (Map<String, Object>) rawOffer;
            }
        }
        return null;
    }

    private static ShopOffer parseShopOffer(Map<String, Object> offerMap, ServerPlayer player) {
        int maxUses = intValue(offerMap.get("max_uses"), 0);
        int uses = Math.max(0, intValue(offerMap.get("uses"), 0));
        boolean consumeSellerItems = boolValue(offerMap.get("consume_seller_items"), false);
        boolean sellerReceivesPayment = boolValue(offerMap.get("seller_receives_payment"), false);
        boolean enabled = boolValue(offerMap.get("enabled"), true);
        return new ShopOffer(
                maxUses,
                uses,
                consumeSellerItems,
                sellerReceivesPayment,
                enabled,
                parseStacks(offerMap.get("costs"), player),
                parseStacks(offerMap.get("rewards"), player)
        );
    }

    private static List<ItemStack> parseStacks(Object raw, ServerPlayer player) {
        if (!(raw instanceof List<?> list)) {
            return List.of();
        }
        List<ItemStack> result = new ArrayList<>();
        for (Object item : list) {
            String stackJson = "";
            if (item instanceof Map<?, ?> map) {
                Object stack = map.get("stack");
                stackJson = stack instanceof String string ? string : "";
            } else if (item instanceof String string) {
                stackJson = string;
            }
            if (stackJson.isBlank()) {
                continue;
            }
            ItemStack stack = ItemCodecUtils.fromJson(stackJson, player.registryAccess());
            if (!stack.isEmpty()) {
                result.add(stack);
            }
        }
        return result;
    }

    @Nullable
    private static Entity resolveSellerEntity(ServerLevel level, @Nullable DialogueContext context) {
        if (context == null) {
            return null;
        }
        Entity speaker = context.resolveSpeakerEntity(level);
        if (speaker != null) {
            return speaker;
        }
        return context.resolveTargetEntity(level);
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
        if (requiredStacks == null || requiredStacks.isEmpty()) {
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
        if (requiredStacks == null || requiredStacks.isEmpty()) {
            return;
        }
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
            if (stack == null || stack.isEmpty()) {
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
            if (stack != null && !stack.isEmpty()) {
                result.add(stack.copy());
            }
        }
        return result;
    }

    private static void dropAt(Entity entity, ItemStack stack) {
        if (entity != null && stack != null && !stack.isEmpty() && entity.level() instanceof ServerLevel level) {
            entity.spawnAtLocation(level, stack);
        }
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
            if ("true".equalsIgnoreCase(string) || "1".equals(string)) {
                return true;
            }
            if ("false".equalsIgnoreCase(string) || "0".equals(string)) {
                return false;
            }
        }
        return fallback;
    }

    private static String stringValue(Object value, String fallback) {
        if (value instanceof String string) {
            return string;
        }
        return fallback;
    }

    private static boolean isDead(@Nullable Entity entity) {
        return entity instanceof LivingEntity livingEntity && !livingEntity.isAlive();
    }

    @Nullable
    private ServerPlayer findPlayer(UUID playerId) {
        for (DialogueSession session : sessionManager.getSessions()) {
            if (session.getPlayerId().equals(playerId)) {
                ServerPlayer player = findPlayer(session);
                if (player != null) {
                    return player;
                }
            }
        }
        return null;
    }

    @Nullable
    private ServerPlayer findPlayer(DialogueSession session) {
        GraphExecutionHandle handle = session.getExecutionHandle();
        if (handle != null && handle.level() != null) {
            ServerPlayer player = handle.level().getServer().getPlayerList().getPlayer(session.getPlayerId());
            if (player != null) {
                return player;
            }
        }
        DialogueContext ownContext = session.getDialogueContext();
        if (ownContext != null && ownContext.player() != null && Objects.equals(ownContext.player().getUUID(), session.getPlayerId())) {
            return ownContext.player();
        }
        UUID playerId = session.getPlayerId();
        for (DialogueSession candidate : sessionManager.getSessions()) {
            DialogueContext context = candidate.getDialogueContext();
            if (context != null && context.player() != null && Objects.equals(context.player().getUUID(), playerId)) {
                return context.player();
            }
        }
        return null;
    }

    private record ShopOffer(
            int maxUses,
            int uses,
            boolean consumeSellerItems,
            boolean sellerReceivesPayment,
            boolean enabled,
            List<ItemStack> costs,
            List<ItemStack> rewards
    ) {
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
            return DialogueRuntime.hasStacks(container, requiredStacks);
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
            return DialogueRuntime.hasStacks(available, requiredStacks);
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

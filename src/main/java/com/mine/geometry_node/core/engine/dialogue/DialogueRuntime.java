package com.mine.geometry_node.core.engine.dialogue;

import com.mine.geometry_node.GeometryNode;
import com.mine.geometry_node.core.engine.dialogue.model.DialogueChoicePayload;
import com.mine.geometry_node.core.engine.dialogue.model.DialoguePagePayload;
import com.mine.geometry_node.core.engine.dialogue.model.shop.ShopPagePayload;
import com.mine.geometry_node.core.engine.dialogue.presenter.ChatDialoguePresenter;
import com.mine.geometry_node.core.engine.dialogue.presenter.DialoguePresenter;
import com.mine.geometry_node.core.engine.dialogue.presenter.PacketDialoguePresenter;
import com.mine.geometry_node.core.engine.graph.GraphKind;
import com.mine.geometry_node.core.engine.graph.runtime.ExternalWaitRequest;
import com.mine.geometry_node.core.engine.graph.runtime.GraphExecutionHandle;
import com.mine.geometry_node.core.engine.graph.runtime.GraphRuntime;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import org.jetbrains.annotations.Nullable;

import java.util.Objects;
import java.util.UUID;

/**
 * Minimal server-side facade for dialogue runtime state.
 */
public class DialogueRuntime implements GraphRuntime {
    public static final DialogueRuntime INSTANCE = new DialogueRuntime();

    private final DialogueSessionStore sessionStore;
    private final DialoguePresenter chatPresenter;
    private final DialoguePresenter packetPresenter;
    private boolean shuttingDown;

    public DialogueRuntime() {
        this(new DialogueSessionStore(), ChatDialoguePresenter.INSTANCE, PacketDialoguePresenter.INSTANCE);
    }

    DialogueRuntime(DialogueSessionStore sessionStore,
                    DialoguePresenter chatPresenter,
                    DialoguePresenter packetPresenter) {
        this.sessionStore = sessionStore;
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
        if (shuttingDown) {
            return false;
        }
        DialogueContext dialogueContext = dialogueRequest.context();
        ServerPlayer player = dialogueContext.player();
        if (player == null) {
            return false;
        }

        DialogueSession.Policy policy = dialogueContext.policy();
        UUID lockEntityId = lockEntityId(dialogueContext);
        if (lockEntityId != null) {
            boolean includeSharedSessions = !policy.allowMultiPlayer();
            if (sessionStore.findEntityOccupant(lockEntityId, player.getUUID(), includeSharedSessions) != null) {
                handle.resume("closed");
                return true;
            }
        }

        DialogueSession replaced = sessionStore.findForPlayer(player.getUUID());
        GraphExecutionHandle replacedHandle = detachSessionInternal(
                replaced,
                DialogueSession.CloseReason.REPLACED,
                true
        );
        DialogueSession session = sessionStore.create(player.getUUID(), handleGraphId(handle));
        session.setPages(dialogueRequest.pages());
        session.setExecutionHandle(handle);
        session.setDialogueContext(dialogueContext);
        session.setPolicy(policy);
        try {
            openForPlayer(player, session);
        } catch (RuntimeException exception) {
            detachSessionInternal(session, DialogueSession.CloseReason.CLOSED, false);
            throw exception;
        } finally {
            if (replacedHandle != null && replacedHandle.isActive()) {
                replacedHandle.resume("closed");
            }
        }
        return true;
    }

    @Override
    public void completeExternalWait(GraphExecutionHandle handle, String outputPortName, GraphRuntime.ExternalWaitCompletion completion) {
        DialogueSession match = findSessionByHandle(handle);
        if (match != null) {
            sessionStore.remove(match.getSessionId());
            match.setExecutionHandle(null);
            match.close(completion == GraphRuntime.ExternalWaitCompletion.NO_TARGET
                    ? DialogueSession.CloseReason.CLOSED
                    : DialogueSession.CloseReason.CHOSEN);
        }
    }

    @Override
    public void endExternalWait(GraphExecutionHandle handle, @Nullable String reason) {
        DialogueSession match = findSessionByHandle(handle);
        if (match != null) {
            closeSessionInternal(match, reason == null ? DialogueSession.CloseReason.CLOSED : reason, "closed", true, false);
        }
    }

    @Nullable
    public DialogueSession choose(ServerPlayer player, UUID sessionId, String choiceId) {
        DialogueSession session = sessionStore.find(sessionId);
        if (session == null || !session.isActive() || !session.getPlayerId().equals(player.getUUID())
                || session.getCurrentPage() == null) {
            return null;
        }
        if (!hasActiveHandle(session)) {
            detachSessionInternal(session, DialogueSession.CloseReason.FORCED, true);
            return null;
        }

        for (DialogueChoicePayload choice : session.getCurrentPage().choices()) {
            if (!choice.id().equals(choiceId) || !choice.enabled()) {
                continue;
            }
            switch (choice.action()) {
                case DialogueChoicePayload.AdvancePage advancePage -> {
                    if (!session.advancePage(advancePage.expectedPageIndex())) {
                        return null;
                    }
                    openForPlayer(player, session);
                    return session;
                }
                case DialogueChoicePayload.ResumePort resumePort -> {
                    closeSessionInternal(session, DialogueSession.CloseReason.CHOSEN, resumePort.outputPortId(), true, true);
                    return session;
                }
            }
        }
        return null;
    }

    @Nullable
    public DialogueSession chooseCurrent(ServerPlayer player, String choiceId) {
        DialogueSession session = sessionStore.findForPlayer(player.getUUID());
        if (session == null) {
            return null;
        }
        return choose(player, session.getSessionId(), choiceId);
    }

    @Nullable
    public DialogueSession tradeShopOffer(ServerPlayer player, UUID sessionId, String offerId) {
        DialogueSession session = sessionStore.find(sessionId);
        if (session == null || !session.isActive() || !session.getPlayerId().equals(player.getUUID())) {
            return null;
        }
        if (!hasActiveHandle(session)) {
            detachSessionInternal(session, DialogueSession.CloseReason.FORCED, true);
            return null;
        }
        DialoguePagePayload page = session.getCurrentPage();
        if (page == null || !(page.content() instanceof DialoguePagePayload.ShopContent shopContent)) {
            return null;
        }

        Entity seller = resolveSellerEntity(player.level(), session.getDialogueContext());
        ShopPagePayload updatedShop = ShopTradeService.trade(
                player,
                seller,
                session.getGraphId(),
                shopContent.shop(),
                offerId
        );
        replaceShopPage(session, updatedShop);
        getPresenter(session).open(player, session);
        return session;
    }

    @Nullable
    public DialogueSession closeFromClient(ServerPlayer player, UUID sessionId) {
        DialogueSession session = sessionStore.find(sessionId);
        if (session == null || !session.getPlayerId().equals(player.getUUID())) {
            return null;
        }
        closeSessionInternal(session, DialogueSession.CloseReason.CLIENT, "closed", true, true);
        return session;
    }

    @Nullable
    public DialogueSession closeCurrentFromClient(ServerPlayer player) {
        DialogueSession session = sessionStore.findForPlayer(player.getUUID());
        if (session == null) {
            return null;
        }
        return closeFromClient(player, session.getSessionId());
    }

    @Nullable
    public DialogueSession closeSession(UUID sessionId) {
        return closeSession(sessionId, DialogueSession.CloseReason.CLOSED);
    }

    @Nullable
    public DialogueSession closeSession(UUID sessionId, String reason) {
        DialogueSession session = sessionStore.find(sessionId);
        if (session != null) {
            closeSessionInternal(session, reason, "closed", true, true);
        }
        return session;
    }

    @Nullable
    public DialogueSession closeForPlayer(ServerPlayer player, String reason) {
        DialogueSession session = sessionStore.findForPlayer(player.getUUID());
        if (session == null) {
            return null;
        }
        closeSessionInternal(session, reason, "closed", true, true);
        return session;
    }

    public void onServerLevelTick(ServerLevel level) {
        for (DialogueSession session : sessionStore.snapshot()) {
            if (!session.isActive()) {
                continue;
            }
            GraphExecutionHandle handle = session.executionHandle();
            if (handle == null || !handle.isActive()) {
                detachSessionInternal(session, DialogueSession.CloseReason.FORCED, true);
                continue;
            }
            if (handle.level() != level) {
                continue;
            }
            String closeReason = evaluateCloseReason(level, session);
            if (closeReason != null) {
                closeSessionInternal(session, closeReason, "closed", true, true);
            }
        }
    }

    public void onPlayerLogout(ServerPlayer player) {
        closeForPlayer(player, DialogueSession.CloseReason.PLAYER_LOGOUT);
    }

    public void onEntityDeath(Entity entity) {
        if (entity instanceof ServerPlayer player) {
            closeForPlayer(player, DialogueSession.CloseReason.PLAYER_DEAD);
        }
        closeSessionsForEntity(entity.getUUID(), DialogueSession.CloseReason.ACTOR_DEAD);
    }

    public void onEntityChangeDimension(Entity entity) {
        if (entity instanceof ServerPlayer player) {
            closeForPlayer(player, DialogueSession.CloseReason.DIMENSION_CHANGED);
        }
        closeSessionsForEntity(entity.getUUID(), DialogueSession.CloseReason.DIMENSION_CHANGED);
    }

    public void onServerStarting() {
        shuttingDown = false;
    }

    public void onServerStopping() {
        shuttingDown = true;
        for (DialogueSession session : sessionStore.snapshot()) {
            GraphExecutionHandle handle = detachSessionInternal(
                    session,
                    DialogueSession.CloseReason.SERVER_SHUTDOWN,
                    false
            );
            if (handle != null) {
                handle.abort(DialogueSession.CloseReason.SERVER_SHUTDOWN);
            }
        }
    }

    private void openForPlayer(ServerPlayer player, DialogueSession session) {
        DialoguePagePayload page = session.getCurrentPage();
        String requestedStyleId = page == null ? DialogueStyleRegistry.DEFAULT : page.styleId();
        DialogueStyleRegistry.Definition style = DialogueStyleRegistry.find(requestedStyleId);
        if (style == null) {
            GeometryNode.LOGGER.warn(
                    "[DialogueRuntime] Unknown dialogue style '{}' on graph '{}', page '{}'; falling back to '{}'.",
                    requestedStyleId,
                    session.getGraphId(),
                    page == null ? "" : page.id(),
                    DialogueStyleRegistry.DEFAULT
            );
            style = DialogueStyleRegistry.defaultDefinition();
        }
        DialoguePresenter presenter = style.presentation() == DialogueStyleRegistry.Presentation.CHAT
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
        for (DialogueSession session : sessionStore.view()) {
            if (session.executionHandle() == handle) {
                return session;
            }
        }
        return null;
    }

    private String handleGraphId(GraphExecutionHandle handle) {
        return handle.graphId();
    }

    private void closeSessionsForEntity(UUID entityId, String reason) {
        for (DialogueSession session : sessionStore.snapshot()) {
            DialogueContext context = session.getDialogueContext();
            if (context == null) {
                continue;
            }
            if (entityId.equals(context.dialogueEntityId())) {
                closeSessionInternal(session, reason, "closed", true, true);
            }
        }
    }

    private void closeSessionInternal(DialogueSession session, String reason, String resumePort, boolean notifyClient, boolean resumeHandle) {
        GraphExecutionHandle handle = detachSessionInternal(session, reason, notifyClient);
        if (resumeHandle && handle != null && handle.isActive()) {
            handle.resume(resumePort == null || resumePort.isBlank() ? "closed" : resumePort);
        }
    }

    private boolean hasActiveHandle(DialogueSession session) {
        GraphExecutionHandle handle = session.executionHandle();
        return handle != null && handle.isActive();
    }

    @Nullable
    private GraphExecutionHandle detachSessionInternal(@Nullable DialogueSession session,
                                                       @Nullable String reason,
                                                       boolean notifyClient) {
        if (session == null) {
            return null;
        }
        String closeReason = reason == null || reason.isBlank() ? DialogueSession.CloseReason.CLOSED : reason;
        GraphExecutionHandle handle = session.executionHandle();
        ServerPlayer player = findPlayer(session);
        sessionStore.remove(session.getSessionId());
        session.setExecutionHandle(null);
        session.close(closeReason);

        if (notifyClient && player != null) {
            getPresenter(session).close(player, session, closeReason);
        }
        return handle;
    }

    @Nullable
    private String evaluateCloseReason(ServerLevel level, DialogueSession session) {
        ServerPlayer player = level.getServer().getPlayerList().getPlayer(session.getPlayerId());
        if (player == null || player.isRemoved()) {
            return DialogueSession.CloseReason.PLAYER_LOGOUT;
        }
        if (!player.isAlive()) {
            return DialogueSession.CloseReason.PLAYER_DEAD;
        }
        if (player.level() != level) {
            return DialogueSession.CloseReason.DIMENSION_CHANGED;
        }

        DialogueContext context = session.getDialogueContext();
        if (context != null) {
            Entity dialogueEntity = context.resolveDialogueEntity(level);
            if (context.dialogueEntityId() != null && dialogueEntity == null) {
                return DialogueSession.CloseReason.ACTOR_REMOVED;
            }
            if (isDead(dialogueEntity)) {
                return DialogueSession.CloseReason.ACTOR_DEAD;
            }
        }

        return null;
    }

    @Nullable
    private UUID lockEntityId(@Nullable DialogueContext context) {
        if (context == null) {
            return null;
        }
        return context.dialogueEntityId();
    }

    private void replaceShopPage(DialogueSession session, ShopPagePayload shop) {
        DialoguePagePayload page = session.getCurrentPage();
        if (page == null) {
            throw new IllegalStateException("Dialogue session has no current page");
        }
        session.replaceCurrentPage(page.withShop(shop));
    }

    @Nullable
    private static Entity resolveSellerEntity(ServerLevel level, @Nullable DialogueContext context) {
        if (context == null) {
            return null;
        }
        return context.resolveDialogueEntity(level);
    }

    private static boolean isDead(@Nullable Entity entity) {
        return entity instanceof LivingEntity livingEntity && !livingEntity.isAlive();
    }

    @Nullable
    private ServerPlayer findPlayer(DialogueSession session) {
        GraphExecutionHandle handle = session.executionHandle();
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
        for (DialogueSession candidate : sessionStore.view()) {
            DialogueContext context = candidate.getDialogueContext();
            if (context != null && context.player() != null && Objects.equals(context.player().getUUID(), playerId)) {
                return context.player();
            }
        }
        return null;
    }

}

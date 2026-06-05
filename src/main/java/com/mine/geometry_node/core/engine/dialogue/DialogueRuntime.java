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
import com.mine.geometry_node.core.engine.dialogue.text.DialogueTextManager;
import com.mine.geometry_node.core.engine.graph.GraphKind;
import com.mine.geometry_node.core.engine.graph.runtime.ExternalWaitRequest;
import com.mine.geometry_node.core.engine.graph.runtime.GraphExecutionHandle;
import com.mine.geometry_node.core.engine.graph.runtime.GraphRuntime;
import net.minecraft.network.chat.Component;
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

    private final DialogueSessionManager sessionManager;
    private final DialogueTextManager textManager;
    private final DialoguePresenter chatPresenter;
    private final DialoguePresenter packetPresenter;

    public DialogueRuntime() {
        this(new DialogueSessionManager(), new DialogueTextManager());
    }

    public DialogueRuntime(DialogueSessionManager sessionManager, DialogueTextManager textManager) {
        this(sessionManager, textManager, ChatDialoguePresenter.INSTANCE, PacketDialoguePresenter.INSTANCE);
    }

    public DialogueRuntime(DialogueSessionManager sessionManager,
                           DialogueTextManager textManager,
                           DialoguePresenter chatPresenter,
                           DialoguePresenter packetPresenter) {
        this.sessionManager = sessionManager;
        this.textManager = textManager;
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
                sendBusyMessage(player, policy);
                handle.resume("closed");
                return true;
            }
        }

        DialogueSession session = sessionManager.createSession(player.getUUID(), handleGraphId(handle));
        session.setCurrentPage(dialogueRequest.page());
        session.setExecutionHandle(handle);
        session.setDialogueContext(dialogueContext);
        session.setPolicy(policy);
        long gameTime = player.serverLevel().getGameTime();
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

    public DialogueTextManager getTextManager() {
        return textManager;
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
            Entity distanceTarget = speakerEntity != null ? speakerEntity : targetEntity;
            if (distanceTarget != null && session.getPolicy().hasDistanceLimit()) {
                double maxDistance = session.getPolicy().maxDistance();
                if (player.distanceToSqr(distanceTarget) > maxDistance * maxDistance) {
                    return DialogueCloseReason.TOO_FAR;
                }
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

    private void sendBusyMessage(ServerPlayer player, DialogueSessionPolicy policy) {
        String key = policy.busyTextKey();
        if (key == null || key.isBlank()) {
            key = "geometry_node.dialogue.busy";
        }
        player.sendSystemMessage(Component.literal(textManager.resolveText(key, key)));
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
}

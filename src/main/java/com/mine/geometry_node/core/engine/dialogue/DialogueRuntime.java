package com.mine.geometry_node.core.engine.dialogue;

import com.mine.geometry_node.core.engine.graph.GraphKind;
import com.mine.geometry_node.core.engine.graph.runtime.ExternalWaitRequest;
import com.mine.geometry_node.core.engine.graph.runtime.GraphExecutionHandle;
import com.mine.geometry_node.core.engine.graph.runtime.GraphRuntime;
import com.mine.geometry_node.core.network.NetworkHandler;
import com.mine.geometry_node.core.network.packet.s2c.PacketCloseDialogue;
import com.mine.geometry_node.core.network.packet.s2c.PacketOpenDialogue;
import net.minecraft.server.level.ServerPlayer;
import org.jetbrains.annotations.Nullable;

import java.util.UUID;

/**
 * Minimal server-side facade for dialogue runtime state.
 */
public class DialogueRuntime implements GraphRuntime {
    public static final DialogueRuntime INSTANCE = new DialogueRuntime(new NoopDialogueGraphLauncher());

    private final DialogueSessionManager sessionManager;
    private final DialogueTextManager textManager;
    private final DialogueGraphLauncher graphLauncher;

    public DialogueRuntime(DialogueGraphLauncher graphLauncher) {
        this(new DialogueSessionManager(), new DialogueTextManager(), graphLauncher);
    }

    public DialogueRuntime(DialogueSessionManager sessionManager, DialogueTextManager textManager, DialogueGraphLauncher graphLauncher) {
        this.sessionManager = sessionManager;
        this.textManager = textManager;
        this.graphLauncher = graphLauncher;
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
    public boolean beginExternalWait(GraphExecutionHandle handle, ExternalWaitRequest request) {
        if (!(request instanceof DialogueWaitRequest dialogueRequest)) {
            return false;
        }
        ServerPlayer player = dialogueRequest.player();
        if (player == null) {
            return false;
        }

        closeForPlayer(player, "replaced");

        DialogueSession session = sessionManager.createSession(player.getUUID(), handleGraphId(handle));
        session.setCurrentPage(dialogueRequest.page());
        session.setExecutionHandle(handle);
        openForPlayer(player, session);
        return true;
    }

    @Override
    public void endExternalWait(GraphExecutionHandle handle, @Nullable String reason) {
        DialogueSession match = null;
        for (DialogueSession session : sessionManager.getSessions()) {
            if (session.getExecutionHandle() == handle) {
                match = session;
                break;
            }
        }
        if (match != null) {
            sessionManager.removeSession(match.getSessionId());
            match.setExecutionHandle(null);
            match.close();
        }
    }

    public DialogueSession startSession(UUID playerId, String graphId) {
        DialogueSession session = sessionManager.createSession(playerId, graphId);
        applyLaunchResult(session, graphLauncher.launch(session));
        return session;
    }

    @Nullable
    public DialogueSession choose(UUID sessionId, String choiceId) {
        DialogueSession session = sessionManager.getSession(sessionId);
        if (session == null || !session.isActive() || session.getCurrentPage() == null) {
            return null;
        }

        for (DialogueChoicePayload choice : session.getCurrentPage().getChoices()) {
            if (choice.getId().equals(choiceId) && choice.isEnabled()) {
                applyLaunchResult(session, graphLauncher.choose(session, choice));
                return session;
            }
        }
        return null;
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
                GraphExecutionHandle handle = session.getExecutionHandle();
                DialogueSession removed = sessionManager.removeSession(sessionId);
                if (removed != null) {
                    removed.setExecutionHandle(null);
                    removed.close();
                }
                if (handle != null) {
                    handle.resume(choice.getId());
                }
                NetworkHandler.sendToPlayer(player, new PacketCloseDialogue(sessionId, "chosen"));
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
        GraphExecutionHandle handle = session.getExecutionHandle();
        sessionManager.removeSession(sessionId);
        session.setExecutionHandle(null);
        session.close();
        if (handle != null) {
            handle.resume("closed");
        }
        NetworkHandler.sendToPlayer(player, new PacketCloseDialogue(sessionId, "client"));
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
        DialogueSession session = sessionManager.getSession(sessionId);
        ServerPlayer player = session != null ? findPlayer(session.getPlayerId()) : null;
        if (session != null) {
            sessionManager.closeSession(sessionId);
        }
        if (session != null) {
            if (player != null) {
                NetworkHandler.sendToPlayer(player, new PacketCloseDialogue(session.getSessionId(), "closed"));
            }
        }
        return session;
    }

    @Nullable
    public DialogueSession closeForPlayer(ServerPlayer player, String reason) {
        DialogueSession session = sessionManager.getSessionForPlayer(player.getUUID());
        if (session == null) {
            return null;
        }
        sessionManager.closeSession(session.getSessionId());
        NetworkHandler.sendToPlayer(player, new PacketCloseDialogue(session.getSessionId(), reason));
        return session;
    }

    public DialogueSessionManager getSessionManager() {
        return sessionManager;
    }

    public DialogueTextManager getTextManager() {
        return textManager;
    }

    public DialogueGraphLauncher getGraphLauncher() {
        return graphLauncher;
    }

    private void applyLaunchResult(DialogueSession session, DialogueGraphLauncher.LaunchResult result) {
        if (result.getPage() != null) {
            session.setCurrentPage(result.getPage());
        }
        if (result.getExecutionHandle() != null) {
            session.setExecutionHandle(result.getExecutionHandle());
        }
    }

    private void openForPlayer(ServerPlayer player, DialogueSession session) {
        DialoguePagePayload page = session.getCurrentPage();
        if (page != null && "default".equals(page.getStyleId())) {
            DefaultDialogueRenderer.render(player, session);
            return;
        }
        NetworkHandler.sendToPlayer(player, PacketOpenDialogue.from(session));
    }

    private String handleGraphId(GraphExecutionHandle handle) {
        return handle.graphId();
    }

    @Nullable
    private ServerPlayer findPlayer(UUID playerId) {
        for (DialogueSession session : sessionManager.getSessions()) {
            if (session.getPlayerId().equals(playerId)
                    && session.getExecutionHandle() != null
                    && session.getExecutionHandle().level() != null) {
                return session.getExecutionHandle().level().getServer().getPlayerList().getPlayer(playerId);
            }
        }
        return null;
    }
}

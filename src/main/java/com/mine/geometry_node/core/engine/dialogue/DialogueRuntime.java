package com.mine.geometry_node.core.engine.dialogue;

import com.mine.geometry_node.core.engine.graph.GraphKind;
import com.mine.geometry_node.core.engine.graph.runtime.GraphRuntime;
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

    public DialogueSession startSession(UUID playerId, String graphId) {
        DialogueSession session = sessionManager.createSession(playerId, graphId);
        applyLaunchResult(session, graphLauncher.launch(session));
        return session;
    }

    @Nullable
    public DialogueSession choose(UUID sessionId, String choiceId) {
        DialogueSession session = sessionManager.getSession(sessionId);
        if (session == null || !session.isActive() || session.getCurrentPage() == null) {
            return session;
        }

        for (DialogueChoicePayload choice : session.getCurrentPage().getChoices()) {
            if (choice.getId().equals(choiceId) && choice.isEnabled()) {
                applyLaunchResult(session, graphLauncher.choose(session, choice));
                return session;
            }
        }
        return session;
    }

    @Nullable
    public DialogueSession closeSession(UUID sessionId) {
        return sessionManager.closeSession(sessionId);
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
}

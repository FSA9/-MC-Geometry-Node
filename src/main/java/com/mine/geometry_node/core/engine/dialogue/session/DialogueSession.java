package com.mine.geometry_node.core.engine.dialogue.session;

import com.mine.geometry_node.core.engine.dialogue.context.DialogueContext;
import com.mine.geometry_node.core.engine.dialogue.payload.DialoguePagePayload;
import com.mine.geometry_node.core.engine.graph.runtime.GraphExecutionHandle;
import org.jetbrains.annotations.Nullable;

import java.time.Instant;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.UUID;

/**
 * Runtime state for one active server-side dialogue session.
 */
public class DialogueSession {

    public enum State {
        ACTIVE,
        CLOSED
    }

    private final UUID sessionId;
    private final UUID playerId;
    private final String graphId;
    private final Instant createdAt;
    private final Map<String, Object> variables = new LinkedHashMap<>();
    private State state = State.ACTIVE;
    private DialogueSessionPolicy policy = DialogueSessionPolicy.DEFAULT;
    private long createdGameTime = -1L;
    private long lastInteractionGameTime = -1L;
    @Nullable
    private String closeReason;
    @Nullable
    private DialoguePagePayload currentPage;
    @Nullable
    private DialogueContext dialogueContext;
    @Nullable
    private GraphExecutionHandle executionHandle;

    public DialogueSession(UUID sessionId, UUID playerId, String graphId) {
        this.sessionId = sessionId;
        this.playerId = playerId;
        this.graphId = graphId;
        this.createdAt = Instant.now();
    }

    public UUID getSessionId() {
        return sessionId;
    }

    public UUID getPlayerId() {
        return playerId;
    }

    public String getGraphId() {
        return graphId;
    }

    public Instant getCreatedAt() {
        return createdAt;
    }

    public State getState() {
        return state;
    }

    public boolean isActive() {
        return state == State.ACTIVE;
    }

    public void close() {
        close(DialogueCloseReason.CLOSED);
    }

    public void close(String reason) {
        this.state = State.CLOSED;
        this.closeReason = reason == null || reason.isBlank() ? DialogueCloseReason.CLOSED : reason;
        if (this.executionHandle != null) {
            this.executionHandle.close();
            this.executionHandle = null;
        }
    }

    @Nullable
    public DialoguePagePayload getCurrentPage() {
        return currentPage;
    }

    public void setCurrentPage(@Nullable DialoguePagePayload currentPage) {
        this.currentPage = currentPage;
    }

    @Nullable
    public DialogueContext getDialogueContext() {
        return dialogueContext;
    }

    public void setDialogueContext(@Nullable DialogueContext dialogueContext) {
        this.dialogueContext = dialogueContext;
    }

    @Nullable
    public GraphExecutionHandle getExecutionHandle() {
        return executionHandle;
    }

    public void setExecutionHandle(@Nullable GraphExecutionHandle executionHandle) {
        this.executionHandle = executionHandle;
    }

    public Map<String, Object> getVariables() {
        return variables;
    }

    public DialogueSessionPolicy getPolicy() {
        return policy;
    }

    public void setPolicy(@Nullable DialogueSessionPolicy policy) {
        this.policy = policy == null ? DialogueSessionPolicy.DEFAULT : policy;
    }

    public long getCreatedGameTime() {
        return createdGameTime;
    }

    public void setCreatedGameTime(long createdGameTime) {
        this.createdGameTime = createdGameTime;
    }

    public long getLastInteractionGameTime() {
        return lastInteractionGameTime;
    }

    public void setLastInteractionGameTime(long lastInteractionGameTime) {
        this.lastInteractionGameTime = lastInteractionGameTime;
    }

    public void touch(long gameTime) {
        this.lastInteractionGameTime = gameTime;
    }

    @Nullable
    public String getCloseReason() {
        return closeReason;
    }
}

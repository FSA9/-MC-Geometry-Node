package com.mine.geometry_node.core.engine.dialogue;

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
    @Nullable
    private DialoguePagePayload currentPage;
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
        this.state = State.CLOSED;
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
    public GraphExecutionHandle getExecutionHandle() {
        return executionHandle;
    }

    public void setExecutionHandle(@Nullable GraphExecutionHandle executionHandle) {
        this.executionHandle = executionHandle;
    }

    public Map<String, Object> getVariables() {
        return variables;
    }
}

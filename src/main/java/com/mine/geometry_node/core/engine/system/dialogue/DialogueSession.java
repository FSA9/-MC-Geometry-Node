package com.mine.geometry_node.core.engine.system.dialogue;

import com.mine.geometry_node.core.engine.system.dialogue.model.DialoguePagePayload;
import com.mine.geometry_node.core.engine.graph.runtime.GraphExecutionHandle;
import org.jetbrains.annotations.Nullable;

import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

/**
 * Runtime state for one server-side dialogue session.
 * Lifecycle mutation is package-private and owned by {@link DialogueRuntime}.
 */
public final class DialogueSession {
    public enum State {
        ACTIVE,
        CLOSED
    }

    private final UUID sessionId;
    private final UUID playerId;
    private final String graphId;
    private final Instant createdAt;
    private State state = State.ACTIVE;
    private Policy policy = Policy.DEFAULT;
    @Nullable
    private String closeReason;
    @Nullable
    private DialoguePagePayload currentPage;
    private List<DialoguePagePayload> pages = List.of();
    private int currentPageIndex = -1;
    @Nullable
    private DialogueContext dialogueContext;
    @Nullable
    private GraphExecutionHandle executionHandle;
    @Nullable
    private String presenterId;

    DialogueSession(UUID sessionId, UUID playerId, String graphId) {
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

    @Nullable
    public DialoguePagePayload getCurrentPage() {
        return currentPage;
    }

    public int getCurrentPageIndex() {
        return currentPageIndex;
    }

    @Nullable
    public DialogueContext getDialogueContext() {
        return dialogueContext;
    }

    @Nullable
    GraphExecutionHandle executionHandle() {
        return executionHandle;
    }

    @Nullable
    public String getPresenterId() {
        return presenterId;
    }

    public Policy getPolicy() {
        return policy;
    }

    @Nullable
    public String getCloseReason() {
        return closeReason;
    }

    void close(String reason) {
        this.state = State.CLOSED;
        this.closeReason = reason == null || reason.isBlank() ? CloseReason.CLOSED : reason;
    }

    void setPages(List<DialoguePagePayload> pages) {
        this.pages = pages == null ? List.of() : List.copyOf(pages);
        this.currentPageIndex = this.pages.isEmpty() ? -1 : 0;
        this.currentPage = this.currentPageIndex < 0 ? null : this.pages.get(this.currentPageIndex);
    }

    boolean advancePage(int expectedPageIndex) {
        if (currentPageIndex != expectedPageIndex || currentPageIndex + 1 >= pages.size()) {
            return false;
        }
        currentPageIndex++;
        currentPage = pages.get(currentPageIndex);
        return true;
    }

    void replaceCurrentPage(DialoguePagePayload page) {
        if (page == null || currentPageIndex < 0 || currentPageIndex >= pages.size()) {
            throw new IllegalStateException("Session has no current page to replace");
        }
        List<DialoguePagePayload> updated = new ArrayList<>(pages);
        updated.set(currentPageIndex, page);
        pages = List.copyOf(updated);
        currentPage = page;
    }

    void setDialogueContext(@Nullable DialogueContext dialogueContext) {
        this.dialogueContext = dialogueContext;
    }

    void setExecutionHandle(@Nullable GraphExecutionHandle executionHandle) {
        this.executionHandle = executionHandle;
    }

    void setPresenterId(@Nullable String presenterId) {
        this.presenterId = presenterId;
    }

    void setPolicy(@Nullable Policy policy) {
        this.policy = policy == null ? Policy.DEFAULT : policy;
    }

    /**
     * Lifecycle policy copied from BeginDialogue into each runtime session.
     */
    public record Policy(boolean allowMultiPlayer) {
        public static final Policy DEFAULT = new Policy(true);
    }

    /**
     * Stable close reason ids for dialogue sessions.
     */
    public static final class CloseReason {
        public static final String REPLACED = "replaced";
        public static final String CLIENT = "client";
        public static final String CHOSEN = "chosen";
        public static final String NODE = "node";
        public static final String PLAYER_LOGOUT = "player_logout";
        public static final String PLAYER_DEAD = "player_dead";
        public static final String ACTOR_DEAD = "actor_dead";
        public static final String ACTOR_REMOVED = "actor_removed";
        public static final String DIMENSION_CHANGED = "dimension_changed";
        public static final String FORCED = "forced";
        public static final String SERVER_SHUTDOWN = "server_shutdown";
        public static final String CLOSED = "closed";

        private CloseReason() {
        }
    }
}

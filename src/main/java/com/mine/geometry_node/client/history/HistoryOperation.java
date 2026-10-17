package com.mine.geometry_node.client.history;

import java.util.Objects;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionStage;

/** An operation handle; only the owning session can change its phase or result. */
public final class HistoryOperation {
    public enum Kind { EDIT, UNDO, REDO }
    public enum Phase { PENDING, UNKNOWN, SETTLED }

    private final UUID id = UUID.randomUUID();
    private final Kind kind;
    private final CompletableFuture<HistoryResult> result = new CompletableFuture<>();
    private final CompletionStage<HistoryResult> completion = result.minimalCompletionStage();
    private volatile Phase phase = Phase.PENDING;

    HistoryOperation(Kind kind) {
        this.kind = Objects.requireNonNull(kind, "kind");
    }

    public UUID id() { return id; }
    public Kind kind() { return kind; }
    public Phase phase() { return phase; }
    public CompletionStage<HistoryResult> completion() { return completion; }

    public static HistoryOperation completed(Kind kind, HistoryResult result) {
        Objects.requireNonNull(result, "result");
        HistoryOperation operation = new HistoryOperation(kind);
        operation.setPhase(Phase.SETTLED);
        operation.complete(result);
        return operation;
    }

    void setPhase(Phase phase) { this.phase = phase; }
    void complete(HistoryResult value) { result.complete(value); }
}

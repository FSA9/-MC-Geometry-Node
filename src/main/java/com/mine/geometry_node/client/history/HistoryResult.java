package com.mine.geometry_node.client.history;

import java.util.Objects;

public record HistoryResult(Status status, String message, String changeId, long revision) {
    public enum Status { APPLIED, NO_CHANGE, REJECTED, CONFLICT, FAILED, SCOPE_CLOSED }

    public HistoryResult {
        Objects.requireNonNull(status, "status");
        message = message == null ? "" : message;
    }

    public boolean applied() {
        return status == Status.APPLIED;
    }
}

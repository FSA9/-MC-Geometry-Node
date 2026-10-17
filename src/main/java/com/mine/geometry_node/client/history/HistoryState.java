package com.mine.geometry_node.client.history;

import java.util.Objects;

public record HistoryState(String scopeId, Phase phase, long revision, String lastChangeId,
                           int undoCount, int redoCount, String undoLabel, String redoLabel,
                           boolean savepoints, boolean dirty, String undoBlockedReason,
                           String redoBlockedReason) {
    public enum Phase { IDLE, PENDING, UNKNOWN, INVALID, CLOSED }

    public HistoryState {
        Objects.requireNonNull(scopeId, "scopeId");
        Objects.requireNonNull(phase, "phase");
        undoLabel = undoLabel == null ? "" : undoLabel;
        redoLabel = redoLabel == null ? "" : redoLabel;
        undoBlockedReason = undoBlockedReason == null ? "" : undoBlockedReason;
        redoBlockedReason = redoBlockedReason == null ? "" : redoBlockedReason;
    }

    public boolean canUndo() {
        return phase == Phase.IDLE && undoCount > 0 && undoBlockedReason.isEmpty();
    }

    public boolean canRedo() {
        return phase == Phase.IDLE && redoCount > 0 && redoBlockedReason.isEmpty();
    }

    public HistoryState withAvailability(String undoReason, String redoReason) {
        return new HistoryState(scopeId, phase, revision, lastChangeId, undoCount, redoCount,
                undoLabel, redoLabel, savepoints, dirty, undoReason, redoReason);
    }
}

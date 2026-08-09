package com.mine.geometry_node.core.engine.system.asset.transfer.model;

public enum AssetTransferState {
    QUEUED(false),
    PREFLIGHT(false),
    TRANSFERRING(false),
    VERIFYING(false),
    COMMITTING(false),
    COMPLETED(true),
    FAILED(true),
    CANCELLED(true);

    private final boolean terminal;

    AssetTransferState(boolean terminal) {
        this.terminal = terminal;
    }

    public boolean isTerminal() {
        return terminal;
    }

    public boolean canTransitionTo(AssetTransferState next) {
        if (next == null || terminal) return false;
        if (next == FAILED || next == CANCELLED) return true;
        return switch (this) {
            case QUEUED -> next == PREFLIGHT;
            case PREFLIGHT -> next == TRANSFERRING;
            case TRANSFERRING -> next == VERIFYING;
            case VERIFYING -> next == COMMITTING;
            case COMMITTING -> next == COMPLETED;
            default -> false;
        };
    }
}

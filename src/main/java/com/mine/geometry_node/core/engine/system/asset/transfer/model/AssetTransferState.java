package com.mine.geometry_node.core.engine.system.asset.transfer.model;

public enum AssetTransferState {
    QUEUED(false),
    TRANSFERRING(false),
    COMMITTING(false),
    COMPLETED(true),
    FAILED(true);

    private final boolean terminal;

    AssetTransferState(boolean terminal) {
        this.terminal = terminal;
    }

    public boolean isTerminal() {
        return terminal;
    }
}

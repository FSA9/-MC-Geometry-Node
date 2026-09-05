package com.mine.geometry_node.core.engine.system.asset.transfer.model;

public enum AssetTransferErrorCode {
    NONE(false),
    INVALID_PATH(false),
    FILE_TOO_LARGE(false),
    PERMISSION_DENIED(false),
    SERVER_BUSY(true),
    STALE_OBJECT(false),
    IO_FAILURE(true),
    DISCONNECTED(true),
    GRAPH_RELOAD_FAILED(true),
    UNKNOWN(true);

    private final boolean retryable;

    AssetTransferErrorCode(boolean retryable) {
        this.retryable = retryable;
    }

    public boolean isRetryable() {
        return retryable;
    }
}

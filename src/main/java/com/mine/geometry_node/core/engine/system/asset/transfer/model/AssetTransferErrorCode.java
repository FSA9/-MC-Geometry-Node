package com.mine.geometry_node.core.engine.system.asset.transfer.model;

public enum AssetTransferErrorCode {
    NONE(false),
    UNSUPPORTED_TYPE(false),
    INVALID_PATH(false),
    SOURCE_MISSING(true),
    SOURCE_CHANGED(true),
    FILE_TOO_LARGE(false),
    CHUNK_TOO_LARGE(false),
    INVALID_SEQUENCE(false),
    PERMISSION_DENIED(false),
    CONFLICT_CHANGED(true),
    STALE_OBJECT(false),
    TEMPORARY_STORAGE_LIMIT(true),
    HASH_MISMATCH(true),
    IO_FAILURE(true),
    TIMEOUT(true),
    DISCONNECTED(true),
    CANCELLED(false),
    GRAPH_RELOAD_FAILED(true),
    SERVER_BUSY(true),
    UNKNOWN(true);

    private final boolean retryable;

    AssetTransferErrorCode(boolean retryable) {
        this.retryable = retryable;
    }

    public boolean isRetryable() {
        return retryable;
    }
}

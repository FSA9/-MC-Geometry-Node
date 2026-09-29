package com.mine.geometry_node.core.engine.system.model.runtime;
/** SERVER_CONTENT_READY is intentionally not a client GPU-ready state. */
public enum ModelLoadState {
    REQUESTED, READING, PARSING, CPU_READY, UPLOAD_QUEUED, READY, FAILED, CANCELLED, SUPERSEDED, RETIRED, DISPOSED
}

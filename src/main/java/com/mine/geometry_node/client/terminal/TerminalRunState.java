package com.mine.geometry_node.client.terminal;

public enum TerminalRunState {
    IDLE,
    STARTING,
    RUNNING,
    STOPPING,
    EXITED,
    FAILED,
    DISPOSED;

    public boolean acceptsInput() {
        return this == RUNNING;
    }

    public boolean hasActiveBackend() {
        return this == STARTING || this == RUNNING || this == STOPPING;
    }
}

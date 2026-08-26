package com.mine.geometry_node.core.engine.behavior.runtime;

/** Lifecycle of one owner-specific behavior-tree instance. */
public enum BehaviorInstanceState {
    CREATED,
    RUNNING,
    SUSPENDED,
    STOPPED,
    ERROR;

    public boolean isActive() {
        return this == RUNNING || this == SUSPENDED;
    }
}

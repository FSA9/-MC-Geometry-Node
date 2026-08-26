package com.mine.geometry_node.core.engine.behavior.contract;

/** Observable lifecycle state. Errors and aborts are not normal node results. */
public enum BehaviorNodeState {
    IDLE,
    ENTERING,
    RUNNING,
    SUSPENDED,
    EXITING,
    SUCCEEDED,
    FAILED,
    ABORTED,
    ERROR;

    public boolean isActive() {
        return this == ENTERING || this == RUNNING || this == SUSPENDED || this == EXITING;
    }

    public boolean isTerminal() {
        return this == SUCCEEDED || this == FAILED || this == ABORTED || this == ERROR;
    }
}

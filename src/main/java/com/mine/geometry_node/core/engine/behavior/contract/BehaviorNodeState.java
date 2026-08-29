package com.mine.geometry_node.core.engine.behavior.contract;

/** Observable lifecycle state. Errors and aborts are not normal node results. */
public enum BehaviorNodeState {
    IDLE,
    ENTERING,
    RUNNING,
    EXITING,
    SUCCEEDED,
    FAILED,
    ABORTED,
    ERROR;

    public boolean isActive() {
        return this == ENTERING || this == RUNNING || this == EXITING;
    }
}

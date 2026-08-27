package com.mine.geometry_node.core.engine.behavior.runtime.action;

/** Signals that an action-specific deterministic hard budget was exceeded. */
public final class BehaviorBudgetExceededException extends RuntimeException {
    public BehaviorBudgetExceededException(String message) {
        super(message);
    }
}

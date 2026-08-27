package com.mine.geometry_node.core.engine.behavior.runtime.action;

/** Signals invalid compiled data or an action port/configuration contract violation. */
public final class BehaviorContractViolation extends RuntimeException {
    public BehaviorContractViolation(String message) {
        super(message);
    }
}

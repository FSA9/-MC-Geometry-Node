package com.mine.geometry_node.core.engine.behavior.contract;

import java.util.Objects;

/** Child-result decision table shared by memory sequence and selector executors. */
public enum BehaviorCompositeMode {
    MEMORY_SEQUENCE(Kind.SEQUENCE),
    MEMORY_SELECTOR(Kind.SELECTOR);

    private final Kind kind;

    BehaviorCompositeMode(Kind kind) {
        this.kind = kind;
    }

    public ChildDecision decide(BehaviorResult childResult, boolean lastChild) {
        Objects.requireNonNull(childResult, "childResult");
        if (childResult == BehaviorResult.RUNNING) return ChildDecision.RETURN_RUNNING;
        if (kind == Kind.SEQUENCE) {
            if (childResult == BehaviorResult.FAILURE) return ChildDecision.RETURN_FAILURE;
            return lastChild ? ChildDecision.RETURN_SUCCESS : ChildDecision.ADVANCE;
        }
        if (childResult == BehaviorResult.SUCCESS) return ChildDecision.RETURN_SUCCESS;
        return lastChild ? ChildDecision.RETURN_FAILURE : ChildDecision.ADVANCE;
    }

    private enum Kind { SEQUENCE, SELECTOR }

    public enum ChildDecision {
        ADVANCE,
        RETURN_SUCCESS,
        RETURN_FAILURE,
        RETURN_RUNNING
    }
}

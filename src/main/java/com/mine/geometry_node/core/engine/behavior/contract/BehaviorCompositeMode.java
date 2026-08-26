package com.mine.geometry_node.core.engine.behavior.contract;

import java.util.Objects;

/** Stable, visually distinct composite semantics. */
public enum BehaviorCompositeMode {
    MEMORY_SEQUENCE(Kind.SEQUENCE, true, false, false),
    MEMORY_SELECTOR(Kind.SELECTOR, true, false, false),
    REACTIVE_SEQUENCE(Kind.SEQUENCE, false, true, true),
    PRIORITY_SELECTOR(Kind.SELECTOR, false, true, true);

    private final Kind kind;
    private final boolean resumesRunningChild;
    private final boolean reevaluatesFromFirstChild;
    private final boolean abortsDeselectedBranch;

    BehaviorCompositeMode(Kind kind, boolean resumesRunningChild,
                          boolean reevaluatesFromFirstChild,
                          boolean abortsDeselectedBranch) {
        this.kind = kind;
        this.resumesRunningChild = resumesRunningChild;
        this.reevaluatesFromFirstChild = reevaluatesFromFirstChild;
        this.abortsDeselectedBranch = abortsDeselectedBranch;
    }

    public boolean resumesRunningChild() {
        return resumesRunningChild;
    }

    public boolean reevaluatesFromFirstChild() {
        return reevaluatesFromFirstChild;
    }

    public boolean abortsDeselectedBranch() {
        return abortsDeselectedBranch;
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

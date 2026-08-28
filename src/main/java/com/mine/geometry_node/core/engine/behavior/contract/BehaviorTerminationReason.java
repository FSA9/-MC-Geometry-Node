package com.mine.geometry_node.core.engine.behavior.contract;

import org.jetbrains.annotations.Nullable;

/** Why an active node left its active lifecycle. */
public enum BehaviorTerminationReason {
    COMPLETED_SUCCESS(Kind.NORMAL, BehaviorResult.SUCCESS),
    COMPLETED_FAILURE(Kind.NORMAL, BehaviorResult.FAILURE),
    PARENT_BRANCH_REJECTED(Kind.ABORT, null),
    PRIORITY_PREEMPTED(Kind.ABORT, null),
    GUARD_INVALIDATED(Kind.ABORT, null),
    TIMEOUT(Kind.ABORT, null),
    RETRIES_EXHAUSTED(Kind.NORMAL, BehaviorResult.FAILURE),
    TARGET_INVALID(Kind.ABORT, null),
    OWNER_INVALID(Kind.ABORT, null),
    WORLD_INVALID(Kind.ABORT, null),
    CAPABILITY_LOST(Kind.ABORT, null),
    TREE_SUSPENDED(Kind.ABORT, null),
    TREE_STOPPED(Kind.ABORT, null),
    UNBOUND(Kind.ABORT, null),
    ASSET_REPLACED(Kind.ABORT, null),
    RESOURCE_RELOADED(Kind.ABORT, null),
    CHUNK_UNLOADED(Kind.ABORT, null),
    DIMENSION_CHANGED(Kind.ABORT, null),
    SERVER_STOPPING(Kind.ABORT, null),
    EXPLICIT_CANCEL(Kind.ABORT, null),
    NODE_EXCEPTION(Kind.ERROR, null),
    INVALID_DATA(Kind.ERROR, null),
    BUDGET_EXHAUSTED(Kind.ERROR, null);

    private final Kind kind;
    @Nullable
    private final BehaviorResult result;

    BehaviorTerminationReason(Kind kind, @Nullable BehaviorResult result) {
        this.kind = kind;
        this.result = result;
    }

    public Kind kind() {
        return kind;
    }

    @Nullable
    public BehaviorResult result() {
        return result;
    }

    public enum Kind { NORMAL, ABORT, ERROR }
}

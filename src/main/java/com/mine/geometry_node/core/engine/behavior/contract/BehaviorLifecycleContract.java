package com.mine.geometry_node.core.engine.behavior.contract;

import java.util.EnumSet;
import java.util.Map;
import java.util.Set;

/**
 * Canonical node-state transition table. Runtime implementations must use this
 * table instead of silently coercing an illegal lifecycle transition. Entering
 * occurs once before the first update. A RUNNING result retains the node and
 * later evaluations call update without entering again. Normal completion,
 * abort and error all pass through EXITING exactly once; active descendants are
 * exited in reverse child order before their parent. Suspending an instance
 * aborts its active branch; resuming starts a fresh evaluation from the root.
 */
public final class BehaviorLifecycleContract {
    private static final Map<BehaviorNodeState, Set<BehaviorNodeState>> TRANSITIONS = Map.of(
            BehaviorNodeState.IDLE, EnumSet.of(BehaviorNodeState.ENTERING),
            BehaviorNodeState.ENTERING, EnumSet.of(BehaviorNodeState.RUNNING, BehaviorNodeState.EXITING),
            BehaviorNodeState.RUNNING, EnumSet.of(BehaviorNodeState.EXITING),
            BehaviorNodeState.EXITING, EnumSet.of(BehaviorNodeState.SUCCEEDED, BehaviorNodeState.FAILED,
                    BehaviorNodeState.ABORTED, BehaviorNodeState.ERROR),
            BehaviorNodeState.SUCCEEDED, EnumSet.of(BehaviorNodeState.IDLE),
            BehaviorNodeState.FAILED, EnumSet.of(BehaviorNodeState.IDLE),
            BehaviorNodeState.ABORTED, EnumSet.of(BehaviorNodeState.IDLE),
            BehaviorNodeState.ERROR, EnumSet.of(BehaviorNodeState.IDLE));

    private BehaviorLifecycleContract() {
    }

    public static boolean allows(BehaviorNodeState from, BehaviorNodeState to) {
        if (from == null || to == null) return false;
        return TRANSITIONS.get(from).contains(to);
    }

    public static BehaviorNodeState terminalState(BehaviorTerminationReason reason) {
        return switch (reason.kind()) {
            case NORMAL -> reason.result() == BehaviorResult.SUCCESS
                    ? BehaviorNodeState.SUCCEEDED : BehaviorNodeState.FAILED;
            case ABORT -> BehaviorNodeState.ABORTED;
            case ERROR -> BehaviorNodeState.ERROR;
        };
    }
}

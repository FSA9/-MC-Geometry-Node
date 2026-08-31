package com.mine.geometry_node.core.engine.behavior.runtime.executor;

import com.mine.geometry_node.core.engine.behavior.contract.BehaviorResult;
import com.mine.geometry_node.core.engine.behavior.contract.BehaviorTerminationReason;
import com.mine.geometry_node.core.engine.behavior.runtime.BehaviorNodeContext;
import com.mine.geometry_node.core.engine.behavior.runtime.BehaviorNodeExecutor;

/** Executors for behavior roots and composite control nodes. */
public final class BehaviorControlExecutors {
    private static final BehaviorNodeExecutor ROOT = context -> context.tickChild(0);
    private static final BehaviorNodeExecutor SEQUENCE =
            new MemoryCompositeExecutor(true);
    private static final BehaviorNodeExecutor SELECTOR =
            new MemoryCompositeExecutor(false);
    private static final BehaviorNodeExecutor REACTIVE_SEQUENCE =
            new ReactiveCompositeExecutor(true);
    private static final BehaviorNodeExecutor PRIORITY_SELECTOR =
            new ReactiveCompositeExecutor(false);

    private BehaviorControlExecutors() {
    }

    public static BehaviorNodeExecutor root() {
        return ROOT;
    }

    public static BehaviorNodeExecutor sequence() {
        return SEQUENCE;
    }

    public static BehaviorNodeExecutor selector() {
        return SELECTOR;
    }

    public static BehaviorNodeExecutor reactiveSequence() {
        return REACTIVE_SEQUENCE;
    }

    public static BehaviorNodeExecutor prioritySelector() {
        return PRIORITY_SELECTOR;
    }

    private static final class MemoryCompositeExecutor implements BehaviorNodeExecutor {
        private final boolean sequence;

        private MemoryCompositeExecutor(boolean sequence) {
            this.sequence = sequence;
        }

        @Override
        public void enter(BehaviorNodeContext context) {
            context.setMemory(0);
        }

        @Override
        public BehaviorResult update(BehaviorNodeContext context) {
            if (context.childCount() == 0) return BehaviorResult.SUCCESS;
            int childIndex = context.memory() instanceof Integer value ? value : 0;
            while (childIndex < context.childCount()) {
                BehaviorResult result = context.tickChild(childIndex);
                if (result == BehaviorResult.RUNNING) return result;
                if (sequence && result == BehaviorResult.FAILURE
                        || !sequence && result == BehaviorResult.SUCCESS) return result;
                childIndex++;
                context.setMemory(childIndex);
            }
            return sequence ? BehaviorResult.SUCCESS : BehaviorResult.FAILURE;
        }

        @Override
        public void exit(BehaviorNodeContext context, BehaviorTerminationReason reason) {
            context.setMemory(null);
        }
    }

    private static final class ReactiveCompositeExecutor implements BehaviorNodeExecutor {
        private final boolean sequence;

        private ReactiveCompositeExecutor(boolean sequence) {
            this.sequence = sequence;
        }

        @Override
        public BehaviorResult update(BehaviorNodeContext context) {
            int previous = context.memory() instanceof Integer value ? value : -1;
            for (int child = 0; child < context.childCount(); child++) {
                BehaviorTerminationReason replacementReason = sequence
                        ? BehaviorTerminationReason.GUARD_INVALIDATED
                        : BehaviorTerminationReason.PRIORITY_PREEMPTED;
                BehaviorResult result = previous >= 0 && previous != child
                        ? context.tickChildReplacing(child, previous, replacementReason)
                        : context.tickChild(child);
                if (result == BehaviorResult.RUNNING) {
                    preemptPrevious(context, previous, child);
                    context.setMemory(child);
                    return BehaviorResult.RUNNING;
                }
                boolean terminal = sequence
                        ? result == BehaviorResult.FAILURE : result == BehaviorResult.SUCCESS;
                if (terminal) {
                    preemptPrevious(context, previous, child);
                    context.setMemory(-1);
                    return result;
                }
            }
            preemptPrevious(context, previous, -1);
            context.setMemory(-1);
            return sequence ? BehaviorResult.SUCCESS : BehaviorResult.FAILURE;
        }

        private void preemptPrevious(BehaviorNodeContext context, int previous, int selected) {
            if (previous < 0 || previous == selected) return;
            context.abortChild(previous, sequence
                    ? BehaviorTerminationReason.GUARD_INVALIDATED
                    : BehaviorTerminationReason.PRIORITY_PREEMPTED);
        }

        @Override
        public void exit(BehaviorNodeContext context, BehaviorTerminationReason reason) {
            context.setMemory(null);
        }
    }
}

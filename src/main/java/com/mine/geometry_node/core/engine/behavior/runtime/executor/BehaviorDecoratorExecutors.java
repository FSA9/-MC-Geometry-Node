package com.mine.geometry_node.core.engine.behavior.runtime.executor;

import com.mine.geometry_node.core.engine.behavior.contract.BehaviorResult;
import com.mine.geometry_node.core.engine.behavior.contract.BehaviorTerminationReason;
import com.mine.geometry_node.core.engine.behavior.runtime.BehaviorNodeContext;
import com.mine.geometry_node.core.engine.behavior.runtime.BehaviorNodeExecutor;
import com.mine.geometry_node.core.engine.behavior.runtime.action.BehaviorContractViolation;
import com.mine.geometry_node.core.node.nodes.behavior.decorator.BehaviorDecoratorNode;
import com.mine.geometry_node.core.node.definition.port.StandardPorts;
import org.jetbrains.annotations.Nullable;

/** Executors for behavior decorators. */
public final class BehaviorDecoratorExecutors {
    private static final BehaviorNodeExecutor GUARD = new GuardExecutor();
    private static final BehaviorNodeExecutor INVERTER = context -> switch (context.tickChild(0)) {
        case SUCCESS -> BehaviorResult.FAILURE;
        case FAILURE -> BehaviorResult.SUCCESS;
        case RUNNING -> BehaviorResult.RUNNING;
    };
    private static final BehaviorNodeExecutor REPEAT = new RepeatExecutor(false);
    private static final BehaviorNodeExecutor RETRY = new RepeatExecutor(true);
    private static final BehaviorNodeExecutor TIMEOUT = new TimeoutExecutor();
    private static final BehaviorNodeExecutor COOLDOWN = new CooldownExecutor();
    private static final BehaviorNodeExecutor ALWAYS_SUCCEED = context -> {
        BehaviorResult result = context.tickChild(0);
        return result == BehaviorResult.RUNNING ? result : BehaviorResult.SUCCESS;
    };
    private static final BehaviorNodeExecutor ALWAYS_FAIL = context -> {
        BehaviorResult result = context.tickChild(0);
        return result == BehaviorResult.RUNNING ? result : BehaviorResult.FAILURE;
    };

    private BehaviorDecoratorExecutors() {
    }

    public static BehaviorNodeExecutor guard() {
        return GUARD;
    }

    public static BehaviorNodeExecutor inverter() {
        return INVERTER;
    }

    public static BehaviorNodeExecutor forKind(BehaviorDecoratorNode.Kind kind) {
        return switch (kind) {
            case REPEAT -> REPEAT;
            case RETRY -> RETRY;
            case TIMEOUT -> TIMEOUT;
            case COOLDOWN -> COOLDOWN;
            case ALWAYS_SUCCEED -> ALWAYS_SUCCEED;
            case ALWAYS_FAIL -> ALWAYS_FAIL;
        };
    }

    private static <T> T require(@Nullable T value, String message) {
        if (value == null) throw new BehaviorContractViolation(message);
        return value;
    }

    private static long deadline(long tick, int delay) {
        if (delay < 0) throw new BehaviorContractViolation("Tick delay cannot be negative");
        return delay > Long.MAX_VALUE - tick ? Long.MAX_VALUE : tick + delay;
    }

    private static final class GuardExecutor implements BehaviorNodeExecutor {
        @Override
        public BehaviorResult update(BehaviorNodeContext context) {
            Boolean condition = require(context.input(StandardPorts.CONDITION.getId(), Boolean.class),
                    "Guard condition is missing");
            return condition ? context.tickChild(0) : BehaviorResult.FAILURE;
        }

        @Override
        public BehaviorTerminationReason childTerminationReason(BehaviorTerminationReason ownReason) {
            return ownReason == BehaviorTerminationReason.COMPLETED_FAILURE
                    ? BehaviorTerminationReason.GUARD_INVALIDATED
                    : BehaviorNodeExecutor.super.childTerminationReason(ownReason);
        }
    }

    private static final class RepeatExecutor implements BehaviorNodeExecutor {
        private final boolean retryFailures;

        private RepeatExecutor(boolean retryFailures) {
            this.retryFailures = retryFailures;
        }

        @Override
        public void enter(BehaviorNodeContext context) {
            context.setMemory(new RepeatState(0, context.gameTick()));
        }

        @Override
        public BehaviorResult update(BehaviorNodeContext context) {
            RepeatState state = require(context.memory() instanceof RepeatState value ? value : null,
                    "Repeat state is unavailable");
            int count = require(context.input(StandardPorts.COUNT.getId(), Integer.class),
                    "Repeat count is missing");
            if (count < 0) throw new BehaviorContractViolation("Repeat count cannot be negative");
            if (context.gameTick() < state.nextAttemptTick()) {
                context.requestWakeupAt(state.nextAttemptTick());
                return BehaviorResult.RUNNING;
            }
            BehaviorResult result = context.tickChild(0);
            if (result == BehaviorResult.RUNNING) return result;

            boolean repeat = retryFailures ? result == BehaviorResult.FAILURE
                    : result == BehaviorResult.SUCCESS;
            if (!repeat) return result;
            int completed = state.completed() + 1;
            if (count > 0 && completed >= count) {
                return retryFailures ? BehaviorResult.FAILURE : BehaviorResult.SUCCESS;
            }
            int interval = retryFailures
                    ? require(context.input(StandardPorts.TICK.getId(), Integer.class),
                    "Retry interval is missing") : 1;
            if (interval <= 0) {
                throw new BehaviorContractViolation("Attempt interval must be positive");
            }
            long next = deadline(context.gameTick(), interval);
            context.setMemory(new RepeatState(completed, next));
            context.requestWakeupAt(next);
            return BehaviorResult.RUNNING;
        }

        @Override
        public void exit(BehaviorNodeContext context, BehaviorTerminationReason reason) {
            context.setMemory(null);
        }

        @Override
        public BehaviorTerminationReason completionReason(BehaviorNodeContext context,
                                                           BehaviorResult result) {
            return retryFailures && result == BehaviorResult.FAILURE
                    ? BehaviorTerminationReason.RETRIES_EXHAUSTED
                    : BehaviorNodeExecutor.super.completionReason(context, result);
        }
    }

    private static final class TimeoutExecutor implements BehaviorNodeExecutor {
        @Override
        public void enter(BehaviorNodeContext context) {
            int ticks = require(context.input(StandardPorts.TICK.getId(), Integer.class),
                    "Timeout ticks are missing");
            context.setMemory(new TimeoutState(deadline(context.gameTick(), ticks), false));
        }

        @Override
        public BehaviorResult update(BehaviorNodeContext context) {
            TimeoutState state = require(context.memory() instanceof TimeoutState value ? value : null,
                    "Timeout deadline is unavailable");
            if (context.gameTick() >= state.deadline()) {
                context.abortChild(0, BehaviorTerminationReason.TIMEOUT);
                context.setMemory(new TimeoutState(state.deadline(), true));
                return BehaviorResult.FAILURE;
            }
            BehaviorResult result = context.tickChild(0);
            if (result == BehaviorResult.RUNNING) context.requestWakeupAt(state.deadline());
            return result;
        }

        @Override
        public BehaviorTerminationReason completionReason(BehaviorNodeContext context,
                                                           BehaviorResult result) {
            return context.memory() instanceof TimeoutState state && state.timedOut()
                    ? BehaviorTerminationReason.TIMEOUT
                    : BehaviorNodeExecutor.super.completionReason(context, result);
        }

        @Override
        public void exit(BehaviorNodeContext context, BehaviorTerminationReason reason) {
            context.setMemory(null);
        }
    }

    private static final class CooldownExecutor implements BehaviorNodeExecutor {
        @Override
        public BehaviorResult update(BehaviorNodeContext context) {
            long availableAt = context.memory() instanceof Long value ? value : Long.MIN_VALUE;
            if (context.gameTick() < availableAt) return BehaviorResult.FAILURE;
            BehaviorResult result = context.tickChild(0);
            if (result == BehaviorResult.SUCCESS) {
                int ticks = require(context.input(StandardPorts.TICK.getId(), Integer.class),
                        "Cooldown ticks are missing");
                context.setMemory(deadline(context.gameTick(), ticks));
            }
            return result;
        }
    }

    private record RepeatState(int completed, long nextAttemptTick) {
    }

    private record TimeoutState(long deadline, boolean timedOut) {
    }
}

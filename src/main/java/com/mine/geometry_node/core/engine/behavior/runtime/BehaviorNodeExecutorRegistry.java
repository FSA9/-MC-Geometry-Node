package com.mine.geometry_node.core.engine.behavior.runtime;

import com.mine.geometry_node.core.engine.behavior.contract.BehaviorCompositeMode;
import com.mine.geometry_node.core.engine.behavior.contract.BehaviorResult;
import com.mine.geometry_node.core.engine.behavior.contract.BehaviorTerminationReason;
import com.mine.geometry_node.core.engine.behavior.runtime.action.BehaviorContractViolation;
import com.mine.geometry_node.core.node.nodes.behavior.action.BehaviorIdleNode;
import com.mine.geometry_node.core.node.nodes.behavior.action.BehaviorWaitNode;
import com.mine.geometry_node.core.node.nodes.behavior.blackboard.BehaviorClearBlackboardNode;
import com.mine.geometry_node.core.node.nodes.behavior.blackboard.BehaviorSetBlackboardNode;
import com.mine.geometry_node.core.node.nodes.behavior.condition.BehaviorConditionNode;
import com.mine.geometry_node.core.node.nodes.behavior.condition.BehaviorHasValidTargetNode;
import com.mine.geometry_node.core.node.nodes.behavior.condition.BehaviorUtilityConditionNode;
import com.mine.geometry_node.core.node.nodes.behavior.control.BehaviorCompositeNode;
import com.mine.geometry_node.core.node.nodes.behavior.control.BehaviorRootNode;
import com.mine.geometry_node.core.node.nodes.behavior.control.BehaviorSelectorNode;
import com.mine.geometry_node.core.node.nodes.behavior.control.BehaviorSequenceNode;
import com.mine.geometry_node.core.node.nodes.behavior.control.BehaviorSubtreeNode;
import com.mine.geometry_node.core.node.nodes.behavior.decorator.BehaviorDecoratorNode;
import com.mine.geometry_node.core.node.nodes.behavior.decorator.BehaviorGuardNode;
import com.mine.geometry_node.core.node.nodes.behavior.decorator.BehaviorInverterNode;
import com.mine.geometry_node.core.node.port.StandardPorts;
import net.minecraft.world.entity.Entity;
import org.jetbrains.annotations.Nullable;

import java.util.Map;
import java.util.Objects;
import java.util.concurrent.ConcurrentHashMap;

/** Runtime behavior implementation registry, intentionally separate from editor node definitions. */
public final class BehaviorNodeExecutorRegistry {
    public static final BehaviorNodeExecutorRegistry INSTANCE = new BehaviorNodeExecutorRegistry();
    private static final BehaviorNodeExecutor ROOT_EXECUTOR = context -> context.tickChild(0);
    private static final BehaviorNodeExecutor SUBTREE_EXECUTOR = new BehaviorNodeExecutor() {
        @Override
        public void enter(BehaviorNodeContext context) {
            context.enterSubtreeCall();
        }

        @Override
        public BehaviorResult update(BehaviorNodeContext context) {
            return context.tickChild(0);
        }

        @Override
        public void exit(BehaviorNodeContext context, BehaviorTerminationReason reason) {
            context.exitSubtreeCall(reason);
        }
    };
    private static final BehaviorNodeExecutor SEQUENCE_EXECUTOR = new MemoryCompositeExecutor(
            BehaviorCompositeMode.MEMORY_SEQUENCE);
    private static final BehaviorNodeExecutor SELECTOR_EXECUTOR = new MemoryCompositeExecutor(
            BehaviorCompositeMode.MEMORY_SELECTOR);
    private static final BehaviorNodeExecutor CONDITION_EXECUTOR = context ->
            require(context.input(StandardPorts.CONDITION.getId(), Boolean.class),
                    "Condition input is missing")
                    ? BehaviorResult.SUCCESS : BehaviorResult.FAILURE;
    private static final BehaviorNodeExecutor GUARD_EXECUTOR = new GuardExecutor();
    private static final BehaviorNodeExecutor INVERTER_EXECUTOR = context -> switch (context.tickChild(0)) {
        case SUCCESS -> BehaviorResult.FAILURE;
        case FAILURE -> BehaviorResult.SUCCESS;
        case RUNNING -> BehaviorResult.RUNNING;
    };
    private static final BehaviorNodeExecutor HAS_VALID_TARGET_EXECUTOR = context -> {
        Entity entity = context.input(StandardPorts.ENTITY.getId(), Entity.class);
        return entity != null && entity.isAlive() && !entity.isRemoved()
                ? BehaviorResult.SUCCESS : BehaviorResult.FAILURE;
    };
    private static final BehaviorNodeExecutor WAIT_EXECUTOR = new WaitExecutor();
    private static final BehaviorNodeExecutor IDLE_EXECUTOR = new IdleExecutor();
    private static final BehaviorNodeExecutor SET_BLACKBOARD_EXECUTOR = context -> {
        String key = requireKey(context);
        Object value = require(context.input(StandardPorts.ANY_VALUE.getId()),
                "Blackboard value is missing: " + key);
        context.setBlackboard(context.blackboardScope(), key, value);
        return BehaviorResult.SUCCESS;
    };
    private static final BehaviorNodeExecutor CLEAR_BLACKBOARD_EXECUTOR = context -> {
        String key = requireKey(context);
        context.clearBlackboard(context.blackboardScope(), key);
        return BehaviorResult.SUCCESS;
    };
    private static final BehaviorNodeExecutor REACTIVE_SEQUENCE_EXECUTOR =
            new ReactiveCompositeExecutor(true);
    private static final BehaviorNodeExecutor PRIORITY_SELECTOR_EXECUTOR =
            new ReactiveCompositeExecutor(false);
    private static final BehaviorNodeExecutor REPEAT_EXECUTOR = new RepeatExecutor(false);
    private static final BehaviorNodeExecutor RETRY_EXECUTOR = new RepeatExecutor(true);
    private static final BehaviorNodeExecutor TIMEOUT_EXECUTOR = new TimeoutExecutor();
    private static final BehaviorNodeExecutor COOLDOWN_EXECUTOR = new CooldownExecutor();
    private static final BehaviorNodeExecutor ALWAYS_SUCCEED_EXECUTOR = context -> {
        BehaviorResult result = context.tickChild(0);
        return result == BehaviorResult.RUNNING ? result : BehaviorResult.SUCCESS;
    };
    private static final BehaviorNodeExecutor ALWAYS_FAIL_EXECUTOR = context -> {
        BehaviorResult result = context.tickChild(0);
        return result == BehaviorResult.RUNNING ? result : BehaviorResult.FAILURE;
    };
    private static final BehaviorNodeExecutor BLACKBOARD_VALUE_CHANGED_EXECUTOR =
            new BlackboardValueChangedExecutor();

    private final Map<String, BehaviorNodeExecutor> executors = new ConcurrentHashMap<>();

    public void register(String typeId, BehaviorNodeExecutor executor) {
        String normalized = typeId != null ? typeId.trim() : "";
        if (normalized.isEmpty()) throw new IllegalArgumentException("Behavior executor type cannot be empty");
        Objects.requireNonNull(executor, "executor");
        BehaviorNodeExecutor existing = executors.putIfAbsent(normalized, executor);
        if (existing != null && existing != executor) {
            throw new IllegalStateException("Duplicate behavior executor: " + normalized);
        }
    }

    @Nullable
    public BehaviorNodeExecutor get(String typeId) {
        return executors.get(typeId);
    }

    public boolean has(String typeId) {
        return executors.containsKey(typeId);
    }

    public void registerCoreExecutors() {
        register(BehaviorRootNode.TYPE_ID, ROOT_EXECUTOR);
        register(BehaviorSubtreeNode.TYPE_ID, SUBTREE_EXECUTOR);
        register(BehaviorSequenceNode.TYPE_ID, SEQUENCE_EXECUTOR);
        register(BehaviorSelectorNode.TYPE_ID, SELECTOR_EXECUTOR);
        register(BehaviorConditionNode.TYPE_ID, CONDITION_EXECUTOR);
        register(BehaviorGuardNode.TYPE_ID, GUARD_EXECUTOR);
        register(BehaviorInverterNode.TYPE_ID, INVERTER_EXECUTOR);
        register(BehaviorHasValidTargetNode.TYPE_ID, HAS_VALID_TARGET_EXECUTOR);
        register(BehaviorWaitNode.TYPE_ID, WAIT_EXECUTOR);
        register(BehaviorIdleNode.TYPE_ID, IDLE_EXECUTOR);
        register(BehaviorSetBlackboardNode.TYPE_ID, SET_BLACKBOARD_EXECUTOR);
        register(BehaviorClearBlackboardNode.TYPE_ID, CLEAR_BLACKBOARD_EXECUTOR);
        register(BehaviorCompositeNode.Kind.REACTIVE_SEQUENCE.typeId(), REACTIVE_SEQUENCE_EXECUTOR);
        register(BehaviorCompositeNode.Kind.PRIORITY_SELECTOR.typeId(), PRIORITY_SELECTOR_EXECUTOR);
        register(BehaviorDecoratorNode.Kind.REPEAT.typeId(), REPEAT_EXECUTOR);
        register(BehaviorDecoratorNode.Kind.RETRY.typeId(), RETRY_EXECUTOR);
        register(BehaviorDecoratorNode.Kind.TIMEOUT.typeId(), TIMEOUT_EXECUTOR);
        register(BehaviorDecoratorNode.Kind.COOLDOWN.typeId(), COOLDOWN_EXECUTOR);
        register(BehaviorDecoratorNode.Kind.ALWAYS_SUCCEED.typeId(), ALWAYS_SUCCEED_EXECUTOR);
        register(BehaviorDecoratorNode.Kind.ALWAYS_FAIL.typeId(), ALWAYS_FAIL_EXECUTOR);
        register(BehaviorUtilityConditionNode.Kind.BLACKBOARD_VALUE_CHANGED.typeId(), BLACKBOARD_VALUE_CHANGED_EXECUTOR);
        BehaviorEntityExecutors.register(this);
    }

    private static String requireKey(BehaviorNodeContext context) {
        String key = context.input(StandardPorts.KEY.getId(), String.class);
        if (key == null || key.isBlank()) throw new BehaviorContractViolation("Blackboard input is missing");
        return key.trim();
    }

    private static <T> T require(@Nullable T value, String message) {
        if (value == null) throw new BehaviorContractViolation(message);
        return value;
    }

    private static long deadline(long tick, int delay) {
        if (delay < 0) throw new BehaviorContractViolation("Tick delay cannot be negative");
        return delay > Long.MAX_VALUE - tick ? Long.MAX_VALUE : tick + delay;
    }

    private static final class MemoryCompositeExecutor implements BehaviorNodeExecutor {
        private final BehaviorCompositeMode mode;

        private MemoryCompositeExecutor(BehaviorCompositeMode mode) {
            this.mode = mode;
        }

        @Override
        public void enter(BehaviorNodeContext context) {
            context.setMemory(0);
        }

        @Override
        public BehaviorResult update(BehaviorNodeContext context) {
            int childIndex = context.memory() instanceof Integer value ? value : 0;
            while (childIndex < context.childCount()) {
                BehaviorResult result = context.tickChild(childIndex);
                BehaviorCompositeMode.ChildDecision decision = mode.decide(
                        result, childIndex == context.childCount() - 1);
                switch (decision) {
                    case ADVANCE -> {
                        childIndex++;
                        context.setMemory(childIndex);
                    }
                    case RETURN_SUCCESS -> { return BehaviorResult.SUCCESS; }
                    case RETURN_FAILURE -> { return BehaviorResult.FAILURE; }
                    case RETURN_RUNNING -> { return BehaviorResult.RUNNING; }
                }
            }
            return BehaviorResult.SUCCESS;
        }

        @Override
        public void exit(BehaviorNodeContext context,
                         com.mine.geometry_node.core.engine.behavior.contract.BehaviorTerminationReason reason) {
            context.setMemory(null);
        }
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

    private static final class WaitExecutor implements BehaviorNodeExecutor {
        @Override
        public void enter(BehaviorNodeContext context) {
            Integer ticks = require(context.input(StandardPorts.BEHAVIOR_TICKS.getId(), Integer.class),
                    "Wait ticks are missing");
            context.setMemory(deadline(context.gameTick(), ticks));
        }

        @Override
        public BehaviorResult update(BehaviorNodeContext context) {
            Long deadline = require(context.memory() instanceof Long value ? value : null,
                    "Wait deadline is unavailable");
            if (context.gameTick() >= deadline) return BehaviorResult.SUCCESS;
            context.requestWakeupAt(deadline);
            return BehaviorResult.RUNNING;
        }

        @Override
        public void exit(BehaviorNodeContext context, BehaviorTerminationReason reason) {
            context.setMemory(null);
        }

    }

    private static final class IdleExecutor implements BehaviorNodeExecutor {
        @Override
        public BehaviorResult update(BehaviorNodeContext context) {
            Integer interval = require(context.input(
                    StandardPorts.POLL_INTERVAL.getId(), Integer.class),
                    "Idle poll interval is missing");
            if (interval <= 0) throw new BehaviorContractViolation("Idle poll interval must be positive");
            context.requestWakeupAt(deadline(context.gameTick(), interval));
            return BehaviorResult.RUNNING;
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
                    ? require(context.input(StandardPorts.RETRY_INTERVAL.getId(), Integer.class),
                    "Retry interval is missing") : 1;
            if (interval <= 0) throw new BehaviorContractViolation("Attempt interval must be positive");
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
            int ticks = require(context.input(StandardPorts.BEHAVIOR_TICKS.getId(), Integer.class),
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
                int ticks = require(context.input(StandardPorts.COOLDOWN_TICKS.getId(), Integer.class),
                        "Cooldown ticks are missing");
                context.setMemory(deadline(context.gameTick(), ticks));
            }
            return result;
        }
    }

    private static final class BlackboardValueChangedExecutor implements BehaviorNodeExecutor {
        @Override
        public BehaviorResult update(BehaviorNodeContext context) {
            String key = requireKey(context);
            var current = context.observeBlackboard(context.blackboardScope(), key);
            var previous = context.memory() instanceof
                    com.mine.geometry_node.core.engine.behavior.blackboard.BehaviorBlackboard.ObservationToken value
                    ? value : current;
            context.setMemory(current);
            return !current.equals(previous) ? BehaviorResult.SUCCESS : BehaviorResult.FAILURE;
        }
    }

    private record RepeatState(int completed, long nextAttemptTick) {
    }

    private record TimeoutState(long deadline, boolean timedOut) {
    }
}

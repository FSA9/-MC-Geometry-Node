package com.mine.geometry_node.core.engine.behavior.runtime;

import com.mine.geometry_node.core.engine.behavior.contract.BehaviorCompositeMode;
import com.mine.geometry_node.core.engine.behavior.contract.BehaviorResult;
import com.mine.geometry_node.core.engine.behavior.contract.BehaviorTerminationReason;
import com.mine.geometry_node.core.engine.behavior.contract.BlackboardScope;
import com.mine.geometry_node.core.engine.behavior.document.BehaviorNodeTypes;
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
        Object value = require(context.input(BehaviorNodeTypes.BLACKBOARD_VALUE_PORT),
                "Blackboard value is missing: " + key);
        context.setBlackboard(BlackboardScope.INSTANCE, key, value);
        return BehaviorResult.SUCCESS;
    };
    private static final BehaviorNodeExecutor CLEAR_BLACKBOARD_EXECUTOR = context -> {
        String key = requireKey(context);
        context.clearBlackboard(BlackboardScope.INSTANCE, key);
        return BehaviorResult.SUCCESS;
    };

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
        register(BehaviorNodeTypes.ROOT, ROOT_EXECUTOR);
        register(BehaviorNodeTypes.SEQUENCE, SEQUENCE_EXECUTOR);
        register(BehaviorNodeTypes.SELECTOR, SELECTOR_EXECUTOR);
        register(BehaviorNodeTypes.CONDITION, CONDITION_EXECUTOR);
        register(BehaviorNodeTypes.GUARD, GUARD_EXECUTOR);
        register(BehaviorNodeTypes.INVERTER, INVERTER_EXECUTOR);
        register(BehaviorNodeTypes.HAS_VALID_TARGET, HAS_VALID_TARGET_EXECUTOR);
        register(BehaviorNodeTypes.WAIT, WAIT_EXECUTOR);
        register(BehaviorNodeTypes.IDLE, IDLE_EXECUTOR);
        register(BehaviorNodeTypes.SET_BLACKBOARD, SET_BLACKBOARD_EXECUTOR);
        register(BehaviorNodeTypes.CLEAR_BLACKBOARD, CLEAR_BLACKBOARD_EXECUTOR);
    }

    private static String requireKey(BehaviorNodeContext context) {
        String key = context.input(BehaviorNodeTypes.BLACKBOARD_KEY_PORT, String.class);
        if (key == null || key.isBlank()) throw new IllegalArgumentException("Blackboard key is missing");
        return key.trim();
    }

    private static <T> T require(@Nullable T value, String message) {
        if (value == null) throw new IllegalArgumentException(message);
        return value;
    }

    private static long deadline(long tick, int delay) {
        if (delay < 0) throw new IllegalArgumentException("Tick delay cannot be negative");
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
            Integer ticks = require(context.input(BehaviorNodeTypes.TICKS_PORT, Integer.class),
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
                    BehaviorNodeTypes.POLL_INTERVAL_PORT, Integer.class),
                    "Idle poll interval is missing");
            if (interval <= 0) throw new IllegalArgumentException("Idle poll interval must be positive");
            context.requestWakeupAt(deadline(context.gameTick(), interval));
            return BehaviorResult.RUNNING;
        }
    }
}

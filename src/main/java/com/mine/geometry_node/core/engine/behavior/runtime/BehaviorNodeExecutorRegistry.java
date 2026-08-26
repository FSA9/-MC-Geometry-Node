package com.mine.geometry_node.core.engine.behavior.runtime;

import com.mine.geometry_node.core.engine.behavior.contract.BehaviorCompositeMode;
import com.mine.geometry_node.core.engine.behavior.contract.BehaviorResult;
import com.mine.geometry_node.core.engine.behavior.document.BehaviorNodeTypes;
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
}

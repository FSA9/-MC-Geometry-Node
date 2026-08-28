package com.mine.geometry_node.core.engine.behavior.runtime;

import org.jetbrains.annotations.Nullable;

import java.util.Map;
import java.util.Objects;
import java.util.concurrent.ConcurrentHashMap;

/** Runtime lookup populated atomically by NodeRegistry from behavior node providers. */
public final class BehaviorNodeExecutorRegistry {
    public static final BehaviorNodeExecutorRegistry INSTANCE = new BehaviorNodeExecutorRegistry();

    private final Map<String, BehaviorNodeExecutor> executors = new ConcurrentHashMap<>();

    public void register(String typeId, BehaviorNodeExecutor executor) {
        String normalized = typeId != null ? typeId.trim() : "";
        if (normalized.isEmpty()) {
            throw new IllegalArgumentException("Behavior executor type cannot be empty");
        }
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
}

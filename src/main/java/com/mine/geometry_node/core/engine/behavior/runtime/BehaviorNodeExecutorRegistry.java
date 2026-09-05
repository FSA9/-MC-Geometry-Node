package com.mine.geometry_node.core.engine.behavior.runtime;

import com.mine.geometry_node.core.node.NodeRegistry;
import org.jetbrains.annotations.Nullable;

/** Behavior-runtime view of executor capabilities owned by the canonical node registry. */
public final class BehaviorNodeExecutorRegistry {
    public static final BehaviorNodeExecutorRegistry INSTANCE = new BehaviorNodeExecutorRegistry();

    @Nullable
    public BehaviorNodeExecutor get(String typeId) {
        return NodeRegistry.INSTANCE.getBehaviorExecutor(typeId);
    }

    public boolean has(String typeId) {
        return get(typeId) != null;
    }
}

package com.mine.geometry_node.core.engine.graph.scoped;

import com.mine.geometry_node.core.engine.graph.runtime.GraphRuntimeContext;
import org.jetbrains.annotations.Nullable;

/**
 * Blueprint adapter for the shared scoped-state backend used by behavior blackboards.
 * INSTANCE remains behavior-frame private and is intentionally not addressable here.
 */
public final class ServerScopedStateStore implements ScopedStateStore {

    @Override
    public void set(GraphRuntimeContext context, ScopedStateNamespace namespace,
                    ScopedStateTarget target, String name, Object value) {
        String key = requireKey(name);
        if (value == null) {
            throw new ScopedStateAccessException(
                    "Scoped state value cannot be Java null: " + key);
        }
        resolve(context, namespace, target).put(key, value);
    }

    @Override
    public @Nullable Object get(GraphRuntimeContext context,
                                ScopedStateNamespace namespace,
                                ScopedStateTarget target, String name) {
        ScopedStateEntry entry = resolve(context, namespace, target).get(requireKey(name));
        return entry != null ? entry.value() : null;
    }

    @Override
    public boolean has(GraphRuntimeContext context,
                       ScopedStateNamespace namespace,
                       ScopedStateTarget target, String name) {
        return resolve(context, namespace, target).hasRecord(requireKey(name));
    }

    @Override
    public boolean clear(GraphRuntimeContext context,
                         ScopedStateNamespace namespace,
                         ScopedStateTarget target, String name) {
        String key = requireKey(name);
        return resolve(context, namespace, target).remove(key);
    }

    private static ScopedStateProvider resolve(GraphRuntimeContext context,
                                               ScopedStateNamespace namespace,
                                               ScopedStateTarget target) {
        return ScopedStateProviderResolver.resolve(context, namespace, target);
    }

    private static String requireKey(String name) {
        return name != null ? name : "";
    }

}

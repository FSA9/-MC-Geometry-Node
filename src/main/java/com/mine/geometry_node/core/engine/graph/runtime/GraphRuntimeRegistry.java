package com.mine.geometry_node.core.engine.graph.runtime;

import com.mine.geometry_node.core.engine.graph.GraphKind;
import com.mine.geometry_node.core.engine.runtime.ServerEngineRegistry;
import org.jetbrains.annotations.Nullable;

import java.util.Collection;
import java.util.EnumMap;
import java.util.List;
import java.util.Map;

/**
 * Registry for graph runtimes.
 */
public final class GraphRuntimeRegistry {
    public static final GraphRuntimeRegistry INSTANCE = new GraphRuntimeRegistry();

    private final Map<GraphKind, GraphRuntime> runtimes = new EnumMap<>(GraphKind.class);

    private GraphRuntimeRegistry() {
    }

    public synchronized void register(GraphRuntime runtime) {
        if (runtime == null || runtime.kind() == null || runtime.kind() == GraphKind.UNKNOWN) {
            return;
        }
        GraphRuntime existing = runtimes.get(runtime.kind());
        if (existing == runtime) {
            return;
        }
        if (existing != null && existing != runtime) {
            throw new IllegalStateException("Duplicate graph runtime: " + runtime.kind().id());
        }
        ServerEngineRegistry.INSTANCE.register(runtime);
        runtimes.put(runtime.kind(), runtime);
    }

    @Nullable
    public synchronized GraphRuntime get(GraphKind kind) {
        return runtimes.get(kind);
    }

    public synchronized Collection<GraphRuntime> all() {
        return List.copyOf(runtimes.values());
    }
}

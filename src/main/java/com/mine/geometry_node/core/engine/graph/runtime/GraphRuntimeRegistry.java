package com.mine.geometry_node.core.engine.graph.runtime;

import com.mine.geometry_node.core.engine.graph.GraphKind;
import org.jetbrains.annotations.Nullable;

import java.util.Collection;
import java.util.Collections;
import java.util.EnumMap;
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
        runtime.init();
        runtimes.put(runtime.kind(), runtime);
    }

    @Nullable
    public GraphRuntime get(GraphKind kind) {
        return runtimes.get(kind);
    }

    public Collection<GraphRuntime> all() {
        return Collections.unmodifiableCollection(runtimes.values());
    }
}

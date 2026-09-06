package com.mine.geometry_node.core.engine.graph.runtime;

import com.mine.geometry_node.core.engine.graph.GraphKind;
import com.mine.geometry_node.core.engine.graph.compile.GraphCompiler;
import com.mine.geometry_node.core.engine.graph.compile.artifact.CompiledGraph;
import com.mine.geometry_node.core.engine.runtime.ServerEngineRegistry;

import java.util.EnumMap;
import java.util.Map;
import java.util.Objects;

/**
 * Canonical registration point for a graph runtime and its compiler.
 */
public final class GraphRuntimeRegistry {
    public static final GraphRuntimeRegistry INSTANCE = new GraphRuntimeRegistry();

    private final Map<GraphKind, Registration> registrations = new EnumMap<>(GraphKind.class);

    private GraphRuntimeRegistry() {
    }

    public synchronized void register(GraphRuntime runtime) {
        Objects.requireNonNull(runtime, "runtime");
        GraphKind kind = Objects.requireNonNull(runtime.kind(), "runtime.kind()");
        if (kind == GraphKind.UNKNOWN) {
            throw new IllegalArgumentException("Cannot register a runtime for unknown graph kind");
        }
        GraphCompiler<? extends CompiledGraph> compiler =
                Objects.requireNonNull(runtime.compiler(), "runtime.compiler()");
        if (compiler.runtimeKind() != kind) {
            throw new IllegalArgumentException("Graph runtime and compiler kinds do not match: " + kind.id());
        }

        Registration existing = registrations.get(kind);
        if (existing != null && existing.runtime() == runtime && existing.compiler() == compiler) {
            return;
        }
        if (existing != null) {
            throw new IllegalStateException("Duplicate graph runtime: " + kind.id());
        }

        ServerEngineRegistry.INSTANCE.register(runtime);
        registrations.put(kind, new Registration(runtime, compiler));
    }

    public synchronized GraphCompiler<? extends CompiledGraph> requireCompiler(GraphKind kind) {
        Registration registration = registrations.get(Objects.requireNonNull(kind, "kind"));
        if (registration == null) {
            throw new IllegalStateException("Graph type is registered but not executable yet: " + kind.id());
        }
        return registration.compiler();
    }

    private record Registration(GraphRuntime runtime, GraphCompiler<? extends CompiledGraph> compiler) {
    }
}

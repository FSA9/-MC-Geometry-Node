package com.mine.geometry_node.core.engine.graph.compile;

import org.jetbrains.annotations.Nullable;

import java.util.List;
import java.util.function.Function;

/** Optional artifact contract for validation that requires resolved dependency contents. */
public interface ResolvedGraphDependencyValidator {
    List<ResolvedGraphDependencyDiagnostic> validateResolvedDependencies(
            Function<String, @Nullable CompiledGraph> resolver);
}

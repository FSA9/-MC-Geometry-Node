package com.mine.geometry_node.core.engine.graph.compile;

import com.google.gson.JsonObject;
import com.mine.geometry_node.core.engine.graph.GraphKind;
import com.mine.geometry_node.core.engine.graph.compile.artifact.CompiledGraph;

/** Compiler owned by one runtime family. The supplied JSON document is read-only. */
public interface GraphCompiler<T extends CompiledGraph> {
    GraphKind runtimeKind();

    /** Compiles {@code document} without mutating it or retaining mutable references to it. */
    T compile(GraphCompileContext context, JsonObject document);
}

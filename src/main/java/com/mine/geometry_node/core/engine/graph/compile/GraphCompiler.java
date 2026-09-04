package com.mine.geometry_node.core.engine.graph.compile;

import com.google.gson.JsonObject;
import com.mine.geometry_node.core.engine.graph.GraphKind;
import com.mine.geometry_node.core.engine.graph.compile.artifact.CompiledGraph;

/** Compiler owned by one runtime family. */
public interface GraphCompiler<T extends CompiledGraph> {
    GraphKind runtimeKind();

    T compile(GraphCompileContext context, JsonObject document);
}

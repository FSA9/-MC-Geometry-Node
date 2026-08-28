package com.mine.geometry_node.core.engine.graph.compile.artifact;

import com.mine.geometry_node.core.engine.graph.GraphKind;

/** Immutable runtime-owned artifact exposed through graph storage. */
public interface CompiledGraph {
    String graphTypeId();

    GraphKind runtimeKind();
}

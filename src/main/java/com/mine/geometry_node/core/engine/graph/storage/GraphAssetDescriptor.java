package com.mine.geometry_node.core.engine.graph.storage;

import com.mine.geometry_node.core.engine.graph.GraphKind;
import com.mine.geometry_node.core.engine.graph.GraphType;
import com.mine.geometry_node.core.engine.graph.compile.artifact.CompiledGraph;

import java.util.Objects;

/** Compiled graph plus the canonical identity used by storage and runtimes. */
public record GraphAssetDescriptor(String graphId, GraphType type, CompiledGraph artifact) {
    public GraphAssetDescriptor {
        graphId = Objects.requireNonNull(graphId, "graphId");
        type = Objects.requireNonNull(type, "type");
        artifact = Objects.requireNonNull(artifact, "artifact");
        if (!type.id().equals(artifact.graphTypeId()) || type.runtimeKind() != artifact.runtimeKind()) {
            throw new IllegalArgumentException("Graph descriptor identity does not match its compiled artifact");
        }
    }

    public GraphKind runtimeKind() {
        return type.runtimeKind();
    }
}

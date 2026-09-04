package com.mine.geometry_node.core.engine.graph.storage;

import com.mine.geometry_node.core.engine.graph.GraphKind;
import com.mine.geometry_node.core.engine.graph.GraphType;
import com.mine.geometry_node.core.engine.graph.compile.artifact.CompiledGraph;

import java.util.Objects;

/** Compiled graph plus the canonical identity used by storage and runtimes. */
public record GraphAssetDescriptor(String graphId, GraphType type, CompiledGraph artifact,
                                   String fingerprint) {
    public GraphAssetDescriptor {
        graphId = GraphAssetId.require(graphId);
        type = Objects.requireNonNull(type, "type");
        artifact = Objects.requireNonNull(artifact, "artifact");
        fingerprint = Objects.requireNonNull(fingerprint, "fingerprint");
        if (fingerprint.isBlank()) {
            throw new IllegalArgumentException("Graph fingerprint cannot be empty");
        }
        if (!type.id().equals(artifact.graphTypeId()) || type.runtimeKind() != artifact.runtimeKind()) {
            throw new IllegalArgumentException("Graph descriptor identity does not match its compiled artifact");
        }
    }

    public GraphKind runtimeKind() {
        return type.runtimeKind();
    }

    public boolean hasSameContent(GraphAssetDescriptor other) {
        return other != null
                && graphId.equals(other.graphId)
                && type.id().equals(other.type.id())
                && fingerprint.equals(other.fingerprint);
    }
}

package com.mine.geometry_node.core.engine.graph.compile;

import org.jetbrains.annotations.Nullable;

/** Immutable, graph-family-neutral view of compiled data connections. */
public interface CompiledDataIndex {
    int getNodeCount();

    @Nullable
    String getNodeId(int nodeId);

    String getNodeType(int nodeId);

    int getPortKey(String portName);

    @Nullable
    DataConnectionSource findDataInput(int targetNodeId, String inputPortName);

    @Nullable
    Object getStaticInput(int nodeId, String portName);

    boolean hasPort(int nodeId, String portName);

    record DataConnectionSource(int sourceNodeId, String sourcePortName) {
    }
}

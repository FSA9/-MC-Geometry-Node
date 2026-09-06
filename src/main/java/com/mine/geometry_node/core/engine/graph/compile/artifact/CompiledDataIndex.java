package com.mine.geometry_node.core.engine.graph.compile.artifact;

import com.mine.geometry_node.core.node.definition.port.PortType;
import com.mine.geometry_node.core.node.nodes.BaseNode;
import org.jetbrains.annotations.Nullable;

/** Immutable, graph-family-neutral view of compiled data connections. */
public interface CompiledDataIndex {
    int getNodeCount();

    @Nullable
    String getNodeId(int nodeId);

    String getNodeType(int nodeId);

    @Nullable
    BaseNode getNodeImplementation(int nodeId);

    int getPortKey(String portName);

    @Nullable
    String getPortName(int portKey);

    @Nullable
    DataConnectionSource findDataInput(int targetNodeId, int inputPortKey);

    default @Nullable DataConnectionSource findDataInput(int targetNodeId, String inputPortName) {
        int portKey = getPortKey(inputPortName);
        return portKey >= 0 ? findDataInput(targetNodeId, portKey) : null;
    }

    @Nullable
    Object getStaticInput(int nodeId, int portKey);

    default @Nullable Object getStaticInput(int nodeId, String portName) {
        int portKey = getPortKey(portName);
        return portKey >= 0 ? getStaticInput(nodeId, portKey) : null;
    }

    boolean isDataPassthroughOutput(int nodeId, int portKey);

    default boolean isDataPassthroughOutput(int nodeId, String portName) {
        int portKey = getPortKey(portName);
        return portKey >= 0 && isDataPassthroughOutput(nodeId, portKey);
    }

    boolean hasPort(int nodeId, int portKey);

    default boolean hasPort(int nodeId, String portName) {
        int portKey = getPortKey(portName);
        return portKey >= 0 && hasPort(nodeId, portKey);
    }

    record DataConnectionSource(int sourceNodeId, int sourcePortKey,
                                PortType sourceType, PortType targetType) {
    }
}

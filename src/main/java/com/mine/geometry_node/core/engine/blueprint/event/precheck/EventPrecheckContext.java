package com.mine.geometry_node.core.engine.blueprint.event.precheck;

import com.mine.geometry_node.core.engine.blueprint.runtime.RuntimeGraphIndex;
import com.mine.geometry_node.core.node.port.StandardPorts;
import org.jetbrains.annotations.Nullable;

import java.util.Objects;

public record EventPrecheckContext(String graphId, RuntimeGraphIndex index, int nodeId, String eventType) {
    public EventPrecheckContext {
        graphId = graphId == null ? "" : graphId;
        Objects.requireNonNull(index, "index");
        Objects.requireNonNull(eventType, "eventType");
    }

    @Nullable
    public Object staticInput(String portId) {
        return index.getNodeStaticInput(nodeId, portId);
    }

    @Nullable
    public Object staticInput(StandardPorts port) {
        return staticInput(port.getId());
    }

    public <T> T staticInput(String portId, Class<T> type, T defaultValue) {
        return index.getNodeStaticInput(nodeId, portId, type, defaultValue);
    }

    public <T> T staticInput(StandardPorts port, Class<T> type, T defaultValue) {
        return staticInput(port.getId(), type, defaultValue);
    }
}

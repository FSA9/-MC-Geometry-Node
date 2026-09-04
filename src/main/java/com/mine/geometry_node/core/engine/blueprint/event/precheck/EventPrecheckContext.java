package com.mine.geometry_node.core.engine.blueprint.event.precheck;

import com.mine.geometry_node.core.engine.blueprint.plan.BlueprintPlan;
import com.mine.geometry_node.core.node.definition.port.StandardPorts;
import org.jetbrains.annotations.Nullable;

import java.util.Objects;

public record EventPrecheckContext(String graphId, BlueprintPlan index, int nodeId, String eventType) {
    public EventPrecheckContext {
        graphId = graphId == null ? "" : graphId;
        Objects.requireNonNull(index, "index");
        Objects.requireNonNull(eventType, "eventType");
    }

    @Nullable
    public Object staticInput(String portId) {
        return index.getStaticInput(nodeId, portId);
    }

    @Nullable
    public Object staticInput(StandardPorts port) {
        return staticInput(port.getId());
    }

    public <T> T staticInput(String portId, Class<T> type, T defaultValue) {
        return index.getStaticInput(nodeId, portId, type, defaultValue);
    }

    public <T> T staticInput(StandardPorts port, Class<T> type, T defaultValue) {
        return staticInput(port.getId(), type, defaultValue);
    }
}

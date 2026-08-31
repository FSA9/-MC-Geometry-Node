package com.mine.geometry_node.core.node.nodes.events;

import com.mine.geometry_node.core.engine.blueprint.runtime.ExecutionContext;
import com.mine.geometry_node.core.engine.blueprint.runtime.ExecutionResult;
import com.mine.geometry_node.core.node.nodes.BaseNode;
import com.mine.geometry_node.core.node.definition.port.StandardPorts;
import org.jetbrains.annotations.Nullable;

public abstract class BaseEventNode extends BaseNode {

    @Override
    public ExecutionResult execute(ExecutionContext context) {
        return next(StandardPorts.FLOW_OUT.getId());
    }

    @Override
    @Nullable
    public Object compute(ExecutionContext context, String portName) {
        Object eventValue = context.getEventData(portName);
        if (eventValue != null) {
            return eventValue;
        }
        if (StandardPorts.ENTITY.getId().equals(portName)) {
            return context.getEntity();
        }
        return null;
    }
}

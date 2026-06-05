package com.mine.geometry_node.core.node.nodes.events;

import com.mine.geometry_node.core.engine.blueprint.runtime.ExecutionContext;
import com.mine.geometry_node.core.engine.blueprint.runtime.ExecutionResult;
import com.mine.geometry_node.core.node.nodes.BaseNode;
import com.mine.geometry_node.core.node.port.StandardPorts;
import org.jetbrains.annotations.Nullable;

public abstract class BaseEventNode extends BaseNode {

    @Override
    public ExecutionResult execute(ExecutionContext context) {
        return next(StandardPorts.FLOW_OUT.getId());
    }

    @Override
    @Nullable
    public Object compute(ExecutionContext context, String portName) {
        return context.getEventData(portName);
    }
}
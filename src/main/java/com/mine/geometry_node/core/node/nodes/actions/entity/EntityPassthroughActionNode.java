package com.mine.geometry_node.core.node.nodes.actions.entity;

import com.mine.geometry_node.core.engine.blueprint.runtime.ExecutionContext;
import com.mine.geometry_node.core.node.nodes.BaseNode;
import com.mine.geometry_node.core.node.definition.port.StandardPorts;

abstract class EntityPassthroughActionNode extends BaseNode {

    @Override
    public Object compute(ExecutionContext context, String portName) {
        if (StandardPorts.ENTITY.getId().equals(portName)) {
            return getRawInput(context, StandardPorts.ENTITY.getId());
        }
        return null;
    }
}

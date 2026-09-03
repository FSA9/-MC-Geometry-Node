package com.mine.geometry_node.core.node.nodes.actions.entity;

import com.mine.geometry_node.core.engine.graph.data.GraphDataContext;
import com.mine.geometry_node.core.node.nodes.BaseNode;
import com.mine.geometry_node.core.node.definition.port.StandardPorts;

abstract class EntityPassthroughActionNode extends BaseNode {

    @Override
    public Object compute(GraphDataContext context, String portName) {
        if (StandardPorts.ENTITY.getId().equals(portName)) {
            return getRawInput(context, StandardPorts.ENTITY.getId());
        }
        return null;
    }
}

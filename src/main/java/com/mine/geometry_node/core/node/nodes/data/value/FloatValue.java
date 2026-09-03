package com.mine.geometry_node.core.node.nodes.data.value;

import com.mine.geometry_node.core.engine.graph.data.GraphDataContext;
import com.mine.geometry_node.core.node.nodes.BaseNode;
import com.mine.geometry_node.core.node.definition.node.NodeDef;
import com.mine.geometry_node.core.node.definition.node.NodeType;
import com.mine.geometry_node.core.node.definition.port.StandardPorts;
import com.mine.geometry_node.core.node.definition.port.UIHint;
import net.minecraft.network.chat.Component;

public class FloatValue extends BaseNode {

    public static final String TYPE_ID = "float_value";

    @Override
    public NodeDef getDefaultDefinition() {
        return NodeDef.builder(TYPE_ID, NodeType.DATA, Component.translatable("geometry_node.node.float_value"))
                .addPassthroughInput(StandardPorts.FLOAT.toInput(0.0f), UIHint.INPUT, null, null)
                .build();
    }

    @Override
    public Object compute(GraphDataContext context, String portName) {
        if (StandardPorts.FLOAT.getId().equals(portName)) {
            return getInput(context, StandardPorts.FLOAT.getId(), Float.class);
        }
        return null;
    }
}

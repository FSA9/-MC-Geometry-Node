package com.mine.geometry_node.core.node.nodes.data.value;

import com.mine.geometry_node.core.node.nodes.BaseNode;
import com.mine.geometry_node.core.node.definition.node.NodeDef;
import com.mine.geometry_node.core.node.definition.node.NodeType;
import com.mine.geometry_node.core.node.definition.port.StandardPorts;
import com.mine.geometry_node.core.node.definition.port.UIHint;
import net.minecraft.network.chat.Component;

public class IntValue extends BaseNode {

    public static final String TYPE_ID = "int_value";

    @Override
    public NodeDef getDefaultDefinition() {
        return NodeDef.builder(TYPE_ID, NodeType.DATA, Component.translatable("geometry_node.node.int_value"))
                .addPassthroughInput(StandardPorts.INT.toInput(0), UIHint.INPUT, null, null)
                .build();
    }
}

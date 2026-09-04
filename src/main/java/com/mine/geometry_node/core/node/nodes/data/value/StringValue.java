package com.mine.geometry_node.core.node.nodes.data.value;

import com.mine.geometry_node.core.node.nodes.BaseNode;
import com.mine.geometry_node.core.node.definition.node.NodeDef;
import com.mine.geometry_node.core.node.definition.node.NodeType;
import com.mine.geometry_node.core.node.definition.port.StandardPorts;
import com.mine.geometry_node.core.node.definition.port.UIHint;
import net.minecraft.network.chat.Component;

public class StringValue extends BaseNode {

    public static final String TYPE_ID = "string_value";

    @Override
    public NodeDef getDefaultDefinition() {
        return NodeDef.builder(TYPE_ID, NodeType.DATA, Component.translatable("geometry_node.node.string_value"))
                .addPassthroughInput(StandardPorts.STRING.toInput(""), UIHint.INPUT, null, null)
                .build();
    }
}

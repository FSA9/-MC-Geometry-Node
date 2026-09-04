package com.mine.geometry_node.core.node.nodes.data.value;

import com.mine.geometry_node.core.node.nodes.BaseNode;
import com.mine.geometry_node.core.node.definition.node.NodeDef;
import com.mine.geometry_node.core.node.definition.node.NodeType;
import com.mine.geometry_node.core.node.definition.port.StandardPorts;
import com.mine.geometry_node.core.node.definition.port.UIHint;
import net.minecraft.network.chat.Component;

public class BoolValue extends BaseNode {

    public static final String TYPE_ID = "bool_value";

    @Override
    public NodeDef getDefaultDefinition() {
        return NodeDef.builder(TYPE_ID, NodeType.DATA, Component.translatable("geometry_node.node.bool_value"))
                .addPassthroughInput(StandardPorts.BOOL.toInput(false), UIHint.CHECKBOX, null, null)
                .build();
    }
}

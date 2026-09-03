package com.mine.geometry_node.core.node.nodes.data.container;

import com.mine.geometry_node.core.engine.graph.data.GraphDataContext;
import com.mine.geometry_node.core.node.definition.node.NodeComment;
import com.mine.geometry_node.core.node.nodes.BaseNode;
import com.mine.geometry_node.core.node.definition.node.NodeDef;
import com.mine.geometry_node.core.node.definition.node.NodeType;
import com.mine.geometry_node.core.node.definition.port.PortRow;
import com.mine.geometry_node.core.node.definition.port.StandardPorts;
import com.mine.geometry_node.core.node.definition.port.UIHint;
import com.mine.geometry_node.core.node.definition.port.PortType;
import net.minecraft.network.chat.Component;

public class GetInputDataType extends BaseNode {

    public static final String TYPE_ID = "get_input_data_type";

    @Override
    public NodeDef getDefaultDefinition() {
        return NodeDef.builder(TYPE_ID, NodeType.DATA, Component.translatable("geometry_node.node.get_input_data_type"))
                .addRow(new PortRow(null, StandardPorts.TYPE.toOutput(), UIHint.DEFAULT, null, null))
                .addPassthroughInput(StandardPorts.ANY_VALUE.toInput(), UIHint.DEFAULT, null, null)
                .comment(NodeComment.builder(TYPE_ID)
                        .text("summary")
                        .text("unsupported")
                        .build())
                .build();
    }

    @Override
    public Object compute(GraphDataContext context, String portName) {
        if (!StandardPorts.TYPE.getId().equals(portName)) return null;

        Object rawValue = getRawInput(context, StandardPorts.ANY_VALUE.getId());

        PortType type = PortType.getTypeOf(rawValue);

        return type.name();
    }
}

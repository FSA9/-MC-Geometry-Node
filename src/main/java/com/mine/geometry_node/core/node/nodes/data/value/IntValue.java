package com.mine.geometry_node.core.node.nodes.data.value;

import com.mine.geometry_node.core.execution.ExecutionContext;
import com.mine.geometry_node.core.node.nodes.BaseNode;
import com.mine.geometry_node.core.node.nodes.NodeDef;
import com.mine.geometry_node.core.node.nodes.NodeType;
import com.mine.geometry_node.core.node.port.PortRow;
import com.mine.geometry_node.core.node.port.StandardPorts;
import com.mine.geometry_node.core.node.port.UIHint;
import net.minecraft.network.chat.Component;

public class IntValue extends BaseNode {

    public static final String TYPE_ID = "int_value";

    @Override
    public NodeDef getDefaultDefinition() {
        return NodeDef.builder(TYPE_ID, NodeType.DATA, Component.translatable("geometry_node.node.int_value"))
                .addRow(new PortRow(StandardPorts.INT.toInput(0), StandardPorts.INT.toOutput(), UIHint.INPUT, null, null))
                .build();
    }

    @Override
    public Object compute(ExecutionContext context, String portName) {
        if (StandardPorts.INT.getId().equals(portName)) {
            return getInput(context, StandardPorts.INT.getId(), Integer.class);
        }
        return null;
    }
}
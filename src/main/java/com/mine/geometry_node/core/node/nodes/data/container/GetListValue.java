package com.mine.geometry_node.core.node.nodes.data.container;

import com.mine.geometry_node.core.execution.ExecutionContext;
import com.mine.geometry_node.core.node.nodes.BaseNode;
import com.mine.geometry_node.core.node.nodes.NodeDef;
import com.mine.geometry_node.core.node.nodes.NodeType;
import com.mine.geometry_node.core.node.port.PortRow;
import com.mine.geometry_node.core.node.port.StandardPorts;
import com.mine.geometry_node.core.node.port.UIHint;
import net.minecraft.network.chat.Component;

import java.util.List;

public class GetListValue extends BaseNode {

    public static final String TYPE_ID = "get_list_value";

    @Override
    public NodeDef getDefaultDefinition() {
        return NodeDef.builder(TYPE_ID, NodeType.DATA, Component.translatable("geometry_node.node.get_list_value"))
                .addRow(new PortRow(StandardPorts.LIST.toInput(), StandardPorts.ANY_VALUE.toOutput(), UIHint.DEFAULT, null, null))
                .addRow(new PortRow(StandardPorts.INT.toInput(0), null, UIHint.INPUT, null, null))
                .build();
    }

    @Override
    public Object compute(ExecutionContext context, String portName) {
        if (!StandardPorts.ANY_VALUE.getId().equals(portName)) return null;

        List<Object> list = getInputList(context, StandardPorts.LIST.getId(), Object.class);
        Integer index = getInput(context, StandardPorts.INT.getId(), Integer.class);

        if (list != null && !list.isEmpty() && index != null) {
            if (index >= 0 && index < list.size()) {
                return list.get(index);
            } else {
                System.err.println("[GeometryNode] GetListValue illegal index: try to use " + index + " ，but len(list) is " + list.size());
            }
        }

        return null;
    }
}
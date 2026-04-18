package com.mine.geometry_node.core.node.nodes.data;

import com.mine.geometry_node.core.execution.ExecutionContext;
import com.mine.geometry_node.core.node.nodes.*;
import com.mine.geometry_node.core.node.port.PortRow;
import com.mine.geometry_node.core.node.port.StandardPorts;
import com.mine.geometry_node.core.node.port.UIHint;
import net.minecraft.network.chat.Component;

import java.util.List;

public class GetListLen extends BaseNode {

    public static final String TYPE_ID = "get_list_len";

    @Override
    public NodeDef getDefaultDefinition() {
        return NodeDef.builder(TYPE_ID, NodeType.DATA, Component.translatable("geometry_node.node.get_list_len"))
                .addRow(new PortRow(
                        StandardPorts.LIST.toInput(),
                        StandardPorts.INT.toOutput(),
                        UIHint.DEFAULT, null, null
                ))
                .build();
    }

    @Override
    public Object compute(ExecutionContext context, String portName) {
        if (!StandardPorts.INT.getId().equals(portName)) {
            return null;
        }

        List<Object> list = getInputList(context, StandardPorts.LIST.getId(), Object.class);

        return list.size();
    }
}
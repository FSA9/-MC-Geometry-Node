package com.mine.geometry_node.core.node.nodes.data.container;

import com.mine.geometry_node.core.engine.blueprint.runtime.ExecutionContext;
import com.mine.geometry_node.core.node.nodes.BaseNode;
import com.mine.geometry_node.core.node.nodes.NodeDef;
import com.mine.geometry_node.core.node.nodes.NodeType;
import com.mine.geometry_node.core.node.port.PortRow;
import com.mine.geometry_node.core.node.port.StandardPorts;
import com.mine.geometry_node.core.node.port.UIHint;
import com.mine.geometry_node.core.node.port.PortType;
import net.minecraft.network.chat.Component;

public class GetInputDataType extends BaseNode {

    public static final String TYPE_ID = "get_input_data_type";

    @Override
    public NodeDef getDefaultDefinition() {
        return NodeDef.builder(TYPE_ID, NodeType.DATA, Component.translatable("geometry_node.node.get_input_data_type"))
                // 左侧接收任意数据 (ANY)，右侧输出该数据的类型名称 (STRING)
                .addRow(new PortRow(StandardPorts.ANY_VALUE.toInput(), StandardPorts.TYPE.toOutput(), UIHint.DEFAULT, null, null))
                .build();
    }

    @Override
    public Object compute(ExecutionContext context, String portName) {
        if (!StandardPorts.TYPE.getId().equals(portName)) return null;

        Object rawValue = getRawInput(context, StandardPorts.ANY_VALUE.getId());

        PortType type = PortType.getTypeOf(rawValue);

        return type.name();
    }
}
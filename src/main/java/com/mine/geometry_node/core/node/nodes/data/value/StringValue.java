package com.mine.geometry_node.core.node.nodes.data.value;

import com.mine.geometry_node.core.execution.ExecutionContext;
import com.mine.geometry_node.core.node.nodes.BaseNode;
import com.mine.geometry_node.core.node.nodes.NodeDef;
import com.mine.geometry_node.core.node.nodes.NodeType;
import com.mine.geometry_node.core.node.port.PortRow;
import com.mine.geometry_node.core.node.port.StandardPorts;
import com.mine.geometry_node.core.node.port.UIHint;
import net.minecraft.network.chat.Component;

public class StringValue extends BaseNode {

    public static final String TYPE_ID = "string_value";

    @Override
    public NodeDef getDefaultDefinition() {
        return NodeDef.builder(TYPE_ID, NodeType.DATA, Component.translatable("geometry_node.node.string_value"))
                // 左右合并在一行，UIHint.INPUT 会在左侧端口旁生成文本框
                .addRow(new PortRow(StandardPorts.STRING.toInput(""), StandardPorts.STRING.toOutput(), UIHint.INPUT, null, null))
                .build();
    }

    @Override
    public Object compute(ExecutionContext context, String portName) {
        if (StandardPorts.STRING.getId().equals(portName)) {
            // getInput 会自动判断：如果左边连了线，就取线上的值；没连线，就取玩家在输入框敲的值
            return getInput(context, StandardPorts.STRING.getId(), String.class);
        }
        return null;
    }
}
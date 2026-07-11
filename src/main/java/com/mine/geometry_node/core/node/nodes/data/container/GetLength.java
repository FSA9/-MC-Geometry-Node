package com.mine.geometry_node.core.node.nodes.data.container;

import com.mine.geometry_node.core.engine.blueprint.runtime.ExecutionContext;
import com.mine.geometry_node.core.node.nodes.BaseNode;
import com.mine.geometry_node.core.node.nodes.NodeDef;
import com.mine.geometry_node.core.node.nodes.NodeType;
import com.mine.geometry_node.core.node.port.PortRow;
import com.mine.geometry_node.core.node.port.StandardPorts;
import com.mine.geometry_node.core.node.port.UIHint;
import com.mine.geometry_node.core.node.value.geometry.GeometryValue;
import net.minecraft.network.chat.Component;

import java.util.List;
import java.util.Map;

public class GetLength extends BaseNode {

    public static final String TYPE_ID = "get_length";

    @Override
    public NodeDef getDefaultDefinition() {
        return NodeDef.builder(TYPE_ID, NodeType.DATA, Component.translatable("geometry_node.node.get_length"))
                .addRow(new PortRow(
                        StandardPorts.ANY_VALUE.toInput(),
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

        Object rawValue = getRawInput(context, StandardPorts.ANY_VALUE.getId());

        if (rawValue == null) return 0;

        if (rawValue instanceof List<?> list) {
            return list.size(); // 列表
        }
        else if (rawValue instanceof Map<?, ?> dict) {
            return dict.size(); // 字典
        }
        else if (rawValue instanceof String str) {
            return str.length(); // 字符串
        }
        else if (rawValue instanceof Object[] arr) {
            return arr.length; // 原生数组
        }
        else if (rawValue instanceof GeometryValue geometry) {
            return geometry.primitiveCount();
        }

        System.err.println("[GetLength] Warning: Unsupported type: " + rawValue.getClass().getSimpleName());
        return 0;
    }
}

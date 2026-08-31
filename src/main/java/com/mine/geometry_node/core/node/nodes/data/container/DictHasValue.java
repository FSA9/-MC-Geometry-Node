package com.mine.geometry_node.core.node.nodes.data.container;

import com.mine.geometry_node.core.engine.blueprint.runtime.ExecutionContext;
import com.mine.geometry_node.core.node.nodes.BaseNode;
import com.mine.geometry_node.core.node.definition.node.NodeDef;
import com.mine.geometry_node.core.node.definition.node.NodeType;
import com.mine.geometry_node.core.node.definition.port.PortRow;
import com.mine.geometry_node.core.node.definition.port.StandardPorts;
import com.mine.geometry_node.core.node.definition.port.UIHint;
import net.minecraft.network.chat.Component;

import java.util.Map;

public class DictHasValue extends BaseNode {

    public static final String TYPE_ID = "dict_has_value";

    @Override
    public NodeDef getDefaultDefinition() {
        return NodeDef.builder(TYPE_ID, NodeType.DATA, Component.translatable("geometry_node.node.dict_has_value"))
                // 第一行：左侧输入字典 DICT，右侧输出 BOOL
                .addRow(new PortRow(StandardPorts.DICT.toInput(), StandardPorts.BOOL.toOutput(), UIHint.DEFAULT, null, null))
                // 第二行：左侧输入任意值 ANY_VALUE
                .addRow(new PortRow(StandardPorts.ANY_VALUE.toInput(), null, UIHint.DEFAULT, null, null))
                .build();
    }

    @Override
    public Object compute(ExecutionContext context, String portName) {
        if (!StandardPorts.BOOL.getId().equals(portName)) return null;

        Map<String, Object> dict = getInputDict(context, StandardPorts.DICT.getId());
        Object targetValue = getRawInput(context, StandardPorts.ANY_VALUE.getId());

        if (dict != null && targetValue != null && !dict.isEmpty()) {
            // containsValue 同样享受 Java 原生的严格类型校验
            return dict.containsValue(targetValue);
        }

        return false;
    }
}
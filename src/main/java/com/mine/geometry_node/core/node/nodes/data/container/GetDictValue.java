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

public class GetDictValue extends BaseNode {

    public static final String TYPE_ID = "get_dict_value";

    @Override
    public NodeDef getDefaultDefinition() {
        return NodeDef.builder(TYPE_ID, NodeType.DATA, Component.translatable("geometry_node.node.get_dict_value"))
                .addRow(new PortRow(null, StandardPorts.ANY_VALUE.toOutput(), UIHint.DEFAULT, null, null))
                .addPassthroughInput(StandardPorts.DICT.toInput(), UIHint.DEFAULT, null, null)
                .addPassthroughInput(StandardPorts.KEY.toInput(""), UIHint.INPUT, null, null)
                .build();
    }

    @Override
    public Object compute(ExecutionContext context, String portName) {
        // 只有当下游请求 ANY 端口的数据时才进行计算
        if (!StandardPorts.ANY_VALUE.getId().equals(portName)) return null;

        // 1. 获取连入的字典
        Map<String, Object> dict = getInputDict(context, StandardPorts.DICT.getId());

        // 2. 获取目标 Key (可能来自手打输入框，也可能来自上游字符串节点的连线)
        String key = getInput(context, StandardPorts.KEY.getId(), String.class);

        // 3. 提取并返回 Raw Object
        if (dict != null && key != null && !key.trim().isEmpty()) {
            return dict.get(key.trim());
        }

        return null;
    }
}

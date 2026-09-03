package com.mine.geometry_node.core.node.nodes.data.container;

import com.mine.geometry_node.core.engine.graph.data.GraphDataContext;
import com.mine.geometry_node.core.engine.graph.value.GraphValueSnapshot;
import com.mine.geometry_node.core.node.nodes.BaseNode;
import com.mine.geometry_node.core.node.definition.node.NodeDef;
import com.mine.geometry_node.core.node.definition.node.NodeType;
import com.mine.geometry_node.core.node.definition.port.PortRow;
import com.mine.geometry_node.core.node.definition.port.StandardPorts;
import com.mine.geometry_node.core.node.definition.port.UIHint;
import net.minecraft.network.chat.Component;

import java.util.List;

public class ListHasValue extends BaseNode {

    public static final String TYPE_ID = "list_has_value";

    @Override
    public NodeDef getDefaultDefinition() {
        return NodeDef.builder(TYPE_ID, NodeType.DATA, Component.translatable("geometry_node.node.list_has_value"))
                .addRow(new PortRow(null, StandardPorts.BOOL.toOutput(), UIHint.DEFAULT, null, null))
                .addRow(new PortRow(null, StandardPorts.INT.toOutput(), UIHint.DEFAULT, null, null))
                .addPassthroughInput(StandardPorts.LIST.toInput(), UIHint.DEFAULT, null, null)
                .addPassthroughInput(StandardPorts.ANY_VALUE.toInput(), UIHint.DEFAULT, null, null)
                .build();
    }

    @Override
    public Object compute(GraphDataContext context, String portName) {
        // 1. 获取基础列表和要寻找的纯正（Raw）Java对象
        List<Object> list = getInputList(context, StandardPorts.LIST.getId(), Object.class);
        Object targetValue = getRawInput(context, StandardPorts.ANY_VALUE.getId());

        int index = -1;
        if (list != null && targetValue != null) {
            for (int candidate = 0; candidate < list.size(); candidate++) {
                if (GraphValueSnapshot.equivalent(list.get(candidate), targetValue)) {
                    index = candidate;
                    break;
                }
            }
        }

        // 2. 根据下游请求的端口分别返回数据
        if (StandardPorts.BOOL.getId().equals(portName)) {
            return index != -1; // 找到了就是 true，否则 false
        }

        if (StandardPorts.INT.getId().equals(portName)) {
            return index; // 返回具体下标，没找到返回 -1
        }

        return null;
    }
}

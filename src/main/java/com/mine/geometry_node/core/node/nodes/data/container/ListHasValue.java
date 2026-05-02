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

public class ListHasValue extends BaseNode {

    public static final String TYPE_ID = "list_has_value";

    @Override
    public NodeDef getDefaultDefinition() {
        return NodeDef.builder(TYPE_ID, NodeType.DATA, Component.translatable("geometry_node.node.list_has_value"))
                // 第一行：左侧输入目标 LIST，右侧输出是否存在 BOOL
                .addRow(new PortRow(StandardPorts.LIST.toInput(), StandardPorts.BOOL.toOutput(), UIHint.DEFAULT, null, null))
                // 第二行：左侧输入要查询的任意值 ANY，右侧输出该值的下标 INT
                .addRow(new PortRow(StandardPorts.ANY_VALUE.toInput(), StandardPorts.INT.toOutput(), UIHint.DEFAULT, null, null))
                .build();
    }

    @Override
    public Object compute(ExecutionContext context, String portName) {
        // 1. 获取基础列表和要寻找的纯正（Raw）Java对象
        List<Object> list = getInputList(context, StandardPorts.LIST.getId(), Object.class);
        Object targetValue = getRawInput(context, StandardPorts.ANY_VALUE.getId());

        int index = -1;
        if (list != null && targetValue != null) {
            // indexOf 底层自带严格的类型校验和 .equals() 判断
            index = list.indexOf(targetValue);
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
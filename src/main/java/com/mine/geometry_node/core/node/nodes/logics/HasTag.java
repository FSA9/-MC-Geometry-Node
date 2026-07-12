package com.mine.geometry_node.core.node.nodes.logics;

import com.mine.geometry_node.core.engine.blueprint.runtime.ExecutionContext;
import com.mine.geometry_node.core.node.nodes.BaseNode;
import com.mine.geometry_node.core.node.nodes.NodeDef;
import com.mine.geometry_node.core.node.nodes.NodeType;
import com.mine.geometry_node.core.node.port.PortRow;
import com.mine.geometry_node.core.node.port.StandardPorts;
import com.mine.geometry_node.core.node.port.UIHint;
import net.minecraft.network.chat.Component;

public class HasTag extends BaseNode {

    public static final String TYPE_ID = "has_tag";

    @Override
    public NodeDef getDefaultDefinition() {
        String comment = """
                判断一个值是否属于指定注册表 tag。
                支持物品栈、物品、方块状态、方块、实体/实体类型、伤害来源和注册 ID 字符串。
                tag 支持 namespace:path 和 #namespace:path 两种写法。""";

        return NodeDef.builder(TYPE_ID, NodeType.LOGIC, Component.translatable("geometry_node.node.has_tag"))
                .comment(comment)
                .addRow(new PortRow(StandardPorts.ANY_VALUE.toInput(), StandardPorts.BOOL.toOutput(), UIHint.DEFAULT, null, null))
                .addRow(new PortRow(StandardPorts.TAG.toInput(), null, UIHint.INPUT, null, null))
                .build();
    }

    @Override
    public Object compute(ExecutionContext context, String portName) {
        if (!StandardPorts.BOOL.getId().equals(portName)) {
            return null;
        }

        Object value = getRawInput(context, StandardPorts.ANY_VALUE.getId());
        String tag = getInput(context, StandardPorts.TAG.getId(), String.class);
        return _ValueTagSupport.hasTag(value, tag, context);
    }
}

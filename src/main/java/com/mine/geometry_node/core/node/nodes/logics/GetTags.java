package com.mine.geometry_node.core.node.nodes.logics;

import com.mine.geometry_node.core.engine.blueprint.runtime.ExecutionContext;
import com.mine.geometry_node.core.node.nodes.BaseNode;
import com.mine.geometry_node.core.node.nodes.NodeDef;
import com.mine.geometry_node.core.node.nodes.NodeType;
import com.mine.geometry_node.core.node.port.PortDef;
import com.mine.geometry_node.core.node.port.PortRow;
import com.mine.geometry_node.core.node.port.PortType;
import com.mine.geometry_node.core.node.port.StandardPorts;
import com.mine.geometry_node.core.node.port.UIHint;
import com.mine.geometry_node.core.node.util.ValueTagUtils;
import net.minecraft.network.chat.Component;

public class GetTags extends BaseNode {

    public static final String TYPE_ID = "get_tags";
    private static final String TAGS = "tags";

    @Override
    public NodeDef getDefaultDefinition() {
        String comment = """
                获取一个值拥有的注册表 tag 列表。
                支持物品栈、物品、方块状态、方块、实体/实体类型、伤害来源和注册 ID 字符串。
                输出格式为 #namespace:path，可直接用于 Contain 或 HasTag。""";

        return NodeDef.builder(TYPE_ID, NodeType.LOGIC, Component.translatable("geometry_node.node.get_tags"))
                .comment(comment)
                .addRow(new PortRow(
                        StandardPorts.ANY_VALUE.toInput(),
                        PortDef.create(TAGS, "geometry_node.port.tags", PortType.LIST),
                        UIHint.DEFAULT, null, null
                ))
                .build();
    }

    @Override
    public Object compute(ExecutionContext context, String portName) {
        if (!TAGS.equals(portName)) {
            return null;
        }

        Object value = getRawInput(context, StandardPorts.ANY_VALUE.getId());
        return ValueTagUtils.tags(value, context);
    }
}

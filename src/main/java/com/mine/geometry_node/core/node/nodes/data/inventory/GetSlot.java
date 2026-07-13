package com.mine.geometry_node.core.node.nodes.data.inventory;

import com.mine.geometry_node.core.engine.blueprint.runtime.ExecutionContext;
import com.mine.geometry_node.core.node.nodes.BaseNode;
import com.mine.geometry_node.core.node.nodes.NodeDef;
import com.mine.geometry_node.core.node.nodes.NodeType;
import com.mine.geometry_node.core.node.port.PortRow;
import com.mine.geometry_node.core.node.port.StandardPorts;
import com.mine.geometry_node.core.node.port.UIHint;
import com.mine.geometry_node.core.node.value.SlotRef;
import net.minecraft.network.chat.Component;

public class GetSlot extends BaseNode {
    public static final String TYPE_ID = "get_slot";

    @Override
    public NodeDef getDefaultDefinition() {
        String comment = """
                创建一个槽位引用。
                槽位引用只描述位置，不包含物品内容。
                运行时会根据目标实体和 space 解析玩家背包、装备栏、容器或实体物品能力。""";

        return NodeDef.builder(TYPE_ID, NodeType.DATA, Component.translatable("geometry_node.node.get_slot"))
                .comment(comment)
                .addRow(new PortRow(null, StandardPorts.SLOT.toOutput(), UIHint.DEFAULT, null, null))
                .addRow(new PortRow(
                        StandardPorts.SLOT.toInput(SlotRef.DEFAULT.serialize()).hiddenPin(),
                        null,
                        UIHint.SLOT_REF,
                        null,
                        null
                ))
                .build();
    }

    @Override
    public Object compute(ExecutionContext context, String portName) {
        if (!StandardPorts.SLOT.getId().equals(portName)) {
            return null;
        }
        SlotRef slotRef = getInput(context, StandardPorts.SLOT.getId(), SlotRef.class);
        return slotRef != null ? slotRef : SlotRef.DEFAULT;
    }
}

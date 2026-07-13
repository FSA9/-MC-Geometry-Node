package com.mine.geometry_node.core.node.nodes.data.inventory;

import com.mine.geometry_node.core.engine.blueprint.runtime.ExecutionContext;
import com.mine.geometry_node.core.node.nodes.BaseNode;
import com.mine.geometry_node.core.node.nodes.NodeDef;
import com.mine.geometry_node.core.node.nodes.NodeType;
import com.mine.geometry_node.core.node.port.PortRow;
import com.mine.geometry_node.core.node.port.StandardPorts;
import com.mine.geometry_node.core.node.port.UIHint;
import com.mine.geometry_node.core.node.util.SlotAccessUtils;
import com.mine.geometry_node.core.node.value.SlotRef;
import net.minecraft.network.chat.Component;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.item.ItemStack;

public class GetSlotItem extends BaseNode {
    public static final String TYPE_ID = "get_slot_item";

    @Override
    public NodeDef getDefaultDefinition() {
        String comment = """
                读取目标实体指定槽位中的物品栈。
                输入 SlotRef 表示位置，输出 ItemStack 表示槽位内容。
                Bool 输出表示槽位中是否有物品。""";

        return NodeDef.builder(TYPE_ID, NodeType.DATA, Component.translatable("geometry_node.node.get_slot_item"))
                .comment(comment)
                .addRow(new PortRow(StandardPorts.ENTITY.toInput(), StandardPorts.ITEM_STACK.toOutput(), UIHint.DEFAULT, null, null))
                .addRow(new PortRow(StandardPorts.SLOT.toInput(SlotRef.DEFAULT.serialize()), null, UIHint.SLOT_REF, null, null))
                .addRow(new PortRow(null, StandardPorts.BOOL.toOutput(), UIHint.DEFAULT, null, null))
                .addRow(new PortRow(null, StandardPorts.COUNT.toOutput(), UIHint.DEFAULT, null, null))
                .build();
    }

    @Override
    public Object compute(ExecutionContext context, String portName) {
        Entity entity = getInput(context, StandardPorts.ENTITY.getId(), Entity.class);
        SlotRef slotRef = getInput(context, StandardPorts.SLOT.getId(), SlotRef.class);
        ItemStack stack = SlotAccessUtils.getItem(entity, slotRef != null ? slotRef : SlotRef.DEFAULT);

        if (StandardPorts.ITEM_STACK.getId().equals(portName)) {
            return stack;
        }
        if (StandardPorts.BOOL.getId().equals(portName)) {
            return !stack.isEmpty();
        }
        if (StandardPorts.COUNT.getId().equals(portName)) {
            return stack.getCount();
        }
        return null;
    }
}

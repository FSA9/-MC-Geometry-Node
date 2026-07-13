package com.mine.geometry_node.core.node.nodes.actions.inventory;

import com.mine.geometry_node.core.engine.blueprint.runtime.ExecutionContext;
import com.mine.geometry_node.core.engine.blueprint.runtime.ExecutionResult;
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

public class DropSlotItem extends BaseNode {
    public static final String TYPE_ID = "drop_slot_item";

    @Override
    public NodeDef getDefaultDefinition() {
        String comment = """
                从指定槽位取出物品并掉落到目标实体位置。
                count 小于等于 0 时丢出整个槽位。
                输出 ItemStack 为实际掉落的物品栈。""";

        return NodeDef.builder(TYPE_ID, NodeType.ACTION, Component.translatable("geometry_node.node.drop_slot_item"))
                .comment(comment)
                .addRow(new PortRow(StandardPorts.FLOW_IN.toExec(), StandardPorts.FLOW_OUT.toExec(), UIHint.DEFAULT, null, null))
                .addRow(new PortRow(StandardPorts.ENTITY.toInput(), StandardPorts.ITEM_STACK.toOutput(), UIHint.DEFAULT, null, null))
                .addRow(new PortRow(StandardPorts.SLOT.toInput(SlotRef.DEFAULT.serialize()), null, UIHint.SLOT_REF, null, null))
                .addRow(new PortRow(StandardPorts.COUNT.toInput(1), null, UIHint.INPUT, null, null))
                .build();
    }

    @Override
    public ExecutionResult execute(ExecutionContext context) {
        Entity entity = getInput(context, StandardPorts.ENTITY.getId(), Entity.class);
        SlotRef slotRef = getInput(context, StandardPorts.SLOT.getId(), SlotRef.class);
        Integer count = getInput(context, StandardPorts.COUNT.getId(), Integer.class);
        ItemStack dropped = SlotAccessUtils.extractItem(entity, slotRef != null ? slotRef : SlotRef.DEFAULT, count != null ? count : 1);
        if (!dropped.isEmpty()) {
            SlotAccessUtils.dropItem(entity, dropped);
        }
        context.setTempData(StandardPorts.ITEM_STACK.getId(), dropped);
        return next(StandardPorts.FLOW_OUT.getId());
    }

    @Override
    public Object compute(ExecutionContext context, String portName) {
        if (StandardPorts.ITEM_STACK.getId().equals(portName)) {
            return context.getTempData(StandardPorts.ITEM_STACK.getId());
        }
        return null;
    }
}

package com.mine.geometry_node.core.node.nodes.data.inventory;

import com.mine.geometry_node.core.engine.graph.data.GraphDataContext;
import com.mine.geometry_node.core.node.NodeComment;
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
        return NodeDef.builder(TYPE_ID, NodeType.DATA, Component.translatable("geometry_node.node.get_slot_item"))
                .comment(NodeComment.builder(TYPE_ID)
                        .text("summary")
                        .output(StandardPorts.ITEM_STACK, "item_stack")
                        .output(StandardPorts.BOOL, "bool")
                        .output(StandardPorts.COUNT, "count")
                        .input(StandardPorts.ENTITY, "entity")
                        .input(StandardPorts.SLOT, "slot")
                        .build())
                .addRow(new PortRow(StandardPorts.ENTITY.toInput(), StandardPorts.ITEM_STACK.toOutput(), UIHint.DEFAULT, null, null))
                .addRow(new PortRow(StandardPorts.SLOT.toInput(SlotRef.DEFAULT.serialize()), null, UIHint.SLOT_REF, null, null))
                .addRow(new PortRow(null, StandardPorts.BOOL.toOutput(), UIHint.DEFAULT, null, null))
                .addRow(new PortRow(null, StandardPorts.COUNT.toOutput(), UIHint.DEFAULT, null, null))
                .build();
    }

    @Override
    public Object compute(GraphDataContext context, String portName) {
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

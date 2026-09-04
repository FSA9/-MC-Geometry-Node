package com.mine.geometry_node.core.node.nodes.actions.inventory;

import com.mine.geometry_node.core.engine.graph.data.GraphDataContext;

import com.mine.geometry_node.core.engine.blueprint.runtime.ExecutionContext;
import com.mine.geometry_node.core.engine.blueprint.runtime.ExecutionResult;
import com.mine.geometry_node.core.node.definition.node.NodeComment;
import com.mine.geometry_node.core.node.nodes.BaseNode;
import com.mine.geometry_node.core.node.definition.node.NodeDef;
import com.mine.geometry_node.core.node.definition.node.NodeType;
import com.mine.geometry_node.core.node.definition.port.PortRow;
import com.mine.geometry_node.core.node.definition.port.StandardPorts;
import com.mine.geometry_node.core.node.definition.port.UIHint;
import com.mine.geometry_node.core.node.util.SlotAccessUtils;
import com.mine.geometry_node.core.node.value.SlotRef;
import net.minecraft.network.chat.Component;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.item.ItemStack;

public class ClearSlot extends BaseNode {

    public static final String TYPE_ID = "clear_slot";

    @Override
    public NodeDef getDefaultDefinition() {
        return NodeDef.builder(TYPE_ID, NodeType.ACTION, Component.translatable("geometry_node.node.clear_slot"))
                .comment(NodeComment.builder(TYPE_ID)
                        .text("summary")
                        .output(StandardPorts.FLOW_OUT, "flow_out")
                        .output(StandardPorts.ITEM_STACK, "item_stack")
                        .input(StandardPorts.FLOW_IN, "flow_in")
                        .input(StandardPorts.ENTITY, "entity")
                        .input(StandardPorts.SLOT, "slot")
                        .build())
                .addRow(new PortRow(StandardPorts.FLOW_IN.toExec(), StandardPorts.FLOW_OUT.toExec(), UIHint.DEFAULT, null, null))
                .addRow(new PortRow(null, StandardPorts.ITEM_STACK.toOutput(), UIHint.DEFAULT, null, null))
                .addPassthroughInput(StandardPorts.ENTITY.toInput(), UIHint.DEFAULT)
                .addPassthroughInput(StandardPorts.SLOT.toInput(SlotRef.DEFAULT.serialize()), UIHint.SLOT_REF)
                .build();
    }

    @Override
    public ExecutionResult execute(ExecutionContext context) {
        Entity entity = getInput(context, StandardPorts.ENTITY.getId(), Entity.class);
        SlotRef slotRef = getInput(context, StandardPorts.SLOT.getId(), SlotRef.class);
        ItemStack removed = SlotAccessUtils.clearItem(entity, slotRef != null ? slotRef : SlotRef.DEFAULT);
        context.setNodeResult(StandardPorts.ITEM_STACK.getId(), removed);

        return next(StandardPorts.FLOW_OUT.getId());
    }

    @Override
    public Object compute(GraphDataContext context, String portName) {
        if (StandardPorts.ITEM_STACK.getId().equals(portName)) {
            return context.getNodeResult(StandardPorts.ITEM_STACK.getId());
        }
        return null;
    }
}

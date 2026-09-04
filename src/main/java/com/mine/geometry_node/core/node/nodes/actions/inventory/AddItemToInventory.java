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
import net.minecraft.network.chat.Component;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.item.ItemStack;

import java.util.List;

public class AddItemToInventory extends BaseNode {
    public static final String TYPE_ID = "add_item_to_inventory";

    @Override
    public NodeDef getDefaultDefinition() {
        return NodeDef.builder(TYPE_ID, NodeType.ACTION, Component.translatable("geometry_node.node.add_item_to_inventory"))
                .comment(NodeComment.builder(TYPE_ID)
                        .text("summary")
                        .output(StandardPorts.FLOW_OUT, "flow_out")
                        .output(StandardPorts.COUNT, "count")
                        .output(StandardPorts.RESULT_ITEM_STACK, "leftover_item_stack")
                        .output(StandardPorts.BOOL, "bool")
                        .input(StandardPorts.FLOW_IN, "flow_in")
                        .input(StandardPorts.ENTITY, "entity")
                        .input(StandardPorts.ITEM_STACK, "item_stack")
                        .build())
                .addRow(new PortRow(StandardPorts.FLOW_IN.toExec(), StandardPorts.FLOW_OUT.toExec(), UIHint.DEFAULT, null, null))
                .addRow(new PortRow(null, StandardPorts.COUNT.toOutput(), UIHint.DEFAULT, null, null))
                .addRow(new PortRow(null, StandardPorts.RESULT_ITEM_STACK.toOutput(), UIHint.DEFAULT, null, null))
                .addRow(new PortRow(null, StandardPorts.BOOL.toOutput(), UIHint.DEFAULT, null, null))
                .addPassthroughInput(StandardPorts.ENTITY.toInput(), UIHint.DEFAULT)
                .addPassthroughInput(StandardPorts.ITEM_STACK.toInput(), UIHint.DEFAULT)
                .build();
    }

    @Override
    public ExecutionResult execute(ExecutionContext context) {
        List<Entity> entities = getInputList(context, StandardPorts.ENTITY.getId(), Entity.class);
        ItemStack stack = getInput(context, StandardPorts.ITEM_STACK.getId(), ItemStack.class);
        int requested = stack != null ? stack.getCount() : 0;
        int inserted = 0;
        ItemStack leftover = stack != null ? stack : ItemStack.EMPTY;

        for (Entity entity : entities) {
            if (leftover.isEmpty()) {
                break;
            }
            int before = leftover.getCount();
            leftover = SlotAccessUtils.insertIntoPrimaryStorage(entity, leftover);
            inserted += before - leftover.getCount();
        }

        context.setNodeResult(StandardPorts.RESULT_ITEM_STACK.getId(), leftover);
        context.setNodeResult(StandardPorts.COUNT.getId(), inserted);
        context.setNodeResult(StandardPorts.BOOL.getId(), requested > 0 && inserted >= requested && leftover.isEmpty());
        return next(StandardPorts.FLOW_OUT.getId());
    }

    @Override
    public Object compute(GraphDataContext context, String portName) {
        if (StandardPorts.RESULT_ITEM_STACK.getId().equals(portName)) {
            Object value = context.getNodeResult(StandardPorts.RESULT_ITEM_STACK.getId());
            return value instanceof ItemStack stack ? stack : ItemStack.EMPTY;
        }
        if (StandardPorts.COUNT.getId().equals(portName)) {
            Object value = context.getNodeResult(StandardPorts.COUNT.getId());
            return value instanceof Number number ? number.intValue() : 0;
        }
        if (StandardPorts.BOOL.getId().equals(portName)) {
            Object value = context.getNodeResult(StandardPorts.BOOL.getId());
            return value instanceof Boolean bool && bool;
        }
        return null;
    }
}

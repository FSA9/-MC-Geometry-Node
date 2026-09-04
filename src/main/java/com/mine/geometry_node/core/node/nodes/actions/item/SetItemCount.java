package com.mine.geometry_node.core.node.nodes.actions.item;

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
import net.minecraft.network.chat.Component;
import net.minecraft.world.item.ItemStack;

public class SetItemCount extends BaseNode {
    public static final String TYPE_ID = "set_item_count";

    @Override
    public NodeDef getDefaultDefinition() {
        return NodeDef.builder(TYPE_ID, NodeType.ACTION, Component.translatable("geometry_node.node.set_item_count"))
                .comment(NodeComment.builder(TYPE_ID)
                        .text("summary")
                        .output(StandardPorts.FLOW_OUT, "flow_out")
                        .output(StandardPorts.RESULT_ITEM_STACK, "result_item_stack")
                        .input(StandardPorts.FLOW_IN, "flow_in")
                        .input(StandardPorts.ITEM_STACK, "item_stack")
                        .input(StandardPorts.COUNT, "count")
                        .build())
                .addRow(new PortRow(StandardPorts.FLOW_IN.toExec(), StandardPorts.FLOW_OUT.toExec(), UIHint.DEFAULT, null, null))
                .addRow(new PortRow(null, StandardPorts.RESULT_ITEM_STACK.toOutput(), UIHint.DEFAULT, null, null))
                .addPassthroughInput(StandardPorts.ITEM_STACK.toInput(), UIHint.DEFAULT)
                .addPassthroughInput(StandardPorts.COUNT.toInput(1), UIHint.INPUT)
                .build();
    }

    @Override
    public ExecutionResult execute(ExecutionContext context) {
        ItemStack stack = getInput(context, StandardPorts.ITEM_STACK.getId(), ItemStack.class);
        Integer count = getInput(context, StandardPorts.COUNT.getId(), Integer.class);
        ItemStack result = stack != null ? stack.copy() : ItemStack.EMPTY;
        int value = count != null ? count : 1;
        if (result.isEmpty() || value <= 0) {
            result = ItemStack.EMPTY;
        } else {
            result.setCount(value);
        }
        context.setNodeResult(StandardPorts.RESULT_ITEM_STACK.getId(), result);
        return next(StandardPorts.FLOW_OUT.getId());
    }

    @Override
    public Object compute(GraphDataContext context, String portName) {
        if (StandardPorts.RESULT_ITEM_STACK.getId().equals(portName)) {
            Object value = context.getNodeResult(StandardPorts.RESULT_ITEM_STACK.getId());
            return value instanceof ItemStack stack ? stack : ItemStack.EMPTY;
        }
        return null;
    }
}

package com.mine.geometry_node.core.node.nodes.data.item.attribution;

import com.mine.geometry_node.core.engine.graph.data.GraphDataContext;
import com.mine.geometry_node.core.node.definition.node.NodeComment;
import com.mine.geometry_node.core.node.nodes.BaseNode;
import com.mine.geometry_node.core.node.definition.node.NodeDef;
import com.mine.geometry_node.core.node.definition.node.NodeType;
import com.mine.geometry_node.core.node.definition.port.PortRow;
import com.mine.geometry_node.core.node.definition.port.StandardPorts;
import com.mine.geometry_node.core.node.definition.port.UIHint;
import net.minecraft.network.chat.Component;
import net.minecraft.world.item.ItemStack;

public class GetItemMaxStackSize extends BaseNode {
    public static final String TYPE_ID = "get_item_max_stack_size";

    @Override
    public NodeDef getDefaultDefinition() {
        return NodeDef.builder(TYPE_ID, NodeType.DATA, Component.translatable("geometry_node.node.get_item_max_stack_size"))
                .comment(NodeComment.builder(TYPE_ID)
                        .text("summary")
                        .output(StandardPorts.COUNT, "count")
                        .input(StandardPorts.ITEM_STACK, "item_stack")
                        .build())
                .addRow(new PortRow(null, StandardPorts.COUNT.toOutput(), UIHint.DEFAULT, null, null))
                .addPassthroughInput(StandardPorts.ITEM_STACK.toInput(), UIHint.DEFAULT, null, null)
                .build();
    }

    @Override
    public Object compute(GraphDataContext context, String portName) {
        if (!StandardPorts.COUNT.getId().equals(portName)) {
            return null;
        }
        ItemStack stack = getInput(context, StandardPorts.ITEM_STACK.getId(), ItemStack.class);
        return stack != null && !stack.isEmpty() ? stack.getMaxStackSize() : 0;
    }
}

package com.mine.geometry_node.core.node.nodes.data.item.attribution;

import com.mine.geometry_node.core.engine.blueprint.runtime.ExecutionContext;
import com.mine.geometry_node.core.node.definition.node.NodeComment;
import com.mine.geometry_node.core.node.nodes.BaseNode;
import com.mine.geometry_node.core.node.definition.node.NodeDef;
import com.mine.geometry_node.core.node.definition.node.NodeType;
import com.mine.geometry_node.core.node.definition.port.PortRow;
import com.mine.geometry_node.core.node.definition.port.StandardPorts;
import com.mine.geometry_node.core.node.definition.port.UIHint;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.network.chat.Component;
import net.minecraft.world.item.ItemStack;

public class GetItemId extends BaseNode {
    public static final String TYPE_ID = "get_item_id";

    @Override
    public NodeDef getDefaultDefinition() {
        return NodeDef.builder(TYPE_ID, NodeType.DATA, Component.translatable("geometry_node.node.get_item_id"))
                .comment(NodeComment.builder(TYPE_ID)
                        .text("summary")
                        .output(StandardPorts.ITEM_TYPE, "item_type")
                        .input(StandardPorts.ITEM_STACK, "item_stack")
                        .build())
                .addRow(new PortRow(StandardPorts.ITEM_STACK.toInput(), StandardPorts.ITEM_TYPE.toOutput(), UIHint.DEFAULT, null, null))
                .build();
    }

    @Override
    public Object compute(ExecutionContext context, String portName) {
        if (!StandardPorts.ITEM_TYPE.getId().equals(portName)) {
            return null;
        }
        ItemStack stack = getInput(context, StandardPorts.ITEM_STACK.getId(), ItemStack.class);
        return stack != null ? BuiltInRegistries.ITEM.getKey(stack.getItem()).toString() : "minecraft:air";
    }
}

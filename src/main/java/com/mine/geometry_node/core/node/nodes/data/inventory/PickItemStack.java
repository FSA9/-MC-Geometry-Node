package com.mine.geometry_node.core.node.nodes.data.inventory;

import com.mine.geometry_node.core.engine.blueprint.runtime.ExecutionContext;
import com.mine.geometry_node.core.node.NodeComment;
import com.mine.geometry_node.core.node.nodes.BaseNode;
import com.mine.geometry_node.core.node.nodes.NodeDef;
import com.mine.geometry_node.core.node.nodes.NodeType;
import com.mine.geometry_node.core.node.port.PortDef;
import com.mine.geometry_node.core.node.port.PortRow;
import com.mine.geometry_node.core.node.port.PortType;
import com.mine.geometry_node.core.node.port.StandardPorts;
import com.mine.geometry_node.core.node.port.UIHint;
import com.mine.geometry_node.core.utils.ItemCodecUtils;
import net.minecraft.network.chat.Component;
import net.minecraft.world.item.ItemStack;

public class PickItemStack extends BaseNode {

    public static final String TYPE_ID = "pick_item_stack";
    public static final String PORT_TARGET_ITEM = "target_item";

    @Override
    public NodeDef getDefaultDefinition() {
        return NodeDef.builder(TYPE_ID, NodeType.DATA, Component.translatable("geometry_node.node.pick_item_stack"))
                .comment(NodeComment.builder(TYPE_ID)
                        .text("summary")
                        .output(StandardPorts.ITEM_STACK, "item_stack")
                        .input(PORT_TARGET_ITEM, "target_item")
                        .build())
                .addRow(new PortRow(null, StandardPorts.ITEM_STACK.toOutput(), UIHint.DEFAULT, null, null))
                .addRow(new PortRow(
                        new PortDef(PORT_TARGET_ITEM, Component.translatable("geometry_node.port.item_storage"), PortType.STRING, "", true),
                        null,
                        UIHint.ITEM_SLOT,
                        null,
                        null
                ))
                .build();
    }

    @Override
    public Object compute(ExecutionContext context, String portName) {
        if (!StandardPorts.ITEM_STACK.getId().equals(portName)) {
            return null;
        }

        String jsonStr = getInput(context, PORT_TARGET_ITEM, String.class);
        if (jsonStr == null || jsonStr.isEmpty() || context.getLevel() == null) {
            return ItemStack.EMPTY;
        }

        ItemStack stack = ItemCodecUtils.fromJson(jsonStr, context.getLevel().registryAccess());
        return stack.copy();
    }
}

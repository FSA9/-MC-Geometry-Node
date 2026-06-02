package com.mine.geometry_node.core.node.nodes.data.type;

import com.mine.geometry_node.core.engine.blueprint.execution.ExecutionContext;
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

public class GetItemSlot extends BaseNode {

    public static final String TYPE_ID = "get_item_slot";
    public static final String PORT_TARGET_ITEM = "target_item";

    @Override
    public NodeDef getDefaultDefinition() {
        return NodeDef.builder(TYPE_ID, NodeType.DATA, Component.translatable("geometry_node.node.get_item_slot"))
                .addRow(new PortRow(null, StandardPorts.ITEM_STACK.toOutput(), UIHint.DEFAULT, null, null))
                .addRow(new PortRow(
                        new PortDef(PORT_TARGET_ITEM, Component.literal("Item Storage"), PortType.STRING, "", true),
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
        if (jsonStr == null || jsonStr.isEmpty()) {
            return ItemStack.EMPTY;
        }

        if (context.getLevel() == null) {
            return ItemStack.EMPTY;
        }

        ItemStack stack = ItemCodecUtils.fromJson(jsonStr, context.getLevel().registryAccess());

        // 杜绝任何下游节点在运行时意外损耗、修改该物品导致蓝图内的模板被集体污染
        return stack.copy();
    }
}
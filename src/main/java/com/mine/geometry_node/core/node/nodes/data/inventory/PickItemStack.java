package com.mine.geometry_node.core.node.nodes.data.inventory;

import com.mine.geometry_node.core.engine.blueprint.runtime.ExecutionContext;
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
        String comment = """
                通过选择界面创建一个物品栈常量。
                输出 ItemStack 为选择时保存的物品数据副本。
                适合连接到物品匹配、槽位写入或背包移除等节点。""";

        return NodeDef.builder(TYPE_ID, NodeType.DATA, Component.translatable("geometry_node.node.pick_item_stack"))
                .comment(comment)
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
        if (jsonStr == null || jsonStr.isEmpty() || context.getLevel() == null) {
            return ItemStack.EMPTY;
        }

        ItemStack stack = ItemCodecUtils.fromJson(jsonStr, context.getLevel().registryAccess());
        return stack.copy();
    }
}

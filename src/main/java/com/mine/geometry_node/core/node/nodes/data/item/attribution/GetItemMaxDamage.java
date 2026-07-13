package com.mine.geometry_node.core.node.nodes.data.item.attribution;

import com.mine.geometry_node.core.engine.blueprint.runtime.ExecutionContext;
import com.mine.geometry_node.core.node.nodes.BaseNode;
import com.mine.geometry_node.core.node.nodes.NodeDef;
import com.mine.geometry_node.core.node.nodes.NodeType;
import com.mine.geometry_node.core.node.port.PortRow;
import com.mine.geometry_node.core.node.port.StandardPorts;
import com.mine.geometry_node.core.node.port.UIHint;
import net.minecraft.network.chat.Component;
import net.minecraft.world.item.ItemStack;

public class GetItemMaxDamage extends BaseNode {
    public static final String TYPE_ID = "get_item_max_damage";

    @Override
    public NodeDef getDefaultDefinition() {
        String comment = """
                读取物品栈最大耐久损耗值。
                可损坏物品输出最大耐久上限。
                不可损坏物品或空物品栈输出 0。""";

        return NodeDef.builder(TYPE_ID, NodeType.DATA, Component.translatable("geometry_node.node.get_item_max_damage"))
                .comment(comment)
                .addRow(new PortRow(StandardPorts.ITEM_STACK.toInput(), StandardPorts.INT.toOutput(), UIHint.DEFAULT, null, null))
                .build();
    }

    @Override
    public Object compute(ExecutionContext context, String portName) {
        if (!StandardPorts.INT.getId().equals(portName)) {
            return null;
        }
        ItemStack stack = getInput(context, StandardPorts.ITEM_STACK.getId(), ItemStack.class);
        return stack != null && !stack.isEmpty() && stack.isDamageableItem() ? stack.getMaxDamage() : 0;
    }
}

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

public class IsItemDamageable extends BaseNode {
    public static final String TYPE_ID = "is_item_damageable";

    @Override
    public NodeDef getDefaultDefinition() {
        String comment = """
                判断物品栈是否支持耐久损耗。
                不可损坏物品和空物品栈输出 false。
                适合在读取或设置耐久前做条件判断。""";

        return NodeDef.builder(TYPE_ID, NodeType.DATA, Component.translatable("geometry_node.node.is_item_damageable"))
                .comment(comment)
                .addRow(new PortRow(StandardPorts.ITEM_STACK.toInput(), StandardPorts.BOOL.toOutput(), UIHint.DEFAULT, null, null))
                .build();
    }

    @Override
    public Object compute(ExecutionContext context, String portName) {
        if (!StandardPorts.BOOL.getId().equals(portName)) {
            return null;
        }
        ItemStack stack = getInput(context, StandardPorts.ITEM_STACK.getId(), ItemStack.class);
        return stack != null && !stack.isEmpty() && stack.isDamageableItem();
    }
}

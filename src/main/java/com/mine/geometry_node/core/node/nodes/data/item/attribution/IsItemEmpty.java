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

public class IsItemEmpty extends BaseNode {
    public static final String TYPE_ID = "is_item_empty";

    @Override
    public NodeDef getDefaultDefinition() {
        String comment = """
                判断物品栈是否为空。
                缺失输入、空气或数量为 0 都视为空。
                输出 bool 为判断结果。""";

        return NodeDef.builder(TYPE_ID, NodeType.DATA, Component.translatable("geometry_node.node.is_item_empty"))
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
        return stack == null || stack.isEmpty();
    }
}

package com.mine.geometry_node.core.node.nodes.data.item.attribution;

import com.mine.geometry_node.core.engine.blueprint.runtime.ExecutionContext;
import com.mine.geometry_node.core.node.nodes.BaseNode;
import com.mine.geometry_node.core.node.nodes.NodeDef;
import com.mine.geometry_node.core.node.nodes.NodeType;
import com.mine.geometry_node.core.node.port.PortRow;
import com.mine.geometry_node.core.node.port.StandardPorts;
import com.mine.geometry_node.core.node.port.UIHint;
import net.minecraft.core.component.DataComponents;
import net.minecraft.network.chat.Component;
import net.minecraft.world.item.ItemStack;

public class HasItemCustomName extends BaseNode {
    public static final String TYPE_ID = "has_item_custom_name";

    @Override
    public NodeDef getDefaultDefinition() {
        String comment = """
                判断物品栈是否拥有自定义名称。
                只检查 CUSTOM_NAME 组件，不把默认翻译名称视为自定义名称。
                空物品栈输出 false。""";

        return NodeDef.builder(TYPE_ID, NodeType.DATA, Component.translatable("geometry_node.node.has_item_custom_name"))
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
        return stack != null && !stack.isEmpty() && stack.get(DataComponents.CUSTOM_NAME) != null;
    }
}

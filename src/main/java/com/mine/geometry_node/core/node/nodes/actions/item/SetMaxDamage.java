package com.mine.geometry_node.core.node.nodes.actions.item;

import com.mine.geometry_node.core.execution.ExecutionContext;
import com.mine.geometry_node.core.execution.ExecutionResult;
import com.mine.geometry_node.core.node.nodes.BaseNode;
import com.mine.geometry_node.core.node.nodes.NodeDef;
import com.mine.geometry_node.core.node.nodes.NodeType;
import com.mine.geometry_node.core.node.port.PortRow;
import com.mine.geometry_node.core.node.port.StandardPorts;
import com.mine.geometry_node.core.node.port.UIHint;
import net.minecraft.core.component.DataComponents;
import net.minecraft.network.chat.Component;
import net.minecraft.world.item.ItemStack;

public class SetMaxDamage extends BaseNode {
    public static final String TYPE_ID = "set_max_damage";

    @Override
    public NodeDef getDefaultDefinition() {
        return NodeDef.builder(TYPE_ID, NodeType.ACTION, Component.translatable("geometry_node.node." + TYPE_ID))
                .addRow(new PortRow(StandardPorts.FLOW_IN.toExec(), StandardPorts.FLOW_OUT.toExec(), UIHint.DEFAULT, null, null))
                .addRow(new PortRow(StandardPorts.ITEM_STACK.toInput(), StandardPorts.ITEM_STACK.toOutput(), UIHint.DEFAULT, null, null))
                .addRow(new PortRow(StandardPorts.INT.toInput(100), null, UIHint.INPUT, null, null))
                .build();
    }

    @Override
    public ExecutionResult execute(ExecutionContext context) {
        ItemStack stack = getInput(context, StandardPorts.ITEM_STACK.getId(), ItemStack.class);
        Integer value = getInput(context, StandardPorts.INT.getId(), Integer.class);

        if (stack != null && !stack.isEmpty() && value != null) {
            stack.set(DataComponents.MAX_DAMAGE, Math.max(1, value));
            context.setTempData(StandardPorts.ITEM_STACK.getId(), stack);
        }
        return next(StandardPorts.FLOW_OUT.getId());
    }

    @Override
    public Object compute(ExecutionContext context, String portName) {
        if (StandardPorts.ITEM_STACK.getId().equals(portName)) return context.getTempData(StandardPorts.ITEM_STACK.getId());
        return null;
    }
}
package com.mine.geometry_node.core.node.nodes.actions.item;

import com.mine.geometry_node.core.execution.ExecutionContext;
import com.mine.geometry_node.core.execution.ExecutionResult;
import com.mine.geometry_node.core.node.nodes.BaseNode;
import com.mine.geometry_node.core.node.nodes.NodeDef;
import com.mine.geometry_node.core.node.nodes.NodeType;
import com.mine.geometry_node.core.node.port.PortRow;
import com.mine.geometry_node.core.node.port.StandardPorts;
import com.mine.geometry_node.core.node.port.UIHint;
import net.minecraft.network.chat.Component;
import net.minecraft.world.item.ItemStack;

public class RepairItemStack extends BaseNode {

    public static final String TYPE_ID = "repair_item_stack";

    @Override
    public NodeDef getDefaultDefinition() {
        return NodeDef.builder(TYPE_ID, NodeType.ACTION, Component.translatable("geometry_node.node.repair_item_stack"))
                .addRow(new PortRow(StandardPorts.FLOW_IN.toExec(), StandardPorts.FLOW_OUT.toExec(), UIHint.DEFAULT, null, null))
                .addRow(new PortRow(StandardPorts.ITEM_STACK.toInput(), null, UIHint.DEFAULT, null, null))
                // 修复量
                .addRow(new PortRow(StandardPorts.INT.toInput(1), null, UIHint.INPUT, null, null))
                .build();
    }

    @Override
    public ExecutionResult execute(ExecutionContext context) {
        ItemStack stack = getInput(context, StandardPorts.ITEM_STACK.getId(), ItemStack.class);
        Integer amount = getInput(context, StandardPorts.INT.getId(), Integer.class);

        if (stack != null && !stack.isEmpty() && amount != null && amount > 0 && stack.isDamaged()) {
            // 获取当前伤害值
            int currentDamage = stack.getDamageValue();
            // 修复即减小伤害值，最小为0
            stack.setDamageValue(Math.max(0, currentDamage - amount));
        }

        return next(StandardPorts.FLOW_OUT.getId());
    }
}
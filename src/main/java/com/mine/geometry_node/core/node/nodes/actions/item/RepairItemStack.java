package com.mine.geometry_node.core.node.nodes.actions.item;

import com.mine.geometry_node.core.engine.blueprint.runtime.ExecutionContext;
import com.mine.geometry_node.core.engine.blueprint.runtime.ExecutionResult;
import com.mine.geometry_node.core.node.nodes.BaseNode;
import com.mine.geometry_node.core.node.definition.node.NodeDef;
import com.mine.geometry_node.core.node.definition.node.NodeType;
import com.mine.geometry_node.core.node.definition.port.PortRow;
import com.mine.geometry_node.core.node.definition.port.StandardPorts;
import com.mine.geometry_node.core.node.definition.port.UIHint;
import net.minecraft.network.chat.Component;
import net.minecraft.world.item.ItemStack;

public class RepairItemStack extends BaseNode {

    public static final String TYPE_ID = "repair_item_stack";

    @Override
    public NodeDef getDefaultDefinition() {
        return NodeDef.builder(TYPE_ID, NodeType.ACTION, Component.translatable("geometry_node.node.repair_item_stack"))
                .addRow(new PortRow(StandardPorts.FLOW_IN.toExec(), StandardPorts.FLOW_OUT.toExec(), UIHint.DEFAULT, null, null))
                .addRow(new PortRow(null, StandardPorts.RESULT_ITEM_STACK.toOutput(), UIHint.DEFAULT, null, null))
                .addPassthroughInput(StandardPorts.ITEM_STACK.toInput(), UIHint.DEFAULT)
                // 修复量
                .addPassthroughInput(StandardPorts.INT.toInput(1), UIHint.INPUT)
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

        context.setNodeResult(StandardPorts.RESULT_ITEM_STACK.getId(),
                stack != null ? stack : ItemStack.EMPTY);
        return next(StandardPorts.FLOW_OUT.getId());
    }

    @Override
    public Object compute(ExecutionContext context, String portName) {
        if (!StandardPorts.RESULT_ITEM_STACK.getId().equals(portName)) return null;
        Object value = context.getNodeResult(StandardPorts.RESULT_ITEM_STACK.getId());
        return value instanceof ItemStack stack ? stack : ItemStack.EMPTY;
    }
}

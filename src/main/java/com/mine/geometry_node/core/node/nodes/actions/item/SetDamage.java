package com.mine.geometry_node.core.node.nodes.actions.item;

import com.mine.geometry_node.core.engine.blueprint.runtime.ExecutionContext;
import com.mine.geometry_node.core.engine.blueprint.runtime.ExecutionResult;
import com.mine.geometry_node.core.node.nodes.BaseNode;
import com.mine.geometry_node.core.node.definition.node.NodeDef;
import com.mine.geometry_node.core.node.definition.node.NodeType;
import com.mine.geometry_node.core.node.definition.port.PortRow;
import com.mine.geometry_node.core.node.definition.port.StandardPorts;
import com.mine.geometry_node.core.node.definition.port.UIHint;
import net.minecraft.core.component.DataComponents;
import net.minecraft.network.chat.Component;
import net.minecraft.world.item.ItemStack;

public class SetDamage extends BaseNode {
    public static final String TYPE_ID = "set_damage";

    @Override
    public NodeDef getDefaultDefinition() {
        return NodeDef.builder(TYPE_ID, NodeType.ACTION, Component.translatable("geometry_node.node." + TYPE_ID))
                .addRow(new PortRow(StandardPorts.FLOW_IN.toExec(), StandardPorts.FLOW_OUT.toExec(), UIHint.DEFAULT, null, null))
                .addRow(new PortRow(null, StandardPorts.RESULT_ITEM_STACK.toOutput(), UIHint.DEFAULT, null, null))
                .addPassthroughInput(StandardPorts.ITEM_STACK.toInput(), UIHint.DEFAULT)
                // 耐久损耗 0 代表满耐久
                .addPassthroughInput(StandardPorts.INT.toInput(0), UIHint.INPUT)
                .build();
    }

    @Override
    public ExecutionResult execute(ExecutionContext context) {
        context.setNodeResult(StandardPorts.RESULT_ITEM_STACK.getId(), null);
        ItemStack stack = getInput(context, StandardPorts.ITEM_STACK.getId(), ItemStack.class);
        Integer value = getInput(context, StandardPorts.INT.getId(), Integer.class);

        if (stack != null && !stack.isEmpty() && value != null) {
            stack.set(DataComponents.DAMAGE, Math.max(0, value));
            context.setNodeResult(StandardPorts.RESULT_ITEM_STACK.getId(), stack);
        }
        return next(StandardPorts.FLOW_OUT.getId());
    }

    @Override
    public Object compute(ExecutionContext context, String portName) {
        if (StandardPorts.RESULT_ITEM_STACK.getId().equals(portName)) return context.getNodeResult(StandardPorts.RESULT_ITEM_STACK.getId());
        return null;
    }
}
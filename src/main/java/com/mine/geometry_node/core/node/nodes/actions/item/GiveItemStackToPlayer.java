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
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;

public class GiveItemStackToPlayer extends BaseNode {

    public static final String TYPE_ID = "give_item_stack_to_player";

    @Override
    public NodeDef getDefaultDefinition() {
        return NodeDef.builder(TYPE_ID, NodeType.ACTION, Component.translatable("geometry_node.node.give_item_stack_to_player"))
                // 1. 执行流
                .addRow(new PortRow(StandardPorts.FLOW_IN.toExec(), StandardPorts.FLOW_OUT.toExec(), UIHint.DEFAULT, null, null))

                // 2. 目标实体 (必须是玩家)
                .addRow(new PortRow(StandardPorts.ENTITY.toInput(), null, UIHint.DEFAULT, null, null))

                // 3. 要给予的物品实例 (ItemStack)
                .addRow(new PortRow(StandardPorts.ITEM_STACK.toInput(), null, UIHint.DEFAULT, null, null))
                .build();
    }

    @Override
    public ExecutionResult execute(ExecutionContext context) {
        Entity target = getInput(context, StandardPorts.ENTITY.getId(), Entity.class);
        ItemStack stack = getInput(context, StandardPorts.ITEM_STACK.getId(), ItemStack.class);

        // 核心逻辑：只有目标是玩家且物品不为空时才执行
        if (target instanceof Player player && stack != null && !stack.isEmpty()) {
            // copy() 是为了防止多个地方共用同一个对象引用导致逻辑错误
            ItemStack copy = stack.copy();

            // 将物品尝试放入背包，如果背包满了，物品会自动掉落在玩家脚下
            if (!player.getInventory().add(copy)) {
                player.drop(copy, false);
            }
        }

        return next(StandardPorts.FLOW_OUT.getId());
    }
}
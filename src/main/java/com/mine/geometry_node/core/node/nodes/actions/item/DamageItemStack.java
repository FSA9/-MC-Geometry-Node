package com.mine.geometry_node.core.node.nodes.actions.item;

import com.mine.geometry_node.core.engine.graph.data.GraphDataContext;

import com.mine.geometry_node.core.engine.blueprint.runtime.ExecutionContext;
import com.mine.geometry_node.core.engine.blueprint.runtime.ExecutionResult;
import com.mine.geometry_node.core.node.nodes.BaseNode;
import com.mine.geometry_node.core.node.definition.node.NodeDef;
import com.mine.geometry_node.core.node.definition.node.NodeType;
import com.mine.geometry_node.core.node.definition.port.PortRow;
import com.mine.geometry_node.core.node.definition.port.StandardPorts;
import com.mine.geometry_node.core.node.definition.port.UIHint;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.item.ItemStack;

public class DamageItemStack extends BaseNode {

    public static final String TYPE_ID = "damage_item_stack";

    @Override
    public NodeDef getDefaultDefinition() {
        return NodeDef.builder(TYPE_ID, NodeType.ACTION, Component.translatable("geometry_node.node.damage_item_stack"))
                // 1. 执行流
                .addRow(new PortRow(StandardPorts.FLOW_IN.toExec(), StandardPorts.FLOW_OUT.toExec(), UIHint.DEFAULT, null, null))
                .addRow(new PortRow(null, StandardPorts.RESULT_ITEM_STACK.toOutput(), UIHint.DEFAULT, null, null))
                // 2. 目标物品栈
                .addPassthroughInput(StandardPorts.ITEM_STACK.toInput(), UIHint.DEFAULT)
                // 3. 伤害数值
                .addPassthroughInput(StandardPorts.INT.toInput(1), UIHint.INPUT)
                // 4. 责任实体 (可选，用于播放物品破碎效果)
                .addPassthroughInput(StandardPorts.ENTITY.toInput(), UIHint.DEFAULT)
                .build();
    }

    @Override
    public ExecutionResult execute(ExecutionContext context) {
        ItemStack stack = getInput(context, StandardPorts.ITEM_STACK.getId(), ItemStack.class);
        Integer amount = getInput(context, StandardPorts.INT.getId(), Integer.class);
        LivingEntity owner = getInputFromList(
                context, StandardPorts.ENTITY.getId(), 0, LivingEntity.class);

        // 只有可损耗耐久的物品才能被伤害
        if (stack != null && !stack.isEmpty() && amount != null && amount > 0 && stack.isDamageableItem()) {
            if (context.getLevel() instanceof ServerLevel serverLevel) {
                // hurtAndBreak 会处理：1. 随机附魔减免 2. 扣除耐久 3. 破损逻辑
                stack.hurtAndBreak(amount, serverLevel, owner, (item) -> {
                    // 当物品破碎时的回调，通常留空或播放特定事件
                });
            }
        }

        context.setNodeResult(StandardPorts.RESULT_ITEM_STACK.getId(),
                stack != null ? stack : ItemStack.EMPTY);
        return next(StandardPorts.FLOW_OUT.getId());
    }

    @Override
    public Object compute(GraphDataContext context, String portName) {
        if (!StandardPorts.RESULT_ITEM_STACK.getId().equals(portName)) return null;
        Object value = context.getNodeResult(StandardPorts.RESULT_ITEM_STACK.getId());
        return value instanceof ItemStack stack ? stack : ItemStack.EMPTY;
    }
}

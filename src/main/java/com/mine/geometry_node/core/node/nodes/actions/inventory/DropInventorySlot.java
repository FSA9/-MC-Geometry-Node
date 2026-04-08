package com.mine.geometry_node.core.node.nodes.actions.inventory;

import com.mine.geometry_node.core.execution.ExecutionContext;
import com.mine.geometry_node.core.execution.ExecutionResult;
import com.mine.geometry_node.core.node.nodes.BaseNode;
import com.mine.geometry_node.core.node.nodes.NodeDef;
import com.mine.geometry_node.core.node.nodes.NodeType;
import com.mine.geometry_node.core.node.port.PortRow;
import com.mine.geometry_node.core.node.port.StandardPorts;
import com.mine.geometry_node.core.node.port.UIHint;
import net.minecraft.network.chat.Component;
import net.minecraft.world.Container;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.SlotAccess;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;

import java.util.List;

public class DropInventorySlot extends BaseNode {

    public static final String TYPE_ID = "drop_inventory_slot";

    @Override
    public NodeDef getDefaultDefinition() {
        return NodeDef.builder(TYPE_ID, NodeType.ACTION, Component.translatable("geometry_node.node.drop_inventory_slot"))
                // 1. 输入输出执行流
                .addRow(new PortRow(StandardPorts.FLOW_IN.toExec(), StandardPorts.FLOW_OUT.toExec(), UIHint.DEFAULT, null, null))
                // 2. 目标实体 (支持单体/List自动转换)
                .addRow(new PortRow(StandardPorts.ENTITY.toInput(), null, UIHint.DEFAULT, null, null))
                // 3. 槽位索引 (默认第0格)
                .addRow(new PortRow(StandardPorts.INDEX.toInput(0), null, UIHint.INPUT, null, null))
                // 4. 丢出数量 (默认1个，如果填比如 99 则为全丢)
                .addRow(new PortRow(StandardPorts.INT.toInput(1), null, UIHint.INPUT, null, null))
                .build();
    }

    @Override
    public ExecutionResult execute(ExecutionContext context) {
        // 获取参数
        List<Entity> entities = getInputList(context, StandardPorts.ENTITY.getId(), Entity.class);
        Integer slotIndex = getInput(context, StandardPorts.INDEX.getId(), Integer.class);
        Integer count = getInput(context, StandardPorts.INT.getId(), Integer.class);

        // 防御性校验
        if (slotIndex != null && count != null && count > 0 && !entities.isEmpty()) {
            for (Entity entity : entities) {
                dropItemFromEntity(entity, slotIndex, count);
            }
        }

        // 继续向下游传递执行流
        return next(StandardPorts.FLOW_OUT.getId());
    }

    /**
     * 核心抛弃逻辑：按实体类型进行不同适配
     */
    private void dropItemFromEntity(Entity entity, int slotIndex, int count) {
        // 情况1：玩家（直接操作背包，使用原版完美的抛掷动作）
        if (entity instanceof Player player) {
            ItemStack stack = player.getInventory().getItem(slotIndex);
            if (!stack.isEmpty()) {
                int dropCount = Math.min(count, stack.getCount());
                ItemStack dropStack = stack.split(dropCount);
                // false: 不往周围随机散落（类似按Q往正前方抛出）
                // true: 携带丢出者信息（记录是谁丢的）
                player.drop(dropStack, false, true);
            }
            return;
        }

        // 情况2：自带原版容器的实体（如箱子矿车、漏斗矿车等）
        if (entity instanceof Container container) {
            if (slotIndex >= 0 && slotIndex < container.getContainerSize()) {
                ItemStack stack = container.getItem(slotIndex);
                if (!stack.isEmpty()) {
                    int dropCount = Math.min(count, stack.getCount());
                    ItemStack dropStack = stack.split(dropCount);
                    // 容器物品直接掉落在其脚下
                    entity.spawnAtLocation(dropStack);
                }
            }
            return;
        }

        // 情况3：其他通用实体（如马的装备、盔甲架上的物品等）
        // 借助原版的万能 SlotAccess（通常用于 /item 指令的槽位操作）
        SlotAccess slotAccess = entity.getSlot(slotIndex);
        if (slotAccess != SlotAccess.NULL) {
            ItemStack stack = slotAccess.get();
            if (!stack.isEmpty()) {
                int dropCount = Math.min(count, stack.getCount());
                ItemStack dropStack = stack.split(dropCount);
                slotAccess.set(stack); // 更新扣除数量后的物品回原槽位
                entity.spawnAtLocation(dropStack);
            }
        }
    }
}
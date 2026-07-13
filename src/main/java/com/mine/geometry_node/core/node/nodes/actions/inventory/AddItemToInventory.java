package com.mine.geometry_node.core.node.nodes.actions.inventory;

import com.mine.geometry_node.core.engine.blueprint.runtime.ExecutionContext;
import com.mine.geometry_node.core.engine.blueprint.runtime.ExecutionResult;
import com.mine.geometry_node.core.node.nodes.BaseNode;
import com.mine.geometry_node.core.node.nodes.NodeDef;
import com.mine.geometry_node.core.node.nodes.NodeType;
import com.mine.geometry_node.core.node.port.PortRow;
import com.mine.geometry_node.core.node.port.StandardPorts;
import com.mine.geometry_node.core.node.port.UIHint;
import com.mine.geometry_node.core.node.util.SlotAccessUtils;
import net.minecraft.network.chat.Component;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.item.ItemStack;

import java.util.List;

public class AddItemToInventory extends BaseNode {
    public static final String TYPE_ID = "add_item_to_inventory";

    @Override
    public NodeDef getDefaultDefinition() {
        String comment = """
                将物品栈插入目标的主要物品存储。
                玩家会插入背包，容器会插入容器槽位，其他实体会尝试插入 NeoForge 物品能力。
                输出 ItemStack 为剩余未插入物品，count 为实际插入数量，bool 表示是否完全插入。""";

        return NodeDef.builder(TYPE_ID, NodeType.ACTION, Component.translatable("geometry_node.node.add_item_to_inventory"))
                .comment(comment)
                .addRow(new PortRow(StandardPorts.FLOW_IN.toExec(), StandardPorts.FLOW_OUT.toExec(), UIHint.DEFAULT, null, null))
                .addRow(new PortRow(StandardPorts.ENTITY.toInput(), StandardPorts.COUNT.toOutput(), UIHint.DEFAULT, null, null))
                .addRow(new PortRow(StandardPorts.ITEM_STACK.toInput(), StandardPorts.ITEM_STACK.toOutput(), UIHint.DEFAULT, null, null))
                .addRow(new PortRow(null, StandardPorts.BOOL.toOutput(), UIHint.DEFAULT, null, null))
                .build();
    }

    @Override
    public ExecutionResult execute(ExecutionContext context) {
        List<Entity> entities = getInputList(context, StandardPorts.ENTITY.getId(), Entity.class);
        ItemStack stack = getInput(context, StandardPorts.ITEM_STACK.getId(), ItemStack.class);
        int requested = stack != null ? stack.getCount() : 0;
        int inserted = 0;
        ItemStack leftover = stack != null ? stack.copy() : ItemStack.EMPTY;

        for (Entity entity : entities) {
            if (leftover.isEmpty()) {
                break;
            }
            int before = leftover.getCount();
            leftover = SlotAccessUtils.insertIntoPrimaryStorage(entity, leftover);
            inserted += before - leftover.getCount();
        }

        context.setTempData(StandardPorts.ITEM_STACK.getId(), leftover);
        context.setTempData(StandardPorts.COUNT.getId(), inserted);
        context.setTempData(StandardPorts.BOOL.getId(), requested > 0 && inserted >= requested && leftover.isEmpty());
        return next(StandardPorts.FLOW_OUT.getId());
    }

    @Override
    public Object compute(ExecutionContext context, String portName) {
        if (StandardPorts.ITEM_STACK.getId().equals(portName)) {
            Object value = context.getTempData(StandardPorts.ITEM_STACK.getId());
            return value instanceof ItemStack stack ? stack : ItemStack.EMPTY;
        }
        if (StandardPorts.COUNT.getId().equals(portName)) {
            Object value = context.getTempData(StandardPorts.COUNT.getId());
            return value instanceof Number number ? number.intValue() : 0;
        }
        if (StandardPorts.BOOL.getId().equals(portName)) {
            Object value = context.getTempData(StandardPorts.BOOL.getId());
            return value instanceof Boolean bool && bool;
        }
        return null;
    }
}

package com.mine.geometry_node.core.node.nodes.actions.inventory;

import com.mine.geometry_node.core.engine.blueprint.runtime.ExecutionContext;
import com.mine.geometry_node.core.engine.blueprint.runtime.ExecutionResult;
import com.mine.geometry_node.core.node.nodes.BaseNode;
import com.mine.geometry_node.core.node.nodes.NodeDef;
import com.mine.geometry_node.core.node.nodes.NodeType;
import com.mine.geometry_node.core.node.port.PortRow;
import com.mine.geometry_node.core.node.port.StandardPorts;
import com.mine.geometry_node.core.node.port.UIHint;
import net.minecraft.network.chat.Component;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;

import java.util.List;

public class ClearItem extends BaseNode {

    public static final String TYPE_ID = "clear_item";

    @Override
    public NodeDef getDefaultDefinition() {
        return NodeDef.builder(TYPE_ID, NodeType.ACTION, Component.translatable("geometry_node.node.clear_item"))
                .addRow(new PortRow(StandardPorts.FLOW_IN.toExec(), StandardPorts.FLOW_OUT.toExec(), UIHint.DEFAULT, null, null))
                .addRow(new PortRow(StandardPorts.ENTITY.toInput(), null, UIHint.DEFAULT, null, null))
                .addRow(new PortRow(StandardPorts.ITEM.toInput(), null, UIHint.DEFAULT, null, null)) // 传入需要清除的 Item
                .build();
    }

    @Override
    public ExecutionResult execute(ExecutionContext context) {
        List<Entity> entities = getInputList(context, StandardPorts.ENTITY.getId(), Entity.class);
        Item targetItem = getInput(context, StandardPorts.ITEM.getId(), Item.class);

        if (targetItem != null && !entities.isEmpty()) {
            for (Entity entity : entities) {
                if (entity instanceof Player player) {
                    int size = player.getInventory().getContainerSize();
                    for (int i = 0; i < size; i++) {
                        ItemStack stack = player.getInventory().getItem(i);
                        if (!stack.isEmpty() && stack.is(targetItem)) {
                            player.getInventory().setItem(i, ItemStack.EMPTY); // 匹配到对应物品直接置空
                        }
                    }
                }
            }
        }

        return next(StandardPorts.FLOW_OUT.getId());
    }
}
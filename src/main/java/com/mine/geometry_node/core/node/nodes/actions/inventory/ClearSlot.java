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
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;

import java.util.List;

public class ClearSlot extends BaseNode {

    public static final String TYPE_ID = "clear_slot";

    @Override
    public NodeDef getDefaultDefinition() {
        return NodeDef.builder(TYPE_ID, NodeType.ACTION, Component.translatable("geometry_node.node.clear_slot"))
                .addRow(new PortRow(StandardPorts.FLOW_IN.toExec(), StandardPorts.FLOW_OUT.toExec(), UIHint.DEFAULT, null, null))
                .addRow(new PortRow(StandardPorts.ENTITY.toInput(), null, UIHint.DEFAULT, null, null))
                .addRow(new PortRow(StandardPorts.INDEX.toInput(), null, UIHint.INPUT, null, null)) // 传入槽位序号
                .build();
    }

    @Override
    public ExecutionResult execute(ExecutionContext context) {
        List<Entity> entities = getInputList(context, StandardPorts.ENTITY.getId(), Entity.class);
        Integer slotIndex = getInput(context, StandardPorts.INDEX.getId(), Integer.class);

        if (slotIndex != null && !entities.isEmpty()) {
            for (Entity entity : entities) {
                if (entity instanceof Player player) {
                    if (slotIndex >= 0 && slotIndex < player.getInventory().getContainerSize()) {
                        player.getInventory().setItem(slotIndex, ItemStack.EMPTY);
                    }
                }
            }
        }

        return next(StandardPorts.FLOW_OUT.getId());
    }
}
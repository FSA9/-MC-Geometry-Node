package com.mine.geometry_node.core.node.nodes.data.entity.attribution;

import com.mine.geometry_node.core.execution.ExecutionContext;
import com.mine.geometry_node.core.node.nodes.*;
import net.minecraft.network.chat.Component;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.player.Player;

import java.util.List;

public class GetFoodLevel extends BaseNode {

    public static final String TYPE_ID = "get_food_level";

    @Override
    public NodeDef getDefaultDefinition() {
        return NodeDef.builder(TYPE_ID, NodeType.DATA, Component.translatable("geometry_node.node.get_food_level"))
                .addRow(new PortRow(
                        StandardPorts.ENTITY.toInput(),
                        StandardPorts.VALUE.toOutput(),
                        UIHint.DEFAULT, null, null
                ))
                .build();
    }

    @Override
    public Object compute(ExecutionContext context, String portName) {
        if (!StandardPorts.VALUE.getId().equals(portName)) return null;

        List<Entity> entities = getInputList(context, StandardPorts.ENTITY.getId(), Entity.class);
        if (entities.isEmpty()) return null;

        if (entities.getFirst() instanceof Player player) {
            return (float) player.getFoodData().getFoodLevel();
        }

        return null;
    }
}
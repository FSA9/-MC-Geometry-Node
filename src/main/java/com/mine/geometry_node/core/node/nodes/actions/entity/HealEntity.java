package com.mine.geometry_node.core.node.nodes.actions.entity;

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
import net.minecraft.world.entity.LivingEntity;

import java.util.List;

public class HealEntity extends BaseNode {

    public static final String TYPE_ID = "heal_entity";

    @Override
    public NodeDef getDefaultDefinition() {
        return NodeDef.builder(TYPE_ID, NodeType.ACTION, Component.translatable("geometry_node.node.heal_entity"))
                .addRow(new PortRow(StandardPorts.FLOW_IN.toExec(), StandardPorts.FLOW_OUT.toExec(), UIHint.DEFAULT, null, null))
                .addRow(new PortRow(StandardPorts.ENTITY.toInput(), null, UIHint.DEFAULT, null, null))
                .addRow(new PortRow(StandardPorts.FLOAT_VALUE.toInput(), null, UIHint.INPUT, null, null)) // 使用 VALUE 作为治疗数值
                .build();
    }

    @Override
    public ExecutionResult execute(ExecutionContext context) {
        List<Entity> entities = getInputList(context, StandardPorts.ENTITY.getId(), Entity.class);
        Float healAmount = getInput(context, StandardPorts.FLOAT_VALUE.getId(), Float.class);

        if (healAmount != null && healAmount > 0 && !entities.isEmpty()) {
            for (Entity entity : entities) {
                if (entity instanceof LivingEntity livingEntity) {
                    livingEntity.heal(healAmount);
                }
            }
        }

        return next(StandardPorts.FLOW_OUT.getId());
    }
}

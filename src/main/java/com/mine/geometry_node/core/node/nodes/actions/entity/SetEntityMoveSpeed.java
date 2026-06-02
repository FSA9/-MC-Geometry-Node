package com.mine.geometry_node.core.node.nodes.actions.entity;

import com.mine.geometry_node.core.engine.blueprint.execution.ExecutionContext;
import com.mine.geometry_node.core.engine.blueprint.execution.ExecutionResult;
import com.mine.geometry_node.core.node.nodes.BaseNode;
import com.mine.geometry_node.core.node.nodes.NodeDef;
import com.mine.geometry_node.core.node.nodes.NodeType;
import com.mine.geometry_node.core.node.port.PortRow;
import com.mine.geometry_node.core.node.port.StandardPorts;
import com.mine.geometry_node.core.node.port.UIHint;
import net.minecraft.network.chat.Component;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.ai.attributes.AttributeInstance;
import net.minecraft.world.entity.ai.attributes.Attributes;

import java.util.List;

public class SetEntityMoveSpeed extends BaseNode {

    public static final String TYPE_ID = "set_entity_move_speed";

    @Override
    public NodeDef getDefaultDefinition() {
        return NodeDef.builder(TYPE_ID, NodeType.ACTION, Component.translatable("geometry_node.node." + TYPE_ID))
                .addRow(new PortRow(StandardPorts.FLOW_IN.toExec(), StandardPorts.FLOW_OUT.toExec(), UIHint.DEFAULT, null, null))
                .addRow(new PortRow(StandardPorts.ENTITY.toInput(), null, UIHint.DEFAULT, null, null))
                // 提供一个合理的默认值 0.25 (大约是玩家默认跑速)
                .addRow(new PortRow(StandardPorts.VALUE.toInput(0.25f), null, UIHint.INPUT, null, null))
                .build();
    }

    @Override
    public ExecutionResult execute(ExecutionContext context) {
        List<Entity> entities = getInputList(context, StandardPorts.ENTITY.getId(), Entity.class);
        Float speed = getInput(context, StandardPorts.VALUE.getId(), Float.class);

        if (speed != null && speed >= 0.0f && !entities.isEmpty()) {
            for (Entity entity : entities) {
                // 速度属性仅存在于 LivingEntity
                if (entity instanceof LivingEntity living) {
                    AttributeInstance speedAttr = living.getAttribute(Attributes.MOVEMENT_SPEED);
                    if (speedAttr != null) {
                        speedAttr.setBaseValue(speed);
                    }
                }
            }
        }
        return next(StandardPorts.FLOW_OUT.getId());
    }
}
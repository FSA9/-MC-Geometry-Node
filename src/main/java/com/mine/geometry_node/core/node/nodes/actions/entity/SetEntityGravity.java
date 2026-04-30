package com.mine.geometry_node.core.node.nodes.actions.entity;

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
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.ai.attributes.AttributeInstance;
import net.minecraft.world.entity.ai.attributes.Attributes;

import java.util.List;

public class SetEntityGravity extends BaseNode {

    public static final String TYPE_ID = "set_entity_gravity";

    @Override
    public NodeDef getDefaultDefinition() {
        return NodeDef.builder(TYPE_ID, NodeType.ACTION, Component.translatable("geometry_node.node.set_entity_gravity"))
                // 1. 执行流
                .addRow(new PortRow(StandardPorts.FLOW_IN.toExec(), StandardPorts.FLOW_OUT.toExec(), UIHint.DEFAULT, null, null))
                // 2. 目标实体
                .addRow(new PortRow(StandardPorts.ENTITY.toInput(), null, UIHint.DEFAULT, null, null))
                // 3. 重力数值 (Float，原版大多数实体默认是 0.08)
                .addRow(new PortRow(StandardPorts.VALUE.toInput(0.08f), null, UIHint.INPUT, null, null))
                .build();
    }

    @Override
    public ExecutionResult execute(ExecutionContext context) {
        List<Entity> entities = getInputList(context, StandardPorts.ENTITY.getId(), Entity.class);
        Float gravityValue = getInput(context, StandardPorts.VALUE.getId(), Float.class);

        if (gravityValue != null && !entities.isEmpty()) {
            for (Entity entity : entities) {
                if (entity instanceof LivingEntity living) {
                    // 获取 1.21 的原生重力属性
                    AttributeInstance gravityAttr = living.getAttribute(Attributes.GRAVITY);
                    if (gravityAttr != null) {
                        gravityAttr.setBaseValue(gravityValue);
                    }
                }
            }
        }

        return next(StandardPorts.FLOW_OUT.getId());
    }
}
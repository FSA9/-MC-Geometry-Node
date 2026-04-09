package com.mine.geometry_node.core.node.nodes.actions.entity;

import com.mine.geometry_node.core.execution.ExecutionContext;
import com.mine.geometry_node.core.execution.ExecutionResult;
import com.mine.geometry_node.core.node.nodes.*;
import com.mine.geometry_node.core.node.port.PortRow;
import com.mine.geometry_node.core.node.port.StandardPorts;
import com.mine.geometry_node.core.node.port.UIHint;
import net.minecraft.network.chat.Component;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.ai.attributes.AttributeInstance;
import net.minecraft.world.entity.ai.attributes.Attributes;

import java.util.List;

public class SetEntitySize extends BaseNode {

    public static final String TYPE_ID = "set_entity_size";

    @Override
    public NodeDef getDefaultDefinition() {
        return NodeDef.builder(TYPE_ID, NodeType.ACTION, Component.translatable("geometry_node.node.set_entity_size"))
                // 第一行：执行流
                .addRow(new PortRow(StandardPorts.FLOW_IN.toExec(), StandardPorts.FLOW_OUT.toExec(), UIHint.DEFAULT, null, null))
                // 第二行：目标实体
                .addRow(new PortRow(StandardPorts.ENTITY.toInput(), null, UIHint.DEFAULT, null, null))
                // 第三行：缩放倍数 (浮点数，比如 2.0 代表放大两倍)
                .addRow(new PortRow(StandardPorts.VALUE.toInput(), null, UIHint.INPUT, null, null))
                .build();
    }

    @Override
    public ExecutionResult execute(ExecutionContext context) {
        List<Entity> entities = getInputList(context, StandardPorts.ENTITY.getId(), Entity.class);
        Float scaleFactor = getInput(context, StandardPorts.VALUE.getId(), Float.class);

        if (scaleFactor != null && scaleFactor > 0.0f && !entities.isEmpty()) {
            for (Entity entity : entities) {
                if (entity instanceof LivingEntity living) {
                    // 1.21.1 支持的原生缩放属性
                    AttributeInstance scaleAttr = living.getAttribute(Attributes.SCALE);
                    if (scaleAttr != null) {
                        scaleAttr.setBaseValue(scaleFactor);
                    }
                }
            }
        }

        return next(StandardPorts.FLOW_OUT.getId());
    }
}
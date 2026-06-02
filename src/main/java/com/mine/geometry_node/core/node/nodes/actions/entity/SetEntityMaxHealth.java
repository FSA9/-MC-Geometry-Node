package com.mine.geometry_node.core.node.nodes.actions.entity;

import com.mine.geometry_node.core.engine.blueprint.execution.ExecutionContext;
import com.mine.geometry_node.core.engine.blueprint.execution.ExecutionResult;
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

public class SetEntityMaxHealth extends BaseNode {

    public static final String TYPE_ID = "set_entity_max_health";

    @Override
    public NodeDef getDefaultDefinition() {
        return NodeDef.builder(TYPE_ID, NodeType.ACTION, Component.translatable("geometry_node.node.set_entity_max_health"))
                .addRow(new PortRow(StandardPorts.FLOW_IN.toExec(), StandardPorts.FLOW_OUT.toExec(), UIHint.DEFAULT, null, null))
                .addRow(new PortRow(StandardPorts.ENTITY.toInput(), null, UIHint.DEFAULT, null, null))
                .addRow(new PortRow(StandardPorts.VALUE.toInput(), null, UIHint.INPUT, null, null))
                .build();
    }

    @Override
    public ExecutionResult execute(ExecutionContext context) {
        List<Entity> entities = getInputList(context, StandardPorts.ENTITY.getId(), Entity.class);
        Float maxHealth = getInput(context, StandardPorts.VALUE.getId(), Float.class);

        if (maxHealth != null && !entities.isEmpty()) {
            for (Entity entity : entities) {
                if (entity instanceof LivingEntity living) {
                    AttributeInstance maxHealthAttr = living.getAttribute(Attributes.MAX_HEALTH);
                    if (maxHealthAttr != null) {
                        // 修改最大生命值的基础属性
                        maxHealthAttr.setBaseValue(maxHealth);

                        // 防溢出保护：如果当前生命值大于了新设置的最大生命值，强制回调当前生命值
                        if (living.getHealth() > maxHealth) {
                            living.setHealth(maxHealth);
                        }
                    }
                }
            }
        }

        return next(StandardPorts.FLOW_OUT.getId());
    }
}
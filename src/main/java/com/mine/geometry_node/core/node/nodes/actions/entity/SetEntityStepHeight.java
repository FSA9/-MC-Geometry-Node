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
import net.minecraft.world.entity.ai.attributes.AttributeInstance;
import net.minecraft.world.entity.ai.attributes.Attributes;

import java.util.List;

public class SetEntityStepHeight extends BaseNode {

    public static final String TYPE_ID = "set_entity_step_height";

    @Override
    public NodeDef getDefaultDefinition() {
        return NodeDef.builder(TYPE_ID, NodeType.ACTION, Component.translatable("geometry_node.node." + TYPE_ID))
                .addRow(new PortRow(StandardPorts.FLOW_IN.toExec(), StandardPorts.FLOW_OUT.toExec(), UIHint.DEFAULT, null, null))
                .addRow(new PortRow(StandardPorts.ENTITY.toInput(), null, UIHint.DEFAULT, null, null))
                // 默认 0.6f 是原版人形生物的步高
                .addRow(new PortRow(StandardPorts.VALUE.toInput(0.6f), null, UIHint.INPUT, null, null))
                .build();
    }

    @Override
    public ExecutionResult execute(ExecutionContext context) {
        List<Entity> entities = getInputList(context, StandardPorts.ENTITY.getId(), Entity.class);
        Float stepHeight = getInput(context, StandardPorts.VALUE.getId(), Float.class);

        if (stepHeight != null && stepHeight >= 0.0f && !entities.isEmpty()) {
            for (Entity entity : entities) {
                if (entity instanceof LivingEntity living) {
                    AttributeInstance stepAttr = living.getAttribute(Attributes.STEP_HEIGHT);
                    if (stepAttr != null) {
                        stepAttr.setBaseValue(stepHeight);
                    }
                }
            }
        }
        return next(StandardPorts.FLOW_OUT.getId());
    }
}
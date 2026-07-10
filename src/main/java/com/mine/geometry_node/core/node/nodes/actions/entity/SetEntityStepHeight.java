package com.mine.geometry_node.core.node.nodes.actions.entity;

import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.ai.attributes.AttributeInstance;
import net.minecraft.world.entity.ai.attributes.Attributes;

public class SetEntityStepHeight extends AbstractSetEntityFloatProperty {

    public static final String TYPE_ID = "set_entity_step_height";

    public SetEntityStepHeight() {
        super(TYPE_ID, 0.6f, 0.0f, (entity, stepHeight) -> {
            if (stepHeight >= 0.0f && entity instanceof LivingEntity living) {
                AttributeInstance stepAttr = living.getAttribute(Attributes.STEP_HEIGHT);
                if (stepAttr != null) {
                    stepAttr.setBaseValue(stepHeight);
                }
            }
        });
    }
}

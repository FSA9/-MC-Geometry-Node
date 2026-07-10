package com.mine.geometry_node.core.node.nodes.actions.entity;

import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.ai.attributes.AttributeInstance;
import net.minecraft.world.entity.ai.attributes.Attributes;

public class SetEntitySize extends AbstractSetEntityFloatProperty {

    public static final String TYPE_ID = "set_entity_size";

    public SetEntitySize() {
        super(TYPE_ID, (entity, scaleFactor) -> {
            if (scaleFactor > 0.0f && entity instanceof LivingEntity living) {
                AttributeInstance scaleAttr = living.getAttribute(Attributes.SCALE);
                if (scaleAttr != null) {
                    scaleAttr.setBaseValue(scaleFactor);
                }
            }
        });
    }
}

package com.mine.geometry_node.core.node.nodes.actions.entity;

import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.ai.attributes.AttributeInstance;
import net.minecraft.world.entity.ai.attributes.Attributes;

public class SetEntityGravity extends AbstractSetEntityFloatProperty {

    public static final String TYPE_ID = "set_entity_gravity";

    public SetEntityGravity() {
        super(TYPE_ID, 0.08f, (entity, gravityValue) -> {
            if (entity instanceof LivingEntity living) {
                AttributeInstance gravityAttr = living.getAttribute(Attributes.GRAVITY);
                if (gravityAttr != null) {
                    gravityAttr.setBaseValue(gravityValue);
                }
            }
        });
    }
}

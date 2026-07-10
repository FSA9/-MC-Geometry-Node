package com.mine.geometry_node.core.node.nodes.actions.entity;

import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.ai.attributes.AttributeInstance;
import net.minecraft.world.entity.ai.attributes.Attributes;

public class SetEntityMoveSpeed extends AbstractSetEntityFloatProperty {

    public static final String TYPE_ID = "set_entity_move_speed";

    public SetEntityMoveSpeed() {
        super(TYPE_ID, 0.25f, 0.0f, (entity, speed) -> {
            if (speed >= 0.0f && entity instanceof LivingEntity living) {
                AttributeInstance speedAttr = living.getAttribute(Attributes.MOVEMENT_SPEED);
                if (speedAttr != null) {
                    speedAttr.setBaseValue(speed);
                }
            }
        });
    }
}

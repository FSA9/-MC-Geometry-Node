package com.mine.geometry_node.core.node.nodes.actions.entity;

import net.minecraft.world.entity.LivingEntity;

public class SetEntityHealth extends AbstractSetEntityFloatProperty {

    public static final String TYPE_ID = "set_entity_health";

    public SetEntityHealth() {
        super(TYPE_ID, (entity, health) -> {
            if (entity instanceof LivingEntity living) {
                living.setHealth(health);
            }
        });
    }
}

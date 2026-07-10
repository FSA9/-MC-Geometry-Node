package com.mine.geometry_node.core.node.nodes.actions.entity;

import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.ai.attributes.AttributeInstance;
import net.minecraft.world.entity.ai.attributes.Attributes;

public class SetEntityMaxHealth extends AbstractSetEntityFloatProperty {

    public static final String TYPE_ID = "set_entity_max_health";

    public SetEntityMaxHealth() {
        super(TYPE_ID, (entity, maxHealth) -> {
            if (entity instanceof LivingEntity living) {
                AttributeInstance maxHealthAttr = living.getAttribute(Attributes.MAX_HEALTH);
                if (maxHealthAttr != null) {
                    maxHealthAttr.setBaseValue(maxHealth);

                    if (living.getHealth() > maxHealth) {
                        living.setHealth(maxHealth);
                    }
                }
            }
        });
    }
}

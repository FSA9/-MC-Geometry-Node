package com.mine.geometry_node.core.node.nodes.actions.entity;

import net.minecraft.world.entity.Mob;

public class SetEntityNoAI extends AbstractSetEntityBooleanProperty {
    public static final String TYPE_ID = "set_entity_no_ai";

    public SetEntityNoAI() {
        super(TYPE_ID, true, (entity, value) -> {
            if (entity instanceof Mob mob) {
                mob.setNoAi(value);
            }
        });
    }
}

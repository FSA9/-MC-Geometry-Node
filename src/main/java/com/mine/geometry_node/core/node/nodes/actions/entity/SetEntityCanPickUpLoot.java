package com.mine.geometry_node.core.node.nodes.actions.entity;

import net.minecraft.world.entity.Mob;

public class SetEntityCanPickUpLoot extends AbstractSetEntityBooleanProperty {
    public static final String TYPE_ID = "set_entity_can_pick_up_loot";

    public SetEntityCanPickUpLoot() {
        super(TYPE_ID, true, (entity, value) -> {
            if (entity instanceof Mob mob) {
                mob.setCanPickUpLoot(value);
            }
        });
    }
}

package com.mine.geometry_node.core.node.nodes.actions.entity;

public class SetInvulnerableTicks extends AbstractSetEntityIntegerProperty {

    public static final String TYPE_ID = "set_invulnerable_ticks";

    public SetInvulnerableTicks() {
        super(TYPE_ID, 0, 0, (entity, ticks) -> {
            if (ticks >= 0) {
                entity.invulnerableTime = ticks;
            }
        });
    }
}

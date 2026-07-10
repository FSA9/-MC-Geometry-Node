package com.mine.geometry_node.core.node.nodes.actions.entity;

public class SetEntityInvulnerable extends AbstractSetEntityBooleanProperty {
    public static final String TYPE_ID = "set_entity_invulnerable";

    public SetEntityInvulnerable() {
        super(TYPE_ID, true, (entity, value) -> entity.setInvulnerable(value));
    }
}

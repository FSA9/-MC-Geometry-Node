package com.mine.geometry_node.core.node.nodes.actions.entity;

public class SetEntityNoGravity extends AbstractSetEntityBooleanProperty {
    public static final String TYPE_ID = "set_entity_no_gravity";

    public SetEntityNoGravity() {
        super(TYPE_ID, true, (entity, value) -> entity.setNoGravity(value));
    }
}

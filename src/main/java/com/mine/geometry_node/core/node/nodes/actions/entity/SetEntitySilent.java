package com.mine.geometry_node.core.node.nodes.actions.entity;

public class SetEntitySilent extends AbstractSetEntityBooleanProperty {

    public static final String TYPE_ID = "set_entity_silent";

    public SetEntitySilent() {
        super(TYPE_ID, true, (entity, value) -> entity.setSilent(value));
    }
}

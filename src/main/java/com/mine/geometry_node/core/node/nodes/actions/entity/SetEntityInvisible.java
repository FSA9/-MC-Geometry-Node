package com.mine.geometry_node.core.node.nodes.actions.entity;

public class SetEntityInvisible extends AbstractSetEntityBooleanProperty {

    public static final String TYPE_ID = "set_entity_invisible";

    public SetEntityInvisible() {
        super(TYPE_ID, true, (entity, value) -> entity.setInvisible(value));
    }
}

package com.mine.geometry_node.core.node.nodes.actions.entity;

public class SetEntityGlowing extends AbstractSetEntityBooleanProperty {

    public static final String TYPE_ID = "set_entity_glowing";

    public SetEntityGlowing() {
        super(TYPE_ID, true, (entity, value) -> entity.setGlowingTag(value));
    }
}

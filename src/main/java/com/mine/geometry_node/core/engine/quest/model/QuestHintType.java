package com.mine.geometry_node.core.engine.quest.model;

import org.jetbrains.annotations.Nullable;

public enum QuestHintType {
    NONE("none"),
    ITEM_STACK("item_stack"),
    BLOCK("block"),
    ENTITY("entity");

    private final String id;

    QuestHintType(String id) {
        this.id = id;
    }

    public String id() {
        return id;
    }

    public static QuestHintType fromId(@Nullable String id) {
        if (id != null) {
            for (QuestHintType value : values()) {
                if (value.id.equalsIgnoreCase(id.trim())) return value;
            }
        }
        return NONE;
    }
}

package com.mine.geometry_node.core.engine.system.quest.model;

import java.util.List;

/** Independent, persisted quest gates; these do not identify or evaluate graph nodes. */
public enum QuestConditionKind {
    VISIBILITY,
    ACCEPTANCE,
    COMPLETION;

    public static List<QuestConditionKind> all() {
        return List.of(values());
    }
}

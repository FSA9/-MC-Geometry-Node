package com.mine.geometry_node.core.engine.quest.model;

import java.util.List;

public enum QuestConditionKind {
    VISIBILITY("quest_visibility_conditions"),
    ACCEPTANCE("quest_accept_conditions"),
    COMPLETION("quest_completion_conditions");

    private final String nodeTypeId;

    QuestConditionKind(String nodeTypeId) {
        this.nodeTypeId = nodeTypeId;
    }

    public String nodeTypeId() {
        return nodeTypeId;
    }

    public static List<QuestConditionKind> all() {
        return List.of(values());
    }
}

package com.mine.geometry_node.core.node.nodes.quest;

import com.mine.geometry_node.core.engine.quest.model.QuestConditionKind;

public final class QuestVisibilityConditions extends BaseQuestConditionsNode {
    public static final String TYPE_ID = QuestConditionKind.VISIBILITY.nodeTypeId();

    public QuestVisibilityConditions() {
        super(QuestConditionKind.VISIBILITY, "geometry_node.node.quest_visibility_conditions");
    }
}

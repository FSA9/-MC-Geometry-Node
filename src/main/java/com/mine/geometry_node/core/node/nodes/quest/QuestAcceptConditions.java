package com.mine.geometry_node.core.node.nodes.quest;

import com.mine.geometry_node.core.engine.system.quest.model.QuestConditionKind;

public final class QuestAcceptConditions extends BaseQuestConditionsNode {
    public static final String TYPE_ID = QuestConditionKind.ACCEPTANCE.nodeTypeId();

    public QuestAcceptConditions() {
        super(QuestConditionKind.ACCEPTANCE, "geometry_node.node.quest_accept_conditions");
    }
}

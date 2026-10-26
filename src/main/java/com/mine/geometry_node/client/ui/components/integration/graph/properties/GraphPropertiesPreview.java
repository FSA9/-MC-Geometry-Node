package com.mine.geometry_node.client.ui.components.integration.graph.properties;

import com.mine.geometry_node.core.engine.system.quest.model.QuestConditionOverview;
import com.mine.geometry_node.core.engine.system.quest.model.QuestDefinition;

/** Host-supplied preview action; independent of graph-properties storage. */
@FunctionalInterface
public interface GraphPropertiesPreview {
    void previewQuest(String taskKey, QuestDefinition definition, QuestConditionOverview conditionOverview);
}

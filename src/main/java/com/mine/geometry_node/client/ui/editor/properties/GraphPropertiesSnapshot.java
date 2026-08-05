package com.mine.geometry_node.client.ui.editor.properties;

import com.mine.geometry_node.core.engine.graph.GraphType;
import com.mine.geometry_node.core.engine.quest.model.QuestDefinition;
import com.mine.geometry_node.core.engine.quest.model.QuestConditionOverview;

import java.util.List;

public record GraphPropertiesSnapshot(
        String fileName,
        String graphTypeId,
        String comment,
        List<String> tags,
        QuestDefinition questDefinition,
        QuestConditionOverview conditionOverview) {

    public GraphPropertiesSnapshot {
        fileName = fileName != null ? fileName : "";
        graphTypeId = GraphType.normalizeId(graphTypeId);
        comment = comment != null ? comment : "";
        tags = tags != null ? List.copyOf(tags) : List.of();
        questDefinition = questDefinition != null ? questDefinition : QuestDefinition.EMPTY;
        conditionOverview = conditionOverview != null ? conditionOverview : QuestConditionOverview.EMPTY;
    }

    public GraphPropertiesSnapshot withMetadata(String updatedTypeId, String updatedComment, List<String> updatedTags,
                                                QuestDefinition updatedQuestDefinition) {
        return new GraphPropertiesSnapshot(
                fileName,
                updatedTypeId,
                updatedComment,
                updatedTags,
                updatedQuestDefinition,
                conditionOverview);
    }
}

package com.mine.geometry_node.client.ui.components.integration.graph.properties;

import com.mine.geometry_node.core.engine.graph.GraphType;
import com.mine.geometry_node.core.engine.system.quest.model.QuestDefinition;

import java.util.List;

public record GraphPropertiesSnapshot(
        String fileName,
        String graphTypeId,
        String comment,
        List<String> tags,
        QuestDefinition questDefinition) {

    public GraphPropertiesSnapshot {
        fileName = fileName != null ? fileName : "";
        graphTypeId = GraphType.normalizeId(graphTypeId);
        comment = comment != null ? comment : "";
        tags = tags != null ? List.copyOf(tags) : List.of();
        questDefinition = questDefinition != null ? questDefinition : QuestDefinition.EMPTY;
    }

    public GraphPropertiesSnapshot withMetadata(String updatedTypeId, String updatedComment, List<String> updatedTags,
                                                QuestDefinition updatedQuestDefinition) {
        return new GraphPropertiesSnapshot(
                fileName,
                updatedTypeId,
                updatedComment,
                updatedTags,
                updatedQuestDefinition);
    }
}

package com.mine.geometry_node.client.ui.editor.properties;

import com.mine.geometry_node.core.engine.graph.GraphType;

import java.util.List;

public record GraphPropertiesSnapshot(
        String fileName,
        String graphTypeId,
        String comment,
        List<String> tags) {

    public GraphPropertiesSnapshot {
        fileName = fileName != null ? fileName : "";
        graphTypeId = GraphType.normalizeId(graphTypeId);
        comment = comment != null ? comment : "";
        tags = tags != null ? List.copyOf(tags) : List.of();
    }

    public GraphPropertiesSnapshot withMetadata(String updatedTypeId, String updatedComment, List<String> updatedTags) {
        return new GraphPropertiesSnapshot(fileName, updatedTypeId, updatedComment, updatedTags);
    }
}

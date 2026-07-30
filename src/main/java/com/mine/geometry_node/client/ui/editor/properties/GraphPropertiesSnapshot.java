package com.mine.geometry_node.client.ui.editor.properties;

import com.mine.geometry_node.core.engine.graph.GraphKind;

import java.util.List;

public record GraphPropertiesSnapshot(
        String fileName,
        GraphKind kind,
        String comment,
        List<String> tags) {

    public GraphPropertiesSnapshot {
        fileName = fileName != null ? fileName : "";
        kind = kind != null ? kind : GraphKind.UNKNOWN;
        comment = comment != null ? comment : "";
        tags = tags != null ? List.copyOf(tags) : List.of();
    }

    public GraphPropertiesSnapshot withMetadata(String updatedComment, List<String> updatedTags) {
        return new GraphPropertiesSnapshot(fileName, kind, updatedComment, updatedTags);
    }
}

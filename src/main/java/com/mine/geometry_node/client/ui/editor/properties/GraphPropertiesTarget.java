package com.mine.geometry_node.client.ui.editor.properties;

import com.mine.geometry_node.core.engine.quest.model.QuestDefinition;

import java.util.List;
import java.util.concurrent.CompletionStage;

/**
 * Storage boundary for the shared graph-properties page.
 */
public interface GraphPropertiesTarget {
    CompletionStage<GraphPropertiesSnapshot> load();

    CompletionStage<Void> save(String graphTypeId, String comment, List<String> tags,
                               QuestDefinition questDefinition);

    default String normalizeComment(String comment) {
        return comment != null ? comment : "";
    }

    default void setChangeListener(Runnable listener) {
    }

    default void onSaveSucceeded(GraphPropertiesSnapshot snapshot) {
    }
}

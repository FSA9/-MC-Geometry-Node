package com.mine.geometry_node.core.engine.dialogue;

import org.jetbrains.annotations.Nullable;

import java.util.LinkedHashMap;
import java.util.Map;

/**
 * Server-side payload for one selectable dialogue choice.
 */
public class DialogueChoicePayload {

    private final String id;
    private final String text;
    @Nullable
    private final String targetNodeId;
    private final boolean enabled;
    private final Map<String, Object> metadata;

    public DialogueChoicePayload(String id, String text) {
        this(id, text, null, true, Map.of());
    }

    public DialogueChoicePayload(String id, String text, @Nullable String targetNodeId, boolean enabled, Map<String, Object> metadata) {
        this.id = id;
        this.text = text;
        this.targetNodeId = targetNodeId;
        this.enabled = enabled;
        this.metadata = new LinkedHashMap<>(metadata);
    }

    public String getId() {
        return id;
    }

    public String getText() {
        return text;
    }

    @Nullable
    public String getTargetNodeId() {
        return targetNodeId;
    }

    public boolean isEnabled() {
        return enabled;
    }

    public Map<String, Object> getMetadata() {
        return metadata;
    }
}

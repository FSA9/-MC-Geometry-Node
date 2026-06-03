package com.mine.geometry_node.core.engine.dialogue.payload;

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
    @Nullable
    private final String disabledReason;
    private final Map<String, Object> metadata;

    public DialogueChoicePayload(String id, String text) {
        this(id, text, null, true, Map.of());
    }

    public DialogueChoicePayload(String id, String text, @Nullable String targetNodeId, boolean enabled, Map<String, Object> metadata) {
        this(id, text, targetNodeId, enabled, null, metadata);
    }

    public DialogueChoicePayload(String id,
                                 String text,
                                 @Nullable String targetNodeId,
                                 boolean enabled,
                                 @Nullable String disabledReason,
                                 Map<String, Object> metadata) {
        this.id = id;
        this.text = text;
        this.targetNodeId = targetNodeId;
        this.enabled = enabled;
        this.disabledReason = disabledReason;
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

    @Nullable
    public String getDisabledReason() {
        return disabledReason;
    }

    public Map<String, Object> getMetadata() {
        return metadata;
    }
}

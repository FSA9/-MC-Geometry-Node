package com.mine.geometry_node.core.engine.dialogue.payload;

import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Server-side payload describing the currently visible dialogue page.
 */
public class DialoguePagePayload {

    private final String id;
    private final String text;
    private final String styleId;
    private final List<DialogueChoicePayload> choices;
    @Nullable
    private final String defaultChoiceId;
    private final Map<String, Object> metadata;

    public DialoguePagePayload(String id, String text) {
        this(id, text, "default", List.of(), Map.of());
    }

    public DialoguePagePayload(String id, String text, List<DialogueChoicePayload> choices, Map<String, Object> metadata) {
        this(id, text, "default", choices, metadata);
    }

    public DialoguePagePayload(String id, String text, String styleId, List<DialogueChoicePayload> choices, Map<String, Object> metadata) {
        this.id = id;
        this.text = text;
        this.styleId = styleId == null || styleId.isBlank() ? "default" : styleId;
        this.choices = new ArrayList<>(choices);
        this.defaultChoiceId = resolveDefaultChoiceId(this.choices);
        this.metadata = new LinkedHashMap<>(metadata);
    }

    public String getId() {
        return id;
    }

    public String getText() {
        return text;
    }

    public String getStyleId() {
        return styleId;
    }

    public List<DialogueChoicePayload> getChoices() {
        return choices;
    }

    @Nullable
    public String getDefaultChoiceId() {
        return defaultChoiceId;
    }

    public Map<String, Object> getMetadata() {
        return metadata;
    }

    @Nullable
    private static String resolveDefaultChoiceId(List<DialogueChoicePayload> choices) {
        for (DialogueChoicePayload choice : choices) {
            if (choice.isEnabled()) {
                return choice.getId();
            }
        }
        return null;
    }
}

package com.mine.geometry_node.core.engine.dialogue;

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
    @Nullable
    private final String speaker;
    private final String text;
    private final List<DialogueChoicePayload> choices;
    private final Map<String, Object> metadata;

    public DialoguePagePayload(String id, @Nullable String speaker, String text) {
        this(id, speaker, text, List.of(), Map.of());
    }

    public DialoguePagePayload(String id, @Nullable String speaker, String text, List<DialogueChoicePayload> choices, Map<String, Object> metadata) {
        this.id = id;
        this.speaker = speaker;
        this.text = text;
        this.choices = new ArrayList<>(choices);
        this.metadata = new LinkedHashMap<>(metadata);
    }

    public String getId() {
        return id;
    }

    @Nullable
    public String getSpeaker() {
        return speaker;
    }

    public String getText() {
        return text;
    }

    public List<DialogueChoicePayload> getChoices() {
        return choices;
    }

    public Map<String, Object> getMetadata() {
        return metadata;
    }
}

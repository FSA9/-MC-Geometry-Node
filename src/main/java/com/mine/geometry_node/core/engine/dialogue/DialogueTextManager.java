package com.mine.geometry_node.core.engine.dialogue;

import org.jetbrains.annotations.Nullable;

import java.util.LinkedHashMap;
import java.util.Map;

/**
 * Minimal text lookup service for dialogue payloads.
 */
public class DialogueTextManager {

    private final Map<String, String> texts = new LinkedHashMap<>();

    public void registerText(String key, String text) {
        texts.put(key, text);
    }

    @Nullable
    public String getText(String key) {
        return texts.get(key);
    }

    public String resolveText(String key, String fallback) {
        return texts.getOrDefault(key, fallback);
    }

    public void clear() {
        texts.clear();
    }
}

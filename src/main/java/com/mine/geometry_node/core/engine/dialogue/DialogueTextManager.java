package com.mine.geometry_node.core.engine.dialogue;

import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import org.jetbrains.annotations.Nullable;

import java.io.Reader;
import java.util.LinkedHashMap;
import java.util.Map;

/**
 * Minimal text lookup service for dialogue payloads.
 */
public class DialogueTextManager {
    public static final String CONTINUE_KEY = "geometry_node.dialogue.continue";

    private final Map<String, String> texts = new LinkedHashMap<>();

    public DialogueTextManager() {
        registerDefaults();
    }

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

    public void loadJsonMap(Reader reader) {
        JsonElement root = JsonParser.parseReader(reader);
        if (root == null || !root.isJsonObject()) {
            return;
        }
        JsonObject object = root.getAsJsonObject();
        for (Map.Entry<String, JsonElement> entry : object.entrySet()) {
            JsonElement value = entry.getValue();
            if (value != null && value.isJsonPrimitive()) {
                texts.put(entry.getKey(), value.getAsString());
            }
        }
    }

    public void clear() {
        texts.clear();
        registerDefaults();
    }

    private void registerDefaults() {
        texts.put(CONTINUE_KEY, "Continue");
    }
}

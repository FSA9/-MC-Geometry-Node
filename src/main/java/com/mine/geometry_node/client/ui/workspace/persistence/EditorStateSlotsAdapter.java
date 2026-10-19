package com.mine.geometry_node.client.ui.workspace.persistence;

import com.google.gson.Gson;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import com.google.gson.TypeAdapter;
import com.google.gson.stream.JsonReader;
import com.google.gson.stream.JsonWriter;

import java.io.IOException;
import java.math.BigDecimal;
import java.util.LinkedHashMap;
import java.util.Map;

/** Tolerant envelope decoding: one corrupt editor must not discard the workspace. */
public final class EditorStateSlotsAdapter extends TypeAdapter<Map<String, Map<String, EditorSessionState.StateSlot>>> {
    private static final Gson GSON = new com.google.gson.GsonBuilder().serializeNulls().create();

    @Override
    public Map<String, Map<String, EditorSessionState.StateSlot>> read(JsonReader reader) throws IOException {
        JsonElement tree = JsonParser.parseReader(reader);
        Map<String, Map<String, EditorSessionState.StateSlot>> scopes = new LinkedHashMap<>();
        if (!tree.isJsonObject()) return scopes;
        for (var scope : tree.getAsJsonObject().entrySet()) {
            Map<String, EditorSessionState.StateSlot> entries = new LinkedHashMap<>();
            scopes.put(scope.getKey(), entries);
            if (!scope.getValue().isJsonObject()) continue;
            for (var entry : scope.getValue().getAsJsonObject().entrySet()) {
                var slot = new EditorSessionState.StateSlot();
                // Preserve the full unknown envelope, including future extension fields.
                slot.rawEnvelope = entry.getValue().deepCopy();
                if (entry.getValue().isJsonObject()) {
                    JsonObject object = entry.getValue().getAsJsonObject();
                    slot.value = object.get("value");
                    try {
                        JsonElement version = object.get("version");
                        if (version != null && version.isJsonPrimitive() && version.getAsJsonPrimitive().isNumber()) {
                            BigDecimal number = version.getAsBigDecimal();
                            if (number.signum() > 0 && number.stripTrailingZeros().scale() <= 0) {
                                slot.version = number.compareTo(BigDecimal.valueOf(Integer.MAX_VALUE)) >= 0
                                        ? Integer.MAX_VALUE : number.intValueExact();
                            }
                        }
                    } catch (RuntimeException ignored) { /* Invalid version reads as unsupported. */ }
                }
                entries.put(entry.getKey(), slot);
            }
        }
        return scopes;
    }

    @Override
    public void write(JsonWriter writer, Map<String, Map<String, EditorSessionState.StateSlot>> scopes) throws IOException {
        JsonObject tree = new JsonObject();
        if (scopes != null) for (var scope : scopes.entrySet()) {
            JsonObject entries = new JsonObject();
            tree.add(scope.getKey(), entries);
            if (scope.getValue() == null) continue;
            for (var entry : scope.getValue().entrySet()) {
                var slot = entry.getValue();
                if (slot == null) {
                    entries.add(entry.getKey(), com.google.gson.JsonNull.INSTANCE);
                } else if (slot.rawEnvelope != null) {
                    entries.add(entry.getKey(), slot.rawEnvelope);
                } else {
                    JsonObject envelope = new JsonObject();
                    envelope.addProperty("version", slot.version);
                    envelope.add("value", slot.value);
                    entries.add(entry.getKey(), envelope);
                }
            }
        }
        boolean serializeNulls = writer.getSerializeNulls();
        try {
            writer.setSerializeNulls(true);
            GSON.toJson(tree, writer);
        } finally {
            writer.setSerializeNulls(serializeNulls);
        }
    }
}

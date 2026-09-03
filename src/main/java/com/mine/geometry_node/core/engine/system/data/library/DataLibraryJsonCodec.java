package com.mine.geometry_node.core.engine.system.data.library;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import com.mine.geometry_node.core.node.definition.port.PortType;
import net.minecraft.core.HolderLookup;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/** Codec for the type-first, versionless Data Library JSON document. */
public final class DataLibraryJsonCodec {
    private static final Gson GSON = new GsonBuilder().setPrettyPrinting().disableHtmlEscaping().create();

    private DataLibraryJsonCodec() {
    }

    public static String encode(DataLibraryDocument document, HolderLookup.Provider registries) {
        JsonObject root = new JsonObject();
        for (PortType type : PortType.values()) {
            Map<UUID, DataLibraryEntry> entries = document.entries(type);
            if (entries.isEmpty()) continue;

            if (!DataLibraryTypes.supports(type)) {
                throw new IllegalArgumentException("Unsupported Data Library type: " + type);
            }
            JsonObject group = new JsonObject();
            for (DataLibraryEntry entry : entries.values()) {
                JsonObject jsonEntry = new JsonObject();
                jsonEntry.addProperty("key", entry.key());
                jsonEntry.add("value", DataLibraryValueCodec.encode(type, entry.value(), registries));
                group.add(entry.id().toString(), jsonEntry);
            }
            root.add(type.name(), group);
        }
        return GSON.toJson(root);
    }

    public static DataLibraryLoadResult decode(String json, HolderLookup.Provider registries) {
        JsonElement parsed = JsonParser.parseString(json == null || json.isBlank() ? "{}" : json);
        if (!parsed.isJsonObject()) {
            throw new IllegalArgumentException("Data Library root must be a JSON object");
        }

        DataLibraryDocument document = new DataLibraryDocument();
        List<DataLibraryDiagnostic> diagnostics = new ArrayList<>();
        for (Map.Entry<String, JsonElement> groupEntry : parsed.getAsJsonObject().entrySet()) {
            PortType type = parseType(groupEntry.getKey());
            if (!DataLibraryTypes.supports(type)) {
                diagnostics.add(new DataLibraryDiagnostic(groupEntry.getKey(), "Unsupported Data Library type"));
                continue;
            }
            if (!groupEntry.getValue().isJsonObject()) {
                diagnostics.add(new DataLibraryDiagnostic(groupEntry.getKey(), "Type group must be an object"));
                continue;
            }
            decodeGroup(document, type, groupEntry.getValue().getAsJsonObject(), registries, diagnostics);
        }
        return new DataLibraryLoadResult(document, diagnostics);
    }

    private static void decodeGroup(DataLibraryDocument document, PortType type, JsonObject group,
                                    HolderLookup.Provider registries,
                                    List<DataLibraryDiagnostic> diagnostics) {
        for (Map.Entry<String, JsonElement> rawEntry : group.entrySet()) {
            String path = type.name() + "." + rawEntry.getKey();
            try {
                UUID id = UUID.fromString(rawEntry.getKey());
                if (!rawEntry.getValue().isJsonObject()) {
                    throw new IllegalArgumentException("Entry must be an object");
                }
                JsonObject jsonEntry = rawEntry.getValue().getAsJsonObject();
                JsonElement keyElement = jsonEntry.get("key");
                // One-time migration for databases written before key became the public identity.
                if (keyElement == null) keyElement = jsonEntry.get("name");
                if (keyElement == null || !keyElement.isJsonPrimitive()
                        || !keyElement.getAsJsonPrimitive().isString()) {
                    throw new IllegalArgumentException("Entry key must be a string");
                }
                // An omitted value and an explicit JSON null both represent the Java null value.
                Object value = DataLibraryValueCodec.decode(type, jsonEntry.get("value"), registries);
                String key = keyElement.getAsString();
                document.put(type, new DataLibraryEntry(id, key, value));
            } catch (RuntimeException exception) {
                diagnostics.add(new DataLibraryDiagnostic(path, readableMessage(exception)));
            }
        }
    }

    private static PortType parseType(String name) {
        try {
            return PortType.valueOf(name);
        } catch (IllegalArgumentException exception) {
            return null;
        }
    }

    private static String readableMessage(RuntimeException exception) {
        String message = exception.getMessage();
        return message == null || message.isBlank() ? exception.getClass().getSimpleName() : message;
    }
}

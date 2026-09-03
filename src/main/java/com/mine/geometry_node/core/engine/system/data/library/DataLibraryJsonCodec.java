package com.mine.geometry_node.core.engine.system.data.library;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.JsonElement;
import com.google.gson.JsonNull;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import com.mine.geometry_node.core.node.definition.port.PortType;
import net.minecraft.core.HolderLookup;

import java.util.ArrayList;
import java.util.Iterator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/** Codec for the versionless, single-file Data Library virtual tree. */
public final class DataLibraryJsonCodec {
    private static final Gson GSON = new GsonBuilder().setPrettyPrinting().disableHtmlEscaping().create();
    private static final String FOLDERS = "folders";
    private static final String ENTRIES = "entries";
    private static final String EXPECTED_FINGERPRINTS = "expected_fingerprints";

    private DataLibraryJsonCodec() {}

    public static String encode(DataLibraryDocument document, HolderLookup.Provider registries) {
        return encode(document, registries, Map.of());
    }

    /** Encodes a transport-only mutation document with per-object CAS preconditions. */
    public static String encodeMutation(DataLibraryDocument document, HolderLookup.Provider registries,
                                        Map<UUID, String> expectedFingerprints) {
        if (expectedFingerprints == null || expectedFingerprints.isEmpty()) {
            throw new IllegalArgumentException("Data Library update requires expected fingerprints");
        }
        return encode(document, registries, expectedFingerprints);
    }

    private static String encode(DataLibraryDocument document, HolderLookup.Provider registries,
                                 Map<UUID, String> expectedFingerprints) {
        JsonObject root = new JsonObject();
        JsonObject folders = new JsonObject();
        for (DataLibraryFolder folder : document.folders().values()) {
            JsonObject value = new JsonObject();
            addParent(value, folder.parentId());
            value.addProperty("name", folder.name());
            folders.add(folder.id().toString(), value);
        }
        JsonObject entries = new JsonObject();
        for (DataLibraryEntry entry : document.entries().values()) {
            JsonObject value = new JsonObject();
            addParent(value, entry.parentId());
            value.addProperty("type", entry.type().name());
            value.addProperty("key", entry.key());
            value.add("value", DataLibraryValueCodec.encode(entry.type(), entry.value(), registries));
            entries.add(entry.id().toString(), value);
        }
        root.add(FOLDERS, folders);
        root.add(ENTRIES, entries);
        if (!expectedFingerprints.isEmpty()) {
            JsonObject expected = new JsonObject();
            expectedFingerprints.forEach((id, fingerprint) -> {
                if (id == null || !DataLibraryObjectFingerprint.isValid(fingerprint)) {
                    throw new IllegalArgumentException("Invalid Data Library expected fingerprint");
                }
                expected.addProperty(id.toString(), fingerprint);
            });
            root.add(EXPECTED_FINGERPRINTS, expected);
        }
        return GSON.toJson(root);
    }

    public static DataLibraryLoadResult decode(String json, HolderLookup.Provider registries) {
        JsonElement parsed = JsonParser.parseString(json == null || json.isBlank() ? "{}" : json);
        if (!parsed.isJsonObject()) throw new IllegalArgumentException("Data Library root must be a JSON object");
        JsonObject root = parsed.getAsJsonObject();
        if (!root.has(FOLDERS) && !root.has(ENTRIES)) return decodeLegacy(root, registries);

        DataLibraryDocument document = new DataLibraryDocument();
        List<DataLibraryDiagnostic> diagnostics = new ArrayList<>();
        decodeFolders(document, objectMember(root, FOLDERS, diagnostics), diagnostics);
        decodeEntries(document, objectMember(root, ENTRIES, diagnostics), registries, diagnostics);
        Map<UUID, String> expected = decodeExpectedFingerprints(root.get(EXPECTED_FINGERPRINTS), diagnostics);
        return new DataLibraryLoadResult(document, diagnostics, expected);
    }

    private static void decodeFolders(DataLibraryDocument document, JsonObject values,
                                      List<DataLibraryDiagnostic> diagnostics) {
        Map<UUID, RawFolder> remaining = new LinkedHashMap<>();
        for (Map.Entry<String, JsonElement> raw : values.entrySet()) {
            String path = FOLDERS + "." + raw.getKey();
            try {
                UUID id = UUID.fromString(raw.getKey());
                JsonObject value = requireObject(raw.getValue(), "Folder must be an object");
                remaining.put(id, new RawFolder(id, readParent(value), requireString(value, "name")));
            } catch (RuntimeException exception) {
                diagnostics.add(new DataLibraryDiagnostic(path, readableMessage(exception)));
            }
        }
        boolean progressed;
        do {
            progressed = false;
            Iterator<RawFolder> iterator = remaining.values().iterator();
            while (iterator.hasNext()) {
                RawFolder raw = iterator.next();
                if (raw.parentId() != null && !document.folders().containsKey(raw.parentId())) continue;
                try {
                    document.putFolder(new DataLibraryFolder(raw.id(), raw.parentId(), raw.name()));
                } catch (RuntimeException exception) {
                    diagnostics.add(new DataLibraryDiagnostic(FOLDERS + "." + raw.id(), readableMessage(exception)));
                }
                iterator.remove();
                progressed = true;
            }
        } while (progressed && !remaining.isEmpty());
        remaining.values().forEach(raw -> diagnostics.add(new DataLibraryDiagnostic(
                FOLDERS + "." + raw.id(), "Unknown parent folder or folder cycle: " + raw.parentId())));
    }

    private static void decodeEntries(DataLibraryDocument document, JsonObject values,
                                      HolderLookup.Provider registries,
                                      List<DataLibraryDiagnostic> diagnostics) {
        for (Map.Entry<String, JsonElement> raw : values.entrySet()) {
            String path = ENTRIES + "." + raw.getKey();
            try {
                UUID id = UUID.fromString(raw.getKey());
                if (document.folders().containsKey(id)) throw new IllegalArgumentException("UUID is already used by a folder");
                JsonObject value = requireObject(raw.getValue(), "Entry must be an object");
                UUID parentId = readParent(value);
                PortType type = parseType(requireString(value, "type"));
                if (!DataLibraryTypes.supports(type)) throw new IllegalArgumentException("Unsupported Data Library type");
                String key = requireString(value, "key");
                Object decoded = DataLibraryValueCodec.decode(type, value.get("value"), registries);
                document.put(new DataLibraryEntry(id, parentId, type, key, decoded));
            } catch (RuntimeException exception) {
                diagnostics.add(new DataLibraryDiagnostic(path, readableMessage(exception)));
            }
        }
    }

    /** Reads the former type-first layout into the new tree's root directory. */
    private static DataLibraryLoadResult decodeLegacy(JsonObject root, HolderLookup.Provider registries) {
        DataLibraryDocument document = new DataLibraryDocument();
        List<DataLibraryDiagnostic> diagnostics = new ArrayList<>();
        for (Map.Entry<String, JsonElement> group : root.entrySet()) {
            PortType type = parseType(group.getKey());
            if (!DataLibraryTypes.supports(type) || !group.getValue().isJsonObject()) {
                diagnostics.add(new DataLibraryDiagnostic(group.getKey(), "Unsupported Data Library type group"));
                continue;
            }
            for (Map.Entry<String, JsonElement> raw : group.getValue().getAsJsonObject().entrySet()) {
                String path = group.getKey() + "." + raw.getKey();
                try {
                    UUID id = UUID.fromString(raw.getKey());
                    JsonObject value = requireObject(raw.getValue(), "Entry must be an object");
                    JsonElement keyValue = value.has("key") ? value.get("key") : value.get("name");
                    if (keyValue == null || !keyValue.isJsonPrimitive()) throw new IllegalArgumentException("Entry key must be a string");
                    document.put(new DataLibraryEntry(id, null, type, keyValue.getAsString(),
                            DataLibraryValueCodec.decode(type, value.get("value"), registries)));
                } catch (RuntimeException exception) {
                    diagnostics.add(new DataLibraryDiagnostic(path, readableMessage(exception)));
                }
            }
        }
        return new DataLibraryLoadResult(document, diagnostics);
    }

    private static JsonObject objectMember(JsonObject root, String name,
                                           List<DataLibraryDiagnostic> diagnostics) {
        JsonElement value = root.get(name);
        if (value == null || value.isJsonNull()) return new JsonObject();
        if (value.isJsonObject()) return value.getAsJsonObject();
        diagnostics.add(new DataLibraryDiagnostic(name, "Value must be an object"));
        return new JsonObject();
    }

    private static Map<UUID, String> decodeExpectedFingerprints(JsonElement value,
                                                                 List<DataLibraryDiagnostic> diagnostics) {
        if (value == null || value.isJsonNull()) return Map.of();
        if (!value.isJsonObject()) {
            diagnostics.add(new DataLibraryDiagnostic(EXPECTED_FINGERPRINTS, "Value must be an object"));
            return Map.of();
        }
        Map<UUID, String> result = new LinkedHashMap<>();
        for (Map.Entry<String, JsonElement> raw : value.getAsJsonObject().entrySet()) {
            try {
                UUID id = UUID.fromString(raw.getKey());
                if (!raw.getValue().isJsonPrimitive() || !raw.getValue().getAsJsonPrimitive().isString()) {
                    throw new IllegalArgumentException("Fingerprint must be a string");
                }
                String fingerprint = raw.getValue().getAsString();
                if (!DataLibraryObjectFingerprint.isValid(fingerprint)) {
                    throw new IllegalArgumentException("Fingerprint must be a lowercase SHA-256 value");
                }
                result.put(id, fingerprint);
            } catch (RuntimeException exception) {
                diagnostics.add(new DataLibraryDiagnostic(
                        EXPECTED_FINGERPRINTS + "." + raw.getKey(), readableMessage(exception)));
            }
        }
        return Map.copyOf(result);
    }

    private static JsonObject requireObject(JsonElement value, String message) {
        if (value == null || !value.isJsonObject()) throw new IllegalArgumentException(message);
        return value.getAsJsonObject();
    }

    private static String requireString(JsonObject value, String name) {
        JsonElement member = value.get(name);
        if (member == null || !member.isJsonPrimitive() || !member.getAsJsonPrimitive().isString()) {
            throw new IllegalArgumentException(name + " must be a string");
        }
        return member.getAsString();
    }

    private static void addParent(JsonObject value, UUID parentId) {
        if (parentId == null) value.add("parent", JsonNull.INSTANCE);
        else value.addProperty("parent", parentId.toString());
    }

    private static UUID readParent(JsonObject value) {
        JsonElement parent = value.get("parent");
        return parent == null || parent.isJsonNull() ? null : UUID.fromString(parent.getAsString());
    }

    private static PortType parseType(String name) {
        try { return PortType.valueOf(name); }
        catch (IllegalArgumentException exception) { return null; }
    }

    private static String readableMessage(RuntimeException exception) {
        String message = exception.getMessage();
        return message == null || message.isBlank() ? exception.getClass().getSimpleName() : message;
    }

    private record RawFolder(UUID id, UUID parentId, String name) {}
}

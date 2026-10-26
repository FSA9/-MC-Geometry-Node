package com.mine.geometry_node.core.engine.system.faction.storage;

import com.google.gson.*;
import com.google.gson.stream.JsonReader;
import com.google.gson.stream.JsonToken;
import com.mine.geometry_node.core.engine.system.faction.model.*;
import com.mine.geometry_node.core.node.value.RichTextValue;
import com.mine.geometry_node.core.engine.metadata.OwnerMetadata;
import net.minecraft.nbt.*;
import java.nio.charset.StandardCharsets;
import java.io.*;
import java.util.*;

/** Versioned, bounded canonical representation shared by world storage and catalogue transport. */
public final class FactionJsonCodec {
    private static final Gson JSON = new Gson();
    private static final Set<String> TEXT_KEYS = Set.of("type", "version", "plain", "segments");
    private FactionJsonCodec() {}

    public static String encode(FactionCatalog catalog) {
        List<Map<String, Object>> definitions = new ArrayList<>();
        for (var faction : catalog.factions()) {
            Map<String, Object> ranks = new LinkedHashMap<>();
            faction.ranks().forEach((level, label) -> ranks.put(level.toString(), label.toMap()));
            Map<String, Object> definition = new LinkedHashMap<>();
            definition.put("id", faction.id().toString());
            definition.put("name", faction.name().toMap());
            definition.put("description", faction.description().toMap());
            definition.put("relations", stringKeys(faction.relations()));
            definition.put("ranks", ranks);
            // Empty owners must add no bytes when a legacy catalogue is already at the document limit.
            if (!faction.metadata().isEmpty()) definition.put("metadata", encodeMetadata(faction.metadata()));
            definitions.add(definition);
        }
        return bounded(JSON.toJson(Map.of("version", 2, "revision", catalog.revision(), "factions", definitions)));
    }

    public static FactionCatalog decode(String source) {
        Map<String, Object> root = parse(source);
        keys(root, Set.of("version", "revision", "factions"));
        int version = integer(root.get("version"));
        if (version != 1 && version != 2) throw new IllegalArgumentException("Unsupported faction format");
        long revision = longInteger(root.get("revision"));
        List<?> raw = list(root.get("factions"), FactionRules.MAX_FACTIONS);
        List<FactionDefinition> definitions = new ArrayList<>();
        for (Object item : raw) {
            Map<String, Object> faction = dictionary(item);
            keys(faction, version == 2 && faction.containsKey("metadata")
                    ? Set.of("id", "name", "description", "relations", "ranks", "metadata")
                    : Set.of("id", "name", "description", "relations", "ranks"));
            Map<Integer, RichTextValue> ranks = new LinkedHashMap<>();
            Map<String, Object> rawRanks = dictionary(faction.get("ranks"));
            if (rawRanks.size() > FactionRules.MAX_RANKS) throw new IllegalArgumentException("Too many ranks");
            rawRanks.forEach((level, label) -> {
                int value;
                try { value = Integer.parseInt(level); }
                catch (NumberFormatException invalid) { throw new IllegalArgumentException("Invalid rank level", invalid); }
                if (!Integer.toString(value).equals(level) || ranks.putIfAbsent(value, text(label, false)) != null)
                    throw new IllegalArgumentException("Duplicate or noncanonical rank level");
            });
            definitions.add(new FactionDefinition(uuid(faction.get("id")), text(faction.get("name"), false),
                    text(faction.get("description"), true), scores(faction.get("relations"), FactionRules.MAX_FACTIONS), ranks,
                    version == 1 || !faction.containsKey("metadata") ? OwnerMetadata.EMPTY : decodeMetadata(faction.get("metadata"))));
        }
        return new FactionCatalog(revision, definitions);
    }

    // Binary NBT preserves every registered graph value and exact numeric type without decoding at save time.
    private static String encodeMetadata(OwnerMetadata metadata) {
        try {
            ByteArrayOutputStream bytes = new ByteArrayOutputStream();
            NbtIo.write(metadata.save(), new DataOutputStream(bytes));
            return Base64.getEncoder().encodeToString(bytes.toByteArray());
        } catch (IOException failure) {
            throw new IllegalArgumentException("Invalid faction metadata", failure);
        }
    }

    private static OwnerMetadata decodeMetadata(Object value) {
        if (!(value instanceof String encoded) || encoded.length() > 4 * ((OwnerMetadata.MAX_ENCODED_BYTES + 2) / 3))
            throw new IllegalArgumentException("Invalid faction metadata encoding");
        byte[] bytes = Base64.getDecoder().decode(encoded);
        if (bytes.length > OwnerMetadata.MAX_ENCODED_BYTES) throw new IllegalArgumentException("Faction metadata exceeds byte limit");
        try (ByteArrayInputStream input = new ByteArrayInputStream(bytes)) {
            CompoundTag metadata = NbtIo.read(new DataInputStream(input),
                    new NbtAccounter(OwnerMetadata.MAX_ENCODED_BYTES, OwnerMetadata.MAX_ENCODED_DEPTH + 1));
            if (input.available() != 0) throw new IllegalArgumentException("Trailing faction metadata NBT");
            return OwnerMetadata.load(metadata);
        } catch (IOException | RuntimeException failure) {
            throw new IllegalArgumentException("Invalid faction metadata NBT", failure);
        }
    }

    public static Map<String, Object> profileValues(EntityFactionProfile profile) {
        return Map.of("memberships", stringKeys(profile.memberships()), "relations", stringKeys(profile.relations()));
    }

    public static EntityFactionProfile profile(Object value) {
        Map<String, Object> root = dictionary(value);
        keys(root, Set.of("memberships", "relations"));
        return new EntityFactionProfile(integers(root.get("memberships"), FactionRules.MAX_ENTITY_REFERENCES),
                scores(root.get("relations"), FactionRules.MAX_ENTITY_REFERENCES));
    }

    public static String encodeProfile(EntityFactionProfile profile) { return bounded(JSON.toJson(profileValues(profile))); }
    public static EntityFactionProfile decodeProfile(String source) { return profile(parse(source)); }

    public static RichTextValue text(Object value, boolean description) {
        if (value instanceof RichTextValue rich) return FactionRules.text(rich, description);
        Map<String, Object> map = dictionary(value);
        keys(map, TEXT_KEYS);
        if (!RichTextValue.TYPE.equals(map.get("type")) || integer(map.get("version")) != RichTextValue.VERSION
                || !(map.get("plain") instanceof String plain)) throw new IllegalArgumentException("Invalid rich text carrier");
        List<RichTextValue.Segment> segments = new ArrayList<>();
        for (Object raw : list(map.get("segments"), FactionRules.MAX_SEGMENTS)) {
            Map<String, Object> segment = dictionary(raw);
            if (!Set.of("kind", "text", "style").containsAll(segment.keySet())
                    || !RichTextValue.KIND_TEXT.equals(segment.get("kind")) || !(segment.get("text") instanceof String text))
                throw new IllegalArgumentException("Invalid faction text segment");
            segments.add(RichTextValue.Segment.text(text, segment.containsKey("style") ? dictionary(segment.get("style")) : Map.of()));
        }
        return FactionRules.text(new RichTextValue(plain, segments), description);
    }

    public static UUID uuid(Object value) {
        if (value instanceof UUID id) return id;
        if (value instanceof String text) {
            try {
                UUID id = UUID.fromString(text);
                if (id.toString().equals(text)) return id;
            } catch (IllegalArgumentException ignored) { }
        }
        throw new IllegalArgumentException("Expected canonical faction UUID");
    }

    public static int integer(Object value) {
        long number = longInteger(value);
        if (number < Integer.MIN_VALUE || number > Integer.MAX_VALUE) throw new IllegalArgumentException("Integer out of range");
        return (int) number;
    }

    private static long longInteger(Object value) {
        if (!(value instanceof Number number)) throw new IllegalArgumentException("Expected integer");
        try { return new java.math.BigDecimal(number.toString()).longValueExact(); }
        catch (NumberFormatException | ArithmeticException invalid) { throw new IllegalArgumentException("Expected integer", invalid); }
    }

    public static Map<String, Object> dictionary(Object value) {
        if (!(value instanceof Map<?, ?> map) || map.keySet().stream().anyMatch(key -> !(key instanceof String)))
            throw new IllegalArgumentException("Expected faction dictionary");
        @SuppressWarnings("unchecked") Map<String, Object> typed = (Map<String, Object>) map;
        return typed;
    }

    public static List<?> list(Object value, int maximum) {
        if (!(value instanceof List<?> list) || list.size() > maximum) throw new IllegalArgumentException("Invalid faction list");
        return list;
    }

    private static Map<UUID, Integer> scores(Object value, int maximum) {
        Map<UUID, Integer> result = integers(value, maximum);
        result.values().forEach(FactionRules::score);
        return result;
    }

    private static Map<UUID, Integer> integers(Object value, int maximum) {
        Map<String, Object> raw = dictionary(value);
        if (raw.size() > maximum) throw new IllegalArgumentException("Too many faction references");
        Map<UUID, Integer> result = new LinkedHashMap<>();
        raw.forEach((id, number) -> result.put(uuid(id), integer(number)));
        return result;
    }

    private static Map<String, Integer> stringKeys(Map<UUID, Integer> values) {
        Map<String, Integer> result = new LinkedHashMap<>();
        values.forEach((id, number) -> result.put(id.toString(), number));
        return result;
    }

    private static void keys(Map<String, Object> map, Set<String> expected) {
        if (!map.keySet().equals(expected)) throw new IllegalArgumentException("Unexpected faction fields");
    }

    private static Map<String, Object> parse(String source) {
        bounded(source);
        try (JsonReader reader = new JsonReader(new StringReader(source))) {
            reader.setStrictness(Strictness.STRICT);
            Map<String, Object> result = dictionary(readJson(reader, 0));
            if (reader.peek() != JsonToken.END_DOCUMENT) throw new IllegalArgumentException("Trailing faction JSON");
            return result;
        } catch (IOException | IllegalStateException failure) { throw new IllegalArgumentException("Invalid faction JSON", failure); }
    }

    private static Object readJson(JsonReader reader, int depth) throws IOException {
        if (depth > 16) throw new IllegalArgumentException("Faction JSON is too deeply nested");
        if (reader.peek() == JsonToken.BEGIN_OBJECT) {
            reader.beginObject();
            Map<String, Object> map = new LinkedHashMap<>();
            while (reader.hasNext()) {
                String key = reader.nextName();
                if (map.containsKey(key)) throw new IllegalArgumentException("Duplicate faction JSON field");
                map.put(key, readJson(reader, depth + 1));
            }
            reader.endObject();
            return map;
        }
        if (reader.peek() == JsonToken.BEGIN_ARRAY) {
            reader.beginArray();
            List<Object> list = new ArrayList<>();
            while (reader.hasNext()) list.add(readJson(reader, depth + 1));
            reader.endArray();
            return list;
        }
        return switch (reader.peek()) {
            case BOOLEAN -> reader.nextBoolean();
            case NUMBER -> new java.math.BigDecimal(reader.nextString());
            case STRING -> reader.nextString();
            default -> throw new IllegalArgumentException("Invalid faction JSON value");
        };
    }

    private static String bounded(String source) {
        Objects.requireNonNull(source);
        if (source.length() > FactionRules.MAX_DOCUMENT_BYTES || source.getBytes(StandardCharsets.UTF_8).length > FactionRules.MAX_DOCUMENT_BYTES)
            throw new IllegalArgumentException("Faction document exceeds 768 KiB");
        return source;
    }
}

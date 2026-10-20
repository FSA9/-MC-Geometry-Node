package com.mine.geometry_node.client.ui.components.integration.metadata;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.stream.JsonReader;
import com.google.gson.stream.JsonToken;
import com.mine.geometry_node.core.metadata.DataDictionary;

import java.io.IOException;
import java.io.StringReader;
import java.util.*;

/** Detached draft and strict plain-value codec. Registered opaque values remain untouched. */
final class DataDictionaryDraft {
    enum Type {
        STRING, BOOLEAN, INTEGER, LONG, FLOAT, DOUBLE, DICTIONARY, LIST, UUID, READONLY;
        String id() { return name().toLowerCase(Locale.ROOT); }
        boolean editable() { return this != READONLY; }
    }
    private static final Gson JSON = new GsonBuilder().setPrettyPrinting().disableHtmlEscaping().serializeNulls().create();
    private final Map<String, Object> baseline;
    private final LinkedHashMap<String, Object> values;

    DataDictionaryDraft(Map<String, Object> original) {
        // Binding snapshots already own mutable registered leaves. Do not serialize them to strings.
        baseline = Collections.unmodifiableMap(new LinkedHashMap<>(original));
        values = new LinkedHashMap<>(original);
    }
    Map<String, Object> baseline() { return baseline; }
    Map<String, Object> values() { return Collections.unmodifiableMap(values); }
    boolean dirty() { return !baseline.equals(values); }
    void put(String oldKey, String newKey, Object value) {
        Objects.requireNonNull(value, "value");
        Objects.requireNonNull(newKey, "key");
        if (oldKey != null && !values.containsKey(oldKey)) throw new IllegalArgumentException("invalid");
        if (!Objects.equals(oldKey, newKey)) {
            DataDictionary.requireKey(newKey);
            if (newKey.isBlank()) throw new IllegalArgumentException("invalid");
            if (values.containsKey(newKey)) throw new IllegalArgumentException("duplicate");
        }
        if (oldKey != null && !Objects.equals(oldKey, newKey)) {
            LinkedHashMap<String, Object> renamed = new LinkedHashMap<>();
            values.forEach((key, previous) -> renamed.put(key.equals(oldKey) ? newKey : key,
                    key.equals(oldKey) ? value : previous));
            values.clear(); values.putAll(renamed);
        } else values.put(newKey, value);
    }
    void remove(String key) { values.remove(key); }

    static Type type(Object value) {
        if (value instanceof String text) return text.length() <= DataDictionary.MAX_TEXT_LENGTH ? Type.STRING : Type.READONLY;
        if (value instanceof Boolean) return Type.BOOLEAN;
        if (value instanceof Integer) return Type.INTEGER;
        if (value instanceof Long) return Type.LONG;
        if (value instanceof Float) return Type.FLOAT;
        if (value instanceof Double) return Type.DOUBLE;
        if (value instanceof java.util.UUID) return Type.UUID;
        if (value instanceof Map<?, ?> && plain(value, new IdentityHashMap<>(), 0, new int[]{0})) return Type.DICTIONARY;
        if (value instanceof List<?> && plain(value, new IdentityHashMap<>(), 0, new int[]{0})) return Type.LIST;
        return Type.READONLY;
    }
    private static boolean plain(Object value, IdentityHashMap<Object, Boolean> active, int depth, int[] nodes) {
        if (++nodes[0] > DataDictionary.MAX_NODES || depth > DataDictionary.MAX_DEPTH) return false;
        if (value instanceof String text && text.length() > DataDictionary.MAX_TEXT_LENGTH) return false;
        if (value == null || value instanceof String || value instanceof Boolean
                || value instanceof Integer || value instanceof Long
                || value instanceof Float || value instanceof Double) return true;
        if (active.put(value, Boolean.TRUE) != null) return false;
        try {
            if (value instanceof Map<?, ?> map) {
                for (var entry : map.entrySet())
                    if (!(entry.getKey() instanceof String) || !plain(entry.getValue(), active, depth + 1, nodes)) return false;
                return true;
            }
            if (value instanceof List<?> list) {
                for (Object item : list) if (!plain(item, active, depth + 1, nodes)) return false;
                return true;
            }
            return false;
        } finally { active.remove(value); }
    }
    static String format(Object value) {
        Type type = type(value);
        if (type == Type.DICTIONARY || type == Type.LIST) return JSON.toJson(value);
        if (type == Type.READONLY) return value == null ? "null" : value.getClass().getSimpleName();
        return String.valueOf(value);
    }
    static Object parse(Type type, String text, Object previous) {
        if (text.length() > DataDictionary.MAX_TEXT_LENGTH) throw new IllegalArgumentException("invalid");
        try {
            Object value = switch (type) {
                case STRING -> text;
                case BOOLEAN -> switch (text.trim()) {
                    case "true" -> true;
                    case "false" -> false;
                    default -> throw new IllegalArgumentException("invalid");
                };
                case INTEGER -> Integer.valueOf(text.trim());
                case LONG -> Long.valueOf(text.trim());
                case FLOAT -> finiteFloat(text.trim());
                case DOUBLE -> finiteDouble(text.trim());
                case UUID -> java.util.UUID.fromString(text.trim());
                case DICTIONARY, LIST -> parseJson(text, previous);
                case READONLY -> throw new IllegalArgumentException("readonly");
            };
            if (type == Type.DICTIONARY && !(value instanceof Map<?, ?>)
                    || type == Type.LIST && !(value instanceof List<?>)) throw new IllegalArgumentException("invalid");
            DataDictionary.validateStructure(value);
            return value;
        } catch (RuntimeException exception) { throw new IllegalArgumentException("invalid", exception); }
    }
    private static Float finiteFloat(String value) {
        float result = Float.parseFloat(value);
        if (!Float.isFinite(result)) throw new IllegalArgumentException("invalid");
        return result;
    }
    private static Double finiteDouble(String value) {
        double result = Double.parseDouble(value);
        if (!Double.isFinite(result)) throw new IllegalArgumentException("invalid");
        return result;
    }
    private static Object parseJson(String text, Object previous) {
        try (JsonReader reader = new JsonReader(new StringReader(text))) {
            reader.setLenient(false);
            Object result = read(reader, previous, 0, new int[]{0});
            if (reader.peek() != JsonToken.END_DOCUMENT) throw new IllegalArgumentException("invalid");
            return result;
        } catch (IOException exception) { throw new IllegalArgumentException("invalid", exception); }
    }
    private static Object read(JsonReader reader, Object previous, int depth, int[] nodes) throws IOException {
        if (depth > DataDictionary.MAX_DEPTH || ++nodes[0] > DataDictionary.MAX_NODES)
            throw new IllegalArgumentException("invalid");
        return switch (reader.peek()) {
            case BEGIN_OBJECT -> {
                reader.beginObject();
                LinkedHashMap<String, Object> map = new LinkedHashMap<>();
                while (reader.hasNext()) {
                    String key = reader.nextName();
                    DataDictionary.requireKey(key);
                    if (map.containsKey(key)) throw new IllegalArgumentException("duplicate");
                    map.put(key, read(reader, previous instanceof Map<?, ?> old ? old.get(key) : null, depth + 1, nodes));
                }
                reader.endObject(); yield Collections.unmodifiableMap(map);
            }
            case BEGIN_ARRAY -> {
                reader.beginArray(); List<Object> list = new ArrayList<>();
                while (reader.hasNext()) {
                    int index = list.size();
                    Object old = previous instanceof List<?> values && index < values.size() ? values.get(index) : null;
                    list.add(read(reader, old, depth + 1, nodes));
                }
                reader.endArray(); yield Collections.unmodifiableList(list);
            }
            case STRING -> reader.nextString();
            case BOOLEAN -> reader.nextBoolean();
            case NULL -> { reader.nextNull(); yield null; }
            case NUMBER -> {
                String number = reader.nextString();
                if (previous instanceof Integer) yield Integer.valueOf(number);
                if (previous instanceof Long) yield Long.valueOf(number);
                if (previous instanceof Float) yield finiteFloat(number);
                if (previous instanceof Double) yield finiteDouble(number);
                if (number.indexOf('.') >= 0 || number.indexOf('e') >= 0 || number.indexOf('E') >= 0)
                    yield finiteDouble(number);
                long integral = Long.parseLong(number);
                if (integral >= Integer.MIN_VALUE && integral <= Integer.MAX_VALUE) yield (int) integral;
                yield integral;
            }
            default -> throw new IllegalArgumentException("invalid");
        };
    }
    static String summary(Object value) {
        if (value instanceof Map<?, ?> map) return "{ " + map.size() + " }";
        if (value instanceof List<?> list) return "[ " + list.size() + " ]";
        String result = type(value) == Type.READONLY ? format(value) : String.valueOf(value);
        result = result.replace('\n', ' ').replace('\r', ' ');
        return result.length() > 100 ? result.substring(0, 97) + "…" : result;
    }
    static List<String> matching(Map<String, Object> values, String search, Type filter) {
        String needle = search.toLowerCase(Locale.ROOT).trim();
        return values.entrySet().stream()
                .filter(entry -> filter == null || type(entry.getValue()) == filter)
                .filter(entry -> needle.isEmpty() || entry.getKey().toLowerCase(Locale.ROOT).contains(needle)
                        || searchValue(entry.getValue()).toLowerCase(Locale.ROOT).contains(needle))
                .map(Map.Entry::getKey).toList();
    }
    private static String searchValue(Object value) {
        return value instanceof String text ? text : summary(value);
    }

    /** Bounded structural preview: no arbitrary toString or unbounded expanded widget tree. */
    static String tree(Object value) {
        StringBuilder output = new StringBuilder();
        tree(output, "", value, 0, new int[]{0});
        return output.toString();
    }
    private static void tree(StringBuilder output, String key, Object value, int depth, int[] lines) {
        if (lines[0] >= 96) return;
        output.append("  ".repeat(depth)).append(key.length() <= 120 ? key : key.substring(0, 117) + "…");
        if (value instanceof Map<?, ?> map) {
            output.append("{ ").append(map.size()).append(" }\n"); lines[0]++;
            if (depth >= 8) return;
            for (var entry : map.entrySet()) {
                if (lines[0] >= 95) { output.append("…\n"); lines[0] = 96; break; }
                tree(output, entry.getKey() + ": ", entry.getValue(), depth + 1, lines);
            }
        } else if (value instanceof List<?> list) {
            output.append("[ ").append(list.size()).append(" ]\n"); lines[0]++;
            if (depth >= 8) return;
            for (int index = 0; index < list.size(); index++) {
                if (lines[0] >= 95) { output.append("…\n"); lines[0] = 96; break; }
                tree(output, index + ": ", list.get(index), depth + 1, lines);
            }
        } else { output.append(summary(value)).append('\n'); lines[0]++; }
    }
}

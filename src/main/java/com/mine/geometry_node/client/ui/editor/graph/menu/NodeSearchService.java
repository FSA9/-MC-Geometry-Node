package com.mine.geometry_node.client.ui.editor.graph.menu;

import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import com.mine.geometry_node.client.ui.editor.graph.node.comment.NodeCommentTextBuilder;
import com.mine.geometry_node.core.node.nodes.NodeDef;

import java.io.InputStream;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Collection;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

/** Shared node-type search used by the viewport and P2 query tools. */
public final class NodeSearchService {
    private static final String NODE_TRANSLATION_PREFIX = "geometry_node.node.";
    private static final Map<String, String> ENGLISH_NODE_NAMES = loadEnglishNodeNames();

    private NodeSearchService() {}

    public record Match(NodeDef definition, String displayName, String englishName, String comment) {}

    public record Page(List<Match> items, int offset, int limit, int total) {
        public Page {
            items = List.copyOf(items);
        }

        public boolean hasMore() { return offset + items.size() < total; }
    }

    public static Page search(Collection<NodeDef> definitions, String query, int offset, int limit) {
        String normalizedQuery = normalize(query);
        String compactQuery = compact(normalizedQuery);
        int requestedOffset = Math.max(0, offset);
        int safeLimit = Math.max(0, limit);
        long requestedEnd = (long) requestedOffset + safeLimit;
        List<Match> pageItems = new ArrayList<>(Math.min(safeLimit, 64));
        int total = 0;
        if (definitions != null) {
            for (NodeDef definition : definitions) {
                if (definition == null) continue;
                String displayName = definition.displayName().getString();
                String englishName = englishNodeName(definition.typeId());
                String comment = NodeCommentTextBuilder.build(definition);
                if (normalizedQuery.isEmpty() || matchesNormalized(normalizedQuery, compactQuery,
                        displayName, englishName, definition.typeId(), comment)) {
                    if (total >= requestedOffset && total < requestedEnd) {
                        pageItems.add(new Match(definition, displayName, englishName, comment));
                    }
                    total++;
                }
            }
        }
        return new Page(pageItems, requestedOffset, safeLimit, total);
    }

    public static boolean matches(String query, String... candidates) {
        String normalizedQuery = normalize(query);
        return normalizedQuery.isEmpty() || matchesNormalized(normalizedQuery, compact(normalizedQuery), candidates);
    }

    private static boolean matchesNormalized(String query, String compactQuery, String... candidates) {
        for (String candidate : candidates) {
            String normalizedCandidate = normalize(candidate);
            if (normalizedCandidate.contains(query)) return true;
            if (!compactQuery.isEmpty() && compact(normalizedCandidate).contains(compactQuery)) return true;
        }
        return false;
    }

    private static String englishNodeName(String typeId) {
        String localTypeId = localTypeId(typeId);
        String exact = ENGLISH_NODE_NAMES.get(localTypeId);
        if (exact != null && !exact.isBlank()) return exact;

        String bestMatch = null;
        int bestExtraLength = Integer.MAX_VALUE;
        for (Map.Entry<String, String> entry : ENGLISH_NODE_NAMES.entrySet()) {
            String translatedTypeId = entry.getKey();
            if (translatedTypeId.endsWith(localTypeId) || localTypeId.endsWith(translatedTypeId)) {
                int extraLength = Math.abs(translatedTypeId.length() - localTypeId.length());
                if (extraLength < bestExtraLength) {
                    bestExtraLength = extraLength;
                    bestMatch = entry.getValue();
                }
            }
        }
        return bestMatch != null && !bestMatch.isBlank() ? bestMatch : humanizeTypeId(localTypeId);
    }

    private static String localTypeId(String typeId) {
        if (typeId == null) return "";
        int separator = typeId.indexOf(':');
        return separator >= 0 ? typeId.substring(separator + 1) : typeId;
    }

    private static String humanizeTypeId(String typeId) {
        StringBuilder result = new StringBuilder(typeId.length());
        boolean capitalizeNext = true;
        for (int i = 0; i < typeId.length(); i++) {
            char value = typeId.charAt(i);
            if (value == '_' || value == '-' || value == '.') {
                if (!result.isEmpty() && result.charAt(result.length() - 1) != ' ') result.append(' ');
                capitalizeNext = true;
            } else {
                result.append(capitalizeNext ? Character.toUpperCase(value) : value);
                capitalizeNext = false;
            }
        }
        return result.toString();
    }

    private static String normalize(String value) {
        return value == null ? "" : value.trim().toLowerCase(Locale.ROOT);
    }

    private static String compact(String value) {
        if (value == null || value.isEmpty()) return "";
        StringBuilder result = new StringBuilder(value.length());
        value.codePoints().filter(Character::isLetterOrDigit).forEach(result::appendCodePoint);
        return result.toString();
    }

    private static Map<String, String> loadEnglishNodeNames() {
        try (InputStream stream = NodeSearchService.class.getResourceAsStream("/assets/geometry_node/lang/en_us.json")) {
            if (stream == null) return Map.of();
            JsonObject root = JsonParser.parseReader(new InputStreamReader(stream, StandardCharsets.UTF_8)).getAsJsonObject();
            Map<String, String> names = new HashMap<>();
            for (Map.Entry<String, JsonElement> entry : root.entrySet()) {
                if (entry.getKey().startsWith(NODE_TRANSLATION_PREFIX) && entry.getValue().isJsonPrimitive()
                        && entry.getValue().getAsJsonPrimitive().isString()) {
                    names.put(entry.getKey().substring(NODE_TRANSLATION_PREFIX.length()), entry.getValue().getAsString());
                }
            }
            return Map.copyOf(names);
        } catch (Exception ignored) {
            return Map.of();
        }
    }
}

package com.mine.geometry_node.client.ui.persistence;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import com.mine.geometry_node.core.engine.graph.GraphType;
import com.mine.geometry_node.core.engine.graph.GraphTypeRegistry;
import com.mine.geometry_node.core.engine.quest.model.QuestDefinition;
import com.mine.geometry_node.core.engine.quest.model.QuestConditionOverview;
import com.mine.geometry_node.core.node.NodeGraph;

import java.io.File;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Set;

public final class GraphTagIO {
    public static final Gson GSON = new GsonBuilder().setPrettyPrinting().disableHtmlEscaping().create();

    private GraphTagIO() {}

    public record GraphMetadata(String graphTypeId, String comment, List<String> tags,
                                QuestDefinition questDefinition,
                                QuestConditionOverview conditionOverview) {}

    public static JsonObject readGraphRoot(File file) throws Exception {
        String content = Files.exists(file.toPath()) ? Files.readString(file.toPath()).trim() : "";
        if (content.isEmpty() || content.equals("{}")) {
            return new JsonObject();
        }

        JsonElement parsed = JsonParser.parseString(content);
        if (!parsed.isJsonObject()) {
            throw new IllegalArgumentException("根节点不是 JSON object");
        }
        return parsed.getAsJsonObject();
    }

    public static List<String> readTags(File file) throws Exception {
        return readTags(readGraphRoot(file));
    }

    public static GraphMetadata readMetadata(File file) throws Exception {
        JsonObject root = readGraphRoot(file);
        String comment = root.has("comment") && root.get("comment").isJsonPrimitive()
                ? root.get("comment").getAsString()
                : "";
        return new GraphMetadata(
                resolveGraphTypeId(root),
                comment,
                readTags(root),
                QuestDefinition.fromJson(root.get("quest")),
                QuestConditionOverview.fromGraph(GSON.fromJson(root, NodeGraph.class)));
    }

    public static List<String> readTags(JsonObject root) {
        List<String> tags = new ArrayList<>();
        if (!root.has("tags") || !root.get("tags").isJsonArray()) {
            return tags;
        }

        boolean needsLegacyKindMigration = !root.has("graph_kind")
                || GraphType.normalizeId(root.get("graph_kind").getAsString()).isEmpty();
        Set<String> seen = new LinkedHashSet<>();
        for (JsonElement element : root.getAsJsonArray("tags")) {
            if (!element.isJsonPrimitive() || !element.getAsJsonPrimitive().isString()) continue;

            String tag = element.getAsString();
            GraphType kind = GraphTypeRegistry.INSTANCE.get(tag);
            if (needsLegacyKindMigration && kind != null) {
                needsLegacyKindMigration = false;
                continue;
            }

            String normalized = normalizeTag(tag);
            if (!normalized.isEmpty() && seen.add(normalized)) {
                tags.add(normalized);
            }
        }
        return tags;
    }

    public static void writeMetadata(File file, String graphTypeId, String comment, List<String> tags,
                                     QuestDefinition questDefinition) throws Exception {
        JsonObject root = readGraphRoot(file);
        writeMetadataRoot(file, root, graphTypeId, comment != null ? comment.trim() : "", tags, questDefinition);
    }

    private static void writeMetadataRoot(File file, JsonObject root, String graphTypeId, String comment,
                                          List<String> tags, QuestDefinition questDefinition) throws Exception {
        String normalizedTypeId = GraphType.normalizeId(graphTypeId);
        if (normalizedTypeId.isEmpty()) {
            throw new IllegalArgumentException("Graph type cannot be empty");
        }
        root.remove("graph_name");
        root.addProperty("graph_kind", normalizedTypeId);
        if (comment != null) root.addProperty("comment", comment);
        root.add("tags", GSON.toJsonTree(tags != null ? tags : List.of()));
        QuestDefinition quest = questDefinition != null ? questDefinition : QuestDefinition.EMPTY;
        if (GraphTypeRegistry.QUEST.id().equals(normalizedTypeId) || !quest.isEmpty()) {
            root.add("quest", quest.toJson());
        } else {
            root.remove("quest");
        }
        if (!root.has("version")) root.addProperty("version", "1.0");
        if (!root.has("nodes") || !root.get("nodes").isJsonObject()) root.add("nodes", new JsonObject());
        if (!root.has("frames") || !root.get("frames").isJsonObject()) root.add("frames", new JsonObject());
        Files.writeString(file.toPath(), GSON.toJson(root), StandardCharsets.UTF_8);
    }

    public static String resolveGraphTypeId(JsonObject root) {
        if (root.has("graph_kind")) {
            String explicitId = GraphType.normalizeId(root.get("graph_kind").getAsString());
            if (!explicitId.isEmpty()) {
                return explicitId;
            }
        }

        if (root.has("tags") && root.get("tags").isJsonArray()) {
            JsonArray tags = root.getAsJsonArray("tags");
            for (JsonElement element : tags) {
                if (!element.isJsonPrimitive() || !element.getAsJsonPrimitive().isString()) continue;
                GraphType kind = GraphTypeRegistry.INSTANCE.get(element.getAsString());
                if (kind != null) {
                    return kind.id();
                }
            }
        }

        return GraphTypeRegistry.BLUEPRINT.id();
    }

    public static String normalizeTag(String raw) {
        if (raw == null) return "";
        String normalized = raw.trim();
        while (normalized.startsWith("#")) {
            normalized = normalized.substring(1).trim();
        }
        normalized = normalized.replaceAll("\\p{Cntrl}", "").trim();
        return normalized.toLowerCase(Locale.ROOT);
    }
}

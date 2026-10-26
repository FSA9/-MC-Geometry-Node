package com.mine.geometry_node.client.feature.graph.persistence;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import com.mine.geometry_node.core.engine.graph.GraphDocumentType;
import com.mine.geometry_node.core.engine.graph.GraphTypeRegistry;
import com.mine.geometry_node.core.engine.system.quest.model.QuestDefinition;
import com.mine.geometry_node.core.engine.system.quest.model.QuestConditionOverview;
import com.mine.geometry_node.core.node.document.NodeGraph;
import com.mine.geometry_node.core.metadata.TagPolicy;
import com.mine.geometry_node.core.metadata.TagSet;
import com.mine.geometry_node.client.feature.graph.persistence.file.GraphDocumentStore;
import com.mine.geometry_node.client.feature.graph.persistence.file.GraphFileReference;

import java.io.File;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.function.UnaryOperator;
import java.io.IOException;

public final class GraphTagIO {
    public static final Gson GSON = new GsonBuilder().setPrettyPrinting().disableHtmlEscaping().create();

    private GraphTagIO() {}

    public record GraphMetadata(String graphTypeId, String comment, List<String> tags,
                                QuestDefinition questDefinition,
                                QuestConditionOverview conditionOverview) {}

    public static JsonObject readGraphRoot(File file) throws Exception {
        return parseGraphRoot(GraphDocumentStore.INSTANCE.readString(file.toPath()).trim());
    }

    public static JsonObject readGraphRoot(GraphFileReference reference) throws Exception {
        return parseGraphRoot(GraphDocumentStore.INSTANCE.readString(reference).trim());
    }

    private static JsonObject parseGraphRoot(String content) {
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

    public static List<String> readUnclaimedTags(GraphFileReference reference) throws IOException {
        JsonObject root = parseGraphRoot(GraphDocumentStore.INSTANCE.readUnclaimedString(reference).trim());
        GraphDocumentType.requireId(root);
        return readTags(root);
    }

    public static GraphMetadata readMetadata(File file) throws Exception {
        return readMetadata(readGraphRoot(file));
    }

    public static GraphMetadata readMetadata(GraphFileReference reference) throws Exception {
        return readMetadata(readGraphRoot(reference));
    }

    private static GraphMetadata readMetadata(JsonObject root) {
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
            return List.of();
        }

        for (JsonElement element : root.getAsJsonArray("tags")) {
            if (!element.isJsonPrimitive() || !element.getAsJsonPrimitive().isString()) continue;
            tags.add(element.getAsString());
        }
        return TagSet.normalize(tags, TagPolicy.GRAPH_LEGACY);
    }

    /** Changes only tags, under the unclaimed file's existing transaction and revision lock. */
    public static boolean updateTags(GraphFileReference reference, UnaryOperator<List<String>> transformation)
            throws IOException {
        Objects.requireNonNull(transformation, "transformation");
        return GraphDocumentStore.INSTANCE.updateStringAtomicIfChanged(reference, StandardCharsets.UTF_8, current -> {
            JsonObject root = parseGraphRoot(current.trim());
            GraphDocumentType.requireId(root);
            List<String> before = readTags(root);
            List<String> after = TagSet.normalize(Objects.requireNonNull(transformation.apply(before), "tags"),
                    TagPolicy.GRAPH_LEGACY);
            if (before.equals(after)) return current;
            root.add("tags", GSON.toJsonTree(after));
            return GSON.toJson(root);
        });
    }

    public static void writeMetadata(GraphFileReference reference, String graphTypeId, String comment, List<String> tags,
                                     QuestDefinition questDefinition) throws Exception {
        GraphDocumentStore.INSTANCE.updateStringAtomic(reference, StandardCharsets.UTF_8, current -> {
            JsonObject root = parseGraphRoot(current.trim());
            writeMetadataRoot(root, graphTypeId, comment != null ? comment.trim() : "", tags, questDefinition);
            return GSON.toJson(root);
        });
    }

    private static void writeMetadataRoot(JsonObject root, String graphTypeId, String comment,
                                          List<String> tags, QuestDefinition questDefinition) {
        String normalizedTypeId = GraphTypeRegistry.INSTANCE.require(graphTypeId).id();
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
    }

    public static String resolveGraphTypeId(JsonObject root) {
        return GraphDocumentType.requireId(root);
    }

    public static String normalizeTag(String raw) {
        return TagPolicy.GRAPH_LEGACY.normalize(raw);
    }
}

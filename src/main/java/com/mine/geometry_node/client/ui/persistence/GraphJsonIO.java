package com.mine.geometry_node.client.ui.persistence;

import com.google.gson.*;
import com.mine.geometry_node.core.engine.graph.GraphKind;
import com.mine.geometry_node.core.node.Connection;
import com.mine.geometry_node.core.node.FrameData;
import com.mine.geometry_node.core.node.NodeData;
import com.mine.geometry_node.core.node.NodeGraph;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

public final class GraphJsonIO {
    private static final Gson GSON = new GsonBuilder().setPrettyPrinting().disableHtmlEscaping().create();

    private GraphJsonIO() {}

    public static String toJson(NodeGraph g) {
        JsonObject root = new JsonObject();
        GraphKind kind = g.getKind();
        root.addProperty("graph_kind", kind != GraphKind.UNKNOWN ? kind.id() : GraphKind.BLUEPRINT.id());
        root.add("tags", GSON.toJsonTree(g.tags != null ? g.tags : List.of()));
        root.addProperty("version", g.version != null ? g.version : "1.0");

        // 序列化 Nodes
        JsonObject nodes = new JsonObject();
        for (Map.Entry<String, NodeData> e : g.nodes.entrySet()) {
            JsonElement el = GSON.toJsonTree(e.getValue());
            if (el.isJsonObject()) el.getAsJsonObject().addProperty("id", e.getKey());
            nodes.add(e.getKey(), el);
        }
        root.add("nodes", nodes);

        // 序列化 Frames
        JsonObject frames = new JsonObject();
        if (g.frames != null) {
            for (Map.Entry<String, FrameData> e : g.frames.entrySet()) {
                JsonElement el = GSON.toJsonTree(e.getValue());
                if (el.isJsonObject()) el.getAsJsonObject().addProperty("id", e.getKey());
                frames.add(e.getKey(), el);
            }
        }
        root.add("frames", frames);

        return GSON.toJson(root);
    }

    public static NodeGraph fromJson(String json) {
        JsonObject root = JsonParser.parseString(json).getAsJsonObject();
        NodeGraph g = new NodeGraph();
        g.graphKind = root.has("graph_kind") ? root.get("graph_kind").getAsString() : GraphKind.BLUEPRINT.id();
        g.version = root.has("version") ? root.get("version").getAsString() : "1.0";
        g.tags = readUserTags(root, g);

        JsonObject nodesObj = root.getAsJsonObject("nodes");
        for (String id : nodesObj.keySet()) {
            NodeData n = GSON.fromJson(nodesObj.get(id), NodeData.class);
            n.id = id;
            g.nodes.put(id, n);
        }

        if (root.has("frames")) {
            JsonObject framesObj = root.getAsJsonObject("frames");
            for (String id : framesObj.keySet()) {
                FrameData f = GSON.fromJson(framesObj.get(id), FrameData.class);
                f.id = id;
                g.frames.put(id, f);
            }
        }

        for (NodeData outNode : g.nodes.values()) {
            if (outNode.outputs != null) {
                for (List<Connection> connections : outNode.outputs.values()) {
                    if (connections != null) {
                        for (Connection link : connections) {
                            NodeData targetNode = g.nodes.get(link.targetNodeId());
                            if (targetNode != null) targetNode.setInputConnected(link.targetPortName(), true);
                        }
                    }
                }
            }
        }
        return g;
    }

    private static List<String> readUserTags(JsonObject root, NodeGraph graph) {
        List<String> tags = new ArrayList<>();
        if (!root.has("tags") || !root.get("tags").isJsonArray()) {
            return tags;
        }

        boolean needsLegacyKindMigration = !root.has("graph_kind")
                || GraphKind.fromId(graph.graphKind) == GraphKind.UNKNOWN;
        for (JsonElement element : root.getAsJsonArray("tags")) {
            if (!element.isJsonPrimitive() || !element.getAsJsonPrimitive().isString()) continue;

            String tag = element.getAsString();
            GraphKind kind = GraphKind.fromId(tag);
            if (needsLegacyKindMigration && kind != GraphKind.UNKNOWN) {
                graph.graphKind = kind.id();
                needsLegacyKindMigration = false;
                continue;
            }

            tags.add(tag);
        }
        return tags;
    }
}

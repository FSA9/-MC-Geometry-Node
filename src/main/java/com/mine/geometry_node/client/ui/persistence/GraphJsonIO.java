package com.mine.geometry_node.client.ui.persistence;

import com.google.gson.*;
import com.mine.geometry_node.core.engine.graph.GraphDocumentType;
import com.mine.geometry_node.core.engine.graph.GraphTypeRegistry;
import com.mine.geometry_node.core.engine.system.quest.model.QuestDefinition;
import com.mine.geometry_node.core.node.document.Connection;
import com.mine.geometry_node.core.node.document.FrameData;
import com.mine.geometry_node.core.node.document.NodeData;
import com.mine.geometry_node.core.node.document.NodeGraph;
import com.mine.geometry_node.core.node.reroute.RerouteNodeSupport;
import net.minecraft.world.phys.Vec3;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

public final class GraphJsonIO {
    private static final Gson GSON = new GsonBuilder()
            .registerTypeAdapter(Vec3.class, (JsonSerializer<Vec3>) (value, type, context) -> {
                JsonArray array = new JsonArray();
                array.add(value.x);
                array.add(value.y);
                array.add(value.z);
                return array;
            })
            .setPrettyPrinting()
            .disableHtmlEscaping()
            .create();

    private GraphJsonIO() {}

    public static String toJson(NodeGraph g) {
        JsonObject root = new JsonObject();
        root.addProperty("graph_kind", g.getGraphTypeId());
        root.add("tags", GSON.toJsonTree(g.tags != null ? g.tags : List.of()));
        root.addProperty("comment", g.comment != null ? g.comment : "");
        QuestDefinition quest = g.quest != null ? g.quest : QuestDefinition.EMPTY;
        if (GraphTypeRegistry.QUEST.id().equals(g.getGraphTypeId()) || !quest.isEmpty()) {
            root.add("quest", quest.toJson());
        }
        root.addProperty("version", g.version != null ? g.version : "1.0");

        // 序列化 Nodes
        JsonObject nodes = new JsonObject();
        for (Map.Entry<String, NodeData> e : g.nodes.entrySet()) {
            JsonElement el = GSON.toJsonTree(e.getValue());
            if (el.isJsonObject()) {
                JsonObject nodeObject = el.getAsJsonObject();
                nodeObject.addProperty("id", e.getKey());
            }
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
        g.graphKind = GraphDocumentType.requireId(root);
        g.version = root.has("version") ? root.get("version").getAsString() : "1.0";
        g.tags = readUserTags(root);
        g.comment = root.has("comment") && root.get("comment").isJsonPrimitive()
                ? root.get("comment").getAsString()
                : "";
        g.quest = QuestDefinition.fromJson(root.get("quest"));
        JsonElement nodesElement = root.get("nodes");
        if (nodesElement != null && !nodesElement.isJsonNull()) {
            if (!nodesElement.isJsonObject()) {
                throw new JsonParseException("nodes must be an object");
            }
            JsonObject nodesObj = nodesElement.getAsJsonObject();
            for (String id : nodesObj.keySet()) {
                NodeData n = GSON.fromJson(nodesObj.get(id), NodeData.class);
                restoreNodeTree(n, id, null);
                if (n != null) g.nodes.put(id, n);
            }
        }

        JsonElement framesElement = root.get("frames");
        if (framesElement != null && !framesElement.isJsonNull()) {
            if (!framesElement.isJsonObject()) {
                throw new JsonParseException("frames must be an object");
            }
            JsonObject framesObj = framesElement.getAsJsonObject();
            for (String id : framesObj.keySet()) {
                FrameData f = GSON.fromJson(framesObj.get(id), FrameData.class);
                if (f == null) continue;
                f.id = id;
                g.frames.put(id, f);
            }
        }

        restoreConnectedInputs(g.nodes);
        refreshRerouteTypes(g.nodes);
        return g;
    }

    private static void restoreNodeTree(NodeData node, String id, NodeData parentGroupNode) {
        if (node == null) return;
        node.id = id;
        node.parentGroupNode = parentGroupNode;
        node.restoreDocumentDefaults();

        if (node.subNodes == null) return;
        for (Map.Entry<String, NodeData> entry : node.subNodes.entrySet()) {
            restoreNodeTree(entry.getValue(), entry.getKey(), node);
        }
    }

    private static void restoreConnectedInputs(Map<String, NodeData> scopeNodes) {
        if (scopeNodes == null) return;
        Map<String, NodeData> scopeIndex = new HashMap<>();
        for (Map.Entry<String, NodeData> entry : scopeNodes.entrySet()) {
            NodeData node = entry.getValue();
            if (node != null) {
                scopeIndex.put(entry.getKey(), node);
            }
        }

        for (NodeData outNode : scopeNodes.values()) {
            if (outNode == null) continue;
            if (outNode.outputs != null) {
                for (List<Connection> connections : outNode.outputs.values()) {
                    if (connections == null) continue;
                    for (Connection link : connections) {
                        if (link == null || !link.isValid()) continue;
                        NodeData targetNode = scopeIndex.get(link.targetNodeId());
                        if (targetNode != null) targetNode.setInputConnected(link.targetPortName(), true);
                    }
                }
            }
            if (outNode.execOutputs != null) {
                for (Connection link : outNode.execOutputs.values()) {
                    if (link == null || !link.isValid()) continue;
                    NodeData targetNode = scopeIndex.get(link.targetNodeId());
                    if (targetNode != null) targetNode.setInputConnected(link.targetPortName(), true);
                }
            }
        }

        for (NodeData node : scopeNodes.values()) {
            if (node != null && node.subNodes != null) {
                restoreConnectedInputs(node.subNodes);
            }
        }
    }

    private static void refreshRerouteTypes(Map<String, NodeData> scopeNodes) {
        if (scopeNodes == null) return;
        RerouteNodeSupport.refreshLockedTypes(scopeNodes);
        for (NodeData node : scopeNodes.values()) {
            if (node != null && node.subNodes != null) {
                refreshRerouteTypes(node.subNodes);
            }
        }
    }

    private static List<String> readUserTags(JsonObject root) {
        List<String> tags = new ArrayList<>();
        if (!root.has("tags") || !root.get("tags").isJsonArray()) {
            return tags;
        }

        for (JsonElement element : root.getAsJsonArray("tags")) {
            if (!element.isJsonPrimitive() || !element.getAsJsonPrimitive().isString()) continue;
            tags.add(element.getAsString());
        }
        return tags;
    }
}

package com.mine.geometry_node.client.ui.persistence;

import com.google.gson.*;
import com.mine.geometry_node.core.node.NodeData;
import com.mine.geometry_node.core.node.NodeGraph;

import java.util.Map;

public final class GraphJsonIO {
    private static final Gson GSON = new GsonBuilder().setPrettyPrinting().disableHtmlEscaping().create();

    private GraphJsonIO() {}

    public static String toJson(NodeGraph g) {
        JsonObject root = new JsonObject();
        root.addProperty("graph_name", g.graphName != null ? g.graphName : "");
        root.addProperty("version", g.version != null ? g.version : "1.0");
        JsonObject nodes = new JsonObject();
        for (Map.Entry<String, NodeData> e : g.nodes.entrySet()) {
            JsonElement el = GSON.toJsonTree(e.getValue());
            if (el.isJsonObject()) {
                el.getAsJsonObject().addProperty("id", e.getKey());
            }
            nodes.add(e.getKey(), el);
        }
        root.add("nodes", nodes);
        return GSON.toJson(root);
    }

    public static NodeGraph fromJson(String json) {
        JsonObject root = JsonParser.parseString(json).getAsJsonObject();
        NodeGraph g = new NodeGraph();
        g.graphName = root.has("graph_name") ? root.get("graph_name").getAsString() : "";
        g.version = root.has("version") ? root.get("version").getAsString() : "1.0";
        JsonObject nodesObj = root.getAsJsonObject("nodes");
        for (String id : nodesObj.keySet()) {
            NodeData n = GSON.fromJson(nodesObj.get(id), NodeData.class);
            n.id = id;
            g.nodes.put(id, n);
        }
        return g;
    }
}

package com.mine.geometry_node.client.ui.persistence;

import com.google.gson.*;
import com.mine.geometry_node.core.node.Connection;
import com.mine.geometry_node.core.node.NodeData;
import com.mine.geometry_node.core.node.NodeGraph;

import java.util.List;
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

        // 1. 先反序列化所有的节点
        for (String id : nodesObj.keySet()) {
            NodeData n = GSON.fromJson(nodesObj.get(id), NodeData.class);
            n.id = id;
            g.nodes.put(id, n);
        }

        // 2. 遍历所有节点，根据 outputs 里的连线数据，重建目标节点的 connectedInputs 缓存
        for (NodeData outNode : g.nodes.values()) {
            if (outNode.outputs != null) {
                for (List<Connection> connections : outNode.outputs.values()) {
                    if (connections != null) {
                        for (Connection link : connections) {
                            // 找到目标节点，并将其对应的输入端口标记为已连接
                            NodeData targetNode = g.nodes.get(link.targetNodeId());
                            if (targetNode != null) {
                                targetNode.setInputConnected(link.targetPortName(), true);
                            }
                        }
                    }
                }
            }
        }

        return g;
    }
}

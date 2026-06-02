package com.mine.geometry_node.core.node;

import com.google.gson.annotations.SerializedName;
import com.mine.geometry_node.core.engine.graph.GraphKind;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * [容器层] 蓝图对象。
 * 代表一整张逻辑图，包含所有节点数据。
 */
public class NodeGraph {
    @SerializedName("graph_name")
    public String graphName;        // 图名称

    @SerializedName("tags")
    public List<String> tags = new ArrayList<>();

    @SerializedName("version")
    public String version;          // 版本

    // 节点列表
    @SerializedName("nodes")
    public Map<String, NodeData> nodes = new HashMap<>();

    // 图框列表
    @SerializedName("frames")
    public Map<String, FrameData> frames = new HashMap<>();

    public NodeGraph() {}

    public NodeGraph(String graphName) {
        this();
        this.graphName = graphName;
    }

    public GraphKind getKind() {
        if (tags != null) {
            for (String tag : tags) {
                GraphKind kind = GraphKind.fromId(tag);
                if (kind != GraphKind.UNKNOWN) {
                    return kind;
                }
            }
        }
        return GraphKind.BLUEPRINT;
    }

    /**
     * 辅助方法
     */
    public FrameData getFrame(String id) {
        return frames.get(id);
    }

    public void addFrame(FrameData frame) {
        this.frames.put(frame.id, frame);
    }

    public void removeFrame(String id) {
        this.frames.remove(id);
    }

    public NodeData getNode(String id) {
        return nodes.get(id);
    }

    public void addNode(NodeData node) {
        if (node.id == null) {
            throw new IllegalArgumentException("Node ID cannot be null when adding to graph");
        }
        this.nodes.put(node.id, node);
    }

    public void removeNode(String id) {
        this.nodes.remove(id);
    }
}

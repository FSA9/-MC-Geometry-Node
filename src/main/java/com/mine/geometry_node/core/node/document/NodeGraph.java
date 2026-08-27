package com.mine.geometry_node.core.node.document;

import com.google.gson.annotations.SerializedName;
import com.mine.geometry_node.core.engine.graph.GraphType;
import com.mine.geometry_node.core.engine.graph.GraphTypeRegistry;
import com.mine.geometry_node.core.engine.system.quest.model.QuestDefinition;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * [容器层] 蓝图对象。
 * 代表一整张逻辑图，包含所有节点数据。
 */
public class NodeGraph {
    @SerializedName("graph_kind")
    public String graphKind = GraphTypeRegistry.BLUEPRINT.id();

    @SerializedName("tags")
    public List<String> tags = new ArrayList<>();

    @SerializedName("comment")
    public String comment = "";

    @SerializedName("quest")
    public QuestDefinition quest = QuestDefinition.EMPTY;

    /** Present only for behavior-tree assets; execution and data links remain on nodes. */
    @SerializedName("behavior_tree")
    public BehaviorTreeStructure behaviorTree;

    @SerializedName("version")
    public String version;          // 版本

    // 节点列表
    @SerializedName("nodes")
    public Map<String, NodeData> nodes = new HashMap<>();

    // 图框列表
    @SerializedName("frames")
    public Map<String, FrameData> frames = new HashMap<>();

    public NodeGraph() {}

    public String getGraphTypeId() {
        String explicitId = GraphType.normalizeId(graphKind);
        if (!explicitId.isEmpty()) {
            return explicitId;
        }

        if (tags != null) {
            for (String tag : tags) {
                String legacyId = GraphType.normalizeId(tag);
                if (GraphTypeRegistry.INSTANCE.get(legacyId) != null) {
                    return legacyId;
                }
            }
        }
        return GraphTypeRegistry.BLUEPRINT.id();
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

    public BehaviorTreeStructure ensureBehaviorTree() {
        if (behaviorTree == null) {
            behaviorTree = new BehaviorTreeStructure();
        }
        return behaviorTree;
    }
}

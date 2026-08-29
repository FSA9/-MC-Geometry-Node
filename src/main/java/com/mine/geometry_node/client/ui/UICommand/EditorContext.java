package com.mine.geometry_node.client.ui.UICommand;

import com.mine.geometry_node.GeometryNode;
import com.mine.geometry_node.client.ui.editor.graph.GraphController;
import com.mine.geometry_node.core.node.document.FrameData;
import com.mine.geometry_node.core.node.document.NodeData;
import com.mine.geometry_node.core.node.document.NodeGraph;
import com.mine.geometry_node.core.node.group.GroupNodeFactory;
import com.mine.geometry_node.core.node.group.GroupNodeTypes;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.function.Consumer;

/**
 * 编辑器全局上下文
 * 连接 UI 层（viewport, Properties 等）与数据层的核心枢纽。
 */
public class EditorContext {

    // --- 核心模块 ---
    private final NodeGraph mGraph;
    private final CommandManager mCommandManager;
    private final GraphController mGraphController;
    private NodeGraph mCurrentGraph;
    private NodeData mCurrentGroupNode;

    // --- 事件监听器列表 ---
    private final List<EditorListener> mListeners = new ArrayList<>();

    public EditorContext(NodeGraph graph) {
        // 如果传入 null，则默认创建一个空的新图
        this.mGraph = (graph != null) ? graph : new NodeGraph();
        this.mCurrentGraph = this.mGraph;
        this.mCommandManager = new CommandManager();
        this.mGraphController = new GraphController(this);
    }

    // --- Getters ---
    public NodeGraph getGraph() { return mGraph; }
    public NodeGraph getCurrentGraph() { return mCurrentGraph != null ? mCurrentGraph : mGraph; }
    public NodeData getCurrentGroupNode() { return mCurrentGroupNode; }
    public boolean isInsideGroupScope() { return mCurrentGroupNode != null; }
    public CommandManager getCommandManager() { return mCommandManager; }
    public GraphController getGraphController() { return mGraphController; }
    public boolean enterGroupScope(NodeData groupNode) {
        if (groupNode == null || !groupNode.isGroupNode()) {
            return false;
        }

        boolean boundariesMissing = groupNode.subNodes == null
                || !groupNode.subNodes.containsKey(GroupNodeTypes.GROUP_IN_ID)
                || !groupNode.subNodes.containsKey(GroupNodeTypes.GROUP_OUT_ID);
        GroupNodeFactory.ensureBoundaryNodes(groupNode);
        if (boundariesMissing) {
            mCommandManager.recordExternalMutation();
        }
        mCurrentGroupNode = groupNode;
        mCurrentGraph = createGroupScopeGraph(groupNode);
        return true;
    }

    public boolean exitGroupScope() {
        if (mCurrentGroupNode == null) {
            return false;
        }

        NodeData parentGroup = mCurrentGroupNode.parentGroupNode;
        if (parentGroup != null && parentGroup.isGroupNode()) {
            mCurrentGroupNode = parentGroup;
            mCurrentGraph = createGroupScopeGraph(parentGroup);
        } else {
            mCurrentGroupNode = null;
            mCurrentGraph = mGraph;
        }
        return true;
    }

    private NodeGraph createGroupScopeGraph(NodeData groupNode) {
        NodeGraph graph = new NodeGraph();
        graph.graphKind = mGraph.graphKind;
        graph.tags = mGraph.tags;
        graph.comment = mGraph.comment;
        graph.quest = mGraph.quest;
        graph.version = mGraph.version;
        graph.nodes = groupNode.ensureSubNodes();
        graph.frames = new LinkedHashMap<String, FrameData>();
        return graph;
    }

    // ==========================================
    // 事件总线 (Event Bus)
    // 用于通知 viewport 和其他 UI 组件更新画面
    // ==========================================

    /**
     * UI 监听器接口
     * viewport 会实现这个接口，以便在数据改变时自动增加/删除节点 View
     */
    public interface EditorListener {
        default void onExecutionConnectionAdded(String outNode, String outPort, String inNode, String inPort) {}
        default void onExecutionConnectionRemoved(String outNode, String outPort, String inNode, String inPort) {}
        default void onNodeAdded(NodeData nodeData) {}
        default void onNodeRemoved(String nodeId) {}
        default void onSelectionChanged(List<String> selectedNodeIds) {}
        default void onNodeMoved(String nodeId, float x, float y) {}
        default void onConnectionAdded(String outNode, String outPort, String inNode, String inPort) {}
        default void onConnectionRemoved(String outNode, String outPort, String inNode, String inPort) {}
        default void onNodeStructureChanged(NodeData nodeData) {}
        default void onGraphMetadataChanged() {}
        default void onGraphConnectionsRebuildRequested() {}
        default void onGraphReloaded() {}
        default void onFrameAdded(FrameData frame) {}
        default void onFrameRemoved(String frameId) {}
        default void onFrameBoundsUpdated(String frameId, float x, float y, float w, float h) {}
        default void onFrameTitleChanged(String frameId, String newTitle) {}
    }

    public void addListener(EditorListener listener) {
        if (!mListeners.contains(listener)) mListeners.add(listener);
    }

    public void removeListener(EditorListener listener) {
        mListeners.remove(listener);
    }

    // --- 触发事件的方法 (由 Controller 调用) ---

    public void notifyNodeAdded(NodeData node) {
        notifyListeners("adding node", listener -> listener.onNodeAdded(node));
    }

    public void notifyNodeRemoved(String nodeId) {
        notifyListeners("removing node", listener -> listener.onNodeRemoved(nodeId));
    }

    public void notifySelectionChanged(List<String> selectedIds) {
        List<String> snapshot = selectedIds == null ? List.of() : List.copyOf(selectedIds);
        notifyListeners("changing selection", listener -> listener.onSelectionChanged(snapshot));
    }

    public void notifyNodeMoved(String nodeId, float x, float y) {
        notifyListeners("moving node", listener -> listener.onNodeMoved(nodeId, x, y));
    }

    public void notifyConnectionAdded(String outN, String outP, String inN, String inP) {
        notifyListeners("adding connection", listener -> listener.onConnectionAdded(outN, outP, inN, inP));
    }

    public void notifyConnectionRemoved(String outN, String outP, String inN, String inP) {
        notifyListeners("removing connection", listener -> listener.onConnectionRemoved(outN, outP, inN, inP));
    }

    public void notifyNodeStructureChanged(NodeData node) {
        notifyListeners("changing node structure", listener -> listener.onNodeStructureChanged(node));
    }

    public void notifyGraphMetadataChanged() {
        notifyListeners("changing graph metadata", EditorListener::onGraphMetadataChanged);
    }

    public void notifyGraphConnectionsRebuildRequested() {
        notifyListeners("rebuilding graph connections", EditorListener::onGraphConnectionsRebuildRequested);
    }

    public void replaceGraphState(NodeGraph replacement) {
        if (replacement == null) throw new IllegalArgumentException("replacement graph cannot be null");
        List<String> groupPath = currentGroupPath();
        mGraph.graphKind = replacement.graphKind;
        mGraph.tags = replacement.tags;
        mGraph.comment = replacement.comment;
        mGraph.quest = replacement.quest;
        mGraph.version = replacement.version;
        mGraph.nodes = replacement.nodes;
        mGraph.frames = replacement.frames;
        restoreScope(groupPath);
        notifyListeners("reloading graph", EditorListener::onGraphReloaded);
    }

    private List<String> currentGroupPath() {
        ArrayList<String> reversed = new ArrayList<>();
        for (NodeData node = mCurrentGroupNode; node != null; node = node.parentGroupNode) {
            reversed.add(node.id);
        }
        java.util.Collections.reverse(reversed);
        return List.copyOf(reversed);
    }

    private void restoreScope(List<String> groupPath) {
        NodeData group = null;
        java.util.Map<String, NodeData> nodes = mGraph.nodes;
        for (String id : groupPath) {
            group = nodes == null ? null : nodes.get(id);
            if (group == null || !group.isGroupNode()) {
                group = null;
                break;
            }
            nodes = group.ensureSubNodes();
        }
        mCurrentGroupNode = group;
        mCurrentGraph = group == null ? mGraph : createGroupScopeGraph(group);
    }

    public void notifyExecutionConnectionAdded(String outN, String outP, String inN, String inP) {
        notifyListeners("adding execution connection",
                listener -> listener.onExecutionConnectionAdded(outN, outP, inN, inP));
    }

    public void notifyExecutionConnectionRemoved(String outN, String outP, String inN, String inP) {
        notifyListeners("removing execution connection",
                listener -> listener.onExecutionConnectionRemoved(outN, outP, inN, inP));
    }

    public void notifyFrameAdded(FrameData frame) {
        notifyListeners("adding frame", listener -> listener.onFrameAdded(frame));
    }

    public void notifyFrameRemoved(String frameId) {
        notifyListeners("removing frame", listener -> listener.onFrameRemoved(frameId));
    }

    public void notifyFrameBoundsUpdated(String frameId, float x, float y, float width, float height) {
        notifyListeners("updating frame bounds",
                listener -> listener.onFrameBoundsUpdated(frameId, x, y, width, height));
    }

    public void notifyFrameTitleChanged(String frameId, String title) {
        notifyListeners("changing frame title", listener -> listener.onFrameTitleChanged(frameId, title));
    }

    private void notifyListeners(String event, Consumer<EditorListener> notification) {
        for (EditorListener listener : List.copyOf(mListeners)) {
            try {
                notification.accept(listener);
            } catch (RuntimeException failure) {
                GeometryNode.LOGGER.error("Editor listener failed while {}: listener={}",
                        event, listener.getClass().getName(), failure);
            }
        }
    }
}

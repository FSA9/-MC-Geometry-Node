package com.mine.geometry_node.client.ui.UICommand;

import com.mine.geometry_node.client.ui.viewport.GraphController;
import com.mine.geometry_node.core.node.FrameData;
import com.mine.geometry_node.core.node.NodeData;
import com.mine.geometry_node.core.node.NodeGraph;
import com.mine.geometry_node.core.node.group.GroupNodeFactory;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;

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
    public List<EditorListener> getListeners() { return mListeners; }

    public boolean enterGroupScope(NodeData groupNode) {
        if (groupNode == null || !groupNode.isGroupNode()) {
            return false;
        }

        GroupNodeFactory.ensureBoundaryNodes(groupNode);
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
        default void onGraphConnectionsRebuildRequested() {}
        default void onFrameAdded(com.mine.geometry_node.core.node.FrameData frame) {}
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
        for (EditorListener l : mListeners) l.onNodeAdded(node);
    }

    public void notifyNodeRemoved(String nodeId) {
        for (EditorListener l : mListeners) l.onNodeRemoved(nodeId);
    }

    public void notifySelectionChanged(List<String> selectedIds) {
        for (EditorListener l : mListeners) l.onSelectionChanged(selectedIds);
    }

    public void notifyNodeMoved(String nodeId, float x, float y) {
        for (EditorListener l : mListeners) l.onNodeMoved(nodeId, x, y);
    }

    public void notifyConnectionAdded(String outN, String outP, String inN, String inP) {
        for (EditorListener l : mListeners) l.onConnectionAdded(outN, outP, inN, inP);
    }

    public void notifyConnectionRemoved(String outN, String outP, String inN, String inP) {
        for (EditorListener l : mListeners) l.onConnectionRemoved(outN, outP, inN, inP);
    }

    public void notifyNodeStructureChanged(NodeData node) {
        for (EditorListener l : mListeners) l.onNodeStructureChanged(node);
    }

    public void notifyGraphConnectionsRebuildRequested() {
        for (EditorListener l : mListeners) l.onGraphConnectionsRebuildRequested();
    }

    public void notifyExecutionConnectionAdded(String outN, String outP, String inN, String inP) {
        for (EditorListener l : mListeners) l.onExecutionConnectionAdded(outN, outP, inN, inP);
    }

    public void notifyExecutionConnectionRemoved(String outN, String outP, String inN, String inP) {
        for (EditorListener l : mListeners) l.onExecutionConnectionRemoved(outN, outP, inN, inP);
    }
}

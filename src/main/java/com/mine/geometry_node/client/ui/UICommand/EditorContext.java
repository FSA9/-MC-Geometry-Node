package com.mine.geometry_node.client.ui.UICommand;

import com.mine.geometry_node.client.ui.viewport.GraphController;
import com.mine.geometry_node.core.node.NodeData;
import com.mine.geometry_node.core.node.NodeGraph;

import java.util.ArrayList;
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

    // --- 事件监听器列表 ---
    private final List<EditorListener> mListeners = new ArrayList<>();

    public EditorContext(NodeGraph graph) {
        // 如果传入 null，则默认创建一个空的新图
        this.mGraph = (graph != null) ? graph : new NodeGraph("New Graph");
        this.mCommandManager = new CommandManager();
        this.mGraphController = new GraphController(this);
    }

    // --- Getters ---
    public NodeGraph getGraph() { return mGraph; }
    public CommandManager getCommandManager() { return mCommandManager; }
    public GraphController getGraphController() { return mGraphController; }

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
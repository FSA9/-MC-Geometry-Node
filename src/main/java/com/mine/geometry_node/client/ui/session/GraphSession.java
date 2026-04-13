package com.mine.geometry_node.client.ui.session;

import com.mine.geometry_node.client.ui.UICommand.EditorContext;
import com.mine.geometry_node.core.node.NodeGraph;
import java.util.ArrayList;
import java.util.List;

public class GraphSession {
    public final String fileId;
    public String tabName;
    public boolean isDirty = false;
    public final EditorContext editorContext;

    // --- 相机状态 ---
    public float viewportX = 0;
    public float viewportY = 0;
    public float currentScale = 1.0f;

    // --- 【关键】只存储选中节点的 ID，不再存储 UINode 对象 ---
    public final List<String> selectedNodeIds = new ArrayList<>();

    // 构造函数现在只需要 3 个参数，且完全不涉及 UI
    public GraphSession(String fileId, String tabName, NodeGraph graph) {
        this.fileId = fileId;
        this.tabName = tabName;
        this.editorContext = new EditorContext(graph);
    }
}
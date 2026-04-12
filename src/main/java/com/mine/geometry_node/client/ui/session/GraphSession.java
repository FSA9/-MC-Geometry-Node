package com.mine.geometry_node.client.ui.session;

import com.mine.geometry_node.client.ui.UICommand.EditorContext;
import com.mine.geometry_node.client.ui.Viewport.UINode;
import com.mine.geometry_node.core.node.NodeGraph;
import icyllis.modernui.core.Context;
import icyllis.modernui.widget.FrameLayout;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

public class GraphSession {
    public final String fileId;
    public String tabName;
    public boolean isDirty = false;
    public final EditorContext editorContext;

    // --- 相机状态 ---
    public float viewportX = 0;
    public float viewportY = 0;
    public float currentScale = 1.0f;

    // --- UI 层状态 (核心修改：完美保留内存) ---
    public final FrameLayout nodeLayer;
    public final Map<String, UINode> nodeViews = new HashMap<>();
    public final List<UINode> selectedNodes = new ArrayList<>();

    public GraphSession(Context uiContext, String fileId, String tabName, NodeGraph graph) {
        this.fileId = fileId;
        this.tabName = tabName;
        this.editorContext = new EditorContext(graph);

        // 为这个标签页创建一个专属的节点渲染层
        this.nodeLayer = new FrameLayout(uiContext);
        this.nodeLayer.setPivotX(0);
        this.nodeLayer.setPivotY(0);
        this.nodeLayer.setClipChildren(false);
    }
}
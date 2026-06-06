package com.mine.geometry_node.client.ui.viewport.interaction;

import com.mine.geometry_node.client.ui.viewport.Viewport;
import com.mine.geometry_node.client.ui.viewport.ViewportCamera;
import com.mine.geometry_node.client.ui.viewport.frame.FrameVisualAdapter;
import com.mine.geometry_node.client.ui.viewport.node.NodeVisualAdapter;

import java.util.List;

public interface InteractionContext {

    // --- 状态 ---
    boolean isReady();

    // --- 核心模块 ---
    ViewportCamera getCamera();

    // --- 节点与选择 (全部基于 UI 逻辑坐标 DP) ---
    FrameVisualAdapter findFrameAt(float uiX, float uiY);
    FrameVisualAdapter getSmallestContainingFrame(float uiX, float uiY);

    NodeVisualAdapter findNodeAt(float uiX, float uiY);
    Viewport.PortInfo findPortAt(float uiX, float uiY);
    void moveSelectedNodes(float uiDx, float uiDy);
    boolean isSnapToGridEnabled();
    float getSnapGridSize();
    void updateBoxSelection(float uiX, float uiY, float uiW, float uiH);

    List<NodeVisualAdapter> getSelectedNodeVisuals();
    void clearSelection();
    void addToSelection(NodeVisualAdapter node);
    void addToSelection(FrameVisualAdapter frame);
    List<FrameVisualAdapter> getSelectedFrameVisuals();
    boolean hasConnection(NodeVisualAdapter outNode, String outPortId, NodeVisualAdapter inNode, String inPortId);

    // --- UI 响应 ---
    void invalidate();
    void requestViewportFocus();
    void showMenu(float screenX, float screenY);
    void closeMenu();
    void previewFrameMove(String frameId, float totalUiDx, float totalUiDy);
    void updateFrameBounds(String frameId);
    void updateConnectionsForNode(String nodeId);
    void cutIntersectingConnections(float lastUiX, float lastUiY, float currentUiX, float currentUiY, InteractionManager.InteractionListener listener);
    Iterable<NodeVisualAdapter> getAllNodeVisuals();
    Iterable<FrameVisualAdapter> getAllFrameVisuals();

    // --- 环境上下文 ---
    icyllis.modernui.core.Context getUIContext();
    float getLastMouseUiX();
    float getLastMouseUiY();

    // ==========================================
    // 意图请求边界 (Intent Boundary)
    // ==========================================
    void requestAddNode(float screenX, float screenY, String typeId);
    void requestAddFrame(float uiX, float uiY);
    void requestGroupIntoFrame();
    void requestRenamePort(String nodeId, String category, String portId, String oldName, String newName);
    void requestSave();
}

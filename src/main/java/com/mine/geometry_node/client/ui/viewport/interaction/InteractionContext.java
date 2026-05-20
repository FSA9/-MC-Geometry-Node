package com.mine.geometry_node.client.ui.viewport.interaction;

import com.mine.geometry_node.client.ui.viewport.UIFrame;
import com.mine.geometry_node.client.ui.viewport.UINode;
import com.mine.geometry_node.client.ui.viewport.Viewport;
import com.mine.geometry_node.client.ui.viewport.ViewportCamera;
import java.util.List;

public interface InteractionContext {

    // --- 状态 ---
    boolean isReady();

    // --- 核心模块 ---
    ViewportCamera getCamera();

    // --- 节点与选择 (全部基于 UI 逻辑坐标 DP) ---
    UIFrame findFrameAt(float uiX, float uiY);
    UIFrame getSmallestContainingFrame(float uiX, float uiY);
    void moveFrameAndChildren(String frameId, float dx, float dy);

    UINode findNodeAt(float uiX, float uiY);
    Viewport.PortInfo findPortAt(float uiX, float uiY);
    void moveSelectedNodes(float uiDx, float uiDy);
    void updateBoxSelection(float uiX, float uiY, float uiW, float uiH);

    List<UINode> getSelectedNodes();
    void clearSelection();
    void addToSelection(UINode node);
    void addToSelection(UIFrame frame);
    List<UIFrame> getSelectedFrames();
    boolean hasConnection(UINode outNode, String outPortId, UINode inNode, String inPortId);

    // --- UI 响应 ---
    void invalidate();
    void requestViewportFocus();
    void showMenu(float screenX, float screenY);
    void closeMenu();
    void addNodeToScene(UINode node);
    void previewFrameMove(String frameId, float totalUiDx, float totalUiDy);
    void updateFrameBounds(String frameId);
    void updateConnectionsForNode(String nodeId);
    void cutIntersectingConnections(float lastUiX, float lastUiY, float currentUiX, float currentUiY, InteractionManager.InteractionListener listener);
    Iterable<UIFrame> getAllFrames();

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
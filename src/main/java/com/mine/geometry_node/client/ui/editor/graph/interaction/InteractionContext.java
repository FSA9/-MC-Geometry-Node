package com.mine.geometry_node.client.ui.editor.graph.interaction;

import com.mine.geometry_node.client.ui.editor.graph.action.ViewportActionState;
import com.mine.geometry_node.client.ui.editor.graph.action.ViewportActionSink;
import com.mine.geometry_node.client.ui.editor.graph.Viewport;
import com.mine.geometry_node.client.ui.editor.graph.ViewportCamera;
import com.mine.geometry_node.client.ui.editor.graph.connection.ConnectionLayer;
import com.mine.geometry_node.client.ui.editor.graph.frame.FrameVisualAdapter;
import com.mine.geometry_node.client.ui.editor.graph.node.NodeVisualAdapter;

import java.util.List;

public interface InteractionContext extends ViewportActionState {

    // --- 状态 ---
    boolean isReady();

    // --- 核心模块 ---
    ViewportCamera getCamera();

    // --- 节点与选择 (全部基于 UI 逻辑坐标 DP) ---
    FrameVisualAdapter findFrameAt(float uiX, float uiY);
    FrameVisualAdapter getSmallestContainingFrame(float uiX, float uiY);

    NodeVisualAdapter findNodeAt(float uiX, float uiY);
    Viewport.PortInfo findPortAt(float uiX, float uiY);
    void previewSelectedElementsMove(float totalUiDx, float totalUiDy);
    void resetSelectedElementsPreview();
    boolean isSnapToGridEnabled();
    float getSnapGridSize();
    void updateBoxSelection(float uiX, float uiY, float uiW, float uiH, boolean includeFrames);

    List<NodeVisualAdapter> getSelectedNodeVisuals();
    boolean isNodeSelected(String nodeId);
    void clearSelection();
    void addToSelection(NodeVisualAdapter node);
    void addToSelection(FrameVisualAdapter frame);
    void toggleSelection(NodeVisualAdapter node);
    void toggleSelection(FrameVisualAdapter frame);
    List<FrameVisualAdapter> getSelectedFrameVisuals();
    boolean hasConnection(NodeVisualAdapter outNode, String outPortId, NodeVisualAdapter inNode, String inPortId);
    boolean canConnectPorts(String outNodeId, String outPortId, String inNodeId, String inPortId);
    boolean isInsideGroupScope();

    // --- UI 响应 ---
    void invalidate();
    void requestViewportFocus();
    void showMenu(float screenX, float screenY);
    void closeMenu();
    void previewFrameMove(String frameId, float totalUiDx, float totalUiDy);
    void updateFrameBounds(String frameId);
    void updateConnectionsForNode(String nodeId);
    void cutIntersectingConnections(float lastUiX, float lastUiY, float currentUiX, float currentUiY, InteractionManager.InteractionListener listener);
    List<ConnectionLayer.ConnectionHit> findIntersectingConnections(float lastUiX, float lastUiY, float currentUiX, float currentUiY);
    Iterable<NodeVisualAdapter> getAllNodeVisuals();
    Iterable<FrameVisualAdapter> getAllFrameVisuals();

    // --- 环境上下文 ---
    icyllis.modernui.core.Context getUIContext();
    ViewportActionSink getActionSink();
    float getLastMouseUiX();
    float getLastMouseUiY();

}

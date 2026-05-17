// --- START OF FILE InteractionContext.java ---
package com.mine.geometry_node.client.ui.viewport.interaction;

import com.mine.geometry_node.client.ui.UICommand.EditorContext;
import com.mine.geometry_node.client.ui.viewport.UIFrame;
import com.mine.geometry_node.client.ui.viewport.UINode;
import com.mine.geometry_node.client.ui.viewport.Viewport;
import com.mine.geometry_node.client.ui.viewport.ViewportCamera;
import java.util.List;

public interface InteractionContext {

    // --- 核心模块 ---

    /**
     * 获取视口摄像机，用于坐标转换与平移缩放
     */
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
    boolean hasConnection(UINode outNode, String outPortId, UINode inNode, String inPortId);

    // --- UI 响应 ---
    void invalidate();
    void requestViewportFocus();
    void showMenu(float screenX, float screenY);
    void addNodeToScene(UINode node);

    // --- 环境上下文 ---
    icyllis.modernui.core.Context getUIContext();
    EditorContext getEditorContext();

    float getLastMouseUiX();
    float getLastMouseUiY();

    // 请求在指定物理屏幕坐标添加节点
    void requestAddNode(float screenX, float screenY, String typeId);

    // 关闭菜单
    void closeMenu();
}
// --- END OF FILE InteractionContext.java ---
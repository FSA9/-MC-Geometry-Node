package com.mine.geometry_node.client.ui.Viewport.Interaction;

import com.mine.geometry_node.client.ui.UICommand.EditorContext;
import com.mine.geometry_node.client.ui.Viewport.UINode;
import com.mine.geometry_node.client.ui.Viewport.Viewport;
import java.util.List;

public interface InteractionContext {

    // --- 核心坐标转换 API (统一入口) ---

    /**
     * 将屏幕物理坐标 (触摸点) 转换为 UI 逻辑坐标 (DP)
     * 自动处理了：平移(Pan) + 缩放(Zoom) + 屏幕密度(Density)
     */
    float screenToUIX(float screenX);
    float screenToUIY(float screenY);

    /**
     * 将 UI 逻辑坐标转换为屏幕物理坐标 (用于绘制连线/Overlay)
     */
    float uiToScreenX(float uiX);
    float uiToScreenY(float uiY);

    // --- 视口控制 ---

    // 获取视口的物理偏移 (用于平移计算)
    float getViewportX();
    float getViewportY();

    // 设置视口的物理偏移
    void setViewportX(float x);
    void setViewportY(float y);

    // 更新视口变换 (应用平移缩放)
    void updateTransform();

    // 获取当前缩放比例
    float getCurrentScale();

    // 执行缩放
    void performZoom(boolean zoomIn, float pivotX, float pivotY);

    // --- 节点与选择 (全部基于 UI 坐标) ---

    // 查找节点 (传入 UI 坐标)
    UINode findNodeAt(float uiX, float uiY);

    // 查找端口 (传入 UI 坐标)
    Viewport.PortInfo findPortAt(float uiX, float uiY);

    // 移动选中节点 (传入 UI 坐标增量)
    void moveSelectedNodes(float uiDx, float uiDy);

    // 框选 (传入 UI 坐标矩形)
    void updateBoxSelection(float uiX, float uiY, float uiW, float uiH);

    // --- 其他保持不变 ---
    void invalidate();
    void requestViewportFocus();
    List<UINode> getSelectedNodes();
    void clearSelection();
    void addToSelection(UINode node);
//    void addConnection(Viewport.Connection connection);
    boolean hasConnection(UINode outNode, String outPortId, UINode inNode, String inPortId);
    void showMenu(float screenX, float screenY);

    void addNodeToScene(UINode node);

    icyllis.modernui.core.Context getUIContext();
    EditorContext getEditorContext();
}
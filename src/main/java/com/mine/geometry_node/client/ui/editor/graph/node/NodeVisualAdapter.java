package com.mine.geometry_node.client.ui.editor.graph.node;

import com.mine.geometry_node.client.ui.editor.graph.CanvasVisualItem;
import com.mine.geometry_node.client.ui.editor.graph.ViewportCamera;
import com.mine.geometry_node.client.ui.editor.graph.connection.ConnectionNodeVisual;
import com.mine.geometry_node.core.node.document.NodeData;
import com.mine.geometry_node.core.node.definition.node.NodeDef;
import icyllis.modernui.graphics.Canvas;
import icyllis.modernui.view.View;

/**
 * Adapter boundary for nodes rendered by the viewport canvas layer.
 */
public interface NodeVisualAdapter extends CanvasVisualItem, ConnectionNodeVisual {
    NodeData getNodeData();

    NodeDef getNodeDef();

    void drawNode(Canvas canvas, ViewportCamera camera);

    String hitTestPort(float localXdp, float localYdp, boolean checkInput, float touchRadiusDp);

    String hitTestLabel(float localXpx, float localYpx);

    View findInteractiveViewAt(float localXpx, float localYpx);

    View getOverlayHostView();

    boolean hasOverlayViews();

    default boolean ensureOverlayViews() { return hasOverlayViews(); }

    default void releaseOverlayViews() {}

    default boolean isOverlayActive() {
        View overlayHost = getOverlayHostView();
        return overlayHost != null && overlayHost.hasFocus();
    }

    default void onOverlayScaleChanged(float scale) {}

    default void onOverlayTransformChanged(float scale, float windowLeftPx, float windowTopPx) {
        onOverlayScaleChanged(scale);
    }

    default void onOverlayTransformChanged(float scale, float windowLeftPx, float windowTopPx, int overlayOrder) {
        onOverlayTransformChanged(scale, windowLeftPx, windowTopPx);
    }

    int getTotalHeightDp();

    void updateNodeLayout();

    @Override
    default String getNodeId() {
        return getNodeData().id;
    }

    default String getParentFrameId() {
        return getNodeData().parentFrame;
    }

    default float getVisualWidthDp() {
        return NodeUiMetrics.width(getNodeDef());
    }

    default float getVisualHeightDp() {
        return getTotalHeightDp();
    }
}

package com.mine.geometry_node.client.ui.viewport.visual;

import com.mine.geometry_node.client.ui.UIConstants;
import com.mine.geometry_node.client.ui.viewport.ViewportCamera;
import com.mine.geometry_node.core.node.NodeData;
import com.mine.geometry_node.core.node.nodes.NodeDef;
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
        return UIConstants.Node.NODE_WIDTH;
    }

    default float getVisualHeightDp() {
        return getTotalHeightDp();
    }
}

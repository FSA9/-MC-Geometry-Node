package com.mine.geometry_node.client.ui.editor.graph.frame;

import com.mine.geometry_node.client.ui.editor.graph.CanvasVisualItem;
import com.mine.geometry_node.client.ui.editor.graph.ViewportCamera;
import com.mine.geometry_node.core.node.document.FrameData;
import icyllis.modernui.graphics.Canvas;

/**
 * Adapter boundary for frames rendered by the viewport canvas layer.
 */
public interface FrameVisualAdapter extends CanvasVisualItem {
    FrameData getFrameData();

    void drawFrame(Canvas canvas, ViewportCamera camera);

    void updateBounds();

    void updateTitle();

    boolean hitTest(float uiX, float uiY);

    void setPreviewBounds(float x, float y, float w, float h);

    float getVisualWidthDp();

    float getVisualHeightDp();

    default String getFrameId() {
        return getFrameData().id;
    }

    default String getParentFrameId() {
        return getFrameData().parentFrame;
    }
}

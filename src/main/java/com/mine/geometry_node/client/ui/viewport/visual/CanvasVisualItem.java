package com.mine.geometry_node.client.ui.viewport.visual;

import icyllis.modernui.graphics.RectF;

/**
 * Canvas-rendered viewport item whose logical position is stored in UI dp.
 */
public interface CanvasVisualItem {
    float getUiX();

    float getUiY();

    void getLogicalBounds(RectF outRect);

    void setPreviewPosition(float x, float y);

    void offsetPreviewPosition(float dx, float dy);

    void setSelected(boolean selected);

    boolean isSelected();
}
